$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $Root ".env"
$BackendRoot = Join-Path $Root "smart-backend"
$FrontendRoot = Join-Path $Root "smart-frontend"
$MavenRepo = Join-Path $Root "local-cache\maven"

function Write-Section($Text) {
    Write-Host ""
    Write-Host "========================================"
    Write-Host $Text
    Write-Host "========================================"
}

function Import-LocalEnv($Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Project .env file was not found: $Path"
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }

        $parts = $line -split "=", 2
        if ($parts.Count -ne 2) {
            continue
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (-not $key) {
            continue
        }

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Set-EnvIfBlank($Key, $Value) {
    $current = [Environment]::GetEnvironmentVariable($Key, "Process")
    if ([string]::IsNullOrWhiteSpace($current)) {
        [Environment]::SetEnvironmentVariable($Key, $Value, "Process")
    }
}

function Resolve-ProjectEnvPath($Key) {
    $value = [Environment]::GetEnvironmentVariable($Key, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        return
    }

    $expanded = [Environment]::ExpandEnvironmentVariables($value)
    if ([System.IO.Path]::IsPathRooted($expanded)) {
        return
    }

    $candidate = Join-Path $Root $expanded
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        [Environment]::SetEnvironmentVariable($Key, (Resolve-Path -LiteralPath $candidate).Path, "Process")
    }
}

function Prepare-RagEnvironment {
    if ([string]::IsNullOrWhiteSpace($env:CHROMA_URL) -and -not [string]::IsNullOrWhiteSpace($env:CHROMA_BASE_URL)) {
        $env:CHROMA_URL = $env:CHROMA_BASE_URL
    }

    Set-EnvIfBlank "CHROMA_URL" "http://127.0.0.1:8000"
    Set-EnvIfBlank "CHROMA_COLLECTION" "campus_maintenance_kb"
    Set-EnvIfBlank "INTERNAL_SERVICE_SECRET" "qiyun-local-internal-secret"
    Set-EnvIfBlank "EMBEDDING_MODEL_PATH" (Join-Path $Root "local-models\paraphrase-multilingual-MiniLM-L12-v2\model.onnx")
    Set-EnvIfBlank "EMBEDDING_TOKENIZER_PATH" (Join-Path $Root "local-models\paraphrase-multilingual-MiniLM-L12-v2\tokenizer.json")

    Resolve-ProjectEnvPath "EMBEDDING_MODEL_PATH"
    Resolve-ProjectEnvPath "EMBEDDING_TOKENIZER_PATH"

    $modelPath = [Environment]::GetEnvironmentVariable("EMBEDDING_MODEL_PATH", "Process")
    $tokenizerPath = [Environment]::GetEnvironmentVariable("EMBEDDING_TOKENIZER_PATH", "Process")
    if ([string]::IsNullOrWhiteSpace($modelPath) -or -not (Test-Path -LiteralPath $modelPath -PathType Leaf)) {
        Write-Host "[WARN] EMBEDDING_MODEL_PATH is not a valid file. RAG embedding will be unavailable until the ONNX model is configured." -ForegroundColor Yellow
    }
    if ([string]::IsNullOrWhiteSpace($tokenizerPath) -or -not (Test-Path -LiteralPath $tokenizerPath -PathType Leaf)) {
        Write-Host "[WARN] EMBEDDING_TOKENIZER_PATH is not a valid file. RAG embedding will be unavailable until the tokenizer is configured." -ForegroundColor Yellow
    }
}

function Test-PortListening($Port) {
    try {
        return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    } catch {
        $pattern = ":$Port\s+.*LISTENING"
        return [bool]((netstat -ano | Select-String -Pattern $pattern) | Select-Object -First 1)
    }
}


function Get-PortListeningPids($Port) {
    try {
        return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique |
            Where-Object { $_ -and $_ -gt 0 })
    } catch {
        $pattern = ":$Port\s+.*LISTENING\s+(\d+)"
        return @(netstat -ano |
            Select-String -Pattern $pattern |
            ForEach-Object { [int]$_.Matches[0].Groups[1].Value } |
            Sort-Object -Unique)
    }
}

function Stop-PortListeners($Name, $Port) {
    $pids = Get-PortListeningPids $Port
    if (-not $pids -or $pids.Count -eq 0) {
        return
    }

    foreach ($processId in $pids) {
        Write-Host "[RESTART] Stopping existing $Name process on port $Port (PID $processId)."
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }

    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Test-PortListening $Port)) {
            return
        }
        Start-Sleep -Milliseconds 500
    }

    throw "$Name is still listening on port $Port after restart attempt. Please close it manually and run the launcher again."
}


function Assert-Port($Name, $Port) {
    if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -InformationLevel Quiet)) {
        throw "$Name is not reachable on 127.0.0.1:$Port. Please start $Name first."
    }
}

function Start-PowerShellWindow($Title, $Command) {
    $wrapped = @"
`$Host.UI.RawUI.WindowTitle = '$Title'
$Command
"@
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($wrapped))
    Start-Process powershell.exe -ArgumentList @("-NoExit", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", $encoded)
}

function Start-MavenService($Name, $Port, $Directory) {
    if (Test-PortListening $Port) {
        Stop-PortListeners $Name $Port
    }

    Write-Host "[START] $Name on port $Port."
    $command = @"
Set-Location -LiteralPath '$Directory'
& mvn.cmd "-Dmaven.repo.local=$MavenRepo" "-Dmaven.test.skip=true" "spring-boot:run"
"@
    Start-PowerShellWindow "$Name $Port" $command
    Start-Sleep -Seconds 3
}

function Start-Frontend($Port, $Directory) {
    if (Test-PortListening $Port) {
        Write-Host "[SKIP] smart-frontend is already running on port $Port."
        return
    }

    Write-Host "[START] smart-frontend on port $Port."
    $command = @"
Set-Location -LiteralPath '$Directory'
& npm.cmd run dev
"@
    Start-PowerShellWindow "smart-frontend $Port" $command
}

try {
    Write-Section "AI Campus Maintenance Platform Launcher"

    if (-not (Test-Path -LiteralPath (Join-Path $BackendRoot "pom.xml"))) {
        throw "smart-backend was not found: $BackendRoot"
    }

    if (-not (Test-Path -LiteralPath (Join-Path $FrontendRoot "package.json"))) {
        throw "smart-frontend was not found: $FrontendRoot"
    }

    Write-Host "Loading local environment variables from .env..."
    Import-LocalEnv $EnvFile
    Prepare-RagEnvironment
    Write-Host "Environment loaded."

    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw 'Maven command "mvn" was not found in PATH.'
    }

    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'npm.cmd was not found in PATH.'
    }

    Write-Host "Checking MySQL on port 3306..."
    Assert-Port "MySQL" 3306

    Write-Host "Checking Nacos on port 8848..."
    Assert-Port "Nacos" 8848

    $mysql = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($mysql) {
        Write-Host "Checking MySQL login from .env..."
        $oldMysqlPwd = $env:MYSQL_PWD
        $env:MYSQL_PWD = $env:DB_PASSWORD
        & mysql.exe --host=localhost --port=3306 --user=root --protocol=tcp --batch --skip-column-names --execute="SELECT 1" *> $null
        $mysqlExit = $LASTEXITCODE
        if ($null -eq $oldMysqlPwd) {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $oldMysqlPwd
        }
        if ($mysqlExit -ne 0) {
            throw "MySQL login failed. Please check DB_PASSWORD in .env. Do not share the password in chat."
        }
        Write-Host "MySQL login OK."
    }

    Write-Section "Preparing backend shared modules"
    Push-Location $BackendRoot
    & mvn.cmd "-Dmaven.repo.local=$MavenRepo" "-Dmaven.test.skip=true" "install"
    if ($LASTEXITCODE -ne 0) {
        throw "Backend preparation failed. Check the Maven output above."
    }
    Pop-Location

    Write-Section "Starting backend services"
    Start-MavenService "qiyun-user-service" 9003 (Join-Path $BackendRoot "qiyun-user-service")
    Start-MavenService "qiyun-repair-service" 9004 (Join-Path $BackendRoot "qiyun-repair-service")
    Start-MavenService "qiyun-ops-service" 9005 (Join-Path $BackendRoot "qiyun-ops-service")
    Start-MavenService "qiyun-ai-service" 9002 (Join-Path $BackendRoot "qiyun-ai-service")
    Start-MavenService "qiyun-biz-service" 9001 (Join-Path $BackendRoot "qiyun-biz-service")
    Start-MavenService "qiyun-gateway" 8070 (Join-Path $BackendRoot "qiyun-gateway")

    Write-Section "Starting frontend"
    Start-Frontend 5173 $FrontendRoot

    Write-Host ""
    Write-Host "Startup windows have been opened."
    Write-Host "Frontend: http://localhost:5173/"
    Write-Host "Gateway:  http://localhost:8070/"
    Write-Host ""
    Write-Host "Waiting a few seconds before opening the browser..."
    Start-Sleep -Seconds 8

    $edge = Get-Command msedge.exe -ErrorAction SilentlyContinue
    if ($edge) {
        Start-Process msedge.exe "http://localhost:5173/"
    } else {
        Write-Host "Please open this URL manually: http://localhost:5173/"
    }

    Write-Host ""
    Write-Host "Keep all service windows open while using the project."
} catch {
    Write-Host ""
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

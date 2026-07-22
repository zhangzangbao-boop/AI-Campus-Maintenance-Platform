# AI Campus Maintenance Platform - smart-backend service launcher

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $projectRoot ".env"
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        if ($_ -match '^([^#][^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim().Replace('"', '')
            Set-Item -Path "Env:$key" -Value $value
        }
    }
    Write-Host "Loaded environment variables from project .env." -ForegroundColor Green
} else {
    Write-Host "Warning: project .env file was not found." -ForegroundColor Yellow
}

function Set-EnvIfBlank($Key, $Value) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Key, "Process"))) {
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
    $candidate = Join-Path $projectRoot $expanded
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        [Environment]::SetEnvironmentVariable($Key, (Resolve-Path -LiteralPath $candidate).Path, "Process")
    }
}

if ([string]::IsNullOrWhiteSpace($env:CHROMA_URL) -and -not [string]::IsNullOrWhiteSpace($env:CHROMA_BASE_URL)) {
    $env:CHROMA_URL = $env:CHROMA_BASE_URL
}
Set-EnvIfBlank "CHROMA_URL" "http://127.0.0.1:8000"
Set-EnvIfBlank "CHROMA_COLLECTION" "campus_maintenance_kb"
Set-EnvIfBlank "INTERNAL_SERVICE_SECRET" "qiyun-local-internal-secret"
Set-EnvIfBlank "EMBEDDING_MODEL_PATH" (Join-Path $projectRoot "local-models\paraphrase-multilingual-MiniLM-L12-v2\model.onnx")
Set-EnvIfBlank "EMBEDDING_TOKENIZER_PATH" (Join-Path $projectRoot "local-models\paraphrase-multilingual-MiniLM-L12-v2\tokenizer.json")
Resolve-ProjectEnvPath "EMBEDDING_MODEL_PATH"
Resolve-ProjectEnvPath "EMBEDDING_TOKENIZER_PATH"

$modelPath = [Environment]::GetEnvironmentVariable("EMBEDDING_MODEL_PATH", "Process")
$tokenizerPath = [Environment]::GetEnvironmentVariable("EMBEDDING_TOKENIZER_PATH", "Process")
if ([string]::IsNullOrWhiteSpace($modelPath) -or -not (Test-Path -LiteralPath $modelPath -PathType Leaf)) {
    Write-Host "Warning: EMBEDDING_MODEL_PATH is not a valid file. RAG embedding will be unavailable until the ONNX model is configured." -ForegroundColor Yellow
}
if ([string]::IsNullOrWhiteSpace($tokenizerPath) -or -not (Test-Path -LiteralPath $tokenizerPath -PathType Leaf)) {
    Write-Host "Warning: EMBEDDING_TOKENIZER_PATH is not a valid file. RAG embedding will be unavailable until the tokenizer is configured." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Select a smart-backend service to start:"
Write-Host "1) Gateway (port 8070)"
Write-Host "2) User Service (port 9003)"
Write-Host "3) Repair Service (port 9004)"
Write-Host "4) AI Service (port 9002)"
Write-Host "5) Ops Service (port 9005)"
Write-Host "6) Biz Service (port 9001, compatibility fallback)"
Write-Host "7) Start all services"
Write-Host ""

$choice = Read-Host "Enter option (1-7)"

$services = [ordered]@{
    "1" = @{ Name = "qiyun-gateway"; Path = "qiyun-gateway" }
    "2" = @{ Name = "qiyun-user-service"; Path = "qiyun-user-service" }
    "3" = @{ Name = "qiyun-repair-service"; Path = "qiyun-repair-service" }
    "4" = @{ Name = "qiyun-ai-service"; Path = "qiyun-ai-service" }
    "5" = @{ Name = "qiyun-ops-service"; Path = "qiyun-ops-service" }
    "6" = @{ Name = "qiyun-biz-service"; Path = "qiyun-biz-service" }
}

function Start-ServiceModule {
    param([hashtable]$Service)

    $path = Join-Path $PSScriptRoot $Service.Path
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host "Missing service directory: $path" -ForegroundColor Red
        return
    }

    Start-Process powershell -ArgumentList @("-NoExit", "-Command", "Set-Location -LiteralPath '$path'; mvn '-Dmaven.test.skip=true' spring-boot:run")
    Write-Host "Opened $($Service.Name)." -ForegroundColor Cyan
}

if ($choice -eq "7") {
    foreach ($svc in $services.Values) {
        Start-ServiceModule -Service $svc
        Start-Sleep -Seconds 2
    }
    Write-Host ""
    Write-Host "All smart-backend service windows were opened." -ForegroundColor Green
} elseif ($services.Contains($choice)) {
    $svc = $services[$choice]
    $path = Join-Path $PSScriptRoot $svc.Path
    Write-Host "Starting $($svc.Name) from $path..." -ForegroundColor Cyan
    Set-Location -LiteralPath $path
    mvn "-Dmaven.test.skip=true" spring-boot:run
} else {
    Write-Host "Invalid option." -ForegroundColor Red
}

param(
    [switch]$SkipBackendTests,
    [switch]$SkipFrontendBuild
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$MavenRepo = Join-Path $Root "local-cache\maven"
$BackendDir = Join-Path $Root "smart-backend"
$FrontendDir = Join-Path $Root "smart-frontend"

New-Item -ItemType Directory -Force -Path $MavenRepo | Out-Null

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Body
    )
    Write-Host ""
    Write-Host "==> $Name"
    & $Body
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE"
    }
}

if (-not $SkipBackendTests) {
    Invoke-Step "smart-backend full Maven test" {
        Push-Location $BackendDir
        try {
            & mvn "-Dmaven.repo.local=$MavenRepo" test
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontendBuild) {
    $PackageJsonPath = Join-Path $FrontendDir "package.json"
    $PackageJson = Get-Content -LiteralPath $PackageJsonPath -Raw | ConvertFrom-Json

    Invoke-Step "smart-frontend build" {
        Push-Location $FrontendDir
        try {
            & npm.cmd run build
        } finally {
            Pop-Location
        }
    }

    if ($PackageJson.scripts.PSObject.Properties.Name -contains "test") {
        Invoke-Step "smart-frontend test" {
            Push-Location $FrontendDir
            try {
                & npm.cmd run test
            } finally {
                Pop-Location
            }
        }
    } else {
        Write-Host ""
        Write-Host "==> smart-frontend test"
        Write-Host "No frontend test script is configured in package.json; skipped."
    }
}

Write-Host ""
Write-Host "All requested verification steps completed."

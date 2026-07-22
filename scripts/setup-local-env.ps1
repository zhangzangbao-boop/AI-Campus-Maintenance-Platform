param()

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$EnvPath = Join-Path $Root ".env"
$ModelPath = Join-Path $Root "local-models\paraphrase-multilingual-MiniLM-L12-v2\model.onnx"
$TokenizerPath = Join-Path $Root "local-models\paraphrase-multilingual-MiniLM-L12-v2\tokenizer.json"

function Convert-SecretToPlainText {
    param([System.Security.SecureString]$SecureValue)

    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function New-RandomSecret {
    $bytes = New-Object byte[] 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
        [Convert]::ToBase64String($bytes)
    } finally {
        $rng.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Escape-DotEnvValue {
    param([string]$Value)

    if ($null -eq $Value) {
        return '""'
    }

    $escaped = $Value.Replace('\', '\\').Replace('"', '\"').Replace("`r", '\r').Replace("`n", '\n')
    '"' + $escaped + '"'
}

if (-not (Test-Path -LiteralPath $ModelPath -PathType Leaf)) {
    throw "Embedding model file was not found under project local-models: $ModelPath"
}

if (-not (Test-Path -LiteralPath $TokenizerPath -PathType Leaf)) {
    throw "Embedding tokenizer file was not found under project local-models: $TokenizerPath"
}

if (Test-Path -LiteralPath $EnvPath -PathType Leaf) {
    $answer = Read-Host "Project .env already exists. Overwrite it? Type YES to continue"
    if ($answer -ne "YES") {
        Write-Host "Cancelled. Existing .env was not changed."
        exit 0
    }
}

$deepseekSecure = Read-Host "Enter DEEPSEEK_API_KEY" -AsSecureString
$dbPasswordSecure = Read-Host "Enter DB_PASSWORD" -AsSecureString

$deepseekApiKey = Convert-SecretToPlainText $deepseekSecure
$dbPassword = Convert-SecretToPlainText $dbPasswordSecure
$jwtSecret = New-RandomSecret
$internalServiceSecret = New-RandomSecret

try {
    if ([string]::IsNullOrWhiteSpace($deepseekApiKey)) {
        throw "DEEPSEEK_API_KEY cannot be empty."
    }
    if ([string]::IsNullOrWhiteSpace($dbPassword)) {
        throw "DB_PASSWORD cannot be empty."
    }

    $lines = @(
        "AI_ENABLED=true",
        "AI_MODEL=deepseek-v4-flash",
        "DEEPSEEK_API_KEY=$(Escape-DotEnvValue $deepseekApiKey)",
        "DB_PASSWORD=$(Escape-DotEnvValue $dbPassword)",
        "JWT_SECRET=$(Escape-DotEnvValue $jwtSecret)",
        "INTERNAL_SERVICE_SECRET=$(Escape-DotEnvValue $internalServiceSecret)",
        "NACOS_SERVER_ADDR=127.0.0.1:8848",
        "CHROMA_URL=http://127.0.0.1:8000",
        "EMBEDDING_MODEL_PATH=$(Escape-DotEnvValue $ModelPath)",
        "EMBEDDING_TOKENIZER_PATH=$(Escape-DotEnvValue $TokenizerPath)"
    )

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($EnvPath, $lines, $utf8NoBom)
    Write-Host "Created project .env with local development settings."
    Write-Host "Secrets were written only to .env and were not displayed."
} finally {
    if ($null -ne $deepseekApiKey) { $deepseekApiKey = $null }
    if ($null -ne $dbPassword) { $dbPassword = $null }
    if ($null -ne $jwtSecret) { $jwtSecret = $null }
    if ($null -ne $internalServiceSecret) { $internalServiceSecret = $null }
    if ($null -ne $deepseekSecure) { $deepseekSecure.Dispose() }
    if ($null -ne $dbPasswordSecure) { $dbPasswordSecure.Dispose() }
}

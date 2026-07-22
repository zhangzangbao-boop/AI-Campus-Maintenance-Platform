param(
    [string]$ModelRepo = "Xenova/paraphrase-multilingual-MiniLM-L12-v2",
    [string]$ModelFile = "onnx/model_quantized.onnx",
    [string]$TokenizerFile = "tokenizer.json",
    [string[]]$BaseUrls = @("https://huggingface.co", "https://hf-mirror.com"),
    [long]$MinModelBytes = 100000000,
    [long]$ExpectedModelBytes = 118308126,
    [long]$MinTokenizerBytes = 1000000,
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $Root "local-models\paraphrase-multilingual-MiniLM-L12-v2"
}

$modelOutput = Join-Path $OutputDir "model.onnx"
$tokenizerOutput = Join-Path $OutputDir "tokenizer.json"

function Save-RemoteFile {
    param(
        [string]$RelativePath,
        [string]$Destination,
        [long]$MinBytes,
        [long]$ExpectedBytes = 0
    )

    $existingDestination = Test-Path -LiteralPath $Destination -PathType Leaf
    $existingDestinationSize = if ($existingDestination) { (Get-Item -LiteralPath $Destination).Length } else { 0 }
    if ($existingDestination -and $ExpectedBytes -gt 0 -and $existingDestinationSize -ne $ExpectedBytes) {
        Write-Host "Removing unexpected-size file: $Destination ($existingDestinationSize bytes)"
        Remove-Item -LiteralPath $Destination -Force
        $existingDestination = $false
        $existingDestinationSize = 0
    }
    if ($existingDestination -and $existingDestinationSize -ge $MinBytes) {
        Write-Host "Using existing file: $Destination"
        return
    }

    $partialDestination = "$Destination.part"
    $partialExists = Test-Path -LiteralPath $partialDestination -PathType Leaf
    if ($partialExists -and $ExpectedBytes -gt 0 -and (Get-Item -LiteralPath $partialDestination).Length -gt $ExpectedBytes) {
        Write-Host "Removing oversized partial file: $partialDestination"
        Remove-Item -LiteralPath $partialDestination -Force
    }
    if ($existingDestination -and $existingDestinationSize -lt $MinBytes) {
        Remove-Item -LiteralPath $Destination -Force
    }

    foreach ($baseUrl in $BaseUrls) {
        $url = "$baseUrl/$ModelRepo/resolve/main/$RelativePath"
        try {
            $partialExists = Test-Path -LiteralPath $partialDestination -PathType Leaf
            if ($partialExists -and (Get-Item -LiteralPath $partialDestination).Length -eq 0) {
                Remove-Item -LiteralPath $partialDestination -Force
            }

            Write-Host "Downloading $url"
            $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
            if ($curl) {
                & curl.exe -L --fail --retry 3 --retry-delay 3 -C - -o $partialDestination $url
                if ($LASTEXITCODE -ne 0) {
                    throw "curl.exe failed with exit code $LASTEXITCODE"
                }
            } else {
                Invoke-WebRequest -Uri $url -OutFile $partialDestination -UseBasicParsing
            }

            $downloaded = Test-Path -LiteralPath $partialDestination -PathType Leaf
            $downloadedSize = if ($downloaded) { (Get-Item -LiteralPath $partialDestination).Length } else { 0 }
            if ($downloaded -and $ExpectedBytes -gt 0 -and $downloadedSize -ne $ExpectedBytes) {
                throw "Downloaded file size is $downloadedSize bytes, expected $ExpectedBytes bytes: $partialDestination"
            }
            if ($downloaded -and $downloadedSize -ge $MinBytes) {
                Move-Item -LiteralPath $partialDestination -Destination $Destination -Force
                return
            }
            throw "Downloaded file is too small: $partialDestination"
        } catch {
            Write-Host "Download failed from ${baseUrl}: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }

    if (-not (Test-Path -LiteralPath $Destination -PathType Leaf)) {
        throw "Download did not create expected file: $Destination"
    }
    if ((Get-Item -LiteralPath $Destination).Length -eq 0) {
        throw "Downloaded file is empty: $Destination"
    }
    if ((Get-Item -LiteralPath $Destination).Length -lt $MinBytes) {
        throw "Downloaded file is smaller than expected: $Destination"
    }
    if ($ExpectedBytes -gt 0 -and (Get-Item -LiteralPath $Destination).Length -ne $ExpectedBytes) {
        throw "Downloaded file size does not match expected size: $Destination"
    }
}

function Assert-TokenizerSupported {
    param([string]$Path)

    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $json = $text | ConvertFrom-Json
    $type = $json.model.type
    if ($type -ne "Unigram") {
        Write-Host "Warning: tokenizer model type is '$type'. This project currently expects a HuggingFace Unigram tokenizer." -ForegroundColor Yellow
    }
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Save-RemoteFile -RelativePath $TokenizerFile -Destination $tokenizerOutput -MinBytes $MinTokenizerBytes
Assert-TokenizerSupported -Path $tokenizerOutput
Save-RemoteFile -RelativePath $ModelFile -Destination $modelOutput -MinBytes $MinModelBytes -ExpectedBytes $ExpectedModelBytes

Write-Host ""
Write-Host "Embedding model files are ready:"
Write-Host "Model file size: $((Get-Item -LiteralPath $modelOutput).Length) bytes"
Write-Host "Tokenizer file size: $((Get-Item -LiteralPath $tokenizerOutput).Length) bytes"
Write-Host "EMBEDDING_MODEL_PATH=$modelOutput"
Write-Host "EMBEDDING_TOKENIZER_PATH=$tokenizerOutput"

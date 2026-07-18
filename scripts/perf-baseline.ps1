param(
    [string]$BaseUrl = $env:QY_PERF_BASE_URL,
    [string]$UserId = $env:QY_PERF_USER_ID,
    [string]$Password = $env:QY_PERF_PASSWORD,
    [int]$Concurrency = 5,
    [int]$Iterations = 20,
    [long]$CategoryId = 1,
    [switch]$SkipCreateTicket
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl) -or [string]::IsNullOrWhiteSpace($UserId) -or [string]::IsNullOrWhiteSpace($Password)) {
    [pscustomobject]@{
        status = "PENDING_FULL_CHAIN_ENVIRONMENT"
        reason = "BaseUrl/UserId/Password are required. No request was sent."
        requiredParameters = @("BaseUrl", "UserId", "Password")
        example = ".\scripts\perf-baseline.ps1 -BaseUrl http://localhost:8070 -UserId test_student -Password <password> -Concurrency 5 -Iterations 20"
    } | ConvertTo-Json -Depth 4
    exit 0
}

if ($Concurrency -lt 1 -or $Concurrency -gt 100) {
    throw "Concurrency must be between 1 and 100."
}
if ($Iterations -lt 1 -or $Iterations -gt 10000) {
    throw "Iterations must be between 1 and 10000."
}

$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-JsonPost {
    param([string]$Path, [object]$Body, [hashtable]$Headers = @{})
    Invoke-RestMethod -Method Post -Uri "$BaseUrl$Path" -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 6) -Headers $Headers
}

function Measure-Scenario {
    param([string]$Name)

    $jobs = New-Object System.Collections.Generic.List[object]
    $results = New-Object System.Collections.Generic.List[object]
    $perWorker = [Math]::Ceiling($Iterations / $Concurrency)

    for ($i = 0; $i -lt $Concurrency; $i++) {
        $workerIterations = [Math]::Min($perWorker, $Iterations - ($i * $perWorker))
        if ($workerIterations -le 0) { continue }
        $jobs.Add((Start-Job -ArgumentList $workerIterations, $Name, $BaseUrl, $UserId, $Password, $Token, $CategoryId -ScriptBlock {
            param($WorkerIterations, $ScenarioName, $ScenarioBaseUrl, $ScenarioUserId, $ScenarioPassword, $ScenarioToken, $ScenarioCategoryId)

            Add-Type -AssemblyName System.Net.Http
            $ScenarioAuthHeaders = @{ Authorization = "Bearer $ScenarioToken" }

            function Invoke-ScenarioRequest {
                switch ($ScenarioName) {
                    "login" {
                        Invoke-RestMethod -Method Post -Uri "$ScenarioBaseUrl/api/auth/login" -ContentType "application/json" -Body (@{ userId = $ScenarioUserId; password = $ScenarioPassword } | ConvertTo-Json) | Out-Null
                    }
                    "query-my-repair-orders" {
                        Invoke-RestMethod -Method Get -Uri "$ScenarioBaseUrl/api/repair-orders/my?page=0&size=10" -Headers $ScenarioAuthHeaders | Out-Null
                    }
                    "create-repair-order" {
                        $client = [System.Net.Http.HttpClient]::new()
                        $content = $null
                        try {
                            $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $ScenarioToken)
                            $content = [System.Net.Http.MultipartFormDataContent]::new()
                            $content.Add([System.Net.Http.StringContent]::new("Performance baseline ticket"), "title")
                            $content.Add([System.Net.Http.StringContent]::new([string]$ScenarioCategoryId), "categoryId")
                            $content.Add([System.Net.Http.StringContent]::new("Test location"), "locationText")
                            $content.Add([System.Net.Http.StringContent]::new("Created by the repeatable performance baseline script in a test environment."), "description")
                            $content.Add([System.Net.Http.StringContent]::new("MEDIUM"), "priority")
                            $response = $client.PostAsync("$ScenarioBaseUrl/api/repair-orders", $content).GetAwaiter().GetResult()
                            if (-not $response.IsSuccessStatusCode) {
                                throw "HTTP $([int]$response.StatusCode) $($response.ReasonPhrase)"
                            }
                        } finally {
                            if ($content) { $content.Dispose() }
                            $client.Dispose()
                        }
                    }
                    default {
                        throw "Unknown scenario: $ScenarioName"
                    }
                }
            }

            $workerResults = @()
            for ($j = 0; $j -lt $WorkerIterations; $j++) {
                $sw = [System.Diagnostics.Stopwatch]::StartNew()
                $ok = $true
                $errorText = $null
                try {
                    Invoke-ScenarioRequest
                } catch {
                    $ok = $false
                    $errorText = $_.Exception.Message
                } finally {
                    $sw.Stop()
                }
                $workerResults += [pscustomobject]@{
                    ok = $ok
                    elapsedMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2)
                    error = $errorText
                }
            }
            $workerResults
        }))
    }

    foreach ($job in $jobs) {
        Receive-Job -Job $job -Wait | ForEach-Object { $results.Add($_) }
        Remove-Job -Job $job
    }

    $latencies = @($results | Where-Object { $_.ok } | Select-Object -ExpandProperty elapsedMs | Sort-Object)
    $success = @($results | Where-Object { $_.ok }).Count
    $total = $results.Count
    $p95 = $null
    $avg = $null
    if ($latencies.Count -gt 0) {
        $avg = [Math]::Round((($latencies | Measure-Object -Average).Average), 2)
        $p95Index = [Math]::Min($latencies.Count - 1, [Math]::Ceiling($latencies.Count * 0.95) - 1)
        $p95 = $latencies[$p95Index]
    }

    [pscustomobject]@{
        scenario = $Name
        concurrency = $Concurrency
        iterations = $total
        successCount = $success
        successRate = if ($total -eq 0) { 0 } else { [Math]::Round($success / $total, 4) }
        averageMs = $avg
        p95Ms = $p95
        firstError = ($results | Where-Object { -not $_.ok } | Select-Object -First 1 -ExpandProperty error)
    }
}

$loginResponse = Invoke-JsonPost -Path "/api/auth/login" -Body @{ userId = $UserId; password = $Password }
$Token = $loginResponse.token
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Login response did not include token; cannot run authenticated scenarios."
}
$AuthHeaders = @{ Authorization = "Bearer $Token" }

$scenarios = New-Object System.Collections.Generic.List[object]
$scenarios.Add((Measure-Scenario -Name "login"))
$scenarios.Add((Measure-Scenario -Name "query-my-repair-orders"))

if (-not $SkipCreateTicket) {
    $scenarios.Add((Measure-Scenario -Name "create-repair-order"))
}

[pscustomobject]@{
    status = "completed"
    baseUrl = $BaseUrl
    generatedAt = (Get-Date).ToString("s")
    scenarios = $scenarios
} | ConvertTo-Json -Depth 6

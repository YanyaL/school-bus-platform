#Requires -Version 5.1
<#
.SYNOPSIS
  Run k6 Sentinel HTTP rate-limit acceptance tests.

.DESCRIPTION
  Validates login, booking, and payment callback endpoints under baseline
  (below threshold) and overload (above threshold) arrival rates.

  Prerequisites:
    1. Application running with SENTINEL_RATE_LIMIT_ENABLED=true
    2. Scenario-specific environment variables (no secrets in this script)
    3. Local k6 OR Docker with grafana/k6 image

.EXAMPLE
  $env:SENTINEL_RATE_LIMIT_ENABLED='true'
  $env:SENTINEL_LOGIN_QPS='5'
  $env:TEST_STUDENT_NUMBER='S1234567'
  $env:TEST_PASSWORD='YourPassword'
  .\scripts\load-test\run-sentinel-rate-limit.ps1 -Scenario login -Rate 20

.EXAMPLE
  .\scripts\load-test\run-sentinel-rate-limit.ps1 -Scenario all -ValidateOnly
#>
param(
    [ValidateSet('login', 'booking', 'payment', 'all')]
    [string] $Scenario = 'all',

    [string] $BaseUrl = 'http://localhost:8080',

    [string] $Duration = '20s',

    [int] $Rate = 20,

    [string] $BaselineDuration = '15s',

    [switch] $SkipSentinelCheck,

    [switch] $ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDir -Parent | Split-Path -Parent
$K6Script = Join-Path $ScriptDir 'sentinel-rate-limit.js'
$ResultsDir = Join-Path $ScriptDir 'results'

function Get-SentinelThreshold {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('login', 'booking', 'payment')]
        [string] $Name
    )

    switch ($Name) {
        'login' {
            $value = $env:SENTINEL_LOGIN_QPS
            if ([string]::IsNullOrWhiteSpace($value)) { return 5.0 }
            return [double] $value
        }
        'booking' {
            $value = $env:SENTINEL_CREATE_BOOKING_QPS
            if ([string]::IsNullOrWhiteSpace($value)) { return 5.0 }
            return [double] $value
        }
        'payment' {
            $value = $env:SENTINEL_PAYMENT_CALLBACK_QPS
            if ([string]::IsNullOrWhiteSpace($value)) { return 10.0 }
            return [double] $value
        }
    }
}

function Get-BaselineRate {
    param(
        [Parameter(Mandatory = $true)]
        [double] $Threshold
    )

    if ($Threshold -le 1.0) {
        return 1
    }

    $candidate = [Math]::Max(1, [Math]::Floor($Threshold * 0.6))
    if ($candidate -ge $Threshold) {
        return [Math]::Max(1, [int] [Math]::Floor($Threshold) - 1)
    }
    return [int] $candidate
}

function Test-OverloadRateAboveThreshold {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('login', 'booking', 'payment')]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [int] $OverloadRate
    )

    $threshold = Get-SentinelThreshold -Name $Name
    if ($OverloadRate -le $threshold) {
        throw @"
Overload rate ($OverloadRate) must be strictly greater than the Sentinel threshold ($threshold) for scenario '$Name'.

Increase -Rate above $threshold, or lower the corresponding SENTINEL_*_QPS environment variable on the running application.
"@
    }
}

function Get-ScenarioRequiredVariables {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('login', 'booking', 'payment')]
        [string] $Name
    )

    switch ($Name) {
        'login' {
            return @(
                'TEST_STUDENT_NUMBER',
                'TEST_PASSWORD'
            )
        }
        'booking' {
            return @(
                'TEST_ACCESS_TOKEN',
                'TEST_TRIP_NUMBER',
                'TEST_SEAT_NUMBER'
            )
        }
        'payment' {
            return @(
                'TEST_PAYMENT_CALLBACK_SECRET',
                'TEST_PAYMENT_NUMBER',
                'TEST_BOOKING_NUMBER',
                'TEST_PAYMENT_AMOUNT'
            )
        }
    }
}

function Test-RequiredEnvironmentVariables {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('login', 'booking', 'payment')]
        [string] $Name
    )

    $missing = @()
    foreach ($variable in (Get-ScenarioRequiredVariables -Name $Name)) {
        if ([string]::IsNullOrWhiteSpace((Get-Item -Path "Env:$variable" -ErrorAction SilentlyContinue).Value)) {
            $missing += $variable
        }
    }

    if ($missing.Count -gt 0) {
        throw @"
Missing required environment variables for scenario '$Name':
  $($missing -join ', ')

See docs/07-sentinel-load-test.md for setup instructions.
Do not embed passwords, JWT tokens, or payment secrets in this script.
"@
    }
}

function Test-ApplicationHealth {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Url
    )

    $healthUri = "$($Url.TrimEnd('/'))/actuator/health"
    try {
        $response = Invoke-RestMethod -Method GET -Uri $healthUri -TimeoutSec 5
    } catch {
        throw @"
Application health check failed: GET $healthUri
Ensure the application is running and reachable.
$($_.Exception.Message)
"@
    }

    if ($response.status -ne 'UP') {
        throw "Application health is not UP: $($response.status)"
    }

    Write-Host "Health check passed: $healthUri => UP" -ForegroundColor Green
}

function Test-SentinelEnabled {
    if ($SkipSentinelCheck) {
        Write-Warning 'Skipping SENTINEL_RATE_LIMIT_ENABLED check (-SkipSentinelCheck).'
        return
    }

    if ($env:SENTINEL_RATE_LIMIT_ENABLED -ne 'true') {
        throw @"
SENTINEL_RATE_LIMIT_ENABLED is not 'true'.

Restart the application in a separate terminal, for example:
  `$env:SENTINEL_RATE_LIMIT_ENABLED='true'
  `$env:SENTINEL_LOGIN_QPS='5'
  `$env:SENTINEL_CREATE_BOOKING_QPS='5'
  `$env:SENTINEL_PAYMENT_CALLBACK_QPS='10'
  mvn spring-boot:run

The running JVM must receive these variables before startup.
"@
    }

    Write-Host 'Sentinel rate limiting: enabled (SENTINEL_RATE_LIMIT_ENABLED=true)' -ForegroundColor Green
}

function Get-K6Runner {
    $localK6 = Get-Command k6 -ErrorAction SilentlyContinue
    if ($null -ne $localK6) {
        return @{
            Mode       = 'local'
            Executable = $localK6.Source
        }
    }

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($null -eq $docker) {
        return $null
    }

    try {
        docker info *> $null
    } catch {
        return $null
    }

    return @{
        Mode = 'docker'
    }
}

function Resolve-DockerBaseUrl {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Url
    )

    if ($Url -match '(?i)localhost|127\.0\.0\.1') {
        return ($Url -replace '(?i)localhost', 'host.docker.internal' `
            -replace '127\.0\.0\.1', 'host.docker.internal')
    }
    return $Url
}

function Invoke-K6Scenario {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('login', 'booking', 'payment')]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [hashtable] $Runner
    )

    $threshold = Get-SentinelThreshold -Name $Name
    $baselineRate = Get-BaselineRate -Threshold $threshold

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $summaryPath = Join-Path $ResultsDir "sentinel-$Name-$timestamp-summary.json"

    $envMap = @{
        BASE_URL           = $BaseUrl
        SCENARIO           = $Name
        BASELINE_RATE      = "$baselineRate"
        OVERLOAD_RATE      = "$Rate"
        BASELINE_DURATION  = $BaselineDuration
        OVERLOAD_DURATION  = $Duration
    }

    foreach ($variable in (Get-ScenarioRequiredVariables -Name $Name)) {
        $envMap[$variable] = (Get-Item -Path "Env:$variable").Value
    }

    Write-Host ""
    Write-Host "=== Scenario: $Name ===" -ForegroundColor Cyan
    Write-Host "Sentinel threshold (QPS): $threshold"
    Write-Host "Baseline: $baselineRate req/s for $BaselineDuration"
    Write-Host "Overload: $Rate req/s for $Duration"
    Write-Host "Summary export: $summaryPath"

    if ($Runner.Mode -eq 'local') {
        $argumentList = @('run', '--summary-export', $summaryPath, $K6Script)
        foreach ($entry in $envMap.GetEnumerator()) {
            $argumentList += '-e'
            $argumentList += ("{0}={1}" -f $entry.Key, $entry.Value)
        }

        Write-Host "Running local k6: $($Runner.Executable)" -ForegroundColor DarkGray
        & $Runner.Executable @argumentList
        if ($LASTEXITCODE -ne 0) {
            throw "k6 exited with code $LASTEXITCODE for scenario '$Name'."
        }
        return
    }

    $dockerBaseUrl = Resolve-DockerBaseUrl -Url $BaseUrl
    $envMap['BASE_URL'] = $dockerBaseUrl

    $dockerEnv = @()
    foreach ($entry in $envMap.GetEnumerator()) {
        $dockerEnv += '-e'
        $dockerEnv += ("{0}={1}" -f $entry.Key, $entry.Value)
    }

    $dockerSummary = "/results/$(Split-Path $summaryPath -Leaf)"
    $dockerArgs = @(
        'run', '--rm',
        '-v', "${ScriptDir}:/scripts:ro",
        '-v', "${ResultsDir}:/results",
        $dockerEnv
    ) + @(
        'grafana/k6', 'run',
        '--summary-export', $dockerSummary,
        '/scripts/sentinel-rate-limit.js'
    )

    Write-Host "Running Docker k6 (BASE_URL=$dockerBaseUrl)" -ForegroundColor DarkGray
    & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Docker k6 exited with code $LASTEXITCODE for scenario '$Name'."
    }
}

function Test-RunPreconditions {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Scenarios
    )

    if (-not (Test-Path $K6Script)) {
        throw "k6 script not found: $K6Script"
    }

    Test-ApplicationHealth -Url $BaseUrl
    Test-SentinelEnabled

    foreach ($name in $Scenarios) {
        Test-RequiredEnvironmentVariables -Name $name
    }
}

if (-not (Test-Path $ResultsDir)) {
    New-Item -ItemType Directory -Path $ResultsDir | Out-Null
}

$scenariosToRun = if ($Scenario -eq 'all') {
    @('login', 'booking', 'payment')
} else {
    @($Scenario)
}

foreach ($name in $scenariosToRun) {
    Test-OverloadRateAboveThreshold -Name $name -OverloadRate $Rate
}

Test-RunPreconditions -Scenarios $scenariosToRun

if ($ValidateOnly) {
    Write-Host ""
    Write-Host 'ValidateOnly: prerequisites satisfied; k6 was not executed.' -ForegroundColor Green
    exit 0
}

$runner = Get-K6Runner
if ($null -eq $runner) {
    Write-Host @"

Neither local k6 nor Docker k6 is available.

Install one of:
  1. k6 — https://grafana.com/docs/k6/latest/set-up/install-k6/
  2. Docker Desktop — then this script uses grafana/k6 automatically

After installation, re-run:
  .\scripts\load-test\run-sentinel-rate-limit.ps1 -Scenario $Scenario
"@ -ForegroundColor Yellow
    exit 2
}

Write-Host "k6 runner: $($runner.Mode)" -ForegroundColor Green

foreach ($name in $scenariosToRun) {
    Invoke-K6Scenario -Name $name -Runner $runner
}

Write-Host ""
Write-Host 'All selected Sentinel rate-limit scenarios completed.' -ForegroundColor Green
Write-Host "Result files are under: $ResultsDir"

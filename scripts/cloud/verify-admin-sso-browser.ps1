#Requires -Version 5.1
[CmdletBinding()]
param(
    [string] $StudentBaseUrl = 'http://127.0.0.1:5173',
    [string] $AdminBaseUrl = 'http://127.0.0.1:5174',
    [string] $IamOrigin = 'http://localhost:8084',
    [string] $GatewayOrigin = 'http://localhost:8080',
    [switch] $Headed
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'admin-sso-browser.helpers.ps1')

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDirectory = Join-Path $projectRoot "target\admin-sso-browser-$runId"
$reportPath = Join-Path $reportDirectory 'report.json'
$evidencePath = Join-Path $reportDirectory 'evidence.json'
$playwrightLog = Join-Path $reportDirectory 'playwright.log'
$report = New-AdminSsoBrowserReport -RunId $runId
$startedProcesses = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$phase = 'Initialize'

New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null

function Resolve-NodeExecutable {
    $command = Get-Command node -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $bundled = 'C:\Users\lyy\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
    if (Test-Path -LiteralPath $bundled) { return $bundled }
    throw 'Node.js was not found. Install Node 20+ or configure PATH.'
}

function Resolve-ChromeExecutable {
    $candidates = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }
    throw 'Google Chrome was not found for Playwright browser acceptance.'
}

function Start-Frontend {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $Directory,
        [Parameter(Mandatory)][int] $Port,
        [Parameter(Mandatory)][string] $Url,
        [Parameter(Mandatory)][string] $NodeExecutable
    )
    if (Test-AdminSsoHttpEndpoint -Uri "$Url/login") { return }

    $vite = Join-Path $Directory 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path -LiteralPath $vite)) {
        throw "$Name dependencies are missing; install frontend dependencies first."
    }
    $stdout = Join-Path $reportDirectory "$Name.stdout.log"
    $stderr = Join-Path $reportDirectory "$Name.stderr.log"
    $process = Start-Process -FilePath $NodeExecutable -ArgumentList @(
        $vite,
        '--host',
        '127.0.0.1',
        '--port',
        $Port.ToString(),
        '--strictPort'
    ) -WorkingDirectory $Directory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $startedProcesses.Add($process)

    $deadline = (Get-Date).AddSeconds(25)
    do {
        if ($process.HasExited) {
            throw "$Name exited before becoming healthy. See $stderr"
        }
        if (Test-AdminSsoHttpEndpoint -Uri "$Url/login") { return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$Name did not become healthy within 25 seconds."
}

try {
    $phase = 'Assert-Credentials'
    if ([string]::IsNullOrWhiteSpace($env:TEST_STUDENT_NUMBER) -or `
            [string]::IsNullOrWhiteSpace($env:TEST_PASSWORD)) {
        throw 'TEST_STUDENT_NUMBER and TEST_PASSWORD must be configured.'
    }

    $phase = 'Assert-Node'
    $node = Resolve-NodeExecutable
    $phase = 'Assert-Chrome'
    [void](Resolve-ChromeExecutable)

    $phase = 'Assert-IAM'
    $report.iamHealthy = Test-AdminSsoHttpEndpoint `
        -Uri "$IamOrigin/.well-known/openid-configuration"
    if (-not $report.iamHealthy) { throw "IAM is unavailable at $IamOrigin" }

    $phase = 'Assert-Gateway'
    $report.gatewayHealthy = Test-AdminSsoHttpEndpoint `
        -Uri "$GatewayOrigin/actuator/health"
    if (-not $report.gatewayHealthy) {
        throw "Gateway is unavailable at $GatewayOrigin"
    }

    $phase = 'Start-Student-Frontend'
    Start-Frontend -Name 'student-frontend' `
        -Directory (Join-Path $projectRoot 'frontend') -Port 5173 `
        -Url $StudentBaseUrl -NodeExecutable $node
    $report.studentFrontendHealthy = Test-AdminSsoHttpEndpoint `
        -Uri "$StudentBaseUrl/login"

    $phase = 'Start-Admin-Frontend'
    Start-Frontend -Name 'admin-frontend' `
        -Directory (Join-Path $projectRoot 'admin-frontend') -Port 5174 `
        -Url $AdminBaseUrl -NodeExecutable $node
    $report.adminFrontendHealthy = Test-AdminSsoHttpEndpoint `
        -Uri "$AdminBaseUrl/login"

    $playwrightCli = Join-Path $projectRoot `
        'admin-frontend\node_modules\@playwright\test\cli.js'
    if (-not (Test-Path -LiteralPath $playwrightCli)) {
        throw 'Playwright dependency is missing in admin-frontend.'
    }

    $phase = 'Browser-Acceptance'
    $env:STUDENT_BASE_URL = $StudentBaseUrl
    $env:ADMIN_BASE_URL = $AdminBaseUrl
    $env:IAM_ORIGIN = $IamOrigin
    $env:SSO_EVIDENCE_PATH = $evidencePath
    $env:PLAYWRIGHT_OUTPUT_DIR = Join-Path $reportDirectory 'playwright-results'
    $env:PLAYWRIGHT_HEADED = if ($Headed) { 'true' } else { 'false' }
    $report.browserTestExecuted = $true

    & $node $playwrightCli test `
        (Join-Path $projectRoot 'admin-frontend\e2e\dual-client-sso.spec.ts') `
        --config (Join-Path $projectRoot 'admin-frontend\playwright.config.ts') `
        *> $playwrightLog
    if ($LASTEXITCODE -ne 0) {
        throw "Playwright SSO acceptance failed. See $playwrightLog"
    }
    if (-not (Test-Path -LiteralPath $evidencePath)) {
        throw 'Playwright completed without writing SSO evidence.'
    }
    $evidence = Get-Content -Raw $evidencePath | ConvertFrom-Json
    foreach ($key in @($report.evidence.Keys)) {
        $report.evidence[$key] = [bool]$evidence.$key
    }
} catch {
    $report.failedInPhase = $phase
    $report.notes += $_.Exception.Message
    $report.environmentBlocked = Test-AdminSsoEnvironmentPhase -Phase $phase
} finally {
    foreach ($process in $startedProcesses) {
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    $resolution = Resolve-AdminSsoBrowserStatus -Report $report
    $report.status = $resolution.status
    $report.failureCategory = $resolution.failureCategory
    $report.completedAt = (Get-Date).ToUniversalTime().ToString('o')
    $report | ConvertTo-Json -Depth 8 | Set-Content -Path $reportPath `
        -Encoding utf8
}

Write-Host "Admin SSO browser report: $reportPath"
Write-Host "Status: $($report.status)"
if ($report.status -ne 'PASSED') {
    Write-Host ($report.notes -join [Environment]::NewLine) -ForegroundColor Yellow
    exit 1
}
Write-Host 'Dual-client browser SSO acceptance PASSED' -ForegroundColor Green

#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'admin-sso-browser.helpers.ps1')

function New-PassedReport {
    $report = New-AdminSsoBrowserReport -RunId 'unit-pass'
    $report.browserTestExecuted = $true
    $report.iamHealthy = $true
    $report.gatewayHealthy = $true
    $report.studentFrontendHealthy = $true
    $report.adminFrontendHealthy = $true
    foreach ($key in @($report.evidence.Keys)) {
        $report.evidence[$key] = $true
    }
    return $report
}

$passed = Resolve-AdminSsoBrowserStatus -Report (New-PassedReport)
if ($passed.status -ne 'PASSED') {
    throw "Expected PASSED, got $($passed.status)"
}

$missingAdminRole = New-PassedReport
$missingAdminRole.evidence.adminRolePresent = $false
$failed = Resolve-AdminSsoBrowserStatus -Report $missingAdminRole
if ($failed.status -ne 'FAILED') {
    throw "Missing ADMIN evidence must fail, got $($failed.status)"
}

$blockedReport = New-AdminSsoBrowserReport -RunId 'unit-blocked'
$blockedReport.environmentBlocked = $true
$blocked = Resolve-AdminSsoBrowserStatus -Report $blockedReport
if ($blocked.status -ne 'BLOCKED') {
    throw "Environment failure must block, got $($blocked.status)"
}

if (-not (Test-AdminSsoEnvironmentPhase -Phase 'Assert-IAM')) {
    throw 'Assert-IAM must be classified as an environment phase'
}
if (Test-AdminSsoEnvironmentPhase -Phase 'Browser-Acceptance') {
    throw 'Browser-Acceptance must be classified as a business phase'
}

$files = @(
    (Join-Path $PSScriptRoot 'admin-sso-browser.helpers.ps1'),
    (Join-Path $PSScriptRoot 'verify-admin-sso-browser.ps1'),
    (Join-Path $PSScriptRoot 'verify-admin-sso-browser.tests.ps1')
)
foreach ($file in $files) {
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $file,
        [ref]$null,
        [ref]$parseErrors
    )
    if ($parseErrors -and $parseErrors.Count -gt 0) {
        throw "Parse failed for $file`: $($parseErrors -join '; ')"
    }
}

Write-Host 'Admin SSO browser verification unit checks PASSED' `
    -ForegroundColor Green

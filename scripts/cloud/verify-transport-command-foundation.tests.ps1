$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'verify-transport-command-foundation.ps1'
. $scriptPath -ImportOnly

$passed = New-TransportCommandReport 'passed'
foreach ($field in $script:RequiredFlags) {
    $passed[$field] = $true
}
$passedStatus = Resolve-TransportCommandStatus $passed
if ($passedStatus.status -ne 'PASSED') {
    throw 'All required flags must resolve to PASSED.'
}

$blocked = New-TransportCommandReport 'blocked'
$blocked.environmentBlocked = $true
$blockedStatus = Resolve-TransportCommandStatus $blocked
if ($blockedStatus.status -ne 'BLOCKED') {
    throw 'Precheck failure must resolve to BLOCKED.'
}

$failed = New-TransportCommandReport 'failed'
$failed.javaAvailable = $true
$failed.dockerAvailable = $true
$failed.infraHealthy = $true
$failedStatus = Resolve-TransportCommandStatus $failed
if ($failedStatus.status -ne 'FAILED') {
    throw 'Business assertion failure must resolve to FAILED.'
}

$noRows = New-TransportCommandReport 'no-rows'
$script:VehicleId = $null
$script:RouteId = $null
Remove-TemporaryTransportData $noRows
if (-not $noRows.temporaryDataCleaned -or $noRows.cleanupEvidence.remainingRows -ne 0) {
    throw 'No-row cleanup must be recorded as successful without requiring Docker.'
}

$parseErrors = $null
[void][Management.Automation.Language.Parser]::ParseFile(
    $scriptPath,
    [ref]$null,
    [ref]$parseErrors
)
if (@($parseErrors).Count -ne 0) {
    throw "Acceptance script has parse errors: $($parseErrors -join '; ')"
}

$bootstrap = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1') -Raw
$initializer = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'initialize-nacos.ps1') -Raw
foreach ($content in @($bootstrap, $initializer)) {
    if ($content -notmatch [regex]::Escape('school-bus-transport-command.yml')) {
        throw 'Every shared Nacos publisher must include Transport Command config.'
    }
}

Write-Host 'verify-transport-command-foundation.tests.ps1 PASSED'

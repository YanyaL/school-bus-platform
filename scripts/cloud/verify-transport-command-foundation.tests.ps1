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

$acceptanceSource = Get-Content -LiteralPath $scriptPath -Raw
if ($acceptanceSource -match '\$DatabaseName\?useSSL') {
    throw 'JDBC URL must use an explicit PowerShell variable boundary before the query string.'
}
foreach ($springDataSourceVariable in @(
    'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_USERNAME',
    'SPRING_DATASOURCE_PASSWORD'
)) {
    if ($acceptanceSource -notmatch [regex]::Escape($springDataSourceVariable)) {
        throw "Acceptance runtime must inject canonical Spring datasource variable: $springDataSourceVariable"
    }
}
if ($acceptanceSource -notmatch '\$Content -is \[byte\[\]\]') {
    throw 'Acceptance HTTP parsing must decode PowerShell byte-array response bodies before JSON parsing.'
}
if ($acceptanceSource -match "departureCampus = 'St Lucia'|arrivalCampus = 'Gatton'") {
    throw 'Acceptance route payload must use values supported by the Campus enum.'
}
if ($acceptanceSource -notmatch '\$remaining = @\(Invoke-MySql') {
    throw 'Cleanup query output must remain an array when MySQL returns a single row.'
}
if ($acceptanceSource -notmatch '\$routeRows = @\(Invoke-MySql') {
    throw 'Route verification output must remain an array when MySQL returns a single row.'
}

Write-Host 'verify-transport-command-foundation.tests.ps1 PASSED'

#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# -ImportOnly loads the report shape, required-flag list, phase classification
# and status resolution without running the live acceptance flow.
. (Join-Path $PSScriptRoot 'verify-booking-service-extraction.ps1') -ImportOnly

function Assert-StatusResolution {
    param(
        [hashtable] $Report,
        [string] $ExpectedStatus,
        [string] $ExpectedCategory,
        [string] $Because
    )

    $resolved = Resolve-BookingExtractionStatus -Report $Report
    if ($resolved.status -ne $ExpectedStatus) {
        throw ("{0}: expected status {1}, got {2}" -f `
            $Because, $ExpectedStatus, $resolved.status)
    }
    if ($resolved.failureCategory -ne $ExpectedCategory) {
        throw ("{0}: expected category {1}, got {2}" -f `
            $Because, $ExpectedCategory, $resolved.failureCategory)
    }
}

function New-FullyPassedReport {
    $report = New-BookingExtractionReport -RunId 'unit-pass'
    foreach ($flag in $script:BookingRequiredFlags) {
        $report[$flag] = $true
    }
    $report.coreBookingOwner = 'disabled'
    $report.bookingServiceBookingOwner = 'booking'
    return $report
}

# --- PASSED requires every single required flag ---------------------------

Assert-StatusResolution -Report (New-FullyPassedReport) `
    -ExpectedStatus 'PASSED' -ExpectedCategory 'verification_succeeded' `
    -Because 'All required flags true'

if ($script:BookingRequiredFlags.Count -lt 12) {
    throw 'The required-flag list unexpectedly shrank.'
}

foreach ($flag in $script:BookingRequiredFlags) {
    $report = New-FullyPassedReport
    $report[$flag] = $false
    $resolved = Resolve-BookingExtractionStatus -Report $report
    if ($resolved.status -eq 'PASSED') {
        throw "Report with $flag=false must never resolve to PASSED."
    }
}

# A brand new report has nothing verified at all.
Assert-StatusResolution -Report (New-BookingExtractionReport -RunId 'unit-new') `
    -ExpectedStatus 'FAILED' -ExpectedCategory 'verification_not_executed' `
    -Because 'Nothing executed'

# --- BLOCKED is reserved for environment pre-check phases -----------------

foreach ($phase in @(
        'Assert-Java21',
        'Assert-Docker',
        'Assert-InfraHealthy',
        'Assert-PortsFree',
        'RabbitMQ-port-check',
        'RabbitMQ-management-port-check'
    )) {
    if (-not (Test-BookingEnvironmentBlockedPhase -Phase $phase)) {
        throw "Phase $phase must be classified as environment blocked."
    }
}

$blockedReport = New-BookingExtractionReport -RunId 'unit-blocked'
$blockedReport.javaAvailable = $true
$blockedReport.dockerAvailable = $true
$blockedReport.failedInPhase = 'Assert-InfraHealthy'
$blockedReport.environmentBlocked = $true
Assert-StatusResolution -Report $blockedReport `
    -ExpectedStatus 'BLOCKED' -ExpectedCategory 'environment_blocked' `
    -Because 'Infrastructure pre-check failed'

# Environment blocked wins even when some business progress was made.
$blockedWithProgress = New-BookingExtractionReport -RunId 'unit-blocked-partial'
$blockedWithProgress.environmentBlocked = $true
$blockedWithProgress.createBookingVerified = $true
Assert-StatusResolution -Report $blockedWithProgress `
    -ExpectedStatus 'BLOCKED' -ExpectedCategory 'environment_blocked' `
    -Because 'Environment blocked outranks partial progress'

# --- Business phases are never BLOCKED ------------------------------------

foreach ($phase in @(
        'nacos-config',
        'maven-build',
        'service-startup',
        'discovery-check',
        'ownership-check',
        'seed-data',
        'auth-setup',
        'routing-check',
        'booking-e2e',
        'unauthenticated-check',
        'payment-succeeded',
        'booking-expiration',
        'trip-cancellation'
    )) {
    if (Test-BookingEnvironmentBlockedPhase -Phase $phase) {
        throw "Phase $phase must NOT be classified as environment blocked."
    }
}
if (Test-BookingEnvironmentBlockedPhase -Phase '') {
    throw 'An empty phase must not be classified as environment blocked.'
}

# A failure inside a business phase after everything ran is a business failure.
$businessFailure = New-FullyPassedReport
$businessFailure.failedInPhase = 'trip-cancellation'
$businessFailure.temporaryDataCleaned = $false
Assert-StatusResolution -Report $businessFailure `
    -ExpectedStatus 'FAILED' -ExpectedCategory 'business_failure' `
    -Because 'Cleanup verification failed after all business checks ran'

# Ownership regression with every business check green is a business failure.
$ownershipFailure = New-FullyPassedReport
$ownershipFailure.failedInPhase = 'ownership-check'
$ownershipFailure.coreBookingEmbeddedDisabled = $false
$ownershipFailure.coreBookingOwner = 'core'
Assert-StatusResolution -Report $ownershipFailure `
    -ExpectedStatus 'FAILED' -ExpectedCategory 'business_failure' `
    -Because 'Core still owns Booking'

# Stopping halfway through the business phases is PARTIAL, not BLOCKED.
$partialReport = New-FullyPassedReport
$partialReport.failedInPhase = 'booking-expiration'
$partialReport.bookingExpirationVerified = $false
$partialReport.tripCancellationSettlementVerified = $false
Assert-StatusResolution -Report $partialReport `
    -ExpectedStatus 'PARTIAL' -ExpectedCategory 'verification_not_executed' `
    -Because 'Expiration and settlement never ran'

# A routing failure before any business verification is FAILED, not BLOCKED.
$routingFailure = New-BookingExtractionReport -RunId 'unit-routing'
$routingFailure.javaAvailable = $true
$routingFailure.dockerAvailable = $true
$routingFailure.infraHealthy = $true
$routingFailure.nacosBookingHealthy = $true
$routingFailure.failedInPhase = 'routing-check'
Assert-StatusResolution -Report $routingFailure `
    -ExpectedStatus 'FAILED' -ExpectedCategory 'verification_not_executed' `
    -Because 'Routing check failed before any booking was created'

# --- Report shape ---------------------------------------------------------

$shapeReport = New-BookingExtractionReport -RunId 'unit-shape'
foreach ($flag in $script:BookingRequiredFlags) {
    if (-not $shapeReport.Contains($flag)) {
        throw "Report is missing required flag $flag."
    }
    if ($shapeReport[$flag] -ne $false) {
        throw "Required flag $flag must default to false."
    }
}
if ($shapeReport.status -ne 'FAILED') {
    throw 'A fresh report must default to FAILED.'
}
if ($shapeReport.environmentBlocked -ne $false) {
    throw 'A fresh report must not claim environmentBlocked.'
}

# --- RabbitMQ acceptance topology is isolated per run --------------------

$topology = New-BookingVerifyTopology -RunId 'unit-run'
if (@($topology.queues).Count -ne 9) {
    throw 'Booking verification topology must contain exactly nine queues.'
}
if (@($topology.exchanges).Count -ne 7) {
    throw 'Booking verification topology must contain exactly seven exchanges.'
}
foreach ($name in @($topology.queues) + @($topology.exchanges)) {
    if ($name -notlike 'verify.booking.unit-run.*') {
        throw "RabbitMQ verification resource is not run-scoped: $name"
    }
    if ($name -like 'schoolbus.booking.*') {
        throw "RabbitMQ verification must not use a shared business name: $name"
    }
}
if ($topology.expirationProcessingQueue -eq
        'schoolbus.booking.expiration') {
    throw 'Expiration verification must not target the shared processing queue.'
}

# --- Parse validation -----------------------------------------------------

$files = @(
    (Join-Path $PSScriptRoot 'verify-booking-service-extraction.ps1'),
    (Join-Path $PSScriptRoot 'verify-booking-service-extraction.tests.ps1')
)
foreach ($file in $files) {
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $file, [ref]$null, [ref]$parseErrors
    )
    if ($parseErrors -and $parseErrors.Count -gt 0) {
        throw ("Parse failed for {0}: {1}" -f $file, `
            ($parseErrors | ForEach-Object { $_.ToString() } | Out-String))
    }
}

Write-Host 'Booking extraction script unit checks PASSED' -ForegroundColor Green

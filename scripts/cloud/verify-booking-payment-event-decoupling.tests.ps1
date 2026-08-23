#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'verify-booking-payment-event-decoupling.ps1') -ImportOnly

function Assert-StatusResolution {
    param(
        [hashtable] $Report,
        [string] $ExpectedStatus,
        [string] $ExpectedCategory,
        [string] $Because
    )
    $resolved = Resolve-BookingPaymentDecouplingStatus -Report $Report
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
    $report = New-BookingPaymentDecouplingReport -RunId 'unit-pass'
    foreach ($flag in $script:DecouplingRequiredFlags) {
        $report[$flag] = $true
    }
    return $report
}

Assert-StatusResolution -Report (New-FullyPassedReport) `
    -ExpectedStatus 'PASSED' -ExpectedCategory 'verification_succeeded' `
    -Because 'All required flags true'

if ($script:DecouplingRequiredFlags.Count -lt 10) {
    throw 'The required-flag list unexpectedly shrank.'
}

$newReport = New-BookingPaymentDecouplingReport -RunId 'unit-topology'
if ($null -ne $newReport.temporaryTopologyCleaned) {
    throw 'Fixed application topology must be reported as not applicable.'
}

foreach ($flag in $script:DecouplingRequiredFlags) {
    $report = New-FullyPassedReport
    $report[$flag] = $false
    $resolved = Resolve-BookingPaymentDecouplingStatus -Report $report
    if ($resolved.status -eq 'PASSED') {
        throw "Report with $flag=false must never resolve to PASSED."
    }
}

Assert-StatusResolution `
    -Report (New-BookingPaymentDecouplingReport -RunId 'unit-new') `
    -ExpectedStatus 'FAILED' -ExpectedCategory 'verification_not_executed' `
    -Because 'Nothing executed'

foreach ($phase in $script:DecouplingEnvironmentPhases) {
    if (-not (Test-DecouplingEnvironmentBlockedPhase -Phase $phase)) {
        throw "Phase $phase must be classified as environment blocked."
    }
}

$blockedReport = New-BookingPaymentDecouplingReport -RunId 'unit-blocked'
$blockedReport.javaAvailable = $true
$blockedReport.dockerAvailable = $true
$blockedReport.failedInPhase = 'Assert-InfraHealthy'
$blockedReport.environmentBlocked = $true
Assert-StatusResolution -Report $blockedReport `
    -ExpectedStatus 'BLOCKED' -ExpectedCategory 'environment_blocked' `
    -Because 'Infrastructure pre-check failed'

$partial = New-BookingPaymentDecouplingReport -RunId 'unit-partial'
$partial.javaAvailable = $true
$partial.dockerAvailable = $true
$partial.infraHealthy = $true
$partial.portsFree = $true
$partial.servicesStarted = $true
$partial.paymentEventMode = $true
$partial.bookingHasNoPaymentRecordSql = $true
$partial.bookingPaidViaEvent = $true
$partial.failedInPhase = 'business-flow'
Assert-StatusResolution -Report $partial `
    -ExpectedStatus 'PARTIAL' -ExpectedCategory 'partial_verification' `
    -Because 'Business progress without full success'

Write-Host 'verify-booking-payment-event-decoupling.tests.ps1 PASSED'

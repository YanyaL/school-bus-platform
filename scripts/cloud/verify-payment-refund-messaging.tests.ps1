#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'payment-refund-messaging.helpers.ps1')

function Assert-StatusResolution {
    param(
        [hashtable] $Report,
        [string] $ExpectedStatus,
        [string] $ExpectedCategory
    )

    $resolved = Resolve-PaymentRefundMessagingStatus -Report $Report
    if ($resolved.status -ne $ExpectedStatus) {
        throw ("Expected status {0}, got {1}" -f $ExpectedStatus, $resolved.status)
    }
    if ($resolved.failureCategory -ne $ExpectedCategory) {
        throw ("Expected category {0}, got {1}" -f `
            $ExpectedCategory, $resolved.failureCategory)
    }
}

function New-FullyPassedReport {
    $r = New-PaymentRefundMessagingReport -RunId 'unit-pass'
    $r.nacosPaymentHealthy = $true
    $r.outboxPublished = $true
    $r.publisherConfirmed = $true
    $r.refundConsumed = $true
    $r.idempotencyVerified = $true
    $r.retryVerified = $true
    $r.dlqVerified = $true
    $r.coreRelayDisabled = $true
    $r.coreConsumerDisabled = $true
    $r.temporaryDataCleaned = $true
    $r.temporaryTopologyCleaned = $true
    return $r
}

# --- Status resolution tests ---

# Strict PASSED requires every gate including temporaryTopologyCleaned.
$passedReport = New-FullyPassedReport
Assert-StatusResolution -Report $passedReport `
    -ExpectedStatus 'PASSED' -ExpectedCategory 'verification_succeeded'

# temporaryTopologyCleaned=false must never be PASSED.
$topologyNotCleanedReport = New-FullyPassedReport
$topologyNotCleanedReport.temporaryTopologyCleaned = $false
Assert-StatusResolution -Report $topologyNotCleanedReport `
    -ExpectedStatus 'FAILED' -ExpectedCategory 'business_failure'

# retryVerified=false must never be PASSED.
$partialReport = New-PaymentRefundMessagingReport -RunId 'unit-partial'
$partialReport.nacosPaymentHealthy = $true
$partialReport.outboxPublished = $true
$partialReport.publisherConfirmed = $true
$partialReport.refundConsumed = $true
$partialReport.idempotencyVerified = $true
$partialReport.coreRelayDisabled = $true
$partialReport.coreConsumerDisabled = $true
$partialReport.temporaryDataCleaned = $true
$partialReport.temporaryTopologyCleaned = $true
Assert-StatusResolution -Report $partialReport `
    -ExpectedStatus 'PARTIAL' -ExpectedCategory 'verification_not_executed'

# Environment blocked wins over partial business progress.
$blockedReport = New-PaymentRefundMessagingReport -RunId 'unit-blocked'
$blockedReport.environmentBlocked = $true
$blockedReport.outboxPublished = $true
Assert-StatusResolution -Report $blockedReport `
    -ExpectedStatus 'BLOCKED' -ExpectedCategory 'environment_blocked'

# Business failure when verification ran but refund did not complete.
$failedReport = New-PaymentRefundMessagingReport -RunId 'unit-failed'
$failedReport.nacosPaymentHealthy = $true
$failedReport.outboxPublished = $true
$failedReport.publisherConfirmed = $true
$failedReport.retryVerified = $true
$failedReport.dlqVerified = $true
$failedReport.coreRelayDisabled = $true
$failedReport.coreConsumerDisabled = $true
$failedReport.temporaryDataCleaned = $true
$failedReport.temporaryTopologyCleaned = $true
Assert-StatusResolution -Report $failedReport `
    -ExpectedStatus 'FAILED' -ExpectedCategory 'business_failure'

# --- BLOCKED / FAILED classification tests (phase-based) ---

# Docker API unavailable is an environment check phase -> BLOCKED.
if (-not (Test-EnvironmentBlockedPhase -Phase 'Assert-Docker')) {
    throw 'Assert-Docker phase should be classified as environment blocked.'
}

# RabbitMQ port check is an environment check phase -> BLOCKED.
if (-not (Test-EnvironmentBlockedPhase -Phase 'RabbitMQ-port-check')) {
    throw 'RabbitMQ-port-check phase should be classified as environment blocked.'
}

# happy-path is NOT an environment check phase -> FAILED (not BLOCKED).
if (Test-EnvironmentBlockedPhase -Phase 'happy-path') {
    throw 'happy-path phase should NOT be classified as environment blocked.'
}

# retry-verification is NOT an environment check phase -> FAILED (not BLOCKED).
if (Test-EnvironmentBlockedPhase -Phase 'retry-verification') {
    throw 'retry-verification phase should NOT be classified as environment blocked.'
}

# dlq-verification is NOT an environment check phase -> FAILED (not BLOCKED).
if (Test-EnvironmentBlockedPhase -Phase 'dlq-verification') {
    throw 'dlq-verification phase should NOT be classified as environment blocked.'
}

# Simulate: "RabbitMQ did not route message" in happy-path -> FAILED not BLOCKED.
$routeFailReport = New-PaymentRefundMessagingReport -RunId 'unit-route-fail'
$routeFailReport.nacosPaymentHealthy = $true
$routeFailReport.outboxPublished = $true
$routeFailReport.failedInPhase = 'happy-path'
Assert-StatusResolution -Report $routeFailReport `
    -ExpectedStatus 'PARTIAL' -ExpectedCategory 'verification_not_executed'

# Simulate: "MySQL assertion expected REFUNDED" in happy-path -> not BLOCKED.
$mysqlAssertReport = New-PaymentRefundMessagingReport -RunId 'unit-mysql-assert'
$mysqlAssertReport.nacosPaymentHealthy = $true
$mysqlAssertReport.outboxPublished = $true
$mysqlAssertReport.publisherConfirmed = $true
$mysqlAssertReport.refundConsumed = $true
$mysqlAssertReport.failedInPhase = 'happy-path'
Assert-StatusResolution -Report $mysqlAssertReport `
    -ExpectedStatus 'PARTIAL' -ExpectedCategory 'verification_not_executed'

# --- Stop-ServiceByPortSafe tests ---

$script:MockListeningPidsByPort = @{}

function Get-ListeningPids([int] $Port) {
    if ($script:MockListeningPidsByPort.ContainsKey($Port)) {
        return @($script:MockListeningPidsByPort[$Port])
    }
    return @()
}

function Test-Stop-ServiceByPortSafe {
    param(
        [int] $Port,
        [string] $Label,
        [int[]] $MockPids = @()
    )

    $script:MockListeningPidsByPort[$Port] = @($MockPids)
    $note = Stop-ServiceByPortSafe -Port $Port -Label $Label
    if ($MockPids.Count -eq 0 -and -not [string]::IsNullOrWhiteSpace($note)) {
        throw "Stop-ServiceByPortSafe should be silent when port $Port is free."
    }
    if ($MockPids.Count -gt 0 -and [string]::IsNullOrWhiteSpace($note)) {
        throw "Expected cleanup note when listeners exist on port $Port."
    }
}

Test-Stop-ServiceByPortSafe -Port 59991 -Label 'never-started'
Test-Stop-ServiceByPortSafe -Port 59992 -Label 'already-released'
Test-Stop-ServiceByPortSafe -Port 59993 -Label 'partial-busy' -MockPids @(4242)
Test-Stop-ServiceByPortSafe -Port 59994 -Label 'partial-free'

# --- Cleanup failure tests ---

$cleanupNotes = @(Invoke-VerificationCleanup -Steps @(
    { throw 'Stop-ServiceByPort simulated failure' }
))
if ($cleanupNotes.Count -ne 1 -or `
        $cleanupNotes[0] -notmatch 'Stop-ServiceByPort simulated failure') {
    throw 'Cleanup failure was not captured in notes.'
}

# --- RabbitMQ Management API HTTP compatibility tests ---

$script:RabbitApiFailureStatus = $null
$script:RabbitApiConnectionFailure = $false

function Invoke-RabbitMqApi {
    if ($script:RabbitApiConnectionFailure) {
        throw [System.Net.Sockets.SocketException]::new(
            [System.Net.Sockets.SocketError]::ConnectionRefused
        )
    }
    if ($null -ne $script:RabbitApiFailureStatus) {
        $exception = [System.Exception]::new(
            "Simulated HTTP $($script:RabbitApiFailureStatus)"
        )
        $response = [pscustomobject]@{
            StatusCode = [System.Net.HttpStatusCode]$script:RabbitApiFailureStatus
        }
        $exception | Add-Member -MemberType NoteProperty `
            -Name Response -Value $response
        throw $exception
    }
    return [pscustomobject]@{ name = 'still-present' }
}

function Assert-ThrowsRabbitResourceOperation {
    param(
        [scriptblock] $Operation,
        [string] $Description
    )

    $thrown = $false
    try {
        & $Operation
    } catch {
        $thrown = $true
    }
    if (-not $thrown) {
        throw "Expected operation to throw: $Description"
    }
}

# HTTP 404 is the only idempotent success for deletion and absence checks.
$script:RabbitApiFailureStatus = 404
Remove-RabbitMqResource -ResourceType 'queue' -ResourceName 'already-gone'
if (-not (Test-RabbitMqResourceAbsent `
        -ResourceType 'exchange' -ResourceName 'already-gone')) {
    throw 'HTTP 404 should mean the RabbitMQ resource is absent.'
}

foreach ($failureStatus in @(401, 500)) {
    $script:RabbitApiFailureStatus = $failureStatus
    Assert-ThrowsRabbitResourceOperation -Description "DELETE HTTP $failureStatus" `
        -Operation {
            Remove-RabbitMqResource -ResourceType 'queue' `
                -ResourceName "delete-$failureStatus"
        }
    Assert-ThrowsRabbitResourceOperation -Description "GET HTTP $failureStatus" `
        -Operation {
            Test-RabbitMqResourceAbsent -ResourceType 'exchange' `
                -ResourceName "get-$failureStatus" | Out-Null
        }
}

$script:RabbitApiFailureStatus = $null
$script:RabbitApiConnectionFailure = $true
Assert-ThrowsRabbitResourceOperation -Description 'DELETE connection failure' `
    -Operation {
        Remove-RabbitMqResource -ResourceType 'queue' `
            -ResourceName 'connection-failure'
    }
Assert-ThrowsRabbitResourceOperation -Description 'GET connection failure' `
    -Operation {
        Test-RabbitMqResourceAbsent -ResourceType 'exchange' `
            -ResourceName 'connection-failure' | Out-Null
    }
$script:RabbitApiConnectionFailure = $false

# --- ParseFile validation ---

$files = @(
    (Join-Path $PSScriptRoot 'payment-refund-messaging.helpers.ps1'),
    (Join-Path $PSScriptRoot 'verify-payment-refund-messaging.ps1'),
    (Join-Path $PSScriptRoot 'verify-payment-refund-messaging.tests.ps1')
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

Write-Host 'Payment refund messaging script unit checks PASSED' -ForegroundColor Green

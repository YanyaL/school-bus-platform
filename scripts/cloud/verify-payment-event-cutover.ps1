param(
    [string] $NacosBaseUrl = 'http://127.0.0.1:8848',
    [string] $AdminPassword = 'nacos',
    [string] $MysqlContainerName = 'school-bus-mysql',
    [int] $CorePort = 8081,
    [int] $PaymentPort = 8085,
    [int] $StartupTimeoutSeconds = 120,
    [switch] $SkipBuild
)

<#
.SYNOPSIS
  Verifies the Payment EVENT cutover against real MySQL and RabbitMQ.

.DESCRIPTION
  Proves that Payment records a successful callback plus an Outbox event
  without directly changing Booking, that Core applies PaymentSucceeded once,
  and that a rejected payment is compensated without corrupting Booking.
  Every run uses isolated RabbitMQ resources and temporary database rows.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:Scenarios = [System.Collections.Generic.List[object]]::new()
$script:Topology = $null

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1')
. (Join-Path $PSScriptRoot 'payment-refund-messaging.helpers.ps1')

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $projectRoot "target\payment-event-cutover-$runId"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Invoke-Mysql([string] $Sql, [switch] $ReturnRows) {
    if ($ReturnRows) {
        $rows = @(docker exec $MysqlContainerName mysql -uroot -proot -N `
            -e $Sql 2>$null)
        if ($LASTEXITCODE -ne 0) {
            throw 'MySQL query failed.'
        }
        return $rows
    }
    $output = @(docker exec $MysqlContainerName mysql -uroot -proot `
        -e $Sql 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join ' ')"
    }
}

function Invoke-HttpCapture {
    param(
        [string] $Uri,
        [string] $Body,
        [hashtable] $Headers
    )
    $response = Invoke-WebRequest -Uri $Uri -Method POST `
        -Headers $Headers -ContentType 'application/json' -Body $Body `
        -SkipHttpErrorCheck -TimeoutSec 15
    $parsed = if ([string]::IsNullOrWhiteSpace($response.Content)) {
        $null
    } else {
        $response.Content | ConvertFrom-Json
    }
    return [pscustomobject]@{
        status = [int] $response.StatusCode
        body = $parsed
    }
}

function New-PaymentSignature([string] $Body, [string] $Secret) {
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [Text.Encoding]::UTF8.GetBytes($Secret)
    )
    try {
        $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Body))
        return 'sha256=' + [Convert]::ToHexString($hash).ToLowerInvariant()
    } finally {
        $hmac.Dispose()
    }
}

function Wait-Condition {
    param(
        [scriptblock] $Condition,
        [int] $TimeoutSeconds,
        [string] $Label
    )
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $value = & $Condition
        if ($null -ne $value -and $value -ne $false) {
            return $value
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Label."
}

function New-IsolatedTopology([string] $Suffix) {
    return [ordered]@{
        exchange = "schoolbus.payment.events.$Suffix"
        refundQueue = "schoolbus.payment.refund.$Suffix"
        refundDlq = "schoolbus.payment.refund.dlq.$Suffix"
        refundRetryExchange = "schoolbus.payment.refund.retry.exchange.$Suffix"
        refundRetryQueue = "schoolbus.payment.refund.retry.$Suffix"
        dlx = "schoolbus.payment.dlx.$Suffix"
        succeededQueue = "schoolbus.booking.payment-succeeded.$Suffix"
        succeededDlq = "schoolbus.booking.payment-succeeded.dlq.$Suffix"
        succeededRetryExchange = "schoolbus.booking.payment-succeeded.retry.exchange.$Suffix"
        succeededRetryQueue = "schoolbus.booking.payment-succeeded.retry.$Suffix"
    }
}

function Add-TopologyEnvironment([hashtable] $Environment) {
    $Environment['PAYMENT_EVENTS_EXCHANGE'] = $script:Topology.exchange
    $Environment['PAYMENT_REFUND_QUEUE'] = $script:Topology.refundQueue
    $Environment['PAYMENT_REFUND_DLQ'] = $script:Topology.refundDlq
    $Environment['PAYMENT_REFUND_RETRY_EXCHANGE'] = $script:Topology.refundRetryExchange
    $Environment['PAYMENT_REFUND_RETRY_QUEUE'] = $script:Topology.refundRetryQueue
    $Environment['PAYMENT_DLX'] = $script:Topology.dlx
    $Environment['PAYMENT_SUCCEEDED_QUEUE'] = $script:Topology.succeededQueue
    $Environment['PAYMENT_SUCCEEDED_DLQ'] = $script:Topology.succeededDlq
    $Environment['PAYMENT_SUCCEEDED_RETRY_EXCHANGE'] = $script:Topology.succeededRetryExchange
    $Environment['PAYMENT_SUCCEEDED_RETRY_QUEUE'] = $script:Topology.succeededRetryQueue
    $Environment['PAYMENT_REFUND_RETRY_DELAY'] = 'PT5S'
    $Environment['PAYMENT_SUCCEEDED_RETRY_DELAY'] = 'PT5S'
}

function New-Scenario([decimal] $BookingAmount, [decimal] $PaidAmount) {
    $orderNo = [guid]::NewGuid().ToString()
    $paymentNo = [guid]::NewGuid().ToString()
    $requestNo = 'event-cutover-' + [guid]::NewGuid().ToString('N')
    $bookingRequestNo = 'event-booking-' + [guid]::NewGuid().ToString('N')
    $seatNumber = (Invoke-Mysql -ReturnRows -Sql @"
SELECT s.seat_number
  FROM school_bus_platform.transport_trip_seat s
 WHERE s.trip_id=9001 AND s.status='AVAILABLE'
   AND NOT EXISTS (
       SELECT 1 FROM school_bus_platform.booking_order b
        WHERE b.trip_id=s.trip_id AND b.seat_number=s.seat_number
          AND b.status IN ('PENDING_PAYMENT','PAID','REFUND_PENDING')
   )
 ORDER BY s.seat_number LIMIT 1;
"@ | Select-Object -First 1).Trim()
    if ([string]::IsNullOrWhiteSpace($seatNumber)) {
        throw 'Demo trip 9001 needs another available seat.'
    }
    $tripNo = (Invoke-Mysql -ReturnRows -Sql `
        'SELECT trip_no FROM school_bus_platform.transport_trip WHERE id=9001 LIMIT 1' `
        | Select-Object -First 1).Trim()
    if ([string]::IsNullOrWhiteSpace($tripNo)) {
        throw 'Demo trip 9001 is missing.'
    }
    $now = [DateTimeOffset]::UtcNow
    $created = $now.ToString('yyyy-MM-dd HH:mm:ss.fff')
    $expires = $now.AddMinutes(10).ToString('yyyy-MM-dd HH:mm:ss.fff')
    $userId = 987654321001 + $script:Scenarios.Count
    Invoke-Mysql -Sql @"
USE school_bus_platform;
INSERT INTO booking_order(
    order_no, request_no, user_id, trip_id, trip_no, seat_number,
    price_snapshot, status, expires_at, version, created_at, updated_at
) VALUES (
    '$orderNo', '$bookingRequestNo', $userId, 9001, '$tripNo',
    '$seatNumber', $BookingAmount, 'PENDING_PAYMENT', '$expires',
    0, '$created', '$created'
);
UPDATE transport_trip_seat
   SET status='LOCKED', locked_by_order_no='$orderNo',
       locked_by_user_id=$userId, lock_expires_at='$expires',
       version=version+1, updated_at='$created'
 WHERE trip_id=9001 AND seat_number='$seatNumber'
   AND status='AVAILABLE';
"@
    $scenario = [pscustomobject]@{
        orderNo = $orderNo
        paymentNo = $paymentNo
        requestNo = $requestNo
        seatNumber = $seatNumber
        bookingAmount = $BookingAmount
        paidAmount = $PaidAmount
        paidAt = $now
    }
    $script:Scenarios.Add($scenario)
    return $scenario
}

function Invoke-Callback([object] $Scenario, [string] $Secret) {
    $payload = [ordered]@{
        requestNumber = $Scenario.requestNo
        paymentNumber = $Scenario.paymentNo
        bookingNumber = $Scenario.orderNo
        amount = $Scenario.paidAmount
        paidAt = $Scenario.paidAt.ToString('o')
    }
    $body = $payload | ConvertTo-Json -Compress
    $signature = New-PaymentSignature -Body $body -Secret $Secret
    return Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$PaymentPort/api/v1/payments/callback" `
        -Body $body -Headers @{ 'X-Payment-Signature' = $signature }
}

function Get-State([object] $Scenario) {
    $rows = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT CONCAT(status, '|', version) FROM school_bus_platform.payment_record
 WHERE payment_no='$($Scenario.paymentNo)';
SELECT CONCAT(status, '|', version) FROM school_bus_platform.booking_order
 WHERE order_no='$($Scenario.orderNo)';
SELECT CONCAT(status, '|', version) FROM school_bus_platform.transport_trip_seat
 WHERE trip_id=9001 AND seat_number='$($Scenario.seatNumber)';
"@)
    if ($rows.Count -lt 3) { return $null }
    return [pscustomobject]@{
        payment = [string] $rows[0]
        booking = [string] $rows[1]
        seat = [string] $rows[2]
    }
}

function Remove-TemporaryData {
    foreach ($scenario in $script:Scenarios) {
        Invoke-Mysql -Sql @"
USE school_bus_platform;
DELETE ec FROM event_consumed ec
 JOIN event_outbox eo ON eo.event_id=ec.event_id
 WHERE eo.aggregate_id='$($scenario.paymentNo)';
DELETE FROM event_outbox WHERE aggregate_id='$($scenario.paymentNo)';
DELETE FROM payment_record WHERE payment_no='$($scenario.paymentNo)';
DELETE FROM booking_order WHERE order_no='$($scenario.orderNo)';
UPDATE transport_trip_seat
   SET status='AVAILABLE', locked_by_order_no=NULL,
       locked_by_user_id=NULL, lock_expires_at=NULL,
       version=version+1, updated_at=UTC_TIMESTAMP(3)
 WHERE trip_id=9001 AND seat_number='$($scenario.seatNumber)'
   AND locked_by_order_no='$($scenario.orderNo)';
"@
    }
}

function Remove-IsolatedTopology {
    if ($null -eq $script:Topology) { return }
    foreach ($queue in @(
        $script:Topology.refundQueue,
        $script:Topology.refundDlq,
        $script:Topology.refundRetryQueue,
        $script:Topology.succeededQueue,
        $script:Topology.succeededDlq,
        $script:Topology.succeededRetryQueue
    )) {
        Remove-RabbitMqResource -ResourceType queue -Name $queue
    }
    foreach ($exchange in @(
        $script:Topology.exchange,
        $script:Topology.refundRetryExchange,
        $script:Topology.succeededRetryExchange,
        $script:Topology.dlx
    )) {
        Remove-RabbitMqResource -ResourceType exchange -Name $exchange
    }
}

$report = [ordered]@{
    runId = $runId
    status = 'FAILED'
    eventModeNoDirectBookingWrite = $false
    paymentSucceededApplied = $false
    duplicateEventIdempotent = $false
    compensationRefunded = $false
    compensationPreservedBooking = $false
    normalEvidence = @{}
    idempotencyEvidence = @{}
    compensationEvidence = @{}
    temporaryDataCleaned = $false
    temporaryTopologyCleaned = $false
    notes = @()
}
$failure = $null

try {
    Assert-Java21
    Assert-Docker
    Assert-InfraHealthy -NacosBaseUrl $NacosBaseUrl `
        -MysqlContainerName $MysqlContainerName
    Assert-PortsFree -GatewayPort 8080 -CorePort $CorePort `
        -QueryPorts @() -AdditionalPorts @($PaymentPort)
    if (-not (Test-TcpPort -HostName '127.0.0.1' -Port 5672) -or
        -not (Test-TcpPort -HostName '127.0.0.1' -Port 15672)) {
        throw 'RabbitMQ AMQP and Management ports must be available.'
    }

    $keys = Ensure-JwtKeys -ProjectRoot $projectRoot
    $null = Publish-NacosConfigs -ProjectRoot $projectRoot `
        -NacosBaseUrl $NacosBaseUrl -AdminPassword $AdminPassword
    if (-not $SkipBuild) {
        Invoke-MavenPackage -WorkingDirectory $projectRoot -Label 'core'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\payment-service') `
            -Label 'payment'
    }
    $coreJar = Find-BootJar (Join-Path $projectRoot 'target') `
        'school-bus-platform'
    $paymentJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\payment-service\target') `
        'school-bus-payment'

    $script:Topology = New-IsolatedTopology "verify-$runId"
    $commonEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
    }
    Add-TopologyEnvironment $commonEnv

    $coreEnv = $commonEnv.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'local,cloud'
    $coreEnv['CORE_SERVER_PORT'] = "$CorePort"
    $coreEnv['SERVER_PORT'] = "$CorePort"
    $coreEnv['JWT_PUBLIC_KEY_LOCATION'] = $keys.Public
    $coreEnv['JWT_ISSUER'] = 'https://school-bus.local'
    $coreEnv['JWT_AUDIENCE'] = 'school-bus-api'
    $coreEnv['OUTBOX_RELAY_ENABLED'] = 'false'
    Start-TrackedJava $coreJar $coreEnv `
        (Join-Path $logDir 'core.log') 'core' $logDir

    $secret = 'payment-event-cutover-secret-2026'
    $paymentEnv = $commonEnv.Clone()
    $paymentEnv['PAYMENT_SERVER_PORT'] = "$PaymentPort"
    $paymentEnv['SERVER_PORT'] = "$PaymentPort"
    $paymentEnv['PAYMENT_CALLBACK_SECRET'] = $secret
    $paymentEnv['SCHOOL_BUS_PAYMENT_WORKER_ID'] = '2'
    $paymentEnv['PAYMENT_BOOKING_WRITE_MODE'] = 'EVENT'
    $paymentEnv['OUTBOX_RELAY_ENABLED'] = 'false'
    Start-TrackedJava $paymentJar $paymentEnv `
        (Join-Path $logDir 'payment-relay-off.log') 'payment' $logDir

    Wait-HttpUp "http://127.0.0.1:$CorePort/actuator/health" `
        $StartupTimeoutSeconds 'Core'
    Wait-HttpUp "http://127.0.0.1:$PaymentPort/actuator/health" `
        $StartupTimeoutSeconds 'Payment'

    $normal = New-Scenario -BookingAmount 5.50 -PaidAmount 5.50
    $normalResponse = Invoke-Callback -Scenario $normal -Secret $secret
    if ($normalResponse.status -ne 200) {
        throw "EVENT callback returned HTTP $($normalResponse.status)."
    }
    $beforeRelay = Get-State $normal
    $outboxBefore = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT CONCAT(event_id, '|', status, '|', JSON_UNQUOTE(payload))
  FROM school_bus_platform.event_outbox
 WHERE aggregate_id='$($normal.paymentNo)' AND event_type='PaymentSucceeded';
"@ | Select-Object -First 1)
    if ($beforeRelay.payment -notmatch '^SUCCEEDED\|' -or
        $beforeRelay.booking -notmatch '^PENDING_PAYMENT\|' -or
        $beforeRelay.seat -notmatch '^LOCKED\|' -or
        $outboxBefore.Count -ne 1) {
        throw 'EVENT mode changed Booking directly or did not create Outbox.'
    }
    $report.eventModeNoDirectBookingWrite = $true
    $eventParts = ([string] $outboxBefore[0]).Split('|', 3)
    $eventId = $eventParts[0]
    $eventPayload = $eventParts[2]

    Stop-ServiceByPort -Port $PaymentPort -Label 'payment relay-off'
    $paymentEnv['OUTBOX_RELAY_ENABLED'] = 'true'
    $paymentEnv['OUTBOX_RELAY_INITIAL_DELAY_MS'] = '500'
    $paymentEnv['OUTBOX_RELAY_FIXED_DELAY_MS'] = '500'
    Start-TrackedJava $paymentJar $paymentEnv `
        (Join-Path $logDir 'payment-relay-on.log') 'payment' $logDir
    Wait-HttpUp "http://127.0.0.1:$PaymentPort/actuator/health" `
        $StartupTimeoutSeconds 'Payment relay-on'

    $afterRelay = Wait-Condition -TimeoutSeconds 45 `
        -Label 'PaymentSucceeded application' -Condition {
            $state = Get-State $normal
            if ($state.booking -match '^PAID\|' -and
                $state.seat -match '^SOLD\|') { return $state }
            return $false
        }
    $published = (Invoke-Mysql -ReturnRows -Sql @"
SELECT status FROM school_bus_platform.event_outbox
 WHERE event_id='$eventId';
"@ | Select-Object -First 1).Trim()
    if ($published -ne 'PUBLISHED') {
        throw 'PaymentSucceeded Outbox was not marked PUBLISHED.'
    }
    $report.paymentSucceededApplied = $true
    $report.normalEvidence = @{
        beforeRelay = $beforeRelay
        afterRelay = $afterRelay
        eventId = $eventId
        outboxStatus = $published
    }

    $bookingVersionBefore = $afterRelay.booking.Split('|')[1]
    $seatVersionBefore = $afterRelay.seat.Split('|')[1]
    Publish-RabbitMqDefaultExchangeMessage `
        -RoutingKey $script:Topology.succeededQueue `
        -Payload $eventPayload -MessageId $eventId
    Start-Sleep -Seconds 2
    $afterDuplicate = Get-State $normal
    $consumedCount = [int] ((Invoke-Mysql -ReturnRows -Sql @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE consumer_name='booking-payment-succeeded-consumer'
   AND event_id='$eventId';
"@ | Select-Object -First 1).Trim())
    if ($consumedCount -ne 1 -or
        $afterDuplicate.booking.Split('|')[1] -ne $bookingVersionBefore -or
        $afterDuplicate.seat.Split('|')[1] -ne $seatVersionBefore) {
        throw 'Duplicate PaymentSucceeded changed business state.'
    }
    $report.duplicateEventIdempotent = $true
    $report.idempotencyEvidence = @{
        eventConsumedRows = $consumedCount
        bookingVersionBefore = $bookingVersionBefore
        bookingVersionAfter = $afterDuplicate.booking.Split('|')[1]
        seatVersionBefore = $seatVersionBefore
        seatVersionAfter = $afterDuplicate.seat.Split('|')[1]
    }

    $compensation = New-Scenario -BookingAmount 5.50 -PaidAmount 6.50
    $compensationResponse = Invoke-Callback `
        -Scenario $compensation -Secret $secret
    if ($compensationResponse.status -ne 200) {
        throw "Compensation callback returned HTTP $($compensationResponse.status)."
    }
    $compensated = Wait-Condition -TimeoutSeconds 60 `
        -Label 'compensating refund' -Condition {
            $state = Get-State $compensation
            if ($state.payment -match '^REFUNDED\|') { return $state }
            return $false
        }
    if ($compensated.booking -notmatch '^PENDING_PAYMENT\|' -or
        $compensated.seat -notmatch '^LOCKED\|') {
        throw 'Compensating refund corrupted the original Booking state.'
    }
    $refundEvidence = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT CONCAT(
           event_type, '|', status, '|',
           COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.reason')), '-')
       )
  FROM school_bus_platform.event_outbox
 WHERE aggregate_id='$($compensation.paymentNo)'
 ORDER BY id;
"@)
    if (-not ($refundEvidence -match '^PaymentSucceeded\|PUBLISHED\|-$') -or
        -not ($refundEvidence -match '^PaymentRefundRequired\|PUBLISHED\|PAYMENT_AMOUNT_MISMATCH$')) {
        throw 'Payment amount mismatch did not publish compensation event.'
    }
    $report.compensationRefunded = $true
    $report.compensationPreservedBooking = $true
    $report.compensationEvidence = @{
        finalState = $compensated
        outboxEvents = $refundEvidence
    }
    $report.status = 'PASSED'
} catch {
    $failure = $_
    $report.notes += $_.Exception.Message
} finally {
    Stop-TrackedProcesses
    try {
        Remove-TemporaryData
        $report.temporaryDataCleaned = $true
    } catch {
        $report.notes += "Data cleanup failed: $($_.Exception.Message)"
    }
    try {
        Remove-IsolatedTopology
        $report.temporaryTopologyCleaned = $true
    } catch {
        $report.notes += "Topology cleanup failed: $($_.Exception.Message)"
    }
    if (-not $report.temporaryDataCleaned -or
        -not $report.temporaryTopologyCleaned) {
        $report.status = 'FAILED'
    }
    $reportPath = Join-Path $logDir 'report.json'
    $report | ConvertTo-Json -Depth 10 | Set-Content `
        -LiteralPath $reportPath -Encoding UTF8
    Write-Host "Payment EVENT cutover report: $reportPath"
}

if ($null -ne $failure -or $report.status -ne 'PASSED') {
    throw "Payment EVENT cutover verification failed: $($report.notes -join '; ')"
}

$report | ConvertTo-Json -Depth 10

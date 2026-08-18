param(
    [string] $NacosBaseUrl = 'http://127.0.0.1:8848',
    [string] $AdminPassword = 'nacos',
    [string] $MysqlContainerName = 'school-bus-mysql',
    [string] $RabbitMqManagementBaseUrl = 'http://127.0.0.1:15672/api',
    [string] $RabbitMqUsername = 'guest',
    [string] $RabbitMqPassword = 'guest',
    [int] $CorePort = 8081,
    [int] $PaymentPort = 8085,
    [int] $StartupTimeoutSeconds = 120,
    [switch] $SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:HappyPathEventId = $null
$script:RetryEventId = $null
$script:HappyPathPaymentNo = $null
$script:HappyPathOrderNo = $null
$script:RetryPaymentNo = $null
$script:RetryOrderNo = $null
$script:DlqMessageId = $null
$script:VerifyTopology = $null
$script:OriginalFailure = $null
$script:ServicesStarted = $false
$script:CurrentPhase = $null

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1')
. (Join-Path $PSScriptRoot 'payment-refund-messaging.helpers.ps1')

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $projectRoot "target\payment-refund-messaging-$runId"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$reportPath = Join-Path $logDir 'report.json'
$report = New-PaymentRefundMessagingReport -RunId $runId
$script:VerifyTopology = New-VerifyTopologyNames -RunId $runId

function Invoke-MysqlLocal([string] $Sql, [switch] $ReturnRows) {
    if ($ReturnRows) {
        return @(
            docker exec $MysqlContainerName mysql -uroot -proot -N `
                -e $Sql 2>$null
        )
    }
    $output = @(docker exec $MysqlContainerName mysql -uroot -proot `
        -e $Sql 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join ' ')"
    }
}

function Remove-TemporaryRefundData {
    $eventIds = @(
        @($script:HappyPathEventId, $script:RetryEventId) |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    if ($eventIds.Count -eq 0) {
        return
    }

    $eventIdList = ($eventIds | ForEach-Object { "'$_'" }) -join ','
    $paymentNos = @(
        @($script:HappyPathPaymentNo, $script:RetryPaymentNo) |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
    $orderNos = @(
        @($script:HappyPathOrderNo, $script:RetryOrderNo) |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )

    $paymentClause = ''
    if ($paymentNos.Count -gt 0) {
        $paymentClause = @"
DELETE FROM payment_record
 WHERE payment_no IN ($(
    ($paymentNos | ForEach-Object { "'$_'" }) -join ','
));
"@
    }

    $orderClause = ''
    if ($orderNos.Count -gt 0) {
        $orderClause = @"
DELETE FROM booking_order
 WHERE order_no IN ($(
    ($orderNos | ForEach-Object { "'$_'" }) -join ','
));
"@
    }

    Invoke-MysqlLocal @"
USE school_bus_platform;
DELETE FROM event_consumed
 WHERE consumer_name='payment-refund-consumer'
   AND event_id IN ($eventIdList);
DELETE FROM event_outbox WHERE event_id IN ($eventIdList);
$paymentClause
$orderClause
"@
}

function New-RefundPayloadJson {
    param(
        [string] $PaymentNo,
        [string] $OrderNo
    )

    return (@{
        paymentNumber = $PaymentNo
        bookingNumber = $OrderNo
        amount = 5.50
        reason = 'PAYMENT_WINDOW_EXPIRED'
        paidAt = '2026-08-18T06:00:00Z'
        occurredAt = '2026-08-18T06:00:01Z'
    } | ConvertTo-Json -Compress)
}

function Insert-RefundOutboxEvent {
    param(
        [string] $EventId,
        [string] $PaymentNo,
        [string] $PayloadJson
    )

    $payloadSql = $PayloadJson.Replace('\', '\\').Replace("'", "''")
    Invoke-MysqlLocal @"
USE school_bus_platform;
INSERT INTO event_outbox (
  event_id, context_name, aggregate_type, aggregate_id,
  aggregate_version, event_type, payload, trace_id,
  status, retry_count, next_retry_at, occurred_at,
  created_at, published_at, version
) VALUES (
  '$EventId', 'payment', 'PaymentRecord', '$PaymentNo',
  0, 'PaymentRefundRequired', CAST('$payloadSql' AS JSON), 'verify-$runId',
  'NEW', 0, NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP(), NULL, 0
);
"@
}

function Insert-RefundPendingBooking {
    param(
        [string] $OrderNo
    )

    Invoke-MysqlLocal @"
USE school_bus_platform;
INSERT INTO booking_order (
  order_no, request_no, user_id, trip_id, trip_no,
  seat_number, price_snapshot,
  status, expires_at, version, created_at, updated_at
) VALUES (
  '$OrderNo', 'verify-$OrderNo', 1000001, 9001,
  '00000000-0000-0000-0000-000000009001', 'A1', 5.50,
  'REFUND_PENDING', UTC_TIMESTAMP() + INTERVAL 1 HOUR, 0,
  UTC_TIMESTAMP(), UTC_TIMESTAMP()
);
"@
}

function Insert-RefundPendingPayment {
    param(
        [string] $PaymentNo,
        [string] $OrderNo,
        [switch] $IncludeBooking
    )

    if ($IncludeBooking) {
        Insert-RefundPendingBooking -OrderNo $OrderNo
    }

    Invoke-MysqlLocal @"
USE school_bus_platform;
INSERT INTO payment_record (
  payment_no, request_no, order_no, amount, status,
  failure_reason, completed_at, version, created_at, updated_at
) VALUES (
  '$PaymentNo', 'req-$PaymentNo', '$OrderNo', 5.50,
  'REFUND_PENDING', 'PAYMENT_WINDOW_EXPIRED', UTC_TIMESTAMP(), 0,
  UTC_TIMESTAMP(), UTC_TIMESTAMP()
);
"@
}

function Wait-Until {
    param(
        [scriptblock] $Predicate,
        [int] $TimeoutSeconds,
        [string] $Description
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (& $Predicate) {
            return $true
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Description."
}

function Get-PaymentEnvOverrides {
    $topology = $script:VerifyTopology
    return @{
        PAYMENT_EVENTS_EXCHANGE = $topology.exchange
        PAYMENT_REFUND_ROUTING_KEY = $topology.refundRoutingKey
        PAYMENT_REFUND_QUEUE = $topology.refundQueue
        PAYMENT_DLX = $topology.deadLetterExchange
        PAYMENT_REFUND_DEAD_ROUTING_KEY = $topology.deadLetterRoutingKey
        PAYMENT_REFUND_DLQ = $topology.deadLetterQueue
        PAYMENT_REFUND_RETRY_EXCHANGE = $topology.retryExchange
        PAYMENT_REFUND_RETRY_ROUTING_KEY = $topology.retryRoutingKey
        PAYMENT_REFUND_RETRY_QUEUE = $topology.retryQueue
        PAYMENT_REFUND_RETRY_DELAY = 'PT3S'
        PAYMENT_REFUND_MAXIMUM_RETRIES = '3'
    }
}

function Assert-CoreAndPaymentOwnership {
    $coreOwner = Get-ActuatorRefundMessagingOwner `
        -BaseUrl "http://127.0.0.1:$CorePort"
    $paymentOwner = Get-ActuatorRefundMessagingOwner `
        -BaseUrl "http://127.0.0.1:$PaymentPort"
    $report.coreRefundMessagingOwner = $coreOwner
    $report.paymentRefundMessagingOwner = $paymentOwner

    if ($coreOwner -ne 'disabled') {
        throw "Core refundMessagingOwner expected disabled, got $coreOwner."
    }
    if ($paymentOwner -ne 'payment') {
        throw "Payment refundMessagingOwner expected payment, got $paymentOwner."
    }
    $report.coreRelayDisabled = $true
    $report.coreConsumerDisabled = $true
}

function Get-PaymentRecordSnapshot([string] $PaymentNo) {
    $row = (Invoke-MysqlLocal @"
SELECT status, version, updated_at
  FROM school_bus_platform.payment_record
 WHERE payment_no='$PaymentNo';
"@ -ReturnRows | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($row)) {
        throw "payment_record not found for $PaymentNo."
    }
    $parts = $row.Trim() -split '\t'
    return [ordered]@{
        status = $parts[0]
        version = $parts[1]
        updatedAt = $parts[2]
    }
}

function Invoke-HappyPathRefundVerification {
    $script:HappyPathEventId = [guid]::NewGuid().ToString()
    $script:HappyPathPaymentNo = [guid]::NewGuid().ToString()
    $script:HappyPathOrderNo = [guid]::NewGuid().ToString()
    $payload = New-RefundPayloadJson `
        -PaymentNo $script:HappyPathPaymentNo `
        -OrderNo $script:HappyPathOrderNo

    Insert-RefundPendingPayment `
        -PaymentNo $script:HappyPathPaymentNo `
        -OrderNo $script:HappyPathOrderNo `
        -IncludeBooking
    Insert-RefundOutboxEvent `
        -EventId $script:HappyPathEventId `
        -PaymentNo $script:HappyPathPaymentNo `
        -PayloadJson $payload

    Wait-Until -TimeoutSeconds 90 -Description 'happy-path outbox publish' {
        $status = (Invoke-MysqlLocal @"
SELECT status FROM school_bus_platform.event_outbox
 WHERE event_id='$($script:HappyPathEventId)';
"@ -ReturnRows | Select-Object -First 1).Trim()
        if ($status -eq 'PUBLISHED') {
            $report.outboxPublished = $true
            $report.publisherConfirmed = $true
            return $true
        }
        return $false
    } | Out-Null

    Wait-Until -TimeoutSeconds 90 -Description 'happy-path refund consumption' {
        $paymentStatus = (Invoke-MysqlLocal @"
SELECT status FROM school_bus_platform.payment_record
 WHERE payment_no='$($script:HappyPathPaymentNo)';
"@ -ReturnRows | Select-Object -First 1).Trim()
        if ($paymentStatus -eq 'REFUNDED') {
            $report.refundConsumed = $true
            return $true
        }
        return $false
    } | Out-Null

    $consumed1 = (Invoke-MysqlLocal @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE consumer_name='payment-refund-consumer'
   AND event_id='$($script:HappyPathEventId)';
"@ -ReturnRows | Select-Object -First 1).Trim()
    if ([int]$consumed1 -ne 1) {
        throw "Happy-path first consumption expected 1 event_consumed row, got $consumed1."
    }

    $snapshotBefore = Get-PaymentRecordSnapshot `
        -PaymentNo $script:HappyPathPaymentNo

    Publish-RabbitMqDefaultExchangeMessage `
        -RoutingKey $script:VerifyTopology.refundQueue `
        -Payload $payload `
        -MessageId $script:HappyPathEventId

    Start-Sleep -Seconds 8

    $consumed2 = (Invoke-MysqlLocal @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE consumer_name='payment-refund-consumer'
   AND event_id='$($script:HappyPathEventId)';
"@ -ReturnRows | Select-Object -First 1).Trim()

    $snapshotAfter = Get-PaymentRecordSnapshot `
        -PaymentNo $script:HappyPathPaymentNo

    $queueCount = Get-RabbitMqQueueMessageCount `
        -QueueName $script:VerifyTopology.refundQueue

    $rowStillOne = ([int]$consumed2 -eq 1)
    $statusStillRefunded = ($snapshotAfter.status -eq 'REFUNDED')
    $versionUnchanged = ($snapshotBefore.version -eq $snapshotAfter.version)
    $updatedAtUnchanged = ($snapshotBefore.updatedAt -eq $snapshotAfter.updatedAt)
    $queueDrained = ($queueCount -eq 0)

    $report.idempotencyVerified = (
        $rowStillOne -and $statusStillRefunded -and
        $versionUnchanged -and $updatedAtUnchanged -and $queueDrained
    )
    $report.idempotencyEvidence = [ordered]@{
        eventId = $script:HappyPathEventId
        eventConsumedRowsBefore = [int]$consumed1
        eventConsumedRowsAfter = [int]$consumed2
        statusBefore = $snapshotBefore.status
        statusAfter = $snapshotAfter.status
        versionBefore = $snapshotBefore.version
        versionAfter = $snapshotAfter.version
        updatedAtBefore = $snapshotBefore.updatedAt
        updatedAtAfter = $snapshotAfter.updatedAt
        queueMessageCountAfterReplay = $queueCount
        rowStillOne = $rowStillOne
        versionUnchanged = $versionUnchanged
        updatedAtUnchanged = $updatedAtUnchanged
        queueDrained = $queueDrained
    }

    if (-not $report.idempotencyVerified) {
        throw (
            "Idempotency verification failed: " +
            "consumedRows=$consumed2, status=$($snapshotAfter.status), " +
            "versionChanged=$(-not $versionUnchanged), " +
            "updatedAtChanged=$(-not $updatedAtUnchanged), " +
            "queueMessages=$queueCount."
        )
    }
}

function Invoke-RetryQueueVerification {
    $script:RetryEventId = [guid]::NewGuid().ToString()
    $script:RetryPaymentNo = [guid]::NewGuid().ToString()
    $script:RetryOrderNo = [guid]::NewGuid().ToString()
    $payload = New-RefundPayloadJson `
        -PaymentNo $script:RetryPaymentNo `
        -OrderNo $script:RetryOrderNo

    Insert-RefundPendingPayment `
        -PaymentNo $script:RetryPaymentNo `
        -OrderNo $script:RetryOrderNo
    Insert-RefundOutboxEvent `
        -EventId $script:RetryEventId `
        -PaymentNo $script:RetryPaymentNo `
        -PayloadJson $payload

    Wait-Until -TimeoutSeconds 90 -Description 'retry scenario outbox publish' {
        $status = (Invoke-MysqlLocal @"
SELECT status FROM school_bus_platform.event_outbox
 WHERE event_id='$($script:RetryEventId)';
"@ -ReturnRows | Select-Object -First 1).Trim()
        return ($status -eq 'PUBLISHED')
    } | Out-Null

    $retryQueue = $script:VerifyTopology.retryQueue
    $retryMessage = $null
    Wait-Until -TimeoutSeconds 30 -Description 'retry queue message' {
        $count = Get-RabbitMqQueueMessageCount -QueueName $retryQueue
        if ($count -lt 1) {
            return $false
        }
        $messages = Get-RabbitMqQueueMessages -QueueName $retryQueue -Count 5
        foreach ($candidate in $messages) {
            $messageId = "$($candidate.properties.message_id)"
            if ($messageId -eq $script:RetryEventId) {
                $retryMessage = $candidate
                return $true
            }
        }
        return $false
    } | Out-Null

    if (-not (Test-RabbitMqRetryMessageDeathHeader `
            -Message $retryMessage -RetryQueueName $retryQueue)) {
        $report.notes = @($report.notes) + @(
            'Retry queue message observed; x-death is added when the ' +
            'message dead-letters back to the main queue after TTL.'
        )
    }

    Insert-RefundPendingBooking -OrderNo $script:RetryOrderNo
    Start-Sleep -Seconds 4

    $xDeathOnRedelivery = $false
    $mainQueue = $script:VerifyTopology.refundQueue
    $redeliveryMessages = Get-RabbitMqQueueMessages -QueueName $mainQueue -Count 5
    foreach ($candidate in $redeliveryMessages) {
        if (Test-RabbitMqRetryMessageDeathHeader `
                -Message $candidate -RetryQueueName $retryQueue) {
            $xDeathOnRedelivery = $true
            break
        }
    }

    Wait-Until -TimeoutSeconds 60 -Description 'retry scenario refund success' {
        $paymentStatus = (Invoke-MysqlLocal @"
SELECT status FROM school_bus_platform.payment_record
 WHERE payment_no='$($script:RetryPaymentNo)';
"@ -ReturnRows | Select-Object -First 1).Trim()
        $orderStatus = (Invoke-MysqlLocal @"
SELECT status FROM school_bus_platform.booking_order
 WHERE order_no='$($script:RetryOrderNo)';
"@ -ReturnRows | Select-Object -First 1).Trim()
        return ($paymentStatus -eq 'REFUNDED' -and $orderStatus -eq 'REFUNDED')
    } | Out-Null

    $consumed = (Invoke-MysqlLocal @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE consumer_name='payment-refund-consumer'
   AND event_id='$($script:RetryEventId)';
"@ -ReturnRows | Select-Object -First 1).Trim()
    if ([int]$consumed -ne 1) {
        throw "Retry scenario expected 1 event_consumed row, got $consumed."
    }

    $report.retryVerified = $true
    $report.retryEvidence = [ordered]@{
        eventId = $script:RetryEventId
        retryQueue = $retryQueue
        retryQueueMessageObserved = $true
        xDeathVerified = $xDeathOnRedelivery
        eventConsumedRows = [int]$consumed
        paymentStatus = 'REFUNDED'
        orderStatus = 'REFUNDED'
    }
    if (-not $xDeathOnRedelivery) {
        $report.notes = @($report.notes) + @(
            'x-death header was not observed on main queue before consumption; ' +
            'retry success confirmed via management API queue inspection and final REFUNDED state.'
        )
    }
}

function Invoke-DlqVerification {
    $script:DlqMessageId = [guid]::NewGuid().ToString()
    Publish-RabbitMqDefaultExchangeMessage `
        -RoutingKey $script:VerifyTopology.refundQueue `
        -Payload '{this is not valid refund json' `
        -MessageId $script:DlqMessageId

    $dlqQueue = $script:VerifyTopology.deadLetterQueue
    Wait-Until -TimeoutSeconds 30 -Description 'dlq message arrival' {
        return (Test-RabbitMqQueueContainsMessageId `
            -QueueName $dlqQueue -MessageId $script:DlqMessageId)
    } | Out-Null

    $report.dlqVerified = $true
    $report.dlqEvidence = [ordered]@{
        messageId = $script:DlqMessageId
        dlqQueue = $dlqQueue
        managementApiVerified = $true
    }
}

try {
    $script:CurrentPhase = 'Assert-Java21'
    Assert-Java21

    $script:CurrentPhase = 'Assert-Docker'
    Assert-Docker

    $script:CurrentPhase = 'Assert-InfraHealthy'
    Assert-InfraHealthy -NacosBaseUrl $NacosBaseUrl `
        -MysqlContainerName $MysqlContainerName

    $script:CurrentPhase = 'Assert-PortsFree'
    Assert-PortsFree -GatewayPort 8080 -CorePort $CorePort `
        -QueryPorts @() -AdditionalPorts @($PaymentPort)

    $script:CurrentPhase = 'RabbitMQ-port-check'
    if (-not (Test-TcpPort '127.0.0.1' 5672 1500)) {
        throw 'RabbitMQ is not listening on 5672.'
    }

    $script:CurrentPhase = 'RabbitMQ-management-port-check'
    if (-not (Test-TcpPort '127.0.0.1' 15672 1500)) {
        throw 'RabbitMQ management API is not listening on 15672.'
    }

    $script:CurrentPhase = 'nacos-config'
    $nacosToken = Publish-NacosConfigs -ProjectRoot $projectRoot `
        -NacosBaseUrl $NacosBaseUrl -AdminPassword $AdminPassword

    $script:CurrentPhase = 'maven-build'
    if (-not $SkipBuild) {
        Invoke-MavenPackage -WorkingDirectory $projectRoot -Label 'core'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\payment-service') `
            -Label 'payment'
    }

    $script:CurrentPhase = 'service-startup'
    $coreJar = Find-BootJar (Join-Path $projectRoot 'target') 'school-bus-platform'
    $paymentJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\payment-service\target') `
        'school-bus-payment'
    $jwtKeys = Ensure-JwtKeys -ProjectRoot $projectRoot

    $commonEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
    }

    $coreEnv = $commonEnv.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'local,cloud'
    $coreEnv['CORE_SERVER_PORT'] = "$CorePort"
    $coreEnv['SERVER_PORT'] = "$CorePort"
    $coreEnv['JWT_PUBLIC_KEY_LOCATION'] = $jwtKeys.Public
    Start-TrackedJava $coreJar $coreEnv `
        (Join-Path $logDir 'core.log') 'core' $logDir

    $paymentEnv = $commonEnv.Clone()
    foreach ($entry in (Get-PaymentEnvOverrides).GetEnumerator()) {
        $paymentEnv[$entry.Key] = $entry.Value
    }
    $paymentEnv['PAYMENT_SERVER_PORT'] = "$PaymentPort"
    $paymentEnv['SERVER_PORT'] = "$PaymentPort"
    $paymentEnv['PAYMENT_CALLBACK_SECRET'] = 'verify-payment-callback-secret-2026'
    $paymentEnv['OUTBOX_RELAY_INITIAL_DELAY_MS'] = '2000'
    $paymentEnv['OUTBOX_RELAY_FIXED_DELAY_MS'] = '500'
    Start-TrackedJava $paymentJar $paymentEnv `
        (Join-Path $logDir 'payment.log') 'payment' $logDir
    $script:ServicesStarted = $true

    Wait-HttpUp "http://127.0.0.1:$CorePort/actuator/health" `
        $StartupTimeoutSeconds 'Core'
    Wait-HttpUp "http://127.0.0.1:$PaymentPort/actuator/health" `
        $StartupTimeoutSeconds 'Payment'

    $paymentReady = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 1 `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-payment'
    $report.nacosPaymentHealthy = ($paymentReady.snapshot.healthy -ge 1)

    $script:CurrentPhase = 'ownership-check'
    Start-Sleep -Seconds 3
    Assert-CoreAndPaymentOwnership

    $script:CurrentPhase = 'happy-path'
    Invoke-HappyPathRefundVerification

    $script:CurrentPhase = 'retry-verification'
    Invoke-RetryQueueVerification

    $script:CurrentPhase = 'dlq-verification'
    Invoke-DlqVerification
}
catch {
    $script:OriginalFailure = $_
    $message = $_.Exception.Message
    $report.notes = @($report.notes) + @($message)
    $report.failedInPhase = $script:CurrentPhase
    if (Test-EnvironmentBlockedPhase -Phase $script:CurrentPhase) {
        $report.environmentBlocked = $true
    }
}
finally {
    $cleanupNotes = Invoke-VerificationCleanup -Steps @(
        {
            Remove-TemporaryRefundData
            $report.temporaryDataCleaned = $true
            return $null
        },
        {
            if ($null -ne $script:VerifyTopology) {
                Remove-RabbitMqVerifyTopology -Topology $script:VerifyTopology
                $verifyFailures = @(
                    Confirm-RabbitMqVerifyTopologyAbsent `
                        -Topology $script:VerifyTopology
                )
                if ($verifyFailures.Count -eq 0) {
                    $report.temporaryTopologyCleaned = $true
                } else {
                    $report.notes = @($report.notes) + $verifyFailures
                }
            }
            return $null
        },
        {
            Stop-TrackedProcesses
            return $null
        },
        {
            Stop-ServiceByPortSafe -Port $CorePort -Label 'core'
        },
        {
            Stop-ServiceByPortSafe -Port $PaymentPort -Label 'payment'
        }
    )

    foreach ($note in @($cleanupNotes)) {
        if (-not [string]::IsNullOrWhiteSpace($note)) {
            $report.notes = @($report.notes) + @($note)
        }
    }

    $resolved = Resolve-PaymentRefundMessagingStatus -Report $report
    $report.status = $resolved.status
    $report.failureCategory = $resolved.failureCategory
    Write-PaymentRefundMessagingReport -Report $report -ReportPath $reportPath

    if ($null -ne $script:OriginalFailure) {
        Write-Error $script:OriginalFailure.Exception.Message
        exit 1
    }
    if ($report.status -ne 'PASSED') {
        exit 1
    }
}

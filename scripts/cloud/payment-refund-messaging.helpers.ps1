#Requires -Version 5.1
Set-StrictMode -Version Latest

function New-PaymentRefundMessagingReport([string] $RunId) {
    return [ordered]@{
        runId = $RunId
        status = 'FAILED'
        failureCategory = 'verification_not_executed'
        environmentBlocked = $false
        failedInPhase = $null
        nacosPaymentHealthy = $false
        outboxPublished = $false
        publisherConfirmed = $false
        refundConsumed = $false
        idempotencyVerified = $false
        retryVerified = $false
        dlqVerified = $false
        coreRelayDisabled = $false
        coreConsumerDisabled = $false
        temporaryDataCleaned = $false
        temporaryTopologyCleaned = $false
        coreRefundMessagingOwner = $null
        paymentRefundMessagingOwner = $null
        idempotencyEvidence = @{}
        retryEvidence = @{}
        dlqEvidence = @{}
        notes = @()
    }
}

$script:EnvironmentCheckPhases = @(
    'Assert-Java21',
    'Assert-Docker',
    'Assert-InfraHealthy',
    'Assert-PortsFree',
    'RabbitMQ-port-check',
    'RabbitMQ-management-port-check'
)

function Test-EnvironmentBlockedPhase([string] $Phase) {
    if ([string]::IsNullOrWhiteSpace($Phase)) {
        return $false
    }
    return ($script:EnvironmentCheckPhases -contains $Phase)
}

function Resolve-PaymentRefundMessagingStatus([hashtable] $Report) {
    $required = @(
        'nacosPaymentHealthy',
        'outboxPublished',
        'publisherConfirmed',
        'refundConsumed',
        'idempotencyVerified',
        'retryVerified',
        'dlqVerified',
        'coreRelayDisabled',
        'coreConsumerDisabled',
        'temporaryDataCleaned',
        'temporaryTopologyCleaned'
    )

    $allTrue = $true
    foreach ($field in $required) {
        if (-not $Report[$field]) {
            $allTrue = $false
            break
        }
    }

    if ($allTrue) {
        return @{
            status = 'PASSED'
            failureCategory = 'verification_succeeded'
        }
    }

    if ($Report.environmentBlocked) {
        return @{
            status = 'BLOCKED'
            failureCategory = 'environment_blocked'
        }
    }

    $verificationExecuted = $Report.outboxPublished -or $Report.refundConsumed `
        -or $Report.retryVerified -or $Report.dlqVerified
    $verificationIncomplete = (-not $Report.retryVerified) -or (-not $Report.dlqVerified)

    if ($verificationIncomplete -and -not $verificationExecuted) {
        return @{
            status = 'FAILED'
            failureCategory = 'verification_not_executed'
        }
    }

    if ($verificationIncomplete) {
        return @{
            status = 'PARTIAL'
            failureCategory = 'verification_not_executed'
        }
    }

    return @{
        status = 'FAILED'
        failureCategory = 'business_failure'
    }
}

function Stop-ServiceByPortSafe {
    param(
        [int] $Port,
        [string] $Label = 'service'
    )

    $getListeningPids = Get-Command Get-ListeningPids -ErrorAction SilentlyContinue
    if ($null -eq $getListeningPids) {
        return $null
    }

    $listenerPids = @(Get-ListeningPids $Port)
    if (@($listenerPids).Count -eq 0) {
        return $null
    }

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    foreach ($processId in $listenerPids) {
        taskkill /PID $processId /T /F 2>$null | Out-Null
    }
    $ErrorActionPreference = $previousErrorAction
    return "Stopped $Label listener(s) on port $Port"
}

function Invoke-VerificationCleanup {
    param(
        [scriptblock[]] $Steps
    )

    $notes = @()
    foreach ($step in $Steps) {
        try {
            $result = & $step
            if (-not [string]::IsNullOrWhiteSpace($result)) {
                $notes += $result
            }
        } catch {
            $notes += "Cleanup step failed: $($_.Exception.Message)"
        }
    }
    return $notes
}

function Get-ActuatorRefundMessagingOwner {
    param(
        [string] $BaseUrl,
        [int] $TimeoutSeconds = 10
    )

    $uri = "$BaseUrl/actuator/info"
    $response = Invoke-RestMethod -Uri $uri -TimeoutSec $TimeoutSeconds
    if ($null -eq $response.refundMessagingOwner) {
        throw "Actuator info at $uri did not expose refundMessagingOwner."
    }
    return [string]$response.refundMessagingOwner
}

function New-VerifyTopologyNames([string] $RunId) {
    $suffix = "verify-$RunId"
    return [ordered]@{
        exchange = "schoolbus.payment.events.$suffix"
        refundQueue = "schoolbus.payment.refund.$suffix"
        refundRoutingKey = 'payment.refund.required'
        deadLetterExchange = "schoolbus.payment.dlx.$suffix"
        deadLetterRoutingKey = 'payment.refund.dead'
        deadLetterQueue = "schoolbus.payment.refund.dlq.$suffix"
        retryExchange = "schoolbus.payment.retry.$suffix"
        retryRoutingKey = 'payment.refund.retry'
        retryQueue = "schoolbus.payment.refund.retry.$suffix"
    }
}

function Get-RabbitMqBasicAuthHeader {
    param(
        [string] $Username = 'guest',
        [string] $Password = 'guest'
    )

    $token = [Convert]::ToBase64String(
        [Text.Encoding]::ASCII.GetBytes("$Username`:$Password")
    )
    return "Basic $token"
}

function Invoke-RabbitMqApi {
    param(
        [ValidateSet('GET', 'PUT', 'POST', 'DELETE')]
        [string] $Method,
        [string] $Path,
        [object] $Body = $null,
        [string] $ManagementBaseUrl = 'http://127.0.0.1:15672/api',
        [string] $Username = 'guest',
        [string] $Password = 'guest'
    )

    $uri = if ($Path.StartsWith('http')) { $Path } else { "$ManagementBaseUrl/$Path" }
    $headers = @{
        Authorization = (Get-RabbitMqBasicAuthHeader -Username $Username -Password $Password)
    }
    $params = @{
        Uri = $uri
        Method = $Method
        Headers = $headers
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 8 -Compress)
        $params['ContentType'] = 'application/json'
    }
    return Invoke-RestMethod @params
}

function Get-RabbitMqQueueMessageCount {
    param(
        [string] $QueueName,
        [string] $VirtualHost = '%2F'
    )

    $encodedQueue = [uri]::EscapeDataString($QueueName)
    $queue = Invoke-RabbitMqApi -Method GET -Path "queues/$VirtualHost/$encodedQueue"
    return [int]$queue.messages
}

function Get-RabbitMqQueueMessages {
    param(
        [string] $QueueName,
        [int] $Count = 1,
        [string] $VirtualHost = '%2F'
    )

    $encodedQueue = [uri]::EscapeDataString($QueueName)
    $body = @{
        count = $Count
        ackmode = 'ack_requeue_true'
        encoding = 'auto'
    }
    return @(Invoke-RabbitMqApi -Method POST `
        -Path "queues/$VirtualHost/$encodedQueue/get" `
        -Body $body)
}

function Test-RabbitMqQueueContainsMessageId {
    param(
        [string] $QueueName,
        [string] $MessageId,
        [int] $SampleSize = 20
    )

    $messages = Get-RabbitMqQueueMessages -QueueName $QueueName -Count $SampleSize
    foreach ($message in $messages) {
        $properties = $message.properties
        if ($null -ne $properties -and "$($properties.message_id)" -eq $MessageId) {
            return $true
        }
    }
    return $false
}

function Publish-RabbitMqDefaultExchangeMessage {
    param(
        [string] $RoutingKey,
        [string] $Payload,
        [string] $MessageId,
        [string] $ContentType = 'application/json'
    )

    $body = @{
        properties = @{
            message_id = $MessageId
            content_type = $ContentType
            delivery_mode = 2
        }
        routing_key = $RoutingKey
        payload = $Payload
        payload_encoding = 'string'
    }
    $result = Invoke-RabbitMqApi -Method POST `
        -Path 'exchanges/%2F/amq.default/publish' `
        -Body $body
    if (-not $result.routed) {
        throw "RabbitMQ did not route message $MessageId to $RoutingKey."
    }
}

function Get-HttpStatusCodeFromErrorRecord {
    param(
        [System.Management.Automation.ErrorRecord] $ErrorRecord
    )

    if ($null -eq $ErrorRecord -or $null -eq $ErrorRecord.Exception) {
        return $null
    }

    $candidates = @($ErrorRecord.Exception)
    $responseProperty = $ErrorRecord.Exception.PSObject.Properties['Response']
    if ($null -ne $responseProperty -and $null -ne $responseProperty.Value) {
        $candidates += $responseProperty.Value
    }

    foreach ($candidate in $candidates) {
        if ($null -eq $candidate) { continue }
        $statusProperty = $candidate.PSObject.Properties['StatusCode']
        if ($null -eq $statusProperty -or $null -eq $statusProperty.Value) {
            continue
        }
        try {
            return [int]$statusProperty.Value
        } catch {
            # Keep looking in case another candidate exposes a usable value.
        }
    }
    return $null
}

function Remove-RabbitMqResource {
    param(
        [ValidateSet('queue', 'exchange')]
        [string] $ResourceType,
        [string] $ResourceName,
        [string] $VirtualHost = '%2F'
    )

    if ([string]::IsNullOrWhiteSpace($ResourceName)) { return }
    $encodedName = [uri]::EscapeDataString($ResourceName)
    $typePlural = if ($ResourceType -eq 'queue') { 'queues' } else { 'exchanges' }
    try {
        Invoke-RabbitMqApi -Method DELETE `
            -Path "$typePlural/$VirtualHost/$encodedName" | Out-Null
    } catch {
        $statusCode = Get-HttpStatusCodeFromErrorRecord -ErrorRecord $_
        if ($statusCode -eq 404) {
            return
        }
        throw
    }
}

function Test-RabbitMqResourceAbsent {
    param(
        [ValidateSet('queue', 'exchange')]
        [string] $ResourceType,
        [string] $ResourceName,
        [string] $VirtualHost = '%2F'
    )

    if ([string]::IsNullOrWhiteSpace($ResourceName)) { return $true }
    $encodedName = [uri]::EscapeDataString($ResourceName)
    $typePlural = if ($ResourceType -eq 'queue') { 'queues' } else { 'exchanges' }
    try {
        Invoke-RabbitMqApi -Method GET `
            -Path "$typePlural/$VirtualHost/$encodedName" | Out-Null
        return $false
    } catch {
        $statusCode = Get-HttpStatusCodeFromErrorRecord -ErrorRecord $_
        if ($statusCode -eq 404) {
            return $true
        }
        throw
    }
}

function Remove-RabbitMqVerifyTopology {
    param(
        [hashtable] $Topology,
        [string] $VirtualHost = '%2F'
    )

    $queueNames = @(
        $Topology.retryQueue,
        $Topology.deadLetterQueue,
        $Topology.refundQueue
    )
    $exchangeNames = @(
        $Topology.retryExchange,
        $Topology.deadLetterExchange,
        $Topology.exchange
    )

    foreach ($queueName in $queueNames) {
        Remove-RabbitMqResource -ResourceType 'queue' `
            -ResourceName $queueName -VirtualHost $VirtualHost
    }
    foreach ($exchangeName in $exchangeNames) {
        Remove-RabbitMqResource -ResourceType 'exchange' `
            -ResourceName $exchangeName -VirtualHost $VirtualHost
    }
}

function Confirm-RabbitMqVerifyTopologyAbsent {
    param(
        [hashtable] $Topology,
        [string] $VirtualHost = '%2F'
    )

    $queueNames = @(
        $Topology.retryQueue,
        $Topology.deadLetterQueue,
        $Topology.refundQueue
    )
    $exchangeNames = @(
        $Topology.retryExchange,
        $Topology.deadLetterExchange,
        $Topology.exchange
    )

    $failures = @()
    foreach ($queueName in $queueNames) {
        if ([string]::IsNullOrWhiteSpace($queueName)) { continue }
        if (-not (Test-RabbitMqResourceAbsent -ResourceType 'queue' `
                -ResourceName $queueName -VirtualHost $VirtualHost)) {
            $failures += "Queue still exists: $queueName"
        }
    }
    foreach ($exchangeName in $exchangeNames) {
        if ([string]::IsNullOrWhiteSpace($exchangeName)) { continue }
        if (-not (Test-RabbitMqResourceAbsent -ResourceType 'exchange' `
                -ResourceName $exchangeName -VirtualHost $VirtualHost)) {
            $failures += "Exchange still exists: $exchangeName"
        }
    }
    return $failures
}

function Test-RabbitMqRetryMessageDeathHeader {
    param(
        [object] $Message,
        [string] $RetryQueueName
    )

    if ($null -eq $Message) {
        return $false
    }
    $properties = $Message.properties
    if ($null -eq $properties) {
        return $false
    }
    $headers = $properties.headers
    if ($null -eq $headers) {
        return $false
    }
    $xDeath = $headers.'x-death'
    if ($null -eq $xDeath) {
        return $false
    }
    foreach ($entry in @($xDeath)) {
        if ("$($entry.queue)" -eq $RetryQueueName) {
            return $true
        }
    }
    return $false
}

function Write-PaymentRefundMessagingReport {
    param(
        [hashtable] $Report,
        [string] $ReportPath
    )

    ($Report | ConvertTo-Json -Depth 6) | Set-Content -Encoding UTF8 $ReportPath
    Write-Host "Report: $ReportPath"
    Write-Host ($Report | ConvertTo-Json -Depth 6)
}

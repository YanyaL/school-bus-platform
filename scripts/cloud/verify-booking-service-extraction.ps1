param(
    [string] $NacosBaseUrl = 'http://127.0.0.1:8848',
    [string] $AdminPassword = 'nacos',
    [string] $MysqlContainerName = 'school-bus-mysql',
    [string] $RabbitMqManagementBaseUrl = 'http://127.0.0.1:15672/api',
    [string] $RabbitMqUsername = 'guest',
    [string] $RabbitMqPassword = 'guest',
    [int] $GatewayPort = 8080,
    [int] $CorePort = 8081,
    [int] $QueryPort = 8082,
    [int] $IamPort = 8084,
    [int] $BookingPort = 8087,
    [int] $StartupTimeoutSeconds = 120,
    [int] $ExpirationWindowSeconds = 30,
    [switch] $SkipBuild,
    [switch] $ImportOnly
)

<#
.SYNOPSIS
  Real acceptance for the Booking strangler (school-bus-booking) extraction.

.DESCRIPTION
  Starts Core (embedded Booking disabled), Gateway, Transport Query, IAM and
  the extracted Booking service against real Nacos 3, MySQL, Redis and
  RabbitMQ, then proves:

    - Nacos registers a healthy school-bus-booking instance
    - Gateway /api/v1/bookings is served by school-bus-booking, not Core,
      and the write route carries no Retry filter
    - Core reports bookingOwner=disabled, Booking reports bookingOwner=booking
    - A real booking lifecycle through the Gateway: list trips, list seats,
      create (with Idempotency-Key), replay, list, detail, cancel, plus the
      matching booking_order / transport_trip_seat / booking_trip_inventory
      rows
    - Unauthenticated booking reads and writes are rejected with 401
    - PaymentSucceeded is consumed by school-bus-booking only
    - The booking payment deadline chain expires an unpaid booking
    - TripCancellationRequested settles bookings and writes the settlement
      outbox event

  Contract:
    - Docker / Java / infra / ports unavailable -> BLOCKED
    - A business assertion failure                -> FAILED or PARTIAL
    - PASSED requires every required flag to be $true

  Temporary rows are always removed in finally and the removal is verified.
  No JWT, password, PEM or Nacos token is written to the report or console.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:OriginalFailure = $null
$script:CurrentPhase = $null
$script:VerifyTopology = $null

$script:TripIds = @(9101, 9102, 9103, 9104)
$script:BookingNumbers = [System.Collections.Generic.List[string]]::new()
$script:BookingIds = [System.Collections.Generic.List[string]]::new()
$script:EventIds = [System.Collections.Generic.List[string]]::new()
$script:RequestNumbers = [System.Collections.Generic.List[string]]::new()
$script:StudentNumber = $null
$script:AccessToken = $null
$script:UserId = $null

$script:BookingQueues = @()

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1')
. (Join-Path $PSScriptRoot 'payment-refund-messaging.helpers.ps1')

function New-BookingExtractionReport([string] $RunId) {
    return [ordered]@{
        runId = $RunId
        status = 'FAILED'
        failureCategory = 'verification_not_executed'
        environmentBlocked = $false
        failedInPhase = $null
        scriptStage = 'real-acceptance'

        # environment
        javaAvailable = $false
        dockerAvailable = $false
        infraHealthy = $false
        rabbitMqReachable = $false
        rabbitMqManagementReachable = $false

        # discovery / routing
        nacosBookingHealthy = $false
        gatewayRoutesBookingToService = $false
        gatewayBookingRouteHasNoRetry = $false

        # ownership
        coreBookingEmbeddedDisabled = $false
        bookingServiceOwnershipReported = $false
        coreBookingOwner = $null
        bookingServiceBookingOwner = $null

        # business verification
        createBookingVerified = $false
        unauthenticatedBookingRejected = $false
        paymentSucceededConsumedByBookingOnly = $false
        bookingExpirationVerified = $false
        tripCancellationSettlementVerified = $false

        # cleanup
        temporaryDataCleaned = $false
        temporaryTopologyCleaned = $false

        # evidence
        routingEvidence = [ordered]@{}
        bookingEvidence = [ordered]@{}
        authenticationEvidence = [ordered]@{}
        paymentSucceededEvidence = [ordered]@{}
        expirationEvidence = [ordered]@{}
        tripCancellationEvidence = [ordered]@{}
        cleanupEvidence = [ordered]@{}

        notes = @()
    }
}

$script:BookingRequiredFlags = @(
    'nacosBookingHealthy',
    'gatewayRoutesBookingToService',
    'gatewayBookingRouteHasNoRetry',
    'coreBookingEmbeddedDisabled',
    'bookingServiceOwnershipReported',
    'createBookingVerified',
    'unauthenticatedBookingRejected',
    'paymentSucceededConsumedByBookingOnly',
    'bookingExpirationVerified',
    'tripCancellationSettlementVerified',
    'temporaryDataCleaned',
    'temporaryTopologyCleaned'
)

$script:BookingBusinessFlags = @(
    'createBookingVerified',
    'paymentSucceededConsumedByBookingOnly',
    'bookingExpirationVerified',
    'tripCancellationSettlementVerified'
)

# Only genuine pre-checks belong here. Anything that fails after the
# environment is proven healthy is a business failure, never BLOCKED.
$script:BookingEnvironmentPhases = @(
    'Assert-Java21',
    'Assert-Docker',
    'Assert-InfraHealthy',
    'Assert-PortsFree',
    'RabbitMQ-port-check',
    'RabbitMQ-management-port-check'
)

function Test-BookingEnvironmentBlockedPhase([string] $Phase) {
    if ([string]::IsNullOrWhiteSpace($Phase)) {
        return $false
    }
    return ($script:BookingEnvironmentPhases -contains $Phase)
}

function Resolve-BookingExtractionStatus([hashtable] $Report) {
    $allTrue = $true
    foreach ($field in $script:BookingRequiredFlags) {
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

    $executed = 0
    foreach ($field in $script:BookingBusinessFlags) {
        if ($Report[$field]) {
            $executed++
        }
    }

    if ($executed -eq 0) {
        return @{
            status = 'FAILED'
            failureCategory = 'verification_not_executed'
        }
    }

    if ($executed -lt $script:BookingBusinessFlags.Count) {
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

function Write-BookingExtractionReport {
    param(
        [hashtable] $Report,
        [string] $ReportPath
    )

    $json = $Report | ConvertTo-Json -Depth 8
    $reportDirectory = Split-Path -Parent $ReportPath
    if (-not (Test-Path -LiteralPath $reportDirectory)) {
        New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
    }
    Set-Content -Path $ReportPath -Value $json -Encoding UTF8
    Write-Host ''
    Write-Host "Report: $ReportPath"
    Write-Host "Status: $($Report.status) ($($Report.failureCategory))"
}

function Get-ActuatorDetail {
    param(
        [string] $BaseUrl,
        [string] $DetailName,
        [int] $TimeoutSeconds = 10
    )

    # Actuator answers with application/vnd.spring-boot.actuator.v3+json, which
    # Invoke-RestMethod does not reliably deserialise on Windows PowerShell, so
    # the body is read as text and converted explicitly.
    $uri = "$BaseUrl/actuator/info"
    $response = Invoke-HttpCapture -Uri $uri -Method GET `
        -TimeoutSeconds $TimeoutSeconds
    if ($response.status -ne 200) {
        throw "Actuator info at $uri returned HTTP $($response.status)."
    }
    $value = Get-ObjectProperty -Object $response.body -Names @($DetailName)
    if ($null -eq $value) {
        throw "Actuator info at $uri did not expose ${DetailName}: $($response.raw)"
    }
    return [string]$value
}

# ---------------------------------------------------------------------------
# Small utilities
# ---------------------------------------------------------------------------

function Get-ObjectProperty {
    param(
        [object] $Object,
        [string[]] $Names
    )

    if ($null -eq $Object) {
        return $null
    }
    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property) {
            return $property.Value
        }
    }
    return $null
}

function Wait-Until {
    param(
        [scriptblock] $Predicate,
        [int] $TimeoutSeconds,
        [string] $Description,
        [int] $PollSeconds = 2
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (& $Predicate) {
            return $true
        }
        Start-Sleep -Seconds $PollSeconds
    } while ((Get-Date) -lt $deadline)
    throw "Timed out after ${TimeoutSeconds}s waiting for $Description."
}

# Windows PowerShell hands back a Byte[] for content types it does not
# recognise as text, and Actuator replies with
# application/vnd.spring-boot.actuator.v3+json, so the payload is normalised
# to UTF-8 text before it is parsed.
function ConvertTo-ResponseText([object] $Content) {
    if ($null -eq $Content) {
        return $null
    }
    if ($Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Content)
    }
    return [string]$Content
}

function ConvertFrom-JsonSafely([object] $Content) {
    $text = ConvertTo-ResponseText -Content $Content
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    try {
        return $text | ConvertFrom-Json
    } catch {
        return $null
    }
}

function ConvertTo-HeaderMap([object] $Headers) {
    $map = @{}
    if ($null -eq $Headers) {
        return $map
    }
    foreach ($key in $Headers.Keys) {
        $map[[string]$key] = ($Headers[$key] -join ',')
    }
    return $map
}

function Get-HeaderValue {
    param(
        [hashtable] $Headers,
        [string] $Name
    )

    if ($null -eq $Headers) {
        return $null
    }
    foreach ($key in $Headers.Keys) {
        if ($key -ieq $Name) {
            return [string]$Headers[$key]
        }
    }
    return $null
}

function Invoke-HttpCapture {
    param(
        [string] $Uri,
        [ValidateSet('GET', 'POST', 'PUT', 'DELETE')]
        [string] $Method = 'GET',
        [hashtable] $Headers = @{},
        [string] $Body = $null,
        [int] $TimeoutSeconds = 20
    )

    $parameters = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = $TimeoutSeconds
    }
    # Windows PowerShell refuses to attach even an empty body to a GET, and a
    # [string] parameter defaulting to $null arrives as '', so the body is only
    # attached when it actually carries JSON.
    if (-not [string]::IsNullOrEmpty($Body)) {
        $parameters['ContentType'] = 'application/json'
        $parameters['Body'] = $Body
    }

    try {
        $response = Invoke-WebRequest @parameters
        return [pscustomobject]@{
            status = [int]$response.StatusCode
            headers = (ConvertTo-HeaderMap $response.Headers)
            body = (ConvertFrom-JsonSafely -Content $response.Content)
            raw = (ConvertTo-ResponseText -Content $response.Content)
        }
    } catch {
        $errorResponse = $null
        $responseProperty = $_.Exception.PSObject.Properties['Response']
        if ($null -ne $responseProperty) {
            $errorResponse = $responseProperty.Value
        }
        if ($null -eq $errorResponse) {
            throw
        }
        $content = $null
        $detailsProperty = $_.PSObject.Properties['ErrorDetails']
        if ($null -ne $detailsProperty -and $null -ne $detailsProperty.Value) {
            $content = $detailsProperty.Value.Message
        }
        return [pscustomobject]@{
            status = [int]$errorResponse.StatusCode
            headers = @{}
            body = (ConvertFrom-JsonSafely -Content $content)
            raw = (ConvertTo-ResponseText -Content $content)
        }
    }
}

function Assert-HttpStatus {
    param(
        [object] $Response,
        [int[]] $Expected,
        [string] $Label
    )

    if ($Expected -notcontains [int]$Response.status) {
        throw ("{0} expected HTTP {1}, got {2}." -f `
            $Label, ($Expected -join ' or '), $Response.status)
    }
}

function Get-JwtSubject([string] $Token) {
    $segments = $Token.Split('.')
    if ($segments.Count -lt 2) {
        throw 'Access token is not a JWT.'
    }
    $payload = $segments[1].Replace('-', '+').Replace('_', '/')
    while (($payload.Length % 4) -ne 0) {
        $payload += '='
    }
    $json = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String($payload)
    )
    $claims = $json | ConvertFrom-Json
    $subject = [string](Get-ObjectProperty -Object $claims -Names @('sub'))
    if ([string]::IsNullOrWhiteSpace($subject)) {
        throw 'Access token does not carry a subject claim.'
    }
    return $subject
}

# ---------------------------------------------------------------------------
# MySQL helpers
# ---------------------------------------------------------------------------

# MYSQL_PWD keeps the password off the container command line, which also
# removes the "Using a password on the command line interface can be insecure"
# warning that Windows PowerShell would otherwise promote into a terminating
# error under $ErrorActionPreference = 'Stop'.
function Invoke-MysqlRows([string] $Sql) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $rows = @(
            docker exec -e MYSQL_PWD=root $MysqlContainerName `
                mysql -uroot -N -e $Sql 2>$null
        )
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed (exit $LASTEXITCODE)."
    }
    return $rows
}

function Invoke-MysqlScalar([string] $Sql) {
    $row = Invoke-MysqlRows $Sql | Select-Object -First 1
    if ($null -eq $row) {
        return ''
    }
    return ([string]$row).Trim()
}

function Invoke-MysqlCount([string] $Sql) {
    $value = Invoke-MysqlScalar $Sql
    if ([string]::IsNullOrWhiteSpace($value)) {
        return 0
    }
    return [int]$value
}

function Invoke-MysqlExec([string] $Sql) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(
            docker exec -e MYSQL_PWD=root $MysqlContainerName `
                mysql -uroot -e $Sql 2>&1
        )
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join ' ')"
    }
}

function Get-SqlList([object[]] $Values) {
    $quoted = @(
        @($Values) |
            Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) } |
            ForEach-Object { "'" + ([string]$_).Replace("'", "''") + "'" }
    )
    if ($quoted.Count -eq 0) {
        return "''"
    }
    return ($quoted -join ',')
}

function Get-BookingOrderSnapshot([string] $BookingNumber) {
    $row = Invoke-MysqlScalar @"
SELECT CONCAT_WS('|', status, IFNULL(cancel_reason,''), IFNULL(payment_no,''),
       version, updated_at, trip_id, seat_number, id)
  FROM school_bus_platform.booking_order
 WHERE order_no='$BookingNumber';
"@
    if ([string]::IsNullOrWhiteSpace($row)) {
        throw "booking_order row not found for $BookingNumber."
    }
    $parts = $row -split '\|'
    return [ordered]@{
        status = $parts[0]
        cancelReason = $parts[1]
        paymentNo = $parts[2]
        version = $parts[3]
        updatedAt = $parts[4]
        tripId = $parts[5]
        seatNumber = $parts[6]
        bookingId = $parts[7]
    }
}

function Get-SeatStatus([int] $TripId, [string] $SeatNumber) {
    return Invoke-MysqlScalar @"
SELECT status FROM school_bus_platform.transport_trip_seat
 WHERE trip_id=$TripId AND seat_number='$SeatNumber';
"@
}

function Get-AvailableSeats([int] $TripId) {
    return Invoke-MysqlCount @"
SELECT available_seats FROM school_bus_platform.booking_trip_inventory
 WHERE trip_id=$TripId;
"@
}

# ---------------------------------------------------------------------------
# RabbitMQ helpers
# ---------------------------------------------------------------------------

function Publish-RabbitMqExchangeMessage {
    param(
        [string] $Exchange,
        [string] $RoutingKey,
        [string] $Payload,
        [string] $MessageId
    )

    $body = @{
        properties = @{
            message_id = $MessageId
            content_type = 'application/json'
            content_encoding = 'UTF-8'
            delivery_mode = 2
        }
        routing_key = $RoutingKey
        payload = $Payload
        payload_encoding = 'string'
    }
    $encodedExchange = [uri]::EscapeDataString($Exchange)
    $result = Invoke-RabbitMqApi -Method POST `
        -Path "exchanges/%2F/$encodedExchange/publish" `
        -Body $body `
        -ManagementBaseUrl $RabbitMqManagementBaseUrl `
        -Username $RabbitMqUsername `
        -Password $RabbitMqPassword
    if (-not $result.routed) {
        throw "RabbitMQ did not route message $MessageId to $Exchange/$RoutingKey."
    }
}

function Get-RabbitMqQueueDepthSafe([string] $QueueName) {
    try {
        return (Get-RabbitMqQueueMessageCount -QueueName $QueueName)
    } catch {
        $statusCode = Get-HttpStatusCodeFromErrorRecord -ErrorRecord $_
        if ($statusCode -eq 404) {
            return 0
        }
        throw
    }
}

function Get-RabbitMqResourceNames([string] $ResourceType) {
    $names = @()
    try {
        $resources = @(
            Invoke-RabbitMqApi -Method GET -Path "$ResourceType/%2F" `
                -ManagementBaseUrl $RabbitMqManagementBaseUrl `
                -Username $RabbitMqUsername `
                -Password $RabbitMqPassword
        )
        foreach ($resource in $resources) {
            $name = Get-ObjectProperty -Object $resource -Names @('name')
            if (-not [string]::IsNullOrWhiteSpace([string]$name)) {
                $names += [string]$name
            }
        }
    } catch {
        throw
    }
    return $names
}

function New-BookingVerifyTopology([string] $RunId) {
    if ([string]::IsNullOrWhiteSpace($RunId)) {
        throw 'RunId must not be blank.'
    }
    $prefix = "verify.booking.$RunId"
    $topology = @{
        paymentSucceededQueue = "$prefix.payment-succeeded"
        paymentSucceededDeadLetterQueue = "$prefix.payment-succeeded.dlq"
        paymentSucceededRetryQueue = "$prefix.payment-succeeded.retry"
        paymentDeadLetterExchange = "$prefix.payment.dlx"
        paymentSucceededRetryExchange = "$prefix.payment.retry"
        paymentSucceededDeadLetterRoutingKey = "$prefix.payment-succeeded.dead"
        paymentSucceededRetryRoutingKey = "$prefix.payment-succeeded.retry"

        expirationDelayQueue = "$prefix.expiration.delay"
        expirationProcessingQueue = "$prefix.expiration"
        expirationDeadLetterQueue = "$prefix.expiration.dlq"
        expirationDelayExchange = "$prefix.expiration.delay.exchange"
        expirationProcessingExchange = "$prefix.expiration.processing.exchange"
        expirationDeadLetterExchange = "$prefix.expiration.dlx"
        expirationDelayRoutingKey = "$prefix.expiration.delay"
        expirationProcessingRoutingKey = "$prefix.expiration.process"
        expirationDeadLetterRoutingKey = "$prefix.expiration.dead"

        tripCancellationQueue = "$prefix.trip-cancellation"
        tripCancellationDeadLetterQueue = "$prefix.trip-cancellation.dlq"
        tripCancellationRetryQueue = "$prefix.trip-cancellation.retry"
        tripCancellationDeadLetterExchange = "$prefix.trip-cancellation.dlx"
        tripCancellationRetryExchange = "$prefix.trip-cancellation.retry.exchange"
        tripCancellationDeadLetterRoutingKey = "$prefix.trip-cancellation.dead"
        tripCancellationRetryRoutingKey = "$prefix.trip-cancellation.retry"
    }
    $topology.queues = @(
        $topology.paymentSucceededQueue,
        $topology.paymentSucceededDeadLetterQueue,
        $topology.paymentSucceededRetryQueue,
        $topology.expirationDelayQueue,
        $topology.expirationProcessingQueue,
        $topology.expirationDeadLetterQueue,
        $topology.tripCancellationQueue,
        $topology.tripCancellationDeadLetterQueue,
        $topology.tripCancellationRetryQueue
    )
    $topology.exchanges = @(
        $topology.paymentDeadLetterExchange,
        $topology.paymentSucceededRetryExchange,
        $topology.expirationDelayExchange,
        $topology.expirationProcessingExchange,
        $topology.expirationDeadLetterExchange,
        $topology.tripCancellationDeadLetterExchange,
        $topology.tripCancellationRetryExchange
    )
    return $topology
}

function Remove-BookingVerifyTopology([hashtable] $Topology) {
    if ($null -eq $Topology) {
        return @('Booking verification topology was not initialized.')
    }
    foreach ($queueName in @($Topology.queues)) {
        Remove-RabbitMqResource -ResourceType 'queue' `
            -ResourceName $queueName
    }
    foreach ($exchangeName in @($Topology.exchanges)) {
        Remove-RabbitMqResource -ResourceType 'exchange' `
            -ResourceName $exchangeName
    }

    $failures = @()
    foreach ($queueName in @($Topology.queues)) {
        if (-not (Test-RabbitMqResourceAbsent -ResourceType 'queue' `
                -ResourceName $queueName)) {
            $failures += "Queue still exists: $queueName"
        }
    }
    foreach ($exchangeName in @($Topology.exchanges)) {
        if (-not (Test-RabbitMqResourceAbsent -ResourceType 'exchange' `
                -ResourceName $exchangeName)) {
            $failures += "Exchange still exists: $exchangeName"
        }
    }
    return $failures
}

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------

function Invoke-BookingMavenPackage {
    param(
        [string] $WorkingDirectory,
        [string] $Label
    )

    # `clean` matters here: a stale target/classes can keep resources whose
    # source file has already been moved or deleted (a leftover MyBatis mapper
    # is enough to stop the service from starting), which would make this
    # acceptance run assert against a jar that no longer matches the source.
    Write-Host "Building $Label ..."
    Push-Location $WorkingDirectory
    try {
        & mvn -q -DskipTests clean package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package failed for $Label (exit $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }
}

# ---------------------------------------------------------------------------
# Nacos configuration
# ---------------------------------------------------------------------------

function Publish-BookingNacosConfigs {
    param(
        [string] $ProjectRoot,
        [string] $NacosBaseUrl,
        [string] $AdminPassword
    )

    Write-Step 'Publish Nacos configs (including school-bus-booking.yml)'
    $configDir = Join-Path $ProjectRoot 'cloud\nacos-config'
    $corePath = Join-Path $configDir 'school-bus-core.yml'
    $coreContent = Get-Content -Raw -LiteralPath $corePath
    if ($coreContent -notmatch '(?ms)booking:\s*\r?\n\s*embedded:\s*\r?\n\s*enabled:\s*false') {
        throw 'school-bus-core.yml must set school-bus.booking.embedded.enabled=false.'
    }

    $login = Invoke-NacosLogin -NacosBaseUrl $NacosBaseUrl `
        -AdminPassword $AdminPassword
    $token = $login.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw 'Nacos login did not return accessToken.'
    }

    $dataIds = @(
        'school-bus-core.yml',
        'school-bus-gateway.yml',
        'school-bus-transport-query.yml',
        'school-bus-iam.yml',
        'school-bus-payment.yml',
        'school-bus-booking.yml'
    )
    foreach ($dataId in $dataIds) {
        $content = Get-Content -Raw -LiteralPath (Join-Path $configDir $dataId)
        $publishUri = "$NacosBaseUrl/nacos/v3/admin/cs/config" +
            "?dataId=$([uri]::EscapeDataString($dataId))" +
            '&groupName=DEFAULT_GROUP' +
            "&content=$([uri]::EscapeDataString($content))"
        $result = Invoke-RestMethod -Method Post -Uri $publishUri `
            -Headers @{ accessToken = $token }
        if ($result.code -ne 0) {
            throw "Publish $dataId failed: $($result.message)"
        }
        Write-Host "Published $dataId"
    }
    return $token
}

# ---------------------------------------------------------------------------
# Seed data
# ---------------------------------------------------------------------------

function Remove-VerificationRows {
    $tripList = ($script:TripIds -join ',')
    $eventList = Get-SqlList @($script:EventIds)
    $aggregateList = Get-SqlList (
        @($script:BookingIds) + @($script:TripIds | ForEach-Object { "$_" })
    )
    $bookingList = Get-SqlList @($script:BookingNumbers)
    $accountClause = ''
    if (-not [string]::IsNullOrWhiteSpace($script:StudentNumber)) {
        $accountClause = @"
DELETE FROM iam_account_role
 WHERE account_id IN (
   SELECT id FROM iam_account
    WHERE student_number='$($script:StudentNumber)'
 );
DELETE FROM iam_account WHERE student_number='$($script:StudentNumber)';
"@
    }

    Invoke-MysqlExec @"
USE school_bus_platform;
DELETE FROM event_consumed WHERE event_id IN ($eventList);
DELETE FROM event_outbox WHERE event_id IN ($eventList);
DELETE FROM event_outbox
 WHERE context_name='booking' AND aggregate_id IN ($aggregateList);
DELETE FROM event_outbox
 WHERE context_name='payment'
   AND JSON_UNQUOTE(JSON_EXTRACT(payload, '`$.bookingNumber'))
       IN ($bookingList);
DELETE FROM booking_trip_cancellation_saga WHERE trip_id IN ($tripList);
DELETE FROM booking_order WHERE trip_id IN ($tripList);
DELETE FROM booking_trip_inventory WHERE trip_id IN ($tripList);
DELETE FROM transport_trip_seat WHERE trip_id IN ($tripList);
DELETE FROM transport_trip WHERE id IN ($tripList);
$accountClause
"@
}

function Get-ResidualRowCount {
    $tripList = ($script:TripIds -join ',')
    $eventList = Get-SqlList @($script:EventIds)
    $accountPredicate = "1=0"
    if (-not [string]::IsNullOrWhiteSpace($script:StudentNumber)) {
        $accountPredicate = "student_number='$($script:StudentNumber)'"
    }

    $counts = [ordered]@{
        transportTrip = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.transport_trip
 WHERE id IN ($tripList);
"@
        transportTripSeat = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.transport_trip_seat
 WHERE trip_id IN ($tripList);
"@
        bookingTripInventory = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.booking_trip_inventory
 WHERE trip_id IN ($tripList);
"@
        bookingOrder = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.booking_order
 WHERE trip_id IN ($tripList);
"@
        tripCancellationSaga = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.booking_trip_cancellation_saga
 WHERE trip_id IN ($tripList);
"@
        eventConsumed = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE event_id IN ($eventList);
"@
        eventOutbox = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_outbox
 WHERE event_id IN ($eventList)
    OR (context_name='booking' AND aggregate_id IN ($tripList));
"@
        iamAccount = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.iam_account
 WHERE $accountPredicate;
"@
    }
    return $counts
}

function New-VerificationTrip {
    param(
        [int] $TripId,
        [string] $DepartureInterval,
        [string] $DeadlineInterval
    )

    $tripNumber = [guid]::NewGuid().ToString()
    Invoke-MysqlExec @"
USE school_bus_platform;
INSERT INTO transport_trip (
  id, trip_no, vehicle_id, route_id, departure_time, booking_deadline,
  price, status, version, created_at, updated_at
) VALUES (
  $TripId, '$tripNumber', 9001, 9001,
  UTC_TIMESTAMP(3) + $DepartureInterval,
  UTC_TIMESTAMP(3) + $DeadlineInterval,
  5.50, 'OPEN_FOR_BOOKING', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
);
INSERT INTO transport_trip_seat (
  trip_id, seat_number, status, version, created_at, updated_at
) VALUES
  ($TripId, 'A1', 'AVAILABLE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  ($TripId, 'A2', 'AVAILABLE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  ($TripId, 'A3', 'AVAILABLE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  ($TripId, 'A4', 'AVAILABLE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO booking_trip_inventory (
  trip_id, total_seats, available_seats, version, created_at, updated_at
) VALUES ($TripId, 4, 4, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
"@
    return $tripNumber
}

# ---------------------------------------------------------------------------
# Booking API helpers
# ---------------------------------------------------------------------------

function New-BookingThroughGateway {
    param(
        [string] $TripNumber,
        [string] $SeatNumber,
        [string] $IdempotencyKey
    )

    [void]$script:RequestNumbers.Add($IdempotencyKey)
    $headers = @{
        Authorization = "Bearer $($script:AccessToken)"
        'Idempotency-Key' = $IdempotencyKey
    }
    $payload = @{
        tripNumber = $TripNumber
        seatNumber = $SeatNumber
    } | ConvertTo-Json -Compress
    $response = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/bookings" `
        -Method POST -Headers $headers -Body $payload
    Assert-HttpStatus -Response $response -Expected @(201) `
        -Label "Create booking on $TripNumber/$SeatNumber"

    $data = $response.body.data
    $bookingNumber = [string]$data.bookingNumber
    $bookingId = [string]$data.bookingId
    if ([string]::IsNullOrWhiteSpace($bookingNumber)) {
        throw 'Create booking response did not contain a bookingNumber.'
    }
    [void]$script:BookingNumbers.Add($bookingNumber)
    if (-not [string]::IsNullOrWhiteSpace($bookingId)) {
        [void]$script:BookingIds.Add($bookingId)
    }
    return [pscustomobject]@{
        response = $response
        bookingNumber = $bookingNumber
        bookingId = $bookingId
        replayed = (Get-HeaderValue -Headers $response.headers `
            -Name 'Idempotency-Replayed')
    }
}

if ($ImportOnly) {
    return
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$script:VerifyTopology = New-BookingVerifyTopology -RunId $runId
$script:BookingQueues = @($script:VerifyTopology.queues)
$logDir = Join-Path $projectRoot "target\booking-service-extraction-$runId"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$reportPath = Join-Path $logDir 'report.json'

$report = New-BookingExtractionReport -RunId $runId

try {
    $script:CurrentPhase = 'Assert-Java21'
    Assert-Java21
    $report.javaAvailable = $true

    $script:CurrentPhase = 'Assert-Docker'
    Assert-Docker
    $report.dockerAvailable = $true

    $script:CurrentPhase = 'Assert-InfraHealthy'
    Assert-InfraHealthy -NacosBaseUrl $NacosBaseUrl `
        -MysqlContainerName $MysqlContainerName
    $report.infraHealthy = $true

    $script:CurrentPhase = 'Assert-PortsFree'
    Assert-PortsFree -GatewayPort $GatewayPort -CorePort $CorePort `
        -QueryPorts @($QueryPort) -AdditionalPorts @($IamPort, $BookingPort)

    $script:CurrentPhase = 'RabbitMQ-port-check'
    if (-not (Test-TcpPort '127.0.0.1' 5672 1500)) {
        throw 'RabbitMQ is not listening on 5672.'
    }
    $report.rabbitMqReachable = $true

    $script:CurrentPhase = 'RabbitMQ-management-port-check'
    if (-not (Test-TcpPort '127.0.0.1' 15672 1500)) {
        throw 'RabbitMQ management API is not listening on 15672.'
    }
    $report.rabbitMqManagementReachable = $true

    # -- 1. Nacos configuration -------------------------------------------
    $script:CurrentPhase = 'nacos-config'
    $nacosToken = Publish-BookingNacosConfigs -ProjectRoot $projectRoot `
        -NacosBaseUrl $NacosBaseUrl -AdminPassword $AdminPassword

    # -- 2. Build ----------------------------------------------------------
    $script:CurrentPhase = 'maven-build'
    if (-not $SkipBuild) {
        Write-Step 'Build Core / Gateway / Transport Query / IAM / Booking'
        Invoke-BookingMavenPackage -WorkingDirectory $projectRoot -Label 'core'
        Invoke-BookingMavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\gateway-service') `
            -Label 'gateway'
        Invoke-BookingMavenPackage `
            -WorkingDirectory (
                Join-Path $projectRoot 'cloud\transport-query-service'
            ) `
            -Label 'transport-query'
        Invoke-BookingMavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\iam-service') `
            -Label 'iam'
        Invoke-BookingMavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\booking-service') `
            -Label 'booking'
    } else {
        $report.notes = @($report.notes) + @(
            'SkipBuild was set; existing boot jars were reused.'
        )
    }

    # `mvn clean` on Core removes the whole repository-level target directory,
    # which is where this run keeps its logs and report.
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null

    # -- 3. Start services -------------------------------------------------
    $script:CurrentPhase = 'service-startup'
    $coreJar = Find-BootJar (Join-Path $projectRoot 'target') `
        'school-bus-platform'
    $gatewayJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\gateway-service\target') `
        'school-bus-gateway'
    $queryJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\transport-query-service\target') `
        'school-bus-transport-query'
    $iamJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\iam-service\target') `
        'school-bus-iam'
    $bookingJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\booking-service\target') `
        'school-bus-booking'

    # Dev-only RSA keypair generated at runtime under cloud/dev-keys, which is
    # git-ignored. Only the public key location reaches non-IAM processes.
    $jwtKeys = Ensure-JwtKeys -ProjectRoot $projectRoot

    $commonEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
        JWT_ISSUER = 'https://school-bus.local'
        JWT_AUDIENCE = 'school-bus-api'
        JWT_PUBLIC_KEY_LOCATION = $jwtKeys.Public
    }

    $coreEnv = $commonEnv.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'local,cloud'
    $coreEnv['CORE_SERVER_PORT'] = "$CorePort"
    $coreEnv['SERVER_PORT'] = "$CorePort"
    Start-TrackedJava $coreJar $coreEnv `
        (Join-Path $logDir 'core.log') 'core' $logDir

    $queryEnv = $commonEnv.Clone()
    $queryEnv['TRANSPORT_QUERY_SERVER_PORT'] = "$QueryPort"
    $queryEnv['SERVER_PORT'] = "$QueryPort"
    $queryEnv['TRIP_LIST_CACHE_TTL'] = 'PT1S'
    Start-TrackedJava $queryJar $queryEnv `
        (Join-Path $logDir 'query.log') 'query' $logDir

    $iamEnv = $commonEnv.Clone()
    $iamEnv['JWT_PRIVATE_KEY_LOCATION'] = $jwtKeys.Private
    $iamEnv['IAM_SERVER_PORT'] = "$IamPort"
    $iamEnv['SERVER_PORT'] = "$IamPort"
    Start-TrackedJava $iamJar $iamEnv `
        (Join-Path $logDir 'iam.log') 'iam' $logDir

    $bookingEnv = $commonEnv.Clone()
    $bookingEnv['BOOKING_SERVER_PORT'] = "$BookingPort"
    $bookingEnv['SERVER_PORT'] = "$BookingPort"
    $bookingEnv['SCHOOL_BUS_BOOKING_WORKER_ID'] = '3'
    $bookingEnv['OUTBOX_RELAY_INITIAL_DELAY_MS'] = '2000'
    $bookingEnv['OUTBOX_RELAY_FIXED_DELAY_MS'] = '500'
    $bookingEnv['PAYMENT_DLX'] =
        $script:VerifyTopology.paymentDeadLetterExchange
    $bookingEnv['PAYMENT_SUCCEEDED_QUEUE'] =
        $script:VerifyTopology.paymentSucceededQueue
    $bookingEnv['PAYMENT_SUCCEEDED_DLQ'] =
        $script:VerifyTopology.paymentSucceededDeadLetterQueue
    $bookingEnv['PAYMENT_SUCCEEDED_DEAD_ROUTING_KEY'] =
        $script:VerifyTopology.paymentSucceededDeadLetterRoutingKey
    $bookingEnv['PAYMENT_SUCCEEDED_RETRY_EXCHANGE'] =
        $script:VerifyTopology.paymentSucceededRetryExchange
    $bookingEnv['PAYMENT_SUCCEEDED_RETRY_ROUTING_KEY'] =
        $script:VerifyTopology.paymentSucceededRetryRoutingKey
    $bookingEnv['PAYMENT_SUCCEEDED_RETRY_QUEUE'] =
        $script:VerifyTopology.paymentSucceededRetryQueue
    $bookingEnv['BOOKING_EXPIRATION_DELAY_EXCHANGE'] =
        $script:VerifyTopology.expirationDelayExchange
    $bookingEnv['BOOKING_EXPIRATION_DELAY_ROUTING_KEY'] =
        $script:VerifyTopology.expirationDelayRoutingKey
    $bookingEnv['BOOKING_EXPIRATION_DELAY_QUEUE'] =
        $script:VerifyTopology.expirationDelayQueue
    $bookingEnv['BOOKING_EXPIRATION_PROCESSING_EXCHANGE'] =
        $script:VerifyTopology.expirationProcessingExchange
    $bookingEnv['BOOKING_EXPIRATION_PROCESSING_ROUTING_KEY'] =
        $script:VerifyTopology.expirationProcessingRoutingKey
    $bookingEnv['BOOKING_EXPIRATION_PROCESSING_QUEUE'] =
        $script:VerifyTopology.expirationProcessingQueue
    $bookingEnv['BOOKING_EXPIRATION_DLX'] =
        $script:VerifyTopology.expirationDeadLetterExchange
    $bookingEnv['BOOKING_EXPIRATION_DEAD_ROUTING_KEY'] =
        $script:VerifyTopology.expirationDeadLetterRoutingKey
    $bookingEnv['BOOKING_EXPIRATION_DLQ'] =
        $script:VerifyTopology.expirationDeadLetterQueue
    $bookingEnv['TRIP_CANCELLATION_REQUESTED_QUEUE'] =
        $script:VerifyTopology.tripCancellationQueue
    $bookingEnv['TRIP_CANCELLATION_DLX'] =
        $script:VerifyTopology.tripCancellationDeadLetterExchange
    $bookingEnv['TRIP_CANCELLATION_DEAD_ROUTING_KEY'] =
        $script:VerifyTopology.tripCancellationDeadLetterRoutingKey
    $bookingEnv['TRIP_CANCELLATION_DLQ'] =
        $script:VerifyTopology.tripCancellationDeadLetterQueue
    $bookingEnv['TRIP_CANCELLATION_RETRY_EXCHANGE'] =
        $script:VerifyTopology.tripCancellationRetryExchange
    $bookingEnv['TRIP_CANCELLATION_REQUESTED_RETRY_ROUTING_KEY'] =
        $script:VerifyTopology.tripCancellationRetryRoutingKey
    $bookingEnv['TRIP_CANCELLATION_REQUESTED_RETRY_QUEUE'] =
        $script:VerifyTopology.tripCancellationRetryQueue
    # The payment window drives the per-message TTL on the expiration delay
    # queue, so it is shortened to keep the real delay -> dead-letter ->
    # processing chain inside this run. Bookings that are already settled when
    # their deadline message arrives are a no-op.
    $bookingEnv['BOOKING_PAYMENT_WINDOW'] = "PT${ExpirationWindowSeconds}S"
    # The database reconciliation job stays enabled but its first run is
    # pushed beyond this script's lifetime, so every expiration observed here
    # is proven to come from the RabbitMQ deadline chain.
    $bookingEnv['BOOKING_EXPIRATION_SCHEDULER_INITIAL_DELAY_MS'] = '3600000'
    Start-TrackedJava $bookingJar $bookingEnv `
        (Join-Path $logDir 'booking.log') 'booking' $logDir

    $gatewayEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
        GATEWAY_SERVER_PORT = "$GatewayPort"
        SERVER_PORT = "$GatewayPort"
        SPRING_CLOUD_LOADBALANCER_CACHE_TTL = '2s'
    }
    Start-TrackedJava $gatewayJar $gatewayEnv `
        (Join-Path $logDir 'gateway.log') 'gateway' $logDir

    Wait-HttpUp "http://127.0.0.1:$CorePort/actuator/health" `
        $StartupTimeoutSeconds 'Core'
    Wait-HttpUp "http://127.0.0.1:$QueryPort/actuator/health" `
        $StartupTimeoutSeconds 'Transport Query'
    Wait-HttpUp "http://127.0.0.1:$IamPort/actuator/health" `
        $StartupTimeoutSeconds 'IAM'
    Wait-HttpUp "http://127.0.0.1:$BookingPort/actuator/health" `
        $StartupTimeoutSeconds 'Booking'
    Wait-HttpUp "http://127.0.0.1:$GatewayPort/actuator/health" `
        $StartupTimeoutSeconds 'Gateway'

    # -- 4. Discovery ------------------------------------------------------
    $script:CurrentPhase = 'discovery-check'
    $bookingReady = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 1 `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-booking'
    $report.nacosBookingHealthy = ($bookingReady.snapshot.healthy -ge 1)
    if (-not $report.nacosBookingHealthy) {
        throw 'Nacos does not report a healthy school-bus-booking instance.'
    }
    foreach ($serviceName in @(
            'school-bus-core',
            'school-bus-transport-query',
            'school-bus-iam',
            'school-bus-gateway'
        )) {
        $null = Wait-NacosServiceHealthyCount `
            -AccessToken $nacosToken `
            -Expected 1 `
            -TimeoutSeconds $StartupTimeoutSeconds `
            -NacosBaseUrl $NacosBaseUrl `
            -ServiceName $serviceName
    }

    # -- 5. Ownership ------------------------------------------------------
    $script:CurrentPhase = 'ownership-check'
    $coreOwner = Get-ActuatorDetail `
        -BaseUrl "http://127.0.0.1:$CorePort" -DetailName 'bookingOwner'
    $bookingOwner = Get-ActuatorDetail `
        -BaseUrl "http://127.0.0.1:$BookingPort" -DetailName 'bookingOwner'
    $report.coreBookingOwner = $coreOwner
    $report.bookingServiceBookingOwner = $bookingOwner
    if ($coreOwner -ne 'disabled') {
        throw "Core bookingOwner expected disabled, got $coreOwner."
    }
    if ($bookingOwner -ne 'booking') {
        throw "Booking bookingOwner expected booking, got $bookingOwner."
    }
    $report.coreBookingEmbeddedDisabled = $true
    $report.bookingServiceOwnershipReported = $true

    # -- 6. Seed trips -----------------------------------------------------
    $script:CurrentPhase = 'seed-data'
    Remove-VerificationRows
    $tripA = New-VerificationTrip -TripId 9101 `
        -DepartureInterval 'INTERVAL 2 DAY' -DeadlineInterval 'INTERVAL 1 DAY'
    $tripB = New-VerificationTrip -TripId 9102 `
        -DepartureInterval 'INTERVAL 3 DAY' -DeadlineInterval 'INTERVAL 1 DAY'
    $tripD = New-VerificationTrip -TripId 9104 `
        -DepartureInterval 'INTERVAL 5 DAY' -DeadlineInterval 'INTERVAL 1 DAY'

    # -- 7. Authentication -------------------------------------------------
    $script:CurrentPhase = 'auth-setup'
    $gatewayBase = "http://127.0.0.1:$GatewayPort"
    $script:StudentNumber = 'B{0:D7}' -f (
        Get-Random -Minimum 1000000 -Maximum 9999999
    )
    $password = 'BookingVerify!2026'
    $credentials = @{
        studentNumber = $script:StudentNumber
        password = $password
    } | ConvertTo-Json -Compress

    $registration = Invoke-HttpCapture -Uri "$gatewayBase/api/v1/accounts" `
        -Method POST -Body $credentials
    Assert-HttpStatus -Response $registration -Expected @(201) `
        -Label 'Account registration'

    $login = Invoke-HttpCapture -Uri "$gatewayBase/api/v1/auth/login" `
        -Method POST -Body $credentials
    Assert-HttpStatus -Response $login -Expected @(200) -Label 'Login'
    $script:AccessToken = [string]$login.body.data.accessToken
    if ([string]::IsNullOrWhiteSpace($script:AccessToken)) {
        throw 'Login did not return an access token.'
    }
    $script:UserId = Get-JwtSubject -Token $script:AccessToken
    $authHeaders = @{ Authorization = "Bearer $($script:AccessToken)" }
    $report.authenticationEvidence = [ordered]@{
        registrationStatus = $registration.status
        loginStatus = $login.status
        subjectResolved = $true
    }

    # -- 8. Routing --------------------------------------------------------
    $script:CurrentPhase = 'routing-check'
    $routesResponse = Invoke-HttpCapture `
        -Uri "$gatewayBase/actuator/gateway/routes" -Method GET
    Assert-HttpStatus -Response $routesResponse -Expected @(200) `
        -Label 'Gateway route table'
    $routes = @($routesResponse.body)
    if ($routes.Count -eq 0) {
        throw 'Gateway returned an empty route table.'
    }
    $bookingRoute = $null
    $tripsRoute = $null
    foreach ($route in $routes) {
        $routeId = [string](Get-ObjectProperty -Object $route `
            -Names @('route_id', 'routeId', 'id'))
        if ($routeId -eq 'school-bus-booking-api') {
            $bookingRoute = $route
        }
        if ($routeId -eq 'school-bus-transport-query-trips') {
            $tripsRoute = $route
        }
    }
    if ($null -eq $bookingRoute) {
        throw 'Gateway does not expose the school-bus-booking-api route.'
    }
    if ($null -eq $tripsRoute) {
        throw 'Gateway does not expose the transport-query trips route.'
    }
    $bookingRouteUri = [string](Get-ObjectProperty -Object $bookingRoute `
        -Names @('uri'))
    if ($bookingRouteUri -notmatch '^lb://school-bus-booking/?$') {
        throw "Booking route targets $bookingRouteUri, not lb://school-bus-booking."
    }
    $bookingFilters = @(
        Get-ObjectProperty -Object $bookingRoute -Names @('filters')
    ) | ForEach-Object { [string]$_ }
    $bookingRouteHasRetry = [bool](
        @($bookingFilters | Where-Object { $_ -match 'Retry' }).Count
    )
    $tripsFilters = @(
        Get-ObjectProperty -Object $tripsRoute -Names @('filters')
    ) | ForEach-Object { [string]$_ }
    $tripsRouteHasRetry = [bool](
        @($tripsFilters | Where-Object { $_ -match 'Retry' }).Count
    )
    if ($bookingRouteHasRetry) {
        throw 'Booking route must not carry a Retry gateway filter.'
    }
    if (-not $tripsRouteHasRetry) {
        throw (
            'Transport Query trips route lost its Retry filter; the ' +
            'no-retry assertion for the booking route cannot be trusted.'
        )
    }

    $gatewayBookings = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings?page=0&size=5" `
        -Method GET -Headers $authHeaders
    Assert-HttpStatus -Response $gatewayBookings -Expected @(200) `
        -Label 'Gateway booking list'

    $coreBookings = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$CorePort/api/v1/bookings?page=0&size=5" `
        -Method GET -Headers $authHeaders
    Assert-HttpStatus -Response $coreBookings -Expected @(404) `
        -Label 'Core direct booking list'

    $directBookings = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$BookingPort/api/v1/bookings?page=0&size=5" `
        -Method GET -Headers $authHeaders
    Assert-HttpStatus -Response $directBookings -Expected @(200) `
        -Label 'Booking service direct list'

    $report.routingEvidence = [ordered]@{
        bookingRouteUri = $bookingRouteUri
        bookingRouteFilters = $bookingFilters
        bookingRouteHasRetryFilter = $bookingRouteHasRetry
        transportQueryTripsRouteHasRetryFilter = $tripsRouteHasRetry
        gatewayBookingListStatus = $gatewayBookings.status
        coreDirectBookingListStatus = $coreBookings.status
        bookingServiceDirectListStatus = $directBookings.status
    }
    $report.gatewayRoutesBookingToService = $true

    # -- 9. Booking lifecycle ---------------------------------------------
    $script:CurrentPhase = 'booking-e2e'
    $trips = Invoke-HttpCapture -Uri "$gatewayBase/api/v1/trips?limit=50" `
        -Method GET -Headers $authHeaders
    Assert-HttpStatus -Response $trips -Expected @(200) -Label 'Trip list'
    $tripNumbers = @($trips.body.data | ForEach-Object { [string]$_.tripNumber })
    if ($tripNumbers -notcontains $tripA) {
        throw "Seeded trip $tripA was not visible through GET /api/v1/trips."
    }

    $seats = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/trips/$tripA/seats" `
        -Method GET -Headers $authHeaders
    Assert-HttpStatus -Response $seats -Expected @(200) -Label 'Seat map'
    $availableSeat = @(
        $seats.body.data.seats |
            Where-Object { [string]$_.status -eq 'AVAILABLE' } |
            ForEach-Object { [string]$_.seatNumber }
    ) | Select-Object -First 1
    if ($availableSeat -ne 'A1') {
        throw "Expected seat A1 to be AVAILABLE, got '$availableSeat'."
    }

    $idempotencyKey = "bk-e2e-$runId"
    $created = New-BookingThroughGateway -TripNumber $tripA `
        -SeatNumber 'A1' -IdempotencyKey $idempotencyKey
    $bookingNumber = $created.bookingNumber
    if ($created.replayed -ne 'false') {
        throw "First create returned Idempotency-Replayed=$($created.replayed)."
    }

    $replayHeaders = @{
        Authorization = "Bearer $($script:AccessToken)"
        'Idempotency-Key' = $idempotencyKey
    }
    $replayBody = @{ tripNumber = $tripA; seatNumber = 'A1' } |
        ConvertTo-Json -Compress
    $replay = Invoke-HttpCapture -Uri "$gatewayBase/api/v1/bookings" `
        -Method POST -Headers $replayHeaders -Body $replayBody
    Assert-HttpStatus -Response $replay -Expected @(201) `
        -Label 'Idempotent booking replay'
    if ([string]$replay.body.data.bookingNumber -ne $bookingNumber) {
        throw 'Idempotent replay returned a different bookingNumber.'
    }
    $replayFlag = Get-HeaderValue -Headers $replay.headers `
        -Name 'Idempotency-Replayed'
    if ($replayFlag -ne 'true') {
        throw "Replay returned Idempotency-Replayed=$replayFlag, expected true."
    }

    $rowsForRequest = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.booking_order
 WHERE request_no='$idempotencyKey';
"@
    if ($rowsForRequest -ne 1) {
        throw "Expected exactly 1 booking_order row, got $rowsForRequest."
    }

    $list = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings?page=0&size=20" `
        -Method GET -Headers $authHeaders
    Assert-HttpStatus -Response $list -Expected @(200) -Label 'Booking list'
    $listedNumbers = @(
        $list.body.data.items | ForEach-Object { [string]$_.bookingNumber }
    )
    if ($listedNumbers -notcontains $bookingNumber) {
        throw 'Created booking was missing from GET /api/v1/bookings.'
    }

    $detail = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings/$bookingNumber" `
        -Method GET -Headers $authHeaders
    Assert-HttpStatus -Response $detail -Expected @(200) -Label 'Booking detail'
    if ([string]$detail.body.data.status -ne 'PENDING_PAYMENT' -or
        [string]$detail.body.data.seatNumber -ne 'A1' -or
        [string]$detail.body.data.tripNumber -ne $tripA) {
        throw 'Booking detail did not match the created booking.'
    }

    $lockedSeatStatus = Get-SeatStatus -TripId 9101 -SeatNumber 'A1'
    $reservedInventory = Get-AvailableSeats -TripId 9101
    if ($lockedSeatStatus -ne 'LOCKED' -or $reservedInventory -ne 3) {
        throw (
            "After create expected seat LOCKED and 3 available seats, got " +
            "$lockedSeatStatus / $reservedInventory."
        )
    }

    $cancellation = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings/$bookingNumber/cancellation" `
        -Method POST -Headers $authHeaders
    Assert-HttpStatus -Response $cancellation -Expected @(200) `
        -Label 'Booking cancellation'

    $cancelledSnapshot = Get-BookingOrderSnapshot -BookingNumber $bookingNumber
    $releasedSeatStatus = Get-SeatStatus -TripId 9101 -SeatNumber 'A1'
    $releasedInventory = Get-AvailableSeats -TripId 9101
    if ($cancelledSnapshot.status -ne 'CANCELLED' -or
        $cancelledSnapshot.cancelReason -ne 'USER_CANCELLED' -or
        $releasedSeatStatus -ne 'AVAILABLE' -or
        $releasedInventory -ne 4) {
        throw (
            "Cancellation did not settle: status=$($cancelledSnapshot.status), " +
            "reason=$($cancelledSnapshot.cancelReason), " +
            "seat=$releasedSeatStatus, available=$releasedInventory."
        )
    }

    $report.bookingEvidence = [ordered]@{
        tripNumber = $tripA
        seatNumber = 'A1'
        bookingNumber = $bookingNumber
        idempotencyReplayedHeader = $replayFlag
        bookingOrderRowsForRequest = $rowsForRequest
        seatStatusAfterCreate = $lockedSeatStatus
        availableSeatsAfterCreate = $reservedInventory
        statusAfterCancellation = $cancelledSnapshot.status
        cancelReasonAfterCancellation = $cancelledSnapshot.cancelReason
        seatStatusAfterCancellation = $releasedSeatStatus
        availableSeatsAfterCancellation = $releasedInventory
    }
    $report.createBookingVerified = $true
    $report.gatewayBookingRouteHasNoRetry = $true

    # -- 10. Unauthenticated access ---------------------------------------
    $script:CurrentPhase = 'unauthenticated-check'
    $anonymousGet = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings?page=0&size=5" -Method GET
    Assert-HttpStatus -Response $anonymousGet -Expected @(401) `
        -Label 'Unauthenticated booking list'
    $anonymousPost = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings" -Method POST `
        -Headers @{ 'Idempotency-Key' = "bk-anon-$runId" } `
        -Body (@{ tripNumber = $tripA; seatNumber = 'A2' } |
            ConvertTo-Json -Compress)
    Assert-HttpStatus -Response $anonymousPost -Expected @(401) `
        -Label 'Unauthenticated booking create'
    $anonymousRows = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.booking_order
 WHERE request_no='bk-anon-$runId';
"@
    if ($anonymousRows -ne 0) {
        throw 'Unauthenticated create must not persist a booking_order row.'
    }
    $report.unauthenticatedBookingRejected = $true

    # -- 11. PaymentSucceeded ownership ------------------------------------
    $script:CurrentPhase = 'payment-succeeded'
    $paidBooking = New-BookingThroughGateway -TripNumber $tripB `
        -SeatNumber 'A1' -IdempotencyKey "bk-paid-$runId"
    $paidNumber = $paidBooking.bookingNumber

    $paymentEventId = [guid]::NewGuid().ToString()
    [void]$script:EventIds.Add($paymentEventId)
    $paymentNumber = [guid]::NewGuid().ToString()
    $paidAt = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
    $paymentPayload = @"
{"schemaVersion":1,"paymentNumber":"$paymentNumber","bookingNumber":"$paidNumber","amount":5.50,"paidAt":"$paidAt","occurredAt":"$paidAt"}
"@
    Publish-RabbitMqExchangeMessage -Exchange 'schoolbus.payment.events' `
        -RoutingKey 'payment.succeeded' `
        -Payload $paymentPayload.Trim() -MessageId $paymentEventId

    Wait-Until -TimeoutSeconds 90 -Description 'PaymentSucceeded consumption' {
        $snapshot = Get-BookingOrderSnapshot -BookingNumber $paidNumber
        return ($snapshot.status -eq 'PAID')
    } | Out-Null

    $paidSnapshot = Get-BookingOrderSnapshot -BookingNumber $paidNumber
    $paidSeatStatus = Get-SeatStatus -TripId 9102 -SeatNumber 'A1'
    if ($paidSeatStatus -ne 'SOLD') {
        throw "PaymentSucceeded left the seat as $paidSeatStatus, expected SOLD."
    }

    $consumerRows = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE event_id='$paymentEventId';
"@
    $consumerName = Invoke-MysqlScalar @"
SELECT consumer_name FROM school_bus_platform.event_consumed
 WHERE event_id='$paymentEventId';
"@
    if ($consumerRows -ne 1) {
        throw (
            "Expected exactly 1 event_consumed row for the PaymentSucceeded " +
            "event, got $consumerRows."
        )
    }
    if ($consumerName -ne 'booking-payment-succeeded-consumer') {
        throw "PaymentSucceeded was consumed by '$consumerName'."
    }

    Publish-RabbitMqExchangeMessage -Exchange 'schoolbus.payment.events' `
        -RoutingKey 'payment.succeeded' `
        -Payload $paymentPayload.Trim() -MessageId $paymentEventId
    Start-Sleep -Seconds 8

    $consumerRowsAfterReplay = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE event_id='$paymentEventId';
"@
    $paidSnapshotAfterReplay = Get-BookingOrderSnapshot `
        -BookingNumber $paidNumber
    $succeededQueueDepth = Get-RabbitMqQueueDepthSafe `
        -QueueName $script:VerifyTopology.paymentSucceededQueue
    if ($consumerRowsAfterReplay -ne 1 -or
        $paidSnapshotAfterReplay.version -ne $paidSnapshot.version -or
        $paidSnapshotAfterReplay.updatedAt -ne $paidSnapshot.updatedAt -or
        $succeededQueueDepth -ne 0) {
        throw (
            "PaymentSucceeded redelivery was not idempotent: rows=" +
            "$consumerRowsAfterReplay, version=" +
            "$($paidSnapshotAfterReplay.version), queue=$succeededQueueDepth."
        )
    }

    $report.paymentSucceededEvidence = [ordered]@{
        bookingNumber = $paidNumber
        eventId = $paymentEventId
        statusAfterConsumption = $paidSnapshot.status
        seatStatusAfterConsumption = $paidSeatStatus
        eventConsumedRows = $consumerRows
        eventConsumedRowsAfterReplay = $consumerRowsAfterReplay
        consumerName = $consumerName
        queueDepthAfterReplay = $succeededQueueDepth
    }
    $report.paymentSucceededConsumedByBookingOnly = $true

    # -- 12. Booking expiration -------------------------------------------
    $script:CurrentPhase = 'booking-expiration'
    $tripC = New-VerificationTrip -TripId 9103 `
        -DepartureInterval 'INTERVAL 4 DAY' `
        -DeadlineInterval "INTERVAL $ExpirationWindowSeconds SECOND"
    $expiringBooking = New-BookingThroughGateway -TripNumber $tripC `
        -SeatNumber 'A1' -IdempotencyKey "bk-expire-$runId"
    $expiringNumber = $expiringBooking.bookingNumber
    $expiringId = $expiringBooking.bookingId

    Wait-Until -TimeoutSeconds 60 -PollSeconds 1 `
        -Description 'booking deadline outbox publication' {
        $status = Invoke-MysqlScalar @"
SELECT status FROM school_bus_platform.event_outbox
 WHERE context_name='booking'
   AND event_type='BookingPaymentDeadlineReached'
   AND aggregate_id='$expiringId';
"@
        return ($status -eq 'PUBLISHED')
    } | Out-Null

    $expirationEventId = Invoke-MysqlScalar @"
SELECT event_id FROM school_bus_platform.event_outbox
 WHERE context_name='booking'
   AND event_type='BookingPaymentDeadlineReached'
   AND aggregate_id='$expiringId';
"@
    $expirationPayload = Invoke-MysqlScalar @"
SELECT CAST(payload AS CHAR) FROM school_bus_platform.event_outbox
 WHERE event_id='$expirationEventId';
"@
    if ([string]::IsNullOrWhiteSpace($expirationPayload)) {
        throw 'Booking expiration outbox payload could not be read.'
    }
    [void]$script:EventIds.Add($expirationEventId)

    $expirationTrigger = 'delay-queue'
    $expired = $false
    $deadline = (Get-Date).AddSeconds($ExpirationWindowSeconds + 45)
    while ((Get-Date) -lt $deadline) {
        $snapshot = Get-BookingOrderSnapshot -BookingNumber $expiringNumber
        if ($snapshot.status -eq 'CANCELLED') {
            $expired = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $expired) {
        # The delay message may have been delivered a few milliseconds before
        # the deadline and acknowledged as a no-op. Re-publishing the same
        # deadline event onto the processing queue is still the message path.
        $expirationTrigger = 'republished-deadline-message'
        Publish-RabbitMqDefaultExchangeMessage `
            -RoutingKey $script:VerifyTopology.expirationProcessingQueue `
            -Payload $expirationPayload -MessageId $expirationEventId
        Wait-Until -TimeoutSeconds 60 -PollSeconds 2 `
            -Description 'booking expiration after redelivery' {
            $snapshot = Get-BookingOrderSnapshot -BookingNumber $expiringNumber
            return ($snapshot.status -eq 'CANCELLED')
        } | Out-Null
    }

    $expiredSnapshot = Get-BookingOrderSnapshot -BookingNumber $expiringNumber
    $expiredSeatStatus = Get-SeatStatus -TripId 9103 -SeatNumber 'A1'
    $expiredInventory = Get-AvailableSeats -TripId 9103
    if ($expiredSnapshot.cancelReason -ne 'PAYMENT_TIMEOUT' -or
        $expiredSeatStatus -ne 'AVAILABLE' -or
        $expiredInventory -ne 4) {
        throw (
            "Expiration did not settle: reason=" +
            "$($expiredSnapshot.cancelReason), seat=$expiredSeatStatus, " +
            "available=$expiredInventory."
        )
    }

    Publish-RabbitMqDefaultExchangeMessage `
        -RoutingKey $script:VerifyTopology.expirationProcessingQueue `
        -Payload $expirationPayload -MessageId $expirationEventId
    Start-Sleep -Seconds 8
    $expiredSnapshotAfterReplay = Get-BookingOrderSnapshot `
        -BookingNumber $expiringNumber
    $expirationQueueDepth = Get-RabbitMqQueueDepthSafe `
        -QueueName $script:VerifyTopology.expirationProcessingQueue
    $expirationDlqDepth = Get-RabbitMqQueueDepthSafe `
        -QueueName $script:VerifyTopology.expirationDeadLetterQueue
    if ($expiredSnapshotAfterReplay.version -ne $expiredSnapshot.version -or
        $expiredSnapshotAfterReplay.updatedAt -ne $expiredSnapshot.updatedAt -or
        $expiredSnapshotAfterReplay.status -ne 'CANCELLED' -or
        $expirationQueueDepth -ne 0 -or
        $expirationDlqDepth -ne 0) {
        throw (
            "Expiration redelivery was not idempotent: version=" +
            "$($expiredSnapshotAfterReplay.version), queue=" +
            "$expirationQueueDepth, dlq=$expirationDlqDepth."
        )
    }

    $report.expirationEvidence = [ordered]@{
        tripNumber = $tripC
        bookingNumber = $expiringNumber
        outboxEventId = $expirationEventId
        outboxStatus = 'PUBLISHED'
        delayQueue = $script:VerifyTopology.expirationDelayQueue
        trigger = $expirationTrigger
        statusAfterExpiration = $expiredSnapshot.status
        cancelReasonAfterExpiration = $expiredSnapshot.cancelReason
        seatStatusAfterExpiration = $expiredSeatStatus
        availableSeatsAfterExpiration = $expiredInventory
        versionBeforeRedelivery = $expiredSnapshot.version
        versionAfterRedelivery = $expiredSnapshotAfterReplay.version
        processingQueueDepthAfterRedelivery = $expirationQueueDepth
        deadLetterQueueDepthAfterRedelivery = $expirationDlqDepth
        databaseReconciliationSchedulerDeferred = $true
    }
    $report.notes = @($report.notes) + @(
        'Booking expiration has no event_consumed row by design; idempotency ' +
        'is proven by an unchanged booking_order version and updated_at ' +
        'after redelivery.'
    )
    $report.bookingExpirationVerified = $true

    # -- 13. Trip cancellation settlement ----------------------------------
    $script:CurrentPhase = 'trip-cancellation'
    $cancellingBooking = New-BookingThroughGateway -TripNumber $tripD `
        -SeatNumber 'A1' -IdempotencyKey "bk-tripcancel-$runId"
    $cancellingNumber = $cancellingBooking.bookingNumber

    $cancellationEventId = [guid]::NewGuid().ToString()
    [void]$script:EventIds.Add($cancellationEventId)
    $requestedAt = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
    $cancellationPayload =
        "{`"tripId`":9104,`"tripVersion`":1,`"requestedAt`":`"$requestedAt`"}"
    Publish-RabbitMqExchangeMessage -Exchange 'schoolbus.transport.events' `
        -RoutingKey 'trip.cancellation.requested' `
        -Payload $cancellationPayload -MessageId $cancellationEventId

    Wait-Until -TimeoutSeconds 90 -Description 'trip cancellation settlement' {
        $snapshot = Get-BookingOrderSnapshot -BookingNumber $cancellingNumber
        return ($snapshot.status -eq 'CANCELLED')
    } | Out-Null

    $tripCancelSnapshot = Get-BookingOrderSnapshot `
        -BookingNumber $cancellingNumber
    $tripCancelSeatStatus = Get-SeatStatus -TripId 9104 -SeatNumber 'A1'
    $tripCancelInventory = Get-AvailableSeats -TripId 9104
    $sagaStatus = Invoke-MysqlScalar @"
SELECT status FROM school_bus_platform.booking_trip_cancellation_saga
 WHERE trip_id=9104;
"@
    $settlementOutboxRows = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_outbox
 WHERE context_name='booking'
   AND event_type='TripCancellationBookingsSettled'
   AND aggregate_id='9104';
"@
    $cancellationConsumedRows = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE event_id='$cancellationEventId';
"@
    $cancellationConsumer = Invoke-MysqlScalar @"
SELECT consumer_name FROM school_bus_platform.event_consumed
 WHERE event_id='$cancellationEventId';
"@

    if ($tripCancelSnapshot.cancelReason -ne 'TRIP_CANCELLED' -or
        $tripCancelSeatStatus -ne 'AVAILABLE' -or
        $tripCancelInventory -ne 4 -or
        $sagaStatus -ne 'SETTLED' -or
        $settlementOutboxRows -ne 1 -or
        $cancellationConsumedRows -ne 1 -or
        $cancellationConsumer -ne 'booking-trip-cancellation-requested-consumer') {
        throw (
            "Trip cancellation settlement failed: reason=" +
            "$($tripCancelSnapshot.cancelReason), seat=$tripCancelSeatStatus, " +
            "available=$tripCancelInventory, saga=$sagaStatus, " +
            "settlementOutboxRows=$settlementOutboxRows, " +
            "consumedRows=$cancellationConsumedRows, " +
            "consumer=$cancellationConsumer."
        )
    }

    Publish-RabbitMqExchangeMessage -Exchange 'schoolbus.transport.events' `
        -RoutingKey 'trip.cancellation.requested' `
        -Payload $cancellationPayload -MessageId $cancellationEventId
    Start-Sleep -Seconds 8
    $cancellationConsumedAfterReplay = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_consumed
 WHERE event_id='$cancellationEventId';
"@
    $settlementOutboxAfterReplay = Invoke-MysqlCount @"
SELECT COUNT(*) FROM school_bus_platform.event_outbox
 WHERE context_name='booking'
   AND event_type='TripCancellationBookingsSettled'
   AND aggregate_id='9104';
"@
    $cancellationQueueDepth = Get-RabbitMqQueueDepthSafe `
        -QueueName $script:VerifyTopology.tripCancellationQueue
    if ($cancellationConsumedAfterReplay -ne 1 -or
        $settlementOutboxAfterReplay -ne 1 -or
        $cancellationQueueDepth -ne 0) {
        throw (
            "Trip cancellation redelivery was not idempotent: consumedRows=" +
            "$cancellationConsumedAfterReplay, settlementRows=" +
            "$settlementOutboxAfterReplay, queue=$cancellationQueueDepth."
        )
    }

    $report.tripCancellationEvidence = [ordered]@{
        tripNumber = $tripD
        bookingNumber = $cancellingNumber
        eventId = $cancellationEventId
        statusAfterSettlement = $tripCancelSnapshot.status
        cancelReasonAfterSettlement = $tripCancelSnapshot.cancelReason
        seatStatusAfterSettlement = $tripCancelSeatStatus
        availableSeatsAfterSettlement = $tripCancelInventory
        sagaStatus = $sagaStatus
        settlementOutboxRows = $settlementOutboxRows
        eventConsumedRows = $cancellationConsumedRows
        eventConsumedRowsAfterReplay = $cancellationConsumedAfterReplay
        consumerName = $cancellationConsumer
        queueDepthAfterReplay = $cancellationQueueDepth
    }
    $report.tripCancellationSettlementVerified = $true
}
catch {
    $script:OriginalFailure = $_
    $report.notes = @($report.notes) + @($_.Exception.Message)
    $report.failedInPhase = $script:CurrentPhase
    if (Test-BookingEnvironmentBlockedPhase -Phase $script:CurrentPhase) {
        $report.environmentBlocked = $true
    }
}
finally {
    $cleanupNotes = Invoke-VerificationCleanup -Steps @(
        {
            $script:AccessToken = $null
            Stop-TrackedProcesses
            return $null
        },
        { Stop-ServiceByPortSafe -Port $GatewayPort -Label 'gateway' },
        { Stop-ServiceByPortSafe -Port $CorePort -Label 'core' },
        { Stop-ServiceByPortSafe -Port $QueryPort -Label 'transport-query' },
        { Stop-ServiceByPortSafe -Port $IamPort -Label 'iam' },
        { Stop-ServiceByPortSafe -Port $BookingPort -Label 'booking' },
        {
            if (-not $report.dockerAvailable) {
                return 'Cleanup skipped: Docker was unavailable.'
            }
            Remove-VerificationRows
            $residual = Get-ResidualRowCount
            $total = 0
            foreach ($key in $residual.Keys) {
                $total += [int]$residual[$key]
            }
            $report.cleanupEvidence = [ordered]@{
                residualRows = $residual
                residualTotal = $total
            }
            if ($total -eq 0) {
                $report.temporaryDataCleaned = $true
            } else {
                $report.notes = @($report.notes) + @(
                    "Temporary rows remain after cleanup: $total"
                )
            }
            return $null
        },
        {
            if (-not $report.rabbitMqManagementReachable) {
                return 'Topology check skipped: RabbitMQ management ' +
                    'API was unreachable.'
            }
            $failures = @()
            $depths = [ordered]@{}
            foreach ($queueName in $script:BookingQueues) {
                $depth = Get-RabbitMqQueueDepthSafe -QueueName $queueName
                $depths[$queueName] = $depth
            }
            $failures += @(Remove-BookingVerifyTopology `
                -Topology $script:VerifyTopology)
            $report.cleanupEvidence['queueDepths'] = $depths
            $report.cleanupEvidence['removedQueues'] =
                @($script:VerifyTopology.queues)
            $report.cleanupEvidence['removedExchanges'] =
                @($script:VerifyTopology.exchanges)
            if ($failures.Count -eq 0) {
                $report.temporaryTopologyCleaned = $true
            } else {
                $report.notes = @($report.notes) + $failures
            }
            return $null
        }
    )

    foreach ($note in @($cleanupNotes)) {
        if (-not [string]::IsNullOrWhiteSpace($note)) {
            $report.notes = @($report.notes) + @($note)
        }
    }

    $resolved = Resolve-BookingExtractionStatus -Report $report
    $report.status = $resolved.status
    $report.failureCategory = $resolved.failureCategory
    Write-BookingExtractionReport -Report $report -ReportPath $reportPath

    if ($null -ne $script:OriginalFailure) {
        Write-Host ('Original failure: ' +
            $script:OriginalFailure.Exception.Message) -ForegroundColor Red
    }
    if ($report.status -ne 'PASSED') {
        exit 1
    }
}

#Requires -Version 5.1
param(
    [string] $NacosBaseUrl = 'http://127.0.0.1:8848',
    [string] $AdminPassword = 'nacos',
    [string] $MysqlContainerName = 'school-bus-mysql',
    [int] $CorePort = 8081,
    [int] $GatewayPort = 8080,
    [int] $IamPort = 8084,
    [int] $PaymentPort = 8085,
    [int] $BookingPort = 8087,
    [int] $QueryPort = 8082,
    [int] $StartupTimeoutSeconds = 180,
    [switch] $SkipBuild,
    [switch] $ImportOnly
)

<#
.SYNOPSIS
  Verifies Booking↔Payment event-driven decoupling (RefundRequested / PaymentRefunded).
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1')
. (Join-Path $PSScriptRoot 'payment-refund-messaging.helpers.ps1')

$script:DecouplingRequiredFlags = @(
    'javaAvailable',
    'dockerAvailable',
    'infraHealthy',
    'portsFree',
    'servicesStarted',
    'paymentEventMode',
    'bookingPaidViaEvent',
    'paymentDidNotWriteBookingOrder',
    'refundRequestedOutboxWritten',
    'paymentRefunded',
    'bookingRefundedViaEvent',
    'bookingHasNoPaymentRecordSql'
)

$script:DecouplingEnvironmentPhases = @(
    'Assert-Java21',
    'Assert-Docker',
    'Assert-InfraHealthy',
    'Assert-PortsFree',
    'RabbitMQ-port-check',
    'RabbitMQ-management-port-check'
)

function New-BookingPaymentDecouplingReport([string] $RunId) {
    return [ordered]@{
        runId = $RunId
        status = 'FAILED'
        failureCategory = 'verification_not_executed'
        notes = [System.Collections.Generic.List[string]]::new()
        failedInPhase = $null
        environmentBlocked = $false
        javaAvailable = $false
        dockerAvailable = $false
        infraHealthy = $false
        portsFree = $false
        servicesStarted = $false
        paymentEventMode = $false
        bookingPaidViaEvent = $false
        paymentDidNotWriteBookingOrder = $false
        refundRequestedOutboxWritten = $false
        paymentRefunded = $false
        bookingRefundedViaEvent = $false
        bookingHasNoPaymentRecordSql = $false
        evidence = [ordered]@{}
        temporaryDataCleaned = $false
        temporaryTopologyCleaned = $null
    }
}

function Test-DecouplingEnvironmentBlockedPhase([string] $Phase) {
    return $script:DecouplingEnvironmentPhases -contains $Phase
}

function Resolve-BookingPaymentDecouplingStatus([hashtable] $Report) {
    if ($Report.environmentBlocked -eq $true -or (
            $null -ne $Report.failedInPhase -and
            (Test-DecouplingEnvironmentBlockedPhase -Phase $Report.failedInPhase)
        )) {
        return [pscustomobject]@{
            status = 'BLOCKED'
            failureCategory = 'environment_blocked'
        }
    }

    $missing = @()
    foreach ($flag in $script:DecouplingRequiredFlags) {
        if ($Report[$flag] -ne $true) {
            $missing += $flag
        }
    }
    if ($missing.Count -eq 0) {
        return [pscustomobject]@{
            status = 'PASSED'
            failureCategory = 'verification_succeeded'
        }
    }

    $businessProgress = @(
        @(
            'bookingPaidViaEvent',
            'refundRequestedOutboxWritten',
            'paymentRefunded',
            'bookingRefundedViaEvent'
        ) | Where-Object { $Report[$_] -eq $true }
    )

    if ($businessProgress.Count -gt 0 -and $missing.Count -gt 0) {
        return [pscustomobject]@{
            status = 'PARTIAL'
            failureCategory = 'partial_verification'
        }
    }

    if ($null -eq $Report.failedInPhase -and $businessProgress.Count -eq 0) {
        return [pscustomobject]@{
            status = 'FAILED'
            failureCategory = 'verification_not_executed'
        }
    }

    return [pscustomobject]@{
        status = 'FAILED'
        failureCategory = 'verification_failed'
    }
}

function Test-BookingHasNoPaymentRecordSql {
    $root = Join-Path $projectRoot 'cloud\booking-service\src\main'
    $offenders = @()
    Get-ChildItem -Path $root -Recurse -Include *.java,*.xml -File |
        ForEach-Object {
            $text = Get-Content -LiteralPath $_.FullName -Raw
            if ($text -match 'payment_record' -or
                $text -match 'PaymentRefundLookupMapper') {
                $offenders += $_.FullName
            }
        }
    return @{
        ok = ($offenders.Count -eq 0)
        offenders = $offenders
    }
}

if ($ImportOnly) {
    return
}

$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:BookingNumber = $null
$script:PaymentNumber = $null
$script:TripId = $null
$script:StudentNumber = $null
$failure = $null
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $projectRoot "target\booking-payment-event-decoupling-$runId"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$report = New-BookingPaymentDecouplingReport -RunId $runId

function Invoke-Mysql([string] $Sql, [switch] $ReturnRows) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($ReturnRows) {
            $rows = @(
                docker exec -e MYSQL_PWD=root $MysqlContainerName `
                    mysql -uroot -N -e $Sql 2>$null
            )
            if ($LASTEXITCODE -ne 0) {
                throw 'MySQL query failed.'
            }
            return $rows
        }
        $output = @(
            docker exec -e MYSQL_PWD=root $MysqlContainerName `
                mysql -uroot -e $Sql 2>&1
        )
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL command failed: $($output -join ' ')"
        }
    } finally {
        $ErrorActionPreference = $previous
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

function New-PaymentSignature([string] $Body, [string] $Secret) {
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [Text.Encoding]::UTF8.GetBytes($Secret)
    )
    try {
        $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($Body))
        $hex = ([BitConverter]::ToString($hash) -replace '-', '').ToLowerInvariant()
        return 'sha256=' + $hex
    } finally {
        $hmac.Dispose()
    }
}

function Invoke-HttpCapture {
    param(
        [string] $Uri,
        [ValidateSet('GET', 'POST', 'PUT', 'DELETE')]
        [string] $Method = 'GET',
        [hashtable] $Headers = @{},
        [string] $Body = $null,
        [int] $TimeoutSeconds = 30
    )
    $parameters = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        UseBasicParsing = $true
        TimeoutSec = $TimeoutSeconds
    }
    if (-not [string]::IsNullOrEmpty($Body)) {
        $parameters['ContentType'] = 'application/json'
        $parameters['Body'] = $Body
    }
    try {
        $response = Invoke-WebRequest @parameters
        $content = if ($response.Content -is [byte[]]) {
            [Text.Encoding]::UTF8.GetString($response.Content)
        } else {
            [string]$response.Content
        }
        $parsed = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            $parsed = $content | ConvertFrom-Json
        }
        return [pscustomobject]@{
            status = [int]$response.StatusCode
            body = $parsed
            raw = $content
        }
    } catch {
        $status = 0
        $raw = $_.Exception.Message
        $parsed = $null
        try {
            $httpResponse = $_.Exception.Response
            if ($null -ne $httpResponse) {
                $status = [int]$httpResponse.StatusCode.value__
                $stream = $httpResponse.GetResponseStream()
                if ($null -ne $stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $raw = $reader.ReadToEnd()
                    $reader.Dispose()
                    if (-not [string]::IsNullOrWhiteSpace($raw)) {
                        $parsed = $raw | ConvertFrom-Json
                    }
                }
            }
        } catch {
            # keep fallback message
        }
        return [pscustomobject]@{
            status = $status
            body = $parsed
            raw = $raw
        }
    }
}

function Publish-DecouplingNacosConfigs {
    param(
        [string] $ProjectRoot,
        [string] $NacosBaseUrl,
        [string] $AdminPassword
    )
    $login = Invoke-NacosLogin -NacosBaseUrl $NacosBaseUrl `
        -AdminPassword $AdminPassword
    $token = $login.accessToken
    $configDir = Join-Path $ProjectRoot 'cloud\nacos-config'
    foreach ($dataId in @(
            'school-bus-core.yml',
            'school-bus-gateway.yml',
            'school-bus-transport-query.yml',
            'school-bus-iam.yml',
            'school-bus-payment.yml',
            'school-bus-booking.yml'
        )) {
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
    }
}

try {
    $report.failedInPhase = 'Assert-Java21'
    Assert-Java21
    $report.javaAvailable = $true

    $report.failedInPhase = 'Assert-Docker'
    Assert-Docker
    $report.dockerAvailable = $true

    $report.failedInPhase = 'Assert-InfraHealthy'
    Assert-InfraHealthy -NacosBaseUrl $NacosBaseUrl `
        -MysqlContainerName $MysqlContainerName
    $report.infraHealthy = $true

    $report.failedInPhase = 'Assert-PortsFree'
    Assert-PortsFree -GatewayPort $GatewayPort -CorePort $CorePort `
        -QueryPorts @($QueryPort) `
        -AdditionalPorts @($IamPort, $BookingPort, $PaymentPort)
    $report.portsFree = $true

    $report.failedInPhase = 'RabbitMQ-port-check'
    if (-not (Test-TcpPort '127.0.0.1' 5672 1500)) {
        throw 'RabbitMQ is not listening on 5672.'
    }
    $report.failedInPhase = 'RabbitMQ-management-port-check'
    if (-not (Test-TcpPort '127.0.0.1' 15672 1500)) {
        throw 'RabbitMQ management API is not listening on 15672.'
    }

    $staticCheck = Test-BookingHasNoPaymentRecordSql
    $report.bookingHasNoPaymentRecordSql = [bool]$staticCheck.ok
    $report.evidence['staticPaymentRecordScan'] = $staticCheck
    if (-not $staticCheck.ok) {
        throw "booking-service still references payment_record"
    }

    $report.failedInPhase = 'nacos-config'
    Publish-DecouplingNacosConfigs -ProjectRoot $projectRoot `
        -NacosBaseUrl $NacosBaseUrl -AdminPassword $AdminPassword

    $report.failedInPhase = 'maven-build'
    if (-not $SkipBuild) {
        Invoke-MavenPackage -WorkingDirectory $projectRoot -Label 'core'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\gateway-service') `
            -Label 'gateway'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\transport-query-service') `
            -Label 'transport-query'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\iam-service') `
            -Label 'iam'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\payment-service') `
            -Label 'payment'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\booking-service') `
            -Label 'booking'
    }
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null

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
        OUTBOX_RELAY_ENABLED = 'true'
        OUTBOX_RELAY_FIXED_DELAY_MS = '500'
        PAYMENT_BOOKING_WRITE_MODE = 'EVENT'
    }

    $report.failedInPhase = 'service-startup'
    $coreJar = Find-BootJar (Join-Path $projectRoot 'target') 'school-bus-platform'
    $gatewayJar = Find-BootJar (Join-Path $projectRoot 'cloud\gateway-service\target') 'school-bus-gateway'
    $queryJar = Find-BootJar (Join-Path $projectRoot 'cloud\transport-query-service\target') 'school-bus-transport-query'
    $iamJar = Find-BootJar (Join-Path $projectRoot 'cloud\iam-service\target') 'school-bus-iam'
    $paymentJar = Find-BootJar (Join-Path $projectRoot 'cloud\payment-service\target') 'school-bus-payment'
    $bookingJar = Find-BootJar (Join-Path $projectRoot 'cloud\booking-service\target') 'school-bus-booking'

    $coreEnv = $commonEnv.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'local,cloud'
    $coreEnv['SERVER_PORT'] = "$CorePort"
    Start-TrackedJava -JarPath $coreJar -Environment $coreEnv `
        -LogPath (Join-Path $logDir 'core.log') -Label 'core' -LogDir $logDir

    $queryEnv = $commonEnv.Clone()
    $queryEnv['SERVER_PORT'] = "$QueryPort"
    Start-TrackedJava -JarPath $queryJar -Environment $queryEnv `
        -LogPath (Join-Path $logDir 'query.log') -Label 'query' -LogDir $logDir

    $iamEnv = $commonEnv.Clone()
    $iamEnv['JWT_PRIVATE_KEY_LOCATION'] = $jwtKeys.Private
    $iamEnv['SERVER_PORT'] = "$IamPort"
    Start-TrackedJava -JarPath $iamJar -Environment $iamEnv `
        -LogPath (Join-Path $logDir 'iam.log') -Label 'iam' -LogDir $logDir

    $paymentEnv = $commonEnv.Clone()
    $paymentEnv['SERVER_PORT'] = "$PaymentPort"
    $paymentEnv['PAYMENT_CALLBACK_SECRET'] = 'verify-decoupling-secret'
    Start-TrackedJava -JarPath $paymentJar -Environment $paymentEnv `
        -LogPath (Join-Path $logDir 'payment.log') -Label 'payment' -LogDir $logDir

    $bookingEnv = $commonEnv.Clone()
    $bookingEnv['SERVER_PORT'] = "$BookingPort"
    Start-TrackedJava -JarPath $bookingJar -Environment $bookingEnv `
        -LogPath (Join-Path $logDir 'booking.log') -Label 'booking' -LogDir $logDir

    $gatewayEnv = $commonEnv.Clone()
    $gatewayEnv['SERVER_PORT'] = "$GatewayPort"
    Start-TrackedJava -JarPath $gatewayJar -Environment $gatewayEnv `
        -LogPath (Join-Path $logDir 'gateway.log') -Label 'gateway' -LogDir $logDir

    Wait-HttpUp "http://127.0.0.1:$PaymentPort/actuator/health" $StartupTimeoutSeconds 'payment'
    Wait-HttpUp "http://127.0.0.1:$BookingPort/actuator/health" $StartupTimeoutSeconds 'booking'
    Wait-HttpUp "http://127.0.0.1:$GatewayPort/actuator/health" $StartupTimeoutSeconds 'gateway'
    $report.servicesStarted = $true
    $paymentInfo = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$PaymentPort/actuator/info"
    if ($paymentInfo.status -ne 200 -or
            [string]$paymentInfo.body.paymentBookingWriteMode -ne 'EVENT') {
        throw "Payment did not expose EVENT booking write mode: $($paymentInfo.raw)"
    }
    $report.paymentEventMode = $true
    $report.evidence['paymentBookingWriteMode'] =
        [string]$paymentInfo.body.paymentBookingWriteMode

    $report.failedInPhase = 'business-flow'
    $script:StudentNumber = 'D{0:D7}' -f (
        Get-Random -Minimum 1000000 -Maximum 9999999
    )
    $password = 'DecoupleVerify!2026'
    $credentials = @{
        studentNumber = $script:StudentNumber
        password = $password
    } | ConvertTo-Json -Compress
    $register = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/accounts" `
        -Method POST -Body $credentials
    if ($register.status -notin @(200, 201)) {
        throw "Register HTTP $($register.status): $($register.raw)"
    }
    $login = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/auth/login" `
        -Method POST -Body $credentials
    if ($login.status -ne 200) {
        throw "Login HTTP $($login.status): $($login.raw)"
    }
    $token = [string]$login.body.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw 'Login did not return accessToken.'
    }

    $script:TripId = 910000 + (Get-Random -Maximum 9000)
    $tripNumber = [guid]::NewGuid().ToString()
    Invoke-Mysql -Sql @"
USE school_bus_platform;
INSERT INTO transport_trip (
  id, trip_no, vehicle_id, route_id, departure_time, booking_deadline,
  price, status, version, created_at, updated_at
) VALUES (
  $($script:TripId), '$tripNumber', 9001, 9001,
  UTC_TIMESTAMP(3) + INTERVAL 2 DAY,
  UTC_TIMESTAMP(3) + INTERVAL 1 DAY,
  5.50, 'OPEN_FOR_BOOKING', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
);
INSERT INTO transport_trip_seat (
  trip_id, seat_number, status, version, created_at, updated_at
) VALUES
  ($($script:TripId), 'A1', 'AVAILABLE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  ($($script:TripId), 'A2', 'AVAILABLE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO booking_trip_inventory (
  trip_id, total_seats, available_seats, version, created_at, updated_at
) VALUES ($($script:TripId), 2, 2, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
"@

    $idempotencyKey = [guid]::NewGuid().ToString()
    $bookingCapture = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/bookings" `
        -Method POST `
        -Headers @{
            Authorization = "Bearer $token"
            'Idempotency-Key' = $idempotencyKey
        } `
        -Body (@{
            tripNumber = $tripNumber
            seatNumber = 'A1'
        } | ConvertTo-Json -Compress)
    if ($bookingCapture.status -ne 201 -and $bookingCapture.status -ne 200) {
        throw "Create booking HTTP $($bookingCapture.status): $($bookingCapture.raw)"
    }
    $script:BookingNumber = [string]$bookingCapture.body.data.bookingNumber
    $amount = [decimal]$bookingCapture.body.data.amount
    if ([string]::IsNullOrWhiteSpace($script:BookingNumber)) {
        throw 'Create booking did not return bookingNumber.'
    }

    Stop-ServiceByPort -Port $BookingPort -Label 'booking'
    Wait-Condition -TimeoutSeconds 30 -Label 'booking port to stop' `
        -Condition {
            if (@(Get-ListeningPids $BookingPort).Count -eq 0) {
                return $true
            }
            return $false
        } | Out-Null

    $script:PaymentNumber = [guid]::NewGuid().ToString()
    $paidAt = [DateTimeOffset]::UtcNow.ToString('o')
    $callbackBody = (@{
        paymentNumber = $script:PaymentNumber
        bookingNumber = $script:BookingNumber
        amount = $amount
        paidAt = $paidAt
        requestNumber = "cb-$($script:StudentNumber)"
    } | ConvertTo-Json -Compress)
    $signature = New-PaymentSignature -Body $callbackBody `
        -Secret 'verify-decoupling-secret'
    $callback = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/payments/callback" `
        -Method POST `
        -Headers @{ 'X-Payment-Signature' = $signature } `
        -Body $callbackBody
    if ($callback.status -ne 200) {
        throw "Payment callback HTTP $($callback.status): $($callback.raw)"
    }

    $paymentBeforeBookingConsumer = Wait-Condition -TimeoutSeconds 60 `
        -Label 'PaymentSucceeded published while Booking is stopped' `
        -Condition {
            $rows = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT CONCAT(p.status, '|', o.status)
  FROM school_bus_platform.payment_record p
  JOIN school_bus_platform.event_outbox o
    ON o.aggregate_id = p.payment_no
   AND o.event_type = 'PaymentSucceeded'
 WHERE p.payment_no = '$($script:PaymentNumber)'
 ORDER BY o.id DESC
 LIMIT 1;
"@)
            if ($rows.Count -gt 0 -and $rows[0].Trim() -eq 'SUCCEEDED|PUBLISHED') {
                return $rows[0].Trim()
            }
            return $false
        }
    $bookingBeforeConsumer = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT status FROM school_bus_platform.booking_order
 WHERE order_no='$($script:BookingNumber)';
"@)
    if ($bookingBeforeConsumer.Count -ne 1 -or
            $bookingBeforeConsumer[0].Trim() -ne 'PENDING_PAYMENT') {
        throw 'Payment modified booking_order before the Booking consumer ran.'
    }
    $report.paymentDidNotWriteBookingOrder = $true
    $report.evidence['paymentBeforeBookingConsumer'] = [ordered]@{
        paymentAndOutbox = [string]$paymentBeforeBookingConsumer
        bookingStatus = $bookingBeforeConsumer[0].Trim()
    }

    Start-TrackedJava -JarPath $bookingJar -Environment $bookingEnv `
        -LogPath (Join-Path $logDir 'booking-restarted.log') `
        -Label 'booking-restarted' -LogDir $logDir
    Wait-HttpUp "http://127.0.0.1:$BookingPort/actuator/health" `
        $StartupTimeoutSeconds 'booking-restarted'

    Wait-Condition -TimeoutSeconds 90 `
        -Label 'booking PAID via PaymentSucceeded' -Condition {
            $row = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT status FROM school_bus_platform.booking_order
 WHERE order_no='$($script:BookingNumber)';
"@)
            if ($row.Count -gt 0 -and $row[0].Trim() -eq 'PAID') { return $true }
            return $false
        } | Out-Null
    $report.bookingPaidViaEvent = $true

    $cancelResp = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/bookings/$($script:BookingNumber)/cancellation" `
        -Method POST `
        -Headers @{ Authorization = "Bearer $token" }
    if ($cancelResp.status -ne 200) {
        throw "Cancel PAID booking HTTP $($cancelResp.status): $($cancelResp.raw)"
    }

    $refundRequested = Wait-Condition -TimeoutSeconds 60 `
        -Label 'RefundRequested outbox' -Condition {
            $rows = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT CONCAT(event_type,'|',status,'|',context_name)
  FROM school_bus_platform.event_outbox
 WHERE event_type='RefundRequested'
   AND aggregate_id='$($script:BookingNumber)'
 ORDER BY id DESC LIMIT 1;
"@)
            if ($rows.Count -gt 0 -and $rows[0] -match '^RefundRequested\|') {
                return $rows[0]
            }
            return $false
        }
    $report.refundRequestedOutboxWritten = $true
    $report.evidence['refundRequested'] = $refundRequested

    Wait-Condition -TimeoutSeconds 120 -Label 'payment REFUNDED' -Condition {
        $row = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT status FROM school_bus_platform.payment_record
 WHERE payment_no='$($script:PaymentNumber)';
"@)
        if ($row.Count -gt 0 -and $row[0].Trim() -eq 'REFUNDED') { return $true }
        return $false
    } | Out-Null
    $report.paymentRefunded = $true

    Wait-Condition -TimeoutSeconds 120 `
        -Label 'booking REFUNDED via PaymentRefunded' -Condition {
            $row = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT status FROM school_bus_platform.booking_order
 WHERE order_no='$($script:BookingNumber)';
"@)
            if ($row.Count -gt 0 -and $row[0].Trim() -eq 'REFUNDED') { return $true }
            return $false
        } | Out-Null
    $report.bookingRefundedViaEvent = $true
    $report.failedInPhase = $null
} catch {
    $failure = $_
    [void]$report.notes.Add($_.Exception.Message)
    if ($null -ne $report.failedInPhase -and
        (Test-DecouplingEnvironmentBlockedPhase -Phase $report.failedInPhase)) {
        $report.environmentBlocked = $true
    }
} finally {
    $cleanupError = $null
    try { Stop-TrackedProcesses } catch {
        [void]$report.notes.Add("Stop processes failed: $($_.Exception.Message)")
    }
    try {
        if ($null -ne $script:BookingNumber -or $null -ne $script:TripId) {
            $bn = $script:BookingNumber
            $pn = $script:PaymentNumber
            $tid = $script:TripId
            $sn = $script:StudentNumber
            $sql = "USE school_bus_platform;`n"
            if ($null -ne $bn) {
                $sql += @"
DELETE FROM event_outbox WHERE aggregate_id='$bn';
DELETE FROM booking_order WHERE order_no='$bn';
"@
            }
            if ($null -ne $pn) {
                $sql += @"
DELETE FROM event_outbox WHERE aggregate_id='$pn';
DELETE FROM payment_record WHERE payment_no='$pn';
"@
            }
            if ($null -ne $tid) {
                $sql += @"
DELETE FROM transport_trip_seat WHERE trip_id=$tid;
DELETE FROM booking_trip_inventory WHERE trip_id=$tid;
DELETE FROM transport_trip WHERE id=$tid;
"@
            }
            if (-not [string]::IsNullOrWhiteSpace($sn)) {
                $sql += @"
DELETE FROM iam_account_role WHERE account_id IN (
  SELECT id FROM iam_account WHERE student_number='$sn');
DELETE FROM iam_account WHERE student_number='$sn';
"@
            }
            $runEventIds = @()
            if ($null -ne $bn -or $null -ne $pn) {
                $aggregateIds = @($bn, $pn) |
                    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                    ForEach-Object { "'$($_.Replace("'", "''"))'" }
                if ($aggregateIds.Count -gt 0) {
                    $runEventIds = @(
                        Invoke-Mysql -ReturnRows -Sql (
                            'SELECT event_id FROM school_bus_platform.event_outbox ' +
                            "WHERE aggregate_id IN ($($aggregateIds -join ','));"
                        ) | ForEach-Object { $_.Trim() } |
                            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
                    )
                }
            }
            if ($runEventIds.Count -gt 0) {
                $quotedEventIds = $runEventIds |
                    ForEach-Object { "'$($_.Replace("'", "''"))'" }
                $sql = "DELETE FROM school_bus_platform.event_consumed WHERE event_id IN ($($quotedEventIds -join ','));`n" + $sql
            }
            Invoke-Mysql -Sql $sql

            $remaining = 0
            if ($null -ne $bn) {
                $remaining += [int](@(Invoke-Mysql -ReturnRows -Sql @"
SELECT (SELECT COUNT(*) FROM school_bus_platform.booking_order WHERE order_no='$bn')
     + (SELECT COUNT(*) FROM school_bus_platform.event_outbox WHERE aggregate_id='$bn');
"@)[0])
            }
            if ($null -ne $pn) {
                $remaining += [int](@(Invoke-Mysql -ReturnRows -Sql @"
SELECT (SELECT COUNT(*) FROM school_bus_platform.payment_record WHERE payment_no='$pn')
     + (SELECT COUNT(*) FROM school_bus_platform.event_outbox WHERE aggregate_id='$pn');
"@)[0])
            }
            if ($runEventIds.Count -gt 0) {
                $remaining += [int](@(Invoke-Mysql -ReturnRows -Sql (
                    'SELECT COUNT(*) FROM school_bus_platform.event_consumed ' +
                    "WHERE event_id IN ($($quotedEventIds -join ','));"
                ))[0])
            }
            if ($remaining -ne 0) {
                throw "Verification cleanup left $remaining owned row(s)."
            }
            $report.evidence['cleanupResidualRows'] = $remaining
        }
        $report.temporaryDataCleaned = $true
    } catch {
        $cleanupError = $_
        [void]$report.notes.Add("Data cleanup failed: $($_.Exception.Message)")
    }
    $report.temporaryTopologyCleaned = $null
    $report.evidence['topologyCleanup'] =
        'NOT_APPLICABLE_FIXED_APPLICATION_TOPOLOGY'
    $resolved = Resolve-BookingPaymentDecouplingStatus -Report $report
    if ($resolved.status -eq 'PASSED' -and -not $report.temporaryDataCleaned) {
        $report.status = 'FAILED'
        $report.failureCategory = 'cleanup_failed'
    } else {
        $report.status = $resolved.status
        $report.failureCategory = $resolved.failureCategory
    }
    if ($null -ne $cleanupError -and $null -eq $failure) {
        # cleanup must not invent PASSED; original success stays unless cleanup required
    }
    $reportPath = Join-Path $logDir 'report.json'
    ($report | ConvertTo-Json -Depth 10) | Set-Content `
        -LiteralPath $reportPath -Encoding UTF8
    Write-Host "Booking↔Payment decoupling report: $reportPath ($($report.status))"
}

if ($report.status -ne 'PASSED') {
    throw "Booking↔Payment event decoupling verification $($report.status): $($report.notes -join '; ')"
}

$report | ConvertTo-Json -Depth 10

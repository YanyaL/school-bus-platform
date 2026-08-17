param(
    [string] $NacosBaseUrl = 'http://127.0.0.1:8848',
    [string] $AdminPassword = 'nacos',
    [string] $MysqlContainerName = 'school-bus-mysql',
    [int] $GatewayPort = 8080,
    [int] $CorePort = 8081,
    [int] $PaymentPort = 8085,
    [int] $StartupTimeoutSeconds = 120,
    [switch] $SkipBuild
)

<#
.SYNOPSIS
  Verifies the first Payment strangler slice against real Nacos and MySQL.

.DESCRIPTION
  Proves Gateway routing, HMAC verification, shared-schema transaction
  atomicity, Core callback removal, Nacos discovery, and outage behavior.
  Temporary booking/payment rows are always removed in finally.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:OrderNo = $null
$script:PaymentNo = $null
$script:SeatNumber = $null

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1')

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $projectRoot "target\payment-strangler-$runId"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Invoke-HttpCapture {
    param(
        [string] $Uri,
        [string] $Method,
        [hashtable] $Headers = @{},
        [string] $Body = $null
    )
    $params = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        SkipHttpErrorCheck = $true
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = $Body
    }
    $response = Invoke-WebRequest @params
    $parsed = $null
    if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
        try {
            $parsed = $response.Content | ConvertFrom-Json
        } catch {
            $parsed = $response.Content
        }
    }
    return [pscustomobject]@{
        status = [int] $response.StatusCode
        body = $parsed
    }
}

function Assert-Status {
    param([object] $Response, [int] $Expected, [string] $Label)
    if ($Response.status -ne $Expected) {
        throw "$Label expected HTTP $Expected but got $($Response.status)"
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

function Invoke-Mysql([string] $Sql, [switch] $ReturnRows) {
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

function Remove-TemporaryPaymentData {
    if ([string]::IsNullOrWhiteSpace($script:OrderNo)) {
        return
    }
    $seatPredicate = if ([string]::IsNullOrWhiteSpace($script:SeatNumber)) {
        "1=0"
    } else {
        "trip_id=9001 AND seat_number='$($script:SeatNumber)' " +
        "AND locked_by_order_no='$($script:OrderNo)'"
    }
    $paymentPredicate = if ([string]::IsNullOrWhiteSpace($script:PaymentNo)) {
        "1=0"
    } else {
        "payment_no='$($script:PaymentNo)'"
    }
    $sql = @"
USE school_bus_platform;
DELETE FROM event_outbox
 WHERE context_name='Payment' AND aggregate_id='$($script:PaymentNo)';
DELETE FROM payment_record WHERE $paymentPredicate;
DELETE FROM booking_order WHERE order_no='$($script:OrderNo)';
UPDATE transport_trip_seat
   SET status='AVAILABLE', locked_by_order_no=NULL,
       locked_by_user_id=NULL, lock_expires_at=NULL,
       version=version+1, updated_at=UTC_TIMESTAMP(3)
 WHERE $seatPredicate;
"@
    Invoke-Mysql -Sql $sql
}

$report = [ordered]@{
    runId = $runId
    status = 'FAILED'
    nacosPaymentHealthy = $null
    gatewayCallbackStatus = $null
    directCoreCallbackStatus = $null
    paymentOutageStatus = $null
    paymentStatus = $null
    bookingStatus = $null
    seatStatus = $null
    paymentIdJsonType = $null
}

try {
    Assert-Java21
    Assert-Docker
    Assert-InfraHealthy `
        -NacosBaseUrl $NacosBaseUrl `
        -MysqlContainerName $MysqlContainerName
    Assert-PortsFree `
        -GatewayPort $GatewayPort `
        -CorePort $CorePort `
        -QueryPorts @() `
        -AdditionalPorts @($PaymentPort)

    $keys = Ensure-JwtKeys -ProjectRoot $projectRoot
    $nacosToken = Publish-NacosConfigs `
        -ProjectRoot $projectRoot `
        -NacosBaseUrl $NacosBaseUrl `
        -AdminPassword $AdminPassword

    if (-not $SkipBuild) {
        Invoke-MavenPackage -WorkingDirectory $projectRoot -Label 'core'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\gateway-service') `
            -Label 'gateway'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $projectRoot 'cloud\payment-service') `
            -Label 'payment'
    }

    $coreJar = Find-BootJar (Join-Path $projectRoot 'target') 'school-bus-platform'
    $gatewayJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\gateway-service\target') `
        'school-bus-gateway'
    $paymentJar = Find-BootJar `
        (Join-Path $projectRoot 'cloud\payment-service\target') `
        'school-bus-payment'

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
    $coreEnv['JWT_PUBLIC_KEY_LOCATION'] = $keys.Public
    $coreEnv['JWT_ISSUER'] = 'https://school-bus.local'
    $coreEnv['JWT_AUDIENCE'] = 'school-bus-api'
    Start-TrackedJava $coreJar $coreEnv `
        (Join-Path $logDir 'core.log') 'core' $logDir

    $secret = 'payment-strangler-secret-2026'
    $paymentEnv = $commonEnv.Clone()
    $paymentEnv['PAYMENT_SERVER_PORT'] = "$PaymentPort"
    $paymentEnv['SERVER_PORT'] = "$PaymentPort"
    $paymentEnv['PAYMENT_CALLBACK_SECRET'] = $secret
    $paymentEnv['SCHOOL_BUS_PAYMENT_WORKER_ID'] = '2'
    Start-TrackedJava $paymentJar $paymentEnv `
        (Join-Path $logDir 'payment.log') 'payment' $logDir

    $gatewayEnv = $commonEnv.Clone()
    $gatewayEnv['GATEWAY_SERVER_PORT'] = "$GatewayPort"
    $gatewayEnv['SERVER_PORT'] = "$GatewayPort"
    $gatewayEnv['SPRING_CLOUD_LOADBALANCER_CACHE_TTL'] = '2s'
    Start-TrackedJava $gatewayJar $gatewayEnv `
        (Join-Path $logDir 'gateway.log') 'gateway' $logDir

    Wait-HttpUp "http://127.0.0.1:$CorePort/actuator/health" `
        $StartupTimeoutSeconds 'Core'
    Wait-HttpUp "http://127.0.0.1:$PaymentPort/actuator/health" `
        $StartupTimeoutSeconds 'Payment'
    Wait-HttpUp "http://127.0.0.1:$GatewayPort/actuator/health" `
        $StartupTimeoutSeconds 'Gateway'
    $paymentReady = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 1 `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-payment'
    $report.nacosPaymentHealthy = $paymentReady.snapshot.healthy

    $script:OrderNo = [guid]::NewGuid().ToString()
    $script:PaymentNo = [guid]::NewGuid().ToString()
    $bookingRequest = 'payment-strangler-booking-' + [guid]::NewGuid().ToString('N')
    $paymentRequest = 'payment-strangler-callback-' + [guid]::NewGuid().ToString('N')
    $now = [DateTimeOffset]::UtcNow
    $created = $now.ToString('yyyy-MM-dd HH:mm:ss.fff')
    $expires = $now.AddMinutes(10).ToString('yyyy-MM-dd HH:mm:ss.fff')
    $tripNo = (Invoke-Mysql `
        -Sql 'SELECT trip_no FROM school_bus_platform.transport_trip WHERE id=9001 LIMIT 1' `
        -ReturnRows | Select-Object -First 1).Trim()
    $script:SeatNumber = (Invoke-Mysql `
        -Sql "SELECT s.seat_number FROM school_bus_platform.transport_trip_seat s WHERE s.trip_id=9001 AND s.status='AVAILABLE' AND NOT EXISTS (SELECT 1 FROM school_bus_platform.booking_order b WHERE b.trip_id=s.trip_id AND b.seat_number=s.seat_number AND b.status IN ('PENDING_PAYMENT','PAID')) ORDER BY s.seat_number LIMIT 1" `
        -ReturnRows | Select-Object -First 1).Trim()
    if ([string]::IsNullOrWhiteSpace($tripNo) -or
        [string]::IsNullOrWhiteSpace($script:SeatNumber)) {
        throw 'Demo trip 9001 with an available seat is required.'
    }

    $seedSql = @"
USE school_bus_platform;
INSERT INTO booking_order(
    order_no, request_no, user_id, trip_id, trip_no, seat_number,
    price_snapshot, status, expires_at, version, created_at, updated_at
) VALUES (
    '$($script:OrderNo)', '$bookingRequest', 987654321001, 9001,
    '$tripNo', '$($script:SeatNumber)', 5.50, 'PENDING_PAYMENT',
    '$expires', 0, '$created', '$created'
);
UPDATE transport_trip_seat
   SET status='LOCKED', locked_by_order_no='$($script:OrderNo)',
       locked_by_user_id=987654321001, lock_expires_at='$expires',
       version=version+1, updated_at='$created'
 WHERE trip_id=9001 AND seat_number='$($script:SeatNumber)'
   AND status='AVAILABLE';
"@
    Invoke-Mysql -Sql $seedSql

    $payload = [ordered]@{
        requestNumber = $paymentRequest
        paymentNumber = $script:PaymentNo
        bookingNumber = $script:OrderNo
        amount = 5.50
        paidAt = $now.ToString('o')
    }
    $body = $payload | ConvertTo-Json -Compress
    $signature = New-PaymentSignature -Body $body -Secret $secret
    $headers = @{ 'X-Payment-Signature' = $signature }

    $gatewayResponse = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/payments/callback" `
        -Method POST -Headers $headers -Body $body
    Assert-Status $gatewayResponse 200 'Gateway payment callback'
    $report.gatewayCallbackStatus = $gatewayResponse.status
    $report.paymentIdJsonType = $gatewayResponse.body.data.paymentId.GetType().Name
    if ($report.paymentIdJsonType -ne 'String') {
        throw 'Payment ID must cross the HTTP boundary as a JSON string.'
    }

    $rows = @(Invoke-Mysql -ReturnRows -Sql @"
SELECT status FROM school_bus_platform.payment_record
 WHERE payment_no='$($script:PaymentNo)';
SELECT status FROM school_bus_platform.booking_order
 WHERE order_no='$($script:OrderNo)';
SELECT status FROM school_bus_platform.transport_trip_seat
 WHERE trip_id=9001 AND seat_number='$($script:SeatNumber)';
"@)
    $report.paymentStatus = [string] $rows[0]
    $report.bookingStatus = [string] $rows[1]
    $report.seatStatus = [string] $rows[2]
    if ($report.paymentStatus -ne 'SUCCEEDED' -or
        $report.bookingStatus -ne 'PAID' -or
        $report.seatStatus -ne 'SOLD') {
        throw 'Payment, booking, and seat states were not committed together.'
    }

    $directCore = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$CorePort/api/v1/payments/callback" `
        -Method POST -Headers $headers -Body $body
    Assert-Status $directCore 404 'Direct Core payment callback'
    $report.directCoreCallbackStatus = $directCore.status

    Stop-ServiceByPort -Port $PaymentPort -Label 'payment'
    $null = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 0 `
        -TimeoutSeconds 30 `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-payment'
    $outage = Invoke-HttpCapture `
        -Uri "http://127.0.0.1:$GatewayPort/api/v1/payments/callback" `
        -Method POST -Headers $headers -Body $body
    Assert-Status $outage 503 'Payment outage callback'
    $report.paymentOutageStatus = $outage.status
    $report.status = 'PASSED'
} finally {
    try { Remove-TemporaryPaymentData } catch {
        Write-Warning "Temporary payment cleanup failed: $($_.Exception.Message)"
    }
    Stop-TrackedProcesses
    $reportPath = Join-Path $logDir 'payment-strangler-report.json'
    $report | ConvertTo-Json -Depth 6 | Set-Content `
        -LiteralPath $reportPath -Encoding UTF8
    Write-Host "Payment strangler report: $reportPath"
}

if ($report.status -ne 'PASSED') {
    throw 'Payment strangler verification failed.'
}

$report | ConvertTo-Json -Depth 6

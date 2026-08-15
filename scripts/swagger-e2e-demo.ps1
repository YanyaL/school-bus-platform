#Requires -Version 5.1
<#
.SYNOPSIS
  Swagger / REST end-to-end demo: register -> login -> trips -> seats -> booking -> pay.

.DESCRIPTION
  Prerequisites:
    1. Docker runtime up (MySQL, Redis, RabbitMQ): E:\HS1\school-bus-runtime\start-runtime.ps1
    2. Application running: mvn spring-boot:run (profile local, port 8080)
    3. Demo trip seeded (this script can seed automatically)

  Swagger UI: http://localhost:8080/swagger-ui.html
#>
param(
    [string] $BaseUrl = "http://localhost:8080",
    [long] $DemoTripId = 9001,
    [string] $SeatNumber = "A01",
    [string] $Password = "DemoPass!2026",
    [string] $PaymentSecret = "local-payment-secret-change-me-1234",
    [switch] $SkipSeed,
    [switch] $SkipRegister
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Api {
    param(
        [ValidateSet("GET", "POST")]
        [string] $Method,
        [string] $Path,
        [hashtable] $Headers = @{},
        [object] $Body = $null
    )

    $uri = "$BaseUrl$Path"
    $params = @{
        Method      = $Method
        Uri         = $uri
        Headers     = $Headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 6 -Compress)
    }

    try {
        return Invoke-RestMethod @params
    } catch {
        $detail = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($detail)) {
            throw
        }
        throw "HTTP request failed ($Method $Path): $detail"
    }
}

function New-PaymentSignature([string] $RawBody, [string] $Secret) {
    $hmac = [System.Security.Cryptography.HMACSHA256]::new(
        [Text.Encoding]::UTF8.GetBytes($Secret)
    )
    try {
        $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($RawBody))
        return "sha256=" + (
            [BitConverter]::ToString($hash).Replace("-", "").ToLowerInvariant()
        )
    } finally {
        $hmac.Dispose()
    }
}

function Test-Health {
    Write-Step "Health check"
    $health = Invoke-RestMethod -Method GET -Uri "$BaseUrl/actuator/health"
    if ($health.status -ne "UP") {
        throw "Application health is not UP: $($health.status)"
    }
    Write-Host "actuator/health = UP"
}

function Invoke-DemoSeed {
    if ($SkipSeed) {
        Write-Host "Skipping demo seed (-SkipSeed)."
        return
    }

    Write-Step "Seed demo trip $DemoTripId (MySQL via Docker)"
    $seedFile = Join-Path $PSScriptRoot "seed-local-demo.sql"
    if (-not (Test-Path $seedFile)) {
        throw "Seed file not found: $seedFile"
    }

    $container = docker ps --filter "name=school-bus-mysql" --format "{{.Names}}" 2>$null
    if ([string]::IsNullOrWhiteSpace($container)) {
        throw "school-bus-mysql container is not running. Start E:\HS1\school-bus-runtime first."
    }

    Get-Content -Raw $seedFile | docker exec -i school-bus-mysql `
        mysql -uroot -proot --default-character-set=utf8mb4
    Write-Host "Demo data loaded (trip $DemoTripId, seats A01-A10)."
}

$studentNumber = "S{0:D7}" -f (Get-Random -Minimum 1000000 -Maximum 9999999)

Test-Health
Invoke-DemoSeed

if (-not $SkipRegister) {
    Write-Step "Register account ($studentNumber)"
    $registerResponse = Invoke-Api -Method POST -Path "/api/v1/accounts" -Body @{
        studentNumber = $studentNumber
        password      = $Password
    }
    Write-Host "Registered userId=$($registerResponse.data.userId)"
} else {
    Write-Host "Skipping registration (-SkipRegister). Set `$studentNumber manually if reusing account."
}

Write-Step "Login"
$loginResponse = Invoke-Api -Method POST -Path "/api/v1/auth/login" -Body @{
    studentNumber = $studentNumber
    password      = $Password
}
$accessToken = $loginResponse.data.accessToken
$authHeaders = @{ Authorization = "Bearer $accessToken" }
Write-Host "Logged in as userId=$($loginResponse.data.userId)"

Write-Step "Current user (/api/v1/auth/me)"
$me = Invoke-Api -Method GET -Path "/api/v1/auth/me" -Headers $authHeaders
Write-Host "me.userId=$($me.data.userId), roles=$($me.data.roles -join ',')"

Write-Step "List bookable trips"
$trips = Invoke-Api -Method GET -Path "/api/v1/trips?limit=20" -Headers $authHeaders
$tripCount = @($trips.data).Count
Write-Host "Found $tripCount bookable trip(s)"
if ($tripCount -eq 0) {
    throw "No bookable trips. Run seed or publish a trip first."
}

$selectedTrip = $trips.data[0]
$tripNumber = [string] $selectedTrip.tripNumber
if ([string]::IsNullOrWhiteSpace($tripNumber)) {
    throw "Bookable trip is missing tripNumber"
}
Write-Host "Using tripNumber=$tripNumber (seed DemoTripId=$DemoTripId is internal only)"

Write-Step "Seat map (/api/v1/trips/$tripNumber/seats)"
$seatMap = Invoke-Api -Method GET -Path "/api/v1/trips/$tripNumber/seats" -Headers $authHeaders
$availableSeat = $seatMap.data.seats |
    Where-Object { $_.status -eq "AVAILABLE" } |
    Select-Object -First 1
if ($null -eq $availableSeat) {
    throw "No AVAILABLE seat on trip $tripNumber"
}
$chosenSeat = $availableSeat.seatNumber
Write-Host "Selected seat $chosenSeat (status=$($availableSeat.status))"

Write-Step "Create booking (Idempotency-Key required)"
$idempotencyKey = [guid]::NewGuid().ToString("N")
$bookingHeaders = $authHeaders.Clone()
$bookingHeaders["Idempotency-Key"] = $idempotencyKey
$booking = Invoke-Api -Method POST -Path "/api/v1/bookings" -Headers $bookingHeaders -Body @{
    tripNumber = $tripNumber
    seatNumber = $chosenSeat
}
Write-Host "bookingNumber=$($booking.data.bookingNumber)"
Write-Host "status=$($booking.data.status), amount=$($booking.data.amount), expiresAt=$($booking.data.expiresAt)"

Write-Step "Booking detail"
$detail = Invoke-Api -Method GET -Path "/api/v1/bookings/$($booking.data.bookingNumber)" -Headers $authHeaders
Write-Host "detail.status=$($detail.data.status), seat=$($detail.data.seatNumber)"

Write-Step "Simulate payment callback (HMAC signed)"
$paymentNumber = [guid]::NewGuid().ToString()
$paidAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
$callbackBody = (@{
    requestNumber = "demo-pay-$idempotencyKey"
    paymentNumber = $paymentNumber
    bookingNumber = $booking.data.bookingNumber
    amount        = [decimal] $booking.data.amount
    paidAt        = $paidAt
} | ConvertTo-Json -Compress)
$signature = New-PaymentSignature -RawBody $callbackBody -Secret $PaymentSecret
$payment = Invoke-RestMethod -Method POST `
    -Uri "$BaseUrl/api/v1/payments/callback" `
    -ContentType "application/json" `
    -Headers @{ "X-Payment-Signature" = $signature } `
    -Body $callbackBody
Write-Host "payment outcome=$($payment.data.outcome)"

Write-Step "Verify booking is PAID"
$paidDetail = Invoke-Api -Method GET -Path "/api/v1/bookings/$($booking.data.bookingNumber)" -Headers $authHeaders
Write-Host "final.status=$($paidDetail.data.status)"

Write-Step "My bookings (first page)"
$list = Invoke-Api -Method GET -Path "/api/v1/bookings?page=0&size=5&sort=createdAt,desc" -Headers $authHeaders
Write-Host "totalElements=$($list.data.totalElements)"

Write-Host ""
Write-Host "E2E demo completed successfully." -ForegroundColor Green
Write-Host "Swagger UI: $BaseUrl/swagger-ui.html"
Write-Host "Student: $studentNumber / $Password"
Write-Host "Booking: $($booking.data.bookingNumber)"

param(
    [string] $NacosBaseUrl = 'http://127.0.0.1:8848',
    [string] $AdminPassword = 'nacos',
    [string] $MysqlContainerName = 'school-bus-mysql',
    [string] $DatabaseName = 'school_bus_platform',
    [string] $DatabaseUser = 'root',
    [string] $DatabasePassword = 'root',
    [int] $GatewayPort = 8080,
    [int] $CorePort = 8081,
    [int] $CommandPort = 8088,
    [int] $StartupTimeoutSeconds = 150,
    [switch] $SkipBuild,
    [switch] $ImportOnly
)

<#
.SYNOPSIS
  Real Nacos/Gateway/MySQL acceptance for Transport Command phase 1.

.DESCRIPTION
  Starts Core, Gateway and school-bus-transport-command, then verifies runtime
  ownership, Nacos registration, JWT role enforcement and real vehicle/route
  writes. Temporary rows and Java processes are cleaned in finally.

  Docker/Nacos/MySQL unavailable => BLOCKED. A failure after prechecks => FAILED.
  Tokens, key material and passwords are never written to the report.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:CurrentPhase = $null
$script:VehicleId = $null
$script:RouteId = $null
. (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1')

$script:EnvironmentPhases = @(
    'Assert-Java21',
    'Assert-Docker',
    'Assert-InfraHealthy',
    'Assert-PortsFree'
)

$script:RequiredFlags = @(
    'javaAvailable',
    'dockerAvailable',
    'infraHealthy',
    'nacosCommandHealthy',
    'gatewayRoutesToCommand',
    'coreTransportAdminDisabled',
    'commandOwnershipReported',
    'unauthenticatedRejected',
    'studentForbidden',
    'vehicleWriteVerified',
    'routeWriteVerified',
    'temporaryDataCleaned'
)

function New-TransportCommandReport([string] $RunId) {
    return [ordered]@{
        runId = $RunId
        status = 'FAILED'
        failureCategory = 'verification_not_executed'
        failedInPhase = $null
        environmentBlocked = $false
        javaAvailable = $false
        dockerAvailable = $false
        infraHealthy = $false
        nacosCommandHealthy = $false
        gatewayRoutesToCommand = $false
        coreTransportAdminDisabled = $false
        commandOwnershipReported = $false
        unauthenticatedRejected = $false
        studentForbidden = $false
        vehicleWriteVerified = $false
        routeWriteVerified = $false
        temporaryDataCleaned = $false
        ownershipEvidence = [ordered]@{}
        routingEvidence = [ordered]@{}
        authorizationEvidence = [ordered]@{}
        writeEvidence = [ordered]@{}
        cleanupEvidence = [ordered]@{}
        notes = @()
    }
}

function Resolve-TransportCommandStatus([System.Collections.IDictionary] $Report) {
    $allPassed = $true
    foreach ($field in $script:RequiredFlags) {
        if (-not $Report[$field]) {
            $allPassed = $false
            break
        }
    }
    if ($allPassed) {
        return @{ status = 'PASSED'; failureCategory = 'verification_succeeded' }
    }
    if ($Report.environmentBlocked) {
        return @{ status = 'BLOCKED'; failureCategory = 'environment_blocked' }
    }
    return @{ status = 'FAILED'; failureCategory = 'business_failure' }
}

function ConvertTo-Base64Url([byte[]] $Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-TestJwt {
    param(
        [string] $PrivateKeyPath,
        [string] $Subject,
        [string[]] $Roles
    )
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = @{ alg = 'RS256'; typ = 'JWT' } | ConvertTo-Json -Compress
    $payloadJson = @{
        iss = 'https://school-bus.local'
        aud = 'school-bus-api'
        sub = $Subject
        roles = $Roles
        iat = $now
        exp = $now + 900
        jti = [guid]::NewGuid().ToString()
    } | ConvertTo-Json -Compress
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsigned = "$header.$payload"
    $rsa = [Security.Cryptography.RSA]::Create()
    try {
        $rsa.ImportFromPem((Get-Content -LiteralPath $PrivateKeyPath -Raw))
        $signature = $rsa.SignData(
            [Text.Encoding]::ASCII.GetBytes($unsigned),
            [Security.Cryptography.HashAlgorithmName]::SHA256,
            [Security.Cryptography.RSASignaturePadding]::Pkcs1
        )
        return "$unsigned.$(ConvertTo-Base64Url $signature)"
    } finally {
        $rsa.Dispose()
    }
}

function Convert-HttpContentToText([object] $Content) {
    if ($null -eq $Content) {
        return ''
    }
    if ($Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Content)
    }
    return [string]$Content
}

function Invoke-HttpCapture {
    param(
        [string] $Uri,
        [string] $Method = 'GET',
        [hashtable] $Headers = @{},
        [object] $Body = $null
    )
    try {
        $arguments = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
            UseBasicParsing = $true
            TimeoutSec = 15
        }
        if ($null -ne $Body) {
            $arguments.ContentType = 'application/json'
            $arguments.Body = $Body | ConvertTo-Json -Depth 8 -Compress
        }
        $response = Invoke-WebRequest @arguments
        $raw = Convert-HttpContentToText $response.Content
        $parsed = if ([string]::IsNullOrWhiteSpace($raw)) {
            $null
        } else {
            $raw | ConvertFrom-Json
        }
        return @{ status = [int]$response.StatusCode; body = $parsed; raw = $raw }
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) {
            throw
        }
        $status = [int]$response.StatusCode
        $raw = ''
        try {
            $reader = [IO.StreamReader]::new($response.GetResponseStream())
            $raw = $reader.ReadToEnd()
            $reader.Dispose()
        } catch {
            $raw = ''
        }
        $parsed = if ([string]::IsNullOrWhiteSpace($raw)) {
            $null
        } else {
            try { $raw | ConvertFrom-Json } catch { $null }
        }
        return @{ status = $status; body = $parsed; raw = $raw }
    }
}

function Assert-Status([hashtable] $Response, [int[]] $Expected, [string] $Label) {
    if ($Expected -notcontains $Response.status) {
        throw "$Label returned HTTP $($Response.status), expected $($Expected -join '/')."
    }
}

function Get-ActuatorDetail([int] $Port, [string] $Name) {
    $response = Invoke-HttpCapture -Uri "http://127.0.0.1:$Port/actuator/info"
    Assert-Status $response @(200) "actuator info on $Port"
    $property = $response.body.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "actuator info on $Port does not expose $Name"
    }
    return [string]$property.Value
}

function Assert-NumericId([object] $Value, [string] $Label) {
    $text = [string]$Value
    if ($text -notmatch '^\d+$') {
        throw "$Label is not a numeric resource id."
    }
    return $text
}

function Invoke-MySql([string] $Sql) {
    $output = docker exec -e "MYSQL_PWD=$DatabasePassword" $MysqlContainerName `
        mysql -N -B -u $DatabaseUser $DatabaseName -e $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join ' ')"
    }
    return @($output)
}

function Remove-TemporaryTransportData([System.Collections.IDictionary] $Report) {
    try {
        if ($null -eq $script:VehicleId -and $null -eq $script:RouteId) {
            $Report.cleanupEvidence.remainingRows = 0
            $Report.cleanupEvidence.note = 'No temporary business rows were created.'
            $Report.temporaryDataCleaned = $true
            return
        }
        if ($null -ne $script:VehicleId) {
            [void](Invoke-MySql "DELETE FROM transport_vehicle_seat WHERE vehicle_id = $script:VehicleId; DELETE FROM transport_vehicle WHERE id = $script:VehicleId;")
        }
        if ($null -ne $script:RouteId) {
            [void](Invoke-MySql "DELETE FROM transport_route WHERE id = $script:RouteId;")
        }
        $vehicleId = if ($null -eq $script:VehicleId) { 0 } else { $script:VehicleId }
        $routeId = if ($null -eq $script:RouteId) { 0 } else { $script:RouteId }
        $remaining = @(Invoke-MySql "SELECT (SELECT COUNT(*) FROM transport_vehicle WHERE id = $vehicleId) + (SELECT COUNT(*) FROM transport_route WHERE id = $routeId);")
        $count = if ($remaining.Count -eq 0) { 0 } else { [int]$remaining[0] }
        $Report.cleanupEvidence.remainingRows = $count
        $Report.temporaryDataCleaned = ($count -eq 0)
    } catch {
        $Report.notes += "Cleanup failed: $($_.Exception.Message)"
        $Report.temporaryDataCleaned = $false
    }
}

function Write-TransportCommandReport([System.Collections.IDictionary] $Report, [string] $Path) {
    $directory = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
    $Report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding UTF8
    Write-Host "Report: $Path"
    Write-Host "Status: $($Report.status) ($($Report.failureCategory))"
}

if ($ImportOnly) {
    return
}

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $projectRoot "target\transport-command-foundation-$runId"
$reportPath = Join-Path $reportDir 'report.json'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$report = New-TransportCommandReport $runId

try {
    $script:CurrentPhase = 'Assert-Java21'
    Assert-Java21
    $report.javaAvailable = $true

    $script:CurrentPhase = 'Assert-Docker'
    Assert-Docker
    $report.dockerAvailable = $true

    $script:CurrentPhase = 'Assert-InfraHealthy'
    Assert-InfraHealthy -NacosBaseUrl $NacosBaseUrl -MysqlContainerName $MysqlContainerName
    $report.infraHealthy = $true

    $script:CurrentPhase = 'Assert-PortsFree'
    Assert-PortsFree -GatewayPort $GatewayPort -CorePort $CorePort -QueryPorts @() -AdditionalPorts @($CommandPort)

    $script:CurrentPhase = 'prepare-runtime'
    $keys = Ensure-JwtKeys -ProjectRoot $projectRoot
    $nacosToken = Publish-NacosConfigs -ProjectRoot $projectRoot -NacosBaseUrl $NacosBaseUrl -AdminPassword $AdminPassword

    if (-not $SkipBuild) {
        Invoke-MavenPackage -WorkingDirectory $projectRoot -Label 'core'
        Invoke-MavenPackage -WorkingDirectory (Join-Path $projectRoot 'cloud\gateway-service') -Label 'gateway'
        Invoke-MavenPackage -WorkingDirectory (Join-Path $projectRoot 'cloud\transport-command-service') -Label 'transport-command'
    }

    $coreJar = Find-BootJar (Join-Path $projectRoot 'target') 'school-bus-platform'
    $gatewayJar = Find-BootJar (Join-Path $projectRoot 'cloud\gateway-service\target') 'school-bus-gateway'
    $commandJar = Find-BootJar (Join-Path $projectRoot 'cloud\transport-command-service\target') 'school-bus-transport-command'

    $jdbcUrl = "jdbc:mysql://127.0.0.1:3306/${DatabaseName}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    $common = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        DB_URL = $jdbcUrl
        DB_USERNAME = $DatabaseUser
        DB_PASSWORD = $DatabasePassword
        SPRING_DATASOURCE_URL = $jdbcUrl
        SPRING_DATASOURCE_USERNAME = $DatabaseUser
        SPRING_DATASOURCE_PASSWORD = $DatabasePassword
        JWT_PUBLIC_KEY_LOCATION = $keys.Public
    }
    $coreEnv = $common.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'cloud'
    $coreEnv['CORE_SERVER_PORT'] = "$CorePort"
    $coreEnv['SCHOOL_BUS_TRANSPORT_ADMIN_EMBEDDED_ENABLED'] = 'false'
    $commandEnv = $common.Clone()
    $commandEnv['TRANSPORT_COMMAND_SERVER_PORT'] = "$CommandPort"
    $gatewayEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        GATEWAY_SERVER_PORT = "$GatewayPort"
        JWT_PUBLIC_KEY_LOCATION = $keys.Public
        TOKEN_REVOCATION_ENABLED = 'false'
        TRANSPORT_COMMAND_SERVICE_ID = 'school-bus-transport-command'
    }

    Start-TrackedJava -JarPath $coreJar -Environment $coreEnv -LogPath (Join-Path $reportDir 'core.log') -Label 'core' -LogDir $reportDir
    Start-TrackedJava -JarPath $commandJar -Environment $commandEnv -LogPath (Join-Path $reportDir 'transport-command.log') -Label 'transport-command' -LogDir $reportDir
    Start-TrackedJava -JarPath $gatewayJar -Environment $gatewayEnv -LogPath (Join-Path $reportDir 'gateway.log') -Label 'gateway' -LogDir $reportDir
    Wait-HttpUp "http://127.0.0.1:$CorePort/actuator/health" $StartupTimeoutSeconds 'Core'
    Wait-HttpUp "http://127.0.0.1:$CommandPort/actuator/health" $StartupTimeoutSeconds 'Transport Command'
    Wait-HttpUp "http://127.0.0.1:$GatewayPort/actuator/health" $StartupTimeoutSeconds 'Gateway'

    $script:CurrentPhase = 'ownership-and-discovery'
    $healthy = Wait-NacosServiceHealthyCount -AccessToken $nacosToken -Expected 1 -TimeoutSeconds 60 -NacosBaseUrl $NacosBaseUrl -ServiceName 'school-bus-transport-command'
    $report.nacosCommandHealthy = ($healthy.snapshot.healthy -eq 1)
    $coreOwner = Get-ActuatorDetail $CorePort 'transportAdminOwner'
    $commandOwner = Get-ActuatorDetail $CommandPort 'transportAdminOwner'
    $report.ownershipEvidence.core = $coreOwner
    $report.ownershipEvidence.command = $commandOwner
    $report.coreTransportAdminDisabled = ($coreOwner -eq 'disabled')
    $report.commandOwnershipReported = ($commandOwner -eq 'transport-command')

    $privatePath = $keys.Private.Substring('file:'.Length)
    $adminToken = New-TestJwt -PrivateKeyPath $privatePath -Subject '900000001' -Roles @('ADMIN')
    $studentToken = New-TestJwt -PrivateKeyPath $privatePath -Subject '900000002' -Roles @('STUDENT')
    $adminHeaders = @{ Authorization = "Bearer $adminToken" }
    $studentHeaders = @{ Authorization = "Bearer $studentToken" }

    $script:CurrentPhase = 'authorization-and-routing'
    $anonymous = Invoke-HttpCapture -Uri "http://127.0.0.1:$GatewayPort/api/v1/admin/vehicles"
    $student = Invoke-HttpCapture -Uri "http://127.0.0.1:$GatewayPort/api/v1/admin/vehicles" -Headers $studentHeaders
    $adminList = Invoke-HttpCapture -Uri "http://127.0.0.1:$GatewayPort/api/v1/admin/vehicles" -Headers $adminHeaders
    $coreDirect = Invoke-HttpCapture -Uri "http://127.0.0.1:$CorePort/api/v1/admin/vehicles" -Headers $adminHeaders
    Assert-Status $anonymous @(401) 'anonymous admin request'
    Assert-Status $student @(403) 'student admin request'
    Assert-Status $adminList @(200) 'admin vehicle list through Gateway'
    Assert-Status $coreDirect @(404) 'direct Core vehicle administration'
    $report.unauthenticatedRejected = $true
    $report.studentForbidden = $true
    $report.gatewayRoutesToCommand = $true
    $report.routingEvidence.gatewayStatus = $adminList.status
    $report.routingEvidence.coreDirectStatus = $coreDirect.status
    $report.authorizationEvidence.anonymousStatus = $anonymous.status
    $report.authorizationEvidence.studentStatus = $student.status

    $script:CurrentPhase = 'vehicle-and-route-writes'
    $suffix = Get-Random -Minimum 100000 -Maximum 999999
    $vehicle = Invoke-HttpCapture -Uri "http://127.0.0.1:$GatewayPort/api/v1/admin/vehicles" -Method POST -Headers $adminHeaders -Body @{
        licensePlate = "QA-$suffix"
        seatCount = 4
    }
    Assert-Status $vehicle @(201) 'create vehicle'
    $script:VehicleId = Assert-NumericId $vehicle.body.data.vehicleId 'vehicleId'
    $vehicleRows = Invoke-MySql "SELECT COUNT(*) FROM transport_vehicle WHERE id = $script:VehicleId; SELECT COUNT(*) FROM transport_vehicle_seat WHERE vehicle_id = $script:VehicleId;"
    if (@($vehicleRows).Count -lt 2 -or [int]$vehicleRows[0] -ne 1 -or [int]$vehicleRows[1] -ne 4) {
        throw 'Vehicle and four-seat layout were not committed together.'
    }
    $report.vehicleWriteVerified = $true
    $report.writeEvidence.vehicleRows = [int]$vehicleRows[0]
    $report.writeEvidence.vehicleSeatRows = [int]$vehicleRows[1]

    $route = Invoke-HttpCapture -Uri "http://127.0.0.1:$GatewayPort/api/v1/admin/routes" -Method POST -Headers $adminHeaders -Body @{
        routeCode = "QA-$suffix"
        departureCampus = 'MAIN'
        arrivalCampus = 'EAST'
        estimatedDurationMinutes = 45
    }
    Assert-Status $route @(201) 'create route'
    $script:RouteId = Assert-NumericId $route.body.data.routeId 'routeId'
    $routeRows = @(Invoke-MySql "SELECT COUNT(*) FROM transport_route WHERE id = $script:RouteId;")
    if (@($routeRows).Count -eq 0 -or [int]$routeRows[0] -ne 1) {
        throw 'Route row was not committed.'
    }
    $report.routeWriteVerified = $true
    $report.writeEvidence.routeRows = [int]$routeRows[0]
}
catch {
    $report.failedInPhase = $script:CurrentPhase
    $report.environmentBlocked = ($script:EnvironmentPhases -contains $script:CurrentPhase)
    $report.notes += $_.Exception.Message
}
finally {
    $adminToken = $null
    $studentToken = $null
    Remove-TemporaryTransportData $report
    Stop-TrackedProcesses
    $resolved = Resolve-TransportCommandStatus $report
    $report.status = $resolved.status
    $report.failureCategory = $resolved.failureCategory
    Write-TransportCommandReport $report $reportPath
}

if ($report.status -ne 'PASSED') {
    exit 1
}

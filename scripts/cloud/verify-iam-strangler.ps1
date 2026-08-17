#Requires -Version 5.1
<#
.SYNOPSIS
  Real infrastructure acceptance for the IAM strangler extraction.

.DESCRIPTION
  Starts Core, Gateway, Transport Query, and IAM against Nacos 3, MySQL, and
  Redis. It verifies authentication routing, JWT interoperability, refresh
  token rotation, logout semantics, IAM failure isolation, Nacos recovery,
  and the runtime private-key boundary.

  No access token, refresh token, password, Nacos token, or PEM content is
  written to the report or console.
#>
param(
    [string] $ProjectRoot = '',
    [int] $GatewayPort = 8080,
    [int] $CorePort = 8081,
    [int] $QueryPort = 8082,
    [int] $IamPort = 8084,
    [int] $StartupTimeoutSeconds = 240,
    [int] $NacosConvergenceTimeoutSeconds = 90,
    [string] $NacosBaseUrl = 'http://localhost:8848',
    [string] $AdminPassword = 'nacos',
    [string] $MysqlContainerName = 'school-bus-mysql',
    [switch] $KeepProcesses,
    [switch] $SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

$bootstrap = Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1'
if (-not (Test-Path -LiteralPath $bootstrap)) {
    throw "Missing bootstrap script: $bootstrap"
}

$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:AccessToken = $null
$script:RefreshToken = $null
$script:ReplacementAccessToken = $null
$script:ReplacementRefreshToken = $null

. $bootstrap

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $ProjectRoot "target\iam-acceptance-logs\$runId"
$reportDir = Join-Path $ProjectRoot 'target\iam-acceptance-reports'
New-Item -ItemType Directory -Force -Path $logDir, $reportDir | Out-Null

$script:Report = [ordered]@{
    startedAt = (Get-Date).ToString('o')
    status = 'RUNNING'
    services = [ordered]@{
        core = $false
        gateway = $false
        query = $false
        iam = $false
    }
    nacos = [ordered]@{
        coreHealthy = $null
        queryHealthy = $null
        iamHealthy = $null
        iamRemovalSeconds = $null
        iamRecoverySeconds = $null
    }
    routing = [ordered]@{
        registrationStatus = $null
        loginStatus = $null
        coreDirectRegistrationStatus = $null
        coreDirectLoginStatus = $null
    }
    jwt = [ordered]@{
        queryAccepted = $false
        coreAccepted = $false
        currentUserAccepted = $false
    }
    refreshRotation = [ordered]@{
        replacementIssued = $false
        oldRefreshRejectedStatus = $null
    }
    outage = [ordered]@{
        loginStatus = $null
        refreshStatus = $null
        existingTokenQueryStatus = $null
        existingTokenCoreStatus = $null
    }
    logout = [ordered]@{
        status = $null
        refreshRejectedStatus = $null
        accessTokenStillValidStatus = $null
    }
    privateKeyBoundary = [ordered]@{
        iamHasPrivateKey = $false
        coreHasPrivateKey = $false
        queryHasPrivateKey = $false
        gatewayHasPrivateKey = $false
    }
    errors = [System.Collections.Generic.List[string]]::new()
}

function ConvertFrom-JsonSafely([string] $Content) {
    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $null
    }
    try {
        return $Content | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Invoke-HttpCapture {
    param(
        [string] $Uri,
        [ValidateSet('GET', 'POST')]
        [string] $Method,
        [hashtable] $Headers = @{},
        [object] $Body = $null
    )

    $parameters = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        ContentType = 'application/json'
        UseBasicParsing = $true
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body | ConvertTo-Json -Depth 6 -Compress
    }

    try {
        $response = Invoke-WebRequest @parameters
        return [pscustomobject]@{
            status = [int] $response.StatusCode
            body = ConvertFrom-JsonSafely -Content $response.Content
        }
    } catch {
        if ($null -eq $_.Exception.Response) {
            throw
        }
        $content = $_.ErrorDetails.Message
        return [pscustomobject]@{
            status = [int] $_.Exception.Response.StatusCode
            body = ConvertFrom-JsonSafely -Content $content
        }
    }
}

function Assert-HttpStatus {
    param(
        [object] $Response,
        [int[]] $Expected,
        [string] $Label
    )
    if ($Expected -notcontains [int] $Response.status) {
        throw "$Label expected HTTP $($Expected -join ' or '), got $($Response.status)."
    }
}

function Get-LauncherHasPrivateKey([string] $LauncherPath) {
    if (-not (Test-Path -LiteralPath $LauncherPath)) {
        throw "Launcher not found for private-key boundary check: $LauncherPath"
    }
    return [bool] (Select-String `
        -LiteralPath $LauncherPath `
        -SimpleMatch 'JWT_PRIVATE_KEY_LOCATION' `
        -Quiet)
}

function Assert-RuntimePrivateKeyBoundary {
    $launchers = [ordered]@{
        core = Join-Path $logDir 'start-core.ps1'
        gateway = Join-Path $logDir 'start-gateway.ps1'
        query = Join-Path $logDir 'start-query.ps1'
        iam = Join-Path $logDir 'start-iam.ps1'
    }

    $script:Report.privateKeyBoundary.coreHasPrivateKey =
        Get-LauncherHasPrivateKey $launchers.core
    $script:Report.privateKeyBoundary.gatewayHasPrivateKey =
        Get-LauncherHasPrivateKey $launchers.gateway
    $script:Report.privateKeyBoundary.queryHasPrivateKey =
        Get-LauncherHasPrivateKey $launchers.query
    $script:Report.privateKeyBoundary.iamHasPrivateKey =
        Get-LauncherHasPrivateKey $launchers.iam

    if (-not $script:Report.privateKeyBoundary.iamHasPrivateKey) {
        throw 'IAM process was not given the JWT private-key location.'
    }
    if ($script:Report.privateKeyBoundary.coreHasPrivateKey -or
        $script:Report.privateKeyBoundary.queryHasPrivateKey -or
        $script:Report.privateKeyBoundary.gatewayHasPrivateKey) {
        throw 'JWT private-key location escaped the IAM runtime boundary.'
    }
}

function Start-IamService {
    param(
        [string] $JarPath,
        [hashtable] $Environment,
        [string] $Label
    )
    Start-TrackedJava `
        -JarPath $JarPath `
        -Environment $Environment `
        -LogPath (Join-Path $logDir "$Label.log") `
        -Label $Label `
        -LogDir $logDir
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
        -QueryPorts @($QueryPort) `
        -AdditionalPorts @($IamPort)

    Write-Step 'Check tracked content contains no private key'
    & (Join-Path $ProjectRoot 'scripts\security\check-no-private-keys.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw 'Tracked-content private-key scan failed.'
    }

    $nacosToken = Publish-NacosConfigs `
        -ProjectRoot $ProjectRoot `
        -NacosBaseUrl $NacosBaseUrl `
        -AdminPassword $AdminPassword
    $keys = Ensure-JwtKeys -ProjectRoot $ProjectRoot

    if (-not $SkipBuild) {
        Write-Step 'Build Core / Gateway / Query / IAM'
        Invoke-MavenPackage -WorkingDirectory $ProjectRoot -Label 'core'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $ProjectRoot 'cloud\gateway-service') `
            -Label 'gateway'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $ProjectRoot 'cloud\transport-query-service') `
            -Label 'transport-query'
        Invoke-MavenPackage `
            -WorkingDirectory (Join-Path $ProjectRoot 'cloud\iam-service') `
            -Label 'iam'
    }

    $coreJar = Find-BootJar `
        (Join-Path $ProjectRoot 'target') `
        'school-bus-platform'
    $gatewayJar = Find-BootJar `
        (Join-Path $ProjectRoot 'cloud\gateway-service\target') `
        'school-bus-gateway'
    $queryJar = Find-BootJar `
        (Join-Path $ProjectRoot 'cloud\transport-query-service\target') `
        'school-bus-transport-query'
    $iamJar = Find-BootJar `
        (Join-Path $ProjectRoot 'cloud\iam-service\target') `
        'school-bus-iam'

    $publicServiceEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
        JWT_PUBLIC_KEY_LOCATION = $keys.Public
        JWT_ISSUER = 'https://school-bus.local'
        JWT_AUDIENCE = 'school-bus-api'
    }

    Write-Step 'Start Core with public key only'
    $coreEnv = $publicServiceEnv.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'local,cloud'
    $coreEnv['CORE_SERVER_PORT'] = "$CorePort"
    $coreEnv['SERVER_PORT'] = "$CorePort"
    Start-TrackedJava `
        -JarPath $coreJar `
        -Environment $coreEnv `
        -LogPath (Join-Path $logDir 'core.log') `
        -Label 'core' `
        -LogDir $logDir

    Write-Step 'Start Transport Query with public key only'
    $queryEnv = $publicServiceEnv.Clone()
    $queryEnv['TRANSPORT_QUERY_SERVER_PORT'] = "$QueryPort"
    $queryEnv['SERVER_PORT'] = "$QueryPort"
    Start-TrackedJava `
        -JarPath $queryJar `
        -Environment $queryEnv `
        -LogPath (Join-Path $logDir 'query.log') `
        -Label 'query' `
        -LogDir $logDir

    Write-Step 'Start IAM with signing key'
    $iamEnv = $publicServiceEnv.Clone()
    $iamEnv['JWT_PRIVATE_KEY_LOCATION'] = $keys.Private
    $iamEnv['IAM_SERVER_PORT'] = "$IamPort"
    $iamEnv['SERVER_PORT'] = "$IamPort"
    Start-IamService -JarPath $iamJar -Environment $iamEnv -Label 'iam'

    Write-Step 'Start Gateway without JWT keys'
    $gatewayEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
        GATEWAY_SERVER_PORT = "$GatewayPort"
        SERVER_PORT = "$GatewayPort"
        SPRING_CLOUD_LOADBALANCER_CACHE_TTL = '2s'
        SPRING_CLOUD_LOADBALANCER_CACHE_ENABLED = 'true'
    }
    Start-TrackedJava `
        -JarPath $gatewayJar `
        -Environment $gatewayEnv `
        -LogPath (Join-Path $logDir 'gateway.log') `
        -Label 'gateway' `
        -LogDir $logDir

    Wait-HttpUp `
        "http://127.0.0.1:$CorePort/actuator/health" `
        $StartupTimeoutSeconds `
        'Core'
    $script:Report.services.core = $true
    Wait-HttpUp `
        "http://127.0.0.1:$QueryPort/actuator/health" `
        $StartupTimeoutSeconds `
        'Transport Query'
    $script:Report.services.query = $true
    Wait-HttpUp `
        "http://127.0.0.1:$IamPort/actuator/health" `
        $StartupTimeoutSeconds `
        'IAM'
    $script:Report.services.iam = $true
    Wait-HttpUp `
        "http://127.0.0.1:$GatewayPort/actuator/health" `
        $StartupTimeoutSeconds `
        'Gateway'
    $script:Report.services.gateway = $true

    $coreReady = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 1 `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-core'
    $queryReady = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 1 `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-transport-query'
    $iamReady = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 1 `
        -TimeoutSeconds $StartupTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-iam'
    $script:Report.nacos.coreHealthy = $coreReady.snapshot.healthy
    $script:Report.nacos.queryHealthy = $queryReady.snapshot.healthy
    $script:Report.nacos.iamHealthy = $iamReady.snapshot.healthy

    Write-Step 'Verify runtime private-key ownership'
    Assert-RuntimePrivateKeyBoundary

    $studentNumber = 'I{0:D7}' -f (Get-Random -Minimum 1000000 -Maximum 9999999)
    $password = 'IamVerify!2026'
    $gatewayBase = "http://127.0.0.1:$GatewayPort"
    $coreBase = "http://127.0.0.1:$CorePort"

    Write-Step 'Register and login through Gateway -> IAM'
    $registration = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/accounts" `
        -Method POST `
        -Body @{ studentNumber = $studentNumber; password = $password }
    $script:Report.routing.registrationStatus = $registration.status
    Assert-HttpStatus -Response $registration -Expected @(201) -Label 'registration'

    $login = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/login" `
        -Method POST `
        -Body @{ studentNumber = $studentNumber; password = $password }
    $script:Report.routing.loginStatus = $login.status
    Assert-HttpStatus -Response $login -Expected @(200) -Label 'login'
    $script:AccessToken = [string] $login.body.data.accessToken
    $script:RefreshToken = [string] $login.body.data.refreshToken
    if ([string]::IsNullOrWhiteSpace($script:AccessToken) -or
        [string]::IsNullOrWhiteSpace($script:RefreshToken)) {
        throw 'Login did not return both access and refresh tokens.'
    }
    $authHeaders = @{ Authorization = "Bearer $($script:AccessToken)" }

    Write-Step 'Verify IAM JWT is accepted independently by Query and Core'
    $queryResponse = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/trips?limit=5" `
        -Method GET `
        -Headers $authHeaders
    Assert-HttpStatus -Response $queryResponse -Expected @(200) -Label 'Query JWT verification'
    $script:Report.jwt.queryAccepted = $true

    $coreResponse = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings?page=0&size=1" `
        -Method GET `
        -Headers $authHeaders
    Assert-HttpStatus -Response $coreResponse -Expected @(200) -Label 'Core JWT verification'
    $script:Report.jwt.coreAccepted = $true

    $meResponse = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/me" `
        -Method GET `
        -Headers $authHeaders
    Assert-HttpStatus -Response $meResponse -Expected @(200) -Label 'current user'
    $script:Report.jwt.currentUserAccepted = $true

    Write-Step 'Verify cloud Core no longer exposes embedded IAM endpoints'
    $directRegistration = Invoke-HttpCapture `
        -Uri "$coreBase/api/v1/accounts" `
        -Method POST `
        -Body @{ studentNumber = 'DIRECT1'; password = $password }
    $script:Report.routing.coreDirectRegistrationStatus = $directRegistration.status
    Assert-HttpStatus `
        -Response $directRegistration `
        -Expected @(404) `
        -Label 'Core direct registration'

    $directLogin = Invoke-HttpCapture `
        -Uri "$coreBase/api/v1/auth/login" `
        -Method POST `
        -Body @{ studentNumber = $studentNumber; password = $password }
    $script:Report.routing.coreDirectLoginStatus = $directLogin.status
    Assert-HttpStatus `
        -Response $directLogin `
        -Expected @(404) `
        -Label 'Core direct login'

    Write-Step 'Stop IAM and verify failure isolation'
    Stop-ServiceByPort -Port $IamPort -Label 'iam'
    $iamRemoved = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 0 `
        -TimeoutSeconds $NacosConvergenceTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-iam'
    $script:Report.nacos.iamRemovalSeconds = $iamRemoved.seconds

    $outageLogin = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/login" `
        -Method POST `
        -Body @{ studentNumber = $studentNumber; password = $password }
    $script:Report.outage.loginStatus = $outageLogin.status
    Assert-HttpStatus -Response $outageLogin -Expected @(503) -Label 'login while IAM is down'

    $outageRefresh = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/refresh" `
        -Method POST `
        -Body @{ refreshToken = $script:RefreshToken }
    $script:Report.outage.refreshStatus = $outageRefresh.status
    Assert-HttpStatus -Response $outageRefresh -Expected @(503) -Label 'refresh while IAM is down'

    $queryDuringOutage = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/trips?limit=5" `
        -Method GET `
        -Headers $authHeaders
    $script:Report.outage.existingTokenQueryStatus = $queryDuringOutage.status
    Assert-HttpStatus `
        -Response $queryDuringOutage `
        -Expected @(200) `
        -Label 'Query with existing JWT while IAM is down'

    $coreDuringOutage = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/bookings?page=0&size=1" `
        -Method GET `
        -Headers $authHeaders
    $script:Report.outage.existingTokenCoreStatus = $coreDuringOutage.status
    Assert-HttpStatus `
        -Response $coreDuringOutage `
        -Expected @(200) `
        -Label 'Core with existing JWT while IAM is down'

    Write-Step 'Restart IAM and verify Nacos recovery'
    Start-IamService -JarPath $iamJar -Environment $iamEnv -Label 'iam-restart'
    Wait-HttpUp `
        "http://127.0.0.1:$IamPort/actuator/health" `
        $StartupTimeoutSeconds `
        'IAM restart'
    $iamRecovered = Wait-NacosServiceHealthyCount `
        -AccessToken $nacosToken `
        -Expected 1 `
        -TimeoutSeconds $NacosConvergenceTimeoutSeconds `
        -NacosBaseUrl $NacosBaseUrl `
        -ServiceName 'school-bus-iam'
    $script:Report.nacos.iamRecoverySeconds = $iamRecovered.seconds

    Write-Step 'Verify refresh-token rotation'
    $refresh = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/refresh" `
        -Method POST `
        -Body @{ refreshToken = $script:RefreshToken }
    Assert-HttpStatus -Response $refresh -Expected @(200) -Label 'refresh after IAM recovery'
    $script:ReplacementAccessToken = [string] $refresh.body.data.accessToken
    $script:ReplacementRefreshToken = [string] $refresh.body.data.refreshToken
    if ([string]::IsNullOrWhiteSpace($script:ReplacementAccessToken) -or
        [string]::IsNullOrWhiteSpace($script:ReplacementRefreshToken) -or
        $script:ReplacementRefreshToken -eq $script:RefreshToken) {
        throw 'Refresh did not rotate both tokens.'
    }
    $script:Report.refreshRotation.replacementIssued = $true

    $oldRefresh = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/refresh" `
        -Method POST `
        -Body @{ refreshToken = $script:RefreshToken }
    $script:Report.refreshRotation.oldRefreshRejectedStatus = $oldRefresh.status
    Assert-HttpStatus -Response $oldRefresh -Expected @(401) -Label 'reused refresh token'

    Write-Step 'Verify logout removes Redis refresh session'
    $replacementHeaders = @{
        Authorization = "Bearer $($script:ReplacementAccessToken)"
    }
    $logout = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/logout" `
        -Method POST `
        -Headers $replacementHeaders
    $script:Report.logout.status = $logout.status
    Assert-HttpStatus -Response $logout -Expected @(200) -Label 'logout'

    $refreshAfterLogout = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/auth/refresh" `
        -Method POST `
        -Body @{ refreshToken = $script:ReplacementRefreshToken }
    $script:Report.logout.refreshRejectedStatus = $refreshAfterLogout.status
    Assert-HttpStatus `
        -Response $refreshAfterLogout `
        -Expected @(401) `
        -Label 'refresh after logout'

    # Access tokens are intentionally stateless and remain usable until their
    # short expiry even after the Redis refresh session is deleted.
    $accessAfterLogout = Invoke-HttpCapture `
        -Uri "$gatewayBase/api/v1/trips?limit=5" `
        -Method GET `
        -Headers $replacementHeaders
    $script:Report.logout.accessTokenStillValidStatus = $accessAfterLogout.status
    Assert-HttpStatus `
        -Response $accessAfterLogout `
        -Expected @(200) `
        -Label 'short-lived access token after logout'

    $script:Report.status = 'PASSED'
    $script:Report.endedAt = (Get-Date).ToString('o')
}
catch {
    $script:Report.status = 'FAILED'
    $script:Report.errors.Add($_.Exception.Message)
    $script:Report.endedAt = (Get-Date).ToString('o')
    Write-Host "IAM acceptance FAILED: $($_.Exception.Message)" -ForegroundColor Red
    throw
}
finally {
    # Best effort: remove all sensitive values from script memory before the
    # report is serialized. The report only contains statuses and booleans.
    $script:AccessToken = $null
    $script:RefreshToken = $null
    $script:ReplacementAccessToken = $null
    $script:ReplacementRefreshToken = $null

    if (-not $KeepProcesses) {
        Stop-TrackedProcesses
    } else {
        Write-Host 'KeepProcesses set; leaving Java processes running.'
    }

    $reportPath = Join-Path $reportDir "iam-acceptance-$runId.json"
    $script:Report | ConvertTo-Json -Depth 8 | Set-Content `
        -LiteralPath $reportPath `
        -Encoding UTF8
    Write-Host "Report written: $reportPath"
}

Write-Host ''
Write-Host 'IAM strangler acceptance completed successfully.' -ForegroundColor Green

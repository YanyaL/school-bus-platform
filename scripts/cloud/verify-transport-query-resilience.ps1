#Requires -Version 5.1
<#
.SYNOPSIS
  Failure-window resilience acceptance for Transport Query GET retries.

.PARAMETER ResilienceEnabled
  When true, Gateway retries Query GET once on 502/503/504.
  When false, records the contrast run without Gateway retry.
#>
param(
    [string] $ProjectRoot = '',
    [switch] $ResilienceDisabled,
    [int] $GatewayPort = 8080,
    [int] $CorePort = 8081,
    [int[]] $QueryPorts = @(8082, 8083),
    [int] $WindowProbeCount = 40,
    [int] $PostFailoverCount = 20,
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

$ResilienceEnabled = -not $ResilienceDisabled.IsPresent

. (Join-Path $PSScriptRoot 'ha-request-counting.ps1')

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:AccessToken = $null
$script:Report = [ordered]@{
    startedAt = (Get-Date).ToString('o')
    resilienceEnabled = $ResilienceEnabled
    window = [ordered]@{}
    postFailover = [ordered]@{}
    recovery = [ordered]@{}
    gatewayRetryMetrics = [ordered]@{}
    unauthorizedStatus = $null
    errors = [System.Collections.Generic.List[string]]::new()
}

$logDir = Join-Path $ProjectRoot 'target\ha-logs'
$reportDir = Join-Path $ProjectRoot 'target\ha-reports'
New-Item -ItemType Directory -Force -Path $logDir, $reportDir | Out-Null

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-Percentile([double[]] $Samples, [double] $Pct) {
    if ($null -eq $Samples -or $Samples.Count -eq 0) { return $null }
    $sorted = @($Samples | Sort-Object)
    $index = [math]::Ceiling(($Pct / 100.0) * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return [math]::Round($sorted[$index], 1)
}

$bootstrap = Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1'
if (-not (Test-Path $bootstrap)) {
    throw "Missing $bootstrap"
}

. $bootstrap

try {
    Assert-Java21
    Assert-Docker
    Assert-InfraHealthy -NacosBaseUrl $NacosBaseUrl -MysqlContainerName $MysqlContainerName
    Assert-PortsFree -GatewayPort $GatewayPort -CorePort $CorePort -QueryPorts $QueryPorts

    $nacosToken = Publish-NacosConfigs -ProjectRoot $ProjectRoot -NacosBaseUrl $NacosBaseUrl -AdminPassword $AdminPassword
    $keys = Ensure-JwtKeys -ProjectRoot $ProjectRoot

    if (-not $SkipBuild) {
        Write-Step 'Build Core / Gateway / Query'
        Invoke-MavenPackage -WorkingDirectory $ProjectRoot -Label 'core'
        Invoke-MavenPackage -WorkingDirectory (Join-Path $ProjectRoot 'cloud\gateway-service') -Label 'gateway'
        Invoke-MavenPackage -WorkingDirectory (Join-Path $ProjectRoot 'cloud\transport-query-service') -Label 'transport-query'
    }

    $coreJar = Find-BootJar (Join-Path $ProjectRoot 'target') 'school-bus-platform'
    $gatewayJar = Find-BootJar (Join-Path $ProjectRoot 'cloud\gateway-service\target') 'school-bus-gateway'
    $queryJar = Find-BootJar (Join-Path $ProjectRoot 'cloud\transport-query-service\target') 'school-bus-transport-query'

    $commonEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
        JWT_PUBLIC_KEY_LOCATION = $keys.Public
        JWT_PRIVATE_KEY_LOCATION = $keys.Private
        JWT_ISSUER = 'https://school-bus.local'
        JWT_AUDIENCE = 'school-bus-api'
    }

    Write-Step 'Start Core / Gateway / Query dual instances'
    $coreEnv = $commonEnv.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'local,cloud'
    $coreEnv['CORE_SERVER_PORT'] = "$CorePort"
    $coreEnv['SERVER_PORT'] = "$CorePort"
    Start-TrackedJava -JarPath $coreJar -Environment $coreEnv -LogPath (Join-Path $logDir 'core-8081.log') -Label 'core' -LogDir $logDir

    $enabledLiteral = if ($ResilienceEnabled) { 'true' } else { 'false' }
    $gwEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
        GATEWAY_SERVER_PORT = "$GatewayPort"
        SERVER_PORT = "$GatewayPort"
        SPRING_CLOUD_LOADBALANCER_CACHE_TTL = '2s'
        SPRING_CLOUD_LOADBALANCER_CACHE_ENABLED = 'true'
        TRANSPORT_QUERY_RESILIENCE_ENABLED = $enabledLiteral
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = 'health,info,gateway,metrics'
    }
    Start-TrackedJava -JarPath $gatewayJar -Environment $gwEnv -LogPath (Join-Path $logDir 'gateway-8080.log') -Label 'gateway' -LogDir $logDir

    foreach ($port in $QueryPorts) {
        $queryEnv = $commonEnv.Clone()
        $queryEnv.Remove('JWT_PRIVATE_KEY_LOCATION')
        $queryEnv['TRANSPORT_QUERY_SERVER_PORT'] = "$port"
        $queryEnv['SERVER_PORT'] = "$port"
        $queryEnv['MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE'] = 'health,info,metrics'
        Start-TrackedJava -JarPath $queryJar -Environment $queryEnv -LogPath (Join-Path $logDir "query-$port.log") -Label "query-$port" -LogDir $logDir
    }

    Wait-HttpUp "http://127.0.0.1:$CorePort/actuator/health" $StartupTimeoutSeconds 'Core'
    foreach ($port in $QueryPorts) {
        Wait-HttpUp "http://127.0.0.1:$port/actuator/health" $StartupTimeoutSeconds "Query:$port"
    }
    Wait-HttpUp "http://127.0.0.1:$GatewayPort/actuator/health" $StartupTimeoutSeconds 'Gateway'

    $ready = Wait-NacosHealthyCount -AccessToken $nacosToken -Expected 2 -TimeoutSeconds $StartupTimeoutSeconds -NacosBaseUrl $NacosBaseUrl
    Write-Host ("Nacos healthy={0}" -f $ready.snapshot.healthy)

    $seedFile = Join-Path $ProjectRoot 'scripts\seed-local-demo.sql'
    if (Test-Path $seedFile) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        Get-Content -Raw $seedFile | docker exec -i $MysqlContainerName `
            mysql -uroot -proot --default-character-set=utf8mb4 school_bus_platform 2>$null
        $ErrorActionPreference = $previous
    }

    $studentNumber = 'R{0:D7}' -f (Get-Random -Minimum 1000000 -Maximum 9999999)
    $password = 'Resilience!2026'
    Invoke-GatewayJson -GatewayPort $GatewayPort -Method POST -Path '/api/v1/accounts' -Body @{
        studentNumber = $studentNumber
        password = $password
    } | Out-Null
    $login = Invoke-GatewayJson -GatewayPort $GatewayPort -Method POST -Path '/api/v1/auth/login' -Body @{
        studentNumber = $studentNumber
        password = $password
    }
    $script:AccessToken = $login.data.accessToken
    if ([string]::IsNullOrWhiteSpace($script:AccessToken)) {
        throw 'Login did not return accessToken.'
    }
    $authHeaders = @{ Authorization = "Bearer $($script:AccessToken)" }

    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$GatewayPort/api/v1/trips?limit=5" -UseBasicParsing -TimeoutSec 10 | Out-Null
        throw 'Expected 401 without JWT.'
    } catch {
        $script:Report.unauthorizedStatus = $_.Exception.Response.StatusCode.value__
        if ($script:Report.unauthorizedStatus -ne 401) {
            throw "Expected 401 without JWT, got $($script:Report.unauthorizedStatus)"
        }
    }

    $metricBefore = Get-GatewayRetryMetricTotal -GatewayPort $GatewayPort

    Write-Step "Failure-window probe ($WindowProbeCount requests) while stopping :$($QueryPorts[0])"
    $latencies = [System.Collections.Generic.List[double]]::new()
    $ok = 0
    $fail = 0
    $stopPort = $QueryPorts[0]
    $remainPort = $QueryPorts[1]
    $half = [math]::Floor($WindowProbeCount / 2)

    for ($i = 1; $i -le $WindowProbeCount; $i++) {
        if ($i -eq ($half + 1)) {
            Write-Host "Stopping Query :$stopPort mid-window"
            $convergenceStart = Get-Date
            Stop-QueryByPort $stopPort
            $script:Report.window.stoppedPort = $stopPort
            $converged = $false
            $deadline = (Get-Date).AddSeconds($NacosConvergenceTimeoutSeconds)
            while ((Get-Date) -lt $deadline) {
                $snap = Get-NacosQueryHealthyCount -AccessToken $nacosToken -NacosBaseUrl $NacosBaseUrl
                if ($snap.healthy -eq 1) {
                    $converged = $true
                    $script:Report.window.convergenceSeconds = [math]::Round(((Get-Date) - $convergenceStart).TotalSeconds, 1)
                    break
                }
                Start-Sleep -Milliseconds 200
            }
            if (-not $converged) {
                throw 'Nacos did not converge to healthy=1 during failure window.'
            }
        }

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $response = Invoke-GatewayJson -GatewayPort $GatewayPort -Method GET -Path '/api/v1/trips?limit=5' -Headers $authHeaders
            if ($response.code -ne 'OK') { throw "code=$($response.code)" }
            $ok++
        } catch {
            $fail++
            $script:Report.errors.Add("window[$i]: $($_.Exception.Message)")
        } finally {
            $sw.Stop()
            $latencies.Add($sw.Elapsed.TotalMilliseconds)
        }
    }

    $script:Report.window.total = $WindowProbeCount
    $script:Report.window.success = $ok
    $script:Report.window.failure = $fail
    $script:Report.window.p95Ms = Get-Percentile -Samples @($latencies) -Pct 95
    $script:Report.window.p99Ms = Get-Percentile -Samples @($latencies) -Pct 99

    $metricAfter = Get-GatewayRetryMetricTotal -GatewayPort $GatewayPort
    $script:Report.gatewayRetryMetrics.before = $metricBefore
    $script:Report.gatewayRetryMetrics.after = $metricAfter
    $script:Report.gatewayRetryMetrics.delta = [math]::Max(0, $metricAfter - $metricBefore)

    Start-Sleep -Seconds 5
    Write-Step "Post-failover $PostFailoverCount requests on :$remainPort"
    $postOk = 0
    for ($i = 1; $i -le $PostFailoverCount; $i++) {
        try {
            $response = Invoke-GatewayJson -GatewayPort $GatewayPort -Method GET -Path '/api/v1/trips?limit=5' -Headers $authHeaders
            if ($response.code -ne 'OK') { throw "code=$($response.code)" }
            $postOk++
        } catch {
            $script:Report.errors.Add("post[$i]: $($_.Exception.Message)")
        }
    }
    $script:Report.postFailover.ok = $postOk
    $script:Report.postFailover.total = $PostFailoverCount
    if ($postOk -ne $PostFailoverCount) {
        throw "Post-failover success $postOk / $PostFailoverCount"
    }

    Write-Step "Restart Query :$stopPort"
    $queryEnv = $commonEnv.Clone()
    $queryEnv.Remove('JWT_PRIVATE_KEY_LOCATION')
    $queryEnv['TRANSPORT_QUERY_SERVER_PORT'] = "$stopPort"
    $queryEnv['SERVER_PORT'] = "$stopPort"
    $queryEnv['MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE'] = 'health,info,metrics'
    Start-TrackedJava -JarPath $queryJar -Environment $queryEnv -LogPath (Join-Path $logDir "query-$stopPort.log") -Label "query-$stopPort-restart" -LogDir $logDir
    Wait-HttpUp "http://127.0.0.1:$stopPort/actuator/health" $StartupTimeoutSeconds "Query:$stopPort"
    $recovered = Wait-NacosHealthyCount -AccessToken $nacosToken -Expected 2 -TimeoutSeconds $NacosConvergenceTimeoutSeconds -NacosBaseUrl $NacosBaseUrl
    $script:Report.recovery.healthyAfterRestart = $recovered.snapshot.healthy
    $probe = Invoke-GatewayJson -GatewayPort $GatewayPort -Method GET -Path '/api/v1/trips?limit=5' -Headers $authHeaders
    $script:Report.recovery.gatewayOk = ($probe.code -eq 'OK')

    if ($ResilienceEnabled -and $script:Report.window.failure -ne 0) {
        throw "Resilience enabled but window failures=$($script:Report.window.failure)"
    }

    $script:Report.endedAt = (Get-Date).ToString('o')
    $script:Report.status = 'PASSED'
}
catch {
    $script:Report.status = 'FAILED'
    $script:Report.errors.Add($_.Exception.Message)
    Write-Host $_.Exception.Message -ForegroundColor Red
    throw
}
finally {
    if (-not $KeepProcesses) {
        Stop-TrackedProcesses
    }
    $reportPath = Join-Path $reportDir ("resilience-report-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
    ($script:Report | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Host "Report written: $reportPath"
    Write-Host ("ResilienceEnabled={0}; windowFail={1}; retryDelta={2}; p95={3}; p99={4}; converge={5}s" -f `
        $ResilienceEnabled,
        $script:Report.window.failure,
        $script:Report.gatewayRetryMetrics.delta,
        $script:Report.window.p95Ms,
        $script:Report.window.p99Ms,
        $script:Report.window.convergenceSeconds)
}

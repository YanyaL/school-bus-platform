#Requires -Version 5.1
<#
.SYNOPSIS
  Automated HA verification for school-bus-transport-query dual instances.

.DESCRIPTION
  Starts Core, Gateway, and two Query instances (8082/8083), proves Gateway
  load-balances via Nacos, then verifies single-instance failover recovery.
#>
param(
    [string] $ProjectRoot = '',
    [int] $GatewayPort = 8080,
    [int] $CorePort = 8081,
    [int[]] $QueryPorts = @(8082, 8083),
    [int] $RequestCount = 60,
    [int] $FailoverRequestCount = 20,
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

. (Join-Path $PSScriptRoot 'ha-request-counting.ps1')

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

$script:StartedPids = [System.Collections.Generic.List[int]]::new()
$script:AccessToken = $null
$script:Report = [ordered]@{
    startedAt = (Get-Date).ToString('o')
    javaVersion = $null
    mavenVersion = $null
    queryPorts = $QueryPorts
    nacosHealthyBefore = $null
    distribution = @{}
    loadSuccessRate = $null
    unauthorizedStatus = $null
    failover = [ordered]@{
        stoppedPort = $null
        convergenceSeconds = $null
        transientFailuresDuringConvergence = $null
        postConvergenceSuccessRate = $null
        postConvergenceOk = $null
    }
    recovery = [ordered]@{
        healthyAfterRestart = $null
        gatewayOk = $null
    }
    errors = [System.Collections.Generic.List[string]]::new()
}

$logDir = Join-Path $ProjectRoot 'target\ha-logs'
$reportDir = Join-Path $ProjectRoot 'target\ha-reports'
New-Item -ItemType Directory -Force -Path $logDir, $reportDir | Out-Null

function Write-Step([string] $Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Assert-Java21 {
    Write-Step 'Check Java 21'
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $javaOut = (& java -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    $mvnOut = (& mvn -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    $ErrorActionPreference = $previous
    $script:Report.javaVersion = (($javaOut -split "`n") | Select-Object -First 1).Trim()
    $script:Report.mavenVersion = (($mvnOut -split "`n") | Select-Object -First 1).Trim()
    Write-Host $script:Report.javaVersion
    Write-Host $script:Report.mavenVersion
    if ($javaOut -notmatch 'version "21') {
        throw "Java 21 is required. Got: $($script:Report.javaVersion)"
    }
}

function Assert-Docker {
    Write-Step 'Check Docker'
    $null = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker is not available.'
    }
    Write-Host 'Docker OK'
}

function Test-TcpPort([string] $HostName, [int] $Port, [int] $TimeoutMs = 800) {
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs)) {
            $client.Close()
            return $false
        }
        $client.EndConnect($iar)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Assert-InfraHealthy {
    Write-Step 'Check Nacos / MySQL / Redis'
    if (-not (Test-TcpPort '127.0.0.1' 8848 1500)) {
        throw "Nacos is not listening on 8848 ($NacosBaseUrl)."
    }
    $nacosContainer = docker ps --filter 'name=school-bus-nacos-3' --format '{{.Image}} {{.Status}}'
    if ([string]::IsNullOrWhiteSpace($nacosContainer)) {
        throw 'Container school-bus-nacos-3 (Nacos 3) is not running. Do not start Nacos 1.4.2.'
    }
    if ($nacosContainer -notmatch 'nacos-server:v3') {
        throw "Expected nacos-server:v3.x, found: $nacosContainer"
    }
    Write-Host "Nacos: $nacosContainer"

    if (-not (Test-TcpPort '127.0.0.1' 3306 1500)) {
        throw 'MySQL is not listening on 3306.'
    }
    $mysql = docker ps --filter "name=$MysqlContainerName" --format '{{.Names}} {{.Status}}'
    if ([string]::IsNullOrWhiteSpace($mysql) -or $mysql -notmatch 'healthy|Up') {
        throw "MySQL container '$MysqlContainerName' is not healthy."
    }
    Write-Host "MySQL: $mysql"

    if (-not (Test-TcpPort '127.0.0.1' 6379 1500)) {
        throw 'Redis is not listening on 6379.'
    }
    $redis = docker ps --filter 'name=school-bus-redis' --format '{{.Names}} {{.Status}}'
    if ([string]::IsNullOrWhiteSpace($redis)) {
        throw 'Redis container school-bus-redis is not running.'
    }
    Write-Host "Redis: $redis"
}

function Get-ListeningPids([int] $Port) {
    @(
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique |
            Where-Object { $null -ne $_ }
    )
}

function Assert-PortsFree {
    Write-Step 'Check application ports are free'
    $ports = @($GatewayPort, $CorePort) + @($QueryPorts)
    foreach ($port in $ports) {
        $pids = @(Get-ListeningPids $port)
        if ($pids.Count -gt 0) {
            throw "Port $port is already in use by PID(s): $($pids -join ', '). Refusing to kill foreign processes."
        }
        Write-Host "port $port free"
    }
}

function Invoke-NacosLogin {
    try {
        return Invoke-RestMethod `
            -Method Post `
            -Uri "$NacosBaseUrl/nacos/v3/auth/user/login" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{ username = 'nacos'; password = $AdminPassword }
    } catch {
        Write-Host 'Initializing Nacos admin...'
        $init = Invoke-RestMethod `
            -Method Post `
            -Uri "$NacosBaseUrl/nacos/v3/auth/user/admin" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{ password = $AdminPassword }
        if ($init.code -ne 0) {
            throw "Nacos admin init failed: $($init.message)"
        }
        return Invoke-RestMethod `
            -Method Post `
            -Uri "$NacosBaseUrl/nacos/v3/auth/user/login" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{ username = 'nacos'; password = $AdminPassword }
    }
}

function Publish-NacosConfigs {
    Write-Step 'Publish Nacos configs'
    $login = Invoke-NacosLogin
    $token = $login.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw 'Nacos login did not return accessToken.'
    }
    $configDir = Join-Path $ProjectRoot 'cloud\nacos-config'
    foreach ($dataId in @(
            'school-bus-core.yml',
            'school-bus-gateway.yml',
            'school-bus-transport-query.yml'
        )) {
        $content = Get-Content -Raw -LiteralPath (Join-Path $configDir $dataId)
        $publishUri = "$NacosBaseUrl/nacos/v3/admin/cs/config" +
            "?dataId=$([uri]::EscapeDataString($dataId))" +
            '&groupName=DEFAULT_GROUP' +
            "&content=$([uri]::EscapeDataString($content))"
        $result = Invoke-RestMethod -Method Post -Uri $publishUri -Headers @{ accessToken = $token }
        if ($result.code -ne 0) {
            throw "Publish $dataId failed: $($result.message)"
        }
        Write-Host "Published $dataId"
    }
    return $token
}

function Ensure-JwtKeys {
    Write-Step 'Ensure local JWT keys'
    $keysDir = Join-Path $ProjectRoot 'cloud\dev-keys'
    $publicKey = Join-Path $keysDir 'local-dev-public.pem'
    $privateKey = Join-Path $keysDir 'local-dev-private.pem'
    if (-not (Test-Path $publicKey) -or -not (Test-Path $privateKey)) {
        & (Join-Path $PSScriptRoot 'generate-local-jwt-keys.ps1') -OutputDirectory $keysDir
    }
    if (-not (Test-Path $publicKey) -or -not (Test-Path $privateKey)) {
        throw 'JWT key files are missing after generation.'
    }
    # Query must not ship private key in its resources.
    $queryPrivate = Join-Path $ProjectRoot 'cloud\transport-query-service\src\main\resources\jwt\local-dev-private.pem'
    if (Test-Path $queryPrivate) {
        throw 'Query service resources must not contain JWT private key.'
    }
    return @{
        Public  = "file:$publicKey"
        Private = "file:$privateKey"
    }
}

function Invoke-MavenPackage([string] $WorkingDirectory, [string] $Label) {
    Write-Host "Building $Label ..."
    Push-Location $WorkingDirectory
    try {
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package failed for $Label (exit $LASTEXITCODE)."
        }
    } finally {
        Pop-Location
    }
}

function Find-BootJar([string] $TargetDir, [string] $ArtifactPrefix) {
    $jar = Get-ChildItem -Path $TargetDir -Filter "$ArtifactPrefix-*.jar" |
        Where-Object { $_.Name -notmatch '(sources|javadoc|original)' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw "Boot jar not found in $TargetDir for prefix $ArtifactPrefix"
    }
    return $jar.FullName
}

function Start-TrackedJava {
    param(
        [string] $JarPath,
        [hashtable] $Environment,
        [string] $LogPath,
        [string] $Label
    )

    $launcherPath = Join-Path $logDir ("start-{0}.ps1" -f $Label)
    $lines = @(
        '$ErrorActionPreference = ''Continue''',
        '$env:JAVA_HOME = ''C:\Program Files\Java\jdk-21''',
        '$env:Path = "$env:JAVA_HOME\bin;$env:Path"'
    )
    foreach ($key in $Environment.Keys) {
        $value = ([string]$Environment[$key]).Replace("'", "''")
        $lines += ('$env:{0} = ''{1}''' -f $key, $value)
    }
    $jarEscaped = $JarPath.Replace("'", "''")
    $logEscaped = $LogPath.Replace("'", "''")
    $lines += '$javaExe = Join-Path $env:JAVA_HOME ''bin\java.exe'''
    $lines += ('& $javaExe -jar ''{0}'' > ''{1}'' 2>&1' -f $jarEscaped, $logEscaped)
    Set-Content -LiteralPath $launcherPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8

    $proc = Start-Process `
        -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $launcherPath) `
        -PassThru `
        -WindowStyle Hidden

    [void]$script:StartedPids.Add([int]$proc.Id)
    Write-Host ("Started {0} wrapper PID={1}, launcher={2}, log={3}" -f $Label, $proc.Id, $launcherPath, $LogPath)
}

function Wait-HttpUp([string] $Url, [int] $TimeoutSeconds, [string] $Label) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "$Label is up ($Url)"
                return
            }
        } catch {
            # keep waiting
        }
        Start-Sleep -Seconds 3
    }
    throw "$Label did not become ready within ${TimeoutSeconds}s: $Url"
}

function Get-NacosQueryHealthyCount([string] $AccessToken) {
    $uri = "$NacosBaseUrl/nacos/v1/ns/instance/list" +
        '?serviceName=school-bus-transport-query' +
        '&groupName=DEFAULT_GROUP' +
        "&accessToken=$([uri]::EscapeDataString($AccessToken))"
    $payload = Invoke-RestMethod -Uri $uri
    $hosts = @($payload.hosts)
    $healthy = @($hosts | Where-Object { $_.healthy -eq $true -and $_.enabled -ne $false })
    return [pscustomobject]@{
        total = @($hosts).Count
        healthy = @($healthy).Count
        hosts = $healthy
    }
}

function Wait-NacosHealthyCount {
    param(
        [string] $AccessToken,
        [int] $Expected,
        [int] $TimeoutSeconds
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $started = Get-Date
    while ((Get-Date) -lt $deadline) {
        $snapshot = Get-NacosQueryHealthyCount $AccessToken
        Write-Host ("Nacos query healthy={0} total={1}" -f $snapshot.healthy, $snapshot.total)
        if ($snapshot.healthy -eq $Expected) {
            return [pscustomobject]@{
                seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
                snapshot = $snapshot
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for Nacos healthyInstanceCount=$Expected"
}

function Invoke-GatewayJson {
    param(
        [string] $Method,
        [string] $Path,
        [hashtable] $Headers = @{},
        [object] $Body = $null
    )
    $uri = "http://127.0.0.1:$GatewayPort$Path"
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $Headers
        ContentType = 'application/json'
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 6 -Compress)
    }
    return Invoke-RestMethod @params
}

function Stop-TrackedProcesses {
    Write-Step 'Stop processes started by this script'
    foreach ($processId in ($script:StartedPids | Select-Object -Unique)) {
        try {
            $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
            if ($null -eq $proc) {
                continue
            }
            taskkill /PID $processId /T /F 2>$null | Out-Null
            Write-Host "Stopped PID $processId"
        } catch {
            Write-Host ("Could not stop PID {0}: {1}" -f $processId, $_.Exception.Message)
        }
    }
    $script:StartedPids.Clear()
}

function Stop-QueryByPort([int] $Port) {
    $listenerPids = @(Get-ListeningPids $Port)
    if (@($listenerPids).Count -eq 0) {
        throw "No listener found on query port $Port"
    }
    foreach ($processId in $listenerPids) {
        taskkill /PID $processId /T /F 2>$null | Out-Null
        Write-Host "Stopped listener PID $processId on port $Port"
        for ($i = $script:StartedPids.Count - 1; $i -ge 0; $i--) {
            if ($script:StartedPids[$i] -eq $processId) {
                $script:StartedPids.RemoveAt($i)
            }
        }
    }
}

try {
    Assert-Java21
    Assert-Docker
    Assert-InfraHealthy
    Assert-PortsFree

    $nacosToken = Publish-NacosConfigs
    $keys = Ensure-JwtKeys

    if (-not $SkipBuild) {
        Write-Step 'Build Core / Gateway / Query'
        Invoke-MavenPackage $ProjectRoot 'core'
        Invoke-MavenPackage (Join-Path $ProjectRoot 'cloud\gateway-service') 'gateway'
        Invoke-MavenPackage (Join-Path $ProjectRoot 'cloud\transport-query-service') 'transport-query'
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

    Write-Step 'Start Core :8081'
    $coreEnv = $commonEnv.Clone()
    $coreEnv['SPRING_PROFILES_ACTIVE'] = 'local,cloud'
    $coreEnv['CORE_SERVER_PORT'] = "$CorePort"
    $coreEnv['SERVER_PORT'] = "$CorePort"
    Start-TrackedJava -JarPath $coreJar -Environment $coreEnv -LogPath (Join-Path $logDir 'core-8081.log') -Label 'core'

    Write-Step 'Start Gateway :8080'
    $gwEnv = @{
        NACOS_CONFIG_ENABLED = 'true'
        NACOS_DISCOVERY_ENABLED = 'true'
        NACOS_SERVER_ADDR = '127.0.0.1:8848'
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = $AdminPassword
        GATEWAY_SERVER_PORT = "$GatewayPort"
        SERVER_PORT = "$GatewayPort"
        # Keep LB cache short so Nacos instance removal is reflected quickly during HA verification.
        SPRING_CLOUD_LOADBALANCER_CACHE_TTL = '2s'
        SPRING_CLOUD_LOADBALANCER_CACHE_ENABLED = 'true'
        TRANSPORT_QUERY_RESILIENCE_ENABLED = 'true'
        # Localhost acceptance only — gateway has no JWT on actuator.
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = 'health,info,gateway,metrics'
    }
    Start-TrackedJava -JarPath $gatewayJar -Environment $gwEnv -LogPath (Join-Path $logDir 'gateway-8080.log') -Label 'gateway'

    $script:HaAccessLogPaths = @{}
    foreach ($port in $QueryPorts) {
        Write-Step "Start Query :$port"
        $accessDir = Join-Path $logDir "access-$port"
        New-Item -ItemType Directory -Force -Path $accessDir | Out-Null
        $accessLogPath = Join-Path $accessDir "query-$port-access.log"
        if (Test-Path -LiteralPath $accessLogPath) {
            Remove-Item -LiteralPath $accessLogPath -Force
        }
        $script:HaAccessLogPaths[$port] = $accessLogPath
        $queryEnv = $commonEnv.Clone()
        $queryEnv.Remove('JWT_PRIVATE_KEY_LOCATION')
        $queryEnv['TRANSPORT_QUERY_SERVER_PORT'] = "$port"
        $queryEnv['SERVER_PORT'] = "$port"
        $queryEnv['MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE'] = 'health,info,metrics'
        $queryEnv['SERVER_TOMCAT_ACCESSLOG_ENABLED'] = 'true'
        $queryEnv['SERVER_TOMCAT_ACCESSLOG_DIRECTORY'] = $accessDir
        $queryEnv['SERVER_TOMCAT_ACCESSLOG_PREFIX'] = "query-$port-access"
        $queryEnv['SERVER_TOMCAT_ACCESSLOG_SUFFIX'] = '.log'
        $queryEnv['SERVER_TOMCAT_ACCESSLOG_PATTERN'] = 'common'
        $queryEnv['SERVER_TOMCAT_ACCESSLOG_ROTATE'] = 'false'
        # Unbuffered so distribution can read completed lines before process stop.
        $queryEnv['SERVER_TOMCAT_ACCESSLOG_BUFFERED'] = 'false'
        Start-TrackedJava `
            -JarPath $queryJar `
            -Environment $queryEnv `
            -LogPath (Join-Path $logDir "query-$port.log") `
            -Label "query-$port"
    }
    $accessLogPaths = $script:HaAccessLogPaths

    Wait-HttpUp "http://127.0.0.1:$CorePort/actuator/health" $StartupTimeoutSeconds 'Core'
    foreach ($port in $QueryPorts) {
        Wait-HttpUp "http://127.0.0.1:$port/actuator/health" $StartupTimeoutSeconds "Query:$port"
    }
    Wait-HttpUp "http://127.0.0.1:$GatewayPort/actuator/health" $StartupTimeoutSeconds 'Gateway'

    Write-Step 'Wait for 2 healthy Query instances in Nacos'
    $ready = Wait-NacosHealthyCount -AccessToken $nacosToken -Expected 2 -TimeoutSeconds $StartupTimeoutSeconds
    $script:Report.nacosHealthyBefore = $ready.snapshot.healthy
    Write-Host ("Nacos healthy instances={0} (waited {1}s)" -f $ready.snapshot.healthy, $ready.seconds)
    $portsRegistered = @($ready.snapshot.hosts | ForEach-Object { [int]$_.port }) | Sort-Object
    foreach ($port in $QueryPorts) {
        if ($portsRegistered -notcontains $port) {
            throw "Expected Query port $port registered in Nacos. Found: $($portsRegistered -join ', ')"
        }
    }

    Write-Step 'Seed demo trips if needed'
    $seedFile = Join-Path $ProjectRoot 'scripts\seed-local-demo.sql'
    if (Test-Path $seedFile) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        Get-Content -Raw $seedFile | docker exec -i $MysqlContainerName `
            mysql -uroot -proot --default-character-set=utf8mb4 school_bus_platform 2>$null
        $ErrorActionPreference = $previous
        Write-Host 'Seed applied.'
    } else {
        Write-Host 'seed-local-demo.sql not found; continuing with existing data.'
    }

    Write-Step 'Register + login through Gateway'
    $studentNumber = 'H{0:D7}' -f (Get-Random -Minimum 1000000 -Maximum 9999999)
    $password = 'HaVerify!2026'
    Invoke-GatewayJson -Method POST -Path '/api/v1/accounts' -Body @{
        studentNumber = $studentNumber
        password = $password
    } | Out-Null
    $login = Invoke-GatewayJson -Method POST -Path '/api/v1/auth/login' -Body @{
        studentNumber = $studentNumber
        password = $password
    }
    $script:AccessToken = $login.data.accessToken
    if ([string]::IsNullOrWhiteSpace($script:AccessToken)) {
        throw 'Login did not return accessToken.'
    }
    $authHeaders = @{ Authorization = "Bearer $($script:AccessToken)" }

    Write-Step 'JWT negative check (no token => 401)'
    try {
        Invoke-WebRequest -Uri "http://127.0.0.1:$GatewayPort/api/v1/trips?limit=5" -UseBasicParsing -TimeoutSec 10 | Out-Null
        throw 'Expected 401 without JWT.'
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        $script:Report.unauthorizedStatus = $status
        if ($status -ne 401) {
            throw "Expected HTTP 401 without JWT, got $status"
        }
        Write-Host 'Unauthorized without JWT: 401'
    }

    Write-Step "Load-balance $RequestCount authenticated trip list requests"
    $metricBefore = @{}
    $accessBefore = @{}
    foreach ($port in $QueryPorts) {
        $metricBefore[$port] = Get-TripMetricCount -Port $port -Token $script:AccessToken
        $accessBefore[$port] = Get-AccessLogTripCount -AccessLogPath $script:HaAccessLogPaths[$port] -SuccessOnly
    }

    $ok = 0
    $fail = 0
    for ($i = 1; $i -le $RequestCount; $i++) {
        try {
            $response = Invoke-GatewayJson -Method GET -Path '/api/v1/trips?limit=5' -Headers $authHeaders
            if ($response.code -ne 'OK') {
                throw "Unexpected code $($response.code)"
            }
            if ($null -eq $response.data) {
                throw 'Missing data array'
            }
            $ok++
        } catch {
            $fail++
            $script:Report.errors.Add("load[$i]: $($_.Exception.Message)")
        }
    }
    $script:Report.httpSuccessCount = $ok
    $script:Report.loadSuccessRate = [math]::Round(100.0 * $ok / $RequestCount, 2)
    if ($fail -ne 0 -or $ok -ne $RequestCount) {
        throw "Load phase had $fail failures out of $RequestCount (ok=$ok)"
    }

    $script:HaMetricToken = $script:AccessToken
    $resolved = Resolve-HaRequestDistribution `
        -Ports $QueryPorts `
        -RequestCount $RequestCount `
        -MetricBefore $metricBefore `
        -AccessBefore $accessBefore `
        -MetricRetryCount 5 `
        -MetricRetryDelaySeconds 2 `
        -AccessRetryCount 10 `
        -AccessRetryDelaySeconds 1 `
        -MetricReader {
            param($port)
            return Get-TripMetricCount -Port $port -Token $script:HaMetricToken
        } `
        -AccessReader {
            param($port)
            return Get-AccessLogTripCount -AccessLogPath $script:HaAccessLogPaths[$port] -SuccessOnly
        }

    foreach ($port in $QueryPorts) {
        $count = [int]$resolved.distribution["$port"]
        Write-Host ("Query :{0} handled {1} trip-list requests ({2})" -f $port, $count, $resolved.evidence)
        if ($count -le 0) {
            throw "Query instance :$port handled 0 trip-list requests during load phase."
        }
    }
    $sum = 0
    foreach ($port in $QueryPorts) { $sum += [int]$resolved.distribution["$port"] }
    if ($sum -ne $RequestCount) {
        throw "Distribution sum $sum != RequestCount $RequestCount"
    }

    $script:Report.distribution = $resolved.distribution
    $script:Report.distributionEvidence = $resolved.evidence
    $script:Report.metricsTotal = $resolved.metricsTotal
    $script:Report.accessLogTotal = $resolved.accessLogTotal
    Write-Host ("Evidence={0}; httpSuccess={1}; metricsTotal={2}; accessLogTotal={3}" -f `
        $resolved.evidence, $ok, $resolved.metricsTotal, $resolved.accessLogTotal)

    $stopPort = $QueryPorts[0]
    $remainPort = $QueryPorts[1]
    Write-Step "Stop Query :$stopPort and wait for Nacos healthy=1"
    $script:Report.failover.stoppedPort = $stopPort
    $convergenceTransientFail = 0
    $convergenceStart = Get-Date
    Stop-QueryByPort $stopPort

    # Observe transient failures until Nacos converges (do not invent retries).
    $converged = $false
    $deadline = (Get-Date).AddSeconds($NacosConvergenceTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-GatewayJson -Method GET -Path '/api/v1/trips?limit=1' -Headers $authHeaders | Out-Null
        } catch {
            $convergenceTransientFail++
        }
        $snap = Get-NacosQueryHealthyCount $nacosToken
        if ($snap.healthy -eq 1) {
            $converged = $true
            $script:Report.failover.convergenceSeconds = [math]::Round(((Get-Date) - $convergenceStart).TotalSeconds, 1)
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $converged) {
        throw 'Nacos did not converge to healthyInstanceCount=1 after stopping one Query.'
    }
    $script:Report.failover.transientFailuresDuringConvergence = $convergenceTransientFail
    Write-Host ("Nacos converged to 1 instance in {0}s; transient probe failures during convergence={1}" -f `
            $script:Report.failover.convergenceSeconds, $convergenceTransientFail)

    # Allow Gateway LoadBalancer cache to expire after Nacos removal.
    Start-Sleep -Seconds 5

    Write-Step "Post-failover $FailoverRequestCount requests (must all succeed)"
    $failoverOk = 0
    $failoverFail = 0
    for ($i = 1; $i -le $FailoverRequestCount; $i++) {
        try {
            $response = Invoke-GatewayJson -Method GET -Path '/api/v1/trips?limit=5' -Headers $authHeaders
            if ($response.code -ne 'OK') { throw "code=$($response.code)" }
            $failoverOk++
        } catch {
            $failoverFail++
            $script:Report.errors.Add("failover[$i]: $($_.Exception.Message)")
        }
    }
    $script:Report.failover.postConvergenceOk = $failoverOk
    $script:Report.failover.postConvergenceSuccessRate = [math]::Round(100.0 * $failoverOk / $FailoverRequestCount, 2)
    if ($failoverFail -ne 0) {
        throw "Failover phase failures=$failoverFail"
    }
    Write-Host "Failover success rate=$($script:Report.failover.postConvergenceSuccessRate)% on remaining :$remainPort"

    Write-Step "Restart Query :$stopPort"
    $accessDir = Join-Path $logDir "access-$stopPort"
    $queryEnv = $commonEnv.Clone()
    $queryEnv.Remove('JWT_PRIVATE_KEY_LOCATION')
    $queryEnv['TRANSPORT_QUERY_SERVER_PORT'] = "$stopPort"
    $queryEnv['SERVER_PORT'] = "$stopPort"
    $queryEnv['MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE'] = 'health,info,metrics'
    $queryEnv['SERVER_TOMCAT_ACCESSLOG_ENABLED'] = 'true'
    $queryEnv['SERVER_TOMCAT_ACCESSLOG_DIRECTORY'] = $accessDir
    $queryEnv['SERVER_TOMCAT_ACCESSLOG_PREFIX'] = "query-$stopPort-access"
    $queryEnv['SERVER_TOMCAT_ACCESSLOG_SUFFIX'] = '.log'
    $queryEnv['SERVER_TOMCAT_ACCESSLOG_PATTERN'] = 'common'
    $queryEnv['SERVER_TOMCAT_ACCESSLOG_ROTATE'] = 'false'
    $queryEnv['SERVER_TOMCAT_ACCESSLOG_BUFFERED'] = 'false'
    Start-TrackedJava `
        -JarPath $queryJar `
        -Environment $queryEnv `
        -LogPath (Join-Path $logDir "query-$stopPort.log") `
        -Label "query-$stopPort-restart"
    Wait-HttpUp "http://127.0.0.1:$stopPort/actuator/health" $StartupTimeoutSeconds "Query:$stopPort(restart)"
    $recovered = Wait-NacosHealthyCount -AccessToken $nacosToken -Expected 2 -TimeoutSeconds $NacosConvergenceTimeoutSeconds
    $script:Report.recovery.healthyAfterRestart = $recovered.snapshot.healthy
    $probe = Invoke-GatewayJson -Method GET -Path '/api/v1/trips?limit=5' -Headers $authHeaders
    $script:Report.recovery.gatewayOk = ($probe.code -eq 'OK')
    if (-not $script:Report.recovery.gatewayOk) {
        throw 'Gateway trip query failed after Query restart.'
    }
    Write-Host 'Recovery verified: healthy=2 and Gateway OK'

    $script:Report.endedAt = (Get-Date).ToString('o')
    $script:Report.status = 'PASSED'
}
catch {
    $script:Report.status = 'FAILED'
    $script:Report.errors.Add($_.Exception.Message)
    Write-Host "HA verification FAILED: $($_.Exception.Message)" -ForegroundColor Red
    throw
}
finally {
    # Clear token from memory as best-effort.
    $script:AccessToken = $null
    if (-not $KeepProcesses) {
        Stop-TrackedProcesses
    } else {
        Write-Host 'KeepProcesses set; leaving Java processes running.'
    }
    $reportPath = Join-Path $reportDir ("ha-report-{0:yyyyMMdd-HHmmss}.json" -f (Get-Date))
    $json = $script:Report | ConvertTo-Json -Depth 8 -Compress:$false
    Set-Content -LiteralPath $reportPath -Value $json -Encoding UTF8
    Write-Host "Report written: $reportPath"
}

Write-Host ""
Write-Host 'HA verification completed successfully.' -ForegroundColor Green

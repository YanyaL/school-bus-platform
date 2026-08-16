#Requires -Version 5.1
<#
.SYNOPSIS
  Shared process / infra helpers for Transport Query HA and resilience scripts.

.NOTES
  Dot-source from verify scripts. Caller must initialize:
    $script:StartedPids = [System.Collections.Generic.List[int]]::new()
  Do not print JWT or Nacos access tokens.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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
    $javaLine = (($javaOut -split "`n") | Select-Object -First 1).Trim()
    $mvnLine = (($mvnOut -split "`n") | Select-Object -First 1).Trim()
    Write-Host $javaLine
    Write-Host $mvnLine
    if ($javaOut -notmatch 'version "21') {
        throw "Java 21 is required. Got: $javaLine"
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
    param(
        [string] $NacosBaseUrl,
        [string] $MysqlContainerName
    )
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
    param(
        [int] $GatewayPort,
        [int] $CorePort,
        [int[]] $QueryPorts
    )
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
    param(
        [string] $NacosBaseUrl,
        [string] $AdminPassword
    )
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
    param(
        [string] $ProjectRoot,
        [string] $NacosBaseUrl,
        [string] $AdminPassword
    )
    Write-Step 'Publish Nacos configs'
    $login = Invoke-NacosLogin -NacosBaseUrl $NacosBaseUrl -AdminPassword $AdminPassword
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
    param([string] $ProjectRoot)
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

function Invoke-MavenPackage {
    param(
        [string] $WorkingDirectory,
        [string] $Label
    )
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
        [string] $Label,
        [string] $LogDir
    )

    $launcherPath = Join-Path $LogDir ("start-{0}.ps1" -f $Label)
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

function Wait-HttpUp([string] $Url, [int] $TimeoutSeconds, [string] $Name) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Host "$Name is up ($Url)"
                return
            }
        } catch {
            # keep waiting
        }
        Start-Sleep -Seconds 3
    }
    throw "$Name did not become ready within ${TimeoutSeconds}s: $Url"
}

function Get-NacosQueryHealthyCount {
    param(
        [string] $AccessToken,
        [string] $NacosBaseUrl
    )
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
        [int] $TimeoutSeconds,
        [string] $NacosBaseUrl
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $started = Get-Date
    while ((Get-Date) -lt $deadline) {
        $snapshot = Get-NacosQueryHealthyCount -AccessToken $AccessToken -NacosBaseUrl $NacosBaseUrl
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

function Invoke-GatewayJson {
    param(
        [int] $GatewayPort,
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

function Get-GatewayRetryMetricTotal {
    param([int] $GatewayPort)
    try {
        $uri = "http://127.0.0.1:$GatewayPort/actuator/metrics/school_bus_gateway_query_retry_total"
        $payload = Invoke-RestMethod -Uri $uri -TimeoutSec 5
        $sum = 0.0
        foreach ($measurement in @($payload.measurements)) {
            if ([string]$measurement.statistic -eq 'COUNT') {
                $sum += [double]$measurement.value
            }
        }
        return [double]$sum
    } catch {
        return 0
    }
}

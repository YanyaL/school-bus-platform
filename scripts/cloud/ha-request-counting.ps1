# Shared HA request-counting helpers (dot-sourced by verify + tests).
# Do not mix tag sets. Exact meter tags only:
#   uri=/api/v1/trips, method=GET, status=200

function Get-ExactTripListMetricUri([int] $Port) {
    $base = "http://127.0.0.1:$Port/actuator/metrics/http.server.requests"
    return (${base} + '?tag=uri:/api/v1/trips&tag=method:GET&tag=status:200')
}

function Get-TripMetricCount {
    param(
        [int] $Port,
        [string] $Token,
        [scriptblock] $Invoker = $null
    )
    $uri = Get-ExactTripListMetricUri -Port $Port
    $headers = @{ Authorization = "Bearer $Token" }
    try {
        if ($null -ne $Invoker) {
            $metric = & $Invoker $uri $headers
        } else {
            $metric = Invoke-RestMethod -Uri $uri -Headers $headers -TimeoutSec 10
        }
        $count = ($metric.measurements |
            Where-Object { $_.statistic -eq 'COUNT' } |
            Select-Object -First 1).value
        if ($null -eq $count) {
            return 0
        }
        return [double]$count
    } catch {
        # Missing meter / unknown tag combination => treat as zero baseline.
        return 0
    }
}

function Get-AccessLogTripCount {
    param(
        [string] $AccessLogPath,
        [switch] $SuccessOnly
    )
    if ([string]::IsNullOrWhiteSpace($AccessLogPath) -or -not (Test-Path $AccessLogPath)) {
        return 0
    }
    $lines = @(Get-Content -LiteralPath $AccessLogPath -ErrorAction SilentlyContinue)
    $matched = @(
        $lines | Where-Object {
            $_ -match 'GET /api/v1/trips(\?|\s)' -and
            $_ -notmatch '/seats' -and
            (-not $SuccessOnly -or $_ -match '"\s+200\s')
        }
    )
    return $matched.Count
}

function Get-PortCountMap {
    param(
        [int[]] $Ports,
        [hashtable] $Before,
        [hashtable] $After
    )
    $deltas = @{}
    $total = 0
    foreach ($port in $Ports) {
        $beforeValue = 0
        if ($Before.ContainsKey($port)) { $beforeValue = [double]$Before[$port] }
        elseif ($Before.ContainsKey("$port")) { $beforeValue = [double]$Before["$port"] }
        $afterValue = 0
        if ($After.ContainsKey($port)) { $afterValue = [double]$After[$port] }
        elseif ($After.ContainsKey("$port")) { $afterValue = [double]$After["$port"] }
        $delta = [int][math]::Max(0, [math]::Round($afterValue - $beforeValue))
        $deltas["$port"] = $delta
        $total += $delta
    }
    return [pscustomobject]@{
        deltas = $deltas
        total = $total
    }
}

function Resolve-HaRequestDistribution {
    param(
        [int[]] $Ports,
        [int] $RequestCount,
        [hashtable] $MetricBefore,
        [hashtable] $AccessBefore,
        [scriptblock] $MetricReader,
        [scriptblock] $AccessReader,
        [int] $MetricRetryCount = 5,
        [int] $MetricRetryDelaySeconds = 2,
        [int] $AccessRetryCount = 10,
        [int] $AccessRetryDelaySeconds = 1
    )

    function Read-AccessSnapshot {
        $accessAfter = @{}
        foreach ($port in $Ports) {
            $accessAfter[$port] = [int](& $AccessReader $port)
        }
        return (Get-PortCountMap -Ports $Ports -Before $AccessBefore -After $accessAfter)
    }

    $metricTotal = -1
    $metricDeltas = $null
    for ($attempt = 1; $attempt -le $MetricRetryCount; $attempt++) {
        $metricAfter = @{}
        foreach ($port in $Ports) {
            $metricAfter[$port] = [double](& $MetricReader $port)
        }
        $metricMap = Get-PortCountMap -Ports $Ports -Before $MetricBefore -After $metricAfter
        $metricTotal = $metricMap.total
        $metricDeltas = $metricMap.deltas
        if ($metricTotal -eq $RequestCount) {
            $accessSnapshot = Read-AccessSnapshot
            return [pscustomobject]@{
                evidence = 'metrics'
                distribution = $metricDeltas
                metricsTotal = $metricTotal
                accessLogTotal = $accessSnapshot.total
                httpSuccessCount = $RequestCount
            }
        }
        if ($attempt -lt $MetricRetryCount) {
            Start-Sleep -Seconds $MetricRetryDelaySeconds
        }
    }

    $accessTotal = -1
    $accessDeltas = $null
    for ($attempt = 1; $attempt -le $AccessRetryCount; $attempt++) {
        $accessMap = Read-AccessSnapshot
        $accessTotal = $accessMap.total
        $accessDeltas = $accessMap.deltas
        if ($accessTotal -eq $RequestCount) {
            return [pscustomobject]@{
                evidence = 'access-log'
                distribution = $accessDeltas
                metricsTotal = $metricTotal
                accessLogTotal = $accessTotal
                httpSuccessCount = $RequestCount
            }
        }
        if ($attempt -lt $AccessRetryCount) {
            Start-Sleep -Seconds $AccessRetryDelaySeconds
        }
    }

    throw ("Request distribution mismatch. httpSuccess={0}, metricsTotal={1}, accessLogTotal={2}. Refusing mixed evidence." -f `
        $RequestCount, $metricTotal, $accessTotal)
}


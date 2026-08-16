#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$helpers = Join-Path $PSScriptRoot 'ha-request-counting.ps1'
. $helpers

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) {
        throw $Message
    }
}

Write-Host 'Running HA counting unit checks...'

# Exact URI must include all three tags and must not be a broad fallback.
$uri = Get-ExactTripListMetricUri -Port 8082
Assert-True ($uri -match 'tag=uri:/api/v1/trips') 'uri tag missing'
Assert-True ($uri -match 'tag=method:GET') 'method tag missing'
Assert-True ($uri -match 'tag=status:200') 'status tag missing'
Assert-True ($uri -notmatch 'tag=status:401') 'must not query 401 meter'

# Baseline without 200 meter => 0 (simulated 404 / missing meter).
$missing = Get-TripMetricCount -Port 8082 -Token 't' -Invoker {
    param($u, $h)
    throw "404 meter not found for $u"
}
Assert-True ($missing -eq 0) "Expected 0 when 200 meter missing, got $missing"

# Prior 401 traffic must not be readable through exact 200 helper.
# Invoker only ever called with status:200 URI (Micrometer tag syntax).
$seenUri = $null
$countWithPrior401Noise = Get-TripMetricCount -Port 8082 -Token 't' -Invoker {
    param($u, $h)
    $script:seenUri = $u
    # Simulate: only the exact 200 meter is returned; a broad fallback would
    # have mixed in prior 401 counts. We assert the URI never asks for 401.
    return [pscustomobject]@{
        measurements = @([pscustomobject]@{ statistic = 'COUNT'; value = 3 })
    }
}
Assert-True ($seenUri -match 'tag=status:200') 'helper must request status:200 only'
Assert-True ($seenUri -notmatch 'tag=status:401') 'helper must not request status:401'
Assert-True ($countWithPrior401Noise -eq 3) 'exact 200 meter value must be used as-is'

# Must never subtract mismatched tag sets (e.g. all-status after minus 200-only before).
$mismatched = Get-PortCountMap `
    -Ports @(8082) `
    -Before @{ 8082 = 10 } `
    -After @{ 8082 = 15 }
Assert-True ($mismatched.total -eq 5) 'delta uses same tag baseline only'
# Documented invariant: callers must feed before/after from identical tag URIs;
# Resolve-HaRequestDistribution always uses the same MetricReader for both.

# Delta math with retry: first incomplete metrics refresh, then exact 60.
$before = @{ 8082 = 0; 8083 = 0 }
$metricReads = @(
    @{ 8082 = 25; 8083 = 30 },
    @{ 8082 = 30; 8083 = 30 }
)
$state = @{ attempt = -1; current = $null }
$resolved = Resolve-HaRequestDistribution `
    -Ports @(8082, 8083) `
    -RequestCount 60 `
    -MetricBefore $before `
    -AccessBefore @{ 8082 = 0; 8083 = 0 } `
    -MetricRetryCount 3 `
    -MetricRetryDelaySeconds 0 `
    -AccessRetryCount 1 `
    -AccessRetryDelaySeconds 0 `
    -MetricReader {
        param($port)
        if ($port -eq 8082) {
            $state.attempt++
            $state.current = $metricReads[$state.attempt]
        }
        return $state.current[$port]
    } `
    -AccessReader { param($port) return 0 }

Assert-True ($resolved.evidence -eq 'metrics') 'expected metrics evidence'
Assert-True ($resolved.metricsTotal -eq 60) "metricsTotal=$($resolved.metricsTotal)"
Assert-True ([int]$resolved.distribution['8082'] -eq 30) '8082 delta'
Assert-True ([int]$resolved.distribution['8083'] -eq 30) '8083 delta'

# When metrics never reach RequestCount, fall back uniformly to access-log.
$resolvedAccess = Resolve-HaRequestDistribution `
    -Ports @(8082, 8083) `
    -RequestCount 60 `
    -MetricBefore @{ 8082 = 0; 8083 = 0 } `
    -AccessBefore @{ 8082 = 0; 8083 = 0 } `
    -MetricRetryCount 2 `
    -MetricRetryDelaySeconds 0 `
    -AccessRetryCount 2 `
    -AccessRetryDelaySeconds 0 `
    -MetricReader { param($port) if ($port -eq 8082) { 20 } else { 20 } } `
    -AccessReader { param($port) if ($port -eq 8082) { 29 } else { 31 } }

Assert-True ($resolvedAccess.evidence -eq 'access-log') 'expected access-log evidence'
Assert-True ($resolvedAccess.accessLogTotal -eq 60) 'access log total'
Assert-True (($resolvedAccess.distribution['8082'] + $resolvedAccess.distribution['8083']) -eq 60) 'access sum'

# Must fail when neither source sums to RequestCount (no mixing).
$failed = $false
try {
    Resolve-HaRequestDistribution `
        -Ports @(8082, 8083) `
        -RequestCount 60 `
        -MetricBefore @{ 8082 = 0; 8083 = 0 } `
        -AccessBefore @{ 8082 = 0; 8083 = 0 } `
        -MetricRetryCount 1 `
        -MetricRetryDelaySeconds 0 `
        -AccessRetryCount 1 `
        -AccessRetryDelaySeconds 0 `
        -MetricReader { param($port) 10 } `
        -AccessReader { param($port) 11 } | Out-Null
} catch {
    $failed = $true
}
Assert-True $failed 'must fail when totals mismatch'

# Access log parser: count only successful trip-list lines.
$tempLog = Join-Path $env:TEMP ("ha-access-{0}.log" -f [guid]::NewGuid())
@(
    '127.0.0.1 - - [15/Aug/2026:17:00:00 +1000] "GET /api/v1/trips?limit=5 HTTP/1.1" 200 123',
    '127.0.0.1 - - [15/Aug/2026:17:00:01 +1000] "GET /api/v1/trips?limit=5 HTTP/1.1" 401 12',
    '127.0.0.1 - - [15/Aug/2026:17:00:02 +1000] "GET /api/v1/trips/11111111-1111-1111-1111-111111111111/seats HTTP/1.1" 200 99'
) | Set-Content -LiteralPath $tempLog -Encoding ascii
$accessCount = Get-AccessLogTripCount -AccessLogPath $tempLog -SuccessOnly
Assert-True ($accessCount -eq 1) "access success-only count=$accessCount"
Remove-Item -LiteralPath $tempLog -Force

Write-Host 'HA counting unit checks PASSED' -ForegroundColor Green

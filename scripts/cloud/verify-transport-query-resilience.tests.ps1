#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-Percentile([double[]] $Samples, [double] $Pct) {
    if ($null -eq $Samples -or $Samples.Count -eq 0) { return $null }
    $sorted = @($Samples | Sort-Object)
    $index = [math]::Ceiling(($Pct / 100.0) * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $sorted.Count) { $index = $sorted.Count - 1 }
    return [math]::Round($sorted[$index], 1)
}

$samples = @(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
$p95 = Get-Percentile -Samples $samples -Pct 95
$p99 = Get-Percentile -Samples $samples -Pct 99
if ($p95 -ne 100.0) { throw "p95 expected 100 got $p95" }
if ($p99 -ne 100.0) { throw "p99 expected 100 got $p99" }

$files = @(
    (Join-Path $PSScriptRoot 'ha-process-bootstrap.ps1'),
    (Join-Path $PSScriptRoot 'verify-transport-query-resilience.ps1'),
    (Join-Path $PSScriptRoot 'ha-request-counting.ps1')
)
foreach ($f in $files) {
    $errs = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($f, [ref]$null, [ref]$errs)
    if ($errs -and $errs.Count -gt 0) {
        throw ("Parse failed for {0}: {1}" -f $f, ($errs | ForEach-Object { $_.ToString() } | Out-String))
    }
}

Write-Host 'Resilience script unit checks PASSED' -ForegroundColor Green

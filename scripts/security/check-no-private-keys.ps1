#Requires -Version 5.1
<#
.SYNOPSIS
  Fail if Git-tracked content contains private key material.

.DESCRIPTION
  Scans only Git-tracked files/paths. Never prints secret bodies — only
  path names and match categories on failure.
#>
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure([string] $Message) {
    $failures.Add($Message)
}

Write-Host 'Checking Git-tracked content for private key material...'

$trackedDevKeys = @(git ls-files -- 'cloud/dev-keys' 2>$null)
foreach ($path in $trackedDevKeys) {
    if ($path -match '\.pem$' -or $path -match '(?i)private') {
        Add-Failure ("tracked cloud/dev-keys secret-like path: $path")
    }
}

$trackedPem = @(git ls-files -- '*.pem' 2>$null)
foreach ($path in $trackedPem) {
    $name = Split-Path -Leaf $path
    if ($name -match '(?i)private') {
        Add-Failure ("tracked private PEM path: $path")
    }
}

# Scan tracked file contents for private-key PEM headers only (no body dump).
# Patterns are concatenated so this script itself does not contain the full marker.
$patterns = @(
    ('BEGIN' + ' PRIVATE KEY'),
    ('BEGIN' + ' RSA PRIVATE KEY')
)

foreach ($pattern in $patterns) {
    $matches = @(git grep -n -I --fixed-strings -- $pattern 2>$null)
    foreach ($line in $matches) {
        # Format: path:line:content — report path + category only
        $pathPart = ($line -split ':', 2)[0]
        Add-Failure ("tracked file contains '$pattern' marker: $pathPart")
    }
}

if ($failures.Count -gt 0) {
    Write-Host 'PRIVATE KEY SCAN FAILED' -ForegroundColor Red
    foreach ($item in $failures) {
        Write-Host (" - $item")
    }
    exit 1
}

Write-Host 'PRIVATE KEY SCAN PASSED (no private key markers in tracked content)' -ForegroundColor Green
exit 0

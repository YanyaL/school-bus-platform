# Runs RabbitMQ messaging acceptance tests (Testcontainers + Docker).
# Requires Docker Desktop and Java 21.
#
# Usage:
#   .\scripts\run-messaging-acceptance.ps1
#   .\scripts\run-messaging-acceptance.ps1 -SkipTopology   # full E2E only
#   .\scripts\run-messaging-acceptance.ps1 -SkipMessaging  # topology only

param(
    [switch]$SkipTopology,
    [switch]$SkipMessaging
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$env:RUN_MESSAGING_ACCEPTANCE_TESTS = "true"

$tests = @()
if (-not $SkipTopology) {
    $tests += @(
        "RabbitMqTopologyIntegrationTest",
        "BookingExpirationRabbitTopologyIntegrationTest"
    )
}
if (-not $SkipMessaging) {
    $tests += "BookingExpirationMessagingIntegrationTest"
}

if ($tests.Count -eq 0) {
    Write-Error "Nothing to run: enable topology and/or messaging tests."
}

$testArg = $tests -join ","
Write-Host "Running messaging acceptance tests: $testArg"
Write-Host "RUN_MESSAGING_ACCEPTANCE_TESTS=$env:RUN_MESSAGING_ACCEPTANCE_TESTS"

mvn test `
    "-Dtest=$testArg" `
    "-Dsurefire.failIfNoSpecifiedTests=false"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Messaging acceptance tests passed."

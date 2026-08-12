#Requires -Version 5.1
<#
.SYNOPSIS
  Run Testcontainers messaging acceptance tests (Outbox relay, TTL/DLX).

.EXAMPLE
  .\scripts\run-messaging-acceptance.ps1
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path $PSScriptRoot -Parent
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "E:\jdk-21_windows-x64_bin\jdk-21.0.6" }

if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$env:RUN_MESSAGING_ACCEPTANCE_TESTS = "true"

Write-Host "Running messaging acceptance tests (Docker required)..." -ForegroundColor Cyan
Push-Location $projectRoot
try {
    mvn test `
        "-Dtest=BookingExpirationMessagingIntegrationTest,BookingExpirationRabbitTopologyIntegrationTest,RabbitMqTopologyIntegrationTest" `
        "-Dsurefire.failIfNoSpecifiedTests=false"
} finally {
    Pop-Location
}

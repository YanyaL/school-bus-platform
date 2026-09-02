#Requires -Version 5.1
<#
.SYNOPSIS
  Strict TripPublished acceptance against disposable MySQL and RabbitMQ containers.
  A successful Maven exit with skipped tests is NOT acceptance.
#>
param(
    [string] $JavaHome = $env:JAVA_HOME,
    [string] $Maven = 'mvn'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$outputDirectory = Join-Path $projectRoot "target/trip-publication-outbox-$runId"
$null = New-Item -ItemType Directory -Path $outputDirectory
$report = [ordered]@{
    status = 'FAILED'
    phase = 'preflight'
    tests = 0
    skipped = 0
    failures = 0
    errors = 0
    message = ''
    evidence = 'TripPublicationTransactionIntegrationTest (disposable Testcontainers)'
}
$exitCode = 1
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
Push-Location $projectRoot
try {
    if ($JavaHome) {
        $env:JAVA_HOME = $JavaHome
        $env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
    }
    $mavenCommand = (Get-Command $Maven -ErrorAction Stop).Source
    $version = & $mavenCommand -version 2>&1
    if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -notmatch 'Java version: 21[.,]') {
        throw 'Maven must use Java 21; pass -JavaHome with your JDK 21 path.'
    }
    $dockerVersion = & docker info --format '{{.ServerVersion}}' 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($dockerVersion -join "`n")
    }

    $report.phase = 'integration-tests'
    $startedAt = [DateTime]::UtcNow
    & $mavenCommand test '-Dtest=TripPublicationTransactionIntegrationTest' '-DskipTests=false' '-Dmaven.test.skip=false' *> (Join-Path $outputDirectory 'maven.log')
    $mavenExit = $LASTEXITCODE
    $suiteFile = Join-Path $projectRoot 'target/surefire-reports/TEST-com.schoolbus.transport.application.trip.TripPublicationTransactionIntegrationTest.xml'
    if (-not (Test-Path -LiteralPath $suiteFile) -or (Get-Item -LiteralPath $suiteFile).LastWriteTimeUtc -lt $startedAt) {
        throw 'No fresh Surefire result; refusing to reuse an earlier test report.'
    }
    [xml] $xml = Get-Content -LiteralPath $suiteFile -Raw
    $report.tests = [int] $xml.testsuite.tests
    $report.skipped = [int] $xml.testsuite.skipped
    $report.failures = [int] $xml.testsuite.failures
    $report.errors = [int] $xml.testsuite.errors
    Copy-Item -LiteralPath $suiteFile -Destination (Join-Path $outputDirectory 'tests.xml')
    $requiredCases = @(
        'shouldCommitStatusSeatsInventoryAndThenEvictCache',
        'shouldRollbackTripAndSeatsWhenInventoryInitializationFails',
        'outboxInsertFailureRollsBackTripSeatsAndInventory',
        'duplicatePublishDoesNotAppendAnotherEvent',
        'publicationOutboxRejectsCallsOutsideTransaction',
        'committedPublicationReachesRealRabbitWithStableEventId',
        'unroutableEventRemainsRetryableAndRecoversWithSameIdentity'
    )
    foreach ($name in $requiredCases) {
        $cases = @($xml.testsuite.testcase | Where-Object { $_.name -eq $name })
        if ($cases.Count -ne 1) { throw "Missing required acceptance case: $name" }
    }
    if ($mavenExit -ne 0 -or $report.tests -lt 10 -or $report.skipped -ne 0 -or $report.failures -ne 0 -or $report.errors -ne 0) {
        throw 'Acceptance requires a successful Maven run, all required cases, and zero skips/failures/errors. See maven.log.'
    }
    $report.status = 'PASSED'
    $report.message = 'Real MySQL rollback and RabbitMQ confirm/return/retry tests passed.'
    $exitCode = 0
} catch {
    if ($report.phase -eq 'preflight') { $report.status = 'BLOCKED' }
    $report.message = $_.Exception.Message
} finally {
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputDirectory 'report.json') -Encoding UTF8
    $env:JAVA_HOME = $previousJavaHome
    $env:Path = $previousPath
    Pop-Location
}
Write-Host ("TripPublished acceptance: {0}. Report: {1}" -f $report.status, (Join-Path $outputDirectory 'report.json'))
if ($exitCode -ne 0) { Write-Warning $report.message }
exit $exitCode

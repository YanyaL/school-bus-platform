#Requires -Version 5.1
<#
.SYNOPSIS
  Verify producer and Booking shadow consumer with disposable MySQL/RabbitMQ suites.
  Does not modify live infrastructure or enable production feature flags.
#>
param([string] $JavaHome = $env:JAVA_HOME, [string] $Maven = 'mvn')
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'trip-publication-acceptance.helpers.ps1')
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
$outputDirectory = Join-Path $projectRoot "target/trip-publication-shadow-$runId"
$null = New-Item -ItemType Directory -Path $outputDirectory
$report = [ordered]@{ status='FAILED'; phase='preflight'; evidence=@(); message=''; liveCutoverPerformed=$false }
$exitCode = 1
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
Push-Location $projectRoot
try {
    if ($JavaHome) { $env:JAVA_HOME=$JavaHome; $env:Path=(Join-Path $JavaHome 'bin')+';'+$env:Path }
    $mavenCommand = (Get-Command $Maven -ErrorAction Stop).Source
    $version = & $mavenCommand -version 2>&1
    if ($LASTEXITCODE -ne 0 -or ($version -join "`n") -notmatch 'Java version: 21[.,]') { throw 'Maven must use Java 21.' }
    $dockerVersion = & docker info --format '{{.ServerVersion}}' 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($dockerVersion -join "`n") }
    $suites = @(
        @{
            module='.'; suite='com.schoolbus.transport.application.trip.TripPublicationTransactionIntegrationTest'
            cases=@('shouldCommitStatusSeatsInventoryAndThenEvictCache','outboxInsertFailureRollsBackTripSeatsAndInventory',
                'publicationOutboxRejectsCallsOutsideTransaction','committedPublicationReachesRealRabbitWithStableEventId',
                'unroutableEventRemainsRetryableAndRecoversWithSameIdentity')
        },
        @{
            module='cloud/booking-service'; suite='com.schoolbus.bookingservice.trippublication.TripPublicationShadowIntegrationTest'
            cases=@('duplicateDeliveryChangesNeitherSnapshotNorTimestamp','sameVersionNewIdentityIsAlreadyAppliedAndOlderVersionCannotOverwrite',
                'newerVersionAdvancesProjection','sameEventDifferentPayloadAndSameVersionConflictRollBack',
                'finalMarkerFailureRollsBackSnapshotAndInboxTogether','concurrentDuplicateEventsProduceOneObservation',
                'storeRejectsWritesWithoutTransaction','realRabbitDuplicateIsObservedAndAcknowledgedAfterCommit',
                'malformedRealMessageIsDeadLetteredWithoutObservation','transientDatabaseFailureRetriesAndCommitsRealSql',
                'exhaustedTransientFailuresReachRealDlqWithoutPartialState')
        }
    )
    foreach ($entry in $suites) {
        $shortName = ($entry.suite -split '\.')[-1]
        $report.phase = $shortName
        $startedAt = [datetime]::UtcNow
        & $mavenCommand -f (Join-Path $entry.module 'pom.xml') test "-Dtest=$shortName" '-DskipTests=false' '-Dmaven.test.skip=false' *> (Join-Path $outputDirectory "$shortName.log")
        $mavenExit = $LASTEXITCODE
        $suiteFile = Join-Path (Join-Path $projectRoot $entry.module) "target/surefire-reports/TEST-$($entry.suite).xml"
        $evidence = Assert-TripPublicationSuiteEvidence -Path $suiteFile -StartedAt $startedAt -SuiteName $entry.suite -RequiredCases $entry.cases -MavenExit $mavenExit
        $report.evidence += $evidence
        Copy-Item -LiteralPath $suiteFile -Destination (Join-Path $outputDirectory "$shortName.xml")
    }
    $report.status = 'PASSED'
    $report.message = 'Producer and consumer container suites passed; this is not a live cloud cutover acceptance.'
    $exitCode = 0
} catch {
    if ($report.phase -eq 'preflight') { $report.status='BLOCKED' }
    $report.message = $_.Exception.Message
} finally {
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $outputDirectory 'report.json') -Encoding UTF8
    $env:JAVA_HOME=$previousJavaHome; $env:Path=$previousPath
    Pop-Location
}
Write-Host ("Trip publication shadow: {0}. Report: {1}" -f $report.status, (Join-Path $outputDirectory 'report.json'))
if ($exitCode -ne 0) { Write-Warning $report.message }
exit $exitCode

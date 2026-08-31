#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'trip-publication-acceptance.helpers.ps1')
$fixturePath = [System.IO.Path]::GetTempFileName()
$startedAt = [datetime]::UtcNow.AddSeconds(-5)
function Write-Fixture([string] $Attributes, [string] $CaseXml='<testcase name="required"/>') {
    "<testsuite name=`"example.Suite`" tests=`"1`" $Attributes>$CaseXml</testsuite>" | Set-Content -LiteralPath $fixturePath -Encoding UTF8
}
function Check-Fixture([datetime] $Start=$startedAt, [int] $Exit=0) {
    Assert-TripPublicationSuiteEvidence -Path $fixturePath -StartedAt $Start -SuiteName 'example.Suite' -RequiredCases @('required') -MavenExit $Exit
}
function Expect-Failure([scriptblock] $Action) {
    $failed=$false
    try { & $Action > $null } catch { $failed=$true }
    if (-not $failed) { throw 'Invalid evidence was accepted.' }
}
try {
    Write-Fixture 'skipped="0" failures="0" errors="0"'
    $result = Check-Fixture
    if ($result.tests -ne 1) { throw 'Valid fixture did not pass.' }
    Expect-Failure { Check-Fixture -Start ([datetime]::UtcNow.AddMinutes(1)) }
    Expect-Failure { Check-Fixture -Exit 1 }
    foreach ($attributes in @('skipped="1" failures="0" errors="0"','skipped="0" failures="1" errors="0"','skipped="0" failures="0" errors="1"')) {
        Write-Fixture $attributes
        Expect-Failure { Check-Fixture }
    }
    foreach ($caseXml in @('<testcase name="wrong"/>','<testcase name="required"><skipped/></testcase>',
            '<testcase name="required"/><testcase name="required"/>')) {
        Write-Fixture 'skipped="0" failures="0" errors="0"' $caseXml
        Expect-Failure { Check-Fixture }
    }
    Write-Host 'Trip publication evidence tests PASSED (valid / stale / exit / skip / error / missing / duplicate).'
} finally {
    # Exact file returned by GetTempFileName; never recurse or touch project/runtime data.
    Remove-Item -LiteralPath $fixturePath
}

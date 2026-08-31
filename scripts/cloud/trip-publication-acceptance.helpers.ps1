Set-StrictMode -Version Latest

function Assert-TripPublicationSuiteEvidence {
    param([string] $Path, [datetime] $StartedAt, [string] $SuiteName, [string[]] $RequiredCases, [int] $MavenExit)
    if ($MavenExit -ne 0) { throw "Maven failed for $SuiteName (exit $MavenExit)." }
    if (-not (Test-Path -LiteralPath $Path) -or (Get-Item -LiteralPath $Path).LastWriteTimeUtc -lt $StartedAt.ToUniversalTime()) {
        throw "No fresh Surefire evidence for $SuiteName."
    }
    [xml] $xml = Get-Content -LiteralPath $Path -Raw
    $suite = $xml.testsuite
    if ($suite.name -ne $SuiteName) { throw 'Unexpected test suite.' }
    if ([int]$suite.tests -lt $RequiredCases.Count -or [int]$suite.skipped -ne 0 -or [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0) {
        throw 'Acceptance requires nonempty evidence with zero skips/failures/errors.'
    }
    foreach ($name in $RequiredCases) {
        $cases = @($suite.testcase | Where-Object { $_.name -eq $name })
        if ($cases.Count -ne 1 -or $cases[0].SelectNodes('skipped|failure|error').Count -ne 0) {
            throw "Missing, duplicated or unsuccessful required case: $name"
        }
    }
    return [ordered]@{ suite = $SuiteName; tests = [int]$suite.tests; failures = 0; errors = 0; skipped = 0 }
}

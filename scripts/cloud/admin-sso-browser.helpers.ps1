#Requires -Version 5.1
Set-StrictMode -Version Latest

function New-AdminSsoBrowserReport {
    param([Parameter(Mandatory)][string] $RunId)

    return [ordered]@{
        runId = $RunId
        status = 'BLOCKED'
        failureCategory = 'environment_blocked'
        failedInPhase = $null
        startedAt = (Get-Date).ToUniversalTime().ToString('o')
        completedAt = $null
        environmentBlocked = $false
        browserTestExecuted = $false
        iamHealthy = $false
        gatewayHealthy = $false
        studentFrontendHealthy = $false
        adminFrontendHealthy = $false
        evidence = [ordered]@{
            studentClientAuthorized = $false
            adminClientAuthorized = $false
            loginPromptBypassedForSecondClient = $false
            accessTokensDistinct = $false
            subjectsMatch = $false
            adminRolePresent = $false
            iamSessionEndedByRpLogout = $false
            freshAuthorizationRequiresLogin = $false
            existingStudentTokenStillPresent = $false
        }
        notes = @()
    }
}

function Test-AdminSsoEnvironmentPhase {
    param([string] $Phase)
    return $Phase -in @(
        'Assert-Credentials',
        'Assert-Node',
        'Assert-Chrome',
        'Assert-IAM',
        'Assert-Gateway',
        'Start-Student-Frontend',
        'Start-Admin-Frontend'
    )
}

function Resolve-AdminSsoBrowserStatus {
    param([Parameter(Mandatory)][System.Collections.IDictionary] $Report)

    if ($Report.environmentBlocked) {
        return [ordered]@{
            status = 'BLOCKED'
            failureCategory = 'environment_blocked'
        }
    }
    if (-not $Report.browserTestExecuted) {
        return [ordered]@{
            status = 'PARTIAL'
            failureCategory = 'verification_not_executed'
        }
    }

    $requiredEvidence = @(
        'studentClientAuthorized',
        'adminClientAuthorized',
        'loginPromptBypassedForSecondClient',
        'accessTokensDistinct',
        'subjectsMatch',
        'adminRolePresent',
        'iamSessionEndedByRpLogout',
        'freshAuthorizationRequiresLogin',
        'existingStudentTokenStillPresent'
    )
    $passed = $Report.iamHealthy `
        -and $Report.gatewayHealthy `
        -and $Report.studentFrontendHealthy `
        -and $Report.adminFrontendHealthy
    foreach ($field in $requiredEvidence) {
        $passed = $passed -and ($Report.evidence[$field] -eq $true)
    }

    if ($passed) {
        return [ordered]@{
            status = 'PASSED'
            failureCategory = 'verification_succeeded'
        }
    }
    return [ordered]@{
        status = 'FAILED'
        failureCategory = 'business_failure'
    }
}

function Test-AdminSsoHttpEndpoint {
    param(
        [Parameter(Mandatory)][string] $Uri,
        [int] $TimeoutSec = 4
    )
    try {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing `
            -TimeoutSec $TimeoutSec
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
    } catch {
        return $false
    }
}

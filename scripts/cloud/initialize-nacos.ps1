param(
    [string]$NacosBaseUrl = 'http://localhost:8848',
    [string]$AdminPassword = 'nacos'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$configDirectory = Join-Path $projectRoot 'cloud\nacos-config'

function Invoke-NacosLogin {
    Invoke-RestMethod `
        -Method Post `
        -Uri "$NacosBaseUrl/nacos/v3/auth/user/login" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ username = 'nacos'; password = $AdminPassword }
}

try {
    $login = Invoke-NacosLogin
} catch {
    Write-Host 'Nacos administrator is not initialized; creating the local development administrator.'
    $initialization = Invoke-RestMethod `
        -Method Post `
        -Uri "$NacosBaseUrl/nacos/v3/auth/user/admin" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ password = $AdminPassword }

    if ($initialization.code -ne 0) {
        throw "Nacos administrator initialization failed: $($initialization.message)"
    }
    $login = Invoke-NacosLogin
}

$accessToken = $login.accessToken
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw 'Nacos login did not return an access token.'
}

foreach ($dataId in @(
    'school-bus-core.yml',
    'school-bus-gateway.yml',
    'school-bus-transport-query.yml',
    'school-bus-iam.yml'
)) {
    $content = Get-Content -Raw -LiteralPath (Join-Path $configDirectory $dataId)
    $publishUri = "$NacosBaseUrl/nacos/v3/admin/cs/config" +
        "?dataId=$([uri]::EscapeDataString($dataId))" +
        '&groupName=DEFAULT_GROUP' +
        "&content=$([uri]::EscapeDataString($content))"

    $publishResult = Invoke-RestMethod `
        -Method Post `
        -Uri $publishUri `
        -Headers @{ accessToken = $accessToken }

    if ($publishResult.code -ne 0) {
        throw "Publishing $dataId failed: $($publishResult.message)"
    }
    Write-Host "Published DEFAULT_GROUP:$dataId"
}

Write-Host 'Nacos administrator login and project configuration publication succeeded.'

[CmdletBinding()]
param(
    [string]$MySqlContainer = 'school-bus-mysql',
    [string]$RootPassword = 'root',
    [string]$CanalUsername = 'canal',
    [string]$CanalPassword = 'canal'
)

$ErrorActionPreference = 'Stop'

if ($CanalUsername -notmatch '^[A-Za-z0-9_]+$') {
    throw 'CanalUsername may contain only letters, numbers, and underscores.'
}
if ($CanalPassword.Contains("'")) {
    throw "CanalPassword must not contain a single quote."
}

$running = docker inspect `
    --format '{{.State.Running}}' `
    $MySqlContainer 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
    throw "MySQL container '$MySqlContainer' is not running."
}

$sql = @"
CREATE USER IF NOT EXISTS '$CanalUsername'@'%'
IDENTIFIED WITH mysql_native_password BY '$CanalPassword';
ALTER USER '$CanalUsername'@'%'
IDENTIFIED WITH mysql_native_password BY '$CanalPassword';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT, SHOW VIEW
ON *.* TO '$CanalUsername'@'%';
FLUSH PRIVILEGES;
"@

docker exec $MySqlContainer `
    mysql --user=root "--password=$RootPassword" `
    --execute $sql
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to provision the MySQL Canal replication account.'
}

$variables = docker exec $MySqlContainer `
    mysql --user=root "--password=$RootPassword" `
    --batch --skip-column-names `
    --execute "SELECT @@log_bin, @@binlog_format, @@binlog_row_image, @@server_id;"
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to inspect MySQL Binlog settings.'
}

$parts = $variables -split "`t"
if ($parts.Count -ne 4 `
        -or $parts[0] -ne '1' `
        -or $parts[1] -ne 'ROW' `
        -or $parts[2] -ne 'FULL' `
        -or [long]$parts[3] -le 0) {
    throw "Unsupported Binlog settings: $variables"
}

Write-Host 'Canal MySQL account and Binlog prerequisites are ready.'
Write-Host "log_bin=$($parts[0]), format=$($parts[1]), row_image=$($parts[2]), server_id=$($parts[3])"

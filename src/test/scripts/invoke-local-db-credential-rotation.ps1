[CmdletBinding()]
param(
    [switch]$Execute,
    [string]$DatabaseHost = 'localhost',
    [int]$DatabasePort = 5432,
    [string[]]$Databases = @('wms_db', 'wms_db_test'),
    [string]$PsqlPath = 'D:\ITsoftware\PostgreSQL\bin\psql.exe',
    [string]$RecoveryDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if ([string]::IsNullOrWhiteSpace($RecoveryDirectory)) {
    $RecoveryDirectory = Join-Path $repositoryRoot 'backups\security'
}

function Get-ConfiguredValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $userValue = [Environment]::GetEnvironmentVariable($Name, 'User')
    if (-not [string]::IsNullOrEmpty($userValue)) {
        return $userValue
    }
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function New-StrongPassword {
    $bytes = New-Object byte[] 36
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    }
    finally {
        $random.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Protect-RecoveryBundle {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Bundle,
        [Parameter(Mandatory = $true)][string]$Path
    )

    Add-Type -AssemblyName System.Security
    $json = $Bundle | ConvertTo-Json -Compress
    $plainBytes = [Text.Encoding]::UTF8.GetBytes($json)
    $protectedBytes = $null
    try {
        $protectedBytes = [Security.Cryptography.ProtectedData]::Protect(
            $plainBytes,
            $null,
            [Security.Cryptography.DataProtectionScope]::CurrentUser
        )
        [IO.File]::WriteAllBytes($Path, $protectedBytes)
    }
    finally {
        [Array]::Clear($plainBytes, 0, $plainBytes.Length)
        if ($null -ne $protectedBytes) {
            [Array]::Clear($protectedBytes, 0, $protectedBytes.Length)
        }
        $json = $null
    }
}

function Invoke-PsqlCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Password,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string[]]$RedactValues = @(),
        [switch]$AllowFailure
    )

    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        $ErrorActionPreference = 'Continue'
        $output = $Sql | & $PsqlPath -X -q -A -t -v ON_ERROR_STOP=1 `
            -h $DatabaseHost -p $DatabasePort -U $Username -d $Database 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }

    $safeOutput = (@($output) -join [Environment]::NewLine)
    foreach ($redactValue in $RedactValues) {
        if (-not [string]::IsNullOrEmpty($redactValue)) {
            $safeOutput = $safeOutput.Replace($redactValue, '<REDACTED>')
        }
    }

    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "PostgreSQL command failed for database '$Database'. $safeOutput"
    }
    return [pscustomobject]@{
        succeeded = $exitCode -eq 0
        output = $safeOutput.Trim()
    }
}

foreach ($database in $Databases) {
    if ($database -notmatch '^[A-Za-z0-9_]+$') {
        throw "Unsafe database identifier: $database"
    }
}
if (-not (Test-Path -LiteralPath $PsqlPath -PathType Leaf)) {
    throw "psql was not found at: $PsqlPath"
}

$currentPassword = Get-ConfiguredValue -Name 'DB_PASSWORD'
$currentUsername = Get-ConfiguredValue -Name 'DB_USERNAME'
if ([string]::IsNullOrEmpty($currentUsername)) {
    $currentUsername = 'postgres'
}

$preflight = [ordered]@{
    mode = if ($Execute) { 'EXECUTE' } else { 'CHECK' }
    databaseHost = $DatabaseHost
    databasePort = $DatabasePort
    databases = $Databases
    psqlPresent = $true
    currentPasswordPresent = -not [string]::IsNullOrEmpty($currentPassword)
    currentUsername = $currentUsername
    recoveryDirectory = $RecoveryDirectory
}

if (-not $Execute) {
    [pscustomobject]$preflight | ConvertTo-Json -Depth 4
    exit 0
}
if ([string]::IsNullOrEmpty($currentPassword)) {
    throw 'DB_PASSWORD is required to authenticate the pre-rotation postgres connection.'
}
if ($currentUsername -cne 'postgres') {
    throw 'The controlled rotation must start with the postgres administrator account.'
}

$newPostgresPassword = New-StrongPassword
$newApplicationPassword = New-StrongPassword
$timestamp = Get-Date -Format 'yyyyMMddTHHmmss'
New-Item -ItemType Directory -Path $RecoveryDirectory -Force | Out-Null
$recoveryPath = Join-Path $RecoveryDirectory "local-db-credentials-$timestamp.dpapi"
$receiptPath = Join-Path $RecoveryDirectory "local-db-credentials-$timestamp.receipt.json"

$recoveryBundle = @{
    createdAt = (Get-Date).ToString('o')
    databaseHost = $DatabaseHost
    databasePort = $DatabasePort
    databases = $Databases
    postgresPassword = $newPostgresPassword
    wmsAppPassword = $newApplicationPassword
}
Protect-RecoveryBundle -Bundle $recoveryBundle -Path $recoveryPath

$redactions = @($currentPassword, $newPostgresPassword, $newApplicationPassword)
$roleSql = $null
try {
    $databaseList = ($Databases | ForEach-Object { "'$_'" }) -join ', '
    $availability = Invoke-PsqlCommand -Password $currentPassword -Username 'postgres' `
        -Database 'postgres' -Sql "SELECT datname FROM pg_database WHERE datname IN ($databaseList) ORDER BY datname;" `
        -RedactValues $redactions
    $availableDatabases = @(([string]$availability.output -split '\r?\n') |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    foreach ($database in $Databases) {
        if ($availableDatabases -notcontains $database) {
            throw "Required database is missing: $database"
        }
    }

    $roleSql = @'
BEGIN;
DO $rotation$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'wms_app') THEN
        CREATE ROLE wms_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
$rotation$;
ALTER ROLE wms_app WITH LOGIN PASSWORD '__WMS_APP_PASSWORD__'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
ALTER ROLE postgres WITH PASSWORD '__POSTGRES_PASSWORD__';
COMMIT;
'@
    $roleSql = $roleSql.Replace('__WMS_APP_PASSWORD__', $newApplicationPassword)
    $roleSql = $roleSql.Replace('__POSTGRES_PASSWORD__', $newPostgresPassword)
    Invoke-PsqlCommand -Password $currentPassword -Username 'postgres' -Database 'postgres' `
        -Sql $roleSql -RedactValues $redactions | Out-Null

    foreach ($database in $Databases) {
        Invoke-PsqlCommand -Password $newPostgresPassword -Username 'postgres' -Database 'postgres' `
            -Sql "ALTER DATABASE $database OWNER TO wms_app;" -RedactValues $redactions | Out-Null

        $ownershipSql = @'
ALTER SCHEMA public OWNER TO wms_app;
DO $ownership$
DECLARE item RECORD;
BEGIN
    FOR item IN SELECT tablename AS object_name FROM pg_tables WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO wms_app', item.object_name);
    END LOOP;
    FOR item IN SELECT sequencename AS object_name FROM pg_sequences WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO wms_app', item.object_name);
    END LOOP;
    FOR item IN SELECT viewname AS object_name FROM pg_views WHERE schemaname = 'public' LOOP
        EXECUTE format('ALTER VIEW public.%I OWNER TO wms_app', item.object_name);
    END LOOP;
END
$ownership$;
'@
        Invoke-PsqlCommand -Password $newPostgresPassword -Username 'postgres' -Database $database `
            -Sql $ownershipSql -RedactValues $redactions | Out-Null
    }

    $newAdminCheck = Invoke-PsqlCommand -Password $newPostgresPassword -Username 'postgres' `
        -Database 'postgres' -Sql 'SELECT 1;' -RedactValues $redactions
    if (-not $newAdminCheck.succeeded) {
        throw 'The rotated postgres credential could not reconnect.'
    }

    $oldAdminCheck = Invoke-PsqlCommand -Password $currentPassword -Username 'postgres' `
        -Database 'postgres' -Sql 'SELECT 1;' -RedactValues $redactions -AllowFailure
    if ($oldAdminCheck.succeeded) {
        throw 'The historical postgres credential still authenticates after rotation.'
    }

    $databaseChecks = @()
    foreach ($database in $Databases) {
        $checkSql = @'
SELECT current_user || '|' || rolsuper || '|' || rolcreatedb || '|' || rolcreaterole || '|' || rolreplication
FROM pg_roles WHERE rolname = current_user;
'@
        $applicationCheck = Invoke-PsqlCommand -Password $newApplicationPassword -Username 'wms_app' `
            -Database $database -Sql $checkSql -RedactValues $redactions
        $expected = 'wms_app|false|false|false|false'
        if ($applicationCheck.output -notmatch "(?m)^$([regex]::Escape($expected))$") {
            throw "Least-privilege verification failed for database '$database'."
        }
        $databaseChecks += [pscustomobject]@{
            database = $database
            wmsAppConnection = 'PASS'
            superuser = $false
            createDatabase = $false
            createRole = $false
            replication = $false
        }
    }

    [Environment]::SetEnvironmentVariable('DB_USERNAME', 'wms_app', 'User')
    [Environment]::SetEnvironmentVariable('DB_PASSWORD', $newApplicationPassword, 'User')

    $receipt = [ordered]@{
        completedAt = (Get-Date).ToString('o')
        databaseHost = $DatabaseHost
        databasePort = $DatabasePort
        databases = $databaseChecks
        postgresCredentialRotated = $true
        historicalPostgresCredentialRejected = $true
        userEnvironmentUpdated = $true
        dbUsername = 'wms_app'
        recoveryFile = $recoveryPath
    }
    [IO.File]::WriteAllText(
        $receiptPath,
        ($receipt | ConvertTo-Json -Depth 5),
        (New-Object Text.UTF8Encoding($false))
    )
    $receipt | ConvertTo-Json -Depth 5
}
catch {
    throw "Credential rotation did not complete. The encrypted recovery package is: $recoveryPath. $($_.Exception.Message)"
}
finally {
    $roleSql = $null
    $newPostgresPassword = $null
    $newApplicationPassword = $null
    $currentPassword = $null
    $recoveryBundle = $null
}

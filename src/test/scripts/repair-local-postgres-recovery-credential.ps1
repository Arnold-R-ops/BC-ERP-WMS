[CmdletBinding()]
param(
    [switch]$Execute
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$serviceName = 'postgresql-x64-18'
$databaseHost = '127.0.0.1'
$databasePort = 5432
$databases = @('wms_db', 'wms_db_test')
$postgresBinDirectory = 'D:\ITsoftware\PostgreSQL\bin'
$dataDirectory = 'D:\ITsoftware\PostgreSQL\data'
$recoveryDirectory = Join-Path $repositoryRoot 'backups\security'
$backupRoot = Join-Path $repositoryRoot 'backups'
$psqlPath = Join-Path $postgresBinDirectory 'psql.exe'
$postgresPath = Join-Path $postgresBinDirectory 'postgres.exe'
$pgControlDataPath = Join-Path $postgresBinDirectory 'pg_controldata.exe'
$workerSourcePath = Join-Path $PSScriptRoot 'invoke-local-postgres-offline-password-worker.ps1'
$configurationFiles = @('pg_hba.conf', 'pg_ident.conf', 'postgresql.conf', 'postgresql.auto.conf')

$protectedBytes = $null
$plainBytes = $null
$historicalBundle = $null
$historicalPostgresPassword = $null
$wmsAppPassword = $null
$newPostgresPassword = $null
$pendingBundle = $null
$pendingPath = $null
$finalRecoveryPath = $null
$receiptPath = $null
$backupPath = $null
$workerDirectory = $null
$workerTaskName = $null
$serviceWasStopped = $false
$operationSucceeded = $false

function Test-IsElevated {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-ConfiguredValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $userValue = [Environment]::GetEnvironmentVariable($Name, 'User')
    if (-not [string]::IsNullOrEmpty($userValue)) {
        return $userValue
    }
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Test-SecretEqual {
    param(
        [Parameter(Mandatory = $true)][string]$Left,
        [Parameter(Mandatory = $true)][string]$Right
    )

    $leftBytes = [Text.Encoding]::UTF8.GetBytes($Left)
    $rightBytes = [Text.Encoding]::UTF8.GetBytes($Right)
    try {
        if ($leftBytes.Length -ne $rightBytes.Length) {
            return $false
        }
        $difference = 0
        for ($index = 0; $index -lt $leftBytes.Length; $index++) {
            $difference = $difference -bor ($leftBytes[$index] -bxor $rightBytes[$index])
        }
        return $difference -eq 0
    }
    finally {
        [Array]::Clear($leftBytes, 0, $leftBytes.Length)
        [Array]::Clear($rightBytes, 0, $rightBytes.Length)
    }
}

function New-StrongPassword {
    $bytes = New-Object byte[] 36
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    }
    finally {
        $random.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Protect-Bundle {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Bundle,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]
        [Security.Cryptography.DataProtectionScope]$Scope
    )

    Add-Type -AssemblyName System.Security
    $json = $Bundle | ConvertTo-Json -Compress
    $plain = [Text.Encoding]::UTF8.GetBytes($json)
    $protected = $null
    $stream = $null
    try {
        $protected = [Security.Cryptography.ProtectedData]::Protect($plain, $null, $Scope)
        $stream = New-Object IO.FileStream(
            $Path,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        $stream.Write($protected, 0, $protected.Length)
        $stream.Flush($true)
    }
    finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
        [Array]::Clear($plain, 0, $plain.Length)
        if ($null -ne $protected) {
            [Array]::Clear($protected, 0, $protected.Length)
        }
        $json = $null
    }
}

function Read-CurrentUserBundle {
    param([Parameter(Mandatory = $true)][string]$Path)

    Add-Type -AssemblyName System.Security
    $encrypted = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $Path))
    $decrypted = $null
    try {
        $decrypted = [Security.Cryptography.ProtectedData]::Unprotect(
            $encrypted,
            $null,
            [Security.Cryptography.DataProtectionScope]::CurrentUser
        )
        return ([Text.Encoding]::UTF8.GetString($decrypted) | ConvertFrom-Json)
    }
    finally {
        [Array]::Clear($encrypted, 0, $encrypted.Length)
        if ($null -ne $decrypted) {
            [Array]::Clear($decrypted, 0, $decrypted.Length)
        }
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
    $previousTimeout = [Environment]::GetEnvironmentVariable('PGCONNECT_TIMEOUT', 'Process')
    $previousPreference = $ErrorActionPreference
    try {
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $Password, 'Process')
        [Environment]::SetEnvironmentVariable('PGCONNECT_TIMEOUT', '5', 'Process')
        $ErrorActionPreference = 'Continue'
        $output = $Sql | & $psqlPath -X -w -q -A -t -v ON_ERROR_STOP=1 `
            -h $databaseHost -p $databasePort -U $Username -d $Database 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
        [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
        [Environment]::SetEnvironmentVariable('PGCONNECT_TIMEOUT', $previousTimeout, 'Process')
    }

    $safeOutput = (@($output) -join [Environment]::NewLine)
    foreach ($value in @($Password) + $RedactValues) {
        if (-not [string]::IsNullOrEmpty($value)) {
            $safeOutput = $safeOutput.Replace($value, '<REDACTED>')
        }
    }
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "PostgreSQL command failed for '$Username' on '$Database'. $($safeOutput.Trim())"
    }
    return [pscustomobject]@{
        succeeded = $exitCode -eq 0
        output = if ($exitCode -eq 0) { $safeOutput.Trim() } else { '' }
    }
}

function Get-DatabaseSnapshot {
    param(
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Password
    )

    $sql = @'
SELECT 'role|' || rolname || '|' || rolsuper || '|' || rolcreatedb || '|' || rolcreaterole || '|' || rolreplication || '|' || rolbypassrls
FROM pg_roles WHERE rolname = current_user;
SELECT 'database_owner|' || pg_get_userbyid(datdba)
FROM pg_database WHERE datname = current_database();
SELECT 'public_schema_owner|' || pg_get_userbyid(nspowner)
FROM pg_namespace WHERE nspname = 'public';
SELECT 'public_relations|' || count(*) || '|' || count(*) FILTER (WHERE object_owner <> 'wms_app') || '|' ||
       md5(COALESCE(string_agg(relkind || ':' || object_name || ':' || object_owner, '|' ORDER BY relkind, object_name), ''))
FROM (
    SELECT c.relkind::text AS relkind, c.relname AS object_name, pg_get_userbyid(c.relowner) AS object_owner
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
) objects;
'@
    $result = Invoke-PsqlCommand -Password $Password -Username 'wms_app' -Database $Database `
        -Sql $sql -RedactValues @($historicalPostgresPassword, $newPostgresPassword)
    $lines = @(([string]$result.output -split '\r?\n') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $expectedRole = 'role|wms_app|false|false|false|false|false'
    if ($lines -notcontains $expectedRole) {
        throw "Least-privilege role verification failed for '$Database'."
    }
    if ($lines -notcontains 'database_owner|wms_app') {
        throw "Database owner verification failed for '$Database'."
    }
    if ($lines -notcontains 'public_schema_owner|wms_app') {
        throw "Public schema owner verification failed for '$Database'."
    }
    $ownerLine = @($lines | Where-Object { $_ -like 'public_relations|*' })
    if ($ownerLine.Count -ne 1 -or ($ownerLine[0] -split '\|')[2] -cne '0') {
        throw "Public object owner verification failed for '$Database'."
    }

    $flywayPresence = Invoke-PsqlCommand -Password $Password -Username 'wms_app' -Database $Database `
        -Sql "SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'ABSENT' ELSE 'PRESENT' END;" `
        -RedactValues @($historicalPostgresPassword, $newPostgresPassword)
    if ($flywayPresence.output -ceq 'PRESENT') {
        $flywaySql = @'
SELECT 'flyway|' || count(*) || '|' || count(*) FILTER (WHERE success) || '|' || count(*) FILTER (WHERE NOT success) || '|' ||
       COALESCE((array_agg(version ORDER BY installed_rank DESC))[1], '') || '|' ||
       md5(COALESCE(string_agg(
           concat_ws(chr(31), installed_rank::text, COALESCE(version, ''), description, type, script,
                     COALESCE(checksum::text, ''), installed_by, installed_on::text, execution_time::text, success::text),
           chr(30) ORDER BY installed_rank
       ), ''))
FROM flyway_schema_history;
'@
        $flywayResult = Invoke-PsqlCommand -Password $Password -Username 'wms_app' -Database $Database `
            -Sql $flywaySql -RedactValues @($historicalPostgresPassword, $newPostgresPassword)
        $lines += [string]$flywayResult.output
        $flywayParts = ([string]$flywayResult.output) -split '\|'
        if ($flywayParts.Count -ne 6 -or $flywayParts[2] -cne $flywayParts[1] -or
            $flywayParts[3] -cne '0' -or $flywayParts[4] -cne '4.72') {
            throw "Flyway baseline verification failed for '$Database'."
        }
    }
    else {
        $lines += 'flyway|ABSENT'
        if ($Database -ceq 'wms_db') {
            throw "Flyway history is unexpectedly absent from '$Database'."
        }
    }
    return [pscustomobject]@{
        database = $Database
        lines = $lines
    }
}

function Get-PostgresRoleSnapshot {
    param([Parameter(Mandatory = $true)][string]$Password)

    $sql = @'
SELECT rolname || '|' || rolsuper || '|' || rolinherit || '|' || rolcreaterole || '|' || rolcreatedb || '|' ||
       rolcanlogin || '|' || rolreplication || '|' || rolbypassrls || '|' || rolconnlimit || '|' ||
       COALESCE(rolvaliduntil::text, '')
FROM pg_roles WHERE rolname = 'postgres';
'@
    $result = Invoke-PsqlCommand -Password $Password -Username 'wms_app' -Database 'wms_db' `
        -Sql $sql -RedactValues @($historicalPostgresPassword, $newPostgresPassword)
    $snapshot = [string]$result.output
    $parts = $snapshot -split '\|'
    if ($parts.Count -ne 10 -or $parts[0] -cne 'postgres' -or
        $parts[1] -cne 'true' -or $parts[5] -cne 'true') {
        throw 'The postgres role is missing or is not an enabled superuser.'
    }
    return $snapshot
}

function Get-ClusterSnapshot {
    param([Parameter(Mandatory = $true)][string]$Password)

    $sql = @'
SELECT 'server_version|' || current_setting('server_version');
SELECT 'password_encryption|' || current_setting('password_encryption');
SELECT 'port|' || inet_server_port();
SELECT 'other_client_connections|' || count(*)
FROM pg_stat_activity WHERE backend_type = 'client backend' AND pid <> pg_backend_pid();
SELECT 'postgres_database|' || count(*)
FROM pg_database WHERE datname = 'postgres' AND datallowconn;
SELECT 'external_tablespace|' || spcname
FROM pg_tablespace WHERE spcname NOT IN ('pg_default', 'pg_global') ORDER BY spcname;
'@
    $result = Invoke-PsqlCommand -Password $Password -Username 'wms_app' -Database 'wms_db' `
        -Sql $sql -RedactValues @($historicalPostgresPassword, $newPostgresPassword)
    $lines = @(([string]$result.output -split '\r?\n') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lines -notcontains 'password_encryption|scram-sha-256') {
        throw 'PostgreSQL password_encryption is not scram-sha-256.'
    }
    if ($lines -notcontains "port|$databasePort") {
        throw 'PostgreSQL is not serving the expected port.'
    }
    if ($lines -notcontains 'other_client_connections|0') {
        throw 'Other PostgreSQL client connections are active; maintenance cannot start safely.'
    }
    if ($lines -notcontains 'postgres_database|1') {
        throw 'The postgres maintenance database is missing or does not allow connections.'
    }
    if (@($lines | Where-Object { $_ -like 'external_tablespace|*' }).Count -ne 0) {
        throw 'External PostgreSQL tablespaces are present; the local cold-backup procedure is incomplete.'
    }
    if (@(Get-ChildItem -LiteralPath (Join-Path $dataDirectory 'pg_tblspc') -Force).Count -ne 0) {
        throw 'pg_tblspc is not empty; the local cold-backup procedure is incomplete.'
    }
    return $lines
}

function Get-ConfigurationSnapshot {
    $items = @()
    foreach ($name in $configurationFiles) {
        $path = Join-Path $dataDirectory $name
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Required PostgreSQL configuration file is missing: $name"
        }
        $items += [pscustomobject]@{
            name = $name
            sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
        }
    }
    $effectiveHbaRules = @(Get-Content -LiteralPath (Join-Path $dataDirectory 'pg_hba.conf') |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and -not $_.StartsWith('#') })
    if (@($effectiveHbaRules | Where-Object { $_ -match '(^|\s)trust(\s|$)' }).Count -ne 0) {
        throw 'An active trust rule exists in pg_hba.conf.'
    }
    return $items
}

function Get-ServiceSnapshot {
    $service = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
    if ($null -eq $service) {
        throw "PostgreSQL service was not found: $serviceName"
    }
    return [pscustomobject]@{
        name = $service.Name
        state = $service.State
        startMode = $service.StartMode
        startName = $service.StartName
        pathName = $service.PathName
        processId = $service.ProcessId
    }
}

function Get-DirectoryStatistics {
    param([Parameter(Mandatory = $true)][string]$Path)

    $files = @(Get-ChildItem -LiteralPath $Path -Recurse -File -Force)
    $sum = ($files | Measure-Object Length -Sum).Sum
    if ($null -eq $sum) {
        $sum = 0
    }
    return [pscustomobject]@{
        fileCount = $files.Count
        bytes = [long]$sum
    }
}

function Test-GitIgnored {
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    $previousGlobalConfig = [Environment]::GetEnvironmentVariable('GIT_CONFIG_GLOBAL', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('GIT_CONFIG_GLOBAL', 'NUL', 'Process')
        & git -C $repositoryRoot check-ignore -q -- $RelativePath
        if ($LASTEXITCODE -ne 0) {
            throw "Sensitive recovery path is not ignored by Git: $RelativePath"
        }
    }
    finally {
        [Environment]::SetEnvironmentVariable('GIT_CONFIG_GLOBAL', $previousGlobalConfig, 'Process')
    }
}

function Set-WorkerDirectoryAcl {
    param([Parameter(Mandatory = $true)][string]$Path)

    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $rules = @(
        @{ Sid = [Security.Principal.SecurityIdentifier]'S-1-5-32-544'; Rights = 'FullControl' },
        @{ Sid = [Security.Principal.SecurityIdentifier]'S-1-5-18'; Rights = 'FullControl' },
        @{ Sid = [Security.Principal.SecurityIdentifier]'S-1-5-20'; Rights = 'Modify' }
    )
    foreach ($item in $rules) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $item.Sid,
            $item.Rights,
            [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit',
            [Security.AccessControl.PropagationFlags]::None,
            [Security.AccessControl.AccessControlType]::Allow
        )
        $acl.AddAccessRule($rule) | Out-Null
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
}

function New-RestrictedBackupAcl {
    param([Parameter(Mandatory = $true)][bool]$Directory)

    $acl = if ($Directory) {
        New-Object Security.AccessControl.DirectorySecurity
    }
    else {
        New-Object Security.AccessControl.FileSecurity
    }
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = if ($Directory) {
        [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
    }
    else {
        [Security.AccessControl.InheritanceFlags]::None
    }
    $rules = @(
        [Security.Principal.WindowsIdentity]::GetCurrent().User,
        [Security.Principal.SecurityIdentifier]'S-1-5-32-544',
        [Security.Principal.SecurityIdentifier]'S-1-5-18'
    )
    foreach ($sid in $rules) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule(
            $sid,
            'FullControl',
            $inheritance,
            [Security.AccessControl.PropagationFlags]::None,
            [Security.AccessControl.AccessControlType]::Allow
        )
        $acl.AddAccessRule($rule) | Out-Null
    }
    return $acl
}

function Set-RestrictedBackupAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$Recurse
    )

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $backupBoundary = (Resolve-Path -LiteralPath $backupRoot).Path.TrimEnd('\')
    if (-not $resolvedPath.StartsWith($backupBoundary + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to change ACL outside the repository backup root: $resolvedPath"
    }
    Set-Acl -LiteralPath $resolvedPath -AclObject (New-RestrictedBackupAcl -Directory $true)
    if ($Recurse) {
        foreach ($item in Get-ChildItem -LiteralPath $resolvedPath -Recurse -Force) {
            Set-Acl -LiteralPath $item.FullName `
                -AclObject (New-RestrictedBackupAcl -Directory ([bool]$item.PSIsContainer))
        }
    }
}

function Assert-RestrictedBackupAcl {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$RequireProtected
    )

    $allowedSids = @(
        [Security.Principal.WindowsIdentity]::GetCurrent().User.Value,
        'S-1-5-32-544',
        'S-1-5-18'
    )
    $acl = Get-Acl -LiteralPath $Path
    if ($RequireProtected -and -not $acl.AreAccessRulesProtected) {
        throw "Backup ACL inheritance is not protected: $Path"
    }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -eq [Security.AccessControl.AccessControlType]::Allow) {
            $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
            if ($allowedSids -notcontains $sid) {
                throw "Backup path grants access to an unexpected identity: $Path"
            }
        }
    }
}

function Assert-SafeWorkerDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = [IO.Path]::GetFullPath($Path).TrimEnd('\')
    $allowedRoot = [IO.Path]::GetFullPath((Split-Path -Parent $dataDirectory)).TrimEnd('\')
    $leaf = [IO.Path]::GetFileName($fullPath)
    if (-not $fullPath.StartsWith($allowedRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or
        $leaf -notmatch '^\.bcwms-postgres-repair-[a-f0-9]{32}$') {
        throw "Unsafe worker directory: $fullPath"
    }
    return $fullPath
}

function Wait-ServiceState {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('Running', 'Stopped')][string]$State,
        [int]$TimeoutSeconds = 30
    )

    $service = Get-Service -Name $serviceName
    $desiredState = [Enum]::Parse($service.Status.GetType(), $State)
    $service.WaitForStatus(
        $desiredState,
        [TimeSpan]::FromSeconds($TimeoutSeconds)
    )
}

function Invoke-OfflineWorker {
    param(
        [Parameter(Mandatory = $true)][string]$WorkerDirectory,
        [Parameter(Mandatory = $true)][string]$PayloadPath
    )

    $copiedWorker = Join-Path $WorkerDirectory 'offline-worker.ps1'
    $resultPath = Join-Path $WorkerDirectory 'offline-result.json'
    Copy-Item -LiteralPath $workerSourcePath -Destination $copiedWorker

    $scriptArguments = @(
        '-NoLogo'
        '-NoProfile'
        '-NonInteractive'
        '-ExecutionPolicy', 'Bypass'
        '-File', ('"{0}"' -f $copiedWorker)
        '-WorkerPayload', ('"{0}"' -f $PayloadPath)
        '-WorkerResult', ('"{0}"' -f $resultPath)
        '-PostgresExe', ('"{0}"' -f $postgresPath)
        '-DataDirectory', ('"{0}"' -f $dataDirectory)
    ) -join ' '
    $action = New-ScheduledTaskAction `
        -Execute 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' `
        -Argument $scriptArguments
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddHours(1)
    $principal = New-ScheduledTaskPrincipal `
        -UserId 'NT AUTHORITY\NETWORK SERVICE' `
        -LogonType ServiceAccount `
        -RunLevel Highest
    $script:workerTaskName = "BCWMS-PostgresRecoveryRepair-$([Guid]::NewGuid().ToString('N'))"
    Register-ScheduledTask -TaskName $script:workerTaskName -Action $action -Trigger $trigger `
        -Principal $principal -Description 'One-time BCWMS local PostgreSQL offline password repair' | Out-Null
    $taskStartedAt = Get-Date
    Start-ScheduledTask -TaskName $script:workerTaskName

    $deadline = (Get-Date).AddMinutes(2)
    $observedRun = $false
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $resultPath -PathType Leaf) {
            $result = Get-Content -Raw -LiteralPath $resultPath | ConvertFrom-Json
            if (-not [bool]$result.succeeded) {
                throw "Offline NetworkService worker failed: $([string]$result.errorCode)"
            }
            return $result
        }
        $task = Get-ScheduledTask -TaskName $script:workerTaskName
        $taskInfo = Get-ScheduledTaskInfo -TaskName $script:workerTaskName
        if ($task.State -eq 'Running' -or $taskInfo.LastRunTime -ge $taskStartedAt.AddSeconds(-1)) {
            $observedRun = $true
        }
        if ($observedRun -and $task.State -ne 'Running') {
            Start-Sleep -Milliseconds 500
            if (-not (Test-Path -LiteralPath $resultPath -PathType Leaf)) {
                throw "Offline NetworkService worker exited without a result. Task result: $($taskInfo.LastTaskResult)"
            }
            continue
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'Offline NetworkService worker timed out.'
}

function Stop-AndRemoveWorkerTask {
    if ([string]::IsNullOrWhiteSpace($script:workerTaskName)) {
        return
    }
    $task = Get-ScheduledTask -TaskName $script:workerTaskName -ErrorAction SilentlyContinue
    if ($null -ne $task) {
        if ($task.State -eq 'Running') {
            Stop-ScheduledTask -TaskName $script:workerTaskName -ErrorAction SilentlyContinue
            $deadline = (Get-Date).AddSeconds(15)
            do {
                Start-Sleep -Milliseconds 250
                $task = Get-ScheduledTask -TaskName $script:workerTaskName -ErrorAction SilentlyContinue
            } while ($null -ne $task -and $task.State -eq 'Running' -and (Get-Date) -lt $deadline)
        }
        Unregister-ScheduledTask -TaskName $script:workerTaskName -Confirm:$false -ErrorAction SilentlyContinue
    }
}

foreach ($path in @($psqlPath, $postgresPath, $pgControlDataPath, $workerSourcePath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required executable or worker is missing: $path"
    }
}
foreach ($database in $databases) {
    if ($database -notmatch '^[A-Za-z0-9_]+$') {
        throw "Unsafe database identifier: $database"
    }
}
if (-not (Test-IsElevated)) {
    throw 'Run this script in an elevated PowerShell session owned by the Windows user that created the DPAPI recovery package.'
}

$resolvedDataDirectory = (Resolve-Path -LiteralPath $dataDirectory).Path
$resolvedBackupRoot = (Resolve-Path -LiteralPath $backupRoot).Path
$resolvedRecoveryDirectory = (Resolve-Path -LiteralPath $recoveryDirectory).Path
if (-not $resolvedRecoveryDirectory.StartsWith($resolvedBackupRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The recovery directory is outside the repository backup root.'
}
Test-GitIgnored -RelativePath 'backups/security/recovery-probe.dpapi'
Test-GitIgnored -RelativePath 'backups/postgres-cluster-recovery-probe/PG_VERSION'

$latestRecovery = Get-ChildItem -LiteralPath $resolvedRecoveryDirectory `
    -Filter 'local-db-credentials-*.dpapi' -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $latestRecovery) {
    throw 'No active local DPAPI recovery package was found.'
}

$historicalBundle = Read-CurrentUserBundle -Path $latestRecovery.FullName
$historicalPostgresPassword = [string]$historicalBundle.postgresPassword
$wmsAppPassword = [string]$historicalBundle.wmsAppPassword
if ([string]::IsNullOrEmpty($historicalPostgresPassword) -or [string]::IsNullOrEmpty($wmsAppPassword)) {
    throw 'The active recovery package does not contain both required credentials.'
}
$configuredUsername = Get-ConfiguredValue -Name 'DB_USERNAME'
$configuredPassword = Get-ConfiguredValue -Name 'DB_PASSWORD'
if ($configuredUsername -cne 'wms_app' -or [string]::IsNullOrEmpty($configuredPassword)) {
    throw 'The Windows user environment is not configured for wms_app.'
}
if (-not (Test-SecretEqual -Left $configuredPassword -Right $wmsAppPassword)) {
    throw 'The Windows user DB_PASSWORD does not match the recoverable wms_app credential.'
}
$configuredPassword = $null

$serviceBefore = Get-ServiceSnapshot
if ($serviceBefore.state -cne 'Running' -or $serviceBefore.startMode -cne 'Auto' -or
    $serviceBefore.startName -ine 'NT AUTHORITY\NetworkService') {
    throw 'The PostgreSQL service is not in the expected Running/Auto/NetworkService state.'
}
if ($serviceBefore.pathName.IndexOf($resolvedDataDirectory, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw 'The PostgreSQL service does not point to the expected data directory.'
}
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $databasePort -ErrorAction Stop)
$listenerProcessIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
if ($listeners.Count -eq 0 -or $listenerProcessIds.Count -ne 1) {
    throw 'Port 5432 is not owned exclusively by the expected PostgreSQL service process.'
}
$listenerProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($listenerProcessIds[0])"
if ($null -eq $listenerProcess -or $listenerProcess.Name -ine 'postgres.exe' -or
    $listenerProcess.ParentProcessId -ne $serviceBefore.processId -or
    $listenerProcess.CommandLine.IndexOf($resolvedDataDirectory, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw 'The port 5432 listener is not the expected postgres.exe child process and data directory.'
}
$clusterBefore = Get-ClusterSnapshot -Password $wmsAppPassword
$postgresRoleBefore = Get-PostgresRoleSnapshot -Password $wmsAppPassword
$configurationBefore = Get-ConfigurationSnapshot
$databaseBefore = @($databases | ForEach-Object { Get-DatabaseSnapshot -Database $_ -Password $wmsAppPassword })
$historicalAdminCheck = Invoke-PsqlCommand -Password $historicalPostgresPassword -Username 'postgres' `
    -Database 'postgres' -Sql 'SELECT 1;' -RedactValues @($wmsAppPassword) -AllowFailure
if ($historicalAdminCheck.succeeded) {
    throw 'The stored postgres credential still authenticates; the stale-credential repair is not applicable.'
}

$preflight = [ordered]@{
    mode = if ($Execute) { 'EXECUTE' } else { 'CHECK' }
    windowsUser = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    service = $serviceBefore
    dataDirectory = $resolvedDataDirectory
    activeRecoveryFile = $latestRecovery.Name
    storedPostgresAuthenticates = $false
    wmsAppAuthenticates = $true
    cluster = $clusterBefore
    postgresRole = $postgresRoleBefore
    databases = $databaseBefore
    configuration = $configurationBefore
    externalTablespaces = 0
}
if (-not $Execute) {
    [pscustomobject]$preflight | ConvertTo-Json -Depth 8
    exit 0
}

$timestamp = Get-Date -Format 'yyyyMMddTHHmmssfff'
$backupPath = Join-Path $resolvedBackupRoot "postgres-cluster-before-postgres-recovery-$timestamp"
$pendingPath = Join-Path $resolvedRecoveryDirectory "local-db-credentials-$timestamp.dpapi.pending"
$finalRecoveryPath = Join-Path $resolvedRecoveryDirectory "local-db-credentials-$timestamp.dpapi"
$receiptPath = Join-Path $resolvedRecoveryDirectory "local-db-credentials-$timestamp.receipt.json"
foreach ($path in @($backupPath, $pendingPath, $finalRecoveryPath, $receiptPath)) {
    if (Test-Path -LiteralPath $path) {
        throw "Refusing to overwrite an existing recovery artifact: $path"
    }
}

$estimatedSourceStatistics = Get-DirectoryStatistics -Path $resolvedDataDirectory
$driveName = [IO.Path]::GetPathRoot($resolvedBackupRoot).TrimEnd('\').TrimEnd(':')
$drive = Get-PSDrive -Name $driveName
$requiredFreeBytes = [long]($estimatedSourceStatistics.bytes + 256MB)
if ($drive.Free -lt $requiredFreeBytes) {
    throw 'Insufficient free space for the PostgreSQL cold backup.'
}

Set-RestrictedBackupAcl -Path $resolvedRecoveryDirectory -Recurse
Assert-RestrictedBackupAcl -Path $resolvedRecoveryDirectory -RequireProtected
$existingClusterBackups = @(Get-ChildItem -LiteralPath $resolvedBackupRoot -Directory |
    Where-Object { $_.Name -match '^postgres-cluster-[A-Za-z0-9._-]+$' })
foreach ($existingBackup in $existingClusterBackups) {
    Set-RestrictedBackupAcl -Path $existingBackup.FullName -Recurse
    Assert-RestrictedBackupAcl -Path $existingBackup.FullName -RequireProtected
}

try {
    $serviceWasStopped = $true
    Stop-Service -Name $serviceName
    Wait-ServiceState -State Stopped -TimeoutSeconds 30
    if (Test-Path -LiteralPath (Join-Path $resolvedDataDirectory 'postmaster.pid')) {
        throw 'PostgreSQL stopped but postmaster.pid is still present.'
    }
    $clusterProcesses = @(Get-CimInstance Win32_Process -Filter "Name='postgres.exe'" |
        Where-Object {
            -not [string]::IsNullOrWhiteSpace($_.CommandLine) -and
            $_.CommandLine.IndexOf($resolvedDataDirectory, [StringComparison]::OrdinalIgnoreCase) -ge 0
        })
    if ($clusterProcesses.Count -ne 0) {
        throw 'A postgres.exe process still references the stopped data directory.'
    }
    $controlOutput = & $pgControlDataPath $resolvedDataDirectory 2>&1
    if ($LASTEXITCODE -ne 0 -or (@($controlOutput) -join "`n") -notmatch 'Database cluster state\s*:\s*shut down') {
        throw 'pg_controldata did not report a cleanly shut down cluster.'
    }

    $sourceStatistics = Get-DirectoryStatistics -Path $resolvedDataDirectory
    New-Item -ItemType Directory -Path $backupPath | Out-Null
    Set-RestrictedBackupAcl -Path $backupPath
    Assert-RestrictedBackupAcl -Path $backupPath -RequireProtected
    & robocopy.exe $resolvedDataDirectory $backupPath /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ /NFL /NDL /NJH /NJS /NP | Out-Null
    $robocopyExitCode = $LASTEXITCODE
    if ($robocopyExitCode -ge 8) {
        throw "PostgreSQL cold backup failed with robocopy exit code $robocopyExitCode."
    }
    foreach ($required in @('PG_VERSION', 'global\pg_control')) {
        if (-not (Test-Path -LiteralPath (Join-Path $backupPath $required) -PathType Leaf)) {
            throw "PostgreSQL cold backup is missing: $required"
        }
    }
    $backupStatistics = Get-DirectoryStatistics -Path $backupPath
    if ($backupStatistics.fileCount -ne $sourceStatistics.fileCount -or
        $backupStatistics.bytes -ne $sourceStatistics.bytes) {
        throw 'PostgreSQL cold backup statistics do not match the stopped source cluster.'
    }
    Assert-RestrictedBackupAcl -Path (Join-Path $backupPath 'global\pg_control')

    $newPostgresPassword = New-StrongPassword
    $pendingBundle = @{
        schemaVersion = 2
        createdAt = (Get-Date).ToString('o')
        databaseHost = $databaseHost
        databasePort = $databasePort
        databases = $databases
        operation = 'postgres-recovery-credential-repair'
        postgresPassword = $newPostgresPassword
        wmsAppPassword = $wmsAppPassword
    }
    Protect-Bundle -Bundle $pendingBundle -Path $pendingPath `
        -Scope ([Security.Cryptography.DataProtectionScope]::CurrentUser)
    $pendingCheck = Read-CurrentUserBundle -Path $pendingPath
    if ([string]$pendingCheck.postgresPassword -cne $newPostgresPassword -or
        [string]$pendingCheck.wmsAppPassword -cne $wmsAppPassword) {
        throw 'The pending CurrentUser DPAPI package failed its in-memory verification.'
    }
    $pendingCheck = $null

    $workerDirectory = Join-Path (Split-Path -Parent $resolvedDataDirectory) `
        ".bcwms-postgres-repair-$([Guid]::NewGuid().ToString('N'))"
    $workerDirectory = Assert-SafeWorkerDirectory -Path $workerDirectory
    New-Item -ItemType Directory -Path $workerDirectory | Out-Null
    Set-WorkerDirectoryAcl -Path $workerDirectory
    $workerPayloadPath = Join-Path $workerDirectory 'offline-payload.dpapi-machine'
    $workerPayload = @{
        createdAt = (Get-Date).ToString('o')
        expiresAt = (Get-Date).AddMinutes(10).ToString('o')
        postgresExe = $postgresPath
        dataDirectory = $resolvedDataDirectory
        postgresPassword = $newPostgresPassword
    }
    Protect-Bundle -Bundle $workerPayload -Path $workerPayloadPath `
        -Scope ([Security.Cryptography.DataProtectionScope]::LocalMachine)
    $workerPayload = $null
    Invoke-OfflineWorker -WorkerDirectory $workerDirectory -PayloadPath $workerPayloadPath | Out-Null

    Start-Service -Name $serviceName
    Wait-ServiceState -State Running -TimeoutSeconds 30
    $serviceWasStopped = $false

    $newAdminCheck = Invoke-PsqlCommand -Password $newPostgresPassword -Username 'postgres' `
        -Database 'postgres' `
        -Sql "SELECT current_user || '|' || rolsuper FROM pg_roles WHERE rolname = current_user; SELECT CASE WHEN rolpassword LIKE 'SCRAM-SHA-256$%' THEN 'true' ELSE 'false' END FROM pg_authid WHERE rolname = 'postgres';" `
        -RedactValues @($historicalPostgresPassword, $wmsAppPassword)
    $newAdminLines = @(([string]$newAdminCheck.output -split '\r?\n') | Where-Object { $_ })
    if ($newAdminLines -notcontains 'postgres|true' -or $newAdminLines -notcontains 'true') {
        throw 'The repaired postgres credential or SCRAM verifier verification failed.'
    }
    $oldAdminCheck = Invoke-PsqlCommand -Password $historicalPostgresPassword -Username 'postgres' `
        -Database 'postgres' -Sql 'SELECT 1;' `
        -RedactValues @($newPostgresPassword, $wmsAppPassword) -AllowFailure
    if ($oldAdminCheck.succeeded) {
        throw 'The superseded postgres credential still authenticates.'
    }

    $clusterAfter = Get-ClusterSnapshot -Password $wmsAppPassword
    $postgresRoleAfter = Get-PostgresRoleSnapshot -Password $wmsAppPassword
    $configurationAfter = Get-ConfigurationSnapshot
    $databaseAfter = @($databases | ForEach-Object { Get-DatabaseSnapshot -Database $_ -Password $wmsAppPassword })
    if (($clusterBefore | ConvertTo-Json -Compress) -cne ($clusterAfter | ConvertTo-Json -Compress)) {
        throw 'PostgreSQL cluster invariants changed during the recovery credential repair.'
    }
    if ($postgresRoleBefore -cne $postgresRoleAfter) {
        throw 'Non-password postgres role attributes changed during the recovery credential repair.'
    }
    if (($configurationBefore | ConvertTo-Json -Compress) -cne ($configurationAfter | ConvertTo-Json -Compress)) {
        throw 'PostgreSQL configuration files changed during the recovery credential repair.'
    }
    if (($databaseBefore | ConvertTo-Json -Depth 6 -Compress) -cne ($databaseAfter | ConvertTo-Json -Depth 6 -Compress)) {
        throw 'Database ownership, object, role, or Flyway invariants changed during the repair.'
    }
    if ((Get-ConfiguredValue -Name 'DB_USERNAME') -cne 'wms_app' -or
        (Get-ConfiguredValue -Name 'DB_PASSWORD') -cne $wmsAppPassword) {
        throw 'The Windows user application database environment changed during the repair.'
    }
    $serviceAfter = Get-ServiceSnapshot
    if ($serviceAfter.state -cne 'Running' -or $serviceAfter.startMode -cne 'Auto' -or
        $serviceAfter.startName -ine 'NT AUTHORITY\NetworkService') {
        throw 'The PostgreSQL service did not return to Running/Auto/NetworkService.'
    }

    [IO.File]::Move($pendingPath, $finalRecoveryPath)
    Assert-RestrictedBackupAcl -Path $finalRecoveryPath
    $activatedCheck = Read-CurrentUserBundle -Path $finalRecoveryPath
    if ([string]$activatedCheck.postgresPassword -cne $newPostgresPassword -or
        [string]$activatedCheck.wmsAppPassword -cne $wmsAppPassword) {
        throw 'The activated CurrentUser DPAPI package failed verification.'
    }
    $activatedCheck = $null

    $receipt = [ordered]@{
        completedAt = (Get-Date).ToString('o')
        operation = 'postgres-recovery-credential-repair'
        status = 'PASS'
        databaseHost = $databaseHost
        databasePort = $databasePort
        postgresCredentialRotated = $true
        newPostgresCredentialAuthenticates = $true
        historicalPostgresCredentialRejected = $true
        postgresVerifier = 'SCRAM-SHA-256'
        wmsAppCredentialChanged = $false
        wmsAppLeastPrivilege = 'PASS'
        applicationEnvironmentChanged = $false
        databaseOwnershipChanged = $false
        flywayHistoryChanged = $false
        configurationChanged = $false
        recoveryArtifactAcl = 'CURRENT_USER_ADMINS_SYSTEM_ONLY'
        service = $serviceAfter
        backup = [ordered]@{
            path = $backupPath
            fileCount = $backupStatistics.fileCount
            bytes = $backupStatistics.bytes
            cleanShutdown = $true
            externalTablespaces = 0
            acl = 'CURRENT_USER_ADMINS_SYSTEM_ONLY'
        }
        recoveryFile = $finalRecoveryPath
        supersedes = $latestRecovery.FullName
    }
    [IO.File]::WriteAllText(
        $receiptPath,
        ($receipt | ConvertTo-Json -Depth 7),
        (New-Object Text.UTF8Encoding($false))
    )
    Assert-RestrictedBackupAcl -Path $receiptPath
    $operationSucceeded = $true
    $receipt | ConvertTo-Json -Depth 7
}
catch {
    $safeError = [string]$_.Exception.Message
    foreach ($secret in @($historicalPostgresPassword, $wmsAppPassword, $newPostgresPassword)) {
        if (-not [string]::IsNullOrEmpty($secret)) {
            $safeError = $safeError.Replace($secret, '<REDACTED>')
        }
    }
    $failurePath = Join-Path $resolvedRecoveryDirectory "local-db-credential-repair-$timestamp.failed.receipt.json"
    $failureReceipt = [ordered]@{
        completedAt = (Get-Date).ToString('o')
        operation = 'postgres-recovery-credential-repair'
        status = 'FAILED'
        error = $safeError
        backup = $backupPath
        pendingRecoveryFile = $pendingPath
        automaticColdBackupRestoreAttempted = $false
    }
    [IO.File]::WriteAllText(
        $failurePath,
        ($failureReceipt | ConvertTo-Json -Depth 5),
        (New-Object Text.UTF8Encoding($false))
    )
    throw "Local postgres recovery credential repair failed. $safeError"
}
finally {
    try {
        Stop-AndRemoveWorkerTask
    }
    catch {
        Write-Warning "Scheduled task cleanup requires manual verification: $($_.Exception.Message)"
    }
    if ($serviceWasStopped) {
        try {
            if ((Get-Service -Name $serviceName).Status.ToString() -cne 'Running') {
                Start-Service -Name $serviceName
            }
            Wait-ServiceState -State Running -TimeoutSeconds 30
            $serviceWasStopped = $false
        }
        catch {
            Write-Error 'PostgreSQL could not be restarted automatically; manual recovery is required.'
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($workerDirectory) -and (Test-Path -LiteralPath $workerDirectory)) {
        $safeWorkerDirectory = Assert-SafeWorkerDirectory -Path $workerDirectory
        Remove-Item -LiteralPath $safeWorkerDirectory -Recurse -Force
    }
    $historicalBundle = $null
    $historicalPostgresPassword = $null
    $wmsAppPassword = $null
    $newPostgresPassword = $null
    $pendingBundle = $null
}

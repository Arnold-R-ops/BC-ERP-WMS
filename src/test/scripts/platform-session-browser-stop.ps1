$ErrorActionPreference = 'Stop'
$taskRoot = 'D:\ERP_WMS\BC warehouse\2G'
if ((Get-Location).Path -ne $taskRoot) { throw 'Wrong workspace; STOP' }
$taskCluster = (Resolve-Path -LiteralPath 'D:\ERP_WMS\BC warehouse\2G\target\p3-m1-pg-20260909').Path
if ($taskCluster -ne (Join-Path $taskRoot 'target\p3-m1-pg-20260909')) { throw 'Unexpected cluster path; STOP' }

function Get-ListenerOwner([int]$taskPort) {
    $taskOwners = @(Get-NetTCPConnection -State Listen -LocalPort $taskPort -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
    if ($taskOwners.Count -ne 1) { throw "Port $taskPort is absent or ambiguous; STOP" }
    return [int]$taskOwners[0]
}
function Assert-OriginalServices {
    foreach ($taskPair in @(@(5432,8980),@(8080,41952),@(5173,41292))) {
        if ((Get-ListenerOwner $taskPair[0]) -ne $taskPair[1]) { throw 'Original service identity changed; STOP' }
    }
}
function Get-CheckedProcess([int]$taskPid, [string]$taskName, [string]$taskExecutable, [string]$taskSuffix) {
    $taskProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$taskPid"
    if (-not $taskProcess -or $taskProcess.Name -ne $taskName -or $taskProcess.ExecutablePath -ne $taskExecutable `
        -or -not $taskProcess.CommandLine.EndsWith($taskSuffix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Dedicated process $taskPid identity changed; STOP"
    }
    return $taskProcess
}

# These exact identities came from the read-only startup/closeout inventory. Never kill by port alone.
Assert-OriginalServices
if ((Get-ListenerOwner 15173) -ne 35792 -or (Get-ListenerOwner 18091) -ne 21664 -or (Get-ListenerOwner 55439) -ne 42712) {
    throw 'Dedicated listener identity changed; STOP'
}
$taskFrontend = Get-CheckedProcess 35792 'node.exe' 'D:\JAVA\NODE\node.exe' ' src/test/scripts/platform-session-browser-server.mjs'
$taskBackend = Get-CheckedProcess 21664 'java.exe' 'C:\Program Files\Amazon Corretto\jdk17.0.17_10\bin\java.exe' ' @target/platform-session-browser/java-v4-90.args'
$taskPostgres = Get-CimInstance Win32_Process -Filter 'ProcessId=42712'
if (-not $taskPostgres -or $taskPostgres.Name -ne 'postgres.exe' `
    -or $taskPostgres.ExecutablePath -ne 'D:\ITsoftware\PostgreSQL\bin\postgres.exe' `
    -or -not $taskPostgres.CommandLine.Replace('\','/').Contains('-D "D:/ERP_WMS/BC warehouse/2G/target/p3-m1-pg-20260909"') `
    -or -not $taskPostgres.CommandLine.Contains('-h 127.0.0.1 -p 55439')) {
    throw 'Dedicated PostgreSQL process identity mismatch; STOP'
}
$taskHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:15173/__isolation/health'
if (-not $taskHealth.ready -or $taskHealth.database -ne 'p3_m1_platform_browser' -or $taskHealth.dbPort -ne 55439 `
    -or $taskHealth.backendPort -ne 18091 -or $taskHealth.migration -ne '4.90' -or $taskHealth.automaticMigrations `
    -or -not $taskHealth.syntheticOnly -or -not $taskHealth.browserScopedLogout) { throw 'Unexpected backend isolation health; STOP' }
$taskFault = Get-Content -LiteralPath (Join-Path $taskRoot 'target\platform-session-browser\logout-transport-fault.json') -Raw | ConvertFrom-Json
if ($taskFault.enabled) { throw 'Fault gate is still enabled; STOP' }

$taskIdentity = & 'D:/ITsoftware/PostgreSQL/bin/psql.exe' -X -w -h 127.0.0.1 -p 55439 -U p3_m1_owner -d p3_m1_platform_browser -v ON_ERROR_STOP=1 -A -t `
    -c "select current_database(),host(inet_server_addr()),inet_server_port(),current_setting('data_directory')"
if ($LASTEXITCODE -ne 0 -or $taskIdentity.Trim().Replace('\','/') -ne 'p3_m1_platform_browser|127.0.0.1|55439|D:/ERP_WMS/BC warehouse/2G/target/p3-m1-pg-20260909') {
    throw 'Live database/cluster identity mismatch; STOP'
}
$taskOtherClients = & 'D:/ITsoftware/PostgreSQL/bin/psql.exe' -X -w -h 127.0.0.1 -p 55439 -U p3_m1_owner -d p3_m1_platform_browser -v ON_ERROR_STOP=1 -A -t `
    -c "select count(*) from pg_stat_activity where backend_type='client backend' and pid<>pg_backend_pid() and not (datname='p3_m1_platform_browser' and application_name='platform-session-browser-isolated' and usename='p3_m1_app')"
if ($LASTEXITCODE -ne 0 -or $taskOtherClients.Trim() -ne '0') { throw 'Unexpected database clients; STOP without terminating them' }

$taskBefore = [DateTimeOffset]::Now.ToString('o')
Stop-Process -InputObject (Get-Process -Id $taskFrontend.ProcessId)
Stop-Process -InputObject (Get-Process -Id $taskBackend.ProcessId)
foreach ($taskStoppedId in @(35792,21664)) {
    if (Get-Process -Id $taskStoppedId -ErrorAction SilentlyContinue) { Wait-Process -Id $taskStoppedId -Timeout 15 }
}
if (@(Get-NetTCPConnection -State Listen -LocalPort 15173,18091 -ErrorAction SilentlyContinue).Count -ne 0) {
    throw 'Dedicated application listener did not stop; STOP before PostgreSQL shutdown'
}

# Orderly checkpoint/cluster shutdown; no database/files are deleted, no service is restarted.
& 'D:/ITsoftware/PostgreSQL/bin/pg_ctl.exe' stop -D $taskCluster -m fast -w -t 30
if ($LASTEXITCODE -ne 0) { throw 'Dedicated PostgreSQL did not stop cleanly; report actual state' }
if (@(Get-NetTCPConnection -State Listen -LocalPort 55439,18091,15173 -ErrorAction SilentlyContinue).Count -ne 0) {
    throw 'Unexpected surviving dedicated listener'
}
Assert-OriginalServices
[ordered]@{
    startedAt = $taskBefore
    completedAt = [DateTimeOffset]::Now.ToString('o')
    stopped = @(@{port=15173;pid=35792},@{port=18091;pid=21664},@{port=55439;pid=42712})
    retainedOriginal = @(@{port=5432;pid=8980},@{port=8080;pid=41952},@{port=5173;pid=41292})
    dedicatedListenersRemaining = 0
    databaseAndPrivateEvidencePreserved = $true
    browserTabsClosed = $false
    productionOpened = $false
} | ConvertTo-Json -Depth 4

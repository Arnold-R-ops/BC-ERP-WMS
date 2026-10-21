param(
    [string]$Database = 'packaging_test_20261019',
    [string]$RecoveryFile = 'D:\ERP_WMS\BC warehouse\2G\backups\security\local-db-credentials-20260825T004710161.dpapi',
    [string]$Tests = 'StockPackagingTest,AllocationServiceTest,ReceivingInspectionServiceTest,InventoryDispositionServiceTest,InventoryPostingServiceTest,InventoryQualityActionServiceTest,InventoryPackagingPostgresIntegrationTest,InventoryQueryServiceTest,OutboundServiceTest,ReceivingReceiptExecutorTest,StocktakeServiceTest,TransferServiceTest,InventoryQualityStateServiceTest,RmaPolicyServiceTest,RmaMigrationContractTest',
    [switch]$PrepareOnly,
    [switch]$StartRuntime,
    [switch]$PrepareBrowser
)
$ErrorActionPreference = 'Stop'
if ($Database -notmatch '^packaging_test_[0-9]{8}(_[a-z0-9]+)?$') { throw 'Refusing a non-packaging test database' }
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $taskRoot
$taskPgBin = 'D:\ITsoftware\PostgreSQL\bin'
$taskMaven = 'D:\ITsoftware\Toolbox\JetBrains社区\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd'
$taskOldPassword = $env:PGPASSWORD
$taskOldDbPassword = $env:DB_PASSWORD
$taskOldDbUser = $env:DB_USERNAME
$taskPlain = $null
try {
    Add-Type -AssemblyName System.Security
    $taskPlain = [Security.Cryptography.ProtectedData]::Unprotect(
        [IO.File]::ReadAllBytes($RecoveryFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $taskCredential = [Text.Encoding]::UTF8.GetString($taskPlain) | ConvertFrom-Json
    $env:PGPASSWORD = [string]$taskCredential.postgresPassword
    if ([string]::IsNullOrWhiteSpace($env:PGPASSWORD)) { throw 'Missing test database credential' }
    $taskExists = & (Join-Path $taskPgBin 'psql.exe') -h 127.0.0.1 -p 5432 -U postgres -d postgres -w -X -t -A -c "select 1 from pg_database where datname = '$Database'"
    if ($LASTEXITCODE -ne 0) { throw 'Database authentication failed' }
    if (($taskExists -join '').Trim() -ne '1') {
        & (Join-Path $taskPgBin 'createdb.exe') -h 127.0.0.1 -p 5432 -U postgres -w -T template0 $Database
        if ($LASTEXITCODE -ne 0) { throw 'Isolated test database creation failed' }
    }
    $taskIdentity = & (Join-Path $taskPgBin 'psql.exe') -h 127.0.0.1 -p 5432 -U postgres -d $Database -w -X -t -A -c 'select current_database()'
    if ($LASTEXITCODE -ne 0 -or ($taskIdentity -join '').Trim() -ne $Database) { throw 'Test database identity mismatch' }
    Write-Output "Isolated test database verified: $Database"
    if ($StartRuntime -or $PrepareBrowser) {
        & (Join-Path $taskPgBin 'psql.exe') -h 127.0.0.1 -p 5432 -U postgres -d $Database -w -X -v ON_ERROR_STOP=1 -c "insert into tenants (id,tenant_code,display_name,slug,status,session_version,timezone,locale,version,created_at,updated_at) values (1,'PACKAGING-TEST','Packaging acceptance only','packaging-test','ACTIVE',1,'Europe/London','zh-CN',0,now(),now()) on conflict (id) do nothing"
        if ($LASTEXITCODE -ne 0) { throw 'Acceptance tenant preparation failed' }
        # Hibernate creates test tables, but HTTP admission also needs the real migration function.
        $taskSecuritySql = Get-Content -Raw -LiteralPath 'src/main/resources/db/migration/V4_85__Tenant_Security_Foundation.sql'
        $taskFunction = [regex]::Match($taskSecuritySql, '(?s)CREATE FUNCTION bcwms_check_tenant_activity.*?END \$\$;').Value
        if (!$taskFunction) { throw 'Tenant admission function not found in migration' }
        $taskFunction = $taskFunction.Replace('CREATE FUNCTION', 'CREATE OR REPLACE FUNCTION')
        $taskFunctionFile = Join-Path $taskRoot 'target/packaging-admission.sql'
        [IO.File]::WriteAllText($taskFunctionFile, $taskFunction, [Text.UTF8Encoding]::new($false))
        & (Join-Path $taskPgBin 'psql.exe') -h 127.0.0.1 -p 5432 -U postgres -d $Database -w -X -v ON_ERROR_STOP=1 -f $taskFunctionFile
        if ($LASTEXITCODE -ne 0) { throw 'Acceptance admission preparation failed' }
    }
    if (!$PrepareOnly -and !$StartRuntime -and !$PrepareBrowser) {
        $env:DB_USERNAME = 'postgres'
        $env:DB_PASSWORD = $env:PGPASSWORD
        & $taskMaven -o -B -ntp -s src/test/resources/platform-r2-maven-settings.xml -gs src/test/resources/platform-r2-maven-settings.xml `
            '-Dmaven.repo.local=target/maven-repository' '-Dwms.build.directory=target/packaging-build' `
            "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5432/$Database" "-Dtest=$Tests" test
        if ($LASTEXITCODE -ne 0) { throw "Packaging verification failed: $LASTEXITCODE" }
    }
    if ($StartRuntime) {
        $env:DB_USERNAME = 'postgres'
        $env:DB_PASSWORD = $env:PGPASSWORD
        $taskReport = [xml](Get-Content -Raw -LiteralPath 'target/packaging-build/surefire-reports/TEST-com.wms.system.service.InventoryPackagingPostgresIntegrationTest.xml')
        $taskClasspath = ($taskReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value.Replace('\', '/')
        if (!$taskClasspath) { throw 'Run integration verification before starting the preview' }
        $taskRuntime = Join-Path $taskRoot 'target/packaging-preview'
        New-Item -ItemType Directory -Force -Path $taskRuntime | Out-Null
        $taskOldRuntimeFile = Join-Path $taskRuntime 'runtime.json'
        $taskOldRuntime = $null
        if (Test-Path -LiteralPath $taskOldRuntimeFile) {
            $taskOldRuntime = Get-Content -Raw -LiteralPath $taskOldRuntimeFile | ConvertFrom-Json
            $taskOldBackend = Get-CimInstance Win32_Process -Filter "ProcessId=$($taskOldRuntime.backendPid)" -ErrorAction SilentlyContinue
            if ($taskOldBackend -and $taskOldBackend.Name -eq 'java.exe' -and $taskOldBackend.CommandLine.Contains('@target/packaging-preview/backend.args')) {
                Stop-Process -Id $taskOldBackend.ProcessId
            }
        }
        $taskArgs = @(
            '-Dspring.devtools.restart.enabled=false', '-cp', ('"' + $taskClasspath + '"'), 'com.wms.system.WmsSystemApplication',
            '--spring.profiles.active=test', '--server.address=127.0.0.1', '--server.port=18102',
            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/$Database",
            '--spring.jpa.hibernate.ddl-auto=validate', '--spring.jpa.properties.hibernate.hbm2ddl.auto=validate',
            '--spring.datasource.hikari.maximum-pool-size=20', '--spring.jpa.open-in-view=false',
            '--wms.tenancy.enabled=false', '--wms.tenancy.require-safe-database-role=false',
            '--wms.legacy-admin-bootstrap-enabled=false', '--wms.platform-bootstrap.enabled=false',
            '--wms.integration.shopify.scheduler-enabled=false', '--wms.integration.shopify.reconcile-enabled=false',
            '--wms.tenant-security.webhook-worker-enabled=false', '--wms.tenancy.purge-enabled=false',
            '--wms.inventory-disposition.expiry-cron=-'
        )
        [IO.File]::WriteAllLines((Join-Path $taskRuntime 'backend.args'), $taskArgs, [Text.UTF8Encoding]::new($false))
        $taskBackend = Start-Process -FilePath 'C:\Program Files\Amazon Corretto\jdk17.0.17_10\bin\java.exe' -WindowStyle Hidden -PassThru -WorkingDirectory $taskRoot -ArgumentList '@target/packaging-preview/backend.args' -RedirectStandardOutput (Join-Path $taskRuntime 'backend.log') -RedirectStandardError (Join-Path $taskRuntime 'backend.stderr.log')
        $taskOldApi = $env:WMS_API_TARGET
        $taskExistingFrontend = Get-NetTCPConnection -LocalPort 55185 -State Listen -ErrorAction SilentlyContinue
        try {
            $env:WMS_API_TARGET = 'http://127.0.0.1:18102'
            if ($taskExistingFrontend) {
                if (!$taskOldRuntime -or $taskOldRuntime.frontendPid -ne $taskExistingFrontend[0].OwningProcess) {
                    throw 'Port 55185 belongs to an unrecognized process; refusing to reuse it'
                }
                $taskFrontendInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($taskExistingFrontend[0].OwningProcess)"
                if ($taskFrontendInfo.Name -ne 'node.exe' -or !$taskFrontendInfo.CommandLine.Contains('node_modules/vite/bin/vite.js')) {
                    throw 'Recorded frontend identity changed; refusing to reuse it'
                }
                $taskFrontend = Get-Process -Id $taskExistingFrontend[0].OwningProcess
            } else {
                $taskFrontend = Start-Process -FilePath 'D:\JAVA\NODE\node.exe' -WindowStyle Hidden -PassThru -WorkingDirectory (Join-Path $taskRoot 'frontend') -ArgumentList 'node_modules/vite/bin/vite.js --host 127.0.0.1 --port 55185 --strictPort' -RedirectStandardOutput (Join-Path $taskRuntime 'frontend.log') -RedirectStandardError (Join-Path $taskRuntime 'frontend.stderr.log')
            }
        } finally { $env:WMS_API_TARGET = $taskOldApi }
        @{ backendPid=$taskBackend.Id; frontendPid=$taskFrontend.Id; database=$Database; url='http://127.0.0.1:55185' } |
            ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath (Join-Path $taskRuntime 'runtime.json')
        Write-Output 'Isolated acceptance preview starting at http://127.0.0.1:55185'
    }
} finally {
    $env:PGPASSWORD = $taskOldPassword
    $env:DB_PASSWORD = $taskOldDbPassword
    $env:DB_USERNAME = $taskOldDbUser
    $taskCredential = $null
    if ($null -ne $taskPlain) { [Array]::Clear($taskPlain, 0, $taskPlain.Length) }
}

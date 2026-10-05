[CmdletBinding()]
param(
    [string]$JavaPath = 'C:\Program Files\Amazon Corretto\jdk17.0.17_10\bin\java.exe',
    [string]$NodePath = 'D:\JAVA\NODE\node.exe'
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$taskStateDir = Join-Path $taskRoot 'target\acceptance-20261004'
$taskJar = Join-Path $taskRoot 'target\acceptance-build\wms-system-0.0.1-SNAPSHOT.jar'
$taskFrontend = Join-Path $taskRoot 'frontend'
foreach ($taskRequired in @($JavaPath,$NodePath,$taskJar,(Join-Path $taskFrontend 'dist\index.html'),(Join-Path $taskStateDir 'migration-passed.json'))) {
    if (!(Test-Path -LiteralPath $taskRequired -PathType Leaf)) { throw "Acceptance prerequisite missing: $taskRequired" }
}
$taskMigration = Get-Content -LiteralPath (Join-Path $taskStateDir 'migration-passed.json') -Raw | ConvertFrom-Json
$taskPackage = Get-Content -LiteralPath (Join-Path $taskStateDir 'final-package.json') -Raw | ConvertFrom-Json
if (!$taskPackage.v476ResourceMatchesSource -or (Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash -ne $taskPackage.jarSha256) {
    throw 'The executable JAR does not match the verified final package.'
}
if ($taskMigration.database -ne 'salesbinder_acceptance_20261005' -or $taskMigration.port -ne 55519 -or $taskMigration.to -ne '4.94' -or !$taskMigration.originalCountsStockAmountsAndPasswordHashesUnchanged) {
    throw 'The migration acceptance evidence does not match this candidate database.'
}
$taskMigrationHashes = Get-Content -LiteralPath (Join-Path $taskStateDir 'migration-source-hashes.json') -Raw | ConvertFrom-Json
foreach ($taskSql in $taskMigrationHashes.PSObject.Properties) {
    $taskSqlPath = Join-Path $taskRoot ('src\main\resources\db\migration\'+$taskSql.Name)
    if ((Get-FileHash -LiteralPath $taskSqlPath -Algorithm SHA256).Hash -ne $taskSql.Value) {
        throw "Migration changed since acceptance: $($taskSql.Name)"
    }
}
foreach ($taskName in @('TENANT_JWT_SECRET','PLATFORM_JWT_SECRET','BATCH_SALT')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskName))) { throw "Required local environment variable missing: $taskName" }
}
$taskPgBin = 'D:\ITsoftware\PostgreSQL\bin'
$taskDbIdentity = & (Join-Path $taskPgBin 'psql.exe') -h 127.0.0.1 -p 55519 -U postgres -d salesbinder_acceptance_20261005 -w -X -t -A -c "select current_database()||':'||inet_server_port()||':'||(select version from flyway_schema_history order by installed_rank desc limit 1)"
if ($LASTEXITCODE -ne 0 -or $taskDbIdentity.Trim() -ne 'salesbinder_acceptance_20261005:55519:4.94') { throw 'The dedicated acceptance database is unavailable or not migrated.' }
foreach ($taskPort in @(18091,55173)) {
    $taskProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$taskPort)
    try { $taskProbe.Start() } catch { throw "Port $taskPort is occupied; no running process was changed." } finally { $taskProbe.Stop() }
}
$taskStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$taskBackendLog = Join-Path $taskStateDir "backend-$taskStamp.stdout.log"
$taskBackendError = Join-Path $taskStateDir "backend-$taskStamp.stderr.log"
$taskArguments = @(
    '-Dfile.encoding=UTF-8','-Dspring.devtools.restart.enabled=false','-jar',('"'+$taskJar.Replace('\','/')+'"'),
    '--server.address=127.0.0.1','--server.port=18091',
    '--spring.datasource.url=jdbc:postgresql://127.0.0.1:55519/salesbinder_acceptance_20261005',
    '--spring.datasource.username=wms_upgrade_app','--spring.datasource.password=',
    '--spring.jpa.hibernate.ddl-auto=validate','--spring.jpa.properties.hibernate.hbm2ddl.auto=validate',
    '--spring.jpa.show-sql=false','--spring.flyway.enabled=false',
    '--wms.tenancy.enabled=false','--wms.tenancy.allow-local-development-host=true','--wms.tenancy.require-safe-database-role=true',
    '--wms.platform-bootstrap.enabled=false','--wms.legacy-admin-bootstrap-enabled=false',
    '--wms.signup.delivery-mode=DEVELOPMENT',
    '--wms.integration.shopify.scheduler-enabled=false','--wms.integration.shopify.reconcile-enabled=false',
    '--wms.tenant-security.webhook-worker-enabled=false','--wms.tenant-security.controls-enabled=false',
    '--wms.tenancy.purge-enabled=false','--wms.tenancy.purge-cron=-','--wms.inventory-disposition.expiry-cron=-',
    '--wms.platform-mfa.challenge-cleanup-cron=-','--wms.platform-access.cleanup-cron=-',
    '--logging.level.com.wms=WARN','--logging.level.org.springframework.web=INFO',
    '--logging.level.org.hibernate.SQL=OFF','--logging.level.org.hibernate.orm.jdbc.bind=OFF'
)
$taskArgFile = Join-Path $taskStateDir 'backend.args'
[IO.File]::WriteAllLines($taskArgFile,$taskArguments,[Text.UTF8Encoding]::new($false))
$taskBackend = Start-Process -FilePath $JavaPath -ArgumentList ('@"'+$taskArgFile+'"') -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $taskBackendLog -RedirectStandardError $taskBackendError
$taskOldApiTarget = $env:WMS_API_TARGET
try {
    $env:WMS_API_TARGET = 'http://127.0.0.1:18091'
    $taskFrontLog = Join-Path $taskStateDir "frontend-$taskStamp.stdout.log"
    $taskFrontError = Join-Path $taskStateDir "frontend-$taskStamp.stderr.log"
    $taskFront = Start-Process -FilePath $NodePath -ArgumentList @('node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','55173','--strictPort') -WorkingDirectory $taskFrontend -WindowStyle Hidden -PassThru -RedirectStandardOutput $taskFrontLog -RedirectStandardError $taskFrontError
} finally { $env:WMS_API_TARGET = $taskOldApiTarget }
[ordered]@{
    root=$taskRoot; backendPid=$taskBackend.Id; frontendPid=$taskFront.Id;
    api='http://127.0.0.1:18091'; url='http://127.0.0.1:55173/'; database='127.0.0.1:55519/salesbinder_acceptance_20261005';
    jarSha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash;
    backendLog=$taskBackendLog; backendError=$taskBackendError; frontendLog=$taskFrontLog;
    state='Started; confirm HTTP and login before acceptance.'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskStateDir 'runtime.json') -Encoding UTF8
Write-Output 'Acceptance copy started at http://127.0.0.1:55173/ . Existing business database and original login entry were not changed.'

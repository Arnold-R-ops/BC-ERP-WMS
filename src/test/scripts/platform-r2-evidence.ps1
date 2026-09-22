$ErrorActionPreference='Stop'
# Read completed preflight output, generate sanitized delivery JSON; no execution/database/service APIs.
$taskRoot='D:\ERP_WMS\BC warehouse\2G'
if((Get-Location).Path -ne $taskRoot){throw 'Wrong workspace'}
$taskRun=Join-Path $taskRoot 'target/platform-r2-preflight-20260911'
$taskDelivery=Join-Path $taskRoot 'docs/releases/platform-r2-preflight-20260911/p2'
$taskManifest=Get-Content -Raw -LiteralPath (Join-Path $taskDelivery 'input-manifest.json') | ConvertFrom-Json
foreach($taskFile in $taskManifest.files){if((Get-FileHash -Algorithm SHA256 -LiteralPath $taskFile.path).Hash.ToLowerInvariant() -ne $taskFile.sha256){throw 'Compiler input changed'}}
function File-Digest($file){(Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant()}
function Report-Summary($attempt){
    $file=Join-Path $taskRun "$attempt/backend/surefire-reports/TEST-com.wms.platformverification.PlatformR2IsolationTest.xml"
    $xml=[xml](Get-Content -Raw -LiteralPath $file)
    [ordered]@{tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped
        xmlSha256=File-Digest $file;cases=@($xml.testsuite.testcase | ForEach-Object{[ordered]@{name=$_.name;passed=(!$_.error -and !$_.failure -and !$_.skipped)}})}
}
$taskFinal=Report-Summary 'p2'
if($taskFinal.tests -ne 36 -or $taskFinal.failures -ne 0 -or $taskFinal.errors -ne 0 -or $taskFinal.skipped -ne 0){throw 'Unexpected final guard result'}
$taskCompile=Get-Content -Raw -LiteralPath (Join-Path $taskRun 'p2/compile.log')
if($taskCompile -notmatch 'Compiling 580 source files' -or $taskCompile -notmatch 'Compiling 192 source files' -or $taskCompile -notmatch 'BUILD SUCCESS'){throw 'Full fresh compilation evidence missing'}
$taskJar=Join-Path $taskRun 'p2/backend/wms-system-0.0.1-SNAPSHOT.jar'
$taskHelperClasses=@(Get-ChildItem -Recurse -File -LiteralPath (Join-Path $taskRun 'p2/backend/test-classes/com/wms/platformverification') -Filter '*.class' | ForEach-Object{
    [ordered]@{name=$_.Name;sha256=File-Digest $_.FullName}
})
$taskSelection=@(
    'com.wms.system.platform.service.PlatformSessionPostgresTest',
    'com.wms.system.platform.service.PlatformInvitationSessionPostgresTest#invitation*',
    'com.wms.system.platform.service.PlatformAccessGrantRoleRaceIntegrationTest',
    'com.wms.system.platform.service.PlatformAdminInvitationMfaTransactionIntegrationTest',
    'com.wms.system.platform.service.PlatformCompanyDataServiceIntegrationTest',
    'com.wms.system.tenant.security.TenantSecurityPostgresTest',
    'com.wms.system.tenant.security.TenantSecurityCommandsPostgresTest',
    'com.wms.system.tenant.security.TenantSecuritySpringPostgresTest'
)
$taskEvidence=[ordered]@{
    collectedAt=[DateTimeOffset]::Now.ToString('o');inputId=$taskManifest.id;formalR2SnapshotCreated=$false
    firstAttempt=Report-Summary 'p1';firstAttemptCause='Two Mockito nested-stubbing fixture errors; source and raw evidence retained'
    finalGuardTests=$taskFinal;guardTestsUseMockConnectionsOnly=$true
    build=[ordered]@{exitCode=0;mainSources=580;testSources=192;oldClassesReused=$false;jarSha256=File-Digest $taskJar;jarBytes=(Get-Item -LiteralPath $taskJar).Length
        compileLogSha256=File-Digest (Join-Path $taskRun 'p2/compile.log');helperClasses=$taskHelperClasses}
    scriptChecks=[ordered]@{nodeSyntaxCheck='passed';toolVersions='Node 24.12.0; pnpm 11.16.0; Java 17.0.17; Maven 3.9.9';preparerPlanOnlyExitCode=0;inputReverification='passed'}
    existingBusinessTestBodiesUnchanged=63;productCodeChanged=$false;globalPathChanged=$false;lockFileChanged=$false
    databaseConnectionsMade=$false;databasesCreated=$false;migrationsExecuted=$false;databaseTestsExecuted=$false;servicesStarted=$false
    frontendFrozenInstallExecuted=$false;formalTypecheckExecuted=$false;formalFrontendBuildExecuted=$false
    blocker='Original addressLength.audit.test.tsx(23,68) TS2769 unchanged; owner task handles fix. No test exclusion.'
    futureDatabase=[ordered]@{authorized=$false;host='127.0.0.1';port=55449;main='p3_m1_platform_release_r2';upgrade='p3_m1_platform_upgrade_r2'
        dataDirectory='D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r2/pgdata';appRole='p3_r2_app';migratorRole='p3_r2_migrator'
        expectedFinalVersions=@('4.71','4.72','4.85','4.86','4.87','4.88','4.89','4.90');selectors=$taskSelection;expectedExistingTestMethods=63
        automaticSkipIsNotPass=$true;zeroExecutedOrUnexpectedSkippedFailsAcceptance=$true
        fixtureAndStartupPreparationStillRequiresApproval=$true;productionMigrationPlan=$false}
    notCovered=@('Live PostgreSQL identity/permissions/DDL', 'Actual Flyway B baseline/increment compatibility', 'Spring/MockMvc database wiring and concurrency',
        'Full manual invitation activation', 'Final recovery reconciliation business boundary', 'Production compatibility or release acceptance')
}
$taskOutput=Join-Path $taskDelivery 'preflight-results.json'
if(Test-Path -LiteralPath $taskOutput){throw 'Never overwrite evidence'}
$taskStream=[IO.File]::Open($taskOutput,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
try{$taskBytes=[Text.Encoding]::UTF8.GetBytes(($taskEvidence | ConvertTo-Json -Depth 15)+"`n");$taskStream.Write($taskBytes,0,$taskBytes.Length)}finally{$taskStream.Dispose()}
Write-Output 'Sanitized preflight result written; no database operations performed'

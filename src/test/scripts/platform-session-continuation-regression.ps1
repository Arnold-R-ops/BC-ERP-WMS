$ErrorActionPreference = 'Stop'
$taskRoot = 'D:\ERP_WMS\BC warehouse\2G'
if ((Get-Location).Path -ne $taskRoot) { throw 'Wrong isolated working directory' }
$taskRun = Join-Path $taskRoot 'target\platform-session-browser'
$taskOutput = Join-Path $taskRun 'private\continuation-regression-20260910'
if (Test-Path -LiteralPath $taskOutput) { throw 'Dedicated result directory already exists; never overwrite evidence' }
$taskJavaRoot = 'C:\Program Files\Amazon Corretto\jdk17.0.17_10'
$taskCpLines = @(Get-Content -LiteralPath (Join-Path $taskRun 'java-v4-90.args') | Where-Object { $_.StartsWith('"D:/ERP_WMS/') })
if ($taskCpLines.Count -ne 1) { throw 'Unexpected isolated classpath' }

# Existing fixed verifier validates the separate automated-test database and its applied checksums.
# It does not migrate, repair, create databases or touch the live browser database.
& (Join-Path $taskJavaRoot 'bin\java.exe') -Xmx256m -cp $taskCpLines[0].Trim('"') com.wms.browseracceptance.PlatformBrowserScopeDatabase verify
if ($LASTEXITCODE -ne 0) { throw 'Isolated database verification failed; STOP' }

# Reuse the exact V4_90 compiled artifact under browser acceptance, not newer ERP worktree output.
# Copy into a new private test directory so existing reports and running service classes are untouched.
$taskSource = Join-Path $taskRoot 'target\platform-browser-scope-build'
New-Item -ItemType Directory -Path $taskOutput | Out-Null
Copy-Item -LiteralPath (Join-Path $taskSource 'classes') -Destination (Join-Path $taskOutput 'classes') -Recurse
Copy-Item -LiteralPath (Join-Path $taskSource 'test-classes') -Destination (Join-Path $taskOutput 'test-classes') -Recurse
$taskServiceRelative = 'classes\com\wms\system\platform\service\PlatformSessionService.class'
$taskServiceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskSource $taskServiceRelative)).Hash
if ((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskOutput $taskServiceRelative)).Hash -ne $taskServiceHash) {
    throw 'Compiled session artifact copy mismatch'
}

$env:JAVA_HOME = $taskJavaRoot
$taskSelectors = 'TenantSecuritySpringPostgresTest#realPlatformExportRequiresSuspensionReasonAndRechecksDownloadAndActor+platformHttpTwoPersonRecoveryCommitsWithRealAuditRepository'
& (Join-Path $taskRoot 'target\codex-tools\apache-maven-3.9.9\bin\mvn.cmd') -o -B -ntp `
    --log-file (Join-Path $taskOutput 'maven-private.log') `
    "-Dwms.build.directory=$taskOutput" "-Dtest=$taskSelectors" `
    '-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55439/p3_m1_platform_browser_scope' `
    '-Dspring.flyway.enabled=false' '-Dspring.sql.init.mode=never' '-Dspring.jpa.hibernate.ddl-auto=validate' `
    '-Dspring.test.mockmvc.print=NONE' '-Dspring.test.mockmvc.print-only-on-failure=false' `
    "-Dwms.platform-access.export-directory=$(Join-Path $taskOutput 'exports')" surefire:test
$taskExitCode = $LASTEXITCODE
$taskReport = Join-Path $taskOutput 'surefire-reports\TEST-com.wms.system.tenant.security.TenantSecuritySpringPostgresTest.xml'
if (-not (Test-Path -LiteralPath $taskReport)) { throw 'No test report; inspect only sanitized private-log diagnostics' }
$taskXml = [xml](Get-Content -LiteralPath $taskReport -Raw)
$taskCases = @($taskXml.testsuite.testcase | ForEach-Object {
    [pscustomobject]@{ name = [string]$_.name; passed = ($null -eq $_.failure -and $null -eq $_.error -and $null -eq $_.skipped) }
})
$taskSummary = [ordered]@{
    observedAt = [DateTimeOffset]::Now.ToString('o')
    database = '127.0.0.1:55439/p3_m1_platform_browser_scope'
    runtimeArtifact = 'target/platform-browser-scope-build'
    copiedPlatformSessionClassSha256 = $taskServiceHash
    tests = [int]$taskXml.testsuite.tests
    failures = [int]$taskXml.testsuite.failures
    errors = [int]$taskXml.testsuite.errors
    skipped = [int]$taskXml.testsuite.skipped
    cases = $taskCases
    exitCode = $taskExitCode
    migrationsEnabled = $false
    browserDatabaseUsed = $false
    existingServiceClassesModified = $false
    rawReportsArePrivate = $true
}
$taskSummary | ConvertTo-Json -Depth 5
if ($taskExitCode -ne 0 -or $taskSummary.tests -ne 2 -or $taskSummary.failures -ne 0 -or $taskSummary.errors -ne 0 -or $taskSummary.skipped -ne 0) {
    throw 'Isolated continuation regression did not pass exactly two tests; STOP'
}

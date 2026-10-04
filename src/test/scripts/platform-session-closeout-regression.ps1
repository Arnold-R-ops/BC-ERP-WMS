$ErrorActionPreference = 'Stop'
$taskRoot = 'D:\ERP_WMS\BC warehouse\2G'
if ((Get-Location).Path -ne $taskRoot) { throw 'Wrong isolated working directory' }
$taskRun = Join-Path $taskRoot 'target\platform-session-browser'
$taskOutput = Join-Path $taskRun 'private\closeout-regression-20260910'
if (Test-Path -LiteralPath $taskOutput) { throw 'Result directory exists; never overwrite acceptance evidence' }
$taskJavaRoot = 'C:\Program Files\Amazon Corretto\jdk17.0.17_10'
$taskCpLines = @(Get-Content -LiteralPath (Join-Path $taskRun 'java-v4-90.args') | Where-Object { $_.StartsWith('"D:/ERP_WMS/') })
if ($taskCpLines.Count -ne 1) { throw 'Unexpected isolated runtime classpath' }
$taskClasspath = $taskCpLines[0].Trim('"')

# Read-only verification: no migrate, repair, baseline, schema creation or daily database fallback.
& (Join-Path $taskJavaRoot 'bin\java.exe') -Xmx256m -cp $taskClasspath com.wms.browseracceptance.PlatformBrowserScopeDatabase verify
if ($LASTEXITCODE -ne 0) { throw 'Database identity/migration mismatch; STOP' }

# Use the accepted runtime snapshot. Only the new test source is compiled, never shared/ERP sources.
$taskSource = Join-Path $taskRoot 'target\platform-browser-scope-build'
New-Item -ItemType Directory -Path $taskOutput | Out-Null
Copy-Item -LiteralPath (Join-Path $taskSource 'classes') -Destination (Join-Path $taskOutput 'classes') -Recurse
Copy-Item -LiteralPath (Join-Path $taskSource 'test-classes') -Destination (Join-Path $taskOutput 'test-classes') -Recurse
$taskProductClasses = @('PlatformSessionService', 'PlatformMfaService', 'PlatformAdminInvitationService')
$taskProductHashes = @($taskProductClasses | ForEach-Object {
    $taskRelative = "classes\com\wms\system\platform\service\$_.class"
    $taskOriginalHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskSource $taskRelative)).Hash
    if ($taskOriginalHash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskOutput $taskRelative)).Hash) {
        throw 'Runtime snapshot copy mismatch'
    }
    [pscustomobject]@{ name = $_; sha256 = $taskOriginalHash }
})
$taskCompileCp = "$(Join-Path $taskOutput 'test-classes');$(Join-Path $taskOutput 'classes');$taskClasspath"
& (Join-Path $taskJavaRoot 'bin\javac.exe') -proc:none -encoding UTF-8 -cp $taskCompileCp `
    -d (Join-Path $taskOutput 'test-classes') `
    (Join-Path $taskRoot 'src\test\java\com\wms\system\platform\service\PlatformInvitationSessionPostgresTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Dedicated acceptance test compilation failed' }

$env:JAVA_HOME = $taskJavaRoot
$taskSelectors = 'PlatformInvitationSessionPostgresTest#invitation*,PlatformSessionPostgresTest,PlatformSessionServiceTest,PlatformBrowserBindingTest,PlatformMfaServiceTest,DynamicAuthorizationManagerTest,JwtAuthenticationFilterTest,JwtUtilTest'
& (Join-Path $taskRoot 'target\codex-tools\apache-maven-3.9.9\bin\mvn.cmd') -o -B -ntp `
    --log-file (Join-Path $taskOutput 'maven-private.log') `
    "-Dwms.build.directory=$taskOutput" "-Dtest=$taskSelectors" `
    '-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55439/p3_m1_platform_browser_scope' `
    '-Dspring.flyway.enabled=false' '-Dspring.sql.init.mode=never' '-Dspring.jpa.hibernate.ddl-auto=validate' `
    '-Dspring.test.mockmvc.print=NONE' '-Dspring.test.mockmvc.print-only-on-failure=false' `
    '-Dmaven.test.redirectTestOutputToFile=true' surefire:test
$taskExitCode = $LASTEXITCODE
$taskSuites = @(Get-ChildItem -LiteralPath (Join-Path $taskOutput 'surefire-reports') -Filter 'TEST-*.xml' | ForEach-Object {
    $taskXml = [xml](Get-Content -LiteralPath $_.FullName -Raw)
    [pscustomobject]@{
        name = [string]$taskXml.testsuite.name
        tests = [int]$taskXml.testsuite.tests
        failures = [int]$taskXml.testsuite.failures
        errors = [int]$taskXml.testsuite.errors
        skipped = [int]$taskXml.testsuite.skipped
        cases = @($taskXml.testsuite.testcase | ForEach-Object {
            [pscustomobject]@{ name = [string]$_.name; passed = ($null -eq $_.failure -and $null -eq $_.error -and $null -eq $_.skipped) }
        })
    }
})
$taskSummary = [ordered]@{
    observedAt = [DateTimeOffset]::Now.ToString('o')
    database = '127.0.0.1:55439/p3_m1_platform_browser_scope'
    runtimeArtifact = 'target/platform-browser-scope-build'
    productClassHashes = $taskProductHashes
    tests = ($taskSuites | Measure-Object tests -Sum).Sum
    failures = ($taskSuites | Measure-Object failures -Sum).Sum
    errors = ($taskSuites | Measure-Object errors -Sum).Sum
    skipped = ($taskSuites | Measure-Object skipped -Sum).Sum
    suites = $taskSuites
    exitCode = $taskExitCode
    migrationsEnabled = $false
    browserDatabaseUsed = $false
    productSourcesCompiled = $false
    rawReportsArePrivate = $true
}
$taskSummary | ConvertTo-Json -Depth 7
if ($taskExitCode -ne 0 -or $taskSummary.tests -ne 69 -or $taskSummary.failures -ne 0 -or $taskSummary.errors -ne 0 -or $taskSummary.skipped -ne 0) {
    throw 'Closeout regression did not pass exactly 69 tests; STOP'
}
foreach ($taskHash in $taskProductHashes) {
    $taskRelative = "classes\com\wms\system\platform\service\$($taskHash.name).class"
    if ($taskHash.sha256 -ne (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskSource $taskRelative)).Hash) {
        throw 'Accepted runtime artifact changed during testing; STOP'
    }
}
& (Join-Path $taskJavaRoot 'bin\java.exe') -Xmx256m -cp $taskClasspath com.wms.browseracceptance.PlatformBrowserScopeDatabase verify
if ($LASTEXITCODE -ne 0) { throw 'Post-test database verification failed; STOP' }

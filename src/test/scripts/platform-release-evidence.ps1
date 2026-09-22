$ErrorActionPreference = 'Stop'
# Evidence generation only. Never starts a service, connects to a database or changes product files.
$taskRoot = 'D:\ERP_WMS\BC warehouse\2G'
if ((Get-Location).Path -ne $taskRoot) { throw 'Wrong workspace' }
$taskRun = Join-Path $taskRoot 'target\platform-release-baseline-20260911\r1'
$taskDelivery = Join-Path $taskRoot 'docs\releases\platform-baseline-20260911'
$taskSource = Join-Path $taskRun 'source'
$taskBackend = Join-Path $taskRun 'backend'
$taskManifest = Get-Content -Raw -LiteralPath (Join-Path $taskDelivery 'source-manifest.json') | ConvertFrom-Json
function New-EvidenceJson($name, $value) {
    $destination = Join-Path $taskDelivery $name
    if (Test-Path -LiteralPath $destination) { throw "Never overwrite existing evidence: $name" }
    $stream = [IO.File]::Open($destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes(($value | ConvertTo-Json -Depth 20) + "`n")
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
}
function File-Digest($file) { (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant() }
function Stream-Digest($stream) {
    $hash = [Security.Cryptography.SHA256]::Create()
    try { [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $hash.Dispose() }
}
function Relative-Path($file) { $file.Substring($taskRoot.Length + 1).Replace('\', '/') }

$snapshotChanges = @()
$workspaceChanges = @()
foreach ($file in $taskManifest.files) {
    if ((File-Digest (Join-Path $taskSource $file.path)) -ne $file.sha256) { $snapshotChanges += $file.path }
    $original = Join-Path $taskRoot $file.path
    if (!(Test-Path -LiteralPath $original) -or (File-Digest $original) -ne $file.sha256) { $workspaceChanges += $file.path }
}
if ($snapshotChanges.Count) { throw 'Snapshot changed; stop evidence generation' }
if ((File-Digest (Join-Path $taskRoot 'frontend/node_modules/.pnpm/lock.yaml')) -ne $taskManifest.frontendLockSha256) { throw 'Dependency lock changed' }

$taskSelection = Get-Content -Raw -LiteralPath (Join-Path $taskDelivery 'no-service-test-selection.json') | ConvertFrom-Json
$suites = @()
$classpath = $null
$javaVersion = $null
foreach ($report in Get-ChildItem -LiteralPath (Join-Path $taskBackend 'surefire-reports') -Filter 'TEST-*.xml') {
    $xml = [xml](Get-Content -Raw -LiteralPath $report.FullName)
    if ($xml.testsuite.name -notin $taskSelection.selectors) { throw "Unexpected suite: $($xml.testsuite.name)" }
    $suites += [ordered]@{ name = [string]$xml.testsuite.name; tests = [int]$xml.testsuite.tests
        failures = [int]$xml.testsuite.failures; errors = [int]$xml.testsuite.errors; skipped = [int]$xml.testsuite.skipped
        timestamp = [string]$xml.testsuite.timestamp; reportSha256 = File-Digest $report.FullName
        cases = @($xml.testsuite.testcase | ForEach-Object { [string]$_.name }) }
    $classpath = [string]($xml.testsuite.properties.property | Where-Object name -eq 'surefire.test.class.path').value
    $javaVersion = [string]($xml.testsuite.properties.property | Where-Object name -eq 'java.version').value
}
if ($suites.Count -ne $taskSelection.selectors.Count) { throw 'Missing selected test report' }
$totals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
foreach ($suite in $suites) {
    foreach ($key in @('tests', 'failures', 'errors', 'skipped')) { $totals[$key] += [int]$suite[$key] }
}
if ($totals.tests -lt 1) { throw 'No test counts collected' }
$testDependencies = @()
foreach ($entry in $classpath.Split(';')) {
    # Surefire appends a trailing separator to the recorded property.
    if ([string]::IsNullOrWhiteSpace($entry)) { continue }
    if ($entry -eq (Join-Path $taskBackend 'test-classes') -or $entry -eq (Join-Path $taskBackend 'classes')) { continue }
    if (!$entry.StartsWith('C:\Users\Arnold\.m2\repository\') -or !$entry.EndsWith('.jar')) {
        throw 'Test classpath contains unexpected compiled output or dependency location'
    }
    $testDependencies += [ordered]@{ repositoryPath = $entry.Substring('C:\Users\Arnold\.m2\repository\'.Length).Replace('\', '/')
        sha256 = File-Digest $entry; bytes = (Get-Item -LiteralPath $entry).Length }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskJar = Join-Path $taskBackend 'wms-system-0.0.1-SNAPSHOT.jar'
$archive = [IO.Compression.ZipFile]::OpenRead($taskJar)
$runtimeDependencies = @()
$embeddedMigrations = @()
$verifiedClassEntries = 0
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName.StartsWith('BOOT-INF/lib/') -and $entry.FullName.EndsWith('.jar')) {
            $stream = $entry.Open()
            try { $runtimeDependencies += [ordered]@{ name = $entry.Name; bytes = $entry.Length; sha256 = Stream-Digest $stream } }
            finally { $stream.Dispose() }
        }
        if ($entry.FullName.StartsWith('BOOT-INF/classes/') -and $entry.FullName.EndsWith('.class')) {
            $compiled = Join-Path (Join-Path $taskBackend 'classes') $entry.FullName.Substring('BOOT-INF/classes/'.Length)
            $stream = $entry.Open()
            try { if ((Stream-Digest $stream) -ne (File-Digest $compiled)) { throw 'JAR and fresh classes differ' } }
            finally { $stream.Dispose() }
            $verifiedClassEntries++
        }
        if ($entry.FullName.StartsWith('BOOT-INF/classes/db/migration/') -and $entry.FullName.EndsWith('.sql')) {
            $sourcePath = 'src/main/resources/' + $entry.FullName.Substring('BOOT-INF/classes/'.Length)
            $stream = $entry.Open()
            try { $digest = Stream-Digest $stream } finally { $stream.Dispose() }
            if ($digest -ne (File-Digest (Join-Path $taskSource $sourcePath))) { throw 'JAR and source migration differ' }
            $embeddedMigrations += [ordered]@{ path = $sourcePath; sha256 = $digest }
        }
    }
} finally { $archive.Dispose() }
if ($embeddedMigrations.Count -ne 94) { throw 'Incomplete migration packaging' }
$compiledFiles = @(foreach ($directory in @('classes', 'test-classes')) {
    Get-ChildItem -LiteralPath (Join-Path $taskBackend $directory) -Recurse -File -Filter '*.class' | ForEach-Object {
        [ordered]@{ path = Relative-Path $_.FullName; bytes = $_.Length; sha256 = File-Digest $_.FullName }
    }
})
New-EvidenceJson 'backend-artifact-manifest.json' ([ordered]@{
    snapshotId = $taskManifest.snapshotId; compiledFromSource = $true; oldClassesReused = $false
    jar = [ordered]@{ path = Relative-Path $taskJar; bytes = (Get-Item -LiteralPath $taskJar).Length; sha256 = File-Digest $taskJar }
    verifiedPackagedClasses = $verifiedClassEntries; embeddedMigrations = $embeddedMigrations; compiledClassFiles = $compiledFiles
    productionReady = $false; note = 'Verification artifact only: shared-backend snapshot, not a separately deployable platform package.'
})
$frontendPackages = @(foreach ($name in @('typescript','vite','vitest','react','react-dom','antd','@testing-library/dom','@testing-library/react','jsdom')) {
    # @testing-library/dom is nested in pnpm; resolve from the testing-library/react entry if not top-level.
    $packageFile = Join-Path $taskRoot "frontend/node_modules/$name/package.json"
    if (!(Test-Path -LiteralPath $packageFile) -and $name -eq '@testing-library/dom') {
        $packageFile = (Get-ChildItem -LiteralPath (Join-Path $taskRoot 'frontend/node_modules/.pnpm') -Directory -Filter '@testing-library+dom@10.4.1*' | Select-Object -First 1).FullName + '/node_modules/@testing-library/dom/package.json'
    }
    $package = Get-Content -Raw -LiteralPath $packageFile | ConvertFrom-Json
    [ordered]@{ name = $package.name; version = $package.version; manifestSha256 = File-Digest $packageFile }
})
New-EvidenceJson 'dependency-manifest.json' ([ordered]@{
    snapshotId = $taskManifest.snapshotId; frontendLockSha256 = $taskManifest.frontendLockSha256
    installedFrontendLockMatches = $true; frontendPackages = $frontendPackages
    javaVersion = $javaVersion; mavenVersion = '3.9.9'; directNodeVersion = '24.12.0'
    packageRunnerNodeVersion = '24.19.0'; declaredPackageManager = 'pnpm@11.16.0'; actualPackageRunner = 'pnpm@11.19.0'
    packageManagerDifference = 'Existing bundled runner used without dependency installation. Pin/approve toolchain before release candidate rerun.'
    pomSha256 = File-Digest (Join-Path $taskSource 'pom.xml'); backendHasStrictLockfile = $false
    backendLockNote = 'POM/parent is not a strict dependency lock. These are resolved dependency byte fingerprints, not a replacement resolver lock.'
    runtimeLibraries = $runtimeDependencies; testClasspathLibraries = $testDependencies
})
function Frontend-Summary($name) {
    $file = Join-Path $taskRun $name
    $report = Get-Content -Raw -LiteralPath $file | ConvertFrom-Json
    [ordered]@{ passed = $report.numPassedTests; failed = $report.numFailedTests; pending = $report.numPendingTests
        tests = $report.numTotalTests; files = $report.testResults.Count; success = $report.success
        startTimeEpochMs = $report.startTime; reportSha256 = File-Digest $file
        results = @($report.testResults | ForEach-Object { [ordered]@{
            path = $_.name.Replace($taskSource.Replace('\', '/') + '/', ''); status = $_.status
            tests = $_.assertionResults.Count; startTimeEpochMs = $_.startTime; endTimeEpochMs = $_.endTime
        } }) }
}
$tap = Get-Content -Raw -LiteralPath (Join-Path $taskRun 'logout-fault-selector.tap')
if ($tap -notmatch '# tests 7' -or $tap -notmatch '# pass 7' -or $tap -notmatch '# fail 0') { throw 'Unexpected fault-selector result' }
$buildLog = Get-Content -Raw -LiteralPath (Join-Path $taskRun 'backend-build-local-cache.log')
if ($buildLog -notmatch 'BUILD SUCCESS' -or $buildLog -notmatch 'Compiling 580 source files' -or $buildLog -notmatch 'Compiling 188 source files') { throw 'Fresh compilation evidence missing' }
New-EvidenceJson 'validation-results.json' ([ordered]@{
    collectedAt = [DateTimeOffset]::Now.ToString('o'); snapshotId = $taskManifest.snapshotId
    backendBuild = [ordered]@{ exitCode = 0; mainSourcesCompiled = 580; testSourcesCompiled = 188
        buildLogSha256 = File-Digest (Join-Path $taskRun 'backend-build-local-cache.log'); execution = 'Offline Maven package with skipTests; fresh empty output directory; no application launch' }
    backendUnitTests = [ordered]@{ exitCode = 0; tests = $totals.tests
        failures = $totals.failures; errors = $totals.errors
        skipped = $totals.skipped; suiteCount = $suites.Count; suites = $suites }
    frontendPlatform = Frontend-Summary 'frontend-platform-tests.json'
    frontendSafetyCompatibility = Frontend-Summary 'frontend-safety-compat-tests.json'
    faultSelector = [ordered]@{ exitCode = 0; tests = 7; passed = 7; failed = 0; skipped = 0; reportSha256 = File-Digest (Join-Path $taskRun 'logout-fault-selector.tap') }
    formalTypecheck = [ordered]@{ command = 'pnpm run typecheck'; processExitCode = 1; scriptExitCode = 2
        error = 'TS2769: frontend/src/test/addressLength.audit.test.tsx(23,68), exact is not a ByRoleOptions property'
        capturedFrom = 'Actual tool output in this task; not an inferred or historical result'; scopeChanged = $false }
    formalFrontendBuild = [ordered]@{ command = 'pnpm run build (tsc --noEmit && vite build)'; processExitCode = 1; scriptExitCode = 2
        blockedBy = 'Same addressLength.audit.test.tsx TS2769'; viteReached = $false; productionBundleProduced = $false; testExcluded = $false }
    priorFailedInvocations = @(
        [ordered]@{ step = 'First offline Maven package'; exitCode = 1; cause = 'Sandbox Java user.home used empty CodexSandboxOffline Maven cache'; compiled = $false },
        [ordered]@{ step = 'First Maven unit-test invocation'; exitCode = 1; cause = 'PowerShell split an unquoted -Dmaven.test.redirectTestOutputToFile argument'; testsExecuted = $false }
    )
    unchangedSnapshotFiles = $taskManifest.files.Count; changedSnapshotFiles = $snapshotChanges; changedWorkspaceSourceFiles = $workspaceChanges
    databaseStartedOrAccessed = $false; httpServiceStarted = $false; migrationsExecuted = $false; browserAcceptanceRepeated = $false
    currentWorkspaceFullPass = $false; historicalTestsAddedToCurrentCounts = $false
    untested = @('Database transaction/concurrency/constraints and migrations on new classes', 'Full manual invitation activation',
        'Current full frontend bundle', 'Production configuration, operations and scheduling', 'Full ERP branch compatibility/integration')
})
Write-Output ('Evidence collected for snapshot ' + $taskManifest.snapshotId)
Write-Output ('Backend suites/tests: ' + $suites.Count + '/' + $totals.tests)
Write-Output ('JAR class entries/dependencies/migrations: ' + $verifiedClassEntries + '/' + $runtimeDependencies.Count + '/' + $embeddedMigrations.Count)

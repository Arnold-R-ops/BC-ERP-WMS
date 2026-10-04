// Authorized F3 behavior guard, dependency recovery and frontend build. F1/F2 evidence stays unchanged.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { runProcess } from './platform-process-lifecycle.mjs';
import { assessVitestReport } from './platform-vitest-report.mjs';

const ROOT = 'D:/ERP_WMS/BC warehouse/2G';
const RUN = ROOT + '/target/platform-frontend-acceptance-20260919/f3';
const EVIDENCE = ROOT + '/docs/releases/platform-frontend-acceptance-20260919/f3';
const F1 = ROOT + '/docs/releases/platform-frontend-acceptance-20260919/f1';
const F2 = ROOT + '/docs/releases/platform-frontend-acceptance-20260919/f2';
const PROBE = RUN + '/missing-dependency-probe';
const PROBE_STORE = RUN + '/missing-dependency-store';
const ORIGINAL_STORE = 'D:/.pnpm-store';
const SOURCE = RUN + '/snapshot/frontend';
const NODE = 'D:/JAVA/NODE/node.exe';
const PNPM = ROOT + '/target/platform-validation-tools/pnpm-11.16.0/package/bin/pnpm.cjs';
const R11 = ROOT + '/docs/releases/platform-db-acceptance-20260919/r11';
const R11_BUILD = ROOT + '/target/platform-db-acceptance-20260919/r11/build/a1';
const BUDGETS = { version: 15000, collection: 180000, address: 180000, typecheck: 180000, install: 600000, build: 300000, platform: 300000 };
const ADDRESS = 'src/test/addressLength.audit.test.tsx';
const BEFORE = 'f7c75aaa8cb8896aea0d6bace0f59b7ba672b51d18111eecf4340603ad3ed44c';
const OLD_LINE = "fireEvent.click(screen.getByRole('button', { name: 'Save', exact: true }));";
const NEW_LINE = "fireEvent.click(screen.getByRole('button', { name: /^Save$/ }));";
const COUNTS = {
  'api.test.ts': 25, 'storage.test.ts': 2, 'browserLogout.test.ts': 7, 'sessionActivity.test.ts': 14,
  'mfaFeedback.test.ts': 3, 'platformAccessGrantStatus.test.ts': 3, 'PlatformAuthProvider.test.tsx': 9,
  'PlatformInvitationActivationPage.test.tsx': 3, 'PlatformAdminManagementPage.test.tsx': 5,
  'PlatformTenantManagementPage.test.tsx': 4, 'PlatformTenantAccessProvider.test.ts': 4,
  'TenantBrowser.test.ts': 3, 'TenantSecurityPanel.test.tsx': 4,
};
const normalize = p => p.replaceAll('\\', '/');
const sha = b => crypto.createHash('sha256').update(b).digest('hex');
const hash = p => sha(fs.readFileSync(p));
const json = p => JSON.parse(fs.readFileSync(p, 'utf8'));
const aggregate = rows => sha(Buffer.from(rows.map(r => r.path + '\0' + r.sha256 + '\n').join('')));
function write(p, value) { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' }); }
function files(root) {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap(e => {
    if (e.isSymbolicLink()) throw Error('Source links are not accepted: ' + e.name);
    const p = root + '/' + e.name;
    return e.isDirectory() ? files(p) : [p];
  }).sort();
}
function frontendRows(root) {
  const selected = ['package.json', 'pnpm-lock.yaml', 'tsconfig.json', 'vite.config.ts', 'vitest.config.ts', 'index.html', 'platform.html']
    .concat(...['src', 'public'].map(d => files(root + '/' + d).map(p => normalize(path.relative(root, p))))).sort();
  if (selected.some(p => /(^|\/)(private|\.env[^/]*)(\/|$)|\.(pem|pfx|key|log)$/i.test(p))) throw Error('Private or generated input requires review');
  return selected.map(p => ({ path: p, sha256: hash(root + '/' + p), bytes: fs.statSync(root + '/' + p).size }));
}
function assertSame(frontend, rows) {
  if (JSON.stringify(frontendRows(frontend)) !== JSON.stringify(rows)) throw Error('Frontend input drift: ' + frontend);
}
function backendCheck() {
  const m = json(R11 + '/backend-input-manifest.json'), b = json(R11 + '/backend-build.json');
  if (m.id !== 'f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62'
      || hash(R11 + '/backend-input-manifest.json') !== '6faefc5ab260931cbc1d378fbef826f82a4c3b1ef391dd62220b7c427aa9c228') throw Error('R11 manifest drift');
  const product = m.groups[0].files.filter(f => f.path === 'pom.xml' || f.path.startsWith('src/main/'));
  const inventory = [...new Set(execFileSync('git', ['--no-optional-locks', 'ls-files', '-c', '-o', '--exclude-standard', '-z'], { cwd: ROOT, encoding: 'utf8' }).split('\0'))]
    .filter(p => p === 'pom.xml' || p.startsWith('src/main/'))
    .filter(p => !/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
  if (JSON.stringify(inventory) !== JSON.stringify(product.map(f => f.path).sort())) throw Error('Backend product inventory drift');
  for (const f of product) if (hash(ROOT + '/' + f.path) !== f.sha256 || hash(m.groups[0].location + '/' + f.path) !== f.sha256) throw Error('Backend source drift: ' + f.path);
  const compiled = b.compiled.filter(f => f.path.startsWith('classes/'));
  for (const f of compiled) if (hash(R11_BUILD + '/' + f.path) !== f.sha256) throw Error('R11 product artifact drift: ' + f.path);
  if (hash(R11_BUILD + '/wms-system-0.0.1-SNAPSHOT.jar') !== b.jarSha256) throw Error('R11 jar drift');
  return { checkedAt: new Date().toISOString(), inputId: m.id, productFiles: product.length, productDifferences: [],
    compiledFiles: compiled.length, compiledDifferences: [], jar: R11_BUILD + '/wms-system-0.0.1-SNAPSHOT.jar', jarSha256: b.jarSha256,
    compiledAggregateSha256: b.compiledAggregateSha256, migrations: m.migrationFiles, databaseHistory: m.databaseHistory,
    backendTestsRepeated: false, note: 'R11 frozen backend acceptance reused only; not a complete release candidate.' };
}
function storeFor(cwd) {
  if (cwd === PROBE) return PROBE_STORE;
  return cwd === SOURCE ? RUN + '/dependency-store' : ORIGINAL_STORE;
}
function environment(cwd) {
  const env = {};
  for (const key of ['SystemRoot', 'WINDIR', 'COMSPEC', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA', 'TEMP', 'TMP', 'PATHEXT']) {
    const actual = Object.keys(process.env).find(k => k.toLowerCase() === key.toLowerCase());
    if (actual) env[key] = process.env[actual];
  }
  env.Path = path.dirname(NODE) + ';' + (process.env.Path ?? process.env.PATH ?? '');
  env.CI = 'true'; env.NO_COLOR = '1';
  env.npm_config_userconfig = RUN + '/tooling/empty.npmrc';
  env.npm_config_cache = RUN + '/dependency-cache';
  env.npm_config_store_dir = storeFor(cwd);
  env.pnpm_config_verify_deps_before_run = 'error';
  if (cwd === PROBE) env.npm_config_offline = 'true';
  return env;
}
async function command(name, cwd, args, budget) {
  const outputFile = EVIDENCE + '/' + name + '.log';
  if (['test', 'run', 'exec'].includes(args[0])) dependencies(cwd);
  if (args[0] === 'install' && !['workspace-restore', 'frozen-install'].includes(name)) throw Error('Installation requires a named independent step');
  const result = await runProcess(NODE, [PNPM, ...args], { cwd, env: environment(cwd), timeoutMs: budget, outputFile });
  const { output, ...receipt } = result;
  const valid = result.exited && result.exitCode === 0 && result.outputComplete && !result.spawnError && !result.timedOut && result.elapsedMs <= budget;
  write(EVIDENCE + '/' + name + '.command.json', { name, executable: NODE, args: [PNPM, ...args], cwd, budgetMs: budget,
    log: outputFile, logSha256: hash(outputFile), status: valid ? 'PASSED' : 'FAILED',
    verifyDepsBeforeRun: 'error', store: environment(cwd).npm_config_store_dir, ...receipt });
  console.log(JSON.stringify({ step: name, exitCode: result.exitCode, elapsedMs: result.elapsedMs, status: valid ? 'PASSED' : 'FAILED' }));
  if (!valid) throw Error('Command failed; no automatic retry: ' + name);
}
const flags = ['--no-cache', '--configLoader=runner'];
function summarize(report, expected, format = 'structured') {
  const summary = { reportSha256: hash(report), ...assessVitestReport(json(report), expected, format) };
  write(report.replace(/\.json$/, '.assessment.json'), summary);
  if (summary.status !== 'PASSED') throw Error('Test report mismatch/failure: ' + report);
  return summary;
}
function dependencies(cwd) {
  const entries = ['vitest/vitest.mjs', 'typescript/bin/tsc', 'vite/bin/vite.js', '.modules.yaml', '.pnpm/lock.yaml', '.pnpm-workspace-state-v1.json'];
  for (const p of entries) if (!fs.existsSync(cwd + '/node_modules/' + p)) throw Error('Dependency entry missing; implicit installation forbidden: ' + p);
  if (hash(cwd + '/pnpm-lock.yaml') !== hash(cwd + '/node_modules/.pnpm/lock.yaml')) throw Error('Installed lock mismatch; explicit install required');
  const expectedStore = normalize((cwd === SOURCE ? RUN + '/dependency-store' : ORIGINAL_STORE) + '/v11');
  const modules = fs.readFileSync(cwd + '/node_modules/.modules.yaml', 'utf8');
  const raw = /^\s*"?storeDir"?\s*:\s*(.+?)\s*,?\s*$/m.exec(modules)?.[1];
  const actualStore = raw?.startsWith('"') ? JSON.parse(raw) : raw;
  if (typeof actualStore !== 'string' || normalize(actualStore).toLowerCase() !== expectedStore.toLowerCase()) throw Error('Dependency store metadata mismatch');
  const versions = Object.fromEntries(['vitest', 'typescript', 'vite'].map(n => [n, json(cwd + '/node_modules/' + n + '/package.json').version]));
  if (versions.vitest !== '4.1.10' || versions.typescript !== '5.9.3' || versions.vite !== '8.1.4') throw Error('Locked entry version mismatch');
  return { checkedAt: new Date().toISOString(), storeDir: actualStore, versions,
    entries: entries.map(p => ({ path: p, sha256: hash(cwd + '/node_modules/' + p) })), lockSha256: hash(cwd + '/pnpm-lock.yaml') };
}
function addressExpected(cwd) {
  return ['customer', 'supplier'].flatMap(entity => ['A', '测'].flatMap(c => [254, 255, 256, 500].map(n => ({
    file: normalize(cwd + '/' + ADDRESS), ancestorTitles: ['address length audit, 2026-09-10'], title: `${entity} admits ${n} ${c} characters`,
  }))));
}
async function probe() {
  if (fs.existsSync(RUN) || fs.existsSync(EVIDENCE)) throw Error('F3 directory already exists; never overwrite');
  const backend = backendCheck();
  const updated = fs.readFileSync(ROOT + '/frontend/' + ADDRESS, 'utf8');
  if (updated.split(NEW_LINE).length !== 2 || sha(Buffer.from(updated.replace(NEW_LINE, OLD_LINE))) !== BEFORE) throw Error('Only authorized one-line edit is accepted');
  const receipt = json(ROOT + '/target/platform-validation-tools/pnpm-11.16.0/receipt.json');
  if (hash(NODE) !== receipt.nodeSha256 || hash(PNPM) !== receipt.cliSha256) throw Error('Fixed toolchain fingerprint mismatch');
  const rows = frontendRows(ROOT + '/frontend');
  assertSame(ROOT + '/frontend', json(F1 + '/precheck-input.json').files);
  fs.mkdirSync(RUN + '/tooling', { recursive: true });
  fs.writeFileSync(RUN + '/tooling/empty.npmrc', '', { flag: 'wx' });
  write(EVIDENCE + '/plan.json', { createdAt: new Date().toISOString(), budgets: BUDGETS, frontend: SOURCE,
    node: '24.12.0', pnpm: '11.16.0', toolchainReceipt: receipt, serviceStartupAuthorized: false, awakeAuthorized: false,
    build: 'tsc --noEmit && vite build', fullBuildEntries: ['index.html', 'platform.html'], platformSourceCaseCounts: COUNTS,
    testsMayNotBeExcludedOrSkipped: true, originalStore: ORIGINAL_STORE + '/v11', verifyDepsBeforeRun: 'error',
    originalStoreEvidence: 'F1 pre-interruption .modules.yaml readback: D:\\.pnpm-store\\v11; no current explicit store override.',
    toolSha256: hash(ROOT + '/src/test/scripts/platform-frontend-f1-build.mjs'),
    previousToolSha256: '0d18a34d12f1fdc9654e42bcf615e00fd6724981d5e9a9f5944aae4f0e1d4f15',
    syntheticProbe: { root: PROBE, store: PROBE_STORE, offline: true, dependency: 'file:./local-dependency',
      expectedExitCode: 1, expectedErrorCode: 'ERR_PNPM_VERIFY_DEPS_BEFORE_RUN', budgetMs: BUDGETS.version } });
  write(EVIDENCE + '/preserved-f1.json', files(F1).map(p => ({ path: normalize(path.relative(F1, p)), sha256: hash(p) })));
  write(EVIDENCE + '/preserved-f2.json', files(F2).map(p => ({ path: normalize(path.relative(F2, p)), sha256: hash(p) })));
  write(EVIDENCE + '/backend-before.json', backend);
  write(EVIDENCE + '/precheck-input.json', { id: aggregate(rows), files: rows, addressBeforeSha256: BEFORE,
    addressAfterSha256: hash(ROOT + '/frontend/' + ADDRESS), exactOneLineChange: { old: OLD_LINE, new: NEW_LINE },
    dependencies: 'Workspace inputs recorded before explicit recovery; restoration and validation results are separate.' });
  await command('pnpm-version', ROOT, ['--version'], BUDGETS.version);
  if (fs.readFileSync(EVIDENCE + '/pnpm-version.log', 'utf8').trim() !== '11.16.0') throw Error('pnpm version mismatch');
  // Valid, never-installed local project. No workspace dependency or shared store is used.
  write(PROBE + '/package.json', { name: 'platform-f3-no-install-probe', version: '1.0.0', private: true,
    packageManager: 'pnpm@11.16.0', scripts: { probe: 'node script-must-not-run.cjs' },
    dependencies: { 'platform-f3-local-dependency': 'file:./local-dependency' } });
  write(PROBE + '/local-dependency/package.json', { name: 'platform-f3-local-dependency', version: '1.0.0', main: 'index.cjs' });
  fs.writeFileSync(PROBE + '/local-dependency/index.cjs', 'module.exports = 1;\n', { flag: 'wx' });
  fs.writeFileSync(PROBE + '/script-must-not-run.cjs', "require('node:fs').writeFileSync('SCRIPT_EXECUTED', 'unexpected', {flag:'wx'});\n", { flag: 'wx' });
  // Local workspace config binds only this synthetic project, no public/global configuration writes.
  fs.writeFileSync(PROBE + '/pnpm-workspace.yaml',
    "packages:\n  - '.'\noffline: true\nstoreDir: " + JSON.stringify(PROBE_STORE) + '\ncacheDir: ' + JSON.stringify(RUN + '/missing-dependency-cache') + '\n', { flag: 'wx' });
  fs.mkdirSync(PROBE_STORE);
  const before = files(PROBE).map(p => ({ path: normalize(path.relative(PROBE, p)), sha256: hash(p) }));
  const outputFile = EVIDENCE + '/no-implicit-install-probe.log';
  const result = await runProcess(NODE, [PNPM, 'run', 'probe'], { cwd: PROBE, env: environment(PROBE), timeoutMs: BUDGETS.version, outputFile });
  const { output, ...receiptData } = result;
  const log = fs.readFileSync(outputFile, 'utf8');
  const after = files(PROBE).map(p => ({ path: normalize(path.relative(PROBE, p)), sha256: hash(p) }));
  const expectedError = log.includes('ERR_PNPM_VERIFY_DEPS_BEFORE_RUN');
  const scriptExecuted = fs.existsSync(PROBE + '/SCRIPT_EXECUTED');
  const installed = fs.existsSync(PROBE + '/node_modules') || files(PROBE_STORE).length !== 0;
  const passed = result.exited && result.exitCode === 1 && result.outputComplete && !result.spawnError && !result.timedOut
    && result.elapsedMs <= BUDGETS.version && expectedError && !scriptExecuted && !installed && JSON.stringify(before) === JSON.stringify(after);
  write(EVIDENCE + '/no-implicit-install-probe.json', { status: passed ? 'PASSED_EXPECTED_REJECTION' : 'FAILED',
    executable: NODE, args: [PNPM, 'run', 'probe'], cwd: PROBE, envOverride: { pnpm_config_verify_deps_before_run: 'error' },
    expectedExitCode: 1, expectedErrorCode: 'ERR_PNPM_VERIFY_DEPS_BEFORE_RUN', expectedErrorObserved: expectedError,
    scriptExecuted, dependenciesInstalled: installed, independentStore: PROBE_STORE, offlineConfig: true,
    logSha256: hash(outputFile), before, after, ...receiptData });
  console.log(JSON.stringify({ step: 'no-implicit-install-probe', status: passed ? 'PASSED_EXPECTED_REJECTION' : 'FAILED', exitCode: result.exitCode }));
  if (!passed) throw Error('Synthetic no-install behavior did not meet expected rejection contract');
}
async function restore() {
  if (json(EVIDENCE + '/no-implicit-install-probe.json').status !== 'PASSED_EXPECTED_REJECTION') throw Error('Behavior probe must pass before installation');
  const rows = json(EVIDENCE + '/precheck-input.json').files;
  assertSame(ROOT + '/frontend', rows);
  if (!fs.existsSync(ORIGINAL_STORE + '/v11')) throw Error('Original store missing');
  await command('workspace-restore', ROOT + '/frontend', ['install', '--frozen-lockfile', '--store-dir=' + ORIGINAL_STORE,
    '--cache-dir=' + RUN + '/dependency-cache', '--fetch-retries=0'], BUDGETS.install);
  const installed = dependencies(ROOT + '/frontend');
  assertSame(ROOT + '/frontend', rows);
  write(EVIDENCE + '/dependency-restoration-result.json', { status: 'PASSED', sourceId: aggregate(rows), installed,
    sourceOrLockChanged: false, testsExecuted: false, sharedStorePruned: false, otherProjectDependenciesChanged: false });
}
async function precheck() {
  if (json(EVIDENCE + '/dependency-restoration-result.json').status !== 'PASSED') throw Error('Dependency restoration must be verified first');
  const rows = json(EVIDENCE + '/precheck-input.json').files;
  assertSame(ROOT + '/frontend', rows);
  dependencies(ROOT + '/frontend');
  const expected = addressExpected(ROOT + '/frontend');
  write(EVIDENCE + '/precheck-address-expected.json', expected);
  await command('precheck-address', ROOT + '/frontend', ['test', ADDRESS, ...flags, '--reporter=default', '--reporter=json', '--outputFile.json=' + EVIDENCE + '/precheck-address.json'], BUDGETS.address);
  const address = summarize(EVIDENCE + '/precheck-address.json', expected);
  assertSame(ROOT + '/frontend', rows);
  await command('precheck-typecheck', ROOT + '/frontend', ['run', 'typecheck'], BUDGETS.typecheck);
  assertSame(ROOT + '/frontend', rows);
  write(EVIDENCE + '/precheck-result.json', { status: 'PASSED', sourceId: aggregate(rows), address, typecheck: 'PASSED', databaseOrServicesStarted: false });
}
async function frozenBuild() {
  if (json(EVIDENCE + '/precheck-result.json').status !== 'PASSED') throw Error('Prechecks must pass');
  if (fs.existsSync(RUN + '/snapshot')) throw Error('Never reuse a frontend snapshot');
  const prior = json(EVIDENCE + '/precheck-input.json');
  assertSame(ROOT + '/frontend', prior.files);
  write(EVIDENCE + '/backend-at-freeze.json', backendCheck());
  for (const f of prior.files) {
    fs.mkdirSync(path.dirname(SOURCE + '/' + f.path), { recursive: true });
    fs.copyFileSync(ROOT + '/frontend/' + f.path, SOURCE + '/' + f.path, fs.constants.COPYFILE_EXCL);
  }
  assertSame(SOURCE, prior.files); assertSame(ROOT + '/frontend', prior.files);
  const toolFiles = ['src/test/scripts/platform-frontend-f1-build.mjs', 'src/test/scripts/platform-process-lifecycle.mjs', 'src/test/scripts/platform-vitest-report.mjs'].map(p => ({ path: p, sha256: hash(ROOT + '/' + p) }));
  for (const f of toolFiles) { const dest = RUN + '/snapshot/tools/' + path.basename(f.path); fs.mkdirSync(path.dirname(dest), { recursive: true }); fs.copyFileSync(ROOT + '/' + f.path, dest, fs.constants.COPYFILE_EXCL); }
  write(EVIDENCE + '/source-manifest.json', { id: aggregate(prior.files), frozenAt: new Date().toISOString(), sourceRoot: SOURCE,
    files: prior.files, tools: toolFiles, branch: execFileSync('git', ['branch', '--show-current'], { cwd: ROOT, encoding: 'utf8' }).trim(),
    commit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: ROOT, encoding: 'utf8' }).trim(),
    sourceIsUncommittedWorkingSnapshot: true, excludedMaterials: ['.env and private authentication files', 'node_modules', 'dist', 'logs', 'target'],
    backendInputId: json(EVIDENCE + '/backend-before.json').inputId });
  const workspaceDependencyBefore = dependencies(ROOT + '/frontend');
  write(EVIDENCE + '/workspace-dependencies-before-frozen-install.json', workspaceDependencyBefore);
  await command('frozen-install', SOURCE, ['install', '--frozen-lockfile', '--store-dir=' + RUN + '/dependency-store', '--cache-dir=' + RUN + '/dependency-cache', '--fetch-retries=0'], BUDGETS.install);
  assertSame(SOURCE, prior.files);
  if (hash(SOURCE + '/node_modules/.pnpm/lock.yaml') !== prior.files.find(f => f.path === 'pnpm-lock.yaml').sha256) throw Error('Frozen installed dependency lock mismatch');
  write(EVIDENCE + '/installed-dependencies.json', { lockSha256: hash(SOURCE + '/node_modules/.pnpm/lock.yaml'),
    moduleMetadataSha256: hash(SOURCE + '/node_modules/.modules.yaml'), frozenLockfile: true, store: RUN + '/dependency-store',
    packages: fs.readdirSync(SOURCE + '/node_modules/.pnpm').filter(n => !['node_modules', 'lock.yaml'].includes(n)).sort() });
  await command('frozen-typecheck', SOURCE, ['run', 'typecheck'], BUDGETS.typecheck);
  await command('frozen-build', SOURCE, ['run', 'build'], BUDGETS.build);
  assertSame(SOURCE, prior.files);
  const dist = files(SOURCE + '/dist').map(p => ({ path: normalize(path.relative(SOURCE + '/dist', p)), sha256: hash(p), bytes: fs.statSync(p).size }));
  if (!dist.some(f => f.path === 'index.html') || !dist.some(f => f.path === 'platform.html')) throw Error('Full build entry missing');
  write(EVIDENCE + '/artifact-manifest.json', { sourceId: prior.id, root: SOURCE + '/dist', aggregateSha256: aggregate(dist), files: dist,
    includesCompanyAndPlatform: true, sourceMapsIncludedNotApprovedForPublicServing: true, servicesStarted: false });
  const platformFiles = Object.keys(COUNTS).sort().map(n => 'src/platform/' + n);
  const actualFiles = fs.readdirSync(SOURCE + '/src/platform').filter(n => /\.test\.(ts|tsx)$/.test(n)).sort();
  if (JSON.stringify(actualFiles) !== JSON.stringify(Object.keys(COUNTS).sort())) throw Error('Platform test file inventory changed');
  await command('frozen-platform-collection', SOURCE, ['exec', 'vitest', 'list', ...platformFiles, ...flags, '--json=' + EVIDENCE + '/platform-collected.json'], BUDGETS.collection);
  const collected = json(EVIDENCE + '/platform-collected.json');
  const expected = collected.map(t => ({ file: normalize(t.file), name: t.name }));
  for (const [file, count] of Object.entries(COUNTS)) if (expected.filter(t => t.file.toLowerCase() === (SOURCE + '/src/platform/' + file).toLowerCase()).length !== count) throw Error('Source collection count mismatch: ' + file);
  if (new Set(expected.map(t => t.file + '\0' + t.name)).size !== expected.length) throw Error('Duplicate collected names');
  write(EVIDENCE + '/platform-expected.json', { sourceId: prior.id, method: 'Vitest source collection before execution; counts independently checked against source declarations, not execution results.', cases: expected });
  await command('frozen-platform', SOURCE, ['test', ...platformFiles, ...flags, '--reporter=default', '--reporter=json', '--outputFile.json=' + EVIDENCE + '/platform.json'], BUDGETS.platform);
  const platform = summarize(EVIDENCE + '/platform.json', expected, 'collection');
  const addressExpectedCases = addressExpected(SOURCE);
  write(EVIDENCE + '/frozen-address-expected.json', addressExpectedCases);
  await command('frozen-address', SOURCE, ['test', ADDRESS, ...flags, '--reporter=default', '--reporter=json', '--outputFile.json=' + EVIDENCE + '/frozen-address.json'], BUDGETS.address);
  const address = summarize(EVIDENCE + '/frozen-address.json', addressExpectedCases);
  assertSame(SOURCE, prior.files); assertSame(ROOT + '/frontend', prior.files);
  const workspaceDependencyAfter = dependencies(ROOT + '/frontend');
  if (JSON.stringify(workspaceDependencyAfter.entries) !== JSON.stringify(workspaceDependencyBefore.entries)) throw Error('Workspace dependency metadata changed during snapshot validation');
  write(EVIDENCE + '/workspace-dependencies-after-build.json', workspaceDependencyAfter);
  for (const f of json(EVIDENCE + '/preserved-f1.json')) if (hash(F1 + '/' + f.path) !== f.sha256) throw Error('F1 historical evidence changed');
  for (const f of json(EVIDENCE + '/preserved-f2.json')) if (hash(F2 + '/' + f.path) !== f.sha256) throw Error('F2 historical evidence changed');
  write(EVIDENCE + '/backend-after.json', backendCheck());
  write(EVIDENCE + '/build-result.json', { status: 'PASSED', completedAt: new Date().toISOString(), sourceId: prior.id, distAggregateSha256: aggregate(dist),
    platform, address, typecheck: 'PASSED', wholeFrontendBuild: 'PASSED', runtimeTestsOutsideSelectedFiles: 'NOT_EXECUTED',
    databaseOrServicesStarted: false, awakeRequested: false, invitationBrowserAcceptance: 'PENDING_SEPARATE_STARTUP_APPROVAL', releaseCandidateComplete: false });
}
export { ROOT, RUN, SOURCE, EVIDENCE, NODE, PNPM, BUDGETS, hash, json, files, aggregate, frontendRows, assertSame, backendCheck, environment, dependencies, addressExpected };

// Importing read-only helpers from the supplement entry cannot invoke installation/build.
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
 if (normalize(process.cwd()).toLowerCase() !== ROOT.toLowerCase() || process.version !== 'v24.12.0') throw Error('Fixed root and Node required');
 try {
  if (process.argv[2] === 'probe') await probe();
  else if (process.argv[2] === 'restore') await restore();
  else if (process.argv[2] === 'precheck') await precheck();
  else if (process.argv[2] === 'build') await frozenBuild();
  else throw Error('Only probe/restore/precheck/build are accepted');
 } catch (error) {
  if (fs.existsSync(EVIDENCE)) {
    const dest = EVIDENCE + '/' + process.argv[2] + '-failure.json';
    if (!fs.existsSync(dest)) write(dest, { status: 'FAILED', at: new Date().toISOString(), name: error.name, message: error.message,
      databaseOrServicesStarted: false, automaticRetry: false });
  }
  console.error(error.message); process.exitCode = 1;
 }
}

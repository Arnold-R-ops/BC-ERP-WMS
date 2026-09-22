// F3 supplement only: no install, build, platform test rerun, database or service entry point.
import fs from 'node:fs';
import path from 'node:path';
import { ROOT, RUN, SOURCE, EVIDENCE, NODE, PNPM, BUDGETS, hash, json, files, aggregate,
  assertSame, backendCheck, environment, dependencies, addressExpected } from './platform-frontend-f1-build.mjs';
import { assessVitestReport } from './platform-vitest-report.mjs';
import { runProcess } from './platform-process-lifecycle.mjs';

const OUT = ROOT + '/docs/releases/platform-frontend-acceptance-20260919/f3-closeout';
const WORK = ROOT + '/target/platform-frontend-acceptance-20260919/f3-closeout';
const TOOL_NAMES = ['platform-frontend-f3-closeout.mjs', 'platform-frontend-f1-build.mjs',
  'platform-vitest-report.mjs', 'platform-vitest-report.test.mjs', 'platform-process-lifecycle.mjs'];
const LIMITS = { syntax: 15000, contracts: 60000, address: BUDGETS.address };
const PINS = {
  'source-manifest.json': '31219a48d73dab66d83c138f0bca01941587bbfe6c0d60d541939193e8a80855',
  'artifact-manifest.json': 'ce58f1ec36b230ba6db42b000cf0ff71b4d41f8a7f1e11b437c1b776437c0801',
  'platform.json': '56000f930e40ca4538192d97ed91735b5fd9131e0528e3d10fd2b6a4aa7465c9',
  'platform-collected.json': '51faa4e1a90f0ef0bed2aa18c86ea4252e569d43ba4daa6b348f9a9d9d155c9b',
  'platform-expected.json': '8069aff6d45294e34dc9aed4edd997a6b30262c57d949d70b8b0c58cfcf3a25b',
  'platform.assessment.json': 'c20e30a6e6158f8c16975f11f3564def23f705a9577b075909b161b310b69e0c',
  'build-failure.json': 'd6d8dae5e69f31f8c85ae286f3e5b8328d9b2cc708d71aafa0e31b07fe5f3e17',
  'frozen-platform.command.json': '801c73deccb6e0e65af3e802738362605f45df9c48145b5d61c33745ee5f73f6',
  'frozen-build.command.json': 'e52d462b2a630b09dfbd87d7287da2fda73dd76e9882f587bee5c30509c6a3ac',
  'frozen-typecheck.command.json': '9c422bb63494230a61a17fe543d91e05c84884f5129e8bb3fb4c97c3c48c82bf',
  'dependency-restoration-result.json': '20b077f2e03b7d972f7f2d4af00d8878535b8ed1e595aea9cd6cbe7c0e55d39f',
};
const norm = p => p.replaceAll('\\', '/');
const write = (name, value) => fs.writeFileSync(OUT + '/' + name, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' });
const rows = root => files(root).map(p => ({ path: norm(path.relative(root, p)), sha256: hash(p), bytes: fs.statSync(p).size }));
const same = (a, b, label) => { if (JSON.stringify(a) !== JSON.stringify(b)) throw Error(label + ' drift'); };
const validCommand = r => r.exited && r.exitCode === 0 && r.outputComplete && r.terminationConfirmed
  && !r.spawnError && !r.timedOut && !r.aborted && r.elapsedMs <= r.budgetMs;
let step = 'preflight', created = false;

function checkInputs() {
  for (const [p, digest] of Object.entries(PINS)) if (hash(EVIDENCE + '/' + p) !== digest) throw Error('Historical F3 fingerprint mismatch: ' + p);
  const manifest = json(EVIDENCE + '/source-manifest.json');
  if (manifest.id !== 'ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d'
    || aggregate(manifest.files) !== manifest.id) throw Error('F3 source manifest ID mismatch');
  assertSame(SOURCE, manifest.files); assertSame(ROOT + '/frontend', manifest.files);
  for (const t of manifest.tools) if (hash(RUN + '/snapshot/tools/' + path.basename(t.path)) !== t.sha256) throw Error('Original frozen F3 tool drift');
  const artifact = json(EVIDENCE + '/artifact-manifest.json'), dist = rows(SOURCE + '/dist');
  same(dist, artifact.files, 'F3 dist files');
  if (artifact.sourceId !== manifest.id || aggregate(dist) !== artifact.aggregateSha256) throw Error('F3 source/artifact pairing mismatch');
  const frozen = dependencies(SOURCE), workspace = dependencies(ROOT + '/frontend');
  const installed = json(EVIDENCE + '/installed-dependencies.json');
  if (hash(SOURCE + '/node_modules/.modules.yaml') !== installed.moduleMetadataSha256
    || frozen.lockSha256 !== installed.lockSha256) throw Error('Original installed metadata mismatch');
  same(fs.readdirSync(SOURCE + '/node_modules/.pnpm').filter(n => !['node_modules', 'lock.yaml'].includes(n)).sort(), installed.packages, 'Frozen package inventory');
  const originalWorkspace = json(EVIDENCE + '/workspace-dependencies-before-frozen-install.json');
  same(workspace.entries, originalWorkspace.entries, 'Original workspace dependencies');
  for (const e of frozen.entries.slice(0, 3)) if (e.sha256 !== originalWorkspace.entries.find(w => w.path === e.path)?.sha256) throw Error('Frozen executable entry mismatch: ' + e.path);
  for (const n of ['f1', 'f2']) for (const p of json(EVIDENCE + '/preserved-' + n + '.json')) {
    if (hash(path.dirname(EVIDENCE) + '/' + n + '/' + p.path) !== p.sha256) throw Error('Historical ' + n + ' evidence drift');
  }
  const receipt = json(ROOT + '/target/platform-validation-tools/pnpm-11.16.0/receipt.json');
  if (process.version !== 'v24.12.0' || hash(NODE) !== receipt.nodeSha256 || hash(PNPM) !== receipt.cliSha256
    || hash(path.dirname(PNPM) + '/../dist/pnpm.mjs') !== '6d1d488e4ff9371a32cf4d322a01a8f6998e31b9c97342a77ca2bb179cf3234f') throw Error('Fixed toolchain mismatch');
  const backend = backendCheck();
  const historicalCommands = files(EVIDENCE).filter(p => p.endsWith('.command.json')).map(p => {
    const r = json(p);
    if (!validCommand(r) || r.status !== 'PASSED' || hash(r.log) !== r.logSha256) throw Error('Original command receipt/log mismatch: ' + r.name);
    return { name: r.name, exitCode: r.exitCode, elapsedMs: r.elapsedMs, receiptSha256: hash(p), logSha256: r.logSha256, repeated: false };
  });
  return { sourceId: manifest.id, sourceFiles: manifest.files.length, distId: artifact.aggregateSha256, distFiles: dist.length,
    lockSha256: frozen.lockSha256, frozenDependencies: frozen, workspaceDependencies: workspace, backend,
    historicalCommands, originalF3Evidence: rows(EVIDENCE),
    originalF1Evidence: rows(path.dirname(EVIDENCE) + '/f1'), originalF2Evidence: rows(path.dirname(EVIDENCE) + '/f2'),
    toolchain: { node: process.version, nodeSha256: hash(NODE), pnpm: '11.16.0', pnpmSha256: hash(PNPM), bundleSha256: hash(path.dirname(PNPM) + '/../dist/pnpm.mjs') } };
}
async function command(name, args, cwd, budget) {
  step = name;
  const log = OUT + '/' + name + '.log';
  const result = await runProcess(NODE, args, { cwd, env: environment(cwd), timeoutMs: budget, outputFile: log });
  const { output, ...receipt } = result;
  const r = { name, executable: NODE, args, cwd, budgetMs: budget, ...receipt,
    log, logSha256: hash(log), verifyDepsBeforeRun: 'error' };
  r.status = validCommand(r) ? 'PASSED' : 'FAILED';
  write(name + '.command.json', r);
  console.log(JSON.stringify({ step, status: r.status, exitCode: r.exitCode, elapsedMs: r.elapsedMs }));
  if (r.status !== 'PASSED') throw Error('Command failed; no automatic retry: ' + name);
  return r;
}

try {
  if (norm(process.cwd()).toLowerCase() !== ROOT.toLowerCase() || process.argv.length !== 2) throw Error('Only fixed-root supplement execution is permitted');
  if (fs.existsSync(OUT) || fs.existsSync(WORK)) throw Error('Supplement directory exists; never overwrite');
  const before = checkInputs();
  const toolRows = TOOL_NAMES.map(name => ({ path: name, sha256: hash(ROOT + '/src/test/scripts/' + name) }));
  const contractNames = [...fs.readFileSync(ROOT + '/src/test/scripts/platform-vitest-report.test.mjs', 'utf8').matchAll(/^test\('([^']+)',/gm)].map(m => m[1]);
  if (contractNames.length !== 25 || new Set(contractNames).size !== 25) throw Error('Focused contract inventory mismatch');
  fs.mkdirSync(OUT, { recursive: true }); created = true;
  fs.mkdirSync(WORK + '/tools', { recursive: true });
  for (const t of toolRows) fs.copyFileSync(ROOT + '/src/test/scripts/' + t.path, WORK + '/tools/' + t.path, fs.constants.COPYFILE_EXCL);
  write('input-before.json', before);
  write('plan.json', { createdAt: new Date().toISOString(), budgets: LIMITS, sourceRoot: SOURCE, originalEvidence: EVIDENCE,
    tools: toolRows, toolsId: aggregate(toolRows), contractNames, contractCount: 25, addressCount: 16,
    originalPlatformCasesReadOnly: 86, neverInvoked: ['install', 'typecheck', 'build', 'platform test run', 'r11', 'database', 'services', 'awake'],
    dependencyStateNote: 'F3 frozen workspace-state file has no separate original recorded hash; pre/post supplement comparison only. Original modules/lock/package inventory and entry hashes are also checked.' });
  for (const name of TOOL_NAMES) await command('syntax-' + name, ['--check', WORK + '/tools/' + name], ROOT, LIMITS.syntax);
  await command('adapter-contracts', ['--test', '--test-reporter=tap', WORK + '/tools/platform-vitest-report.test.mjs'], ROOT, LIMITS.contracts);
  const tap = fs.readFileSync(OUT + '/adapter-contracts.log', 'utf8');
  const tapNames = [...tap.matchAll(/^# Subtest: (.+)$/gm)].map(m => m[1].trimEnd());
  same(tapNames, contractNames, 'Contract executed names');
  for (const [key, value] of Object.entries({ tests: 25, pass: 25, fail: 0, cancelled: 0, skipped: 0, todo: 0 })) {
    if (Number(new RegExp('^# ' + key + ' (\\d+)\\r?$', 'm').exec(tap)?.[1]) !== value) throw Error('Contract TAP count mismatch: ' + key);
  }
  write('adapter-contracts.assessment.json', { status: 'PASSED', expected: 25, passed: 25, skipped: 0, failed: 0, names: tapNames });
  step = 'original-platform-report-reassessment';
  const expected = json(EVIDENCE + '/platform-expected.json');
  if (expected.sourceId !== before.sourceId || expected.cases.length !== 86) throw Error('Original expectation source mismatch');
  same(json(EVIDENCE + '/platform-collected.json').map(t => ({ file: norm(t.file), name: t.name })), expected.cases, 'Pre-execution collection');
  const platform = assessVitestReport(json(EVIDENCE + '/platform.json'), expected.cases, 'collection');
  write('original-platform.reassessment.json', { ...platform, reportSha256: PINS['platform.json'],
    expectationSha256: PINS['platform-expected.json'], originalCommandSha256: PINS['frozen-platform.command.json'],
    originalCommandExitCode: 0, rerun: false, originalBatchStatus: 'FAILED_PRESERVED' });
  if (platform.status !== 'PASSED' || platform.passed !== 86) throw Error('Original platform report reassessment failed');
  console.log(JSON.stringify({ step, status: platform.status, passed: platform.passed, rerun: false }));
  const addressCases = addressExpected(SOURCE);
  write('frozen-address-expected.json', addressCases);
  dependencies(SOURCE);
  await command('frozen-address', [PNPM, 'test', 'src/test/addressLength.audit.test.tsx', '--no-cache', '--configLoader=runner',
    '--reporter=default', '--reporter=json', '--outputFile.json=' + OUT + '/frozen-address.json'], SOURCE, LIMITS.address);
  const address = assessVitestReport(json(OUT + '/frozen-address.json'), addressCases);
  write('frozen-address.assessment.json', { ...address, reportSha256: hash(OUT + '/frozen-address.json') });
  if (address.status !== 'PASSED' || address.passed !== 16) throw Error('New frozen address report failed');
  step = 'final-fingerprints';
  const after = checkInputs();
  for (const key of ['originalF3Evidence', 'originalF1Evidence', 'originalF2Evidence', 'historicalCommands', 'toolchain']) same(after[key], before[key], key);
  same(after.frozenDependencies.entries, before.frozenDependencies.entries, 'Frozen dependencies during supplement');
  same(after.workspaceDependencies.entries, before.workspaceDependencies.entries, 'Workspace dependencies during supplement');
  for (const t of toolRows) if (hash(ROOT + '/src/test/scripts/' + t.path) !== t.sha256 || hash(WORK + '/tools/' + t.path) !== t.sha256) throw Error('Supplement tool drift');
  write('input-after.json', after);
  write('closeout-result.json', { status: 'PASSED_SUPPLEMENTAL_CLOSEOUT', completedAt: new Date().toISOString(),
    sourceId: before.sourceId, distAggregateSha256: before.distId, toolSnapshotId: aggregate(toolRows),
    originalF3Status: 'FAILED_UNMODIFIED', historicalCommands: before.historicalCommands,
    performedNow: { syntaxChecks: TOOL_NAMES.length, reportContractTests: 25, readOnlyPlatformReassessment: platform, frozenAddressTests: address },
    fingerprintDifferences: 0, backendInputId: after.backend.inputId, r11Reexecuted: false,
    remaining: ['invitation full browser activation', 'unselected runtime tests', 'final recovery reconciliation business decision', 'release integration and production readiness'],
    servicesStarted: false, databaseConnected: false, awakeRequested: false, releaseCandidateComplete: false, productionApproved: false });
  console.log(JSON.stringify({ status: 'PASSED_SUPPLEMENTAL_CLOSEOUT', reportContracts: 25, platformReassessed: 86, addressExecuted: 16, evidence: OUT }));
} catch (error) {
  const failure = { status: 'FAILED', step, at: new Date().toISOString(), type: error.name, message: error.message,
    automaticRetry: false, historicalEvidenceRewritten: false, servicesStarted: false, databaseConnected: false };
  if (created) write('closeout-failure.json', failure);
  console.error(JSON.stringify(failure)); process.exitCode = 1;
}

// Platform delivery traceability only: no product edits, database, service, git writes or network.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const root = 'D:/ERP_WMS/BC warehouse/2G';
const run = path.join(root, 'target/platform-release-baseline-20260911/r1');
const source = path.join(run, 'source');
const delivery = path.join(root, 'docs/releases/platform-baseline-20260911');
const normalize = p => p.replaceAll('\\', '/');
if (normalize(process.cwd()).toLowerCase() !== root.toLowerCase()) throw new Error('Run only in the fixed platform workspace');
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const read = p => fs.readFileSync(p);
const git = (...args) => execFileSync('git', ['--no-optional-locks', '-c', 'core.quotepath=false', ...args], { cwd: root, encoding: 'utf8' });
const writeNew = (p, value) => {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, `${JSON.stringify(value, null, 2)}\n`, { flag: 'wx' });
};
const files = () => [...new Set(git('ls-files', '-c', '-o', '--exclude-standard', '-z').split('\0').filter(Boolean))];
const aggregate = records => sha(Buffer.from(records.map(r => `${r.path}\0${r.sha256}\n`).join('')));
const packageInputs = new Set(['pom.xml', 'frontend/package.json', 'frontend/pnpm-lock.yaml', 'frontend/tsconfig.json',
  'frontend/vite.config.ts', 'frontend/vitest.config.ts', 'frontend/index.html', 'frontend/platform.html']);
const isInput = p => packageInputs.has(p) || /^(src\/(main|test)\/|frontend\/(src|public)\/)/.test(p);
const excluded = p => /(^|\/)(\.env[^/]*|application-local\.[^/]*|node_modules|target|private)(\/|$)/i.test(p)
  || /\.(class|jar|log|p12|pfx|jks|pem|key)$/i.test(p);
const sessionNames = /Platform(Session|MfaService|JwtClaims)|JwtProperties|sessionActivity/;
const browserNames = /PlatformBrowser|browserLogout/;
function ownership(p) {
  if (p === 'frontend/src/test/addressLength.audit.test.tsx' || p.startsWith('outputs/')
      || /docs\/(SALESBINDER|FONGVO|TENANT_ERP|DELIVERY_IMPROVEMENT)/.test(p)) return { groups: [], scope: 'outside-platform-preserve' };
  if (/src\/main\/resources\/db\/migration\/V4_(85|86|87|88)__/.test(p)) return { groups: ['P3-M1'], scope: 'platform-migration' };
  if (/src\/main\/resources\/db\/migration\/V4_89__/.test(p)) return { groups: ['platform-session'], scope: 'platform-migration' };
  if (/src\/main\/resources\/db\/migration\/V4_90__/.test(p)) return { groups: ['browser-logout'], scope: 'platform-migration' };
  if (p.startsWith('docs/')) return { groups: ['evidence-tools'], scope: /PHASE[345]|DETAILED_DESIGN|PRODUCTION_OPERATIONS/.test(p) ? 'reference-only-not-feature-authorization' : 'documentation' };
  if (/src\/test\/(scripts|java\/com\/wms\/browseracceptance|resources)/.test(p) || p === 'pom.xml')
    return { groups: ['evidence-tools'], scope: p === 'pom.xml' ? 'shared-build-hunk-review' : 'test-tool' };
  if (/frontend\/src\/platform\/(api|storage|Platform(AuthProvider|App|InvitationActivationPage))/.test(p))
    return { groups: ['P3-M1', 'platform-session', 'browser-logout'], scope: 'platform-mixed-hunks' };
  if (browserNames.test(p)) return { groups: ['browser-logout'], scope: p.includes('/test/') || p.includes('.test.') ? 'platform-test' : 'platform-product' };
  if (sessionNames.test(p)) return { groups: ['platform-session', 'browser-logout'], scope: p.includes('/test/') || p.includes('.test.') ? 'platform-test' : 'platform-product' };
  if (p.includes('/platform/') || p.includes('/tenant/security/') || p.includes('/tenant/web/') || p.endsWith('/TenantSecurityProperties.java'))
    return { groups: ['P3-M1'], scope: p.includes('/test/') || p.includes('.test.') ? 'platform-test' : 'platform-product' };
  if (p.startsWith('src/') || p.startsWith('frontend/src/')) return { groups: ['P3-M1'], scope: 'existing-shared-safety-hunks-review-required' };
  return { groups: [], scope: 'unclassified-preserve' };
}

function prepare() {
  if (fs.existsSync(run) || fs.existsSync(delivery)) throw new Error('Baseline output already exists; never overwrite evidence');
  const selected = files().filter(p => isInput(p) && !excluded(p)).sort();
  const records = [];
  for (const p of selected) {
    const original = path.join(root, p);
    if (!fs.lstatSync(original).isFile()) throw new Error(`Non-file source input: ${p}`);
    const bytes = read(original);
    const destination = path.join(source, p);
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.writeFileSync(destination, bytes, { flag: 'wx' });
    const hash = sha(bytes);
    if (sha(read(destination)) !== hash || sha(read(original)) !== hash) throw new Error(`Source changed while copied: ${p}`);
    records.push({ path: p, bytes: bytes.length, sha256: hash });
  }
  const status = git('status', '--porcelain=v1', '-uall', '-z').split('\0').filter(Boolean);
  const changes = status.map(line => ({ gitStatus: line.slice(0, 2), path: line.slice(3), ...ownership(line.slice(3)) }));
  const lock = sha(read(path.join(root, 'frontend/pnpm-lock.yaml')));
  const installedLock = sha(read(path.join(root, 'frontend/node_modules/.pnpm/lock.yaml')));
  if (lock !== installedLock) throw new Error('Installed frontend dependency lock does not match source lock; STOP');
  // Dependencies only, never target/classes. No cache writes through this junction: tests use --no-cache and runner config loading.
  fs.symlinkSync(path.join(root, 'frontend/node_modules'), path.join(source, 'frontend/node_modules'), 'junction');
  const noServices = [
    ...selected.filter(p => p.startsWith('src/test/java/com/wms/system/platform/') && p.endsWith('Test.java') && !/(Integration|Postgres)Test\.java$/.test(p)),
    ...selected.filter(p => /\/(JwtUtilTest|JwtAuthenticationFilterTest|DynamicAuthorizationManagerTest|TenantSecurityFoundationTest|TenantSessionSecurityControllerTest|ShopifyTokenProviderTest|ShopifyApiClientTest|ShopifyWebhookServiceTest|ActiveCompanyTaskRunnerTest|CompanyScopeTest|TenantLifecycleServiceTest|IntegrationSchedulerTest)\.java$/.test(p)),
  ].sort();
  for (const p of noServices) {
    const text = read(path.join(source, p)).toString('utf8');
    if (/@SpringBootTest|@DataJpaTest|@ContextConfiguration|@ActiveProfiles|@DynamicPropertySource|DriverManager\.getConnection|new Socket\(/.test(text))
      throw new Error(`Service-dependent test rejected from no-service selection: ${p}`);
  }
  const excludedDatabaseTests = selected.filter(p => p.startsWith('src/test/java/com/wms/system/platform/') && /(Integration|Postgres)Test\.java$/.test(p));
  const migrations = records.filter(r => r.path.startsWith('src/main/resources/db/migration/') && r.path.endsWith('.sql'))
    .map(r => ({ ...r, version: /^([VB])([\d_]+)__/.exec(path.basename(r.path))?.[2].replaceAll('_', '.') ?? null,
      kind: path.basename(r.path)[0] }));
  const versioned = migrations.filter(m => m.kind === 'V');
  if (new Set(versioned.map(m => m.version)).size !== versioned.length || !versioned.some(m => m.version === '4.89') || !versioned.some(m => m.version === '4.90'))
    throw new Error('Migration inventory mismatch');
  const manifest = { createdAt: new Date().toISOString(), branch: git('branch', '--show-current').trim(), baseCommit: git('rev-parse', 'HEAD').trim(),
    snapshotId: aggregate(records), sourceRoot: normalize(path.relative(root, source)), files: records,
    frontendLockSha256: lock, installedFrontendLockSha256: installedLock,
    sourceIsUncommittedWorkingSnapshot: true, dependenciesLinkedNotCopiedClasses: true, originalAuthMaterialsIncluded: false };
  writeNew(path.join(delivery, 'source-manifest.json'), manifest);
  writeNew(path.join(delivery, 'change-ownership.json'), { snapshotId: manifest.snapshotId, changes, note: 'Classification is a delivery proposal, not authorization to modify shared hunks or stage entire files.' });
  writeNew(path.join(delivery, 'migration-manifest.json'), { snapshotId: manifest.snapshotId, sqlCount: migrations.length, migrations,
    executionAuthorized: false, erpVersionsNotInThisSnapshot: '4.73 through 4.84; separate integration task owns migration callbacks and full shared sequence' });
  writeNew(path.join(delivery, 'no-service-test-selection.json'), { snapshotId: manifest.snapshotId, sourceFiles: noServices,
    selectors: noServices.map(p => p.replace('src/test/java/', '').replaceAll('/', '.').replace(/\.java$/, '')),
    databaseSuitesDeferred: excludedDatabaseTests, note: 'Explicit no-service subset; compiling other tests is not executing them. No database skip is counted as a pass.' });
  const historical = JSON.parse(read(path.join(root, 'target/platform-session-browser/platform-session-closeout-result-20260910.json')));
  writeNew(path.join(delivery, 'historical-evidence-summary.json'), { historicalOnly: true, completedAt: historical.completedAt,
    backend: { tests: historical.backend.tests, passed: historical.backend.passed, artifact: historical.backend.artifact, productSourcesRecompiled: false },
    frontend: { tests: historical.frontend.tests, passed: historical.frontend.passed, method: historical.frontend.method },
    faultSelector: historical.faultSelector, previousContinuationRun: historical.previousContinuationRun,
    notCovered: historical.notCovered, pendingBusinessDecision: historical.pendingBusinessDecision,
    actualShutdownRecordedAt: historical.shutdown.completedAt, currentServicesInspectedOrChanged: false });
  console.log(JSON.stringify({ snapshotId: manifest.snapshotId, files: records.length, sourceRoot: manifest.sourceRoot,
    noServiceSuites: noServices.length, migrationFiles: migrations.length, frontendDependenciesMatchLock: true }));
}

function verify() {
  const manifest = JSON.parse(read(path.join(delivery, 'source-manifest.json')));
  const changedSnapshot = [], changedWorkspace = [];
  for (const r of manifest.files) {
    if (sha(read(path.join(source, r.path))) !== r.sha256) changedSnapshot.push(r.path);
    if (!fs.existsSync(path.join(root, r.path)) || sha(read(path.join(root, r.path))) !== r.sha256) changedWorkspace.push(r.path);
  }
  console.log(JSON.stringify({ snapshotId: manifest.snapshotId, changedSnapshot, changedWorkspace,
    dependencyLockUnchanged: sha(read(path.join(root, 'frontend/node_modules/.pnpm/lock.yaml'))) === manifest.frontendLockSha256 }));
  if (changedSnapshot.length) throw new Error('Frozen source snapshot was modified');
}

if (process.argv[2] === 'prepare') prepare();
else if (process.argv[2] === 'verify') verify();
else throw new Error('Use prepare or verify; no arbitrary filesystem/database/service targets accepted');

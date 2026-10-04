// Fixed-scope delivery inventory. This does not stage files, apply diffs, run tests or access services.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
const root = 'D:/ERP_WMS/BC warehouse/2G';
if (process.cwd().replaceAll('\\', '/').toLowerCase() !== root.toLowerCase()) throw new Error('Wrong workspace');
const directory = path.join(root, 'docs/releases/platform-baseline-20260911');
const read = p => fs.readFileSync(path.join(root, p));
const digest = data => crypto.createHash('sha256').update(data).digest('hex');
const json = name => JSON.parse(fs.readFileSync(path.join(directory, name), 'utf8'));
const manifest = json('source-manifest.json');
const sourceFiles = new Map(manifest.files.map(f => [f.path, f]));
const git = (...args) => execFileSync('git', ['--no-optional-locks', '-c', 'core.quotepath=false', ...args],
  { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
const sourceHash = p => sourceFiles.get(p)?.sha256 ?? null;
const writeNew = (name, data) => fs.writeFileSync(path.join(directory, name), JSON.stringify(data, null, 2) + '\n', { flag: 'wx' });

if (process.argv[2] === 'index') {
  const entries = json('change-ownership.json').changes;
  const reviewed = entries.filter(e => e.groups.length).map(e => {
    const entry = { ...e };
    const p = entry.path;
    // Refine the initial filename heuristic with actual diff-hunk review.
    if (/frontend\/src\/platform\/Platform(App|AuthProvider|InvitationActivationPage)(\.test)?\./.test(p)) entry.groups = ['platform-session', 'browser-logout'];
    if (p === 'frontend/src/platform/storage.ts') entry.groups = ['browser-logout'];
    if (/sessionActivity|PlatformSessionCleanupScheduler|PlatformSessionException/.test(p)) entry.groups = ['platform-session'];
    if (/PlatformUserRepository|PlatformInvitationSessionPostgresTest/.test(p)) entry.groups = ['platform-session', 'browser-logout'];
    if (/\/security\/(JwtUtil|JwtAuthenticationFilter)(Test)?\.java$/.test(p)) entry.groups = ['P3-M1', 'platform-session'];
    if (/\/security\/DynamicAuthorizationManager\.java$/.test(p)) entry.groups = ['P3-M1', 'browser-logout'];
    if (/\/security\/(PlatformJwtClaims|JwtProperties)\.java$/.test(p)) entry.groups = ['platform-session'];
    if (p.endsWith('/SecurityConfig.java')) entry.groups = ['P3-M1', 'platform-session'];
    const referenceOnly = entry.scope === 'reference-only-not-feature-authorization';
    const shared = entry.scope.includes('shared') || /src\/main\/java\/com\/wms\/system\/security\//.test(p)
      || p === 'docs/CODEX_HANDOFF.md';
    const diff = entry.gitStatus.includes('M') ? git('diff', '--no-ext-diff', '--unified=0', '--', p) : null;
    return { ...entry, sourceSnapshotSha256: sourceHash(p), currentFileSha256: digest(read(p)), referenceOnly,
      sharedOrCrossTask: shared, wholeFileStagingAuthorized: false,
      reviewState: referenceOnly ? 'reference only; no additional feature scope' : shared ? 'existing safety dependency; future staging/integration needs hunk approval' : 'delivery classification; no commit authorization',
      diffSha256: diff === null ? null : digest(Buffer.from(diff)),
      diffHunks: diff === null ? [] : [...diff.matchAll(/^@@ .+? @@.*$/gm)].map(m => m[0]),
    };
  });
  const compareVersion = (a, b) => {
    const av = a.version.split('.').map(Number), bv = b.version.split('.').map(Number);
    for (let i = 0; i < Math.max(av.length, bv.length); i++) if ((av[i] ?? 0) !== (bv[i] ?? 0)) return (av[i] ?? 0) - (bv[i] ?? 0);
    return 0;
  };
  const migrations = json('migration-manifest.json').migrations;
  const results = json('validation-results.json');
  const summed = ['tests', 'failures', 'errors', 'skipped'].map(key => [key, results.backendUnitTests.suites.reduce((total, suite) => total + suite[key], 0)]);
  if (summed.some(([key, value]) => results.backendUnitTests[key] !== value)) throw new Error('Aggregate test evidence mismatch');
  if (results.backendUnitTests.tests !== 186 || results.frontendPlatform.passed !== 86 || results.frontendSafetyCompatibility.passed !== 6) throw new Error('Unexpected result inventory');
  writeNew('delivery-index.json', {
    snapshotId: manifest.snapshotId, baseCommit: manifest.baseCommit, independentlyDeployableGroups: false,
    groupingAuthority: 'This reviewed index refines the initial change-ownership filename heuristic. Shared hunk labels are review proposals, not permission to stage/merge.',
    generatedDeliveryItems: { group: 'evidence-tools', createdAfterSourceSnapshot: true,
      paths: ['docs/PLATFORM_RELEASE_BASELINE_PREPARATION_2026-09-11.md', 'docs/releases/platform-baseline-20260911/*.json',
        'src/test/scripts/platform-release-evidence.ps1', 'src/test/scripts/platform-release-index.mjs'],
      note: 'Recorded separately from the 132 initial changed/reference paths; not business compilation inputs.' },
    groups: ['P3-M1', 'platform-session', 'browser-logout', 'evidence-tools'].map(id => ({ id,
      files: reviewed.filter(e => e.groups.includes(id)).map(e => e.path) })),
    files: reviewed, outsideScopePreserved: entries.filter(e => !e.groups.length).map(e => ({ path: e.path, gitStatus: e.gitStatus, scope: e.scope })),
    orderedVersionedMigrations: migrations.filter(m => m.kind === 'V').sort(compareVersion),
    baselineMigrations: migrations.filter(m => m.kind === 'B'),
    databaseTestsNotExecuted: manifest.files.filter(f => /src\/test\/java\/.+(Integration|Postgres)Test\.java$/.test(f.path)
      && /\/platform\/|\/tenant\/security\//.test(f.path)).map(f => f.path),
    deferred: ['ERP V4_73..V4_84 and migration callbacks belong to separate integration approval',
      'Full manual invitation activation', 'Final recovery reconciliation business boundary', 'Production rollout'],
  });
  console.log(JSON.stringify({ reviewedFiles: reviewed.length, outsideScopeFiles: entries.length - reviewed.length, testAggregatesVerified: true }));
} else if (process.argv[2] === 'seal') {
  const records = fs.readdirSync(directory).filter(name => name.endsWith('.json') && name !== 'delivery-seal.json')
    .map(name => ({ path: 'docs/releases/platform-baseline-20260911/' + name, sha256: digest(fs.readFileSync(path.join(directory, name))) }));
  for (const p of ['docs/PLATFORM_RELEASE_BASELINE_PREPARATION_2026-09-11.md',
    'src/test/scripts/platform-release-baseline.mjs', 'src/test/scripts/platform-release-evidence.ps1', 'src/test/scripts/platform-release-index.mjs']) {
    records.push({ path: p, sha256: digest(read(p)) });
  }
  const changed = manifest.files.filter(f => digest(read(f.path)) !== f.sha256).map(f => f.path);
  if (changed.length) throw new Error('Workspace source drift: ' + changed.join(', '));
  writeNew('delivery-seal.json', { snapshotId: manifest.snapshotId, createdAt: new Date().toISOString(), files: records,
    postSnapshotToolsAreNotProductChanges: true, currentTrackedDiffSha256: digest(Buffer.from(git('diff', '--no-ext-diff', '--binary'))),
    branch: git('branch', '--show-current').trim(), commit: git('rev-parse', 'HEAD').trim(),
    stagedPaths: git('diff', '--cached', '--name-only').trim().split('\n').filter(Boolean), sourceDrift: changed });
  console.log(JSON.stringify({ sealedFiles: records.length, sourceDrift: changed }));
} else throw new Error('Use index or seal');

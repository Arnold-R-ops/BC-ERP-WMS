// Source contracts only. No database, service, socket or launcher calls.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {testBodies} from './platform-r9-ledger.mjs';
const R9_APPROVED={
 realPlatformExportRequiresSuspensionReasonAndRechecksDownloadAndActor:'c098a92e29811f0bb094ad41d2b99d51f090721b7581ba33642218e2e6eb817a',
 alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate:'e661fb0a7e94a96682656eeaf4286b823251b3d4f61b543b4250e13c7016d605',
 provisionAndLoginHandoffPreserveErpAndCannotReusePrePauseCode:'e0372adbfed26fedd3eb127696751ad2636be3b3de5d13a1adffd91023b12a4b'
};
const root=new URL('../../../',import.meta.url);
const read=p=>fs.readFileSync(new URL(p,root),'utf8');
const prefix='src/test/java/com/wms/system/platform/service/';
const target=read(prefix+'PlatformCompanyDataServiceIntegrationTest.java');
const fixtures=read(prefix+'PlatformCompanyDataFixture.java');
const tasks=read(prefix+'PlatformExportTestConfiguration.java');
const input=JSON.parse(fs.readFileSync('D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260916/r8/backend-input-manifest.json','utf8'));
test('every product source and migration remains identical to frozen r8',()=>{
 for(const f of input.groups.find(g=>g.name==='backend-source').files.filter(f=>f.path.startsWith('src/main/')))
  assert.equal(crypto.createHash('sha256').update(fs.readFileSync(new URL(f.path,root))).digest('hex'),f.sha256,f.path);
});
test('tenant fixture invokes UTC retention helper before persistence',()=>{
 assert.match(target,/tenantRepository.save\(PlatformCompanyDataFixture.retention/);
 assert.match(fixtures,/closed.plusDays\(30\)/);assert.match(fixtures,/minusDays\(31\)/);
 assert.match(read('src/main/resources/db/migration/B4_71__SaaS_Empty_Database_Baseline.sql'),/ck_tenants_retention/);
});
test('export fixture uses baseline least role with no super-admin fallback',()=>{
 assert.match(fixtures,/EXPORT_ROLE = "PLATFORM_TENANT_EXPORT"/);
 assert.doesNotMatch(fixtures,/PLATFORM_SUPER_ADMIN|insert into platform_roles|delete from|update platform_roles/i);
 assert.match(target,/fixtures.requireOwnedUser\(actorId\)/);
 assert.match(target,/new TransactionTemplate\(transactionManager\).execute\(tx ->\s*PlatformCompanyDataFixture.persistExportRole/);
 assert.match(target,/authenticate\(authority\)/);
});
test('grant is exact actor tenant dataset capability and validity window',()=>{
 for(const part of ['grantee_platform_user_id=?','tenant_id=?',"capability='EXPORT'","dataset_code='warehouses'",'revoked_at is null','effective_from<=current_timestamp','expires_at>current_timestamp'])assert.ok(fixtures.includes(part),part);
});
test('real named executor is wrapped not replaced by fake worker',()=>{
 assert.match(target,/@org.springframework.context.annotation.Import\(PlatformExportTestConfiguration.class\)/);
 assert.match(tasks,/"platformExportExecutor".equals\(name\) \? new TrackingExecutor\(\(Executor\) bean\)/);
 assert.match(tasks,/delegate.execute\(/);assert.match(tasks,/command.run\(\)/);
 assert.doesNotMatch(target,/MockBean|SpyBean|exportWorker.run|new PlatformExportService/);
});
test('test waits beyond terminal status and preserves integrity and exclusion assertions',()=>{
 assert.ok(target.indexOf('directory.awaitWorker()')<target.indexOf('assertThat(job.getStatus()).isEqualTo("COMPLETED")'));
 for(const token of ['hasSize(64)','MessageDigest.getInstance("SHA-256")','manifest.json','warehouses.csv','name.contains("audit")','name.contains("password")','name.contains("token")','name.contains("secret")'])assert.ok(target.includes(token),token);
});
test('diagnostic SQL is exact actor tenant job action dataset',()=>{
 for(const token of ['platform_user_id=?','target_tenant_id=?',"action='EXPORT'","resource_type='warehouses'","detail_json::jsonb->>'jobId'=?"])assert.ok(target.includes(token),token);
 assert.match(fixtures,/underlyingErrorCode=NOT_CAPTURED/);
 assert.doesNotMatch(fixtures,/printStackTrace|getMessage\(|writeValueAsString|toString\(\).*exception/);
});
test('owned directory is closed by try-with-resources and background admission remains untouched',()=>{
 assert.match(target,/try \(var directory = exportTasks.begin/);assert.match(tasks,/RETAINED_WORKER_ACTIVE/);
 assert.match(tasks,/if \(deferred\)/);assert.match(tasks,/properties.setExportDirectory\(previous\)/);
 assert.match(tasks,/LinkOption.NOFOLLOW_LINKS/);assert.match(tasks,/directoryKey, attrs.fileKey/);
 assert.doesNotMatch(tasks,/walkFileTree|Files.walk|\.deleteOnExit|listRoots/);
 assert.match(read('src/main/java/com/wms/system/platform/service/PlatformExportWorker.java'),/safety.requireWorkerExport/);
});
test('only exact known file is deleted without recursive cleanup or historical prefix scan',()=>{
 assert.match(tasks,/Files.deleteIfExists\(artifact\(jobId\)\)/);assert.match(tasks,/Files.delete\(root\)/);
 assert.match(tasks,/markerContents.equals\(Files.readString\(marker\)\)/);assert.match(tasks,/createdAt.equals\(attrs.creationTime\(\)\)/);
 assert.match(tasks,/EXPLICIT_EXPORT_FIXTURE_TEMP_ROOT_REQUIRED/);
 assert.doesNotMatch(tasks,/bcwms-platform-export-test-|16037803463767874847|glob:|DirectoryStream/);
});
test('migration tool display uses current binding not stale R7 label',()=>{
 const tool=read('src/test/java/com/wms/platformverification/PlatformR2DatabasePreparer.java');
 assert.doesNotMatch(tool,/release R7|upgrade R7|R7 dedicated/);assert.match(tool,/PlatformR2Isolation.MAIN \+ " and " \+ PlatformR2Isolation.UPGRADE/);
});
test('r9 retains fifteen methods and only the three explicitly approved bodies change',()=>{
 for(const name of ['TenantSecuritySpringPostgresTest.java','PlatformAccessGrantRoleRaceIntegrationTest.java']){
  const f=input.groups.find(g=>g.name==='backend-source').files.find(f=>f.path.endsWith('/'+name));
  const before=input.businessTests.find(e=>e.path===f.path).tests,after=testBodies(read(f.path));
  assert.deepEqual(after.map(t=>t.name),before.map(t=>t.name));
  for(const t of after){const expected=R9_APPROVED[t.name]??before.find(b=>b.name===t.name).sha256;assert.equal(t.sha256,expected);}
 }
});
test('guard evidence matches a frozen name catalog with exact source identity not previous pass flags',()=>{
 const runner=read('src/test/scripts/platform-export-fixture-verification.mjs');
 assert.match(runner,/approvedSource.files.find\(f=>f.path===source.path\)\?\.sha256!==source.sha256/);
 assert.match(runner,/approvedCases.find\(g=>g.name===name\).cases.map\(c=>c.name\)/);
 assert.match(runner,/new Set\(expected\).size!==count/);
 assert.match(runner,/parser.assessSuite\(\{name,methods:expected\},\{process:java,report\}\)/);
 assert.doesNotMatch(runner,/approvedCases[^;\n]*\.passed|approvedCases[^;\n]*\.failures/);
});

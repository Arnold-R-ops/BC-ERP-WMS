import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {exportEvidence} from './platform-r9-export-evidence.mjs';
import {expectedCases,testBodies} from './platform-r9-ledger.mjs';
const read=p=>fs.readFileSync(new URL('../java/'+p,import.meta.url),'utf8');
const spring=read('com/wms/system/tenant/security/TenantSecuritySpringPostgresTest.java');
const helper=read('com/wms/system/tenant/security/SpringSecurityFixture.java');
const race=read('com/wms/system/platform/service/PlatformAccessGrantRoleRaceIntegrationTest.java');
test('original super export reason logout and disabled actor assertions are preserved',()=>{
 for(const text of ["role_code='PLATFORM_SUPER_ADMIN'",'Security investigation 100% verified','isUnauthorized()','isBadRequest()',"assertEquals('P',file[0])",'assertEquals("FAILED",exportJobs.findById(revoked.getId())','assertNull(exportJobs.findById(revoked.getId())'])assert.ok(spring.includes(text),text);
});
test('both synchronous exports bind exact files and wait for worker before lease close',()=>{
 assert.equal((spring.match(/try\(var directory=taskRunner.begin/g)??[]).length,2);
 for(const x of ['directory.bind(job.getPublicId())','directory.bind(revoked.getPublicId())','taskRunner.execute(()->exportWorker.run(job.getId()))','taskRunner.execute(()->exportWorker.run(revoked.getId()))','directory.artifact(job.getPublicId())'])assert.ok(spring.includes(x));
 assert.equal((spring.match(/directory.awaitWorker\(\)/g)??[]).length,2);
});
test('mail writes only exact owned row and always restores mock and settings',()=>{
 assert.doesNotMatch(spring,/update tenant_security_alerts set next_mail_at=null/);
 assert.match(spring,/where id=\? and tenant_id=\?/);assert.match(spring,/finally \{\s*org.mockito.Mockito.reset\(mail\)/);
 for(const x of ['settings.setAlertRecipientIds(previousRecipients)','settings.setControlsEnabled(previousControls)','SpringSecurityFixture.requireOwnedMail','SpringSecurityFixture.AlertLocks.acquire'])assert.ok(spring.includes(x));
});
test('foreign alerts receive row locks only and rollback releases locks',()=>{
 assert.match(helper,/for update nowait/);assert.match(helper,/connection.rollback\(\)/);assert.match(helper,/FOREIGN_ALERT_ROWS_CHANGED/);
 assert.doesNotMatch(helper,/update tenant_security_alerts|delete from|\.commit\(/i);
 assert.match(helper,/throw new AssertionError\("UNOWNED_ALERT_DELIVERY_REFUSED"\)/);
 const product=fs.readFileSync(new URL('../../../src/main/java/com/wms/system/tenant/security/TenantSecurityAlertService.java',import.meta.url),'utf8');assert.match(product,/for update skip locked/);
});
test('TRIAL baseline read does not repair missing reference data',()=>{
 assert.match(spring,/SpringSecurityFixture.requireTrial\(plans\)/);assert.doesNotMatch(spring,/plans.save/);
 assert.match(helper,/BASELINE_TRIAL_MISSING/);assert.match(helper,/Objects.equals\(plan.getTrialDays\(\), 30\)/);
});
test('concurrent test remains parallel and close barrier precedes fixture deletion',()=>{
 assert.match(race,/newFixedThreadPool\(2\)/);assert.ok(race.indexOf('PlatformFixtureWorkers.requireStopped')<race.indexOf('grants.deleteAll'));
 assert.match(race,/grantCreation.get\(300, TimeUnit.MILLISECONDS\)/);assert.match(race,/PLATFORM_ADMIN_ROLE_CHANGE_FORBIDDEN/);
});
test('independently derived isolation parameterized catalog contains 45 cases including old r10 rejection',()=>{
 const cases=expectedCases(read('com/wms/platformverification/PlatformR2IsolationTest.java'));assert.equal(cases.length,45);
 assert.ok(cases.includes('wrongDestinationsRejectedBeforeAnyConnector(String)[16]'));
});
test('helper tests retain 24 cases plus two actual mail-case failure-path checks',()=>{
 assert.equal(testBodies(read('com/wms/platformverification/PlatformFixtureWorkersTest.java')).length,8);
 assert.equal(testBodies(read('com/wms/system/tenant/security/SpringSecurityFixtureTest.java')).length,18);
});
test('export diagnostic reader preserves unknown historical underlying cause',()=>{
 const value=exportEvidence('PLATFORM_EXPORT_FIXTURE_DIAGNOSTIC job=00000000-0000-0000-0000-000000000000 actor=1 tenant=2 state=FAILED publicErrorCode=PLATFORM_EXPORT_FAILED matchedFailureAudits=1 auditExceptionTypes=[AccessDeniedException] malformedCandidateAudits=0 underlyingErrorCode=NOT_CAPTURED','D:/r9/export-fixture-temp');
 assert.equal(value.diagnostics[0].underlyingErrorCode,'NOT_CAPTURED');assert.equal(value.diagnostics[0].auditExceptionTypes[0],'AccessDeniedException');
});
test('export diagnostic reader refuses foreign roots and malformed sensitive text',()=>{
 assert.throws(()=>exportEvidence('PLATFORM_EXPORT_DIRECTORY owner=fixture root=C:/Temp/owned-export-123 state=CREATED','D:/r9/export-fixture-temp'));
 assert.throws(()=>exportEvidence('PLATFORM_EXPORT_FIXTURE_DIAGNOSTIC password=redacted-test-value','D:/r9/export-fixture-temp'));
});

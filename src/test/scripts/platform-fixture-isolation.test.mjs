// Pure source/ownership contract checks: no database, sockets or services.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
const ROOT='D:/ERP_WMS/BC warehouse/2G';
const prior=JSON.parse(fs.readFileSync(ROOT+'/docs/releases/platform-db-acceptance-20260911/r5/backend-input-manifest.json','utf8'));
const previous=prior.groups.find(g=>g.name==='backend-source');
const read=p=>fs.readFileSync(new URL('../java/'+p,import.meta.url),'utf8').replaceAll('\r\n','\n');
const java='com/wms/system/platform/service/';
const scope=read('com/wms/platformverification/PlatformFixtureScope.java');
const branch=read(java+'PlatformMfaFailureFixture.java');
const target=read(java+'PlatformAdminInvitationMfaTransactionIntegrationTest.java');
const R9_APPROVED={
 realPlatformExportRequiresSuspensionReasonAndRechecksDownloadAndActor:'c098a92e29811f0bb094ad41d2b99d51f090721b7581ba33642218e2e6eb817a',
 alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate:'e661fb0a7e94a96682656eeaf4286b823251b3d4f61b543b4250e13c7016d605',
 provisionAndLoginHandoffPreserveErpAndCannotReusePrePauseCode:'e0372adbfed26fedd3eb127696751ad2636be3b3de5d13a1adffd91023b12a4b'
};
export function methods(text){
  text=text.replaceAll('\r\n','\n');
  const mask=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,s=>s.replace(/[^\r\n]/g,' '));
  return [...mask.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\([^)]*\)\s*(?:throws[^\{]+)?\{/g)].map(m=>{
    let end=m.index+m[0].length,n=1;for(;end<mask.length&&n;end++){if(mask[end]==='{')n++;if(mask[end]==='}')n--;}
    assert.equal(n,0);const body=text.slice(m.index,end);
    return {name:m[1],body,sha256:crypto.createHash('sha256').update(body).digest('hex')};});
}
for(const row of prior.expectedBusinessTests){
  const p=row.path.substring('src/test/java/'.length),now=read(p),old=fs.readFileSync(previous.location+'/'+row.path,'utf8').replaceAll('\r\n','\n');
  test('preserves business method names and coverage: '+p,()=>assert.deepEqual(methods(now).map(m=>m.name),row.unchangedTests.map(m=>m.name)));
  test('only approved assertion/fixture body changes: '+p,()=>{
    const before=methods(old),after=methods(now);
    for(let i=0;i<before.length;i++){
      if(p.endsWith('PlatformAdminInvitationMfaTransactionIntegrationTest.java')){
        const original=before[i].body.replace(/assertThatThrownBy\(\(\) -> invitationsService.create\(request, null\)\)\s*\.isInstanceOf\(BusinessException.class\);/,
          'fixtures.requireOwnedUser(actorId);\n        PlatformMfaFailureFixture.requireReady(PlatformMfaFailureFixture.inspect(jdbc, actorId, challengeId), System.out::println);\n        Throwable failure = catchThrowable(() -> invitationsService.create(request, null));\n        PlatformMfaFailureFixture.assertCredentialFailure(failure, actorId, crypto.hashToken(rawChallenge), System.out::println);');
        assert.equal(after[i].body,original,'No unrelated target-body edits');
      }else if(p.endsWith('PlatformCompanyDataServiceIntegrationTest.java') && after[i].name==='singleDatasetExportContainsNoSecretDataset'){
        // 2026-09-18 explicitly authorized: persisted least export role, exact sanitized evidence and owned async cleanup.
        // Freeze the entire revised method, not a permissive stripping rule. Dedicated contracts cover its wiring/assertions.
        assert.equal(after[i].sha256,'812265b4be1885cdaab06b5aaa3c1e16db522112ba685e8c895d003ed5818556');
      }else if(p.endsWith('TenantSecuritySpringPostgresTest.java') && Object.hasOwn(R9_APPROVED,after[i].name)){
        assert.equal(after[i].sha256,R9_APPROVED[after[i].name],'Exact approved r9 fixture body');
      }else{
        // Only explicit ownership registration may be added inside existing method bodies.
        const withoutRegistration=after[i].body.replace(/^\s*fixtures\.own(?:User|Tenant)\((?:actor|actors\[i\]|tenant)\);\n/gm,'');
        assert.equal(withoutRegistration,before[i].body);
      }
    }
  });
}
test('all eight database suites are attached to ownership scope',()=>{
  for(const row of prior.expectedBusinessTests){const body=read(row.path.substring('src/test/java/'.length));
    if(row.path.endsWith('PlatformInvitationSessionPostgresTest.java'))assert.match(body,/extends PlatformSessionPostgresTest/);
    else {assert.match(body,/PlatformFixtureScope\.open\(/);assert.match(body,/fixtures\.close\(\)/);}}
});
test('source contains no broad cleanup nor audit or public role mutation',()=>{
  assert.doesNotMatch(scope,/(?:delete from|update) (?:platform_audit_logs|platform_roles|tenants)\b/i);
  assert.doesNotMatch(scope,/truncate|delete from platform_users|delete from platform_user_roles/i);
  assert.match(scope,/delete from platform_admin_invitation_active_emails where invitation_id=\? and normalized_email=\?/);
});
test('retirement verifies all ownership before mutating within transaction',()=>{
  const start=scope.indexOf('store.transaction('),unknown=scope.indexOf('UNKNOWN_NEW_FIXTURE_AT_CLOSE'),mutation=scope.indexOf('store.retireInvitation(row)');
  assert.ok(start<unknown&&unknown<mutation);assert.match(scope,/UNKNOWN_NEW_TENANT_AT_CLOSE/);assert.match(scope,/OWNED_USER_IDENTITY_CHANGED/);
});
test('invitation child ownership uses exact registered email, inviter and accepted user',()=>{
  for(const marker of ['initialInvitations.contains(row.id())','row.actor() == intent.getValue()','row.email().equals(intent.getKey())','accepted.email().equals(row.email())'])assert.ok(scope.includes(marker));
});
test('target validates baseline role without auto-creation or deletion',()=>{
  assert.match(target,/requireBaselineRole\(roles, "PLATFORM_SUPER_ADMIN"\)/);
  assert.doesNotMatch(target,/roles\.save|roles\.delete|createdRole/);
});
test('MFA preconditions precede invocation and exact branch check',()=>{
  assert.ok(target.indexOf('PlatformMfaFailureFixture.requireReady')<target.indexOf('catchThrowable(() -> invitationsService.create'));
  assert.match(branch,/new Before\(1,0,0,1,1,4,4\)/);
  for(const clause of ["purpose='ADMIN_INVITE'",'consumed_at is null','expires_at>clock_timestamp()','mfa_locked_until','mfa_enabled=true'])assert.ok(branch.includes(clause));
});
test('safe diagnostics do not serialize Throwable, request, params or challenge hash',()=>{
  assert.doesNotMatch(branch,/printStackTrace|failure\.toString|request\.|getParams\(|output\.accept\([^\n]*(?:challengeHash|actual\))/);
  assert.match(branch,/safeKey\(b.getErrorKey\(\)\)/);assert.match(branch,/PlatformAdminInvitationMfaCredentialException.class/);assert.match(branch,/ErrorKeys.AUTH_FAILED/);
});
test('seat and MFA product code and every migration remain byte-identical to r5',()=>{
  for(const f of previous.files.filter(f=>f.path.startsWith('src/main/'))){
    const current=new URL('../../../'+f.path,import.meta.url);
    assert.equal(crypto.createHash('sha256').update(fs.readFileSync(current)).digest('hex'),f.sha256,f.path);
  }
});

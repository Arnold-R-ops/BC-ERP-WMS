import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {r6Plans,runR6Acceptance,finalizeR6Results} from './platform-r6-database-batch.mjs';
import {summarizeR6,fixtureDiagnostics,requireCompleteScopes,newRegistry,registerScopes,validateSnapshot,ROOT_SNAPSHOT_SQL,targetStateSql,targetComparison,TARGET} from './platform-r6-stages.mjs';
const input=JSON.parse(fs.readFileSync('D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-fixture-repair-20260912/a2/input-manifest.json','utf8'));
const clone=v=>structuredClone(v),suite=r6Plans(input)[0].plan[0];
const processOkay={exitCode:0,exited:true,outputComplete:true,terminationConfirmed:true};
function xml(s,{failed=false}={}){return `<testsuite name="${s.name}" tests="${s.methods.length}" failures="${failed?1:0}" errors="0" skipped="0">${s.methods.map((name,i)=>`<testcase classname="${s.name}" name="${name}">${failed&&i===0?'<failure/>':''}</testcase>`).join('')}</testsuite>`;}
test('actual R6 entry imports without initializing DB and plans 1/42/63 instances',()=>{
 const plans=r6Plans(input);assert.deepEqual(plans.map(p=>p.planned),[1,42,63]);assert.deepEqual(plans.map(p=>p.plan.length),[1,5,8]);
 assert.equal(new Set(plans.flatMap(p=>p.plan.flatMap(s=>s.methods.map(m=>s.name+'#'+m)))).size,63);
 assert.equal(input.methodBodyChanges.length,7);assert.equal(input.original63MethodBodiesUnchanged,false);
});
for(const [label,change] of [['missing suite',v=>v.businessTests.pop()],['extra suite',v=>v.businessTests.push(v.businessTests[0])],
 ['missing method',v=>v.businessTests[0].tests.pop()],['duplicate method',v=>v.businessTests[0].tests[1]=v.businessTests[0].tests[0]],
 ['bad hash',v=>v.businessTests[0].tests[0].sha256='bad'],['undisclosed changes',v=>v.methodBodyChanges.pop()],
 ['wrong target',v=>v.businessTests.find(e=>e.path.endsWith('PlatformAdminInvitationMfaTransactionIntegrationTest.java')).tests[0].name='other']])
 test('plan rejects '+label,()=>{const value=clone(input);change(value);assert.throws(()=>r6Plans(value));});
test('actual entry runs all 106 instances; full 63 stands alone with before/after hooks',async()=>{
 const calls=[],before=[],after=[];
 const result=await runR6Acceptance({input,execute:async(p,s)=>{calls.push([p.id,s.name]);return {process:processOkay,xml:xml(s)};},
 beforeSuite:async(p,s)=>before.push(p.id+s.name),afterSuite:async(p,s)=>after.push(p.id+s.name)});
 assert.equal(result.status,'PASSED');assert.equal(result.executionInstances.passed,106);assert.equal(result.executionInstances.distinctBusinessMethods,63);
 assert.equal(result.fullRegression.coverage.passed,63);assert.deepEqual(before,after);assert.equal(calls.length,14);
 const saved={status:'PASSED',r6:result};finalizeR6Results(saved,input);assert.equal(saved.coverage.passed,63);assert.equal(saved.suites.length,8);
});
for(const phase of ['independent','sequential','full'])test('business failure in '+phase+' stops every later suite/phase',async()=>{
 const calls=[];const result=await runR6Acceptance({input,execute:async(p,s)=>{calls.push(p.id);const failed=p.id===phase&&s.name===TARGET;
 return {process:{...processOkay,exitCode:failed?1:0},xml:xml(s,{failed})};}});
 assert.equal(result.status,'FAILED');assert.equal(calls.at(-1),phase);assert.equal(result.executionInstances.failures,1);
 const bad=result.phases.find(p=>p.id===phase);assert.equal(bad.failureCategory,'BUSINESS_TEST_FAILED');
 if(phase!=='full')assert.equal(result.fullRegression.coverage.unexecuted,63);
});
test('precondition failure cannot execute or credit any method',async()=>{
 let called=false;const r=await runR6Acceptance({input,beforeSuite:async()=>{throw Error('unknown residual');},execute:async()=>{called=true;}});
 assert.equal(called,false);assert.equal(r.executionInstances.unexecuted,106);assert.equal(r.phases[0].fixtureFailure,'FIXTURE_PRECONDITION_FAILED');
});
test('fixture failure retains actual business pass but forbids next stage',async()=>{
 const r=await runR6Acceptance({input,execute:async(p,s)=>({process:processOkay,xml:xml(s)}),afterSuite:async()=>{throw Error('unknown residual');}});
 assert.equal(r.status,'FAILED');assert.equal(r.executionInstances.passed,1);assert.equal(r.fullRegression.coverage.unexecuted,63);
 assert.equal(r.phases[0].fixtureFailure,'FIXTURE_RECONCILIATION_FAILED');
});
test('damaged XML is unverified, never replaced by Maven success',async()=>{
 const r=await runR6Acceptance({input,execute:async()=>({process:processOkay,xml:'<broken'})});
 assert.equal(r.executionInstances.unverified,1);assert.equal(r.executionInstances.unexecuted,105);assert.equal(r.phases[0].failureCategory,'REPORT_PARSE_FAILED');
});
test('successful XML cannot mask failed process',async()=>{
 const r=await runR6Acceptance({input,execute:async(p,s)=>({process:{...processOkay,exitCode:1},xml:xml(s)})});
 assert.equal(r.status,'FAILED');assert.equal(r.phases[0].failureCategory,'EXECUTION_FAILED');assert.equal(r.fullRegression.coverage.unexecuted,63);
});
test('no execution cannot be credited by historic or cached totals',()=>{
 const r=summarizeR6(input);assert.equal(r.executionInstances.unexecuted,106);assert.equal(r.fullRegression.coverage.passed,0);
 assert.throws(()=>summarizeR6(input,[{id:'r5'}]));assert.throws(()=>summarizeR6(input,[{id:'full'},{id:'full'}]));
});
const owner=TARGET+'.credentialFailureFixture:00000000-0000-4000-8000-000000000001';
const output=`PLATFORM_FIXTURE_START owner=${owner} activeUsers=0 pendingInvitations=0
MFA_FIXTURE_PRECONDITIONS Before[enabledSuperAdmins=1, pendingSuperInvitations=0, allPendingInvitations=0, matchingActor=1, matchingChallenge=1, actorAttempts=4, challengeAttempts=4]
MFA_FIXTURE_EXCEPTION type=com.wms.system.platform.service.PlatformAdminInvitationMfaCredentialException errorKey=AUTH_FAILED
PLATFORM_FIXTURE_SCOPE owner=${owner} userIds=[1] invitationIds=[] tenantIds=[] status=RETIRED_AUDIT_RETAINED`;
const diagnostics=()=>fixtureDiagnostics(output,suite);
test('only allowlisted diagnostic fields retained; unrelated output is not archived',()=>{
 const d=fixtureDiagnostics('unrelated private output deliberately discarded\n'+output,suite);requireCompleteScopes(d,suite);
 assert.deepEqual(d.scopes[0].userIds,[1]);assert.ok(!JSON.stringify(d).includes('private'));
});
for(const [label,change] of [['malformed prefix',s=>s+'\nMFA_FIXTURE_EXCEPTION arbitrary private text'],
 ['bad IDs',s=>s.replace('userIds=[1]','userIds=[0]')],['duplicate IDs',s=>s.replace('userIds=[1]','userIds=[1, 1]')],
 ['wrong owner',s=>s.replaceAll(TARGET,'other.Suite')]])test('diagnostics reject '+label,()=>assert.throws(()=>fixtureDiagnostics(change(output),suite)));
for(const [label,change] of [['missing close',d=>d.scopes=[]],['duplicate start',d=>d.starts.push(d.starts[0])],
 ['active user',d=>d.starts[0].activeUsers=1],['pending invite',d=>d.starts[0].pendingInvitations=1],
 ['wrong owner match',d=>d.scopes[0].owner+='x']])test('complete scope rejects '+label,()=>{const d=diagnostics();change(d);assert.throws(()=>requireCompleteScopes(d,suite));});
test('BeforeAll foundation scope is one, not method count',()=>{
 const s=r6Plans(input)[2].plan[0],d=fixtureDiagnostics(output.replaceAll(TARGET,s.name),s);requireCompleteScopes(d,s);
});
const empty=()=>({users:[],invitations:[],tenants:[],activeUsers:0,pendingInvitations:0,roles:Array.from({length:7},(_,i)=>({id:i+1,hash:'a'.repeat(32)})),audits:[]});
test('initial main DB cannot silently adopt unknown inactive synthetic rows',()=>{
 validateSnapshot(empty(),newRegistry());const v=empty();v.users=[9];assert.throws(()=>validateSnapshot(v,newRegistry()));
});
test('new scope registry is immutable and rejects repeated ownership',()=>{
 const original=newRegistry(),next=registerScopes(original,diagnostics().scopes);assert.equal(original.users.size,0);assert.deepEqual([...next.users],[1]);
 assert.throws(()=>registerScopes(next,diagnostics().scopes));const changed=diagnostics().scopes;changed[0].owner+='x';assert.throws(()=>registerScopes(next,changed));
});
for(const key of ['users','invitations','tenants'])test('unknown '+key+' residual is refused, not auto-owned or removed',()=>{
 const v=empty();v[key]=[999];assert.throws(()=>validateSnapshot(v,newRegistry()));
});
test('registered exact deletion by owning legacy suite is allowed; history may accumulate',()=>{
 const registry=registerScopes(newRegistry(),diagnostics().scopes),a=empty(),b=empty();
 a.users=[1];b.audits=[{id:4,hash:'b'.repeat(32)}];validateSnapshot(a,registry);validateSnapshot(b,registry,a);
 assert.equal(b.users.length,0);assert.equal(b.audits.length,1);
});
for(const [label,change] of [['active account',v=>v.activeUsers=1],['pending invitation',v=>v.pendingInvitations=1],
 ['missing public role',v=>v.roles.pop()],['altered public role',v=>v.roles[0].hash='b'.repeat(32)],
 ['unknown projection',v=>v.password='not-allowed'],['duplicate row',v=>v.roles.push(v.roles[0])],
 ['deleted audit',v=>v.audits=[]],['modified audit',v=>v.audits[0].hash='c'.repeat(32)]])test('snapshot rejects '+label,()=>{
 const a=empty();a.audits=[{id:4,hash:'b'.repeat(32)}];const b=clone(a);change(b);assert.throws(()=>validateSnapshot(b,newRegistry(),a));
});
const persisted=()=>({actorCount:1,disabled:true,actorAttempts:5,locked:true,challengeCount:1,challengeAttempts:5,challengeConsumed:true,invitationCount:0});
test('MFA comparison is effective state/exact exception/persistence, not IDs or accumulated totals',()=>{
 const value=targetComparison(diagnostics(),persisted());assert.deepEqual(targetComparison(diagnostics(),persisted(),value),value);
 assert.ok(!JSON.stringify(value).includes('userIds'));assert.ok(!JSON.stringify(value).includes('historical'));
});
for(const key of Object.keys(persisted()))test('MFA persistence mismatch: '+key,()=>{
 const v=persisted();v[key]=typeof v[key]==='boolean'?!v[key]:v[key]+1;assert.throws(()=>targetComparison(diagnostics(),v));
});
for(const [label,change] of [['seat precondition',d=>d.mfaBefore[0].enabledSuperAdmins=2],['wrong error',d=>d.exceptions[0].errorKey='FORBIDDEN'],
 ['generic BusinessException',d=>d.exceptions[0].type='com.wms.system.exception.BusinessException'],['missing exact exception',d=>d.exceptions=[]]])
 test('MFA comparison rejects '+label,()=>{const d=diagnostics();change(d);assert.throws(()=>targetComparison(d,persisted()));});
test('target state reads only exact numeric actor and contains no authentication fields',()=>{
 assert.throws(()=>targetStateSql('1;delete'));assert.throws(()=>targetStateSql(0));
 assert.match(targetStateSql(1),/where id=1/);assert.doesNotMatch(targetStateSql(1),/password|token_hash|secret|recovery/i);
 assert.doesNotMatch(ROOT_SNAPSHOT_SQL,/\b(delete|update|truncate|insert)\b/i);assert.match(ROOT_SNAPSHOT_SQL,/READ ONLY/);
});
test('real batch wires separate reports, physical SQL checks, scopes, target comparison and final full summary',()=>{
 const source=fs.readFileSync(new URL('./platform-r6-database-batch.mjs',import.meta.url),'utf8');
 for(const literal of ['runR6Acceptance({input:manifest','afterSuite:async','beforeSuite:async',"reportRoot:BUILD+'/reports'",'return executePlatformSuite(',
 'requireCompleteScopes(check.diagnostics,suite)','registerScopes(registry,check.diagnostics.scopes)','validateSnapshot(snapshot,registered,previousSnapshot)',
 'targetComparison(check.diagnostics,check.targetPersisted,firstTarget)','finalizeR6Results(result,manifest)','runUpgradeSentinels(','stopOwnedDatabase('])assert.ok(source.includes(literal),literal);
 assert.match(source,/result\.suites=summary\.fullRegression\.suites/);assert.match(source,/Unsealed runner/);
 assert.match(source,/executePlatformSuite=executePlatformMavenSuite/);assert.match(source,/failureCategory=acceptance\.firstFailure\?\.category/);
 assert.doesNotMatch(source,/-Dsurefire\.reportsDirectory=/);
});

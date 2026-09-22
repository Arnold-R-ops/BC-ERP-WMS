// R6 test-only orchestration and allowlisted fixture evidence. Import opens no database.
import {runSuitePlan,summarizeSuiteResults} from './platform-test-results.mjs';
import {failureDetails} from './platform-process-lifecycle.mjs';
export const TARGET='com.wms.system.platform.service.PlatformAdminInvitationMfaTransactionIntegrationTest';
export const TARGET_METHOD='credentialFailureSurvivesRollbackOfInvitationCreationTransaction';
export const SELECTORS=Object.freeze([
 'com.wms.system.tenant.security.TenantSecurityPostgresTest','com.wms.system.tenant.security.TenantSecurityCommandsPostgresTest',
 'com.wms.system.platform.service.PlatformSessionPostgresTest','com.wms.system.platform.service.PlatformInvitationSessionPostgresTest#invitation*',
 TARGET,'com.wms.system.platform.service.PlatformCompanyDataServiceIntegrationTest',
 'com.wms.system.tenant.security.TenantSecuritySpringPostgresTest','com.wms.system.platform.service.PlatformAccessGrantRoleRaceIntegrationTest']);
const expectedCounts=[4,6,27,4,1,6,14,1];
export function r6Plans(input){
 const r9=input.methodLedgerVersion==='R9_EIGHT_METHODS_ELEVEN_REVISIONS';
 if(input.businessTests?.length!==8||input.methodBodyChanges?.length!==(r9?8:7))throw Error('Revised manifest required: 63 methods and disclosed body changes');
 if(r9){
  if(input.methodRevisionHistory?.length!==11||new Set(input.methodBodyChanges.map(c=>c.path+'#'+c.name)).size!==8)throw Error('R9 revision ledger incomplete');
  for(const c of input.methodBodyChanges){
   if(!input.businessTests.some(e=>e.path===c.path&&e.tests.some(t=>t.name===c.name&&t.sha256===c.afterSha256)))throw Error('R9 current method fingerprint mismatch');
   const revisions=input.methodRevisionHistory.filter(r=>r.path===c.path&&r.name===c.name);
   if(!revisions.length||revisions.at(-1).afterSha256!==c.afterSha256||revisions.some((r,i)=>i>0&&r.beforeSha256!==revisions[i-1].afterSha256))throw Error('R9 revision chain mismatch');
  }
 }
 const plan=SELECTORS.map((selector,i)=>{const name=selector.split('#')[0],file='src/test/java/'+name.replaceAll('.','/')+'.java';
  const matches=input.businessTests.filter(e=>e.path===file);
  if(matches.length!==1||matches[0].tests.length!==expectedCounts[i])throw Error('Suite method manifest mismatch');
  const methods=matches[0].tests.map(t=>t.name);
  if(new Set(methods).size!==methods.length||matches[0].tests.some(t=>!/^[a-f0-9]{64}$/.test(t.sha256)))throw Error('Invalid method fingerprints');
  return {selector,name,methods};});
 if(plan[4].methods[0]!==TARGET_METHOD)throw Error('Wrong MFA target');
 return [{id:'independent',planned:1,plan:[plan[4]]},{id:'sequential',planned:42,plan:plan.slice(0,5)},
  {id:'full',planned:63,plan}];
}
export function summarizeR6(input,phases=[]){
 const plans=r6Plans(input);
 if(new Set(phases.map(p=>p.id)).size!==phases.length||phases.some(p=>!plans.some(q=>q.id===p.id)))throw Error('Unknown phase evidence');
 const summaries=plans.map(p=>{const existing=phases.find(q=>q.id===p.id),s=summarizeSuiteResults(p.plan,existing?.suites??[]);
  return {...s,id:p.id,fixtureStatus:existing?.fixtureStatus??'NOT_EXECUTED',fixtureFailure:existing?.fixtureFailure??null,
   evidence:existing?.evidence??[],failures:existing?.failures??[],firstFailure:existing?.failures?.[0]??null,
   status:existing?.fixtureStatus==='FAILED'||existing?.failures?.length?'FAILED':s.status};});
 const counts={plannedExecutionInstances:106,distinctBusinessMethods:63,passed:0,failures:0,errors:0,skipped:0,unexecuted:0,unverified:0};
 for(const p of summaries)for(const key of ['passed','failures','errors','skipped','unexecuted','unverified'])counts[key]+=p.coverage[key];
 return {status:summaries.every(p=>p.status==='PASSED'&&p.fixtureStatus==='PASSED')?'PASSED':summaries.some(p=>p.status==='FAILED')?'FAILED':'NOT_COMPLETED',
  phases:summaries,firstFailure:summaries.find(p=>p.firstFailure)?.firstFailure??null,executionInstances:counts,fullRegression:summaries[2]};
}
export async function runR6Phases({input,execute,beforeSuite=async()=>{},afterSuite=async()=>{},onUpdate=()=>{}}){
 const phases=[];onUpdate(summarizeR6(input,phases));
 for(const p of r6Plans(input)){
  const row={id:p.id,suites:[],evidence:[],failures:[],fixtureStatus:'RUNNING'};phases.push(row);
  const failure=(suite,category,code,details={})=>row.failures.push({...details,phase:p.id,suite:suite.name,category,code});
  for(const suite of p.plan){
   try{await beforeSuite(p,suite);}catch(error){
    const details=failureDetails(error,'BEFORE_SUITE'),environment=isEnvironmentFailure(error),fixture=error?.category==='FIXTURE_PRECONDITION_ERROR'||/^(FIXTURE_|EXPORT_FIXTURE_)/.test(details.failureStep??'');
    row.fixtureStatus='FAILED';row.fixtureFailure=environment?'ENVIRONMENT_PRECONDITION_FAILED':'FIXTURE_PRECONDITION_FAILED';
    const category=environment?'ENVIRONMENT_PRECONDITION_ERROR':fixture?'FIXTURE_PRECONDITION_ERROR':'PRECONDITION_ERROR';
    failure(suite,category,details.code??(environment?'ENVIRONMENT_PRECONDITION_FAILED':fixture?'FIXTURE_PRECONDITION_FAILED':'PRECONDITION_FAILED'),details);
    onUpdate(summarizeR6(input,phases));return summarizeR6(input,phases);
   }
   let payload;
   const outcome=await runSuitePlan({plan:[suite],execute:async s=>{payload=await execute(p,s);return payload;}});
   row.suites.push(...outcome.suites);
   if(payload?.evidence){row.evidence.push({suite:suite.name,...payload.evidence});for(const issue of payload.evidence.issues??[])failure(suite,issue.category,issue.code);}
   if(outcome.status!=='PASSED'&&!row.failures.length)failure(suite,outcome.failureCategory??'EXECUTION_ERROR',outcome.failureCategory??'SUITE_NOT_VERIFIED');
   try{await afterSuite(p,suite,outcome.suites[0],payload);}catch(error){
    const details=failureDetails(error,'AFTER_SUITE'),environment=isEnvironmentFailure(error);
    row.fixtureStatus='FAILED';row.fixtureFailure=environment?'ENVIRONMENT_CLOSEOUT_FAILED':'FIXTURE_RECONCILIATION_FAILED';
    const category=environment?'ENVIRONMENT_CLOSEOUT_ERROR':
     ['REPORT_PATH_ERROR','REPORT_PARSE_ERROR','FIXTURE_LOG_ERROR','FIXTURE_CLEANUP_ERROR'].includes(error?.category)?error.category:/^(FIXTURE_|EXPORT_FIXTURE_)/.test(details.failureStep??'')?'FIXTURE_CLEANUP_ERROR':'CLOSEOUT_ERROR';
    failure(suite,category,details.code??(environment?'ENVIRONMENT_CLOSEOUT_FAILED':category==='FIXTURE_CLEANUP_ERROR'?'FIXTURE_RECONCILIATION_FAILED':category==='CLOSEOUT_ERROR'?'CLOSEOUT_FAILED':'AFTER_SUITE_EVIDENCE_UNAVAILABLE'),details);
   }
   onUpdate(summarizeR6(input,phases));
   if(outcome.status!=='PASSED'||row.fixtureStatus==='FAILED'||row.failures.length)return summarizeR6(input,phases);
  }
  row.fixtureStatus='PASSED';onUpdate(summarizeR6(input,phases));
 }
 return summarizeR6(input,phases);
}
// Classify by typed/allowlisted metadata, never by exception text or captured output.
function isEnvironmentFailure(error){
 return ['EnvironmentInspectionError','IdentityError'].includes(error?.name)
  ||typeof error?.code==='string'&&/^(?:IDENTITY_|PROCESS_QUERY_)[A-Z0-9_.-]+$/.test(error.code);
}
function ids(value){
 const parsed=value===''?[]:value.split(', ').map(Number);
 if(parsed.some(n=>!Number.isSafeInteger(n)||n<=0)||new Set(parsed).size!==parsed.length)throw Error('Invalid fixture IDs');return parsed;
}
const owner='([A-Za-z0-9_.$]+:[a-f0-9-]{36})';
const start=new RegExp('^PLATFORM_FIXTURE_START owner='+owner+' activeUsers=(\\d+) pendingInvitations=(\\d+)$');
const end=new RegExp('^PLATFORM_FIXTURE_SCOPE owner='+owner+' userIds=\\[([\\d, ]*)\\] invitationIds=\\[([\\d, ]*)\\] tenantIds=\\[([\\d, ]*)\\] status=RETIRED_AUDIT_RETAINED$');
const before=/^MFA_FIXTURE_PRECONDITIONS Before\[enabledSuperAdmins=(\d+), pendingSuperInvitations=(\d+), allPendingInvitations=(\d+), matchingActor=(\d+), matchingChallenge=(\d+), actorAttempts=(-?\d+), challengeAttempts=(-?\d+)\]$/;
const exception=/^MFA_FIXTURE_EXCEPTION type=([A-Za-z0-9_.$]+) errorKey=([A-Z_]+)$/;
export function fixtureDiagnostics(output,suite){
 if(typeof output!=='string'||output.length>8388608)throw Error('Fixture output missing or oversized');
 const starts=[],scopes=[],mfaBefore=[],exceptions=[];
 for(const line of output.split(/\r?\n/)){
  let m;if((m=line.match(start)))starts.push({owner:m[1],activeUsers:Number(m[2]),pendingInvitations:Number(m[3])});
  else if((m=line.match(end)))scopes.push({owner:m[1],userIds:ids(m[2]),invitationIds:ids(m[3]),tenantIds:ids(m[4]),status:'RETIRED_AUDIT_RETAINED'});
  else if((m=line.match(before)))mfaBefore.push(Object.fromEntries(['enabledSuperAdmins','pendingSuperInvitations','allPendingInvitations','matchingActor','matchingChallenge','actorAttempts','challengeAttempts'].map((k,i)=>[k,Number(m[i+1])])));
  else if((m=line.match(exception)))exceptions.push({type:m[1],errorKey:m[2]});
  else if(/^(PLATFORM_FIXTURE_|MFA_FIXTURE_)/.test(line))throw Error('Malformed allowlisted diagnostic; raw content withheld');
 }
 if([...starts,...scopes].some(s=>!s.owner.startsWith(suite.name+'.')))throw Error('Wrong fixture suite owner');
 return {starts,scopes,mfaBefore,exceptions};
}
export function requireCompleteScopes(diag,suite){
 const expected=suite.name.endsWith('.TenantSecurityPostgresTest')?1:suite.methods.length;
 if(diag.starts.length!==expected||diag.scopes.length!==expected||new Set(diag.starts.map(s=>s.owner)).size!==expected||new Set(diag.scopes.map(s=>s.owner)).size!==expected
  ||diag.starts.some(s=>s.activeUsers!==0||s.pendingInvitations!==0||!diag.scopes.some(e=>e.owner===s.owner)))throw Error('Fixture scopes incomplete or contaminated');
}
const rootKeys=['users','invitations','tenants'];
export function newRegistry(){return {users:new Set(),invitations:new Set(),tenants:new Set(),owners:new Set()};}
export function registerScopes(registry,scopes){
 const next={...Object.fromEntries(rootKeys.map(k=>[k,new Set(registry[k])])),owners:new Set(registry.owners)};
 for(const scope of scopes){if(next.owners.has(scope.owner))throw Error('Duplicate fixture owner');next.owners.add(scope.owner);
  for(const [key,field] of [['users','userIds'],['invitations','invitationIds'],['tenants','tenantIds']])for(const id of scope[field]){
   if(next[key].has(id))throw Error('Fixture ID already owned');next[key].add(id);}}
 return next;
}
export function validateSnapshot(snapshot,registry,previous=null){
 if(!snapshot||Object.keys(snapshot).sort().join(',')!=='activeUsers,audits,invitations,pendingInvitations,roles,tenants,users')throw Error('Unexpected snapshot projection');
 for(const key of rootKeys){if(!Array.isArray(snapshot[key])||new Set(snapshot[key]).size!==snapshot[key].length||snapshot[key].some(id=>!Number.isSafeInteger(id)||!registry[key].has(id)))throw Error('Unknown residual '+key);}
 if(snapshot.activeUsers!==0||snapshot.pendingInvitations!==0)throw Error('Active fixture seats remain');
 for(const key of ['roles','audits'])if(!Array.isArray(snapshot[key])||new Set(snapshot[key].map(r=>r.id)).size!==snapshot[key].length||snapshot[key].some(r=>Object.keys(r).sort().join(',')!=='hash,id'||!Number.isSafeInteger(r.id)||!/^[a-f0-9]{32}$/.test(r.hash)))throw Error('Invalid immutable evidence');
 if(snapshot.roles.length!==7)throw Error('Baseline role catalogue mismatch');
 if(previous){if(JSON.stringify(previous.roles)!==JSON.stringify(snapshot.roles))throw Error('Public roles changed');
  if(previous.audits.some(r=>!snapshot.audits.some(s=>s.id===r.id&&s.hash===r.hash)))throw Error('Immutable audit changed or deleted');}
 return snapshot;
}
export const ROOT_SNAPSHOT_SQL=`BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT json_build_object(
 'users',coalesce((select json_agg(id order by id) from platform_users),'[]'::json),
 'invitations',coalesce((select json_agg(id order by id) from platform_admin_invitations),'[]'::json),
 'tenants',coalesce((select json_agg(id order by id) from tenants),'[]'::json),
 'activeUsers',(select count(*) from platform_users where enabled),
 'pendingInvitations',(select count(*) from platform_admin_invitations where accepted_at is null and revoked_at is null and expires_at>clock_timestamp()),
 'roles',(select json_agg(v order by id) from (select id,md5(row_to_json(r)::text) as hash from platform_roles r) v),
 'audits',coalesce((select json_agg(v order by id) from (select id,md5(row_to_json(a)::text) as hash from platform_audit_logs a) v),'[]'::json));
COMMIT;`;
export function targetStateSql(actorId){
 if(!Number.isSafeInteger(actorId)||actorId<=0)throw Error('Invalid owned actor ID');
 return `select json_build_object('actorCount',(select count(*) from platform_users where id=${actorId}),
 'disabled',(select not enabled from platform_users where id=${actorId}),
 'actorAttempts',(select mfa_failed_attempts from platform_users where id=${actorId}),
 'locked',(select mfa_locked_until>clock_timestamp() from platform_users where id=${actorId}),
 'challengeCount',(select count(*) from platform_mfa_challenges where platform_user_id=${actorId} and purpose='ADMIN_INVITE'),
 'challengeAttempts',(select attempt_count from platform_mfa_challenges where platform_user_id=${actorId} and purpose='ADMIN_INVITE'),
 'challengeConsumed',(select consumed_at is not null from platform_mfa_challenges where platform_user_id=${actorId} and purpose='ADMIN_INVITE'),
 'invitationCount',(select count(*) from platform_admin_invitations where invited_by_platform_user_id=${actorId}));`;
}
export function targetComparison(diag,persisted,previous=null){
 const expectedBefore={enabledSuperAdmins:1,pendingSuperInvitations:0,allPendingInvitations:0,matchingActor:1,matchingChallenge:1,actorAttempts:4,challengeAttempts:4};
 const expectedException={type:'com.wms.system.platform.service.PlatformAdminInvitationMfaCredentialException',errorKey:'AUTH_FAILED'};
 const expectedAfter={actorCount:1,disabled:true,actorAttempts:5,locked:true,challengeCount:1,challengeAttempts:5,challengeConsumed:true,invitationCount:0};
 if(JSON.stringify(diag.mfaBefore)!==JSON.stringify([expectedBefore])||JSON.stringify(diag.exceptions)!==JSON.stringify([expectedException])||JSON.stringify(persisted)!==JSON.stringify(expectedAfter))throw Error('Target MFA branch or persistence mismatch');
 const value={before:diag.mfaBefore[0],exception:diag.exceptions[0],persisted};
 if(previous&&JSON.stringify(value)!==JSON.stringify(previous))throw Error('Target result changed with preceding suites');
 return value;
}

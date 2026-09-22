// Query failure evidence is exercised through the real batch's exported stage engine.
// No OS query, PostgreSQL process, driver, SQL connection or Maven process is started.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {runR11Acceptance,finalizeR11Results} from './platform-r11-database-batch.mjs';
import {EnvironmentInspectionError} from './platform-launch-preflight.mjs';
import {IdentityError} from './platform-process-lifecycle.mjs';
const input=JSON.parse(fs.readFileSync('D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260918/r10/backend-input-manifest.json','utf8'));
const processOkay={exitCode:0,exited:true,outputComplete:true,terminationConfirmed:true};
const xml=(suite,failed=false)=>`<testsuite name="${suite.name}" tests="${suite.methods.length}" failures="${failed?1:0}" errors="0" skipped="0">${suite.methods.map((name,i)=>`<testcase classname="${suite.name}" name="${name}">${failed&&i===0?'<failure/>':''}</testcase>`).join('')}</testsuite>`;
const execute=async(_phase,suite)=>({process:processOkay,xml:xml(suite)});
function queryError(code='IDENTITY_QUERY_EXECUTOR_FAILED',stage='QUERY_EXECUTOR'){
 const error=new EnvironmentInspectionError(code,stage,{status:'QUERY_FAILED',stage,pid:46128,category:'EXECUTOR_TIMEOUT'});
 error.failureStep='OS_IDENTITY_BEFORE_SUITE';
 error.queryExecution={commandIndex:267,budgetMs:15000,elapsedMs:432347,timedOut:true,terminationConfirmed:true,
  receipt:{pid:46128,parentPid:45940,startedAt:'2026-09-18T15:07:41.760Z',exitAt:'2026-09-18T15:14:54.107Z',exited:true,terminationConfirmed:true},
  timing:{timeoutMs:15000,wallElapsedMs:432347,monotonicElapsedMs:432347.1116,
   events:[{event:'TIMEOUT_FIRED',at:'2026-09-18T15:14:54.076Z',wallElapsedMs:432316,monotonicElapsedMs:432315.295,latenessMs:417315.2663}]}};
 return error;
}
test('real entry retains query timeout identity, command link, actual timing and zero business credit',async()=>{
 let invoked=0;const result=await runR11Acceptance({input,beforeSuite:async()=>{throw queryError();},execute:async()=>{invoked++;}});
 assert.equal(invoked,0);assert.equal(result.status,'FAILED');assert.equal(result.executionInstances.unexecuted,106);
 const first=result.firstFailure;assert.equal(first.category,'ENVIRONMENT_PRECONDITION_ERROR');assert.equal(first.code,'IDENTITY_QUERY_EXECUTOR_FAILED');
 assert.equal(first.name,'EnvironmentInspectionError');assert.equal(first.stage,'BEFORE_SUITE');assert.equal(first.queryStage,'QUERY_EXECUTOR');
 assert.equal(first.failureStep,'OS_IDENTITY_BEFORE_SUITE');assert.equal(first.queryExecution.commandIndex,267);
 assert.equal(first.queryExecution.timing.timeoutMs,15000);assert.equal(first.queryExecution.timing.events[0].latenessMs,417315.2663);
 assert.equal(first.queryExecution.receipt.pid,46128);assert.equal(first.queryExecution.terminationConfirmed,true);
 assert.equal(result.phases[0].fixtureFailure,'ENVIRONMENT_PRECONDITION_FAILED');
});
for(const [code,stage] of [['IDENTITY_QUERY_INVALID_REPORT','QUERY_PARSE'],['IDENTITY_QUERY_ACCESS_DENIED','postgres'],['PROCESS_QUERY_ACCESS_DENIED','ANCESTRY_PARENT']])
 test('query subtype preserved without claiming fixture failure: '+code,async()=>{
  const result=await runR11Acceptance({input,beforeSuite:async()=>{throw queryError(code,stage);},execute});
  assert.equal(result.firstFailure.code,code);assert.equal(result.firstFailure.queryStage,stage);
  assert.equal(result.firstFailure.category,'ENVIRONMENT_PRECONDITION_ERROR');assert.equal(result.executionInstances.passed,0);
 });
test('identity mismatch without query execution is still environment failure',async()=>{
 const result=await runR11Acceptance({input,beforeSuite:async()=>{throw new IdentityError('synthetic mismatch');},execute});
 assert.equal(result.firstFailure.name,'IdentityError');assert.equal(result.firstFailure.category,'ENVIRONMENT_PRECONDITION_ERROR');
 assert.equal(result.firstFailure.code,'ENVIRONMENT_PRECONDITION_FAILED');assert.equal(Object.hasOwn(result.firstFailure,'queryExecution'),false);
});
test('explicit fixture failure retains its own category and exact allowlisted code',async()=>{
 const error=Object.assign(new Error('unknown synthetic row'),{category:'FIXTURE_PRECONDITION_ERROR',code:'FIXTURE_UNKNOWN_RESIDUAL',failureStep:'FIXTURE_SNAPSHOT_VALIDATE'});
 const result=await runR11Acceptance({input,beforeSuite:async()=>{throw error;},execute});
 assert.equal(result.firstFailure.category,'FIXTURE_PRECONDITION_ERROR');assert.equal(result.firstFailure.code,'FIXTURE_UNKNOWN_RESIDUAL');
 assert.equal(result.firstFailure.failureStep,'FIXTURE_SNAPSHOT_VALIDATE');assert.equal(result.executionInstances.passed,0);
});
test('unknown precondition is not mislabeled as fixture corruption',async()=>{
 const result=await runR11Acceptance({input,beforeSuite:async()=>{throw new Error('synthetic unexplained failure');},execute});
 assert.equal(result.firstFailure.category,'PRECONDITION_ERROR');assert.equal(result.firstFailure.code,'PRECONDITION_FAILED');
 assert.equal(result.firstFailure.name,'Error');assert.equal(result.executionInstances.unexecuted,106);
});
test('exception messages, stack, arbitrary diagnostics and raw command material never enter phase evidence',async()=>{
 const sentinel='PRIVATE-MFA-JWT-PASSWORD-SENTINEL',error=queryError();
 error.message=sentinel;error.stack=sentinel;error.evidence={password:sentinel};error.password=sentinel;
 error.queryExecution.output=sentinel;error.queryExecution.args=[sentinel];error.queryExecution.password=sentinel;
 error.queryExecution.receipt.commandLine=sentinel;error.queryExecution.timing.events[0].rawOutput=sentinel;
 error.diagnostics=[{stage:'QUERY_EXECUTOR',pid:46128,status:'QUERY_FAILED',category:'EXECUTOR_TIMEOUT',password:sentinel}];
 const updates=[];const result=await runR11Acceptance({input,beforeSuite:async()=>{throw error;},execute,onUpdate:s=>updates.push(s)});
 assert.ok(!JSON.stringify({result,updates}).includes(sentinel));assert.equal(result.firstFailure.queryExecution.commandIndex,267);
});
test('business first cause survives environment closeout failure in chronological order',async()=>{
 const error=queryError('PROCESS_QUERY_EXECUTOR_FAILED','ANCESTRY_PARENT');error.failureStep='OS_IDENTITY_AFTER_SUITE';
 const result=await runR11Acceptance({input,execute:async(_p,s)=>({process:{...processOkay,exitCode:1},xml:xml(s,true)}),afterSuite:async()=>{throw error;}});
 const failures=result.phases[0].failures;
 assert.equal(result.firstFailure.category,'BUSINESS_TEST_FAILED');assert.equal(failures.length,2);
 assert.equal(failures[1].category,'ENVIRONMENT_CLOSEOUT_ERROR');assert.equal(failures[1].code,'PROCESS_QUERY_EXECUTOR_FAILED');
 assert.equal(failures[1].stage,'AFTER_SUITE');assert.equal(failures[1].failureStep,'OS_IDENTITY_AFTER_SUITE');
 assert.equal(failures[1].queryExecution.commandIndex,267);assert.equal(result.executionInstances.failures,1);
 assert.equal(result.fullRegression.coverage.unexecuted,63);
});
for(const category of ['REPORT_PATH_ERROR','REPORT_PARSE_ERROR','FIXTURE_LOG_ERROR','FIXTURE_CLEANUP_ERROR'])
 test('explicit closeout classification remains separate: '+category,async()=>{
  const error=Object.assign(new Error('synthetic after-stage issue'),{category,code:'EXPECTED_EVIDENCE_MISSING',failureStep:'AFTER_EVIDENCE'});
  const result=await runR11Acceptance({input,execute,afterSuite:async()=>{throw error;}});
  assert.equal(result.firstFailure.category,category);assert.equal(result.firstFailure.code,'EXPECTED_EVIDENCE_MISSING');
  assert.equal(result.firstFailure.stage,'AFTER_SUITE');assert.equal(result.executionInstances.passed,1);
 });
test('unknown after-suite failure is a closeout failure, not a claimed fixture cause',async()=>{
 const result=await runR11Acceptance({input,execute,afterSuite:async()=>{throw Error('unknown after-suite failure');}});
 assert.equal(result.firstFailure.category,'CLOSEOUT_ERROR');assert.equal(result.firstFailure.code,'CLOSEOUT_FAILED');
 assert.equal(result.executionInstances.passed,1);assert.equal(result.executionInstances.unexecuted,105);
});
test('full-stage identity failure preserves same-run 1/42 outcomes but credits no full business methods',async()=>{
 const calls=[];const result=await runR11Acceptance({input,
  beforeSuite:async p=>{if(p.id==='full')throw queryError();},
  execute:async(p,s)=>{calls.push(p.id);return execute(p,s);}});
 assert.deepEqual(result.phases.map(p=>p.coverage.passed),[1,42,0]);assert.equal(result.fullRegression.coverage.unexecuted,63);
 assert.equal(result.executionInstances.passed,43);assert.equal(result.executionInstances.unexecuted,63);
 assert.equal(result.firstFailure.phase,'full');assert.equal(result.firstFailure.category,'ENVIRONMENT_PRECONDITION_ERROR');
 assert.equal(calls.filter(p=>p==='full').length,0);assert.equal(calls.length,6);
 const final={status:'PASSED',r11:result};finalizeR11Results(final,input);
 assert.equal(final.status,'FAILED');assert.equal(final.coverage.passed,0);assert.equal(final.coverage.unexecuted,63);
 assert.equal(final.r11.firstFailure.queryExecution.commandIndex,267);
});

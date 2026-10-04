// Targeted evidence contracts only; commands are injected, not OS/database calls.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {inspectWindowsEnvironment,queryWindowsProcess,requireInspectionCapability} from './platform-launch-preflight.mjs';
import {failureDetails,withFailureStep,queryExecutionDetails} from './platform-process-lifecycle.mjs';
import {BUDGETS} from './platform-r11-plan.mjs';
import {BUDGETS as R10_BUDGETS} from 'file:///D:/ERP_WMS/BC%20warehouse/2G/target/platform-db-acceptance-20260918/r10/snapshot/source/src/test/scripts/platform-r10-plan.mjs';
const options={directory:'D:/synthetic-only/pgdata',port:55449,ownerPid:111,ownerExecutable:'D:/synthetic/node.exe'};
const timing={timeoutMs:15000,wallElapsedMs:432347,monotonicElapsedMs:432347,events:[{event:'TIMEOUT_FIRED',at:'2026-09-18T15:14:54.076Z',latenessMs:417315}]};
const result=output=>({commandIndex:267,elapsedMs:432347,exited:true,exitCode:0,outputComplete:true,terminationConfirmed:true,timedOut:false,timing,receipt:{pid:46128,parentPid:45940},output});
test('full query timeout carries recorded command index and original timing into failureDetails',async()=>{
 await assert.rejects(()=>inspectWindowsEnvironment(async()=>({...result('private'),timedOut:true,exitCode:null}),options),error=>{
  const d=failureDetails(error,'BEFORE_SUITE');assert.equal(d.code,'IDENTITY_QUERY_EXECUTOR_FAILED');assert.equal(d.queryExecution.commandIndex,267);
  assert.equal(d.queryExecution.budgetMs,15000);assert.equal(d.queryExecution.elapsedMs,432347);assert.deepEqual(d.queryExecution.timing,timing);assert.ok(!JSON.stringify(d).includes('private'));return true;
 });
});
test('invalid full JSON retains executor evidence without raw content',async()=>{
 await assert.rejects(()=>inspectWindowsEnvironment(async()=>result('private damaged JSON'),options),error=>{
  const d=failureDetails(error,'BEFORE_SUITE');assert.equal(d.code,'IDENTITY_QUERY_INVALID_REPORT');assert.equal(d.queryExecution.commandIndex,267);assert.ok(!JSON.stringify(d).includes('private'));return true;
 });
});
test('capability rejection after valid JSON also retains original command link',async()=>{
 const r=await inspectWindowsEnvironment(async()=>result(JSON.stringify({schema:1,target:{directory:options.directory,port:55449},context:{status:'ACCESS_DENIED'}})),options);
 assert.throws(()=>requireInspectionCapability(r),error=>{assert.equal(error.code,'IDENTITY_QUERY_ACCESS_DENIED');assert.equal(failureDetails(error,'BEFORE_SUITE').queryExecution.commandIndex,267);return true;});
});
for(const [label,output,extra,code] of [
 ['timeout','private',{timedOut:true,exitCode:null},'PROCESS_QUERY_EXECUTOR_FAILED'],
 ['damaged','private',{},'PROCESS_QUERY_INVALID_REPORT'],
 ['denied',JSON.stringify({pid:123,stage:'PARENT',status:'ACCESS_DENIED',missingFields:['CommandLine']}),{},'PROCESS_QUERY_ACCESS_DENIED']])
 test('single PID '+label+' retains 5s budget, index and evidence',async()=>{
  await assert.rejects(()=>queryWindowsProcess(async()=>({...result(output),...extra}),{pid:123,stage:'PARENT'}),error=>{
   const d=failureDetails(error,'CLEANUP');assert.equal(d.code,code);assert.equal(d.queryExecution.commandIndex,267);assert.equal(d.queryExecution.budgetMs,5000);assert.ok(!JSON.stringify(d).includes('private'));return true;
  });
 });
test('failure step wrapper preserves type/code and earliest nested step',async()=>{
 const error=Object.assign(new Error('private'),{name:'EnvironmentInspectionError',code:'IDENTITY_QUERY_EXECUTOR_FAILED'});
 await assert.rejects(()=>withFailureStep('OUTER',()=>withFailureStep('INNER',()=>{throw error;})),e=>{assert.equal(e,error);assert.equal(e.failureStep,'INNER');return true;});
});
for(const kind of ['full','pid'])test(kind+' query rejects null/scalar/array without losing typed evidence',async()=>{
 for(const value of [null,3,[]])await assert.rejects(()=>kind==='full'
  ?inspectWindowsEnvironment(async()=>result(JSON.stringify(value)),options)
  :queryWindowsProcess(async()=>result(JSON.stringify(value)),{pid:123,stage:'PARENT'}),error=>{
   assert.equal(error.name,'EnvironmentInspectionError');assert.equal(error.code,kind==='full'?'IDENTITY_QUERY_INVALID_REPORT':'PROCESS_QUERY_INVALID_REPORT');assert.equal(failureDetails(error,'PRECHECK').queryExecution.commandIndex,267);return true;
  });
});
test('non-error throw is withheld, not silently dropped or leaked',async()=>{
 await assert.rejects(()=>withFailureStep('BEFORE_SUITE',()=>{throw 'private';}),e=>{const d=failureDetails(e,'PRECHECK');assert.equal(d.name,'NonErrorThrown');assert.equal(d.failureStep,'BEFORE_SUITE');assert.ok(!JSON.stringify(d).includes('private'));return true;});
});
test('unsafe metadata and arbitrary payload fields are removed recursively',()=>{
 const d=failureDetails({name:'private request with spaces',code:'private request',stage:'private request',message:'private',diagnostics:[{stage:'QUERY',password:'private',missingFields:['CommandLine','private request']}],queryExecution:{commandIndex:-1,args:['private'],receipt:{pid:123,commandLine:'private'},timing:{events:[{event:'TIMEOUT_FIRED',output:'private'}]}}},'BEFORE_SUITE');
 assert.equal(d.name,'Error');assert.equal(d.queryExecution.commandIndex,undefined);assert.ok(!JSON.stringify(d).includes('private'));assert.deepEqual(d.diagnostics[0].missingFields,['CommandLine']);
 assert.equal(queryExecutionDetails(null),undefined);
});
test('actual batch binds index, precise steps and sanitized after-suite error',()=>{
 const source=fs.readFileSync(new URL('./platform-r11-database-batch.mjs',import.meta.url),'utf8');
 for(const value of ['commandIndex:commandRecords.length-1',"withFailureStep('OS_IDENTITY_BEFORE_SUITE'","withFailureStep('OS_IDENTITY_FOR_SQL'","withFailureStep('ARTIFACT_BEFORE_SUITE'","check.failure=failureDetails(error,'AFTER_SUITE')",'result.firstFailure=acceptance.firstFailure','result.closeoutErrors.push(failureDetails'])assert.ok(source.includes(value),value);
 assert.ok(!source.includes('check.failure=redact(error.message)'));
});
test('runProcess scheduling and every fixed budget are unchanged from frozen r10',()=>{
 const original='D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260918/r10/snapshot/source/src/test/scripts/';
 const now=fs.readFileSync(new URL('./platform-process-lifecycle.mjs',import.meta.url),'utf8'),then=fs.readFileSync(original+'platform-process-lifecycle.mjs','utf8');
 const body=s=>s.slice(s.indexOf('export function runProcess('),s.indexOf('export class IdentityError'));
 assert.equal(body(now),body(then));assert.deepEqual(BUDGETS,R10_BUDGETS);
 assert.equal(BUDGETS.fullQueryMs,15000);assert.equal(BUDGETS.cleanupMs,60000);
});
test('fixed event window and limited query collector cannot start or connect a database',()=>{
 const events=fs.readFileSync(new URL('./platform-query-event-window.ps1',import.meta.url),'utf8');
 assert.ok(events.includes('2026-09-18T15:05:00Z')&&events.includes('2026-09-18T15:17:00Z'));
 assert.doesNotMatch(events,/Start-Process|Set-Service|Stop-Service|wevtutil|powercfg|pg_ctl|initdb|psql/i);
 const runner=fs.readFileSync(new URL('./platform-query-diagnostics.mjs',import.meta.url),'utf8');
 assert.ok(runner.includes('attempts>=3'));assert.ok(runner.includes("flag:'wx'"));assert.ok(runner.includes('directory:TARGET,port:55449'));
 assert.doesNotMatch(runner,/createR10Environment|startOwnedDatabase|stopOwnedDatabase|pg_ctl\.exe|psql\.exe|initdb\.exe/);
});

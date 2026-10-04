// Synthetic OS contracts only; actual Windows permission checks are separately recorded read-only.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {inspectWindowsEnvironment,requireInspectionCapability,confirmTargetAbsent,contextBinding,initializationPreflight,readPidFile,POWERSHELL} from './platform-launch-preflight.mjs';
import {withInitializationPreflight} from './platform-r7-database-batch.mjs';
const expected={directory:'D:/synthetic-platform/pgdata',port:55449,ownerPid:123,ownerExecutable:'D:/synthetic-tools/node.exe'};
const proof=(pid,executable)=>({status:'AVAILABLE',pid,parentPid:123,executable,commandLineReadable:true,commandLineSha256:'a'.repeat(64),creationEpoch:100,ownerSid:'synthetic-sid'});
const report=()=>({schema:1,checkedAt:'synthetic-time',target:{directory:expected.directory,port:55449},expected,
 context:{status:'AVAILABLE',user:'synthetic-user',sid:'synthetic-sid',restrictedToken:false,administratorEnabled:false,elevated:false,elevationType:1,powerShellPid:124,powerShellPath:POWERSHELL},
 self:proof(124,POWERSHELL),owner:proof(123,expected.ownerExecutable),
 postgres:{status:'AVAILABLE',count:1,incomplete:0,targetMatches:[],processIds:[1000],parentIds:[999]},
 listeners:{status:'AVAILABLE',total:1,malformed:0,selected:[{address:'0.0.0.0',port:5432,pid:1000}],pidProofs:[proof(1000,'D:/synthetic-tools/postgres.exe')]},
 directory:{status:'ABSENT'},pidFile:{status:'ABSENT'}});
const success=output=>({exited:true,exitCode:0,timedOut:false,aborted:false,spawnError:null,outputComplete:true,terminationConfirmed:true,output:JSON.stringify(output)});
test('actual database entry exports the same initialization capability gate',()=>assert.equal(withInitializationPreflight,initializationPreflight));
test('actual entry gates password creation and initdb, rechecks launch, and uses capability-checked cleanup',()=>{
 const text=fs.readFileSync(new URL('./platform-r7-database-batch.mjs',import.meta.url),'utf8');
 const gate=text.indexOf('const init=await withInitializationPreflight('),password=text.indexOf('fs.writeFileSync(pwfile,'),init=text.indexOf("return command(PG+'/initdb.exe'");
 assert.ok(gate>0&&password>gate&&init>password);
 assert.ok(text.indexOf('result.beforeStartEnvironment=await inspectEnvironment()')<text.indexOf('attemptedStart=true'));
 assert.ok(text.includes('requireInspectionCapability(evidence,{context:executionContext})'));
 assert.ok(text.includes("confirmTargetAbsent(evidence,pinned).status==='CONFIRMED_ABSENT'"));
 assert.ok(text.includes("result.firstFailure??=result.lifecycle.firstFailure"));
 assert.ok(text.includes("status:'NOT_STARTED_NO_STOP_PERFORMED'"));
 assert.ok(!text.includes('else result.stop.completed=!listeners()'));
 assert.ok(text.includes('readPidFile(DATA+'));
 assert.ok(text.includes("['identityPreflight','platform-launch-preflight.test.mjs',PREFLIGHT_TEST_COUNT]"));
});
test('read-only PowerShell payload has no database/service command or policy mutation',()=>{
 const text=fs.readFileSync(new URL('./platform-windows-identity.ps1',import.meta.url),'utf8');
 assert.doesNotMatch(text,/pg_ctl|initdb|psql|Stop-Process|Start-Process|Set-ExecutionPolicy|Set-Service|Stop-Service|Start-Service/i);
 assert.match(text,/Get-CimInstance Win32_Process/);assert.match(text,/GetOwnerSid/);assert.match(text,/IsTokenRestricted/);
});
test('positive capability proves parent identity, command lines and an existing unrelated listener PID',()=>{
 const r=report();assert.equal(requireInspectionCapability(r),r);assert.equal(confirmTargetAbsent(r).status,'CONFIRMED_ABSENT');
});
test('zero postgres rows is legitimate only with positive self and owner proof',()=>{
 const r=report();Object.assign(r.postgres,{count:0,processIds:[],parentIds:[]});assert.equal(requireInspectionCapability(r),r);
 r.self.status='ABSENT';assert.throws(()=>confirmTargetAbsent(r),{code:'IDENTITY_QUERY_ABSENT'});
});
for(const key of ['context','self','owner','postgres','listeners','directory','pidFile'])for(const status of ['ACCESS_DENIED','QUERY_FAILED','INCOMPLETE']){
 test(key+' '+status+' blocks initialization without creating even a password file',async()=>{
  const r=report();r[key]={status};let initialized=false,recorded;
  await assert.rejects(()=>withInitializationPreflight({inspect:async()=>r,record:row=>{recorded=row;},initialize:()=>{initialized=true;}}),{code:'IDENTITY_QUERY_'+status});
  assert.equal(initialized,false);assert.equal(recorded.status,'BLOCKED');assert.equal(recorded.evidence,r);
  assert.throws(()=>confirmTargetAbsent(r));
 });
}
test('recording failure before initialization prevents writes',async()=>{
 let initialized=false;await assert.rejects(()=>initializationPreflight({inspect:async()=>report(),record:()=>{throw Error('record failed');},initialize:()=>{initialized=true;}}),/record failed/);assert.equal(initialized,false);
});
test('successful gate records evidence before initialization and preserves initialization failure',async()=>{
 const steps=[],error=Error('synthetic initialization failure');
 await assert.rejects(()=>initializationPreflight({inspect:async()=>report(),record:r=>{assert.equal(r.status,'PASSED');steps.push('record');},initialize:()=>{steps.push('initialize');throw error;}}),e=>e===error);
 assert.deepEqual(steps,['record','initialize']);
});
test('existing directory is never reused even when no process or port is present',async()=>{
 const r=report();r.directory.status='PRESENT';await assert.rejects(()=>initializationPreflight({inspect:async()=>r,initialize:()=>assert.fail('must not initialize')}),{code:'INITIALIZATION_DIRECTORY_EXISTS'});
});
test('PID file, matching command directory, listening port or pinned descendant each prevents absence claim',()=>{
 for(const mutate of [r=>{r.pidFile.status='PRESENT';},r=>r.postgres.targetMatches.push({pid:987}),r=>{r.listeners.selected[0].port=55449;},r=>{r.postgres.processIds[0]=987;},r=>{r.postgres.parentIds[0]=987;}]){
  const r=report();mutate(r);assert.equal(confirmTargetAbsent(r,{pid:987}).status,'PRESENT');
 }
 assert.throws(()=>confirmTargetAbsent(report(),{pid:'987'}),{code:'INVALID_PINNED_PID'});
});
test('occupied target blocks the real entry gate',async()=>{
 const r=report();r.listeners.selected[0].port=55449;
 await assert.rejects(()=>withInitializationPreflight({inspect:async()=>r,initialize:()=>assert.fail('must not initialize')}),{code:'INITIALIZATION_TARGET_OCCUPIED'});
});
test('wrong report target or missing schema is rejected',()=>{
 for(const mutate of [r=>{r.schema=2;},r=>{r.target.port=5432;},r=>{r.target.directory='D:/wrong';},r=>{delete r.expected;}]){const r=report();mutate(r);assert.throws(()=>requireInspectionCapability(r),{code:'IDENTITY_REPORT_BINDING_MISMATCH'});}
});
test('changing OS user/token between init and launch is rejected',()=>{
 const r=report(),binding=contextBinding(r);assert.equal(requireInspectionCapability(r,{context:binding}),r);
 r.context.restrictedToken=true;assert.throws(()=>requireInspectionCapability(r,{context:binding}),{code:'IDENTITY_CONTEXT_CHANGED'});
});
test('wrong parent PID, command line, executable, owner SID or token data is incomplete, not absent',()=>{
 for(const mutate of [r=>{r.self.parentPid=99;},r=>{r.owner.commandLineReadable=false;},r=>{r.owner.commandLineSha256='';},r=>{r.owner.executable='D:/wrong/node.exe';},r=>{r.owner.ownerSid='wrong';},r=>{r.self.ownerSid='wrong';},r=>{r.self.pid=111;},r=>{delete r.context.elevated;},r=>{r.context.elevationType=0;}]){
  const r=report();mutate(r);assert.throws(()=>requireInspectionCapability(r),{code:'IDENTITY_CONTEXT_INCOMPLETE'});
 }
});
test('incomplete process rows, listener output or listener PID proof cannot confirm absence',()=>{
 for(const mutate of [r=>{r.postgres.incomplete=1;},r=>{r.postgres.processIds=[];},r=>{r.postgres.parentIds=[];},r=>{delete r.postgres.targetMatches;},r=>{r.listeners.malformed=1;},r=>{r.listeners.pidProofs=[];},r=>{r.listeners.pidProofs[0].status='ACCESS_DENIED';},r=>{r.listeners.pidProofs[0].pid=22;},r=>{r.listeners.selected[0].pid=0;}]){
  const r=report();mutate(r);assert.throws(()=>requireInspectionCapability(r),{code:'IDENTITY_INVENTORY_INCOMPLETE'});
 }
});
test('PID file not found differs from denied or damaged reads',()=>{
 for(const code of ['ENOENT','ENOTDIR'])assert.equal(readPidFile('synthetic',()=>{throw Object.assign(Error(),{code});}),null);
 for(const code of ['EACCES','EPERM'])assert.throws(()=>readPidFile('synthetic',()=>{throw Object.assign(Error(),{code});}),{code:'PID_FILE_ACCESS_DENIED'});
 assert.throws(()=>readPidFile('synthetic',()=>{throw Object.assign(Error(),{code:'EIO'});}),{code:'PID_FILE_READ_FAILED'});
 assert.equal(readPidFile('synthetic',()=>'123\n'), '123\n');
});
test('same command adapter receives inline read-only inspection with fixed PowerShell and bounded timeout',async()=>{
 let call;const r=await inspectWindowsEnvironment(async(...args)=>{call=args;return success(report());},expected);
 assert.equal(call[0],POWERSHELL);assert.deepEqual(call[1].slice(0,3),['-NoProfile','-NonInteractive','-Command']);
 assert.ok(call[1][3].includes('-OwnerPid 123'));assert.equal(call[2].timeout,15000);assert.equal(r.expected.ownerPid,123);
 assert.doesNotMatch(call[1].join(' '),/ExecutionPolicy\s+Bypass/i);
});
test('failed/truncated/timed-out query executor cannot return an empty successful process inventory',async()=>{
 for(const fields of [{exitCode:1},{exited:false},{timedOut:true},{aborted:true},{spawnError:'EACCES'},{outputComplete:false},{terminationConfirmed:false}])
  await assert.rejects(()=>inspectWindowsEnvironment(async()=>({...success(report()),...fields}),expected),{code:'IDENTITY_QUERY_EXECUTOR_FAILED'});
});
test('invalid JSON and invalid arguments stop before any initialization',async()=>{
 await assert.rejects(()=>inspectWindowsEnvironment(async()=>({...success(report()),output:'not-json'}),expected),{code:'IDENTITY_QUERY_INVALID_REPORT'});
 for(const patch of [{directory:'relative'},{port:5432.5},{ownerPid:0}])await assert.rejects(()=>inspectWindowsEnvironment(()=>assert.fail('must not execute'),{...expected,...patch}),{code:'INVALID_INSPECTION_TARGET'});
});
test('query exceptions keep the original cause and never convert it to absence',async()=>{
 const error=Object.assign(Error('synthetic private message'),{code:'EACCES'});let record;
 await assert.rejects(()=>initializationPreflight({inspect:async()=>{throw error;},record:r=>{record=r;},initialize:()=>assert.fail('must not execute')}),e=>e===error);
 assert.equal(record.status,'BLOCKED');assert.equal(record.code,'EACCES');assert.ok(!JSON.stringify(record).includes('private message'));
});

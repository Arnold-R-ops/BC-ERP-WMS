// Real, disposable Node children only. No SQL, PostgreSQL program, network listener or existing PID termination.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
import {runProcess,startOwnedDatabase,stopOwnedDatabase} from './platform-process-lifecycle.mjs';
const record=(name,value)=>{if(process.env.P3_TOOL_EVIDENCE_DIR)fs.writeFileSync(process.env.P3_TOOL_EVIDENCE_DIR+'/'+name+'.json',JSON.stringify(value,null,2)+'\n',{flag:'wx'});};
const hanging=['-e','setTimeout(()=>{},10000)'];
const names=r=>r.timing.events.map(e=>e.event);
const expected={directory:'D:/synthetic-only/pgdata',executable:'D:/synthetic-only/postgres.exe',port:55449,address:'127.0.0.1',clusterId:'synthetic',earliestStartEpoch:100,latestStartEpoch:110};
const local=()=>({pid:123,processPid:123,directory:expected.directory,commandDirectory:expected.directory,executable:expected.executable,port:55449,address:'127.0.0.1',clusterId:'synthetic',startedEpoch:105,processStartedEpoch:105,listeners:[]});
test('real normal child has handle receipt and confirmed exit without timeout/termination request',async()=>{
 const r=await runProcess(process.execPath,['-e',"console.log('synthetic-normal')"],{timeoutMs:5000});record('real-normal',r);
 assert.equal(r.exitCode,0);assert.equal(r.outputComplete,true);assert.ok(names(r).includes('TERMINATION_CONFIRMED'));assert.ok(!names(r).includes('TERMINATION_REQUESTED'));
 assert.ok(r.receipt.pid>0);assert.ok(Date.parse(r.receipt.exitAt)>=Date.parse(r.receipt.startedAt));assert.ok(r.timing.monotonicElapsedMs>=0);
});
test('real stuck child fires timeout, requests handle kill, and confirms termination',async()=>{
 const r=await runProcess(process.execPath,hanging,{timeoutMs:200,killWaitMs:2000});record('real-timeout',r);
 assert.equal(r.timedOut,true);assert.equal(r.terminationConfirmed,true);assert.ok(names(r).indexOf('TIMEOUT_FIRED')<names(r).indexOf('TERMINATION_REQUESTED'));
 assert.ok(names(r).indexOf('TERMINATION_REQUESTED')<names(r).indexOf('TERMINATION_CONFIRMED'));assert.ok(r.timing.monotonicElapsedMs>=190);
});
test('real external abort records abort separately from timeout',async()=>{
 const a=new AbortController(),timer=setTimeout(()=>a.abort(),200);
 try{const r=await runProcess(process.execPath,hanging,{timeoutMs:5000,signal:a.signal,killWaitMs:2000});record('real-abort',r);assert.equal(r.aborted,true);assert.equal(r.timedOut,false);assert.equal(r.terminationConfirmed,true);assert.ok(names(r).includes('ABORT_OBSERVED'));}finally{clearTimeout(timer);}
});
test('delayed JS scheduling records actual lateness, not an invented hard wall-clock guarantee',async()=>{
 const r=await runProcess(process.execPath,hanging,{timeoutMs:50,killWaitMs:2000,spawnProcess:(exe,args,options)=>{const child=spawn(exe,args,options);const until=performance.now()+180;while(performance.now()<until){}return child;}});
 record('real-delayed-scheduling',r);assert.equal(r.timedOut,true);assert.equal(r.terminationConfirmed,true);assert.ok(r.timing.events.find(e=>e.event==='TIMEOUT_FIRED').latenessMs>=100);
});
test('actual lifecycle uses a fresh cleanup budget after a real launch timeout',async()=>{
 let alive=true,launchResult,cleanupResult;
 const adapter={launch:async signal=>(launchResult=await runProcess(process.execPath,hanging,{timeoutMs:150,killWaitMs:2000,signal})),inspectLocal:async()=>alive?local():null,
  probeReady:async()=>{throw Object.assign(Error('synthetic'),{code:'SYNTHETIC_NOT_READY'});},stop:async(_,signal)=>{cleanupResult=await runProcess(process.execPath,['-e',"console.log('synthetic-cleanup')"],{timeoutMs:2000,signal});alive=false;return cleanupResult;},isStopped:async()=>!alive};
 const r=await startOwnedDatabase(adapter,expected,{launcherMs:3000,readinessMs:1000,cleanupMs:3000});record('real-independent-cleanup',{result:r,launchResult,cleanupResult,identityIsSynthetic:true});
 assert.equal(r.firstFailure.stage,'LAUNCHER');assert.equal(r.firstFailure.timedOut,true);assert.equal(r.cleanup.status,'STOPPED');assert.equal(cleanupResult.aborted,false);
 assert.notEqual(launchResult.receipt.pid,cleanupResult.receipt.pid);assert.ok(Date.parse(r.cleanup.budgets[0].startedAt)>=Date.parse(launchResult.timing.completedAt));
});
test('actual cleanup deadline aborts its real child and separately records termination confirmation',async()=>{
 let completed;const adapter={inspectLocal:async()=>local(),stop:(_,signal)=>(completed=runProcess(process.execPath,hanging,{timeoutMs:5000,signal,killWaitMs:2000})),isStopped:async()=>false};
 const r=await stopOwnedDatabase(adapter,expected,local(),{cleanupMs:250});const child=await completed;record('real-cleanup-timeout',{result:r,child,identityIsSynthetic:true});
 assert.equal(r.status,'CLEANUP_TIMEOUT');assert.equal(r.completed,false);assert.equal(child.aborted,true);assert.equal(child.terminationConfirmed,true);assert.ok(r.budgets[0].events.some(e=>e.event==='TIMEOUT_FIRED'));
});
test('real cleanup command failure cannot replace the first launcher failure',async()=>{
 const adapter={launch:signal=>runProcess(process.execPath,['-e','process.exit(7)'],{signal,timeoutMs:5000}),inspectLocal:async()=>local(),probeReady:async()=>{throw Error('synthetic readiness');},
  stop:(_,signal)=>runProcess(process.execPath,['-e','process.exit(9)'],{signal,timeoutMs:5000}),isStopped:async()=>false};
 const r=await startOwnedDatabase(adapter,expected,{launcherMs:5000,readinessMs:1000,cleanupMs:5000});record('real-cleanup-failure',{result:r,identityIsSynthetic:true});
 assert.equal(r.firstFailure.exitCode,7);assert.equal(r.cleanup.status,'CLEANUP_FAILED');assert.equal(r.cleanup.launcher.exitCode,9);
});
test('sealed s1 evidence establishes delay only, not its unrecorded root cause',()=>{
 const root='D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-start-stop-validation-20260914/s1/',raw=fs.readFileSync(root+'result.json');
 assert.equal(crypto.createHash('sha256').update(raw).digest('hex'),'f7ccfb905ae421e743c6ac3f9b116a36210ae090a4c4b4d2988732ca62c2d0fd');
 const r=JSON.parse(raw),long=r.commands.filter(c=>c.elapsedMs>3600000);assert.equal(long.length,1);assert.equal(long[0].timedOut,true);
 record('historical-delay-review',{sourceSha256:crypto.createHash('sha256').update(raw).digest('hex'),originalStatus:r.status,commands:long.map(c=>({pid:c.pid,startedAt:c.startedAt,elapsedMs:c.elapsedMs,timedOut:c.timedOut,exited:c.exited,terminationConfirmed:c.terminationConfirmed})),oldTimerCallbackTimestampPresent:!!long[0].timing,rootCause:'UNCONFIRMED',normalCloseoutRecordedSeparately:true});
 assert.equal(r.status,'FAILED');assert.equal(long[0].timing,undefined);
});

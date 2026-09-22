// No PostgreSQL, database sockets, HTTP servers or existing processes. Child fixtures are short-lived Node/native processes.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {EventEmitter} from 'node:events';
import {PassThrough} from 'node:stream';
import {fileURLToPath} from 'node:url';
import {runProcess,assertOwned,startOwnedDatabase,stopOwnedDatabase} from './platform-process-lifecycle.mjs';

const expected={directory:'D:/synthetic-r3/pgdata',executable:'D:/synthetic-tools/postgres.exe',port:55449,address:'127.0.0.1',
  clusterId:'7684185108789200000',database:'postgres',user:'p3_r3_initializer',earliestStartEpoch:100,latestStartEpoch:110};
const local=()=>({pid:123,processPid:123,directory:expected.directory,commandDirectory:expected.directory,executable:expected.executable,
  port:55449,address:'127.0.0.1',clusterId:expected.clusterId,startedEpoch:105,processStartedEpoch:105,
  listeners:[{pid:123,port:55449,address:'127.0.0.1'}]});
const ready=()=>({database:expected.database,user:expected.user,port:55449,address:'127.0.0.1',listenAddresses:'127.0.0.1',directory:expected.directory,clusterId:expected.clusterId});
const exited=()=>({exited:true,exitCode:0,timedOut:false,aborted:false,pipesClosed:false});
const limits={launcherMs:30,readinessMs:30,cleanupMs:60,pollMs:1};
function fixture(overrides={}){
  let alive=true,stops=0;
  const adapter={launch:async()=>exited(),inspectLocal:async()=>alive?local():null,probeReady:async()=>ready(),
    stop:async()=>{stops++;alive=false;return exited();},isStopped:async()=>!alive,...overrides};
  return {adapter,stops:()=>stops};
}
test('launcher exit plus inherited open pipes is successful only after live identity readiness',async()=>{
  const f=fixture(),r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'READY');assert.equal(r.launcher.pipesClosed,false);assert.equal(r.databaseState,'READY_VERIFIED');assert.equal(f.stops(),0);
});
test('ready database but failed executor is accurately classified and cleaned up',async()=>{
  const f=fixture({launch:async()=>({...exited(),timedOut:true})}),r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'DATABASE_READY_EXECUTOR_FAILED');assert.equal(r.databaseState,'READY_VERIFIED');assert.equal(r.cleanup.completed,true);assert.equal(f.stops(),1);
});
test('launcher startup failure and no observed database is not called ready',async()=>{
  const f=fixture({launch:async()=>({...exited(),exitCode:1}),inspectLocal:async()=>null,isStopped:async()=>true});
  const r=await startOwnedDatabase(f.adapter,expected,limits);assert.equal(r.status,'DATABASE_START_FAILED');assert.equal(r.cleanup.status,'ALREADY_STOPPED');assert.equal(f.stops(),0);
});
test('spawn exception still reaches independent cleanup',async()=>{
  const f=fixture({launch:async()=>{throw Error('synthetic spawn failure');}});
  const r=await startOwnedDatabase(f.adapter,expected,limits);assert.equal(r.status,'DATABASE_READY_EXECUTOR_FAILED');assert.equal(r.cleanup.completed,true);
});
test('inspection failure is unverified, not evidence that the database failed to start',async()=>{
  const f=fixture({launch:async()=>({...exited(),exitCode:1}),inspectLocal:async()=>{throw Error('synthetic inspection unavailable');}});
  const r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'DATABASE_READINESS_FAILED');assert.equal(r.databaseState,'READINESS_UNVERIFIED');assert.equal(r.cleanup.completed,false);
});
test('unobserved startup with a remaining resource cannot be reported database-start-failed or stopped',async()=>{
  const f=fixture({launch:async()=>({...exited(),exitCode:1}),inspectLocal:async()=>null,isStopped:async()=>false});
  const r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'STARTUP_UNVERIFIED');assert.equal(r.cleanup.status,'REFUSED_IDENTITY');assert.equal(r.cleanup.completed,false);assert.equal(f.stops(),0);
});
test('hung launcher cannot block independent readiness and cleanup',async()=>{
  let aborted=false;const f=fixture({launch:signal=>new Promise(()=>signal.addEventListener('abort',()=>{aborted=true;}))});
  const started=Date.now(),r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(aborted,true);assert.equal(r.launcherError,'DeadlineError');assert.equal(r.cleanup.completed,true);assert.ok(Date.now()-started<1000);
});
test('exit zero and a port are not sufficient when SQL readiness never arrives',async()=>{
  const f=fixture({probeReady:async()=>null}),r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'DATABASE_READINESS_FAILED');assert.equal(r.cleanup.completed,true);assert.equal(f.stops(),1);
});
test('hung readiness gets a bounded timeout and a fresh cleanup budget',async()=>{
  const f=fixture({probeReady:async()=>new Promise(()=>{})}),r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.readinessError,'DeadlineError');assert.equal(r.cleanup.completed,true);
});
test('wrong SQL database identity rejects startup even when process and port match',async()=>{
  const f=fixture({probeReady:async()=>({...ready(),database:'wms_db'})}),r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'IDENTITY_REJECTED');assert.equal(r.cleanup.completed,true);
});
test('wrong SQL cluster identity rejects startup',async()=>{
  const f=fixture({probeReady:async()=>({...ready(),clusterId:'different'})}),r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'IDENTITY_REJECTED');assert.equal(r.cleanup.completed,true);
});
for(const [label,mutate] of [
  ['PID',v=>({...v,processPid:999})],['directory',v=>({...v,directory:'D:/synthetic-r3/pgdata-other'})],
  ['command directory',v=>({...v,commandDirectory:'D:/another/pgdata'})],['executable',v=>({...v,executable:'D:/other/postgres.exe'})],
  ['old process',v=>({...v,startedEpoch:50,processStartedEpoch:50})],['listener owner',v=>({...v,listeners:[{pid:999,port:55449,address:'127.0.0.1'}]})],
])test('wrong '+label+' refuses cleanup without any stop operation',async()=>{
  const f=fixture({inspectLocal:async()=>mutate(local())}),r=await stopOwnedDatabase(f.adapter,expected,null,limits);
  assert.equal(r.status,'REFUSED_IDENTITY');assert.equal(r.attempted,false);assert.equal(f.stops(),0);
});
test('reused PID or changed start time after pinning is rejected',()=>{
  assert.throws(()=>assertOwned(expected,{...local(),startedEpoch:106,processStartedEpoch:106},local()),/changed/);
});
test('identity is rechecked immediately before stopping',async()=>{
  let inspections=0;const f=fixture({inspectLocal:async()=>++inspections===1?local():({...local(),pid:999,processPid:999,listeners:[]})});
  const r=await stopOwnedDatabase(f.adapter,expected,null,limits);assert.equal(r.status,'REFUSED_IDENTITY');assert.equal(f.stops(),0);
});
test('missing PID while a resource still exists is not reported stopped',async()=>{
  const f=fixture({inspectLocal:async()=>null,isStopped:async()=>false}),r=await stopOwnedDatabase(f.adapter,expected,null,limits);
  assert.equal(r.status,'REFUSED_IDENTITY');assert.equal(r.completed,false);assert.equal(f.stops(),0);
});
test('normal stop exit failure is reported, not silently converted to stopped',async()=>{
  const f=fixture({stop:async()=>({...exited(),exitCode:1})}),r=await stopOwnedDatabase(f.adapter,expected,local(),limits);
  assert.equal(r.status,'CLEANUP_FAILED');assert.equal(r.completed,false);
});
test('hung stop is bounded and its abort signal is delivered',async()=>{
  let aborted=false;const f=fixture({stop:(_,signal)=>new Promise(()=>signal.addEventListener('abort',()=>{aborted=true;}))});
  const r=await stopOwnedDatabase(f.adapter,expected,local(),limits);assert.equal(r.status,'CLEANUP_TIMEOUT');assert.equal(aborted,true);assert.equal(r.completed,false);
});
test('late inspection completion after cleanup timeout cannot trigger a delayed stop',async()=>{
  const f=fixture({inspectLocal:async()=>{await new Promise(resolve=>setTimeout(resolve,70));return local();}});
  const r=await stopOwnedDatabase(f.adapter,expected,null,{...limits,cleanupMs:10});
  assert.equal(r.status,'CLEANUP_TIMEOUT');await new Promise(resolve=>setTimeout(resolve,90));assert.equal(f.stops(),0);
});
test('executor failure and cleanup failure remain separately visible',async()=>{
  const f=fixture({launch:async()=>({...exited(),exitCode:1}),stop:async()=>({...exited(),exitCode:1})});
  const r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'DATABASE_READY_EXECUTOR_FAILED');assert.equal(r.cleanup.status,'CLEANUP_FAILED');assert.equal(r.cleanup.completed,false);
});
test('Windows startup error codes remain first cause when readiness and cleanup queries are denied',async()=>{
  const denied=()=>{throw Object.assign(Error('synthetic private request'),{name:'EnvironmentInspectionError',code:'IDENTITY_QUERY_ACCESS_DENIED',stage:'postgres'});};
  const f=fixture({launch:async()=>({...exited(),exitCode:1,output:'pg_ctl: could not create restricted token: error code 87\npg_ctl: could not start server: error code 3\n'}),inspectLocal:async()=>null,isStopped:denied});
  const r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.firstFailure.stage,'LAUNCHER');assert.deepEqual(r.firstFailure.windowsErrorCodes,[87,3]);assert.equal(r.firstFailure.exitCode,1);
  assert.equal(r.readinessFailure.name,'DeadlineError');assert.equal(r.cleanup.failure.code,'IDENTITY_QUERY_ACCESS_DENIED');assert.equal(r.cleanup.completed,false);
  assert.equal(r.status,'STARTUP_UNVERIFIED');assert.ok(!JSON.stringify(r).includes('private request'));
});
test('readiness query is first cause when launcher succeeds, cleanup remains secondary',async()=>{
  const f=fixture({inspectLocal:async()=>{throw Object.assign(Error('private'),{name:'EnvironmentInspectionError',code:'IDENTITY_QUERY_INCOMPLETE'});}});
  const r=await startOwnedDatabase(f.adapter,expected,limits);assert.equal(r.firstFailure.stage,'READINESS');assert.equal(r.firstFailure.code,'IDENTITY_QUERY_INCOMPLETE');
  assert.equal(r.cleanup.failure.stage,'CLEANUP');assert.equal(r.cleanup.completed,false);assert.equal(f.stops(),0);
});
test('launcher exception is not overwritten by later cleanup timeout',async()=>{
  const f=fixture({launch:async()=>{throw Object.assign(Error('private'),{code:'EACCES'});},inspectLocal:async()=>new Promise(()=>{})});
  const r=await startOwnedDatabase(f.adapter,expected,limits);assert.equal(r.firstFailure.stage,'LAUNCHER');assert.equal(r.firstFailure.code,'EACCES');assert.equal(r.cleanup.status,'CLEANUP_TIMEOUT');
});
test('process replacement after SQL readiness cannot be accepted or stopped using the old PID',async()=>{
  let inspections=0;const f=fixture({inspectLocal:async()=>++inspections===1?local():({...local(),pid:999,processPid:999,listeners:[{pid:999,port:55449,address:'127.0.0.1'}]})});
  const r=await startOwnedDatabase(f.adapter,expected,limits);
  assert.equal(r.status,'IDENTITY_REJECTED');assert.equal(r.cleanup.status,'REFUSED_IDENTITY');assert.equal(f.stops(),0);
});
test('exit zero from stop still requires process, PID file and listener absence',async()=>{
  const f=fixture({stop:async()=>exited(),isStopped:async()=>false}),r=await stopOwnedDatabase(f.adapter,expected,local(),limits);
  assert.equal(r.status,'CLEANUP_TIMEOUT');assert.equal(r.completed,false);
});
test('successful checked cleanup does not require a listening socket during partial startup',async()=>{
  const f=fixture({inspectLocal:async()=>({...local(),listeners:[]}),isStopped:async()=>f.stops()>0}),r=await stopOwnedDatabase(f.adapter,expected,null,limits);
  assert.equal(r.status,'STOPPED');assert.equal(f.stops(),1);
});
test('native Windows launcher exits while its short-lived descendant still owns inherited pipes',async()=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'platform-native-pipes-')),exe=path.join(directory,'pipe-fixture.exe');
  try{
    const source=fileURLToPath(new URL('./fixtures/PlatformPipeInheritance.cs',import.meta.url));
    const compilation=await runProcess('C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe',
      ['/nologo','/target:exe','/out:'+exe,source],{timeoutMs:10000});
    assert.equal(compilation.exitCode,0,compilation.output);
    const r=await runProcess(exe,[],{timeoutMs:5000,drainMs:30});
    assert.equal(r.exitCode,0);assert.equal(r.exited,true);assert.equal(r.timedOut,false);assert.equal(r.outputComplete,false);
  }finally{
    // The native descendant has no ports/files and self-terminates after 1.5 seconds.
    await new Promise(resolve=>setTimeout(resolve,1700));
    if(fs.existsSync(exe))fs.unlinkSync(exe);fs.rmdirSync(directory);
  }
});
test('real command timeout settles and terminates only its own Node child',async()=>{
  const r=await runProcess(process.execPath,['-e','setTimeout(()=>{},10000)'],{timeoutMs:80,killWaitMs:1000});
  assert.equal(r.timedOut,true);assert.equal(r.terminationConfirmed,true);assert.equal(r.exited,true);
});
test('an uncooperative mocked launcher cannot keep the executor pending indefinitely',async()=>{
  const child=new EventEmitter();child.stdin=new PassThrough();child.stdout=new PassThrough();child.stderr=new PassThrough();child.kill=()=>false;
  const r=await runProcess('synthetic',[],{spawnProcess:()=>child,timeoutMs:5,killWaitMs:5});
  assert.equal(r.timedOut,true);assert.equal(r.terminationConfirmed,false);
});
test('spawn errors settle without waiting for a close event',async()=>{
  const r=await runProcess('synthetic',[],{spawnProcess:()=>{throw Object.assign(Error('missing'),{code:'ENOENT'});}});
  assert.equal(r.spawnError,'ENOENT');assert.equal(r.exited,false);
});
test('server launcher output is independent of Node pipes and preserved in a new file',async()=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'platform-lifecycle-')),file=path.join(directory,'launcher.log');
  try{
    const r=await runProcess(process.execPath,['-e',"console.log('synthetic launcher')"],{outputFile:file,timeoutMs:5000});
    assert.equal(r.exitCode,0);assert.equal(r.outputComplete,true);assert.match(fs.readFileSync(file,'utf8'),/synthetic launcher/);
  }finally{fs.unlinkSync(file);fs.rmdirSync(directory);}
});

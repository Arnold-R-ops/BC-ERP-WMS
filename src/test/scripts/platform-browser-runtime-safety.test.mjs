import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {RESTART_DISABLED,backendArguments,requireSealedBackendCommand,classifyStoppedPid,confirmBrowserAncestryStopped,adaptBrowserStopChecks} from './platform-browser-runtime-safety.mjs';
import {launchBrowserBackend,browserChildEnvironment} from './platform-b1-batch.mjs';
import {sealedBackendCommand} from './platform-b1-prepare.mjs';
import {createB1Environment,DATA,PORT} from './platform-b1-environment.mjs';
import * as B from './platform-b1-binding.mjs';
import {runProcess,stopOwnedDatabase} from './platform-process-lifecycle.mjs';
import {POWERSHELL} from './platform-launch-preflight.mjs';
const HERE=path.dirname(fileURLToPath(import.meta.url)),BASE=path.dirname(HERE),BUILD=BASE+'/classes';
const RESULTS=BASE+'/synthetic-runtime-safety-results',SID='S-1-5-21-3770167052-703495069-1631994977-1001';
const CLUSTER='123456789012345678',DIGEST='a'.repeat(64),epoch=Date.parse('2026-09-20T14:00:00Z');
const ticks=ms=>(BigInt(ms)*10000n+621355968000000000n).toString();
function proof(pid,parentPid,exe,ms,directory=DATA){return {status:'AVAILABLE',pid,parentPid,executable:exe,directory,ownerSid:SID,creationUtc:new Date(ms).toISOString(),creationTicks:ticks(ms),creationEpoch:Math.floor(ms/1000),commandLineReadable:true,commandLineSha256:DIGEST};}
const ok=(output='',extra={})=>({exited:true,exitCode:0,timedOut:false,aborted:false,spawnError:null,outputComplete:true,terminationConfirmed:true,elapsedMs:1,output,...extra});
function report(ms,live=false,server,relay){
 const self=proof(401,process.pid,POWERSHELL,ms-100,''),owner=proof(process.pid,400,process.execPath,ms-200,'');
 return {schema:1,checkedAt:new Date(ms).toISOString(),target:{directory:DATA,port:PORT},expected:{directory:DATA,port:PORT,ownerPid:process.pid,ownerExecutable:process.execPath},
  context:{status:'AVAILABLE',user:'ARNOLD\\Arnold',sid:SID,restrictedToken:false,administratorEnabled:true,elevated:true,elevationType:1,powerShellPid:401,powerShellPath:POWERSHELL},self,owner,
  directory:{status:'PRESENT',directory:true},pidFile:live?{status:'PRESENT',pid:String(server.pid),directory:DATA,startedEpoch:String(server.creationEpoch),port:String(PORT),address:'127.0.0.1'}:{status:'ABSENT'},
  postgres:{status:'AVAILABLE',count:live?1:0,incomplete:0,targetMatches:live?[server]:[],processIds:live?[server.pid]:[],parentIds:live?[relay.pid]:[]},
  listeners:{status:'AVAILABLE',total:live?1:0,malformed:0,selected:live?[{address:'127.0.0.1',port:PORT,pid:server.pid}]:[],pidProofs:live?[server]:[]}};
}
function fixture(){
 const server=proof(30,20,B.PG+'/postgres.exe',epoch),relay=proof(20,10,'C:/Windows/System32/cmd.exe',epoch-100);
 const launcher={pid:10,parentPid:process.pid,ownerSid:SID,executable:B.PG+'/pg_ctl.exe',startedAt:new Date(epoch-200).toISOString(),exitAt:new Date(epoch+10).toISOString(),exited:true,terminationConfirmed:true};
 const stop=ok('server stopped',{receipt:{pid:40,parentPid:process.pid,executable:B.PG+'/pg_ctl.exe',exited:true,terminationConfirmed:true},timing:{startedAt:new Date(epoch+1000).toISOString(),completedAt:new Date(epoch+1200).toISOString()}});
 const args={ancestry:{serverPid:30,serverCreationTicks:server.creationTicks,relayPid:20,relayCreationTicks:relay.creationTicks,launcherPid:10,ownerSid:SID},proofs:{server,relay},launcher,
  report:report(epoch+2000),expected:{directory:DATA,port:PORT,executable:B.PG+'/postgres.exe'},stopReceipt:stop,absence:{confirmed:true,descendants:[]}};
 const rows=new Map([[30,{pid:30,status:'ABSENT'}],[20,{pid:20,status:'ABSENT'}]]),records=[];
 args.query=async pid=>rows.get(pid);args.record=v=>records.push(v);return {args,rows,records,server,relay};
}
const reused=(original,ms=epoch+1500)=>({...proof(original.pid,999,POWERSHELL,ms,''),commandLineSha256:'b'.repeat(64)});
test('sealed command and actual backend invocation use identical JVM arguments before main',()=>{
 let seen;launchBrowserBackend((...a)=>seen=a,{commands:{backend:sealedBackendCommand()}},CLUSTER,'synthetic-not-recorded');
 assert.equal(seen[0],'backend');assert.equal(seen[1],B.JAVA);assert.deepEqual(seen[2],backendArguments(B,CLUSTER));
 assert.equal(seen[2][0],RESTART_DISABLED);assert.equal(seen[2].at(-1),'com.wms.browseracceptance.PlatformB1Harness');
 assert.equal(seen[2].filter(s=>s.includes('spring.devtools.restart.enabled')).length,1);
});
test('actual backend launch refuses omitted changed reordered or conflicting sealed options',()=>{
 const original=sealedBackendCommand();for(const bad of [original.filter(s=>s!==RESTART_DISABLED),original.map(s=>s===RESTART_DISABLED?s.replace('false','true'):s),[...original,'-Dspring.devtools.restart.enabled=true']]){
  let calls=0;assert.throws(()=>launchBrowserBackend(()=>calls++,{commands:{backend:bad}},CLUSTER,''),{code:'BROWSER_BACKEND_COMMAND_DRIFT'});assert.equal(calls,0);
 }
});
test('invalid cluster arguments fail before launch and placeholders stay manifest-only',()=>{
 assert.throws(()=>backendArguments(B,'bad'),{code:'BROWSER_CLUSTER_ARGUMENT'});
 assert.throws(()=>requireSealedBackendCommand(sealedBackendCommand(),B.JAVA,backendArguments(B,'999999999999'),CLUSTER));
});
async function jvm(label,mode){
 fs.mkdirSync(RESULTS,{recursive:true});const outputFile=RESULTS+'/'+label+'.log';assert.equal(fs.existsSync(outputFile),false);
 let invocation;launchBrowserBackend((name,exe,args)=>{invocation={exe,args};},{commands:{backend:sealedBackendCommand()}},CLUSTER,'unused-synthetic');
 const cut=invocation.args.findIndex(s=>s.startsWith('@'));assert.ok(cut>0);
 // Keep the exact actual JVM prefix; substitute only the test main/classpath. No Spring/JDBC on this classpath.
 let prefix=invocation.args.slice(0,cut);if(mode==='missing')prefix=prefix.filter(s=>s!==RESTART_DISABLED);
 const args=[...prefix,'-cp',BUILD,'com.wms.browseracceptance.PlatformJvmEntryProbe',...(mode==='argument'?['SYNTHETIC_ARGUMENT_DO_NOT_LOG']:[])];
 const r=await runProcess(invocation.exe,args,{cwd:B.ROOT,env:browserChildEnvironment(),timeoutMs:10000,outputFile});
 const actual=JSON.parse(fs.readFileSync(outputFile,'utf8'));assert.equal(r.exitCode,mode==='ok'?0:2);assert.equal(r.exited,true);assert.equal(r.outputComplete,true);assert.equal(r.terminationConfirmed,true);assert.equal(r.timedOut,false);assert.ok(r.elapsedMs<=10000);
 assert.equal(actual.disabledAtClassInitialization,mode!=='missing');assert.equal(actual.disabledAtEntry,mode!=='missing');assert.equal(actual.argumentCount,mode==='argument'?1:0);
 assert.ok(!fs.readFileSync(outputFile,'utf8').includes('SYNTHETIC_ARGUMENT_DO_NOT_LOG'));
 const {output,...receipt}=r;fs.writeFileSync(RESULTS+'/'+label+'.json',JSON.stringify({mode,actual,receipt,commandPrefix:prefix,classPath:BUILD,main:'com.wms.browseracceptance.PlatformJvmEntryProbe',argumentContentsOmitted:true})+'\n',{flag:'wx'});
}
test('real synthetic JVM sees property at class initialization and main entry',async()=>jvm('entry-enabled','ok'));
test('real synthetic JVM missing property is expected exit two not success',async()=>jvm('entry-negative','missing'));
test('real synthetic JVM reports only argument count not contents',async()=>jvm('entry-safe-count','argument'));
test('synthetic probe remains JDK only and live harness guards remain unchanged',()=>{
 const p=fs.readFileSync(HERE+'/PlatformJvmEntryProbe.java','utf8'),s=fs.readFileSync(HERE+'/PlatformB1Harness.java','utf8');
 assert.ok(!/import |DriverManager|SpringApplication|PlatformB1Harness\.main/.test(p));
 assert.ok(s.includes('if(args.length!=0 || !Path.of("").toAbsolutePath().normalize().equals(ROOT))'));
});
test('complete identical process is original-present',()=>{const f=fixture();assert.equal(classifyStoppedPid(f.server,f.server,f.args.stopReceipt.timing.startedAt),'ORIGINAL_PRESENT');});
test('confirmed absence is separate from identity change',()=>{const f=fixture();assert.equal(classifyStoppedPid({pid:30,status:'ABSENT'},f.server,f.args.stopReceipt.timing.startedAt),'CONFIRMED_ABSENT');});
test('complete later creation after stop begins proves numeric PID reuse',()=>{const f=fixture();assert.equal(classifyStoppedPid(reused(f.server),f.server,f.args.stopReceipt.timing.startedAt),'PID_REUSED');});
test('missing identity or inconsistent creation timestamps remain unconfirmed',()=>{
 const f=fixture();for(const key of ['parentPid','executable','directory','ownerSid','creationTicks','creationUtc','creationEpoch','commandLineReadable','commandLineSha256']){const p=reused(f.server);delete p[key];assert.equal(classifyStoppedPid(p,f.server,f.args.stopReceipt.timing.startedAt),'UNCONFIRMED',key);}
 for(const key of ['creationTicks','creationEpoch']){const p=reused(f.server);p[key]=key==='creationTicks'?ticks(epoch+1):1;assert.equal(classifyStoppedPid(p,f.server,f.args.stopReceipt.timing.startedAt),'UNCONFIRMED');}
});
test('same birth with changed path owner parent directory or command is not accepted reuse',()=>{
 const f=fixture();for(const [k,v]of [['executable',POWERSHELL],['ownerSid','S-1-5-18'],['parentPid',777],['directory','D:/other'],['commandLineSha256','b'.repeat(64)]])assert.equal(classifyStoppedPid({...f.server,[k]:v},f.server,f.args.stopReceipt.timing.startedAt),'UNCONFIRMED');
});
test('older birth or new birth before normal stop is not accepted reuse',()=>{const f=fixture();for(const ms of [epoch-1,epoch+500])assert.equal(classifyStoppedPid(reused(f.server,ms),f.server,f.args.stopReceipt.timing.startedAt),'UNCONFIRMED');});
test('wrong PID and incomplete original proof are unconfirmed even when absent',()=>{const f=fixture();assert.equal(classifyStoppedPid({pid:31,status:'ABSENT'},f.server,''),'UNCONFIRMED');assert.equal(classifyStoppedPid({pid:30,status:'ABSENT'},{...f.server,creationTicks:null},''),'UNCONFIRMED');});
test('normal stop plus both confirmed absent passes',async()=>{const f=fixture();assert.equal(await confirmBrowserAncestryStopped(f.args),true);assert.equal(f.records[0].status,'CONFIRMED_STOPPED');});
test('normal stop accepts complete server PID reuse without terminating replacement',async()=>{const f=fixture();f.rows.set(30,reused(f.server));assert.equal(await confirmBrowserAncestryStopped(f.args),true);assert.equal(f.records[0].processes[0].relation,'PID_REUSED');});
test('normal stop accepts complete relay PID reuse with same residual checks',async()=>{const f=fixture();f.rows.set(20,reused(f.relay));assert.equal(await confirmBrowserAncestryStopped(f.args),true);assert.equal(f.records[0].processes[1].relation,'PID_REUSED');});
test('original server still present prevents completion',async()=>{const f=fixture();f.rows.set(30,f.server);assert.equal(await confirmBrowserAncestryStopped(f.args),false);assert.equal(f.records[0].status,'ORIGINAL_PRESENT');});
test('original relay still present prevents completion',async()=>{const f=fixture();f.rows.set(20,f.relay);assert.equal(await confirmBrowserAncestryStopped(f.args),false);});
test('query denied failed exited-during-query or incomplete remains unconfirmed',async()=>{
 for(const status of ['ACCESS_DENIED','QUERY_FAILED','EXITED_DURING_QUERY','INCOMPLETE']){const f=fixture();f.rows.set(30,{pid:30,status});await assert.rejects(()=>confirmBrowserAncestryStopped(f.args),{code:'BROWSER_STOP_QUERY_UNCONFIRMED'});assert.equal(f.records[0].status,'UNCONFIRMED');}
});
test('query exception is recorded separately and never treated as absence',async()=>{const f=fixture();f.args.query=async()=>{const e=Error('synthetic-not-persisted');e.code='PROCESS_QUERY_EXECUTOR_FAILED';throw e;};await assert.rejects(()=>confirmBrowserAncestryStopped(f.args));assert.equal(f.records[0].reasonCode,'PROCESS_QUERY_EXECUTOR_FAILED');assert.ok(!JSON.stringify(f.records).includes('synthetic-not-persisted'));});
test('nonzero incomplete timed-out or missing stop receipt cannot authorize reuse',async()=>{
 for(const [k,v]of [['exitCode',1],['exited',false],['outputComplete',false],['terminationConfirmed',false],['timedOut',true],['aborted',true],['spawnError','EFAIL'],['receipt',null],['timing',{}]]){
  const f=fixture();f.args.stopReceipt[k]=v;await assert.rejects(()=>confirmBrowserAncestryStopped(f.args),{code:'BROWSER_NORMAL_STOP_UNVERIFIED'});
 }
});
test('without a normal stop the existing strict ancestry rejection stays effective',async()=>{const f=fixture();f.args.stopReceipt=null;f.rows.set(30,reused(f.server));await assert.rejects(()=>confirmBrowserAncestryStopped(f.args),{code:'ANCESTRY_ABSENCE_PID_REUSED'});});
test('PID file listener directory process and descendant residue prevent completion',async()=>{
 for(const change of [f=>f.args.report.pidFile={status:'PRESENT'},f=>{f.args.report.listeners.selected=[{port:PORT,pid:55}];f.args.report.listeners.pidProofs=[{status:'AVAILABLE',pid:55}];},f=>{f.args.report.postgres.targetMatches=[f.server];},f=>{f.args.absence={confirmed:false,descendants:[90]};},f=>{f.args.report.directory={status:'ABSENT'};}]){
  const f=fixture();f.rows.set(30,reused(f.server));change(f);assert.equal(await confirmBrowserAncestryStopped(f.args),false);assert.equal(f.records[0].status,'RESIDUAL_OR_UNCONFIRMED');
 }
});
test('incomplete OS inventory cannot confirm stop',async()=>{const f=fixture();f.args.report.postgres.status='INCOMPLETE';await assert.rejects(()=>confirmBrowserAncestryStopped(f.args));});
test('wrong target or stale observation cannot confirm stop',async()=>{
 for(const change of [f=>f.args.expected.port=9999,f=>f.args.expected.directory='D:/other',f=>f.args.report.checkedAt=new Date(epoch).toISOString()]){const f=fixture();change(f);await assert.rejects(()=>confirmBrowserAncestryStopped(f.args));}
});
test('missing pinned server relay or launcher identity cannot confirm stop',async()=>{
 for(const change of [f=>f.args.proofs.server={...f.server,creationTicks:null},f=>f.args.proofs.relay={...f.relay,parentPid:999},f=>f.args.launcher={...f.args.launcher,pid:999}]){const f=fixture();change(f);await assert.rejects(()=>confirmBrowserAncestryStopped(f.args),{code:'BROWSER_POSTSTOP_BINDING'});}
});
test('abort during query remains a failure and no subsequent PID query runs',async()=>{const f=fixture(),a=new AbortController();let n=0;f.args.signal=a.signal;f.args.query=async pid=>{n++;a.abort();return {pid,status:'ABSENT'};};await assert.rejects(()=>confirmBrowserAncestryStopped(f.args),{name:'AbortError'});assert.equal(n,1);});

// A controlled command adapter feeds the actual frozen environment and lifecycle, never spawning a process.
function environmentFixture(mode='reuse-server'){
 const base=Date.now(),server=proof(30,20,B.PG+'/postgres.exe',base+50),relay=proof(20,10,'C:/Windows/System32/cmd.exe',base+25);
 const launcher={pid:10,parentPid:process.pid,ownerSid:SID,executable:B.PG+'/pg_ctl.exe',startedAt:new Date(base).toISOString(),exitAt:new Date(base+100).toISOString(),exited:true,terminationConfirmed:true};
 const state={stopped:false,stopCommands:0,calls:[],beforeReuse:false},evidence={};
 const command=async(exe,args)=>{
  state.calls.push(path.win32.basename(exe));
  if(exe===POWERSHELL){
   const text=args[3],match=text.match(/ProcessProof (\d+) '([A-Z_]+)'/);
   if(!match)return ok(JSON.stringify(report(base+3000,!state.stopped,server,relay)));
   const pid=Number(match[1]),stage=match[2];let p;
   if(!state.stopped)p=pid===30?(state.beforeReuse?reused(server,base+1500):server):pid===20?relay:{pid,status:'ABSENT'};
   else if(mode==='query-failure'&&pid===30)p={pid,status:'ACCESS_DENIED'};
   else if(mode==='original-server'&&pid===30)p=server;
   else if((mode==='reuse-server'&&pid===30)||(mode==='reuse-relay'&&pid===20))p=reused(pid===30?server:relay,base+2500);
   else p={pid,status:'ABSENT'};
   return ok(JSON.stringify({...p,stage}));
  }
  if(exe===B.PG+'/pg_controldata.exe')return ok('Database system identifier: '+CLUSTER);
  if(exe===B.PG+'/pg_ctl.exe'&&args[0]==='start')return ok('server started',{receipt:launcher});
  if(exe===B.PG+'/pg_ctl.exe'&&args[0]==='stop'){
   state.stopCommands++;state.stopped=true;
   return ok('server stopped',{receipt:{pid:40,parentPid:process.pid,executable:exe,exited:true,terminationConfirmed:true},timing:{startedAt:new Date(base+2000).toISOString(),completedAt:new Date(base+2100).toISOString()}});
  }
  throw Error('UNEXPECTED_SYNTHETIC_COMMAND');
 };
 const environment=createB1Environment({command,evidence,password:()=>{throw Error('NO_SQL_AUTHORIZED');}});
 return {state,evidence,environment,async started(){await environment.inspect('synthetic-before-launch');const expected=environment.configure(CLUSTER);await environment.adapter.launch();const identity=await environment.observe();environment.pin(identity);return {expected,identity};}};
}
test('actual lifecycle and final closeout accept server reuse only after normal stop',async()=>{
 const f=environmentFixture(),{expected,identity}=await f.started();const r=await stopOwnedDatabase(f.environment.adapter,expected,identity,{cleanupMs:1000,pollMs:1});
 assert.equal(r.completed,true);assert.equal(f.state.stopCommands,1);const end=await f.environment.closeout();assert.equal(end.absence.confirmed,true);assert.equal(end.ancestryAbsent,true);
 assert.equal(f.evidence.postStopChecks.length,2);assert.ok(f.evidence.postStopChecks.every(r=>r.processes.some(p=>p.relation==='PID_REUSED')));
 assert.ok(!f.state.calls.some(s=>/psql|taskkill/i.test(s)));
});
test('actual adapter handles relay reuse and retains independent final proof',async()=>{const f=environmentFixture('reuse-relay'),{expected,identity}=await f.started();assert.equal((await stopOwnedDatabase(f.environment.adapter,expected,identity,{cleanupMs:1000,pollMs:1})).completed,true);assert.equal((await f.environment.closeout()).ancestryAbsent,true);});
test('actual adapter refuses original process presence after nominal stop',async()=>{const f=environmentFixture('original-server'),{identity}=await f.started();await f.environment.adapter.stop(identity);assert.equal(await f.environment.isStopped(undefined,identity),false);assert.equal((await f.environment.closeout()).absence.confirmed,false);});
test('actual adapter keeps query failure unconfirmed with diagnostic record',async()=>{const f=environmentFixture('query-failure'),{identity}=await f.started();await f.environment.adapter.stop(identity);await assert.rejects(()=>f.environment.isStopped(undefined,identity),{code:'PROCESS_QUERY_ACCESS_DENIED'});assert.equal(f.evidence.postStopChecks[0].status,'UNCONFIRMED');});
test('actual pre-stop strict identity refuses a reused server without issuing stop',async()=>{const f=environmentFixture(),{expected,identity}=await f.started();f.state.beforeReuse=true;const r=await stopOwnedDatabase(f.environment.adapter,expected,identity,{cleanupMs:1000,pollMs:1});assert.equal(r.completed,false);assert.equal(f.state.stopCommands,0);});
test('sealed adapter uses post-stop helper and template refuses duplicate adaptation',()=>{
 const s=fs.readFileSync(HERE+'/platform-b1-environment.mjs','utf8');assert.ok(s.includes('stopReceipt:normalStop'));assert.ok(s.includes('verifyLauncherAncestry({'));assert.ok(s.includes('inspectLocal:observe'));assert.ok(s.includes('Validated ancestry required before SQL'));assert.throws(()=>adaptBrowserStopChecks(s),{code:'BROWSER_ENVIRONMENT_TEMPLATE_MISMATCH'});
});
test('preparation and actual supervisor use shared tested functions rather than alternate commands',()=>{
 const p=fs.readFileSync(HERE+'/platform-b1-prepare.mjs','utf8'),b=fs.readFileSync(HERE+'/platform-b1-batch.mjs','utf8');
 assert.ok(p.includes('backend:sealedBackendCommand()'));assert.ok(p.includes("content.set('platform-b1-environment.mjs',adaptBrowserStopChecks(env))"));assert.ok(b.includes('launchBrowserBackend(launch,manifest,clusterId,secrets[B.APP])'));
 for(const name of ['platform-browser-runtime-safety.mjs','platform-browser-runtime-safety.test.mjs','PlatformJvmEntryProbe.java'])assert.ok(p.includes(name));
});

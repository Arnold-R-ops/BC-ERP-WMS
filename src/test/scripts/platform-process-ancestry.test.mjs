import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {verifyLauncherAncestry,confirmAncestryStopped} from './platform-process-ancestry.mjs';
const begin='2026-09-16T01:00:00.000Z',end='2026-09-16T01:00:02.000Z';
function fixture(direct=false){
 const proof=(pid,parentPid,executable,time)=>({status:'AVAILABLE',pid,parentPid,executable,directory:'D:/synthetic/pgdata',ownerSid:'synthetic-owner',creationUtc:time,creationTicks:String(BigInt(Date.parse(time))*10000n),creationEpoch:Math.floor(Date.parse(time)/1000),commandLineReadable:true,commandLineSha256:'a'.repeat(64)});
 const server=proof(30,direct?10:20,'D:/synthetic/bin/postgres.exe','2026-09-16T01:00:01.200Z');
 const relay=proof(20,10,'C:/Windows/System32/cmd.exe','2026-09-16T01:00:01.100Z');
 const rows=new Map([[10,{status:'ABSENT',pid:10}],[20,relay]]);
 const args={local:{pid:30,parentPid:server.parentPid,executable:server.executable,processStartedEpoch:server.creationEpoch},server,
  launcher:{pid:10,parentPid:5,ownerSid:'synthetic-owner',executable:'D:/synthetic/bin/pg_ctl.exe',startedAt:begin,exitAt:end,exited:true,terminationConfirmed:true},
  launcherExecutable:'D:/synthetic/bin/pg_ctl.exe',relayExecutable:'C:/Windows/System32/cmd.exe',directory:'D:/synthetic/pgdata',ownerSid:'synthetic-owner',query:async pid=>rows.get(pid)};
 return {args,rows,relay};
}
for(const direct of [true,false])test('exited launcher binds '+(direct?'direct child':'cmd relay')+' with exact identity',async()=>{
 const f=fixture(direct),r=await verifyLauncherAncestry(f.args);assert.equal(r.kind,direct?'PG_CTL_POSTGRES':'PG_CTL_CMD_POSTGRES');
 assert.deepEqual(await verifyLauncherAncestry({...f.args,pinned:r}),r);
});
for(const field of ['exited','terminationConfirmed','pid','parentPid','ownerSid','executable','startedAt','exitAt'])test('unverified launcher receipt rejects '+field,async()=>{
 const f=fixture();f.args.launcher[field]=field==='pid'?0:field==='exited'||field==='terminationConfirmed'?false:'wrong';await assert.rejects(()=>verifyLauncherAncestry(f.args));
});
for(const status of ['AVAILABLE','ACCESS_DENIED','QUERY_FAILED','EXITED_DURING_QUERY'])test('launcher current PID '+status+' is not adopted as the old launcher',async()=>{
 const f=fixture();f.rows.set(10,{status,pid:10});await assert.rejects(()=>verifyLauncherAncestry(f.args));
});
for(const field of ['parentPid','executable','ownerSid','directory','creationUtc','creationTicks','commandLineReadable'])test('cmd relay wrong '+field+' fails closed',async()=>{
 const f=fixture();f.relay[field]=field==='parentPid'?999:field==='commandLineReadable'?false:field==='creationUtc'?'2026-09-16T00:59:00.000Z':'wrong';await assert.rejects(()=>verifyLauncherAncestry(f.args));
});
test('missing relay cannot be inferred from server parent number alone',async()=>{const f=fixture();f.rows.set(20,{status:'ABSENT',pid:20});await assert.rejects(()=>verifyLauncherAncestry(f.args));});
for(const target of ['server','relay'])test('same numeric '+target+' PID with changed subsecond birth is refused after pinning',async()=>{
 const f=fixture(),pinned=await verifyLauncherAncestry(f.args);(target==='server'?f.args.server:f.relay).creationTicks+='1';await assert.rejects(()=>verifyLauncherAncestry({...f.args,pinned}),{code:'PINNED_ANCESTRY_CHANGED'});
});
test('server must be born inside actual child-handle launch window',async()=>{const f=fixture();f.args.server.creationUtc='2026-09-16T01:01:00.000Z';await assert.rejects(()=>verifyLauncherAncestry(f.args),{code:'ANCESTRY_CREATION_WINDOW_MISMATCH'});});
test('abort after process query prevents further ancestry acceptance',async()=>{
 const f=fixture(),a=new AbortController();f.args.signal=a.signal;f.args.query=async pid=>{a.abort();return f.rows.get(pid);};await assert.rejects(()=>verifyLauncherAncestry(f.args),{name:'AbortError'});
});
test('actual start/stop observation wires shared ancestry and detailed query before both SQL and cleanup',()=>{
 const s=fs.readFileSync(new URL('./platform-s1-start-stop.mjs',import.meta.url),'utf8');
 assert.match(s,/ancestry=await verifyLauncherAncestry/);assert.match(s,/inspectLocal:observe/);assert.match(s,/local\.ancestry!==ancestry/);assert.match(s,/launchReceipt=r\.receipt/);assert.match(s,/return queryWindowsProcess/);
 assert.doesNotMatch(s,/local\.parentPid!==launchPid/);assert.match(s,/timing:r\.timing,receipt:r\.receipt/);
 assert.match(s,/if\(ancestry&&!await confirmAncestryStopped/);assert.match(s,/finalAncestryAbsent=await confirmAncestryStopped/);
});
test('normal absence confirms both the server and the identified cmd relay',async()=>{
 const f=fixture(),ancestry=await verifyLauncherAncestry(f.args),queried=[];
 assert.equal(await confirmAncestryStopped({ancestry,query:async pid=>{queried.push(pid);return {pid,status:'ABSENT'};}}),true);assert.deepEqual(queried,[30,20]);
});
test('remaining known cmd relay prevents normal-closeout claim',async()=>{
 const f=fixture(),ancestry=await verifyLauncherAncestry(f.args);assert.equal(await confirmAncestryStopped({ancestry,query:async pid=>pid===30?{pid,status:'ABSENT'}:f.relay}),false);
});
test('relay PID reuse is rejected and no replacement is adopted or terminated',async()=>{
 const f=fixture(),ancestry=await verifyLauncherAncestry(f.args);await assert.rejects(()=>confirmAncestryStopped({ancestry,query:async pid=>pid===30?{pid,status:'ABSENT'}:{...f.relay,creationTicks:f.relay.creationTicks+'1'}}),{code:'ANCESTRY_ABSENCE_PID_REUSED'});
});
for(const status of ['ACCESS_DENIED','EXITED_DURING_QUERY','INCOMPLETE'])test('closeout query '+status+' cannot prove absence',async()=>{
 const f=fixture(),ancestry=await verifyLauncherAncestry(f.args);await assert.rejects(()=>confirmAncestryStopped({ancestry,query:async pid=>({pid,status})}),{code:'ANCESTRY_ABSENCE_UNVERIFIED'});
});

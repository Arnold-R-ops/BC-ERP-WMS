import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {DATA,PORT,ADMIN,assertAuthorizedContext,checkCommand,processAbsence,validateLocal,createR8Environment} from './platform-r8-environment.mjs';
import {RUN,PG} from './platform-r8-backend-snapshot.mjs';
const TARGET={directory:DATA,port:PORT};
import {POWERSHELL} from './platform-launch-preflight.mjs';
const sid='S-1-5-21-3770167052-703495069-1631994977-1001';
const proof=(pid,executable)=>({status:'AVAILABLE',pid,parentPid:123,executable,commandLineReadable:true,commandLineSha256:'a'.repeat(64),creationEpoch:105,ownerSid:sid});
const report=()=>({schema:1,checkedAt:'synthetic',target:TARGET,expected:{...TARGET,ownerPid:123,ownerExecutable:'D:/synthetic/node.exe'},
 context:{status:'AVAILABLE',user:'ARNOLD\\Arnold',sid,restrictedToken:false,administratorEnabled:true,elevated:true,elevationType:1,powerShellPid:124,powerShellPath:POWERSHELL},
 self:proof(124,POWERSHELL),owner:proof(123,'D:/synthetic/node.exe'),
 postgres:{status:'AVAILABLE',count:0,incomplete:0,targetMatches:[],processIds:[],parentIds:[]},
 listeners:{status:'AVAILABLE',total:0,malformed:0,selected:[],pidProofs:[]},directory:{status:'ABSENT'},pidFile:{status:'ABSENT'}});
const command=(output='success')=>({exitCode:0,exited:true,outputComplete:true,terminationConfirmed:true,timedOut:false,aborted:false,spawnError:null,output});
const expected={...TARGET,executable:PG+'/postgres.exe',address:'127.0.0.1',clusterId:'1234567890',earliestStartEpoch:100,latestStartEpoch:110};
function live(){const r=report(),p={...proof(900,PG+'/postgres.exe'),parentPid:800,directory:DATA};
 r.directory={status:'PRESENT',directory:true};r.pidFile={status:'PRESENT',pid:'900',directory:DATA,startedEpoch:'105',port:'55449',address:'127.0.0.1'};
 r.postgres={status:'AVAILABLE',count:1,incomplete:0,targetMatches:[p],processIds:[900],parentIds:[800]};
 r.listeners.selected=[{address:'127.0.0.1',port:55449,pid:900}];r.listeners.pidProofs=[p];return {r,p};}

test('r8 environment import is inert and binds only the approved fresh cluster',()=>{
 assert.ok(RUN.endsWith('/platform-db-acceptance-20260916/r8'));assert.equal(ADMIN,'p3_r8_initializer');assert.equal(PORT,55449);
 assert.equal(typeof createR8Environment,'function');
});
test('s2 approved context checker is reused without weakening identity',()=>{
 assert.equal(assertAuthorizedContext(report()).user,'ARNOLD\\Arnold');
 const r=report();r.context.restrictedToken=true;assert.throws(()=>assertAuthorizedContext(r));
});
test('matching local identity requires directory, cluster, owner, parent and listener',()=>{
 const {r,p}=live(),v=validateLocal(expected,r,p,expected.clusterId);assert.equal(v.pid,900);assert.equal(v.parentPid,800);
});
for(const field of ['ownerSid','directory','executable','parentPid','creationEpoch'])
 test('r8 local identity refuses changed '+field,()=>{const {r,p}=live(),changed={...p,[field]:field==='parentPid'?0:field==='creationEpoch'?106:'wrong'};assert.throws(()=>validateLocal(expected,r,changed,expected.clusterId));});
test('wrong physical cluster or listener ownership is refused',()=>{
 const {r,p}=live();assert.throws(()=>validateLocal(expected,r,p,'wrong'));
 r.listeners.selected[0].pid=901;assert.throws(()=>validateLocal(expected,r,p,expected.clusterId));
});
test('query failure is not absence and absent PID is not a fabricated identity',()=>{
 assert.equal(validateLocal(expected,report(),null,expected.clusterId),null);
 const r=report();r.postgres.status='ACCESS_DENIED';assert.throws(()=>processAbsence(r));
});
test('startup restricted token diagnostics cannot be hidden behind exit zero',()=>{
 for(const code of [87,3])assert.throws(()=>checkCommand(command('pg_ctl: could not create restricted token: error code '+code+'\nserver started'),'START'),{code:'RESTRICTED_TOKEN_ERROR'});
});
test('actual adapter preserves shared parent chain, PID reuse checks and independent ancestry absence',()=>{
 const text=fs.readFileSync(new URL('./platform-r8-environment.mjs',import.meta.url),'utf8');
 for(const literal of ['verifyLauncherAncestry({','confirmAncestryStopped({','queryWindowsProcess(','assertAuthorizedContext(r)','creationTicks','launchReceipt=r.receipt','firstFailure={','Validated ancestry required before SQL'])assert.ok(text.includes(literal),literal);
 assert.doesNotMatch(text,/RunAs|Stop-Process|Set-ExecutionPolicy/);
});


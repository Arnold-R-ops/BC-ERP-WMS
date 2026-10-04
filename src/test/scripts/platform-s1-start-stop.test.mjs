import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {TARGET,DATA,PG,ROLE,PORT,RUN,SQL,AUTH,assertAuthorizedContext,checkCommand,processAbsence,localIdentity,runS1Sequence} from './platform-s1-start-stop.mjs';
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
function fixture(patch={}){const calls=[];const adapter={preflight:async()=>calls.push('preflight'),initialize:async()=>{calls.push('initialize');return command();},prepare:async()=>{calls.push('prepare');return expected;},start:async()=>{calls.push('start');return {status:'READY',identity:{pid:900}};},verify:async()=>calls.push('verify'),cleanup:async()=>{calls.push('cleanup');return {status:'STOPPED',attempted:true,completed:true};},closeout:async()=>{calls.push('closeout');return {status:'PASSED'};},...patch};return {adapter,calls};}
test('fixed single s1 target has no business database or migration SQL',()=>{
 assert.ok(RUN.endsWith('/platform-start-stop-validation-20260914/s1'));assert.equal(PORT,55449);assert.equal(ROLE,'p3_s1_initializer');
 assert.ok(AUTH.includes('arnold-elevated-empty-cluster-only'));assert.match(SQL,/^select json_build_object/);assert.doesNotMatch(SQL,/create|insert|update|delete|alter|drop|flyway/i);
});
test('approved explicit Windows context passes',()=>assert.equal(assertAuthorizedContext(report()).user,'ARNOLD\\Arnold'));
for(const patch of [{user:'ARNOLD\\CodexSandboxOffline'},{sid:'wrong'},{restrictedToken:true},{administratorEnabled:false},{elevated:false},{elevationType:2}])
 test('changed approved context fails: '+Object.keys(patch)[0],()=>{const r=report();Object.assign(r.context,patch);assert.throws(()=>assertAuthorizedContext(r));});
test('CIM denied cannot be bypassed by the correct username',()=>{const r=report();r.postgres.status='ACCESS_DENIED';assert.throws(()=>assertAuthorizedContext(r),{code:'IDENTITY_QUERY_ACCESS_DENIED'});});
for(const program of ['initdb','pg_ctl'])for(const code of [87,3])
 test(program+' error '+code+' stops even with exit zero and a success tail',()=>assert.throws(()=>checkCommand(command(program+': could not create restricted token: error code '+code+'\nSuccess.\n'),'SYNTHETIC'),{code:'RESTRICTED_TOKEN_ERROR'}));
test('startup error unrelated to 87/3 still cannot be hidden by exit zero',()=>assert.throws(()=>checkCommand(command('pg_ctl: could not start server\nserver started'),'START'),{code:'POSTGRES_COMMAND_DIAGNOSTIC_ERROR'}));
test('executor failure, incomplete output or timeout is never successful',()=>{for(const patch of [{exitCode:1},{exited:false},{outputComplete:false},{terminationConfirmed:false},{timedOut:true},{aborted:true},{spawnError:'EACCES'}])assert.throws(()=>checkCommand({...command(),...patch},'START'),{code:'COMMAND_EXECUTOR_FAILED'});});
test('ordinary successful command is accepted unchanged',()=>{const r=command();assert.equal(checkCommand(r,'START'),r);});
test('safe absence needs complete queries and rejects a transitive owned descendant',()=>{
 const r=report();assert.equal(processAbsence(r).confirmed,true);
 Object.assign(r.postgres,{count:2,processIds:[801,802],parentIds:[800,801]});assert.deepEqual(processAbsence(r,{launcherPids:[800]}),{confirmed:false,descendants:[801,802]});
 r.postgres.status='QUERY_FAILED';assert.throws(()=>processAbsence(r));
});
test('matching local identity proves Windows owner, PID, command directory, cluster and listener',()=>{const {r,p}=live();const local=localIdentity(expected,r,p,expected.clusterId);assert.equal(local.pid,900);assert.equal(local.ownerSid,sid);assert.equal(local.parentPid,800);});
for(const field of ['ownerSid','directory','executable','parentPid','creationEpoch'])
 test('local identity refuses changed '+field,()=>{const {r,p}=live(),changed={...p,[field]:field==='parentPid'?0:field==='creationEpoch'?106:'wrong'};assert.throws(()=>localIdentity(expected,r,changed,expected.clusterId));});
test('wrong physical cluster or listener address/owner is refused',()=>{
 const {r,p}=live();assert.throws(()=>localIdentity(expected,r,p,'wrong'));
 r.listeners.selected[0].address='0.0.0.0';assert.throws(()=>localIdentity(expected,r,p,expected.clusterId));
});
test('absence of PID file is not a fabricated process identity',()=>assert.equal(localIdentity(expected,report(),null,expected.clusterId),null));
test('successful sequence performs exactly one initialization and start followed by normal cleanup',async()=>{
 const f=fixture(),r=await runS1Sequence(f.adapter);assert.equal(r.status,'PASSED');assert.equal(r.normalShutdownProven,true);assert.deepEqual(f.calls,['preflight','initialize','prepare','start','verify','cleanup','closeout']);
});
test('preflight rejection prevents initialization and startup',async()=>{const f=fixture({preflight:async()=>{throw Error('query denied');},cleanup:async()=>({status:'NOT_STARTED',attempted:false,completed:false})});const r=await runS1Sequence(f.adapter);assert.equal(r.failedStage,'preflight');assert.equal(r.normalShutdownProven,false);assert.deepEqual(f.calls,['closeout']);});
test('initialization error 87 prevents control, start and SQL even if initialization exits zero',async()=>{
 const f=fixture({initialize:async()=>command('initdb: could not create restricted token: error code 87\nSuccess'),cleanup:async()=>({status:'NOT_STARTED_ABSENCE_CONFIRMED',attempted:false,completed:false})});const r=await runS1Sequence(f.adapter);assert.equal(r.failedStage,'initialize');assert.equal(r.firstFailure.code,'RESTRICTED_TOKEN_ERROR');assert.deepEqual(r.firstFailure.windowsErrorCodes,[87]);assert.equal(r.normalShutdownProven,false);assert.deepEqual(f.calls,['preflight','closeout']);
});
test('startup failure retains lifecycle and does not repeat its cleanup or run SQL verification',async()=>{
 const cleanup={status:'CLEANUP_FAILED',attempted:false,completed:false};const failure={stage:'START',code:'RESTRICTED_TOKEN_ERROR'};
 const f=fixture({start:async()=>({status:'STARTUP_UNVERIFIED',firstFailure:failure,cleanup}),cleanup:async()=>assert.fail('must not repeat cleanup')});const r=await runS1Sequence(f.adapter);assert.equal(r.firstFailure,failure);assert.equal(r.lifecycle.status,'STARTUP_UNVERIFIED');assert.equal(r.cleanup,cleanup);assert.equal(r.liveIdentityVerified,false);
});
test('cleanup error never overwrites startup first cause and credentials closeout still runs',async()=>{
 const first={stage:'START',code:'RESTRICTED_TOKEN_ERROR'};const f=fixture({start:async()=>({status:'FAILED',firstFailure:first}),cleanup:async()=>{throw Error('secondary');}});const r=await runS1Sequence(f.adapter);assert.equal(r.firstFailure,first);assert.equal(r.cleanup.status,'CLEANUP_FAILED');assert.ok(f.calls.includes('closeout'));
});
test('already absent is not recorded as an actual normal stop',async()=>{const f=fixture({cleanup:async()=>({status:'ALREADY_STOPPED',attempted:false,completed:true})});const r=await runS1Sequence(f.adapter);assert.equal(r.status,'FAILED');assert.equal(r.normalShutdownProven,false);});
test('credential or final identity closeout failure prevents acceptance',async()=>{const f=fixture({closeout:async()=>({status:'FAILED',failure:{code:'CREDENTIAL_FILE_REMAINS'}})});const r=await runS1Sequence(f.adapter);assert.equal(r.status,'FAILED');assert.equal(r.firstFailure.code,'CREDENTIAL_FILE_REMAINS');});
test('real entry uses shared lifecycle, suppresses SQL after startup error and has no retry or unsafe process control',()=>{
 const text=fs.readFileSync(new URL('./platform-s1-start-stop.mjs',import.meta.url),'utf8');
 assert.ok(text.includes('life=await startOwnedDatabase(liveAdapter,expected,'));assert.ok(text.includes('stopOwnedDatabase(liveAdapter,expected,pinned,'));
 assert.ok(text.includes("if(firstFailure)throw new S1Failure('SQL_PROBE_SUPPRESSED_AFTER_START_FAILURE'"));
 assert.ok(text.includes('return initializationPreflight({inspect:'));assert.ok(text.includes('validateInputs()'));
 assert.doesNotMatch(text,/\bRunAs\b|Set-ExecutionPolicy|Stop-Process|pg_terminate_backend|CREATE DATABASE|flyway.*migrate/i);
});

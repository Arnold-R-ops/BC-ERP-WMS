// Single, explicitly authorized OS/empty-cluster smoke. No migration, business DB, HTTP or product code.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runProcess,startOwnedDatabase,stopOwnedDatabase,assertOwned,assertReady,IdentityError,failureDetails} from './platform-process-lifecycle.mjs';
import {inspectWindowsEnvironment,requireInspectionCapability,confirmTargetAbsent,initializationPreflight,contextBinding,queryWindowsProcess} from './platform-launch-preflight.mjs';
import {verifyLauncherAncestry,confirmAncestryStopped} from './platform-process-ancestry.mjs';
export const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const RUN=ROOT+'/target/platform-start-stop-validation-20260914/s1';
export const DATA=RUN+'/pgdata',DELIVERY=ROOT+'/docs/releases/platform-start-stop-validation-20260914/s1';
export const PREPARATION=ROOT+'/target/platform-s1-tool-validation-20260914/a1';
export const PG='D:/ITsoftware/PostgreSQL/bin',ROLE='p3_s1_initializer',PORT=55449;
export const TARGET=Object.freeze({directory:DATA,port:PORT});
export const SQL="select json_build_object('database',current_database(),'user',current_user,'port',inet_server_port(),'address',host(inet_server_addr()),'listenAddresses',current_setting('listen_addresses'),'directory',current_setting('data_directory'),'clusterId',(pg_control_system()).system_identifier::text);";
export const AUTH='--authorized-20260914-s1-arnold-elevated-empty-cluster-only';
const canonical=p=>typeof p==='string'?path.win32.normalize(p).replaceAll('\\','/').toLowerCase():'';
const digest=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
export class S1Failure extends Error{constructor(code,stage,details={}){super(code);this.name='S1Failure';this.code=code;this.stage=stage;this.details=details;}}
export function assertAuthorizedContext(report){
 requireInspectionCapability(report);const c=report.context;
 if(c.user!=='ARNOLD\\Arnold'||c.sid!=='S-1-5-21-3770167052-703495069-1631994977-1001'||c.restrictedToken!==false||c.administratorEnabled!==true||c.elevated!==true||c.elevationType!==1)
  throw new S1Failure('UNAUTHORIZED_WINDOWS_CONTEXT','PREFLIGHT');
 return contextBinding(report);
}
export function checkCommand(result,stage){
 const output=String(result.output??'');
 const windowsErrorCodes=[...new Set([...output.matchAll(/error code\s+(\d+)/gi)].map(m=>Number(m[1])))];
 if(/restricted token/i.test(output)&&windowsErrorCodes.some(n=>n===87||n===3))throw new S1Failure('RESTRICTED_TOKEN_ERROR',stage,{exitCode:result.exitCode,windowsErrorCodes});
 if(/^\s*(?:initdb|pg_ctl):\s*(?:error:|could not|cannot|failed)/im.test(output))throw new S1Failure('POSTGRES_COMMAND_DIAGNOSTIC_ERROR',stage,{exitCode:result.exitCode,windowsErrorCodes});
 if(result.exitCode!==0||!result.exited||result.timedOut||result.aborted||result.spawnError||!result.outputComplete||!result.terminationConfirmed)
  throw new S1Failure('COMMAND_EXECUTOR_FAILED',stage,{exitCode:result.exitCode,timedOut:result.timedOut,spawnError:result.spawnError});
 return result;
}
export function processAbsence(report,{pinned,launcherPids=[]}={}){
 requireInspectionCapability(report);
 const basic=confirmTargetAbsent(report,pinned).status==='CONFIRMED_ABSENT';
 const owned=new Set([...launcherPids,...(pinned?[pinned.pid]:[])]);
 for(let change=true;change;){change=false;for(let i=0;i<report.postgres.processIds.length;i++)if(owned.has(report.postgres.parentIds[i])&&!owned.has(report.postgres.processIds[i])){owned.add(report.postgres.processIds[i]);change=true;}}
 const descendants=report.postgres.processIds.filter(pid=>owned.has(pid));
 return {confirmed:basic&&descendants.length===0,descendants};
}
export function localIdentity(expected,report,proof,clusterId){
 requireInspectionCapability(report);
 if(report.pidFile.status==='ABSENT')return null;
 const pid=Number(report.pidFile.pid),matching=report.postgres.targetMatches.filter(p=>p.pid===pid);
 if(report.directory.status!=='PRESENT'||report.directory.directory!==true||matching.length!==1||proof?.status!=='AVAILABLE'
  ||proof.pid!==pid||proof.ownerSid!==report.context.sid||!Number.isInteger(proof.parentPid)||proof.parentPid<=0
  ||canonical(proof.directory)!==canonical(DATA)||canonical(proof.executable)!==canonical(PG+'/postgres.exe')
  ||matching[0].parentPid!==proof.parentPid||matching[0].creationEpoch!==proof.creationEpoch
  ||((matching[0].creationTicks!==undefined||proof.creationTicks!==undefined)&&matching[0].creationTicks!==proof.creationTicks)||clusterId!==expected.clusterId)
  throw new IdentityError('S1 process owner, command directory, parent or cluster mismatch');
 return assertOwned(expected,{pid,processPid:proof.pid,parentPid:proof.parentPid,ownerSid:proof.ownerSid,
  directory:report.pidFile.directory,commandDirectory:proof.directory,executable:proof.executable,
  startedEpoch:Number(report.pidFile.startedEpoch),processStartedEpoch:proof.creationEpoch,
  port:Number(report.pidFile.port),address:report.pidFile.address,clusterId,
  listeners:report.listeners.selected.filter(l=>l.port===PORT)});
}
export async function runS1Sequence(adapter){
 const result={status:'RUNNING',stages:[],firstFailure:null,initialized:false,startAttempted:false,liveIdentityVerified:false,normalShutdownProven:false};let stage='preflight',expected;
 const perform=async(name,fn)=>{stage=name;const row={stage:name,status:'RUNNING'};result.stages.push(row);try{const value=await fn();row.status='PASSED';return value;}catch(e){row.status='FAILED';throw e;}};
 try{
  await perform('preflight',()=>adapter.preflight());
  await perform('initialize',async()=>{checkCommand(await adapter.initialize(),'INITIALIZE');result.initialized=true;});
  expected=await perform('physical-identity',()=>adapter.prepare());
  result.lifecycle=await perform('start',async()=>{result.startAttempted=true;const r=await adapter.start(expected);result.lifecycle=r;
   if(r.status!=='READY'){result.firstFailure=adapter.firstFailure?.()??r.firstFailure;throw new S1Failure('STARTUP_NOT_READY','START');}return r;});
  await perform('live-identity',async()=>{await adapter.verify(expected,result.lifecycle.identity);result.liveIdentityVerified=true;});
 }catch(e){result.status='FAILED';result.failedStage=stage;result.firstFailure??={...failureDetails(e,stage),...(e.details??{})};}
 finally{
  try{result.cleanup=result.lifecycle?.cleanup??await adapter.cleanup(expected,result.lifecycle?.identity);
   result.normalShutdownProven=result.cleanup.status==='STOPPED'&&result.cleanup.attempted===true&&result.cleanup.completed===true;
  }catch(e){result.cleanup={status:'CLEANUP_FAILED',attempted:false,completed:false,failure:failureDetails(e,'CLEANUP')};}
  try{result.closeout=await adapter.closeout();}catch(e){result.closeout={status:'FAILED',failure:failureDetails(e,'CLOSEOUT')};}
  if(result.status!=='FAILED'&&result.initialized&&result.liveIdentityVerified&&result.normalShutdownProven&&result.closeout.status==='PASSED')result.status='PASSED';
  else{result.status='FAILED';result.firstFailure??=result.cleanup.failure??result.closeout.failure??{stage:'CLEANUP',name:'UnverifiedCloseout'};}
 }
 return result;
}
function absent(p){try{fs.lstatSync(p);return false;}catch(e){if(e.code==='ENOENT')return true;throw e;}}
function validateInputs(){
 const m=read(DELIVERY+'/input-manifest.json'),v=read(DELIVERY+'/no-database-results.json');
 if(process.version!=='v24.12.0'||canonical(process.cwd())!==canonical(ROOT)||fileURLToPath(import.meta.url).replaceAll('\\','/')!==m.source+'/src/test/scripts/platform-s1-start-stop.mjs')throw new S1Failure('UNSEALED_EXECUTOR','INPUT');
 const gates={'platform-process-lifecycle.test.mjs':35,'platform-launch-preflight.test.mjs':40,'platform-s1-start-stop.test.mjs':33};
 if(v.status!=='PASSED'||v.inputId!==m.id||v.suites.length!==3||v.totals?.passed!==108||v.suites.some(s=>s.tests!==gates[s.script]||s.passed!==s.tests||s.failed||s.skipped||s.cancelled)||new Set(v.suites.map(s=>s.script)).size!==3)throw new S1Failure('NO_DATABASE_GATES_NOT_PASSED','INPUT');
 for(const f of m.files)if(digest(m.source+'/'+f.path)!==f.sha256||digest(ROOT+'/'+f.path)!==f.sha256)throw new S1Failure('SOURCE_FINGERPRINT_MISMATCH','INPUT');
 for(const t of m.tools)if(digest(t.path)!==t.sha256)throw new S1Failure('TOOL_FINGERPRINT_MISMATCH','INPUT');
 return m;
}
async function actual(){
 if(process.argv.length!==3||process.argv[2]!==AUTH)throw new S1Failure('EXPLICIT_S1_AUTHORIZATION_REQUIRED','AUTHORIZATION');
 const manifest=validateInputs();
 if(!absent(DELIVERY+'/result.json'))throw new S1Failure('S1_ALREADY_ATTEMPTED','INPUT');
 const evidence={inputId:manifest.id,startedAt:new Date().toISOString(),commands:[],inspections:[],initializationCommands:0,sqlConnections:0,serverStartCommands:0,stopCommands:0,migrations:0,businessDatabaseCreates:0,businessTests:0};
 const env={...process.env};for(const key of Object.keys(env))if(/^(PG|SPRING_|P3_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(key))delete env[key];
 let password,rootCreated=false,context,expected,pinned,life,firstFailure,ready,initAttempted=false,initPid,launchPid,launchReceipt,ancestry;
 const pwfile=RUN+'/init-password.ephemeral',knownLauncherPids=[];
 const redact=s=>{s=String(s).replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[JWT REDACTED]');return password?s.replaceAll(password,'[REDACTED]'):s;};
 async function command(executable,args,options={}){
  const {label,signal,input='',extraEnv={},timeoutMs=15000,fileOutput=false}=options;
  const record={executable,args,startedAt:new Date().toISOString(),cwd:rootCreated?RUN:ROOT};evidence.commands.push(record);
  const outputFile=fileOutput?RUN+'/'+label+'.output':undefined;
  const r=await runProcess(executable,args,{cwd:record.cwd,env:{...env,...extraEnv},input,signal,timeoutMs:options.timeout??timeoutMs,outputFile,
   spawnProcess:(exe,a,o)=>{const child=spawn(exe,a,o);record.pid=child.pid??null;return child;}});
  const raw=outputFile&&fs.existsSync(outputFile)?fs.readFileSync(outputFile,'utf8'):r.output;
  const output=redact(raw);if(outputFile&&output!==raw)fs.writeFileSync(outputFile,output);
  Object.assign(record,{exitCode:r.exitCode,exited:r.exited,timedOut:r.timedOut,aborted:r.aborted,spawnError:r.spawnError,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,elapsedMs:r.elapsedMs,timing:r.timing,receipt:r.receipt});
  if(label){const log=DELIVERY+'/'+label+'.log';fs.writeFileSync(log,output,{flag:'wx'});record.log=log;record.logSha256=digest(log);}
  if(outputFile){record.outputFile=outputFile;record.outputSha256=digest(outputFile);}
  return {...r,output,pid:record.pid};
 }
 let inspection=0;
 async function inspect(label,signal){
  const r=await inspectWindowsEnvironment(command,{...TARGET,signal});
  const item={label,sequence:++inspection,report:r};evidence.inspections.push(item);
  assertAuthorizedContext(r);if(context)requireInspectionCapability(r,{context});return r;
 }
 async function control(label,signal){const r=checkCommand(await command(PG+'/pg_controldata.exe',['-D',DATA],{label,signal}),label);
  const id=r.output.match(/Database system identifier:\s*(\d+)/)?.[1];if(!id)throw new S1Failure('PHYSICAL_CLUSTER_ID_MISSING','CONTROL');return id;}
 let processQuery=0;
 async function proof(pid,signal,stage='SERVER_PROCESS'){
  return queryWindowsProcess((exe,args,options)=>command(exe,args,{...options,label:'process-proof-'+(++processQuery)}),{pid,stage,signal});
 }
 let localCount=0,lastLocal;
 async function observe(signal){
  const r=await inspect('local-identity',signal);
  if(r.pidFile.status==='ABSENT'){lastLocal=null;return null;}
  const p=await proof(Number(r.pidFile.pid),signal),clusterId=await control('local-control-'+(++localCount),signal);
  const value=localIdentity(expected,r,p,clusterId);if(pinned&&(value.parentPid!==pinned.parentPid||value.ownerSid!==pinned.ownerSid))throw new IdentityError('S1 pinned owner or parent changed');
  assertOwned(expected,value,pinned);
  ancestry=await verifyLauncherAncestry({local:value,server:p,launcher:launchReceipt,launcherExecutable:PG+'/pg_ctl.exe',relayExecutable:'C:/Windows/System32/cmd.exe',directory:DATA,ownerSid:context.sid,
   query:(pid,stage,s)=>proof(pid,s,stage),pinned:ancestry,signal});
  evidence.ancestry=ancestry;value.ancestry=ancestry;lastLocal=value;return value;
 }
 let absenceCount=0;
 async function isStopped(signal,current){const r=await inspect('absence-'+(++absenceCount),signal);const absent=processAbsence(r,{pinned:current,launcherPids:knownLauncherPids});
  if(current){const p=await proof(current.pid,signal);if(p.status!=='ABSENT')return false;}
  if(ancestry&&!await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage),signal}))return false;
  return absent.confirmed;}
 const liveAdapter={
  launch:async signal=>{
   evidence.serverStartCommands++;const r=await command(PG+'/pg_ctl.exe',['start','-D',DATA,'-l',RUN+'/postgres-private.log','-w','-t','30'],{signal,label:'pg-start',timeoutMs:35000,fileOutput:true});
   launchPid=r.pid;launchReceipt=r.receipt;if(launchReceipt)launchReceipt.ownerSid=context.sid;if(launchPid)knownLauncherPids.push(launchPid);
   try{return checkCommand(r,'START');}catch(e){firstFailure={...failureDetails(e,'START'),...e.details};throw e;}
  },
  inspectLocal:observe,
  probeReady:async(local,signal)=>{
   // On launcher errors (including exit 0 + error 87/3), only identity-safe cleanup follows; no SQL validation.
   if(firstFailure)throw new S1Failure('SQL_PROBE_SUPPRESSED_AFTER_START_FAILURE','READINESS');
   if(!local.ancestry||local.ancestry!==ancestry)throw new IdentityError('Validated launcher ancestry required before SQL');
   evidence.sqlConnections++;const r=checkCommand(await command(PG+'/psql.exe',['-X','-w','-h','127.0.0.1','-p','55449','-U',ROLE,'-d','postgres','-v','ON_ERROR_STOP=1','-q','-A','-t'],
    {signal,input:SQL,extraEnv:{PGPASSWORD:password,PGCONNECT_TIMEOUT:'2',PGOPTIONS:'-c statement_timeout=2000'},label:'sql-identity-'+evidence.sqlConnections,timeoutMs:4000}),'SQL_IDENTITY');
   ready=JSON.parse(r.output.trim());return ready;
  },
  stop:async(_,signal)=>{evidence.stopCommands++;return checkCommand(await command(PG+'/pg_ctl.exe',['stop','-D',DATA,'-m','fast','-w','-t','30'],{signal,label:'pg-stop',timeoutMs:32000,fileOutput:true}),'STOP');},
  isStopped
 };
 const adapter={
  preflight:async()=>{
   if(!absent(RUN))throw new S1Failure('S1_ROOT_ALREADY_EXISTS','PREFLIGHT');
   const r=await inspect('before-root-creation');context=assertAuthorizedContext(r);evidence.context=context;evidence.beforeOtherListeners=r.listeners.selected;
   await initializationPreflight({inspect:async()=>r,initialize:async()=>{}});
   validateInputs();
  },
  initialize:async()=>{
   return initializationPreflight({inspect:()=>inspect('immediately-before-initialize'),initialize:async()=>{
    if(!absent(RUN))throw new S1Failure('S1_ROOT_ALREADY_EXISTS','INITIALIZE');
    fs.mkdirSync(path.dirname(RUN),{recursive:true});fs.mkdirSync(RUN);rootCreated=true;
    password=crypto.randomBytes(32).toString('hex');fs.writeFileSync(pwfile,password+'\n',{flag:'wx',mode:0o600});initAttempted=true;
    try{evidence.initializationCommands++;const r=await command(PG+'/initdb.exe',['-D',DATA,'-U',ROLE,'--pwfile='+pwfile,'--auth-host=scram-sha-256','--auth-local=scram-sha-256','--encoding=UTF8','--locale=C',
     '-c','listen_addresses=127.0.0.1','-c','port=55449','-c','max_connections=10','-c','log_statement=none','-c','log_min_error_statement=panic','-c','log_parameter_max_length_on_error=0'],{label:'initdb',timeoutMs:60000,fileOutput:true});
     initPid=r.pid;if(initPid)knownLauncherPids.push(initPid);evidence.initReportedOwner=r.output.match(/owned by user "([^"]+)"/)?.[1]??null;return r;
    }finally{if(!absent(pwfile))fs.unlinkSync(pwfile);}
   }});
  },
  prepare:async()=>{
   if(evidence.initReportedOwner!=='Arnold')throw new S1Failure('INITIALIZER_WINDOWS_OWNER_MISMATCH','PHYSICAL_IDENTITY');
   const clusterId=await control('control-before-start');
   // Only this newly created cluster's HBA. Existing services and configuration are never touched.
   fs.writeFileSync(DATA+'/pg_hba.conf',`host postgres ${ROLE} 127.0.0.1/32 scram-sha-256\nhost all all 0.0.0.0/0 reject\nhost all all ::0/0 reject\n`);
   const r=await inspect('before-start');if(!processAbsence(r,{launcherPids:knownLauncherPids}).confirmed)throw new IdentityError('Resource appeared before launch');
   expected={directory:DATA,executable:PG+'/postgres.exe',port:PORT,address:'127.0.0.1',clusterId,database:'postgres',user:ROLE,earliestStartEpoch:Math.floor(Date.now()/1000)-2,latestStartEpoch:Math.floor(Date.now()/1000)+90};
   evidence.expected=expected;return expected;
  },
  start:async()=>{life=await startOwnedDatabase(liveAdapter,expected,{launcherMs:37000,readinessMs:10000,cleanupMs:45000});pinned=life.identity;return life;},
  firstFailure:()=>firstFailure,
  verify:async(e,id)=>{assertReady(e,id,ready);assertOwned(e,lastLocal,id);evidence.sqlIdentity=ready;evidence.processIdentity=id;},
  cleanup:async()=>{
   if(life?.cleanup)return life.cleanup;
   if(expected&&evidence.serverStartCommands)return stopOwnedDatabase(liveAdapter,expected,pinned,{cleanupMs:45000});
   if(!initAttempted)return {status:'NOT_STARTED',attempted:false,completed:false};
   // Failed init: no server start occurred. Never relabel an absence observation as a normal stop.
   const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),15000);
   try{const r=await inspect('failed-initialization-absence',abort.signal),a=processAbsence(r,{launcherPids:knownLauncherPids});
    return {status:a.confirmed?'NOT_STARTED_ABSENCE_CONFIRMED':'UNVERIFIED_RESOURCE_REMAINS',attempted:false,completed:false,absence:a};
   }finally{clearTimeout(timer);}
  },
  closeout:async()=>{
   const errors=[];if(rootCreated&&!absent(pwfile))fs.unlinkSync(pwfile);
   evidence.temporaryCredentialFileRemoved=absent(pwfile);if(!evidence.temporaryCredentialFileRemoved)errors.push('CREDENTIAL_FILE_REMAINS');
   if(rootCreated&&fs.existsSync(RUN+'/postgres-private.log')){
    const raw=fs.readFileSync(RUN+'/postgres-private.log','utf8'),clean=redact(raw);if(raw!==clean)fs.writeFileSync(RUN+'/postgres-private.log',clean);
    evidence.serverLogSha256=digest(RUN+'/postgres-private.log');
   }
   try{const r=await inspect('final-os-check');evidence.finalAbsence=processAbsence(r,{pinned,launcherPids:knownLauncherPids});
    if(ancestry){evidence.finalAncestryAbsent=await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage)});if(!evidence.finalAncestryAbsent)errors.push('FINAL_ANCESTRY_REMAINS');}
    evidence.afterOtherListeners=r.listeners.selected.filter(l=>l.port!==PORT);
    evidence.otherListenersUnchanged=JSON.stringify(evidence.beforeOtherListeners)===JSON.stringify(evidence.afterOtherListeners);
    if(!evidence.finalAbsence.confirmed||!evidence.otherListenersUnchanged)errors.push('FINAL_RESOURCE_OR_OTHER_LISTENER_MISMATCH');
   }catch(e){errors.push(e.code??e.name);evidence.finalInspectionError=failureDetails(e,'FINAL_INSPECTION');}
   try{validateInputs();evidence.inputsUnchanged=true;}catch(e){errors.push(e.code);}
   password=undefined;
   return {status:errors.length?'FAILED':'PASSED',errors};
  }
 };
 const result=await runS1Sequence(adapter);
 Object.assign(evidence,{...result,completedAt:new Date().toISOString(),directoryPreserved:rootCreated&&!absent(DATA)});
 save(DELIVERY+'/result.json',evidence);
 console.log(JSON.stringify({status:result.status,failedStage:result.failedStage,firstFailure:result.firstFailure,context:evidence.context,serverStartCommands:evidence.serverStartCommands,sqlConnections:evidence.sqlConnections,normalShutdownProven:result.normalShutdownProven,cleanup:result.cleanup,credentialsRemoved:evidence.temporaryCredentialFileRemoved}));
 if(result.status!=='PASSED')process.exitCode=1;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await actual();

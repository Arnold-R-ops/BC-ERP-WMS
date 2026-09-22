// Supplementary first normal stop only. Never starts/reinitializes/connects to any database.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {runProcess,stopOwnedDatabase,assertOwned,IdentityError,failureDetails} from './platform-process-lifecycle.mjs';
import {inspectWindowsEnvironment,requireInspectionCapability,contextBinding,POWERSHELL} from './platform-launch-preflight.mjs';
import {ROOT,RUN,DATA,DELIVERY,PG,TARGET,assertAuthorizedContext,processAbsence,localIdentity,checkCommand} from './platform-s1-start-stop.mjs';
const RAW_HASH='f7ccfb905ae421e743c6ac3f9b116a36210ae090a4c4b4d2988732ca62c2d0fd';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const canonical=p=>String(p).replaceAll('\\','/').toLowerCase();
export function assertParent(chain,pinned,launcherPid){
 if(!chain||chain.status!=='AVAILABLE'||chain.pid!==pinned.parentPid||chain.parentPid!==launcherPid||canonical(chain.executable)!=='c:/windows/system32/cmd.exe'
  ||chain.ownerSid!==pinned.ownerSid||canonical(chain.directory)!==canonical(DATA)||chain.creationEpoch>pinned.startedEpoch||pinned.startedEpoch-chain.creationEpoch>2)
  throw new IdentityError('Exact pg_ctl -> cmd -> postgres ancestry mismatch');
}
function selfTest(){
 const pin={parentPid:36212,ownerSid:'synthetic',startedEpoch:105},p={status:'AVAILABLE',pid:36212,parentPid:60380,executable:'C:/Windows/System32/cmd.exe',ownerSid:'synthetic',directory:DATA,creationEpoch:105};
 assert.doesNotThrow(()=>assertParent(p,pin,60380));
 for(const fields of [{pid:1},{parentPid:1},{executable:'wrong'},{directory:'wrong'},{ownerSid:'wrong'},{creationEpoch:90},{status:'INCOMPLETE'}])assert.throws(()=>assertParent({...p,...fields},pin,60380),IdentityError);
 console.log(JSON.stringify({noDatabaseChecks:8,passed:8,databaseCommands:0}));
}
async function stop(){
 if(process.argv[2]!=='--authorized-s1-first-normal-stop-only'||process.version!=='v24.12.0'||canonical(process.cwd())!==canonical(ROOT))throw Error('Dedicated closeout authorization required');
 if(hash(DELIVERY+'/result.json')!==RAW_HASH||fs.existsSync(DELIVERY+'/checked-stop-attempt.json'))throw Error('Original result drift / supplemental stop already attempted');
 const original=JSON.parse(fs.readFileSync(DELIVERY+'/result.json','utf8')),pinned=original.lifecycle.identity,expected=original.expected;
 if(pinned.pid!==60768||pinned.parentPid!==36212||pinned.clusterId!=='7685305294091642656'||original.lifecycle.launcher.pid!==60380||original.stopCommands!==0||original.status!=='FAILED')throw Error('Unexpected original target');
 const manifest=JSON.parse(fs.readFileSync(DELIVERY+'/input-manifest.json','utf8'));
 for(const f of manifest.files)if(hash(ROOT+'/'+f.path)!==f.sha256||hash(manifest.source+'/'+f.path)!==f.sha256)throw Error('Source fingerprint drift');
 for(const t of manifest.tools)if(hash(t.path)!==t.sha256)throw Error('Tool fingerprint drift');
 const record={startedAt:new Date().toISOString(),originalResultSha256:RAW_HASH,scriptSha256:hash(fileURLToPath(import.meta.url)),purpose:'FIRST_NORMAL_STOP_NO_ACCEPTANCE_RETRY',commands:[],inspections:[],proofs:[],sqlConnections:0,serverStarts:0};
 fs.writeFileSync(DELIVERY+'/checked-stop-attempt.json',JSON.stringify(record,null,2)+'\n',{flag:'wx'});
 const env={...process.env};for(const k of Object.keys(env))if(/^(PG|SPRING_|P3_)/i.test(k))delete env[k];
 let count=0,context;
 async function command(exe,args,opt={}){
  const n=++count,outputFile=opt.stop?RUN+'/checked-normal-stop.output':undefined;
  const r=await runProcess(exe,args,{cwd:RUN,env,signal:opt.signal,timeoutMs:opt.timeout??opt.timeoutMs??10000,outputFile});
  const output=outputFile?fs.readFileSync(outputFile,'utf8'):r.output;
  record.commands.push({number:n,exe,args,exitCode:r.exitCode,exited:r.exited,timedOut:r.timedOut,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,elapsedMs:r.elapsedMs,outputSha256:crypto.createHash('sha256').update(output).digest('hex')});
  if(opt.stop)fs.writeFileSync(DELIVERY+'/checked-normal-stop.log',output,{flag:'wx'});
  return {...r,output};
 }
 async function inspect(signal){const r=await inspectWindowsEnvironment(command,{...TARGET,signal});record.inspections.push(r);assertAuthorizedContext(r);requireInspectionCapability(r,{context});context??=contextBinding(r);return r;}
 async function proof(pid,signal){
  const q=`$ErrorActionPreference='Stop';$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}' -ErrorAction Stop;if(-not $p){@{status='ABSENT';pid=${pid}}|ConvertTo-Json -Compress;exit 0};$o=Invoke-CimMethod -InputObject $p -MethodName GetOwnerSid -ErrorAction Stop;if(-not $p.CommandLine -or -not $p.ExecutablePath -or -not $p.CreationDate -or $o.ReturnValue -ne 0){throw 'Incomplete process proof'};$m=[regex]::Match($p.CommandLine,'(?:^|\\s)-D\\s+"([^"]+)"');@{status='AVAILABLE';pid=[int]$p.ProcessId;parentPid=[int]$p.ParentProcessId;ownerSid=$o.Sid;executable=$p.ExecutablePath;directory=$m.Groups[1].Value;creationEpoch=([DateTimeOffset]$p.CreationDate).ToUnixTimeSeconds()}|ConvertTo-Json -Compress`;
  const r=checkCommand(await command(POWERSHELL,['-NoProfile','-NonInteractive','-Command',q],{signal}),'CHECKED_STOP_PROCESS_QUERY');const p=JSON.parse(r.output.trim());record.proofs.push(p);return p;
 }
 const adapter={
  inspectLocal:async signal=>{
   const r=await inspect(signal);if(r.pidFile.status==='ABSENT')return null;
   const p=await proof(pinned.pid,signal),parent=await proof(pinned.parentPid,signal);assertParent(parent,pinned,60380);
   const c=checkCommand(await command(PG+'/pg_controldata.exe',['-D',DATA],{signal}),'CHECKED_STOP_CONTROL');
   const id=c.output.match(/Database system identifier:\s*(\d+)/)?.[1];const value=localIdentity(expected,r,p,id);assertOwned(expected,value,pinned);
   if(value.parentPid!==pinned.parentPid||value.ownerSid!==pinned.ownerSid)throw new IdentityError('Pinned parent/owner changed');return value;
  },
  stop:async(_,signal)=>checkCommand(await command(PG+'/pg_ctl.exe',['stop','-D',DATA,'-m','fast','-w','-t','30'],{signal,timeoutMs:32000,stop:true}),'CHECKED_NORMAL_STOP'),
  isStopped:async(signal,current)=>{
   const r=await inspect(signal),absent=processAbsence(r,{pinned:current??pinned,launcherPids:[60380,36212,55272]});
   if(!absent.confirmed)return false;
   for(const pid of [60768,36212])if((await proof(pid,signal)).status!=='ABSENT')return false;
   return true;
  }
 };
 try{
  record.cleanup=await stopOwnedDatabase(adapter,expected,pinned,{cleanupMs:45000});
  record.normalShutdownProven=record.cleanup.status==='STOPPED'&&record.cleanup.attempted&&record.cleanup.completed;
  const last=record.inspections.at(-1);record.finalListeners=last?.listeners.selected;record.credentialFileAbsent=!fs.existsSync(RUN+'/init-password.ephemeral');
  record.originalResultUnchanged=hash(DELIVERY+'/result.json')===RAW_HASH;
  record.status=record.normalShutdownProven&&record.credentialFileAbsent&&record.originalResultUnchanged?'PASSED_CLOSEOUT_ONLY':'FAILED_CLOSEOUT';
 }catch(e){record.status='FAILED_CLOSEOUT';record.failure=failureDetails(e,'SUPPLEMENTARY_CLOSEOUT');}
 record.completedAt=new Date().toISOString();fs.writeFileSync(DELIVERY+'/checked-stop-result.json',JSON.stringify(record,null,2)+'\n',{flag:'wx'});
 console.log(JSON.stringify({status:record.status,cleanup:record.cleanup,normalShutdownProven:record.normalShutdownProven,originalResultUnchanged:record.originalResultUnchanged,finalListeners:record.finalListeners}));
 if(record.status!=='PASSED_CLOSEOUT_ONLY')process.exitCode=1;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){if(process.argv[2]==='--self-test')selfTest();else await stop();}

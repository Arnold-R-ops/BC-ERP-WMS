// r9-only wiring of the s2-proven identity/query/ancestry/lifecycle chain. Import is inert.
import path from 'node:path';
import {RUN,PG} from './platform-r9-backend-snapshot.mjs';
import {BUDGETS} from './platform-r9-plan.mjs';
import {assertOwned,IdentityError,failureDetails} from './platform-process-lifecycle.mjs';
import {inspectWindowsEnvironment,requireInspectionCapability,queryWindowsProcess} from './platform-launch-preflight.mjs';
import {verifyLauncherAncestry,confirmAncestryStopped} from './platform-process-ancestry.mjs';
import {assertAuthorizedContext,checkCommand,processAbsence,SQL} from './platform-s2-start-stop.mjs';
export {assertAuthorizedContext,checkCommand,processAbsence};
export const DATA=RUN+'/pgdata',PORT=55449,ADMIN='p3_r9_initializer';
const canonical=p=>typeof p==='string'?path.win32.normalize(p).replaceAll('\\','/').toLowerCase():'';
export function validateLocal(expected,report,proof,clusterId){
 requireInspectionCapability(report);
 if(report.pidFile.status==='ABSENT')return null;
 const pid=Number(report.pidFile.pid),matching=report.postgres.targetMatches.filter(p=>p.pid===pid);
 if(report.directory.status!=='PRESENT'||report.directory.directory!==true||matching.length!==1||proof?.status!=='AVAILABLE'
  ||proof.pid!==pid||proof.ownerSid!==report.context.sid||!Number.isInteger(proof.parentPid)||proof.parentPid<=0
  ||canonical(proof.directory)!==canonical(DATA)||canonical(proof.executable)!==canonical(PG+'/postgres.exe')
  ||matching[0].parentPid!==proof.parentPid||matching[0].creationEpoch!==proof.creationEpoch
  ||((matching[0].creationTicks!==undefined||proof.creationTicks!==undefined)&&matching[0].creationTicks!==proof.creationTicks)||clusterId!==expected.clusterId)
  throw new IdentityError('R9 process owner, directory, parent or cluster mismatch');
 return assertOwned(expected,{pid,processPid:proof.pid,parentPid:proof.parentPid,ownerSid:proof.ownerSid,
  directory:report.pidFile.directory,commandDirectory:proof.directory,executable:proof.executable,
  startedEpoch:Number(report.pidFile.startedEpoch),processStartedEpoch:proof.creationEpoch,
  port:Number(report.pidFile.port),address:report.pidFile.address,clusterId,
  listeners:report.listeners.selected.filter(l=>l.port===PORT)});
}
export function createR9Environment({command,evidence,password}){
 let context,expected,pinned,launchReceipt,ancestry,firstFailure,queryCount=0,localCount=0,readyCount=0;
 const launcherPids=[];evidence.inspections??=[];evidence.serverStartCommands=0;evidence.stopCommands=0;
 async function inspect(label='inspection',signal){
  const r=await inspectWindowsEnvironment(command,{directory:DATA,port:PORT,signal});
  evidence.inspections.push({label,report:r});
  const verified=assertAuthorizedContext(r);if(context)requireInspectionCapability(r,{context});else context=verified;
  evidence.executionContext=context;return r;
 }
 async function proof(pid,signal,stage='SERVER_PROCESS'){
  return queryWindowsProcess((exe,args,options)=>command(exe,args,{...options,log:'process-proof-'+(++queryCount)+'.log'}),{pid,stage,signal});
 }
 async function control(label,signal){
  const r=checkCommand(await command(PG+'/pg_controldata.exe',['-D',DATA],{log:label+'.log',timeout:15000,signal}),label);
  const id=r.output.match(/Database system identifier:\s*(\d+)/)?.[1];if(!id)throw new IdentityError('Physical cluster ID missing');return id;
 }
 async function observe(signal){
  const r=await inspect('local-identity',signal);
  if(r.pidFile.status==='ABSENT')return null;
  const p=await proof(Number(r.pidFile.pid),signal),cluster=await control('local-control-'+(++localCount),signal);
  const value=validateLocal(expected,r,p,cluster);
  if(pinned&&(value.parentPid!==pinned.parentPid||value.ownerSid!==pinned.ownerSid))throw new IdentityError('R9 pinned owner or parent changed');
  assertOwned(expected,value,pinned);
  ancestry=await verifyLauncherAncestry({local:value,server:p,launcher:launchReceipt,launcherExecutable:PG+'/pg_ctl.exe',relayExecutable:'C:/Windows/System32/cmd.exe',directory:DATA,ownerSid:context.sid,
   query:(pid,stage,s)=>proof(pid,s,stage),pinned:ancestry,signal});
  evidence.ancestry=ancestry;value.ancestry=ancestry;return value;
 }
 async function isStopped(signal,current){
  const r=await inspect('stop-absence',signal),a=processAbsence(r,{pinned:current,launcherPids});
  if(current&&(await proof(current.pid,signal)).status!=='ABSENT')return false;
  if(ancestry&&!await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage),signal}))return false;
  return a.confirmed;
 }
 const adapter={
  launch:async signal=>{
   evidence.serverStartCommands++;
   const r=await command(PG+'/pg_ctl.exe',['start','-D',DATA,'-l',RUN+'/postgres-private.log','-w','-t','30'],{signal,log:'pg-start.log',timeout:BUDGETS.launcherCommandMs,fileOutput:true});
   launchReceipt=r.receipt;if(launchReceipt){launchReceipt.ownerSid=context.sid;launcherPids.push(launchReceipt.pid);}
   try{return checkCommand(r,'START');}catch(e){firstFailure={...failureDetails(e,'START'),...e.details};throw e;}
  },
  inspectLocal:observe,
  probeReady:async(local,signal)=>{
   if(firstFailure)throw new IdentityError('SQL probe suppressed after startup diagnostic');
   if(!local.ancestry||local.ancestry!==ancestry)throw new IdentityError('Validated ancestry required before SQL');
   const r=checkCommand(await command(PG+'/psql.exe',['-X','-w','-h','127.0.0.1','-p','55449','-U',ADMIN,'-d','postgres','-v','ON_ERROR_STOP=1','-q','-A','-t'],
    {signal,input:SQL,env:{PGPASSWORD:password(),PGCONNECT_TIMEOUT:'2',PGOPTIONS:'-c statement_timeout=2000'},log:'ready-'+(++readyCount)+'.log',timeout:BUDGETS.readySqlMs}),'SQL_IDENTITY');
   evidence.sqlIdentity=JSON.parse(r.output.trim());return evidence.sqlIdentity;
  },
  stop:async(_,signal)=>{
   evidence.stopCommands++;
   return checkCommand(await command(PG+'/pg_ctl.exe',['stop','-D',DATA,'-m','fast','-w','-t','30'],{signal,log:'pg-stop.log',timeout:BUDGETS.stopCommandMs,fileOutput:true}),'STOP');
  },
  isStopped
 };
 return {inspect,control,adapter,observe,isStopped,
  configure(clusterId){
   expected={directory:DATA,executable:PG+'/postgres.exe',port:PORT,address:'127.0.0.1',clusterId,database:'postgres',user:ADMIN,
    earliestStartEpoch:Math.floor(Date.now()/1000)-2,latestStartEpoch:Math.floor(Date.now()/1000)+90};evidence.expected=expected;return expected;
  },
  pin(identity){pinned=identity;},
  firstFailure:()=>firstFailure,
  async requireLive(){const value=assertOwned(expected,await observe(),pinned);if(value.listeners.length!==1)throw new IdentityError('Required listener absent');return value;},
  async closeout(){
   const r=await inspect('final-os-check'),absence=processAbsence(r,{pinned,launcherPids});
   const ancestryAbsent=ancestry?await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage)}):null;
   return {report:r,absence,ancestryAbsent};
  }
 };
}

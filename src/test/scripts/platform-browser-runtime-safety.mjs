// Browser-harness-only JVM options and post-stop identity checks. No OS effects on import.
import path from 'node:path';
import {IdentityError} from './platform-process-lifecycle.mjs';
import {requireInspectionCapability} from './platform-launch-preflight.mjs';
import {confirmAncestryStopped as strictAncestryStopped} from './platform-process-ancestry.mjs';
const canonical=p=>typeof p==='string'?path.win32.normalize(p).replaceAll('\\','/').toLowerCase():'';
const fail=code=>{const e=new IdentityError(code);e.code=code;throw e;};
export const RESTART_DISABLED='-Dspring.devtools.restart.enabled=false';
export function backendArguments(binding,clusterId){
 if(!/^\d{10,25}$/.test(clusterId)&&clusterId!=='<verified-public-cluster-id>')fail('BROWSER_CLUSTER_ARGUMENT');
 return [RESTART_DISABLED,'-Dp3.b4.database-authorized=true','-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55459/'+binding.DATABASE,
  '-Dp3.b4.cluster-id='+clusterId,'@'+binding.SOURCE+'/runtime.args','com.wms.browseracceptance.PlatformB1Harness'];
}
export function requireSealedBackendCommand(sealed,executable,args,clusterId){
 const expected=sealed.map(s=>s.replaceAll('<verified-public-cluster-id>',clusterId));
 if(JSON.stringify(expected)!==JSON.stringify([executable,...args]))fail('BROWSER_BACKEND_COMMAND_DRIFT');
}
function completeProof(p){
 if(p?.status!=='AVAILABLE'||!Number.isInteger(p.pid)||p.pid<=0||!Number.isInteger(p.parentPid)||p.parentPid<=0
  ||!path.win32.isAbsolute(p.executable??'')||typeof p.directory!=='string'||!/^S-\d+(?:-\d+)+$/.test(p.ownerSid??'')
  ||p.commandLineReadable!==true||!/^[a-f0-9]{64}$/.test(p.commandLineSha256??'')||!/^\d{18}$/.test(p.creationTicks??''))return false;
 const ms=Date.parse(p.creationUtc),tickMs=Number((BigInt(p.creationTicks)-621355968000000000n)/10000n);
 return Number.isFinite(ms)&&tickMs===ms&&p.creationEpoch===Math.floor(ms/1000);
}
/** PID equality or identity mismatch alone never proves that the original process ended. */
export function classifyStoppedPid(current,original,stopStartedAt){
 if(!completeProof(original)||current?.pid!==original.pid)return 'UNCONFIRMED';
 if(current.status==='ABSENT')return 'CONFIRMED_ABSENT';
 if(!completeProof(current))return 'UNCONFIRMED';
 if(current.creationTicks===original.creationTicks){
  return current.ownerSid===original.ownerSid&&canonical(current.executable)===canonical(original.executable)
   &&current.parentPid===original.parentPid&&canonical(current.directory)===canonical(original.directory)
   &&current.commandLineSha256===original.commandLineSha256?'ORIGINAL_PRESENT':'UNCONFIRMED';
 }
 const stopTime=Date.parse(stopStartedAt);
 return Number.isFinite(stopTime)&&BigInt(current.creationTicks)>BigInt(original.creationTicks)
  &&Date.parse(current.creationUtc)>=stopTime?'PID_REUSED':'UNCONFIRMED';
}
function requireNormalStop(r,expected,report,launcher){
 const start=Date.parse(r?.timing?.startedAt),end=Date.parse(r?.timing?.completedAt);
 if(!r||r.exited!==true||r.exitCode!==0||r.outputComplete!==true||r.terminationConfirmed!==true||r.timedOut||r.aborted||r.spawnError
  ||!Number.isFinite(start)||!Number.isFinite(end)||end<start||Date.parse(report.checkedAt)<end||!Number.isFinite(Date.parse(report.checkedAt))
  ||r.receipt?.exited!==true||r.receipt?.terminationConfirmed!==true||!Number.isInteger(r.receipt?.pid)||r.receipt.pid<=0
  ||canonical(r.receipt.executable)!==canonical(path.win32.join(path.win32.dirname(expected.executable),'pg_ctl.exe'))
  ||!launcher?.exited||!launcher.terminationConfirmed||!Number.isInteger(launcher.pid)||launcher.pid<=0)fail('BROWSER_NORMAL_STOP_UNVERIFIED');
}
/** Relaxation is available only AFTER a verified normal stop; all pre-stop ancestry code is unchanged. */
export async function confirmBrowserAncestryStopped({ancestry,query,signal,report,expected,stopReceipt,proofs,launcher,absence,record=()=>{}}){
 if(!stopReceipt)return strictAncestryStopped({ancestry,query,signal});
 const outcome={status:'UNCONFIRMED',processes:[],checkedAt:report?.checkedAt};
 try{
  requireInspectionCapability(report);
  if(canonical(report.target.directory)!==canonical(expected?.directory)||report.target.port!==expected?.port
   ||!ancestry||proofs?.server?.pid!==ancestry.serverPid||proofs.server.creationTicks!==ancestry.serverCreationTicks
   ||proofs.server.ownerSid!==ancestry.ownerSid||canonical(proofs.server.directory)!==canonical(expected.directory)
   ||canonical(proofs.server.executable)!==canonical(expected.executable)
   ||(ancestry.relayPid!==null&&(proofs?.relay?.pid!==ancestry.relayPid||proofs.relay.creationTicks!==ancestry.relayCreationTicks
     ||proofs.relay.ownerSid!==ancestry.ownerSid||canonical(proofs.relay.directory)!==canonical(expected.directory)
     ||canonical(proofs.relay.executable)!=='c:/windows/system32/cmd.exe'))
   ||proofs.server.parentPid!==(ancestry.relayPid??ancestry.launcherPid)
   ||(ancestry.relayPid!==null&&proofs.relay.parentPid!==ancestry.launcherPid)
   ||launcher?.pid!==ancestry.launcherPid||launcher.ownerSid!==ancestry.ownerSid)fail('BROWSER_POSTSTOP_BINDING');
  requireNormalStop(stopReceipt,expected,report,launcher);
  for(const [kind,original]of [['server',proofs.server],...(ancestry.relayPid===null?[]:[['relay',proofs.relay]])]){
   signal?.throwIfAborted();const proof=await query(original.pid,'ANCESTRY_ABSENCE',signal);signal?.throwIfAborted();
   const relation=classifyStoppedPid(proof,original,stopReceipt.timing.startedAt);
   outcome.processes.push({kind,pid:original.pid,original,proof,relation});
   if(relation==='UNCONFIRMED')fail('BROWSER_STOP_QUERY_UNCONFIRMED');
  }
  // Also require no target PID file, listener, directory process or PostgreSQL descendant.
  const residual=absence?.confirmed!==true||!Array.isArray(absence?.descendants)||absence.descendants.length!==0
   ||report.directory.status!=='PRESENT'||report.directory.directory!==true||report.pidFile.status!=='ABSENT'
   ||report.postgres.targetMatches.length!==0||report.listeners.selected.some(l=>l.port===expected.port);
  const gone=outcome.processes.every(p=>['CONFIRMED_ABSENT','PID_REUSED'].includes(p.relation));
  outcome.status=residual?'RESIDUAL_OR_UNCONFIRMED':gone?'CONFIRMED_STOPPED':'ORIGINAL_PRESENT';
  record(outcome);return gone&&!residual;
 }catch(e){outcome.reasonCode=/^[A-Z_]+$/.test(e.code??'')?e.code:'BROWSER_STOP_QUERY_UNCONFIRMED';record(outcome);throw e;}
}
/** Exact edits to the existing frozen r11-derived browser adapter, shared by preparation and repair verification. */
export function adaptBrowserStopChecks(source){
 source=source.replaceAll('\r\n','\n');
 const edit=(from,to)=>{if(source.split(from).length!==2)fail('BROWSER_ENVIRONMENT_TEMPLATE_MISMATCH');source=source.replace(from,to);};
 edit("import {verifyLauncherAncestry,confirmAncestryStopped} from './platform-process-ancestry.mjs';","import {verifyLauncherAncestry} from './platform-process-ancestry.mjs';\nimport {confirmBrowserAncestryStopped as confirmAncestryStopped} from './platform-browser-runtime-safety.mjs';");
 edit(' const launcherPids=[];', ' let normalStop,stopProofs,relayProof;\n const launcherPids=[];');
 edit("  return queryWindowsProcess((exe,args,options)=>command(exe,args,{...options,log:'process-proof-'+(++queryCount)+'.log'}),{pid,stage,signal});", "  const p=await queryWindowsProcess((exe,args,options)=>command(exe,args,{...options,log:'process-proof-'+(++queryCount)+'.log'}),{pid,stage,signal});\n  if(stage==='ANCESTRY_RELAY')relayProof=p;return p;");
 edit('  evidence.ancestry=ancestry;value.ancestry=ancestry;return value;','  stopProofs={server:p,relay:ancestry.relayPid===null?null:relayProof};\n  evidence.ancestry=ancestry;value.ancestry=ancestry;return value;');
 edit("  const r=await inspect('stop-absence',signal),a=processAbsence(r,{pinned:current,launcherPids});\n  if(current&&(await proof(current.pid,signal)).status!=='ABSENT')return false;\n  if(ancestry&&!await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage),signal}))return false;", "  const r=await inspect('stop-absence',signal),a=processAbsence(r,{pinned:current,launcherPids:[...launcherPids,...(ancestry?.relayPid?[ancestry.relayPid]:[])]});\n  if(!normalStop&&current&&(await proof(current.pid,signal)).status!=='ABSENT')return false;\n  if(ancestry&&!await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage),signal,report:r,expected,stopReceipt:normalStop,proofs:stopProofs,launcher:launchReceipt,absence:a,record:v=>{(evidence.postStopChecks??=[]).push(v);}}))return false;");
 edit("   return checkCommand(await command(PG+'/pg_ctl.exe',['stop','-D',DATA,'-m','fast','-w','-t','30'],{signal,log:'pg-stop.log',timeout:BUDGETS.stopCommandMs,fileOutput:true}),'STOP');", "   normalStop=checkCommand(await command(PG+'/pg_ctl.exe',['stop','-D',DATA,'-m','fast','-w','-t','30'],{signal,log:'pg-stop.log',timeout:BUDGETS.stopCommandMs,fileOutput:true}),'STOP');return normalStop;");
 edit("   const r=await inspect('final-os-check'),absence=processAbsence(r,{pinned,launcherPids});\n   const ancestryAbsent=ancestry?await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage)}):null;\n   return {report:r,absence,ancestryAbsent};", "   const r=await inspect('final-os-check'),absence=processAbsence(r,{pinned,launcherPids:[...launcherPids,...(ancestry?.relayPid?[ancestry.relayPid]:[])]});\n   const ancestryAbsent=ancestry?await confirmAncestryStopped({ancestry,query:(pid,stage,s)=>proof(pid,s,stage),report:r,expected,stopReceipt:normalStop,proofs:stopProofs,launcher:launchReceipt,absence,record:v=>{(evidence.postStopChecks??=[]).push(v);}}):null;\n   return {report:r,absence:{...absence,confirmed:absence.confirmed&&(!ancestry||ancestryAbsent===true)},ancestryAbsent};");
 return source;
}

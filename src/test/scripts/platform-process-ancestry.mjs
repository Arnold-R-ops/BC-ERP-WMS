// Test-only ancestry binding, used by the actual minimal start/stop entry before SQL and before stop.
import path from 'node:path';
import {IdentityError} from './platform-process-lifecycle.mjs';
const canonical=p=>typeof p==='string'?path.win32.normalize(p).replaceAll('\\','/').toLowerCase():'';
function reject(code){const e=new IdentityError(code);e.code=code;throw e;}
function validProof(p,pid,exe,sid){
 if(p?.status!=='AVAILABLE'||p.pid!==pid||canonical(p.executable)!==canonical(exe)||p.ownerSid!==sid
  ||!p.commandLineReadable||!p.commandLineSha256||!/^\d+$/.test(p.creationTicks??'')||!Number.isFinite(Date.parse(p.creationUtc)))reject('ANCESTRY_PROOF_INCOMPLETE');
}
export async function verifyLauncherAncestry({local,server,launcher,launcherExecutable,relayExecutable,directory,ownerSid,query,pinned,signal}){
 signal?.throwIfAborted();
 if(!launcher||!Number.isInteger(launcher.pid)||launcher.pid<=0||!Number.isInteger(launcher.parentPid)||launcher.parentPid<=0||launcher.ownerSid!==ownerSid||canonical(launcher.executable)!==canonical(launcherExecutable)
  ||!Number.isFinite(Date.parse(launcher.startedAt))||!launcher.exited||!launcher.terminationConfirmed||!Number.isFinite(Date.parse(launcher.exitAt)))reject('LAUNCHER_RECEIPT_UNVERIFIED');
 validProof(server,local.pid,local.executable,ownerSid);
 if(canonical(server.directory)!==canonical(directory)||server.parentPid!==local.parentPid||server.creationEpoch!==local.processStartedEpoch)reject('SERVER_ANCESTRY_MISMATCH');
 // An exited launcher's numeric PID must not bind to a new process, even with the same name/path.
 const now=await query(launcher.pid,'ANCESTRY_LAUNCHER',signal);signal?.throwIfAborted();
 if(now?.status!=='ABSENT')reject(now?.status==='AVAILABLE'?'LAUNCHER_PID_REUSED_OR_STALE':'LAUNCHER_QUERY_UNVERIFIED');
 const begin=Date.parse(launcher.startedAt),end=Date.parse(launcher.exitAt),serverTime=Date.parse(server.creationUtc);
 if(end<begin||serverTime<begin-2||serverTime>end+2)reject('ANCESTRY_CREATION_WINDOW_MISMATCH');
 let relay=null;
 if(server.parentPid!==launcher.pid){
  relay=await query(server.parentPid,'ANCESTRY_RELAY',signal);signal?.throwIfAborted();
  validProof(relay,server.parentPid,relayExecutable,ownerSid);
  if(relay.parentPid!==launcher.pid||canonical(relay.directory)!==canonical(directory)
   ||Date.parse(relay.creationUtc)<begin-2||Date.parse(relay.creationUtc)>serverTime+2)reject('RELAY_ANCESTRY_MISMATCH');
 }
 const proof={kind:relay?'PG_CTL_CMD_POSTGRES':'PG_CTL_POSTGRES',launcherPid:launcher.pid,launcherStartedAt:launcher.startedAt,launcherExitAt:launcher.exitAt,
  serverPid:server.pid,serverCreationTicks:server.creationTicks,relayPid:relay?.pid??null,relayCreationTicks:relay?.creationTicks??null,ownerSid};
 if(pinned&&JSON.stringify(pinned)!==JSON.stringify(proof))reject('PINNED_ANCESTRY_CHANGED');
 return proof;
}
/** The identified relay is part of closeout too. A successful stop cannot leave it unverified. */
export async function confirmAncestryStopped({ancestry,query,signal}){
 if(!ancestry)return false;
 for(const [pid,ticks] of [[ancestry.serverPid,ancestry.serverCreationTicks],[ancestry.relayPid,ancestry.relayCreationTicks]]){
  if(pid===null)continue;signal?.throwIfAborted();
  const p=await query(pid,'ANCESTRY_ABSENCE',signal);signal?.throwIfAborted();
  if(p?.status==='ABSENT'&&p.pid===pid)continue;
  if(p?.status!=='AVAILABLE'||p.pid!==pid)reject('ANCESTRY_ABSENCE_UNVERIFIED');
  if(p.creationTicks!==ticks||p.ownerSid!==ancestry.ownerSid)reject('ANCESTRY_ABSENCE_PID_REUSED');
  return false;
 }
 return true;
}

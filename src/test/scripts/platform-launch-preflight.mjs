// Dedicated OS query capability gate. Importing is inert; no database/start/stop implementation here.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {runProcess,queryExecutionDetails} from './platform-process-lifecycle.mjs';
export const POWERSHELL='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const script=fileURLToPath(new URL('./platform-windows-identity.ps1',import.meta.url));
const queryScript=fileURLToPath(new URL('./platform-process-query.ps1',import.meta.url));
const canonical=p=>typeof p==='string'?path.win32.normalize(p).replaceAll('\\','/').toLowerCase():'';
export class EnvironmentInspectionError extends Error {
 constructor(code,stage,evidence){super(code);this.name='EnvironmentInspectionError';this.code=code;this.stage=stage;this.evidence=evidence;this.diagnostics=queryDiagnostics(evidence,stage);if(evidence?.queryExecution)this.queryExecution=queryExecutionDetails(evidence.queryExecution);}
}
// Allowlisted diagnostics only. Never copy arbitrary query strings/command lines or error messages.
export function queryDiagnostics(evidence,stage){
 const rows=[evidence,evidence?.[stage],...(evidence?.postgres?.issues??[]),...(evidence?.listeners?.pidProofs??[])].filter(r=>r&&r.status!=='AVAILABLE'&&r.status!=='ABSENT');
 const safe=s=>typeof s==='string'&&/^[A-Za-z0-9_.-]{1,100}$/.test(s)?s:undefined;
 return rows.filter(r=>r.status).map(r=>({stage:safe(r.stage)??safe(stage),pid:Number.isInteger(r.pid)?r.pid:null,status:safe(r.status),category:safe(r.category)??'UNKNOWN',queryPart:safe(r.queryPart),missingFields:(r.missingFields??[]).map(safe).filter(Boolean),firstQueryStatus:safe(r.firstQueryFailure?.status),diagnosticStatus:safe(r.diagnosticFailure?.status),exception:safe(r.firstQueryFailure?.exception??r.exception),hresult:Number.isInteger(r.firstQueryFailure?.hresult??r.hresult)?r.firstQueryFailure?.hresult??r.hresult:undefined,ownerReturnCode:Number.isInteger(r.firstQueryFailure?.ownerReturnCode)?r.firstQueryFailure.ownerReturnCode:undefined}));
}
const fail=(code,stage,evidence)=>{throw new EnvironmentInspectionError(code,stage,evidence?.execution?.queryExecution?{...evidence,queryExecution:evidence.execution.queryExecution}:evidence);};
export function contextBinding(r){return Object.fromEntries(['user','sid','restrictedToken','administratorEnabled','elevated','elevationType','powerShellPath'].map(k=>[k,r.context[k]]));}
export async function inspectWindowsEnvironment(command,{directory,port,signal,ownerPid=process.pid,ownerExecutable=process.execPath}={}){
 if(!path.win32.isAbsolute(directory??'')||/[\r\n]/.test(directory)||!Number.isInteger(port)||port<1||port>65535||!Number.isInteger(ownerPid)||ownerPid<=0)fail('INVALID_INSPECTION_TARGET','QUERY_ARGUMENTS');
 // Match the existing batch's inline -Command query chain. No ExecutionPolicy / token / global setting changes.
 const quote=s=>"'"+s.replaceAll("'","''")+"'";
 const payload=fs.readFileSync(script,'utf8').replace('# PROCESS_QUERY_HELPERS',fs.readFileSync(queryScript,'utf8'));
 const args=['-NoProfile','-NonInteractive','-Command',`& {\n${payload}\n} -Directory ${quote(directory)} -Port ${port} -OwnerPid ${ownerPid} -OwnerExecutable ${quote(ownerExecutable)}`];
 const r=await command(POWERSHELL,args,{timeout:15000,timeoutMs:15000,signal});
 const execution={queryExecution:queryExecutionDetails({...r,budgetMs:15000}),exited:r.exited,exitCode:r.exitCode,timedOut:r.timedOut??r.timeout,aborted:r.aborted,spawnError:r.spawnError,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,
  pid:r.receipt?.pid??null,status:r.exited&&r.exitCode===0&&!r.timedOut&&!r.aborted&&r.outputComplete&&r.terminationConfirmed?'AVAILABLE':'QUERY_FAILED',stage:'QUERY_EXECUTOR',category:r.timedOut||r.timeout?'EXECUTOR_TIMEOUT':r.aborted?'EXECUTOR_ABORTED':'EXECUTOR_RESULT',missingFields:r.outputComplete?[]:['CompleteQueryResult']};
 if(!r.exited||r.exitCode!==0||r.timedOut||r.timeout||r.aborted||r.spawnError||!r.outputComplete||!r.terminationConfirmed)fail('IDENTITY_QUERY_EXECUTOR_FAILED','QUERY_EXECUTOR',execution);
 let report;try{report=JSON.parse(r.output.trim().replace(/^\uFEFF/,''));}catch{fail('IDENTITY_QUERY_INVALID_REPORT','QUERY_PARSE',execution);}
 if(!report||typeof report!=='object'||Array.isArray(report))fail('IDENTITY_QUERY_INVALID_REPORT','QUERY_PARSE',execution);
 return {...report,execution,invocation:{executable:POWERSHELL,args},expected:{directory,port,ownerPid,ownerExecutable}};
}
export async function queryWindowsProcess(command,{pid,stage='PROCESS',signal}={}){
 if(!Number.isInteger(pid)||pid<=0||!/^[A-Z_]+$/.test(stage))fail('INVALID_PROCESS_QUERY','QUERY_ARGUMENTS');
 signal?.throwIfAborted();
 const payload=fs.readFileSync(queryScript,'utf8')+`\nProcessProof ${pid} '${stage}' | ConvertTo-Json -Depth 8 -Compress`;
 const r=await command(POWERSHELL,['-NoProfile','-NonInteractive','-Command',payload],{signal,timeoutMs:5000});
 const queryExecution=queryExecutionDetails({...r,budgetMs:5000});
 if(!r.exited||r.exitCode!==0||r.timedOut||r.aborted||r.spawnError||!r.outputComplete||!r.terminationConfirmed)fail('PROCESS_QUERY_EXECUTOR_FAILED',stage,{queryExecution,status:'QUERY_FAILED',pid,stage,category:'EXECUTOR_FAILED',missingFields:['CompleteQueryResult']});
 let proof;try{proof=JSON.parse(r.output.trim().replace(/^\uFEFF/,''));}catch{fail('PROCESS_QUERY_INVALID_REPORT',stage,{queryExecution,status:'INCOMPLETE',pid,stage,missingFields:['StructuredResult']});}
 if(!proof||typeof proof!=='object'||Array.isArray(proof))fail('PROCESS_QUERY_INVALID_REPORT',stage,{queryExecution,status:'INCOMPLETE',pid,stage,missingFields:['StructuredResult']});
 if(proof.pid!==pid||proof.stage!==stage||!['AVAILABLE','ABSENT'].includes(proof.status))fail('PROCESS_QUERY_'+(proof.status??'INCOMPLETE'),stage,{...proof,queryExecution});
 return proof;
}
export function requireInspectionCapability(report,{context}={}){
 const r=report,e=r?.expected;
 if(!e||r.schema!==1||canonical(r.target?.directory)!==canonical(e.directory)||r.target?.port!==e.port)fail('IDENTITY_REPORT_BINDING_MISMATCH','REPORT_BINDING',r);
 for(const key of ['context','self','owner','postgres','listeners']){
  if(r[key]?.status!=='AVAILABLE')fail('IDENTITY_QUERY_'+(r[key]?.status??'INCOMPLETE'),key,r);
 }
 for(const key of ['directory','pidFile'])if(!['ABSENT','PRESENT'].includes(r[key]?.status))fail('IDENTITY_QUERY_'+(r[key]?.status??'INCOMPLETE'),key,r);
 if(!r.self.commandLineReadable||!r.owner.commandLineReadable||!r.self.commandLineSha256||!r.owner.commandLineSha256
  ||r.self.parentPid!==e.ownerPid||r.owner.pid!==e.ownerPid||canonical(r.owner.executable)!==canonical(e.ownerExecutable)
  ||r.context.sid!==r.owner.ownerSid||r.context.sid!==r.self.ownerSid||canonical(r.self.executable)!==canonical(POWERSHELL)
  ||!Number.isInteger(r.self.pid)||r.self.pid!==r.context.powerShellPid||!r.context.user||!r.context.sid
  ||['restrictedToken','administratorEnabled','elevated'].some(k=>typeof r.context[k]!=='boolean')||![1,2,3].includes(r.context.elevationType))fail('IDENTITY_CONTEXT_INCOMPLETE','EXECUTION_CONTEXT',r);
 if(context&&JSON.stringify(contextBinding(r))!==JSON.stringify(context))fail('IDENTITY_CONTEXT_CHANGED','EXECUTION_CONTEXT',r);
 if(!Number.isInteger(r.postgres.count)||r.postgres.count<0||r.postgres.incomplete!==0||!Array.isArray(r.postgres.targetMatches)
  ||!Array.isArray(r.postgres.processIds)||!Array.isArray(r.postgres.parentIds)||r.postgres.processIds.length!==r.postgres.count||r.postgres.parentIds.length!==r.postgres.count
  ||!Array.isArray(r.listeners.selected)||!Array.isArray(r.listeners.pidProofs)||r.listeners.pidProofs.length!==r.listeners.selected.length||r.listeners.malformed!==0
  ||r.listeners.selected.some(l=>!Number.isInteger(l.pid)||l.pid<=0||!Number.isInteger(l.port))
  ||r.listeners.pidProofs.some((p,i)=>p.status!=='AVAILABLE'||p.pid!==r.listeners.selected[i].pid))fail('IDENTITY_INVENTORY_INCOMPLETE','INVENTORY',r);
 return r;
}
export function confirmTargetAbsent(report,pinned){
 const r=requireInspectionCapability(report);
 if(pinned&&(!Number.isInteger(pinned.pid)||pinned.pid<=0))fail('INVALID_PINNED_PID','ABSENCE',r);
 const absent=r.pidFile.status==='ABSENT'&&r.postgres.targetMatches.length===0&&!r.listeners.selected.some(l=>l.port===r.expected.port)
  &&(!pinned||!r.postgres.processIds.includes(pinned.pid)&&!r.postgres.parentIds.includes(pinned.pid));
 return {status:absent?'CONFIRMED_ABSENT':'PRESENT',checkedAt:r.checkedAt};
}
export async function initializationPreflight({inspect,record=()=>{},initialize}){
 let report;
 try{
  report=await inspect();requireInspectionCapability(report);
  if(report.directory.status!=='ABSENT')fail('INITIALIZATION_DIRECTORY_EXISTS','INITIALIZATION_PREFLIGHT',report);
  if(confirmTargetAbsent(report).status!=='CONFIRMED_ABSENT')fail('INITIALIZATION_TARGET_OCCUPIED','INITIALIZATION_PREFLIGHT',report);
 }catch(error){await record({status:'BLOCKED',code:error.code??'IDENTITY_QUERY_FAILED',stage:error.stage??'QUERY',evidence:error.evidence??report});throw error;}
 await record({status:'PASSED',context:contextBinding(report),evidence:report});
 return initialize(report);
}
export function readPidFile(file,read=fs.readFileSync){
 try{return read(file,'utf8');}catch(error){if(error.code==='ENOENT'||error.code==='ENOTDIR')return null;fail('PID_FILE_'+(error.code==='EACCES'||error.code==='EPERM'?'ACCESS_DENIED':'READ_FAILED'),'PID_FILE');}
}
// Manual OS-only diagnostic; fixed paths and new evidence file. NOT a database authorization switch.
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const [mode,label]=process.argv.slice(2);
 if(mode!=='inspect'||!['sandbox','sandbox-inline','outside-readonly','sandbox-sealed','outside-readonly-sealed'].includes(label)||process.argv.length!==4)throw Error('Only fixed read-only inspection labels');
 const root='D:/ERP_WMS/BC warehouse/2G';
 const output=root+'/docs/releases/platform-launch-preflight-20260914/a1/'+label+'.json';
 if(fs.existsSync(output))throw Error('Never overwrite inspection evidence');
 const target={directory:root+'/target/platform-start-stop-validation-20260914/s1/pgdata',port:55449};
 const command=(exe,args,options)=>runProcess(exe,args,{...options,cwd:root});
 const inputFiles=[fileURLToPath(import.meta.url),script,fileURLToPath(new URL('./platform-process-lifecycle.mjs',import.meta.url))].map(p=>({path:p,sha256:crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex')}));
 const result={label,checkedAt:new Date().toISOString(),databaseCommands:0,databaseConnections:0,inputFiles,nodeVersion:process.version,nodeExecutable:process.execPath};
 try{result.report=await inspectWindowsEnvironment(command,target);requireInspectionCapability(result.report);result.absence=confirmTargetAbsent(result.report);result.status='AVAILABLE';}
 catch(error){result.status='BLOCKED';result.code=error.code??error.name;result.stage=error.stage;result.report??=error.evidence;process.exitCode=2;}
 fs.writeFileSync(output,JSON.stringify(result,null,2)+'\n',{flag:'wx'});
 console.log(JSON.stringify({status:result.status,code:result.code,context:result.report?.context,self:result.report?.self?.status,owner:result.report?.owner?.status,postgres:result.report?.postgres?.status,listeners:result.report?.listeners?.status,absence:result.absence,evidence:output}));
}

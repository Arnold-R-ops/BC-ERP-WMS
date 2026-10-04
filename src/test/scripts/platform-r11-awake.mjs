// Single authorized r11 wrapper. Importing this module never requests power state or starts a process.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
export const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const SOURCE=ROOT+'/target/platform-db-acceptance-20260919/r11/snapshot/source';
export const DELIVERY=ROOT+'/docs/releases/platform-db-acceptance-20260919/r11';
export const AUTH='--authorized-20260919-r11-arnold-elevated-platform-backend-only';
export const AWAKE_BUDGETS=Object.freeze({observationMs:18000000,executorMs:18060000,killWaitMs:1000});
export const AWAKE_TEST_COUNT=26;
const PS='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const script=SOURCE+'/src/test/scripts/platform-r11-awake.ps1';
const journal=DELIVERY+'/awake-journal.jsonl';
const resultPath=DELIVERY+'/awake-result.json';
const stdout=DELIVERY+'/awake-process.log';
const normalized=p=>path.win32.normalize(p).toLowerCase();
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
export function awakeInvocation(){return {executable:PS,args:['-NoProfile','-NonInteractive','-File',script,'-Authorization',AUTH],options:{cwd:ROOT,timeoutMs:AWAKE_BUDGETS.executorMs,killWaitMs:AWAKE_BUDGETS.killWaitMs,outputFile:stdout}};}
export function parseAwakeJournal(text){
 const lines=text.trim().split(/\r?\n/).filter(Boolean);if(!lines.length)throw Error('AWAKE_JOURNAL_EMPTY');
 const rows=lines.map(line=>JSON.parse(line));
 const allowed=new Set(['WRAPPER_STARTED','CONTEXT_CHECKED','REQUEST_ACQUIRED','CHILD_STARTED','CHILD_EXITED','OBSERVATION_TIMEOUT','WRAPPER_ERROR','REQUEST_RELEASED','WRAPPER_FINISHED']);
 for(const r of rows)if(!allowed.has(r.event)||!/^[a-f0-9]{64}$/.test(r.inputId??'')||!Number.isInteger(r.wrapperPid)||r.wrapperPid<=0||!Number.isInteger(r.osThreadId)||r.osThreadId<=0||!Number.isFinite(r.elapsedMs)||r.elapsedMs<0||!Number.isFinite(Date.parse(r.at)))throw Error('AWAKE_JOURNAL_INVALID');
 if(new Set(rows.map(r=>r.wrapperPid)).size!==1||new Set(rows.map(r=>r.osThreadId)).size!==1||new Set(rows.map(r=>r.inputId)).size!==1)throw Error('AWAKE_JOURNAL_IDENTITY_CHANGED');
 if(rows.some((r,i)=>i&&r.elapsedMs<rows[i-1].elapsedMs))throw Error('AWAKE_JOURNAL_TIME_REVERSED');
 for(const event of allowed)if(event!=='WRAPPER_ERROR'&&rows.filter(r=>r.event===event).length>1)throw Error('AWAKE_JOURNAL_DUPLICATE');
 return rows;
}
export function assertAwakeBinding(inputId,rows,{parentPid=process.ppid,env=process.env}={}){
 const acquired=rows.find(r=>r.event==='REQUEST_ACQUIRED'),context=rows.find(r=>r.event==='CONTEXT_CHECKED');
 const started=rows.findIndex(r=>r.event==='WRAPPER_STARTED'),checked=rows.findIndex(r=>r.event==='CONTEXT_CHECKED'),acquiredAt=rows.findIndex(r=>r.event==='REQUEST_ACQUIRED'),childAt=rows.findIndex(r=>r.event==='CHILD_STARTED');
 if(!/^[a-f0-9]{64}$/.test(inputId??'')||env.P3_R11_AWAKE_INPUT_ID!==inputId||rows.some(r=>r.inputId!==inputId)
  ||!Number.isInteger(parentPid)||parentPid<=0||Number(env.P3_R11_AWAKE_WRAPPER_PID)!==parentPid
  ||rows.some(r=>r.wrapperPid!==parentPid)||!acquired||acquired.confirmed!==true||acquired.result<=0||acquired.flags!==2147483651
  ||Number(env.P3_R11_AWAKE_THREAD_ID)!==acquired.osThreadId||!context||context.elevated!==true||context.administratorEnabled!==true||context.restrictedToken!==false
  ||context.user?.toLowerCase()!=='arnold\\arnold'||context.sid!=='S-1-5-21-3770167052-703495069-1631994977-1001'
  ||started!==0||checked<=started||acquiredAt<=checked||(childAt!==-1&&childAt<=acquiredAt)
  ||rows.some(r=>['WRAPPER_ERROR','OBSERVATION_TIMEOUT','REQUEST_RELEASED','WRAPPER_FINISHED','CHILD_EXITED'].includes(r.event)))throw Error('AWAKE_ACTIVE_BINDING_REQUIRED');
 return {inputId,wrapperPid:parentPid,osThreadId:acquired.osThreadId,requestAcquired:true};
}
export function requireAwakeBinding(inputId){return assertAwakeBinding(inputId,parseAwakeJournal(fs.readFileSync(journal,'utf8')));}
export function assessAwake(rows,execution){
 const find=e=>rows.find(r=>r.event===e),issues=[];
 const start=find('WRAPPER_STARTED'),context=find('CONTEXT_CHECKED'),acquire=find('REQUEST_ACQUIRED'),child=find('CHILD_STARTED'),exit=find('CHILD_EXITED'),release=find('REQUEST_RELEASED'),finish=find('WRAPPER_FINISHED');
 const index=e=>rows.findIndex(r=>r.event===e);
 if(!start||start.observationBudgetMs!==AWAKE_BUDGETS.observationMs)issues.push('AWAKE_BUDGET_UNVERIFIED');
 if(!context||context.user?.toLowerCase()!=='arnold\\arnold'||context.sid!=='S-1-5-21-3770167052-703495069-1631994977-1001'||context.administratorEnabled!==true||context.elevated!==true||context.restrictedToken!==false||![1,2,3].includes(context.elevationType))issues.push('EXECUTION_CONTEXT_UNVERIFIED');
 const acquired=acquire?.confirmed===true&&Number.isInteger(acquire.result)&&acquire.result>0&&acquire.flags===2147483651;
 if(!acquired)issues.push('AWAKE_ACQUIRE_UNCONFIRMED');
 const childExited=child&&Number.isInteger(child.childPid)&&child.childPid>0&&exit?.childPid===child.childPid&&exit.confirmed===true&&Number.isInteger(exit.exitCode);
 if(!childExited)issues.push('BATCH_CHILD_EXIT_UNCONFIRMED');else if(exit.exitCode!==0)issues.push('BATCH_CHILD_FAILED');
 const released=acquired&&release?.confirmed===true&&release.sameThread===true&&release.osThreadId===acquire.osThreadId&&Number.isInteger(release.result)&&release.result>0&&release.flags===2147483648;
 if(!released)issues.push('AWAKE_RELEASE_UNCONFIRMED');
 if(!finish||finish.acquired!==true||finish.releaseConfirmed!==true||finish.exitCode!==0)issues.push('AWAKE_WRAPPER_FINISH_FAILED');
 if(['WRAPPER_STARTED','CONTEXT_CHECKED','REQUEST_ACQUIRED','CHILD_STARTED','CHILD_EXITED','REQUEST_RELEASED','WRAPPER_FINISHED'].some((e,i,a)=>index(e)<0||(i&&index(e)<=index(a[i-1]))))issues.push('AWAKE_EVENT_ORDER_INVALID');
 if(rows.some(r=>r.event==='WRAPPER_ERROR'||r.event==='OBSERVATION_TIMEOUT'))issues.push('AWAKE_WRAPPER_RECORDED_FAILURE');
 const wrapperExited=execution?.exited===true&&execution.terminationConfirmed===true&&execution.receipt?.exited===true&&execution.receipt.terminationConfirmed===true&&execution.receipt.pid===start?.wrapperPid;
 if(!wrapperExited)issues.push('AWAKE_WRAPPER_EXIT_UNCONFIRMED');
 if(execution?.exitCode!==0||execution?.timedOut||execution?.aborted||execution?.spawnError||execution?.outputComplete!==true)issues.push('AWAKE_EXECUTOR_FAILED');
 return {status:issues.length?'FAILED':'PASSED',issues,acquired,releaseConfirmed:released,wrapperExitConfirmed:wrapperExited,childExitConfirmed:!!childExited,
 firstChildFailure:childExited&&exit.exitCode!==0?{exitCode:exit.exitCode}:null,releaseErrors:released?[]:['AWAKE_RELEASE_UNCONFIRMED'],databaseClosedByWrapper:false};
}
function verifySealed(){
 if(normalized(fileURLToPath(import.meta.url))!==normalized(SOURCE+'/src/test/scripts/platform-r11-awake.mjs'))throw Error('AWAKE_UNSEALED_RUNNER');
 if(normalized(process.execPath)!==normalized('D:/JAVA/NODE/node.exe')||process.version!=='v24.12.0')throw Error('AWAKE_FIXED_NODE_REQUIRED');
 const input=JSON.parse(fs.readFileSync(DELIVERY+'/backend-input-manifest.json','utf8'));
 const source=input.groups.find(g=>g.name==='backend-source');
 for(const name of ['platform-r11-awake.mjs','platform-r11-awake.ps1','platform-r11-database-batch.mjs']){
  const p='src/test/scripts/'+name,entry=source?.files.find(f=>f.path===p);
  if(!entry||hash(fs.readFileSync(SOURCE+'/'+p))!==entry.sha256)throw Error('AWAKE_FROZEN_INPUT_MISMATCH');
 }
 return input.id;
}
export async function executeAwake(){
 const inputId=verifySealed();
 for(const p of [journal,resultPath,stdout])if(fs.existsSync(p))throw Error('AWAKE_EVIDENCE_ALREADY_EXISTS');
 const invoke=awakeInvocation(),startedAt=new Date().toISOString();
 const executed=await runProcess(invoke.executable,invoke.args,{...invoke.options,env:{...process.env,P3_R11_AWAKE_INPUT_ID:inputId}});
 let rows=[],journalFailure=null;
 try{rows=parseAwakeJournal(fs.readFileSync(journal,'utf8'));}catch(error){journalFailure={name:error.name,code:'AWAKE_JOURNAL_UNVERIFIED'};}
 const assessment=assessAwake(rows,executed);
 if(journalFailure){assessment.status='FAILED';assessment.issues.push('AWAKE_JOURNAL_UNVERIFIED');}
 if(rows.some(r=>r.inputId!==inputId)){assessment.status='FAILED';assessment.issues.push('AWAKE_INPUT_BINDING_MISMATCH');}
 const result={inputId,startedAt,completedAt:new Date().toISOString(),budgets:AWAKE_BUDGETS,...assessment,journalFailure,invocation:invoke,
 execution:{exitCode:executed.exitCode,exited:executed.exited,timedOut:executed.timedOut,aborted:executed.aborted,spawnError:executed.spawnError,outputComplete:executed.outputComplete,terminationConfirmed:executed.terminationConfirmed,receipt:executed.receipt,timing:executed.timing},
 journalSha256:fs.existsSync(journal)?hash(fs.readFileSync(journal)):null,stdoutSha256:fs.existsSync(stdout)?hash(fs.readFileSync(stdout)):null,
 policyChanged:false,requestScope:'single fixed wrapper OS thread; this r11 batch only',hardWallClockGuarantee:false};
 fs.writeFileSync(resultPath,JSON.stringify(result,null,2)+'\n',{flag:'wx'});return result;
}
if(process.argv[1]&&normalized(process.argv[1])===normalized(fileURLToPath(import.meta.url))){
 try{if(process.argv.length!==4||process.argv[2]!=='run'||process.argv[3]!==AUTH)throw Error('Exact r11 authorization required');
  const result=await executeAwake();console.log(JSON.stringify({status:result.status,releaseConfirmed:result.releaseConfirmed,wrapperExitConfirmed:result.wrapperExitConfirmed,issues:result.issues}));if(result.status!=='PASSED')process.exitCode=1;
 }catch(error){console.error(JSON.stringify({status:'FAILED',type:error.name,code:'AWAKE_WRAPPER_ENTRY_FAILED'}));process.exitCode=1;}
}

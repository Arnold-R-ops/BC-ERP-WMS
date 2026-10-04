// Test infrastructure only. Importing this module never starts a process or contacts a database.
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

/** Observe exit separately from pipe closure. Timers require a scheduled event loop; host suspension is not a hard wall-clock guarantee. */
export function runProcess(executable,args,{cwd,env,input='',timeoutMs=180000,killWaitMs=1000,drainMs=100,
  outputFile,signal,spawnProcess=spawn}={}) {
  return new Promise(resolve=>{
    const started=Date.now(),mono=performance.now();let child,fd,finished=false,exitTimer,killTimer,drainTimer,terminationRequested=false;
    const timing={startedAt:new Date(started).toISOString(),timeoutMs,killWaitMs,events:[]};
    const stamp=(event,details={})=>{const row={event,at:new Date().toISOString(),wallElapsedMs:Date.now()-started,monotonicElapsedMs:performance.now()-mono,...details};timing.events.push(row);return row;};
    const receipt={pid:null,parentPid:process.pid,executable,startedAt:timing.startedAt,exitAt:null,exited:false,terminationConfirmed:false};stamp('EXECUTOR_STARTED');
    const state={exited:false,exitCode:null,signal:null,timedOut:false,aborted:false,spawnError:null,
      pipesClosed:false,outputComplete:false,terminationConfirmed:false,output:''};
    const release=()=>{child?.stdout?.destroy();child?.stderr?.destroy();child?.stdin?.destroy();};
    const finish=()=>{
      if(finished)return;finished=true;
      clearTimeout(exitTimer);clearTimeout(killTimer);clearTimeout(drainTimer);signal?.removeEventListener('abort',abort);
      release();if(fd!==undefined)fs.closeSync(fd);
      const ended=stamp('EXECUTOR_SETTLED');timing.completedAt=ended.at;timing.wallElapsedMs=ended.wallElapsedMs;timing.monotonicElapsedMs=ended.monotonicElapsedMs;
      resolve({...state,elapsedMs:Date.now()-started,timing,receipt});
    };
    const terminate=()=>{
      if(state.exited){finish();return;}
      if(terminationRequested)return;terminationRequested=true;
      stamp('TERMINATION_REQUESTED',{pid:child?.pid??null});
      try{stamp('TERMINATION_REQUEST_RESULT',{accepted:child?.kill()===true});}catch(error){stamp('TERMINATION_REQUEST_FAILED',{code:error.code??error.name});}
      killTimer=setTimeout(()=>{stamp('TERMINATION_CONFIRMATION_DEADLINE',{confirmed:state.terminationConfirmed});finish();},killWaitMs);
    };
    const abort=()=>{state.aborted=true;stamp('ABORT_OBSERVED');terminate();};
    if(signal?.aborted){state.aborted=true;finish();return;}
    try{
      if(outputFile)fd=fs.openSync(outputFile,'wx');
      child=spawnProcess(executable,args,{cwd,env,windowsHide:true,stdio:outputFile?['pipe',fd,fd]:['pipe','pipe','pipe']});
      receipt.pid=child.pid??null;stamp('CHILD_HANDLE_CREATED',{pid:receipt.pid});
    }catch(error){state.spawnError=error.code??error.name;finish();return;}
    exitTimer=setTimeout(()=>{state.timedOut=true;stamp('TIMEOUT_FIRED',{latenessMs:Math.max(0,performance.now()-mono-timeoutMs)});terminate();},Math.max(0,timeoutMs-(performance.now()-mono)));
    stamp('TIMEOUT_ARMED',{budgetMs:timeoutMs});
    signal?.addEventListener('abort',abort,{once:true});
    child.stdout?.on('data',b=>{state.output+=b;});child.stderr?.on('data',b=>{state.output+=b;});
    child.on('error',error=>{state.spawnError=error.code??error.name;finish();});
    child.on('exit',(code,exitSignal)=>{
      state.exited=true;state.terminationConfirmed=true;state.exitCode=code;state.signal=exitSignal;
      const exit=stamp('TERMINATION_CONFIRMED',{pid:receipt.pid,exitCode:code,signal:exitSignal});Object.assign(receipt,{exitAt:exit.at,exited:true,terminationConfirmed:true});
      clearTimeout(exitTimer);clearTimeout(killTimer);
      // Server launch output uses independent files, not inherited Node pipes.
      if(outputFile){state.outputComplete=true;finish();}
      else if(state.pipesClosed){state.outputComplete=true;finish();}
      else drainTimer=setTimeout(finish,drainMs);
    });
    child.on('close',()=>{state.pipesClosed=true;if(!finished)stamp('PIPES_CLOSED');if(state.exited){state.outputComplete=true;finish();}});
    child.stdin?.on('error',()=>{});child.stdin?.end(input);
  });
}

export class IdentityError extends Error { constructor(message){super(message);this.name='IdentityError';} }
const canonical=p=>typeof p==='string'?path.win32.normalize(p).replaceAll('\\','/').toLowerCase():'';
export function assertOwned(expected,observed,pinned) {
  if(!observed)throw new IdentityError('No owned process evidence');
  const valid=Number.isInteger(observed.pid)&&observed.pid>0&&observed.processPid===observed.pid
    && canonical(observed.directory)===canonical(expected.directory)
    && canonical(observed.commandDirectory)===canonical(expected.directory)
    && canonical(observed.executable)===canonical(expected.executable)
    && observed.port===expected.port&&observed.address===expected.address
    && observed.clusterId===expected.clusterId
    && Number.isInteger(observed.startedEpoch)&&Number.isFinite(observed.processStartedEpoch)
    && Math.abs(observed.startedEpoch-observed.processStartedEpoch)<=2
    && observed.startedEpoch>=expected.earliestStartEpoch
    && observed.startedEpoch<=expected.latestStartEpoch
    && (observed.listeners??[]).every(l=>l.pid===observed.pid&&l.port===expected.port&&l.address===expected.address);
  if(!valid)throw new IdentityError('PID/directory/executable/start/cluster/listener identity rejected');
  if(pinned&&(observed.pid!==pinned.pid||observed.startedEpoch!==pinned.startedEpoch
    ||observed.processStartedEpoch!==pinned.processStartedEpoch))throw new IdentityError('Process identity changed');
  return observed;
}
export function assertReady(expected,local,ready) {
  if(!ready||ready.database!==expected.database||ready.user!==expected.user||ready.port!==expected.port
    ||ready.address!==expected.address||ready.listenAddresses!==expected.address
    ||canonical(ready.directory)!==canonical(expected.directory)||ready.clusterId!==expected.clusterId
    ||local.listeners.length!==1)throw new IdentityError('Live database readiness/identity rejected');
}
async function bounded(label,operation,ms,traces) {
  const abort=new AbortController();let timer;const started=Date.now(),mono=performance.now(),deadline=started+ms;
  const trace={stage:label,budgetMs:ms,startedAt:new Date(started).toISOString(),events:[]};traces?.push(trace);
  const event=name=>trace.events.push({event:name,at:new Date().toISOString(),wallElapsedMs:Date.now()-started,monotonicElapsedMs:performance.now()-mono});
  const limit=new Promise((_,reject)=>{timer=setTimeout(()=>{event('TIMEOUT_FIRED');abort.abort();event('ABORT_REQUESTED');const error=new Error(label+' timeout');error.name='DeadlineError';reject(error);},ms);});
  try{
    const result=await Promise.race([Promise.resolve().then(()=>operation(abort.signal)),limit]);
    if(Date.now()>deadline||performance.now()-mono>ms){event('LATE_COMPLETION_REJECTED');const error=new Error(label+' timeout');error.name='DeadlineError';throw error;}
    return result;
  }
  finally{clearTimeout(timer);abort.abort();event('BUDGET_SETTLED');trace.completedAt=new Date().toISOString();}
}
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const errorName=error=>error?.name??'Error';
// Only allowlisted type/code metadata: never include arbitrary exception messages/authentication data.
const safeWord=value=>typeof value==='string'&&/^[A-Za-z0-9_.-]{1,100}$/.test(value)?value:undefined;
const iso=value=>typeof value==='string'&&/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3,7}Z$/.test(value)?value:undefined;
function project(row,numbers=[],booleans=[],words=[],dates=[]){
 const out={};for(const k of numbers)if(Number.isFinite(row?.[k]))out[k]=row[k];
 for(const k of booleans)if(typeof row?.[k]==='boolean')out[k]=row[k];
 for(const k of words)if(safeWord(row?.[k]))out[k]=safeWord(row[k]);
 for(const k of dates)if(iso(row?.[k]))out[k]=iso(row[k]);return out;
}
export function queryExecutionDetails(row){
 if(!row||typeof row!=='object')return undefined;
 const out=project(row,['commandIndex','budgetMs','elapsedMs','exitCode'],['exited','timedOut','aborted','outputComplete','terminationConfirmed']);
 if(!Number.isInteger(out.commandIndex)||out.commandIndex<0)delete out.commandIndex;
 if(row.receipt)out.receipt=project(row.receipt,['pid','parentPid'],['exited','terminationConfirmed'],[],['startedAt','exitAt']);
 if(row.timing){
  out.timing=project(row.timing,['timeoutMs','killWaitMs','wallElapsedMs','monotonicElapsedMs'],[],[],['startedAt','completedAt']);
  out.timing.events=(Array.isArray(row.timing.events)?row.timing.events:[]).slice(0,64).map(e=>project(e,
   ['wallElapsedMs','monotonicElapsedMs','latenessMs','pid','exitCode','budgetMs'],['accepted','confirmed'],['event','signal','code'],['at']));
 }
 return out;
}
export function failureDetails(error,stage){
 const out={stage:safeWord(stage)??'UNKNOWN',name:safeWord(error?.name)??'Error'};
 if(typeof error?.code==='string'&&/^[A-Z0-9_.-]{1,80}$/.test(error.code))out.code=error.code;
 if(safeWord(error?.stage))out.queryStage=safeWord(error.stage);
 if(safeWord(error?.failureStep))out.failureStep=safeWord(error.failureStep);
 if(error?.queryExecution)out.queryExecution=queryExecutionDetails(error.queryExecution);
 if(Array.isArray(error?.diagnostics))out.diagnostics=error.diagnostics.slice(0,64).map(d=>({
  ...project(d,['pid','hresult','ownerReturnCode'],[],['stage','status','category','queryPart','firstQueryStatus','diagnosticStatus','exception']),
  ...(Array.isArray(d?.missingFields)?{missingFields:d.missingFields.slice(0,32).map(safeWord).filter(Boolean)}:{})}));
 return out;
}
// Annotates the actual failing operation without replacing its type/code or earlier query step.
export async function withFailureStep(step,operation){
 if(!safeWord(step))throw Error('Invalid failure step');
 try{return await operation();}catch(thrown){
  const error=thrown instanceof Error?thrown:Object.assign(new Error('Non-error throw; raw value withheld'),{name:'NonErrorThrown'});
  error.failureStep??=step;throw error;
 }
}

/** Cleanup has its own budget; it never uses a timed-out launch promise or a port-only kill. */
export async function stopOwnedDatabase(adapter,expected,pinned,{cleanupMs=40000,pollMs=100}={}) {
  const result={attempted:false,completed:false,status:'PENDING',budgets:[]};
  try{
    await bounded('cleanup',async signal=>{
      const local=await adapter.inspectLocal(signal);signal.throwIfAborted();
      if(!local){
        if(!await adapter.isStopped(signal,pinned))throw new IdentityError('No PID proof, but resource may remain');
        result.status='ALREADY_STOPPED';result.completed=true;return;
      }
      assertOwned(expected,local,pinned);result.identity=local;
      // Recheck immediately before a state-changing stop, not only before a long probe.
      const current=await adapter.inspectLocal(signal);signal.throwIfAborted();assertOwned(expected,current,local);
      result.attempted=true;
      const stopped=await adapter.stop(current,signal);signal.throwIfAborted();
      result.launcher=stopped;
      if(stopped.exitCode!==0||!stopped.exited||stopped.timedOut||stopped.aborted||stopped.spawnError)throw new Error('Stop command failed');
      while(!await adapter.isStopped(signal,current)){signal.throwIfAborted();await pause(pollMs);}
      signal.throwIfAborted();result.completed=true;result.status='STOPPED';
    },cleanupMs,result.budgets);
  }catch(error){result.status=error instanceof IdentityError?'REFUSED_IDENTITY':error.name==='DeadlineError'?'CLEANUP_TIMEOUT':'CLEANUP_FAILED';result.error=errorName(error);result.failure=failureDetails(error,'CLEANUP');}
  return result;
}

/** Requires launcher completion AND live SQL/physical identity, not a successful exit or listening port alone. */
export async function startOwnedDatabase(adapter,expected,{launcherMs=45000,readinessMs=10000,cleanupMs=40000,pollMs=100}={}) {
  const result={status:'STARTING',databaseState:'UNVERIFIED',executorState:'PENDING',identity:null,launcher:null,budgets:[]};
  try{result.launcher=await bounded('launcher',signal=>adapter.launch(signal),launcherMs,result.budgets);}
  catch(error){result.launcherError=errorName(error);result.firstFailure=failureDetails(error,'LAUNCHER');}
  const launcher=result.launcher;
  result.executorState=launcher?.exited&&launcher.exitCode===0&&!launcher.timedOut&&!launcher.aborted&&!launcher.spawnError?'SUCCEEDED':'FAILED';
  if(result.executorState==='FAILED')result.firstFailure??={stage:'LAUNCHER',name:'ProcessExecutionError',exitCode:launcher?.exitCode??null,
    timedOut:launcher?.timedOut??false,aborted:launcher?.aborted??false,spawnError:launcher?.spawnError??null,
    windowsErrorCodes:[...new Set([...String(launcher?.output??'').matchAll(/^pg_ctl:.*error code (\d+)\s*$/gm)].map(m=>Number(m[1]))) ]};
  let observed=false;
  try{
    await bounded('readiness',async signal=>{
      while(true){
        signal.throwIfAborted();const local=await adapter.inspectLocal(signal);signal.throwIfAborted();
        if(local){
          observed=true;assertOwned(expected,local,result.identity);result.identity=local;
          const ready=await adapter.probeReady(local,signal);signal.throwIfAborted();
          if(ready){
            assertReady(expected,local,ready);
            const after=await adapter.inspectLocal(signal);signal.throwIfAborted();assertOwned(expected,after,local);
            result.databaseState='READY_VERIFIED';return;
          }
        }
        await pause(pollMs);
      }
    },readinessMs,result.budgets);
  }catch(error){
    result.readinessError=errorName(error);
    result.readinessFailure=failureDetails(error,'READINESS');result.firstFailure??=result.readinessFailure;
    result.databaseState=error instanceof IdentityError?'IDENTITY_REJECTED'
      :error.name==='DeadlineError'?(observed?'NOT_READY':'NOT_OBSERVED'):'READINESS_UNVERIFIED';
  }
  if(result.databaseState==='READY_VERIFIED'&&result.executorState==='SUCCEEDED')result.status='READY';
  else {
    result.status=result.databaseState==='READY_VERIFIED'?'DATABASE_READY_EXECUTOR_FAILED'
      :result.databaseState==='IDENTITY_REJECTED'?'IDENTITY_REJECTED'
      :result.databaseState==='NOT_OBSERVED'&&result.executorState==='FAILED'?'STARTUP_UNVERIFIED':'DATABASE_READINESS_FAILED';
    result.cleanup=await stopOwnedDatabase(adapter,expected,result.identity,{cleanupMs,pollMs});
    // Absence can only be claimed after independent physical absence checks, not an inspection exception.
    if(result.status==='STARTUP_UNVERIFIED'&&result.cleanup.status==='ALREADY_STOPPED')result.status='DATABASE_START_FAILED';
  }
  return result;
}

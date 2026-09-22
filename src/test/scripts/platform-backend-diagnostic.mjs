// Narrow protocol for the isolated Java startup harness. No raw child output is persisted.
import fs from 'node:fs';
export const PREFIX='BROWSER_DIAGNOSTIC_V1 ';
export const STAGES=Object.freeze(['ARGUMENTS','CREDENTIALS_READ','ISOLATION_CONFIG','DATABASE_IDENTITY','SPRING_CONFIGURATION','SPRING_START','SYNTHETIC_ACCOUNT_PREPARATION','READY','WAIT_STOP','SPRING_CLOSE','COMPLETE']);
export const REASONS=Object.freeze(['UNKNOWN','ARGUMENTS_REJECTED','ISOLATION_PRECONDITION','UNKNOWN_ACCOUNT_RESIDUE','REFERENCE_ROLE_MISSING','SEED_ROLE_FAILED','STOP_IDENTITY_MISMATCH']);
export const LIMITS=Object.freeze({lineBytes:12288,events:32,chain:4,frames:4,suppressed:2,classLength:160,methodLength:100});
const exact=(v,keys)=>v!==null&&typeof v==='object'&&!Array.isArray(v)&&Object.keys(v).length===keys.length&&keys.every(k=>Object.hasOwn(v,k));
const clazz=v=>typeof v==='string'&&v.length<=LIMITS.classLength&&(v==='UNKNOWN'||/^(?:java|javax|jdk|sun|com\.wms|org\.springframework|org\.postgresql|com\.zaxxer|org\.hibernate|org\.apache)\.[A-Za-z0-9_.$]+$/.test(v));
const method=v=>typeof v==='string'&&v.length<=LIMITS.methodLength&&/^(?:[A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>)$/.test(v);
function node(v){return exact(v,['type','frames','framesTruncated'])&&clazz(v.type)&&typeof v.framesTruncated==='boolean'&&Array.isArray(v.frames)&&v.frames.length<=LIMITS.frames&&v.frames.every(f=>exact(f,['class','method','line'])&&clazz(f.class)&&method(f.method)&&Number.isInteger(f.line)&&f.line>=-2&&f.line<=1000000);}
export function parseDiagnosticLine(line){
 if(typeof line!=='string'||Buffer.byteLength(line)>LIMITS.lineBytes||!line.startsWith(PREFIX))return null;
 let v;try{v=JSON.parse(line.slice(PREFIX.length));}catch{return null;}
 if(v?.v!==1||!STAGES.includes(v.stage))return null;
 if(v.event==='STAGE')return exact(v,['v','event','stage'])?v:null;
 if(v.event!=='FAILURE'||!exact(v,['v','event','stage','reason','chain','chainTruncated','suppressed','suppressedTruncated'])||!REASONS.includes(v.reason)
  ||!Array.isArray(v.chain)||v.chain.length<1||v.chain.length>LIMITS.chain||!v.chain.every(node)||typeof v.chainTruncated!=='boolean'
  ||!Array.isArray(v.suppressed)||v.suppressed.length>LIMITS.suppressed||!v.suppressed.every(node)||typeof v.suppressedTruncated!=='boolean')return null;
 return v;
}
const markers=new Set(['B1_BACKEND_READY','B1_BACKEND_CLOSED','B1_PROXY_READY','B1_PROXY_CLOSED']);
export function createStartupCapture({directory,service}){
 if(!['backend','proxy'].includes(service))throw Error('BROWSER_DIAGNOSTIC_SERVICE');
 const log=directory+'/'+service+'-diagnostic.jsonl',markerLog=directory+'/'+service+'-markers.log';
 if(fs.existsSync(log)||fs.existsSync(markerLog))throw Error('BROWSER_DIAGNOSTIC_OUTPUT_EXISTS');
 const channels={stdout:{text:'',dropping:false},stderr:{text:'',dropping:false}};
 const state={version:1,service,events:0,lastStage:null,firstFailure:null,records:[],protocolIssues:{malformed:0,oversized:0,truncated:0,eventLimit:0,afterFailure:0,stageMismatch:0,ioError:0},suppressedLines:0,complete:false};
 const increment=(o,k)=>{o[k]=Math.min(1000000,o[k]+1);};
 function persist(file,text){try{fs.appendFileSync(file,text);}catch{increment(state.protocolIssues,'ioError');}}
 function line(text,stream){
  if(text.endsWith('\r'))text=text.slice(0,-1);
  if(markers.has(text)){if(text.startsWith('B1_'+service.toUpperCase()+'_'))persist(markerLog,text+'\n');else increment(state,'suppressedLines');return;}
  if(!text.startsWith(PREFIX)){increment(state,'suppressedLines');return;}
  const event=parseDiagnosticLine(text);if(!event){increment(state.protocolIssues,'malformed');return;}
  if(service!=='backend'){increment(state.protocolIssues,'malformed');return;}
  if(state.events>=LIMITS.events){increment(state.protocolIssues,'eventLimit');return;}
  if(state.firstFailure){increment(state.protocolIssues,'afterFailure');return;}
  if(event.event==='FAILURE'&&state.lastStage!==event.stage){increment(state.protocolIssues,'stageMismatch');return;}
  if(event.event==='STAGE')state.lastStage=event.stage;else state.firstFailure=event;
  const row={index:++state.events,stream,...event};state.records.push(row);persist(log,JSON.stringify(row)+'\n');
 }
 return {
  push(stream,chunk){
   if(state.complete||!Object.hasOwn(channels,stream))throw Error('BROWSER_DIAGNOSTIC_STREAM');
   const c=channels[stream];for(const byte of Buffer.from(chunk)){
    if(byte===10){if(!c.dropping)line(c.text,stream);c.text='';c.dropping=false;continue;}
    if(c.dropping)continue;
    if(c.text.length>=LIMITS.lineBytes){increment(state.protocolIssues,'oversized');c.text='';c.dropping=true;continue;}
    c.text+=String.fromCharCode(byte);
   }
  },
  finish(){if(!state.complete){for(const c of Object.values(channels)){if(c.text&&(c.text.startsWith(PREFIX)||PREFIX.startsWith(c.text)))increment(state.protocolIssues,'truncated');else if(c.text)increment(state,'suppressedLines');c.text='';c.dropping=false;}state.complete=true;}return this.snapshot();},
  snapshot(){return JSON.parse(JSON.stringify({...state,integrity:Object.values(state.protocolIssues).every(n=>n===0)}));}
 };
}
// Actual supervisor and synthetic subprocess tests share attachment, bounded drain and final projection.
export function attachStartupCapture(child,{directory,service}){
 const capture=createStartupCapture({directory,service});let drained=false;
 child.stdout.on('data',chunk=>capture.push('stdout',chunk));child.stderr.on('data',chunk=>capture.push('stderr',chunk));
 const done=new Promise(resolve=>{child.once('close',()=>{drained=true;resolve(capture.finish());});});
 return {snapshot:()=>capture.snapshot(),async finish(budgetMs=1000){
  if(drained)return capture.finish();let timer;const timeout=new Promise(resolve=>{timer=setTimeout(()=>resolve(null),budgetMs);});
  const result=await Promise.race([done,timeout]);clearTimeout(timer);
  if(result)return result;
  child.stdout.removeAllListeners('data');child.stderr.removeAllListeners('data');
  return {...capture.finish(),drainUnconfirmed:true,integrity:false};
 }};
}
export function mergeBackendDiagnostic(evidence,diagnostic){
 evidence.backendDiagnostic=diagnostic;
 if(diagnostic?.firstFailure&&evidence.firstFailure)evidence.firstFailure={...evidence.firstFailure,backend:diagnostic.firstFailure};
 if(diagnostic&&!diagnostic.integrity){
  evidence.diagnosticErrors??=[];evidence.diagnosticErrors.push({stage:'BACKEND_DIAGNOSTIC',code:'DIAGNOSTIC_EVIDENCE_INCOMPLETE'});
  evidence.firstFailure??={stage:'BACKEND_DIAGNOSTIC',name:'DiagnosticProtocolError',code:'DIAGNOSTIC_EVIDENCE_INCOMPLETE'};
 }
 return evidence;
}

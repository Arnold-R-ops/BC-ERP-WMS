// One authorized diagnosis, not a database runner. No init/start/SQL or policy changes.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {runProcess,failureDetails} from './platform-process-lifecycle.mjs';
import {inspectWindowsEnvironment,requireInspectionCapability,contextBinding,POWERSHELL} from './platform-launch-preflight.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G',OUT=ROOT+'/docs/releases/platform-query-diagnostics-20260919/a1';
const TARGET=ROOT+'/target/platform-db-acceptance-20260918/r10/pgdata';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const files=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(d+'/'+e.name):[d+'/'+e.name]);
const write=(name,value)=>fs.writeFileSync(OUT+'/'+name,JSON.stringify(value,null,2)+'\n',{flag:'wx'});
const read=name=>JSON.parse(fs.readFileSync(OUT+'/'+name,'utf8'));
const fingerprint=paths=>paths.map(p=>({path:p.slice(ROOT.length+1),sha256:sha(fs.readFileSync(p))}));
const controls=()=>['src/main','src/test/java','src/test/resources','frontend/src','docs/releases'].flatMap(p=>files(ROOT+'/'+p)).filter(p=>!p.startsWith(OUT+'/'));
export async function main(mode){
 if(mode==='baseline'){
  if(fs.existsSync(OUT))throw Error('New evidence directory required');
  const protectedFiles=fingerprint(controls());
  const tools=fingerprint(files(ROOT+'/src/test/scripts'));
  fs.mkdirSync(OUT,{recursive:true});write('before.json',{at:new Date().toISOString(),protectedFiles,tools});return;
 }
 if(!fs.existsSync(OUT+'/before.json'))throw Error('Baseline required');
 if(['events','events-detail','events-utf8'].includes(mode)){
  if(fs.existsSync(OUT+'/'+mode+'.json')||fs.existsSync(OUT+'/'+mode+'-attempt.json'))throw Error('Event inspection already attempted');
  write(mode+'-attempt.json',{startedAt:new Date().toISOString(),budgetMs:45000});
  const script=fileURLToPath(new URL('./platform-query-event-window.ps1',import.meta.url));
  const r=await runProcess(POWERSHELL,['-NoProfile','-NonInteractive','-Command',fs.readFileSync(script,'utf8')],{cwd:ROOT,timeoutMs:45000});
  let report=null;try{report=JSON.parse(r.output.trim().replace(/^\uFEFF/,''));}catch{}
  write(mode+'.json',{status:r.exitCode===0&&r.outputComplete&&!r.timedOut&&report?'COLLECTED':'FAILED',process:{...r,output:undefined},scriptSha256:sha(fs.readFileSync(script)),report});
  console.log(JSON.stringify({exitCode:r.exitCode,timedOut:r.timedOut,elapsedMs:r.elapsedMs,logs:report?.logs.map(l=>({log:l.log,status:l.status,count:l.events.length}))}));return;
 }
 if(mode==='query'){
  const attempts=files(OUT).filter(p=>/query-[123]-attempt\.json$/.test(p)).length;
  if(attempts>=3)throw Error('Three read-only query attempts exhausted');
  const id=attempts+1;write('query-'+id+'-attempt.json',{startedAt:new Date().toISOString(),directory:TARGET,port:55449,budgetMs:15000});
  const commands=[],result={id,databaseConnections:0,startedAt:new Date().toISOString(),commands,status:'RUNNING',inputFiles:fingerprint(['platform-query-diagnostics.mjs','platform-launch-preflight.mjs','platform-process-lifecycle.mjs','platform-process-query.ps1','platform-windows-identity.ps1'].map(n=>ROOT+'/src/test/scripts/'+n))};
  const command=async(exe,args,options)=>{
   const r=await runProcess(exe,args,{...options,cwd:ROOT});
   commands.push({index:commands.length,executable:exe,payloadSha256:sha(args.join('\0')),timeoutMs:options.timeoutMs,timing:r.timing,receipt:r.receipt,exitCode:r.exitCode,exited:r.exited,timedOut:r.timedOut,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed});
   return {...r,commandIndex:commands.length-1};
  };
  try{
   const r=await inspectWindowsEnvironment(command,{directory:TARGET,port:55449});requireInspectionCapability(r);
   const context=contextBinding(r);result.context=context;
   if(context.user!=='ARNOLD\\Arnold'||!context.elevated||!context.administratorEnabled||context.restrictedToken)throw Error('Approved execution context mismatch');
   result.inventory={directory:r.directory.status,pidFile:r.pidFile.status,targetMatches:r.postgres.targetMatches.length,targetListeners:r.listeners.selected.filter(p=>p.port===55449).length,selfPid:r.self.pid,ownerPid:r.owner.pid};
   if(result.inventory.pidFile!=='ABSENT'||result.inventory.targetMatches!==0||result.inventory.targetListeners!==0)throw Error('Unexpected target presence; no remediation authorized');
   result.status='PASSED';
  }catch(error){result.status='FAILED';result.failure=failureDetails(error,'READ_ONLY_QUERY');}
  result.completedAt=new Date().toISOString();write('query-'+id+'.json',result);
  console.log(JSON.stringify(result));if(result.status!=='PASSED')process.exitCode=1;return;
 }
 if(mode==='verify'){
  const before=read('before.json');const after=fingerprint(controls());
  if(JSON.stringify(before.protectedFiles)!==JSON.stringify(after))throw Error('Protected input/evidence changed');
  const current=fingerprint(files(ROOT+'/src/test/scripts'));
  const changed=current.filter(f=>before.tools.find(b=>b.path===f.path)?.sha256!==f.sha256);
  write('after.json',{at:new Date().toISOString(),protectedFilesUnchanged:true,protectedCount:after.length,tools:current,changed});console.log(JSON.stringify({protectedFilesUnchanged:true,changed}));return;
 }
 throw Error('Only baseline/events/query/verify');
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await main(process.argv[2]);

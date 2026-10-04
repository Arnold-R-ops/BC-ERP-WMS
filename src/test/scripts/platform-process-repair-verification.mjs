// Dedicated no-database repair evidence. Never initializes, starts or connects to PostgreSQL.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {runProcess} from './platform-process-lifecycle.mjs';
import {historyCheck} from './platform-s1-preparation.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G',BASE=ROOT+'/docs/releases/platform-process-repair-20260916';
const PRIOR=ROOT+'/docs/releases/platform-start-stop-validation-20260914/s1';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const digest=p=>hash(fs.readFileSync(p));
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const walk=p=>fs.readdirSync(p,{withFileTypes:true}).flatMap(e=>{if(e.isSymbolicLink())throw Error('Symlink refused');return e.isDirectory()?walk(p+'/'+e.name):[p+'/'+e.name];}).sort();
const extra=['platform-process-repair-verification.mjs','platform-process-ancestry.mjs','platform-process-ancestry.test.mjs',
 'platform-process-query.ps1','platform-process-query.test.mjs','platform-process-timing.test.mjs','platform-s1-checked-stop.mjs'].map(f=>'src/test/scripts/'+f);
const allowed=[...extra,'platform-process-lifecycle.mjs','platform-launch-preflight.mjs','platform-windows-identity.ps1','platform-s1-start-stop.mjs'].map(f=>f.startsWith('src/')?f:'src/test/scripts/'+f);
function baseline(){
 if(fs.existsSync(BASE))throw Error('Evidence root already exists');
 const prior=read(PRIOR+'/input-manifest.json');
 for(const f of prior.files)if(digest(ROOT+'/'+f.path)!==f.sha256||digest(prior.source+'/'+f.path)!==f.sha256)throw Error('Unreviewed initial drift: '+f.path);
 const history=historyCheck();
 const roots=[ROOT+'/target/platform-start-stop-validation-20260914/s1',PRIOR,ROOT+'/target/platform-s1-tool-validation-20260914/a1'];
 const preserved=roots.map(location=>({location,files:walk(location).map(p=>({path:path.relative(location,p).replaceAll('\\','/'),sha256:digest(p)}))}));
 const protectedFiles=['docs/PLATFORM_S1_START_STOP_VALIDATION_2026-09-14.md','src/test/scripts/platform-s1-checked-stop.mjs'].map(p=>({path:p,sha256:digest(ROOT+'/'+p)}));
 fs.mkdirSync(BASE,{recursive:true});save(BASE+'/baseline.json',{createdAt:new Date().toISOString(),history,preserved,protectedFiles,head:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim()});
 console.log(JSON.stringify({baseline:true,history,preservedFiles:preserved.reduce((n,g)=>n+g.files.length,0)}));
}
function preserved(){
 const b=read(BASE+'/baseline.json');historyCheck();
 for(const g of b.preserved){const names=walk(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/'));if(JSON.stringify(names)!==JSON.stringify(g.files.map(f=>f.path)))throw Error('Historical inventory changed');for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical hash changed');}
 for(const f of b.protectedFiles)if(digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Protected source/report changed');
 return {unchanged:true,r2ThroughR7Files:b.history.files,s1Files:b.preserved.reduce((n,g)=>n+g.files.length,0)};
}
function seal(label){
 const source=ROOT+'/target/platform-process-repair-20260916/'+label+'/source',delivery=BASE+'/'+label;
 if(fs.existsSync(source)||fs.existsSync(delivery))throw Error('Never reuse repair output');
 const old=read(PRIOR+'/input-manifest.json'),oldMap=new Map(old.files.map(f=>[f.path,f.sha256]));
 const rows=[...new Set([...oldMap.keys(),...extra])].sort().map(p=>({path:p,sha256:digest(ROOT+'/'+p)}));
 const changes=rows.filter(f=>f.sha256!==oldMap.get(f.path)).map(f=>({...f,previousSha256:oldMap.get(f.path)??null}));
 for(const f of changes)if(!allowed.includes(f.path))throw Error('Out of scope change: '+f.path);
 for(const t of old.tools)if(digest(t.path)!==t.sha256)throw Error('Tool drift');
 const history=preserved();fs.mkdirSync(source,{recursive:true});fs.mkdirSync(delivery,{recursive:true});
 for(const f of rows){const dest=source+'/'+f.path;fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(ROOT+'/'+f.path,dest,fs.constants.COPYFILE_EXCL);}
 const m={id:hash(rows.map(f=>f.path+'\0'+f.sha256+'\n').join('')),source,delivery,createdAt:new Date().toISOString(),files:rows,changes,tools:old.tools,history,previousInputId:old.id,javaCompilation:false};
 save(delivery+'/input-manifest.json',m);console.log(JSON.stringify({inputId:m.id,files:rows.length,changes:changes.map(f=>f.path),source}));
}
async function verify(label){
 const m=read(BASE+'/'+label+'/input-manifest.json');
 if(fileURLToPath(import.meta.url).replaceAll('\\','/')!==m.source+'/src/test/scripts/platform-process-repair-verification.mjs')throw Error('Use frozen verifier');
 const out=path.dirname(m.source)+'/verification';if(fs.existsSync(out))throw Error('Never overwrite verification');fs.mkdirSync(out);
 const suites=['platform-process-lifecycle','platform-launch-preflight','platform-s1-start-stop','platform-process-ancestry','platform-process-query','platform-process-timing','platform-fixture-isolation','platform-upgrade-sentinels','platform-test-results','platform-r6-stages','platform-report-runner','platform-r7-binding'];
 const r={inputId:m.id,status:'RUNNING',startedAt:new Date().toISOString(),syntax:[],suites:[],databaseCommands:0,databaseConnections:0,javaCompilation:false};
 try{
  for(const f of m.changes.filter(f=>f.path.endsWith('.mjs'))){const c=await runProcess(process.execPath,['--check',m.source+'/'+f.path],{timeoutMs:15000});if(c.exitCode!==0||!c.outputComplete)throw Error('Syntax failed: '+f.path);r.syntax.push({file:f.path,status:'PASSED'});}
  for(const s of suites){
   const args=['--test','--test-reporter=tap',m.source+'/src/test/scripts/'+s+'.test.mjs'];
   const c=await runProcess(process.execPath,args,{cwd:ROOT,timeoutMs:120000,env:{...process.env,P3_TOOL_EVIDENCE_DIR:out}});
   const log=out+'/'+s+'.tap';fs.writeFileSync(log,c.output,{flag:'wx'});const n=k=>Number(c.output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
   const row={suite:s,args,exitCode:c.exitCode,exited:c.exited,outputComplete:c.outputComplete,terminationConfirmed:c.terminationConfirmed,timing:c.timing,tests:n('tests'),passed:n('pass'),failed:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),log,sha256:digest(log)};r.suites.push(row);
   if(c.exitCode!==0||!c.outputComplete||!c.terminationConfirmed||!row.tests||row.tests!==row.passed||row.failed||row.skipped||row.cancelled)throw Error('Regression failed: '+s);
   console.log(s+': '+row.passed+' passed');
  }
  for(const f of m.files)if(digest(ROOT+'/'+f.path)!==f.sha256||digest(m.source+'/'+f.path)!==f.sha256)throw Error('Source drift');
  r.history=preserved();r.status='PASSED';
 }catch(e){r.status='FAILED';r.failure=e.message;process.exitCode=1;}
 r.completedAt=new Date().toISOString();r.totals={tests:r.suites.reduce((n,s)=>n+s.tests,0),passed:r.suites.reduce((n,s)=>n+s.passed,0)};
 r.outputFiles=walk(out).map(p=>({path:p,sha256:digest(p)}));save(m.delivery+'/no-database-results.json',r);console.log(JSON.stringify({status:r.status,...r.totals,failure:r.failure}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(process.version!=='v24.12.0'||process.cwd().replaceAll('\\','/')!==ROOT)throw Error('Fixed Node/workspace required');
 const [mode,label]=process.argv.slice(2);if(mode==='baseline')baseline();else if(mode==='history')console.log(JSON.stringify(preserved()));else if(!/^a[1-9][0-9]*$/.test(label??''))throw Error('Explicit fresh repair label required');else if(mode==='seal')seal(label);else if(mode==='verify')await verify(label);else throw Error('No database modes');
}

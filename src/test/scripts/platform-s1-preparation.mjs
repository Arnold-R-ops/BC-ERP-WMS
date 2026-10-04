// Only file sealing / Node tests. The database directory is never created by this preparation entry.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {runProcess} from './platform-process-lifecycle.mjs';
import {ROOT,RUN,DELIVERY,PREPARATION,PG} from './platform-s1-start-stop.mjs';
const priorDir=ROOT+'/docs/releases/platform-launch-preflight-20260914/a1';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const digest=p=>sha(fs.readFileSync(p));
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
const files=p=>fs.readdirSync(p,{withFileTypes:true}).flatMap(e=>{if(e.isSymbolicLink())throw Error('No symbolic links');return e.isDirectory()?files(p+'/'+e.name):[p+'/'+e.name];}).sort();
const added=['platform-s1-start-stop.mjs','platform-s1-start-stop.test.mjs','platform-s1-preparation.mjs'].map(f=>'src/test/scripts/'+f);
export function historyCheck(){const base=read(priorDir+'/baseline.json');for(const g of base.history){const names=g.rootFilesOnly?fs.readdirSync(g.location,{withFileTypes:true}).filter(e=>e.isFile()).map(e=>e.name).sort():files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort();
 if(JSON.stringify(names)!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('Historical inventory changed');for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical file changed');}
 for(const f of base.protectedFiles)if(digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Protected file changed');return {files:base.history.reduce((n,g)=>n+g.files.length,0),unchanged:true};}
function seal(){
 if(fs.existsSync(PREPARATION)||fs.existsSync(DELIVERY)||fs.existsSync(RUN))throw Error('Existing preparation, evidence or s1 target; stop');
 const old=read(priorDir+'/input-manifest.json');for(const f of old.files)if(digest(ROOT+'/'+f.path)!==f.sha256||digest(old.source+'/'+f.path)!==f.sha256)throw Error('Unreviewed prior source drift');
 const history=historyCheck();
 const source=PREPARATION+'/source',rows=[...old.files,...added.map(p=>({path:p,sha256:digest(ROOT+'/'+p)}))].sort((a,b)=>a.path.localeCompare(b.path));
 const oldTools=read(ROOT+'/docs/releases/platform-db-acceptance-20260911/r7/backend-input-manifest.json').externalTools.filter(t=>!t.path.includes('Corretto'));
 for(const t of oldTools)if(digest(t.path)!==t.sha256)throw Error('Fixed tool drift');
 fs.mkdirSync(source,{recursive:true});fs.mkdirSync(DELIVERY,{recursive:true});
 for(const f of rows){const p=source+'/'+f.path;fs.mkdirSync(path.dirname(p),{recursive:true});fs.copyFileSync(ROOT+'/'+f.path,p,fs.constants.COPYFILE_EXCL);if(digest(p)!==f.sha256)throw Error('Copy mismatch');}
 const m={id:sha(Buffer.from(rows.map(f=>f.path+'\0'+f.sha256+'\n').join(''))),createdAt:new Date().toISOString(),source,files:rows,added,existingSourceChanges:0,tools:oldTools,history,
  head:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),previousInputId:old.id,fullBackendCompilation:false};
 save(DELIVERY+'/input-manifest.json',m);console.log(JSON.stringify({sealed:true,inputId:m.id,sourceFiles:rows.length,history,existingSourceChanges:0}));
}
async function verify(){
 const m=read(DELIVERY+'/input-manifest.json');if(fileURLToPath(import.meta.url).replaceAll('\\','/')!==m.source+'/src/test/scripts/platform-s1-preparation.mjs')throw Error('Use the frozen verifier');
 const out=PREPARATION+'/verification';if(fs.existsSync(out)||fs.existsSync(RUN))throw Error('Do not reuse output / database root must still be absent');fs.mkdirSync(out);
 const result={inputId:m.id,status:'RUNNING',startedAt:new Date().toISOString(),syntax:[],suites:[],databaseStarted:false,databaseConnections:0,javaCompilation:false};
 const expected=[['platform-process-lifecycle.test.mjs',35],['platform-launch-preflight.test.mjs',40],['platform-s1-start-stop.test.mjs',33]];
 try{
  for(const file of added){const r=await runProcess(process.execPath,['--check',m.source+'/'+file],{cwd:ROOT,timeoutMs:15000});if(r.exitCode!==0||!r.outputComplete)throw Error('Syntax failed');result.syntax.push({file,exitCode:r.exitCode});}
  const version=await runProcess(PG+'/postgres.exe',['--version'],{cwd:ROOT,timeoutMs:10000});if(version.exitCode!==0||!version.outputComplete||!version.output.includes('(PostgreSQL) 18.6'))throw Error('PostgreSQL version mismatch');result.postgresVersion=version.output.trim();
  for(const [script,count] of expected){const args=['--test','--test-reporter=tap',m.source+'/src/test/scripts/'+script],r=await runProcess(process.execPath,args,{cwd:ROOT,timeoutMs:120000});
   const log=out+'/'+script+'.tap';fs.writeFileSync(log,r.output,{flag:'wx'});const n=k=>Number(r.output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
   const row={script,args,exitCode:r.exitCode,exited:r.exited,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,tests:n('tests'),passed:n('pass'),failed:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),log,sha256:digest(log)};result.suites.push(row);
   if(r.exitCode!==0||!r.outputComplete||!r.terminationConfirmed||!row.tests||row.tests!==row.passed||(count!==null&&row.tests!==count)||row.failed||row.skipped||row.cancelled)throw Error('No-database regression failed: '+script);
   console.log(script+': '+row.passed+' passed');
  }
  for(const f of m.files)if(digest(m.source+'/'+f.path)!==f.sha256||digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Source changed');
  result.history=historyCheck();if(fs.existsSync(RUN))throw Error('No-database verification created target');result.status='PASSED';
 }catch(e){result.status='FAILED';result.failure=e.message;process.exitCode=1;}
 result.completedAt=new Date().toISOString();result.totals={tests:result.suites.reduce((n,s)=>n+s.tests,0),passed:result.suites.reduce((n,s)=>n+s.passed,0)};
 save(DELIVERY+'/no-database-results.json',result);console.log(JSON.stringify({status:result.status,...result.totals,databaseStarted:false}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(process.version!=='v24.12.0'||process.cwd().replaceAll('\\','/')!==ROOT)throw Error('Wrong Node or working directory');
 if(process.argv[2]==='seal')seal();else if(process.argv[2]==='verify')await verify();else if(process.argv[2]==='history')console.log(JSON.stringify(historyCheck()));else throw Error('Only seal/verify/history');
}

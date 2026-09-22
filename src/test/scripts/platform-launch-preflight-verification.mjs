// Tool-only delivery. Never initializes, starts, connects to, or stops PostgreSQL.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {runProcess} from './platform-process-lifecycle.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const RUN=ROOT+'/target/platform-launch-preflight-20260914/a1';
export const DELIVERY=ROOT+'/docs/releases/platform-launch-preflight-20260914/a1';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const digest=p=>sha(fs.readFileSync(p));
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const save=(p,v)=>{fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});};
const files=p=>fs.readdirSync(p,{withFileTypes:true}).flatMap(e=>{if(e.isSymbolicLink())throw Error('Unexpected link');return e.isDirectory()?files(p+'/'+e.name):[p+'/'+e.name];}).sort();
const inventory=p=>files(p).map(f=>({path:path.relative(p,f).replaceAll('\\','/'),sha256:digest(f)}));
const aggregate=rows=>sha(Buffer.from(rows.map(r=>r.path+'\0'+r.sha256+'\n').join('')));
const prior=()=>read(ROOT+'/docs/releases/platform-db-acceptance-20260911/r7/backend-input-manifest.json');
const allowed=['platform-r7-database-batch.mjs','platform-process-lifecycle.mjs','platform-process-lifecycle.test.mjs'].map(f=>'src/test/scripts/'+f);
const added=['platform-launch-preflight-verification.mjs','platform-windows-identity.ps1','platform-launch-preflight.mjs','platform-launch-preflight.test.mjs'].map(f=>'src/test/scripts/'+f);
function checkHistory(base){for(const group of base.history){const now=group.rootFilesOnly?fs.readdirSync(group.location,{withFileTypes:true}).filter(e=>e.isFile()).map(e=>({path:e.name,sha256:digest(group.location+'/'+e.name)})).sort((a,b)=>a.path.localeCompare(b.path)):inventory(group.location);if(JSON.stringify(now)!==JSON.stringify(group.files))throw Error('Historical directory drift: '+group.location);}return true;}
function baseline(){
 if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('Never reuse tool verification directory');
 const source=prior().groups.find(g=>g.name==='backend-source').files.map(f=>({path:f.path,sha256:digest(ROOT+'/'+f.path)}));
 const originalDifferences=source.filter(f=>prior().groups.find(g=>g.name==='backend-source').files.find(o=>o.path===f.path)?.sha256!==f.sha256);
 if(originalDifferences.length)throw Error('Unreviewed input drift: '+originalDifferences.map(f=>f.path).join(','));
 const history=[];
 for(const r of [2,3,4,5,6,7])for(const kind of ['target','docs/releases']){
  const location=ROOT+'/'+kind+'/platform-db-acceptance-20260911'+(r===2&&kind==='docs/releases'?'':'/r'+r);
  const rows=r===2&&kind==='docs/releases'?fs.readdirSync(location,{withFileTypes:true}).filter(e=>e.isFile()).map(e=>({path:e.name,sha256:digest(location+'/'+e.name)})).sort((a,b)=>a.path.localeCompare(b.path)):inventory(location);
  history.push({location,rootFilesOnly:r===2&&kind==='docs/releases',files:rows});
 }
 const protectedFiles=['docs/PLATFORM_R7_DATABASE_ACCEPTANCE_2026-09-14.md','frontend/src/test/addressLength.audit.test.tsx','frontend/pnpm-lock.yaml'].map(p=>({path:p,sha256:digest(ROOT+'/'+p)}));
 save(DELIVERY+'/baseline.json',{createdAt:new Date().toISOString(),head:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),source,history,protectedFiles});
 fs.mkdirSync(RUN,{recursive:true});console.log(JSON.stringify({baseline:true,sourceFiles:source.length,historicalFiles:history.reduce((n,g)=>n+g.files.length,0)}));
}
function seal(){
 const base=read(DELIVERY+'/baseline.json'),source=base.source.map(f=>({path:f.path,sha256:digest(ROOT+'/'+f.path)}));
 const changes=source.filter(f=>base.source.find(o=>o.path===f.path).sha256!==f.sha256);
 if(changes.some(f=>!allowed.includes(f.path)))throw Error('Out-of-scope source change');
 for(const f of base.protectedFiles)if(digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Protected file drift');
 source.push(...added.map(p=>({path:p,sha256:digest(ROOT+'/'+p)})));source.sort((a,b)=>a.path.localeCompare(b.path));
 const dest=RUN+'/source';if(fs.existsSync(dest))throw Error('Never replace frozen tool source');
 for(const f of source){const p=dest+'/'+f.path;fs.mkdirSync(path.dirname(p),{recursive:true});fs.copyFileSync(ROOT+'/'+f.path,p,fs.constants.COPYFILE_EXCL);if(digest(p)!==f.sha256)throw Error('Copy drift');}
 checkHistory(base);
 save(DELIVERY+'/input-manifest.json',{id:aggregate(source),createdAt:new Date().toISOString(),source:dest,files:source,changes,added,productAndBusinessJavaChanges:0,migrationChanges:0,historicalDirectoriesVerified:base.history.length,
  node:{path:process.execPath,version:process.version,sha256:digest(process.execPath)},powershell:{path:'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',sha256:digest('C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe')},fullBackendCompilation:false});
 console.log(JSON.stringify({sealed:true,inputId:aggregate(source),changedExistingFiles:changes.map(f=>f.path),added,javaChanges:0}));
}
async function verify(){
 const m=read(DELIVERY+'/input-manifest.json');if(fileURLToPath(import.meta.url).replaceAll('\\','/')!==m.source+'/src/test/scripts/platform-launch-preflight-verification.mjs')throw Error('Run the frozen verifier');
 const check=()=>{for(const f of m.files)if(digest(m.source+'/'+f.path)!==f.sha256||digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Input drift');};check();
 const out=RUN+'/verification';if(fs.existsSync(out))throw Error('Fresh output required');fs.mkdirSync(out);
 const suites=[['platform-fixture-isolation.test.mjs',24],['platform-process-lifecycle.test.mjs',35],['platform-upgrade-sentinels.test.mjs',65],['platform-test-results.test.mjs',67],['platform-r6-stages.test.mjs',57],['platform-report-runner.test.mjs',60],['platform-r7-binding.test.mjs',8],['platform-launch-preflight.test.mjs',40]];
 const result={inputId:m.id,startedAt:new Date().toISOString(),databaseStarted:false,databaseConnections:0,databaseBusinessTestsExecuted:0,javaCompilation:false,syntax:[],suites:[],status:'RUNNING'};
 try{
  for(const f of [...allowed,...added].filter(f=>f.endsWith('.mjs'))){const args=['--check',m.source+'/'+f],r=await runProcess(process.execPath,args,{cwd:ROOT,timeoutMs:30000});if(r.exitCode!==0||!r.outputComplete)throw Error('Syntax failed: '+f);result.syntax.push({file:f,args,exitCode:r.exitCode});}
  const psArgs=['-NoProfile','-NonInteractive','-Command',`$t=$null;$e=$null;[System.Management.Automation.Language.Parser]::ParseFile('${m.source}/src/test/scripts/platform-windows-identity.ps1',[ref]$t,[ref]$e)|Out-Null;if($e.Count){throw 'PowerShell syntax failed'};'PowerShell syntax passed'`];
  const ps=await runProcess(m.powershell.path,psArgs,{cwd:ROOT,timeoutMs:15000});if(ps.exitCode!==0||!ps.outputComplete)throw Error('PowerShell syntax failed');result.syntax.push({file:'platform-windows-identity.ps1',args:psArgs,exitCode:ps.exitCode});
  for(const [script,expected] of suites){
   const args=['--test','--test-reporter=tap',m.source+'/src/test/scripts/'+script];
   const r=await runProcess(process.execPath,args,{cwd:ROOT,timeoutMs:120000});const log=out+'/'+script+'.tap';fs.writeFileSync(log,r.output,{flag:'wx'});
   const n=k=>Number(r.output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
   const row={script,args,exitCode:r.exitCode,exited:r.exited,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,tests:n('tests'),passed:n('pass'),failed:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),log,sha256:digest(log)};result.suites.push(row);
   if(r.exitCode!==0||!r.exited||!r.outputComplete||!r.terminationConfirmed||!row.tests||row.passed!==row.tests||(expected!==null&&row.tests!==expected)||row.failed||row.skipped||row.cancelled)throw Error('No-database gate failed: '+script);
   console.log(script+': '+row.passed+' passed');
  }
  check();checkHistory(read(DELIVERY+'/baseline.json'));result.historyUnchanged=true;result.status='PASSED';
 }catch(e){result.status='FAILED';result.failure=e.message;process.exitCode=1;}
 result.completedAt=new Date().toISOString();result.totals={tests:result.suites.reduce((n,s)=>n+s.tests,0),passed:result.suites.reduce((n,s)=>n+s.passed,0)};
 save(DELIVERY+'/verification-results.json',result);console.log(JSON.stringify({status:result.status,...result.totals,databaseBusinessTestsExecuted:0}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(process.cwd().replaceAll('\\','/')!==ROOT||process.version!=='v24.12.0')throw Error('Wrong root or Node');
 if(process.argv[2]==='baseline')baseline();else if(process.argv[2]==='seal')seal();else if(process.argv[2]==='verify')await verify();else throw Error('Only baseline/seal/verify');
}

// One fresh no-database diagnostic snapshot. Never invokes supervisor, Spring, PostgreSQL, or the proxy.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
const ROOT='D:/ERP_WMS/BC warehouse/2G';
const RUN=ROOT+'/target/platform-backend-diagnostic-repair-20260920/a1';
const DELIVERY=ROOT+'/docs/releases/platform-backend-diagnostic-repair-20260920/a1';
const SOURCE=RUN+'/snapshot',BUILD=RUN+'/classes';
const OLD=ROOT+'/target/platform-b2-preparation-20260920/a1';
const OLD_EVIDENCE=ROOT+'/docs/releases/platform-browser-invitation-20260920/b2';
const OLD_RUNTIME=ROOT+'/target/platform-browser-invitation-20260920/b2';
const NODE='D:/JAVA/NODE/node.exe',JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'),hash=p=>sha(fs.readFileSync(p)),json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
function files(root){return fs.readdirSync(root,{withFileTypes:true}).flatMap(e=>{if(e.isSymbolicLink())throw Error('DIAGNOSTIC_SOURCE_LINK');const p=root+'/'+e.name;return e.isDirectory()?files(p):[p];}).sort();}
const inventory=root=>files(root).map(p=>({path:path.relative(root,p).replaceAll('\\','/'),sha256:hash(p)}));
const aggregate=rows=>sha(rows.map(r=>r.path+'\0'+r.sha256+'\n').join(''));
const ownScripts=['platform-b1-batch.mjs','platform-b1-prepare.mjs','platform-backend-diagnostic.mjs','platform-backend-diagnostic.test.mjs','platform-backend-diagnostic-verification.mjs'];
const ownJava=['PlatformB1Harness.java','PlatformBackendDiagnostic.java','PlatformBackendDiagnosticProbe.java'];
async function main(){
 if(process.version!=='v24.12.0'||process.cwd().replaceAll('\\','/')!==ROOT||process.argv.length!==3||process.argv[2]!=='no-database-diagnostic-only')throw Error('DIAGNOSTIC_CONTEXT');
 if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('DIAGNOSTIC_OUTPUT_EXISTS');
 const {verifySealed}=await import(pathToFileURL(OLD+'/snapshot/platform-b1-prepare.mjs').href),old=verifySealed();
 if(old.id!=='02de26106a9a896ea29f72dd0c7fd85ff522adedb5f6b773aa69d49fb5da84b2'||hash(OLD_EVIDENCE+'/environment-final.json')!=='1cbb61799b85c8410afc45d158424cb1404ca04881b76d896c80025e1156f158')throw Error('DIAGNOSTIC_B2_BASE_DRIFT');
 const history=[OLD,OLD_EVIDENCE,OLD_RUNTIME].map(root=>({root,files:inventory(root)}));
 if(fs.readdirSync(OLD_RUNTIME+'/private').length)throw Error('DIAGNOSTIC_B2_PRIVATE_CHANGED');
 const content=new Map(),provenance=[];
 for(const f of old.files){if(['compile.args','runtime.args'].includes(f.path))continue;const origin=OLD+'/snapshot/'+f.path,bytes=fs.readFileSync(origin);if(sha(bytes)!==f.sha256)throw Error('DIAGNOSTIC_BASE_FILE_DRIFT');content.set(f.path,bytes);provenance.push({path:f.path,origin,sha256:f.sha256,mode:'UNCHANGED_B2_TEST_SNAPSHOT'});}
 for(const [folder,names]of [['src/test/scripts/',ownScripts],['src/test/tools/browseracceptance/java/com/wms/browseracceptance/',ownJava]])for(const name of names){
  const origin=ROOT+'/'+folder+name,bytes=fs.readFileSync(origin);content.set(name,bytes);const i=provenance.findIndex(r=>r.path===name);if(i>=0)provenance.splice(i,1);provenance.push({path:name,origin,sha256:sha(bytes),mode:'AUTHORIZED_DIAGNOSTIC_CHANGE'});
 }
 const {requireContents}=await import(pathToFileURL(OLD+'/snapshot/platform-b1-catalog.mjs').href);requireContents(content);
 const gates=[...old.gates,['platform-b1-catalog.test.mjs',13],['platform-b1-startup-security.test.mjs',25],['platform-backend-diagnostic.test.mjs',38]];
 for(const [name]of gates.slice(0,-1))if(sha(content.get(name))!==old.files.find(r=>r.path===name).sha256)throw Error('DIAGNOSTIC_EXISTING_ASSERTIONS_CHANGED');
 if(sha(content.get('PlatformB1Contract.java'))!==old.files.find(r=>r.path==='PlatformB1Contract.java').sha256)throw Error('DIAGNOSTIC_JAVA_ASSERTIONS_CHANGED');
 for(const row of provenance)if(hash(row.origin)!==row.sha256)throw Error('DIAGNOSTIC_SOURCE_CHANGED');
 for(const row of old.dependencies)if(hash(row.location)!==row.sha256)throw Error('DIAGNOSTIC_DEPENDENCY_DRIFT');
 fs.mkdirSync(SOURCE,{recursive:true});fs.mkdirSync(DELIVERY,{recursive:true});
 for(const [name,bytes]of content){fs.mkdirSync(path.dirname(SOURCE+'/'+name),{recursive:true});fs.writeFileSync(SOURCE+'/'+name,bytes,{flag:'wx'});}
 const classpath=[BUILD,ROOT+'/target/platform-db-acceptance-20260919/r11/build/a1/classes',...old.dependencies.map(d=>d.location)].join(';');
 fs.writeFileSync(SOURCE+'/runtime.args','-cp\n'+JSON.stringify(classpath)+'\n',{flag:'wx'});
 fs.writeFileSync(SOURCE+'/compile.args',['-proc:none','-encoding','UTF-8','-cp',JSON.stringify(classpath),'-d',JSON.stringify(BUILD),...[...content.keys()].filter(n=>n.endsWith('.java')).map(n=>JSON.stringify(SOURCE+'/'+n))].join('\n'),{flag:'wx'});
 const rows=inventory(SOURCE),inputId=aggregate(rows),syntax=rows.filter(r=>/\.(mjs|ps1)$/.test(r.path)).map(r=>r.path);
 save(DELIVERY+'/input.json',{id:inputId,createdAt:new Date().toISOString(),source:SOURCE,build:BUILD,files:rows,provenance,history,originalInputId:old.id,products:old.products,dependencies:old.dependencies,externalTools:old.externalTools,syntax,gates,javaContracts:16,budgets:{syntax:15000,compile:300000,java:60000,nodeSuite:120000,syntheticJava:10000,pipeDrain:1000},scope:'DIAGNOSTIC_VALIDATION_ONLY',runtimeStartAuthorized:false,awakeRequested:false});
 const {runProcess}=await import(pathToFileURL(SOURCE+'/platform-process-lifecycle.mjs').href);
 async function run(label,exe,args,budget){
  const r=await runProcess(exe,args,{cwd:ROOT,timeoutMs:budget,env:{...process.env,pnpm_config_verify_deps_before_run:'error'},outputFile:DELIVERY+'/'+label+'.log'});
  const {output,...receipt}=r;const passed=r.exited&&r.exitCode===0&&r.outputComplete&&r.terminationConfirmed&&!r.timedOut&&!r.aborted&&!r.spawnError&&r.elapsedMs<=budget;
  save(DELIVERY+'/'+label+'.command.json',{label,exe,args,budgetMs:budget,...receipt,status:passed?'PASSED':'FAILED',logSha256:hash(DELIVERY+'/'+label+'.log')});
  if(!passed)throw Error('DIAGNOSTIC_COMMAND_FAILED '+label);console.log(label+' PASSED');return fs.readFileSync(DELIVERY+'/'+label+'.log','utf8');
 }
 for(const name of syntax){if(name.endsWith('.mjs'))await run('syntax-'+name,NODE,['--check',SOURCE+'/'+name],15000);else await run('syntax-'+name,'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',['-NoProfile','-NonInteractive','-Command',"$t=$null;$e=$null;[void][Management.Automation.Language.Parser]::ParseFile('"+SOURCE+'/'+name+"',[ref]$t,[ref]$e);if($e.Count){$e|ForEach-Object {$_.ErrorId};exit 1}"],15000);}
 fs.mkdirSync(BUILD);await run('independent-javac',JAVA.replace('/java.exe','/javac.exe'),['@'+SOURCE+'/compile.args'],300000);
 const classes=inventory(BUILD);save(DELIVERY+'/compiled.json',{id:aggregate(classes),inputId,files:classes,productClassesRecompiled:false});
 const java=await run('java-contracts',JAVA,['@'+SOURCE+'/runtime.args','com.wms.browseracceptance.PlatformB1Contract'],60000);if(!java.includes('B1_NO_DATABASE_CONTRACTS_PASSED=16'))throw Error('DIAGNOSTIC_JAVA_COVERAGE');
 const results=[];
 for(const [script,count]of gates){const output=await run('gate-'+script,NODE,['--test','--test-reporter=tap',SOURCE+'/'+script],120000);const n=k=>Number(output.match(new RegExp('^# '+k+' (\\d+)\\r?$','m'))?.[1]);const r={script,expected:count,tests:n('tests'),passed:n('pass'),failed:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),todo:n('todo')};save(DELIVERY+'/assessment-'+script+'.json',r);results.push(r);if(r.tests!==count||r.passed!==count||[r.failed,r.skipped,r.cancelled,r.todo].some(v=>v!==0))throw Error('DIAGNOSTIC_COVERAGE '+script);}
 const synthetic=inventory(RUN+'/synthetic-diagnostic-results');
 for(const file of synthetic){const text=fs.readFileSync(RUN+'/synthetic-diagnostic-results/'+file.path,'utf8');if(['SYNTHETIC_PASSWORD_DO_NOT_PERSIST','eyJsynthetic.payload.signature','SYNTHETIC_MFA','SYNTHETIC_RECOVERY','SELECT private_value'].some(s=>text.includes(s)))throw Error('DIAGNOSTIC_SENSITIVE_OUTPUT');}
 save(DELIVERY+'/synthetic-files.json',{root:RUN+'/synthetic-diagnostic-results',files:synthetic,id:aggregate(synthetic)});
 if(JSON.stringify(inventory(SOURCE))!==JSON.stringify(rows)||JSON.stringify(inventory(BUILD))!==JSON.stringify(classes))throw Error('DIAGNOSTIC_FROZEN_DRIFT');
 for(const h of history)if(JSON.stringify(inventory(h.root))!==JSON.stringify(h.files))throw Error('DIAGNOSTIC_HISTORY_DRIFT');
 for(const p of provenance)if(hash(p.origin)!==p.sha256)throw Error('DIAGNOSTIC_WORKSPACE_DRIFT');
 verifySealed();
 save(DELIVERY+'/result.json',{status:'PASSED',completedAt:new Date().toISOString(),inputId,compiledId:aggregate(classes),syntax:syntax.length,javaContracts:16,nodeGates:results,syntheticJavaProcesses:16,historyPreserved:true,databaseCommands:0,springServicesStarted:0,frontendsStarted:0,awakeRequested:false,historicalCauseStillUnknown:true});
 console.log(JSON.stringify({status:'PASSED',inputId,classes:classes.length,javaContracts:16,nodeTests:results.reduce((n,r)=>n+r.passed,0),syntheticJavaProcesses:16,syntax:syntax.length}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(e=>{if(fs.existsSync(DELIVERY)&&!fs.existsSync(DELIVERY+'/failure.json'))save(DELIVERY+'/failure.json',{status:'FAILED',at:new Date().toISOString(),name:e.name,message:e.message,noAutomaticRetry:true,databaseCommands:0,servicesStarted:0});console.error(e.message);process.exitCode=1;});

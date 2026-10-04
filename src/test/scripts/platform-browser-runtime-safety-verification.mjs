// One frozen no-database repair verification. No supervisor, database, Spring or proxy invocation.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {adaptBrowserStopChecks} from './platform-browser-runtime-safety.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G';
const RUN=ROOT+'/target/platform-browser-runtime-safety-repair-20260920/a2';
const DELIVERY=ROOT+'/docs/releases/platform-browser-runtime-safety-repair-20260920/a2';
const PREVIOUS=ROOT+'/target/platform-browser-runtime-safety-repair-20260920/a1';
const PREVIOUS_EVIDENCE=ROOT+'/docs/releases/platform-browser-runtime-safety-repair-20260920/a1';
const SOURCE=RUN+'/snapshot',BUILD=RUN+'/classes';
const OLD=ROOT+'/target/platform-b3-preparation-20260920/a1';
const OLD_EVIDENCE=ROOT+'/docs/releases/platform-browser-invitation-20260920/b3';
const OLD_RUNTIME=ROOT+'/target/platform-browser-invitation-20260920/b3';
const NODE='D:/JAVA/NODE/node.exe',JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'),hash=p=>sha(fs.readFileSync(p)),json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
function files(root){return fs.readdirSync(root,{withFileTypes:true}).flatMap(e=>{if(e.isSymbolicLink())throw Error('RUNTIME_SOURCE_LINK');const p=root+'/'+e.name;return e.isDirectory()?files(p):[p];}).sort();}
const inventory=root=>files(root).map(p=>({path:path.relative(root,p).replaceAll('\\','/'),sha256:hash(p)}));
const aggregate=rows=>sha(rows.map(r=>r.path+'\0'+r.sha256+'\n').join(''));
const ownScripts=['platform-b1-batch.mjs','platform-b1-prepare.mjs','platform-browser-runtime-safety.mjs','platform-browser-runtime-safety.test.mjs','platform-browser-runtime-safety-verification.mjs'];
async function main(){
 if(process.version!=='v24.12.0'||process.cwd().replaceAll('\\','/')!==ROOT||process.argv.length!==3||process.argv[2]!=='no-database-runtime-safety-only')throw Error('RUNTIME_CONTEXT');
 if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('RUNTIME_OUTPUT_EXISTS');
 const {verifySealed}=await import(pathToFileURL(OLD+'/snapshot/platform-b1-prepare.mjs').href),old=verifySealed();
 if(old.id!=='8360497c422294d418b7667254b80634724124cf819f4583e8896732428eef14'||hash(OLD_EVIDENCE+'/environment-final.json')!=='4ff025df4a5f9ff729d5d6faacbcba3c81ecd9c134397f069432a5c4a111cbfc')throw Error('RUNTIME_B3_BASE_DRIFT');
 const previous=json(PREVIOUS_EVIDENCE+'/input.json');
 if(previous.id!=='234c7cc6cea20521da48ff25d7d88802c4ec99aa2db31dafb145621b1aa1c4ec'||hash(PREVIOUS_EVIDENCE+'/failure.json')!=='96c19bcabf0353c349df63d10efdc19e44da089f9e9965fa0736cacecb8ea242')throw Error('RUNTIME_A1_BASE_DRIFT');
 const history=[OLD,OLD_EVIDENCE,OLD_RUNTIME,PREVIOUS,PREVIOUS_EVIDENCE].map(root=>({root,files:inventory(root)}));
 if(fs.readdirSync(OLD_RUNTIME+'/private').length)throw Error('RUNTIME_PRIVATE_CHANGED');
 const content=new Map(),provenance=[];
 for(const f of old.files){if(['compile.args','runtime.args'].includes(f.path))continue;const origin=OLD+'/snapshot/'+f.path,bytes=fs.readFileSync(origin);if(sha(bytes)!==f.sha256)throw Error('RUNTIME_BASE_FILE_DRIFT');content.set(f.path,bytes);provenance.push({path:f.path,origin,sha256:f.sha256,mode:'UNCHANGED_B3_TEST_SNAPSHOT'});}
 for(const [folder,names]of [['src/test/scripts/',ownScripts],['src/test/tools/browseracceptance/java/com/wms/browseracceptance/',['PlatformJvmEntryProbe.java']]])for(const name of names){
  const origin=ROOT+'/'+folder+name,stat=fs.lstatSync(origin);if(!stat.isFile()||stat.isSymbolicLink())throw Error('RUNTIME_SOURCE_NOT_REGULAR');
  const bytes=fs.readFileSync(origin);content.set(name,bytes);const i=provenance.findIndex(r=>r.path===name);if(i>=0)provenance.splice(i,1);provenance.push({path:name,origin,sha256:sha(bytes),mode:'AUTHORIZED_RUNTIME_SAFETY_CHANGE'});
 }
 content.set('platform-b1-environment.mjs',Buffer.from(adaptBrowserStopChecks(content.get('platform-b1-environment.mjs').toString())));
 // a2 authorizes only one fixture-order insertion, not runtime logic or assertion changes.
 for(const [name,bytes]of content){
  const approved=previous.files.find(f=>f.path===name);
  if(!approved||hash(PREVIOUS+'/snapshot/'+name)!==approved.sha256)throw Error('RUNTIME_A1_FILE_DRIFT');
  if(name==='platform-browser-runtime-safety-verification.mjs')continue;
  if(name==='platform-browser-runtime-safety.test.mjs'){
   const original=fs.readFileSync(PREVIOUS+'/snapshot/'+name,'utf8');
   const from='async started(){const expected=environment.configure(CLUSTER);';
   const to="async started(){await environment.inspect('synthetic-before-launch');const expected=environment.configure(CLUSTER);";
   if(original.split(from).length!==2||original.replace(from,to)!==bytes.toString())throw Error('RUNTIME_FIXTURE_SCOPE_DRIFT');
  }else if(sha(bytes)!==approved.sha256)throw Error('RUNTIME_LOGIC_OR_ASSERTION_CHANGED');
 }
 const envRow=provenance.find(r=>r.path==='platform-b1-environment.mjs');envRow.mode='EXACT_POSTSTOP_ADAPTATION';envRow.sealedSha256=sha(content.get(envRow.path));
 const {requireContents}=await import(pathToFileURL(OLD+'/snapshot/platform-b1-catalog.mjs').href);requireContents(content);
 const gates=[...old.gates,['platform-b1-catalog.test.mjs',13],['platform-b1-startup-security.test.mjs',25],['platform-backend-diagnostic.test.mjs',38],['platform-browser-runtime-safety.test.mjs',35]];
 for(const [name]of gates.slice(0,-1))if(sha(content.get(name))!==old.files.find(r=>r.path===name).sha256)throw Error('RUNTIME_EXISTING_ASSERTIONS_CHANGED');
 for(const name of ['PlatformB1Contract.java','PlatformB1Harness.java','platform-process-ancestry.mjs','platform-process-lifecycle.mjs','platform-launch-preflight.mjs'])if(sha(content.get(name))!==old.files.find(r=>r.path===name).sha256)throw Error('RUNTIME_SHARED_OR_HARNESS_CHANGED');
 // Preserve everything through the pre-stop observation entry; only collect full proof for later use.
 const before=fs.readFileSync(OLD+'/snapshot/platform-b1-environment.mjs','utf8').replaceAll('\r\n','\n');
 if(!before.includes('assertOwned(expected,value,pinned)')||!content.get('platform-b1-environment.mjs').toString().includes('assertOwned(expected,value,pinned)'))throw Error('RUNTIME_PRESTOP_GUARD_MISSING');
 for(const row of provenance)if(hash(row.origin)!==row.sha256)throw Error('RUNTIME_SOURCE_CHANGED');
 for(const row of old.dependencies)if(hash(row.location)!==row.sha256)throw Error('RUNTIME_DEPENDENCY_DRIFT');
 fs.mkdirSync(SOURCE,{recursive:true});fs.mkdirSync(DELIVERY,{recursive:true});
 for(const [name,bytes]of content){fs.mkdirSync(path.dirname(SOURCE+'/'+name),{recursive:true});fs.writeFileSync(SOURCE+'/'+name,bytes,{flag:'wx'});}
 const classpath=[BUILD,ROOT+'/target/platform-db-acceptance-20260919/r11/build/a1/classes',...old.dependencies.map(d=>d.location)].join(';');
 fs.writeFileSync(SOURCE+'/runtime.args','-cp\n'+JSON.stringify(classpath)+'\n',{flag:'wx'});
 fs.writeFileSync(SOURCE+'/compile.args',['-proc:none','-encoding','UTF-8','-cp',JSON.stringify(classpath),'-d',JSON.stringify(BUILD),...[...content.keys()].filter(n=>n.endsWith('.java')).map(n=>JSON.stringify(SOURCE+'/'+n))].join('\n'),{flag:'wx'});
 const rows=inventory(SOURCE),inputId=aggregate(rows),syntax=rows.filter(r=>/\.(mjs|ps1)$/.test(r.path)).map(r=>r.path);
 save(DELIVERY+'/input.json',{id:inputId,createdAt:new Date().toISOString(),source:SOURCE,build:BUILD,files:rows,provenance,history,originalInputId:old.id,previousRepairInputId:previous.id,fixtureOnlyChangeVerified:true,products:old.products,dependencies:old.dependencies,externalTools:old.externalTools,syntax,gates,javaContracts:16,budgets:{syntax:15000,compile:300000,java:60000,nodeSuite:120000,syntheticJava:10000,pipeDrain:1000},scope:'FIXTURE_PREFLIGHT_ORDER_ONLY_FULL_REGRESSION',runtimeStartAuthorized:false,awakeRequested:false});
 const {runProcess}=await import(pathToFileURL(SOURCE+'/platform-process-lifecycle.mjs').href);
 async function run(label,exe,args,budget){
  const r=await runProcess(exe,args,{cwd:ROOT,timeoutMs:budget,env:{...process.env,pnpm_config_verify_deps_before_run:'error'},outputFile:DELIVERY+'/'+label+'.log'});
  const {output,...receipt}=r;const passed=r.exited&&r.exitCode===0&&r.outputComplete&&r.terminationConfirmed&&!r.timedOut&&!r.aborted&&!r.spawnError&&r.elapsedMs<=budget;
  save(DELIVERY+'/'+label+'.command.json',{label,exe,args,budgetMs:budget,...receipt,status:passed?'PASSED':'FAILED',logSha256:hash(DELIVERY+'/'+label+'.log')});
  if(!passed)throw Error('RUNTIME_COMMAND_FAILED '+label);console.log(label+' PASSED');return fs.readFileSync(DELIVERY+'/'+label+'.log','utf8');
 }
 for(const name of syntax){if(name.endsWith('.mjs'))await run('syntax-'+name,NODE,['--check',SOURCE+'/'+name],15000);else await run('syntax-'+name,'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',['-NoProfile','-NonInteractive','-Command',"$t=$null;$e=$null;[void][Management.Automation.Language.Parser]::ParseFile('"+SOURCE+'/'+name+"',[ref]$t,[ref]$e);if($e.Count){$e|ForEach-Object {$_.ErrorId};exit 1}"],15000);}
 fs.mkdirSync(BUILD);await run('independent-javac',JAVA.replace('/java.exe','/javac.exe'),['@'+SOURCE+'/compile.args'],300000);
 const classes=inventory(BUILD);save(DELIVERY+'/compiled.json',{id:aggregate(classes),inputId,files:classes,productClassesRecompiled:false});
 const java=await run('java-contracts',JAVA,['@'+SOURCE+'/runtime.args','com.wms.browseracceptance.PlatformB1Contract'],60000);if(!java.includes('B1_NO_DATABASE_CONTRACTS_PASSED=16'))throw Error('RUNTIME_JAVA_COVERAGE');
 const results=[];
 for(const [script,count]of gates){const output=await run('gate-'+script,NODE,['--test','--test-reporter=tap',SOURCE+'/'+script],120000);const n=k=>Number(output.match(new RegExp('^# '+k+' (\\d+)\\r?$','m'))?.[1]);const r={script,expected:count,tests:n('tests'),passed:n('pass'),failed:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),todo:n('todo')};save(DELIVERY+'/assessment-'+script+'.json',r);results.push(r);if(r.tests!==count||r.passed!==count||[r.failed,r.skipped,r.cancelled,r.todo].some(v=>v!==0))throw Error('RUNTIME_COVERAGE '+script);}
 const syntheticRoots=['synthetic-diagnostic-results','synthetic-runtime-safety-results'];
 for(const root of syntheticRoots){const synthetic=inventory(RUN+'/'+root);for(const file of synthetic){const text=fs.readFileSync(RUN+'/'+root+'/'+file.path,'utf8');if(['SYNTHETIC_PASSWORD_DO_NOT_PERSIST','eyJsynthetic.payload.signature','SYNTHETIC_MFA','SYNTHETIC_RECOVERY','SELECT private_value','SYNTHETIC_ARGUMENT_DO_NOT_LOG'].some(s=>text.includes(s)))throw Error('RUNTIME_SENSITIVE_OUTPUT');}save(DELIVERY+'/'+root+'.json',{root:RUN+'/'+root,files:synthetic,id:aggregate(synthetic)});}
 if(JSON.stringify(inventory(SOURCE))!==JSON.stringify(rows)||JSON.stringify(inventory(BUILD))!==JSON.stringify(classes))throw Error('RUNTIME_FROZEN_DRIFT');
 for(const h of history)if(JSON.stringify(inventory(h.root))!==JSON.stringify(h.files))throw Error('RUNTIME_HISTORY_DRIFT');
 for(const p of provenance)if(hash(p.origin)!==p.sha256)throw Error('RUNTIME_WORKSPACE_DRIFT');
 verifySealed();
 save(DELIVERY+'/result.json',{status:'PASSED',completedAt:new Date().toISOString(),inputId,compiledId:aggregate(classes),syntax:syntax.length,javaContracts:16,nodeGates:results,syntheticJavaProcesses:19,historyPreserved:true,databaseCommands:0,springServicesStarted:0,frontendsStarted:0,awakeRequested:false,realStartupFixNotProven:true,b2CauseStillUnknown:true,b3StatusUnchanged:true});
 console.log(JSON.stringify({status:'PASSED',inputId,classes:classes.length,javaContracts:16,nodeTests:results.reduce((n,r)=>n+r.passed,0),syntheticJavaProcesses:19,syntax:syntax.length}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(e=>{if(fs.existsSync(DELIVERY)&&!fs.existsSync(DELIVERY+'/failure.json'))save(DELIVERY+'/failure.json',{status:'FAILED',at:new Date().toISOString(),name:e.name,message:e.message,noAutomaticRetry:true,databaseCommands:0,servicesStarted:0});console.error(e.message);process.exitCode=1;});

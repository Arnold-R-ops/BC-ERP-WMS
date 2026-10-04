import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {hash,json,files,aggregate,assertSame,backendCheck} from './platform-frontend-f1-build.mjs';
import {SHARED_FILES,SHARED_EDGES,hashBytes,preflightShared,requireContents} from './platform-b1-catalog.mjs';
import * as B from './platform-b1-binding.mjs';
import {backendArguments,adaptBrowserStopChecks} from './platform-browser-runtime-safety.mjs';
export const sealedBackendCommand=()=>[B.JAVA,...backendArguments(B,'<verified-public-cluster-id>')];
const OLD_DELIVERY=B.ROOT+'/docs/releases/platform-browser-invitation-20260919/b1';
const OLD_RUN=B.ROOT+'/target/platform-browser-invitation-20260919/b1';
const OLD_PREPARATION=B.ROOT+'/target/platform-b1-preparation-20260920/a1';
const OLD_PREPARATION_DELIVERY=B.ROOT+'/docs/releases/platform-b1-preparation-20260920/a1';
const R11_MANIFEST=B.ROOT+'/docs/releases/platform-db-acceptance-20260919/r11/backend-input-manifest.json';
const R11_MANIFEST_HASH='6faefc5ab260931cbc1d378fbef826f82a4c3b1ef391dd62220b7c427aa9c228';
const gates=[['platform-process-lifecycle.test.mjs',35],['platform-launch-preflight.test.mjs',40],['platform-process-ancestry.test.mjs',33],['platform-process-query.test.mjs',16],['platform-b1-environment.test.mjs',12],['platform-b1.test.mjs',19]];
const inventory=root=>files(root).map(p=>({path:path.relative(root,p).replaceAll('\\','/'),sha256:hash(p)}));
function preserveHistory(history,exact=false){for(const h of history){
 for(const f of h.files)if(hash(h.root+'/'+f.path)!==f.sha256)throw Error('B1_HISTORY_DRIFT '+f.path);
 if(exact&&JSON.stringify(inventory(h.root))!==JSON.stringify(h.files))throw Error('B1_HISTORY_INVENTORY_DRIFT');
}}
function requireNeverStarted(){for(const p of [B.RUN,B.DATA,B.PRIVATE,B.DELIVERY+'/start-attempt.json',B.DELIVERY+'/environment-ready.json'])if(fs.existsSync(p))throw Error('B4_RUNTIME_ALREADY_ATTEMPTED');}
export function verifyProducts(){
 const f=B.ROOT+'/docs/releases/platform-frontend-acceptance-20260919/f3',m=json(f+'/source-manifest.json'),a=json(f+'/artifact-manifest.json');
 if(m.id!=='ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d'||a.aggregateSha256!=='7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c')throw Error('B1_FRONTEND_INPUT_DRIFT');
 assertSame(B.FRONTEND,m.files);assertSame(B.ROOT+'/frontend',m.files);
 const dist=files(B.FRONTEND+'/dist').map(p=>({path:path.relative(B.FRONTEND+'/dist',p).replaceAll('\\','/'),sha256:hash(p),bytes:fs.statSync(p).size}));
 if(JSON.stringify(dist)!==JSON.stringify(a.files)||aggregate(dist)!==a.aggregateSha256)throw Error('B1_DIST_DRIFT');
 return {frontend:m.id,dist:a.aggregateSha256,backend:backendCheck()};
}
export const save=(p,v)=>fs.writeFileSync(p,JSON.stringify(v,null,2)+'\n',{flag:'wx'});
export function verifySealed(){
 const m=json(B.DELIVERY+'/input.json');verifyProducts();
 if(aggregate(m.files)!==m.id||JSON.stringify(inventory(B.SOURCE))!==JSON.stringify(m.files))throw Error('B1_SOURCE_INVENTORY_DRIFT');
 requireContents(new Map(SHARED_FILES.map(n=>[n,fs.readFileSync(B.SOURCE+'/'+n)])));
 if(hash(R11_MANIFEST)!==R11_MANIFEST_HASH)throw Error('B1_R11_MANIFEST_DRIFT');
 preserveHistory(m.preservedHistory);
 for(const t of m.externalTools)if(hash(t.path)!==t.sha256)throw Error('B1_TOOL_DRIFT');
 for(const j of m.dependencies)if(hash(j.location)!==j.sha256)throw Error('B1_DEPENDENCY_DRIFT');
 if(fs.existsSync(B.DELIVERY+'/compiled.json')){const c=json(B.DELIVERY+'/compiled.json');if(aggregate(c.files)!==c.id||JSON.stringify(inventory(B.BUILD))!==JSON.stringify(c.files))throw Error('B1_CLASS_DRIFT');}
 return m;
}
function replaceRequired(text,from,to){if(!text.includes(from))throw Error('B1_TEMPLATE_MISMATCH '+from);return text.replaceAll(from,to);}
function derive(text){for(const [a,b]of [['com.wms.platformverification','com.wms.browseracceptance'],['PlatformR2Isolation','PlatformB1Isolation'],['PlatformR2DatabasePreparer','PlatformB1DatabasePreparer'],['p3.r2','p3.b4'],['P3_R2','P3_B4'],['p3_r11','p3_b4'],['55449','55459'],['p3_m1_platform_release_r11','p3_platform_browser_b4'],['platform-db-acceptance-20260919/r11','platform-browser-invitation-20260920/b4']])text=text.replaceAll(a,b);return text;}
// Explicit standalone tool sources: these main/contract programs are not business JUnit sources.
export const BROWSER_JAVA_SOURCE_DIR='src/test/tools/browseracceptance/java/com/wms/browseracceptance/';
export function browserJavaSources(template){
 let iso=derive(template('java/com/wms/platformverification/PlatformR2Isolation.java','PlatformB1Isolation.java'));
 iso=replaceRequired(iso,'    public static final String UPGRADE = "p3_m1_platform_upgrade_r11";','');
 iso=replaceRequired(iso,'Set.of(MAIN, UPGRADE)','Set.of(MAIN)');
 let prep=derive(template('java/com/wms/platformverification/PlatformR2DatabasePreparer.java','PlatformB1DatabasePreparer.java'));
 prep=prep.split(/\r?\n/).filter(l=>!l.includes('case "upgrade-to-')&&!l.includes('p3_m1_probe')).join('\n');
 prep=replaceRequired(prep,' + " and " + PlatformB1Isolation.UPGRADE','');
 return new Map([['PlatformB1Isolation.java',iso],['PlatformB1DatabasePreparer.java',prep]]);
}
// Only target identity literals may change in these inherited contracts. Never copy prior results.
function rebindContract(text,name){
 if(!['platform-b1.test.mjs','platform-b1-environment.test.mjs','PlatformB1Contract.java'].includes(name))return text;
 for(const [a,b]of [['platform-browser-invitation-20260919/b1','platform-browser-invitation-20260920/b4'],['p3_platform_browser_b1','p3_platform_browser_b4'],['p3_b1_','p3_b4_'],['p3.b1.','p3.b4.'],['fixed b1 identity','fixed b4 identity']])text=text.replaceAll(a,b);
 return text;
}
async function prepare(){
 if(process.version!=='v24.12.0'||process.cwd().replaceAll('\\','/')!==B.ROOT||process.argv.length!==2)throw Error('B1_PREP_CONTEXT');
 if(fs.existsSync(B.PREPARATION)||fs.existsSync(B.DELIVERY))throw Error('B1_PREPARATION_ROOT_EXISTS');
 requireNeverStarted();
 const preservedHistory=[OLD_RUN,OLD_DELIVERY,OLD_PREPARATION,OLD_PREPARATION_DELIVERY,B.ROOT+'/target/platform-b1-startup-repair-20260920/a1',B.ROOT+'/docs/releases/platform-b1-startup-repair-20260920/a1',B.ROOT+'/target/platform-browser-invitation-20260920/b2',B.ROOT+'/target/platform-b2-preparation-20260920/a1',B.ROOT+'/docs/releases/platform-browser-invitation-20260920/b2',B.ROOT+'/target/platform-backend-diagnostic-repair-20260920/a1',B.ROOT+'/docs/releases/platform-backend-diagnostic-repair-20260920/a1',B.ROOT+'/target/platform-browser-invitation-20260920/b3',B.ROOT+'/target/platform-b3-preparation-20260920/a1',B.ROOT+'/docs/releases/platform-browser-invitation-20260920/b3',B.ROOT+'/target/platform-browser-runtime-safety-repair-20260920/a1',B.ROOT+'/docs/releases/platform-browser-runtime-safety-repair-20260920/a1',B.ROOT+'/target/platform-browser-runtime-safety-repair-20260920/a2',B.ROOT+'/docs/releases/platform-browser-runtime-safety-repair-20260920/a2'].map(root=>({root,files:inventory(root)}));
 const products=verifyProducts();
 if(hash(R11_MANIFEST)!==R11_MANIFEST_HASH)throw Error('B1_R11_MANIFEST_DRIFT');
 const r11=json(R11_MANIFEST);
 for(const t of r11.externalTools)if(hash(t.path)!==t.sha256)throw Error('B1_FIXED_TOOL_DRIFT');
 const deps=r11.testClasspath.map(j=>({...j,location:B.BACKEND_RUN+'/snapshot/maven-repository/'+j.path}));
 for(const j of deps)if(hash(j.location)!==j.sha256)throw Error('B1_DEPENDENCY_DRIFT');
 const legacy=B.BACKEND_RUN+'/snapshot/source/src/test/';
 // Complete explicit dependency preflight before creating either new output root.
 const {content,provenance}=preflightShared({sourceRoot:legacy+'scripts',approvedFiles:r11.groups[0].files});
 function source(relative){
  const origin=B.ROOT+'/'+relative,stat=fs.lstatSync(origin);if(!stat.isFile()||stat.isSymbolicLink())throw Error('B1_SOURCE_NOT_REGULAR');
  const bytes=fs.readFileSync(origin);provenance.push({path:path.basename(relative),origin,sourceSha256:hashBytes(bytes),mode:'WORKSPACE_TEST_TOOL'});return bytes;
 }
 function template(relative,destination){
  const origin=legacy+relative,matches=r11.groups[0].files.filter(f=>f.path==='src/test/'+relative);
  if(matches.length!==1||hash(origin)!==matches[0].sha256)throw Error('B1_TEMPLATE_SOURCE_DRIFT '+relative);
  provenance.push({path:destination,origin,sourceSha256:matches[0].sha256,mode:'DERIVED_R11_TEST_TEMPLATE'});return fs.readFileSync(origin,'utf8');
 }
 const own=['platform-b1-binding.mjs','platform-b1-prepare.mjs','platform-b1-batch.mjs','platform-b1-proxy.mjs','platform-b1.test.mjs','platform-b1-catalog.mjs','platform-b1-catalog.test.mjs','platform-b1-private-acl.ps1','platform-b1-startup-security.mjs','platform-b1-startup-security.test.mjs','platform-backend-diagnostic.mjs','platform-backend-diagnostic.test.mjs','platform-browser-runtime-safety.mjs','platform-browser-runtime-safety.test.mjs'];
 for(const n of own)content.set(n,source('src/test/scripts/'+n));
 // Read-only frontend/backend fingerprint helpers: inert on import; not their original CLI pipeline.
 for(const n of ['platform-frontend-f1-build.mjs','platform-vitest-report.mjs'])content.set(n,source('src/test/scripts/'+n));
 let env=template('scripts/platform-r11-environment.mjs','platform-b1-environment.mjs');
 env=replaceRequired(env,"'./platform-r11-backend-snapshot.mjs'","'./platform-b1-binding.mjs'");
 env=replaceRequired(env,"'./platform-r11-plan.mjs'","'./platform-b1-binding.mjs'");
 env=env.replaceAll('createR11Environment','createB1Environment').replaceAll('p3_r11','p3_b4').replaceAll('55449','55459').replaceAll('R11','B1').replaceAll('r11-only','b4-only');
 content.set('platform-b1-environment.mjs',adaptBrowserStopChecks(env));
 let et=template('scripts/platform-r11-environment.test.mjs','platform-b1-environment.test.mjs');
 et=et.replaceAll('platform-r11-environment','platform-b1-environment').replaceAll('platform-r11-backend-snapshot','platform-b1-binding').replaceAll('createR11Environment','createB1Environment').replaceAll('platform-db-acceptance-20260919/r11','platform-browser-invitation-20260920/b4').replaceAll('55449','55459').replaceAll('p3_r11','p3_b4');content.set('platform-b1-environment.test.mjs',et);
 for(const [name,text]of browserJavaSources(template))content.set(name,text);
 for(const n of ['PlatformB1Harness.java','PlatformB1Contract.java','PlatformBackendDiagnostic.java','PlatformBackendDiagnosticProbe.java','PlatformJvmEntryProbe.java'])content.set(n,source(BROWSER_JAVA_SOURCE_DIR+n));
 content.set('platform-b1-isolation.properties','spring.flyway.enabled=false\nspring.sql.init.mode=never\nspring.jpa.hibernate.ddl-auto=validate\nlogging.level.root=OFF\nspring.main.banner-mode=off\nspring.devtools.restart.enabled=false\n');
 requireContents(content);
 const oldInput=json(OLD_DELIVERY+'/input.json');
 const contractBindings=[...gates.map(([n])=>n),'PlatformB1Contract.java'].map(n=>{
  const previous=oldInput.files.find(f=>f.path===n),current=hashBytes(content.get(n));
  if(!previous||hash(OLD_RUN+'/snapshot/'+n)!==previous.sha256||hashBytes(rebindContract(fs.readFileSync(OLD_RUN+'/snapshot/'+n,'utf8'),n))!==current)throw Error('B4_ORIGINAL_ASSERTIONS_DRIFT '+n);
  return {path:n,sha256:current,previousSha256:previous.sha256,previousInputId:oldInput.id,change:current===previous.sha256?'UNCHANGED':'TARGET_BINDING_ONLY'};
 });
 for(const p of provenance)if(hash(p.origin)!==p.sourceSha256)throw Error('B1_SOURCE_CHANGED_DURING_PREFLIGHT');
 fs.mkdirSync(B.SOURCE,{recursive:true});fs.mkdirSync(B.DELIVERY,{recursive:true});
 for(const[n,s]of content){fs.mkdirSync(path.dirname(B.SOURCE+'/'+n),{recursive:true});fs.writeFileSync(B.SOURCE+'/'+n,s,{flag:'wx'});}
 const cp=[B.BUILD,B.BACKEND_CLASSES,...deps.map(d=>d.location)].join(';');
 fs.writeFileSync(B.SOURCE+'/runtime.args','-cp\n'+JSON.stringify(cp)+'\n',{flag:'wx'});
 const javaSources=[...content.keys()].filter(n=>n.endsWith('.java')).map(n=>B.SOURCE+'/'+n);
 fs.writeFileSync(B.SOURCE+'/compile.args',['-proc:none','-encoding','UTF-8','-cp',JSON.stringify(cp),'-d',JSON.stringify(B.BUILD),...javaSources.map(s=>JSON.stringify(s))].join('\n'),{flag:'wx'});
 const rows=inventory(B.SOURCE);
 for(const r of rows){const p=provenance.find(p=>p.path===r.path);if(p)p.sealedSha256=r.sha256;else provenance.push({path:r.path,origin:'platform-b1-prepare.mjs explicit generated content',mode:'GENERATED_TEST_CONFIGURATION',sealedSha256:r.sha256});}
 const syntaxFiles=rows.filter(r=>r.path.endsWith('.mjs')||r.path.endsWith('.ps1')).map(r=>r.path);
 const javaProps=['-Dp3.b4.database-authorized=true','-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55459/'+B.DATABASE];
 save(B.DELIVERY+'/input.json',{id:aggregate(rows),createdAt:new Date().toISOString(),files:rows,products,dependencies:deps,externalTools:r11.externalTools,gates,budgets:B.BUDGETS,
 completeness:{sharedFiles:SHARED_FILES,edges:SHARED_EDGES,provenance,r11Manifest:R11_MANIFEST,r11ManifestSha256:R11_MANIFEST_HASH,preflightBeforeDirectoryCreation:true,noFallback:true,historicalCliExecution:false},
 preservedHistory,contractBindings,syntaxFiles,additionalIntegrityGate:{script:'platform-b1-catalog.test.mjs',expected:13},additionalStartupGate:{script:'platform-b1-startup-security.test.mjs',expected:25},additionalDiagnosticGate:{script:'platform-backend-diagnostic.test.mjs',expected:38},additionalRuntimeSafetyGate:{script:'platform-browser-runtime-safety.test.mjs',expected:35},
 target:{preparation:B.PREPARATION,source:B.SOURCE,build:B.BUILD,evidence:B.DELIVERY,run:B.RUN,directory:B.DATA,database:B.DATABASE,roles:[B.ADMIN,B.MIG,B.APP],ports:B.PORTS},
 commands:{compile:[B.JAVA.replace('/java.exe','/javac.exe'),'@'+B.SOURCE+'/compile.args'],guard:[B.JAVA,'@'+B.SOURCE+'/runtime.args','com.wms.browseracceptance.PlatformB1Contract'],
 supervisor:[B.NODE,B.SOURCE+'/platform-b1-batch.mjs','start',B.AUTH],migrate:[B.JAVA,'-Dp3.b4.migration-authorized=true',javaProps[1],'-Dp3.b4.cluster-id=<verified-public-cluster-id>','@'+B.SOURCE+'/runtime.args','com.wms.browseracceptance.PlatformB1DatabasePreparer','prepare-release'],
 backend:sealedBackendCommand(),proxy:[B.NODE,B.SOURCE+'/platform-b1-proxy.mjs']},awakeAuthorized:false,
 derivation:'r11 frozen test-layer templates only; b4 identity substitution, single database, no probe table or upgrade commands. Legacy tool/class names retained; no b1 runtime reuse. Product classes/resources unchanged.'});
 const results=[];
 const {runProcess}=await import(pathToFileURL(B.SOURCE+'/platform-process-lifecycle.mjs').href);
 verifySealed();
 async function run(label,exe,args,budget){
  const r=await runProcess(exe,args,{cwd:B.ROOT,env:{...process.env,pnpm_config_verify_deps_before_run:'error'},timeoutMs:budget,outputFile:B.DELIVERY+'/'+label+'.log'});const {output,...receipt}=r;
  const good=r.exited&&r.exitCode===0&&!r.timedOut&&!r.aborted&&!r.spawnError&&r.outputComplete&&r.terminationConfirmed&&r.elapsedMs<=budget;
  save(B.DELIVERY+'/'+label+'.json',{executable:exe,args,cwd:B.ROOT,budgetMs:budget,...receipt,status:good?'PASSED':'FAILED',logSha256:hash(B.DELIVERY+'/'+label+'.log')});
  if(!good)throw Error('B1_PREPARATION_COMMAND_FAILED '+label);console.log(label+' PASSED');return fs.readFileSync(B.DELIVERY+'/'+label+'.log','utf8');
 }
 for(const n of syntaxFiles){
  if(n.endsWith('.mjs'))await run('syntax-'+n,B.NODE,['--check',B.SOURCE+'/'+n],B.BUDGETS.syntaxMs);
  else await run('syntax-'+n,'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe',['-NoProfile','-NonInteractive','-Command',"$tokens=$null; $parseErrors=$null; [void][System.Management.Automation.Language.Parser]::ParseFile('"+B.SOURCE+'/'+n+"',[ref]$tokens,[ref]$parseErrors); if($parseErrors.Count){ $parseErrors | ForEach-Object { Write-Output $_.Message }; exit 1 }; Write-Output 'POWERSHELL_SYNTAX_PASSED'"],B.BUDGETS.syntaxMs);
 }
 async function nodeGate(script,count){const output=await run('gate-'+script,B.NODE,['--test','--test-reporter=tap',B.SOURCE+'/'+script],B.BUDGETS.nodeSuiteMs);
  const n=k=>Number(output.match(new RegExp('^# '+k+' (\\d+)\\r?$','m'))?.[1]);
  const row={script,expected:count,tests:n('tests'),passed:n('pass'),failed:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),todo:n('todo')};
  save(B.DELIVERY+'/assessment-'+script+'.json',row);
  if(row.tests!==count||row.passed!==count||row.failed!==0||row.skipped!==0||row.cancelled!==0||row.todo!==0)throw Error('B1_NODE_GUARD_COVERAGE '+script);
  return row;
 }
 const integrity=await nodeGate('platform-b1-catalog.test.mjs',13);
 const startup=await nodeGate('platform-b1-startup-security.test.mjs',25);
 fs.mkdirSync(B.BUILD);
 await run('independent-javac',B.JAVA.replace('/java.exe','/javac.exe'),['@'+B.SOURCE+'/compile.args'],B.BUDGETS.compileMs);
 const guard=await run('java-no-database',B.JAVA,['@'+B.SOURCE+'/runtime.args','com.wms.browseracceptance.PlatformB1Contract'],B.BUDGETS.javaGuardsMs);
 if(!guard.includes('B1_NO_DATABASE_CONTRACTS_PASSED=16'))throw Error('B1_JAVA_GUARD_COVERAGE');
 const diagnostic=await nodeGate('platform-backend-diagnostic.test.mjs',38);
 const runtimeSafety=await nodeGate('platform-browser-runtime-safety.test.mjs',35);
 for(const [script,count]of gates)results.push(await nodeGate(script,count));
 verifySealed();preserveHistory(preservedHistory,true);requireNeverStarted();
 for(const p of provenance)if(p.sourceSha256&&hash(p.origin)!==p.sourceSha256)throw Error('B1_ORIGIN_CHANGED_DURING_GATES');
 const compiled=inventory(B.BUILD);
 save(B.DELIVERY+'/compiled.json',{id:aggregate(compiled),files:compiled,productClassesRecompiled:false});
 save(B.DELIVERY+'/preparation-result.json',{status:'PASSED',completedAt:new Date().toISOString(),inputId:aggregate(rows),compiledId:aggregate(compiled),nodeGates:results,javaContracts:16,catalogContracts:13,startupContracts:25,diagnosticContracts:38,runtimeSafetyContracts:35,integrity,startup,diagnostic,runtimeSafety,syntaxFiles,historyPreserved:true,databasesStarted:0,servicesStarted:0,awakeRequested:false});
 console.log(JSON.stringify({status:'PASSED_PREPARATION',nodeTests:results.reduce((n,r)=>n+r.passed,0),javaContracts:16,compiled:compiled.length}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))prepare().catch(e=>{if(fs.existsSync(B.DELIVERY))save(B.DELIVERY+'/preparation-failure.json',{status:'FAILED',name:e.name,message:e.message,databaseStarted:false,automaticRetry:false});console.error(e.message);process.exitCode=1;});

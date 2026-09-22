// One T1 no-database verification, not a service launcher or a general release framework.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
import {verifyProducts,browserJavaSources,BROWSER_JAVA_SOURCE_DIR} from './platform-b1-prepare.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G';
const RUN=ROOT+'/target/platform-precommit-closeout-20260921/a2';
const OUT=ROOT+'/docs/releases/platform-precommit-closeout-20260921/a2';
const SOURCE=RUN+'/maven-source',SNAPSHOT=RUN+'/snapshot',BUILD=RUN+'/maven-build',CLASSES=RUN+'/classes';
const R11=ROOT+'/target/platform-db-acceptance-20260919/r11';
const B4=ROOT+'/target/platform-b4-preparation-20260920/a1';
const R11_RECORD=ROOT+'/docs/releases/platform-db-acceptance-20260919/r11';
const B4_RECORD=ROOT+'/docs/releases/platform-browser-invitation-20260920/b4';
const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe',NODE='D:/JAVA/NODE/node.exe';
const MAVEN=R11+'/snapshot/apache-maven-3.9.9',REPO=RUN+'/maven-repository';
const LIMITS=Object.freeze({version:15000,syntax:15000,mavenCompile:300000,javac:300000,javaContracts:60000,nodeSuite:120000});
const JAVA_NAMES=['PlatformB1Harness.java','PlatformB1Contract.java','PlatformBackendDiagnostic.java','PlatformBackendDiagnosticProbe.java','PlatformJvmEntryProbe.java'];
const GATES=[['platform-browser-java-sources.test.mjs',12],['platform-b1-catalog.test.mjs',13],['platform-b1-startup-security.test.mjs',25],['platform-backend-diagnostic.test.mjs',38],['platform-browser-runtime-safety.test.mjs',35],['platform-b1.test.mjs',19],['platform-b1-environment.test.mjs',12]];
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'),hash=p=>sha(fs.readFileSync(p)),json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const save=(name,data)=>fs.writeFileSync(OUT+'/'+name,JSON.stringify(data,null,2)+'\n',{flag:'wx'});
function files(root){return fs.readdirSync(root,{withFileTypes:true}).flatMap(e=>{if(e.isSymbolicLink())throw Error('T1_LINK_REFUSED');const p=root+'/'+e.name;return e.isDirectory()?files(p):[p];}).sort();}
const inventory=root=>files(root).map(p=>({path:path.relative(root,p).replaceAll('\\','/'),sha256:hash(p)}));
const aggregate=rows=>sha(rows.map(r=>r.path+'\0'+r.sha256+'\n').join(''));
function same(actual,expected,label){if(JSON.stringify(actual)!==JSON.stringify(expected))throw Error('T1_DRIFT_'+label);}
function copy(source,destination,digest){if(hash(source)!==digest)throw Error('T1_COPY_INPUT_DRIFT');fs.mkdirSync(path.dirname(destination),{recursive:true});fs.copyFileSync(source,destination,fs.constants.COPYFILE_EXCL);if(hash(destination)!==digest)throw Error('T1_COPY_OUTPUT_DRIFT');}
function environment(){const env={};for(const k of ['SystemRoot','WINDIR','COMSPEC','USERPROFILE','APPDATA','LOCALAPPDATA','TEMP','TMP','PATHEXT','Path']){const actual=Object.keys(process.env).find(n=>n.toLowerCase()===k.toLowerCase());if(actual)env[k]=process.env[actual];}return {...env,JAVA_HOME:path.dirname(path.dirname(JAVA)),CI:'true',pnpm_config_verify_deps_before_run:'error'};}
let step='preflight';
async function command(label,exe,args,budget){
 step=label;const result=await runProcess(exe,args,{cwd:ROOT,env:environment(),timeoutMs:budget,outputFile:OUT+'/'+label+'.log'});
 const {output,...receipt}=result,passed=result.exited&&result.exitCode===0&&result.outputComplete&&result.terminationConfirmed&&!result.spawnError&&!result.timedOut&&!result.aborted&&result.elapsedMs<=budget;
 save(label+'.json',{executable:exe,args,cwd:ROOT,budgetMs:budget,...receipt,status:passed?'PASSED':'FAILED',logSha256:hash(OUT+'/'+label+'.log')});
 if(!passed)throw Error('T1_COMMAND_FAILED_'+label);
 console.log(label+' PASSED');return fs.readFileSync(OUT+'/'+label+'.log','utf8');
}
async function main(){
 if(process.version!=='v24.12.0'||process.cwd().replaceAll('\\','/')!==ROOT||process.argv.length!==3||process.argv[2]!=='no-database-only')throw Error('T1_CONTEXT_REFUSED');
 if(fs.existsSync(RUN)||fs.existsSync(OUT))throw Error('T1_OUTPUT_EXISTS_NO_RETRY');
 const products=verifyProducts(),r11=json(R11_RECORD+'/backend-input-manifest.json'),b4=json(B4_RECORD+'/input.json'),b4Classes=json(B4_RECORD+'/compiled.json');
 if(b4.id!=='da326580be0ef0d1f5787e06148fae89fe1f62e79095286b979dabe3087b55b2')throw Error('T1_B4_INPUT_DRIFT');
 same(inventory(B4+'/snapshot'),b4.files,'B4_SOURCE');same(inventory(B4+'/classes'),b4Classes.files,'B4_CLASSES');
 const history=[ROOT+'/docs/PLATFORM_B4_INVITATION_ACCEPTANCE_2026-09-20.md',B4_RECORD+'/input.json',B4_RECORD+'/compiled.json',B4_RECORD+'/acceptance-assessment.json',B4_RECORD+'/environment-final.json',R11_RECORD+'/backend-input-manifest.json',R11_RECORD+'/backend-build.json'].map(p=>({path:p,sha256:hash(p)}));
 for(const base of ['target','docs/releases']){const previous=ROOT+'/'+base+'/platform-precommit-closeout-20260921/a1';for(const file of files(previous))history.push({path:file,sha256:hash(file)});}
 const inherited=[{path:'src/main/java/com/wms/system/signup/service/TenantProvisioningService.java',sha256:'69e9de7a590bffbd2003860aecd9e25d75ab107b6a57812f98a664d53cde4d8b'},...json(ROOT+'/docs/releases/platform-frontend-acceptance-20260919/f3/source-manifest.json').files.filter(f=>f.path==='src/test/addressLength.audit.test.tsx').map(f=>({...f,path:'frontend/'+f.path}))];
 if(inherited.length!==2)throw Error('T1_INHERITED_INVENTORY');for(const f of inherited)if(hash(ROOT+'/'+f.path)!==f.sha256)throw Error('T1_INHERITED_DRIFT');
 const currentJava=files(ROOT+'/src/test/java').filter(p=>p.endsWith('.java'));
 const junit=currentJava.filter(p=>/^\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b/m.test(fs.readFileSync(p,'utf8'))).map(p=>({path:p.slice(ROOT.length+1),sha256:hash(p)}));
 const javaHashes={};for(const name of JAVA_NAMES){
  const origin=ROOT+'/'+BROWSER_JAVA_SOURCE_DIR+name,row=b4.files.find(f=>f.path===name);
  if(!row||hash(origin)!==row.sha256||fs.existsSync(ROOT+'/src/test/java/com/wms/browseracceptance/'+name))throw Error('T1_RELOCATION_DRIFT');
  if(/^\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b/m.test(fs.readFileSync(origin,'utf8')))throw Error('T1_FORMAL_TEST_RELOCATION_REFUSED');javaHashes[name]=row.sha256;
 }
 const templates=new Map();for(const name of ['PlatformR2Isolation.java','PlatformR2DatabasePreparer.java']){
  const relative='src/test/java/com/wms/platformverification/'+name,row=r11.groups[0].files.find(f=>f.path===relative);
  if(!row||hash(ROOT+'/'+relative)!==row.sha256||hash(r11.groups[0].location+'/'+relative)!==row.sha256)throw Error('T1_TEMPLATE_DRIFT');templates.set(name,fs.readFileSync(ROOT+'/'+relative));
 }
 const generated=browserJavaSources(source=>{const value=templates.get(path.basename(source));if(!value)throw Error('T1_TEMPLATE_MISSING');return value.toString();});
 const generatedHashes={};for(const [name,text]of generated){const expected=b4.files.find(f=>f.path===name);if(!expected||sha(text)!==expected.sha256)throw Error('T1_GENERATED_SAFETY_DRIFT');generatedHashes[name]=expected.sha256;}
 const productRows=r11.groups[0].files.filter(f=>f.path==='pom.xml'||f.path.startsWith('src/main/'));
 const ordinary=[...productRows,...currentJava.map(p=>({path:p.slice(ROOT.length+1),sha256:hash(p)})),...files(ROOT+'/src/test/resources').map(p=>({path:p.slice(ROOT.length+1),sha256:hash(p)}))];
 const own=['platform-b1-prepare.mjs','platform-browser-java-sources.test.mjs','platform-precommit-closeout.mjs','platform-backend-diagnostic-verification.mjs','platform-browser-runtime-safety-verification.mjs'];
 const sourceProvenance=[...ordinary.map(f=>({...f,origin:ROOT+'/'+f.path})),...JAVA_NAMES.map(name=>({path:BROWSER_JAVA_SOURCE_DIR+name,sha256:javaHashes[name],origin:ROOT+'/'+BROWSER_JAVA_SOURCE_DIR+name})),...own.map(name=>({path:'src/test/scripts/'+name,sha256:hash(ROOT+'/src/test/scripts/'+name),origin:ROOT+'/src/test/scripts/'+name}))];
 for(const g of r11.groups.filter(g=>['frozen-maven-cache','maven-distribution'].includes(g.name)))for(const f of g.files)if(hash(g.location+'/'+f.path)!==f.sha256)throw Error('T1_TOOLCHAIN_DRIFT');
 for(const t of r11.externalTools)if(hash(t.path)!==t.sha256)throw Error('T1_EXTERNAL_TOOL_DRIFT');
 fs.mkdirSync(RUN,{recursive:true});fs.mkdirSync(OUT,{recursive:true});
 save('plan.json',{scope:'T1_NO_DATABASE_COMPILE_AND_CONTRACTS',createdAt:new Date().toISOString(),run:RUN,source:SOURCE,snapshot:SNAPSHOT,build:BUILD,classes:CLASSES,budgets:LIMITS,gates:GATES,javaContracts:16,sourceProvenance,inherited,history,products,java:javaHashes,generated:generatedHashes,originalB4InputId:b4.id,originalB4CompiledId:b4Classes.id,noDatabase:true,noServices:true,noAwake:true});
 for(const row of ordinary)copy(ROOT+'/'+row.path,SOURCE+'/'+row.path,row.sha256);
 for(const row of r11.groups.find(g=>g.name==='frozen-maven-cache').files)copy(R11+'/snapshot/maven-repository/'+row.path,REPO+'/'+row.path,row.sha256);
 for(const row of b4.files){if(['compile.args','runtime.args'].includes(row.path))continue;copy(B4+'/snapshot/'+row.path,SNAPSHOT+'/'+row.path,row.sha256);}
 // Only the new frozen copy is updated; accepted b4 snapshots remain byte-identical.
 for(const name of own)fs.writeFileSync(SNAPSHOT+'/'+name,fs.readFileSync(ROOT+'/src/test/scripts/'+name));
 for(const name of JAVA_NAMES)fs.writeFileSync(SNAPSHOT+'/'+name,fs.readFileSync(ROOT+'/'+BROWSER_JAVA_SOURCE_DIR+name));
 fs.mkdirSync(SNAPSHOT+'/templates');for(const [name,bytes]of templates)fs.writeFileSync(SNAPSHOT+'/templates/'+name,bytes,{flag:'wx'});
 for(const [name,text]of generated)fs.writeFileSync(SNAPSHOT+'/'+name,text);
 const cp=[CLASSES,BUILD+'/classes',...r11.testClasspath.map(f=>REPO+'/'+f.path)].join(';');
 const javaNames=[...JAVA_NAMES,...generated.keys()];
 fs.writeFileSync(SNAPSHOT+'/compile.args',['-proc:none','-encoding','UTF-8','-cp',JSON.stringify(cp),'-d',JSON.stringify(CLASSES),...javaNames.map(n=>JSON.stringify(SNAPSHOT+'/'+n))].join('\n'),{flag:'wx'});
 fs.writeFileSync(SNAPSHOT+'/runtime.args','-cp\n'+JSON.stringify(cp)+'\n',{flag:'wx'});
 fs.writeFileSync(SNAPSHOT+'/t1-context.json',JSON.stringify({source:SOURCE,build:BUILD,classes:CLASSES,pomSha256:hash(ROOT+'/pom.xml'),junit,java:javaHashes,generated:generatedHashes})+'\n',{flag:'wx'});
 const sourceRows=inventory(SOURCE),toolRows=inventory(SNAPSHOT),inputId=aggregate([...sourceRows.map(r=>({...r,path:'maven-source/'+r.path})),...toolRows.map(r=>({...r,path:'snapshot/'+r.path}))]);
 const mavenArgs=['-Dmaven.home='+MAVEN,'-Dclassworlds.conf='+MAVEN+'/bin/m2.conf','-Dmaven.multiModuleProjectDirectory='+SOURCE,'-cp',MAVEN+'/boot/*','org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-ntp','-s',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-gs',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-Dmaven.repo.local='+REPO,'-Dwms.build.directory='+BUILD,'-f',SOURCE+'/pom.xml'];
 save('input.json',{id:inputId,source:sourceRows,tools:toolRows,ordinaryJavaSources:currentJava.length,junitSources:junit.length,sourceProvenance,commands:{mavenCompile:[JAVA,...mavenArgs,'test-compile'],standaloneCompile:[JAVA.replace('/java.exe','/javac.exe'),'@'+SNAPSHOT+'/compile.args']},budgets:LIMITS,noSkipOrExcludeProperties:true,unchangedPublicPomSha256:hash(ROOT+'/pom.xml')});
 const version=await command('toolchain-java',JAVA,['-version'],LIMITS.version);if(!version.includes('17.0.17'))throw Error('T1_JAVA_VERSION');
 const mavenVersion=await command('toolchain-maven',JAVA,[...mavenArgs,'--version'],LIMITS.version);if(!mavenVersion.includes('Apache Maven 3.9.9'))throw Error('T1_MAVEN_VERSION');
 for(const name of ['platform-b1-prepare.mjs','platform-browser-java-sources.test.mjs','platform-precommit-closeout.mjs'])await command('syntax-'+name,NODE,['--check',SNAPSHOT+'/'+name],LIMITS.syntax);
 for(const name of ['platform-backend-diagnostic-verification.mjs','platform-browser-runtime-safety-verification.mjs'])await command('syntax-'+name,NODE,['--check',SNAPSHOT+'/'+name],LIMITS.syntax);
 await command('maven-test-compile',JAVA,[...mavenArgs,'test-compile'],LIMITS.mavenCompile);
 const testInputs=fs.readFileSync(BUILD+'/maven-status/maven-compiler-plugin/testCompile/default-testCompile/inputFiles.lst','utf8').trim().split(/\r?\n/).map(p=>path.resolve(p).replaceAll('\\','/')).sort();
 same(testInputs,currentJava.map(p=>SOURCE+p.slice(ROOT.length)).sort(),'ALL_ORDINARY_JAVA_COMPILED');
 const classes=inventory(BUILD);if(!classes.some(f=>f.path.startsWith('test-classes/')))throw Error('T1_TEST_CLASSES_MISSING');
 save('ordinary-compile-assessment.json',{status:'PASSED',inputId,ordinaryJavaSources:currentJava.length,junitSources:junit.length,compilerInputFiles:testInputs,compiled:classes,compiledId:aggregate(classes),testsExecuted:0,skipFlagsUsed:false,publicPomUnchanged:true});
 fs.mkdirSync(CLASSES);await command('standalone-javac',JAVA.replace('/java.exe','/javac.exe'),['@'+SNAPSHOT+'/compile.args'],LIMITS.javac);
 const guard=await command('java-contracts',JAVA,['@'+SNAPSHOT+'/runtime.args','com.wms.browseracceptance.PlatformB1Contract'],LIMITS.javaContracts);
 if(!guard.includes('B1_NO_DATABASE_CONTRACTS_PASSED=16'))throw Error('T1_JAVA_CONTRACT_COUNT');
 const results=[];for(const [script,count]of GATES){const output=await command('gate-'+script,NODE,['--test','--test-reporter=tap',SNAPSHOT+'/'+script],LIMITS.nodeSuite);const n=k=>Number(output.match(new RegExp('^# '+k+' (\\d+)\\r?$','m'))?.[1]);
  const r={script,expected:count,tests:n('tests'),passed:n('pass'),failed:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),todo:n('todo')};save('assessment-'+script+'.json',r);results.push(r);if(r.tests!==count||r.passed!==count||[r.failed,r.skipped,r.cancelled,r.todo].some(v=>v!==0))throw Error('T1_CONTRACT_COVERAGE');}
 for(const name of ['synthetic-diagnostic-results','synthetic-runtime-safety-results'])for(const f of files(RUN+'/'+name)){const text=fs.readFileSync(f,'utf8');if(['SYNTHETIC_PASSWORD_DO_NOT_PERSIST','eyJsynthetic.payload.signature','SYNTHETIC_MFA','SYNTHETIC_RECOVERY','SELECT private_value','SYNTHETIC_ARGUMENT_DO_NOT_LOG'].some(v=>text.includes(v)))throw Error('T1_SYNTHETIC_SECRET_LEAK');}
 for(const f of sourceProvenance)if(hash(f.origin)!==f.sha256)throw Error('T1_PARALLEL_SOURCE_CHANGE');
 for(const f of inherited)if(hash(ROOT+'/'+f.path)!==f.sha256)throw Error('T1_INHERITED_FINAL_DRIFT');
 for(const f of history)if(hash(f.path)!==f.sha256)throw Error('T1_HISTORY_DRIFT');
 same(inventory(SOURCE),sourceRows,'SOURCE');same(inventory(SNAPSHOT),toolRows,'TOOLS');same(inventory(B4+'/snapshot'),b4.files,'B4_SOURCE_FINAL');same(inventory(B4+'/classes'),b4Classes.files,'B4_CLASSES_FINAL');
 const productsAfter=verifyProducts(),wrapperClasses=inventory(CLASSES);
 save('result.json',{status:'PASSED',completedAt:new Date().toISOString(),inputId,ordinaryJavaSources:currentJava.length,junitSources:junit.length,ordinaryCompiledId:aggregate(classes),wrapperCompiledId:aggregate(wrapperClasses),wrapperClasses,javaContracts:16,nodeGates:results,syntaxChecks:5,productsAfter,historyPreserved:true,inheritedUnchanged:true,productLogicChanged:false,publicPomChanged:false,excludedFormalTests:false,databaseCommands:0,servicesStarted:0,awakeRequested:false,fullProductAcceptanceRepeated:false});
 console.log(JSON.stringify({status:'PASSED',inputId,ordinaryJavaSources:currentJava.length,javaContracts:16,nodeTests:results.reduce((n,r)=>n+r.passed,0),wrapperClasses:wrapperClasses.length}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(e=>{if(fs.existsSync(OUT)&&!fs.existsSync(OUT+'/failure.json'))save('failure.json',{status:'FAILED',stage:step,type:e.name,code:e.code??'UNKNOWN',message:e.message,automaticRetry:false,databaseCommands:0,servicesStarted:0});console.error(e.message);process.exitCode=1;});

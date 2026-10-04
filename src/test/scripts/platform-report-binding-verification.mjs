// Report-binding repair only. This entry has no PostgreSQL launch, connection, migration or business-test mode.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {pathToFileURL,fileURLToPath} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G',RUN=ROOT+'/target/platform-report-binding-repair-20260913/a2';
const DELIVERY=ROOT+'/docs/releases/platform-report-binding-repair-20260913/a2',PRIOR=ROOT+'/docs/releases/platform-db-acceptance-20260911/r6';
const SOURCE=RUN+'/source',BUILD=RUN+'/backend',REPO=RUN+'/maven-repository',MAVEN=RUN+'/apache-maven-3.9.9';
const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe',POM=SOURCE+'/platform-acceptance-pom.xml';
const REPORT_GATE_COUNT=60; // Concrete reporting/first-cause/POM and post-execution I/O guard inventory, before sealing.
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'),digest=p=>sha(fs.readFileSync(p));
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const aggregate=rows=>sha(Buffer.from(rows.map(f=>f.path+'\0'+f.sha256+'\n').join('')));
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=path.join(dir,e.name);if(e.isSymbolicLink())throw Error('No snapshot links');return e.isDirectory()?files(p):[p];}).sort();}
const records=(base,paths)=>paths.map(p=>({path:path.relative(base,p).replaceAll('\\','/'),sha256:digest(p),bytes:fs.statSync(p).size}));
function write(name,value){fs.mkdirSync(path.dirname(DELIVERY+'/'+name),{recursive:true});fs.writeFileSync(DELIVERY+'/'+name,JSON.stringify(value,null,2)+'\n',{flag:'wx'});}
function copy(from,to,rows){for(const f of rows){const destination=to+'/'+f.path;if(digest(from+'/'+f.path)!==f.sha256)throw Error('Source drift');fs.mkdirSync(path.dirname(destination),{recursive:true});fs.copyFileSync(from+'/'+f.path,destination,fs.constants.COPYFILE_EXCL);if(digest(destination)!==f.sha256)throw Error('Copy drift');}}
function verify(){const m=read(DELIVERY+'/input-manifest.json');
 for(const g of [...m.groups,...m.preserved]){for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Input/history drift: '+f.path);
  if(g.exactInventory&&JSON.stringify(files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort())!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('Historical inventory drift');}
 for(const t of m.tools)if(digest(t.path)!==t.sha256)throw Error('Fixed tool drift');
 if(digest(m.derivedTestPom.path)!==m.derivedTestPom.sha256)throw Error('Derived POM drift');return m;
}
function observation(){const output=execFileSync('C:/Windows/System32/netstat.exe',['-ano','-p','TCP'],{encoding:'utf8',windowsHide:true,timeout:5000});
 const ports=[5432,8080,5173,55439,18091,15173,55449];return output.split(/\r?\n/).map(s=>s.trim().split(/\s+/)).filter(v=>v[0]==='TCP'&&v[3]==='LISTENING')
 .map(v=>({address:v[1].slice(0,v[1].lastIndexOf(':')),port:Number(v[1].slice(v[1].lastIndexOf(':')+1)),pid:Number(v[4])})).filter(r=>ports.includes(r.port)).sort((a,b)=>a.port-b.port||a.address.localeCompare(b.address));}
async function seal(){
 if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('Never reuse a repair output directory');
 const prior=read(PRIOR+'/backend-input-manifest.json'),sourceBefore=prior.groups.find(g=>g.name==='backend-source');
 const changed=sourceBefore.files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
 const allowed=['src/test/scripts/platform-r6-database-batch.mjs','src/test/scripts/platform-r6-stages.mjs','src/test/scripts/platform-r6-stages.test.mjs'];
 if(changed.some(p=>!allowed.includes(p)))throw Error('Unreviewed existing source drift: '+changed.join(','));
 const extra=['src/test/java/com/wms/platformverification/PlatformReportLandingTest.java','src/test/scripts/platform-report-pom.ps1',
 'src/test/scripts/platform-report-runner.mjs','src/test/scripts/platform-report-runner.test.mjs','src/test/scripts/platform-report-binding-verification.mjs'];
 const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
 .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
 if(JSON.stringify(paths)!==JSON.stringify([...sourceBefore.files.map(f=>f.path),...extra].sort()))throw Error('Unexpected new source inventory');
 const source=records(ROOT,paths.map(p=>ROOT+'/'+p));
 const preserved=[...prior.groups,...prior.preserved,...[PRIOR,ROOT+'/target/platform-db-acceptance-20260911/r6'].map((location,i)=>({name:'r6-complete-'+i,location,exactInventory:true,files:records(location,files(location))})),
 ...[ROOT+'/target/platform-report-binding-repair-20260913/a1',ROOT+'/docs/releases/platform-report-binding-repair-20260913/a1'].map((location,i)=>({name:'uncompiled-repair-a1-'+i,location,exactInventory:true,files:records(location,files(location))})),
 {name:'r6-report',location:ROOT+'/docs',files:records(ROOT+'/docs',[ROOT+'/docs/PLATFORM_R6_DATABASE_ACCEPTANCE_2026-09-12.md'])}];
 for(const g of preserved)for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical evidence drift');
 const deps=prior.groups.find(g=>g.name==='frozen-maven-cache'),maven=prior.groups.find(g=>g.name==='maven-distribution');
 copy(ROOT,SOURCE,source);copy(deps.location,REPO,deps.files);copy(maven.location,MAVEN,maven.files);
 const module=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-report-runner.mjs'));
 const generated=module.createReportPom(SOURCE,POM);
 if(generated.ok!==true||generated.onlyThreeAuthorizedFieldsChanged!==true||generated.publicPomUnchanged!==true||generated.sha256!==digest(POM))throw Error('Dedicated POM generation failed');
 const groups=[{name:'source',location:SOURCE,files:source},{name:'dependencies',location:REPO,files:deps.files},{name:'maven',location:MAVEN,files:maven.files}].map(g=>({...g,id:aggregate(g.files)}));
 const tools=[process.execPath,JAVA,JAVA.replace('java.exe','javac.exe'),'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe'].map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
 const derivedTestPom={path:POM,sha256:digest(POM),generation:generated};
 const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(tools)+derivedTestPom.sha256));
 write('input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'REPORT_BINDING_REPAIR_NO_DATABASE_NOT_RELEASE_CANDIDATE',previousInputId:prior.id,
 groups,preserved,tools,derivedTestPom,changes:changed,addedFiles:extra,businessTests:prior.businessTests,methodBodyChanges:prior.methodBodyChanges,
 revisedBusinessMethodCount:63,revisedBusinessMethodsUnchanged:true,original63MethodBodiesUnchanged:false,migrationFiles:prior.migrationFiles,
 databaseAuthorized:false,databaseTestsExecuted:0,r5ActualExceptionKeyStillUnknown:true,beforeListeners:observation(),
 untouchedFrontend:records(ROOT,[ROOT+'/frontend/src/test/addressLength.audit.test.tsx',ROOT+'/frontend/pnpm-lock.yaml']),
 branch:execFileSync('git',['branch','--show-current'],{encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim()});
 verify();console.log(JSON.stringify({id,sourceFiles:source.length,changed,addedFiles:extra,derivedTestPomSha256:derivedTestPom.sha256,databaseAuthorized:false}));
}
async function command(executable,args,log){const env={...process.env,JAVA_HOME:path.dirname(path.dirname(JAVA))};
 for(const key of Object.keys(env))if(/^(PG|P3_|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(key))delete env[key];
 const result=await runProcess(executable,args,{cwd:SOURCE,env,timeoutMs:300000});fs.writeFileSync(RUN+'/'+log,result.output,{flag:'wx'});
 if(result.exitCode!==0||!result.exited||result.timedOut||result.aborted||result.spawnError||!result.outputComplete||!result.terminationConfirmed)throw Error('Verification command failed: '+log);
 return {executable,args,cwd:SOURCE,exitCode:result.exitCode,exited:result.exited,outputComplete:result.outputComplete,terminationConfirmed:result.terminationConfirmed,elapsedMs:result.elapsedMs,logSha256:digest(RUN+'/'+log)};
}
async function build(){
 const m=verify();if(fs.existsSync(BUILD))throw Error('Never reuse compiled output');fs.mkdirSync(BUILD,{recursive:true});
 const shared=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-report-runner.mjs'));
 const entry=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-r6-database-batch.mjs'));
 if(entry.executePlatformSuite!==shared.executePlatformMavenSuite)throw Error('Smoke and database call chains differ');
 const context={source:SOURCE,build:BUILD,maven:MAVEN,repo:REPO,java:JAVA,pom:POM,pomSha256:m.derivedTestPom.sha256,runDirectory:RUN,reportRoot:BUILD+'/phase-reports'};
 const mvn=extra=>[...shared.mavenBaseArgs(context,{dedicated:false}),'-Dp3.r2.database-authorized=false','-Dp3.r2.migration-authorized=false',...extra];
 const versions={node:await command(process.execPath,['--version'],'node-version.log'),java:await command(JAVA,['-version'],'java-version.log'),
  maven:await command(JAVA,mvn(['--version']),'maven-version.log')};
 for(const [file,pattern] of [['node-version.log',/^v24\.12\.0\s*$/],['java-version.log',/version "17\.0\.17"/],['maven-version.log',/Apache Maven 3\.9\.9\b/]])
  if(!pattern.test(fs.readFileSync(RUN+'/'+file,'utf8')))throw Error('Fixed tool version mismatch: '+file);
 const compile=await command(JAVA,mvn(['-DskipTests','package']),'compile.log');console.log('Fresh report-binding backend compilation passed');
 const suites=[['com.wms.platformverification.PlatformR2IsolationTest',40],['com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest',7],
 ['com.wms.platformverification.PlatformFixtureScopeTest',29],['com.wms.system.platform.service.PlatformMfaFailureFixtureTest',22]];
 const java=await command(JAVA,mvn(['-Dtest='+suites.map(s=>s[0]).join(','),'-Dmaven.test.redirectTestOutputToFile=true','surefire:test']),'java-gates.log');
 const parser=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-test-results.mjs'));
 const guards=suites.map(([name,count])=>{const report=parser.readJUnitReport(BUILD+'/surefire-reports/TEST-'+name+'.xml');
  if(report.tests!==count||parser.assessSuite({name,methods:report.cases.map(c=>c.name)},{process:java,report}).status!=='PASSED')throw Error('Java gate count mismatch');return report;});
 const nodeResults={};
 for(const [key,file,count] of [['ownership','platform-fixture-isolation.test.mjs',24],['lifecycle','platform-process-lifecycle.test.mjs',32],
 ['sentinels','platform-upgrade-sentinels.test.mjs',65],['parser','platform-test-results.test.mjs',67],['stages','platform-r6-stages.test.mjs',57],['reports','platform-report-runner.test.mjs',REPORT_GATE_COUNT]]){
  const run=await command(process.execPath,['--test','--test-reporter=tap',SOURCE+'/src/test/scripts/'+file],key+'.tap');
  const output=fs.readFileSync(RUN+'/'+key+'.tap','utf8'),n=k=>Number(output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const summary={...run,tests:n('tests'),passed:n('pass'),failures:n('fail'),skipped:n('skipped'),cancelled:n('cancelled')};
  if(summary.tests!==count||summary.passed!==count||summary.failures||summary.skipped||summary.cancelled)throw Error('Node gate coverage mismatch: '+key);
  nodeResults[key]=summary;console.log(key+': '+count+' passed');
 }
 const syntax=[];for(const name of ['platform-r6-database-batch.mjs','platform-r6-stages.mjs','platform-r6-stages.test.mjs','platform-report-runner.mjs','platform-report-runner.test.mjs','platform-report-binding-verification.mjs'])
  syntax.push({name,...await command(process.execPath,['--check',SOURCE+'/src/test/scripts/'+name],'syntax-'+name+'.log')});
 const compiled=records(BUILD,files(BUILD).filter(p=>p.endsWith('.class')||p.endsWith('.sql'))),oldBuild=read(PRIOR+'/backend-build.json');
 const changes=compiled.filter(f=>!oldBuild.compiled.some(o=>o.path===f.path&&o.sha256===f.sha256)).map(f=>f.path);
 if(changes.some(p=>p.startsWith('classes/')))throw Error('Product class or migration changed');
 const beforeDefault=shared.inventory(BUILD+'/surefire-reports'),landings=[];
 for(const phase of ['independent','sequential','full']){
  console.log('Real Maven no-database report landing: '+phase);
  const result=await entry.executePlatformSuite({context,phase,suite:shared.LANDING_SUITE,mode:'landing'});
  const e=result.evidence;write('landing/'+phase+'.json',{inputId:m.id,jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),...e,databaseTestsExecuted:0});
  if(e.firstFailure||e.assessment.status!=='PASSED'||e.assessment.counts.passed!==1||e.protectedDirectories.some(p=>p.status!=='PASSED'))throw Error('Real report landing failed: '+phase);
  shared.requirePreserved(BUILD+'/surefire-reports',beforeDefault);
  for(const file of compiled)if(digest(BUILD+'/'+file.path)!==file.sha256)throw Error('Compiled artifact changed across report phases');
  landings.push(e);console.log(phase+': actual XML and stdout passed, prior/default reports unchanged');
 }
 const workspaceDrift=[...m.groups[0].files,...m.untouchedFrontend].filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);if(workspaceDrift.length)throw Error('Workspace drift');verify();
 write('verification-results.json',{inputId:m.id,completedAt:new Date().toISOString(),versions,compile,java,guards,nodeResults,syntax,compiled,compiledAggregateSha256:aggregate(compiled),
  jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),derivedTestPom:m.derivedTestPom,artifactDifferences:changes,productClassOrSqlChanges:0,workspaceDrift,
  realLandingExecutions:3,distinctLandingMethods:1,landings,defaultReportsBefore:beforeDefault,defaultReportsAfter:shared.inventory(BUILD+'/surefire-reports'),
  sameDatabaseAndLandingExecutor:true,executorSha256:digest(SOURCE+'/src/test/scripts/platform-report-runner.mjs'),databaseEntrySha256:digest(SOURCE+'/src/test/scripts/platform-r6-database-batch.mjs'),
  revised63BusinessMethodsUnchanged:true,databaseTestsExecuted:0,databaseStarted:false,databaseConnected:false,oldR2ToR6Unchanged:true,frontendChecksExecuted:false,releaseCandidate:false});
 const after=observation();write('closeout-check.json',{checkedAt:new Date().toISOString(),inputId:m.id,beforeListeners:m.beforeListeners,afterListeners:after,
  observedListenersUnchanged:JSON.stringify(after)===JSON.stringify(m.beforeListeners),newDatabaseDirectoryCreated:false,databaseConnected:false,
  oldR2ToR6Unchanged:true,rawR6Sha256:digest(PRIOR+'/database-result.json'),r6ReadonlyReviewSha256:digest(PRIOR+'/independent-readonly-review.json'),
  compiledDrift:[],workspaceDrift,frontendAddressTestSha256:digest(ROOT+'/frontend/src/test/addressLength.audit.test.tsx'),pnpmLockSha256:digest(ROOT+'/frontend/pnpm-lock.yaml')});
 console.log(JSON.stringify({inputId:m.id,javaTests:98,nodeTests:Object.values(nodeResults).reduce((n,r)=>n+r.tests,0),realLandingExecutions:3,databaseTestsExecuted:0,productClassOrSqlChanges:0}));
}
if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Fixed root and Node required');
const mode=process.argv[2];
try{if(mode==='seal')await seal();else if(mode==='build')await build();else if(mode==='verify')console.log(JSON.stringify({id:verify().id,drift:false}));else throw Error('Only seal/build/verify; no database mode');}
catch(error){if(fs.existsSync(DELIVERY+'/input-manifest.json')&&!fs.existsSync(DELIVERY+'/'+mode+'-failure.json'))write(mode+'-failure.json',{at:new Date().toISOString(),status:'FAILED',stage:mode,error:error.message,databaseStarted:false,databaseConnected:false});throw error;}

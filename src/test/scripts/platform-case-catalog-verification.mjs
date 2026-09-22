// Dedicated catalog repair / r9 fixture validation. NO PostgreSQL, migration or business-test mode.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
import {javaCatalogPreflight} from './platform-r9-backend-snapshot.mjs';
import {currentBusiness,testBodies} from './platform-r9-ledger.mjs';
import {BUDGETS,JAVA_GATES,NODE_GATES} from './platform-r9-plan.mjs';
import {createReportPom,mavenBaseArgs} from './platform-report-runner.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G',RUN=ROOT+'/target/platform-case-catalog-repair-20260918/a1';
const DELIVERY=ROOT+'/docs/releases/platform-case-catalog-repair-20260918/a1';
const SOURCE=RUN+'/source',BUILD=RUN+'/backend',REPO=RUN+'/maven-repository',MAVEN=RUN+'/apache-maven-3.9.9';
const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe';
const PRIOR=ROOT+'/docs/releases/platform-export-fixture-repair-20260918/a3';
const R9=ROOT+'/docs/releases/platform-db-acceptance-20260918/r9',R9RUN=ROOT+'/target/platform-db-acceptance-20260918/r9';
const sha=x=>crypto.createHash('sha256').update(x).digest('hex'),digest=p=>sha(fs.readFileSync(p));
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const aggregate=rows=>sha(Buffer.from(rows.map(f=>f.path+'\0'+f.sha256+'\n').join('')));
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=dir+'/'+e.name;if(e.isSymbolicLink())throw Error('Snapshot links refused');return e.isDirectory()?files(p):[p];}).sort();}
const records=(base,paths)=>paths.map(p=>({path:path.relative(base,p).replaceAll('\\','/'),sha256:digest(p),bytes:fs.statSync(p).size}));
function write(name,obj){fs.mkdirSync(path.dirname(DELIVERY+'/'+name),{recursive:true});fs.writeFileSync(DELIVERY+'/'+name,JSON.stringify(obj,null,2)+'\n',{flag:'wx'});}
function copy(from,to,rows){for(const f of rows){const dest=to+'/'+f.path;if(digest(from+'/'+f.path)!==f.sha256)throw Error('Copy input drift');fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(from+'/'+f.path,dest,fs.constants.COPYFILE_EXCL);if(digest(dest)!==f.sha256)throw Error('Copy output drift');}}
function checkGroups(groups){const checked=new Set();for(const g of groups){
 for(const f of g.files){const key=g.location+'/'+f.path+'\0'+f.sha256;if(checked.has(key))continue;if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('History/input drift: '+g.name+'/'+f.path);checked.add(key);}
 if(g.exactInventory&&JSON.stringify(files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort())!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('Historical inventory changed');
}}
function verify(){const m=read(DELIVERY+'/input-manifest.json');checkGroups([...m.groups,...m.preserved]);
 for(const f of [...m.groups[0].files,...m.untouchedFrontend])if(digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Workspace drift: '+f.path);
 for(const t of m.tools)if(digest(t.path)!==t.sha256)throw Error('Tool drift');
 if(digest(m.derivedTestPom.path)!==m.derivedTestPom.sha256)throw Error('Derived POM drift');return m;
}
function seal(){
 // Same function as the real database batch's seal; it has no filesystem writes or Java execution.
 const javaCaseCatalog=javaCatalogPreflight();
 if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('New repair directories required');
 if(digest(R9+'/seal-failure.json')!=='4975ac07b1d004b1e9c96f5f63580d3269d494a269b8586cd561229257b3fe0e'
  ||digest(R9+'/method-ledger.json')!=='7377d64848c9ca3130878a3a11f4814f3c22907ce4ad6509931a11184f9b6592')throw Error('R9 failure/ledger drift');
 const failed=read(R9+'/seal-failure.json'),ledger=read(R9+'/method-ledger.json'),prior=read(PRIOR+'/input-manifest.json');
 checkGroups(failed.groups);
 const baseline=failed.groups[0].files.filter(f=>f.path!=='platform-acceptance-pom.xml');
 const allowed=['src/test/java/com/wms/system/tenant/security/SpringSecurityFixtureTest.java',
  ...['platform-r9-ledger.mjs','platform-r9-backend-snapshot.mjs','platform-r9-binding.test.mjs','platform-r9-plan.mjs','platform-r9-fixture.test.mjs'].map(n=>'src/test/scripts/'+n)];
 const changes=baseline.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>({path:f.path,beforeSha256:f.sha256,afterSha256:digest(ROOT+'/'+f.path)}));
 if(changes.some(c=>!allowed.includes(c.path)))throw Error('Out of scope source change');
 const extra=['platform-case-catalog.mjs','platform-case-catalog.test.mjs','platform-case-catalog-verification.mjs'].map(n=>'src/test/scripts/'+n);
 const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
  .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
 if(JSON.stringify(paths)!==JSON.stringify([...baseline.map(f=>f.path),...extra].sort()))throw Error('Unexpected input inventory');
 const source=records(ROOT,paths.map(p=>ROOT+'/'+p)),businessTests=currentBusiness(ROOT,ledger);
 if(JSON.stringify(businessTests)!==JSON.stringify(ledger.businessTests))throw Error('R9 business method changed');
 const migrationFiles=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
 if(JSON.stringify(migrationFiles)!==JSON.stringify(prior.migrationFiles))throw Error('Migration drift');
 const preserved=[...prior.groups,...prior.preserved,
  ...[PRIOR,ROOT+'/target/platform-export-fixture-repair-20260918/a3',R9,R9RUN].map((location,i)=>({name:'a3-r9-complete-'+i,location,exactInventory:true,files:records(location,files(location))})),
  {name:'historical-reports',location:ROOT,files:records(ROOT,[ROOT+'/docs/PLATFORM_R9_DATABASE_ACCEPTANCE_2026-09-18.md',ROOT+'/docs/PLATFORM_EXPORT_FIXTURE_REPAIR_2026-09-18.md'])}];
 checkGroups(preserved);
 const deps=prior.groups.find(g=>g.name==='dependencies'),maven=prior.groups.find(g=>g.name==='maven');
 copy(ROOT,SOURCE,source);copy(deps.location,REPO,deps.files);copy(maven.location,MAVEN,maven.files);
 const generated=createReportPom(SOURCE,SOURCE+'/platform-acceptance-pom.xml');
 if(!generated.ok||!generated.onlyThreeAuthorizedFieldsChanged||!generated.publicPomUnchanged)throw Error('Derived POM failed');
 const groups=[{name:'source',location:SOURCE,files:source},{name:'dependencies',location:REPO,files:deps.files},{name:'maven',location:MAVEN,files:maven.files}].map(g=>({...g,id:aggregate(g.files)}));
 const tools=[process.execPath,JAVA,JAVA.replace('java.exe','javac.exe'),'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'].map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
 const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(tools)+generated.sha256+JSON.stringify(javaCaseCatalog)+JSON.stringify(BUDGETS)));
 write('input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'CASE_CATALOG_REPAIR_R9_FIXTURE_NO_DATABASE_NOT_RELEASE_CANDIDATE',
  groups,preserved,tools,budgets:BUDGETS,javaGates:JAVA_GATES,nodeGates:NODE_GATES,javaCaseCatalog,
  derivedTestPom:{path:SOURCE+'/platform-acceptance-pom.xml',sha256:generated.sha256,generation:generated},changes,addedFiles:extra,
  businessTests,methodBodyChanges:ledger.methodBodyChanges,methodRevisionHistory:ledger.methodRevisionHistory,methodLedgerVersion:'R9_EIGHT_METHODS_ELEVEN_REVISIONS',
  original63MethodBodiesUnchanged:false,r9BusinessMethodBodiesUnchanged:true,businessMethodCount:63,migrationFiles,
  changedNoDatabaseMethods:changes.filter(c=>c.path.endsWith('Test.java')).map(c=>({path:c.path,tests:testBodies(fs.readFileSync(ROOT+'/'+c.path,'utf8'))})),
  parameterCatalogPreparedBeforeArtifacts:true,databaseAuthorized:false,r9OriginalFailureSha256:digest(R9+'/seal-failure.json'),
  untouchedFrontend:records(ROOT,[ROOT+'/frontend/src/test/addressLength.audit.test.tsx',ROOT+'/frontend/pnpm-lock.yaml']),
  branch:execFileSync('git',['branch','--show-current'],{cwd:ROOT,encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim()});
 verify();console.log(JSON.stringify({id,sourceFiles:source.length,javaCases:javaCaseCatalog.reduce((n,g)=>n+g.methods.length,0),databaseAuthorized:false}));
}
const commands=[];
async function command(executable,args,log,timeoutMs){
 const env={...process.env,JAVA_HOME:path.dirname(path.dirname(JAVA))};
 for(const key of Object.keys(env))if(/^(PG|P3_|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(key))delete env[key];
 if(executable===process.execPath&&args.includes('--test'))env.P3_TOOL_EVIDENCE_DIR=RUN+'/tool-evidence';
 const r=await runProcess(executable,args,{cwd:SOURCE,env,timeoutMs});
 fs.writeFileSync(RUN+'/'+log,r.output,{flag:'wx'});
 const row={executable,args,cwd:SOURCE,timeoutMs,exitCode:r.exitCode,exited:r.exited,timedOut:r.timedOut,aborted:r.aborted,spawnFailed:!!r.spawnError,
  outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,elapsedMs:r.elapsedMs,timing:r.timing,log:RUN+'/'+log,logSha256:digest(RUN+'/'+log)};
 commands.push(row);write('commands/'+log+'.json',row);
 if(r.exitCode!==0||!r.exited||r.timedOut||r.aborted||r.spawnError||!r.outputComplete||!r.terminationConfirmed)throw Error('Command failed: '+log);
 return row;
}
async function build(){
 const m=verify();
 if(fileURLToPath(import.meta.url).replaceAll('\\','/').toLowerCase()!==(SOURCE+'/src/test/scripts/platform-case-catalog-verification.mjs').toLowerCase())throw Error('Build must use frozen runner');
 if(fs.existsSync(BUILD))throw Error('Do not reuse classes');fs.mkdirSync(BUILD,{recursive:true});fs.mkdirSync(RUN+'/tool-evidence');fs.mkdirSync(RUN+'/export-fixture-temp');
 const shared=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-report-runner.mjs'));
 const entry=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-r9-database-batch.mjs'));
 if(entry.executePlatformSuite!==shared.executePlatformMavenSuite)throw Error('Probe differs from actual database Maven chain');
 const context={source:SOURCE,build:BUILD,maven:MAVEN,repo:REPO,java:JAVA,pom:m.derivedTestPom.path,pomSha256:m.derivedTestPom.sha256,
  runDirectory:RUN,reportRoot:BUILD+'/landing-reports',exportFixtureTempRoot:RUN+'/export-fixture-temp'};
 const mvn=extra=>[...mavenBaseArgs(context),'-Dp3.r2.database-authorized=false','-Dp3.r2.migration-authorized=false','-Dp3.export.fixture-temp-root='+context.exportFixtureTempRoot,...extra];
 for(const [exe,args,log,pattern] of [[process.execPath,['--version'],'node-version.log',/^v24\.12\.0\s*$/],[JAVA,['-version'],'java-version.log',/version "17\.0\.17"/],
  [JAVA,mvn(['--version']),'maven-version.log',/Apache Maven 3\.9\.9\b/]]){
  await command(exe,args,log,BUDGETS.versionMs);if(!pattern.test(fs.readFileSync(RUN+'/'+log,'utf8')))throw Error('Tool version mismatch');
 }
 const compile=await command(JAVA,mvn(['-DskipTests','package']),'compile.log',BUDGETS.compileMs);console.log('Fresh compilation passed');
 const java=await command(JAVA,mvn(['-Dtest='+JAVA_GATES.map(g=>g[0]).join(','),'-Dplatform.verification.reportsDirectory='+BUILD+'/no-database-reports',
  '-DforkCount=1','-DreuseForks=false','-Dsurefire.timeout=120','-Dsurefire.exitTimeout=20','org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test']),'java-gates.log',BUDGETS.javaGuardsMs);
 const parser=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-test-results.mjs'));
 const guards=JAVA_GATES.map(([name,count])=>{const report=parser.readJUnitReport(BUILD+'/no-database-reports/TEST-'+name+'.xml');
  const expected=m.javaCaseCatalog.find(c=>c.name===name);if(!expected||expected.methods.length!==count||report.tests!==count||parser.assessSuite({name,methods:expected.methods},{process:java,report}).status!=='PASSED')throw Error('Java gate evidence mismatch: '+name);return report;});
 console.log('No-database Java cases passed: '+guards.reduce((n,g)=>n+g.tests,0));
 const nodeResults=[];
 for(const [file,count] of NODE_GATES){
  const result=await command(process.execPath,['--test','--test-reporter=tap',SOURCE+'/src/test/scripts/'+file],file+'.tap',BUDGETS.nodeSuiteMs);
  const output=fs.readFileSync(RUN+'/'+file+'.tap','utf8'),n=k=>Number(output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const row={file,...result,tests:n('tests'),passed:n('pass'),failures:n('fail'),skipped:n('skipped'),cancelled:n('cancelled')};
  if(row.tests!==count||row.passed!==count||row.failures||row.skipped||row.cancelled)throw Error('Node gate evidence mismatch: '+file);
  nodeResults.push(row);console.log(file+': '+count+' passed');
 }
 const compiled=records(BUILD,files(BUILD).filter(p=>p.endsWith('.class')||p.endsWith('.sql'))),before=read(PRIOR+'/verification-results.json');
 const differences=compiled.filter(f=>!before.compiled.some(o=>o.path===f.path&&o.sha256===f.sha256)).map(f=>f.path);
 if(differences.some(p=>p.startsWith('classes/')))throw Error('Product class/migration changed');
 const jarSha256=digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar');
 const defaultReports=shared.inventory(BUILD+'/surefire-reports'),gateReports=shared.inventory(BUILD+'/no-database-reports'),landings=[];
 for(const phase of ['independent','sequential','full']){
  console.log('Real Maven no-database probe: '+phase);
  const probe=await entry.executePlatformSuite({context,phase,suite:shared.LANDING_SUITE,mode:'landing'}),e=probe.evidence;
  write('landing/'+phase+'.json',{inputId:m.id,jarSha256,...e,databaseTestsExecuted:0});
  if(e.firstFailure||e.assessment.status!=='PASSED'||e.assessment.counts.passed!==1||e.protectedDirectories.some(d=>d.status!=='PASSED'))throw Error('Maven report probe failed: '+phase);
  shared.requirePreserved(BUILD+'/surefire-reports',defaultReports);shared.requirePreserved(BUILD+'/no-database-reports',gateReports);
  for(const f of compiled)if(digest(BUILD+'/'+f.path)!==f.sha256)throw Error('Compiled artifact drift');
  if(fs.existsSync(BUILD+'/reports'))throw Error('No-database probe wrote business reports');
  landings.push(e);console.log(phase+': XML/stdout/previous phase hashes verified');
 }
 if(fs.readdirSync(RUN+'/export-fixture-temp').length!==0)throw Error('Test-owned export cleanup incomplete');
 verify();
 write('verification-results.json',{inputId:m.id,completedAt:new Date().toISOString(),commands,compile,java,guards,nodeResults,compiled,compiledAggregateSha256:aggregate(compiled),jarSha256,
  artifactDifferences:differences,productClassOrSqlChanges:0,oldClassesReused:false,landings,realLandingExecutions:3,distinctLandingMethods:1,
  sameDatabaseAndLandingExecutor:true,derivedTestPom:m.derivedTestPom,toolEvidence:records(RUN+'/tool-evidence',files(RUN+'/tool-evidence')),
  exportTempRootEmpty:true,historyUnchanged:true,r9FailureUnchanged:true,r9BusinessMethodsUnchanged:true,methodBodyChanges:m.methodBodyChanges,methodRevisionHistory:m.methodRevisionHistory,
  databaseTestsExecuted:0,databaseStarted:false,databaseConnected:false,databaseDirectoryCreated:false,frontendChecksExecuted:false,releaseCandidate:false});
 console.log(JSON.stringify({inputId:m.id,javaTests:guards.reduce((n,g)=>n+g.tests,0),nodeTests:nodeResults.reduce((n,g)=>n+g.tests,0),mavenProbes:3,databaseTestsExecuted:0}));
}
if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Fixed workspace and Node required');
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const mode=process.argv[2];
 try{if(mode==='seal')seal();else if(mode==='build')await build();else if(mode==='verify')console.log(JSON.stringify({id:verify().id,drift:false}));else throw Error('Only seal/build/verify; NO DATABASE MODE');}
 catch(error){if(fs.existsSync(RUN))write(mode+'-failure.json',{status:'FAILED',stage:mode,at:new Date().toISOString(),error:error.message,commands,databaseStarted:false,databaseConnected:false});throw error;}
}

// Authorized test-only repair. No database connection, initialization or startup mode exists.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
import {createReportPom,mavenBaseArgs} from './platform-report-runner.mjs';
import {JAVA_GATES,NODE_GATES} from './platform-r8-plan.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G';
const BASE=ROOT+'/target/platform-export-fixture-repair-20260918/a3';
const DELIVERY=ROOT+'/docs/releases/platform-export-fixture-repair-20260918/a3';
const SOURCE=BASE+'/source',BUILD=BASE+'/backend',REPO=BASE+'/dependencies',MAVEN=BASE+'/apache-maven-3.9.9';
const PRIOR=ROOT+'/docs/releases/platform-db-acceptance-20260916/r8',PRIOR_RUN=ROOT+'/target/platform-db-acceptance-20260916/r8';
const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe';
const budgets={compileMs:300000,javaMs:300000,nodeMs:120000,syntaxMs:15000};
const javaSuites=[...JAVA_GATES,['com.wms.system.platform.service.PlatformCompanyDataFixtureTest',18],['com.wms.system.platform.service.PlatformExportTestConfigurationTest',17]];
const nodeSuites=[...NODE_GATES,['platform-export-fixture.test.mjs',12]];
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'),digest=p=>sha(fs.readFileSync(p));
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const aggregate=rows=>sha(Buffer.from(rows.map(f=>f.path+'\0'+f.sha256+'\n').join('')));
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=path.join(dir,e.name);if(e.isSymbolicLink())throw Error('No links');return e.isDirectory()?files(p):[p];}).sort();}
const records=(base,paths)=>paths.map(p=>({path:path.relative(base,p).replaceAll('\\','/'),sha256:digest(p),bytes:fs.statSync(p).size}));
function write(name,value){fs.mkdirSync(DELIVERY,{recursive:true});fs.writeFileSync(DELIVERY+'/'+name,JSON.stringify(value,null,2)+'\n',{flag:'wx'});}
function copy(from,to,rows){for(const f of rows){const dest=to+'/'+f.path;fs.mkdirSync(path.dirname(dest),{recursive:true});if(digest(from+'/'+f.path)!==f.sha256)throw Error('Source drift');fs.copyFileSync(from+'/'+f.path,dest,fs.constants.COPYFILE_EXCL);if(digest(dest)!==f.sha256)throw Error('Copy drift');}}
function methods(text){
 text=text.replaceAll('\r\n','\n');const mask=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,s=>s.replace(/[^\r\n]/g,' '));
 return [...mask.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\([^)]*\)\s*(?:throws[^\{]+)?\{/g)].map(m=>{
 let end=m.index+m[0].length,n=1;for(;end<mask.length&&n;end++){if(mask[end]==='{')n++;if(mask[end]==='}')n--;}if(n)throw Error('Method parse');return {name:m[1],sha256:sha(Buffer.from(text.slice(m.index,end)))};});
}
function verify(){const m=read(DELIVERY+'/input-manifest.json');
 const checked=new Set();
 for(const g of [...m.groups,...m.preserved]){
  for(const f of g.files){const key=g.location+'/'+f.path+'\0'+f.sha256;if(checked.has(key))continue;if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Input/history drift: '+f.path);checked.add(key);}
  if(g.exactInventory&&JSON.stringify(files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort())!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('History inventory changed');
 }
 for(const tool of m.tools)if(digest(tool.path)!==tool.sha256)throw Error('Tool drift');
 if(digest(m.derivedTestPom.path)!==m.derivedTestPom.sha256)throw Error('Derived POM drift');
 for(const f of m.groups[0].files)if(digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Workspace drift: '+f.path);
 return m;
}
function seal(){
 if(fs.existsSync(BASE)||fs.existsSync(DELIVERY))throw Error('New repair directories required');
 const prior=read(PRIOR+'/backend-input-manifest.json'),original=prior.groups.find(g=>g.name==='backend-source');
 const allowed=['src/test/java/com/wms/system/platform/service/PlatformCompanyDataServiceIntegrationTest.java',
  'src/test/java/com/wms/platformverification/PlatformR2DatabasePreparer.java','src/test/java/com/wms/platformverification/PlatformR2Isolation.java','src/test/scripts/platform-fixture-isolation.test.mjs'];
 const changed=original.files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
 if(changed.some(p=>!allowed.includes(p)))throw Error('Out-of-scope source changes: '+changed.join(','));
 const extra=['PlatformCompanyDataFixture.java','PlatformCompanyDataFixtureTest.java','PlatformExportTestConfiguration.java','PlatformExportTestConfigurationTest.java'].map(p=>'src/test/java/com/wms/system/platform/service/'+p)
  .concat(['src/test/scripts/platform-export-fixture.test.mjs','src/test/scripts/platform-export-fixture-verification.mjs']);
 const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
  .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
 if(JSON.stringify(paths)!==JSON.stringify([...original.files.map(f=>f.path),...extra].sort()))throw Error('Unexpected input inventory');
 const source=records(ROOT,paths.map(p=>ROOT+'/'+p)),methodChanges=[];
 const businessTests=prior.businessTests.map(e=>{const tests=methods(fs.readFileSync(ROOT+'/'+e.path,'utf8'));
  if(JSON.stringify(tests.map(t=>t.name))!==JSON.stringify(e.tests.map(t=>t.name)))throw Error('Coverage changed');
  for(const t of tests){const before=e.tests.find(o=>o.name===t.name);if(before.sha256!==t.sha256)methodChanges.push({path:e.path,name:t.name,beforeSha256:before.sha256,afterSha256:t.sha256,kind:'AUTHORIZED_EXPORT_FIXTURE_DIAGNOSTICS_AND_CLEANUP'});}
  return {path:e.path,tests};});
 if(businessTests.reduce((n,e)=>n+e.tests.length,0)!==63||methodChanges.length!==1||methodChanges[0].name!=='singleDatasetExportContainsNoSecretDataset')throw Error('Unexpected business method changes');
 const migrations=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
 if(JSON.stringify(migrations)!==JSON.stringify(prior.migrationFiles))throw Error('Migration changes forbidden');
 const preserved=[...prior.groups,...prior.preserved,
  ...[PRIOR_RUN,PRIOR].map((location,i)=>({name:'r8-complete-'+i,location,exactInventory:true,files:records(location,files(location))})),
  {name:'r8-report',location:ROOT,files:records(ROOT,[ROOT+'/docs/PLATFORM_R8_DATABASE_ACCEPTANCE_2026-09-17.md'])},
  {name:'frontend-boundary',location:ROOT,files:records(ROOT,[ROOT+'/frontend/pnpm-lock.yaml',ROOT+'/frontend/src/test/addressLength.audit.test.tsx'])},
  ...['a1','a2'].flatMap(attempt=>['target','docs/releases'].map(prefix=>{const location=ROOT+'/'+prefix+'/platform-export-fixture-repair-20260918/'+attempt;return {name:'failed-'+attempt+'-'+prefix,location,exactInventory:true,files:records(location,files(location))};}))];
 const checked=new Set();for(const g of preserved)for(const f of g.files){const key=g.location+'/'+f.path+'\0'+f.sha256;if(checked.has(key))continue;if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical drift');checked.add(key);}
 const deps=prior.groups.find(g=>g.name==='frozen-maven-cache'),mvn=prior.groups.find(g=>g.name==='maven-distribution');
 copy(ROOT,SOURCE,source);copy(deps.location,REPO,deps.files);copy(mvn.location,MAVEN,mvn.files);
 const generated=createReportPom(SOURCE,SOURCE+'/platform-acceptance-pom.xml');
 if(!generated.ok||!generated.onlyThreeAuthorizedFieldsChanged||!generated.publicPomUnchanged)throw Error('Derived POM mismatch');
 const groups=[{name:'source',location:SOURCE,files:source},{name:'dependencies',location:REPO,files:deps.files},{name:'maven',location:MAVEN,files:mvn.files}].map(g=>({...g,id:aggregate(g.files)}));
 const tools=[process.execPath,JAVA,JAVA.replace('java.exe','javac.exe'),'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'].map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
 const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(tools)+JSON.stringify(budgets)+generated.sha256));
 write('input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'PLATFORM_EXPORT_FIXTURE_REPAIR_NO_DATABASE_NOT_RELEASE_CANDIDATE',previousInputId:prior.id,
  groups,preserved,tools,budgets,javaSuites,nodeSuites,derivedTestPom:{path:SOURCE+'/platform-acceptance-pom.xml',sha256:generated.sha256},changedFiles:changed,addedFiles:extra,
  migrationFiles:migrations,businessTests,methodBodyChanges:methodChanges,historicalMethodBodyChanges:prior.methodBodyChanges,
  original63MethodBodiesUnchanged:false,r8Revised63MethodBodiesUnchanged:false,businessMethodCount:63,databaseAuthorized:false,r8ExportUnderlyingExceptionStillUnknown:true,r5ActualExceptionKeyStillUnknown:true,
  newNoDatabaseMethods:extra.filter(p=>p.endsWith('Test.java')).map(p=>({path:p,tests:methods(fs.readFileSync(ROOT+'/'+p,'utf8'))})),
  branch:execFileSync('git',['branch','--show-current'],{encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim()});
 verify();console.log(JSON.stringify({id,sourceFiles:source.length,changedFiles:changed,methodChanges,databaseAuthorized:false}));
}
async function command(exe,args,log,timeoutMs){const env={...process.env,JAVA_HOME:path.dirname(path.dirname(JAVA))};
 for(const k of Object.keys(env))if(/^(PG|P3_|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(k))delete env[k];
 if(exe===process.execPath&&args.includes('--test'))env.P3_TOOL_EVIDENCE_DIR=BASE+'/tool-evidence';
 const r=await runProcess(exe,args,{cwd:SOURCE,env,timeoutMs,drainMs:500});fs.writeFileSync(BASE+'/'+log,r.output,{flag:'wx'});
 const result={executable:exe,args,timeoutMs,exitCode:r.exitCode,exited:r.exited,timedOut:r.timedOut,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,elapsedMs:r.elapsedMs,log:BASE+'/'+log,logSha256:digest(BASE+'/'+log)};
 write(log+'.result.json',result);
 if(r.exitCode!==0||!r.exited||r.timedOut||r.aborted||r.spawnError||!r.outputComplete)throw Error('Verification failed: '+log);
 return result;
}
async function build(){const m=verify();if(fs.existsSync(BUILD))throw Error('Do not reuse output');fs.mkdirSync(BUILD,{recursive:true});
 fs.mkdirSync(BASE+'/tool-evidence');
 const context={source:SOURCE,build:BUILD,repo:REPO,maven:MAVEN,java:JAVA,pom:m.derivedTestPom.path,runDirectory:BASE};
 const fixtureTemp=BASE+'/export-fixture-temp';fs.mkdirSync(fixtureTemp);
 const mvn=extra=>[...mavenBaseArgs(context),'-Dp3.r2.database-authorized=false','-Dp3.r2.migration-authorized=false','-Dp3.export.fixture-temp-root='+fixtureTemp,...extra];
 const compile=await command(JAVA,mvn(['-DskipTests','package']),'compile.log',budgets.compileMs);console.log('Fresh backend compile passed');
 const java=await command(JAVA,mvn(['-Dtest='+javaSuites.map(s=>s[0]).join(','),'-Dplatform.verification.reportsDirectory='+BUILD+'/no-database-reports',
  '-DforkCount=1','-DreuseForks=false','-Dsurefire.timeout=120','-Dsurefire.exitTimeout=20','org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test']),'java-tests.log',budgets.javaMs);
 const parser=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-test-results.mjs'));
 const approvedCases=read(PRIOR+'/backend-build.json').guards;
 const approvedSource=read(PRIOR+'/backend-input-manifest.json').groups.find(g=>g.name==='backend-source');
 const guards=javaSuites.map(([name,count])=>{const report=parser.readJUnitReport(BUILD+'/no-database-reports/TEST-'+name+'.xml');
  const source=m.groups[0].files.find(f=>f.path==='src/test/java/'+name.replaceAll('.','/')+'.java');
  let expected;
  if(JAVA_GATES.some(([legacy])=>legacy===name)) {
   // Only the frozen NAME catalog is reused, never its result flags or pass counts. Source identity is mandatory.
   if(approvedSource.files.find(f=>f.path===source.path)?.sha256!==source.sha256)throw Error('Parameterized gate source changed: '+name);
   expected=approvedCases.find(g=>g.name===name).cases.map(c=>c.name);
  } else expected=methods(fs.readFileSync(SOURCE+'/'+source.path,'utf8')).map(t=>t.name);
  if(expected.length!==count||new Set(expected).size!==count)throw Error('Invalid independently frozen expected cases: '+name);
  if(report.tests!==count||parser.assessSuite({name,methods:expected},{process:java,report}).status!=='PASSED')throw Error('Unexpected Java coverage: '+name);return report;});
 const nodeResults=[];
 for(const [file,count] of nodeSuites){
  const result=await command(process.execPath,['--test','--test-reporter=tap','src/test/scripts/'+file],file+'.tap',budgets.nodeMs);
  const tap=fs.readFileSync(BASE+'/'+file+'.tap','utf8'),n=k=>Number(tap.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const summary={file,...result,tests:n('tests'),passed:n('pass'),failures:n('fail'),skipped:n('skipped'),cancelled:n('cancelled')};
  if(summary.tests!==count||summary.passed!==count||summary.failures||summary.skipped||summary.cancelled)throw Error('Unexpected Node coverage: '+file);
  nodeResults.push(summary);console.log(file+': '+count+' passed');
 }
 const syntax=[];for(const file of ['platform-fixture-isolation.test.mjs','platform-export-fixture.test.mjs','platform-export-fixture-verification.mjs'])syntax.push({file,...await command(process.execPath,['--check','src/test/scripts/'+file],'syntax-'+file+'.log',budgets.syntaxMs)});
 const compiled=records(BUILD,files(BUILD).filter(p=>p.endsWith('.class')||p.endsWith('.sql'))),oldBuild=read(PRIOR+'/backend-build.json');
 const differences=compiled.filter(f=>!oldBuild.compiled.some(o=>o.path===f.path&&o.sha256===f.sha256)).map(f=>f.path);
 if(differences.some(p=>p.startsWith('classes/')))throw Error('Product classes/SQL changed');
 verify();
 write('verification-results.json',{inputId:m.id,completedAt:new Date().toISOString(),compile,java,guards,nodeResults,syntax,compiled,compiledAggregateSha256:aggregate(compiled),
  jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),artifactDifferences:differences,productClassOrSqlChanges:0,oldClassesReused:false,
  methodBodyChanges:m.methodBodyChanges,businessMethodCount:63,databaseTestsExecuted:0,databaseStarted:false,databaseConnected:false,
  historyUnchanged:true,toolEvidence:records(BASE+'/tool-evidence',files(BASE+'/tool-evidence')),frontendChecksExecuted:false,releaseCandidate:false});
 console.log(JSON.stringify({inputId:m.id,javaTests:guards.reduce((n,g)=>n+g.tests,0),nodeTests:nodeResults.reduce((n,g)=>n+g.tests,0),databaseTestsExecuted:0,productClassOrSqlChanges:0}));
}
if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Fixed root/Node required');
const mode=process.argv[2];
try{if(mode==='seal')seal();else if(mode==='build')await build();else if(mode==='verify')console.log(JSON.stringify({id:verify().id,drift:false}));else throw Error('Only seal/build/verify; no database mode');}
catch(error){if(fs.existsSync(DELIVERY+'/input-manifest.json')&&!fs.existsSync(DELIVERY+'/'+mode+'-failure.json'))write(mode+'-failure.json',{at:new Date().toISOString(),status:'FAILED',stage:mode,error:error.message,databaseStarted:false,databaseConnected:false});throw error;}

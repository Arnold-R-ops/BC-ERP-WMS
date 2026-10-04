// Authorized tool-only repair verification. No PostgreSQL executable, SQL connection or service action.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G',BASE=ROOT+'/target/platform-result-parser-repair-20260911/a1';
const SOURCE=BASE+'/source',BUILD=BASE+'/backend',REPO=BASE+'/dependencies',MAVEN=BASE+'/apache-maven-3.9.9';
const DELIVERY=ROOT+'/docs/releases/platform-result-parser-repair-20260911/a1';
const OLD=ROOT+'/docs/releases/platform-db-acceptance-20260911/r4',OLD_RUN=ROOT+'/target/platform-db-acceptance-20260911/r4';
const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe',PS='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'),digest=p=>sha(fs.readFileSync(p));
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const aggregate=rows=>sha(Buffer.from(rows.map(f=>f.path+'\0'+f.sha256+'\n').join('')));
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=path.join(dir,e.name);if(e.isSymbolicLink())throw Error('Links forbidden');return e.isDirectory()?files(p):[p];}).sort();}
const records=(base,paths)=>paths.map(p=>({path:path.relative(base,p).replaceAll('\\','/'),sha256:digest(p),bytes:fs.statSync(p).size}));
function write(name,value){fs.mkdirSync(DELIVERY,{recursive:true});fs.writeFileSync(DELIVERY+'/'+name,JSON.stringify(value,null,2)+'\n',{flag:'wx'});}
function copy(from,to,rows){for(const f of rows){const dest=to+'/'+f.path;fs.mkdirSync(path.dirname(dest),{recursive:true});
  if(digest(from+'/'+f.path)!==f.sha256)throw Error('Copy input changed');fs.copyFileSync(from+'/'+f.path,dest,fs.constants.COPYFILE_EXCL);
  if(digest(dest)!==f.sha256)throw Error('Copy mismatch');}}
function verify(){
  const m=read(DELIVERY+'/input-manifest.json');
  for(const g of [...m.groups,...m.preserved]){
    for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Input or historical evidence changed: '+f.path);
    if(g.exactInventory){const actual=files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort();
      if(JSON.stringify(actual)!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('Historical inventory changed');}
  }
  for(const t of m.tools)if(digest(t.path)!==t.sha256)throw Error('Tool changed');return m;
}
function seal(){
  if(fs.existsSync(BASE)||fs.existsSync(DELIVERY))throw Error('New repair directories required; do not overwrite');
  const prior=read(OLD+'/backend-input-manifest.json'),originalSource=prior.groups.find(g=>g.name==='backend-source');
  const allowedChanged=['src/test/scripts/platform-r4-database-batch.mjs'];
  const changed=originalSource.files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
  if(changed.some(p=>!allowedChanged.includes(p)))throw Error('Out-of-scope source drift: '+changed.join(','));
  const extra=['platform-junit-xml.ps1','platform-test-results.mjs','platform-test-results.test.mjs','platform-result-parser-repair-verification.mjs','fixtures/platform-junit/mixed.xml'].map(p=>'src/test/scripts/'+p);
  const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
  const sourcePaths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
    .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
  const expected=[...originalSource.files.map(f=>f.path),...extra].sort();
  if(JSON.stringify(sourcePaths)!==JSON.stringify(expected))throw Error('Unexpected source inventory');
  const source=records(ROOT,sourcePaths.map(p=>ROOT+'/'+p));
  for(const e of prior.expectedBusinessTests){const old=originalSource.files.find(f=>f.path===e.path);if(digest(ROOT+'/'+e.path)!==old.sha256)throw Error('Original business test file changed');}
  const migrationFiles=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
  if(JSON.stringify(migrationFiles)!==JSON.stringify(prior.migrationFiles))throw Error('Migration changes forbidden');
  const preserved=[...prior.groups,...prior.preserved,
    {name:'r4-complete-run',location:OLD_RUN,exactInventory:true,files:records(OLD_RUN,files(OLD_RUN))},
    {name:'r4-delivery',location:OLD,exactInventory:true,files:records(OLD,files(OLD))},
    {name:'r4-original-report',location:ROOT+'/docs',files:records(ROOT+'/docs',[ROOT+'/docs/PLATFORM_R4_DATABASE_ACCEPTANCE_2026-09-11.md'])}];
  for(const g of preserved)for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical drift');
  const deps=prior.groups.find(g=>g.name==='frozen-maven-cache'),mvn=prior.groups.find(g=>g.name==='maven-distribution');
  copy(ROOT,SOURCE,source);copy(deps.location,REPO,deps.files);copy(mvn.location,MAVEN,mvn.files);
  const groups=[{name:'source',location:SOURCE,files:source},{name:'dependencies',location:REPO,files:deps.files},{name:'maven',location:MAVEN,files:mvn.files}].map(g=>({...g,id:aggregate(g.files)}));
  const tools=[process.execPath,JAVA,JAVA.replace('java.exe','javac.exe'),PS,'C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe'].map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
  const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(tools)));
  write('input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'platform-result-parser-repair-no-database-not-release-candidate',
    previousInputId:prior.id,groups,preserved,tools,changedFiles:changed,addedFiles:extra,migrationFiles,expectedBusinessTests:prior.expectedBusinessTests,
    original63MethodsAndWholeFilesUnchanged:true,databaseAuthorized:false,
    branch:execFileSync('git',['branch','--show-current'],{encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim()});
  verify();console.log(JSON.stringify({inputId:id,sourceFiles:source.length,changedFiles:changed,addedFiles:extra,oldR4Preserved:true,databaseAuthorized:false}));
}
async function command(exe,args,log){
  const env={...process.env,JAVA_HOME:path.dirname(path.dirname(JAVA))};
  for(const k of Object.keys(env))if(/^(PG|P3_|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(k))delete env[k];
  const r=await runProcess(exe,args,{cwd:SOURCE,env,timeoutMs:300000,drainMs:500});
  fs.writeFileSync(BASE+'/'+log,r.output,{flag:'wx'});
  if(r.exitCode!==0||!r.exited||r.timedOut||r.aborted||r.spawnError||!r.outputComplete)throw Error('Verification failed: '+log);
  return {exitCode:r.exitCode,exited:r.exited,timedOut:r.timedOut,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,
    elapsedMs:r.elapsedMs,logSha256:digest(BASE+'/'+log)};
}
async function build(){
  const m=verify();if(fs.existsSync(BUILD))throw Error('Do not reuse build output');fs.mkdirSync(BUILD,{recursive:true});
  const mvn=extra=>['-Dmaven.home='+MAVEN,'-Dclassworlds.conf='+MAVEN+'/bin/m2.conf','-Dmaven.multiModuleProjectDirectory='+SOURCE,'-cp',MAVEN+'/boot/*',
    'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-ntp','-s',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-gs',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml',
    '-Dmaven.repo.local='+REPO,'-Dwms.build.directory='+BUILD,...extra];
  const compile=await command(JAVA,mvn(['-DskipTests','package']),'compile.log');console.log('Fresh Java compilation completed');
  const java=await command(JAVA,mvn(['-Dtest=com.wms.platformverification.PlatformR2IsolationTest,com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest',
    '-Dmaven.test.redirectTestOutputToFile=true','surefire:test']),'java-tests.log');
  const parser=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-test-results.mjs'));
  const guards=['com.wms.platformverification.PlatformR2IsolationTest','com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest'].map(name=>{
    const report=parser.readJUnitReport(BUILD+'/surefire-reports/TEST-'+name+'.xml');
    const result=parser.assessSuite({name,methods:report.cases.map(c=>c.name)},{process:java,report});if(result.status!=='PASSED')throw Error('Java no-database suite failed');return report;});
  if(guards[0].tests!==38||guards[1].tests!==7)throw Error('No-database Java count mismatch');
  const syntax=[];for(const f of ['platform-r4-database-batch.mjs','platform-test-results.mjs','platform-test-results.test.mjs','platform-result-parser-repair-verification.mjs']){
    syntax.push({file:f,...await command(process.execPath,['--check','src/test/scripts/'+f],'syntax-'+f+'.log')});}
  const nodeResults={};
  for(const [key,file,count] of [['lifecycle','platform-process-lifecycle.test.mjs',32],['sentinels','platform-upgrade-sentinels.test.mjs',65],['parser','platform-test-results.test.mjs',67]]){
    const result=await command(process.execPath,['--test','--test-reporter=tap','src/test/scripts/'+file],key+'-tests.tap');
    const tap=fs.readFileSync(BASE+'/'+key+'-tests.tap','utf8'),n=k=>Number(tap.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
    const summary={...result,tests:n('tests'),passed:n('pass'),failures:n('fail'),skipped:n('skipped'),cancelled:n('cancelled')};
    if(summary.tests!==count||summary.passed!==count||summary.failures||summary.skipped||summary.cancelled)throw Error('Unexpected no-database coverage: '+key);
    nodeResults[key]=summary;console.log(key+': '+count+' passed');
  }
  const batch=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-r4-database-batch.mjs'));
  const old=read(OLD+'/database-result.json'),r4=read(OLD+'/backend-input-manifest.json');
  const replay=await batch.runPlatformBusinessAcceptance({input:r4,execute:async p=>{
    const prior=old.suites.find(s=>s.selector===p.selector);if(!prior)return null;
    const log=OLD_RUN+'/test-'+p.name.split('.').at(-1)+'.log';
    if(prior.exitCode!==0||prior.timeout||digest(log)!==prior.logSha256||!fs.readFileSync(log,'utf8').includes('BUILD SUCCESS'))throw Error('R4 process evidence mismatch');
    return {process:{exitCode:prior.exitCode,exited:true,timeout:prior.timeout,outputComplete:true,terminationConfirmed:true},
      xmlFile:OLD_RUN+'/build/a1/surefire-reports/TEST-'+p.name+'.xml',logSha256:prior.logSha256};
  }});
  if(replay.status!=='INCOMPLETE'||replay.coverage.passed!==37||replay.coverage.unexecuted!==26||replay.coverage.reportFailures)throw Error('R4 replay mismatch');
  write('r4-readonly-replay.json',{inputId:m.id,checkedAt:new Date().toISOString(),mode:'READ_ONLY_EXISTING_REPORTS_NO_TESTS_RERUN',
    processEvidence:'R4 retained exitCode/timeout, hashed BUILD SUCCESS logs and historical runner completion gates; no new business process executed',
    originalBatchStatusRetained:'FAILED',...replay});
  const compiled=records(BUILD,files(BUILD).filter(p=>p.endsWith('.class')||p.endsWith('.sql')));
  const oldBuild=read(OLD+'/backend-build.json');
  const artifactChanges=compiled.filter(f=>!oldBuild.compiled.some(o=>o.path===f.path&&o.sha256===f.sha256)).map(f=>f.path);
  if(artifactChanges.length||compiled.length!==oldBuild.compiled.length)throw Error('Product/test class or migration changed');
  const drift=m.groups.find(g=>g.name==='source').files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
  if(drift.length)throw Error('Workspace source drift');verify();
  write('verification-results.json',{inputId:m.id,completedAt:new Date().toISOString(),compile,java,guards,nodeResults,syntax,
    jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),compiled,compiledAggregateSha256:aggregate(compiled),artifactChanges,workspaceDrift:drift,
    original63MethodsUnchanged:true,migrationSqlCount:m.migrationFiles.length,databaseTestsExecuted:0,databaseStarted:false,databaseConnected:false,
    oldR2R3R4DataAndEvidenceUnchanged:true,frontendChecksExecuted:false,releaseCandidate:false});
  console.log(JSON.stringify({inputId:m.id,javaTests:45,lifecycle:32,sentinels:65,parser:67,r4ReadOnly:[4,6,27],databaseTestsExecuted:0,artifactChanges:0}));
}
if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Fixed root and Node required');
const mode=process.argv[2];
try{
  if(mode==='seal')seal();else if(mode==='build')await build();else if(mode==='verify')console.log(JSON.stringify({id:verify().id,drift:false}));
  else throw Error('Only seal/build/verify; database execution is not implemented');
}catch(error){
  if(fs.existsSync(DELIVERY+'/input-manifest.json')&&!fs.existsSync(DELIVERY+'/'+mode+'-failure.json'))write(mode+'-failure.json',{at:new Date().toISOString(),status:'FAILED',stage:mode,error:error.message,databaseStarted:false,databaseConnected:false});
  throw error;
}

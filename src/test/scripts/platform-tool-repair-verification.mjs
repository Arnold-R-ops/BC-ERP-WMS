// Authorized no-database repair verification only. No PostgreSQL/psql/service commands are provided.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {runProcess} from './platform-process-lifecycle.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G', BASE=ROOT+'/target/platform-tool-repair-20260911/a2';
const SOURCE=BASE+'/source', BUILD=BASE+'/backend', REPO=BASE+'/dependencies', MAVEN=BASE+'/apache-maven-3.9.9';
const DELIVERY=ROOT+'/docs/releases/platform-tool-repair-20260911/a2';
const OLD=ROOT+'/docs/releases/platform-db-acceptance-20260911', OLD_RUN=ROOT+'/target/platform-db-acceptance-20260911/r2';
const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe', CSC='C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe';
if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Fixed root and Node required');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'), digest=p=>sha(fs.readFileSync(p));
const json=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const write=(name,data)=>{fs.mkdirSync(DELIVERY,{recursive:true});fs.writeFileSync(DELIVERY+'/'+name,JSON.stringify(data,null,2)+'\n',{flag:'wx'});};
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=path.join(dir,e.name);if(e.isSymbolicLink())throw Error('No links');return e.isDirectory()?files(p):[p];}).sort();}
const record=(base,items)=>items.map(p=>({path:path.relative(base,p).replaceAll('\\','/'),sha256:digest(p),bytes:fs.statSync(p).size}));
const aggregate=rows=>sha(Buffer.from(rows.map(f=>f.path+'\0'+f.sha256+'\n').join('')));
function copyRecords(from,to,records){
  for(const f of records){const p=to+'/'+f.path;fs.mkdirSync(path.dirname(p),{recursive:true});fs.copyFileSync(from+'/'+f.path,p,fs.constants.COPYFILE_EXCL);if(digest(p)!==f.sha256)throw Error('Copy drift');}
}
function testBodies(text){
  text=text.replaceAll('\r\n','\n');
  const mask=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,s=>s.replace(/[^\r\n]/g,' '));
  return [...mask.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\([^)]*\)\s*(?:throws[^\{]+)?\{/g)].map(m=>{
    let end=m.index+m[0].length,n=1;for(;end<mask.length&&n;end++){if(mask[end]==='{')n++;if(mask[end]==='}')n--;}
    if(n)throw Error('Test parse failed');return {name:m[1],sha256:sha(Buffer.from(text.slice(m.index,end)))};});
}
function verify(){
  const m=json(DELIVERY+'/input-manifest.json');
  for(const g of m.groups)for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Input drift: '+f.path);
  for(const g of m.preserved)for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical evidence changed: '+f.path);
  for(const t of m.tools)if(digest(t.path)!==t.sha256)throw Error('Tool drift');return m;
}
function seal(){
  if(fs.existsSync(BASE)||fs.existsSync(DELIVERY+'/input-manifest.json'))throw Error('Do not overwrite evidence');
  const previous=json(OLD+'/backend-input-manifest-v2.json'),oldSource=previous.groups.find(g=>g.name==='backend-source');
  const allowed=new Set(['src/test/scripts/platform-r2-database-batch.mjs','src/test/java/com/wms/system/platform/service/PlatformAccessGrantRoleRaceIntegrationTest.java']);
  const changed=oldSource.files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
  if(changed.some(p=>!allowed.has(p)))throw Error('Out-of-scope drift: '+changed.join(','));
  const git=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
  const paths=[...new Set(git.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
    .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
  const source=paths.map(p=>({path:p,sha256:digest(ROOT+'/'+p),bytes:fs.statSync(ROOT+'/'+p).size}));
  const assertions=previous.expectedBusinessTests.map(e=>{const now=testBodies(fs.readFileSync(ROOT+'/'+e.path,'utf8'));
    if(JSON.stringify(now)!==JSON.stringify(e.unchangedTests))throw Error('Business test changed: '+e.path);return e;});
  const preserved=[...previous.groups,
    {name:'old-pgdata',location:OLD_RUN+'/pgdata',files:record(OLD_RUN+'/pgdata',files(OLD_RUN+'/pgdata'))},
    {name:'old-delivery',location:OLD,files:record(OLD,files(OLD))},
    {name:'old-batch-logs',location:OLD_RUN,files:record(OLD_RUN,fs.readdirSync(OLD_RUN).filter(n=>n.endsWith('.log')).map(n=>OLD_RUN+'/'+n))}];
  for(const g of preserved)for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical input drift');
  copyRecords(ROOT,SOURCE,source);
  const deps=previous.groups.find(g=>g.name==='frozen-maven-cache'),maven=previous.groups.find(g=>g.name==='maven-distribution');
  copyRecords(deps.location,REPO,deps.files);copyRecords(maven.location,MAVEN,maven.files);
  const groups=[{name:'source',location:SOURCE,files:source},{name:'dependencies',location:REPO,files:deps.files},{name:'maven',location:MAVEN,files:maven.files}].map(g=>({...g,id:aggregate(g.files)}));
  const tools=[process.execPath,JAVA,CSC].map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
  const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(tools)));
  write('input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'platform-test-tools-repair-no-database-not-full-R2',
    previousInputId:previous.id,groups,preserved,tools,changedFiles:changed,addedFiles:source.filter(f=>!oldSource.files.some(o=>o.path===f.path)).map(f=>f.path),
    expectedBusinessTests:assertions,existingBusinessTestBodiesUnchanged:assertions.reduce((n,e)=>n+e.unchangedTests.length,0),
    migrationFiles:source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql')),databaseAuthorized:false});
  verify();console.log(JSON.stringify({id,sourceFiles:source.length,businessTestsUnchanged:63,oldDataPreserved:true}));
}
function summary(file){const xml=fs.readFileSync(file,'utf8'),head=xml.match(/<testsuite\s[^>]+>/)[0];
  const attr=(s,k)=>s.match(new RegExp('\\b'+k+'="([^"]*)"'))?.[1];
  return {name:attr(head,'name'),tests:Number(attr(head,'tests')),failures:Number(attr(head,'failures')),errors:Number(attr(head,'errors')),skipped:Number(attr(head,'skipped')),sha256:digest(file)};}
async function execute(exe,args,log){
  const env={...process.env,JAVA_HOME:path.dirname(path.dirname(JAVA))};
  for(const k of Object.keys(env))if(/^(PG|P3_|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(k))delete env[k];
  const r=await runProcess(exe,args,{cwd:SOURCE,env,timeoutMs:300000,drainMs:500});
  fs.writeFileSync(BASE+'/'+log,r.output,{flag:'wx'});
  if(r.exitCode!==0||!r.exited||r.timedOut||r.aborted||!r.outputComplete)throw Error('Verification failed: '+log);
  return {exitCode:r.exitCode,elapsedMs:r.elapsedMs,logSha256:digest(BASE+'/'+log)};
}
async function build(){
  const m=verify();if(fs.existsSync(BUILD))throw Error('Never reuse output');fs.mkdirSync(BUILD,{recursive:true});
  const mvn=extra=>['-Dmaven.home='+MAVEN,'-Dclassworlds.conf='+MAVEN+'/bin/m2.conf','-Dmaven.multiModuleProjectDirectory='+SOURCE,'-cp',MAVEN+'/boot/*',
    'org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-ntp','-s',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-gs',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml',
    '-Dmaven.repo.local='+REPO,'-Dwms.build.directory='+BUILD,...extra];
  const compile=await execute(JAVA,mvn(['-DskipTests','package']),'compile.log');
  const java=await execute(JAVA,mvn(['-Dtest=com.wms.platformverification.PlatformR2IsolationTest,com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest',
    '-Dmaven.test.redirectTestOutputToFile=true','surefire:test']),'java-tests.log');
  const suites=['com.wms.platformverification.PlatformR2IsolationTest','com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest']
    .map(name=>summary(BUILD+'/surefire-reports/TEST-'+name+'.xml'));
  if(suites[0].tests!==36||suites[1].tests!==7||suites.some(s=>s.failures||s.errors||s.skipped))throw Error('Unexpected Java test coverage');
  const syntax=[];for(const file of ['platform-process-lifecycle.mjs','platform-process-lifecycle.test.mjs','platform-r2-database-batch.mjs','platform-tool-repair-verification.mjs']){
    syntax.push(await execute(process.execPath,['--check','src/test/scripts/'+file],'syntax-'+file+'.log'));}
  const node=await execute(process.execPath,['--test','--test-reporter=tap','src/test/scripts/platform-process-lifecycle.test.mjs'],'lifecycle-tests.tap');
  const tap=fs.readFileSync(BASE+'/lifecycle-tests.tap','utf8');
  const count=k=>Number(tap.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const lifecycle={...node,tests:count('tests'),passed:count('pass'),failures:count('fail'),skipped:count('skipped'),cancelled:count('cancelled')};
  if(lifecycle.tests!==32||lifecycle.passed!==32||lifecycle.failures||lifecycle.skipped||lifecycle.cancelled)throw Error('Unexpected lifecycle coverage');
  const compiled=record(BUILD,files(BUILD).filter(p=>p.endsWith('.class')||p.endsWith('.sql')));
  const prior=json(OLD+'/backend-build.json');
  const changes=compiled.filter(f=>!prior.compiled.some(p=>p.path===f.path&&p.sha256===f.sha256)).map(f=>f.path);
  if(changes.some(p=>p.startsWith('classes/')))throw Error('Product class or migration changed');
  verify();
  write('verification-results.json',{inputId:m.id,completedAt:new Date().toISOString(),compile,java,suites,lifecycle,syntax,
    jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),compiled,changedTestArtifacts:changes,productClassOrSqlChanges:0,
    existingBusinessBodiesUnchanged:63,databaseTestsExecuted:0,databaseTestPlan:63,databaseStarted:false,databaseConnected:false,oldDataAndEvidenceUnchanged:true,
    fullR2Candidate:false,frontendChecksExecuted:false});
  console.log(JSON.stringify({inputId:m.id,javaTests:43,lifecycleTests:32,oldDataPreserved:true,databaseTestsExecuted:0,productClassOrSqlChanges:0}));
}
if(process.argv[2]==='seal')seal();else if(process.argv[2]==='build')await build();else if(process.argv[2]==='verify')console.log(JSON.stringify({id:verify().id,unchanged:true}));else throw Error('Only seal/build/verify; database execution is not implemented here');

// Authorized fixture-only, NO DATABASE verification. No initdb/psql/PG launcher path exists here.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G';
const BASE=ROOT+'/target/platform-fixture-repair-20260912/a2',DELIVERY=ROOT+'/docs/releases/platform-fixture-repair-20260912/a2';
const SOURCE=BASE+'/source',BUILD=BASE+'/backend',REPO=BASE+'/dependencies',MAVEN=BASE+'/apache-maven-3.9.9';
const PRIOR=ROOT+'/docs/releases/platform-db-acceptance-20260911/r5',PRIOR_RUN=ROOT+'/target/platform-db-acceptance-20260911/r5';
const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe';
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
 for(const g of [...m.groups,...m.preserved]){for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Input/history drift: '+f.path);
 if(g.exactInventory){const actual=files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort();if(JSON.stringify(actual)!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('History inventory drift');}}
 for(const tool of m.tools)if(digest(tool.path)!==tool.sha256)throw Error('Tool drift');return m;}
function seal(){
 if(fs.existsSync(BASE)||fs.existsSync(DELIVERY))throw Error('New repair directories required');
 const prior=read(PRIOR+'/backend-input-manifest.json'),original=prior.groups.find(g=>g.name==='backend-source');
 const allowed=new Set(prior.expectedBusinessTests.map(t=>t.path));
 const changed=original.files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
 if(changed.some(p=>!allowed.has(p)))throw Error('Out-of-scope changes: '+changed.join(','));
 const extra=['src/test/java/com/wms/platformverification/PlatformFixtureScope.java','src/test/java/com/wms/platformverification/PlatformFixtureScopeTest.java',
 'src/test/java/com/wms/system/platform/service/PlatformMfaFailureFixture.java','src/test/java/com/wms/system/platform/service/PlatformMfaFailureFixtureTest.java',
 'src/test/scripts/platform-fixture-isolation.test.mjs','src/test/scripts/platform-fixture-repair-verification.mjs'];
 const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
 .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
 if(JSON.stringify(paths)!==JSON.stringify([...original.files.map(f=>f.path),...extra].sort()))throw Error('Unexpected input inventory');
 const source=records(ROOT,paths.map(p=>ROOT+'/'+p)),methodChanges=[];
 const businessTests=prior.expectedBusinessTests.map(e=>{const tests=methods(fs.readFileSync(ROOT+'/'+e.path,'utf8'));
 if(JSON.stringify(tests.map(t=>t.name))!==JSON.stringify(e.unchangedTests.map(t=>t.name)))throw Error('Coverage changed');
 for(const t of tests){const before=e.unchangedTests.find(o=>o.name===t.name);if(before.sha256!==t.sha256)methodChanges.push({path:e.path,name:t.name,beforeSha256:before.sha256,afterSha256:t.sha256,
 kind:e.path.endsWith('PlatformAdminInvitationMfaTransactionIntegrationTest.java')?'AUTHORIZED_PRECONDITIONS_AND_EXACT_EXCEPTION_ASSERTION':'EXPLICIT_FIXTURE_REGISTRATION_ONLY'});}
 return {path:e.path,tests};});
 if(businessTests.reduce((n,e)=>n+e.tests.length,0)!==63)throw Error('Expected 63 methods');
 const migrations=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
 if(JSON.stringify(migrations)!==JSON.stringify(prior.migrationFiles))throw Error('Migration changes forbidden');
 const preserved=[...prior.groups,...prior.preserved,{name:'r5-run',location:PRIOR_RUN,exactInventory:true,files:records(PRIOR_RUN,files(PRIOR_RUN))},
 {name:'r5-delivery',location:PRIOR,exactInventory:true,files:records(PRIOR,files(PRIOR))},
 {name:'r5-report',location:ROOT+'/docs',files:records(ROOT+'/docs',[ROOT+'/docs/PLATFORM_R5_DATABASE_ACCEPTANCE_2026-09-12.md'])},
 ...['target','docs/releases'].map(prefix=>{const location=ROOT+'/'+prefix+'/platform-fixture-repair-20260912/a1';return {name:'a1-failed-'+prefix,location,exactInventory:true,files:records(location,files(location))};})];
 for(const g of preserved)for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical input drift');
 const deps=prior.groups.find(g=>g.name==='frozen-maven-cache'),mvn=prior.groups.find(g=>g.name==='maven-distribution');
 copy(ROOT,SOURCE,source);copy(deps.location,REPO,deps.files);copy(mvn.location,MAVEN,mvn.files);
 const groups=[{name:'source',location:SOURCE,files:source},{name:'dependencies',location:REPO,files:deps.files},{name:'maven',location:MAVEN,files:mvn.files}].map(g=>({...g,id:aggregate(g.files)}));
 const tools=[process.execPath,JAVA,JAVA.replace('java.exe','javac.exe'),'C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe'].map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
 const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(tools)));
 write('input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'platform-fixture-repair-no-database-not-release-candidate',previousInputId:prior.id,
 groups,preserved,tools,changedFiles:changed,addedFiles:extra,migrationFiles:migrations,businessTests,methodBodyChanges:methodChanges,original63MethodBodiesUnchanged:false,
 businessMethodCount:63,coveragePurposesPreserved:true,databaseAuthorized:false,r5ActualExceptionKeyStillUnknown:true,
 branch:execFileSync('git',['branch','--show-current'],{encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim()});
 verify();console.log(JSON.stringify({id,sourceFiles:source.length,changedFiles:changed,methodBodyChanges:methodChanges,databaseAuthorized:false}));
}
async function command(exe,args,log){const env={...process.env,JAVA_HOME:path.dirname(path.dirname(JAVA))};
 for(const k of Object.keys(env))if(/^(PG|P3_|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(k))delete env[k];
 const r=await runProcess(exe,args,{cwd:SOURCE,env,timeoutMs:300000,drainMs:500});fs.writeFileSync(BASE+'/'+log,r.output,{flag:'wx'});
 if(r.exitCode!==0||!r.exited||r.timedOut||r.aborted||r.spawnError||!r.outputComplete)throw Error('Verification failed: '+log);
 return {exitCode:r.exitCode,exited:r.exited,timedOut:r.timedOut,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,elapsedMs:r.elapsedMs,logSha256:digest(BASE+'/'+log)};
}
async function build(){const m=verify();if(fs.existsSync(BUILD))throw Error('Do not reuse output');fs.mkdirSync(BUILD,{recursive:true});
 const mvn=extra=>['-Dmaven.home='+MAVEN,'-Dclassworlds.conf='+MAVEN+'/bin/m2.conf','-Dmaven.multiModuleProjectDirectory='+SOURCE,'-cp',MAVEN+'/boot/*','org.codehaus.plexus.classworlds.launcher.Launcher',
 '-o','-B','-ntp','-s',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-gs',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-Dmaven.repo.local='+REPO,'-Dwms.build.directory='+BUILD,
 '-Dp3.r2.database-authorized=false','-Dp3.r2.migration-authorized=false',...extra];
 const compile=await command(JAVA,mvn(['-DskipTests','package']),'compile.log');console.log('Fresh backend compile passed');
 const suites=[['com.wms.platformverification.PlatformR2IsolationTest',39],['com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest',7],
 ['com.wms.platformverification.PlatformFixtureScopeTest',29],['com.wms.system.platform.service.PlatformMfaFailureFixtureTest',22]];
 const java=await command(JAVA,mvn(['-Dtest='+suites.map(s=>s[0]).join(','),'-Dmaven.test.redirectTestOutputToFile=true','surefire:test']),'java-tests.log');
 const parser=await import(pathToFileURL(SOURCE+'/src/test/scripts/platform-test-results.mjs'));
 const guards=suites.map(([name,count])=>{const report=parser.readJUnitReport(BUILD+'/surefire-reports/TEST-'+name+'.xml');
 if(report.tests!==count||parser.assessSuite({name,methods:report.cases.map(c=>c.name)},{process:java,report}).status!=='PASSED')throw Error('Unexpected Java coverage: '+name);return report;});
 const nodeResults={};
 for(const [key,file,count] of [['ownership','platform-fixture-isolation.test.mjs',24],['lifecycle','platform-process-lifecycle.test.mjs',32],['sentinels','platform-upgrade-sentinels.test.mjs',65],['parser','platform-test-results.test.mjs',67]]){
 const result=await command(process.execPath,['--test','--test-reporter=tap','src/test/scripts/'+file],key+'.tap');
 const tap=fs.readFileSync(BASE+'/'+key+'.tap','utf8'),n=k=>Number(tap.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
 const summary={...result,tests:n('tests'),passed:n('pass'),failures:n('fail'),skipped:n('skipped'),cancelled:n('cancelled')};
 if(summary.tests!==count||summary.passed!==count||summary.failures||summary.skipped||summary.cancelled)throw Error('Unexpected Node coverage: '+key);nodeResults[key]=summary;console.log(key+': '+count+' passed');}
 const syntax=[];for(const file of ['platform-fixture-isolation.test.mjs','platform-fixture-repair-verification.mjs'])syntax.push({file,...await command(process.execPath,['--check','src/test/scripts/'+file],'syntax-'+file+'.log')});
 const compiled=records(BUILD,files(BUILD).filter(p=>p.endsWith('.class')||p.endsWith('.sql'))),oldBuild=read(PRIOR+'/backend-build.json');
 const differences=compiled.filter(f=>!oldBuild.compiled.some(o=>o.path===f.path&&o.sha256===f.sha256)).map(f=>f.path);
 if(differences.some(p=>p.startsWith('classes/')))throw Error('Product classes/SQL changed');
 const drift=m.groups.find(g=>g.name==='source').files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);if(drift.length)throw Error('Workspace drift');verify();
 write('verification-results.json',{inputId:m.id,completedAt:new Date().toISOString(),compile,java,guards,nodeResults,syntax,compiled,compiledAggregateSha256:aggregate(compiled),
 jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),artifactDifferences:differences,productClassOrSqlChanges:0,workspaceDrift:drift,oldClassesReused:false,
 original63MethodBodiesUnchanged:false,methodBodyChanges:m.methodBodyChanges,businessMethodCount:63,databaseTestsExecuted:0,databaseStarted:false,databaseConnected:false,
 oldR2R3R4R5Unchanged:true,frontendChecksExecuted:false,releaseCandidate:false});
 console.log(JSON.stringify({inputId:m.id,javaTests:guards.reduce((n,g)=>n+g.tests,0),nodeTests:Object.values(nodeResults).reduce((n,g)=>n+g.tests,0),databaseTestsExecuted:0,productClassOrSqlChanges:0}));
}
if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Fixed root/Node required');
const mode=process.argv[2];
try{if(mode==='seal')seal();else if(mode==='build')await build();else if(mode==='verify')console.log(JSON.stringify({id:verify().id,drift:false}));else throw Error('Only seal/build/verify; no database mode');}
catch(error){if(fs.existsSync(DELIVERY+'/input-manifest.json')&&!fs.existsSync(DELIVERY+'/'+mode+'-failure.json'))write(mode+'-failure.json',{at:new Date().toISOString(),status:'FAILED',stage:mode,error:error.message,databaseStarted:false,databaseConnected:false});throw error;}

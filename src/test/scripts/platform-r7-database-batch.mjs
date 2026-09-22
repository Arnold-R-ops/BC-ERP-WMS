// Dedicated, fail-closed batch. No product/default datasource changes. No frontend/HTTP server.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runProcess,startOwnedDatabase,stopOwnedDatabase,assertOwned,IdentityError,failureDetails} from './platform-process-lifecycle.mjs';
import {inspectWindowsEnvironment,requireInspectionCapability,confirmTargetAbsent,initializationPreflight,readPidFile} from './platform-launch-preflight.mjs';
export const withInitializationPreflight=initializationPreflight;
import {MIGRATIONS,sentinel,verifyMigrationContract,phaseRecorder,runUpgradeSentinels} from './platform-upgrade-sentinels.mjs';
import {readJUnitReport,assessSuite,runSuitePlan,summarizeSuiteResults} from './platform-test-results.mjs';
import {ROOT,RUN,SOURCE,REPO,MAVEN,BUILD,DELIVERY,JAVA,PG,sha,digest,readJson,writeJson,verify,requireRoot,files} from './platform-r7-backend-snapshot.mjs';
import {r6Plans,runR6Phases,summarizeR6,fixtureDiagnostics,requireCompleteScopes,newRegistry,registerScopes,validateSnapshot,ROOT_SNAPSHOT_SQL,targetStateSql,targetComparison,TARGET} from './platform-r6-stages.mjs';
import {executePlatformMavenSuite,LANDING_SUITE,inventory,requirePreserved,mavenBaseArgs} from './platform-report-runner.mjs';
export {r6Plans};
export const executePlatformSuite=executePlatformMavenSuite;
export const runR7Acceptance=runR6Phases;
export function finalizeR7Results(result,input){
 const summary=summarizeR6(input,result.r7?.phases??[]);
 result.r7=summary;result.suites=summary.fullRegression.suites;result.coverage=summary.fullRegression.coverage;
 result.databaseBusinessTestsStatus=summary.status;
 if(summary.status!=='PASSED')result.status='FAILED';
 return summary;
}
const PORT=55449, DATA=RUN+'/pgdata', MAIN='p3_m1_platform_release_r7', UPGRADE='p3_m1_platform_upgrade_r7';
const ADMIN='p3_r7_initializer', APP='p3_r7_app', MIG='p3_r7_migrator';
const PS='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const R6_STAGE_TEST_COUNT=57,REPORT_TEST_COUNT=60,R7_BINDING_TEST_COUNT=8,LIFECYCLE_TEST_COUNT=35,PREFLIGHT_TEST_COUNT=40;
export const REPORT_DIRECTORIES=Object.freeze({landing:BUILD+'/landing-reports',database:BUILD+'/reports'});
export function suiteContext(mode,pomSha256){
 if(!Object.hasOwn(REPORT_DIRECTORIES,mode)||!(/^[a-f0-9]{64}$/).test(pomSha256??''))throw Error('Exact report mode and POM fingerprint required');
 return {source:SOURCE,build:BUILD,maven:MAVEN,repo:REPO,java:JAVA,runDirectory:RUN,
 pom:SOURCE+'/platform-acceptance-pom.xml',pomSha256,reportRoot:REPORT_DIRECTORIES[mode]};
}
function assertArtifacts(built){
 if(digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar')!==built.jarSha256)throw Error('JAR drift');
 for(const f of built.compiled)if(digest(BUILD+'/'+f.path)!==f.sha256)throw Error('Compiled input drift');
}
function assertLandingAndDefault(built){
 requirePreserved(REPORT_DIRECTORIES.landing,built.landingReportFiles);
 requirePreserved(BUILD+'/surefire-reports',built.defaultReportFiles);
}
let manifest,migrationContract;
// Importing the real batch entry for result regression must not initialize or inspect a database.
function initializeBatch(){
requireRoot();
manifest=verify();
const sealedRunner=manifest.groups.find(g=>g.name==='backend-source').files.find(f=>f.path==='src/test/scripts/platform-r7-database-batch.mjs');
if(fileURLToPath(import.meta.url).replaceAll('\\','/').toLowerCase()!==(SOURCE+'/src/test/scripts/platform-r7-database-batch.mjs').toLowerCase()
  ||!sealedRunner||digest(fileURLToPath(import.meta.url))!==sealedRunner.sha256)throw Error('Unsealed runner; new authorized input/binding required');
migrationContract=verifyMigrationContract(Object.fromEntries(MIGRATIONS.map(n=>[n,fs.readFileSync(SOURCE+'/src/main/resources/db/migration/'+n,'utf8')])));
}
const credentials={};
const envBase={...process.env,JAVA_HOME:path.dirname(path.dirname(JAVA))};
for(const key of Object.keys(envBase))if(/^(PG|SPRING_|P3_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(key))delete envBase[key];
function redact(s){s=s.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[JWT REDACTED]');
  for(const value of Object.values(credentials))s=s.replaceAll(value,'[REDACTED]');return s;}
async function command(exe,args,{input='',env={},cwd=SOURCE,log,timeout=180000,signal,fileOutput=false}={}){
  const outputFile=fileOutput?RUN+'/'+log+'.launcher-output':undefined;
  const completed=await runProcess(exe,args,{cwd,env:{...envBase,...env},input,timeoutMs:timeout,signal,outputFile});
  const output=redact(outputFile&&fs.existsSync(outputFile)?fs.readFileSync(outputFile,'utf8'):completed.output);
  if(log)fs.writeFileSync(RUN+'/'+log,output,{flag:'wx'});
  return {...completed,timeout:completed.timedOut,output,log};
}
function okay(result,label){if(result.exitCode!==0||!result.exited||result.timeout||result.aborted||result.spawnError||!result.outputComplete)throw Error(label+' executor failed; exit='+result.exitCode+'; see '+result.log);return result.output;}
function listeners(){
  const output=execFileSync('C:/Windows/System32/netstat.exe',['-ano','-p','TCP'],{encoding:'utf8',windowsHide:true,timeout:3000});
  return output.split(/\r?\n/).map(line=>line.trim().split(/\s+/)).filter(v=>v[0]==='TCP'&&v[3]==='LISTENING')
    .map(v=>({address:v[1].slice(0,v[1].lastIndexOf(':')),port:Number(v[1].slice(v[1].lastIndexOf(':')+1)),pid:Number(v[4])}));
}
async function lifecycleListeners(signal){
  signal?.throwIfAborted();
  const output=okay(await command('C:/Windows/System32/netstat.exe',['-ano','-p','TCP'],{timeout:3000,signal}),'Listener inspection');
  return output.split(/\r?\n/).map(line=>line.trim().split(/\s+/)).filter(v=>v[0]==='TCP'&&v[3]==='LISTENING')
    .map(v=>({address:v[1].slice(0,v[1].lastIndexOf(':')),port:Number(v[1].slice(v[1].lastIndexOf(':')+1)),pid:Number(v[4])}));
}
const otherPorts=[5432,8080,5173,55439,18091,15173];
function others(){return listeners().filter(l=>otherPorts.includes(l.port)).sort((a,b)=>a.port-b.port||a.address.localeCompare(b.address));}
async function pgProcess(pid,signal){if(!Number.isInteger(pid)||pid<=0)throw new IdentityError('Invalid PID');
  signal?.throwIfAborted();
  const script=`$p=Get-CimInstance Win32_Process -Filter 'ProcessId=${pid}' -ErrorAction Stop; if(-not $p){'null'}else{@{ProcessId=$p.ProcessId;ExecutablePath=$p.ExecutablePath;CommandLine=$p.CommandLine;creationEpoch=([DateTimeOffset]$p.CreationDate).ToUnixTimeSeconds()}|ConvertTo-Json -Compress}`;
  return JSON.parse(okay(await command(PS,['-NoProfile','-NonInteractive','-Command',script],{timeout:3000,signal}),'Process inspection'));}
let activeExpected,activeIdentity,executionContext;
const inspectEnvironment=signal=>inspectWindowsEnvironment(command,{directory:DATA,port:PORT,signal});
async function observeLocalIdentity(signal){
  signal?.throwIfAborted();
  const pidText=readPidFile(DATA+'/postmaster.pid');if(pidText===null)return null;
  const pidRows=pidText.trim().split(/\r?\n/),pid=Number(pidRows[0]);
  if(pidRows[1]?.replaceAll('\\','/').toLowerCase()!==DATA.toLowerCase()||Number(pidRows[3])!==PORT||pidRows[5]!=='127.0.0.1')throw new IdentityError('Postmaster file mismatch');
  const process=await pgProcess(pid,signal);if(!process)throw new IdentityError('PID file without matching process');
  const exe=process.ExecutablePath?.replaceAll('\\','/');
  const commandDirectory=process.CommandLine?.match(/(?:^|\s)-D\s+"([^"]+)"/)?.[1]?.replaceAll('\\','/');
  signal?.throwIfAborted();
  const control=okay(await command(PG+'/pg_controldata.exe',['-D',DATA],{timeout:3000,signal}),'Physical cluster inspection');
  const clusterId=control.match(/Database system identifier:\s*(\d+)/)?.[1];
  const bound=(await lifecycleListeners(signal)).filter(l=>l.port===PORT);
  return {pid,processPid:process.ProcessId,startedEpoch:Number(pidRows[2]),processStartedEpoch:process.creationEpoch,
    executable:exe,commandDirectory,port:Number(pidRows[3]),address:pidRows[5],directory:pidRows[1],clusterId,listeners:bound};
}
async function localIdentity(){
  const local=assertOwned(activeExpected,await observeLocalIdentity(),activeIdentity);
  if(local.listeners.length!==1)throw new IdentityError('Required dedicated listener absent');return local;
}
async function noOwnedPostgres(pinned,signal){
  signal?.throwIfAborted();
  const evidence=await inspectEnvironment(signal);requireInspectionCapability(evidence,{context:executionContext});
  return confirmTargetAbsent(evidence,pinned).status==='CONFIRMED_ABSENT'&&(!pinned||await pgProcess(pinned.pid,signal)===null);
}
function mavenArgs(extra){return ['-Dmaven.home='+MAVEN,'-Dclassworlds.conf='+MAVEN+'/bin/m2.conf','-Dmaven.multiModuleProjectDirectory='+SOURCE,
  '-cp',MAVEN+'/boot/*','org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-ntp',
  '-s',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-gs',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-Dmaven.repo.local='+REPO,
  '-Dwms.build.directory='+BUILD,...extra];}
function classpath(){return [BUILD+'/test-classes',BUILD+'/classes',...manifest.testClasspath.map(j=>REPO+'/'+j.path)].join(';');}
async function build(){
 if(fs.existsSync(BUILD))throw Error('Never reuse build directory');
 fs.mkdirSync(BUILD,{recursive:true});
 const context=suiteContext('landing',manifest.derivedTestPom.sha256);
 const mvn=extra=>[...mavenBaseArgs(context,{dedicated:false}),'-Dp3.r2.database-authorized=false','-Dp3.r2.migration-authorized=false',...extra];
 const commands=[];
 async function checked(exe,args,log,timeout=300000){
  const r=await command(exe,args,{log,timeout});
  commands.push({executable:exe,args,cwd:SOURCE,exitCode:r.exitCode,exited:r.exited,outputComplete:r.outputComplete,terminationConfirmed:r.terminationConfirmed,log,logSha256:digest(RUN+'/'+log)});
  okay(r,log);if(!r.terminationConfirmed)throw Error('Command termination unverified');return r;
 }
 for(const [exe,args,log,pattern] of [
  [process.execPath,['--version'],'node-version.log',/^v24\.12\.0\s*$/],
  [JAVA,['-version'],'java-version.log',/version "17\.0\.17"/],
  [JAVA,mvn(['--version']),'maven-version.log',/Apache Maven 3\.9\.9\b/],
  [PG+'/postgres.exe',['--version'],'postgres-version.log',/PostgreSQL\) 18\.6\b/]]){
  const r=await checked(exe,args,log);if(!pattern.test(r.output))throw Error('Fixed version mismatch: '+log);
 }
 await checked(JAVA,mvn(['-DskipTests','package']),'backend-compile-r7.log');
 console.log('Fresh R7 compilation passed');
 const names=[['com.wms.platformverification.PlatformR2IsolationTest',41],['com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest',7],
 ['com.wms.platformverification.PlatformFixtureScopeTest',29],['com.wms.system.platform.service.PlatformMfaFailureFixtureTest',22]];
 const test=await checked(JAVA,mvn(['-Dtest='+names.map(n=>n[0]).join(','),'-Dmaven.test.redirectTestOutputToFile=true','surefire:test']),'backend-guards-r7.log');
 const guards=names.map(([name,count])=>{const g=readJUnitReport(BUILD+'/surefire-reports/TEST-'+name+'.xml');
  if(g.tests!==count||assessSuite({name,methods:g.cases.map(c=>c.name)},{process:test,report:g}).status!=='PASSED')throw Error('Java gate coverage mismatch');return g;});
 console.log('No-database Java gates: 99 passed');
 const nodeResults={};
 for(const [key,script,expected] of [['ownership','platform-fixture-isolation.test.mjs',24],['lifecycle','platform-process-lifecycle.test.mjs',LIFECYCLE_TEST_COUNT],
 ['sentinels','platform-upgrade-sentinels.test.mjs',65],['parser','platform-test-results.test.mjs',67],['stages','platform-r6-stages.test.mjs',R6_STAGE_TEST_COUNT],
 ['reports','platform-report-runner.test.mjs',REPORT_TEST_COUNT],['r7binding','platform-r7-binding.test.mjs',R7_BINDING_TEST_COUNT],['identityPreflight','platform-launch-preflight.test.mjs',PREFLIGHT_TEST_COUNT]]){
  const r=await checked(process.execPath,['--test','--test-reporter=tap',SOURCE+'/src/test/scripts/'+script],key+'-tests.tap');
  const n=k=>Number(r.output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const row={tests:n('tests'),passed:n('pass'),failures:n('fail'),skipped:n('skipped'),cancelled:n('cancelled'),logSha256:digest(RUN+'/'+r.log)};
  if(row.tests!==expected||row.passed!==expected||row.failures||row.skipped||row.cancelled)throw Error('Node gate mismatch: '+key);
  nodeResults[key]=row;console.log(key+': '+expected+' passed');
 }
 const syntax=[];
 for(const script of ['platform-r7-backend-snapshot.mjs','platform-r7-database-batch.mjs','platform-r7-binding.test.mjs',
 'platform-r6-stages.mjs','platform-r6-stages.test.mjs','platform-report-runner.mjs','platform-report-runner.test.mjs',
 'platform-process-lifecycle.mjs','platform-process-lifecycle.test.mjs','platform-upgrade-sentinels.mjs','platform-upgrade-sentinels.test.mjs',
 'platform-test-results.mjs','platform-test-results.test.mjs','platform-fixture-isolation.test.mjs','platform-launch-preflight.mjs','platform-launch-preflight.test.mjs']){
  const r=await checked(process.execPath,['--check',SOURCE+'/src/test/scripts/'+script],'syntax-'+script+'.log');
  syntax.push({script,exitCode:r.exitCode,logSha256:digest(RUN+'/'+r.log)});
 }
 const compiled=files(BUILD).filter(p=>p.endsWith('.class')||p.endsWith('.sql')).sort().map(p=>({path:path.relative(BUILD,p).replaceAll('\\','/'),sha256:digest(p)}));
 const prior=readJson(ROOT+'/docs/releases/platform-report-binding-repair-20260913/a2/verification-results.json');
 const differences=compiled.filter(f=>!prior.compiled.some(o=>o.path===f.path&&o.sha256===f.sha256)).map(f=>f.path);
 if(differences.some(p=>p.startsWith('classes/')))throw Error('Product class or migration changed');
 const built={inputId:manifest.id,compiledAt:new Date().toISOString(),commands,guards,nodeResults,migrationContract,syntax,
 jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),compiled,
 compiledAggregateSha256:sha(Buffer.from(compiled.map(f=>f.path+'\0'+f.sha256+'\n').join(''))),
 reportRepairClassOrSqlDifferences:differences,productClassOrSqlChanges:0,oldClassesReused:false,
 revised63MethodBodiesUnchanged:true,derivedTestPom:manifest.derivedTestPom,reportDirectories:REPORT_DIRECTORIES,landings:[]};
 const defaultFiles=inventory(BUILD+'/surefire-reports');
 if(fs.existsSync(REPORT_DIRECTORIES.database))throw Error('Database report directory already exists before landing');
 for(const phase of ['independent','sequential','full']){
  console.log('Real Maven no-database landing: '+phase);
  const probe=await executePlatformSuite({context,phase,suite:LANDING_SUITE,mode:'landing'});
  const e=probe.evidence;
  writeJson(DELIVERY+'/landing/'+phase+'.json',{inputId:manifest.id,jarSha256:built.jarSha256,...e,databaseBusinessTestsExecuted:0});
  if(e.firstFailure||e.assessment.status!=='PASSED'||e.assessment.counts.passed!==1||e.protectedDirectories.some(p=>p.status!=='PASSED'))throw Error('Landing failed: '+phase);
  requirePreserved(BUILD+'/surefire-reports',defaultFiles);assertArtifacts(built);
  if(fs.existsSync(REPORT_DIRECTORIES.database))throw Error('Landing wrote into database reports');
  built.landings.push(e);console.log('Landing '+phase+' verified; database reports absent');
 }
 built.landingReportFiles=inventory(REPORT_DIRECTORIES.landing);built.defaultReportFiles=defaultFiles;
 built.landingCompletedAt=new Date().toISOString();built.databaseBusinessTestsExecuted=0;
 verify();
 writeJson(DELIVERY+'/backend-build.json',built);
 console.log(JSON.stringify({compiled:true,inputId:manifest.id,javaTests:99,nodeTests:Object.values(nodeResults).reduce((n,r)=>n+r.tests,0),realLandingExecutions:3,databaseBusinessTestsExecuted:0}));
}
async function database(){
  if(process.argv[3]!=='--authorized-20260914-r7-platform-backend-only')throw Error('Separate DB authorization required');
  const built=readJson(DELIVERY+'/backend-build.json');
  if(built.inputId!==manifest.id||built.guards?.reduce((n,g)=>n+g.tests,0)!==99||built.nodeResults?.ownership?.passed!==24||built.nodeResults?.lifecycle?.passed!==LIFECYCLE_TEST_COUNT||built.nodeResults?.identityPreflight?.passed!==PREFLIGHT_TEST_COUNT
   ||built.nodeResults?.sentinels?.passed!==65||built.nodeResults?.parser?.passed!==67||built.nodeResults?.stages?.passed!==R6_STAGE_TEST_COUNT
   ||built.nodeResults?.reports?.passed!==REPORT_TEST_COUNT||built.nodeResults?.r7binding?.passed!==R7_BINDING_TEST_COUNT
   ||built.landings?.length!==3||built.landings.some(e=>e.firstFailure||e.assessment.status!=='PASSED')
   ||JSON.stringify(built.reportDirectories)!==JSON.stringify(REPORT_DIRECTORIES))throw Error('Build/input or landing gate mismatch');
  assertArtifacts(built);assertLandingAndDefault(built);
  if(fs.existsSync(REPORT_DIRECTORIES.database))throw Error('Database report root already exists; no old result fallback');
  if(fs.existsSync(DELIVERY+'/database-result.json'))throw Error('Existing batch evidence; STOP');
  const before=others(),startedAt=new Date().toISOString();
  const result={inputId:manifest.id,jarSha256:built.jarSha256,startedAt,beforeOtherListeners:before,steps:[],suites:[],status:'RUNNING',databaseBusinessTestsStatus:'NOT_EXECUTED',frontendStarted:false,httpServerStarted:false};
  const phase=phaseRecorder(result),upgradeFixture=sentinel();
  result.upgradeFixtureId=upgradeFixture.id;
  let attemptedStart=false,identity,clusterId,stage='identity-preflight',adapter;
  for(const role of [ADMIN,MIG,APP])credentials[role]=crypto.randomBytes(32).toString('hex');
  const pwfile=RUN+'/init-password.ephemeral';
  async function sql(database,role,statement,label){
    if(![MAIN,UPGRADE,'postgres'].includes(database)||!Object.hasOwn(credentials,role))throw Error('Unapproved SQL destination');
    await localIdentity();
    const assertion=`DO $r2$ BEGIN IF current_database()<>'${database}' OR host(inet_server_addr())<>'127.0.0.1' OR inet_server_port()<>55449 OR current_setting('data_directory')<>'${DATA}' OR (pg_control_system()).system_identifier::text<>'${clusterId}' THEN RAISE EXCEPTION 'R7 physical identity mismatch'; END IF; END $r2$;\n`;
    // The application role uses its restricted read-only identity functions; no pg_control_system grant.
    const guarded=role===APP?assertion.replaceAll("current_setting('data_directory')","p3_r7_control.data_directory()").replaceAll('(pg_control_system()).system_identifier::text','p3_r7_control.cluster_id()'):assertion;
    return okay(await command(PG+'/psql.exe',['-X','-w','-h','127.0.0.1','-p','55449','-U',role,'-d',database,'-v','ON_ERROR_STOP=1','-q','-A','-t'],
      {input:guarded+statement,env:{PGPASSWORD:credentials[role],PGCONNECT_TIMEOUT:'5'},log:label+'.log'}),label).trim().replaceAll('\r\n','\n');
  }
  async function migrate(database,step){
    return phase('migration:'+step,'MIGRATION',async()=>{
    stage=step;await localIdentity();
    const out=await command(JAVA,['-Dp3.r2.migration-authorized=true','-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55449/'+database,
      '-Dp3.r2.cluster-id='+clusterId,'-cp',classpath(),'com.wms.platformverification.PlatformR2DatabasePreparer',step],
      {env:{P3_R2_MIGRATOR_PASSWORD:credentials[MIG]},log:step+'.log'});
    result.steps.push({step,database,exitCode:out.exitCode,timeout:out.timeout,elapsedMs:out.elapsedMs,logSha256:digest(RUN+'/'+out.log)});
    okay(out,step);
    result.steps.at(-1).history=await sql(database,MIG,"select version||':'||type||':'||script||':'||checksum||':'||success from flyway_schema_history order by installed_rank;",step+'-history');
    console.log('Completed migration step: '+step);
    });
  }
  try{
    const init=await withInitializationPreflight({inspect:()=>inspectEnvironment(),record:row=>{
      result.environmentPreflight=row;if(row.status==='PASSED')executionContext=row.context;
    },initialize:async()=>{
    stage='initialize';
    fs.writeFileSync(pwfile,credentials[ADMIN]+'\n',{flag:'wx',mode:0o600});
    return command(PG+'/initdb.exe',['-D',DATA,'-U',ADMIN,'--pwfile='+pwfile,'--auth-host=scram-sha-256','--auth-local=scram-sha-256','--encoding=UTF8','--locale=C',
      '-c','listen_addresses=127.0.0.1','-c','port=55449','-c','max_connections=60','-c','log_statement=none','-c','log_min_error_statement=panic','-c','log_parameter_max_length_on_error=0'],{log:'initdb.log'});
    }});
    fs.unlinkSync(pwfile);okay(init,'initdb');
    // Restrict the new cluster only. No existing configuration files are read or changed.
    fs.writeFileSync(DATA+'/pg_hba.conf',`host postgres ${ADMIN} 127.0.0.1/32 scram-sha-256\nhost ${MAIN},${UPGRADE} ${ADMIN},${MIG},${APP} 127.0.0.1/32 scram-sha-256\nhost all all 0.0.0.0/0 reject\nhost all all ::0/0 reject\n`);
    const control=okay(await command(PG+'/pg_controldata.exe',['-D',DATA],{log:'control-before-start.log'}),'pg_controldata');
    clusterId=control.match(/Database system identifier:\s*(\d+)/)?.[1];if(!clusterId)throw Error('Cannot establish cluster identifier');
    if(listeners().some(l=>l.port===PORT))throw Error('Port acquired by another process; STOP');
    stage='start';
    result.beforeStartEnvironment=await inspectEnvironment();requireInspectionCapability(result.beforeStartEnvironment,{context:executionContext});
    if(confirmTargetAbsent(result.beforeStartEnvironment).status!=='CONFIRMED_ABSENT')throw new IdentityError('Target changed before launch');
    activeExpected={directory:DATA,executable:PG+'/postgres.exe',port:PORT,address:'127.0.0.1',clusterId,database:'postgres',user:ADMIN,
      earliestStartEpoch:Math.floor(Date.now()/1000)-2,latestStartEpoch:Math.floor(Date.now()/1000)+90};
    let readinessAttempt=0;
    adapter={
      launch:signal=>command(PG+'/pg_ctl.exe',['start','-D',DATA,'-l',RUN+'/postgres-private.log','-w','-t','30'],
        {log:'pg-start.log',timeout:35000,signal,fileOutput:true}),
      inspectLocal:async signal=>{signal.throwIfAborted();return observeLocalIdentity(signal);},
      probeReady:async(_,signal)=>{
        signal.throwIfAborted();
        const probe=await command(PG+'/psql.exe',['-X','-w','-h','127.0.0.1','-p','55449','-U',ADMIN,'-d','postgres','-v','ON_ERROR_STOP=1','-q','-A','-t'],
          {input:"select json_build_object('database',current_database(),'user',current_user,'port',inet_server_port(),'address',host(inet_server_addr()),'listenAddresses',current_setting('listen_addresses'),'directory',current_setting('data_directory'),'clusterId',(pg_control_system()).system_identifier::text);",
            env:{PGPASSWORD:credentials[ADMIN],PGCONNECT_TIMEOUT:'2',PGOPTIONS:'-c statement_timeout=2000'},log:'ready-'+(++readinessAttempt)+'.log',timeout:4000,signal});
        signal.throwIfAborted();if(probe.exitCode!==0||probe.timeout||probe.aborted)return null;
        return JSON.parse(okay(probe,'readiness probe').trim());
      },
      stop:async(_,signal)=>{
        signal.throwIfAborted();
        return command(PG+'/pg_ctl.exe',['stop','-D',DATA,'-m','fast','-w','-t','30'],{log:'pg-stop.log',timeout:32000,signal,fileOutput:true});
      },
      isStopped:async(signal,pinned)=>{signal.throwIfAborted();return await noOwnedPostgres(pinned,signal);}
    };
    attemptedStart=true;
    result.lifecycle=await startOwnedDatabase(adapter,activeExpected,{launcherMs:37000,readinessMs:10000,cleanupMs:45000});
    if(result.lifecycle.status!=='READY'){result.firstFailure??=result.lifecycle.firstFailure;throw Error('Startup classification: '+result.lifecycle.status);}
    activeIdentity=result.lifecycle.identity;
    identity=await localIdentity();result.identity={...identity,clusterId};
    writeJson(DELIVERY+'/environment-started.json',{...result.identity,inputId:manifest.id,startedAt,beforeOtherListeners:before});
    const live=await sql('postgres',ADMIN,"select current_database(),current_user,host(inet_server_addr()),inet_server_port(),current_setting('listen_addresses'),current_setting('data_directory'),(pg_control_system()).system_identifier;",'live-cluster-identity');
    result.liveIdentity=live;
    stage='roles-and-databases';
    await sql('postgres',ADMIN,`CREATE ROLE ${MIG} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD '${credentials[MIG]}';\nCREATE ROLE ${APP} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD '${credentials[APP]}';\nGRANT pg_read_all_settings TO ${MIG};\nCREATE DATABASE ${MAIN} OWNER ${MIG} TEMPLATE template0;\nCREATE DATABASE ${UPGRADE} OWNER ${MIG} TEMPLATE template0;\n`,'bootstrap');
    for(const db of [MAIN,UPGRADE])await sql(db,ADMIN,`REVOKE ALL ON DATABASE ${db} FROM public; GRANT CONNECT ON DATABASE ${db} TO ${MIG},${APP}; GRANT EXECUTE ON FUNCTION pg_catalog.pg_control_system() TO ${MIG};`,'bootstrap-'+db);
    // pg_read_all_settings membership is deliberately non-inherited: grant SET parameter read via role inheritance is needed.
    // Use the explicit read privilege membership, without changing runtime role or granting superuser/BYPASSRLS.
    await sql('postgres',ADMIN,`GRANT pg_read_all_settings TO ${MIG} WITH INHERIT TRUE;`,'migrator-read-identity');
    await migrate(MAIN,'prepare-release');
    result.upgradeSentinels=await runUpgradeSentinels({fixture:upgradeFixture,
      sql:(statement,label)=>sql(UPGRADE,MIG,statement,label),migrate:step=>migrate(UPGRADE,step),phase});
    stage='runtime-permissions';
    result.runtimePermissions=await sql(MAIN,APP,"select current_user,rolsuper,rolbypassrls from pg_roles where rolname=current_user; select has_table_privilege(current_user,'flyway_schema_history','INSERT'),has_table_privilege(current_user,'flyway_schema_history','UPDATE'),has_table_privilege(current_user,'flyway_schema_history','DELETE'),has_table_privilege(current_user,'p3_r7_control.ready','UPDATE'); select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname in ('public','p3_r7_control') and c.relowner=(select oid from pg_roles where rolname=current_user);",'runtime-permissions');
    if(result.runtimePermissions!==APP+'|f|f\nf|f|f|f\n0')throw Error('Runtime permissions mismatch');
    result.fixtureChecks=[];result.targetComparisons=[];
    let registry=newRegistry(),previousSnapshot=null,firstTarget=null;
    const initial=JSON.parse(await sql(MAIN,MIG,ROOT_SNAPSHOT_SQL,'fixtures-initial'));
    validateSnapshot(initial,registry);previousSnapshot=initial;
    result.initialFixtureSnapshot=initial;
    result.databaseBusinessTestsStatus='RUNNING';
    const acceptance=await runR7Acceptance({input:manifest,
      beforeSuite:async(p,suite)=>{
        stage=p.id+':'+suite.name.split('.').at(-1);assertArtifacts(built);assertLandingAndDefault(built);await localIdentity();
        const observed=JSON.parse(await sql(MAIN,MIG,ROOT_SNAPSHOT_SQL,'fixtures-'+p.id+'-'+suite.name.split('.').at(-1)+'-before'));
        validateSnapshot(observed,registry,previousSnapshot);previousSnapshot=observed;
      },
      onUpdate:summary=>{result.r7=summary;},
      execute:async(p,suite)=>{
        console.log('Starting '+p.id+': '+suite.name.split('.').at(-1));
        return executePlatformSuite({context:suiteContext('database',manifest.derivedTestPom.sha256),
          phase:p.id,suite,mode:'database',database:{url:'jdbc:postgresql://127.0.0.1:55449/'+MAIN,clusterId},appPassword:credentials[APP]});
      },
      afterSuite:async(p,suite,outcome,payload)=>{
        const check={phase:p.id,suite:suite.name,businessStatus:outcome.status,status:'RUNNING'};result.fixtureChecks.push(check);
        try{
          check.reportEvidence=payload?.evidence??null;
          assertArtifacts(built);assertLandingAndDefault(built);
          if(payload?.evidence?.reportPath?.status!=='PASSED'){
            const error=new Error('Expected phase report artifacts unavailable');error.category='REPORT_PATH_ERROR';throw error;
          }
          if(payload.evidence.fixtureLog?.status!=='PASSED'){
            const error=new Error('Phase fixture log invalid');error.category='FIXTURE_LOG_ERROR';throw error;
          }
          check.outputSha256=payload.evidence.outputSha256;check.diagnostics=payload.evidence.diagnostics;
          const snapshot=JSON.parse(await sql(MAIN,MIG,ROOT_SNAPSHOT_SQL,'fixtures-'+p.id+'-'+suite.name.split('.').at(-1)+'-after'));
          check.snapshot=snapshot;
          requireCompleteScopes(check.diagnostics,suite);
          const registered=registerScopes(registry,check.diagnostics.scopes);
          validateSnapshot(snapshot,registered,previousSnapshot);
          registry=registered;previousSnapshot=snapshot;
          if(suite.name===TARGET){
            if(check.diagnostics.scopes.length!==1||check.diagnostics.scopes[0].userIds.length!==1)throw Error('MFA fixture ownership mismatch');
            const actorId=check.diagnostics.scopes[0].userIds[0];
            check.targetPersisted=JSON.parse(await sql(MAIN,MIG,targetStateSql(actorId),'target-persisted-'+p.id));
            check.targetComparison=targetComparison(check.diagnostics,check.targetPersisted,firstTarget);
            firstTarget??=check.targetComparison;result.targetComparisons.push({phase:p.id,value:check.targetComparison});
          }
          check.status='PASSED';console.log('Completed '+p.id+': '+suite.name.split('.').at(-1)+' '+outcome.status+'; fixtures reconciled');
        }catch(error){check.status='FAILED';check.category=error.category??'FIXTURE_CLEANUP_ERROR';check.failure=redact(error.message);throw error;}
      }
    });
    if(acceptance.status!=='PASSED'){
      result.failedStage=stage;result.firstFailure=acceptance.firstFailure;result.failureCategory=acceptance.firstFailure?.category
        ??acceptance.phases.find(p=>p.failureCategory)?.failureCategory??'INCOMPLETE';
      throw Error('R7 acceptance stopped: '+result.failureCategory);
    }
    result.databaseBusinessTestsStatus='PASSED';result.status='PASSED';
  }catch(error){result.status='FAILED';result.failedStage??=stage;result.failureCategory??='BATCH';
    result.firstFailure??=failureDetails(error,stage);
    if(result.databaseBusinessTestsStatus==='RUNNING')result.databaseBusinessTestsStatus='FAILED';
    result.failure=redact(error.message);console.log('Batch stopped: '+result.failure);}
  finally{
    if(fs.existsSync(pwfile))fs.unlinkSync(pwfile);
    result.stop={attempted:false};
    try{
      if(result.lifecycle?.cleanup){
        // Preserve a failed/refused cleanup; never secretly retry it against a changed identity.
        result.stop=result.lifecycle.cleanup;
      }else if(attemptedStart){
        result.stop=await stopOwnedDatabase(adapter,activeExpected,activeIdentity,{cleanupMs:45000});
      }else result.stop={attempted:false,completed:false,status:'NOT_STARTED_NO_STOP_PERFORMED'};
      if(!result.stop.completed)result.status='FAILED';
    }catch(error){result.stop.failure=redact(error.message);result.status='FAILED';}
    result.afterOtherListeners=others();result.otherListenersUnchanged=JSON.stringify(before)===JSON.stringify(result.afterOtherListeners);
    result.completedAt=new Date().toISOString();
    try{assertArtifacts(built);assertLandingAndDefault(built);result.compiledAndLandingUnchanged=true;result.snapshotStillMatches=verify().id===manifest.id;}catch(error){result.snapshotStillMatches=false;result.inputVerificationFailure=redact(error.message);result.status='FAILED';}
    if(!result.otherListenersUnchanged)result.status='FAILED';
    finalizeR7Results(result,manifest);
    for(const p of result.r7.phases)writeJson(DELIVERY+'/phases/'+p.id+'.json',{inputId:manifest.id,jarSha256:built.jarSha256,...p,
      reportDirectory:BUILD+'/reports/'+p.id,fixtureChecks:result.fixtureChecks?.filter(c=>c.phase===p.id)??[]});
    result.finalDedicatedListeners=listeners().filter(l=>l.port===PORT);result.finalPidFilePresent=fs.existsSync(DATA+'/postmaster.pid');
    result.databaseDirectoryPreserved=fs.existsSync(DATA);result.temporaryPasswordFileRemoved=!fs.existsSync(pwfile);
    writeJson(DELIVERY+'/database-result.json',result);
    console.log(JSON.stringify({status:result.status,failedStage:result.failedStage,migrationSteps:result.steps.length,executionInstances:result.r7.executionInstances,plannedSuites:result.coverage.plannedSuites,executedSuites:result.coverage.executedSuites,stopped:result.stop.completed,otherListenersUnchanged:result.otherListenersUnchanged}));
  }
  if(result.status!=='PASSED'||!result.stop.completed)process.exitCode=1;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  if(!['build','database'].includes(process.argv[2]))throw Error('Only build/database');
  initializeBatch();
  if(process.argv[2]==='build'){
    try{await build();}catch(error){writeJson(DELIVERY+'/preparation-failure.json',{status:'FAILED',stage:'PREPARATION',error:error.message,databaseStarted:false,databaseBusinessTestsExecuted:0});throw error;}
  }else await database();
}

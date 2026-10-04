// Dedicated, fail-closed batch. No product/default datasource changes. No frontend/HTTP server.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runProcess,startOwnedDatabase,stopOwnedDatabase,assertOwned,IdentityError} from './platform-process-lifecycle.mjs';
import {MIGRATIONS,sentinel,verifyMigrationContract,phaseRecorder,runUpgradeSentinels} from './platform-upgrade-sentinels.mjs';
import {ROOT,RUN,SOURCE,REPO,MAVEN,BUILD,DELIVERY,JAVA,PG,sha,digest,readJson,writeJson,verify,requireRoot,files} from './platform-r3-backend-snapshot.mjs';
requireRoot();
const PORT=55449, DATA=RUN+'/pgdata', MAIN='p3_m1_platform_release_r3', UPGRADE='p3_m1_platform_upgrade_r3';
const ADMIN='p3_r3_initializer', APP='p3_r3_app', MIG='p3_r3_migrator';
const PS='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const manifest=verify();
const sealedRunner=manifest.groups.find(g=>g.name==='backend-source').files.find(f=>f.path==='src/test/scripts/platform-r3-database-batch.mjs');
if(fileURLToPath(import.meta.url).replaceAll('\\','/').toLowerCase()!==(SOURCE+'/src/test/scripts/platform-r3-database-batch.mjs').toLowerCase()
  ||!sealedRunner||digest(fileURLToPath(import.meta.url))!==sealedRunner.sha256)throw Error('Unsealed runner; new authorized input/binding required');
const migrationContract=verifyMigrationContract(Object.fromEntries(MIGRATIONS.map(n=>[n,fs.readFileSync(SOURCE+'/src/main/resources/db/migration/'+n,'utf8')])));
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
let activeExpected,activeIdentity;
async function observeLocalIdentity(signal){
  signal?.throwIfAborted();
  if(!fs.existsSync(DATA+'/postmaster.pid'))return null;
  const pidRows=fs.readFileSync(DATA+'/postmaster.pid','utf8').trim().split(/\r?\n/),pid=Number(pidRows[0]);
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
  const script=`@(Get-CimInstance Win32_Process -Filter "Name='postgres.exe'" -ErrorAction Stop|Where-Object {$_.CommandLine.Replace('\\','/').Contains('${DATA}')${pinned?' -or $_.ParentProcessId -eq '+pinned.pid:''}}).Count`;
  const count=Number(okay(await command(PS,['-NoProfile','-NonInteractive','-Command',script],{timeout:3000,signal}),'Remaining process inspection').trim());
  return count===0&&(!pinned||await pgProcess(pinned.pid,signal)===null);
}
function mavenArgs(extra){return ['-Dmaven.home='+MAVEN,'-Dclassworlds.conf='+MAVEN+'/bin/m2.conf','-Dmaven.multiModuleProjectDirectory='+SOURCE,
  '-cp',MAVEN+'/boot/*','org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-ntp',
  '-s',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-gs',SOURCE+'/src/test/resources/platform-r2-maven-settings.xml','-Dmaven.repo.local='+REPO,
  '-Dwms.build.directory='+BUILD,...extra];}
function classpath(){return [BUILD+'/test-classes',BUILD+'/classes',...manifest.testClasspath.map(j=>REPO+'/'+j.path)].join(';');}
function report(xmlFile){
  const xml=fs.readFileSync(xmlFile,'utf8'),head=xml.match(/<testsuite\s[^>]+>/)?.[0];if(!head)throw Error('No suite report');
  const attr=(s,k)=>s.match(new RegExp('\\b'+k+'="([^"]*)"'))?.[1];
  return {name:attr(head,'name'),tests:Number(attr(head,'tests')),failures:Number(attr(head,'failures')),errors:Number(attr(head,'errors')),skipped:Number(attr(head,'skipped')),
    xmlSha256:sha(Buffer.from(xml)),cases:[...xml.matchAll(/<testcase\s([^>]*)(?:\/>|>([\s\S]*?)<\/testcase>)/g)].map(m=>({name:attr(m[1],'name'),
      failure:!!m[2]?.match(/<failure\b/),error:!!m[2]?.match(/<error\b/),skipped:!!m[2]?.match(/<skipped\b/)}))};
}
async function build(){
  if(fs.existsSync(BUILD))throw Error('Never reuse build directory');
  fs.mkdirSync(BUILD,{recursive:true});
  const result=await command(JAVA,mavenArgs(['-DskipTests','package']),{log:'backend-compile-r3.log',timeout:300000});okay(result,'Fresh backend compilation');
  const test=await command(JAVA,mavenArgs(['-Dtest=com.wms.platformverification.PlatformR2IsolationTest,com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest',
    '-Dmaven.test.redirectTestOutputToFile=true','surefire:test']),{log:'backend-guards-r3.log'});okay(test,'No-database guards and role fixtures');
  const guards=['com.wms.platformverification.PlatformR2IsolationTest','com.wms.system.platform.service.PlatformAccessGrantRoleFixtureTest']
    .map(name=>report(BUILD+'/surefire-reports/TEST-'+name+'.xml'));
  if(guards[0].tests!==37||guards[1].tests!==7||guards.some(g=>g.failures||g.errors||g.skipped))throw Error('Guard coverage mismatch');
  const syntax=[];
  for(const script of ['platform-process-lifecycle.mjs','platform-process-lifecycle.test.mjs','platform-r3-backend-snapshot.mjs','platform-r3-database-batch.mjs','platform-upgrade-sentinels.mjs','platform-upgrade-sentinels.test.mjs']){
    const checked=await command(process.execPath,['--check',SOURCE+'/src/test/scripts/'+script],{log:'syntax-'+script+'.log'});okay(checked,'Script syntax');
    syntax.push({script,exitCode:checked.exitCode,logSha256:digest(RUN+'/'+checked.log)});
  }
  const lifecycleRun=await command(process.execPath,['--test','--test-reporter=tap',SOURCE+'/src/test/scripts/platform-process-lifecycle.test.mjs'],{log:'lifecycle-tests.tap'});
  okay(lifecycleRun,'No-database lifecycle regression');
  const count=k=>Number(lifecycleRun.output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const lifecycle={tests:count('tests'),passed:count('pass'),failures:count('fail'),skipped:count('skipped'),cancelled:count('cancelled'),logSha256:digest(RUN+'/lifecycle-tests.tap')};
  if(lifecycle.tests!==32||lifecycle.passed!==32||lifecycle.failures||lifecycle.skipped||lifecycle.cancelled)throw Error('Lifecycle coverage mismatch');
  const sentinelRun=await command(process.execPath,['--test','--test-reporter=tap',SOURCE+'/src/test/scripts/platform-upgrade-sentinels.test.mjs'],{log:'sentinel-tests.tap'});
  okay(sentinelRun,'No-database sentinel contract regression');
  const sc=k=>Number(sentinelRun.output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const sentinels={tests:sc('tests'),passed:sc('pass'),failures:sc('fail'),skipped:sc('skipped'),cancelled:sc('cancelled'),logSha256:digest(RUN+'/sentinel-tests.tap')};
  if(sentinels.tests!==65||sentinels.passed!==65||sentinels.failures||sentinels.skipped||sentinels.cancelled)throw Error('Sentinel coverage mismatch');
  const compiled=files(BUILD).filter(p=>p.endsWith('.class')||p.includes(path.sep+'classes'+path.sep)&&p.endsWith('.sql')).sort().map(p=>({path:path.relative(BUILD,p).replaceAll('\\','/'),sha256:digest(p)}));
  const old=ROOT+'/target/platform-tool-repair-20260911/a2/backend';
  const differences=compiled.filter(f=>!fs.existsSync(old+'/'+f.path)||digest(old+'/'+f.path)!==f.sha256).map(f=>f.path);
  if(differences.some(p=>p.startsWith('classes/')))throw Error('Product class or migration changed');
  verify();
  writeJson(DELIVERY+'/backend-build.json',{inputId:manifest.id,compiledAt:new Date().toISOString(),exitCode:result.exitCode,guards,lifecycle,sentinels,migrationContract,syntax,
    jarSha256:digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar'),compileLogSha256:digest(RUN+'/backend-compile-r3.log'),compiled,
    repairClassOrSqlDifferences:differences,productClassOrSqlChanges:0,oldClassesReused:false,databaseConnections:false});
  console.log(JSON.stringify({compiled:true,guardAndFixtureTests:44,lifecycleTests:32,repairClassOrSqlDifferences:differences.length,inputId:manifest.id}));
}
async function database(){
  if(process.argv[3]!=='--authorized-20260911-r3-platform-backend-only')throw Error('Separate DB authorization required');
  const built=readJson(DELIVERY+'/backend-build.json');if(built.inputId!==manifest.id||built.guards?.reduce((n,g)=>n+g.tests,0)!==44||built.lifecycle?.passed!==32||built.sentinels?.passed!==65||digest(BUILD+'/wms-system-0.0.1-SNAPSHOT.jar')!==built.jarSha256)throw Error('Build/input mismatch');
  for(const file of built.compiled)if(digest(BUILD+'/'+file.path)!==file.sha256)throw Error('Compiled input drift');
  if(fs.existsSync(DATA)||fs.existsSync(DELIVERY+'/database-result.json')||listeners().some(l=>l.port===PORT))throw Error('Existing target or occupied port; STOP');
  const before=others(),startedAt=new Date().toISOString();
  const result={inputId:manifest.id,jarSha256:built.jarSha256,startedAt,beforeOtherListeners:before,steps:[],suites:[],status:'RUNNING',databaseBusinessTestsStatus:'NOT_EXECUTED',frontendStarted:false,httpServerStarted:false};
  const phase=phaseRecorder(result),upgradeFixture=sentinel();
  result.upgradeFixtureId=upgradeFixture.id;
  let attemptedStart=false,identity,clusterId,stage='initialize',adapter;
  for(const role of [ADMIN,MIG,APP])credentials[role]=crypto.randomBytes(32).toString('hex');
  const pwfile=RUN+'/init-password.ephemeral';
  async function sql(database,role,statement,label){
    if(![MAIN,UPGRADE,'postgres'].includes(database)||!Object.hasOwn(credentials,role))throw Error('Unapproved SQL destination');
    await localIdentity();
    const assertion=`DO $r2$ BEGIN IF current_database()<>'${database}' OR host(inet_server_addr())<>'127.0.0.1' OR inet_server_port()<>55449 OR current_setting('data_directory')<>'${DATA}' OR (pg_control_system()).system_identifier::text<>'${clusterId}' THEN RAISE EXCEPTION 'R3 physical identity mismatch'; END IF; END $r2$;\n`;
    // The application role uses its restricted read-only identity functions; no pg_control_system grant.
    const guarded=role===APP?assertion.replaceAll("current_setting('data_directory')","p3_r3_control.data_directory()").replaceAll('(pg_control_system()).system_identifier::text','p3_r3_control.cluster_id()'):assertion;
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
    fs.writeFileSync(pwfile,credentials[ADMIN]+'\n',{flag:'wx',mode:0o600});
    const init=await command(PG+'/initdb.exe',['-D',DATA,'-U',ADMIN,'--pwfile='+pwfile,'--auth-host=scram-sha-256','--auth-local=scram-sha-256','--encoding=UTF8','--locale=C',
      '-c','listen_addresses=127.0.0.1','-c','port=55449','-c','max_connections=60','-c','log_statement=none','-c','log_min_error_statement=panic','-c','log_parameter_max_length_on_error=0'],{log:'initdb.log'});
    fs.unlinkSync(pwfile);okay(init,'initdb');
    // Restrict the new cluster only. No existing configuration files are read or changed.
    fs.writeFileSync(DATA+'/pg_hba.conf',`host postgres ${ADMIN} 127.0.0.1/32 scram-sha-256\nhost ${MAIN},${UPGRADE} ${ADMIN},${MIG},${APP} 127.0.0.1/32 scram-sha-256\nhost all all 0.0.0.0/0 reject\nhost all all ::0/0 reject\n`);
    const control=okay(await command(PG+'/pg_controldata.exe',['-D',DATA],{log:'control-before-start.log'}),'pg_controldata');
    clusterId=control.match(/Database system identifier:\s*(\d+)/)?.[1];if(!clusterId)throw Error('Cannot establish cluster identifier');
    if(listeners().some(l=>l.port===PORT))throw Error('Port acquired by another process; STOP');
    stage='start';
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
      isStopped:async(signal,pinned)=>{signal.throwIfAborted();return !fs.existsSync(DATA+'/postmaster.pid')&&!(await lifecycleListeners(signal)).some(l=>l.port===PORT)&&await noOwnedPostgres(pinned,signal);}
    };
    attemptedStart=true;
    result.lifecycle=await startOwnedDatabase(adapter,activeExpected,{launcherMs:37000,readinessMs:10000,cleanupMs:45000});
    if(result.lifecycle.status!=='READY')throw Error('Startup classification: '+result.lifecycle.status);
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
    result.runtimePermissions=await sql(MAIN,APP,"select current_user,rolsuper,rolbypassrls from pg_roles where rolname=current_user; select has_table_privilege(current_user,'flyway_schema_history','INSERT'),has_table_privilege(current_user,'flyway_schema_history','UPDATE'),has_table_privilege(current_user,'flyway_schema_history','DELETE'),has_table_privilege(current_user,'p3_r3_control.ready','UPDATE'); select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname in ('public','p3_r3_control') and c.relowner=(select oid from pg_roles where rolname=current_user);",'runtime-permissions');
    if(result.runtimePermissions!==APP+'|f|f\nf|f|f|f\n0')throw Error('Runtime permissions mismatch');
    const ordered=[
      'com.wms.system.tenant.security.TenantSecurityPostgresTest','com.wms.system.tenant.security.TenantSecurityCommandsPostgresTest',
      'com.wms.system.platform.service.PlatformSessionPostgresTest','com.wms.system.platform.service.PlatformInvitationSessionPostgresTest#invitation*',
      'com.wms.system.platform.service.PlatformAdminInvitationMfaTransactionIntegrationTest','com.wms.system.platform.service.PlatformCompanyDataServiceIntegrationTest',
      'com.wms.system.tenant.security.TenantSecuritySpringPostgresTest','com.wms.system.platform.service.PlatformAccessGrantRoleRaceIntegrationTest'];
    result.databaseBusinessTestsStatus='RUNNING';
    for(const selector of ordered){
      const name=selector.split('#')[0],short=name.split('.').at(-1);stage=short;await localIdentity();
      const expected=manifest.expectedBusinessTests.find(e=>e.path.endsWith('/'+short+'.java')).unchangedTests;
      const log='test-'+short+'.log';
      const executed=await command(JAVA,mavenArgs(['-Dp3.r2.database-authorized=true','-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55449/'+MAIN,
        '-Dp3.r2.cluster-id='+clusterId,'-Dtest='+selector,'-Dmaven.test.redirectTestOutputToFile=true','-DforkCount=1','-DreuseForks=false','-Dsurefire.timeout=120','-Dsurefire.exitTimeout=20','surefire:test']),
        {env:{P3_R2_APP_PASSWORD:credentials[APP]},log,timeout:240000});
      const xml=BUILD+'/surefire-reports/TEST-'+name+'.xml',summary=fs.existsSync(xml)?report(xml):null;
      result.suites.push({selector,exitCode:executed.exitCode,timeout:executed.timeout,expectedTests:expected.length,summary,logSha256:digest(RUN+'/'+log)});
      okay(executed,short);
      if(!summary||summary.tests!==expected.length||summary.failures||summary.errors||summary.skipped||expected.some(e=>!summary.cases.some(c=>c.name===e.name)))throw Error('Test execution/coverage mismatch: '+short);
      console.log(short+': '+summary.tests+' passed');
    }
    result.databaseBusinessTestsStatus='PASSED';result.status='PASSED';
  }catch(error){result.status='FAILED';result.failedStage??=stage;result.failureCategory??='BATCH';
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
      }else result.stop.completed=!listeners().some(l=>l.port===PORT);
      if(!result.stop.completed)result.status='FAILED';
    }catch(error){result.stop.failure=redact(error.message);result.status='FAILED';}
    result.afterOtherListeners=others();result.otherListenersUnchanged=JSON.stringify(before)===JSON.stringify(result.afterOtherListeners);
    result.completedAt=new Date().toISOString();
    try{result.snapshotStillMatches=verify().id===manifest.id;}catch(error){result.snapshotStillMatches=false;result.inputVerificationFailure=redact(error.message);result.status='FAILED';}
    if(!result.otherListenersUnchanged)result.status='FAILED';
    result.coverage={plannedSuites:8,plannedMethods:63,reportedTests:result.suites.reduce((n,s)=>n+(s.summary?.tests||0),0),skipped:result.suites.reduce((n,s)=>n+(s.summary?.skipped||0),0),failures:result.suites.reduce((n,s)=>n+(s.summary?.failures||0),0),errors:result.suites.reduce((n,s)=>n+(s.summary?.errors||0),0),unexecutedMethods:63-result.suites.reduce((n,s)=>n+(s.summary?.tests||0)-(s.summary?.skipped||0),0)};
    result.finalDedicatedListeners=listeners().filter(l=>l.port===PORT);result.finalPidFilePresent=fs.existsSync(DATA+'/postmaster.pid');
    result.databaseDirectoryPreserved=fs.existsSync(DATA);result.temporaryPasswordFileRemoved=!fs.existsSync(pwfile);
    writeJson(DELIVERY+'/database-result.json',result);
    console.log(JSON.stringify({status:result.status,failedStage:result.failedStage,migrationSteps:result.steps.length,suites:result.suites.length,stopped:result.stop.completed,otherListenersUnchanged:result.otherListenersUnchanged}));
  }
  if(result.status!=='PASSED'||!result.stop.completed)process.exitCode=1;
}
if(process.argv[2]==='build')await build();else if(process.argv[2]==='database')await database();else throw Error('Only build/database');

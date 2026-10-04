// b1 supervisor. Explicit preparation gate, one start attempt, separate verified cleanup.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import * as B from './platform-b1-binding.mjs';
import {verifySealed,save} from './platform-b1-prepare.mjs';
import {hash,json} from './platform-frontend-f1-build.mjs';
import {runProcess,startOwnedDatabase,stopOwnedDatabase,failureDetails} from './platform-process-lifecycle.mjs';
import {inspectWindowsEnvironment,initializationPreflight,queryWindowsProcess,requireInspectionCapability} from './platform-launch-preflight.mjs';
import {createB1Environment,assertAuthorizedContext,checkCommand,processAbsence} from './platform-b1-environment.mjs';
import {privateAclInvocation,validatePrivateAcl,classifyCurrentPid} from './platform-b1-startup-security.mjs';
import {attachStartupCapture,mergeBackendDiagnostic} from './platform-backend-diagnostic.mjs';
import {backendArguments,requireSealedBackendCommand} from './platform-browser-runtime-safety.mjs';
export {attachStartupCapture,mergeBackendDiagnostic};
export const saveBrowserFinalReport=(file,evidence)=>save(file,evidence);
export function launchBrowserBackend(launch,manifest,clusterId,password){
 const args=backendArguments(B,clusterId);
 requireSealedBackendCommand(manifest.commands.backend,B.JAVA,args,clusterId);
 return launch('backend',B.JAVA,args,{P3_B4_APP_PASSWORD:password});
}

const PS='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const SID='S-1-5-21-3770167052-703495069-1631994977-1001';
const canonical=p=>path.win32.normalize(p).replaceAll('\\','/').toLowerCase();
const pause=ms=>new Promise(r=>setTimeout(r,ms));
const env={...process.env};
for(const k of Object.keys(env))if(/^(PG|SPRING_|P3_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(k))delete env[k];
export const browserChildEnvironment=()=>({...env});
// These exports are the actual runtime call sites, also exercised without a database.
export const queryBrowserReady=(command,pid)=>queryWindowsProcess(command,{pid,stage:'BROWSER_HTTP_PROCESS'});
export const queryBrowserStopped=(command,pid)=>queryWindowsProcess(command,{pid,stage:'BROWSER_STOP_CONFIRM'});
export async function queryBrowserStarterNow(command,expected){
 const proof=await queryWindowsProcess(command,{pid:expected?.pid,stage:'BROWSER_STARTER_CLOSED'});
 return {checkedAt:new Date().toISOString(),proof,...classifyCurrentPid(proof,expected)};
}
export async function requireBrowserPrivateAcl(command,directory,ownerSid){
 const invocation=privateAclInvocation(directory,ownerSid);
 const r=checkCommand(await command(invocation.executable,invocation.args,{log:'private-acl.log',timeout:B.BUDGETS.sqlMs,timeoutMs:B.BUDGETS.sqlMs}),'PRIVATE_ACL');
 return validatePrivateAcl(JSON.parse(r.output.trim()),directory,ownerSid);
}
const STATE_SQL=`SELECT json_build_object(
 'users',(select coalesce(json_agg(x),'[]') from (select u.id,u.normalized_email,u.enabled,u.mfa_enabled,
  (select coalesce(json_agg(r.role_code order by r.role_code),'[]') from platform_user_roles ur join platform_roles r on r.id=ur.platform_role_id where ur.platform_user_id=u.id) roles,
  (select count(*) from platform_sessions s where s.platform_user_id=u.id and s.revoked_at is null and s.idle_expires_at>now() and s.absolute_expires_at>now()) active_sessions,
  (select count(*) from platform_access_grants g where g.grantee_platform_user_id=u.id) grant_records
  from platform_users u where u.normalized_email in ('inviter-b4@platform.invalid','invitee-b4@platform.invalid') order by u.id) x),
 'unknown_users',(select count(*) from platform_users where normalized_email not in ('inviter-b4@platform.invalid','invitee-b4@platform.invalid')),
 'invitations',(select coalesce(json_agg(x),'[]') from (select id,normalized_email,role_code,invitation_type,invited_by_platform_user_id,accepted_platform_user_id,accepted_at is not null accepted,revoked_at is not null revoked,expires_at>now() unexpired from platform_admin_invitations where normalized_email='invitee-b4@platform.invalid' order by id) x),
 'unknown_invitations',(select count(*) from platform_admin_invitations where normalized_email<>'invitee-b4@platform.invalid'),
 'audit',(select coalesce(json_agg(x),'[]') from (select action,result,count(*) count from platform_audit_logs where platform_user_id in (select id from platform_users where normalized_email in ('inviter-b4@platform.invalid','invitee-b4@platform.invalid')) group by action,result order by action,result) x));`;

async function supervisor(){
 if(process.argv.length!==4||process.argv[3]!==B.AUTH||canonical(fileURLToPath(import.meta.url))!==canonical(B.SOURCE+'/platform-b1-batch.mjs'))throw Error('B1_UNSEALED_OR_UNAUTHORIZED');
 const manifest=verifySealed(),gate=json(B.DELIVERY+'/preparation-result.json');
 if(gate.status!=='PASSED'||gate.inputId!==manifest.id||gate.javaContracts!==16||gate.catalogContracts!==13||gate.startupContracts!==25||gate.diagnosticContracts!==38||gate.runtimeSafetyContracts!==35||gate.runtimeSafety?.passed!==35||gate.runtimeSafety?.tests!==35||gate.runtimeSafety?.failed!==0||gate.runtimeSafety?.skipped!==0||gate.runtimeSafety?.cancelled!==0||gate.runtimeSafety?.todo!==0||gate.compiledId!==json(B.DELIVERY+'/compiled.json').id||gate.nodeGates.length!==manifest.gates.length
  ||manifest.gates.some(([script,count])=>!gate.nodeGates.some(g=>g.script===script&&g.tests===count&&g.passed===count&&!g.failed&&!g.skipped&&!g.cancelled&&!g.todo)))throw Error('B1_PREPARATION_GATE');
 if(fs.existsSync(B.RUN)||fs.existsSync(B.DATA)||fs.existsSync(B.PRIVATE)||fs.existsSync(B.DELIVERY+'/start-attempt.json'))throw Error('B1_TARGET_ALREADY_ATTEMPTED');
 save(B.DELIVERY+'/start-attempt.json',{at:new Date().toISOString(),inputId:manifest.id,supervisorPid:process.pid,awakeRequested:false});
 const evidence={inputId:manifest.id,status:'STARTING',commands:[],steps:[],services:{},firstFailure:null,cleanupErrors:[]};
 const secrets={},ownedPrivate=new Set();let stage='OS_PREFLIGHT',environment,expected,lifecycle,identity,clusterId,ready=false;
 const children={};let commandNo=0,inspectionNo=0,requestNo=0;
 const instance=crypto.randomBytes(16).toString('hex');
 const redact=s=>{for(const value of Object.values(secrets))if(typeof value==='string'&&value)s=s.replaceAll(value,'[REDACTED]');return s.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED]');};
 async function command(exe,args,{input='',env:extra={},cwd=B.ROOT,log,timeout=B.BUDGETS.sqlMs,timeoutMs,signal,fileOutput=false}={}){
  const index=++commandNo,budget=timeoutMs??timeout,name=String(index).padStart(3,'0')+'-'+(log??'query.log');
  const rawPath=B.RUN+'/'+name+'.output';
  const r=await runProcess(exe,args,{cwd,env:{...env,...extra},input,timeoutMs:budget,signal,outputFile:fileOutput?rawPath:undefined});
  const output=redact(fileOutput&&fs.existsSync(rawPath)?fs.readFileSync(rawPath,'utf8'):r.output);
  if(fileOutput&&fs.existsSync(rawPath)&&fs.readFileSync(rawPath,'utf8')!==output)fs.writeFileSync(rawPath,output);
  fs.writeFileSync(B.DELIVERY+'/'+name,output,{flag:'wx'});
  const {output:unused,...receipt}=r;
  evidence.commands.push({index,executable:exe,args,cwd,budgetMs:budget,...receipt,log:name,logSha256:hash(B.DELIVERY+'/'+name)});
  return {...r,output,commandIndex:index};
 }
 const checked=async(exe,args,opts,label)=>checkCommand(await command(exe,args,opts),label);
 async function inspection(port,label){const report=await inspectWindowsEnvironment(command,{directory:B.DATA,port});assertAuthorizedContext(report);save(B.DELIVERY+'/os-'+(++inspectionNo)+'-'+label+'.json',report);return report;}
 async function sql(db,role,statement,label){
  if(!['postgres',B.DATABASE].includes(db)||![B.ADMIN,B.MIG,B.APP].includes(role))throw Error('B1_SQL_TARGET');
  await environment.requireLive();
  const dir=role===B.APP?'p3_b4_control.data_directory()':"current_setting('data_directory')",cid=role===B.APP?'p3_b4_control.cluster_id()':'(pg_control_system()).system_identifier::text';
  const guard=`DO $b1$ BEGIN IF current_database()<>'${db}' OR current_user<>'${role}' OR host(inet_server_addr())<>'127.0.0.1' OR inet_server_port()<>55459 OR ${dir}<>'${B.DATA}' OR ${cid}<>'${clusterId}' THEN RAISE EXCEPTION 'B1_IDENTITY_MISMATCH'; END IF; END $b1$;\n`;
  return (await checked(B.PG+'/psql.exe',['-X','-w','-h','127.0.0.1','-p','55459','-U',role,'-d',db,'-v','ON_ERROR_STOP=1','-q','-A','-t'],
   {input:guard+statement,env:{PGPASSWORD:secrets[role],PGCONNECT_TIMEOUT:'5',PGOPTIONS:'-c statement_timeout=15000'},log:label+'.log',timeout:B.BUDGETS.sqlMs},label)).output.trim().replaceAll('\r\n','\n');
 }
 async function processProof(c){
  const p=await queryBrowserReady(command,c.pid);
  if(p.status!=='AVAILABLE'||canonical(p.executable)!==canonical(c.executable)||p.parentPid!==process.pid||p.ownerSid!==SID
   ||p.creationEpoch<Math.floor(c.started/1000)-2||p.creationEpoch>Math.floor(c.started/1000)+5
   ||(c.proof&&(p.creationTicks!==c.proof.creationTicks||p.commandLineSha256!==c.proof.commandLineSha256)))throw Error('B1_CHILD_IDENTITY');
  c.proof??=p;return p;
 }
 function launch(name,executable,args,extra){
  const c={name,executable,args,started:Date.now(),exited:false,code:null};
  const child=spawn(executable,args,{cwd:B.ROOT,env:{...env,...extra},windowsHide:true,stdio:['ignore','pipe','pipe']});
  c.child=child;c.pid=child.pid;children[name]=c;
  c.diagnostic=attachStartupCapture(child,{directory:B.DELIVERY,service:name});
  child.on('error',e=>{c.spawnError=e.code??e.name;});child.on('exit',(code,signal)=>{c.exited=true;c.code=code;c.signal=signal;c.exitAt=new Date().toISOString();});
  evidence.services[name]={pid:c.pid,executable,args,startedAt:new Date(c.started).toISOString()};return c;
 }
 async function waitReady(c,port,budget){
  const start=Date.now();let value;
  while(Date.now()-start<budget){
   if(c.exited||c.spawnError){if(c.name==='backend')evidence.backendDiagnostic=await c.diagnostic.finish();throw Error('B1_'+c.name.toUpperCase()+'_EARLY_EXIT');}
   if(!c.diagnostic.snapshot().integrity)throw Error('BROWSER_DIAGNOSTIC_EVIDENCE_INCOMPLETE');
   try{const res=await fetch('http://127.0.0.1:'+port+'/__isolation/health',{signal:AbortSignal.timeout(1000)});if(res.ok){value=await res.json();if(value.ready===true&&value.instance===instance&&value.port===port)break;throw Error('B1_HEALTH_IDENTITY');}}
   catch(e){if(e.message==='B1_HEALTH_IDENTITY')throw e;}await pause(200);
  }
  if(!value||Date.now()-start>budget)throw Error('B1_'+c.name.toUpperCase()+'_READY_TIMEOUT');
  const proof=await processProof(c),r=await inspection(port,c.name+'-ready');
  const selected=r.listeners.selected.filter(l=>l.port===port);if(selected.length!==1||selected[0].pid!==c.pid||selected[0].address!=='127.0.0.1')throw Error('B1_LISTENER_IDENTITY');
  evidence.services[c.name]={...evidence.services[c.name],proof,health:value,readyElapsedMs:Date.now()-start};
  if(Date.now()-start>budget)throw Error('B1_'+c.name.toUpperCase()+'_READY_BUDGET');
 }
 async function stopChild(name,port,budget){
  const c=children[name];if(!c)return {status:'NOT_STARTED'};
  const start=Date.now();
  if(!c.exited){await processProof(c);fs.writeFileSync(B.RUN+'/'+name+'.stop',instance,{flag:'wx'});while(!c.exited&&Date.now()-start<budget)await pause(100);}
  if(!c.exited)throw Error('B1_'+name.toUpperCase()+'_STOP_TIMEOUT');
  const p=await queryBrowserStopped(command,c.pid),r=await inspection(port,name+'-stopped');
  if(p.status!=='ABSENT'||r.listeners.selected.some(l=>l.port===port)||Date.now()-start>budget)throw Error('B1_'+name.toUpperCase()+'_STOP_UNCONFIRMED');
  return {status:c.code===0?'STOPPED':'EXITED_NONZERO',exitCode:c.code,pid:c.pid,absenceConfirmed:true,elapsedMs:Date.now()-start};
 }
 async function snapshot(label){
  const s=JSON.parse(await sql(B.DATABASE,B.APP,STATE_SQL,'state-'+label));
  if(s.unknown_users!==0||s.unknown_invitations!==0||s.users.some(u=>u.grant_records!==0))throw Error('B1_UNKNOWN_FIXTURE_OR_GRANT');
  save(B.DELIVERY+'/checkpoint-'+label+'.json',{at:new Date().toISOString(),state:s});return s;
 }
 try{
  for(const port of Object.values(B.PORTS)){const report=await inspection(port,'preflight-'+port);if(report.directory.status!=='ABSENT'||report.listeners.selected.some(l=>l.port===port)||report.postgres.targetMatches.length)throw Error('B1_TARGET_OCCUPIED');}
  stage='PRIVATE_ACL';fs.mkdirSync(B.RUN,{recursive:true});fs.mkdirSync(B.PRIVATE);
  evidence.privateAcl=await requireBrowserPrivateAcl(command,B.PRIVATE,SID);
  for(const role of [B.ADMIN,B.MIG,B.APP])secrets[role]=crypto.randomBytes(32).toString('hex');
  for(const k of ['jwt.tenant-secret','jwt.platform-secret','wms.platform-mfa.encryption-key','wms.tenant-security.credential-encryption-key'])secrets[k]=crypto.randomBytes(32).toString('base64');
  secrets['password.a']='B4!Aa7'+crypto.randomBytes(16).toString('hex');
  const privateWrite=(name,text)=>{fs.writeFileSync(B.PRIVATE+'/'+name,text,{flag:'wx'});ownedPrivate.add(name);};
  privateWrite('runtime.properties','instance='+instance+'\n'+Object.entries(secrets).filter(([k])=>!k.startsWith('p3_b4_')).map(([k,v])=>k+'='+v).join('\n')+'\n');
  privateWrite('login-credentials.txt','B4 LOCAL SYNTHETIC CREDENTIALS - DO NOT COPY INTO CHAT/REPORTS\nURL: http://127.0.0.1:15174/platform.html#/login\nA email: inviter-b4@platform.invalid\nA password: '+secrets['password.a']+'\nFirst step: log in as A, stop at first MFA enrollment.\n');
  save(B.RUN+'/runtime-public.json',{instance,supervisorPid:process.pid,inputId:manifest.id});
  environment=createB1Environment({command,evidence,password:()=>secrets[B.ADMIN]});
  stage='INITIALIZE';
  const pw=B.PRIVATE+'/init-password.ephemeral';privateWrite('init-password.ephemeral',secrets[B.ADMIN]+'\n');
  const init=await initializationPreflight({inspect:()=>environment.inspect('before-initialize'),record:r=>{evidence.initializationPreflight=r;},initialize:()=>command(B.PG+'/initdb.exe',
   ['-D',B.DATA,'-U',B.ADMIN,'--pwfile='+pw,'--auth-host=scram-sha-256','--auth-local=scram-sha-256','--encoding=UTF8','--locale=C','-c','listen_addresses=127.0.0.1','-c','port=55459','-c','max_connections=30','-c','log_statement=none','-c','log_min_error_statement=panic','-c','log_parameter_max_length_on_error=0'],{log:'initdb.log',timeout:B.BUDGETS.initializationMs,fileOutput:true})});
  fs.unlinkSync(pw);ownedPrivate.delete('init-password.ephemeral');checkCommand(init,'INITIALIZE');
  if(init.output.match(/owned by user "([^"]+)"/)?.[1]!=='Arnold')throw Error('B1_INIT_OWNER');
  fs.writeFileSync(B.DATA+'/pg_hba.conf',`host postgres ${B.ADMIN} 127.0.0.1/32 scram-sha-256\nhost ${B.DATABASE} ${B.ADMIN},${B.MIG},${B.APP} 127.0.0.1/32 scram-sha-256\nhost all all 0.0.0.0/0 reject\nhost all all ::0/0 reject\n`);
  clusterId=await environment.control('before-start-control');expected=environment.configure(clusterId);
  stage='PG_START';
  if(!processAbsence(await environment.inspect('before-start')).confirmed)throw Error('B1_TARGET_CHANGED');
  lifecycle=await startOwnedDatabase(environment.adapter,expected,{launcherMs:B.BUDGETS.launcherMs,readinessMs:B.BUDGETS.readyMs,cleanupMs:B.BUDGETS.cleanupMs});evidence.lifecycle=lifecycle;
  if(lifecycle.status!=='READY'){evidence.firstFailure=environment.firstFailure()??lifecycle.firstFailure;throw Error('B1_PG_NOT_READY');}
  identity=lifecycle.identity;environment.pin(identity);
  stage='BOOTSTRAP';
  await sql('postgres',B.ADMIN,`CREATE ROLE ${B.MIG} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD '${secrets[B.MIG]}'; CREATE ROLE ${B.APP} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS PASSWORD '${secrets[B.APP]}'; GRANT pg_read_all_settings TO ${B.MIG} WITH INHERIT TRUE; CREATE DATABASE ${B.DATABASE} OWNER ${B.MIG} TEMPLATE template0;`,'roles-database');
  await sql(B.DATABASE,B.ADMIN,`REVOKE ALL ON DATABASE ${B.DATABASE} FROM public; GRANT CONNECT ON DATABASE ${B.DATABASE} TO ${B.MIG},${B.APP}; GRANT EXECUTE ON FUNCTION pg_catalog.pg_control_system() TO ${B.MIG};`,'database-permissions');
  stage='MIGRATION';await environment.requireLive();
  await checked(B.JAVA,['-Dp3.b4.migration-authorized=true','-Dp3.test.jdbcUrl=jdbc:postgresql://127.0.0.1:55459/'+B.DATABASE,'-Dp3.b4.cluster-id='+clusterId,'@'+B.SOURCE+'/runtime.args','com.wms.browseracceptance.PlatformB1DatabasePreparer','prepare-release'],
   {env:{P3_B4_MIGRATOR_PASSWORD:secrets[B.MIG]},log:'migration.log',timeout:B.BUDGETS.migrationMs},'MIGRATION');
  evidence.migrationHistory=await sql(B.DATABASE,B.MIG,'select version,type,script,checksum,success from flyway_schema_history order by installed_rank;','migration-history');
  const permissions=await sql(B.DATABASE,B.APP,"select current_user,rolsuper,rolbypassrls from pg_roles where rolname=current_user; select has_table_privilege(current_user,'flyway_schema_history','INSERT'),has_table_privilege(current_user,'flyway_schema_history','UPDATE'),has_table_privilege(current_user,'flyway_schema_history','DELETE'),has_table_privilege(current_user,'p3_b4_control.ready','UPDATE'); select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname in ('public','p3_b4_control') and c.relowner=(select oid from pg_roles where rolname=current_user);",'runtime-permissions');
  if(permissions!==B.APP+'|f|f\nf|f|f|f\n0')throw Error('B1_RUNTIME_PERMISSIONS');evidence.runtimePermissions=permissions;
  verifySealed();
  stage='BACKEND_START';const backend=launchBrowserBackend(launch,manifest,clusterId,secrets[B.APP]);await waitReady(backend,18092,B.BUDGETS.backendReadyMs);
  stage='PROXY_START';const proxy=launch('proxy',B.NODE,[B.SOURCE+'/platform-b1-proxy.mjs'],{});await waitReady(proxy,15174,B.BUDGETS.frontendReadyMs);
  const page=await fetch('http://127.0.0.1:15174/platform.html',{signal:AbortSignal.timeout(3000)});const bytes=Buffer.from(await page.arrayBuffer());
  if(!page.ok||crypto.createHash('sha256').update(bytes).digest('hex')!==hash(B.FRONTEND+'/dist/platform.html'))throw Error('B1_SERVED_FRONTEND_DRIFT');
  for(const p of ['/index.html','/private/runtime.properties','/assets/platform-B41VIFMu.js.map'])if((await fetch('http://127.0.0.1:15174'+p,{signal:AbortSignal.timeout(3000)})).status!==404)throw Error('B1_STATIC_BOUNDARY');
  const initial=await snapshot('ready');if(initial.users.length!==1||initial.users[0].normalized_email!=='inviter-b4@platform.invalid'||initial.users[0].mfa_enabled||initial.invitations.length)throw Error('B1_SYNTHETIC_BASELINE');
  ready=true;evidence.status='READY_WAITING_FOR_MANUAL';save(B.DELIVERY+'/environment-ready.json',{...evidence,at:new Date().toISOString(),login:'http://127.0.0.1:15174/platform.html#/login',clusterId,manualStepsCompleted:0,awakeRequested:false});
  console.log('B1_READY_WAITING_FOR_MANUAL');
  stage='MANUAL_WAIT';
  while(!fs.existsSync(B.RUN+'/stop.request')){
   for(const c of Object.values(children))if(c.exited||c.spawnError)throw Error('B1_'+c.name.toUpperCase()+'_UNEXPECTED_EXIT');
   const request=B.RUN+'/checkpoint.request';if(fs.existsSync(request)){
    const label=fs.readFileSync(request,'utf8');if(!/^(a-ready|invitation-created|b-active|b-logged-out|b-link-rejected|b-relogin)$/.test(label))throw Error('B1_CHECKPOINT_LABEL');
    await snapshot(String(++requestNo).padStart(2,'0')+'-'+label);fs.unlinkSync(request);
   }
   await pause(500);
  }
  const end=fs.readFileSync(B.RUN+'/stop.request','utf8');if(!['MANUAL_COMPLETED','ABORT'].includes(end))throw Error('B1_STOP_REQUEST');
  evidence.status=end==='MANUAL_COMPLETED'?'MANUAL_COMPLETION_REPORTED':'ABORTED_BY_OPERATOR';
 }catch(error){evidence.status='FAILED';evidence.firstFailure??={...failureDetails(error,stage),safeMessage:/^[A-Z0-9_]+$/.test(error.message)?error.message:undefined};mergeBackendDiagnostic(evidence,evidence.backendDiagnostic??children.backend?.diagnostic.snapshot());}
 finally{
  const closure={};
  for(const [name,port,budget]of [['proxy',15174,B.BUDGETS.frontendStopMs],['backend',18092,B.BUDGETS.backendStopMs]]){
   try{closure[name]=await stopChild(name,port,budget);}catch(e){evidence.cleanupErrors.push(failureDetails(e,'STOP_'+name.toUpperCase()));}
  }
  if(expected&&environment){
   if(Object.values(children).some(c=>!c.exited))evidence.cleanupErrors.push({stage:'PG_STOP',name:'HttpWorkersStillRunning'});
   else try{closure.database=lifecycle?.cleanup??await stopOwnedDatabase(environment.adapter,expected,identity,{cleanupMs:B.BUDGETS.cleanupMs});closure.databaseFinal=await environment.closeout();
    if(!closure.database.completed||!closure.databaseFinal.absence.confirmed)throw Error('B1_DATABASE_CLOSE_UNCONFIRMED');
   }catch(e){evidence.cleanupErrors.push(failureDetails(e,'PG_STOP'));}
  }
  const removed=[];
  if(Object.values(children).every(c=>c.exited))for(const name of ownedPrivate){const p=B.PRIVATE+'/'+name;if(fs.existsSync(p)){fs.unlinkSync(p);removed.push(name);}}
  evidence.closure=closure;evidence.privateMaterialsRemoved=removed;evidence.readyReached=ready;evidence.finishedAt=new Date().toISOString();
  evidence.manualAcceptancePassed=false; // User observations and exact checkpoints must be reviewed before final acceptance.
  if(children.backend)mergeBackendDiagnostic(evidence,await children.backend.diagnostic.finish());
  if(children.proxy)evidence.proxyDiagnostic=await children.proxy.diagnostic.finish();
  if(evidence.diagnosticErrors?.length)evidence.status='FAILED';
  if(evidence.cleanupErrors.length)evidence.status='FAILED_WITH_CLEANUP_ISSUES';
  saveBrowserFinalReport(B.DELIVERY+'/environment-final.json',evidence);console.log('B1_FINISHED '+evidence.status);
  if(evidence.status.startsWith('FAILED'))process.exitCode=1;
 }
}
async function control(){
 const [action,label]=process.argv.slice(2);if(canonical(process.cwd())!==canonical(B.ROOT)||process.version!=='v24.12.0')throw Error('B1_CONTEXT');
 if(action==='start'){await supervisor();return;}
 if(!fs.existsSync(B.DELIVERY+'/environment-ready.json')||fs.existsSync(B.DELIVERY+'/environment-final.json'))throw Error('B1_NOT_WAITING');
 if(action==='checkpoint'&&/^(a-ready|invitation-created|b-active|b-logged-out|b-link-rejected|b-relogin)$/.test(label??'')){fs.writeFileSync(B.RUN+'/checkpoint.request',label,{flag:'wx'});console.log('B1_CHECKPOINT_REQUESTED');return;}
 if(action==='stop'&&['MANUAL_COMPLETED','ABORT'].includes(label)){fs.writeFileSync(B.RUN+'/stop.request',label,{flag:'wx'});console.log('B1_STOP_REQUESTED');return;}
 throw Error('B1_CONTROL_ARGUMENTS');
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))control().catch(e=>{console.error('B1_ENTRY_FAILURE '+(/^[A-Z0-9_]+$/.test(e.message)?e.message:e.name));process.exitCode=1;});

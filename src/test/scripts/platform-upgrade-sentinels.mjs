// Pure test infrastructure: generates SQL and validates evidence; never opens a connection/process.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
export const MIGRATIONS=['B4_71__SaaS_Empty_Database_Baseline.sql','V4_72__Repair_Platform_Admin_Fingerprint_Types.sql',
  'V4_85__Tenant_Security_Foundation.sql','V4_86__Tenant_Security_Commands_And_Integration_State.sql',
  'V4_87__Tenant_Compromised_Credential_Fingerprints.sql','V4_88__Tenant_Webhook_Copy_Retention.sql',
  'V4_89__Platform_Backend_Sessions.sql','V4_90__Platform_Browser_Session_Scope.sql'];
export function sentinel(id=randomUUID()) {
  assert.match(id,/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  return Object.freeze({id,tag:'p3-upgrade-'+id,email:'p3-upgrade-'+id+'@fixture.invalid',sessionId:id,
    action:'WRITE',resourceType:'PLATFORM_USER',result:'SUCCESS',displayName:'Platform upgrade sentinel',
    detail:JSON.stringify({sentinel:id,purpose:'platform-schema-upgrade'})});
}
function checked(f){assert.deepEqual(f,sentinel(f.id));return f;}
const literal=s=>"'"+s.replaceAll("'","''")+"'";
const userWhere=f=>'normalized_email='+literal(f.email);
const auditWhere=f=>'resource_id='+literal(f.tag);
const sessionWhere=f=>'session_id='+literal(f.sessionId)+'::uuid';
function transaction(body){return 'BEGIN;\nSET LOCAL lock_timeout=\'5s\';\nSET LOCAL statement_timeout=\'15s\';\n'+body+'\nCOMMIT;\n';}
export function prepareUserAudit(f){
  checked(f);
  return transaction(`DO $sentinel$
DECLARE own_user_id bigint;
BEGIN
  IF (SELECT count(*) FROM platform_users WHERE ${userWhere(f)}) <> 0
     OR (SELECT count(*) FROM platform_audit_logs WHERE ${auditWhere(f)}) <> 0 THEN
    RAISE EXCEPTION 'Sentinel collision; never reuse or overwrite';
  END IF;
  INSERT INTO platform_users(normalized_email,password_hash,display_name,enabled)
    VALUES(${literal(f.email)},'not-an-authentication-hash',${literal(f.displayName)},false) RETURNING id INTO STRICT own_user_id;
  INSERT INTO platform_audit_logs(platform_user_id,action,resource_type,resource_id,result,detail_json)
    VALUES(own_user_id,${literal(f.action)},${literal(f.resourceType)},${literal(f.tag)},${literal(f.result)},${literal(f.detail)});
  IF (SELECT count(*) FROM platform_users WHERE ${userWhere(f)}) <> 1
     OR (SELECT count(*) FROM platform_audit_logs WHERE ${auditWhere(f)} AND platform_user_id=own_user_id) <> 1 THEN
    RAISE EXCEPTION 'User/audit sentinel cardinality mismatch';
  END IF;
END $sentinel$;`);
}
export function prepareSession(f){
  checked(f);
  return transaction(`DO $sentinel$
DECLARE own_user_id bigint; own_security_version bigint;
BEGIN
  SELECT id,security_version INTO STRICT own_user_id,own_security_version FROM platform_users WHERE ${userWhere(f)} FOR UPDATE;
  IF (SELECT count(*) FROM platform_audit_logs WHERE ${auditWhere(f)} AND platform_user_id=own_user_id) <> 1
     OR (SELECT count(*) FROM platform_sessions WHERE ${sessionWhere(f)}) <> 0 THEN
    RAISE EXCEPTION 'Session sentinel prerequisites or collision';
  END IF;
  INSERT INTO platform_sessions(session_id,platform_user_id,security_version,started_at,last_activity_at,idle_expires_at,absolute_expires_at)
    VALUES(${literal(f.sessionId)}::uuid,own_user_id,own_security_version,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP+interval '30 minutes',CURRENT_TIMESTAMP+interval '12 hours');
  IF (SELECT count(*) FROM platform_sessions WHERE ${sessionWhere(f)} AND platform_user_id=own_user_id) <> 1 THEN
    RAISE EXCEPTION 'Session sentinel cardinality mismatch';
  END IF;
END $sentinel$;`);
}
export function snapshotSql(f,version){
  checked(f);assert.ok([88,89,90].includes(version));
  // Include every persisted user field, but replace raw authentication fields by one change-detection digest.
  const userProjection="(to_jsonb(u)-'password_hash'-'mfa_secret_encrypted'-'recovery_code_hashes_json') || jsonb_build_object('authentication_fields_sha256',encode(sha256(convert_to(jsonb_build_array(u.password_hash,u.mfa_secret_encrypted,u.recovery_code_hashes_json)::text,'UTF8')),'hex'))";
  return `SELECT jsonb_build_object(
    'users',COALESCE((SELECT jsonb_agg(${userProjection} ORDER BY u.id) FROM platform_users u WHERE ${userWhere(f)}),'[]'::jsonb),
    'audits',COALESCE((SELECT jsonb_agg(to_jsonb(a) ORDER BY a.id) FROM platform_audit_logs a WHERE ${auditWhere(f)}),'[]'::jsonb),
    'sessions',${version===88?"'[]'::jsonb":`COALESCE((SELECT jsonb_agg(to_jsonb(s) ORDER BY s.session_id) FROM platform_sessions s WHERE ${sessionWhere(f)}),'[]'::jsonb)`});`;
}
export function validateWindow(s){
  const t=key=>{assert.equal(typeof s[key],'string');const n=Date.parse(s[key]);assert.ok(Number.isFinite(n));return n;};
  const start=t('started_at'),last=t('last_activity_at'),idle=t('idle_expires_at'),absolute=t('absolute_expires_at');
  assert.ok(Number.isSafeInteger(s.security_version)&&s.security_version>0);
  assert.ok(last>=start&&absolute>start&&absolute<=start+12*3600000&&idle<=absolute&&idle<=last+30*60000,'Session time constraint mismatch');
}
export function parseSnapshot(text,f,version){
  checked(f);assert.ok([88,89,90].includes(version));
  const value=JSON.parse(text);assert.deepEqual(Object.keys(value).sort(),['audits','sessions','users']);
  for(const key of ['users','audits','sessions'])assert.ok(Array.isArray(value[key]));
  assert.equal(value.users.length,1,'Expected exactly one owned user');assert.equal(value.audits.length,1,'Expected exactly one owned audit');
  assert.equal(value.sessions.length,version===88?0:1,'Expected exact owned session count');
  const u=value.users[0],a=value.audits[0];
  assert.deepEqual(Object.keys(u).sort(),[...Object.keys(userColumns).filter(k=>!['password_hash','mfa_secret_encrypted','recovery_code_hashes_json'].includes(k)),'authentication_fields_sha256'].sort());
  assert.deepEqual(Object.keys(a).sort(),Object.keys(auditColumns).sort());
  for(const value of [u.created_at,u.updated_at,a.created_at])assert.ok(typeof value==='string'&&Number.isFinite(Date.parse(value)));
  assert.ok(Number.isSafeInteger(u.id)&&u.id>0);assert.ok(Number.isSafeInteger(a.id)&&a.id>0);
  assert.equal(u.normalized_email,f.email);assert.equal(u.display_name,f.displayName);assert.equal(u.enabled,false);
  assert.equal(u.mfa_enabled,false);assert.equal(u.mfa_failed_attempts,0);assert.equal(u.security_version,1);
  assert.equal(u.mfa_locked_until,null);assert.equal(u.mfa_enrolled_at,null);
  assert.match(u.authentication_fields_sha256,/^[0-9a-f]{64}$/);
  for(const key of ['password_hash','mfa_secret_encrypted','recovery_code_hashes_json'])assert.ok(!Object.hasOwn(u,key),'Never return raw authentication material');
  assert.equal(a.platform_user_id,u.id);assert.equal(a.action,f.action);assert.equal(a.resource_type,f.resourceType);
  assert.equal(a.resource_id,f.tag);assert.equal(a.result,f.result);assert.equal(a.detail_json,f.detail);
  assert.equal(a.target_platform_user_id,null);assert.equal(a.target_tenant_id,null);
  if(version!==88){const s=value.sessions[0];assert.equal(s.session_id,f.sessionId);assert.equal(s.platform_user_id,u.id);
    assert.deepEqual(Object.keys(s).sort(),[...Object.keys(sessionColumns),...(version===90?['browser_binding_hash']:[])].sort());
    assert.equal(s.security_version,u.security_version);assert.equal(s.revoked_at,null);validateWindow(s);
    if(version===89)assert.ok(!Object.hasOwn(s,'browser_binding_hash'));
    else {assert.ok(Object.hasOwn(s,'browser_binding_hash'));assert.equal(s.browser_binding_hash,null);}}
  return value;
}
export function assertPreserved(before,after,{session=false}={}){
  assert.deepEqual(after.users,before.users,'User fields changed');assert.deepEqual(after.audits,before.audits,'Audit fields changed');
  if(session){assert.equal(before.sessions.length,1);assert.equal(after.sessions.length,1);
    assert.ok(Object.hasOwn(after.sessions[0],'browser_binding_hash'));assert.equal(after.sessions[0].browser_binding_hash,null);
    const {browser_binding_hash,...oldFields}=after.sessions[0];assert.deepEqual(oldFields,before.sessions[0],'Old session fields changed');}
}
export function phaseRecorder(result){
  result.phases??=[];
  return async(stage,kind,operation)=>{
    const entry={stage,kind,status:'RUNNING',startedAt:new Date().toISOString()};result.phases.push(entry);
    try{const value=await operation();entry.status='SUCCEEDED';return value;}
    catch(error){entry.status='FAILED';entry.errorType=error.name;result.failedStage=stage;result.failureCategory=kind;throw error;}
    finally{entry.completedAt=new Date().toISOString();}
  };
}
export async function runUpgradeSentinels({fixture:f,sql,migrate,phase}){
  checked(f);
  const read=(version,label)=>phase(label,'SENTINEL_VERIFY',async()=>parseSnapshot(await sql(snapshotSql(f,version),label),f,version));
  await migrate('upgrade-to-88');
  await phase('sentinel-user-audit-prepare-v88','SENTINEL_PREPARATION',()=>sql(prepareUserAudit(f),'sentinel-user-audit-prepare-v88'));
  const before89=await read(88,'sentinel-before-v89');
  await migrate('upgrade-to-89');
  const after89=await read(88,'sentinel-after-v89');
  await phase('sentinel-preserved-v89','SENTINEL_VERIFY',()=>assertPreserved(before89,after89));
  await phase('sentinel-session-prepare-v89','SENTINEL_PREPARATION',()=>sql(prepareSession(f),'sentinel-session-prepare-v89'));
  const sessionBefore=await read(89,'sentinel-session-before-v90');
  await phase('sentinel-pre-v90-consistency','SENTINEL_VERIFY',()=>assertPreserved(before89,sessionBefore));
  await migrate('upgrade-to-90');
  const after90=await read(90,'sentinel-after-v90');
  await phase('sentinel-preserved-v90','SENTINEL_VERIFY',()=>assertPreserved(sessionBefore,after90,{session:true}));
  return {userAndAuditPreserved:true,oldSessionFieldsPreserved:true,browserBindingRemainsNull:true,fixtureId:f.id,expectedCounts:{users:1,audits:1,sessions:1}};
}

// Narrow source contract, not a SQL engine or proof of live rollback/migration behavior.
const compact=s=>s.replace(/--[^\r\n]*/g,'').replace(/\s+/g,' ').trim().toLowerCase();
function tableBody(sql,table){
  const body=sql.match(new RegExp('CREATE TABLE (?:public\\.)?'+table+' \\(([\\s\\S]*?)\\n\\);','i'))?.[1];assert.ok(body,'Missing '+table);
  return body;
}
function columns(sql,table){
  const body=tableBody(sql,table);
  return Object.fromEntries(body.split(/\r?\n/).map(l=>l.trim().replace(/,$/,''))
    .filter(l=>l&&!/^(CONSTRAINT|last_activity_at >=|absolute_expires_at [><=]|idle_expires_at <=|\))/i.test(l))
    .map(l=>{const m=l.match(/^(\w+)\s+(.*)$/);assert.ok(m,'Unexpected column syntax');return [m[1],compact(m[2])];}));
}
const userColumns={id:'bigint not null',normalized_email:'character varying(254) not null',password_hash:'character varying(255) not null',display_name:'character varying(100) not null',
  enabled:'boolean default true not null',security_version:'bigint default 1 not null',created_at:'timestamp with time zone default current_timestamp not null',updated_at:'timestamp with time zone default current_timestamp not null',
  mfa_enabled:'boolean default false not null',mfa_secret_encrypted:'text',recovery_code_hashes_json:'text',mfa_failed_attempts:'integer default 0 not null',mfa_locked_until:'timestamp with time zone',mfa_enrolled_at:'timestamp with time zone'};
const auditColumns={id:'bigint not null',platform_user_id:'bigint not null',target_tenant_id:'bigint',action:'character varying(30) not null',resource_type:'character varying(80)',resource_id:'character varying(100)',
  request_id:'character varying(100)',request_ip:'character varying(64)',user_agent:'character varying(500)',result:'character varying(30) not null',detail_json:'text',created_at:'timestamp with time zone default current_timestamp not null',target_platform_user_id:'bigint'};
const sessionColumns={session_id:'uuid primary key',platform_user_id:'bigint not null references platform_users(id)',security_version:'bigint not null check (security_version > 0)',started_at:'timestamptz not null',last_activity_at:'timestamptz not null',idle_expires_at:'timestamptz not null',absolute_expires_at:'timestamptz not null',revoked_at:'timestamptz'};
export function verifyMigrationContract(migrations){
  assert.deepEqual(Object.keys(migrations).sort(),[...MIGRATIONS].sort());
  const baseline=migrations[MIGRATIONS[0]],b=compact(baseline),v89=compact(migrations[MIGRATIONS[6]]),v90=compact(migrations[MIGRATIONS[7]]);
  assert.deepEqual(columns(baseline,'platform_users'),userColumns);assert.deepEqual(columns(baseline,'platform_audit_logs'),auditColumns);
  assert.deepEqual(columns(migrations[MIGRATIONS[6]],'platform_sessions'),sessionColumns);
  for(const [sql,table,expected] of [[baseline,'platform_users',['ck_platform_users_email_normalized','ck_platform_users_mfa_attempts','ck_platform_users_security_version']],
    [baseline,'platform_audit_logs',['ck_platform_audit_action']],[migrations[MIGRATIONS[6]],'platform_sessions',['ck_platform_session_window']]]){
    assert.deepEqual([...tableBody(sql,table).matchAll(/CONSTRAINT (\w+)/g)].map(m=>m[1]).sort(),expected.sort(),'Unexpected table constraint requires review');
  }
  const includes=(sql,part)=>assert.ok(sql.includes(compact(part)),'Missing contract: '+part);
  for(const part of ['CHECK (((normalized_email)::text = lower(btrim((normalized_email)::text))))','CHECK (((mfa_failed_attempts >= 0) AND (mfa_failed_attempts <= 5)))','CHECK ((security_version > 0))'])includes(compact(tableBody(baseline,'platform_users')),part);
  includes(b,'CREATE TRIGGER trg_platform_audit_append_only BEFORE DELETE OR UPDATE ON public.platform_audit_logs');
  const actions=baseline.match(/CONSTRAINT ck_platform_audit_action CHECK[^\r\n]+/)?.[0];assert.ok(actions);assert.ok(actions.includes("'WRITE'::character varying"));
  const alters=[...b.matchAll(/alter table only public\.(platform_users|platform_audit_logs) [^;]+;/g)].map(m=>m[0]).sort();
  const expectedAlters=[
    "ALTER TABLE ONLY public.platform_users ALTER COLUMN id SET DEFAULT nextval('public.platform_users_id_seq'::regclass);",
    "ALTER TABLE ONLY public.platform_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.platform_audit_logs_id_seq'::regclass);",
    'ALTER TABLE ONLY public.platform_users ADD CONSTRAINT platform_users_pkey PRIMARY KEY (id);',
    'ALTER TABLE ONLY public.platform_audit_logs ADD CONSTRAINT platform_audit_logs_pkey PRIMARY KEY (id);',
    'ALTER TABLE ONLY public.platform_users ADD CONSTRAINT uk_platform_users_email UNIQUE (normalized_email);',
    'ALTER TABLE ONLY public.platform_audit_logs ADD CONSTRAINT fk_platform_audit_target_user FOREIGN KEY (target_platform_user_id) REFERENCES public.platform_users(id);',
    'ALTER TABLE ONLY public.platform_audit_logs ADD CONSTRAINT fk_platform_audit_tenant FOREIGN KEY (target_tenant_id) REFERENCES public.tenants(id);',
    'ALTER TABLE ONLY public.platform_audit_logs ADD CONSTRAINT fk_platform_audit_user FOREIGN KEY (platform_user_id) REFERENCES public.platform_users(id);'].map(compact).sort();
  assert.deepEqual(alters,expectedAlters);
  assert.doesNotMatch(b,/create unique index [^;]+ on public\.(platform_users|platform_audit_logs)\b/);
  for(const name of MIGRATIONS.slice(1,6))assert.doesNotMatch(compact(migrations[name]),/\b(?:alter table|create table|drop table|insert into|update|delete from)\s+(?:only\s+)?(?:public\.)?(?:platform_users|platform_audit_logs|platform_sessions)\b/,'Intervening sentinel schema/data change needs review');
  includes(v89,"CHECK ( last_activity_at >= started_at AND absolute_expires_at > started_at AND absolute_expires_at <= started_at + interval '12 hours' AND idle_expires_at <= absolute_expires_at AND idle_expires_at <= last_activity_at + interval '30 minutes' )");
  includes(v90,'ALTER TABLE platform_sessions ADD COLUMN browser_binding_hash varchar(64);');
  includes(v90,"CHECK (browser_binding_hash IS NULL OR browser_binding_hash ~ '^[0-9a-f]{64}$');");
  assert.doesNotMatch(v90,/\b(?:update|insert into|delete from|drop|truncate)\b/);
  for(const table of ['platform_users','platform_audit_logs'])assert.doesNotMatch(v89+' '+v90,new RegExp('(?:alter|create|drop) table '+table+'\\b'));
  return {versions:[88,89,90],userColumns:Object.keys(userColumns),auditColumns:Object.keys(auditColumns),sessionColumnsV89:Object.keys(sessionColumns),browserBindingV90:'nullable varchar(64), lowercase hex or NULL',
    liveDatabaseValidated:false};
}

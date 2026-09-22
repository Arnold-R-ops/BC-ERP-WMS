// No PostgreSQL, JDBC, sockets or service processes. SQL is inspected, never executed here.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {MIGRATIONS,sentinel,prepareUserAudit,prepareSession,snapshotSql,parseSnapshot,assertPreserved,validateWindow,
  verifyMigrationContract,phaseRecorder,runUpgradeSentinels} from './platform-upgrade-sentinels.mjs';
const migrations=Object.fromEntries(MIGRATIONS.map(name=>[name,fs.readFileSync(new URL('../../main/resources/db/migration/'+name,import.meta.url),'utf8').replaceAll('\r\n','\n')]));
const f=sentinel('5245c2c1-0000-4000-8000-000000000001');
const stamp='2026-09-11T00:00:00.000Z';
const clone=v=>structuredClone(v);
function evidence(version){
  const value={users:[{id:41,normalized_email:f.email,display_name:f.displayName,enabled:false,security_version:1,
    created_at:stamp,updated_at:stamp,mfa_enabled:false,mfa_failed_attempts:0,mfa_locked_until:null,mfa_enrolled_at:null,authentication_fields_sha256:'a'.repeat(64)}],
    audits:[{id:73,platform_user_id:41,target_tenant_id:null,action:'WRITE',resource_type:f.resourceType,resource_id:f.tag,request_id:null,request_ip:null,
      user_agent:null,result:'SUCCESS',detail_json:f.detail,created_at:stamp,target_platform_user_id:null}],sessions:[]};
  if(version!==88)value.sessions.push({session_id:f.sessionId,platform_user_id:41,security_version:1,started_at:stamp,last_activity_at:stamp,
    idle_expires_at:'2026-09-11T00:30:00.000Z',absolute_expires_at:'2026-09-11T12:00:00.000Z',revoked_at:null,...(version===90?{browser_binding_hash:null}:{})});
  return value;
}
test('actual B baseline through V4_90 source contracts match fixture fields and constraints',()=>{
  const c=verifyMigrationContract(migrations);assert.equal(c.liveDatabaseValidated,false);assert.deepEqual(c.versions,[88,89,90]);
});
const mutations=[
  ['user non-null',0,'normalized_email character varying(254) NOT NULL','normalized_email character varying(254)'],
  ['user size',0,'display_name character varying(100)','display_name character varying(10)'],
  ['user security default',0,'security_version bigint DEFAULT 1','security_version bigint DEFAULT 0'],
  ['user enabled default',0,'enabled boolean DEFAULT true','enabled boolean DEFAULT false'],
  ['new required user field',0,'    normalized_email character varying(254)','    unexpected_field text NOT NULL,\n    normalized_email character varying(254)'],
  ['user sequence default',0,"nextval('public.platform_users_id_seq'::regclass)","nextval('public.wrong_seq'::regclass)"],
  ['user primary key',0,'platform_users_pkey PRIMARY KEY (id)','platform_users_pkey PRIMARY KEY (normalized_email)'],
  ['user unique email',0,'uk_platform_users_email UNIQUE (normalized_email)','uk_platform_users_email UNIQUE (display_name)'],
  ['normalized email check',0,'lower(btrim((normalized_email)::text))','lower((normalized_email)::text)'],
  ['MFA attempts check',0,'mfa_failed_attempts <= 5','mfa_failed_attempts <= 9'],
  ['audit legal action',0,"'WRITE'::character varying","'UNREVIEWED_WRITE'::character varying"],
  ['audit resource size',0,'resource_id character varying(100)','resource_id character varying(20)'],
  ['audit non-null actor',0,'    platform_user_id bigint NOT NULL,\n    target_tenant_id bigint,\n    action','    platform_user_id bigint,\n    target_tenant_id bigint,\n    action'],
  ['audit default sequence',0,"nextval('public.platform_audit_logs_id_seq'::regclass)","nextval('public.wrong_seq'::regclass)"],
  ['audit primary key',0,'platform_audit_logs_pkey PRIMARY KEY (id)','platform_audit_logs_pkey PRIMARY KEY (platform_user_id)'],
  ['audit actor FK',0,'fk_platform_audit_user FOREIGN KEY (platform_user_id) REFERENCES public.platform_users(id)','fk_platform_audit_user FOREIGN KEY (platform_user_id) REFERENCES public.users(id)'],
  ['audit tenant FK',0,'fk_platform_audit_tenant FOREIGN KEY (target_tenant_id) REFERENCES public.tenants(id)','fk_platform_audit_tenant FOREIGN KEY (target_tenant_id) REFERENCES public.users(id)'],
  ['audit target user FK',0,'fk_platform_audit_target_user FOREIGN KEY (target_platform_user_id) REFERENCES public.platform_users(id)','fk_platform_audit_target_user FOREIGN KEY (target_platform_user_id) REFERENCES public.users(id)'],
  ['session primary key',6,'session_id uuid PRIMARY KEY','session_id uuid'],
  ['session user FK',6,'REFERENCES platform_users(id)','REFERENCES users(id)'],
  ['session security check',6,'CHECK (security_version > 0)','CHECK (security_version >= 0)'],
  ['session timestamp non-null',6,'started_at timestamptz NOT NULL','started_at timestamptz'],
  ['session last activity lower bound',6,'last_activity_at >= started_at','last_activity_at > started_at'],
  ['session absolute lower bound',6,'absolute_expires_at > started_at','absolute_expires_at >= started_at'],
  ['session absolute cap',6,"interval '12 hours'","interval '24 hours'"],
  ['session idle absolute cap',6,'idle_expires_at <= absolute_expires_at','idle_expires_at < absolute_expires_at'],
  ['session idle cap',6,"interval '30 minutes'","interval '60 minutes'"],
  ['browser nullable contract',7,'browser_binding_hash varchar(64);','browser_binding_hash varchar(64) NOT NULL;'],
  ['browser format',7,"'^[0-9a-f]{64}$'","'^[0-9a-z]{64}$'"],
];
for(const [name,index,from,to] of mutations)test('schema drift rejected: '+name,()=>{
  const changed={...migrations};assert.ok(changed[MIGRATIONS[index]].includes(from),'Mutation must affect real schema');
  const original=changed[MIGRATIONS[index]],table=name.startsWith('audit')?'platform_audit_logs':'platform_users';
  const block=index===0?original.match(new RegExp('CREATE TABLE public\\.'+table+' \\([\\s\\S]*?\\n\\);'))?.[0]:null;
  changed[MIGRATIONS[index]]=block?.includes(from)?original.replace(block,block.replace(from,to)):original.replace(from,to);
  assert.throws(()=>verifyMigrationContract(changed));
});
test('intervening V4_88 sentinel table mutation requires review',()=>assert.throws(()=>verifyMigrationContract({...migrations,[MIGRATIONS[5]]:migrations[MIGRATIONS[5]]+'\nALTER TABLE platform_users ADD COLUMN added text;'})));
test('V4_90 old-session backfill is not silently accepted',()=>assert.throws(()=>verifyMigrationContract({...migrations,[MIGRATIONS[7]]:migrations[MIGRATIONS[7]]+"\nUPDATE platform_sessions SET browser_binding_hash='x';"})));
test('additional audit CHECK cannot silently invalidate the fixture',()=>{
  const changed={...migrations,[MIGRATIONS[0]]:migrations[MIGRATIONS[0]].replace('    CONSTRAINT ck_platform_audit_action',"    CONSTRAINT unexpected_action CHECK (action <> 'WRITE'),\n    CONSTRAINT ck_platform_audit_action")};
  assert.throws(()=>verifyMigrationContract(changed));
});
test('invalid or injection-shaped marker rejected before generating SQL',()=>{
  for(const id of ['',"x'; COMMIT; --",'5245C2C1-0000-4000-8000-000000000001'])assert.throws(()=>sentinel(id));
  assert.throws(()=>prepareUserAudit({...f,action:'R3_UPGRADE_SENTINEL'}));
});
test('valid marker fits actual field lengths and normalized unique email',()=>{
  assert.ok(f.tag.length<=100&&f.email.length<=254&&f.displayName.length<=100);assert.equal(f.email,f.email.trim().toLowerCase());
  assert.notEqual(sentinel().id,sentinel().id);assert.equal(f.action,'WRITE');assert.equal(JSON.parse(f.detail).sentinel,f.id);
});
for(const [name,make] of [['user/audit',prepareUserAudit],['session',prepareSession]])test(name+' preparation is one explicit fail-fast transaction',()=>{
  const sql=make(f);assert.match(sql,/^BEGIN;\n/);assert.match(sql,/\nCOMMIT;\n$/);assert.equal((sql.match(/^COMMIT;/gm)||[]).length,1);
  assert.match(sql,/SET LOCAL statement_timeout='15s'/);assert.match(sql,/RAISE EXCEPTION/);assert.doesNotMatch(sql,/EXCEPTION\s+WHEN|ON CONFLICT|DELETE FROM|TRUNCATE/i);
  assert.ok(sql.indexOf('INSERT INTO')>sql.indexOf('BEGIN;'));assert.ok(sql.indexOf('INSERT INTO')<sql.indexOf('COMMIT;'));
});
test('user and audit share returned generated identity and cardinality checks',()=>{
  const sql=prepareUserAudit(f);assert.match(sql,/RETURNING id INTO STRICT own_user_id/);assert.match(sql,/VALUES\(own_user_id,'WRITE'/);
  assert.match(sql,/platform_users WHERE normalized_email=/);assert.match(sql,/platform_audit_logs WHERE resource_id=/);
  assert.match(sql,/<> 0/);assert.match(sql,/<> 1/);assert.match(sql,/false\) RETURNING/);
});
test('old-session preparation copies owner/version and uses transaction-stable 30m/12h bounds',()=>{
  const sql=prepareSession(f);assert.match(sql,/INTO STRICT own_user_id,own_security_version/);assert.match(sql,/FOR UPDATE/);
  assert.match(sql,/CURRENT_TIMESTAMP,CURRENT_TIMESTAMP/);assert.match(sql,/interval '30 minutes'/);assert.match(sql,/interval '12 hours'/);
  assert.doesNotMatch(sql,/browser_binding_hash/);assert.match(sql,/session_id='[^']+'::uuid/);
});
for(const version of [88,89,90])test('V'+version+' read is precisely scoped, counted and redacts authentication values',()=>{
  const sql=snapshotSql(f,version);assert.match(sql,/FROM platform_users u WHERE normalized_email=/);assert.match(sql,/FROM platform_audit_logs a WHERE resource_id=/);
  assert.match(sql,/authentication_fields_sha256/);assert.match(sql,/to_jsonb\(u\)-'password_hash'-'mfa_secret_encrypted'-'recovery_code_hashes_json'/);
  if(version===88)assert.doesNotMatch(sql,/FROM platform_sessions/);else assert.match(sql,/FROM platform_sessions s WHERE session_id=/);
  assert.deepEqual(parseSnapshot(JSON.stringify(evidence(version)),f,version),evidence(version));
});
for(const [name,change] of [
  ['missing user',v=>v.users=[]],['duplicate audit',v=>v.audits.push(clone(v.audits[0]))],['missing session',v=>v.sessions=[]],
  ['wrong email',v=>v.users[0].normalized_email='another@fixture.invalid'],['wrong audit marker',v=>v.audits[0].resource_id='another'],
  ['wrong audit owner',v=>v.audits[0].platform_user_id=42],['wrong audit action',v=>v.audits[0].action='READ'],
  ['wrong session owner',v=>v.sessions[0].platform_user_id=42],['wrong session UUID',v=>v.sessions[0].session_id=sentinel().id],
  ['secret projection leak',v=>v.users[0].password_hash='not-an-authentication-hash'],['missing required timestamp',v=>delete v.users[0].created_at],
  ['unexpected browser backfill',v=>v.sessions[0].browser_binding_hash='b'.repeat(64)],['missing browser field',v=>delete v.sessions[0].browser_binding_hash],
])test('snapshot rejects '+name,()=>{const v=evidence(90);change(v);assert.throws(()=>parseSnapshot(JSON.stringify(v),f,90));});
test('time predicates reject invalid security version and each invalid time boundary',()=>{
  const valid=evidence(89).sessions[0];validateWindow(valid);
  for(const patch of [{security_version:0},{last_activity_at:'2026-09-10T23:59:59Z'},{absolute_expires_at:stamp},
    {absolute_expires_at:'2026-09-11T12:00:01Z'},{idle_expires_at:'2026-09-11T00:30:01Z'},
    {absolute_expires_at:'2026-09-11T00:10:00Z'},{started_at:null}])assert.throws(()=>validateWindow({...valid,...patch}));
});
test('malformed, multiple-row or missing-array SQL output is rejected',()=>{
  for(const output of ['',JSON.stringify(evidence(88))+'\n{}',JSON.stringify({...evidence(88),audits:null}),JSON.stringify({users:[]})])assert.throws(()=>parseSnapshot(output,f,88));
});
test('field equality is structural, detects removals/changes, ignores only the new null browser column',()=>{
  const before=evidence(89),after=evidence(90);assertPreserved(before,after,{session:true});
  for(const change of [v=>v.users[0].authentication_fields_sha256='b'.repeat(64),v=>v.audits[0].created_at='2026-09-11T00:01:00Z',
    v=>delete v.sessions[0].revoked_at,v=>v.sessions[0].last_activity_at='2026-09-11T00:01:00Z']){
    const changed=clone(after);change(changed);assert.throws(()=>assertPreserved(before,changed,{session:true}));}
});
function fakeWorkflow({failureAt}={}){
  const result={databaseBusinessTestsStatus:'NOT_EXECUTED'},phase=phaseRecorder(result),calls=[];
  const migrate=step=>phase('migration:'+step,'MIGRATION',async()=>{calls.push(step);if(failureAt===step)throw Error('simulated migration failure');});
  const sql=async(statement,label)=>{calls.push(label);if(label===failureAt)throw Error('simulated SQL failure');
    if(label.includes('prepare'))return '';
    const version=label==='sentinel-after-v90'?90:label==='sentinel-session-before-v90'?89:88;
    return JSON.stringify(evidence(version));};
  return {result,calls,run:()=>runUpgradeSentinels({fixture:f,sql,migrate,phase})};
}
test('full in-memory workflow preserves fields across both upgrades without invoking any database',async()=>{
  const w=fakeWorkflow();const r=await w.run();assert.equal(r.oldSessionFieldsPreserved,true);
  assert.deepEqual(w.calls.filter(s=>s.startsWith('upgrade-')),['upgrade-to-88','upgrade-to-89','upgrade-to-90']);
  assert.ok(w.result.phases.every(p=>p.status==='SUCCEEDED'));assert.equal(w.result.databaseBusinessTestsStatus,'NOT_EXECUTED');
});
for(const [where,kind,lastMigration] of [
  ['sentinel-user-audit-prepare-v88','SENTINEL_PREPARATION','upgrade-to-88'],
  ['sentinel-session-prepare-v89','SENTINEL_PREPARATION','upgrade-to-89'],
  ['sentinel-before-v89','SENTINEL_VERIFY','upgrade-to-88'],
  ['upgrade-to-89','MIGRATION','upgrade-to-89'],
  ['sentinel-after-v90','SENTINEL_VERIFY','upgrade-to-90']
])test('stage accurately records and stops after '+where,async()=>{
  const w=fakeWorkflow({failureAt:where});await assert.rejects(w.run());
  assert.equal(w.result.failedStage,kind==='MIGRATION'?'migration:'+where:where);assert.equal(w.result.failureCategory,kind);
  assert.equal(w.calls.filter(s=>s.startsWith('upgrade-')).at(-1),lastMigration);assert.equal(w.result.phases.at(-1).status,'FAILED');
  assert.equal(w.result.databaseBusinessTestsStatus,'NOT_EXECUTED');
  if(kind!=='MIGRATION')assert.equal(w.result.phases.find(p=>p.stage==='migration:'+lastMigration).status,'SUCCEEDED');
});
test('runner uses fail-fast psql, shared workflow, phase recording and frozen-runner guard',()=>{
  const runner=fs.readFileSync(new URL('./platform-r5-database-batch.mjs',import.meta.url),'utf8');
  assert.match(runner,/'ON_ERROR_STOP=1'/);assert.match(runner,/runUpgradeSentinels\(/);assert.match(runner,/phaseRecorder\(result\)/);
  assert.match(runner,/Unsealed runner/);assert.match(runner,/databaseBusinessTestsStatus:'NOT_EXECUTED'/);
  assert.doesNotMatch(runner,/R3_UPGRADE_SENTINEL|select row_to_json\(s\)::text from platform_sessions s;/);
});

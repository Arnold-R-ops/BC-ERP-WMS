// R10-specific binding contracts. No Maven process, driver or database is started.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {r6Plans,runR10Acceptance,finalizeR10Results,executePlatformSuite,suiteContext,REPORT_DIRECTORIES} from './platform-r10-database-batch.mjs';
import {runR6Phases} from './platform-r6-stages.mjs';
import {executePlatformMavenSuite,LANDING_SUITE,reportPaths,suiteInvocation} from './platform-report-runner.mjs';
// The approved repair baseline supplies names and revision fingerprints only; no historical scores.
const input={...JSON.parse(fs.readFileSync('D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-case-catalog-repair-20260918/a1/input-manifest.json','utf8')),methodLedgerVersion:'R9_EIGHT_METHODS_ELEVEN_REVISIONS'};
const hash='0'.repeat(64),target=r6Plans(input)[0].plan[0];
test('actual r10 import is inert and exports the same Maven and stage engines',()=>{
 assert.equal(executePlatformSuite,executePlatformMavenSuite);assert.equal(runR10Acceptance,runR6Phases);
});
test('actual r10 plan remains 1 then 42 then 63 and absent results cannot borrow historical passes',()=>{
 assert.deepEqual(r6Plans(input).map(p=>p.planned),[1,42,63]);
 const row={status:'PASSED'};finalizeR10Results(row,input);
 assert.equal(row.status,'FAILED');assert.equal(row.r10.executionInstances.unexecuted,106);
 assert.equal(row.coverage.unexecuted,63);assert.equal(row.coverage.passed,0);
});
test('two fixed report roots share one immutable source POM and compiled output',()=>{
 const a=suiteContext('landing',hash),b=suiteContext('database',hash);
 assert.notEqual(a.reportRoot,b.reportRoot);assert.equal(a.reportRoot,REPORT_DIRECTORIES.landing);assert.equal(b.reportRoot,REPORT_DIRECTORIES.database);
 for(const key of ['source','build','maven','repo','java','runDirectory','pom','pomSha256'])assert.equal(a[key],b[key]);
 assert.ok(a.source.endsWith('/r10/snapshot/source'));assert.ok(a.build.endsWith('/r10/build/a1'));
 assert.ok(a.reportRoot.endsWith('/landing-reports'));assert.ok(b.reportRoot.endsWith('/reports'));
});
test('report context rejects a missing or arbitrary mode without environment fallback',()=>{
 for(const mode of [undefined,'','full','r6','database/../landing','toString'])assert.throws(()=>suiteContext(mode,hash));
});
test('report context rejects an unbound POM fingerprint',()=>{
 for(const value of [undefined,'','0'.repeat(63),'x'.repeat(64)])assert.throws(()=>suiteContext('landing',value));
});
test('all three phase XML and stdout paths are disjoint between landing and database evidence',()=>{
 for(const phase of ['independent','sequential','full']){
  const a=reportPaths(suiteContext('landing',hash),phase,LANDING_SUITE);
  const b=reportPaths(suiteContext('database',hash),phase,LANDING_SUITE);
  assert.notEqual(a.directory,b.directory);assert.notEqual(a.xml,b.xml);assert.notEqual(a.stdout,b.stdout);
  assert.equal(a.defaultDirectory,b.defaultDirectory);
 }
});
test('both modes use the same Maven builder but retain exact authorization and dedicated r10 target',()=>{
 const landing=suiteInvocation(suiteContext('landing',hash),'independent',LANDING_SUITE);
 const db=suiteInvocation(suiteContext('database',hash),'independent',target,{mode:'database',database:{url:'jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r10',clusterId:'1234567890123456789'}});
 assert.ok(landing.args.includes('-Dp3.r2.database-authorized=false'));
 assert.ok(db.args.includes('-Dp3.r2.database-authorized=true'));
 assert.ok(db.args.includes('-Dp3.r2.migration-authorized=false'));
 assert.equal(landing.args.at(-1),db.args.at(-1));
 assert.equal(landing.args[landing.args.indexOf('-f')+1],db.args[db.args.indexOf('-f')+1]);
 assert.ok(landing.args.some(a=>a.includes('/landing-reports/independent')));
 assert.ok(db.args.some(a=>a.includes('/reports/independent')));
});
test('r10 real build and database callbacks use shared execution with separated report roots and preservation gates',()=>{
 const source=fs.readFileSync(new URL('./platform-r10-database-batch.mjs',import.meta.url),'utf8');
 for(const literal of ["suiteContext('landing',manifest.derivedTestPom.sha256)","context:suiteContext('database',manifest.derivedTestPom.sha256)",
 "executePlatformSuite({context,phase,suite:LANDING_SUITE,mode:'landing'})","return executePlatformSuite({","assertLandingAndDefault(built)",
 "Landing wrote into database reports","built.landings?.length!==3","runR10Acceptance({input:manifest","stopOwnedDatabase("])
  assert.ok(source.includes(literal),literal);
 assert.ok(!source.includes('release_r6'));assert.ok(!source.includes('p3_r6_'));
});
import {BUDGETS,JAVA_GATES,NODE_GATES,AUTH} from './platform-r10-plan.mjs';
test('finite budgets are frozen before execution, including migration and real Maven',()=>{
 assert.ok(Object.isFrozen(BUDGETS));for(const n of Object.values(BUDGETS))assert.ok(Number.isInteger(n)&&n>0);
 assert.equal(BUDGETS.readyMs,40000);assert.equal(BUDGETS.cleanupMs,60000);
 assert.equal(BUDGETS.compileMs,300000);assert.equal(BUDGETS.migrationMs,180000);
 assert.equal(BUDGETS.mavenSuiteMs,240000);assert.ok(AUTH.includes('r10-arnold-elevated'));
 const runner=fs.readFileSync(new URL('./platform-report-runner.mjs',import.meta.url),'utf8');
 assert.ok(runner.includes('timeoutMs:240000'));assert.ok(runner.includes('-Dsurefire.timeout=120'));
});
test('frozen gate inventory requires fresh actual counts rather than historical total',()=>{
 assert.equal(JAVA_GATES.reduce((n,e)=>n+e[1],0),163);
 assert.equal(NODE_GATES.reduce((n,e)=>n+e[1],0),613);
 assert.equal(new Set(NODE_GATES.map(e=>e[0])).size,NODE_GATES.length);
 const batch=fs.readFileSync(new URL('./platform-r10-database-batch.mjs',import.meta.url),'utf8');
 assert.ok(batch.includes('JSON.stringify(built.requiredGates)!==JSON.stringify(REQUIRED_GATES)'));
 assert.ok(batch.includes('oldClassesReused:false'));
});
test('r10 test-layer target rejects r9 and requires least-privilege application role',()=>{
 // Historical target contract reads its frozen source, never historical scores.
 const base=new URL('file:///D:/ERP_WMS/BC%20warehouse/2G/target/platform-db-acceptance-20260918/r10/snapshot/source/src/test/java/com/wms/platformverification/');
 const isolation=fs.readFileSync(new URL('PlatformR2Isolation.java',base),'utf8');
 const tests=fs.readFileSync(new URL('PlatformR2IsolationTest.java',base),'utf8');
 assert.ok(isolation.includes('platform-db-acceptance-20260918/r10/pgdata'));
 for(const name of ['p3_m1_platform_release_r10','p3_m1_platform_upgrade_r10','p3_r10_app','p3_r10_migrator'])assert.ok(isolation.includes(name));
 assert.ok(tests.includes('jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r9'));
 const batch=fs.readFileSync(new URL('./platform-r10-database-batch.mjs',import.meta.url),'utf8');
 assert.ok(batch.includes("APP+'|f|f\\nf|f|f|f\\n0'"));assert.ok(batch.includes('c.relowner='));
});
test('real entry consumes s2 environment chain and has one startup plus identity-bound shutdown',()=>{
 const batch=fs.readFileSync(new URL('./platform-r10-database-batch.mjs',import.meta.url),'utf8');
 assert.ok(batch.includes('environment=createR10Environment('));assert.ok(batch.includes('adapter=environment.adapter'));
 assert.ok(batch.includes('readinessMs:BUDGETS.readyMs,cleanupMs:BUDGETS.cleanupMs'));
 assert.ok(batch.includes('environment.closeout()'));assert.ok(batch.includes('environment.firstFailure()??result.lifecycle.firstFailure'));
 assert.doesNotMatch(batch,/readinessMs:10000|cleanupMs:45000|Get-CimInstance|function pgProcess/);
});
test('r10 revised ledger covers eight distinct methods with eleven chained revisions',()=>{
 assert.equal(input.methodLedgerVersion,'R9_EIGHT_METHODS_ELEVEN_REVISIONS');
 assert.equal(input.methodBodyChanges.length,8);assert.equal(input.methodRevisionHistory.length,11);
 for(const change of input.methodBodyChanges)assert.ok(input.businessTests.some(e=>e.path===change.path&&e.tests.some(t=>t.name===change.name&&t.sha256===change.afterSha256)));
});
test('r10 ledger refuses missing revisions or changed final fingerprints',()=>{
 for(const mutate of [i=>i.methodRevisionHistory.pop(),i=>i.methodBodyChanges[0].afterSha256='0'.repeat(64)]){
  const row=structuredClone(input);mutate(row);assert.throws(()=>r6Plans(row));
 }
});
test('shared actual Maven chain binds export temp directory in both probe and database modes',()=>{
 for(const mode of ['landing','database']){
  const context=suiteContext(mode,hash),suite=mode==='landing'?LANDING_SUITE:target;
  const call=suiteInvocation(context,'independent',suite,mode==='landing'?{}:{mode,database:{url:'jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r10',clusterId:'1234567890123456789'}});
  assert.ok(call.args.includes('-Dp3.export.fixture-temp-root='+context.runDirectory+'/export-fixture-temp'));
 }
});
test('shared actual Maven chain rejects unowned or old export temp roots',()=>{
 for(const root of ['C:/Windows/Temp','D:/old/r8/export-fixture-temp','relative']){
  assert.throws(()=>suiteInvocation({...suiteContext('landing',hash),exportFixtureTempRoot:root},'independent',LANDING_SUITE));
 }
});

import {javaCatalogPreflight} from './platform-r10-backend-snapshot.mjs';
test('r10 actual seal uses shared catalog before creating or copying files',()=>{
 const source=fs.readFileSync(new URL('./platform-r10-backend-snapshot.mjs',import.meta.url),'utf8');
 assert.ok(source.includes("from './platform-case-catalog.mjs'"));
 const pre=source.indexOf('const javaCaseCatalog=javaCatalogPreflight()');
 assert.ok(pre>=0&&pre<source.indexOf('copy(ROOT,SOURCE,source)'));
 const rows=javaCatalogPreflight('D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260918/r10/snapshot/source');assert.equal(rows.reduce((n,r)=>n+r.methods.length,0),163);
 assert.equal(rows.find(r=>r.name.endsWith('.PlatformMfaFailureFixtureTest')).methods.length,22);
 assert.ok(rows.find(r=>r.name.endsWith('.PlatformR2IsolationTest')).methods.includes('wrongDestinationsRejectedBeforeAnyConnector(String)[15]'));
});
test('r10 gates and real probes retain dedicated POM and disjoint guard paths',()=>{
 const source=fs.readFileSync(new URL('./platform-r10-database-batch.mjs',import.meta.url),'utf8');
 assert.ok(source.includes('mavenBaseArgs(context)'));assert.ok(!source.includes('dedicated:false'));
 assert.ok(source.includes("BUILD+'/no-database-reports/TEST-'"));
 assert.ok(source.includes("-Dplatform.verification.reportsDirectory="));
 assert.equal(executePlatformSuite,executePlatformMavenSuite);
});

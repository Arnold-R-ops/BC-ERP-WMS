// R7-specific binding contracts. No Maven process, driver or database is started.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {r6Plans,runR7Acceptance,finalizeR7Results,executePlatformSuite,suiteContext,REPORT_DIRECTORIES} from './platform-r7-database-batch.mjs';
import {runR6Phases} from './platform-r6-stages.mjs';
import {executePlatformMavenSuite,LANDING_SUITE,reportPaths,suiteInvocation} from './platform-report-runner.mjs';
const input=JSON.parse(fs.readFileSync('D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-report-binding-repair-20260913/a2/input-manifest.json','utf8'));
const hash='0'.repeat(64),target=r6Plans(input)[0].plan[0];
test('actual r7 import is inert and exports the same Maven and stage engines',()=>{
 assert.equal(executePlatformSuite,executePlatformMavenSuite);assert.equal(runR7Acceptance,runR6Phases);
});
test('actual r7 plan remains 1 then 42 then 63 and absent results cannot borrow historical passes',()=>{
 assert.deepEqual(r6Plans(input).map(p=>p.planned),[1,42,63]);
 const row={status:'PASSED'};finalizeR7Results(row,input);
 assert.equal(row.status,'FAILED');assert.equal(row.r7.executionInstances.unexecuted,106);
 assert.equal(row.coverage.unexecuted,63);assert.equal(row.coverage.passed,0);
});
test('two fixed report roots share one immutable source POM and compiled output',()=>{
 const a=suiteContext('landing',hash),b=suiteContext('database',hash);
 assert.notEqual(a.reportRoot,b.reportRoot);assert.equal(a.reportRoot,REPORT_DIRECTORIES.landing);assert.equal(b.reportRoot,REPORT_DIRECTORIES.database);
 for(const key of ['source','build','maven','repo','java','runDirectory','pom','pomSha256'])assert.equal(a[key],b[key]);
 assert.ok(a.source.endsWith('/r7/snapshot/source'));assert.ok(a.build.endsWith('/r7/build/a1'));
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
test('both modes use the same Maven builder but retain exact authorization and dedicated r7 target',()=>{
 const landing=suiteInvocation(suiteContext('landing',hash),'independent',LANDING_SUITE);
 const db=suiteInvocation(suiteContext('database',hash),'independent',target,{mode:'database',database:{url:'jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r7',clusterId:'1234567890123456789'}});
 assert.ok(landing.args.includes('-Dp3.r2.database-authorized=false'));
 assert.ok(db.args.includes('-Dp3.r2.database-authorized=true'));
 assert.ok(db.args.includes('-Dp3.r2.migration-authorized=false'));
 assert.equal(landing.args.at(-1),db.args.at(-1));
 assert.equal(landing.args[landing.args.indexOf('-f')+1],db.args[db.args.indexOf('-f')+1]);
 assert.ok(landing.args.some(a=>a.includes('/landing-reports/independent')));
 assert.ok(db.args.some(a=>a.includes('/reports/independent')));
});
test('r7 real build and database callbacks use shared execution with separated report roots and preservation gates',()=>{
 const source=fs.readFileSync(new URL('./platform-r7-database-batch.mjs',import.meta.url),'utf8');
 for(const literal of ["suiteContext('landing',manifest.derivedTestPom.sha256)","context:suiteContext('database',manifest.derivedTestPom.sha256)",
 "executePlatformSuite({context,phase,suite:LANDING_SUITE,mode:'landing'})","return executePlatformSuite({","assertLandingAndDefault(built)",
 "Landing wrote into database reports","built.landings?.length!==3","runR7Acceptance({input:manifest","stopOwnedDatabase("])
  assert.ok(source.includes(literal),literal);
 assert.ok(!source.includes('release_r6'));assert.ok(!source.includes('p3_r6_'));
});


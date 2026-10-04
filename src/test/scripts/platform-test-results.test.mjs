// No database, HTTP server or business Maven test is started. R4 XML is read-only evidence.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {parseJUnitXml,readJUnitReport,assessSuite,runSuitePlan,summarizeSuiteResults,ReportError} from './platform-test-results.mjs';
import {BUSINESS_SELECTORS,platformBusinessPlan,runPlatformBusinessAcceptance,finalizePlatformBusinessResults} from './platform-r5-database-batch.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G',R4=ROOT+'/docs/releases/platform-db-acceptance-20260911/r4';
const BUILD=ROOT+'/target/platform-db-acceptance-20260911/r4/build/a1';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const manifest=read(R4+'/backend-input-manifest.json'), original=read(R4+'/database-result.json');
const reconciliation=read(R4+'/independent-test-reconciliation.json');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const mixed=fs.readFileSync(new URL('./fixtures/platform-junit/mixed.xml',import.meta.url),'utf8');
const GOOD=Object.freeze({exited:true,exitCode:0,timedOut:false,aborted:false,spawnError:null,signal:null,outputComplete:true,terminationConfirmed:true});
const plan={name:'fixture.Suite',selector:'fixture.Suite',methods:['alpha','beta','gamma','delta']};
const escape=s=>s.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;');
function xmlFor(p,{outcomes={},head={},methods=p.methods,extra=''}={}){
  const counts={tests:methods.length,failures:methods.filter(n=>outcomes[n]==='failure').length,
    errors:methods.filter(n=>outcomes[n]==='error').length,skipped:methods.filter(n=>outcomes[n]==='skipped').length,...head};
  const attrs=Object.entries(counts).map(([k,v])=>k+'="'+v+'"').join(' ');
  return '<testsuite name="'+p.name+'" '+attrs+'>'+extra+methods.map((name,i)=>{
    const attrs='classname="'+p.name+'" name="'+escape(name)+'"';
    if(outcomes[name])return '<testcase '+attrs+'><'+outcomes[name]+' message="synthetic-only"/></testcase>';
    return i%2?'<testcase '+attrs+'><system-out><![CDATA[synthetic <testcase/>]]></system-out></testcase>':'<testcase '+attrs+'/>';
  }).join('')+'</testsuite>';
}
const parsedMixed=parseJUnitXml(mixed);
test('structured parser preserves all mixed self-closing and paired cases including CDATA',()=>{
  assert.equal(parsedMixed.tests,4);assert.deepEqual(parsedMixed.cases.map(c=>c.name),plan.methods);
  assert.equal(assessSuite(plan,{process:GOOD,report:parsedMixed}).status,'PASSED');
});
test('only safe fields leave XML parser; properties stdout and failure detail never escape',()=>{
  const report=parseJUnitXml(xmlFor(plan,{outcomes:{alpha:'failure'},extra:'<properties><property name="synthetic-secret" value="do-not-return"/></properties>'}));
  assert.doesNotMatch(JSON.stringify(report),/synthetic-secret|do-not-return|synthetic-only|system-out|CDATA/);
  assert.equal(report.failures,1);
});
test('attribute order, single quotes, whitespace, XML entities and Unicode are handled structurally',()=>{
  const report=parseJUnitXml("<testsuite errors='0' name='fixture.Suite'\n skipped='0' failures='0' tests='1'><testcase\n classname='fixture.Suite' name='检查 &amp; &quot;safe&quot;'/></testsuite>");
  assert.equal(report.cases[0].name,'检查 & "safe"');
});
test('XML attributes cannot shadow structural .NET properties',()=>{
  const report=parseJUnitXml(mixed.replace('<testsuite ','<testsuite LocalName="bad" ChildNodes="bad" ').replace('name="alpha"','name="alpha" NodeType="bad"'));
  assert.equal(report.tests,4);
});
for(const [label,xml] of [
  ['empty',''],['truncated',mixed.slice(0,-15)],['wrong root','<testsuites/>'],['two roots',mixed+mixed],
  ['missing count',mixed.replace('tests="4"','')],['negative count',mixed.replace('tests="4"','tests="-1"')],
  ['NaN count',mixed.replace('tests="4"','tests="NaN"')],['fraction count',mixed.replace('tests="4"','tests="1.5"')],
  ['oversize count',mixed.replace('tests="4"','tests="2147483648"')],
  ['missing method',mixed.replace('name="alpha"','')],['missing classname',mixed.replace('classname="fixture.Suite"','')],
  ['nested case',mixed.replace('<testcase name="alpha" classname="fixture.Suite"/>','<testcase name="alpha" classname="fixture.Suite"><testcase name="forged"/></testcase>')],
  ['namespaced root',mixed.replace('<testsuite ','<testsuite xmlns="urn:unexpected" ')],
  ['double outcome',mixed.replace('<testcase name="alpha" classname="fixture.Suite"/>','<testcase name="alpha" classname="fixture.Suite"><failure/><error/></testcase>')],
  ['internal DTD','<!DOCTYPE testsuite [<!ENTITY x "synthetic">]>'+mixed.replace(/<\?xml[^?]*\?>/, '')],
  ['external entity','<!DOCTYPE testsuite [<!ENTITY x SYSTEM "file:///not-authorized-fixture">]>'+mixed.replace(/<\?xml[^?]*\?>/, '').replace('synthetic text','&x;')],
  ['external DTD','<!DOCTYPE testsuite SYSTEM "https://invalid.example/never-request.dtd">'+mixed.replace(/<\?xml[^?]*\?>/, '')],
])test('unsafe or malformed report rejected: '+label,()=>{
  assert.throws(()=>parseJUnitXml(xml),e=>e instanceof ReportError&&e.code==='INVALID_OR_UNSAFE_JUNIT_XML');
});
test('oversized input rejected before a parser process is needed',()=>{
  assert.throws(()=>parseJUnitXml('x'.repeat(8388609)),ReportError);
});
for(const [label,changes,issue] of [
  ['duplicate',{methods:['alpha','beta','gamma','gamma']},'DUPLICATE_METHODS'],
  ['missing',{methods:['alpha','beta','gamma']},'MISSING_METHODS'],
  ['extra',{methods:[...plan.methods,'extra']},'EXTRA_METHODS'],
  ['header tests',{head:{tests:999}},'HEADER_TESTS_MISMATCH'],
  ['header failures',{head:{failures:1}},'HEADER_FAILURES_MISMATCH'],
  ['header errors',{head:{errors:1}},'HEADER_ERRORS_MISMATCH'],
  ['header skipped',{head:{skipped:1}},'HEADER_SKIPPED_MISMATCH'],
])test('coverage validation fails closed: '+label,()=>{
  const r=assessSuite(plan,{process:GOOD,report:parseJUnitXml(xmlFor(plan,changes))});
  assert.equal(r.status,'REPORT_VALIDATION_FAILED');assert.ok(r.issues.includes(issue));
});
test('wrong suite or testcase class cannot count as passing expected method',()=>{
  for(const xml of [mixed.replace('name="fixture.Suite"','name="wrong.Suite"'),mixed.replace('classname="fixture.Suite"','classname="wrong.Suite"')]){
    const r=assessSuite(plan,{process:GOOD,report:parseJUnitXml(xml)});assert.equal(r.status,'REPORT_VALIDATION_FAILED');assert.ok(r.counts.unverified>0);
  }
});
for(const outcome of ['failure','error','skipped'])for(const exitCode of [0,1])test(outcome+' is not a pass even with process exit '+exitCode,()=>{
  const r=assessSuite(plan,{process:{...GOOD,exitCode},report:parseJUnitXml(xmlFor(plan,{outcomes:{alpha:outcome}}))});
  assert.equal(r.status,outcome==='skipped'?'UNEXPECTED_SKIP':'BUSINESS_TEST_FAILED');
  assert.equal(r.counts[outcome==='failure'?'failures':outcome==='error'?'errors':'skipped'],1);assert.equal(r.counts.passed,3);
  assert.equal(r.executionStatus,exitCode===0?'SUCCEEDED':'FAILED');
});
for(const [label,patch] of [
  ['nonzero',{exitCode:1}],['missing exit',{exitCode:null}],['not exited',{exited:false}],['timed out',{timedOut:true}],
  ['timeout alias',{timeout:true}],['aborted',{aborted:true}],['spawn failed',{spawnError:'EACCES'}],
  ['signal',{signal:'SIGTERM'}],['output incomplete',{outputComplete:false}],['termination unconfirmed',{terminationConfirmed:false}],
])test('valid XML cannot hide executor failure: '+label,()=>{
  const r=assessSuite(plan,{process:{...GOOD,...patch},report:parsedMixed});
  assert.equal(r.status,'EXECUTION_FAILED');assert.equal(r.reportStatus,'VALID');
});
test('unexecuted is distinct from an executed suite with an unreadable report',()=>{
  const absent=assessSuite(plan,{report:parsedMixed});assert.equal(absent.status,'NOT_EXECUTED');assert.equal(absent.counts.unexecuted,4);assert.equal(absent.counts.passed,0);
  const missing=assessSuite(plan,{process:GOOD,reportError:'REPORT_MISSING_OR_UNREADABLE'});assert.equal(missing.status,'REPORT_PARSE_FAILED');assert.equal(missing.counts.unverified,4);assert.equal(missing.counts.unexecuted,0);
});
test('summary recalculates evidence and rejects duplicated or unexpected suite rows',()=>{
  const row=assessSuite(plan,{process:{...GOOD,exitCode:1},report:parsedMixed});row.status='PASSED';row.counts.passed=999;
  const r=summarizeSuiteResults([plan],[row]);assert.equal(r.status,'FAILED');assert.equal(r.coverage.passed,4);assert.equal(r.coverage.executionFailures,1);
  assert.throws(()=>summarizeSuiteResults([plan],[row,row]));assert.throws(()=>summarizeSuiteResults([plan],[{...row,name:'extra'}]));
});
test('empty or duplicated expected methods and changed 8/63 plan are rejected',()=>{
  assert.throws(()=>summarizeSuiteResults([{...plan,methods:[]}],[]));assert.throws(()=>summarizeSuiteResults([{...plan,methods:['x','x']}],[]));
  assert.throws(()=>platformBusinessPlan({...manifest,expectedBusinessTests:manifest.expectedBusinessTests.slice(1)}));
});
test('shared loop stops on failure and does not classify remaining suites as skips',async()=>{
  const second={...plan,name:'fixture.Second',selector:'fixture.Second'},calls=[];
  const r=await runSuitePlan({plan:[plan,second],execute:async p=>{calls.push(p.name);return {process:GOOD,xml:xmlFor(p,{outcomes:{alpha:'failure'}})};}});
  assert.equal(r.status,'FAILED');assert.deepEqual(calls,['fixture.Suite']);assert.equal(r.coverage.failures,1);assert.equal(r.coverage.unexecuted,4);assert.equal(r.coverage.skipped,0);
});
test('executor throws are sanitized and never promoted to business pass',async()=>{
  const r=await runSuitePlan({plan:[plan],execute:async()=>{throw Error('synthetic-private-message');}});
  assert.equal(r.status,'FAILED');assert.equal(r.coverage.executionFailures,1);assert.equal(r.coverage.unverified,4);
  assert.doesNotMatch(JSON.stringify(r),/synthetic-private-message/);
});
test('missing file is a report failure, not zero passed tests',async()=>{
  const r=await runSuitePlan({plan:[plan],execute:async()=>({process:GOOD,xmlFile:new URL('./fixtures/platform-junit/absent.xml',import.meta.url)})});
  assert.equal(r.failureCategory,'REPORT_PARSE_FAILED');assert.equal(r.coverage.unverified,4);
});
test('real batch entry imports without running initialization and uses the shared result path',()=>{
  assert.equal(BUSINESS_SELECTORS.length,8);assert.equal(platformBusinessPlan(manifest).reduce((n,p)=>n+p.methods.length,0),63);
  const entry=fs.readFileSync(new URL('./platform-r5-database-batch.mjs',import.meta.url),'utf8');
  assert.match(entry,/const acceptance=await runPlatformBusinessAcceptance\(/);assert.match(entry,/finalizePlatformBusinessResults\(result,manifest\)/);
  assert.match(entry,/platform-test-results.test.mjs/);assert.match(entry,/built\.parser\?\.passed!==67/);
  assert.match(entry,/Pre-existing business XML/);assert.doesNotMatch(entry,/matchAll\(\/<testcase|function report\(/);
  assert.match(entry,/Unsealed runner/);assert.match(entry,/Existing target or occupied port; STOP/);
});
test('actual batch entry and actual finalizer accept all 8 synthetic suites and exactly 63 methods',async()=>{
  const calls=[];const r=await runPlatformBusinessAcceptance({input:manifest,execute:async p=>{calls.push(p.selector);return {process:GOOD,xml:xmlFor(p)};}});
  assert.deepEqual(calls,[...BUSINESS_SELECTORS]);assert.equal(r.status,'PASSED');assert.equal(r.coverage.passed,63);
  const batch={status:'PASSED',suites:r.suites};finalizePlatformBusinessResults(batch,manifest);
  assert.equal(batch.status,'PASSED');assert.equal(batch.databaseBusinessTestsStatus,'PASSED');assert.equal(batch.coverage.unexecuted,0);
});
for(const [label,make,category] of [
  ['damaged',p=>({process:GOOD,xml:'<broken'}),'REPORT_PARSE_FAILED'],
  ['count mismatch',p=>({process:GOOD,xml:xmlFor(p,{head:{tests:999}})}),'REPORT_VALIDATION_FAILED'],
  ['business failure',p=>({process:GOOD,xml:xmlFor(p,{outcomes:{[p.methods[0]]:'failure'}})}),'BUSINESS_TEST_FAILED'],
  ['process failure',p=>({process:{...GOOD,exitCode:1},xml:xmlFor(p)}),'EXECUTION_FAILED'],
  ['skip',p=>({process:GOOD,xml:xmlFor(p,{outcomes:{[p.methods[0]]:'skipped'}})}),'UNEXPECTED_SKIP'],
])test('actual entry and finalizer preserve '+label+' and 59 not executed methods',async()=>{
  let calls=0;const r=await runPlatformBusinessAcceptance({input:manifest,execute:async p=>{calls++;return make(p);}});
  assert.equal(calls,1);assert.equal(r.failureCategory,category);assert.equal(r.coverage.unexecuted,59);
  const batch={status:'PASSED',suites:r.suites};finalizePlatformBusinessResults(batch,manifest);
  assert.equal(batch.status,'FAILED');assert.equal(batch.failureCategory,category);assert.equal(batch.coverage.unexecuted,59);
});
const r4XmlBefore=reconciliation.Suites.map(s=>({name:s.Suite,path:BUILD+'/surefire-reports/TEST-'+s.Suite+'.xml',hash:s.XmlSha256}));
for(const expected of reconciliation.Suites)test('read-only r4 original XML: '+expected.Suite+' / '+expected.ExpectedMethods,()=>{
  const file=BUILD+'/surefire-reports/TEST-'+expected.Suite+'.xml';assert.equal(hash(file),expected.XmlSha256);
  const parsed=readJUnitReport(file);const p=platformBusinessPlan(manifest).find(p=>p.name===expected.Suite);
  assert.equal(parsed.tests,expected.ExpectedMethods);assert.equal(assessSuite(p,{process:GOOD,report:parsed}).status,'PASSED');
  assert.equal(hash(file),expected.XmlSha256);
});
test('r4 process evidence and XML replay through actual entry yields 37 passed and 26 not executed without rerunning',async()=>{
  const r=await runPlatformBusinessAcceptance({input:manifest,execute:async p=>{
    const saved=original.suites.find(s=>s.selector===p.selector);if(!saved)return null;
    assert.equal(saved.exitCode,0);assert.equal(saved.timeout,false);
    // R4 only retained exitCode/timeout at suite level. Other completion evidence is the preserved BUILD SUCCESS log.
    const log=ROOT+'/target/platform-db-acceptance-20260911/r4/test-'+p.name.split('.').at(-1)+'.log';
    assert.equal(hash(log),saved.logSha256);assert.match(fs.readFileSync(log,'utf8'),/BUILD SUCCESS/);
    return {process:{...GOOD,exitCode:saved.exitCode,timeout:saved.timeout},xmlFile:BUILD+'/surefire-reports/TEST-'+p.name+'.xml',logSha256:saved.logSha256};
  }});
  assert.equal(r.status,'INCOMPLETE');assert.equal(r.coverage.passed,37);assert.equal(r.coverage.unexecuted,26);assert.equal(r.coverage.reportFailures,0);
  const batch={status:'FAILED',suites:r.suites};finalizePlatformBusinessResults(batch,manifest);assert.equal(batch.status,'FAILED');assert.equal(batch.databaseBusinessTestsStatus,'INCOMPLETE');
  for(const item of r4XmlBefore)assert.equal(hash(item.path),item.hash);
  assert.equal(read(R4+'/database-result.json').status,'FAILED');
});
test('actual finalizer before business stage records all 63 methods not executed',()=>{
  const batch={status:'FAILED',suites:[],failedStage:'synthetic-migration-failure'};
  finalizePlatformBusinessResults(batch,manifest);assert.equal(batch.coverage.unexecuted,63);assert.equal(batch.databaseBusinessTestsStatus,'NOT_EXECUTED');
  assert.equal(batch.failedStage,'synthetic-migration-failure');assert.equal(batch.coverage.unverified,0);
});
test('broken XML with nonzero exit retains both report and executor failures',async()=>{
  const r=await runSuitePlan({plan:[plan],execute:async()=>({process:{...GOOD,exitCode:1},xml:'broken'})});
  assert.equal(r.coverage.reportFailures,1);assert.equal(r.coverage.executionFailures,1);assert.equal(r.coverage.unverified,4);
});
test('identity admission rejection prevents executor and preserves later unexecuted accounting',async()=>{
  let called=false,last;await assert.rejects(()=>runPlatformBusinessAcceptance({input:manifest,beforeSuite:async()=>{throw Error('synthetic-identity-rejected');},
    execute:async()=>{called=true;},onUpdate:r=>{last=r;}}));
  assert.equal(called,false);assert.equal(last.coverage.unexecuted,63);
});
test('source parser disables DTD and external resolution without stdout detail forwarding',()=>{
  const ps=fs.readFileSync(new URL('./platform-junit-xml.ps1',import.meta.url),'utf8');
  assert.match(ps,/DtdProcessing\]::Prohibit/);assert.equal((ps.match(/XmlResolver = \$null/g)||[]).length,2);
  assert.doesNotMatch(ps,/Write-Error|Exception\.Message|Invoke-WebRequest|WebClient|SqlConnection|Npgsql/);
});

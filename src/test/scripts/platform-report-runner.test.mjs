// No Maven, PostgreSQL, JDBC, HTTP or product execution. Parser subprocess only.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {
 LANDING_SUITE, reportPaths, inventory, requirePreserved, mavenBaseArgs,
 suiteInvocation, validateEffectiveConfig, inspectSuiteArtifacts, createReportPom, executePlatformMavenSuite
} from './platform-report-runner.mjs';
import {r6Plans,runR6Acceptance} from './platform-r6-database-batch.mjs';
import {runSuitePlan} from './platform-test-results.mjs';

const input=JSON.parse(fs.readFileSync('D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-fixture-repair-20260912/a2/input-manifest.json','utf8'));
const target=r6Plans(input)[0].plan[0];
const invocation='00000000-0000-4000-8000-000000000071';
const database={url:'jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r7',clusterId:'7684313611194871252'};
const okay={exitCode:0,exited:true,outputComplete:true,terminationConfirmed:true};
const normal=p=>path.resolve(p).replaceAll('\\','/');
function context(root=path.resolve(os.tmpdir(),'platform-report-runner-contract-context')){
 return Object.fromEntries(Object.entries({source:root+'/source',build:root+'/backend',maven:root+'/maven',repo:root+'/repository',java:root+'/jdk/bin/java.exe',pom:root+'/source/platform-report-pom.xml',runDirectory:root+'/run',reportRoot:root+'/reports'}).map(([k,v])=>[k,normal(v)]));
}
function minimalXml(suite=LANDING_SUITE,{method=suite.methods[0],tests=1,failure=false,error=false,skipped=false}={}){
 return `<testsuite name="${suite.name}" tests="${tests}" failures="${failure?1:0}" errors="${error?1:0}" skipped="${skipped?1:0}"><testcase classname="${suite.name}" name="${method}">${failure?'<failure/>':error?'<error/>':skipped?'<skipped/>':''}</testcase></testsuite>`;
}
function minimalOutput(suite=LANDING_SUITE,phase='independent'){
 const owner=suite.name+'.'+suite.methods[0]+':00000000-0000-4000-8000-000000000072';
 return `PLATFORM_REPORT_INVOCATION run=${invocation} phase=${phase}\nPLATFORM_FIXTURE_START owner=${owner} activeUsers=0 pendingInvitations=0\nPLATFORM_FIXTURE_SCOPE owner=${owner} userIds=[] invitationIds=[] tenantIds=[] status=RETIRED_AUDIT_RETAINED\n`;
}
function artifacts({suite=LANDING_SUITE,phase='independent',xml=minimalXml(suite),stdout=minimalOutput(suite,phase),defaultReport=false}={}){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'platform-report-contract-'));
 const ctx=context(root),paths=reportPaths(ctx,phase,suite);
 fs.mkdirSync(paths.directory,{recursive:true});
 if(xml!==null)fs.writeFileSync(paths.xml,xml,{flag:'wx'});
 if(stdout!==null)fs.writeFileSync(paths.stdout,stdout,{flag:'wx'});
 if(defaultReport){
  fs.mkdirSync(paths.defaultDirectory,{recursive:true});
  fs.writeFileSync(path.join(paths.defaultDirectory,'TEST-'+suite.name+'.xml'),minimalXml(suite),{flag:'wx'});
  fs.writeFileSync(path.join(paths.defaultDirectory,suite.name+'-output.txt'),minimalOutput(suite,phase),{flag:'wx'});
 }
 return {root,context:ctx,paths,suite,phase};
}
function inspect(fixture,process=okay){
 return inspectSuiteArtifacts(fixture.paths,fixture.suite,process,{mode:fixture.suite.name===LANDING_SUITE.name?'landing':'database',invocation,phase:fixture.phase});
}

// No executable is invoked: the injected process adapter creates only minimal temporary report fixtures.
function controlledExecutor({suite=LANDING_SUITE,xml=minimalXml(suite),exitCode=0,throwTestProcess=false}={}){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'platform-report-execution-io-'));
 const ctx=context(root),phase='independent',paths=reportPaths(ctx,phase,suite),calls=[];
 fs.mkdirSync(ctx.source,{recursive:true});fs.mkdirSync(ctx.runDirectory,{recursive:true});
 fs.writeFileSync(ctx.pom,'synthetic dedicated POM fingerprint fixture',{flag:'wx'});
 ctx.pomSha256=crypto.createHash('sha256').update(fs.readFileSync(ctx.pom)).digest('hex');
 const completed={...okay,exitCode,output:'synthetic process output',elapsedMs:1};
 const fixture={context:ctx,phase,suite,paths,calls,completed,testAttempted:false};
 fixture.options={context:ctx,phase,suite,mode:suite.name===LANDING_SUITE.name?'landing':'database',
  ...(suite.name===LANDING_SUITE.name?{}:{database,appPassword:'synthetic-unrecorded-fixture'})};
 fixture.dependencies={
  runProcess:async(executable,args)=>{
   calls.push({executable,args});
   if(args.at(-1).endsWith(':effective-pom')){
    const output=args.find(a=>a.startsWith('-Doutput=')).slice('-Doutput='.length);
    fs.writeFileSync(output,'synthetic effective-POM input for injected inspector',{flag:'wx'});
    return {...okay,output:'synthetic effective process',elapsedMs:1};
   }
   assert.equal(args.at(-1),'org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test');
   fixture.testAttempted=true;
   if(throwTestProcess)throw Error('controlled executor result unavailable');
   const run=args.find(a=>a.startsWith('-Dp3.report.invocation=')).slice('-Dp3.report.invocation='.length);
   if(xml!==null)fs.writeFileSync(paths.xml,xml,{flag:'wx'});
   fs.writeFileSync(paths.stdout,minimalOutput(suite,phase).replaceAll(invocation,run),{flag:'wx'});
   return completed;
  },
  inspectEffectivePom:()=>({surefireVersion:'3.1.2',redirectTestOutputToFile:true,reportsDirectory:paths.directory,
   classesDirectory:ctx.build+'/classes',testClassesDirectory:ctx.build+'/test-classes',
   sourceDirectory:ctx.source+'/src/main/java',testSourceDirectory:ctx.source+'/src/test/java'})
 };
 return fixture;
}
function failFinalInventory(fixture){
 fixture.dependencies.inventory=directory=>{
  if(fixture.testAttempted&&directory===fixture.paths.directory)throw Object.assign(Error('controlled inventory failure'),{code:'EACCES'});
  return inventory(directory);
 };
}

test('landing and database share the same Maven, dedicated POM, Surefire goal and report construction',()=>{
 const ctx=context(),landing=suiteInvocation(ctx,'independent',LANDING_SUITE,{mode:'landing',invocation});
 const db=suiteInvocation(ctx,'independent',target,{mode:'database',database,invocation});
 const normalized=args=>args.filter(a=>!a.startsWith('-Dtest=')&&!/^-(?:D)p3\.(?:r2\.(?:database-authorized|cluster-id)|test\.jdbcUrl|report\.landing-authorized)=/.test(a));
 assert.deepEqual(normalized(landing.args),normalized(db.args));
 assert.deepEqual(landing.args.slice(0,mavenBaseArgs(ctx).length),mavenBaseArgs(ctx));
 assert.deepEqual(db.args.slice(0,mavenBaseArgs(ctx).length),mavenBaseArgs(ctx));
 assert.equal(landing.args.at(-1),'org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test');
 assert.equal(landing.args[landing.args.indexOf('-f')+1],ctx.pom);
 assert.equal(landing.paths.directory,db.paths.directory);
 assert.ok(landing.properties.includes('-Dp3.r2.migration-authorized=false'));
 assert.ok(db.properties.includes('-Dp3.r2.migration-authorized=false'));
 assert.ok(landing.properties.includes('-Dp3.r2.database-authorized=false'));
 assert.ok(db.properties.includes('-Dp3.r2.database-authorized=true'));
 assert.equal(landing.properties.some(p=>p.startsWith('-Dp3.test.jdbcUrl=')||p.startsWith('-Dp3.r2.cluster-id=')),false);
 assert.ok(landing.properties.includes('-Dp3.report.landing-authorized=true'));
 assert.ok(db.properties.includes('-Dp3.report.landing-authorized=false'));
});
test('all three phases use distinct explicit XML/stdout directories with one unchanged default directory',()=>{
 const ctx=context(),paths=['independent','sequential','full'].map(phase=>reportPaths(ctx,phase,LANDING_SUITE));
 assert.equal(new Set(paths.map(p=>p.directory)).size,3);
 assert.equal(new Set(paths.map(p=>p.defaultDirectory)).size,1);
 for(const [i,p] of paths.entries()){
  assert.equal(path.dirname(p.xml).replaceAll('\\','/'),p.directory);
  assert.equal(path.dirname(p.stdout).replaceAll('\\','/'),p.directory);
  assert.equal(p.directory,ctx.reportRoot+'/'+['independent','sequential','full'][i]);
  assert.notEqual(p.directory,p.defaultDirectory);
 }
});
for(const phase of ['missing','../independent','Independent'])test('reject phase '+phase,()=>{
 assert.throws(()=>suiteInvocation(context(),phase,LANDING_SUITE,{invocation}));
});
for(const selector of ['other.Suite',LANDING_SUITE.name+'Extra',LANDING_SUITE.name+',other.Suite',LANDING_SUITE.name+' -DskipTests'])test('reject non-bound selector '+selector,()=>{
 assert.throws(()=>suiteInvocation(context(),'independent',{...LANDING_SUITE,selector},{mode:'database',database,invocation}));
 // Repeat against the non-probe suite to verify selector checks, not just the landing mode restriction.
 const wrong=selector.replaceAll(LANDING_SUITE.name,target.name);
 assert.throws(()=>suiteInvocation(context(),'independent',{...target,selector:wrong},{mode:'database',database,invocation}));
});
for(const mode of ['smoke','',null])test('reject unsupported execution mode '+mode,()=>{
 assert.throws(()=>suiteInvocation(context(),'independent',LANDING_SUITE,{mode,invocation}));
});
test('landing rejects a database binding and a business selector',()=>{
 assert.throws(()=>suiteInvocation(context(),'independent',LANDING_SUITE,{database,invocation}));
 assert.throws(()=>suiteInvocation(context(),'independent',target,{invocation}));
});
test('database rejects the probe and missing explicit binding',()=>{
 assert.throws(()=>suiteInvocation(context(),'independent',LANDING_SUITE,{mode:'database',database,invocation}));
 assert.throws(()=>suiteInvocation(context(),'independent',target,{mode:'database',invocation}));
});
for(const url of ['jdbc:postgresql://localhost:55449/p3_m1_platform_release_r7','jdbc:postgresql://127.0.0.1:5432/wms_db_test','jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_release_r7?user=postgres','jdbc:postgresql://127.0.0.1:55449/p3_m1_platform_upgrade_r7'])test('reject unsafe/non-main URL '+url,()=>{
 assert.throws(()=>suiteInvocation(context(),'independent',target,{mode:'database',database:{...database,url},invocation}));
});
test('reject malformed invocation and cluster identifier without executing anything',()=>{
 assert.throws(()=>suiteInvocation(context(),'independent',LANDING_SUITE,{invocation:'not-a-uuid'}));
 assert.throws(()=>suiteInvocation(context(),'independent',target,{mode:'database',database:{...database,clusterId:'1'},invocation}));
});
for(const invalid of ['public','outside','relative'])test('reject '+invalid+' dedicated POM binding',()=>{
 const ctx=context();ctx.pom=invalid==='public'?ctx.source+'/pom.xml':invalid==='outside'?ctx.runDirectory+'/platform-report-pom.xml':'platform-report-pom.xml';
 assert.throws(()=>mavenBaseArgs(ctx));
});
test('effective configuration requires the frozen build paths, Surefire and redirected stdout',()=>{
 const ctx=context(),call=suiteInvocation(ctx,'independent',LANDING_SUITE,{invocation});
 const config={surefireVersion:'3.1.2',redirectTestOutputToFile:true,reportsDirectory:call.paths.directory,classesDirectory:ctx.build+'/classes',testClassesDirectory:ctx.build+'/test-classes',sourceDirectory:ctx.source+'/src/main/java',testSourceDirectory:ctx.source+'/src/test/java'};
 assert.equal(validateEffectiveConfig(config,ctx,call),config);
 for(const update of [{surefireVersion:'3.2.5'},{redirectTestOutputToFile:false},{reportsDirectory:call.paths.defaultDirectory},{testClassesDirectory:ctx.source+'/target/test-classes'}])assert.throws(()=>validateEffectiveConfig({...config,...update},ctx,call));
});

test('actual secure parser and fixture reader consume only the selected invocation evidence',()=>{
 const f=artifacts(),e=inspect(f);
 assert.equal(e.assessment.status,'PASSED');assert.equal(e.reportPath.status,'PASSED');
 assert.equal(e.reportParse.status,'PASSED');assert.equal(e.fixtureLog.status,'PASSED');
 assert.deepEqual(e.diagnostics.scopes[0].userIds,[]);assert.deepEqual(e.issues,[]);assert.equal(e.firstFailure,null);
 assert.match(e.outputSha256,/^[a-f0-9]{64}$/);
});
test('missing selected XML never falls back to a valid default report',()=>{
 const f=artifacts({xml:null,defaultReport:true}),e=inspect(f);
 assert.equal(e.firstFailure.category,'REPORT_PATH_ERROR');assert.equal(e.firstFailure.code,'EXPECTED_XML_MISSING');
 assert.equal(e.reportParse.status,'NOT_EXECUTED');assert.equal(e.assessment.counts.unverified,1);assert.equal(e.assessment.counts.passed,0);
});
test('missing selected stdout never falls back to valid default fixture output',()=>{
 const f=artifacts({stdout:null,defaultReport:true}),e=inspect(f);
 assert.equal(e.firstFailure.category,'REPORT_PATH_ERROR');assert.equal(e.firstFailure.code,'EXPECTED_STDOUT_MISSING');
 assert.equal(e.fixtureLog.status,'NOT_EXECUTED');assert.equal(e.diagnostics,null);
 assert.equal(e.assessment.counts.passed,1); // Business XML evidence is retained, not a complete phase pass.
});
test('both missing files retain XML path failure as the first cause',()=>{
 const e=inspect(artifacts({xml:null,stdout:null,defaultReport:true}));
 assert.deepEqual(e.issues.slice(0,2),[{category:'REPORT_PATH_ERROR',code:'EXPECTED_XML_MISSING'},{category:'REPORT_PATH_ERROR',code:'EXPECTED_STDOUT_MISSING'}]);
 assert.equal(e.firstFailure.code,'EXPECTED_XML_MISSING');
});
for(const [label,xml] of [['damaged XML','<testsuite'],['DTD XML','<!DOCTYPE testsuite [<!ENTITY x "blocked">]>'+minimalXml()]])test('actual parser rejects '+label,()=>{
 const e=inspect(artifacts({xml}));assert.equal(e.firstFailure.category,'REPORT_PARSE_ERROR');assert.equal(e.reportParse.status,'FAILED');
 assert.equal(e.assessment.counts.unverified,1);assert.equal(e.fixtureLog.status,'PASSED');
});
for(const [label,xml] of [['wrong method',minimalXml(LANDING_SUITE,{method:'unexpectedMethod'})],['wrong header count',minimalXml(LANDING_SUITE,{tests:2})]])test('actual expected method/header validation rejects '+label,()=>{
 const e=inspect(artifacts({xml}));assert.equal(e.firstFailure.category,'REPORT_PARSE_ERROR');
 assert.equal(e.firstFailure.code,'REPORT_METHOD_OR_HEADER_MISMATCH');assert.equal(e.assessment.reportStatus,'VALIDATION_FAILED');
});
for(const [label,change] of [['missing close',s=>s.split('\n').filter(line=>!line.startsWith('PLATFORM_FIXTURE_SCOPE ')).join('\n')],['malformed scope',s=>s.replace('userIds=[]','userIds=[0]')],['wrong owner',s=>s.replaceAll(LANDING_SUITE.name,'other.Suite')],['wrong marker',s=>s.replace('phase=independent','phase=full')],['duplicate marker',s=>s+'PLATFORM_REPORT_INVOCATION run='+invocation+' phase=independent\n']])test('fixture log/invocation validation rejects '+label,()=>{
 const e=inspect(artifacts({stdout:change(minimalOutput())}));
 assert.equal(e.firstFailure.category,'FIXTURE_LOG_ERROR');assert.equal(e.fixtureLog.status,'FAILED');assert.equal(e.assessment.counts.passed,1);
});
test('Maven nonzero is not converted to a pass by valid XML and stdout',()=>{
 const e=inspect(artifacts(),{...okay,exitCode:1});
 assert.equal(e.firstFailure.category,'EXECUTION_ERROR');assert.equal(e.assessment.status,'EXECUTION_FAILED');assert.equal(e.assessment.counts.passed,1);
});
for(const [label,options,status] of [['failure',{failure:true},'BUSINESS_TEST_FAILED'],['error',{error:true},'BUSINESS_TEST_FAILED'],['skipped',{skipped:true},'UNEXPECTED_SKIP']])test('retain explicit '+label+' outcome rather than zero-exit pass',()=>{
 const e=inspect(artifacts({xml:minimalXml(LANDING_SUITE,options)}));assert.equal(e.assessment.status,status);assert.equal(e.firstFailure.category,status);
});

test('same source content is inventoried and preserved exactly',()=>{
 const f=artifacts(),before=inventory(f.paths.directory);requirePreserved(f.paths.directory,before);
 assert.equal(before.length,2);for(const file of before){assert.match(file.sha256,/^[a-f0-9]{64}$/);assert.ok(file.bytes>0);}
});
test('same-length report alteration is rejected by hashes, not just file size',()=>{
 const f=artifacts(),before=inventory(f.paths.directory);
 fs.writeFileSync(f.paths.xml,minimalXml().replace('failures="0"','failures="1"'));
 assert.throws(()=>requirePreserved(f.paths.directory,before),/inventory or content changed/);
});
test('unexpected additional report is rejected by inventory preservation',()=>{
 const f=artifacts(),before=inventory(f.paths.directory);fs.writeFileSync(path.join(f.paths.directory,'unexpected.txt'),'synthetic',{flag:'wx'});
 assert.throws(()=>requirePreserved(f.paths.directory,before));
});
test('creating later phase evidence does not alter prior phase files or default directory',()=>{
 const f=artifacts(),before=inventory(f.paths.directory),defaultBefore=inventory(f.paths.defaultDirectory);
 for(const phase of ['sequential','full']){
  const paths=reportPaths(f.context,phase,LANDING_SUITE);fs.mkdirSync(paths.directory,{recursive:true});
  fs.writeFileSync(paths.xml,minimalXml(),{flag:'wx'});fs.writeFileSync(paths.stdout,minimalOutput(LANDING_SUITE,phase),{flag:'wx'});
  const evidence=inspectSuiteArtifacts(paths,LANDING_SUITE,okay,{mode:'landing',phase,invocation});assert.equal(evidence.firstFailure,null);
  requirePreserved(f.paths.directory,before);requirePreserved(f.paths.defaultDirectory,defaultBefore);assert.equal(fs.existsSync(f.paths.defaultDirectory),false);
 }
});

for(const [label,xml,expected] of [['path',null,'REPORT_PATH_ERROR'],['parse','<broken','REPORT_PARSE_ERROR'],['business',minimalXml(target,{failure:true}),'BUSINESS_TEST_FAILED']])test('actual r6 entry preserves first '+label+' failure when fixture teardown also fails',async()=>{
 const f=artifacts({suite:target,xml}),process=label==='business'?{...okay,exitCode:1}:okay;
 const evidence=inspect(f,process),calls=[];
 const result=await runR6Acceptance({input,execute:async(p,s)=>{calls.push([p.id,s.name]);return {process,xmlFile:f.paths.xml,evidence};},afterSuite:async()=>{throw Object.assign(Error('synthetic fixture reconciliation failure'),{category:'FIXTURE_CLEANUP_ERROR'});}});
 assert.equal(result.status,'FAILED');assert.equal(calls.length,1);assert.equal(calls[0][0],'independent');
 assert.equal(result.firstFailure.category,expected);assert.equal(result.phases[0].firstFailure.category,expected);
 assert.ok(result.phases[0].failures.some(f=>f.category==='FIXTURE_CLEANUP_ERROR'));
 assert.equal(result.phases[0].fixtureStatus,'FAILED');assert.equal(result.fullRegression.coverage.unexecuted,63);
 assert.equal(result.phases[1].coverage.unexecuted,42);assert.equal(result.executionInstances.plannedExecutionInstances,106);
 assert.equal(result.executionInstances.distinctBusinessMethods,63);
});
test('actual r6 entry retains valid target pass while marking incomplete fixture teardown separately',async()=>{
 const f=artifacts({suite:target}),evidence=inspect(f);
 const result=await runR6Acceptance({input,execute:async()=>({process:okay,xmlFile:f.paths.xml,evidence}),afterSuite:async()=>{throw Object.assign(Error('synthetic fixture reconciliation failure'),{category:'FIXTURE_CLEANUP_ERROR'});}});
 assert.equal(result.firstFailure.category,'FIXTURE_CLEANUP_ERROR');assert.equal(result.executionInstances.passed,1);
 assert.equal(result.fullRegression.coverage.passed,0);assert.equal(result.fullRegression.coverage.unexecuted,63);
});

test('actual dedicated POM generator preserves root and changes only the three permitted fields',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'platform-report-pom-contract-'));
 const source=fs.readFileSync(new URL('../../../pom.xml',import.meta.url));
 fs.writeFileSync(root+'/pom.xml',source,{flag:'wx'});
 const digest=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),before=digest(root+'/pom.xml');
 const generated=createReportPom(root,root+'/platform-acceptance-pom.xml');
 assert.equal(generated.ok,true);assert.equal(generated.onlyThreeAuthorizedFieldsChanged,true);assert.equal(generated.publicPomUnchanged,true);
 assert.equal(generated.sha256,digest(root+'/platform-acceptance-pom.xml'));assert.equal(digest(root+'/pom.xml'),before);
 assert.throws(()=>createReportPom(root,root+'/platform-acceptance-pom.xml'));assert.equal(digest(root+'/pom.xml'),before);
});
test('actual dedicated POM generator rejects DTD without writing an adapted POM',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'platform-report-pom-dtd-'));
 const source=fs.readFileSync(new URL('../../../pom.xml',import.meta.url),'utf8').replace(/<\?xml[^>]+\?>/,'<!DOCTYPE project [<!ENTITY blocked "fixture">]>');
 fs.writeFileSync(root+'/pom.xml',source,{flag:'wx'});assert.throws(()=>createReportPom(root,root+'/platform-acceptance-pom.xml'));
 assert.equal(fs.existsSync(root+'/platform-acceptance-pom.xml'),false);
});
test('actual shared executor refuses existing reports as path error before any Maven process',async()=>{
 const f=artifacts();fs.mkdirSync(f.context.source,{recursive:true});fs.writeFileSync(f.context.pom,'synthetic-unused-pom',{flag:'wx'});
 f.context.pomSha256=crypto.createHash('sha256').update(fs.readFileSync(f.context.pom)).digest('hex');
 const result=await executePlatformMavenSuite({context:f.context,phase:f.phase,suite:LANDING_SUITE,mode:'landing'});
 assert.equal(result.process,null);assert.equal(result.evidence.firstFailure.category,'REPORT_PATH_ERROR');
 assert.equal(result.evidence.firstFailure.code,'PRE_EXISTING_REPORTS_REFUSED');assert.equal(result.evidence.businessProcessStarted,false);
 assert.equal(result.evidence.assessment.counts.unexecuted,1);assert.deepEqual(result.evidence.commands,[]);
});

test('completed test process and attempted command survive command-log I/O failure as unverified',async()=>{
 const f=controlledExecutor();
 f.dependencies.writeFileSync=(file,...args)=>{if(file.endsWith('-test.log'))throw Object.assign(Error('controlled write failure'),{code:'EACCES'});return fs.writeFileSync(file,...args);};
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(f.calls.length,2);assert.equal(result.process,f.completed);
 assert.equal(result.reportError,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(result.evidence.businessProcessStarted,true);assert.equal(result.evidence.invocationAttempted,true);
 assert.equal(result.evidence.firstFailure.category,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(result.evidence.evidenceCollection.stage,'TEST_COMMAND_LOG');
 assert.equal(result.evidence.commands.length,2);assert.equal(result.evidence.commands[1].process.exitCode,0);
 assert.equal(result.evidence.commands[1].logStatus,'FAILED');assert.equal(result.evidence.commands[1].logSha256,null);
 assert.equal(result.evidence.assessment.counts.unverified,1);assert.equal(result.evidence.assessment.counts.unexecuted,0);
 const summary=await runSuitePlan({plan:[LANDING_SUITE],execute:async()=>result});
 assert.equal(summary.coverage.unverified,1);assert.equal(summary.coverage.unexecuted,0);assert.equal(summary.coverage.passed,0);
});

test('known nonzero test process remains the first failure when writing its evidence log also fails',async()=>{
 const f=controlledExecutor({exitCode:1});
 f.dependencies.writeFileSync=(file,...args)=>{if(file.endsWith('-test.log'))throw Error('controlled write failure');return fs.writeFileSync(file,...args);};
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(result.process.exitCode,1);assert.equal(result.evidence.businessProcessStarted,true);
 assert.equal(result.evidence.firstFailure.category,'EXECUTION_ERROR');
 assert.equal(result.evidence.issues.at(-1).category,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(result.evidence.assessment.counts.unverified,1);assert.equal(result.evidence.assessment.counts.unexecuted,0);
});

test('late inventory I/O preserves a previously verified pass but blocks the actual r6 phase',async()=>{
 const f=controlledExecutor({suite:target});failFinalInventory(f);
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(result.process,f.completed);assert.equal(result.xmlFile,f.paths.xml);assert.equal(result.reportError,undefined);
 assert.equal(result.evidence.firstFailure.category,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(result.evidence.assessment.counts.passed,1);assert.equal(result.evidence.assessment.counts.unverified,0);
 assert.equal(result.evidence.observedAssessmentBeforeEvidenceFailure.counts.passed,1);
 const phases=await runR6Acceptance({input,execute:async()=>result});
 assert.equal(phases.status,'FAILED');assert.equal(phases.firstFailure.category,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(phases.executionInstances.passed,1);assert.equal(phases.phases[1].coverage.unexecuted,42);
 assert.equal(phases.fullRegression.coverage.unexecuted,63);
});

test('late evidence I/O cannot erase an already verified business failure or replace its first cause',async()=>{
 const f=controlledExecutor({suite:target,xml:minimalXml(target,{failure:true}),exitCode:1});failFinalInventory(f);
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(result.evidence.firstFailure.category,'BUSINESS_TEST_FAILED');
 assert.equal(result.evidence.issues.at(-1).category,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(result.evidence.assessment.counts.failures,1);assert.equal(result.evidence.assessment.counts.unexecuted,0);
 const phases=await runR6Acceptance({input,execute:async()=>result,afterSuite:async()=>{throw Object.assign(Error('controlled teardown failure'),{category:'FIXTURE_CLEANUP_ERROR'});}});
 assert.equal(phases.firstFailure.category,'BUSINESS_TEST_FAILED');assert.equal(phases.executionInstances.failures,1);
 assert.equal(phases.phases[0].failures.at(-1).category,'FIXTURE_CLEANUP_ERROR');
 assert.equal(phases.fullRegression.coverage.unexecuted,63);
});

for(const [label,xml,first] of [['path',null,'REPORT_PATH_ERROR'],['parse','<broken','REPORT_PARSE_ERROR']])test('late inventory I/O retains earlier '+label+' error and unverified execution',async()=>{
 const f=controlledExecutor({xml});failFinalInventory(f);
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(result.evidence.firstFailure.category,first);assert.equal(result.evidence.issues.at(-1).category,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(result.evidence.businessProcessStarted,true);assert.equal(result.evidence.assessment.counts.unverified,1);
 const summary=await runSuitePlan({plan:[LANDING_SUITE],execute:async()=>result});
 assert.equal(summary.coverage.unverified,1);assert.equal(summary.coverage.unexecuted,0);
});

test('completed protected-path mismatch remains first when reading a later protected inventory fails',async()=>{
 const f=controlledExecutor();
 f.dependencies.inventory=directory=>{
  if(f.testAttempted&&directory===f.paths.defaultDirectory)return [{path:'synthetic-changed.xml',sha256:'0'.repeat(64),bytes:1}];
  if(f.testAttempted&&directory===f.context.reportRoot+'/sequential')throw Error('controlled protected inventory failure');
  return inventory(directory);
 };
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(result.evidence.firstFailure.category,'REPORT_PATH_ERROR');
 assert.equal(result.evidence.firstFailure.code,'DEFAULT_REPORT_DIRECTORY_CHANGED');
 assert.equal(result.evidence.issues.at(-1).category,'REPORT_EVIDENCE_IO_ERROR');
 assert.equal(result.evidence.protectedDirectories.length,1);assert.equal(result.evidence.protectedDirectories[0].status,'FAILED');
 assert.equal(result.evidence.assessment.counts.passed,1);
});

test('executor throwing after invocation attempt records unknown start and unverified result instead of post-execution I/O',async()=>{
 const f=controlledExecutor({throwTestProcess:true});
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(f.calls.length,2);assert.notEqual(result.process,null);
 assert.equal(result.evidence.invocationAttempted,true);assert.equal(result.evidence.businessProcessStarted,'UNKNOWN');
 assert.equal(result.reportError,'TEST_EXECUTION_RESULT_UNAVAILABLE');
 assert.equal(result.evidence.firstFailure.category,'EXECUTION_ERROR');
 assert.equal(result.evidence.firstFailure.code,'TEST_EXECUTION_RESULT_UNAVAILABLE');
 assert.equal(result.evidence.commands.length,2);assert.equal(result.evidence.commands[1].invocationAttempted,true);
 assert.equal(result.evidence.commands[1].process,null);assert.equal(result.evidence.commands[1].logStatus,'NOT_ATTEMPTED');
 assert.equal(result.evidence.issues.some(i=>i.category==='REPORT_EVIDENCE_IO_ERROR'),false);
 const summary=await runSuitePlan({plan:[LANDING_SUITE],execute:async()=>result});
 assert.equal(summary.coverage.unverified,1);assert.equal(summary.coverage.unexecuted,0);
});

test('effective-process exception retains the attempted configuration command without claiming a test launch',async()=>{
 const f=controlledExecutor();f.dependencies.runProcess=async()=>{throw Error('controlled effective process failure');};
 const result=await executePlatformMavenSuite(f.options,f.dependencies);
 assert.equal(result.process,null);assert.equal(result.evidence.invocationAttempted,false);assert.equal(result.evidence.businessProcessStarted,false);
 assert.equal(result.evidence.commands.length,1);assert.equal(result.evidence.commands[0].label,'effective');
 assert.equal(result.evidence.commands[0].invocationAttempted,true);assert.equal(result.evidence.commands[0].process,null);
 assert.equal(result.evidence.assessment.counts.unexecuted,1);
});

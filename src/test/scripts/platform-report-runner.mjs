// Shared real Maven/Surefire invocation and phase evidence reader. No PostgreSQL launcher here.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {runProcess} from './platform-process-lifecycle.mjs';
import {readJUnitReport,assessSuite,executionEvidence} from './platform-test-results.mjs';
import {fixtureDiagnostics,requireCompleteScopes} from './platform-r6-stages.mjs';
const PS='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const POM_TOOL=fileURLToPath(new URL('./platform-report-pom.ps1',import.meta.url));
export const LANDING_SUITE=Object.freeze({name:'com.wms.platformverification.PlatformReportLandingTest',selector:'com.wms.platformverification.PlatformReportLandingTest',methods:['writesPhaseBoundFixtureEvidenceWithoutDatabase']});
const digest=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const normal=p=>path.resolve(p).replaceAll('\\','/');
function safePath(p){if(typeof p!=='string'||!path.isAbsolute(p)||p.includes('\0')||p.includes('..'))throw Error('Invalid absolute verification path');return normal(p);}
export function reportPaths(context,phase,suite){
 if(!['independent','sequential','full'].includes(phase)||!/^[A-Za-z0-9_.$]+$/.test(suite.name))throw Error('Invalid phase or suite');
 const root=safePath(context.reportRoot),directory=root+'/'+phase;
 return {directory,xml:directory+'/TEST-'+suite.name+'.xml',stdout:directory+'/'+suite.name+'-output.txt',defaultDirectory:safePath(context.build)+'/surefire-reports'};
}
export function inventory(directory){
 if(!fs.existsSync(directory))return [];
 const collect=(dir,relative='')=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{
  if(e.isSymbolicLink())throw Error('No links in report evidence');const p=dir+'/'+e.name,r=relative+e.name;
  return e.isDirectory()?collect(p,r+'/'):[{path:r,sha256:digest(p),bytes:fs.statSync(p).size}];});
 return collect(directory).sort((a,b)=>a.path.localeCompare(b.path));
}
export function requirePreserved(directory,before){if(JSON.stringify(inventory(directory))!==JSON.stringify(before))throw Error('Report inventory or content changed');}
export function createReportPom(source,destination){
 return JSON.parse(execFileSync(PS,['-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',POM_TOOL],
  {input:JSON.stringify({mode:'create',rootPom:source+'/pom.xml',destination}),encoding:'utf8',windowsHide:true,timeout:15000,stdio:['pipe','pipe','pipe']}));
}
export function inspectEffectivePom(file){
 return JSON.parse(execFileSync(PS,['-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',POM_TOOL],
  {input:JSON.stringify({mode:'inspect',effectivePom:file}),encoding:'utf8',windowsHide:true,timeout:15000,stdio:['pipe','pipe','pipe']}));
}
export function mavenBaseArgs(context,{dedicated=true}={}){
 for(const key of ['source','build','maven','repo','java','pom','runDirectory'])safePath(context[key]);
 if(normal(path.dirname(context.pom))!==normal(context.source)||normal(context.pom)===normal(context.source+'/pom.xml'))throw Error('Dedicated POM must share frozen source basedir');
 return ['-Dmaven.home='+context.maven,'-Dclassworlds.conf='+context.maven+'/bin/m2.conf','-Dmaven.multiModuleProjectDirectory='+context.source,
  '-cp',context.maven+'/boot/*','org.codehaus.plexus.classworlds.launcher.Launcher','-o','-B','-ntp',
  '-s',context.source+'/src/test/resources/platform-r2-maven-settings.xml','-gs',context.source+'/src/test/resources/platform-r2-maven-settings.xml',
  '-Dmaven.repo.local='+context.repo,'-Dwms.build.directory='+context.build,'-f',dedicated?context.pom:context.source+'/pom.xml'];
}
export function suiteInvocation(context,phase,suite,{mode='landing',database,invocation=crypto.randomUUID()}={}){
 const paths=reportPaths(context,phase,suite);
 const selectorPattern=suite.selector?.startsWith(suite.name+'#')?suite.selector.slice(suite.name.length+1):null;
 if(!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(invocation)
  ||(suite.selector!==suite.name&&(!selectorPattern||!/^[A-Za-z0-9_*]+$/.test(selectorPattern))))throw Error('Invalid invocation or selector');
 if(selectorPattern&&suite.methods.some(name=>!new RegExp('^'+selectorPattern.replaceAll('*','.*')+'$').test(name)))throw Error('Selector does not match expected methods');
 const properties=['-Dtest='+suite.selector,'-Dplatform.verification.reportsDirectory='+paths.directory,
  '-Dp3.r2.migration-authorized=false','-Dp3.report.phase='+phase,'-Dp3.report.invocation='+invocation,
  '-DforkCount=1','-DreuseForks=false','-Dsurefire.timeout=120','-Dsurefire.exitTimeout=20'];
 if(context.exportFixtureTempRoot!==undefined){
  const root=safePath(context.exportFixtureTempRoot);
  if(root!==safePath(context.runDirectory)+'/export-fixture-temp')throw Error('Export fixture root must belong to the exact batch');
  properties.push('-Dp3.export.fixture-temp-root='+root);
 }
 if(mode==='landing'){
  if(database||JSON.stringify(suite)!==JSON.stringify(LANDING_SUITE))throw Error('Landing mode permits only the in-memory probe');
  properties.push('-Dp3.r2.database-authorized=false','-Dp3.report.landing-authorized=true');
 }else if(mode==='database'){
  if(!database||!/^jdbc:postgresql:\/\/127\.0\.0\.1:55449\/p3_m1_platform_release_r\d+$/.test(database.url)||!/^\d{19}$/.test(database.clusterId)||suite.name===LANDING_SUITE.name)throw Error('Explicit isolated database binding required');
  properties.push('-Dp3.r2.database-authorized=true','-Dp3.report.landing-authorized=false','-Dp3.test.jdbcUrl='+database.url,'-Dp3.r2.cluster-id='+database.clusterId);
 }else throw Error('Unsupported suite mode');
 return {phase,mode,invocation,paths,properties,args:[...mavenBaseArgs(context),...properties,'org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test']};
}
export function validateEffectiveConfig(config,context,invocation){
 const expected={reportsDirectory:invocation.paths.directory,classesDirectory:normal(context.build)+'/classes',testClassesDirectory:normal(context.build)+'/test-classes',sourceDirectory:normal(context.source)+'/src/main/java',testSourceDirectory:normal(context.source)+'/src/test/java'};
 if(config.surefireVersion!=='3.1.2'||String(config.redirectTestOutputToFile)!=='true')throw Error('Effective Surefire version or stdout binding mismatch');
 for(const [key,value] of Object.entries(expected))if(normal(config[key]??'')!==normal(value))throw Error('Effective path mismatch: '+key);
 return config;
}
export function inspectSuiteArtifacts(paths,suite,process,{mode,invocation,phase}={}){
 const issues=[],fail=(category,code)=>issues.push({category,code});
 const reportPath={status:'PASSED',xmlPresent:fs.existsSync(paths.xml),stdoutPresent:fs.existsSync(paths.stdout)};
 if(!reportPath.xmlPresent)fail('REPORT_PATH_ERROR','EXPECTED_XML_MISSING');
 if(!reportPath.stdoutPresent)fail('REPORT_PATH_ERROR','EXPECTED_STDOUT_MISSING');
 if(issues.length)reportPath.status='FAILED';
 let report=null,reportError=null,diagnostics=null,outputSha256=null;
 const reportParse={status:'NOT_EXECUTED'},fixtureLog={status:'NOT_EXECUTED'};
 if(reportPath.xmlPresent){try{report=readJUnitReport(paths.xml);reportParse.status='PASSED';}catch(error){reportError=error.code??'REPORT_READ_FAILED';reportParse.status='FAILED';reportParse.code=reportError;fail('REPORT_PARSE_ERROR',reportError);}}
 if(reportPath.stdoutPresent){try{
  const output=fs.readFileSync(paths.stdout,'utf8');outputSha256=digest(paths.stdout);
  diagnostics=fixtureDiagnostics(output,suite);requireCompleteScopes(diagnostics,suite);
  if(mode==='landing'){
   const markers=output.split(/\r?\n/).filter(s=>s.startsWith('PLATFORM_REPORT_INVOCATION '));
   if(markers.length!==1||markers[0]!==`PLATFORM_REPORT_INVOCATION run=${invocation} phase=${phase}`)throw Error('Invocation marker mismatch');
  }
  fixtureLog.status='PASSED';
 }catch{fixtureLog.status='FAILED';fixtureLog.code='FIXTURE_LOG_INVALID';fail('FIXTURE_LOG_ERROR','FIXTURE_LOG_INVALID');}}
 const assessment=assessSuite(suite,{process,report,reportError:reportError??(!reportPath.xmlPresent?'REPORT_MISSING_OR_UNREADABLE':null)});
 if(assessment.reportStatus==='VALIDATION_FAILED')fail('REPORT_PARSE_ERROR','REPORT_METHOD_OR_HEADER_MISMATCH');
 if(assessment.status==='BUSINESS_TEST_FAILED'||assessment.status==='UNEXPECTED_SKIP')fail(assessment.status,assessment.status);
 if(assessment.executionStatus==='FAILED')fail('EXECUTION_ERROR','MAVEN_EXECUTION_FAILED');
 return {assessment,reportPath,reportParse,fixtureLog,diagnostics,outputSha256,issues,firstFailure:issues[0]??null};
}
export class SuiteEvidenceError extends Error {
 constructor(category,code){super(code);this.category=category;this.code=code;}
}
export async function executePlatformMavenSuite(options,testDependencies={}){
 // Defaults are the production test-tool call chain. Injection is only for deterministic no-Maven I/O failure contracts.
 const dependencies={runProcess,writeFileSync:fs.writeFileSync,inspectEffectivePom,inventory,...testDependencies};
 const state={commands:[],process:null,invocationAttempted:false,businessProcessStarted:false,evidence:null,stage:'SUITE_PREPARATION'};
 try{
  if(Object.keys(testDependencies).some(key=>!['runProcess','writeFileSync','inspectEffectivePom','inventory'].includes(key))
   ||Object.values(dependencies).some(value=>typeof value!=='function'))throw Error('Invalid test dependency');
  return await executeGuardedSuite(options,state,dependencies);
 }catch(error){
  if(state.invocationAttempted){
   // A completed/attempted invocation cannot be relabelled "not executed" because subsequent evidence I/O failed.
   const process=state.process??{exitCode:null,exited:false,outputComplete:false,terminationConfirmed:false};
   const prior=state.evidence,resultUnavailable=state.process===null;
   const issue=resultUnavailable?{category:'EXECUTION_ERROR',code:'TEST_EXECUTION_RESULT_UNAVAILABLE'}
    :{category:'REPORT_EVIDENCE_IO_ERROR',code:'POST_EXECUTION_EVIDENCE_IO_FAILED'};
   const reportError=resultUnavailable?'TEST_EXECUTION_RESULT_UNAVAILABLE':'REPORT_EVIDENCE_IO_ERROR';
   const knownExecution=state.process?executionEvidence(state.process):null;
   const knownExecutionFailed=knownExecution&&(knownExecution.exitCode!==0||!knownExecution.exited||knownExecution.timedOut||knownExecution.aborted||knownExecution.spawnFailed||knownExecution.signal||!knownExecution.outputComplete||!knownExecution.terminationConfirmed);
   const priorIssues=prior?.issues??(knownExecutionFailed?[{category:'EXECUTION_ERROR',code:'MAVEN_EXECUTION_FAILED'}]:[]);
   const issues=[...priorIssues,issue];
   const assessment=prior?.assessment??assessSuite(options.suite,{process,reportError});
   const reportPayload=prior?.assessment?.report?{xmlFile:state.call.paths.xml}
    :{reportError:prior?.assessment?.reportError??reportError};
   return {process,...reportPayload,logSha256:state.commands.findLast(c=>c.label==='test')?.logSha256??null,
    evidence:{...prior,phase:options.phase,mode:options.mode??'landing',commands:state.commands,
     paths:state.call?.paths,invocation:state.call?.invocation,invocationAttempted:true,businessProcessStarted:state.businessProcessStarted,
     reportPath:prior?.reportPath??{status:'UNVERIFIED'},reportParse:prior?.reportParse??{status:'UNVERIFIED'},
     fixtureLog:prior?.fixtureLog??{status:'UNVERIFIED'},protectedDirectories:prior?.protectedDirectories??[],
     observedAssessmentBeforeEvidenceFailure:prior?.assessment??null,assessment,
     evidenceCollection:{status:'FAILED',category:issue.category,code:issue.code,stage:state.stage},
     issues,firstFailure:prior?.firstFailure??issues[0]}};
  }
  const category=['REPORT_PATH_ERROR','REPORT_CONFIGURATION_ERROR'].includes(error.category)?error.category:'REPORT_CONFIGURATION_ERROR';
  const code=/^[A-Z_]+$/.test(error.code??'')?error.code:'SUITE_PREPARATION_FAILED';
  const firstFailure={category,code};
  return {process:null,evidence:{phase:options.phase,mode:options.mode??'landing',commands:state.commands,
   reportPath:{status:category==='REPORT_PATH_ERROR'?'FAILED':'NOT_EXECUTED'},reportParse:{status:'NOT_EXECUTED'},fixtureLog:{status:'NOT_EXECUTED'},
   assessment:assessSuite(options.suite),issues:[firstFailure],firstFailure,protectedDirectories:[],invocationAttempted:false,businessProcessStarted:false}};
 }
}
async function executeGuardedSuite({context,phase,suite,mode='landing',database,appPassword},state,dependencies){
 const call=suiteInvocation(context,phase,suite,{mode,database}),short=suite.name.split('.').at(-1),prefix=phase+'-'+short;
 state.call=call;
 if(digest(context.pom)!==context.pomSha256)throw Error('Dedicated POM fingerprint mismatch');
 if(fs.existsSync(call.paths.xml)||fs.existsSync(call.paths.stdout))throw new SuiteEvidenceError('REPORT_PATH_ERROR','PRE_EXISTING_REPORTS_REFUSED');
 const protectedDirs=[call.paths.defaultDirectory,...['independent','sequential','full'].filter(p=>p!==phase).map(p=>context.reportRoot+'/'+p)];
 const before=protectedDirs.map(directory=>({directory,existed:fs.existsSync(directory),files:dependencies.inventory(directory)}));
 fs.mkdirSync(call.paths.directory,{recursive:true});
 const env={...process.env,JAVA_HOME:path.dirname(path.dirname(context.java))};
 for(const key of Object.keys(env))if(/^(PG|P3_|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(key))delete env[key];
 const commands=state.commands;
 async function launch(args,label,{credential=false}={}){
  const executionEnv={...env};if(credential&&mode==='database'){if(!appPassword)throw Error('Missing isolated runtime credential');executionEnv.P3_R2_APP_PASSWORD=appPassword;}
  const log=context.runDirectory+'/'+prefix+'-'+label+'.log';
  const command={executable:context.java,args,cwd:context.source,process:null,label,log,logSha256:null,logStatus:'NOT_ATTEMPTED',invocationAttempted:true};
  commands.push(command);
  if(label==='test'){state.invocationAttempted=true;state.businessProcessStarted='UNKNOWN';state.stage='TEST_PROCESS_EXECUTION';}
  const result=await dependencies.runProcess(context.java,args,{cwd:context.source,env:executionEnv,timeoutMs:240000});
  command.process=executionEvidence(result);command.logStatus='PENDING';
  if(label==='test'){state.process=result;state.businessProcessStarted=result.spawnError||result.spawnFailed?false:result.exited||result.signal||result.exitCode!=null?true:'UNKNOWN';}
  let output=result.output.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[JWT REDACTED]');if(appPassword)output=output.replaceAll(appPassword,'[REDACTED]');
  state.stage=label==='test'?'TEST_COMMAND_LOG':'EFFECTIVE_CONFIGURATION_LOG';
  try{dependencies.writeFileSync(log,output,{flag:'wx'});command.logSha256=digest(log);command.logStatus='WRITTEN';}
  catch(error){command.logStatus='FAILED';throw error;}
  return result;
 }
 const effectivePom=context.runDirectory+'/'+prefix+'-effective-pom.xml';
 if(fs.existsSync(effectivePom))throw new SuiteEvidenceError('REPORT_PATH_ERROR','PRE_EXISTING_EFFECTIVE_POM_REFUSED');
 const effectiveArgs=[...mavenBaseArgs(context),...call.properties,'-Doutput='+effectivePom,'org.apache.maven.plugins:maven-help-plugin:3.4.1:effective-pom'];
 const effectiveProcess=await launch(effectiveArgs,'effective');
 let config;
 if(effectiveProcess.exitCode!==0||!effectiveProcess.exited||effectiveProcess.timedOut||!effectiveProcess.outputComplete||!effectiveProcess.terminationConfirmed){const error=new SuiteEvidenceError('REPORT_CONFIGURATION_ERROR','EFFECTIVE_POM_EXECUTION_FAILED');error.commands=commands;throw error;}
 try{config=validateEffectiveConfig(dependencies.inspectEffectivePom(effectivePom),context,call);}catch{const error=new SuiteEvidenceError('REPORT_CONFIGURATION_ERROR','EFFECTIVE_POM_BINDING_MISMATCH');error.commands=commands;throw error;}
 const testProcess=await launch(call.args,'test',{credential:true});
 state.stage='REPORT_INSPECTION';
 const evidence=inspectSuiteArtifacts(call.paths,suite,testProcess,call);
 state.evidence=evidence;
 evidence.protectedDirectories=[];state.stage='PROTECTED_REPORT_INSPECTION';
 for(const p of before){
  const after=dependencies.inventory(p.directory);
  const status=JSON.stringify(after)===JSON.stringify(p.files)&&fs.existsSync(p.directory)===p.existed?'PASSED':'FAILED';
  if(status==='FAILED')evidence.issues.push({category:'REPORT_PATH_ERROR',code:p.directory===call.paths.defaultDirectory?'DEFAULT_REPORT_DIRECTORY_CHANGED':'PRIOR_PHASE_REPORT_CHANGED'});
  evidence.protectedDirectories.push({...p,status});
 }
 evidence.firstFailure??=evidence.issues[0]??null;
 evidence.phase=phase;evidence.mode=mode;evidence.invocation=call.invocation;evidence.paths=call.paths;evidence.commands=commands;
 evidence.effectiveConfiguration={...config,file:effectivePom,sha256:digest(effectivePom),dedicatedPomSha256:context.pomSha256};
 evidence.invocationAttempted=true;evidence.businessProcessStarted=state.businessProcessStarted;state.stage='REPORT_INVENTORY';
 evidence.actualFiles=dependencies.inventory(call.paths.directory);
 evidence.evidenceCollection={status:'PASSED'};
 return {process:testProcess,xmlFile:call.paths.xml,logSha256:commands.at(-1).logSha256,evidence};
}

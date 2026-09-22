// Fixed no-database regression recorder. No build, migration, OS inventory or database mode.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {runProcess,failureDetails} from './platform-process-lifecycle.mjs';
import {NODE_GATES} from './platform-r10-plan.mjs';
const ROOT='D:/ERP_WMS/BC warehouse/2G',RUN=ROOT+'/target/platform-query-diagnostics-20260919/a2';
const OUT=ROOT+'/docs/releases/platform-query-diagnostics-20260919/a2';
const affected=['platform-process-lifecycle.test.mjs','platform-launch-preflight.test.mjs','platform-s1-start-stop.test.mjs','platform-process-ancestry.test.mjs','platform-process-query.test.mjs','platform-process-timing.test.mjs','platform-r6-stages.test.mjs','platform-report-runner.test.mjs','platform-s2-start-stop.test.mjs','platform-r10-binding.test.mjs','platform-r10-environment.test.mjs'];
const plan=[['platform-query-evidence.test.mjs',14],['platform-query-failure.test.mjs',15],...NODE_GATES.filter(([n])=>affected.includes(n))];
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const files=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(d+'/'+e.name):[d+'/'+e.name]).sort();
const write=(name,value)=>fs.writeFileSync(OUT+'/'+name,JSON.stringify(value,null,2)+'\n',{flag:'wx'});
if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Fixed workspace and Node required');
if(fs.existsSync(RUN)||fs.existsSync(OUT+'/regression.json'))throw Error('Fresh no-database output required');
const sources=files(ROOT+'/src/test/scripts').map(p=>({path:p.slice(ROOT.length+1),sha256:sha(fs.readFileSync(p))}));
const inputId=sha(JSON.stringify({sources,plan,node:process.version,perSuiteBudgetMs:120000,syntaxBudgetMs:15000}));
write('regression-input.json',{inputId,sources,plan,node:process.version,nodeSha256:sha(fs.readFileSync(process.execPath)),perSuiteBudgetMs:120000,syntaxBudgetMs:15000,executionCwd:ROOT,executeFromWorkspaceWithFingerprintChecks:true});
fs.mkdirSync(RUN,{recursive:true});fs.mkdirSync(RUN+'/tool-evidence');
for(const s of sources){const target=RUN+'/source/'+s.path;fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(ROOT+'/'+s.path,target,fs.constants.COPYFILE_EXCL);}
const verify=()=>{for(const s of sources)if(sha(fs.readFileSync(ROOT+'/'+s.path))!==s.sha256||sha(fs.readFileSync(RUN+'/source/'+s.path))!==s.sha256)throw Error('Source drift');};
const result={inputId,status:'RUNNING',startedAt:new Date().toISOString(),commands:[],suites:[],databaseStarted:false,databaseConnected:false,businessTestsExecuted:0,javaOrMavenExecuted:false};
const env={...process.env,P3_TOOL_EVIDENCE_DIR:RUN+'/tool-evidence'};
for(const key of Object.keys(env))if(/^(PG|SPRING_|JAVA_TOOL_OPTIONS$|_JAVA_OPTIONS$|JDK_JAVA_OPTIONS$|MAVEN_OPTS$|MAVEN_ARGS$)/i.test(key))delete env[key];
async function command(args,log,budget){
 verify();const r=await runProcess(process.execPath,args,{cwd:ROOT,env,timeoutMs:budget});
 const file=RUN+'/'+log;fs.writeFileSync(file,r.output,{flag:'wx'});
 result.commands.push({...r,output:undefined,args,budgetMs:budget,log:file,logSha256:sha(fs.readFileSync(file))});
 if(r.exitCode!==0||r.timedOut||r.aborted||!r.exited||!r.outputComplete||!r.terminationConfirmed)throw Error('No-database command failed: '+log);
 return r.output;
}
try{
 for(const n of ['platform-process-lifecycle.mjs','platform-launch-preflight.mjs','platform-r6-stages.mjs','platform-r10-database-batch.mjs','platform-query-diagnostics.mjs','platform-query-repair-verification.mjs'])await command(['--check',ROOT+'/src/test/scripts/'+n],n+'.syntax.log',15000);
 for(const [name,count] of plan){
  const output=await command(['--test','--test-reporter=tap',ROOT+'/src/test/scripts/'+name],name+'.tap',120000);
  const n=k=>Number(output.match(new RegExp('^# '+k+' (\\d+)$','m'))?.[1]);
  const row={name,expected:count,tests:n('tests'),passed:n('pass'),failures:n('fail'),skipped:n('skipped'),cancelled:n('cancelled')};result.suites.push(row);
  if(row.tests!==count||row.passed!==count||row.failures!==0||row.skipped!==0||row.cancelled!==0)throw Error('Expected case count/result mismatch: '+name);
  console.log(name+': '+count+' passed');
 }
 verify();result.sourceStillMatches=true;result.status='PASSED';
}catch(error){result.status='FAILED';result.failure=failureDetails(error,'NO_DATABASE_REGRESSION');result.failedCommand=result.commands.at(-1)?.log;process.exitCode=1;}
finally{result.completedAt=new Date().toISOString();write('regression.json',result);console.log(JSON.stringify({status:result.status,suites:result.suites.length,passed:result.suites.reduce((n,s)=>n+s.passed,0),failedCommand:result.failedCommand}));}

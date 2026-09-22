// Test-only result handling. Import is side-effect free; the sole parser process reads XML from stdin.
import fs from 'node:fs';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const PS='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';
const SCRIPT=fileURLToPath(new URL('./platform-junit-xml.ps1',import.meta.url));
export class ReportError extends Error {
  constructor(code){super(code);this.name='ReportError';this.code=code;}
}
export function parseJUnitXml(xml){
  if(typeof xml!=='string'||xml.length>8388608)throw new ReportError('INVALID_OR_UNSAFE_JUNIT_XML');
  let parsed;
  try {
    parsed=JSON.parse(execFileSync(PS,['-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',SCRIPT],
      {input:JSON.stringify({xml}),encoding:'utf8',windowsHide:true,timeout:15000,maxBuffer:16777216,stdio:['pipe','pipe','pipe']}));
  } catch(error) {
    if(error.status===2)throw new ReportError('INVALID_OR_UNSAFE_JUNIT_XML');
    throw new ReportError('XML_PARSER_EXECUTOR_FAILED');
  }
  if(parsed.ok!==true||!Array.isArray(parsed.cases))throw new ReportError('XML_PARSER_PROTOCOL_FAILED');
  const cases=parsed.cases,failures=cases.filter(c=>c.failure).length,errors=cases.filter(c=>c.error).length,skipped=cases.filter(c=>c.skipped).length;
  return {name:parsed.name,header:parsed.header,tests:cases.length,failures,errors,skipped,passed:cases.length-failures-errors-skipped,cases,
    xmlSha256:crypto.createHash('sha256').update(xml).digest('hex')};
}
export function readJUnitReport(file){
  let xml;try{xml=fs.readFileSync(file,'utf8');}catch{throw new ReportError('REPORT_MISSING_OR_UNREADABLE');}
  return parseJUnitXml(xml);
}
function validatePlan(plan){
  if(!Array.isArray(plan)||!plan.length||new Set(plan.map(s=>s.name)).size!==plan.length)throw Error('Invalid suite plan');
  for(const s of plan)if(!s.name||!Array.isArray(s.methods)||!s.methods.length||s.methods.some(n=>typeof n!=='string'||!n.trim())||new Set(s.methods).size!==s.methods.length)throw Error('Invalid expected methods');
}
export function executionEvidence(process){
  if(!process)return null;
  // Never retain raw child output, environment, exception messages, passwords or command arguments.
  return {exitCode:process.exitCode??null,exited:process.exited===true,timedOut:process.timedOut===true||process.timeout===true,
    aborted:process.aborted===true,spawnFailed:!!(process.spawnError||process.spawnFailed),signal:process.signal?true:false,
    outputComplete:process.outputComplete===true,terminationConfirmed:process.terminationConfirmed===true};
}
export function assessSuite(plan,{process=null,report=null,reportError=null,logSha256=null}={}){
  const p=executionEvidence(process),issues=[],counts={planned:plan.methods.length,passed:0,failures:0,errors:0,skipped:0,unexecuted:0,unverified:0};
  const processOkay=!!p&&p.exitCode===0&&p.exited&&!p.timedOut&&!p.aborted&&!p.spawnFailed&&!p.signal&&p.outputComplete&&p.terminationConfirmed;
  if(!p){counts.unexecuted=plan.methods.length;return {selector:plan.selector,name:plan.name,process:null,report:null,reportError:null,
    status:'NOT_EXECUTED',executionStatus:'NOT_EXECUTED',reportStatus:'NOT_READ',issues:[],counts};}
  if(!processOkay)issues.push('EXECUTION_FAILED');
  let reportStatus='VALID';
  if(reportError||!report){reportStatus='PARSE_FAILED';issues.push(reportError??'REPORT_MISSING_OR_UNREADABLE');counts.unverified=plan.methods.length;}
  else {
    if(report.name!==plan.name||report.cases.some(c=>c.classname!==plan.name))issues.push('WRONG_SUITE_OR_CLASS');
    const names=report.cases.map(c=>c.name),duplicates=[...new Set(names.filter((n,i)=>names.indexOf(n)!==i))];
    if(duplicates.length)issues.push('DUPLICATE_METHODS');
    if(names.some(n=>!plan.methods.includes(n)))issues.push('EXTRA_METHODS');
    if(plan.methods.some(n=>!names.includes(n)))issues.push('MISSING_METHODS');
    for(const key of ['tests','failures','errors','skipped'])if(report.header[key]!==report[key])issues.push('HEADER_'+key.toUpperCase()+'_MISMATCH');
    for(const name of plan.methods){
      const matches=report.cases.filter(c=>c.name===name&&c.classname===plan.name);
      if(report.name!==plan.name||matches.length!==1||duplicates.includes(name)){counts.unverified++;continue;}
      const c=matches[0];counts[c.failure?'failures':c.error?'errors':c.skipped?'skipped':'passed']++;
    }
    if(issues.some(i=>i!=='EXECUTION_FAILED'))reportStatus='VALIDATION_FAILED';
    if(report.failures||report.errors)issues.push('BUSINESS_TEST_FAILED');
    if(report.skipped)issues.push('UNEXPECTED_SKIP');
  }
  const status=reportStatus==='PARSE_FAILED'?'REPORT_PARSE_FAILED':reportStatus==='VALIDATION_FAILED'?'REPORT_VALIDATION_FAILED'
    :issues.includes('BUSINESS_TEST_FAILED')?'BUSINESS_TEST_FAILED':issues.includes('UNEXPECTED_SKIP')?'UNEXPECTED_SKIP':!processOkay?'EXECUTION_FAILED':'PASSED';
  return {selector:plan.selector,name:plan.name,process:p,report,reportError,logSha256:/^[a-f0-9]{64}$/.test(logSha256)?logSha256:null,
    status,executionStatus:processOkay?'SUCCEEDED':'FAILED',reportStatus,issues,counts};
}
export function summarizeSuiteResults(plan,rows=[]){
  validatePlan(plan);
  if(rows.some(r=>!plan.some(p=>p.name===r.name))||new Set(rows.map(r=>r.name)).size!==rows.length)throw Error('Unexpected or duplicate suite result');
  // Re-evaluate raw structured evidence. Cached status/counts cannot turn a failed suite into a pass.
  const suites=plan.map(p=>assessSuite(p,rows.find(r=>r.name===p.name)));
  const total={plannedSuites:plan.length,plannedMethods:plan.reduce((n,p)=>n+p.methods.length,0),passed:0,failures:0,errors:0,skipped:0,unexecuted:0,unverified:0,
    executedSuites:suites.filter(s=>s.process).length,passedSuites:suites.filter(s=>s.status==='PASSED').length,reportFailures:0,executionFailures:0};
  for(const s of suites){for(const k of ['passed','failures','errors','skipped','unexecuted','unverified'])total[k]+=s.counts[k];
    if(s.reportStatus==='PARSE_FAILED'||s.reportStatus==='VALIDATION_FAILED')total.reportFailures++;
    if(s.executionStatus==='FAILED')total.executionFailures++;}
  const bad=suites.find(s=>s.status!=='PASSED'&&s.status!=='NOT_EXECUTED');
  return {status:bad?'FAILED':total.passedSuites===plan.length?'PASSED':total.executedSuites?'INCOMPLETE':'NOT_EXECUTED',suites,coverage:total,
    failedStage:bad?.name.split('.').at(-1)??null,failureCategory:bad?.status??null};
}
export async function runSuitePlan({plan,execute,beforeSuite=async()=>{},onUpdate=()=>{}}){
  validatePlan(plan);let rows=[];onUpdate(summarizeSuiteResults(plan,rows));
  for(const p of plan){
    await beforeSuite(p);
    let payload;
    try{payload=await execute(p);}catch{payload={process:{spawnFailed:true},reportError:'SUITE_EXECUTOR_THROWN'};}
    let report=null,reportError=null;
    if(payload?.process){
      if(payload.reportError)reportError=payload.reportError;
      else try{report=payload.xmlFile?readJUnitReport(payload.xmlFile):parseJUnitXml(payload.xml);}catch(error){reportError=error instanceof ReportError?error.code:'REPORT_READ_FAILED';}
    }
    const row=assessSuite(p,{process:payload?.process,report,reportError,logSha256:payload?.logSha256});rows.push(row);
    const result=summarizeSuiteResults(plan,rows);onUpdate(result);
    if(row.status!=='PASSED')return result;
  }
  return summarizeSuiteResults(plan,rows);
}

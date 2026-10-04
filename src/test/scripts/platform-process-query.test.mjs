import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {runProcess,failureDetails} from './platform-process-lifecycle.mjs';
import {queryWindowsProcess,inspectWindowsEnvironment,EnvironmentInspectionError,POWERSHELL} from './platform-launch-preflight.mjs';
const helper=fs.readFileSync(new URL('./platform-process-query.ps1',import.meta.url),'utf8');
const ps=`$ErrorActionPreference='Stop'
${helper}
function Sample {param([string]$line='synthetic -D "D:/synthetic/pgdata"')
 return [pscustomobject]@{ProcessId=123;ParentProcessId=100;CommandLine=$line;ExecutablePath='D:/synthetic/node.exe';CreationDate=[datetime]'2026-09-16T01:00:00Z'}
}
$owner={param($p) @{ReturnValue=0;Sid='synthetic-owner'}}
$good={param($n) Sample}
$partial=Sample;$partial.CommandLine=$null
$r=@{}
$r.good=ProcessProof 123 'SYNTHETIC' $null $good $owner
$r.absent=ProcessProof 123 'SYNTHETIC' $null {param($n)} $owner
$r.exited=ProcessProof 123 'INVENTORY' $partial {param($n)} $owner
$r.denied=ProcessProof 123 'INVENTORY' $partial {param($n) throw [UnauthorizedAccessException]::new('SYNTHETIC_SECRET_MUST_NOT_LOG')} $owner
$r.unknown=ProcessProof 123 'INVENTORY' $partial {param($n) throw [InvalidOperationException]::new('SYNTHETIC_SECRET_MUST_NOT_LOG')} $owner
$r.recovered=ProcessProof 123 'INVENTORY' $partial $good $owner
$r.reused=ProcessProof 123 'INVENTORY' $partial {param($n) $p=Sample;$p.CreationDate=$p.CreationDate.AddSeconds(1);$p} $owner
$r.ownerDenied=ProcessProof 123 'LISTENER_OWNER' $null $good {param($p) @{ReturnValue=2;Sid=$null}}
$r.ownerExited=ProcessProof 123 'SERVER' (Sample) {param($n)} {param($p) throw [InvalidOperationException]::new('synthetic')}
$r.initialFailedThenEmpty=ProcessProof 123 'SERVER' $null {param($n) if(-not $script:once){$script:once=$true;throw [InvalidOperationException]::new('synthetic')}} $owner
$r | ConvertTo-Json -Depth 10 -Compress`;
let result;
test('actual shared PowerShell helper executes no-database diagnostic contracts',async()=>{
 const r=await runProcess(POWERSHELL,['-NoProfile','-NonInteractive','-Command',ps],{timeoutMs:15000});assert.equal(r.exitCode,0,r.output);assert.ok(r.outputComplete);result=JSON.parse(r.output.trim());
 assert.ok(!r.output.includes('SYNTHETIC_SECRET_MUST_NOT_LOG'));
 if(process.env.P3_TOOL_EVIDENCE_DIR)fs.writeFileSync(process.env.P3_TOOL_EVIDENCE_DIR+'/query-contract-results.json',JSON.stringify({result,timing:r.timing},null,2),{flag:'wx'});
});
test('available proof has exact time, directory and command hash, never raw command',()=>{assert.equal(result.good.status,'AVAILABLE');assert.equal(result.good.directory,'D:/synthetic/pgdata');assert.match(result.good.creationTicks,/^\d+$/);assert.equal(result.good.commandLineSha256.length,64);assert.ok(!Object.hasOwn(result.good,'CommandLine'));});
for(const [label,status,category] of [['absent','ABSENT','CONFIRMED_ABSENT'],['exited','EXITED_DURING_QUERY','PROCESS_EXITED_DURING_QUERY'],['denied','ACCESS_DENIED','ACCESS_DENIED'],['unknown','INCOMPLETE','QUERY_FAILED_UNKNOWN'],['recovered','INCOMPLETE','INCOMPLETE_UNKNOWN'],['reused','PID_REUSED','PID_REUSED_DURING_QUERY'],['ownerDenied','ACCESS_DENIED','ACCESS_DENIED'],['ownerExited','EXITED_DURING_QUERY','PROCESS_EXITED_DURING_QUERY'],['initialFailedThenEmpty','QUERY_FAILED','INCOMPLETE_UNKNOWN']])
 test('diagnosis distinguishes '+label+' and does not silently recover',()=>{assert.equal(result[label].status,status);assert.equal(result[label].category,category);assert.equal(result[label].pid,123);if(label!=='absent')assert.ok(result[label].missingFields.length);});
test('real query adapter receives same helper and preserves fail-closed PID/field details',async()=>{
 let payload;const command=async(exe,args)=>{assert.equal(exe,POWERSHELL);payload=args[3];return {exitCode:0,exited:true,terminationConfirmed:true,outputComplete:true,output:JSON.stringify({...result.exited,stage:'INVENTORY'})};};
 await assert.rejects(()=>queryWindowsProcess(command,{pid:123,stage:'INVENTORY'}),e=>{const d=failureDetails(e,'READINESS');assert.equal(d.diagnostics[0].pid,123);assert.deepEqual(d.diagnostics[0].missingFields,['CommandLine']);assert.equal(d.diagnostics[0].status,'EXITED_DURING_QUERY');return true;});
 assert.ok(payload.includes(helper));
});
test('summary keeps first failure plus incomplete global PID diagnostics without sensitive fields',()=>{
 const e=new EnvironmentInspectionError('IDENTITY_QUERY_INCOMPLETE','postgres',{postgres:{status:'INCOMPLETE',issues:[{...result.exited,CommandLine:'private',exceptionMessage:'private'}]}});
 const d=failureDetails(e,'CLEANUP');assert.ok(d.diagnostics.some(x=>x.pid===123&&x.stage==='INVENTORY'));assert.ok(!JSON.stringify(d).includes('private'));
});
test('query executor failure and invalid JSON cannot produce ABSENT',async()=>{
 for(const r of [{exitCode:1},{exitCode:0,exited:true,outputComplete:true,terminationConfirmed:true,output:'damaged'}])await assert.rejects(()=>queryWindowsProcess(async()=>r,{pid:123}));
});
test('main environment inventory and target PID query both use shared diagnostics',()=>{
 const s=fs.readFileSync(new URL('./platform-launch-preflight.mjs',import.meta.url),'utf8'),p=fs.readFileSync(new URL('./platform-windows-identity.ps1',import.meta.url),'utf8');
 assert.match(s,/replace\('# PROCESS_QUERY_HELPERS',fs\.readFileSync\(queryScript/);assert.match(p,/issues\+=ProcessProof/);assert.match(p,/issues=\$issues/);
});
test('complete actual inline environment payload parses in real Windows PowerShell without executing its OS queries',async()=>{
 let payload;
 const sample={schema:1};
 await inspectWindowsEnvironment(async(exe,args)=>{payload=args[3];return {exitCode:0,exited:true,outputComplete:true,terminationConfirmed:true,output:JSON.stringify(sample)};},{directory:'D:/synthetic-not-created/pgdata',port:55449});
 const encoded=Buffer.from(payload,'utf8').toString('base64');
 const parse=`$text=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${encoded}'));$tokens=$null;$errors=$null;[void][Management.Automation.Language.Parser]::ParseInput($text,[ref]$tokens,[ref]$errors);if($errors.Count){$errors|ForEach-Object {$_.ErrorId};exit 1};'PARSE_OK'`;
 const r=await runProcess(POWERSHELL,['-NoProfile','-NonInteractive','-Command',parse],{timeoutMs:10000});assert.equal(r.exitCode,0,r.output);assert.match(r.output,/PARSE_OK/);
});

// Narrow catalog support for this platform verification batch, not a general JUnit engine.
import fs from 'node:fs';
import crypto from 'node:crypto';
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
const ROOT='D:/ERP_WMS/BC warehouse/2G';
const APPROVED=ROOT+'/docs/releases/platform-export-fixture-repair-20260918/a3';
const MFA='com.wms.system.platform.service.PlatformMfaFailureFixtureTest';
const HELPER='src/test/java/com/wms/system/platform/service/PlatformMfaFailureFixture.java';
const unique=(rows,label)=>{if(!Array.isArray(rows)||!rows.length||new Set(rows).size!==rows.length)throw Error('Missing or duplicate '+label);};
function declarations(text){
 // Preserve quoted annotation arguments but ignore comment-only annotations.
 const clean=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,
  token=>token.startsWith('//')||token.startsWith('/*')?token.replace(/[^\r\n]/g,' '):token);
 const ordinary=[...clean.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\(/g)].map(m=>m[1]);
 const values=[...clean.matchAll(/@ParameterizedTest\s+@ValueSource\((strings|ints)=\{([\s\S]*?)\}\)\s+void\s+(\w+)\((String|int)\s+\w+\)/g)];
 const methods=[...clean.matchAll(/@ParameterizedTest\s+@MethodSource\("([A-Za-z0-9_.#$]+)"\)\s+void\s+(\w+)\(([A-Za-z0-9_.$]+)\s+\w+\)/g)];
 // Fail closed for declarations outside the exact annotation forms used by this batch.
 const count=(clean.match(/@ParameterizedTest\b/g)??[]).length;
 if(count!==values.length+methods.length||(clean.match(/@Test\b/g)??[]).length!==ordinary.length)throw Error('Unsupported test declaration');
 const names=[...ordinary,...values.map(m=>m[3]),...methods.map(m=>m[2])];unique(names,'test methods');
 const generated=[...ordinary];
 for(const m of values){
  const inputs=m[1]==='strings'?[...m[2].matchAll(/"(?:\\.|[^"\\])*"/g)]:m[2].split(',').map(v=>{if(!/^\s*\d+\s*$/.test(v))throw Error('Invalid value source');return v;});
  if(!inputs.length)throw Error('Empty value source');
  for(let i=1;i<=inputs.length;i++)generated.push(`${m[3]}(${m[4]})[${i}]`);
 }
 return {generated,methodSources:methods.map(m=>({provider:m[1],method:m[2],type:m[3].split('.').at(-1)}))};
}
export function resolveCases(text,{approval=null,actualFiles={}}={}){
 const declared=declarations(text);
 if(!approval){if(declared.methodSources.length)throw Error('MethodSource requires unchanged source and approved complete catalog');return declared.generated;}
 if(hash(text)!==approval.sourceSha256)throw Error('Approved test source changed');
 unique(approval.methods,'approved case names');
 if(!Array.isArray(approval.sources)||approval.sources.length===0||new Set(approval.sources.map(s=>s.path)).size!==approval.sources.length)throw Error('Missing or duplicate parameter source fingerprints');
 for(const source of approval.sources)if(!/^[a-f0-9]{64}$/.test(source.sha256)||actualFiles[source.path]!==source.sha256)throw Error('Parameter source changed or missing');
 const expected=[...declared.generated],descriptors=approval.methodSources??[];
 if(descriptors.length!==declared.methodSources.length||new Set(descriptors.map(s=>s.method)).size!==descriptors.length)throw Error('MethodSource declaration catalog mismatch');
 for(const source of declared.methodSources){
  const matches=descriptors.filter(d=>d.method===source.method&&d.provider===source.provider&&d.type===source.type);
  if(matches.length!==1||!Number.isInteger(matches[0].count)||matches[0].count<=0)throw Error('MethodSource coverage missing');
  for(let i=1;i<=matches[0].count;i++)expected.push(`${source.method}(${source.type})[${i}]`);
 }
 unique(expected,'independent case names');
 if(expected.length!==approval.methods.length||expected.some(name=>!approval.methods.includes(name)))throw Error('Approved case catalog has missing or extra names');
 return [...approval.methods]; // Names only. No historical result or pass flag is consumed.
}
export function loadApprovedCatalog(){
 const input=APPROVED+'/input-manifest.json',results=APPROVED+'/verification-results.json';
 if(hash(fs.readFileSync(input))!=='a26173d00c7a1705e31d0ba4621002c38551d1f09570877b46a27fc86a9a4e74'
  ||hash(fs.readFileSync(results))!=='9d299cf9191944bf9e80ea902d475a364073e2a8fa9b8a7e1d730e2b029de99a')throw Error('Approved catalog evidence fingerprint changed');
 const manifest=JSON.parse(fs.readFileSync(input,'utf8')),names=JSON.parse(fs.readFileSync(results,'utf8')).guards;
 return names.map(g=>{
  const file='src/test/java/'+g.name.replaceAll('.','/')+'.java';
  const paths=g.name===MFA?[file,HELPER]:[file];
  const sources=paths.map(p=>{const record=manifest.groups[0].files.find(f=>f.path===p);if(!record)throw Error('Approved source record missing');return {path:p,sha256:record.sha256};});
  return {name:g.name,sourceSha256:sources[0].sha256,sources,methods:g.cases.map(c=>c.name),
   methodSources:g.name===MFA?[{provider:'invalid',method:'wrongCountsOrChallengeStateStopBeforeInvocation',type:'Before',count:12}]:[]};
 });
}
export function preflightCatalog({root,gates,approved=loadApprovedCatalog(),read=p=>fs.readFileSync(p,'utf8')}){
 unique(gates.map(g=>g[0]),'gate suites');unique(approved.map(g=>g.name),'approved suites');
 return gates.map(([name,count])=>{
  const file='src/test/java/'+name.replaceAll('.','/')+'.java',text=read(root+'/'+file),sourceSha256=hash(text);
  const prior=approved.find(c=>c.name===name),unchanged=prior?.sourceSha256===sourceSha256;
  const actualFiles={};if(unchanged)for(const s of prior.sources)actualFiles[s.path]=hash(read(root+'/'+s.path));
  const methods=resolveCases(text,{approval:unchanged?prior:null,actualFiles});
  if(methods.length!==count)throw Error('Independent Java case count mismatch: '+name);
  return {name,methods,sourceSha256,mode:unchanged?'VERIFIED_APPROVED_NAMES_ONLY':'INDEPENDENT_SOURCE_DECLARATIONS',
   parameterSources:unchanged?prior.sources:[{path:file,sha256:sourceSha256}]};
 });
}

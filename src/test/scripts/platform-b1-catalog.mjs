// Explicit inventory for the six approved b1 gates. No generic import discovery or fallback.
import fs from 'node:fs';
import crypto from 'node:crypto';
export const SHARED_FILES=Object.freeze([
 'platform-process-lifecycle.test.mjs','platform-launch-preflight.test.mjs','platform-process-ancestry.test.mjs','platform-process-query.test.mjs',
 'platform-process-lifecycle.mjs','platform-launch-preflight.mjs','platform-process-ancestry.mjs','platform-process-query.ps1','platform-windows-identity.ps1',
 'fixtures/PlatformPipeInheritance.cs','platform-s1-start-stop.mjs','platform-s2-start-stop.mjs',
 'platform-r7-database-batch.mjs','platform-r7-backend-snapshot.mjs','platform-upgrade-sentinels.mjs','platform-r6-stages.mjs',
 'platform-test-results.mjs','platform-report-runner.mjs','platform-junit-xml.ps1','platform-report-pom.ps1',
]);
export const SHARED_EDGES=Object.freeze({
 'platform-process-lifecycle.test.mjs':['platform-process-lifecycle.mjs','fixtures/PlatformPipeInheritance.cs'],
 'platform-launch-preflight.test.mjs':['platform-launch-preflight.mjs','platform-r7-database-batch.mjs','platform-windows-identity.ps1'],
 'platform-process-ancestry.test.mjs':['platform-process-ancestry.mjs','platform-s1-start-stop.mjs'],
 'platform-process-query.test.mjs':['platform-process-lifecycle.mjs','platform-launch-preflight.mjs','platform-process-query.ps1','platform-windows-identity.ps1'],
 'platform-launch-preflight.mjs':['platform-process-lifecycle.mjs','platform-windows-identity.ps1','platform-process-query.ps1'],
 'platform-process-ancestry.mjs':['platform-process-lifecycle.mjs'],
 'platform-s1-start-stop.mjs':['platform-process-lifecycle.mjs','platform-launch-preflight.mjs','platform-process-ancestry.mjs'],
 'platform-s2-start-stop.mjs':['platform-process-lifecycle.mjs','platform-launch-preflight.mjs','platform-process-ancestry.mjs'],
 'platform-r7-database-batch.mjs':['platform-process-lifecycle.mjs','platform-launch-preflight.mjs','platform-upgrade-sentinels.mjs','platform-test-results.mjs','platform-r7-backend-snapshot.mjs','platform-r6-stages.mjs','platform-report-runner.mjs'],
 'platform-r7-backend-snapshot.mjs':['platform-report-runner.mjs'],
 'platform-r6-stages.mjs':['platform-test-results.mjs','platform-process-lifecycle.mjs'],
 'platform-test-results.mjs':['platform-junit-xml.ps1'],
 'platform-report-runner.mjs':['platform-process-lifecycle.mjs','platform-test-results.mjs','platform-r6-stages.mjs','platform-report-pom.ps1'],
});
export const hashBytes=b=>crypto.createHash('sha256').update(b).digest('hex');
export function requireCatalogNames(names){
 if(!Array.isArray(names)||names.length!==SHARED_FILES.length||new Set(names).size!==names.length
   ||names.some(n=>!SHARED_FILES.includes(n)))throw Error('B1_SHARED_CATALOG_MISMATCH');
}
export function requireContents(content){
 for(const name of SHARED_FILES)if(!content.has(name))throw Error('B1_REQUIRED_CONTENT_MISSING '+name);
 for(const [owner,targets]of Object.entries(SHARED_EDGES))for(const target of targets){
  if(!content.has(target)||!content.get(owner).toString().includes('./'+target))throw Error('B1_EXPLICIT_EDGE_MISMATCH '+owner+' -> '+target);
 }
}
export function preflightShared({sourceRoot,approvedFiles,names=SHARED_FILES,read=p=>{
 const stat=fs.lstatSync(p);if(!stat.isFile()||stat.isSymbolicLink())throw Error('B1_SOURCE_NOT_REGULAR');return fs.readFileSync(p);
}}){
 requireCatalogNames(names);
 const content=new Map(),provenance=[];
 for(const name of names){
  const relative='src/test/scripts/'+name,matches=approvedFiles.filter(f=>f.path===relative);
  if(matches.length!==1||!/^[a-f0-9]{64}$/.test(matches[0].sha256))throw Error('B1_APPROVED_SOURCE_MISSING_OR_DUPLICATE '+name);
  const origin=sourceRoot+'/'+name,bytes=read(origin); // Exact source only. ENOENT propagates; never try another root.
  if(hashBytes(bytes)!==matches[0].sha256)throw Error('B1_APPROVED_SOURCE_HASH_MISMATCH '+name);
  content.set(name,bytes);provenance.push({path:name,origin,sourceSha256:matches[0].sha256,mode:'EXACT_R11_FROZEN_TEST_DEPENDENCY'});
 }
 requireContents(content);return {content,provenance};
}

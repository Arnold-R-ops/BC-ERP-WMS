import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import cp from 'node:child_process';
import {syncBuiltinESMExports} from 'node:module';
import {SHARED_FILES,SHARED_EDGES,hashBytes,requireCatalogNames,requireContents,preflightShared} from './platform-b1-catalog.mjs';
function fixture(){
 const content=new Map(SHARED_FILES.map(n=>[n,Buffer.from((SHARED_EDGES[n]??[]).map(d=>"from './"+d+"'").join('\n'))]));
 const approvedFiles=[...content].map(([n,b])=>({path:'src/test/scripts/'+n,sha256:hashBytes(b)}));
 const calls=[];const read=p=>{calls.push(p);const n=p.slice('/frozen/'.length);if(!content.has(n))throw Object.assign(Error('missing'),{code:'ENOENT'});return content.get(n);};
 return {content,approvedFiles,calls,read};
}
const run=f=>preflightShared({sourceRoot:'/frozen',approvedFiles:f.approvedFiles,read:f.read});
test('complete explicit inventory preserves relative fixture directory and origin hashes',()=>{const f=fixture(),r=run(f);assert.equal(r.content.size,20);assert.equal(r.provenance.find(p=>p.path==='fixtures/PlatformPipeInheritance.cs').origin,'/frozen/fixtures/PlatformPipeInheritance.cs');assert.ok(r.provenance.every(p=>p.sourceSha256.length===64));});
test('missing native fixture fails before sealing',()=>{const f=fixture();f.content.delete('fixtures/PlatformPipeInheritance.cs');assert.throws(()=>run(f),{code:'ENOENT'});});
test('missing r7 test dependency fails before sealing',()=>{const f=fixture();f.content.delete('platform-r7-database-batch.mjs');assert.throws(()=>run(f),{code:'ENOENT'});});
test('missing s1 source dependency fails before sealing',()=>{const f=fixture();f.content.delete('platform-s1-start-stop.mjs');assert.throws(()=>run(f),{code:'ENOENT'});});
test('each transitive dependency is mandatory',()=>{for(const n of ['platform-r7-backend-snapshot.mjs','platform-report-runner.mjs','platform-r6-stages.mjs','platform-test-results.mjs','platform-junit-xml.ps1','platform-report-pom.ps1']){const f=fixture();f.content.delete(n);assert.throws(()=>run(f),{code:'ENOENT'});}});
test('missing approved provenance is rejected',()=>{const f=fixture();f.approvedFiles.pop();assert.throws(()=>run(f),/B1_APPROVED_SOURCE_MISSING_OR_DUPLICATE/);});
test('changed source bytes are rejected',()=>{const f=fixture();f.content.set(SHARED_FILES[0],Buffer.from('changed'));assert.throws(()=>run(f),/B1_APPROVED_SOURCE_HASH_MISMATCH/);});
test('duplicate catalog and duplicate approved origin are rejected',()=>{assert.throws(()=>requireCatalogNames([...SHARED_FILES.slice(1),SHARED_FILES[1]]));const f=fixture();f.approvedFiles.push(f.approvedFiles[0]);assert.throws(()=>run(f),/B1_APPROVED_SOURCE_MISSING_OR_DUPLICATE/);});
test('unexpected or traversing path cannot enter the catalog',()=>{for(const n of ['../outside.mjs','extra.mjs'])assert.throws(()=>requireCatalogNames([...SHARED_FILES.slice(1),n]));});
test('missing file never falls back to workspace or another history root',()=>{const f=fixture();f.content.delete(SHARED_FILES[0]);assert.throws(()=>run(f),{code:'ENOENT'});assert.deepEqual(f.calls,['/frozen/'+SHARED_FILES[0]]);});
test('content and edge preflight reject missing copied files or changed references',()=>{const f=fixture();f.content.delete('platform-report-pom.ps1');assert.throws(()=>requireContents(f.content));const g=fixture();g.content.set('platform-r7-backend-snapshot.mjs',Buffer.from('wrong'));assert.throws(()=>requireContents(g.content),/B1_EXPLICIT_EDGE_MISMATCH/);});
test('historical entries import without process execution or file mutation',async()=>{
 const changed=[];const deny=()=>{throw Error('B1_HISTORICAL_ENTRY_SIDE_EFFECT');};
 for(const [object,keys]of [[cp,['spawn','spawnSync','exec','execSync','execFile','execFileSync','fork']],[fs,['writeFileSync','appendFileSync','mkdirSync','rmSync','unlinkSync','copyFileSync']]])for(const k of keys){changed.push([object,k,object[k]]);object[k]=deny;}
 syncBuiltinESMExports();
 try{const r=await import('./platform-r7-database-batch.mjs');const s=await import('./platform-s1-start-stop.mjs');const s2=await import('./platform-s2-start-stop.mjs');assert.equal(typeof r.withInitializationPreflight,'function');assert.equal(typeof s.runS1Sequence,'function');assert.equal(typeof s2.runS2Sequence,'function');}
 finally{for(const [o,k,v]of changed)o[k]=v;syncBuiltinESMExports();}
});
test('historical CLI branches are guarded and no-db preparation does not call them',()=>{
 for(const n of ['platform-r7-database-batch.mjs','platform-r7-backend-snapshot.mjs','platform-s1-start-stop.mjs','platform-s2-start-stop.mjs'])assert.ok(fs.readFileSync(new URL('./'+n,import.meta.url),'utf8').includes('path.resolve(process.argv[1])===fileURLToPath(import.meta.url)'));
 const prepare=fs.readFileSync(new URL('./platform-b1-prepare.mjs',import.meta.url),'utf8');assert.ok(prepare.indexOf('preflightShared({')<prepare.indexOf('fs.mkdirSync(B.SOURCE'));assert.ok(prepare.includes('requireContents(content)'));assert.ok(!prepare.includes("await import('./platform-r7-database-batch.mjs')"));
});

// T1 contracts only: pure generation and source-set completeness, no OS or database discovery.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {browserJavaSources,BROWSER_JAVA_SOURCE_DIR} from './platform-b1-prepare.mjs';
const HERE=path.dirname(fileURLToPath(import.meta.url));
const context=JSON.parse(fs.readFileSync(HERE+'/t1-context.json','utf8'));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const read=name=>fs.readFileSync(HERE+'/templates/'+name,'utf8');
function template(source){return read(path.basename(source));}
const generated=()=>browserJavaSources(template);

test('generation reads exactly the two declared Java templates',()=>{
 const calls=[];browserJavaSources((source,destination)=>{calls.push([source,destination]);return template(source);});
 assert.deepEqual(calls,[['java/com/wms/platformverification/PlatformR2Isolation.java','PlatformB1Isolation.java'],['java/com/wms/platformverification/PlatformR2DatabasePreparer.java','PlatformB1DatabasePreparer.java']]);
});
test('generated isolation and preparer are byte-identical to accepted b4 sources',()=>{
 for(const [name,text]of generated())assert.equal(sha(text),context.generated[name]);
});
test('missing template fails without fallback or trying a second source',()=>{
 let calls=0;assert.throws(()=>browserJavaSources(()=>{calls++;throw Object.assign(Error('synthetic missing'),{code:'ENOENT'});}),{code:'ENOENT'});assert.equal(calls,1);
});
test('missing upgrade identity guard refuses generation',()=>{
 assert.throws(()=>browserJavaSources(source=>template(source).replace('    public static final String UPGRADE = "p3_m1_platform_upgrade_r11";','')),/B1_TEMPLATE_MISMATCH/);
});
test('missing database allowlist guard refuses generation',()=>{
 assert.throws(()=>browserJavaSources(source=>template(source).replace('Set.of(MAIN, UPGRADE)','Set.of(MAIN)')),/B1_TEMPLATE_MISMATCH/);
});
test('missing preparer upgrade reference refuses generation',()=>{
 assert.throws(()=>browserJavaSources(source=>template(source).replace(' + " and " + PlatformR2Isolation.UPGRADE','')),/B1_TEMPLATE_MISMATCH/);
});
test('generation retains single target and every accepted isolation check',()=>{
 const sources=generated();assert.equal(sources.size,2);
 const isolation=sources.get('PlatformB1Isolation.java'),preparer=sources.get('PlatformB1DatabasePreparer.java');
 assert.ok(isolation.includes('Set.of(MAIN)'));assert.ok(isolation.includes('verifyIdentity('));
 assert.ok(isolation.includes('runtimeDataSource()'));assert.ok(isolation.includes('127.0.0.1'));
 assert.ok(!preparer.includes('upgrade-to-')&&!preparer.includes('p3_m1_probe'));
 for(const [name,text]of sources)assert.equal(sha(text),context.generated[name]);
});
test('all five standalone Java sources retain accepted bytes and contract count',()=>{
 assert.equal(Object.keys(context.java).length,5);
 for(const [name,digest]of Object.entries(context.java))assert.equal(sha(fs.readFileSync(HERE+'/'+name)),digest);
 assert.ok(fs.readFileSync(HERE+'/PlatformB1Contract.java','utf8').includes('if(passed!=16)'));
});
test('standalone main programs contain no removed JUnit test declarations',()=>{
 for(const name of Object.keys(context.java))assert.doesNotMatch(fs.readFileSync(HERE+'/'+name,'utf8'),/^\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b/m);
 assert.equal(BROWSER_JAVA_SOURCE_DIR,'src/test/tools/browseracceptance/java/com/wms/browseracceptance/');
});
test('real preparation calls the same generator and explicit standalone source directory',()=>{
 const prepare=fs.readFileSync(HERE+'/platform-b1-prepare.mjs','utf8');
 assert.ok(prepare.includes('for(const [name,text]of browserJavaSources(template))content.set(name,text)'));
 assert.ok(prepare.includes('source(BROWSER_JAVA_SOURCE_DIR+n)'));
 assert.equal((prepare.match(/let iso=derive\(/g)||[]).length,1);
});
test('ordinary Maven POM and formal JUnit source inventory are unchanged',()=>{
 assert.equal(sha(fs.readFileSync(context.source+'/pom.xml')),context.pomSha256);
 for(const row of context.junit)assert.equal(sha(fs.readFileSync(context.source+'/'+row.path)),row.sha256);
 assert.ok(context.junit.length>0);
 for(const name of Object.keys(context.java))assert.equal(fs.existsSync(context.source+'/src/test/java/com/wms/browseracceptance/'+name),false);
});
test('dedicated compilation has fresh product classes and explicit generated sources, not old class output',()=>{
 const args=fs.readFileSync(HERE+'/compile.args','utf8');
 assert.ok(args.includes(context.build+'/classes'));assert.ok(args.includes(context.classes));
 for(const name of [...Object.keys(context.java),...Object.keys(context.generated)])assert.ok(args.includes(HERE.replaceAll('\\','/')+'/'+name));
 assert.ok(!args.includes('/r11/build/')&&!args.includes('/platform-b4-preparation-'));
});

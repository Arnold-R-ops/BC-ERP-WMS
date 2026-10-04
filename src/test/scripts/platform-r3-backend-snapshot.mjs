// R3 backend-only acceptance: immutable fresh inputs, historical R2 data/evidence stay untouched.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
export const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const RUN=ROOT+'/target/platform-db-acceptance-20260911/r3';
export const SOURCE=RUN+'/snapshot/source',REPO=RUN+'/snapshot/maven-repository';
export const MAVEN=RUN+'/snapshot/apache-maven-3.9.9',BUILD=RUN+'/build/a1';
export const DELIVERY=ROOT+'/docs/releases/platform-db-acceptance-20260911/r3';
export const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe',PG='D:/ITsoftware/PostgreSQL/bin';
const REPAIR=ROOT+'/docs/releases/platform-tool-repair-20260911/a2';
const OLD=ROOT+'/docs/releases/platform-db-acceptance-20260911';
export const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
export const digest=p=>sha(fs.readFileSync(p));
export const readJson=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
export function writeJson(p,obj){fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(obj,null,2)+'\n',{flag:'wx'});}
export function requireRoot(){if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Wrong root or Node');}
export function files(directory){return fs.readdirSync(directory,{withFileTypes:true}).flatMap(e=>{
  const p=path.join(directory,e.name);if(e.isSymbolicLink())throw Error('No links in snapshot');return e.isDirectory()?files(p):[p];}).sort();}
const aggregate=rows=>sha(Buffer.from(rows.map(r=>r.path+'\0'+r.sha256+'\n').join('')));
function copyRecords(from,to,records){for(const f of records){const dest=to+'/'+f.path;
  if(digest(from+'/'+f.path)!==f.sha256)throw Error('Input changed before copy');
  fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(from+'/'+f.path,dest,fs.constants.COPYFILE_EXCL);
  if(digest(dest)!==f.sha256||digest(from+'/'+f.path)!==f.sha256)throw Error('Copy drift');}}
function testBodies(text){
  text=text.replaceAll('\r\n','\n');
  const mask=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,s=>s.replace(/[^\r\n]/g,' '));
  return [...mask.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\([^)]*\)\s*(?:throws[^\{]+)?\{/g)].map(m=>{
    let end=m.index+m[0].length,n=1;for(;end<mask.length&&n;end++){if(mask[end]==='{')n++;if(mask[end]==='}')n--;}
    if(n)throw Error('Test parse failed');return {name:m[1],sha256:sha(Buffer.from(text.slice(m.index,end)))};});
}
export function verify(){
  const manifest=readJson(DELIVERY+'/backend-input-manifest.json');
  for(const group of [...manifest.groups,...manifest.preserved]){
    for(const f of group.files)if(digest(group.location+'/'+f.path)!==f.sha256)throw Error('Input/evidence drift: '+f.path);
    if(group.name==='old-pgdata'){
      const actual=files(group.location).map(p=>path.relative(group.location,p).replaceAll('\\','/')).sort();
      if(JSON.stringify(actual)!==JSON.stringify(group.files.map(f=>f.path).sort()))throw Error('Old data inventory changed');
    }
  }
  for(const tool of manifest.externalTools)if(digest(tool.path)!==tool.sha256)throw Error('Tool drift');
  for(const j of manifest.testClasspath)if(digest(REPO+'/'+j.path)!==j.sha256)throw Error('Classpath drift');
  return manifest;
}
function seal(){
  requireRoot();if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('Never reuse R3 directory or evidence');
  const previous=readJson(REPAIR+'/input-manifest.json'),oldSource=previous.groups.find(g=>g.name==='source');
  const oldDb=readJson(OLD+'/backend-input-manifest-v2.json'),repairResult=readJson(REPAIR+'/verification-results.json');
  const allowed=new Set(['PlatformR2Isolation.java','PlatformR2IsolationTest.java','PlatformR2SpringInitializer.java','PlatformR2DatabasePreparer.java'].map(n=>'src/test/java/com/wms/platformverification/'+n));
  const changes=oldSource.files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
  if(changes.some(p=>!allowed.has(p)))throw Error('Unreviewed existing input drift: '+changes.join(','));
  for(const g of [...previous.groups,...previous.preserved])for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical evidence drift');
  const repairBuild=ROOT+'/target/platform-tool-repair-20260911/a2/backend';
  if(digest(repairBuild+'/wms-system-0.0.1-SNAPSHOT.jar')!==repairResult.jarSha256)throw Error('Previous artifact mismatch');
  const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
  const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
    .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
  const source=paths.map(p=>({path:p,sha256:digest(ROOT+'/'+p),bytes:fs.statSync(ROOT+'/'+p).size}));
  const added=source.filter(f=>!oldSource.files.some(o=>o.path===f.path)).map(f=>f.path);
  if(added.some(p=>!['src/test/scripts/platform-r3-backend-snapshot.mjs','src/test/scripts/platform-r3-database-batch.mjs'].includes(p)))throw Error('Unreviewed new input: '+added.join(','));
  const assertions=previous.expectedBusinessTests.map(e=>{
    if(JSON.stringify(testBodies(fs.readFileSync(ROOT+'/'+e.path,'utf8')))!==JSON.stringify(e.unchangedTests))throw Error('Business assertions changed: '+e.path);return e;});
  if(assertions.length!==8||assertions.reduce((n,e)=>n+e.unchangedTests.length,0)!==63)throw Error('Business coverage drift');
  const deps=previous.groups.find(g=>g.name==='dependencies'),maven=previous.groups.find(g=>g.name==='maven');
  copyRecords(ROOT,SOURCE,source);copyRecords(deps.location,REPO,deps.files);copyRecords(maven.location,MAVEN,maven.files);
  const groups=[{name:'backend-source',location:SOURCE,files:source},{name:'frozen-maven-cache',location:REPO,files:deps.files},{name:'maven-distribution',location:MAVEN,files:maven.files}].map(g=>({...g,id:aggregate(g.files)}));
  const externalTools=[process.execPath,JAVA,JAVA.replace('java.exe','javac.exe'),'C:/Windows/Microsoft.NET/Framework64/v4.0.30319/csc.exe',...['postgres','initdb','pg_ctl','pg_controldata','psql'].map(n=>PG+'/'+n+'.exe')].map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
  const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(externalTools)));
  const preserved=[...previous.groups,...previous.preserved,{name:'repair-results',location:REPAIR,files:['input-manifest.json','verification-results.json'].map(p=>({path:p,sha256:digest(REPAIR+'/'+p)}))}];
  const migrationFiles=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
  if(JSON.stringify(migrationFiles)!==JSON.stringify(previous.migrationFiles))throw Error('Migration input changed');
  writeJson(DELIVERY+'/backend-input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'R3-backend-isolated-database-acceptance-not-full-R2-or-release-candidate',
    previousInputId:previous.id,previousJarSha256:repairResult.jarSha256,groups,preserved,externalTools,changes,addedFiles:added,
    branch:execFileSync('git',['branch','--show-current'],{cwd:ROOT,encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim(),
    expectedBusinessTests:assertions,testClasspath:oldDb.testClasspath,migrationFiles,databaseHistory:oldDb.databaseHistory,
    target:{directory:RUN+'/pgdata',host:'127.0.0.1',port:55449,databases:['p3_m1_platform_release_r3','p3_m1_platform_upgrade_r3'],roles:['p3_r3_initializer','p3_r3_migrator','p3_r3_app']},
    legacyTestClassAndPropertyNamesRetained:true,frontendValidated:false});
  verify();console.log(JSON.stringify({id,sourceFiles:source.length,changedFiles:changes,addedFiles:added,businessTestsUnchanged:63,migrationFiles:migrationFiles.length,databaseStarted:false}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  if(process.argv[2]==='seal')seal();else if(process.argv[2]==='verify'){requireRoot();console.log(JSON.stringify({id:verify().id,drift:false}));}else throw Error('Only seal/verify');
}

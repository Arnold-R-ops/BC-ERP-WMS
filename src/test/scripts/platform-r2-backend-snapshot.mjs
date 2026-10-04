// Backend-only evidence capture. Never starts a service or connects to a database.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
export const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const RUN=ROOT+'/target/platform-db-acceptance-20260911/r2';
export const SOURCE=RUN+'/snapshot2/source', REPO=RUN+'/snapshot/maven-repository';
export const MAVEN=RUN+'/snapshot/apache-maven-3.9.9', BUILD=RUN+'/build/a2';
export const DELIVERY=ROOT+'/docs/releases/platform-db-acceptance-20260911';
export const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe';
export const PG='D:/ITsoftware/PostgreSQL/bin';
export const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
export const digest=p=>sha(fs.readFileSync(p));
export const readJson=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
export function writeJson(p,obj){fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(obj,null,2)+'\n',{flag:'wx'});}
export function requireRoot(){if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Wrong root or Node');}
export function files(directory){return fs.readdirSync(directory,{withFileTypes:true}).flatMap(e=>{
  const p=path.join(directory,e.name);if(e.isSymbolicLink())throw Error('No links in snapshot');return e.isDirectory()?files(p):[p];});}
const aggregate=rows=>sha(Buffer.from(rows.map(r=>r.path+'\0'+r.sha256+'\n').join('')));
function copyTree(from,to,filter=()=>true){
  return files(from).filter(filter).sort().map(p=>{const rel=path.relative(from,p).replaceAll('\\','/'),dest=path.join(to,rel);
    const before=digest(p);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(p,dest,fs.constants.COPYFILE_EXCL);
    if(before!==digest(dest)||before!==digest(p))throw Error('Copy drift');return {path:rel,bytes:fs.statSync(dest).size,sha256:before};});
}
export function verify(){
  const manifest=readJson(DELIVERY+'/backend-input-manifest-v2.json');
  for(const group of manifest.groups)for(const f of group.files)if(digest(path.join(group.location,f.path))!==f.sha256)throw Error('Sealed input drift: '+f.path);
  for(const tool of manifest.externalTools)if(digest(tool.path)!==tool.sha256)throw Error('Tool drift');
  return manifest;
}
function seal(){
  requireRoot();if(fs.existsSync(SOURCE)||fs.existsSync(RUN+'/pgdata')||fs.existsSync(DELIVERY+'/backend-input-manifest-v2.json'))throw Error('Never replace a snapshot or environment');
  const previous=readJson(DELIVERY+'/backend-input-manifest.json');
  for(const group of previous.groups)for(const f of group.files)if(digest(path.join(group.location,f.path))!==f.sha256)throw Error('Original frozen snapshot drift');
  const p2=readJson(ROOT+'/docs/releases/platform-r2-preflight-20260911/p2/input-manifest.json');
  const drift=p2.files.filter(f=>!f.path.startsWith('frontend/')&&digest(ROOT+'/'+f.path)!==f.sha256);
  if(drift.length)throw Error('Explain source drift before resealing: '+drift.map(f=>f.path).join(','));
  const oldRoot=ROOT+'/target/platform-r2-preflight-20260911/p2/backend';
  const oldResults=readJson(ROOT+'/docs/releases/platform-r2-preflight-20260911/p2/preflight-results.json');
  if(digest(oldRoot+'/wms-system-0.0.1-SNAPSHOT.jar')!==oldResults.build.jarSha256)throw Error('Preflight artifact mismatch');
  const current=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
  const paths=[...new Set(current.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
    .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
  const source=paths.map(p=>{const dest=SOURCE+'/'+p,before=digest(ROOT+'/'+p);fs.mkdirSync(path.dirname(dest),{recursive:true});
    fs.copyFileSync(ROOT+'/'+p,dest,fs.constants.COPYFILE_EXCL);if(digest(dest)!==before||digest(ROOT+'/'+p)!==before)throw Error('Source changed during seal');
    return {path:p,sha256:before,bytes:fs.statSync(dest).size};});
  const deps=previous.groups.find(g=>g.name==='frozen-maven-cache').files;
  const maven=previous.groups.find(g=>g.name==='maven-distribution').files;
  const xml=fs.readFileSync(oldRoot+'/surefire-reports/TEST-com.wms.platformverification.PlatformR2IsolationTest.xml','utf8');
  const cp=xml.match(/<property name="java.class.path" value="([^"]+)"/)[1].replaceAll('&amp;','&').replaceAll('&quot;','"');
  const jars=cp.split(';').filter(p=>p.endsWith('.jar')).map(p=>{
    const normalized=p.replaceAll('\\','/'),prefix='C:/Users/Arnold/.m2/repository/';
    if(!normalized.startsWith(prefix))throw Error('Unreviewed classpath dependency');
    const rel=normalized.slice(prefix.length),sealed=REPO+'/'+rel;
    if(digest(p)!==digest(sealed))throw Error('Dependency differs');return {path:rel,sha256:digest(sealed)};});
  const externalTools=[process.execPath,JAVA,JAVA.replace('java.exe','javac.exe'),...['postgres','initdb','pg_ctl','pg_controldata','psql'].map(n=>PG+'/'+n+'.exe')]
    .map(p=>({path:p.replaceAll('\\','/'),sha256:digest(p)}));
  const groups=[{name:'backend-source',location:SOURCE,files:source},{name:'frozen-maven-cache',location:REPO,files:deps},{name:'maven-distribution',location:MAVEN,files:maven}]
    .map(g=>({...g,id:aggregate(g.files)}));
  const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+externalTools.map(t=>t.path+'\0'+t.sha256+'\n').join('')));
  const migration=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
  if(migration.length!==94)throw Error('Migration set changed');
  writeJson(DELIVERY+'/backend-input-manifest-v2.json',{id,createdAt:new Date().toISOString(),kind:'backend-database-acceptance-only-not-full-R2',
    previousInputId:previous.id,revisionReason:'Dedicated offline Maven repository-origin declarations; no Java/product/migration/assertion changes. Initial build failed before compilation; retained.',
    branch:'2-1G',commit:execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim(),groups,externalTools,
    preflight:{id:p2.id,jarSha256:oldResults.build.jarSha256,existingBackendInputDrift:[],addedExecutionTools:source.filter(f=>!p2.files.some(p=>p.path===f.path)).map(f=>f.path)},
    testClasspath:jars,expectedBusinessTests:p2.assertions,migrationFiles:migration,
    databaseHistory:['4.71','4.72','4.85','4.86','4.87','4.88','4.89','4.90'],frontendValidated:false});
  console.log(JSON.stringify({id,sourceFiles:source.length,dependencyFiles:deps.length,testJars:jars.length,migrationFiles:migration.length,databaseStarted:false}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  if(process.argv[2]==='seal')seal();else if(process.argv[2]==='verify'){requireRoot();console.log(JSON.stringify({id:verify().id,drift:false}));}else throw Error('Only seal/verify');
}

// Authorized R7 backend-only acceptance. Preserve R2-R6 and report repair evidence.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {createReportPom} from './platform-report-runner.mjs';
export const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const RUN=ROOT+'/target/platform-db-acceptance-20260911/r7';
export const SOURCE=RUN+'/snapshot/source',REPO=RUN+'/snapshot/maven-repository';
export const MAVEN=RUN+'/snapshot/apache-maven-3.9.9',BUILD=RUN+'/build/a1';
export const DELIVERY=ROOT+'/docs/releases/platform-db-acceptance-20260911/r7';
export const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe',PG='D:/ITsoftware/PostgreSQL/bin';
const PRIOR=ROOT+'/docs/releases/platform-report-binding-repair-20260913/a2';
const PRIOR_RUN=ROOT+'/target/platform-report-binding-repair-20260913/a2';
export const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
export const digest=p=>sha(fs.readFileSync(p));
export const readJson=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
export function writeJson(p,obj){fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(obj,null,2)+'\n',{flag:'wx'});}
export function requireRoot(){if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Wrong root or Node');}
export function files(directory){return fs.readdirSync(directory,{withFileTypes:true}).flatMap(e=>{
 const p=path.join(directory,e.name);if(e.isSymbolicLink())throw Error('No links in snapshot');return e.isDirectory()?files(p):[p];}).sort();}
const aggregate=rows=>sha(Buffer.from(rows.map(r=>r.path+'\0'+r.sha256+'\n').join('')));
const records=(base,list)=>list.map(p=>({path:path.relative(base,p).replaceAll('\\','/'),sha256:digest(p),bytes:fs.statSync(p).size}));
function copyRecords(from,to,rows){for(const f of rows){const dest=to+'/'+f.path;if(digest(from+'/'+f.path)!==f.sha256)throw Error('Input drift before copy');
 fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(from+'/'+f.path,dest,fs.constants.COPYFILE_EXCL);if(digest(dest)!==f.sha256)throw Error('Copy drift');}}
function testBodies(text){
  text=text.replaceAll('\r\n','\n');
  const mask=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,s=>s.replace(/[^\r\n]/g,' '));
  return [...mask.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\([^)]*\)\s*(?:throws[^\{]+)?\{/g)].map(m=>{
    let end=m.index+m[0].length,n=1;for(;end<mask.length&&n;end++){if(mask[end]==='{')n++;if(mask[end]==='}')n--;}
    if(n)throw Error('Test parse failed');return {name:m[1],sha256:sha(Buffer.from(text.slice(m.index,end)))};});
}
export function verify(){
 const m=readJson(DELIVERY+'/backend-input-manifest.json');
 for(const g of [...m.groups,...m.preserved]){
  for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Input/evidence drift: '+f.path);
  if(g.exactInventory&&JSON.stringify(files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort())!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('Historical directory changed');
 }
 for(const t of m.externalTools)if(digest(t.path)!==t.sha256)throw Error('Tool drift');
 for(const j of m.testClasspath)if(digest(REPO+'/'+j.path)!==j.sha256)throw Error('Classpath drift');
 if(digest(m.derivedTestPom.path)!==m.derivedTestPom.sha256)throw Error('Dedicated POM drift');
 return m;
}
function seal(){
 requireRoot();if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('Never reuse R7 directory');
 const previous=readJson(PRIOR+'/input-manifest.json'),priorResult=readJson(PRIOR+'/verification-results.json');
 const r6=readJson(ROOT+'/docs/releases/platform-db-acceptance-20260911/r6/backend-input-manifest.json');
 const oldSource=previous.groups.find(g=>g.name==='source');
 const allowed=['PlatformR2Isolation.java','PlatformR2IsolationTest.java','PlatformR2SpringInitializer.java','PlatformR2DatabasePreparer.java'].map(n=>'src/test/java/com/wms/platformverification/'+n);
 const changes=oldSource.files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
 if(changes.some(p=>!allowed.includes(p)))throw Error('Unreviewed existing source drift: '+changes.join(','));
 for(const t of [...previous.tools,...r6.externalTools])if(digest(t.path)!==t.sha256)throw Error('Approved tool changed');
 if(digest(PRIOR_RUN+'/backend/wms-system-0.0.1-SNAPSHOT.jar')!==priorResult.jarSha256)throw Error('Prior artifact drift');
 const added=['src/test/scripts/platform-r7-backend-snapshot.mjs','src/test/scripts/platform-r7-database-batch.mjs','src/test/scripts/platform-r7-binding.test.mjs'];
 const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
 .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
 if(JSON.stringify(paths)!==JSON.stringify([...oldSource.files.map(f=>f.path),...added].sort()))throw Error('Unexpected source inventory');
 const source=records(ROOT,paths.map(p=>ROOT+'/'+p));
 const assertions=previous.businessTests.map(e=>{if(JSON.stringify(testBodies(fs.readFileSync(ROOT+'/'+e.path,'utf8')))!==JSON.stringify(e.tests))throw Error('Business method drift: '+e.path);return e;});
 if(assertions.length!==8||assertions.reduce((n,e)=>n+e.tests.length,0)!==63||previous.methodBodyChanges.length!==7)throw Error('Business plan mismatch');
 for(const c of previous.methodBodyChanges)if(!assertions.some(e=>e.path===c.path&&e.tests.some(t=>t.name===c.name&&t.sha256===c.afterSha256)))throw Error('Disclosed body change mismatch');
 const preserved=[...previous.groups,...previous.preserved,...[PRIOR,PRIOR_RUN].map((location,i)=>({name:'report-repair-a2-'+i,location,exactInventory:true,files:records(location,files(location))})),
 {name:'report-binding-repair-report',location:ROOT+'/docs',files:records(ROOT+'/docs',[ROOT+'/docs/PLATFORM_REPORT_BINDING_REPAIR_2026-09-13.md'])}];
 for(const g of preserved)for(const f of g.files)if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Historical evidence drift');
 const deps=previous.groups.find(g=>g.name==='dependencies'),maven=previous.groups.find(g=>g.name==='maven');
 copyRecords(ROOT,SOURCE,source);copyRecords(deps.location,REPO,deps.files);copyRecords(maven.location,MAVEN,maven.files);
 const generated=createReportPom(SOURCE,SOURCE+'/platform-acceptance-pom.xml');
 if(generated.ok!==true||generated.onlyThreeAuthorizedFieldsChanged!==true||generated.publicPomUnchanged!==true||generated.sha256!==digest(SOURCE+'/platform-acceptance-pom.xml'))throw Error('Dedicated POM mismatch');
 const derivedTestPom={path:SOURCE+'/platform-acceptance-pom.xml',sha256:generated.sha256,generation:generated};
 const groups=[{name:'backend-source',location:SOURCE,files:source},{name:'frozen-maven-cache',location:REPO,files:deps.files},{name:'maven-distribution',location:MAVEN,files:maven.files}].map(g=>({...g,id:aggregate(g.files)}));
 const migrationFiles=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
 if(JSON.stringify(migrationFiles)!==JSON.stringify(previous.migrationFiles))throw Error('Migration changes');
 const target={directory:RUN+'/pgdata',host:'127.0.0.1',port:55449,databases:['p3_m1_platform_release_r7','p3_m1_platform_upgrade_r7'],roles:['p3_r7_initializer','p3_r7_migrator','p3_r7_app']};
 const reportDirectories={landing:BUILD+'/landing-reports',database:BUILD+'/reports'};
 const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(r6.externalTools)+derivedTestPom.sha256+JSON.stringify(target)+JSON.stringify(reportDirectories)));
 writeJson(DELIVERY+'/backend-input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'R7_BACKEND_ONLY_NOT_RELEASE_CANDIDATE',previousInputId:previous.id,
 groups,preserved,externalTools:r6.externalTools,derivedTestPom,changes,addedFiles:added,target,reportDirectories,previousJarSha256:priorResult.jarSha256,
 businessTests:assertions,methodBodyChanges:previous.methodBodyChanges,original63MethodBodiesUnchanged:false,revised63MethodBodiesUnchanged:true,r5ActualExceptionKeyStillUnknown:true,
 testClasspath:r6.testClasspath,migrationFiles,databaseHistory:r6.databaseHistory,untouchedFrontend:records(ROOT,[ROOT+'/frontend/src/test/addressLength.audit.test.tsx',ROOT+'/frontend/pnpm-lock.yaml']),
 branch:execFileSync('git',['branch','--show-current'],{cwd:ROOT,encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim()});
 verify();console.log(JSON.stringify({id,sourceFiles:source.length,changes,added,revisedBusinessMethods:63,disclosedHistoricalBodyChanges:7,migrations:migrationFiles.length,databaseStarted:false}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(process.argv[2]==='seal')seal();else if(process.argv[2]==='verify'){requireRoot();console.log(JSON.stringify({id:verify().id,drift:false}));}else throw Error('Only seal/verify');
}


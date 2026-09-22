// Authorized R11 backend input. No database activity in seal/verify; historical records stay immutable.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {createReportPom} from './platform-report-runner.mjs';
import {BUDGETS,JAVA_GATES,NODE_GATES} from './platform-r11-plan.mjs';
import {AWAKE_BUDGETS} from './platform-r11-awake.mjs';
import {testBodies,expectedCases,currentBusiness,changesSince} from './platform-r9-ledger.mjs';
import {r6Plans} from './platform-r6-stages.mjs';
import {preflightCatalog} from './platform-case-catalog.mjs';
export const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const RUN=ROOT+'/target/platform-db-acceptance-20260919/r11';
export const SOURCE=RUN+'/snapshot/source',REPO=RUN+'/snapshot/maven-repository';
export const MAVEN=RUN+'/snapshot/apache-maven-3.9.9',BUILD=RUN+'/build/a1';
export const DELIVERY=ROOT+'/docs/releases/platform-db-acceptance-20260919/r11';
export const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe',PG='D:/ITsoftware/PostgreSQL/bin';
const PRIOR=ROOT+'/docs/releases/platform-db-acceptance-20260918/r10';
const REPAIR=ROOT+'/docs/releases/platform-query-diagnostics-20260919/a2';
export const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
export const digest=p=>sha(fs.readFileSync(p));
export const readJson=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
export function writeJson(p,obj){fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,JSON.stringify(obj,null,2)+'\n',{flag:'wx'});}
export function requireRoot(){if(process.cwd().replaceAll('\\','/').toLowerCase()!==ROOT.toLowerCase()||process.version!=='v24.12.0')throw Error('Wrong root or Node');}
export function files(directory){return fs.readdirSync(directory,{withFileTypes:true}).flatMap(e=>{
 const p=path.join(directory,e.name);if(e.isSymbolicLink())throw Error('No links in snapshot');return e.isDirectory()?files(p):[p];}).sort();}
const aggregate=rows=>sha(Buffer.from(rows.map(r=>r.path+'\0'+r.sha256+'\n').join('')));
const records=(base,list)=>list.map(p=>({path:path.relative(base,p).replaceAll('\\','/'),sha256:digest(p),bytes:fs.statSync(p).size}));
function copy(from,to,rows){for(const f of rows){const dest=to+'/'+f.path;if(digest(from+'/'+f.path)!==f.sha256)throw Error('Input drift');fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(from+'/'+f.path,dest,fs.constants.COPYFILE_EXCL);if(digest(dest)!==f.sha256)throw Error('Copy drift');}}
function checkGroups(groups){const checked=new Set();for(const g of groups){
 for(const f of g.files){const key=g.location+'/'+f.path+'\0'+f.sha256;if(checked.has(key))continue;if(digest(g.location+'/'+f.path)!==f.sha256)throw Error('Input/history drift: '+g.name+'/'+f.path);checked.add(key);}
 if(g.exactInventory&&JSON.stringify(files(g.location).map(p=>path.relative(g.location,p).replaceAll('\\','/')).sort())!==JSON.stringify(g.files.map(f=>f.path).sort()))throw Error('Historical inventory changed: '+g.name);
}}
export function verify(){
 const m=readJson(DELIVERY+'/backend-input-manifest.json');checkGroups([...m.groups,...m.preserved]);
 for(const f of m.groups[0].files)if(digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Workspace source drift: '+f.path);
 for(const f of m.untouchedFrontend)if(digest(ROOT+'/'+f.path)!==f.sha256)throw Error('Protected frontend drift');
 for(const t of m.externalTools)if(digest(t.path)!==t.sha256)throw Error('Tool drift');
 for(const j of m.testClasspath)if(digest(REPO+'/'+j.path)!==j.sha256)throw Error('Classpath drift');
 if(digest(m.derivedTestPom.path)!==m.derivedTestPom.sha256)throw Error('Dedicated POM drift');
 r6Plans(m);return m;
}
export function javaCatalogPreflight(root=ROOT){return preflightCatalog({root,gates:JAVA_GATES});}
function seal(){
 requireRoot();if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('Never reuse R11 directory');
 const javaCaseCatalog=javaCatalogPreflight(); // Shared verified catalog, before mkdir or copying.
 if(digest(REPAIR+'/regression-input.json')!=='ade6964bb82be5ef288d3a6682c773e717ba05f7e799f828d93cb538e2c8149e'
  ||digest(REPAIR+'/regression.json')!=='a73048991cbda444d9236f63dac3b7683aec5740c0e2c22580ab4c8d33e25487'
  ||digest(PRIOR+'/database-result.json')!=='bc50631ceff9da530d662233a8c0f3d2ae89758e0dadab8afddb444ea00580ff')throw Error('Approved input/evidence changed');
 const previous=readJson(PRIOR+'/backend-input-manifest.json'),priorResult=readJson(PRIOR+'/backend-build.json'),repair=readJson(REPAIR+'/regression-input.json');
 if(readJson(REPAIR+'/regression.json').status!=='PASSED')throw Error('Repair not verified');
 // These two old generated results were included in the repair's directory snapshot,
 // not its executable source inventory. Preserve their historical copies, never import scores.
 const historicalResults=['src/test/scripts/v45-hardening-result.json','src/test/scripts/v452-chaotic-storage-result.json'];
 const baseline=[...previous.groups[0].files.filter(f=>!f.path.startsWith('src/test/scripts/')), ...repair.sources.filter(f=>!historicalResults.includes(f.path))];
 const allowed=['PlatformR2Isolation.java','PlatformR2IsolationTest.java','PlatformR2SpringInitializer.java','PlatformR2DatabasePreparer.java'].map(n=>'src/test/java/com/wms/platformverification/'+n)
  .concat(['platform-r10-binding.test.mjs','platform-r9-fixture.test.mjs','platform-query-evidence.test.mjs','platform-query-failure.test.mjs'].map(n=>'src/test/scripts/'+n));
 const changes=baseline.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>({path:f.path,beforeSha256:f.sha256,afterSha256:digest(ROOT+'/'+f.path)}));
 if(changes.some(c=>!allowed.includes(c.path)))throw Error('Unreviewed source drift: '+changes.filter(c=>!allowed.includes(c.path)).map(c=>c.path).join(','));
 const added=['backend-snapshot.mjs','database-batch.mjs','environment.mjs','environment.test.mjs','binding.test.mjs','plan.mjs','awake.ps1','awake.mjs','awake.test.mjs'].map(n=>'src/test/scripts/platform-r11-'+n);
 const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
  .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
 if(JSON.stringify(paths)!==JSON.stringify([...baseline.map(f=>f.path),...added].sort()))throw Error('Unexpected source inventory');
 const source=records(ROOT,paths.map(p=>ROOT+'/'+p)),assertions=currentBusiness(ROOT,previous);
 if(JSON.stringify(assertions)!==JSON.stringify(previous.businessTests))throw Error('63 business method bodies changed');
 const methodRevisionHistory=previous.methodRevisionHistory,methodBodyChanges=previous.methodBodyChanges;
 const migrationFiles=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
 if(JSON.stringify(migrationFiles)!==JSON.stringify(previous.migrationFiles))throw Error('Migration changes');
 const preserved=[...previous.groups,...previous.preserved,
  ...[PRIOR,ROOT+'/docs/releases/platform-query-diagnostics-20260919/a1',REPAIR,
   ROOT+'/target/platform-query-diagnostics-20260919/a1',ROOT+'/target/platform-query-diagnostics-20260919/a2']
    .map((location,i)=>({name:'r10-and-approved-repair-'+i,location,exactInventory:true,files:records(location,files(location))})),
  {name:'approved-r11-report',location:ROOT,files:records(ROOT,[ROOT+'/docs/PLATFORM_QUERY_MARKER_REGRESSION_2026-09-19.md'])}];
 if(preserved.at(-1).files[0].sha256!=='18efccde5d8efbce588e7daa8550957b8ef75296be0593d6e87f8e3ce7a86ce1')throw Error('Approved report changed');
 checkGroups(preserved);
 const deps=previous.groups.find(g=>g.name==='frozen-maven-cache'),maven=previous.groups.find(g=>g.name==='maven-distribution');
 copy(ROOT,SOURCE,source);copy(deps.location,REPO,deps.files);copy(maven.location,MAVEN,maven.files);
 const generated=createReportPom(SOURCE,SOURCE+'/platform-acceptance-pom.xml');
 if(generated.ok!==true||generated.onlyThreeAuthorizedFieldsChanged!==true||generated.publicPomUnchanged!==true||generated.sha256!==digest(SOURCE+'/platform-acceptance-pom.xml'))throw Error('Dedicated POM mismatch');
 const derivedTestPom={path:SOURCE+'/platform-acceptance-pom.xml',sha256:generated.sha256,generation:generated};
 const groups=[{name:'backend-source',location:SOURCE,files:source},{name:'frozen-maven-cache',location:REPO,files:deps.files},{name:'maven-distribution',location:MAVEN,files:maven.files}].map(g=>({...g,id:aggregate(g.files)}));
 const target={directory:RUN+'/pgdata',host:'127.0.0.1',port:55449,databases:['p3_m1_platform_release_r11','p3_m1_platform_upgrade_r11'],roles:['p3_r11_initializer','p3_r11_migrator','p3_r11_app'],controlSchema:'p3_r11_control',exportFixtureRoot:RUN+'/export-fixture-temp'};
 const reportDirectories={landing:BUILD+'/landing-reports',database:BUILD+'/reports'};
 const externalTools=previous.externalTools;
 const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(externalTools)+derivedTestPom.sha256+JSON.stringify(target)+JSON.stringify(reportDirectories)+JSON.stringify(BUDGETS)+JSON.stringify(AWAKE_BUDGETS)+JSON.stringify(JAVA_GATES)+JSON.stringify(NODE_GATES)+JSON.stringify(javaCaseCatalog)));
 writeJson(DELIVERY+'/backend-input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'R11_BACKEND_ONLY_NOT_RELEASE_CANDIDATE',previousInputId:previous.id,approvedRepairInputId:repair.inputId,
  budgets:BUDGETS,awakeBudgets:AWAKE_BUDGETS,javaGates:JAVA_GATES,nodeGates:NODE_GATES,javaCaseCatalog,groups,preserved,externalTools,derivedTestPom,changes,addedFiles:added,target,reportDirectories,
  businessTests:assertions,methodBodyChanges,methodRevisionHistory,currentMethodBodyChanges:[],methodLedgerVersion:'R9_EIGHT_METHODS_ELEVEN_REVISIONS',
  original63MethodBodiesUnchanged:false,r10FrozenMethodBodiesUnchanged:true,r5ActualExceptionKeyStillUnknown:true,r8ExportUnderlyingExceptionStillUnknown:true,
  testClasspath:previous.testClasspath,migrationFiles,databaseHistory:previous.databaseHistory,previousJarSha256:priorResult.jarSha256,
  untouchedFrontend:records(ROOT,[ROOT+'/frontend/src/test/addressLength.audit.test.tsx',ROOT+'/frontend/pnpm-lock.yaml']),
  branch:execFileSync('git',['branch','--show-current'],{cwd:ROOT,encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim()});
 verify();console.log(JSON.stringify({id,sourceFiles:source.length,changes,added,businessMethods:63,cumulativeChangedMethods:8,revisionEntries:11,migrations:migrationFiles.length,databaseStarted:false}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(process.argv[2]==='seal')seal();else if(process.argv[2]==='verify'){requireRoot();console.log(JSON.stringify({id:verify().id,drift:false}));}else throw Error('Only seal/verify');
}

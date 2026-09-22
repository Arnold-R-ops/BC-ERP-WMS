// Authorized R9 backend input. No database activity in seal/verify; historical records stay immutable.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {createReportPom} from './platform-report-runner.mjs';
import {BUDGETS,JAVA_GATES,NODE_GATES} from './platform-r9-plan.mjs';
import {testBodies,expectedCases,currentBusiness,changesSince} from './platform-r9-ledger.mjs';
import {r6Plans} from './platform-r6-stages.mjs';
import {preflightCatalog} from './platform-case-catalog.mjs';
export const ROOT='D:/ERP_WMS/BC warehouse/2G';
export const RUN=ROOT+'/target/platform-db-acceptance-20260918/r9';
export const SOURCE=RUN+'/snapshot/source',REPO=RUN+'/snapshot/maven-repository';
export const MAVEN=RUN+'/snapshot/apache-maven-3.9.9',BUILD=RUN+'/build/a1';
export const DELIVERY=ROOT+'/docs/releases/platform-db-acceptance-20260918/r9';
export const JAVA='C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin/java.exe',PG='D:/ITsoftware/PostgreSQL/bin';
const PRIOR=ROOT+'/docs/releases/platform-export-fixture-repair-20260918/a3',PRIOR_RUN=ROOT+'/target/platform-export-fixture-repair-20260918/a3';
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
 requireRoot();if(fs.existsSync(RUN)||fs.existsSync(DELIVERY))throw Error('Never reuse R9 directory');
 const javaCaseCatalog=javaCatalogPreflight(); // Before mkdir, input copying or derived POM creation.
 if(digest(PRIOR+'/input-manifest.json')!=='a26173d00c7a1705e31d0ba4621002c38551d1f09570877b46a27fc86a9a4e74'
  ||digest(PRIOR+'/verification-results.json')!=='9d299cf9191944bf9e80ea902d475a364073e2a8fa9b8a7e1d730e2b029de99a')throw Error('Approved a3 evidence changed');
 const previous=readJson(PRIOR+'/input-manifest.json'),priorResult=readJson(PRIOR+'/verification-results.json');
 const r8=readJson(ROOT+'/docs/releases/platform-db-acceptance-20260916/r8/backend-input-manifest.json');
 const allowed=['PlatformR2Isolation.java','PlatformR2IsolationTest.java','PlatformR2SpringInitializer.java','PlatformR2DatabasePreparer.java'].map(n=>'src/test/java/com/wms/platformverification/'+n)
  .concat(['src/test/java/com/wms/system/tenant/security/TenantSecuritySpringPostgresTest.java',
   'src/test/java/com/wms/system/platform/service/PlatformAccessGrantRoleRaceIntegrationTest.java','src/test/java/com/wms/system/platform/service/PlatformExportTestConfiguration.java'])
  .concat(['platform-fixture-isolation.test.mjs','platform-export-fixture.test.mjs','platform-r6-stages.mjs','platform-report-runner.mjs','platform-r8-binding.test.mjs'].map(n=>'src/test/scripts/'+n));
 const changes=previous.groups[0].files.filter(f=>digest(ROOT+'/'+f.path)!==f.sha256).map(f=>f.path);
 if(changes.some(p=>!allowed.includes(p)))throw Error('Unreviewed source drift: '+changes.join(','));
 const added=['PlatformFixtureWorkers.java','PlatformFixtureWorkersTest.java'].map(n=>'src/test/java/com/wms/platformverification/'+n)
  .concat(['SpringSecurityFixture.java','SpringSecurityFixtureTest.java'].map(n=>'src/test/java/com/wms/system/tenant/security/'+n))
  .concat(['backend-snapshot.mjs','database-batch.mjs','environment.mjs','environment.test.mjs','binding.test.mjs','plan.mjs','fixture.test.mjs','ledger.mjs','export-evidence.mjs'].map(n=>'src/test/scripts/platform-r9-'+n));
 const listed=execFileSync('git',['--no-optional-locks','ls-files','-c','-o','--exclude-standard','-z'],{cwd:ROOT,encoding:'utf8',stdio:['ignore','pipe','pipe']});
 const paths=[...new Set(listed.split('\0'))].filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/'))
  .filter(p=>!/(^|\/)(private|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
 if(JSON.stringify(paths)!==JSON.stringify([...previous.groups[0].files.map(f=>f.path),...added].sort()))throw Error('Unexpected source inventory');
 const source=records(ROOT,paths.map(p=>ROOT+'/'+p));
 const assertions=currentBusiness(ROOT,previous),revisions=changesSince(previous,assertions);
 const approved={realPlatformExportRequiresSuspensionReasonAndRechecksDownloadAndActor:'c098a92e29811f0bb094ad41d2b99d51f090721b7581ba33642218e2e6eb817a',
  alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate:'e661fb0a7e94a96682656eeaf4286b823251b3d4f61b543b4250e13c7016d605',
  provisionAndLoginHandoffPreserveErpAndCannotReusePrePauseCode:'e0372adbfed26fedd3eb127696751ad2636be3b3de5d13a1adffd91023b12a4b'};
 if(revisions.length!==3||revisions.some(c=>approved[c.name]!==c.afterSha256))throw Error('Unapproved body change');
 const methodRevisionHistory=[...previous.historicalMethodBodyChanges,...previous.methodBodyChanges,...revisions];
 const methodBodyChanges=[...new Map(methodRevisionHistory.map(c=>[c.path+'#'+c.name,c])).values()];
 const migrationFiles=source.filter(f=>f.path.startsWith('src/main/resources/db/migration/')&&f.path.endsWith('.sql'));
 if(JSON.stringify(migrationFiles)!==JSON.stringify(previous.migrationFiles))throw Error('Migration changes');
 const preserved=[...previous.groups,...previous.preserved,...[PRIOR,PRIOR_RUN].map((location,i)=>({name:'a3-complete-'+i,location,exactInventory:true,files:records(location,files(location))})),
  {name:'approved-export-repair-report',location:ROOT,files:records(ROOT,[ROOT+'/docs/PLATFORM_EXPORT_FIXTURE_REPAIR_2026-09-18.md'])}];
 if(preserved.at(-1).files[0].sha256!=='34b6f0ff772dff7a775a686e13f40025a0fedb3e9f5229281d01a063c186e3d6')throw Error('Approved report changed');
 checkGroups(preserved);
 const deps=previous.groups.find(g=>g.name==='dependencies'),maven=previous.groups.find(g=>g.name==='maven');
 copy(ROOT,SOURCE,source);copy(deps.location,REPO,deps.files);copy(maven.location,MAVEN,maven.files);
 const generated=createReportPom(SOURCE,SOURCE+'/platform-acceptance-pom.xml');
 if(generated.ok!==true||generated.onlyThreeAuthorizedFieldsChanged!==true||generated.publicPomUnchanged!==true||generated.sha256!==digest(SOURCE+'/platform-acceptance-pom.xml'))throw Error('Dedicated POM mismatch');
 const derivedTestPom={path:SOURCE+'/platform-acceptance-pom.xml',sha256:generated.sha256,generation:generated};
 const groups=[{name:'backend-source',location:SOURCE,files:source},{name:'frozen-maven-cache',location:REPO,files:deps.files},{name:'maven-distribution',location:MAVEN,files:maven.files}].map(g=>({...g,id:aggregate(g.files)}));
 const target={directory:RUN+'/pgdata',host:'127.0.0.1',port:55449,databases:['p3_m1_platform_release_r9','p3_m1_platform_upgrade_r9'],roles:['p3_r9_initializer','p3_r9_migrator','p3_r9_app']};
 const reportDirectories={landing:BUILD+'/landing-reports',database:BUILD+'/reports'};
 const externalTools=[...new Map([...r8.externalTools,...previous.tools].map(t=>[t.path,t])).values()];
 const id=sha(Buffer.from(groups.map(g=>g.name+'\0'+g.id+'\n').join('')+JSON.stringify(externalTools)+derivedTestPom.sha256+JSON.stringify(target)+JSON.stringify(reportDirectories)+JSON.stringify(BUDGETS)+JSON.stringify(JAVA_GATES)+JSON.stringify(NODE_GATES)));
 writeJson(DELIVERY+'/backend-input-manifest.json',{id,createdAt:new Date().toISOString(),kind:'R9_BACKEND_ONLY_NOT_RELEASE_CANDIDATE',previousInputId:previous.id,
  budgets:BUDGETS,javaGates:JAVA_GATES,nodeGates:NODE_GATES,javaCaseCatalog,groups,preserved,externalTools,derivedTestPom,changes,addedFiles:added,target,reportDirectories,
  businessTests:assertions,methodBodyChanges,methodRevisionHistory,currentMethodBodyChanges:revisions,methodLedgerVersion:'R9_EIGHT_METHODS_ELEVEN_REVISIONS',
  original63MethodBodiesUnchanged:false,r8Revised63MethodBodiesUnchanged:false,r9FrozenMethodBodiesUnchanged:true,r5ActualExceptionKeyStillUnknown:true,r8ExportUnderlyingExceptionStillUnknown:true,
  newNoDatabaseMethods:added.filter(p=>p.endsWith('Test.java')).map(p=>({path:p,tests:testBodies(fs.readFileSync(SOURCE+'/'+p,'utf8'))})),
  testClasspath:r8.testClasspath,migrationFiles,databaseHistory:r8.databaseHistory,previousJarSha256:priorResult.jarSha256,
  untouchedFrontend:records(ROOT,[ROOT+'/frontend/src/test/addressLength.audit.test.tsx',ROOT+'/frontend/pnpm-lock.yaml']),
  branch:execFileSync('git',['branch','--show-current'],{cwd:ROOT,encoding:'utf8'}).trim(),commit:execFileSync('git',['rev-parse','HEAD'],{cwd:ROOT,encoding:'utf8'}).trim()});
 verify();console.log(JSON.stringify({id,sourceFiles:source.length,changes,added,businessMethods:63,cumulativeChangedMethods:8,revisionEntries:11,migrations:migrationFiles.length,databaseStarted:false}));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(process.argv[2]==='seal')seal();else if(process.argv[2]==='verify'){requireRoot();console.log(JSON.stringify({id:verify().id,drift:false}));}else throw Error('Only seal/verify');
}

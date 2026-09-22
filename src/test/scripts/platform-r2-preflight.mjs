// No database or service API. Capture/verify compiler inputs and sanitize existing build reports only.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
const root='D:/ERP_WMS/BC warehouse/2G';
if(process.cwd().replaceAll('\\','/').toLowerCase()!==root.toLowerCase() || process.version!=='v24.12.0') throw new Error('Fixed root/Node required');
const attempt=process.argv[3]??'p1';
if(!['p1','p2'].includes(attempt))throw new Error('Unapproved preflight attempt');
const run=path.join(root,'target/platform-r2-preflight-20260911',attempt);
const delivery=path.join(root,'docs/releases/platform-r2-preflight-20260911',attempt==='p1'?'':attempt);
const read=p=>fs.readFileSync(path.join(root,p));
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const json=p=>JSON.parse(read(p));
const git=(...args)=>execFileSync('git',['--no-optional-locks','-c','core.quotepath=false',...args],{cwd:root,encoding:'utf8',stdio:['ignore','pipe','pipe']});
const write=(name,value)=>fs.writeFileSync(path.join(delivery,name),JSON.stringify(value,null,2)+'\n',{flag:'wx'});
const previous=json('docs/releases/platform-baseline-20260911/source-manifest.json');
const testPaths=[
  'platform/service/PlatformSessionPostgresTest.java','platform/service/PlatformInvitationSessionPostgresTest.java',
  'platform/service/PlatformCompanyDataServiceIntegrationTest.java','platform/service/PlatformAdminInvitationMfaTransactionIntegrationTest.java',
  'platform/service/PlatformAccessGrantRoleRaceIntegrationTest.java','tenant/security/TenantSecurityPostgresTest.java',
  'tenant/security/TenantSecurityCommandsPostgresTest.java','tenant/security/TenantSecuritySpringPostgresTest.java',
].map(p=>'src/test/java/com/wms/system/'+p);
function bodies(text) {
  text=text.replaceAll('\r\n','\n');
  const masked=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,s=>s.replace(/[^\r\n]/g,' '));
  const result=[];
  for(const match of masked.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\([^)]*\)\s*(?:throws[^\{]+)?\{/g)) {
    let end=match.index+match[0].length,depth=1;
    for(;end<masked.length && depth;end++){if(masked[end]==='{')depth++;if(masked[end]==='}')depth--;}
    if(depth)throw new Error('Cannot balance test method');
    result.push({name:match[1],sha256:sha(Buffer.from(text.slice(match.index,end)))});
  }
  return result;
}
function protectedChanges() {
  return previous.files.filter(f=>f.path.startsWith('src/main/') || f.path.startsWith('frontend/') || f.path==='pom.xml')
    .filter(f=>sha(read(f.path))!==f.sha256).map(f=>f.path);
}
if(process.argv[2]==='capture') {
  if(fs.existsSync(run)||fs.existsSync(delivery))throw new Error('Never overwrite preflight evidence');
  if(protectedChanges().length)throw new Error('Product/frontend/shared configuration drift requires review');
  const assertions=testPaths.map(p=>{
    const before=bodies(read(path.join(previous.sourceRoot,p)).toString('utf8')),after=bodies(read(p).toString('utf8'));
    if(!before.length || JSON.stringify(before)!==JSON.stringify(after))throw new Error('Business test body changed: '+p);
    return {path:p,unchangedTests:after};
  });
  const paths=[...new Set(git('ls-files','-c','-o','--exclude-standard','-z').split('\0'))]
    .filter(p=>p==='pom.xml'||p.startsWith('src/main/')||p.startsWith('src/test/')||['frontend/package.json','frontend/pnpm-lock.yaml','frontend/src/test/addressLength.audit.test.tsx'].includes(p))
    .filter(p=>!/(^|\/)(private|node_modules|target|\.env[^/]*)(\/|$)|application-local\.|\.(log|jar|class|p12|pfx|jks|pem|key)$/i.test(p)).sort();
  const records=paths.map(p=>({path:p,sha256:sha(read(p)),bytes:read(p).length}));
  const id=sha(Buffer.from(records.map(f=>`${f.path}\0${f.sha256}\n`).join('')));
  fs.mkdirSync(run,{recursive:true}); fs.mkdirSync(delivery,{recursive:true});
  write('input-manifest.json',{createdAt:new Date().toISOString(),id,kind:'preflight-compiler-input-fingerprint-not-formal-R2-source-snapshot',
    branch:git('branch','--show-current').trim(),commit:git('rev-parse','HEAD').trim(),files:records,assertions,
    formalSnapshotBlockedBy:'Unchanged addressLength.audit.test.tsx TS2769',databaseAuthorized:false,productChanges:[]});
  write('toolchain.json',json('target/platform-validation-tools/pnpm-11.16.0/receipt.json'));
  console.log(JSON.stringify({id,files:records.length,unchangedBusinessTests:assertions.reduce((n,f)=>n+f.unchangedTests.length,0)}));
} else if(process.argv[2]==='verify') {
  const manifest=JSON.parse(fs.readFileSync(path.join(delivery,'input-manifest.json')));
  const drift=manifest.files.filter(f=>sha(read(f.path))!==f.sha256).map(f=>f.path);
  if(drift.length||protectedChanges().length)throw new Error('Compiler input/protected source drift: '+drift.join(','));
  console.log(JSON.stringify({id:manifest.id,inputDrift:[],productChanges:[],formalSnapshotCreated:false}));
} else throw new Error('Only capture or verify; never starts services');

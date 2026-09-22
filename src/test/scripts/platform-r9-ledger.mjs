// Source-only inventory. Imported by the actual sealing and gate entry points; no database calls.
import fs from 'node:fs';
import crypto from 'node:crypto';
import {resolveCases} from './platform-case-catalog.mjs';
const sha=s=>crypto.createHash('sha256').update(s).digest('hex');
export function testBodies(text){
 text=text.replaceAll('\r\n','\n');
 const mask=text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,s=>s.replace(/[^\r\n]/g,' '));
 return [...mask.matchAll(/@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*void\s+(\w+)\s*\([^)]*\)\s*(?:throws[^\{]+)?\{/g)].map(m=>{
  let end=m.index+m[0].length,n=1;for(;end<mask.length&&n;end++){if(mask[end]==='{')n++;if(mask[end]==='}')n--;}
  if(n)throw Error('Test method parse failed');return {name:m[1],sha256:sha(text.slice(m.index,end))};
 });
}
export function expectedCases(text,options){return resolveCases(text,options);}
export function currentBusiness(root,prior){return prior.businessTests.map(e=>({path:e.path,tests:testBodies(fs.readFileSync(root+'/'+e.path,'utf8'))}));}
export function changesSince(prior,current){return current.flatMap(e=>{
 const old=prior.businessTests.find(p=>p.path===e.path);
 if(JSON.stringify(old.tests.map(t=>t.name))!==JSON.stringify(e.tests.map(t=>t.name)))throw Error('Business coverage changed');
 return e.tests.filter(t=>old.tests.find(x=>x.name===t.name).sha256!==t.sha256).map(t=>({path:e.path,name:t.name,beforeSha256:old.tests.find(x=>x.name===t.name).sha256,afterSha256:t.sha256,kind:'R9_AUTHORIZED_ISOLATION_BASELINE_AND_CLOSEOUT'}));
});}

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {ROOT,RUN,FRONTEND,PORTS} from './platform-b1-binding.mjs';

export function classifyRequest(method,raw,host,allowedAssets){
 if(host!=='127.0.0.1:15174'||typeof raw!=='string'||/[\\\x00-\x20]/.test(raw)||/%(?:2e|2f|5c|00)/i.test(raw))return 'REJECT';
 const p=raw.split('?')[0];
 if(p.includes('..')||p.endsWith('.map'))return 'REJECT';
 if(p==='/__isolation/health'&&method==='GET')return 'HEALTH';
 if(p.startsWith('/api/platform/auth/')||p.startsWith('/api/platform/admin')||p.startsWith('/api/platform/audit')
   ||(p==='/api/platform/access-grants/me'&&method==='GET'))return ['GET','POST','PUT','PATCH','DELETE'].includes(method)?'API':'REJECT';
 if(['GET','HEAD'].includes(method)&&(p==='/platform.html'||allowedAssets.has(p)))return 'STATIC';
 return 'REJECT';
}
export function platformAssets(dist){
 const allowed=new Set(),queue=['platform.html'];
 // Frozen Vite outputs refer to exact generated file names. Only follow resources referenced by platform entry graph.
 const known=fs.readdirSync(dist+'/assets').filter(n=>!n.endsWith('.map'));
 while(queue.length){const relative=queue.shift(),text=fs.readFileSync(dist+'/'+relative,'utf8');
  for(const name of known)if(text.includes(name)&&!allowed.has('/assets/'+name)){allowed.add('/assets/'+name);if(/\.(js|css)$/.test(name))queue.push('assets/'+name);}
 }
 return allowed;
}
async function main(){
 if(process.argv.length!==2||process.cwd().replaceAll('\\','/')!==ROOT)throw Error('B1_PROXY_BINDING');
 const {instance}=JSON.parse(fs.readFileSync(RUN+'/runtime-public.json','utf8'));
 if(!/^[a-f0-9]{32}$/.test(instance))throw Error('B1_PROXY_INSTANCE');
 const allowed=platformAssets(FRONTEND+'/dist');
 const sockets=new Set();
 const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2'};
 const server=http.createServer((req,res)=>{
  res.setHeader('Cache-Control','no-store');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('X-Content-Type-Options','nosniff');
  const mode=classifyRequest(req.method,req.url,req.headers.host,allowed);
  if(mode==='REJECT'){res.writeHead(404).end();return;}
  if(mode==='HEALTH'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({ready:true,port:PORTS.frontend,upstream:PORTS.backend,instance}));return;}
  if(mode==='STATIC'){
   try{const p=req.url.split('?')[0],data=fs.readFileSync(FRONTEND+'/dist'+p);res.setHeader('Content-Type',mime[path.extname(p)]??'application/octet-stream');res.end(req.method==='HEAD'?undefined:data);}
   catch{res.writeHead(404).end();}return;
  }
  const headers={...req.headers,host:'127.0.0.1:18092'};delete headers.origin;
  const upstream=http.request({hostname:'127.0.0.1',port:18092,path:req.url,method:req.method,headers,timeout:30000},r=>{res.writeHead(r.statusCode??502,{...r.headers,'cache-control':'no-store','referrer-policy':'no-referrer'});r.pipe(res);});
  upstream.on('timeout',()=>upstream.destroy());upstream.on('error',()=>{if(!res.headersSent)res.writeHead(502);res.end();});req.pipe(upstream);
 });
 server.on('connection',s=>{sockets.add(s);s.on('close',()=>sockets.delete(s));});
 server.on('error',e=>{console.error('B1_PROXY_ERROR '+(e.code??e.name));process.exitCode=1;clearInterval(timer);});
 server.listen(PORTS.frontend,'127.0.0.1',()=>console.log('B1_PROXY_READY'));
 const timer=setInterval(()=>{
  if(!fs.existsSync(RUN+'/proxy.stop'))return;
  if(fs.readFileSync(RUN+'/proxy.stop','utf8')!==instance){console.error('B1_PROXY_STOP_IDENTITY');return;}
  clearInterval(timer);server.close(()=>console.log('B1_PROXY_CLOSED'));server.closeIdleConnections();
 },200);
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))main().catch(e=>{console.error('B1_PROXY_FAILURE '+(e.code??e.name));process.exitCode=1;});

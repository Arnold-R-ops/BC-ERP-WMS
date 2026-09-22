// Fixed loopback preview/proxy. No env-based upstream and no fallback to port 8080.
import http from 'node:http';
import { appendFile, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { shouldDropLogout } from './platform-session-browser-logout-fault.mjs';

const root = 'D:/ERP_WMS/BC warehouse/2G';
const staticRoot = path.join(root, 'target/platform-browser-scope-frontend-dist');
const run = path.join(root, 'target/platform-session-browser');
const port = 15173;
const upstreamPort = 18091;
const banner = '<aside style="position:fixed;bottom:4px;right:4px;z-index:99999;background:#fff3cd;color:#473700;padding:6px 10px;font:12px sans-serif;pointer-events:none">隔离验收 V4_90 · 同浏览器退出 · 15173 → 18091 → 55439 / p3_m1_platform_browser · 仅合成数据</aside>';
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };

const server = http.createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  if (!['127.0.0.1:15173', 'localhost:15173'].includes(req.headers.host)) { res.writeHead(400).end(); return; }
  const url = new URL(req.url, 'http://127.0.0.1:15173');
  if (req.method === 'POST' && url.pathname === '/api/platform/auth/logout') {
    let control;
    try { control = JSON.parse(await readFile(path.join(run, 'logout-transport-fault.json'), 'utf8')); }
    catch (error) {
      if (error.code !== 'ENOENT') { res.writeHead(503).end(); return; }
    }
    if (shouldDropLogout({ method: req.method, pathname: url.pathname, authorization: req.headers.authorization }, control)) {
      // Intercept before constructing any upstream request. Never log request headers/body or claims.
      try {
        await appendFile(path.join(run, 'logout-transport-fault-events.jsonl'), JSON.stringify({
          at: new Date().toISOString(), scenario: 'drop-logout-before-upstream', method: 'POST',
          path: '/api/platform/auth/logout', forwarded: false,
          source: req.headers['x-isolation-fault-canary'] === '1' ? 'invalid-signature-canary' : 'manual-browser',
        }) + '\n', 'utf8');
      } catch { console.error('ISOLATED_FAULT_EVIDENCE_WRITE_FAILED'); }
      req.socket.destroy(); return;
    }
  }
  if (url.pathname.startsWith('/api/platform/') || url.pathname === '/__isolation/health') {
    const headers = { ...req.headers, host: '127.0.0.1:18091' };
    // Browser -> proxy is same-origin. Do not introduce a cross-origin request to the internal hop.
    delete headers.origin;
    const request = http.request({ hostname: '127.0.0.1', port: upstreamPort, path: url.pathname + url.search,
      method: req.method, headers, timeout: 60_000 }, response => {
      res.writeHead(response.statusCode ?? 502, { ...response.headers, 'cache-control': 'no-store' });
      response.pipe(res);
    });
    request.on('timeout', () => request.destroy());
    request.on('error', () => { if (!res.headersSent) res.writeHead(502, { 'content-type': 'application/json' }); res.end('{"errorKey":"ISOLATED_BACKEND_UNAVAILABLE"}'); });
    req.pipe(request); return;
  }
  if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405).end(); return; }
  const isEntry = url.pathname === '/' || url.pathname === '/platform.html';
  if (!isEntry && !/^\/assets\/[A-Za-z0-9_.-]+$/.test(url.pathname)) { res.writeHead(404).end(); return; }
  if (url.pathname.endsWith('.map')) { res.writeHead(404).end(); return; }
  try {
    const file = isEntry ? 'platform.html' : url.pathname.slice(1);
    let body = await readFile(path.join(staticRoot, file));
    if (isEntry) body = Buffer.from(body.toString('utf8').replace('</body>', banner + '</body>'));
    res.writeHead(200, { 'content-type': mime[path.extname(file)] ?? 'application/octet-stream' });
    res.end(req.method === 'HEAD' ? undefined : body);
  } catch { res.writeHead(404).end(); }
});

server.on('error', error => { console.error('ISOLATION_PREVIEW_FAILED', error.code); process.exitCode = 1; });
server.listen(port, '127.0.0.1', async () => {
  await writeFile(path.join(run, 'frontend.pid'), String(process.pid), 'utf8');
  console.log('ISOLATION_PREVIEW_READY 127.0.0.1:15173 -> 127.0.0.1:18091 only');
});

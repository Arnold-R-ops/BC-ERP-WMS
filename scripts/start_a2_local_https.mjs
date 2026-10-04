// Isolated A2 only. No tunnel, trust-store change or automatic browser launch.
// Keep the existing HTTP Vite server running; add HTTPS and a certificate-only
// bootstrap listener on this computer's explicit RFC1918 address.
import { readFileSync } from 'node:fs';
import { createServer as createHttpServer } from 'node:http';
import { networkInterfaces } from 'node:os';
import { X509Certificate } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { createServer as createViteServer } from '../frontend/node_modules/vite/dist/node/index.js';

const root = fileURLToPath(new URL('../', import.meta.url));
const frontend = resolve(root, 'frontend');
const tlsDirectory = resolve(root, 'target/a2-local-tls');
const metadata = JSON.parse(readFileSync(resolve(tlsDirectory, 'metadata.json'), 'utf8'));
const host = metadata.host;
const parts = typeof host === 'string' ? host.split('.').map(Number) : [];
const privateIp = parts.length === 4 && parts.every((value) => Number.isInteger(value) && value >= 0 && value <= 255)
  && (parts[0] === 10 || parts[0] === 192 && parts[1] === 168 || parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31);
if (!privateIp || !Object.values(networkInterfaces()).flat().some((entry) => entry?.address === host)) {
  throw new Error('Certificate host must be an explicit RFC1918 IPv4 address currently assigned to this computer');
}
const caDer = readFileSync(resolve(tlsDirectory, 'a2-test-ca.cer'));
const certificate = readFileSync(resolve(tlsDirectory, 'server.pem'));
const authority = new X509Certificate(caDer);
const leaf = new X509Certificate(certificate);
if (Date.parse(leaf.validTo) <= Date.now() || !leaf.checkIP(host) || !leaf.verify(authority.publicKey)
    || metadata.sha256 !== authority.fingerprint256.replaceAll(':', '')) {
  throw new Error('Test certificate expired or does not match the configured LAN address / authority');
}
// Only a fixed public certificate is downloadable. Never serve this directory.
const escape = (text) => String(text).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const httpsUrl = `https://${host}:5443/mobile/stocktake`;
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>2G A2 手机 HTTPS 测试入口</title><style>body{font:17px/1.7 system-ui;max-width:760px;margin:32px auto;padding:0 20px;color:#203c35}a{color:#006d66}code{overflow-wrap:anywhere}li{margin:14px 0}.notice{padding:16px;background:#fff6df;border-radius:12px}</style>
<h1>手机实时扫码测试</h1><p class="notice">这是临时局域网测试证书，不是正式上线证书。安装根证书会改变本机信任设置，请确认用途和证书指纹后再自行安装。测试结束后请删除该证书。无需安装 VPN、管理描述文件或 App。</p>
<p>证书名称：<strong>${escape(metadata.caName)}</strong><br>到期时间：${escape(metadata.expiresAt)}<br>SHA-256：<code>${escape(authority.fingerprint256)}</code></p>
<ol><li>用 iPhone Safari 打开本页，<a href="/a2-test-ca.cer">下载测试公开证书</a>；仅当你同意安装时允许下载。</li>
<li>进入“设置 → 通用 → VPN 与设备管理”（部分版本显示“已下载描述文件”），核对上述名称后安装。此文件只有一个根证书，不含设备管理权限。</li>
<li>进入“设置 → 通用 → 关于本机 → 证书信任设置”，仅对上述测试证书开启完全信任。可参考 <a href="https://support.apple.com/zh-cn/102390">Apple 官方说明</a>。</li>
<li><a href="${httpsUrl}">打开 HTTPS 盘点页</a>，重新登录并选择 A2 任务，点击扫码，允许相机访问。HTTPS 与旧 HTTP 登录状态相互独立。</li>
<li>测试完成后，回到“VPN 与设备管理”移除该测试证书。正式上线使用公开受信任证书，租户不需要此安装步骤。</li></ol>
<p>摄像头画面在手机本地识别，不上传。这个证书下载入口不接受账号、密码或业务请求。</p></html>`;
const bootstrap = createHttpServer((request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return; }
  const path = new URL(request.url, `http://${host}:5442`).pathname;
  if (path === '/a2-test-ca.cer') {
    response.setHeader('Content-Type', 'application/x-x509-ca-cert');
    response.setHeader('Content-Disposition', 'attachment; filename="a2-test-ca.cer"');
    response.end(request.method === 'HEAD' ? undefined : caDer);
  } else if (path === '/') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end(request.method === 'HEAD' ? undefined : html);
  } else { response.writeHead(404); response.end(); }
});
// Do not inherit root cwd: both config and .env resolution belong to frontend.
process.chdir(frontend);
let vite;
try {
  vite = await createViteServer({
    root: frontend,
    configFile: resolve(frontend, 'vite.config.ts'),
    cacheDir: resolve(frontend, 'node_modules/.vite-a2-https'),
    server: {
      host, port: 5443, strictPort: true,
      https: { key: readFileSync(resolve(tlsDirectory, 'server.key')), cert: certificate },
      fs: { strict: true, allow: [frontend], deny: ['.env', '.env.*', '*.{crt,pem,key}', '**/.git/**', '**/target/**'] },
    },
  });
  await vite.listen();
  await new Promise((done, reject) => { bootstrap.once('error', reject); bootstrap.listen(5442, host, done); });
  console.log(`A2 HTTPS ready: ${httpsUrl}\nCertificate instructions: http://${host}:5442/\nRoot SHA-256: ${authority.fingerprint256}\nNo device trust settings were modified.`);
} catch (error) {
  bootstrap.close();
  await vite?.close();
  throw error;
}
let closing = false;
async function close() {
  if (closing) return;
  closing = true;
  bootstrap.close();
  await vite.close();
}
process.on('SIGINT', close);
process.on('SIGTERM', close);

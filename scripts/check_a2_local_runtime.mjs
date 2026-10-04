// Read-only local A2 smoke check. No login, business writes or device trust changes.
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { X509Certificate } from 'node:crypto';
import { networkInterfaces } from 'node:os';

const root = new URL('../', import.meta.url);
const base = 'http://127.0.0.1:5173';
let checks = 0;
for (const [path, marker] of [
  ['/mobile/stocktake', '/src/main.tsx'],
  ['/platform.html', '/src/platform-main.tsx'],
  ['/api/auth/health', 'UP'],
  ['/a2-mobile-barcodes.html', 'RCB-9EA067CFCEBF43CC'],
  ['/src/pages/mobile/MobileStocktakePage.tsx', 'hasUnsavedCount'],
  ['/src/pages/stocktake/StocktakeTaskDrawer.tsx', 'onFinish'],
  ['/src/pages/mobile/ScanInput.tsx', 'LiveScanDialog'],
  ['/src/pages/mobile/liveBarcodeScanner.ts', 'decode'],
]) {
  const response = await fetch(base + path, { signal: AbortSignal.timeout(20000) });
  assert.equal(response.status, 200, path);
  const body = await response.text();
  assert(body.includes(marker), `Expected current application content: ${path}`);
  if (path.endsWith('/health')) assert.equal(JSON.parse(body).status, 'UP');
  console.log(JSON.stringify({ path, status: response.status, contentChecked: true }));
  checks++;
}

const tls = new URL('target/a2-local-tls/', root);
const metadata = JSON.parse(readFileSync(new URL('metadata.json', tls), 'utf8'));
const ca = new X509Certificate(readFileSync(new URL('a2-test-ca.cer', tls)));
const leaf = new X509Certificate(readFileSync(new URL('server.pem', tls)));
assert.equal(ca.fingerprint256.replaceAll(':', ''), metadata.sha256);
assert(leaf.verify(ca.publicKey));
assert(leaf.checkIP(metadata.host));
const certificateTimeValid = Date.parse(leaf.validFrom) <= Date.now()
  && Date.parse(leaf.validTo) > Date.now()
  && Date.parse(ca.validFrom) <= Date.now() && Date.parse(ca.validTo) > Date.now();
const certificateHostAssigned = Object.values(networkInterfaces()).flat()
  .some((entry) => entry?.address === metadata.host);
console.log(JSON.stringify({ certificateHost: metadata.host, certificateTimeValid,
  certificateHostAssigned, certificateExpiresAt: leaf.validTo,
  phoneHttpsAcceptance: 'PENDING_USER_EVIDENCE', trustStoreModified: false }));

const distFiles = readdirSync(new URL('frontend/dist/', root), { recursive: true }).map(String);
assert(!distFiles.some((name) => /\.(map|key|pem|cer|p12|pfx)$/i.test(name)));
assert(!distFiles.some((name) => /a2-(mobile-barcodes|scan-diagnostic|live-scan-diagnostic)\.html$/i.test(name)));
assert(distFiles.includes('index.html') && distFiles.includes('platform.html'));
console.log(JSON.stringify({ result: 'PASS_LOCAL_ONLY', httpContentChecks: checks,
  releaseBoundaryChecked: true, phoneHttpsAccepted: false }));

// Read-only check of the existing A2 LAN server. Does not install/trust certificates.
import assert from 'node:assert/strict';
import http from 'node:http';
import https from 'node:https';
import { readFileSync } from 'node:fs';
import { X509Certificate } from 'node:crypto';
import { networkInterfaces } from 'node:os';
import { fileURLToPath } from 'node:url';

const tls = new URL('../target/a2-local-tls/', import.meta.url);
const metadata = JSON.parse(readFileSync(new URL('metadata.json', tls), 'utf8'));
const ca = readFileSync(new URL('a2-test-ca.pem', tls));
const authority = new X509Certificate(ca);
assert.equal(authority.fingerprint256.replaceAll(':', ''), metadata.sha256);
assert(Object.values(networkInterfaces()).flat().some((entry) => entry?.address === metadata.host),
  'The certificate LAN address is not currently assigned to this computer');
const secureBase = `https://${metadata.host}:5443`;
const bootstrapBase = `http://${metadata.host}:5442`;

function get(url) {
  return new Promise((resolve, reject) => {
    const encrypted = url.startsWith('https:');
    const request = (encrypted ? https : http).get(url, { ca, rejectUnauthorized: true }, (response) => {
      const verified = response.socket.authorized === true;
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('error', reject);
      response.on('end', () => resolve({ status: response.statusCode, verified,
        body: Buffer.concat(chunks), headers: response.headers }));
    });
    request.setTimeout(20000, () => request.destroy(new Error('A2 request timed out')));
    request.on('error', reject);
  });
}

let checks = 0;
for (const [base, path, status, marker] of [
  [secureBase, '/mobile/stocktake', 200, '/src/main.tsx'],
  [secureBase, '/api/auth/health', 200, 'UP'],
  [secureBase, '/src/pages/mobile/MobileStocktakePage.tsx', 200, 'hasUnsavedCount'],
  [secureBase, '/src/pages/mobile/ScanInput.tsx', 200, 'LiveScanDialog'],
  [secureBase, '/src/pages/mobile/liveBarcodeScanner.ts', 200, 'decode'],
  [secureBase, '/a2-live-scan-diagnostic.html', 200, '/src/dev/a2LiveScanDiagnostic.tsx'],
  [secureBase, '/src/dev/a2LiveScanDiagnostic.tsx', 200, 'isSecureContext'],
  [secureBase, '/a2-mobile-barcodes.html', 200, 'RCB-9EA067CFCEBF43CC'],
  [bootstrapBase, '/', 200, '/a2-test-ca.cer'],
  [bootstrapBase, '/a2-test-ca.cer', 200],
  [bootstrapBase, '/server.key', 404],
  [bootstrapBase, '/metadata.json', 404],
  [bootstrapBase, '/api/auth/health', 404],
]) {
  const result = await get(base + path);
  assert.equal(result.status, status, `Unexpected HTTP status for ${path}`);
  if (base === secureBase) assert(result.verified, `TLS verification failed: ${path}`);
  if (marker) assert(result.body.toString('utf8').includes(marker), `Content mismatch: ${path}`);
  if (base === secureBase && path.endsWith('/health')) {
    assert.equal(JSON.parse(result.body.toString('utf8')).status, 'UP');
  }
  if (base === bootstrapBase && path === '/a2-test-ca.cer') {
    assert.equal(new X509Certificate(result.body).fingerprint256, authority.fingerprint256);
    assert.equal(result.headers['cache-control'], 'no-store');
  }
  console.log(JSON.stringify({ url: base + path, status: result.status,
    tlsVerified: base === secureBase ? result.verified : null }));
  checks++;
}
const privatePath = fileURLToPath(new URL('server.key', tls)).replaceAll('\\', '/');
const privateResult = await get(`${secureBase}/@fs/${privatePath}`);
assert.equal(privateResult.status, 403, 'Private key access must be denied');
assert(privateResult.verified);
checks++;
console.log(JSON.stringify({ privateKeyRequestDenied: true, status: privateResult.status }));
console.log(JSON.stringify({ result: 'PASS_SERVER_ONLY', checks,
  tlsChainAndIpVerified: true, trustStoreModified: false, phoneEvidence: 'PENDING' }));

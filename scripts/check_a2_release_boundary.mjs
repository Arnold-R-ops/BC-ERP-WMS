// Read-only checks plus a short-lived loopback Vite server; never starts the backend.
import assert from 'node:assert/strict';
import { existsSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from '../frontend/node_modules/vite/dist/node/index.js';

const frontend = fileURLToPath(new URL('../frontend/', import.meta.url));
const dist = resolve(frontend, 'dist');
const files = readdirSync(dist, { recursive: true }).map(String);
for (const entry of ['index.html', 'platform.html']) assert(existsSync(resolve(dist, entry)));
for (const entry of ['a2-mobile-barcodes.html', 'a2-scan-diagnostic.html', 'a2-live-scan-diagnostic.html']) {
  assert(!existsSync(resolve(dist, entry)), `UAT entry leaked into release: ${entry}`);
}
assert(!files.some((file) => /\.(map|key|pem|cer|p12|pfx)$/i.test(file)));
const server = await createServer({
  root: frontend,
  configFile: resolve(frontend, 'vite.config.ts'),
  server: { host: '127.0.0.1', port: 0, open: false },
  logLevel: 'error',
});
try {
  await server.listen();
  const address = server.httpServer.address();
  assert(address && typeof address === 'object');
  const response = await fetch(`http://127.0.0.1:${address.port}/a2-mobile-barcodes.html`);
  assert.equal(response.status, 200);
  const html = await response.text();
  assert(html.includes('RCB-9EA067CFCEBF43CC') && html.includes('A2BAR003'));
  console.log(JSON.stringify({ developmentBarcodePage: 200, productionUatPages: 0, sourceMaps: 0,
    certificateFiles: 0, productionEntrypoints: 2 }));
} finally {
  await server.close();
}

// Fixed-version tool acquisition only. No project install, dependency update, database or service operation.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
const root = 'D:/ERP_WMS/BC warehouse/2G';
const destination = path.join(root, 'target/platform-validation-tools/pnpm-11.16.0');
const metadataUrl = 'https://registry.npmjs.org/pnpm/11.16.0';
const tarballUrl = 'https://registry.npmjs.org/pnpm/-/pnpm-11.16.0.tgz';
if (process.cwd().replaceAll('\\', '/').toLowerCase() !== root.toLowerCase() || process.version !== 'v24.12.0') throw new Error('Fixed workspace and Node 24.12.0 required');
const hash = (bytes, algorithm = 'sha256', encoding = 'hex') => crypto.createHash(algorithm).update(bytes).digest(encoding);
if (process.argv[2] !== 'prepare') throw new Error('Only prepare is accepted');
if (fs.existsSync(path.join(destination, 'receipt.json'))) throw new Error('Completed tool installation exists; do not overwrite');
const response = await fetch(metadataUrl, { redirect: 'error', signal: AbortSignal.timeout(30000) });
if (!response.ok) throw new Error('Official package metadata unavailable');
const metadata = await response.json();
if (metadata.name !== 'pnpm' || metadata.version !== '11.16.0' || metadata.dist.tarball !== tarballUrl || !/^sha512-[A-Za-z0-9+/=]+$/.test(metadata.dist.integrity)) throw new Error('Unexpected official metadata');
const archiveResponse = await fetch(tarballUrl, { redirect: 'error', signal: AbortSignal.timeout(30000) });
if (!archiveResponse.ok) throw new Error('Official package archive unavailable');
const archive = Buffer.from(await archiveResponse.arrayBuffer());
if ('sha512-' + hash(archive, 'sha512', 'base64') !== metadata.dist.integrity) throw new Error('Package integrity mismatch');
fs.mkdirSync(destination, { recursive: true });
const archivePath = path.join(destination, 'pnpm-11.16.0.tgz');
fs.writeFileSync(archivePath, archive, { flag: 'wx' });
const listing = execFileSync('tar', ['-tzf', archivePath], { encoding: 'utf8' }).trim().split(/\r?\n/);
if (listing.some(name => !name.startsWith('package/') || name.includes('..') || name.includes('\\') || name.includes(':'))) throw new Error('Unsafe archive path');
const types = execFileSync('tar', ['-tvzf', archivePath], { encoding: 'utf8' }).trim().split(/\r?\n/);
if (types.some(line => !['-', 'd'].includes(line[0]))) throw new Error('Archive links/special files not accepted');
execFileSync('tar', ['-xzf', archivePath, '-C', destination]);
const cli = path.join(destination, 'package/bin/pnpm.cjs');
const installed = JSON.parse(fs.readFileSync(path.join(destination, 'package/package.json')));
if (installed.version !== '11.16.0') throw new Error('Installed package version mismatch');
const version = execFileSync(process.execPath, [cli, '--version'], { cwd: destination, encoding: 'utf8' }).trim();
if (version !== '11.16.0') throw new Error('CLI version mismatch');
const receipt = { preparedAt: new Date().toISOString(), node: process.version, nodePath: process.execPath,
  nodeSha256: hash(fs.readFileSync(process.execPath)), pnpm: version, cliPath: cli, cliSha256: hash(fs.readFileSync(cli)),
  source: metadataUrl, tarball: tarballUrl, integrity: metadata.dist.integrity, archiveSha256: hash(archive),
  engines: installed.engines, projectInstallExecuted: false, globalConfigurationChanged: false };
fs.writeFileSync(path.join(destination, 'receipt.json'), JSON.stringify(receipt, null, 2) + '\n', { flag: 'wx' });
console.log(JSON.stringify(receipt));

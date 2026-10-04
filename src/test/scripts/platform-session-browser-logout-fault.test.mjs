import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { shouldDropLogout } from './platform-session-browser-logout-fault.mjs';

// Intentionally unsigned, hard-coded test vectors; not a real account or session credential.
const id = '11111111-1111-1111-1111-111111111111';
const now = Date.parse('2026-09-10T00:00:00Z');
const claims = { principal_type: 'PLATFORM', normalized_email: 'session-uat-a@platform.invalid', platform_session_id: id };
const auth = value => `Bearer test.${Buffer.from(JSON.stringify(value)).toString('base64url')}.invalid`;
const request = { method: 'POST', pathname: '/api/platform/auth/logout', authorization: auth(claims) };
const control = { enabled: true, scenario: 'drop-logout-before-upstream', armedAt: new Date(now).toISOString(),
  expiresAt: new Date(now + 30 * 60_000).toISOString(), sessionSha256: createHash('sha256').update(id).digest('hex') };

test('only the specified session logout is selected', () => assert.equal(shouldDropLogout(request, control, now), true));
test('other methods and platform endpoints remain untouched', () => {
  for (const method of ['GET', 'HEAD', 'OPTIONS']) assert.equal(shouldDropLogout({ ...request, method }, control, now), false);
  for (const pathname of ['/api/platform/auth/me', '/api/platform/auth/session/activity', '/api/platform/admins'])
    assert.equal(shouldDropLogout({ ...request, pathname }, control, now), false);
});
test('other accounts, sessions and principal types remain untouched', () => {
  for (const changes of [{ normalized_email: 'session-uat-b@platform.invalid' }, { platform_session_id: '22222222-2222-2222-2222-222222222222' }, { principal_type: 'TENANT' }])
    assert.equal(shouldDropLogout({ ...request, authorization: auth({ ...claims, ...changes }) }, control, now), false);
});
test('missing or disabled control cannot activate the fault', () => {
  for (const value of [null, {}, { ...control, enabled: false }, { ...control, scenario: 'different' }])
    assert.equal(shouldDropLogout(request, value, now), false);
});
test('expired or not-yet-armed control cannot activate the fault', () => {
  assert.equal(shouldDropLogout(request, control, now - 1), false);
  assert.equal(shouldDropLogout(request, control, now + 30 * 60_000), false);
});
test('invalid or oversized lifetime is rejected', () => {
  for (const expiresAt of ['bad', new Date(now + 30 * 60_000 + 1).toISOString()])
    assert.equal(shouldDropLogout(request, { ...control, expiresAt }, now), false);
});
test('malformed bearer and session selector are rejected', () => {
  for (const authorization of [undefined, '', 'Bearer bad', 'Bearer a.???.c', auth({ ...claims, platform_session_id: null })])
    assert.equal(shouldDropLogout({ ...request, authorization }, control, now), false);
  assert.equal(shouldDropLogout(request, { ...control, sessionSha256: 'bad' }, now), false);
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { activatePlatformInvitation, changePlatformAdminRole, confirmPlatformInvitationActivation, confirmPlatformLogoutAll, confirmPlatformMfaEnrollment, createPlatformAdminInvitation, createPlatformExport, getMyPlatformEffectiveAccess, getPlatformAdmin, getPlatformCurrentSession, getPlatformInvitationStatus, getPlatformRecoveryCodeStatus, getPlatformTenant, listPlatformAdmins, listPlatformTenants, platformLogin, PlatformInvitationError, PlatformLoginError, PlatformMfaError, PLATFORM_AUTH_UNAUTHORIZED_EVENT, regeneratePlatformRecoveryCodes, resetPlatformAdminMfa, revokePlatformAdminInvitation, revokePlatformAdminSessions, searchPlatformAuditLogs, startPlatformAdminInvitation, startPlatformAdminMfaReset, startPlatformAdminRoleChange, startPlatformAdminSessionRevoke, startPlatformLogoutAll, startPlatformRecoveryCodeRegeneration, verifyPlatformMfa } from './api';
import { writePlatformSession } from './storage';

describe('platform read API', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('uses only the platform bearer token', async () => {
    localStorage.setItem('2g-wms.auth-session', JSON.stringify({ token: 'company-token' }));
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      content: [], number: 0, size: 20, totalElements: 0, totalPages: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await listPlatformTenants(0, 20);

    const options = fetchMock.mock.calls[0][1] as RequestInit;
    expect(options.headers).toEqual({ Authorization: 'Bearer platform-token' });
    expect(JSON.stringify(options.headers)).not.toContain('company-token');
  });

  it('encodes tenant directory filters only when explicitly supplied', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      content: [], number: 0, size: 20, totalElements: 0, totalPages: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await listPlatformTenants(2, 20, { keyword: '  华东 A/1  ', status: 'ACTIVE' });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/platform/companies?page=2&size=20&keyword=%E5%8D%8E%E4%B8%9C+A%2F1&status=ACTIVE',
      expect.objectContaining({ headers: { Authorization: 'Bearer platform-token' } }),
    );
  });

  it('queries the administrator directory and detail with explicit safe filters', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({
        content: [], number: 1, size: 20, totalElements: 0, totalPages: 0,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: 9, displayName: 'Administrator', email: 'admin@example.com', enabled: true,
        roles: [], mfaStatus: 'ENROLLED', mfaEnrolledAt: null, mfaLockedUntil: null,
        createdAt: '2026-08-20T00:00:00Z', updatedAt: '2026-08-20T00:00:00Z',
        activeGrantCount: 0, activeGrantCounts: { READ: 0, EXPORT: 0 },
        currentUser: false, lastEnabledSuperAdmin: false,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await listPlatformAdmins(1, 20, {
      keyword: '  安全 A/1  ', enabled: false, role: 'PLATFORM_SUPER_ADMIN',
      mfaStatus: 'TEMPORARILY_LOCKED',
    });
    await getPlatformAdmin(9);

    expect(fetchMock).toHaveBeenNthCalledWith(1,
      '/api/platform/admins?page=1&size=20&keyword=%E5%AE%89%E5%85%A8+A%2F1&enabled=false&role=PLATFORM_SUPER_ADMIN&mfaStatus=TEMPORARILY_LOCKED',
      expect.objectContaining({ headers: { Authorization: 'Bearer platform-token' } }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/platform/admins/9', expect.objectContaining({
      headers: { Authorization: 'Bearer platform-token' },
    }));
  });

  it('loads one tenant overview through the platform-scoped endpoint', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      id: 9, tenantCode: 'T-009', displayName: 'Tenant 9', slug: 'tenant-9', status: 'ACTIVE',
      closedAt: null, purgeDueAt: null, createdAt: '2026-08-21T00:00:00Z',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await getPlatformTenant(9);

    expect(fetchMock).toHaveBeenCalledWith('/api/platform/companies/9', expect.objectContaining({
      headers: { Authorization: 'Bearer platform-token' },
    }));
  });

  it('creates a single-dataset export with the platform bearer token', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      id: 'job-1', companyId: 9, status: 'PENDING', resource: 'users', recordCount: null,
      sha256: null, expiresAt: '2026-08-17T00:00:00Z', createdAt: '2026-08-16T00:00:00Z', completedAt: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await createPlatformExport(9, 'users');

    expect(fetchMock).toHaveBeenCalledWith('/api/platform/companies/9/exports', expect.objectContaining({
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer platform-token' },
      body: JSON.stringify({ resource: 'users' }),
    }));
  });

  it('checks only the signed-in platform administrator effective scope', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'delegate@bcwms.invalid',
      roles: ['PLATFORM_TENANT_READ'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      superAdmin: false,
      scopes: [{ tenantId: 6, datasetCode: 'users', read: true, export: false }],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    const result = await getMyPlatformEffectiveAccess();

    expect(result.scopes).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/platform/access-grants/me', expect.objectContaining({
      headers: { Authorization: 'Bearer platform-token' },
    }));
  });

  it('queries the safe current-session view and revokes all sessions with an in-memory MFA challenge', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({
        email: 'admin@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], mfaEnabled: true,
        expiresAt: '2026-08-18T12:00:00Z',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ challengeToken: 'temporary-challenge', expiresIn: 300_000 }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }));

    await getPlatformCurrentSession();
    const challenge = await startPlatformLogoutAll();
    await confirmPlatformLogoutAll(challenge.challengeToken, '123456');

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/platform/auth/me', expect.objectContaining({
      headers: { Authorization: 'Bearer platform-token' },
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/platform/auth/logout-all/challenge', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/platform/auth/logout-all', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ challengeToken: 'temporary-challenge', code: '123456' }),
    }));
    expect(sessionStorage.getItem('temporary-challenge')).toBeNull();
  });

  it('regenerates recovery codes only after password and TOTP reauthentication', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ remaining: 7 }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ challengeToken: 'recovery-challenge', expiresIn: 300_000 }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        recoveryCodes: ['ABCDE-12345'], remaining: 10,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await getPlatformRecoveryCodeStatus();
    const challenge = await startPlatformRecoveryCodeRegeneration();
    await regeneratePlatformRecoveryCodes(challenge.challengeToken, 'current-password', '123456');

    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/platform/auth/recovery-codes/regenerate', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ challengeToken: 'recovery-challenge', password: 'current-password', code: '123456' }),
    }));
    expect(sessionStorage.getItem('recovery-challenge')).toBeNull();
  });

  it('binds an administrator MFA reset challenge to one target and sends no business-task mutation', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ challengeToken: 'admin-reset-challenge', expiresIn: 300_000, targetSecurityVersion: 8 }), {
        status: 200, headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        targetUserId: 9, action: 'MFA_RESET', changed: true, enabled: true, mfaStatus: 'NOT_ENROLLED',
        roles: ['PLATFORM_SUPER_ADMIN'], activeGrantCount: 0, securityVersion: 9, completedAt: '2026-08-22T14:00:00Z',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    const challenge = await startPlatformAdminMfaReset(9);
    const result = await resetPlatformAdminMfa(
      9, challenge.challengeToken, 'current-password', '123456', 'Lost authenticator', 'mfa-reset-command-1',
    );

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/platform/admins/9/mfa-reset/challenge', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/platform/admins/9/mfa-reset', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'Idempotency-Key': 'mfa-reset-command-1' }),
      body: JSON.stringify({
        challengeToken: 'admin-reset-challenge', password: 'current-password', code: '123456', reason: 'Lost authenticator',
      }),
    }));
    expect(challenge.targetSecurityVersion).toBe(8);
    expect(result.mfaStatus).toBe('NOT_ENROLLED');
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/exports'))).toBe(false);
  });

  it('sends target session revocation as a separate idempotent administrator command', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({
        challengeToken: 'session-revoke-challenge', expiresIn: 300_000, targetSecurityVersion: 12,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        targetUserId: 9, action: 'ADMIN_SESSIONS_REVOKED', changed: true, enabled: true, mfaStatus: 'ENROLLED',
        roles: ['HISTORICAL_UNKNOWN'], activeGrantCount: 2, securityVersion: 13, completedAt: '2026-08-22T14:00:00Z',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    const challenge = await startPlatformAdminSessionRevoke(9);
    const result = await revokePlatformAdminSessions(
      9, challenge.challengeToken, 'current-password', '654321', 'Compromised laptop', 'session-command-1',
    );

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/platform/admins/9/sessions/revoke/challenge', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/platform/admins/9/sessions/revoke', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({ 'Idempotency-Key': 'session-command-1' }),
      body: JSON.stringify({
        challengeToken: 'session-revoke-challenge', password: 'current-password', code: '654321', reason: 'Compromised laptop',
      }),
    }));
    expect(result.action).toBe('ADMIN_SESSIONS_REVOKED');
    expect(result.enabled).toBe(true);
  });

  it('creates a super-administrator invitation only after password and MFA reauthentication', async () => {
    writePlatformSession({ token: 'platform-token', tokenType: 'Bearer', email: 'owner@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000 });
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ challengeToken: 'invite-challenge', expiresIn: 300_000 }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: 11, email: 'second@example.com', displayName: 'Second Owner', roleCode: 'PLATFORM_SUPER_ADMIN', status: 'ACTIVE',
        createdAt: '2026-08-19T00:00:00Z', expiresAt: '2026-08-20T00:00:00Z', acceptedAt: null, revokedAt: null,
        activationPath: '/platform.html#/activate?token=one-time-token',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    const challenge = await startPlatformAdminInvitation();
    await createPlatformAdminInvitation({ challengeToken: challenge.challengeToken, password: 'current-password', code: '123456', email: 'second@example.com', displayName: 'Second Owner', reason: 'Continuity', invitationType: 'SUPER_ADMIN', roleCode: 'PLATFORM_SUPER_ADMIN' });

    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/platform/admins/invitations', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ challengeToken: 'invite-challenge', password: 'current-password', code: '123456', email: 'second@example.com', displayName: 'Second Owner', reason: 'Continuity', invitationType: 'SUPER_ADMIN', roleCode: 'PLATFORM_SUPER_ADMIN' }),
    }));
    expect(sessionStorage.getItem('one-time-token')).toBeNull();
  });

  it('keeps a valid platform session when invitation reauthentication fails', async () => {
    writePlatformSession({ token: 'platform-token', tokenType: 'Bearer', email: 'owner@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000 });
    const unauthorized = vi.fn();
    window.addEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, unauthorized);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      errorKey: 'AUTH_FAILED', params: { reason: 'MFA_VERIFICATION_FAILED' }, status: 401,
    }), { status: 401, headers: { 'Content-Type': 'application/json' } }));

    await expect(createPlatformAdminInvitation({ challengeToken: 'challenge', password: 'wrong', code: '000000', email: 'second@example.com', displayName: 'Second Owner', reason: 'Continuity', invitationType: 'SUPER_ADMIN', roleCode: 'PLATFORM_SUPER_ADMIN' }))
      .rejects.toMatchObject({ reason: 'reauthentication' });

    expect(unauthorized).not.toHaveBeenCalled();
    expect(sessionStorage.getItem('2g-wms.platform-auth-session')).toContain('platform-token');
    window.removeEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, unauthorized);
  });

  it('still clears the platform session signal when the bearer token itself is rejected', async () => {
    writePlatformSession({ token: 'expired-token', tokenType: 'Bearer', email: 'owner@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000 });
    const unauthorized = vi.fn();
    window.addEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, unauthorized);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ errorKey: 'AUTH_FAILED', status: 401 }), {
      status: 401, headers: { 'Content-Type': 'application/json' },
    }));

    await expect(getPlatformCurrentSession()).rejects.toMatchObject({ reason: 'request' });

    expect(unauthorized).toHaveBeenCalledOnce();
    window.removeEventListener(PLATFORM_AUTH_UNAUTHORIZED_EVENT, unauthorized);
  });

  it('queries audit logs only with explicit safe filters and the platform token', async () => {
    writePlatformSession({
      token: 'platform-token', tokenType: 'Bearer', email: 'admin@bcwms.com',
      roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000,
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      content: [], number: 0, size: 20, totalElements: 0, totalPages: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await searchPlatformAuditLogs({
      from: '2026-08-09T00:00:00.000Z', to: '2026-08-16T23:59:59.999Z',
      tenantId: 9, action: 'EXPORT', page: 0, size: 20,
    });

    const [url, options] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/platform/audit-logs?');
    expect(String(url)).toContain('tenantId=9');
    expect(String(url)).toContain('action=EXPORT');
    expect(options?.headers).toEqual({ Authorization: 'Bearer platform-token' });
  });
});

describe('platform login API', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it('distinguishes unavailable service from rejected credentials', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new TypeError('network unavailable'));
    await expect(platformLogin({ email: 'admin@bcwms.com', password: 'secret' }))
      .rejects.toMatchObject({ reason: 'service' } satisfies Partial<PlatformLoginError>);

    vi.mocked(globalThis.fetch).mockResolvedValueOnce(new Response(null, { status: 401 }));
    await expect(platformLogin({ email: 'admin@bcwms.com', password: 'secret' }))
      .rejects.toMatchObject({ reason: 'credentials' } satisfies Partial<PlatformLoginError>);
  });

  it('accepts an MFA challenge without treating it as an authenticated session', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      status: 'MFA_ENROLLMENT_REQUIRED', challengeToken: 'challenge-token',
      enrollmentSecret: 'setup-secret', otpauthUri: 'otpauth://setup',
      email: 'admin@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresIn: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    const response = await platformLogin({ email: 'admin@bcwms.com', password: 'secret' });

    expect(response.status).toBe('MFA_ENROLLMENT_REQUIRED');
    expect(response.token).toBeUndefined();
    expect(sessionStorage.getItem('2g-wms.platform-auth-session')).toBeNull();
    expect(fetchMock).toHaveBeenCalledWith('/api/platform/auth/login', expect.any(Object));
  });

  it('submits MFA enrollment through the pre-JWT challenge endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      status: 'AUTHENTICATED', token: 'platform-token', tokenType: 'Bearer',
      email: 'admin@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresIn: 60_000,
      recoveryCodes: ['ABCDE-23456'],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await confirmPlatformMfaEnrollment('challenge-token', '123456');

    expect(fetchMock).toHaveBeenCalledWith('/api/platform/auth/mfa/enroll/confirm', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ challengeToken: 'challenge-token', code: '123456' }),
    }));
  });

  it('keeps an MFA challenge retryable and reports remaining attempts for a mismatched code', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      errorKey: 'AUTH_FAILED',
      params: { reason: 'MFA_VERIFICATION_FAILED', mfaReason: 'CODE_INVALID', remainingAttempts: 3 },
    }), { status: 401, headers: { 'Content-Type': 'application/json' } }));

    await expect(verifyPlatformMfa('challenge-token', '000000'))
      .rejects.toMatchObject({ reason: 'code', remainingAttempts: 3 } satisfies Partial<PlatformMfaError>);

    expect(fetchMock).toHaveBeenCalledWith('/api/platform/auth/mfa/verify', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ challengeToken: 'challenge-token', code: '000000', recoveryCode: undefined }),
    }));
  });

  it('distinguishes an expired MFA step from an invalid authenticator code', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      errorKey: 'AUTH_FAILED',
      params: { reason: 'MFA_VERIFICATION_FAILED', mfaReason: 'CHALLENGE_EXPIRED' },
    }), { status: 401, headers: { 'Content-Type': 'application/json' } }));

    await expect(verifyPlatformMfa('expired-challenge', '123456'))
      .rejects.toMatchObject({ reason: 'expired' } satisfies Partial<PlatformMfaError>);
  });

  it('activates an invitation without a tenant or platform bearer session and requires MFA enrollment', async () => {
    localStorage.setItem('2g-wms.auth-session', JSON.stringify({ token: 'tenant-token' }));
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ email: 'second@example.com', displayName: 'Second Owner', roleCode: 'PLATFORM_SUPER_ADMIN', expiresAt: '2026-08-20T00:00:00Z' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'MFA_ENROLLMENT_REQUIRED', challengeToken: 'enrollment-challenge', enrollmentSecret: 'secret', otpauthUri: 'otpauth://setup', email: 'second@example.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresIn: 300_000 }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    await getPlatformInvitationStatus('one-time-token');
    const response = await activatePlatformInvitation('one-time-token', 'Strong-Password-42!');

    expect(response.status).toBe('MFA_ENROLLMENT_REQUIRED');
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(JSON.stringify(fetchMock.mock.calls)).not.toContain('tenant-token');
    expect(sessionStorage.getItem('2g-wms.platform-auth-session')).toBeNull();
  });

  it('confirms invitation MFA through the atomic activation endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      status: 'AUTHENTICATED', token: 'platform-jwt', tokenType: 'Bearer',
      email: 'operator@example.com', roles: ['PLATFORM_OPERATIONS_ADMIN'],
      expiresIn: 900_000, recoveryCodes: ['RECOVERY-1'],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    const result = await confirmPlatformInvitationActivation('activation-challenge', '123456');

    expect(result.status).toBe('AUTHENTICATED');
    expect(fetchMock).toHaveBeenCalledWith('/api/platform/auth/invitations/activate/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ challengeToken: 'activation-challenge', code: '123456' }),
    });
  });

  it('sends role-change intent in both the MFA challenge and idempotent mutation', async () => {
    writePlatformSession({ token: 'platform-token', tokenType: 'Bearer', email: 'owner@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000 });
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ challengeToken: 'role-challenge', expiresIn: 300_000, targetSecurityVersion: 4 }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ targetUserId: 9, changed: true, enabled: false, beforeRoles: ['PLATFORM_OPERATIONS_ADMIN'], afterRoles: ['PLATFORM_SECURITY_AUDITOR'], activeGrantCount: 0, securityVersion: 5, completedAt: '2026-08-23T00:00:00Z' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    const challenge = await startPlatformAdminRoleChange(9, 'PLATFORM_SECURITY_AUDITOR');
    await changePlatformAdminRole(9, 'PLATFORM_SECURITY_AUDITOR', challenge.challengeToken, 'owner-password', '123456', 'separate duties', 'role-key');

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/platform/admins/9/roles/change/challenge', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ jobRoleCode: 'PLATFORM_SECURITY_AUDITOR' }),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/platform/admins/9/roles', expect.objectContaining({
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'role-key', Authorization: 'Bearer platform-token' },
    }));
  });

  it('requires an explicit reason when revoking an invitation', async () => {
    writePlatformSession({ token: 'platform-token', tokenType: 'Bearer', email: 'owner@bcwms.com', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000 });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));

    await revokePlatformAdminInvitation(11, 'recipient changed');

    expect(fetchMock).toHaveBeenCalledWith('/api/platform/admins/invitations/11/revoke', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ reason: 'recipient changed' }),
    }));
  });

  it('distinguishes an invalid invitation from a temporary activation failure', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({
        errorKey: 'AUTH_FAILED', params: { reason: 'PLATFORM_INVITATION_INVALID' }, status: 401,
      }), { status: 401, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        errorKey: 'INTERNAL_SERVER_ERROR', status: 500,
      }), { status: 500, headers: { 'Content-Type': 'application/json' } }));

    await expect(getPlatformInvitationStatus('invalid-token'))
      .rejects.toMatchObject({ reason: 'invalid' } satisfies Partial<PlatformInvitationError>);
    await expect(activatePlatformInvitation('still-valid-token', 'Strong-Password-42!'))
      .rejects.toMatchObject({ reason: 'service' } satisfies Partial<PlatformInvitationError>);
  });
});

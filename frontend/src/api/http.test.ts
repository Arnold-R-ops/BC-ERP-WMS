import { beforeEach, describe, expect, it, vi } from 'vitest';
import { writeAuthSession } from '../auth/storage';
import {
  apiRawRequest,
  apiRequest,
  AUTH_FORBIDDEN_EVENT,
  AUTH_UNAUTHORIZED_EVENT,
} from './http';

describe('API request authentication handling', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('injects the bearer token', async () => {
    writeAuthSession({
      token: 'abc123',
      tokenType: 'Bearer',
      username: 'admin',
      currentRole: 'TENANT_ADMIN',
      permissionCodes: [],
      availableRoles: ['TENANT_ADMIN'],
      expiresAt: Date.now() + 10_000,
      mustChangePassword: false,
    });
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));

    await apiRequest<{ ok: boolean }>('/api/test');

    const requestInit = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(requestInit?.headers);
    expect(headers.get('Authorization')).toBe('Bearer abc123');
  });

  it('omits an existing bearer token for one-time session exchange', async () => {
    writeAuthSession({
      token: 'stale-company-token',
      tokenType: 'Bearer',
      username: 'admin',
      currentRole: 'TENANT_ADMIN',
      permissionCodes: [],
      availableRoles: ['TENANT_ADMIN'],
      expiresAt: Date.now() + 10_000,
      mustChangePassword: false,
    });
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));

    await apiRequest<{ ok: boolean }>('/public/v1/session-handoff/consume', {
      method: 'POST',
      body: { code: 'one-time-code' },
      omitAuth: true,
    });

    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.has('Authorization')).toBe(false);
  });

  it('emits the unauthorized event for a 401 response', async () => {
    const listener = vi.fn();
    window.addEventListener(AUTH_UNAUTHORIZED_EVENT, listener);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ errorKey: 'AUTH_TOKEN_INVALID' }), { status: 401 }),
    );

    await expect(apiRequest('/api/test')).rejects.toMatchObject({ status: 401 });
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener(AUTH_UNAUTHORIZED_EVENT, listener);
  });

  it('emits the forbidden event without converting it to 401', async () => {
    const listener = vi.fn();
    window.addEventListener(AUTH_FORBIDDEN_EVENT, listener);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ errorKey: 'AUTH_ACCESS_DENIED' }), { status: 403 }),
    );

    await expect(apiRequest('/api/test')).rejects.toMatchObject({ status: 403 });
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener(AUTH_FORBIDDEN_EVENT, listener);
  });

  it('accepts an empty 204 response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    await expect(apiRequest<void>('/api/test')).resolves.toBeUndefined();
  });

  it('sends multipart data without forcing a JSON content type', async () => {
    writeAuthSession({
      token: 'abc123',
      tokenType: 'Bearer',
      username: 'admin',
      currentRole: 'TENANT_ADMIN',
      permissionCodes: [],
      availableRoles: ['TENANT_ADMIN'],
      expiresAt: Date.now() + 10_000,
      mustChangePassword: false,
    });
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ count: 1 }), { status: 200 }));
    const form = new FormData();
    form.append('file', new Blob(['test']), 'test.xlsx');

    await apiRawRequest('/api/upload', { method: 'POST', body: form });

    const requestInit = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(requestInit?.headers);
    expect(headers.get('Authorization')).toBe('Bearer abc123');
    expect(headers.has('Content-Type')).toBe(false);
    expect(requestInit?.body).toBe(form);
  });
});

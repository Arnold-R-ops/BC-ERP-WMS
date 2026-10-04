import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { beginBrowserLogout, browserLogoutFence, browserLogoutKey, finishBrowserLogout,
  subscribeBrowserLogout, withPlatformBrowserBinding } from './browserLogout';
import { PLATFORM_AUTH_STORAGE_KEY, readPlatformSession, writePlatformSession } from './storage';

beforeEach(() => { localStorage.clear(); sessionStorage.clear(); vi.stubGlobal('BroadcastChannel', undefined); });
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); localStorage.clear(); sessionStorage.clear(); });
describe('platform browser logout coordination', () => {
  it('a sleeping tab cannot restore a pre-logout session from storage, even after a later login', () => {
    writePlatformSession({ token: 'old-jwt', tokenType: 'Bearer', email: 'a@invalid.test', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000 });
    const sleeping = sessionStorage.getItem(PLATFORM_AUTH_STORAGE_KEY)!;
    const signal = beginBrowserLogout('a@invalid.test'); finishBrowserLogout(signal, 'revoked');
    expect(readPlatformSession()).toBeNull();
    writePlatformSession({ token: 'fresh-jwt', tokenType: 'Bearer', email: 'a@invalid.test', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 60_000 });
    expect(readPlatformSession()?.token).toBe('fresh-jwt');
    sessionStorage.setItem(PLATFORM_AUTH_STORAGE_KEY, sleeping);
    expect(readPlatformSession()).toBeNull();
    expect(localStorage.getItem(browserLogoutKey('a@invalid.test'))).not.toMatch(/old-jwt|fresh-jwt/);
  });
  it('stale completion cannot replace the newer logout fence', () => {
    const first = beginBrowserLogout('a@invalid.test');
    const second = beginBrowserLogout('a@invalid.test');
    finishBrowserLogout(first, 'revoked');
    expect(browserLogoutFence(first.email)).toBe(second.id);
    expect(JSON.parse(localStorage.getItem(browserLogoutKey(first.email))!).phase).toBe('pending');
  });
  it('storage notifications work without BroadcastChannel and clean up listeners', () => {
    const accept = vi.fn(); const stop = subscribeBrowserLogout(accept);
    beginBrowserLogout('a@invalid.test'); accept.mockClear();
    window.dispatchEvent(new StorageEvent('storage', { key: browserLogoutKey('a@invalid.test') }));
    expect(accept).toHaveBeenCalledTimes(1);
    stop(); accept.mockClear();
    window.dispatchEvent(new StorageEvent('storage', { key: browserLogoutKey('a@invalid.test') }));
    expect(accept).not.toHaveBeenCalled();
  });
  it('a blocked BroadcastChannel does not prevent the logout request workflow', () => {
    vi.stubGlobal('BroadcastChannel', class { constructor() { throw new Error('blocked'); } });
    const stop = subscribeBrowserLogout(vi.fn());
    expect(() => { const signal = beginBrowserLogout('a@invalid.test'); finishBrowserLogout(signal, 'unconfirmed'); }).not.toThrow();
    stop();
  });
  it('keeps different accounts logout fences independent', () => {
    const a = beginBrowserLogout('a@invalid.test'); const b = beginBrowserLogout('b@invalid.test');
    finishBrowserLogout(a, 'revoked'); finishBrowserLogout(b, 'unconfirmed');
    expect(browserLogoutFence(a.email)).toBe(a.id); expect(browserLogoutFence(b.email)).toBe(b.id);
  });
  it('initializes HttpOnly grouping with a non-simple request before authentication', async () => {
    const lock = vi.fn(async (_name: string, action: () => Promise<unknown>) => action());
    vi.stubGlobal('navigator', { locks: { request: lock } });
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 })); vi.stubGlobal('fetch', fetch);
    const action = vi.fn().mockResolvedValue('authenticated');
    expect(await withPlatformBrowserBinding(action)).toBe('authenticated');
    expect(lock).toHaveBeenCalledWith('wms-platform-browser-auth', expect.any(Function));
    expect(fetch).toHaveBeenCalledWith('/api/platform/auth/browser-session', expect.objectContaining({
      method: 'POST', credentials: 'same-origin', headers: { 'X-Platform-Browser-Init': '1' },
    }));
    expect(action).toHaveBeenCalledTimes(1);
  });
  it('does not authenticate when cookie initialization fails or cross-tab locking is unavailable', async () => {
    const action = vi.fn(); vi.stubGlobal('navigator', {});
    await expect(withPlatformBrowserBinding(action)).rejects.toThrow('PLATFORM_BROWSER_LOCKS_REQUIRED');
    vi.stubGlobal('navigator', { locks: { request: async (_name: string, callback: () => Promise<unknown>) => callback() } });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })));
    await expect(withPlatformBrowserBinding(action)).rejects.toThrow('PLATFORM_BROWSER_BINDING_UNAVAILABLE');
    expect(action).not.toHaveBeenCalled();
  });
});

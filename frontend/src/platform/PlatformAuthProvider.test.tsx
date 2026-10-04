import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PlatformAuthProvider, usePlatformAuth } from './PlatformAuthProvider';
import { PLATFORM_AUTH_UNAUTHORIZED_EVENT } from './api';
import { PLATFORM_AUTH_STORAGE_KEY, writePlatformSession } from './storage';
import { beginBrowserLogout, finishBrowserLogout, browserLogoutKey } from './browserLogout';

const session = { token: 'isolated-platform-token', tokenType: 'Bearer', email: 'test@invalid.test', roles: ['PLATFORM_SUPER_ADMIN'], expiresAt: Date.now() + 43_200_000 };
function Probe() {
  const auth = usePlatformAuth();
  return <><div data-testid="state">{auth.session ? auth.sessionVerified ? 'verified' : 'checking' : 'signed-out'}</div>
    <div data-testid="notice">{auth.sessionNotice}</div><button onClick={auth.logout}>logout</button>
    <button onClick={() => auth.establishSession({ status: 'AUTHENTICATED', token: 'new-token', email: session.email, roles: session.roles, expiresIn: 43_200_000 })}>new login</button></>;
}
function okWindow() {
  return new Response(JSON.stringify({ email: session.email, roles: session.roles, mfaEnabled: true,
    serverNow: new Date().toISOString(), idleExpiresAt: new Date(Date.now() + 1_800_000).toISOString(), expiresAt: new Date(session.expiresAt).toISOString() }), { status: 200 });
}
beforeEach(() => { sessionStorage.clear(); localStorage.clear(); vi.stubGlobal('BroadcastChannel', undefined); writePlatformSession(session); });
afterEach(() => { cleanup(); vi.unstubAllGlobals(); sessionStorage.clear(); localStorage.clear(); });
describe('platform backend session boundary', () => {
  it('does not expose a verified session before the backend check completes', async () => {
    let resolve!: (response: Response) => void;
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(done => { resolve = done; })));
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    expect(screen.getByTestId('state').textContent).toBe('checking');
    await act(async () => resolve(okWindow()));
    expect(screen.getByTestId('state').textContent).toBe('verified');
  });
  it('reload with an idle-expired JWT is rejected, not silently renewed', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ errorKey: 'PLATFORM_SESSION_IDLE_EXPIRED' }), { status: 401 }));
    vi.stubGlobal('fetch', fetch);
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('signed-out'));
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0][0]).toBe('/api/platform/auth/me');
    expect(sessionStorage.getItem(PLATFORM_AUTH_STORAGE_KEY)).toBeNull();
  });
  it('ordinary logout sends the captured JWT even after browser state is cleared', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(okWindow()).mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetch);
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    fireEvent.click(screen.getByText('logout'));
    expect(sessionStorage.getItem(PLATFORM_AUTH_STORAGE_KEY)).toBeNull();
    await waitFor(() => expect(screen.getByTestId('notice').textContent).toBe('logout-revoked'));
    expect(fetch.mock.calls[1][1].headers.Authorization).toBe('Bearer isolated-platform-token');
  });
  it('network failure reports unconfirmed server revocation, not success', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(okWindow()).mockRejectedValueOnce(new TypeError('offline'));
    vi.stubGlobal('fetch', fetch);
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    fireEvent.click(screen.getByText('logout'));
    await waitFor(() => expect(screen.getByTestId('notice').textContent).toBe('logout-unconfirmed'));
    expect(screen.getByTestId('state').textContent).toBe('signed-out');
  });
  it('old asynchronous 401 cannot clear a newer login', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(okWindow())));
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    fireEvent.click(screen.getByText('new login'));
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    act(() => window.dispatchEvent(new CustomEvent(PLATFORM_AUTH_UNAUTHORIZED_EVENT, { detail: { token: session.token } })));
    expect(screen.getByTestId('state').textContent).toBe('verified');
  });
  it('mousemove, synthetic clicks and focus checks never report activity', async () => {
    const fetch = vi.fn(() => Promise.resolve(okWindow())); vi.stubGlobal('fetch', fetch);
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    fireEvent.mouseMove(window); fireEvent.click(window); fireEvent.focus(window);
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    expect(fetch.mock.calls.every(call => (call as unknown[])[0] === '/api/platform/auth/me')).toBe(true);
  });
  it('a sibling tab logout clears this account and reports a failed revocation honestly', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(okWindow())));
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    let signal!: ReturnType<typeof beginBrowserLogout>;
    act(() => { signal = beginBrowserLogout(session.email); });
    expect(screen.getByTestId('state').textContent).toBe('signed-out');
    expect(screen.getByTestId('notice').textContent).toBe('logout-pending');
    act(() => finishBrowserLogout(signal, 'unconfirmed'));
    expect(screen.getByTestId('notice').textContent).toBe('logout-unconfirmed');
    expect(localStorage.getItem(browserLogoutKey(session.email))).not.toContain(session.token);
  });
  it('does not sign out a different platform account on a sibling notification', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(okWindow())));
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    act(() => beginBrowserLogout('another@invalid.test'));
    expect(screen.getByTestId('state').textContent).toBe('verified');
  });
  it('a late logout response cannot clear or overwrite a newly authenticated session', async () => {
    let complete!: (response: Response) => void;
    vi.stubGlobal('fetch', vi.fn((path: string) => path.endsWith('/logout')
      ? new Promise<Response>(resolve => { complete = resolve; }) : Promise.resolve(okWindow())));
    render(<PlatformAuthProvider><Probe /></PlatformAuthProvider>);
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    fireEvent.click(screen.getByText('logout'));
    fireEvent.click(screen.getByText('new login'));
    await waitFor(() => expect(screen.getByTestId('state').textContent).toBe('verified'));
    await act(async () => complete(new Response(null, { status: 204 })));
    expect(screen.getByTestId('state').textContent).toBe('verified');
    expect(screen.getByTestId('notice').textContent).toBe('');
  });
});

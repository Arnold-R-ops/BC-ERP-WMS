// Same-origin browser notifications only. No JWT, password or browser cookie is broadcast/stored here.
export const PLATFORM_BROWSER_LOGOUT_KEY = '2g-wms.platform-browser-logout';
export const browserLogoutKey = (email: string): string => `${PLATFORM_BROWSER_LOGOUT_KEY}:${encodeURIComponent(email.toLowerCase())}`;
const EVENT = 'wms:platform-browser-logout';
const CHANNEL = 'wms-platform-browser-logout';
export type LogoutPhase = 'pending' | 'revoked' | 'invalid' | 'unconfirmed';
export interface BrowserLogoutSignal { email: string; id: string; phase: LogoutPhase; }
type Signals = Record<string, BrowserLogoutSignal>;

function valid(value: unknown): value is BrowserLogoutSignal {
  if (!value || typeof value !== 'object') return false;
  const signal = value as Partial<BrowserLogoutSignal>;
  return typeof signal.email === 'string' && typeof signal.id === 'string'
    && ['pending', 'revoked', 'invalid', 'unconfirmed'].includes(signal.phase || '');
}
export function browserLogoutSnapshot(): Signals {
  const result: Signals = Object.create(null) as Signals;
  // One key per account avoids read/modify/write races between two different accounts' logouts.
  for (let index = 0; index < localStorage.length; index += 1) {
    const key = localStorage.key(index);
    if (!key?.startsWith(`${PLATFORM_BROWSER_LOGOUT_KEY}:`)) continue;
    const parsed: unknown = JSON.parse(localStorage.getItem(key) || 'null');
    if (!valid(parsed) || key !== browserLogoutKey(parsed.email)) throw new Error('PLATFORM_LOGOUT_STORAGE_INVALID');
    result[parsed.email.toLowerCase()] = parsed;
  }
  return result;
}
export function browserLogoutFence(email: string): string {
  return browserLogoutSnapshot()[email.toLowerCase()]?.id || '';
}

function announce(signal: BrowserLogoutSignal): void {
  window.dispatchEvent(new CustomEvent(EVENT, { detail: signal }));
  if (typeof BroadcastChannel !== 'undefined') {
    try {
      const channel = new BroadcastChannel(CHANNEL);
      try { channel.postMessage(signal); } finally { channel.close(); }
    } catch { /* A notification transport failure must NEVER prevent server revocation. */ }
  }
}
export function beginBrowserLogout(email: string): BrowserLogoutSignal {
  const signal: BrowserLogoutSignal = { email: email.toLowerCase(), id: crypto.randomUUID(), phase: 'pending' };
  try { localStorage.setItem(browserLogoutKey(signal.email), JSON.stringify(signal)); }
  catch { /* Still revoke on the server and send a best-effort live notification if storage is unavailable. */ }
  announce(signal);
  return signal;
}
export function finishBrowserLogout(signal: BrowserLogoutSignal, phase: LogoutPhase): void {
  const completed = { ...signal, phase };
  try {
    const all = browserLogoutSnapshot();
    if (all[signal.email]?.id !== signal.id) return; // A late result cannot overwrite a newer logout.
    localStorage.setItem(browserLogoutKey(signal.email), JSON.stringify(completed));
  } catch { /* Notifications are advisory; the actual HTTP outcome is handled by the caller. */ }
  announce(completed);
}
export function subscribeBrowserLogout(accept: (signal: BrowserLogoutSignal) => void): () => void {
  const local = (event: Event) => { const signal = (event as CustomEvent).detail; if (valid(signal)) accept(signal); };
  const storage = (event: StorageEvent) => {
    if (!event.key?.startsWith(`${PLATFORM_BROWSER_LOGOUT_KEY}:`)) return;
    try { Object.values(browserLogoutSnapshot()).forEach(accept); } catch { /* Server checks remain authoritative. */ }
  };
  let channel: BroadcastChannel | null = null;
  try { if (typeof BroadcastChannel !== 'undefined') channel = new BroadcastChannel(CHANNEL); }
  catch { /* Storage events and backend checks remain available. */ }
  if (channel) channel.onmessage = event => { if (valid(event.data)) accept(event.data); };
  window.addEventListener(EVENT, local);
  window.addEventListener('storage', storage);
  return () => { window.removeEventListener(EVENT, local); window.removeEventListener('storage', storage); channel?.close(); };
}

/** Serialize first-cookie initialization across tabs; never split a normal concurrent browser login. */
export async function withPlatformBrowserBinding<T>(action: () => Promise<T>): Promise<T> {
  if (!navigator.locks) throw new Error('PLATFORM_BROWSER_LOCKS_REQUIRED');
  return navigator.locks.request('wms-platform-browser-auth', async () => {
    const response = await fetch('/api/platform/auth/browser-session', {
      method: 'POST', credentials: 'same-origin', headers: { 'X-Platform-Browser-Init': '1' },
      signal: AbortSignal.timeout(10_000),
    });
    if (response.status !== 204) throw new Error('PLATFORM_BROWSER_BINDING_UNAVAILABLE');
    return action();
  });
}

export function ledgerLoginDestination(raw: string, baseDomain: string): string {
  const url = new URL(raw);
  const suffix = '.' + baseDomain.toLowerCase();
  const slug = url.hostname.endsWith(suffix) ? url.hostname.slice(0, -suffix.length) : '';
  if (url.protocol !== 'https:' || url.port || url.username || url.password
      || url.pathname !== '/login' || !/^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$/.test(slug)) {
    throw new Error('Invalid Ledger destination');
  }
  return url.href;
}

/** Preserve existing bookmarks and fragment handoff codes without another HTTP request. */
export function restoreLegacyRoute(): void {
  if (!window.location.hash.startsWith('#/')) return;
  const next = new URL(window.location.hash.slice(1), window.location.origin);
  if (next.origin === window.location.origin) {
    window.history.replaceState(null, '', next.pathname + next.search + next.hash);
  }
}

import { afterEach, describe, expect, it } from 'vitest';
import { ledgerLoginDestination, restoreLegacyRoute } from './ledgerEntry';

describe('Ledger production entry boundaries', () => {
  afterEach(() => window.history.replaceState(null, '', '/'));
  it('accepts a controlled tenant login and preserves encoded email', () => {
    const url = 'https://acme.ledger.fongvo.com/login?email=a%2Bb%40example.com';
    expect(ledgerLoginDestination(url, 'ledger.fongvo.com')).toBe(url);
  });
  it.each([
    'javascript:alert(1)', 'http://acme.ledger.fongvo.com/login',
    'https://ledger.fongvo.com.evil.invalid/login',
    'https://acme.evil.invalid/login', 'https://nested.acme.ledger.fongvo.com/login',
    'https://acme.ledger.fongvo.com:444/login', 'https://user@acme.ledger.fongvo.com/login',
    'https://acme.ledger.fongvo.com/api/users',
  ])('rejects an uncontrolled redirect: %s', (url) => {
    expect(() => ledgerLoginDestination(url, 'ledger.fongvo.com')).toThrow();
  });
  it('keeps old bookmarked routes and fragment handoffs working', () => {
    window.history.replaceState(null, '', '/#/login?email=a%40example.com');
    restoreLegacyRoute();
    expect(window.location.pathname + window.location.search).toBe('/login?email=a%40example.com');
    window.history.replaceState(null, '', '/#/session/handoff?code=one-use');
    restoreLegacyRoute();
    expect(window.location.pathname).toBe('/session/handoff');
    expect(window.location.search).toBe('?code=one-use');
  });
  it('does not follow an external URL embedded in a hash', () => {
    window.history.replaceState(null, '', '/#//evil.invalid/login');
    restoreLegacyRoute();
    expect(window.location.pathname).toBe('/');
  });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listSessionSecurityAudits } from './sessionSecurity';

describe('session security API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify([]), {
      headers: { 'Content-Type': 'application/json' }, status: 200,
    }));
  });

  it('loads the bounded tenant audit history', async () => {
    await listSessionSecurityAudits(100);
    expect(fetch).toHaveBeenCalledWith(
      '/api/session-security/audits?limit=100',
      expect.any(Object),
    );
  });
});

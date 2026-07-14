import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getUrgentReorderSuggestions } from './dashboard';

describe('dashboard API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ suggestions: [], totalCount: 0, totalCost: 0 }), { status: 200 }),
    );
  });

  it('requests urgent reorder suggestions for the selected calculation period', async () => {
    await getUrgentReorderSuggestions(45);

    expect(fetch).toHaveBeenCalledWith(
      '/api/predictions/reorder/urgent?days=45',
      expect.any(Object),
    );
  });
});

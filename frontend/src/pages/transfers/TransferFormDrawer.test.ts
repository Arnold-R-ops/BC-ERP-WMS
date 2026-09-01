import { describe, expect, it } from 'vitest';
import type { TransferDraftPayload } from '../../api/transfers';
import { getTransferBackorderValidationIssue } from './TransferFormDrawer';

function draft(overrides: Partial<TransferDraftPayload> = {}): TransferDraftPayload {
  return {
    sourceWarehouseId: 1,
    destinationWarehouseId: 2,
    reason: 'Rebalance stock',
    lines: [{ productSkuId: 10, quantity: 3 }],
    ...overrides,
  };
}

describe('transfer draft backorder validation', () => {
  it('requires an ETA when a destination backorder is linked', () => {
    expect(getTransferBackorderValidationIssue(draft({
      lines: [{ productSkuId: 10, quantity: 3, backorderLineId: 41 }],
    }))).toBe('ETA_REQUIRED');
  });

  it('rejects duplicate links and accepts distinct links with an ETA', () => {
    expect(getTransferBackorderValidationIssue(draft({
      estimatedArrivalAt: '2026-09-05T10:00',
      lines: [
        { productSkuId: 10, quantity: 3, backorderLineId: 41 },
        { productSkuId: 11, quantity: 2, backorderLineId: 41 },
      ],
    }))).toBe('DUPLICATE_BACKORDER');
    expect(getTransferBackorderValidationIssue(draft({
      estimatedArrivalAt: '2026-09-05T10:00',
      lines: [
        { productSkuId: 10, quantity: 3, backorderLineId: 41 },
        { productSkuId: 11, quantity: 2, backorderLineId: 42 },
      ],
    }))).toBeUndefined();
  });
});

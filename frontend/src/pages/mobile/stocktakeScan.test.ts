import { describe, expect, it } from 'vitest';
import type { StocktakeItem } from '../../api/stocktake';
import { resolveStocktakeScan } from './stocktakeScan';

const items: StocktakeItem[] = [
  { id: 1, locationCode: 'A-01', batchCode: 'BATCH-OLD', productBarcode: 'SKU-1', isCounted: false },
  { id: 2, locationCode: 'A-01', batchCode: 'BATCH-FRESH', productBarcode: 'SKU-1', isCounted: false },
  { id: 3, locationCode: 'B-01', batchCode: 'BATCH-DONE', productBarcode: 'SKU-2', isCounted: true },
];

describe('stocktake scan resolution', () => {
  it('selects a unique uncounted item by batch', () => {
    const result = resolveStocktakeScan(items, ' batch-old ');
    expect(result.kind).toBe('match');
    if (result.kind === 'match') expect(result.item.id).toBe(1);
  });

  it('does not guess when one location contains multiple count lines', () => {
    expect(resolveStocktakeScan(items, 'A-01')).toEqual({ kind: 'ambiguous', candidateIds: [1, 2] });
  });

  it('uses a second scan to narrow ambiguous candidates', () => {
    const result = resolveStocktakeScan(items, 'BATCH-FRESH', [1, 2]);
    expect(result.kind).toBe('match');
    if (result.kind === 'match') expect(result.item.id).toBe(2);
  });

  it('ignores already-counted items', () => {
    expect(resolveStocktakeScan(items, 'BATCH-DONE')).toEqual({ kind: 'none' });
  });
});

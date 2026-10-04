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

  it('allows an already-counted batch to be selected for correction', () => {
    expect(resolveStocktakeScan(items, 'BATCH-DONE')).toEqual({ kind: 'match', item: items[2] });
  });

  it('does not guess between a counted and an uncounted line for the same SKU', () => {
    const mixed = [{ ...items[0], isCounted: true }, items[1]];
    expect(resolveStocktakeScan(mixed, 'SKU-1')).toEqual({ kind: 'ambiguous', candidateIds: [1, 2] });
    expect(resolveStocktakeScan(mixed, 'BATCH-OLD', [1, 2])).toEqual({ kind: 'match', item: mixed[0] });
  });
});

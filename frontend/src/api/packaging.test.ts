import { describe, expect, it } from 'vitest';
import { packagingTotal, selectionTotal, suggestPackagingTake, type PackagingView } from './packaging';

const view: PackagingView = { batchId: 1, batchCode: 'LOT', productSkuId: 1, warehouseId: 1,
  locationId: 1, locationCode: 'A01', quantity: 41, version: 3, reservedQuantity: 0,
  standardPackSize: 12, packaging: { packSize: 12, sealedBoxes: 2, openedQuantities: [9, 8] } };
describe('actual packaging selections', () => {
  it('does not invent unopened boxes from opened contents', () => {
    const actual = { packSize: 12, sealedBoxes: 0, openedQuantities: [17] };
    const selection = suggestPackagingTake({ ...view, packaging: actual }, 12)!;
    expect(selection.sealedBoxes).toBe(0);
    expect(selection.openedQuantities).toEqual([12]);
    expect(selectionTotal(actual, selection)).toBe(12);
  });
  it('takes existing opened goods before opening another box', () => {
    const selection = suggestPackagingTake(view, 20)!;
    expect(selection.openedQuantities).toEqual([9, 8]);
    expect(selection.sealedBoxes).toBe(0);
    expect(selection.takeFromNewlyOpened).toEqual([3]);
    expect(selection.version).toBe(3);
    expect(selectionTotal(view.packaging!, selection)).toBe(20);
  });
  it('preserves totals when contents are rearranged above the standard size', () => {
    expect(packagingTotal({ packSize: 12, sealedBoxes: 1, openedQuantities: [9, 8] })).toBe(29);
    expect(packagingTotal({ packSize: 12, sealedBoxes: 1, openedQuantities: [17] })).toBe(29);
  });
  it('does not infer packaging for legacy stock', () => {
    expect(suggestPackagingTake({ ...view, packaging: null }, 12)).toBeUndefined();
  });
});

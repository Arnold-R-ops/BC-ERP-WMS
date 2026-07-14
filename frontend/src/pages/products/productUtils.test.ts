import { describe, expect, it } from 'vitest';
import type { Product } from '../../api/products';
import { filterProducts, getSpuOptions } from './productUtils';

const products: Product[] = [
  { id: 1, name: 'Green Tea', skuName: 'Box', barcode: 'TEA-001', spuId: 2, spuName: 'Tea', enabled: true },
  { id: 2, name: 'Cola', skuName: 'Bottle', barcode: 'COKE-001', spuId: 1, spuName: 'Drinks', enabled: false },
];

describe('product utilities', () => {
  it('filters by name, SKU name, or barcode without case sensitivity', () => {
    expect(filterProducts(products, { search: 'tea' })).toHaveLength(1);
    expect(filterProducts(products, { search: 'bottle' })).toHaveLength(1);
    expect(filterProducts(products, { search: 'coke-001' })).toHaveLength(1);
  });

  it('filters by enabled status', () => {
    expect(filterProducts(products, { enabled: false })).toEqual([products[1]]);
  });

  it('creates unique SPU options from real product data', () => {
    expect(getSpuOptions([...products, products[0]])).toEqual([
      { label: 'Drinks (#1)', value: 1 },
      { label: 'Tea (#2)', value: 2 },
    ]);
  });
});

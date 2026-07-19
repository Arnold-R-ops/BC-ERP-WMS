import { describe, expect, it } from 'vitest';
import type { Product } from '../../api/products';
import { filterProducts } from './productUtils';

const products: Product[] = [
  {
    id: 1,
    productCode: 'TEA',
    productName: 'Green Tea',
    categoryName: 'Tea',
    parentCategoryName: 'Beverages',
    brand: 'Garden',
    enabled: true,
  },
  {
    id: 2,
    productCode: 'COLA',
    productName: 'Cola',
    categoryName: 'Soft Drinks',
    parentCategoryName: 'Beverages',
    enabled: false,
  },
];

describe('product utilities', () => {
  it('filters by code, name, category, or brand without case sensitivity', () => {
    expect(filterProducts(products, { search: 'tea' })).toHaveLength(1);
    expect(filterProducts(products, { search: 'cola' })).toHaveLength(1);
    expect(filterProducts(products, { search: 'soft drinks' })).toHaveLength(1);
    expect(filterProducts(products, { search: 'garden' })).toHaveLength(1);
  });

  it('filters by enabled status', () => {
    expect(filterProducts(products, { enabled: false })).toEqual([products[1]]);
  });
});

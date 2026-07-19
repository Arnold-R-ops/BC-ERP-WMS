import { describe, expect, it } from 'vitest';
import type { Category } from '../../api/categories';
import {
  filterCategoryTree,
  getCategoryPath,
  getCategoryScopeIds,
  isSystemCategory,
  reorderCategorySiblings,
} from './categoryUtils';

const categories: Category[] = [
  {
    id: 1,
    categoryCode: 'RAW',
    categoryName: 'Food ingredients',
    children: [
      { id: 2, parentId: 1, categoryCode: 'TEA', categoryName: 'Tea' },
      { id: 3, parentId: 1, categoryCode: 'DAIRY', categoryName: 'Dairy' },
    ],
  },
  {
    id: 4,
    categoryCode: 'UNCATEGORIZED',
    categoryName: 'Uncategorized',
    children: [{ id: 5, parentId: 4, categoryCode: 'PENDING_CLASSIFICATION', categoryName: 'Pending' }],
  },
];

describe('category utilities', () => {
  it('keeps ancestors when a descendant matches search', () => {
    const result = filterCategoryTree(categories, 'dairy');
    expect(result).toHaveLength(1);
    expect(result[0]?.id).toBe(1);
    expect(result[0]?.children?.map((item) => item.id)).toEqual([3]);
  });

  it('resolves paths, descendants, and protected system subtrees', () => {
    expect(getCategoryPath(categories, 3).map((item) => item.id)).toEqual([1, 3]);
    expect([...getCategoryScopeIds(categories[0])]).toEqual([1, 2, 3]);
    expect(isSystemCategory(categories, categories[1]?.children?.[0])).toBe(true);
    expect(isSystemCategory(categories, categories[0]?.children?.[0])).toBe(false);
  });

  it('reorders categories only within the same parent', () => {
    expect(reorderCategorySiblings(categories, 2, 3, true)?.map((item) => item.id)).toEqual([3, 2]);
    expect(reorderCategorySiblings(categories, 2, 4, true)).toBeUndefined();
  });
});

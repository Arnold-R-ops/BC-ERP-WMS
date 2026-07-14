import { describe, expect, it } from 'vitest';
import { paginateArray, toProTablePage, toSpringPage } from './pagination';

describe('pagination adapters', () => {
  it('converts the one-based table page to a zero-based Spring page', () => {
    expect(toSpringPage({ current: 3, pageSize: 50 })).toEqual({ page: 2, size: 50 });
  });

  it('normalizes a Spring page response', () => {
    expect(toProTablePage({ content: ['a'], totalElements: 7 })).toEqual({
      data: ['a'],
      success: true,
      total: 7,
    });
  });

  it('paginates an array for non-paged compatibility endpoints', () => {
    expect(paginateArray([1, 2, 3, 4, 5], { current: 2, pageSize: 2 })).toEqual({
      data: [3, 4],
      success: true,
      total: 5,
    });
  });
});

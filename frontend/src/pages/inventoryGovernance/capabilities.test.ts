import { describe, expect, it } from 'vitest';
import { getCorrectionCapabilities, getStocktakeCapabilities } from './capabilities';

describe('inventory governance role boundaries', () => {
  it('lets warehouse staff count but not create or review stocktakes', () => {
    expect(getStocktakeCapabilities('WAREHOUSE_STAFF')).toEqual({
      canCreate: false,
      canCount: true,
      canReview: false,
    });
  });

  it('separates warehouse adjustment work from manager approval', () => {
    expect(getCorrectionCapabilities('WAREHOUSE_ADMIN')).toEqual({
      canAdjust: true,
      canApprove: false,
    });
    expect(getCorrectionCapabilities('GENERAL_MANAGER')).toEqual({
      canAdjust: false,
      canApprove: true,
    });
  });

  it('lets managers review but not enter physical counts', () => {
    expect(getStocktakeCapabilities('GENERAL_MANAGER')).toEqual({
      canCreate: true,
      canCount: false,
      canReview: true,
    });
  });

  it('keeps unknown roles outside both workflows', () => {
    expect(getStocktakeCapabilities('CUSTOM_ROLE')).toEqual({
      canCreate: false,
      canCount: false,
      canReview: false,
    });
    expect(getCorrectionCapabilities('CUSTOM_ROLE')).toEqual({
      canAdjust: false,
      canApprove: false,
    });
  });
});

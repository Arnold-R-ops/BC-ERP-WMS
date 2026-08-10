import { describe, expect, it } from 'vitest';
import { getCorrectionCapabilities, getStocktakeCapabilities } from './capabilities';

describe('inventory governance role boundaries', () => {
  it('lets warehouse staff count but not create or review stocktakes', () => {
    expect(getStocktakeCapabilities('WAREHOUSE_STAFF', ['stocktake:count'])).toEqual({
      canCreate: false,
      canCount: true,
      canReview: false,
    });
  });

  it('separates warehouse adjustment work from manager approval', () => {
    expect(getCorrectionCapabilities('WAREHOUSE_ADMIN', ['inventory:adjust'])).toEqual({
      canAdjust: true,
      canApprove: false,
      canReject: false,
      canReview: false,
    });
    expect(getCorrectionCapabilities('GENERAL_MANAGER', ['inventory:correction:review', 'inventory:correction:approve', 'inventory:correction:reject'])).toEqual({
      canAdjust: false,
      canApprove: true,
      canReject: true,
      canReview: true,
    });
  });

  it('lets managers review but not enter physical counts', () => {
    expect(getStocktakeCapabilities('GENERAL_MANAGER', ['stocktake:create', 'stocktake:review'])).toEqual({
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
      canReject: false,
      canReview: false,
    });
  });
});

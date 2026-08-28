import { describe, expect, it } from 'vitest';
import type { PurchaseAsn } from '../../api/purchasing';
import { getPurchaseAsnAvailableActions } from './PurchaseOrderDrawer';

const allCapabilities = {
  canCancel: true,
  canCloseRemainder: true,
  canConfirm: true,
  canCreate: true,
  canUpdate: true,
};

function asn(status: PurchaseAsn['status'], receivedQuantity = 0, remainingQuantity = 5): PurchaseAsn {
  return {
    id: 31,
    version: 2,
    asnNumber: 'ASN-31',
    purchaseOrderId: 9,
    warehouseId: 4,
    status,
    items: [{
      id: 41,
      purchaseOrderItemId: 21,
      shippedQuantity: 5,
      receivedQuantity,
      remainingQuantity,
    }],
  };
}

describe('purchase ASN action availability', () => {
  it('allows a draft to be edited, confirmed, or cancelled according to permissions', () => {
    expect(getPurchaseAsnAvailableActions(asn('DRAFT'), allCapabilities)).toEqual([
      'edit', 'confirm', 'cancel',
    ]);
  });

  it('allows an unreceived confirmed ASN to be corrected or cancelled', () => {
    expect(getPurchaseAsnAvailableActions(asn('CONFIRMED'), allCapabilities)).toEqual(['correct', 'cancel']);
  });

  it('hides correction and cancellation after receiving starts and only closes a partially received remainder', () => {
    expect(getPurchaseAsnAvailableActions(asn('CONFIRMED', 2, 3), allCapabilities)).toEqual([]);
    expect(getPurchaseAsnAvailableActions(asn('PARTIALLY_RECEIVED', 2, 3), allCapabilities)).toEqual([
      'closeRemainder',
    ]);
  });

  it('does not expose actions without their permissions', () => {
    expect(getPurchaseAsnAvailableActions(asn('DRAFT'), {
      canCancel: false,
      canCloseRemainder: false,
      canConfirm: false,
      canCreate: false,
      canUpdate: false,
    })).toEqual([]);
  });
});

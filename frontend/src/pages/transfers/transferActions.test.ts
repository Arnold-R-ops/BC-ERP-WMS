import { describe, expect, it } from 'vitest';
import type { Transfer, TransferLine } from '../../api/transfers';
import { getTransferAvailableActions } from './transferActions';

function line(overrides: Partial<TransferLine> = {}): TransferLine {
  return {
    id: 1, version: 0, productSkuId: 10, skuCode: 'SKU-10', productName: 'Widget',
    requestedQuantity: 10, allocatedQuantity: 0, dispatchedQuantity: 0,
    receivedQuantity: 0, damagedQuantity: 0, quarantinedQuantity: 0,
    closedUnshippedQuantity: 0, shortageClosedQuantity: 0,
    inTransitQuantity: 0, allocations: [], ...overrides,
  };
}

function transfer(overrides: Partial<Transfer> = {}): Transfer {
  return {
    id: 1, version: 0, transferNumber: 'TR-1', sourceWarehouseId: 1,
    sourceWarehouseCode: 'A', sourceWarehouseName: 'A', destinationWarehouseId: 2,
    destinationWarehouseCode: 'B', destinationWarehouseName: 'B', reason: 'Rebalance stock',
    businessStatus: 'DRAFT', outboundStatus: 'NOT_RELEASED', inboundStatus: 'NOT_IN_TRANSIT',
    approvalModeSnapshot: 'DIRECT', receivingInspectionSnapshot: false,
    requestedQuantity: 10, allocatedQuantity: 0, dispatchedQuantity: 0, inTransitQuantity: 0,
    receivedQuantity: 0, damagedQuantity: 0, quarantinedQuantity: 0, closedQuantity: 0,
    requestedBy: 1, requestedByName: 'user', createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z', lines: [line()], dispatches: [], receipts: [], events: [],
    ...overrides,
  };
}

describe('transfer action boundaries', () => {
  it('allows a clean draft to be edited, submitted, cancelled or voided', () => {
    const actions = getTransferAvailableActions(transfer());
    expect(actions).toMatchObject({ edit: true, submit: true, cancel: true, void: true });
  });

  it('does not allow whole-order cancellation once dispatch has happened', () => {
    const actions = getTransferAvailableActions(transfer({
      businessStatus: 'APPROVED', outboundStatus: 'PARTIAL',
      inboundStatus: 'IN_TRANSIT', lines: [line({ allocatedQuantity: 10, dispatchedQuantity: 4, inTransitQuantity: 4 })],
    }));
    expect(actions.cancel).toBe(false);
    expect(actions.closeRemainder).toBe(true);
    expect(actions.reallocate).toBe(true);
    expect(actions.closeShortage).toBe(true);
  });

  it('offers shortage closure only while a dispatched balance remains in transit', () => {
    expect(getTransferAvailableActions(transfer({
      businessStatus: 'APPROVED', outboundStatus: 'DISPATCHED', inboundStatus: 'EXCEPTION',
      lines: [line({ dispatchedQuantity: 10, receivedQuantity: 8, inTransitQuantity: 2 })],
    })).closeShortage).toBe(true);
    expect(getTransferAvailableActions(transfer({ businessStatus: 'COMPLETED' })).closeShortage).toBe(false);
  });

  it('allows a rejected order to be cancelled before dispatch', () => {
    expect(getTransferAvailableActions(transfer({ businessStatus: 'REJECTED' })).cancel).toBe(true);
  });
});

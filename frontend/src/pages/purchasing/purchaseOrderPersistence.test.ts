import { describe, expect, it, vi } from 'vitest';
import type { PurchaseOrder } from '../../api/purchasing';
import type { PurchaseOrderFormValues } from './PurchaseOrderFormDrawer';
import { persistPurchaseDraft, PurchaseAllocationPersistenceError } from './purchaseOrderPersistence';

const values: PurchaseOrderFormValues = {
  supplierId: 3,
  items: [{
    productSkuId: 12,
    orderedQuantity: 100,
    allocations: [
      { warehouseId: 2, plannedQuantity: 60 },
      { warehouseId: 3, plannedQuantity: 40 },
    ],
  }],
};

const savedOrder = {
  id: 9,
  version: 5,
  poNumber: 'PO-09',
  status: 'DRAFT',
  supplierId: 3,
  items: [{ id: 21, productSkuId: 12, orderedQuantity: 100 }],
} as PurchaseOrder;

describe('purchase draft allocation persistence', () => {
  it('recovers the created draft and retries with PUT without creating a duplicate PO', async () => {
    const create = vi.fn().mockResolvedValue(savedOrder);
    const update = vi.fn().mockResolvedValue({ ...savedOrder, version: 6 });
    const saveAllocations = vi.fn()
      .mockRejectedValueOnce(new Error('allocation service unavailable'))
      .mockResolvedValueOnce([]);
    const dependencies = { create, saveAllocations, update };

    let recovery: PurchaseAllocationPersistenceError | undefined;
    try {
      await persistPurchaseDraft(values, undefined, dependencies);
    } catch (error) {
      expect(error).toBeInstanceOf(PurchaseAllocationPersistenceError);
      recovery = error as PurchaseAllocationPersistenceError;
    }

    expect(recovery?.order).toBe(savedOrder);
    expect(recovery?.allocations).toEqual([
      { purchaseOrderItemId: 21, warehouseId: 2, plannedQuantity: 60 },
      { purchaseOrderItemId: 21, warehouseId: 3, plannedQuantity: 40 },
    ]);

    await persistPurchaseDraft(values, recovery?.order, dependencies);

    expect(create).toHaveBeenCalledTimes(1);
    expect(update).toHaveBeenCalledTimes(1);
    expect(saveAllocations).toHaveBeenCalledTimes(2);
  });
});

import type {
  PurchaseOrder,
  PurchaseOrderAllocationInput,
  PurchaseOrderPayload,
  PurchaseOrderUpdatePayload,
} from '../../api/purchasing';
import type { PurchaseOrderFormValues } from './PurchaseOrderFormDrawer';

interface PurchaseDraftPersistenceDependencies {
  create: (payload: PurchaseOrderPayload) => Promise<PurchaseOrder>;
  saveAllocations: (
    id: number,
    version: number,
    allocations: PurchaseOrderAllocationInput[],
  ) => Promise<unknown>;
  update: (id: number, payload: PurchaseOrderUpdatePayload) => Promise<PurchaseOrder>;
}

export interface PersistedPurchaseDraft {
  allocations: PurchaseOrderAllocationInput[];
  order: PurchaseOrder;
}

export class PurchaseAllocationPersistenceError extends Error {
  readonly allocations: PurchaseOrderAllocationInput[];
  readonly order: PurchaseOrder;

  constructor(order: PurchaseOrder, allocations: PurchaseOrderAllocationInput[], cause: unknown) {
    super('Purchase draft was saved, but warehouse allocations still need to be persisted', { cause });
    this.name = 'PurchaseAllocationPersistenceError';
    this.order = order;
    this.allocations = allocations;
  }
}

export function buildPurchaseAllocationInputs(
  lines: PurchaseOrderFormValues['items'],
  savedItems: PurchaseOrder['items'],
): PurchaseOrderAllocationInput[] {
  return lines.flatMap((line) => {
    const savedItem = savedItems?.find((item) => item.id === line.id || item.productSkuId === line.productSkuId);
    const purchaseOrderItemId = savedItem?.id;
    if (purchaseOrderItemId === undefined) {
      throw new Error('Purchase line and allocation mapping is incomplete');
    }
    return line.allocations.map((allocation) => {
      if (!allocation.warehouseId || !allocation.plannedQuantity) {
        throw new Error('Purchase line and allocation mapping is incomplete');
      }
      return {
        purchaseOrderItemId,
        warehouseId: allocation.warehouseId,
        plannedQuantity: allocation.plannedQuantity,
      };
    });
  });
}

export async function persistPurchaseDraft(
  values: PurchaseOrderFormValues,
  existingOrder: PurchaseOrder | undefined,
  dependencies: PurchaseDraftPersistenceDependencies,
): Promise<PersistedPurchaseDraft> {
  const { items, ...header } = values;
  const documentItems = items.map(({ allocations: _allocations, ...item }) => item);
  const wasCreated = existingOrder?.id === undefined;
  let saved: PurchaseOrder;

  if (wasCreated) {
    saved = await dependencies.create({ ...header, items: documentItems } as PurchaseOrderPayload);
  } else {
    if (existingOrder.version === undefined) throw new Error('Purchase order version is unavailable');
    saved = await dependencies.update(existingOrder.id as number, {
      ...header,
      items: documentItems,
      version: existingOrder.version,
    });
  }

  if (saved.id === undefined || saved.version === undefined) {
    throw new Error('Saved purchase order identity is unavailable');
  }

  let allocations: PurchaseOrderAllocationInput[] = [];
  try {
    allocations = buildPurchaseAllocationInputs(items, saved.items);
    await dependencies.saveAllocations(saved.id, saved.version, allocations);
  } catch (error) {
    if (wasCreated) throw new PurchaseAllocationPersistenceError(saved, allocations, error);
    throw error;
  }
  return { allocations, order: saved };
}

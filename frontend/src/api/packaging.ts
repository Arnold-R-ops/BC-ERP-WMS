import { apiRequest } from './http';

export interface StockPackaging {
  packSize: number;
  sealedBoxes: number;
  openedQuantities: number[];
}
export interface PackagingSelection {
  version: number;
  sealedBoxes: number;
  openedQuantities: number[];
  takeFromNewlyOpened: number[];
}
export interface PackagingView {
  batchId: number;
  batchCode: string;
  productSkuId: number;
  warehouseId: number;
  locationId: number;
  locationCode: string;
  quantity: number;
  version: number;
  reservedQuantity: number;
  stocktakeTaskId?: number;
  baseUnit?: string;
  standardPackSize: number;
  packaging?: StockPackaging | null;
}
export interface ArrangePackagingPayload {
  mode: 'VERIFY' | 'ARRANGE';
  sources: { batchId: number; version: number }[];
  results: { locationId: number; packaging: StockPackaging }[];
}
export const packagingTotal = (p: StockPackaging): number =>
  p.sealedBoxes * p.packSize + p.openedQuantities.reduce((a, b) => a + b, 0);
export const emptyPackaging = (packSize: number): StockPackaging => ({ packSize, sealedBoxes: 0, openedQuantities: [] });
export function getPackaging(batchId: number): Promise<PackagingView> {
  return apiRequest(`/api/inventory/packaging/${batchId}`);
}
export function arrangePackaging(payload: ArrangePackagingPayload, key: string): Promise<PackagingView[]> {
  return apiRequest('/api/inventory/packaging/arrange', {
    method: 'POST', body: payload, headers: { 'Idempotency-Key': key },
  });
}

/** A visible suggestion only. The worker confirms the actual sources before dispatch. */
export function suggestPackagingTake(view: PackagingView, quantity: number): PackagingSelection | undefined {
  if (!view.packaging) return undefined;
  const p = view.packaging;
  let remaining = quantity;
  const openedQuantities = p.openedQuantities.map(available => {
    const taken = Math.min(remaining, available); remaining -= taken; return taken;
  });
  const sealedBoxes = Math.min(p.sealedBoxes, Math.floor(remaining / p.packSize));
  remaining -= sealedBoxes * p.packSize;
  const takeFromNewlyOpened = remaining > 0 && sealedBoxes < p.sealedBoxes ? [remaining] : [];
  return { version: view.version, sealedBoxes, openedQuantities, takeFromNewlyOpened };
}
export function selectionTotal(p: StockPackaging, s: PackagingSelection): number {
  return s.sealedBoxes * p.packSize + s.openedQuantities.reduce((a, b) => a + b, 0)
    + s.takeFromNewlyOpened.reduce((a, b) => a + b, 0);
}

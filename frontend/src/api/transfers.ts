import { apiRequest } from './http';

export type TransferBusinessStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'COMPLETED'
  | 'CLOSED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'VOIDED';

export type TransferOutboundStatus =
  | 'NOT_RELEASED'
  | 'PENDING'
  | 'PARTIAL'
  | 'DISPATCHED'
  | 'CLOSED';

export type TransferInboundStatus =
  | 'NOT_IN_TRANSIT'
  | 'IN_TRANSIT'
  | 'PARTIAL_RECEIPT'
  | 'RECEIVED'
  | 'EXCEPTION'
  | 'CLOSED';

export type TransferApprovalMode = 'DIRECT' | 'ALWAYS';
export type TransferWarehouseDirection = 'OUTBOUND' | 'INBOUND';
export type TransferReceiptDisposition = 'AVAILABLE' | 'DAMAGED' | 'QUARANTINED';
export type TransferAllocationStatus =
  | 'ACTIVE'
  | 'PARTIALLY_CONSUMED'
  | 'CONSUMED'
  | 'PARTIALLY_RELEASED'
  | 'RELEASED';
export type TransferWarehouseTaskStatus = 'EXPECTED' | 'PENDING' | 'PARTIAL' | 'COMPLETED' | 'EXCEPTION' | 'CLOSED';

export interface TransferAllocation {
  id: number;
  sourceBatchId: number;
  batchCode: string;
  externalBatchCode?: string | null;
  locationId: number;
  locationCode: string;
  productionDate?: string | null;
  expiryDate?: string | null;
  allocatedQuantity: number;
  consumedQuantity: number;
  releasedQuantity: number;
  remainingQuantity: number;
  status: TransferAllocationStatus;
}

export interface TransferLine {
  id: number;
  version: number;
  productSkuId: number;
  skuCode: string;
  productName: string;
  preferredBatchId?: number | null;
  backorderLineId?: number | null;
  requestedQuantity: number;
  allocatedQuantity: number;
  dispatchedQuantity: number;
  inTransitQuantity: number;
  receivedQuantity: number;
  damagedQuantity: number;
  quarantinedQuantity: number;
  closedUnshippedQuantity: number;
  shortageClosedQuantity: number;
  allocations: TransferAllocation[];
}

export interface TransferEvent {
  id: number;
  eventType: string;
  actorId: number;
  actorName: string;
  occurredAt: string;
  payloadJson?: string | null;
}

export interface TransferDispatchLine {
  id: number;
  transferLineId: number;
  allocationId: number;
  sourceBatchId: number;
  sourceBatchCode: string;
  externalBatchCode?: string | null;
  productionDate?: string | null;
  expiryDate?: string | null;
  quantity: number;
  receivedQuantity: number;
  damagedQuantity: number;
  quarantinedQuantity: number;
  shortageClosedQuantity: number;
  openQuantity: number;
}

export interface TransferDispatch {
  id: number;
  dispatchNumber: string;
  handoverReference?: string | null;
  estimatedArrivalAt?: string | null;
  warehouseRemark?: string | null;
  dispatchedBy: number;
  dispatchedByName: string;
  dispatchedAt: string;
  lines: TransferDispatchLine[];
}

export interface TransferReceiptLine {
  id: number;
  dispatchLineId: number;
  transferLineId: number;
  locationId: number;
  locationCode: string;
  destinationBatchId: number;
  destinationBatchCode: string;
  disposition: TransferReceiptDisposition;
  quantity: number;
}

export interface TransferReceipt {
  id: number;
  receiptNumber: string;
  receivedBy: number;
  receivedByName: string;
  receivedAt: string;
  remark?: string | null;
  lines: TransferReceiptLine[];
}

export interface Transfer {
  id: number;
  version: number;
  transferNumber: string;
  sourceWarehouseId: number;
  sourceWarehouseCode: string;
  sourceWarehouseName: string;
  destinationWarehouseId: number;
  destinationWarehouseCode: string;
  destinationWarehouseName: string;
  businessStatus: TransferBusinessStatus;
  outboundStatus: TransferOutboundStatus;
  inboundStatus: TransferInboundStatus;
  approvalModeSnapshot: TransferApprovalMode;
  receivingInspectionSnapshot: boolean;
  reason: string;
  estimatedArrivalAt?: string | null;
  requestedQuantity: number;
  allocatedQuantity: number;
  dispatchedQuantity: number;
  inTransitQuantity: number;
  receivedQuantity: number;
  damagedQuantity: number;
  quarantinedQuantity: number;
  closedQuantity: number;
  requestedBy: number;
  requestedByName: string;
  submittedAt?: string | null;
  reviewedByName?: string | null;
  reviewedBy?: number | null;
  reviewedAt?: string | null;
  reviewComment?: string | null;
  terminatedAt?: string | null;
  terminationReason?: string | null;
  createdAt: string;
  updatedAt: string;
  lines: TransferLine[];
  dispatches: TransferDispatch[];
  receipts: TransferReceipt[];
  events: TransferEvent[];
}

export interface TransferReferenceWarehouse {
  id: number;
  code: string;
  name: string;
}

export interface TransferReferenceSku {
  id: number;
  skuCode: string;
  name: string;
  barcode?: string | null;
}

export interface TransferReferenceBackorder {
  id: number;
  salesOrderId: number;
  productSkuId: number;
  remainingQuantity: number;
  promisedDate?: string | null;
}

export interface TransferReferences {
  warehouses: TransferReferenceWarehouse[];
  skus: TransferReferenceSku[];
  backorders: TransferReferenceBackorder[];
}

export interface TransferBatchCandidate {
  id: number;
  batchCode: string;
  externalBatchCode?: string | null;
  locationId: number;
  locationCode: string;
  productionDate?: string | null;
  expiryDate?: string | null;
  availableQuantity: number;
}

export interface TransferDraftLinePayload {
  productSkuId: number;
  quantity: number;
  preferredBatchId?: number;
  backorderLineId?: number;
}

export interface TransferDraftPayload {
  sourceWarehouseId: number;
  destinationWarehouseId: number;
  reason: string;
  estimatedArrivalAt?: string;
  lines: TransferDraftLinePayload[];
}

export interface TransferUpdatePayload extends TransferDraftPayload {
  version: number;
}

export interface TransferPolicy {
  approvalMode: TransferApprovalMode;
  receivingInspectionEnabled: boolean;
}

export interface TransferPolicyUpdatePayload extends TransferPolicy {
  reason: string;
}

export interface TransferDecisionPayload {
  version: number;
  comment: string;
}

export interface TransferReasonPayload {
  version: number;
  reason: string;
}

export interface TransferCloseShortagePayload {
  version: number;
  reason: string;
  confirmed: true;
}

export interface TransferWarehouseAllocation {
  allocationId: number;
  sourceBatchId: number;
  sourceBatchCode: string;
  externalBatchCode?: string | null;
  sourceLocationId: number;
  sourceLocationCode: string;
  productionDate?: string | null;
  expiryDate?: string | null;
  remainingQuantity: number;
}

export interface TransferWarehouseTaskLine {
  transferLineId: number;
  productSkuId: number;
  skuCode: string;
  productName: string;
  requestedQuantity: number;
  allocatedQuantity: number;
  dispatchedQuantity: number;
  receivedQuantity: number;
  inTransitQuantity: number;
  allocations: TransferWarehouseAllocation[];
}

export interface TransferWarehouseDispatchLine {
  dispatchLineId: number;
  dispatchNumber: string;
  transferLineId: number;
  productSkuId: number;
  skuCode: string;
  productName: string;
  sourceBatchId: number;
  sourceBatchCode: string;
  externalBatchCode?: string | null;
  productionDate?: string | null;
  expiryDate?: string | null;
  dispatchedQuantity: number;
  receivedQuantity: number;
  openQuantity: number;
}

/**
 * Deliberately warehouse-safe. Commercial reason, approval comments, backorder/customer,
 * supplier, cost, carrier and tracking data do not belong on this surface.
 */
export interface TransferWarehouseTask {
  id: number;
  version: number;
  direction: TransferWarehouseDirection;
  taskNumber: string;
  transferNumber: string;
  status: TransferWarehouseTaskStatus;
  outboundStatus: TransferOutboundStatus;
  inboundStatus: TransferInboundStatus;
  sourceWarehouseId: number;
  sourceWarehouseName: string;
  destinationWarehouseId: number;
  destinationWarehouseName: string;
  priority: number;
  expectedFrom?: string | null;
  expectedTo?: string | null;
  estimatedArrivalAt?: string | null;
  assignee?: string | null;
  teamName?: string | null;
  operationArea?: string | null;
  warehouseRemark?: string | null;
  transferId: number;
  businessStatus: TransferBusinessStatus;
  lines: TransferWarehouseTaskLine[];
  dispatchLines: TransferWarehouseDispatchLine[];
}

export interface TransferTaskLocation {
  id: number;
  locationCode: string;
  locationName?: string | null;
}

export interface TransferWarehouseTaskUpdatePayload {
  version: number;
  priority?: number;
  expectedFrom?: string;
  expectedTo?: string;
  assignee?: string;
  teamName?: string;
  operationArea?: string;
  warehouseRemark?: string;
}

export interface TransferDispatchPayload {
  version: number;
  handoverReference?: string;
  estimatedArrivalAt?: string;
  warehouseRemark?: string;
  lines: Array<{ allocationId: number; quantity: number }>;
}

export interface TransferReceiptPayload {
  version: number;
  remark?: string;
  lines: Array<{
    dispatchLineId: number;
    locationId: number;
    quantity: number;
    disposition: TransferReceiptDisposition;
  }>;
}

export const TRANSFERS_QUERY_KEY = ['transfers'] as const;
export const TRANSFER_REFERENCES_QUERY_KEY = ['transfer-references'] as const;
export const TRANSFER_POLICY_QUERY_KEY = ['transfer-policy'] as const;
export const TRANSFER_WAREHOUSE_TASKS_QUERY_KEY = ['transfer-warehouse-tasks'] as const;

export function createTransferIdempotencyKey(operation: string, resource: string | number): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `transfer-${operation}-${resource}-${suffix}`;
}

export function createTransferCommandFingerprint(
  resource: string | number,
  payload: object,
): string {
  return JSON.stringify({ resource, payload });
}

export function listTransfers(): Promise<Transfer[]> {
  return apiRequest<Transfer[]>('/api/transfers');
}

export function getTransfer(id: number): Promise<Transfer> {
  return apiRequest<Transfer>(`/api/transfers/${id}`);
}

export function getTransferReferences(): Promise<TransferReferences> {
  return apiRequest<TransferReferences>('/api/transfers/references');
}

export function listTransferBatches(warehouseId: number, productSkuId: number): Promise<TransferBatchCandidate[]> {
  const search = new URLSearchParams({ warehouseId: String(warehouseId), productSkuId: String(productSkuId) });
  return apiRequest<TransferBatchCandidate[]>(`/api/transfers/batches?${search.toString()}`);
}

export function createTransfer(payload: TransferDraftPayload, idempotencyKey: string): Promise<Transfer> {
  return apiRequest<Transfer>('/api/transfers', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export function updateTransfer(id: number, payload: TransferUpdatePayload, idempotencyKey: string): Promise<Transfer> {
  return apiRequest<Transfer>(`/api/transfers/${id}`, {
    method: 'PUT', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export type TransferAction =
  | 'submit'
  | 'approve'
  | 'reject'
  | 'cancel'
  | 'reallocate'
  | 'close-remainder'
  | 'close-shortage'
  | 'void';

export interface TransferActionPayloadMap {
  submit: TransferDecisionPayload;
  approve: TransferDecisionPayload;
  reject: TransferDecisionPayload;
  cancel: TransferReasonPayload;
  reallocate: TransferDecisionPayload;
  'close-remainder': TransferReasonPayload;
  'close-shortage': TransferCloseShortagePayload;
  void: TransferReasonPayload;
}

export type TransferActionRequest = {
  [TAction in TransferAction]: { action: TAction; payload: TransferActionPayloadMap[TAction] }
}[TransferAction];

export function runTransferAction<TAction extends TransferAction>(
  id: number,
  action: TAction,
  payload: TransferActionPayloadMap[TAction],
  idempotencyKey: string,
): Promise<Transfer> {
  return apiRequest<Transfer>(`/api/transfers/${id}/${action}`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export function getTransferPolicy(): Promise<TransferPolicy> {
  return apiRequest<TransferPolicy>('/api/transfers/policy');
}

export function updateTransferPolicy(payload: TransferPolicyUpdatePayload, idempotencyKey: string): Promise<TransferPolicy> {
  return apiRequest<TransferPolicy>('/api/transfers/policy', {
    method: 'PUT', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export function listTransferWarehouseTasks(direction: TransferWarehouseDirection): Promise<TransferWarehouseTask[]> {
  return apiRequest<TransferWarehouseTask[]>(`/api/transfer-warehouse/tasks?direction=${direction}`);
}

export function getTransferWarehouseTask(id: number): Promise<TransferWarehouseTask> {
  return apiRequest<TransferWarehouseTask>(`/api/transfer-warehouse/tasks/${id}`);
}

export function updateTransferWarehouseTask(
  id: number,
  payload: TransferWarehouseTaskUpdatePayload,
  idempotencyKey: string,
): Promise<TransferWarehouseTask> {
  return apiRequest<TransferWarehouseTask>(`/api/transfer-warehouse/tasks/${id}`, {
    method: 'PUT', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export function listTransferTaskLocations(id: number): Promise<TransferTaskLocation[]> {
  return apiRequest<TransferTaskLocation[]>(`/api/transfer-warehouse/tasks/${id}/locations`);
}

export function dispatchTransferTask(
  id: number,
  payload: TransferDispatchPayload,
  idempotencyKey: string,
): Promise<TransferWarehouseTask> {
  return apiRequest<TransferWarehouseTask>(`/api/transfer-warehouse/tasks/${id}/dispatches`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

export function receiveTransferTask(
  id: number,
  payload: TransferReceiptPayload,
  idempotencyKey: string,
): Promise<TransferWarehouseTask> {
  return apiRequest<TransferWarehouseTask>(`/api/transfer-warehouse/tasks/${id}/receipts`, {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: payload,
  });
}

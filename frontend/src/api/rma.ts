import { apiRequest } from './http';

export type RmaType = 'RETURN_REFUND' | 'EXCHANGE' | 'REFUND_ONLY';
export type RmaBusinessStatus = 'PENDING_APPROVAL' | 'AUTHORIZED' | 'REJECTED' | 'CANCELLED' | 'CLOSED';
export type RmaReceiptStatus = 'NOT_EXPECTED' | 'EXPECTED' | 'PARTIAL' | 'RECEIVED' | 'CLOSED';
export type RmaInspectionStatus = 'NOT_REQUIRED' | 'PENDING' | 'PARTIAL' | 'COMPLETED';
export type RmaResolutionStatus = 'PENDING' | 'REFUND_REQUESTED' | 'REFUND_CONFIRMED' | 'EXCHANGE_CREATED' | 'REJECTED_GOODS_PENDING' | 'COMPLETED' | 'EXCEPTION';
export type RmaTaskStatus = 'EXPECTED' | 'PARTIAL' | 'RECEIVED' | 'CLOSED';
export type RmaDisposition = 'RESTOCK_AVAILABLE' | 'DAMAGED' | 'QUARANTINE' | 'REJECT_CUSTOMER';
export type RmaApprovalMode = 'DIRECT' | 'RISK_BASED' | 'ALWAYS';
export type RmaStatutoryBasis = 'VOLUNTARY_POLICY' | 'DISTANCE_CANCELLATION' | 'FAULTY_NOT_AS_DESCRIBED' | 'OTHER_STATUTORY';
export type RmaChannelCaseStatus = 'UNMATCHED_PENDING' | 'MATCHED_PENDING' | 'RMA_CREATED' | 'DISMISSED';

export interface RmaChannelCase {
  id: number; channel: string; storeIdentifier: string; externalCaseId: string;
  externalOrderId?: string; salesOrderId?: number; salesOrderNumber?: string;
  status: RmaChannelCaseStatus; rmaId?: number; receivedAt: string; matchedAt?: string;
}

export interface RmaLine {
  id: number; salesOrderItemId: number; outboundTaskId: number; originalBatchId: number;
  originalBatchCode: string; productSkuId: number; skuCode: string; productName: string;
  shippedQuantity: number; requestedQuantity: number; authorizedQuantity: number;
  receivedQuantity: number; inspectedQuantity: number; restockQuantity: number;
  damagedQuantity: number; quarantineQuantity: number; rejectedQuantity: number;
  refundEligibleQuantity: number; unitPriceSnapshot: number;
}

export interface RmaTask {
  id: number; version: number; taskNumber: string; status: RmaTaskStatus; priority: number;
  expectedFrom?: string; expectedTo?: string; assignee?: string; teamName?: string;
  operationArea?: string; warehouseRemark?: string;
}

export interface RmaReceiptLine {
  id: number; rmaLineId: number; locationId: number; locationCode: string;
  returnBatchId: number; returnBatchCode: string; qualityStatus: string;
  quantity: number; inspectedQuantity: number;
}

export interface RmaReceipt {
  id: number; receiptNumber: string; receivedBy: number; receivedByName: string;
  receivedAt: string; remark?: string; lines: RmaReceiptLine[];
}

export interface RmaInspectionLine {
  id: number; receiptLineId: number; disposition: RmaDisposition; quantity: number;
  refundEligibleQuantity: number; resultBatchId: number; resultBatchCode: string;
  resultQualityStatus: string;
}

export interface RmaInspection {
  id: number; inspectionNumber: string; inspectedBy: number; inspectedByName: string;
  inspectedAt: string; reasonDetail?: string; lines: RmaInspectionLine[];
}

export interface RmaEvent {
  id: number; eventType: string; payloadJson: string; actorId: number;
  actorName: string; occurredAt: string;
}

export interface Rma {
  id: number; version: number; rmaNumber: string; salesOrderId: number; salesOrderNo: string;
  shipmentId?: number; warehouseId: number; warehouseName: string; customerId: number;
  customerName: string; rmaType: RmaType; businessStatus: RmaBusinessStatus;
  receiptStatus: RmaReceiptStatus; inspectionStatus: RmaInspectionStatus;
  resolutionStatus: RmaResolutionStatus; approvalModeSnapshot: RmaApprovalMode;
  customerTypeSnapshot: 'CLIENT' | 'CONSUMER'; statutoryBasis: RmaStatutoryBasis;
  standardReturnDaysSnapshot: number; outsideStandardWindow: boolean; reasonCode: string;
  reasonDetail?: string; customerShippedAt?: string; returnCarrier?: string;
  returnTrackingNo?: string; refundAmount?: number; refundCurrency?: string;
  refundReference?: string; advanceResolution: boolean; replacementSalesOrderId?: number;
  channel?: string; externalCaseId?: string; requestedBy: number; requestedByName: string;
  requestedAt: string; reviewedBy?: number; reviewedByName?: string; reviewedAt?: string;
  reviewComment?: string; lines: RmaLine[]; task?: RmaTask; receipts: RmaReceipt[];
  inspections: RmaInspection[]; events: RmaEvent[];
}

export interface RmaWarehouseTask extends RmaTask {
  rmaNumber: string; warehouseId: number; warehouseName: string;
  lines: Array<{ rmaLineId: number; productSkuId: number; skuCode: string; productName: string; expectedQuantity: number; receivedQuantity: number }>;
  receiptLines: Array<{ id: number; rmaLineId: number; productSkuId: number; skuCode: string; productName: string; locationId: number; locationCode: string; returnBatchId: number; returnBatchCode: string; quantity: number; inspectedQuantity: number }>;
}

export interface RmaPolicy {
  approvalMode: RmaApprovalMode; standardReturnDays: number; refundOnlyEnabled: boolean;
  advanceResolutionEnabled: boolean; quarantineStagingEnabled: boolean;
}

export interface CreateRmaPayload {
  salesOrderId: number; shipmentId?: number; warehouseId: number; rmaType: RmaType;
  statutoryBasis: RmaStatutoryBasis; reasonCode: string; reasonDetail?: string;
  customerShippedAt?: string; returnCarrier?: string; returnTrackingNo?: string;
  channelCaseId?: number;
  lines: Array<{ outboundTaskId: number; quantity: number }>;
}

export const RMA_QUERY_KEY = ['rmas'] as const;
export const RMA_TASK_QUERY_KEY = ['rma-warehouse-tasks'] as const;

function key(operation: string, resource: string | number): string {
  const suffix = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `rma-${operation}-${resource}-${suffix}`;
}

export function listRmas(salesOrderId?: number): Promise<Rma[]> {
  return apiRequest<Rma[]>(`/api/rmas${salesOrderId ? `?salesOrderId=${salesOrderId}` : ''}`);
}

export function listRmaChannelCases(): Promise<RmaChannelCase[]> {
  return apiRequest<RmaChannelCase[]>('/api/rmas/channel-cases');
}

export function getRma(id: number): Promise<Rma> { return apiRequest<Rma>(`/api/rmas/${id}`); }
export function createRma(payload: CreateRmaPayload): Promise<Rma> {
  return apiRequest<Rma>('/api/rmas', { method: 'POST', headers: { 'Idempotency-Key': key('create', payload.salesOrderId) }, body: payload });
}
export function decideRma(id: number, decision: 'approve' | 'reject', comment: string): Promise<Rma> {
  return apiRequest<Rma>(`/api/rmas/${id}/${decision}`, { method: 'POST', headers: { 'Idempotency-Key': key(decision, id) }, body: { comment } });
}
export function terminateRma(id: number, operation: 'cancel' | 'close-remainder', reason: string): Promise<Rma> {
  return apiRequest<Rma>(`/api/rmas/${id}/${operation}`, { method: 'POST', headers: { 'Idempotency-Key': key(operation, id) }, body: { reason } });
}
export function setRefundEligibility(id: number, reason: string, lines: Array<{ rmaLineId: number; eligibleQuantity: number }>): Promise<Rma> {
  return apiRequest<Rma>(`/api/rmas/${id}/refund-eligibility`, { method: 'POST', headers: { 'Idempotency-Key': key('eligibility', id) }, body: { reason, lines } });
}
export function requestRmaResolution(id: number, payload: { resolutionType: 'REFUND' | 'EXCHANGE'; refundAmount?: number; currency?: string; advance: boolean; reason?: string; exchangeLines?: Array<{ rmaLineId: number; quantity: number }> }): Promise<Rma> {
  return apiRequest<Rma>(`/api/rmas/${id}/resolution`, { method: 'POST', headers: { 'Idempotency-Key': key('resolution', id) }, body: payload });
}
export function confirmRmaRefund(id: number, payload: { reference: string; amount: number; currency: string; succeeded: boolean; failureReason?: string }): Promise<Rma> {
  return apiRequest<Rma>(`/api/rmas/${id}/refund-confirmation`, { method: 'POST', headers: { 'Idempotency-Key': key('refund-confirm', id) }, body: payload });
}
export function getRmaPolicy(): Promise<RmaPolicy> { return apiRequest<RmaPolicy>('/api/rmas/policy'); }
export function updateRmaPolicy(payload: RmaPolicy & { reason: string }): Promise<RmaPolicy> {
  return apiRequest<RmaPolicy>('/api/rmas/policy', {
    method: 'PUT', headers: { 'Idempotency-Key': key('policy', 'tenant') }, body: payload,
  });
}
export function listRmaWarehouseTasks(status?: RmaTaskStatus): Promise<RmaWarehouseTask[]> {
  return apiRequest<RmaWarehouseTask[]>(`/api/rma-warehouse/tasks${status ? `?status=${status}` : ''}`);
}
export function updateRmaWarehouseTask(id: number, payload: Partial<Pick<RmaTask, 'priority' | 'expectedFrom' | 'expectedTo' | 'assignee' | 'teamName' | 'operationArea' | 'warehouseRemark'>>): Promise<RmaWarehouseTask> {
  return apiRequest<RmaWarehouseTask>(`/api/rma-warehouse/tasks/${id}`, { method: 'PUT', headers: { 'Idempotency-Key': key('task', id) }, body: payload });
}
export function receiveRmaTask(id: number, payload: { remark?: string; lines: Array<{ rmaLineId: number; locationId: number; quantity: number }> }): Promise<RmaWarehouseTask> {
  return apiRequest<RmaWarehouseTask>(`/api/rma-warehouse/tasks/${id}/receipts`, { method: 'POST', headers: { 'Idempotency-Key': key('receive', id) }, body: payload });
}
export function inspectRmaTask(id: number, payload: { reasonDetail?: string; lines: Array<{ receiptLineId: number; disposition: RmaDisposition; quantity: number }> }): Promise<RmaWarehouseTask> {
  return apiRequest<RmaWarehouseTask>(`/api/rma-warehouse/tasks/${id}/inspections`, { method: 'POST', headers: { 'Idempotency-Key': key('inspect', id) }, body: payload });
}

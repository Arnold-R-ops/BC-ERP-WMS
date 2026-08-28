import { apiRequest } from './http';

export type OtherInboundBusinessType =
  | 'FREE_SAMPLE'
  | 'CUSTOMER_SUPPLIED'
  | 'PRODUCTION_COMPLETION'
  | 'SUBCONTRACT_RETURN'
  | 'AUTHORIZED_SPECIAL';
export type OtherInboundStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_RECEIVED' | 'COMPLETED' | 'CLOSED' | 'CANCELLED' | 'VOIDED';

export interface OtherInboundPolicy {
  enabled: boolean;
  allowedBusinessTypes: OtherInboundBusinessType[];
}

export interface OtherInboundPolicyUpdatePayload extends OtherInboundPolicy {
  reason: string;
  confirmed: true;
}

export interface OtherInboundApplicationItemInput {
  productSkuId: number;
  plannedQuantity: number;
  productionDate?: string;
  expiryDate?: string;
  externalBatchCode?: string;
  remark?: string;
}

export interface OtherInboundApplicationPayload {
  businessType: OtherInboundBusinessType;
  sourceReference: string;
  businessReason: string;
  warehouseId: number;
  items: OtherInboundApplicationItemInput[];
  remark?: string;
}

export interface OtherInboundApplicationUpdatePayload extends OtherInboundApplicationPayload {
  version: number;
}

export interface OtherInboundReferenceSku {
  id: number;
  skuCode: string;
  name: string;
  barcode?: string;
  expiryManagementEnabled: boolean;
}

export interface OtherInboundReferenceWarehouse {
  id: number;
  code: string;
  name: string;
}

export interface OtherInboundApplicationItem extends OtherInboundApplicationItemInput {
  id: number;
  productName?: string;
  productBarcode?: string;
  receivedQuantity?: number;
  closedQuantity?: number;
}

export interface OtherInboundApplication {
  id: number;
  version: number;
  applicationNumber: string;
  businessType: OtherInboundBusinessType;
  sourceReference: string;
  businessReason: string;
  warehouseId: number;
  warehouseName?: string;
  legacyInboundOrderId?: number;
  status: OtherInboundStatus;
  plannedQuantity?: number;
  receivedQuantity?: number;
  createdByName?: string;
  confirmedAt?: string;
  terminationReason?: string;
  remark?: string;
  items: OtherInboundApplicationItem[];
  createdAt?: string;
  updatedAt?: string;
}

export const OTHER_INBOUND_QUERY_KEY = ['other-inbound-applications'] as const;

export async function getOtherInboundPolicy(): Promise<OtherInboundPolicy> {
  return apiRequest<OtherInboundPolicy>('/api/other-inbound-policy');
}

export async function updateOtherInboundPolicy(
  payload: OtherInboundPolicyUpdatePayload,
): Promise<OtherInboundPolicy> {
  return apiRequest<OtherInboundPolicy>('/api/other-inbound-policy', {
    method: 'PUT', body: payload,
  });
}

export async function listOtherInboundApplications(status?: OtherInboundStatus): Promise<OtherInboundApplication[]> {
  const search = new URLSearchParams({ page: '0', size: '500' });
  if (status) search.set('status', status);
  return apiRequest<OtherInboundApplication[]>(`/api/other-inbound-applications?${search.toString()}`);
}

export async function getOtherInboundApplication(id: number): Promise<OtherInboundApplication> {
  return apiRequest<OtherInboundApplication>(`/api/other-inbound-applications/${id}`);
}

export async function listOtherInboundReferenceSkus(): Promise<OtherInboundReferenceSku[]> {
  return apiRequest<OtherInboundReferenceSku[]>('/api/other-inbound-applications/reference/skus');
}

export async function listOtherInboundReferenceWarehouses(): Promise<OtherInboundReferenceWarehouse[]> {
  return apiRequest<OtherInboundReferenceWarehouse[]>('/api/other-inbound-applications/reference/warehouses');
}

export async function createOtherInboundApplication(payload: OtherInboundApplicationPayload): Promise<OtherInboundApplication> {
  return apiRequest<OtherInboundApplication>('/api/other-inbound-applications', { method: 'POST', body: payload });
}

export async function updateOtherInboundApplication(
  id: number,
  payload: OtherInboundApplicationUpdatePayload,
): Promise<OtherInboundApplication> {
  return apiRequest<OtherInboundApplication>(`/api/other-inbound-applications/${id}`, {
    method: 'PUT', body: payload,
  });
}

export async function runOtherInboundAction(
  id: number,
  action: 'confirm' | 'cancel' | 'void' | 'close',
  version: number,
  reason?: string,
): Promise<OtherInboundApplication> {
  return apiRequest<OtherInboundApplication>(`/api/other-inbound-applications/${id}/${action}`, {
    method: 'POST', body: { version, reason },
  });
}

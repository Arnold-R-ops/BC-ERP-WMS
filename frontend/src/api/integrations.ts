import type { components } from './schema';
import { apiRequest } from './http';
import type { PageLike } from './pagination';

export type IntegrationConfig = components['schemas']['IntegrationConfigResponse'];
export type IntegrationConfigPayload = components['schemas']['IntegrationConfigRequest'];
export type ChannelSkuMapping = components['schemas']['ChannelSkuMappingResponse'];
export type ChannelSkuMappingPayload = components['schemas']['ChannelSkuMappingRequest'];
export type PendingSkuMapping = components['schemas']['PendingSkuMapping'];
export type ResolvePendingSkuPayload = components['schemas']['ResolvePendingSkuRequest'];
export type RawEventSummary = components['schemas']['RawEventSummary'];
export type ChannelRawEvent = components['schemas']['ChannelRawEvent'];

export interface ConnectionTestResult {
  configId?: number;
  storeUrl?: string;
  connected?: boolean;
  shopName?: string;
  domain?: string;
  currency?: string;
  timezone?: string;
  plan?: string;
}

export interface ShopifySyncResult {
  success: number;
  skipped: number;
  failed: number;
}

export interface ProductSuggestion {
  productId: number;
  barcode: string;
  skuName: string;
  name: string;
}

export interface PendingSkuListParams {
  channel?: string;
  status?: string;
}

export interface RawEventListParams {
  channel?: string;
  status?: string;
  page?: number;
  size?: number;
}

export const INTEGRATION_CONFIGS_QUERY_KEY = ['integration-configs'] as const;
export const SKU_MAPPINGS_QUERY_KEY = ['channel-sku-mappings'] as const;
export const PENDING_SKU_QUERY_KEY = ['pending-sku-mappings'] as const;
export const RAW_EVENTS_QUERY_KEY = ['channel-raw-events'] as const;

export function listIntegrationConfigs(): Promise<IntegrationConfig[]> {
  return apiRequest<IntegrationConfig[]>('/api/integration/configs');
}

export function createIntegrationConfig(payload: IntegrationConfigPayload): Promise<IntegrationConfig> {
  return apiRequest<IntegrationConfig>('/api/integration/configs', { method: 'POST', body: payload });
}

export function updateIntegrationConfig(id: number, payload: IntegrationConfigPayload): Promise<IntegrationConfig> {
  return apiRequest<IntegrationConfig>(`/api/integration/configs/${id}`, { method: 'PUT', body: payload });
}

export function setIntegrationConfigActive(id: number, active: boolean): Promise<IntegrationConfig> {
  return apiRequest<IntegrationConfig>(`/api/integration/configs/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'PUT' });
}

export function deleteIntegrationConfig(id: number): Promise<void> {
  return apiRequest<void>(`/api/integration/configs/${id}`, { method: 'DELETE' });
}

export function testIntegrationConnection(id: number): Promise<ConnectionTestResult> {
  return apiRequest<ConnectionTestResult>(`/api/integration/configs/${id}/test-connection`, { method: 'POST' });
}

export function syncShopifyOrders(): Promise<ShopifySyncResult> {
  return apiRequest<ShopifySyncResult>('/api/integration/shopify/sync', { method: 'POST' });
}

export function listSkuMappings(channel = 'SHOPIFY'): Promise<ChannelSkuMapping[]> {
  const search = new URLSearchParams({ channel });
  return apiRequest<ChannelSkuMapping[]>(`/api/integration/sku-mappings?${search.toString()}`);
}

export function createSkuMapping(payload: ChannelSkuMappingPayload): Promise<ChannelSkuMapping> {
  return apiRequest<ChannelSkuMapping>('/api/integration/sku-mappings', { method: 'POST', body: payload });
}

export function updateSkuMapping(id: number, payload: ChannelSkuMappingPayload): Promise<ChannelSkuMapping> {
  return apiRequest<ChannelSkuMapping>(`/api/integration/sku-mappings/${id}`, { method: 'PUT', body: payload });
}

export function listPendingSkuMappings(params: PendingSkuListParams = {}): Promise<PendingSkuMapping[]> {
  const search = new URLSearchParams();
  if (params.channel) search.set('channel', params.channel);
  if (params.status) search.set('status', params.status);
  const query = search.size > 0 ? `?${search.toString()}` : '';
  return apiRequest<PendingSkuMapping[]>(`/api/integration/sku-mappings/pending${query}`);
}

export function getPendingSkuSuggestions(id: number): Promise<ProductSuggestion[]> {
  return apiRequest<ProductSuggestion[]>(`/api/integration/sku-mappings/pending/${id}/suggestions`);
}

export function resolvePendingSku(id: number, payload: ResolvePendingSkuPayload): Promise<PendingSkuMapping> {
  return apiRequest<PendingSkuMapping>(`/api/integration/sku-mappings/pending/${id}/resolve`, { method: 'POST', body: payload });
}

export function listRawEvents(params: RawEventListParams = {}): Promise<PageLike<RawEventSummary>> {
  const search = new URLSearchParams({
    channel: params.channel ?? 'SHOPIFY',
    status: params.status ?? 'MANUAL_REVIEW',
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  return apiRequest<PageLike<RawEventSummary>>(`/api/integration/raw-events?${search.toString()}`);
}

export function getRawEvent(id: number): Promise<ChannelRawEvent> {
  return apiRequest<ChannelRawEvent>(`/api/integration/raw-events/${id}`);
}

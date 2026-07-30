import { apiRequest } from './http';

export type CustomerFactType = 'CLIENT' | 'CONSUMER';
export type CustomerFactSource = 'MANUAL' | 'CHANNEL';

export interface SalesDailySummary {
  summaryDate: string;
  totalOrderCount: number;
  totalAmount: number;
  draftCount: number;
  pendingApprovalCount: number;
  approvedAwaitingShipmentCount: number;
  shippedCount: number;
  rejectedCount: number;
  cancelledCount: number;
  voidedCount: number;
  refreshedAt?: string;
}

export interface SalesOverview extends Omit<SalesDailySummary, 'summaryDate'> {
  startDate: string;
  endDate: string;
  averageOrderValue: number;
}

export interface CustomerProductFact {
  productSkuId: number;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  barcode?: string;
  totalOrderCount: number;
  totalQuantity: number;
  totalAmount: number;
  firstOrderDate?: string;
  lastOrderDate?: string;
  averageIntervalDays: number;
}

export interface CustomerFact {
  customerId: number;
  customerCode?: string;
  customerName?: string;
  customerType?: CustomerFactType;
  source?: CustomerFactSource;
  totalOrderCount: number;
  totalAmount: number;
  averageOrderValue: number;
  lastOrderDate?: string;
  averageIntervalDays: number;
  refreshedAt?: string;
}

export interface CustomerFactDetail extends CustomerFact {
  topProducts: CustomerProductFact[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CustomerFactParams {
  keyword?: string;
  customerType?: CustomerFactType;
  source?: CustomerFactSource;
  sortBy?: 'totalAmount' | 'totalOrderCount' | 'lastOrderDate' | 'averageIntervalDays';
  sortDirection?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

export const REPORTS_QUERY_KEY = ['reports'] as const;

function dateQuery(startDate: string, endDate: string): string {
  return new URLSearchParams({ startDate, endDate }).toString();
}

export async function getSalesOverview(startDate: string, endDate: string): Promise<SalesOverview> {
  return apiRequest<SalesOverview>(`/api/reports/sales/overview?${dateQuery(startDate, endDate)}`);
}

export async function listSalesDaily(startDate: string, endDate: string): Promise<SalesDailySummary[]> {
  return apiRequest<SalesDailySummary[]>(`/api/reports/sales/daily?${dateQuery(startDate, endDate)}`);
}

export async function listCustomerFacts(params: CustomerFactParams): Promise<PageResponse<CustomerFact>> {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  return apiRequest<PageResponse<CustomerFact>>(`/api/reports/customers?${search.toString()}`);
}

export async function getCustomerFact(customerId: number, topProducts = 8): Promise<CustomerFactDetail> {
  return apiRequest<CustomerFactDetail>(
    `/api/reports/customer/${customerId}?topProducts=${topProducts}`,
  );
}

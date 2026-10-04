import { apiBlobRequest, apiRequest } from './http';
export interface CompanyProfile { name: string; address?: string; phone?: string; email?: string; terms?: string }
export interface DocumentLine { productSkuId: number; skuCode: string; description: string; unit: string; quantity: number; unitPrice: number; amount: number }
export interface Receipt { id: number; kind: 'RECEIPT'|'REVERSAL'; reversesId?: number; amount: number; paymentDate: string; method: string; reference?: string; note?: string; createdBy: number; createdAt: string }
export interface CommercialDocument { id: number; version: number; number: string; kind: 'QUOTE'|'INVOICE'; status: string; customerId: number; customerName: string; billingAddress?: string; shippingAddress?: string; company: CompanyProfile; documentDate: string; dueDate: string; currency: string; terms?: string; total: number; received: number; outstanding: number; salesOrderId?: number; salesOrderNo?: string; orderAmountChanged: boolean; closeReason?: string; lines: DocumentLine[]; receipts: Receipt[] }
export interface QuoteInput { requestKey: string; version?: number; customerId: number; validUntil: string; shippingAddress?: string; terms?: string; lines: Array<{ productSkuId: number; quantity: number; unitPrice: number }> }
export interface ReceiptInput { requestKey: string; amount: number; paymentDate: string; method: string; reference?: string; note?: string }
export interface DocumentPage { content: CommercialDocument[]; totalElements: number }
const base='/api/commercial';
export const getCompanyProfile=(): Promise<CompanyProfile> => apiRequest(`${base}/profile`);
export const saveCompanyProfile=(body: CompanyProfile): Promise<CompanyProfile> => apiRequest(`${base}/profile`,{method:'PUT',body});
export const listDocuments=(kind: 'quotes'|'invoices',page=0,salesOrderId?: number): Promise<DocumentPage> => apiRequest(`${base}/${kind}?page=${page}&size=20${salesOrderId ? `&salesOrderId=${salesOrderId}`:''}`);
export const getDocument=(kind: 'quotes'|'invoices',id: number): Promise<CommercialDocument> => apiRequest(`${base}/${kind}/${id}`);
export const saveQuote=(body: QuoteInput,id?: number): Promise<CommercialDocument> => apiRequest(`${base}/quotes${id ? `/${id}`:''}`,{method:id?'PUT':'POST',body});
export const convertQuote=(id: number): Promise<CommercialDocument> => apiRequest(`${base}/quotes/${id}/convert`,{method:'POST'});
export const closeDocument=(kind: 'quotes'|'invoices',id: number,reason: string): Promise<CommercialDocument> => apiRequest(`${base}/${kind}/${id}/${kind==='quotes'?'cancel':'void'}`,{method:'POST',body:{reason}});
export const createInvoice=(salesOrderId: number,dueDate: string,requestKey: string): Promise<CommercialDocument> => apiRequest(`${base}/invoices`,{method:'POST',body:{salesOrderId,dueDate,requestKey}});
export const recordReceipt=(id: number,body: ReceiptInput): Promise<CommercialDocument> => apiRequest(`${base}/invoices/${id}/receipts`,{method:'POST',body});
export const reverseReceipt=(id: number,receiptId: number,reason: string): Promise<CommercialDocument> => apiRequest(`${base}/invoices/${id}/receipts/${receiptId}/reverse`,{method:'POST',body:{reason}});
export const exportDocument=(kind: 'quotes'|'invoices',id: number,format: 'pdf'|'xlsx'): Promise<Blob> => apiBlobRequest(`${base}/${kind}/${id}/${format}`);

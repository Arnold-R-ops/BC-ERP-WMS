export type WorkflowDomain = 'sales' | 'purchasing' | 'inbound' | 'outbound' | 'transfers';

const statusColors: Record<string, string> = {
  DRAFT: 'default',
  ORDERING: 'processing',
  PENDING_APPROVAL: 'gold',
  APPROVED_PLAN: 'blue',
  IN_TRANSIT: 'cyan',
  AWAITING_RECEIVAL: 'purple',
  PARTIALLY_RECEIVED: 'orange',
  PARTIAL_RECEIPT: 'orange',
  PARTIAL: 'orange',
  PARTIALLY_ALLOCATED: 'orange',
  WAITING_INBOUND: 'gold',
  PENDING: 'gold',
  PICKING: 'blue',
  CREATED: 'default',
  COUNTING: 'processing',
  REVIEWING: 'gold',
  PENDING_REVIEW: 'gold',
  APPROVED: 'blue',
  DISPATCHED: 'green',
  RECEIVED: 'green',
  NOT_RELEASED: 'default',
  NOT_IN_TRANSIT: 'default',
  EXCEPTION: 'red',
  APPLIED: 'green',
  COMPLETED: 'green',
  SHIPPED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
  VOIDED: 'default',
};

export function getStatusColor(status?: string): string {
  return status ? statusColors[status] ?? 'default' : 'default';
}

export function formatMoney(value: number | undefined, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'GBP',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value ?? 0);
}

export function formatDate(value: string | null | undefined, locale: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? '-'
    : new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date);
}

export function formatDateTime(value: string | null | undefined, locale: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '-'
    : new Intl.DateTimeFormat(locale, {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
}

export function difference(actual?: number, planned?: number): number {
  return (actual ?? 0) - (planned ?? 0);
}

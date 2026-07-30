export type WorkflowDomain = 'sales' | 'purchasing' | 'inbound' | 'outbound';

const statusColors: Record<string, string> = {
  DRAFT: 'default',
  ORDERING: 'processing',
  PENDING_APPROVAL: 'gold',
  APPROVED_PLAN: 'blue',
  APPROVED_AWAITING_SHIPMENT: 'cyan',
  IN_TRANSIT: 'cyan',
  AWAITING_RECEIVAL: 'purple',
  PARTIALLY_RECEIVED: 'orange',
  PARTIALLY_ALLOCATED: 'orange',
  WAITING_INBOUND: 'gold',
  PENDING: 'gold',
  PICKING: 'blue',
  CREATED: 'default',
  COUNTING: 'processing',
  REVIEWING: 'gold',
  PENDING_REVIEW: 'gold',
  APPROVED: 'blue',
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

export function formatDate(value: string | undefined, locale: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? '-'
    : new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date);
}

export function formatDateTime(value: string | undefined, locale: string): string {
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

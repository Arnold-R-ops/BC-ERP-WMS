export type FreshnessStatus = 'expired' | 'nearExpiry' | 'fresh' | 'unknown';

export type InventoryQualityStatus =
  | 'AVAILABLE'
  | 'PENDING_INSPECTION'
  | 'QUARANTINED'
  | 'DAMAGED'
  | 'EXPIRED'
  | 'RECALL_HOLD'
  | 'RETURN_INSPECTION';

export const inventoryQualityStatuses: InventoryQualityStatus[] = [
  'AVAILABLE',
  'PENDING_INSPECTION',
  'QUARANTINED',
  'DAMAGED',
  'EXPIRED',
  'RECALL_HOLD',
  'RETURN_INSPECTION',
];

export function getQualityStatusColor(status?: InventoryQualityStatus): string {
  switch (status) {
    case 'AVAILABLE': return 'success';
    case 'PENDING_INSPECTION':
    case 'RETURN_INSPECTION': return 'processing';
    case 'QUARANTINED':
    case 'RECALL_HOLD': return 'warning';
    case 'DAMAGED':
    case 'EXPIRED': return 'error';
    default: return 'default';
  }
}

const MILLISECONDS_PER_DAY = 86_400_000;

export function getDaysUntilExpiry(expiryDate?: string, now = new Date()): number | undefined {
  if (!expiryDate) {
    return undefined;
  }

  const expiry = new Date(`${expiryDate}T00:00:00`);
  if (Number.isNaN(expiry.getTime())) {
    return undefined;
  }

  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.ceil((expiry.getTime() - today.getTime()) / MILLISECONDS_PER_DAY);
}

export function getFreshnessStatus(
  expiryDate?: string,
  nearExpiryDays = 90,
  now = new Date(),
): FreshnessStatus {
  const days = getDaysUntilExpiry(expiryDate, now);
  if (days === undefined) {
    return 'unknown';
  }
  if (days < 0) {
    return 'expired';
  }
  if (days <= nearExpiryDays) {
    return 'nearExpiry';
  }
  return 'fresh';
}

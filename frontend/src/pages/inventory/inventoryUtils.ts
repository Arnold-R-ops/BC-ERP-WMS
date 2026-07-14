export type FreshnessStatus = 'expired' | 'nearExpiry' | 'fresh' | 'unknown';

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

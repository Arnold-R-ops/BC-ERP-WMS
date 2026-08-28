import { describe, expect, it } from 'vitest';
import {
  getDaysUntilExpiry,
  getFreshnessStatus,
  getQualityStatusColor,
} from './inventoryUtils';

const now = new Date(2026, 6, 12, 15, 30);

describe('inventory freshness utilities', () => {
  it('uses calendar days without depending on the current time of day', () => {
    expect(getDaysUntilExpiry('2026-07-13', now)).toBe(1);
  });

  it('classifies expired, near-expiry, and fresh batches', () => {
    expect(getFreshnessStatus('2026-07-11', 30, now)).toBe('expired');
    expect(getFreshnessStatus('2026-08-01', 30, now)).toBe('nearExpiry');
    expect(getFreshnessStatus('2027-01-01', 30, now)).toBe('fresh');
  });

  it('uses distinct quality-state severity colors', () => {
    expect(getQualityStatusColor('AVAILABLE')).toBe('success');
    expect(getQualityStatusColor('QUARANTINED')).toBe('warning');
    expect(getQualityStatusColor('DAMAGED')).toBe('error');
  });
});

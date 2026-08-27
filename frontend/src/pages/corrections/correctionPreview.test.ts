import { describe, expect, it } from 'vitest';
import type { EmergencyCorrectionImpact } from '../../api/emergencyCorrections';
import { getCorrectionPreviewState } from './correctionPreview';

const impact: EmergencyCorrectionImpact = {
  correctionId: 41,
  snapshotSystemQty: 100,
  countedQty: 90,
  adjustmentQty: -10,
  currentQty: 95,
  reservedQty: 80,
  projectedQty: 85,
  canApply: true,
  affectedSalesOrderIds: [],
  previewToken: 'preview-token-41',
  previewExpiresAt: '2026-08-25T08:10:00Z',
};

describe('emergency correction preview token', () => {
  it('accepts only a present, unexpired token', () => {
    expect(getCorrectionPreviewState(impact, Date.parse('2026-08-25T08:00:00Z'))).toBe('READY');
  });

  it('requires a fresh preview when the token is missing', () => {
    expect(getCorrectionPreviewState({ ...impact, previewToken: ' ' })).toBe('MISSING');
  });

  it('requires a fresh preview when the token has expired', () => {
    expect(getCorrectionPreviewState(impact, Date.parse('2026-08-25T08:10:00Z'))).toBe('EXPIRED');
  });
});

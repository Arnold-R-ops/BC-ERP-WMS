import type { EmergencyCorrectionImpact } from '../../api/emergencyCorrections';

export type CorrectionPreviewState = 'READY' | 'MISSING' | 'EXPIRED';

export function getCorrectionPreviewState(
  impact: EmergencyCorrectionImpact | undefined,
  now = Date.now(),
): CorrectionPreviewState {
  if (!impact?.previewToken?.trim()) return 'MISSING';
  const expiresAt = Date.parse(impact.previewExpiresAt);
  if (!Number.isFinite(expiresAt)) return 'MISSING';
  return expiresAt > now ? 'READY' : 'EXPIRED';
}

import { afterEach, describe, expect, it } from 'vitest';
import {
  clearCorrectionIdempotencyKey,
  getStableCorrectionIdempotencyKey,
  hasCorrectionIdempotencyKey,
} from './correctionIdempotency';

describe('emergency correction idempotency keys', () => {
  afterEach(() => {
    clearCorrectionIdempotencyKey(41);
    clearCorrectionIdempotencyKey(42);
  });

  it('retains one key for repeated attempts of the same correction', () => {
    const first = getStableCorrectionIdempotencyKey(41);
    const retry = getStableCorrectionIdempotencyKey(41);

    expect(retry).toBe(first);
    expect(first).toMatch(/^esc-41-/);
  });

  it('uses distinct keys per correction and rotates only after success clears it', () => {
    const first = getStableCorrectionIdempotencyKey(41);
    const otherCorrection = getStableCorrectionIdempotencyKey(42);
    clearCorrectionIdempotencyKey(41);
    const afterSuccess = getStableCorrectionIdempotencyKey(41);

    expect(otherCorrection).not.toBe(first);
    expect(afterSuccess).not.toBe(first);
  });

  it('reports whether a retained attempt exists before an operator starts a new request', () => {
    expect(hasCorrectionIdempotencyKey(41)).toBe(false);
    getStableCorrectionIdempotencyKey(41);
    expect(hasCorrectionIdempotencyKey(41)).toBe(true);
    clearCorrectionIdempotencyKey(41);
    expect(hasCorrectionIdempotencyKey(41)).toBe(false);
  });
});

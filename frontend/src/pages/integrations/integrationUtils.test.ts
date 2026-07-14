import { describe, expect, it } from 'vitest';
import { formatRawPayload, isNoSkuExternal } from './integrationUtils';

describe('integration utilities', () => {
  it('recognizes synthetic no-SKU rows', () => {
    expect(isNoSkuExternal('NOSKU::Shipping fee')).toBe(true);
    expect(isNoSkuExternal('TOP0002 - 1KG')).toBe(false);
  });

  it('formats valid JSON and preserves malformed payloads', () => {
    expect(formatRawPayload('{"order":{"id":7}}')).toContain('\n  "order"');
    expect(formatRawPayload('not-json')).toBe('not-json');
    expect(formatRawPayload()).toBe('-');
  });
});

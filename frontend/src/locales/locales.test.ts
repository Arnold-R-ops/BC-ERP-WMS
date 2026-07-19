import { describe, expect, it } from 'vitest';
import enUS from './en-US';
import zhCN from './zh-CN';

function collectKeys(value: unknown, prefix = ''): string[] {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return prefix ? [prefix] : [];
  }

  return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return collectKeys(child, path);
  });
}

describe('locale resources', () => {
  it('keeps zh-CN and en-US translation key sets aligned', () => {
    expect(collectKeys(enUS).sort()).toEqual(collectKeys(zhCN).sort());
  });
});

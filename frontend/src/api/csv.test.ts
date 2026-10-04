import { describe, expect, it } from 'vitest';
import { toCsv } from './csv';

describe('CSV export', () => {
  it('preserves Chinese, commas, quotes, newlines and empty cells', () => {
    expect(toCsv([['袋,瓶', 'a"b\nc', null]])).toBe('\uFEFF"袋,瓶","a""b\nc",""\r\n');
  });
  it('neutralizes spreadsheet formulas in text but preserves numeric quantities', () => {
    expect(toCsv([['=1+2', ' @SUM(A1)', '-3', -3]])).toBe('\uFEFF"\'=1+2","\' @SUM(A1)","\'-3","-3"\r\n');
  });
});

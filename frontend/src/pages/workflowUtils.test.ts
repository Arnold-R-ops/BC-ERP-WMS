import { describe, expect, it } from 'vitest';
import { difference, formatDate, getStatusColor } from './workflowUtils';

describe('workflow utilities', () => {
  it('maps workflow states to stable colors', () => {
    expect(getStatusColor('PENDING_APPROVAL')).toBe('gold');
    expect(getStatusColor('COMPLETED')).toBe('green');
    expect(getStatusColor('UNRECOGNIZED')).toBe('default');
  });

  it('formats invalid dates defensively', () => {
    expect(formatDate(undefined, 'en-US')).toBe('-');
    expect(formatDate('not-a-date', 'en-US')).toBe('-');
  });

  it('calculates quantity differences', () => {
    expect(difference(8, 10)).toBe(-2);
    expect(difference(undefined, 5)).toBe(-5);
  });
});

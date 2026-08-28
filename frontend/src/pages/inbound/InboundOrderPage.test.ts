import { describe, expect, it } from 'vitest';
import { getOtherInboundAvailableActions } from './InboundOrderPage';

describe('other inbound state actions', () => {
  it('exposes only the operations permitted by each application state', () => {
    expect(getOtherInboundAvailableActions('DRAFT', true)).toEqual(['edit', 'confirm', 'void']);
    expect(getOtherInboundAvailableActions('CONFIRMED', true)).toEqual(['cancel']);
    expect(getOtherInboundAvailableActions('PARTIALLY_RECEIVED', true)).toEqual(['close']);
    expect(getOtherInboundAvailableActions('COMPLETED', true)).toEqual([]);
    expect(getOtherInboundAvailableActions('CLOSED', true)).toEqual([]);
  });

  it('hides every mutation without other_inbound:manage', () => {
    expect(getOtherInboundAvailableActions('DRAFT', false)).toEqual([]);
    expect(getOtherInboundAvailableActions('PARTIALLY_RECEIVED', false)).toEqual([]);
  });

  it('blocks entry actions but keeps terminal actions when the feature switch is disabled', () => {
    expect(getOtherInboundAvailableActions('DRAFT', true, false)).toEqual(['void']);
    expect(getOtherInboundAvailableActions('CONFIRMED', true, false)).toEqual(['cancel']);
    expect(getOtherInboundAvailableActions('PARTIALLY_RECEIVED', true, false)).toEqual(['close']);
  });
});

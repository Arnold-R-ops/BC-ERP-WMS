import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  confirmRmaRefund, createRma, inspectRmaTask, receiveRmaTask, setRefundEligibility,
  updateRmaPolicy,
} from './rma';

describe('RMA API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(async () =>
      new Response(JSON.stringify({ id: 1 }), { status: 200 }));
  });

  it('creates only a linked return using completed-dispatch evidence', async () => {
    await createRma({
      salesOrderId: 10,
      warehouseId: 2,
      rmaType: 'RETURN_REFUND',
      statutoryBasis: 'VOLUNTARY_POLICY',
      reasonCode: 'CUSTOMER_CHANGED_MIND',
      lines: [{ outboundTaskId: 88, quantity: 2 }],
    });

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    const body = JSON.parse(String(options?.body));
    expect(body.lines).toEqual([{ outboundTaskId: 88, quantity: 2 }]);
    expect(body).not.toHaveProperty('operatorId');
    expect(body).not.toHaveProperty('operatorName');
    expect(body).not.toHaveProperty('unlinkedSource');
    expect(new Headers(options?.headers).get('Idempotency-Key')).toContain('rma-create-10-');
  });

  it('keeps receipt and inspection in the warehouse task surface', async () => {
    await receiveRmaTask(5, {
      lines: [{ rmaLineId: 9, locationId: 3, quantity: 2 }],
    });
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/rma-warehouse/tasks/5/receipts', expect.objectContaining({ method: 'POST' }),
    );

    await inspectRmaTask(5, {
      lines: [{ receiptLineId: 11, disposition: 'DAMAGED', quantity: 2 }],
    });
    const inspectionBody = JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body));
    expect(inspectionBody.lines[0]).not.toHaveProperty('refundEligibleQuantity');
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/rma-warehouse/tasks/5/inspections', expect.objectContaining({ method: 'POST' }),
    );
  });

  it('records refund eligibility as a separate tenant-side business decision', async () => {
    await setRefundEligibility(4, 'Customer refund policy accepted', [
      { rmaLineId: 9, eligibleQuantity: 2 },
    ]);

    expect(fetch).toHaveBeenCalledWith(
      '/api/rmas/4/refund-eligibility', expect.objectContaining({ method: 'POST' }),
    );
  });

  it('records an external refund confirmation without calling a payment endpoint', async () => {
    await confirmRmaRefund(4, {
      reference: 'MANUAL-REF-1', amount: 12.5, currency: 'GBP', succeeded: true,
    });

    expect(fetch).toHaveBeenCalledWith(
      '/api/rmas/4/refund-confirmation', expect.objectContaining({ method: 'POST' }),
    );
  });

  it('updates tenant RMA policy as an idempotent command', async () => {
    await updateRmaPolicy({
      approvalMode: 'DIRECT', standardReturnDays: 5, refundOnlyEnabled: false,
      advanceResolutionEnabled: false, quarantineStagingEnabled: false,
      reason: 'Initial tenant policy',
    });

    const options = vi.mocked(fetch).mock.calls[0]?.[1];
    expect(fetch).toHaveBeenCalledWith('/api/rmas/policy', expect.objectContaining({ method: 'PUT' }));
    expect(new Headers(options?.headers).get('Idempotency-Key')).toContain('rma-policy-tenant-');
  });
});

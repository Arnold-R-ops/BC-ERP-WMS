import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { changeLanguage } from '../locales/i18n';
import { ClientFormModal } from '../pages/customers/ClientFormModal';
import { SupplierFormModal } from '../pages/suppliers/SupplierFormModal';

// Audit of current behavior, paired with the isolated real-controller HTTP boundary probe.
// Values above 255 are currently admitted by these forms but rejected by the API.
describe('address length audit, 2026-09-10', () => {
  beforeEach(async () => { await changeLanguage('en-US'); });
  afterEach(cleanup);
  for (const entity of ['customer', 'supplier'] as const) {
    for (const character of ['A', '测']) {
      it.each([254, 255, 256, 500])(`${entity} admits %i ${character} characters`, async (length) => {
        const address = character.repeat(length);
        const onSubmit = vi.fn(async (_values: { address?: string }) => true);
        const record = { id: 1, code: 'TEST-ADDRESS', name: 'Boundary Test', address };
        render(entity === 'customer'
          ? <ClientFormModal customer={record} loading={false} onClose={vi.fn()} onSubmit={onSubmit} open />
          : <SupplierFormModal supplier={record} loading={false} onClose={vi.fn()} onSubmit={onSubmit} open />);
        const input = await screen.findByDisplayValue(address);
        expect(input).toHaveAttribute('maxlength', '500');
        fireEvent.click(screen.getByRole('button', { name: /^Save$/ }));
        await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
        expect(onSubmit.mock.calls[0][0].address).toBe(address);
      });
    }
  }
});

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { changeLanguage } from '../locales/i18n';
import { ClientFormModal } from '../pages/customers/ClientFormModal';
import { SupplierFormModal } from '../pages/suppliers/SupplierFormModal';

// The 2026-09-10 audit found that these forms admitted more than the API's 255 characters.
// The approved simplification now aligns input and submit validation with that existing limit.
describe('address length boundary regression', () => {
  beforeEach(async () => { await changeLanguage('en-US'); });
  afterEach(cleanup);
  for (const entity of ['customer', 'supplier'] as const) {
    for (const character of ['A', '测']) {
      it.each([254, 255, 256, 500])(`${entity} enforces the 255-character limit for %i ${character} characters`, async (length) => {
        const address = character.repeat(length);
        const onSubmit = vi.fn(async (_values: { address?: string }) => true);
        const record = { id: 1, code: 'TEST-ADDRESS', name: 'Boundary Test', address };
        render(entity === 'customer'
          ? <ClientFormModal customer={record} loading={false} onClose={vi.fn()} onSubmit={onSubmit} open />
          : <SupplierFormModal supplier={record} loading={false} onClose={vi.fn()} onSubmit={onSubmit} open />);
        const input = await screen.findByDisplayValue(address);
        expect(input).toHaveAttribute('maxlength', '255');
        fireEvent.click(screen.getByRole('button', { name: /^Save$/ }));
        if (length <= 255) {
          await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
          expect(onSubmit.mock.calls[0][0].address).toBe(address);
        } else {
          await waitFor(() => expect(screen.getByDisplayValue(address).closest('.ant-form-item'))
            .toHaveClass('ant-form-item-has-error'));
          expect(onSubmit).not.toHaveBeenCalled();
        }
      });
    }
  }
});

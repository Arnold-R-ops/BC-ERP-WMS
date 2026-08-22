import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { changeLanguage } from '../../locales/i18n';
import { ClientFormModal } from './ClientFormModal';

describe('client form localization', () => {
  beforeEach(async () => {
    await changeLanguage('en-US');
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the new account and tax fields entirely in English', async () => {
    render(
      <ClientFormModal
        loading={false}
        onClose={vi.fn()}
        onSubmit={vi.fn(async () => true)}
        open
      />,
    );

    const dialog = await screen.findByRole('dialog');
    expect(screen.getByRole('tab', { name: 'Account details' })).toBeInTheDocument();
    expect(screen.getByText('Client details')).toBeInTheDocument();
    expect(screen.getByText('Billing address')).toBeInTheDocument();
    expect(dialog.textContent).not.toMatch(/[\u4e00-\u9fff]/);

    fireEvent.click(screen.getByRole('tab', { name: 'Tax settings' }));

    expect(screen.getByText('Client tax')).toBeInTheDocument();
    expect(screen.getByText('VAT registration')).toBeInTheDocument();
    expect(dialog.textContent).not.toMatch(/[\u4e00-\u9fff]/);
  });
});

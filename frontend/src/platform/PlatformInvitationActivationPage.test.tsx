import { App } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { PlatformInvitationActivationPage } from './PlatformInvitationActivationPage';

const mocks = vi.hoisted(() => ({ status: vi.fn(), activate: vi.fn(), confirm: vi.fn(), establish: vi.fn(), logout: vi.fn(), session: null as object | null }));
vi.mock('./api', () => ({
  getPlatformInvitationStatus: mocks.status, activatePlatformInvitation: mocks.activate,
  confirmPlatformInvitationActivation: mocks.confirm, PlatformInvitationError: class extends Error {},
}));
vi.mock('./PlatformAuthProvider', () => ({ usePlatformAuth: () => ({ session: mocks.session, logout: mocks.logout, establishSession: mocks.establish }) }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ i18n: { language: 'en-US' } }) }));

// All responses are in-memory mock fixtures, not user authentication or captured credentials.
const authenticated = { status: 'AUTHENTICATED', token: 'mock-token-not-a-jwt', email: 'mock@invalid.test',
  roles: ['PLATFORM_SECURITY_AUDITOR'], expiresIn: 43_200_000,
  recoveryCodes: Array.from({ length: 10 }, (_, i) => `mock-unused-code-${i}`) };
beforeEach(() => {
  vi.clearAllMocks(); mocks.establish.mockReset(); mocks.session = null;
  mocks.status.mockResolvedValue({ displayName: 'Synthetic invite', email: 'mock@invalid.test', invitationType: 'ORDINARY_ADMIN',
    roleCode: 'PLATFORM_SECURITY_AUDITOR', expiresAt: '2099-01-01T00:00:00Z' });
  mocks.activate.mockResolvedValue({ status: 'MFA_ENROLLMENT_REQUIRED', challengeToken: 'mock-challenge' });
  mocks.confirm.mockResolvedValue(authenticated);
});
afterEach(cleanup);
function page() {
  render(<App><MemoryRouter initialEntries={['/activate?token=mock-invitation']}><Routes>
    <Route path="/activate" element={<PlatformInvitationActivationPage />} />
    <Route path="/" element={<div>Platform home fixture</div>} />
    <Route path="/login" element={<div>Login fixture</div>} />
  </Routes></MemoryRouter></App>);
}
async function reachRecoveryConfirmation() {
  page();
  fireEvent.change(await screen.findByLabelText('New password'), { target: { value: 'Mock-Password-42!' } });
  fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'Mock-Password-42!' } });
  // Ant Design keeps the loading icon in its exit animation; its accessible name
  // may retain a 'loading' prefix even after the button accepts submissions.
  const activateButton = await screen.findByRole('button', { name: /Set password and bind MFA/ });
  await waitFor(() => expect(activateButton).not.toHaveClass('ant-btn-loading'));
  fireEvent.click(activateButton);
  fireEvent.change(await screen.findByLabelText('6-digit code'), { target: { value: '123456' } });
  fireEvent.click(await screen.findByRole('button', { name: /Bind and continue/ }));
  await screen.findByRole('button', { name: 'Enter platform' });
}

it('hands completed invitation authentication to the shared session provider only after recovery acknowledgement', async () => {
  await reachRecoveryConfirmation();
  expect(screen.getByRole('button', { name: 'Enter platform' })).toBeDisabled();
  expect(mocks.establish).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('checkbox', { name: 'I stored all recovery codes securely offline.' }));
  fireEvent.click(screen.getByRole('button', { name: 'Enter platform' }));
  await screen.findByText('Platform home fixture');
  expect(mocks.establish).toHaveBeenCalledExactlyOnceWith(authenticated);
});

it('returns to login instead of home if the shared provider rejects an interrupted activation session', async () => {
  mocks.establish.mockImplementation(() => { throw new Error('Mock logout boundary'); });
  await reachRecoveryConfirmation();
  fireEvent.click(screen.getByRole('checkbox', { name: 'I stored all recovery codes securely offline.' }));
  fireEvent.click(screen.getByRole('button', { name: 'Enter platform' }));
  await screen.findByText('Login fixture');
  expect(screen.queryByText('Platform home fixture')).not.toBeInTheDocument();
  expect(mocks.establish).toHaveBeenCalledTimes(1);
});

it('requires existing platform identity to sign out before starting invitation activation', async () => {
  mocks.session = { email: 'another-mock@invalid.test' };
  page();
  fireEvent.click(await screen.findByRole('button', { name: 'Sign out current account' }));
  await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
  expect(mocks.activate).not.toHaveBeenCalled();
  expect(mocks.confirm).not.toHaveBeenCalled();
  expect(mocks.establish).not.toHaveBeenCalled();
});

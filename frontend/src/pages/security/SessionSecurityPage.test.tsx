import { App as AntdApp, ConfigProvider } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';
import { SessionSecurityPage } from './SessionSecurityPage';

const authMocks = vi.hoisted(() => ({
  revokeAllSessions: vi.fn().mockResolvedValue(undefined),
  session: { currentRole: 'TENANT_ADMIN', username: 'admin' },
}));

const apiMocks = vi.hoisted(() => ({
  listSessionSecurityAudits: vi.fn().mockResolvedValue([{
    id: 3,
    action: 'ADMIN_REVOKE_ALL_SESSIONS',
    operatorId: 2,
    operatorUsername: 'admin',
    targetUserId: 69,
    targetUsername: 'warehouse.employee',
    reason: 'Reported suspicious activity',
    result: 'SUCCESS',
    createdAt: '2026-08-19T17:40:55',
  }]),
  listUsers: vi.fn().mockResolvedValue([
    { id: 2, username: 'admin', displayName: 'Administrator', roleCodes: ['TENANT_ADMIN'], enabled: true },
    { id: 69, username: 'warehouse.employee', displayName: 'Warehouse Employee', roleCodes: ['WAREHOUSE_STAFF'], enabled: true },
  ]),
  revokeUserSessions: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('../../auth/AuthProvider', () => ({ useAuth: () => authMocks }));
vi.mock('../../api/iam', () => ({
  listUsers: apiMocks.listUsers,
  revokeUserSessions: apiMocks.revokeUserSessions,
}));
vi.mock('../../api/sessionSecurity', () => ({
  listSessionSecurityAudits: apiMocks.listSessionSecurityAudits,
  SESSION_SECURITY_AUDITS_QUERY_KEY: ['session-security', 'audits'],
}));

function renderPage(): void {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <MemoryRouter>
      <ConfigProvider>
        <AntdApp>
          <QueryClientProvider client={client}><SessionSecurityPage /></QueryClientProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>,
  );
}

describe('tenant session security page', () => {
  beforeAll(async () => { await i18n.changeLanguage('en-US'); });
  beforeEach(() => {
    authMocks.session.currentRole = 'TENANT_ADMIN';
    vi.clearAllMocks();
  });
  afterEach(() => cleanup());

  it('requires a second confirmation before an administrator revokes employee sessions', async () => {
    renderPage();
    expect(await screen.findByText('Reported suspicious activity')).toBeVisible();

    fireEvent.mouseDown(screen.getAllByRole('combobox')[0] as HTMLElement);
    fireEvent.click((await screen.findAllByText(/Warehouse Employee/)).at(-1) as HTMLElement);
    fireEvent.change(screen.getByPlaceholderText(/employee reported suspicious/i), {
      target: { value: 'Employee reported suspicious account activity' },
    });
    fireEvent.click(screen.getByRole('button', { name: /review and confirm/i }));

    const dialog = await screen.findByRole('dialog', { name: /revoke every session/i });
    expect(dialog).toHaveTextContent('Employee reported suspicious account activity');
    expect(apiMocks.revokeUserSessions).not.toHaveBeenCalled();

    fireEvent.click(within(dialog).getByRole('button', { name: /revoke all sessions/i }));
    await waitFor(() => expect(apiMocks.revokeUserSessions).toHaveBeenCalledWith(
      69, 'Employee reported suspicious account activity',
    ));
  }, 15_000);

  it('shows personal policy without administrator data to an ordinary employee', async () => {
    authMocks.session.currentRole = 'WAREHOUSE_STAFF';
    renderPage();

    expect(screen.getByText('Personal security')).toBeVisible();
    expect(screen.getByRole('button', { name: /sign out all devices/i })).toBeVisible();
    expect(screen.queryByText('Employee session response')).not.toBeInTheDocument();
    expect(apiMocks.listUsers).not.toHaveBeenCalled();
    expect(apiMocks.listSessionSecurityAudits).not.toHaveBeenCalled();
  });
});

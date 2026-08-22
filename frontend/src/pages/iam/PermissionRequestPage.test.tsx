import { App as AntdApp, ConfigProvider } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';
import { PermissionRequestPage } from './PermissionRequestPage';

const apiMocks = vi.hoisted(() => {
  const request = {
    id: 1, targetUserId: 2, targetUsername: 'warehouse.user', requestedRoleId: 4,
    requestedRoleCode: 'WAREHOUSE_ADMIN', requestedRoleName: 'Warehouse administrator', warehouses: [],
    highRiskPermissionCount: 0, submittedAt: '2026-08-08T12:00:00Z', submittedByUsername: 'requester.admin',
    status: 'PENDING_REVIEW' as const,
  };
  return {
    request,
    createPermissionRequest: vi.fn().mockResolvedValue(request),
    listPermissionRequests: vi.fn().mockResolvedValue({ items: [request], total: 1, page: 0, size: 20 }),
    reviewPermissionRequest: vi.fn().mockResolvedValue({ ...request, status: 'APPROVED' }),
    revokePermissionRequest: vi.fn(),
  };
});

vi.mock('../../auth/AuthProvider', () => ({
  useAuth: () => ({ session: { username: 'admin', currentRole: 'TENANT_ADMIN' } }),
}));

vi.mock('../../api/iam', async () => {
  const actual = await vi.importActual<typeof import('../../api/iam')>('../../api/iam');
  return {
    ...actual,
    listUsers: vi.fn().mockResolvedValue([{ id: 2, username: 'warehouse.user', enabled: true, roleCodes: ['WAREHOUSE_STAFF'] }]),
    listRoles: vi.fn().mockResolvedValue([
      { id: 4, roleCode: 'WAREHOUSE_ADMIN', roleName: 'Warehouse administrator', roleType: 'SYSTEM', status: 'ACTIVE', permissionIds: [11] },
      { id: 1, roleCode: 'TENANT_ADMIN', roleName: 'Super administrator', roleType: 'SYSTEM', status: 'ACTIVE', privilegedRole: true },
    ]),
    listPermissions: vi.fn().mockResolvedValue([{ id: 11, permissionCode: 'inventory:view', permissionName: 'View inventory', permissionType: 'MENU', riskLevel: 'NORMAL', status: 'ACTIVE' }]),
    listAssignableWarehouses: vi.fn().mockResolvedValue([{ id: 48, code: 'WH-01', name: 'Main' }]),
    createPermissionRequest: apiMocks.createPermissionRequest,
    listPermissionRequests: apiMocks.listPermissionRequests,
    reviewPermissionRequest: apiMocks.reviewPermissionRequest,
    revokePermissionRequest: apiMocks.revokePermissionRequest,
  };
});

function renderPage(): void {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <ConfigProvider><AntdApp><QueryClientProvider client={client}><PermissionRequestPage /></QueryClientProvider></AntdApp></ConfigProvider>,
  );
}

describe('permission request API page', () => {
  beforeAll(async () => { await i18n.changeLanguage('en-US'); });
  afterEach(() => cleanup());

  it('submits and approves through the real permission request API adapters', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: /create permission request/i }));
    const dialog = await screen.findByRole('dialog');
    const selects = within(dialog).getAllByRole('combobox');
    fireEvent.mouseDown(selects[0] as HTMLElement);
    fireEvent.click((await screen.findAllByText('warehouse.user')).at(-1) as HTMLElement);
    fireEvent.mouseDown(selects[1] as HTMLElement);
    fireEvent.click((await screen.findAllByText('Warehouse administrator (WAREHOUSE_ADMIN)')).at(-1) as HTMLElement);
    fireEvent.click(within(dialog).getByRole('button', { name: /submit request/i }));
    await waitFor(() => expect(apiMocks.createPermissionRequest).toHaveBeenCalledWith(expect.objectContaining({
      targetUserId: 2, requestedRoleId: 4,
    }), expect.any(Object)));

    fireEvent.click(await screen.findByRole('button', { name: /approve request/i }));
    const approveButtons = await screen.findAllByRole('button', { name: /approve request/i });
    fireEvent.click(approveButtons.at(-1) as HTMLButtonElement);
    await waitFor(() => expect(apiMocks.reviewPermissionRequest).toHaveBeenCalledWith(1, true, undefined));
    expect(screen.getAllByText(/live permission operation/i).length).toBeGreaterThan(0);
  }, 15_000);
});

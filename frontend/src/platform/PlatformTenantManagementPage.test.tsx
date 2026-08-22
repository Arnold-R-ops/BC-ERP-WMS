import { App as AntdApp } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PlatformTenantManagementPage } from './PlatformTenantManagementPage';

const apiMocks = vi.hoisted(() => ({
  listPlatformTenants: vi.fn(),
  getPlatformTenant: vi.fn(),
  readPlatformTenantData: vi.fn(),
}));

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    listPlatformTenants: apiMocks.listPlatformTenants,
    getPlatformTenant: apiMocks.getPlatformTenant,
    readPlatformTenantData: apiMocks.readPlatformTenantData,
  };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: 'zh-CN' } }),
}));

describe('platform tenant management page', () => {
  afterEach(cleanup);

  beforeEach(() => {
    apiMocks.listPlatformTenants.mockReset();
    apiMocks.getPlatformTenant.mockReset();
    apiMocks.readPlatformTenantData.mockReset();
    apiMocks.listPlatformTenants.mockResolvedValue({
      content: [],
      number: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('loads the tenant directory on entry without selecting or reading a tenant', async () => {
    render(<AntdApp><PlatformTenantManagementPage access={{ superAdmin: true, scopes: [] }} /></AntdApp>);

    expect(screen.getByRole('heading', { name: '租户管理' })).toBeInTheDocument();
    await waitFor(() => expect(apiMocks.listPlatformTenants).toHaveBeenCalledWith(0, 20, {
      keyword: undefined,
      status: undefined,
    }));
    expect(screen.getByText('请选择租户')).toBeInTheDocument();
    expect(apiMocks.getPlatformTenant).not.toHaveBeenCalled();
    expect(apiMocks.readPlatformTenantData).not.toHaveBeenCalled();
  });

  it('does not search while the administrator is typing', async () => {
    render(<AntdApp><PlatformTenantManagementPage access={{ superAdmin: true, scopes: [] }} /></AntdApp>);

    await waitFor(() => expect(apiMocks.listPlatformTenants).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByPlaceholderText('租户名称或编码'), { target: { value: '华东' } });
    expect(apiMocks.listPlatformTenants).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));

    await waitFor(() => expect(apiMocks.listPlatformTenants).toHaveBeenCalledWith(0, 20, {
      keyword: '华东',
      status: undefined,
    }));
  });

  it('loads the overview first without selecting or reading a dataset', async () => {
    const directoryTenant = {
      id: 7, tenantCode: 'T-007', displayName: '租户甲', slug: 'tenant-a', status: 'ACTIVE' as const,
      closedAt: null, purgeDueAt: null, createdAt: '2026-08-21T00:00:00Z',
    };
    apiMocks.listPlatformTenants.mockResolvedValue({
      content: [directoryTenant], number: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    apiMocks.getPlatformTenant.mockResolvedValue({ ...directoryTenant, displayName: '租户甲（详情）' });

    render(<AntdApp><PlatformTenantManagementPage access={{ superAdmin: true, scopes: [] }} /></AntdApp>);
    fireEvent.click(await screen.findByRole('button', { name: /租户甲/ }));

    await waitFor(() => expect(apiMocks.getPlatformTenant).toHaveBeenCalledWith(7));
    expect(await screen.findByText('租户甲（详情）')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '数据浏览' })).not.toHaveAttribute('aria-disabled', 'true');
    expect(apiMocks.readPlatformTenantData).not.toHaveBeenCalled();
  });

  it('ignores a late overview response after switching tenants', async () => {
    type TenantResult = Awaited<ReturnType<typeof import('./api').getPlatformTenant>>;
    let resolveFirst!: (value: TenantResult) => void;
    let resolveSecond!: (value: TenantResult) => void;
    const first = new Promise<TenantResult>((resolve) => { resolveFirst = resolve; });
    const second = new Promise<TenantResult>((resolve) => { resolveSecond = resolve; });
    const tenants = [
      { id: 1, tenantCode: 'T-001', displayName: '租户一', slug: 'one', status: 'ACTIVE' as const, closedAt: null, purgeDueAt: null, createdAt: '2026-08-21T00:00:00Z' },
      { id: 2, tenantCode: 'T-002', displayName: '租户二', slug: 'two', status: 'ACTIVE' as const, closedAt: null, purgeDueAt: null, createdAt: '2026-08-21T00:00:00Z' },
    ];
    apiMocks.listPlatformTenants.mockResolvedValue({ content: tenants, number: 0, size: 20, totalElements: 2, totalPages: 1 });
    apiMocks.getPlatformTenant.mockImplementation((tenantId: number) => tenantId === 1 ? first : second);

    render(<AntdApp><PlatformTenantManagementPage access={{ superAdmin: true, scopes: [] }} /></AntdApp>);
    fireEvent.click(await screen.findByRole('button', { name: /租户一/ }));
    fireEvent.click(screen.getByRole('button', { name: /租户二/ }));

    await act(async () => resolveSecond({ ...tenants[1], displayName: '租户二详情' }));
    expect(await screen.findByText('租户二详情')).toBeInTheDocument();
    await act(async () => resolveFirst({ ...tenants[0], displayName: '旧请求租户一详情' }));

    expect(screen.queryByText('旧请求租户一详情')).not.toBeInTheDocument();
    expect(apiMocks.readPlatformTenantData).not.toHaveBeenCalled();
  });
});

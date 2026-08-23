import { App as AntdApp } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PlatformAdminManagementPage } from './PlatformAdminManagementPage';

const apiMocks = vi.hoisted(() => ({
  listPlatformAdmins: vi.fn(),
  getPlatformAdmin: vi.fn(),
  listPlatformAdminInvitations: vi.fn(),
  startPlatformAdminInvitation: vi.fn(),
  createPlatformAdminInvitation: vi.fn(),
  revokePlatformAdminInvitation: vi.fn(),
}));

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    listPlatformAdmins: apiMocks.listPlatformAdmins,
    getPlatformAdmin: apiMocks.getPlatformAdmin,
    listPlatformAdminInvitations: apiMocks.listPlatformAdminInvitations,
    startPlatformAdminInvitation: apiMocks.startPlatformAdminInvitation,
    createPlatformAdminInvitation: apiMocks.createPlatformAdminInvitation,
    revokePlatformAdminInvitation: apiMocks.revokePlatformAdminInvitation,
  };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language: 'zh-CN' } }),
}));

vi.mock('./PlatformAuthProvider', () => ({
  usePlatformAuth: () => ({
    session: { roles: ['PLATFORM_SUPER_ADMIN'] },
  }),
}));

const administrator = {
  id: 7,
  displayName: '张管理员',
  email: 'admin@example.com',
  enabled: true,
  roles: [
    { code: 'PLATFORM_SUPER_ADMIN', name: '平台超级管理员', type: 'JOB_ROLE' as const },
    { code: 'PLATFORM_TENANT_READ', name: '租户数据读取能力', type: 'TECHNICAL_CAPABILITY' as const },
  ],
  mfaStatus: 'ENROLLED' as const,
  mfaEnrolledAt: '2026-08-20T00:00:00Z',
  mfaLockedUntil: null,
  createdAt: '2026-08-18T00:00:00Z',
  updatedAt: '2026-08-20T00:00:00Z',
  activeGrantCount: 3,
  currentUser: true,
  lastEnabledSuperAdmin: true,
};

describe('platform administrator management page', () => {
  afterEach(cleanup);

  beforeEach(() => {
    Object.values(apiMocks).forEach((mock) => mock.mockReset());
    apiMocks.listPlatformAdmins.mockResolvedValue({
      content: [administrator], number: 0, size: 20, totalElements: 1, totalPages: 1,
    });
    apiMocks.getPlatformAdmin.mockResolvedValue({
      ...administrator,
      activeGrantCounts: { READ: 2, EXPORT: 1 },
    });
    apiMocks.listPlatformAdminInvitations.mockResolvedValue([]);
  });

  it('opens on the administrator directory and does not load invitations', async () => {
    render(<AntdApp><PlatformAdminManagementPage /></AntdApp>);

    expect(screen.getByRole('heading', { name: '平台管理员管理' })).toBeInTheDocument();
    await waitFor(() => expect(apiMocks.listPlatformAdmins).toHaveBeenCalledWith(0, 20, {}));
    expect(await screen.findByText('张管理员')).toBeInTheDocument();
    expect(screen.getByText('租户数据读取能力')).toBeInTheDocument();
    expect(screen.getByText('当前账号')).toBeInTheDocument();
    expect(screen.getByText('最后启用的超级管理员')).toBeInTheDocument();
    expect(apiMocks.listPlatformAdminInvitations).not.toHaveBeenCalled();
  });

  it('does not query while typing and applies filters only after search', async () => {
    render(<AntdApp><PlatformAdminManagementPage /></AntdApp>);
    await waitFor(() => expect(apiMocks.listPlatformAdmins).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('管理员姓名或邮箱'), { target: { value: '  安全  ' } });
    expect(apiMocks.listPlatformAdmins).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));

    await waitFor(() => expect(apiMocks.listPlatformAdmins).toHaveBeenLastCalledWith(0, 20, {
      keyword: '安全', enabled: undefined, role: undefined, mfaStatus: undefined,
    }));
  });

  it('opens a detail drawer with split grant counts and protected operations', async () => {
    render(<AntdApp><PlatformAdminManagementPage /></AntdApp>);
    fireEvent.click(await screen.findByRole('button', { name: '查看详情' }));

    await waitFor(() => expect(apiMocks.getPlatformAdmin).toHaveBeenCalledWith(7));
    expect(await screen.findByText('共 3 条 · 读取 2 · 导出 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '停用账号' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '重置 MFA' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '撤销全部会话' })).toBeDisabled();
  });

  it('loads the existing super-administrator invitation area only after switching tabs', async () => {
    render(<AntdApp><PlatformAdminManagementPage /></AntdApp>);
    await waitFor(() => expect(apiMocks.listPlatformAdmins).toHaveBeenCalledOnce());

    fireEvent.click(screen.getByRole('tab', { name: '超级管理员邀请' }));

    await waitFor(() => expect(apiMocks.listPlatformAdminInvitations).toHaveBeenCalledOnce());
    expect(screen.getByText(/这里只邀请另一名独立超级管理员/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /验证本人并创建邀请/ })).toBeInTheDocument();
    expect(screen.queryByText('PLATFORM_TENANT_READ')).not.toBeInTheDocument();
    expect(screen.queryByText('PLATFORM_TENANT_EXPORT')).not.toBeInTheDocument();
  });

  it('opens ordinary invitations with exactly the two approved job roles', async () => {
    render(<AntdApp><PlatformAdminManagementPage /></AntdApp>);
    await waitFor(() => expect(apiMocks.listPlatformAdmins).toHaveBeenCalledOnce());

    fireEvent.click(screen.getByRole('tab', { name: '普通管理员邀请' }));

    await waitFor(() => expect(apiMocks.listPlatformAdminInvitations).toHaveBeenCalledOnce());
    expect(screen.getByText(/普通管理员必须且只能选择一个岗位/)).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole('combobox'));
    expect(await screen.findByText('平台运营管理员')).toBeInTheDocument();
    expect(screen.getByText('平台安全审计员')).toBeInTheDocument();
    expect(screen.queryByText('PLATFORM_TENANT_READ')).not.toBeInTheDocument();
    expect(screen.queryByText('PLATFORM_TENANT_EXPORT')).not.toBeInTheDocument();
  });
});

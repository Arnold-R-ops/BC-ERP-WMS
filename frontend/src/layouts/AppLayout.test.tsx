import { App as AntdApp, ConfigProvider } from 'antd';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import { writeAuthSession } from '../auth/storage';
import '../locales/i18n';
import { AppLayout } from './AppLayout';

function setMobileViewport(matches: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      addEventListener: vi.fn(),
      addListener: vi.fn(),
      dispatchEvent: vi.fn(),
      matches,
      media: query,
      onchange: null,
      removeEventListener: vi.fn(),
      removeListener: vi.fn(),
    })),
    writable: true,
  });
}

describe('responsive application navigation', () => {
  beforeEach(() => {
    localStorage.clear();
    setMobileViewport(true);
    writeAuthSession({
      availableRoles: ['WAREHOUSE_STAFF'],
      currentRole: 'WAREHOUSE_STAFF',
      permissionCodes: ['menu:warehouse-mobile'],
      expiresAt: Date.now() + 2 * 60 * 60 * 1000,
      mustChangePassword: false,
      token: 'test-token',
      tokenType: 'Bearer',
      username: 'warehouse-user',
    });
  });

  it('opens a permission-filtered drawer and routes to receiving', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <ConfigProvider>
          <AntdApp>
            <AuthProvider>
              <Routes>
                <Route element={<AppLayout />}>
                  <Route element={<div>dashboard body</div>} index />
                </Route>
                <Route element={<div>receiving destination</div>} path="/mobile/receiving" />
              </Routes>
            </AuthProvider>
          </AntdApp>
        </ConfigProvider>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: '打开导航菜单' }));

    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText('warehouse-user · 库管员')).toBeVisible();
    expect(within(drawer).queryByText('销售订单')).not.toBeInTheDocument();
    expect(within(drawer).queryByText('采购管理')).not.toBeInTheDocument();

    fireEvent.click(within(drawer).getByText('仓库作业'));
    fireEvent.click(await within(drawer).findByText('收货'));

    expect(await screen.findByText('receiving destination')).toBeVisible();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('groups the desktop account controls and opens the account menu', async () => {
    setMobileViewport(false);

    render(
      <MemoryRouter initialEntries={['/']}>
        <ConfigProvider>
          <AntdApp>
            <AuthProvider>
              <Routes>
                <Route element={<AppLayout />}>
                  <Route element={<div>dashboard body</div>} index />
                </Route>
              </Routes>
            </AuthProvider>
          </AntdApp>
        </ConfigProvider>
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: '切换组织机构' })).toBeVisible();
    expect(screen.getByRole('button', { name: '语言' })).toBeVisible();
    expect(screen.getByRole('button', { name: '帮助' })).toBeVisible();
    expect(screen.getByRole('button', { name: '系统公告' })).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: '打开账户菜单' }));

    expect(await screen.findAllByRole('menuitem', { name: '账户设置' })).not.toHaveLength(0);
    expect(screen.getAllByRole('menuitem', { name: '个人资料' })).not.toHaveLength(0);
    expect(screen.getAllByRole('menuitem', { name: '修改密码' })).not.toHaveLength(0);
  });
});

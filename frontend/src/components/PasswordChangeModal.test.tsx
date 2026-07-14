import { App as AntdApp, ConfigProvider } from 'antd';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import '../locales/i18n';
import { PasswordChangeModal } from './PasswordChangeModal';

vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({
    changePassword: vi.fn(),
    logout: vi.fn(),
  }),
}));

describe('forced password change modal', () => {
  it('cannot be dismissed and still offers sign out', () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
            <PasswordChangeModal forced open />
          </MemoryRouter>
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByRole('dialog', { name: '首次登录必须修改密码' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /退出登录/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: '修改并重新登录' })).toBeEnabled();
  });
});

import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import type { Permission } from '../../api/iam';
import i18n from '../../locales/i18n';
import { RolePackageCreateModal } from './RolePackageCreateModal';

const normalPermission: Permission = {
  id: 11,
  permissionCode: 'inventory:view',
  permissionName: 'View inventory',
  permissionType: 'API',
  riskLevel: 'NORMAL',
  customAssignable: true,
  status: 'ACTIVE',
};
const highPermission: Permission = {
  id: 12,
  permissionCode: 'outbound:pick',
  permissionName: 'Confirm picking',
  permissionType: 'BUTTON',
  riskLevel: 'HIGH',
  customAssignable: true,
  status: 'ACTIVE',
};
const protectedPermission: Permission = {
  id: 13,
  permissionCode: 'system:admin',
  permissionName: 'System administration',
  permissionType: 'API',
  riskLevel: 'CRITICAL',
  customAssignable: false,
  status: 'ACTIVE',
};

describe('blank permission package creation', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('en-US');
  });

  afterEach(() => cleanup());

  it('creates a disabled custom draft from checked business permissions only', async () => {
    const onSubmit = vi.fn().mockResolvedValue(true);
    render(
      <ConfigProvider>
        <AntdApp>
          <RolePackageCreateModal
            loading={false}
            onClose={vi.fn()}
            onSubmit={onSubmit}
            open
            permissionCatalog={[normalPermission, highPermission, protectedPermission]}
            submitting={false}
          />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByText('View inventory')).toBeInTheDocument();
    expect(screen.getByText('Confirm picking')).toBeInTheDocument();
    expect(screen.queryByText('System administration')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Package code'), { target: { value: 'custom_sales' } });
    fireEvent.change(screen.getByLabelText('Package name'), { target: { value: 'Custom sales' } });
    fireEvent.change(screen.getByLabelText('Business purpose'), { target: { value: 'Sales order entry' } });
    const normalRow = screen.getByText('View inventory').closest('tr');
    fireEvent.click(normalRow?.querySelector('input[type="checkbox"]') as HTMLInputElement);
    fireEvent.click(screen.getByRole('button', { name: 'Create draft package' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({
      roleCode: 'CUSTOM_SALES',
      roleName: 'Custom sales',
      description: 'Sales order entry',
      permissionIds: [11],
    }));
  });
});

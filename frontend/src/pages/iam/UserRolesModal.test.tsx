import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import type { AssignableWarehouse, Role, UserAccount } from '../../api/iam';
import i18n from '../../locales/i18n';
import { UserRolesModal } from './UserRolesModal';

const warehouseStaffRole: Role = {
  id: 6,
  roleCode: 'WAREHOUSE_STAFF',
  roleName: 'Warehouse operator',
  roleType: 'SYSTEM',
  status: 'ACTIVE',
};

const warehouse: AssignableWarehouse = {
  id: 1,
  code: 'WH01',
  name: 'Main warehouse',
};

const warehouseStaff: UserAccount = {
  id: 9,
  username: 'warehouse-operator',
  roleIds: [6],
  roleCodes: ['WAREHOUSE_STAFF'],
  defaultRoleCode: 'WAREHOUSE_STAFF',
  warehouseIds: [],
};

describe('user role assignment modal', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('en-US');
  });

  afterEach(() => cleanup());

  it('restores the existing warehouse staff role after the destroyed modal mounts', async () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <UserRolesModal
            loading={false}
            onClose={vi.fn()}
            onSubmit={vi.fn().mockResolvedValue(true)}
            open
            roles={[warehouseStaffRole]}
            user={warehouseStaff}
            warehouses={[warehouse]}
          />
        </AntdApp>
      </ConfigProvider>,
    );

    expect((await screen.findAllByText('Warehouse operator (WAREHOUSE_STAFF)')).length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Work warehouses')).toBeInTheDocument();
  });
});

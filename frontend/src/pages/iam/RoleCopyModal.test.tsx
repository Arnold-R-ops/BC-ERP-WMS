import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';
import type { RoleCopyPreview } from '../../api/iam';
import { RoleCopyModal } from './RoleCopyModal';

const basePreview: RoleCopyPreview = {
  sourceRoleId: 3,
  sourceRoleCode: 'WAREHOUSE_STAFF',
  sourceRoleName: 'Warehouse operator',
  permissionCount: 2,
  normalRiskCount: 1,
  highRiskCount: 1,
  criticalRiskCount: 0,
  copyAllowed: true,
  snapshotFingerprint: 'a'.repeat(64),
  permissions: [
    { id: 1, permissionCode: 'inbound:receive_goods', permissionName: 'Receive goods', permissionType: 'BUTTON', riskLevel: 'HIGH' },
    { id: 2, permissionCode: 'inventory:view', permissionName: 'View inventory', permissionType: 'MENU', riskLevel: 'NORMAL' },
  ],
  highRiskPermissions: [
    { id: 1, permissionCode: 'inbound:receive_goods', permissionName: 'Receive goods', permissionType: 'BUTTON', riskLevel: 'HIGH' },
  ],
};

function renderModal(preview: RoleCopyPreview): void {
  render(
    <ConfigProvider>
      <AntdApp>
        <RoleCopyModal
          loading={false}
          onClose={vi.fn()}
          onSubmit={vi.fn().mockResolvedValue(true)}
          open
          preview={preview}
          source={{ id: 3, roleCode: 'WAREHOUSE_STAFF', roleName: 'Warehouse operator', status: 'ACTIVE' }}
          submitting={false}
        />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe('role permission package copy modal', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('en-US');
  });

  afterEach(() => cleanup());

  it('requires the enhanced confirmation surface for high-risk snapshots', () => {
    renderModal(basePreview);

    expect(screen.getByText('Enhanced confirmation required')).toBeInTheDocument();
    expect(screen.getByLabelText('High-risk grant reason')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /reviewed and accept copying 1 high-risk permissions/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Type the new package code to confirm')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirm risk and create' })).toBeEnabled();
  });

  it('blocks creation when protected permissions are present', () => {
    renderModal({
      ...basePreview,
      highRiskCount: 0,
      criticalRiskCount: 1,
      copyAllowed: false,
      blockers: ['CRITICAL_PERMISSION_PRESENT'],
      permissions: [{ id: 9, permissionCode: 'system:admin', permissionName: 'System admin', riskLevel: 'CRITICAL' }],
    });

    expect(screen.getByText('This template contains protected permissions')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create package' })).toBeDisabled();
    expect(screen.queryByLabelText('Package code')).not.toBeInTheDocument();
  });
});

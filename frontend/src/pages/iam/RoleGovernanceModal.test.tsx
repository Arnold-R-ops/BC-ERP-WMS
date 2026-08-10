import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';
import type { Permission, Role } from '../../api/iam';
import { RoleGovernanceModal, type RoleGovernanceMode } from './RoleGovernanceModal';

const normalPermission: Permission = {
  id: 11,
  permissionCode: 'inventory:view',
  permissionName: 'View inventory',
  permissionType: 'MENU',
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

const baseRole: Role = {
  id: 41,
  roleCode: 'CUSTOM_WAREHOUSE',
  roleName: 'Custom warehouse',
  roleType: 'CUSTOM',
  description: 'Warehouse operating package',
  status: 'DISABLED',
  reviewStatus: 'PENDING_REVIEW',
  approvalTemplateCode: 'ROLE_PACKAGE_SIMPLE_APPROVAL',
  reviewSubmittedByUsername: 'security.admin',
};

function renderModal(
  mode: RoleGovernanceMode,
  overrides: Partial<Parameters<typeof RoleGovernanceModal>[0]> = {},
): ReturnType<typeof vi.fn> {
  const onReview = vi.fn().mockResolvedValue(true);
  render(
    <ConfigProvider>
      <AntdApp>
        <RoleGovernanceModal
          directPermissions={[normalPermission]}
          history={[]}
          loading={false}
          mode={mode}
          onClose={vi.fn()}
          onReview={onReview}
          onRuntimeChange={vi.fn().mockResolvedValue(true)}
          onSaveDraft={vi.fn().mockResolvedValue(true)}
          onSubmitReview={vi.fn().mockResolvedValue(true)}
          permissionCatalog={[normalPermission, highPermission]}
          role={baseRole}
          submitting={false}
          {...overrides}
        />
      </AntdApp>
    </ConfigProvider>,
  );
  return onReview;
}

describe('permission package governance modal', () => {
  beforeAll(async () => {
    await i18n.changeLanguage('en-US');
  });

  afterEach(() => cleanup());

  it('explains the second-reviewer rule for high-risk packages', () => {
    renderModal('REVIEW', { directPermissions: [normalPermission, highPermission] });

    expect(screen.getByText('Permission package simple approval')).toBeInTheDocument();
    expect(screen.getByText(/must be reviewed by another super administrator/i)).toBeInTheDocument();
    expect(screen.getByText('security.admin')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /approve/i })).toBeEnabled();
  });

  it('lets SECURITY_ADMIN reject but not approve a high-risk package', () => {
    renderModal('REVIEW', {
      canApproveHighRisk: false,
      directPermissions: [normalPermission, highPermission],
    });

    expect(screen.getByText(/only another super administrator can approve/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /approve/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /reject for changes/i })).toBeEnabled();
  });

  it('requires requested changes before rejecting a package', async () => {
    const onReview = renderModal('REVIEW');

    fireEvent.click(screen.getByRole('button', { name: /reject for changes/i }));

    expect(await screen.findByText('Enter the requested changes before rejecting')).toBeInTheDocument();
    expect(onReview).not.toHaveBeenCalled();
  });

  it('requires the exact package code before enabling', async () => {
    const onRuntimeChange = vi.fn().mockResolvedValue(true);
    renderModal('ACTIVATE', { onRuntimeChange });

    fireEvent.change(screen.getByLabelText('Operation reason'), { target: { value: 'Approved release' } });
    fireEvent.change(screen.getByLabelText(/type package code custom_warehouse to confirm/i), { target: { value: 'WRONG' } });
    fireEvent.click(screen.getByRole('button', { name: /enable package/i }));

    expect(await screen.findByText('The confirmation code does not match')).toBeInTheDocument();
    await waitFor(() => expect(onRuntimeChange).not.toHaveBeenCalled());
  });
});

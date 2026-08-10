import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import type { PermissionRequest } from '../../api/iam';
import i18n from '../../locales/i18n';
import { PermissionRequestDecisionModal } from './PermissionRequestDecisionModal';

const request: PermissionRequest = {
  id: 1, targetUserId: 2, targetUsername: 'warehouse.user', requestedRoleId: 4,
  requestedRoleCode: 'WAREHOUSE_STAFF', requestedRoleName: 'Warehouse staff', warehouses: [{ id: 48, code: 'WH-01', name: 'Main' }],
  highRiskPermissionCount: 0, submittedAt: '2026-08-08T12:00:00Z', submittedByUsername: 'admin', status: 'PENDING_REVIEW',
};

describe('permission request decisions', () => {
  beforeAll(async () => { await i18n.changeLanguage('en-US'); });
  afterEach(() => cleanup());

  it('requires a review comment before rejecting', async () => {
    const onConfirm = vi.fn().mockResolvedValue(true);
    render(
      <ConfigProvider><AntdApp><PermissionRequestDecisionModal decision="REJECTED" loading={false} onClose={vi.fn()} onConfirm={onConfirm} request={request} /></AntdApp></ConfigProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: /reject request/i }));
    expect(await screen.findByText('Enter a review comment before rejecting')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('warns that approval grants the role and invalidates old tokens', () => {
    render(
      <ConfigProvider><AntdApp><PermissionRequestDecisionModal decision="APPROVED" loading={false} onClose={vi.fn()} onConfirm={vi.fn().mockResolvedValue(true)} request={request} /></AntdApp></ConfigProvider>,
    );
    expect(screen.getByText(/immediately adds the role/i)).toBeInTheDocument();
  });

  it('requires a reason before revoking access', async () => {
    const onConfirm = vi.fn().mockResolvedValue(true);
    render(
      <ConfigProvider><AntdApp><PermissionRequestDecisionModal decision="REVOKED" loading={false} onClose={vi.fn()} onConfirm={onConfirm} request={{ ...request, status: 'APPROVED' }} /></AntdApp></ConfigProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: /revoke access now/i }));
    expect(await screen.findByText('Enter a reason before revoking access')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});

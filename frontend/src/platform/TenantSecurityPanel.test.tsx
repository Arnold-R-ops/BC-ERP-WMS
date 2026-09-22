import { App } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TenantSecurityPanel } from './TenantSecurityPanel';
const request = vi.hoisted(() => vi.fn());
vi.mock('./api', () => ({ platformRequest: request }));
afterEach(cleanup);
beforeEach(() => { request.mockReset(); });
const base = { status: 'SUSPENDED', suspension_kind: 'SECURITY', controlsEnabled: true, actorId: 1, requests: [], connections: [], alerts: [] };
it('keeps unreleased controls disabled', async () => {
  request.mockResolvedValue({ ...base, status: 'ACTIVE', controlsEnabled: false });
  render(<App><TenantSecurityPanel tenantId={9} onChanged={vi.fn()} /></App>);
  expect(await screen.findByRole('button', { name: '紧急安全暂停' })).toBeDisabled();
  expect(request).toHaveBeenCalledWith('/api/platform/tenants/9/security');
});
it('shows withdrawal only to the pending initiator', async () => {
  request.mockResolvedValue({ ...base, requests: [{ public_id: 'r1', initiator_id: 1, status: 'PENDING', reason: 'verified' }] });
  render(<App><TenantSecurityPanel tenantId={9} onChanged={vi.fn()} /></App>);
  expect(await screen.findByRole('button', { name: /撤\s*回/ })).toBeEnabled();
  expect(screen.queryByRole('button', { name: '批准并恢复' })).not.toBeInTheDocument();
});
it('shows independent approval and truthful failed connection state', async () => {
  request.mockResolvedValue({ ...base, requests: [{ public_id: 'r1', initiator_id: 2, status: 'PENDING', reason: 'verified' }], connections: [{ config_id: 8, state: 'REAUTH_REQUIRED', remote_status: 'ROTATION_VERIFIED', check_status: 'FAILED', gap_status: 'REVIEW_REQUIRED' }] });
  render(<App><TenantSecurityPanel tenantId={9} onChanged={vi.fn()} /></App>);
  expect(await screen.findByRole('button', { name: '批准并恢复' })).toBeEnabled();
  expect(screen.queryByRole('button', { name: /撤\s*回/ })).not.toBeInTheDocument();
  expect(screen.getByText('REAUTH_REQUIRED')).toBeInTheDocument();
  expect(screen.getByText('REVIEW_REQUIRED')).toBeInTheDocument();
});
it('submits the MFA challenge and retries an unknown result with the same immutable command', async () => {
  const onChanged = vi.fn();
  let attempts = 0;
  request.mockImplementation(async (path: string, options?: RequestInit) => {
    if (path.endsWith('/challenges')) return { challengeToken: 'dummy-challenge' };
    if (path.endsWith('/commands') && options?.method === 'POST') {
      if (++attempts === 1) throw new Error('Simulated lost response');
      return { status: 'ACTIVE' };
    }
    if (path.includes('/commands/')) throw new Error('No confirmed outcome yet');
    return { ...base, requests: [{ public_id: 'r1', initiator_id: 2, status: 'PENDING', reason: 'verified' }] };
  });
  render(<App><TenantSecurityPanel tenantId={9} onChanged={onChanged} /></App>);
  fireEvent.click(await screen.findByRole('button', { name: '批准并恢复' }));
  const dialog = within(await screen.findByRole('dialog'));
  fireEvent.change(dialog.getByLabelText('操作原因／风险解除证据'), { target: { value: 'Independent evidence checked' } });
  fireEvent.change(dialog.getByLabelText('当前平台管理员密码'), { target: { value: 'dummy-test-password' } });
  fireEvent.change(dialog.getByLabelText('六位 TOTP'), { target: { value: '123456' } });
  fireEvent.click(dialog.getByRole('button', { name: '认证并执行' }));
  await waitFor(() => expect(dialog.getByRole('button', { name: '同编号重试' })).toBeEnabled());
  expect(onChanged).not.toHaveBeenCalled();
  expect(dialog.getByLabelText('操作原因／风险解除证据')).toBeDisabled();
  fireEvent.click(dialog.getByRole('button', { name: '同编号重试' }));
  await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
  const commands = request.mock.calls.filter(([path, options]) => String(path).endsWith('/commands') && options?.method === 'POST').map(([, options]) => JSON.parse(String(options.body)));
  expect(commands).toHaveLength(2);
  expect(commands[0].idempotencyKey).toBe(commands[1].idempotencyKey);
  expect(commands[1].draft).toEqual({ action: 'APPROVE_RESUME', requestId: 'r1', reason: 'Independent evidence checked' });
  expect(commands[1].challengeToken).toBe('dummy-challenge');
  expect(request.mock.calls.filter(([path]) => String(path).endsWith('/challenges'))).toHaveLength(1);
});

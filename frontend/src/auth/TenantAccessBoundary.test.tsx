import { cleanup, render, screen, fireEvent } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { TenantAccessBoundary } from './TenantAccessBoundary';
const mocks = vi.hoisted(() => ({ request: vi.fn(), logout: vi.fn() }));
vi.mock('../api/http', () => ({ apiRequest: mocks.request }));
vi.mock('./AuthProvider', () => ({ useAuth: () => ({ session: { token: 'current-token' }, logout: mocks.logout }) }));
afterEach(cleanup);
beforeEach(() => { mocks.request.mockReset(); mocks.logout.mockReset(); });
it('does not mount ERP children before authoritative ACTIVE confirmation', async () => {
  mocks.request.mockResolvedValue({ status: 'ACTIVE', businessAvailable: true });
  render(<TenantAccessBoundary><div>ERP business</div></TenantAccessBoundary>);
  expect(screen.queryByText('ERP business')).not.toBeInTheDocument();
  expect(await screen.findByText('ERP business')).toBeInTheDocument();
});
it('restricted login cannot mount business pages or export controls', async () => {
  mocks.request.mockResolvedValue({ status: 'SUSPENDED', businessAvailable: false });
  render(<TenantAccessBoundary><div>ERP export</div></TenantAccessBoundary>);
  expect(await screen.findByText('租户服务已暂停')).toBeInTheDocument();
  expect(screen.queryByText('ERP export')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '退出登录' }));
  expect(mocks.logout).toHaveBeenCalledOnce();
});
it('fails closed when the state cannot be verified', async () => {
  mocks.request.mockRejectedValue(new Error('unavailable'));
  render(<TenantAccessBoundary><div>ERP business</div></TenantAccessBoundary>);
  expect(await screen.findByText('暂时无法确认租户服务状态')).toBeInTheDocument();
  expect(screen.queryByText('ERP business')).not.toBeInTheDocument();
});

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import '../locales/i18n';
import { AuthProvider } from '../auth/AuthProvider';
import { writeAuthSession } from '../auth/storage';
import { DashboardPage } from './DashboardPage';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  });
}

describe('dashboard workspace', () => {
  beforeEach(() => {
    localStorage.clear();
    writeAuthSession({
      availableRoles: ['SUPER_ADMIN'],
      currentRole: 'SUPER_ADMIN',
      expiresAt: Date.now() + 60_000,
      mustChangePassword: false,
      token: 'test-token',
      tokenType: 'Bearer',
      username: 'admin',
    });

    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/api/sales-orders')) {
        return Promise.resolve(jsonResponse(Array.from({ length: 7 }, (_, index) => ({
          id: index + 1,
          orderNo: `SO-${index + 1}`,
          status: 'PENDING_APPROVAL',
          createdAt: `2026-07-${String(index + 1).padStart(2, '0')}T08:00:00Z`,
        }))));
      }
      if (url.includes('/api/integration/sku-mappings/pending')) {
        return Promise.resolve(jsonResponse(Array.from({ length: 6 }, (_, index) => ({ id: index + 1 }))));
      }
      if (url.includes('/api/integration/raw-events')) {
        return Promise.resolve(jsonResponse({ content: [{ id: 1 }], totalElements: 1 }));
      }
      if (url.includes('/api/predictions/reorder/urgent')) {
        return Promise.resolve(jsonResponse({ suggestions: [{ productSkuId: 1 }], totalCount: 1, totalCost: 800 }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
  });

  it('renders all four operational counters from backend data', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <MemoryRouter>
        <ConfigProvider>
          <AntdApp>
            <QueryClientProvider client={queryClient}>
              <AuthProvider>
                <DashboardPage />
              </AuthProvider>
            </QueryClientProvider>
          </AntdApp>
        </ConfigProvider>
      </MemoryRouter>,
    );

    expect(await within(screen.getByRole('button', { name: '待审批销售订单' })).findByText('7')).toBeVisible();
    expect(await within(screen.getByRole('button', { name: '待映射 SKU' })).findByText('6')).toBeVisible();
    expect(await within(screen.getByRole('button', { name: '待人工复核事件' })).findByText('1')).toBeVisible();
    expect(await within(screen.getByRole('button', { name: '紧急补货建议' })).findByText('1')).toBeVisible();
    expect(screen.getByRole('button', { name: '收货' })).toBeVisible();
    expect(screen.getByRole('button', { name: '拣货' })).toBeVisible();
    expect(screen.getByRole('button', { name: '盘点' })).toBeVisible();
  });
});

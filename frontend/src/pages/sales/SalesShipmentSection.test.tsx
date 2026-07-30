import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

vi.mock('../../api/sales', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/sales')>(),
  listSalesOrderShipments: vi.fn(),
}));

import { listSalesOrderShipments } from '../../api/sales';
import { SalesShipmentSection } from './SalesShipmentSection';

function renderSection(): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <MemoryRouter>
      <ConfigProvider>
        <AntdApp>
          <QueryClientProvider client={queryClient}>
            <SalesShipmentSection canEdit salesOrderId={13} />
          </QueryClientProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>,
  );
}

describe('sales shipment audit trail', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    vi.mocked(listSalesOrderShipments).mockResolvedValue([{
      id: 2,
      salesOrderId: 13,
      trackingNo: 'P2-SHIP-UAT-UPDATED',
      carrier: 'UAT-CARRIER-B',
      status: 'VOIDED',
      createdBy: 2,
      createdByName: 'admin',
      createdAt: '2026-07-24T17:35:13.308418',
      voidedBy: 2,
      voidedByName: 'admin',
      voidedAt: '2026-07-24T17:35:13.350938',
      remark: 'P2 shipment acceptance update',
    }]);
  });

  it('shows registration and void audit actor and timestamp', async () => {
    renderSection();

    expect(await screen.findByText('P2-SHIP-UAT-UPDATED')).toBeVisible();
    expect(screen.getByRole('columnheader', { name: '登记时间' })).toBeVisible();
    expect(screen.getByRole('columnheader', { name: '作废人' })).toBeVisible();
    expect(screen.getByRole('columnheader', { name: '作废时间' })).toBeVisible();
    expect(screen.getAllByText('admin')).toHaveLength(2);
    expect(screen.getAllByText('2026年7月24日 17:35')).toHaveLength(2);
  });
});

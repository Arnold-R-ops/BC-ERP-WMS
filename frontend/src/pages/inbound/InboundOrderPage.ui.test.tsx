import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';

const authState = vi.hoisted(() => ({
  currentRole: 'BUYER',
  permissionCodes: ['other_inbound:manage'],
}));

vi.mock('../../auth/AuthProvider', () => ({
  useAuth: () => ({ session: authState }),
}));

vi.mock('../../api/inbound', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../api/inbound')>(),
  createOtherInboundApplication: vi.fn(),
  getOtherInboundApplication: vi.fn(),
  getOtherInboundPolicy: vi.fn(),
  listOtherInboundApplications: vi.fn(),
  listOtherInboundReferenceSkus: vi.fn(),
  listOtherInboundReferenceWarehouses: vi.fn(),
  runOtherInboundAction: vi.fn(),
  updateOtherInboundApplication: vi.fn(),
  updateOtherInboundPolicy: vi.fn(),
}));

import {
  getOtherInboundApplication,
  getOtherInboundPolicy,
  listOtherInboundApplications,
  listOtherInboundReferenceSkus,
  listOtherInboundReferenceWarehouses,
  type OtherInboundApplication,
} from '../../api/inbound';
import { InboundOrderPage } from './InboundOrderPage';

const application: OtherInboundApplication = {
  id: 7,
  version: 3,
  applicationNumber: 'OI-20260828-07',
  businessType: 'FREE_SAMPLE',
  sourceReference: 'SAMPLE-07',
  businessReason: '供应商免费样品',
  warehouseId: 2,
  warehouseName: '主仓',
  status: 'CONFIRMED',
  plannedQuantity: 5,
  receivedQuantity: 0,
  items: [{ id: 71, productSkuId: 12, plannedQuantity: 5, productName: '茉莉绿茶' }],
};

function renderPage(): void {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  render(
    <MemoryRouter>
      <ConfigProvider>
        <AntdApp>
          <QueryClientProvider client={queryClient}>
            <InboundOrderPage />
          </QueryClientProvider>
        </AntdApp>
      </ConfigProvider>
    </MemoryRouter>,
  );
}

describe('other inbound page policy and scoped reference options', () => {
  afterEach(() => cleanup());

  beforeEach(async () => {
    vi.clearAllMocks();
    await i18n.changeLanguage('zh-CN');
    authState.currentRole = 'BUYER';
    authState.permissionCodes = ['other_inbound:manage'];
    vi.mocked(getOtherInboundPolicy).mockResolvedValue({ enabled: false, allowedBusinessTypes: ['FREE_SAMPLE'] });
    vi.mocked(listOtherInboundApplications).mockResolvedValue([application]);
    vi.mocked(getOtherInboundApplication).mockResolvedValue(application);
    vi.mocked(listOtherInboundReferenceSkus).mockResolvedValue([{
      id: 12,
      skuCode: 'SKU-12',
      name: '茉莉绿茶',
      barcode: '6900000012',
      expiryManagementEnabled: false,
    }]);
    vi.mocked(listOtherInboundReferenceWarehouses).mockResolvedValue([{ id: 2, code: 'WH-02', name: '主仓' }]);
  });

  it('keeps existing applications and terminal actions available while creation is disabled', async () => {
    renderPage();

    expect(await screen.findByText('其他入库入口当前关闭')).toBeInTheDocument();
    expect(await screen.findByText('OI-20260828-07')).toBeInTheDocument();
    expect(screen.queryByText('新建其他入库申请')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /查\s*看/ }));
    expect(await screen.findByRole('button', { name: '取消申请' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '确认并生成收货任务' })).not.toBeInTheDocument();
    expect(listOtherInboundReferenceSkus).not.toHaveBeenCalled();
    expect(listOtherInboundReferenceWarehouses).not.toHaveBeenCalled();
  });

  it('loads domain-scoped options for a manager without master-data permissions', async () => {
    vi.mocked(getOtherInboundPolicy).mockResolvedValue({ enabled: true, allowedBusinessTypes: ['FREE_SAMPLE'] });
    renderPage();

    fireEvent.click(await screen.findByText('新建其他入库申请'));
    await waitFor(() => {
      expect(listOtherInboundReferenceSkus).toHaveBeenCalled();
      expect(listOtherInboundReferenceWarehouses).toHaveBeenCalled();
    });
    expect(authState.permissionCodes).toEqual(['other_inbound:manage']);
  });
});

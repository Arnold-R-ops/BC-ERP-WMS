import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { EmergencyCorrectionApprovalMode } from '../../api/emergencyCorrections';
import i18n from '../../locales/i18n';

import { EmergencyCorrectionDrawer } from './EmergencyCorrectionDrawer';

function renderDrawer(
  effectiveMode: EmergencyCorrectionApprovalMode,
  snapshot?: EmergencyCorrectionApprovalMode,
  evidenceUrl?: string,
): { onAction: ReturnType<typeof vi.fn>; onApply: ReturnType<typeof vi.fn> } {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onAction = vi.fn();
  const onApply = vi.fn();
  render(
    <ConfigProvider>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <EmergencyCorrectionDrawer
            canAdjust
            canApprove={false}
            canReject={false}
            canReview={false}
            correction={{
              id: 41,
              correctionNo: 'ESC-000041',
              status: 'DRAFT',
              approvalModeSnapshot: snapshot,
              productSkuId: 12,
              productSkuCode: 'SKU-12',
              productSkuName: 'Test item',
              warehouseId: 7,
              warehouseCode: 'WH-07',
              warehouseName: '验收仓',
              locationId: 13,
              locationCode: 'A-01',
              systemQty: 100,
              countedQty: 90,
              adjustmentQty: -10,
              evidenceUrl,
              submissionComment: '提交时的事实说明',
              reviewComment: '独立复核意见',
            }}
            effectiveApprovalMode={effectiveMode}
            onAction={onAction}
            onApply={onApply}
            onClose={vi.fn()}
            policyLoading={false}
            previewLoading={false}
          />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  );
  return { onAction, onApply };
}

describe('emergency correction draft actions', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN');
  });

  afterEach(() => {
    cleanup();
    document.body.innerHTML = '';
  });

  it('uses impact preview and direct apply when multi-person approval is disabled', () => {
    const { onAction, onApply } = renderDrawer('NONE');

    fireEvent.click(screen.getByRole('button', { name: /预览影响并应用/ }));
    expect(onApply).toHaveBeenCalledTimes(1);
    expect(onAction).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /提交审批流程/ })).not.toBeInTheDocument();
    expect(screen.getByText('提交时的事实说明')).toBeInTheDocument();
    expect(screen.getByText('独立复核意见')).toBeInTheDocument();
    expect(screen.getByText('SKU-12 · Test item')).toBeInTheDocument();
    expect(screen.getByText('WH-07 · 验收仓')).toBeInTheDocument();
    expect(screen.getByText('A-01')).toBeInTheDocument();
  });

  it.each<EmergencyCorrectionApprovalMode>(['TWO_PERSON', 'THREE_PERSON'])(
    'submits the draft into the configured %s workflow',
    (mode) => {
      const { onAction, onApply } = renderDrawer(mode);

      fireEvent.click(screen.getByRole('button', { name: /提交审批流程/ }));
      expect(onAction).toHaveBeenCalledWith('submit', expect.objectContaining({ id: 41 }));
      expect(onApply).not.toHaveBeenCalled();
      expect(screen.queryByRole('button', { name: /预览影响并应用/ })).not.toBeInTheDocument();
    },
  );

  it('keeps a draft on its frozen approval snapshot after the tenant policy changes', () => {
    const { onAction, onApply } = renderDrawer('NONE', 'THREE_PERSON');

    fireEvent.click(screen.getByRole('button', { name: /提交审批流程/ }));
    expect(onAction).toHaveBeenCalledWith('submit', expect.objectContaining({ id: 41 }));
    expect(onApply).not.toHaveBeenCalled();
  });

  it('keeps a no-approval draft direct even if the tenant later enables approval', () => {
    const { onAction, onApply } = renderDrawer('THREE_PERSON', 'NONE');

    fireEvent.click(screen.getByRole('button', { name: /预览影响并应用/ }));
    expect(onApply).toHaveBeenCalledTimes(1);
    expect(onAction).not.toHaveBeenCalled();
  });

  it.each(['javascript:alert(1)', 'data:text/html,unsafe', '/relative-evidence'])(
    'renders a non-HTTP evidence URL as non-clickable text: %s',
    (evidenceUrl) => {
      renderDrawer('NONE', undefined, evidenceUrl);

      expect(screen.getByText(evidenceUrl)).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: evidenceUrl })).not.toBeInTheDocument();
    },
  );
});

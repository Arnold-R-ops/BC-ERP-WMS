import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';
import { EmergencyCorrectionApplyModal } from './EmergencyCorrectionApplyModal';

const correction = { id: 41, correctionNo: 'ESC-000041', status: 'DRAFT' as const };

function renderModal(
  canApply: boolean,
  onConfirm = vi.fn(),
  hasPriorAttempt = false,
  onStartNewAttempt = vi.fn(),
): void {
  render(
    <ConfigProvider>
      <AntdApp>
        <EmergencyCorrectionApplyModal
          correction={correction}
          hasPriorAttempt={hasPriorAttempt}
          impact={{
            correctionId: 41,
            snapshotSystemQty: 100,
            countedQty: 90,
            adjustmentQty: -10,
            currentQty: 95,
            reservedQty: 90,
            projectedQty: 85,
            canApply,
            affectedSalesOrderIds: [701, 702],
            previewToken: 'preview-token-41',
            previewExpiresAt: '2099-08-25T08:00:00Z',
          }}
          loading={false}
          onCancel={vi.fn()}
          onConfirm={onConfirm}
          onStartNewAttempt={onStartNewAttempt}
        />
      </AntdApp>
    </ConfigProvider>,
  );
}

describe('emergency correction impact confirmation', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN');
  });

  afterEach(() => {
    cleanup();
    document.body.innerHTML = '';
  });

  it('shows delta carry-forward quantities and blocks reserved-stock conflicts', () => {
    const onConfirm = vi.fn();
    renderModal(false, onConfirm);

    expect(screen.getByText('制单时账面数')).toBeInTheDocument();
    expect(screen.getByText('当前库存')).toBeInTheDocument();
    expect(screen.getByText('应用后库存')).toBeInTheDocument();
    expect(screen.getByText('#701')).toBeInTheDocument();
    expect(screen.getByText('当前不可应用')).toBeInTheDocument();

    const confirm = screen.getByRole('button', { name: '确认并应用' });
    expect(confirm).toBeDisabled();
    fireEvent.click(confirm);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('allows the second confirmation only when the server preview permits applying', () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    renderModal(true, onConfirm);

    fireEvent.click(screen.getByRole('button', { name: '确认并应用' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('requires an explicit operator action before rotating a retained failed request', () => {
    const onStartNewAttempt = vi.fn();
    renderModal(true, vi.fn(), true, onStartNewAttempt);

    expect(screen.getByText('已保留上一次请求')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '开始新尝试' }));
    expect(onStartNewAttempt).toHaveBeenCalledTimes(1);
  });

  it('blocks apply and asks for a new preview when the token is missing', () => {
    render(
      <ConfigProvider>
        <AntdApp>
          <EmergencyCorrectionApplyModal
            correction={correction}
            hasPriorAttempt={false}
            impact={{
              correctionId: 41,
              snapshotSystemQty: 100,
              countedQty: 90,
              adjustmentQty: -10,
              currentQty: 95,
              reservedQty: 80,
              projectedQty: 85,
              canApply: true,
              affectedSalesOrderIds: [],
              previewToken: '',
              previewExpiresAt: '2099-08-25T08:00:00Z',
            }}
            loading={false}
            onCancel={vi.fn()}
            onConfirm={vi.fn()}
            onStartNewAttempt={vi.fn().mockResolvedValue(undefined)}
          />
        </AntdApp>
      </ConfigProvider>,
    );

    expect(screen.getByText('预览凭证缺失')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '确认并应用' })).toBeDisabled();
  });

  it('allows a retained command key to retry with a fresh valid authorization preview', () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(
      <ConfigProvider>
        <AntdApp>
          <EmergencyCorrectionApplyModal
            correction={correction}
            hasPriorAttempt
            impact={{
              correctionId: 41,
              snapshotSystemQty: 100,
              countedQty: 90,
              adjustmentQty: -10,
              currentQty: 95,
              reservedQty: 80,
              projectedQty: 85,
              canApply: true,
              affectedSalesOrderIds: [],
              previewToken: 'new-preview-token',
              previewExpiresAt: '2099-08-25T08:00:00Z',
            }}
            loading={false}
            onCancel={vi.fn()}
            onConfirm={onConfirm}
            onStartNewAttempt={vi.fn().mockResolvedValue(undefined)}
          />
        </AntdApp>
      </ConfigProvider>,
    );

    const confirm = screen.getByRole('button', { name: '确认并应用' });
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});

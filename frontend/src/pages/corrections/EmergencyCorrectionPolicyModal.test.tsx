import { App as AntdApp, ConfigProvider } from 'antd';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../locales/i18n';
import { EmergencyCorrectionPolicyModal } from './EmergencyCorrectionPolicyModal';

describe('emergency correction approval policy', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN');
  });

  afterEach(() => {
    cleanup();
    document.body.innerHTML = '';
  });

  it('requires a reason and explicit confirmation before saving an entitled mode', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(
      <ConfigProvider>
        <AntdApp>
          <EmergencyCorrectionPolicyModal
            loading={false}
            onCancel={vi.fn()}
            onConfirm={onConfirm}
            open
            policy={{
              configuredMode: 'NONE',
              effectiveMode: 'NONE',
              approvalEntitled: true,
              configurable: true,
              availableModes: ['NONE', 'TWO_PERSON', 'THREE_PERSON'],
            }}
          />
        </AntdApp>
      </ConfigProvider>,
    );

    fireEvent.mouseDown(screen.getByRole('combobox'));
    fireEvent.click(await screen.findByText('双人审批'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '高风险仓库启用职责分离' } });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith({
      mode: 'TWO_PERSON',
      reason: '高风险仓库启用职责分离',
      confirmed: true,
    }));
  });
});

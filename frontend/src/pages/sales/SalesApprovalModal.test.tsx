import { ConfigProvider } from 'antd';
import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import '../../locales/i18n';
import { WORKFLOW_MODAL_Z_INDEX } from '../../constants/layers';
import { SalesApprovalModal } from './SalesApprovalModal';

describe('sales approval modal layering', () => {
  it('renders above an open order details drawer', () => {
    render(
      <ConfigProvider>
        <SalesApprovalModal
          loading={false}
          onCancel={vi.fn()}
          onConfirm={vi.fn()}
          open
        />
      </ConfigProvider>,
    );

    const mask = document.querySelector<HTMLElement>('.ant-modal-mask');
    const wrap = document.querySelector<HTMLElement>('.ant-modal-wrap');

    expect(mask).toHaveStyle({ zIndex: WORKFLOW_MODAL_Z_INDEX });
    expect(wrap).toHaveStyle({ zIndex: WORKFLOW_MODAL_Z_INDEX });
  });
});

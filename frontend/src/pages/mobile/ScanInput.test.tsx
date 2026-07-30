import { App as AntdApp } from 'antd';
import { fireEvent, render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ScanInput } from './ScanInput';

const { decodeFromImageUrl } = vi.hoisted(() => ({ decodeFromImageUrl: vi.fn() }));

vi.mock('@zxing/browser', () => ({
  BrowserMultiFormatReader: class {
    decodeFromImageUrl = decodeFromImageUrl;
  },
}));

describe('ScanInput', () => {
  beforeEach(() => {
    decodeFromImageUrl.mockReset();
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:barcode-photo'),
      revokeObjectURL: vi.fn(),
    });
  });

  it('opens image capture and submits the decoded barcode', async () => {
    const onScan = vi.fn();
    decodeFromImageUrl.mockResolvedValue({ getText: () => ' PHONE-BARCODE-1 ' });
    render(<AntdApp><ScanInput label="Scan" onScan={onScan} placeholder="Code" /></AntdApp>);

    const capture = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(capture).not.toBeNull();
    const file = new File(['image'], 'barcode.jpg', { type: 'image/jpeg' });
    fireEvent.change(capture, { target: { files: [file] } });

    await waitFor(() => expect(onScan).toHaveBeenCalledWith('PHONE-BARCODE-1'));
    expect(URL.createObjectURL).toHaveBeenCalledWith(file);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:barcode-photo');
  });

  it('submits the configured target location from the manual-fill button', () => {
    const onScan = vi.fn();
    render(
      <AntdApp>
        <ScanInput
          label="Location"
          manualValue="WH-UAT-ZONE_A-A-01"
          onScan={onScan}
          placeholder="Location code"
        />
      </AntdApp>,
    );

    fireEvent.click(document.querySelector('.mobile-scan-field .ant-btn-lg') as HTMLButtonElement);

    expect(onScan).toHaveBeenCalledWith('WH-UAT-ZONE_A-A-01');
  });

  it('submits keyboard input from the visible confirmation button', async () => {
    const onScan = vi.fn();
    const { container } = render(<AntdApp><ScanInput label="Batch" onScan={onScan} placeholder="Batch code" /></AntdApp>);

    fireEvent.change(container.querySelector('.mobile-scan-field .ant-input') as HTMLInputElement, {
      target: { value: ' BATCH-WEB-001 ' },
    });
    const confirmButton = container.querySelector('.mobile-scan-field .ant-input-suffix button') as HTMLButtonElement;
    await waitFor(() => expect(confirmButton.disabled).toBe(false));
    fireEvent.click(confirmButton);

    await waitFor(() => expect(onScan).toHaveBeenCalledWith('BATCH-WEB-001'));
  });
});

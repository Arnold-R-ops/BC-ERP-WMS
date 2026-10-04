import { App as AntdApp } from 'antd';
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ScanInput } from './ScanInput';

const { multiCanvas, multiImage, qrCanvas, qrImage } = vi.hoisted(() => ({
  multiCanvas: vi.fn(),
  multiImage: vi.fn(),
  qrCanvas: vi.fn(),
  qrImage: vi.fn(),
}));

vi.mock('@zxing/browser', () => ({
  BarcodeFormat: {
    CODABAR: 0,
    CODE_39: 2,
    CODE_93: 3,
    CODE_128: 4,
    DATA_MATRIX: 5,
    EAN_8: 7,
    EAN_13: 8,
    ITF: 10,
    QR_CODE: 11,
  },
  BrowserMultiFormatReader: class {
    decodeFromCanvas = multiCanvas;
    decodeFromImageUrl = multiImage;
  },
  BrowserQRCodeReader: class {
    decodeFromCanvas = qrCanvas;
    decodeFromImageUrl = qrImage;
  },
}));

describe('ScanInput', () => {
  beforeEach(() => {
    multiCanvas.mockReset();
    multiImage.mockReset();
    qrCanvas.mockReset();
    qrImage.mockReset();
    multiCanvas.mockImplementation(() => { throw new Error('Not found'); });
    qrCanvas.mockImplementation(() => { throw new Error('Not found'); });
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(), fillRect: vi.fn(), rotate: vi.fn(), translate: vi.fn(),
      getImageData: vi.fn(() => ({ data: new Uint8ClampedArray(4) })),
      putImageData: vi.fn(),
    } as unknown as CanvasRenderingContext2D);
    class LoadedImage {
      naturalHeight = 6048;
      naturalWidth = 8064;
      onerror: null | (() => void) = null;
      onload: null | (() => void) = null;
      removeAttribute = vi.fn();
      set src(_value: string) { queueMicrotask(() => this.onload?.()); }
    }
    vi.stubGlobal('Image', LoadedImage);
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:barcode-photo'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('downsizes a 48MP photo before any decoding and releases the canvas after recognition', async () => {
    const onScan = vi.fn();
    let decodedCanvas: HTMLCanvasElement | undefined;
    qrCanvas.mockImplementation((canvas: HTMLCanvasElement) => {
      decodedCanvas = canvas;
      expect(canvas.width).toBe(1280);
      expect(canvas.height).toBe(960);
      return { getText: () => ' RETRIED-QR-1 ' };
    });

    render(<AntdApp><ScanInput label="Scan" onScan={onScan} placeholder="Code" /></AntdApp>);
    const capture = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(capture, {
      target: { files: [new File(['image'], 'large-rotated-photo.jpg', { type: 'image/jpeg' })] },
    });

    await waitFor(() => expect(onScan).toHaveBeenCalledWith('RETRIED-QR-1'));
    expect(qrCanvas).toHaveBeenCalledTimes(1);
    expect(multiCanvas).not.toHaveBeenCalled();
    expect(qrImage).not.toHaveBeenCalled();
    expect(multiImage).not.toHaveBeenCalled();
    expect(decodedCanvas?.width).toBe(0);
    expect(decodedCanvas?.height).toBe(0);
  });

  it('opens image capture and submits the decoded barcode', async () => {
    const onScan = vi.fn();
    multiCanvas.mockReturnValue({ getText: () => ' PHONE-BARCODE-1 ' });
    render(<AntdApp><ScanInput label="Scan" onScan={onScan} placeholder="Code" /></AntdApp>);

    const capture = document.querySelector('input[type="file"]') as HTMLInputElement;
    expect(capture).not.toBeNull();
    const file = new File(['image'], 'barcode.jpg', { type: 'image/jpeg' });
    fireEvent.change(capture, { target: { files: [file] } });

    await waitFor(() => expect(onScan).toHaveBeenCalledWith('PHONE-BARCODE-1'));
    expect(URL.createObjectURL).toHaveBeenCalledWith(file);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:barcode-photo');
  });

  it('prioritizes QR decoding before the multi-format reader', async () => {
    const onScan = vi.fn();
    qrCanvas.mockReturnValue({ getText: () => ' QR-FIRST-1 ' });
    render(<AntdApp><ScanInput label="Scan" onScan={onScan} placeholder="Code" /></AntdApp>);

    const capture = document.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(capture, {
      target: { files: [new File(['image'], 'qr.jpg', { type: 'image/jpeg' })] },
    });

    await waitFor(() => expect(onScan).toHaveBeenCalledWith('QR-FIRST-1'));
    expect(multiCanvas).not.toHaveBeenCalled();
  });

  it('cleans up an unrecognizable photo without submitting a guessed code', async () => {
    const onScan = vi.fn();
    render(<AntdApp><ScanInput label="Scan" onScan={onScan} placeholder="Code" /></AntdApp>);
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, {
      target: { files: [new File(['image'], 'no-code.jpg', { type: 'image/jpeg' })] },
    });
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalled());
    expect(onScan).not.toHaveBeenCalled();
    expect(qrCanvas).toHaveBeenCalledTimes(8);
    expect(multiCanvas).toHaveBeenCalledTimes(8);
    const canvases = new Set(qrCanvas.mock.calls.map(([canvas]) => canvas));
    expect(canvases.size).toBe(1);
    expect([...canvases][0].width).toBe(0);
  });

  it('reports an image-load failure separately from an unrecognized barcode', async () => {
    const onScan = vi.fn();
    class UnreadableImage {
      onerror: null | (() => void) = null;
      onload: null | (() => void) = null;
      removeAttribute = vi.fn();
      set src(_value: string) { queueMicrotask(() => this.onerror?.()); }
    }
    vi.stubGlobal('Image', UnreadableImage);
    const { findByText } = render(<AntdApp><ScanInput label="Scan" onScan={onScan} placeholder="Code" /></AntdApp>);
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, {
      target: { files: [new File(['invalid'], 'unreadable.heic', { type: 'image/heic' })] },
    });
    await findByText('mobile.cameraPhotoUnreadable');
    expect(onScan).not.toHaveBeenCalled();
    expect(qrCanvas).not.toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalled();
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

import { App as AntdApp } from 'antd';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ScanInput } from './ScanInput';
import type { startLiveBarcodeScanner } from './liveBarcodeScanner';

const { start, stop } = vi.hoisted(() => ({ start: vi.fn(), stop: vi.fn() }));
vi.mock('./liveBarcodeScanner', () => ({ startLiveBarcodeScanner: start }));

describe('ScanInput live camera integration', () => {
  let callbacks: Parameters<typeof startLiveBarcodeScanner>[1];
  beforeEach(() => {
    stop.mockReset();
    start.mockReset().mockImplementation((_video, handlers) => {
      callbacks = handlers;
      handlers.onReady();
      return { stop };
    });
  });
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });

  it('opens an in-page live scanner, accepts a result and releases the session', async () => {
    const onScan = vi.fn();
    render(<AntdApp><ScanInput label="Batch" placeholder="Code" onScan={onScan} /></AntdApp>);
    fireEvent.click(screen.getByRole('button', { name: 'mobile.openCamera' }));
    await waitFor(() => expect(start).toHaveBeenCalledOnce());
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(document.querySelector('video')?.playsInline).toBe(true);
    act(() => callbacks.onResult('BATCH-01'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(onScan).toHaveBeenCalledExactlyOnceWith('BATCH-01');
    expect(stop).toHaveBeenCalledOnce();
  });

  it('shows an HTTP explanation and preserves the explicit photo fallback', async () => {
    start.mockImplementation((_video, handlers) => {
      handlers.onError('secure-required');
      return { stop };
    });
    render(<AntdApp><ScanInput label="Batch" placeholder="Code" onScan={vi.fn()} /></AntdApp>);
    fireEvent.click(screen.getByRole('button', { name: 'mobile.openCamera' }));
    expect(await screen.findByText('mobile.liveScanSecureRequired')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'mobile.scanTakePhoto' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'mobile.liveScanRetry' })).not.toBeInTheDocument();
  });

  it('releases the camera before opening the album file picker', async () => {
    render(<AntdApp><ScanInput label="Batch" placeholder="Code" onScan={vi.fn()} /></AntdApp>);
    const album = document.querySelector('input[type="file"]:not([capture])') as HTMLInputElement;
    const click = vi.spyOn(album, 'click').mockImplementation(() => expect(stop).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: 'mobile.openCamera' }));
    await waitFor(() => expect(start).toHaveBeenCalledOnce());
    fireEvent.click(screen.getByRole('button', { name: 'mobile.scanFromAlbum' }));
    expect(click).toHaveBeenCalledOnce();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('only reacquires the camera after the user presses retry', async () => {
    render(<AntdApp><ScanInput label="Batch" placeholder="Code" onScan={vi.fn()} /></AntdApp>);
    fireEvent.click(screen.getByRole('button', { name: 'mobile.openCamera' }));
    await waitFor(() => expect(start).toHaveBeenCalledOnce());
    act(() => callbacks.onError('permission'));
    expect(screen.getByText('mobile.liveScanPermission')).toBeInTheDocument();
    expect(start).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', { name: 'mobile.liveScanRetry' }));
    await waitFor(() => expect(start).toHaveBeenCalledTimes(2));
  });

  it('closes and releases the camera when the task disables scanning', async () => {
    const onScan = vi.fn();
    const { rerender } = render(<AntdApp><ScanInput label="Batch" placeholder="Code" onScan={onScan} /></AntdApp>);
    fireEvent.click(screen.getByRole('button', { name: 'mobile.openCamera' }));
    await waitFor(() => expect(start).toHaveBeenCalledOnce());
    rerender(<AntdApp><ScanInput label="Batch" placeholder="Code" onScan={onScan} disabled /></AntdApp>);
    expect(stop).toHaveBeenCalledOnce();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(onScan).not.toHaveBeenCalled();
  });
});

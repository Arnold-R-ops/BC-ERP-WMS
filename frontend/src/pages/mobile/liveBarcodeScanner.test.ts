import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { liveCameraAvailability, startLiveBarcodeScanner } from './liveBarcodeScanner';

const { decode } = vi.hoisted(() => ({ decode: vi.fn() }));
vi.mock('./barcodePhoto', () => ({ createBarcodeCanvasDecoder: () => decode }));

describe('liveBarcodeScanner', () => {
  let video: HTMLVideoElement;
  let track: MediaStreamTrack;
  let stream: MediaStream;
  let getUserMedia: ReturnType<typeof vi.fn>;
  let callbacks: Parameters<typeof startLiveBarcodeScanner>[1];
  let session: ReturnType<typeof startLiveBarcodeScanner> | undefined;

  beforeEach(() => {
    vi.useFakeTimers();
    decode.mockReset();
    video = document.createElement('video');
    Object.defineProperties(video, {
      readyState: { configurable: true, value: 2 },
      videoWidth: { configurable: true, value: 3024 },
      videoHeight: { configurable: true, value: 4032 },
    });
    vi.spyOn(video, 'play').mockResolvedValue();
    vi.spyOn(video, 'pause').mockImplementation(() => undefined);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() } as unknown as CanvasRenderingContext2D);
    track = Object.assign(new EventTarget(), { stop: vi.fn() }) as unknown as MediaStreamTrack;
    stream = { getTracks: () => [track] } as unknown as MediaStream;
    getUserMedia = vi.fn().mockResolvedValue(stream);
    vi.stubGlobal('isSecureContext', true);
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });
    callbacks = { onReady: vi.fn(), onResult: vi.fn(), onError: vi.fn() };
  });

  afterEach(() => {
    session?.stop();
    session = undefined;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('blocks HTTP before requesting camera permission', () => {
    vi.stubGlobal('isSecureContext', false);
    expect(liveCameraAvailability()).toBe('secure-required');
    session = startLiveBarcodeScanner(video, callbacks);
    expect(getUserMedia).not.toHaveBeenCalled();
    expect(callbacks.onError).toHaveBeenCalledWith('secure-required');
  });

  it('explains unsupported secure browsers', () => {
    vi.stubGlobal('navigator', {});
    session = startLiveBarcodeScanner(video, callbacks);
    expect(callbacks.onError).toHaveBeenCalledWith('unsupported');
  });

  it('continuously retries and delivers the same result only once after two frames', async () => {
    decode.mockReturnValueOnce(undefined).mockReturnValue('BATCH-001');
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(0);
    expect(callbacks.onReady).toHaveBeenCalledOnce();
    expect(callbacks.onResult).not.toHaveBeenCalled();
    expect(video.playsInline).toBe(true);
    expect(video.muted).toBe(true);
    expect(getUserMedia).toHaveBeenCalledWith(expect.objectContaining({ audio: false }));
    await vi.advanceTimersByTimeAsync(250);
    expect(callbacks.onResult).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(250);
    expect(callbacks.onResult).toHaveBeenCalledExactlyOnceWith('BATCH-001');
    expect(track.stop).toHaveBeenCalledOnce();
    expect(video.srcObject).toBeNull();
    expect(decode.mock.calls[0][0].width).toBe(0);
    await vi.advanceTimersByTimeAsync(5000);
    expect(decode).toHaveBeenCalledTimes(3);
  });

  it('does not accept alternating codes or an isolated frame', async () => {
    decode.mockReturnValueOnce('A').mockReturnValueOnce('B').mockReturnValueOnce('A');
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(3000);
    decode.mockReturnValueOnce('A');
    await vi.advanceTimersByTimeAsync(250);
    expect(callbacks.onResult).not.toHaveBeenCalled();
    expect(callbacks.onError).not.toHaveBeenCalled();
  });

  it('bounds frame size, reuses one canvas, and throttles geometric recovery', async () => {
    const sizes: number[] = [];
    decode.mockImplementation((canvas: HTMLCanvasElement) => { sizes.push(Math.max(canvas.width, canvas.height)); });
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(750);
    expect(sizes).toEqual([1280, 800, 1280, 1280]);
    expect(new Set(decode.mock.calls.map(([canvas]) => canvas)).size).toBe(1);
    expect(decode.mock.calls.map((call) => call[2])).toEqual([true, false, false, true]);
  });

  it('releases a stream that arrives after the dialog has closed', async () => {
    let resolve!: (stream: MediaStream) => void;
    getUserMedia.mockReturnValue(new Promise<MediaStream>((done) => { resolve = done; }));
    session = startLiveBarcodeScanner(video, callbacks);
    session.stop();
    resolve(stream);
    await vi.advanceTimersByTimeAsync(1000);
    expect(track.stop).toHaveBeenCalledOnce();
    expect(video.play).not.toHaveBeenCalled();
    expect(callbacks.onResult).not.toHaveBeenCalled();
    expect(callbacks.onError).not.toHaveBeenCalled();
  });

  it('does not resume decoding if closed while video.play is pending', async () => {
    let resolve!: () => void;
    vi.mocked(video.play).mockReturnValue(new Promise<void>((done) => { resolve = done; }));
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(0);
    session.stop();
    resolve();
    await vi.advanceTimersByTimeAsync(15_000);
    expect(decode).not.toHaveBeenCalled();
    expect(track.stop).toHaveBeenCalledOnce();
    expect(callbacks.onError).not.toHaveBeenCalled();
  });

  it('reports denied permission without calling the business callback', async () => {
    getUserMedia.mockRejectedValue(Object.assign(new Error('Denied'), { name: 'NotAllowedError' }));
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(0);
    expect(callbacks.onError).toHaveBeenCalledExactlyOnceWith('permission');
    expect(callbacks.onResult).not.toHaveBeenCalled();
  });

  it('releases camera when hidden and requires an explicit restart', async () => {
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(0);
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(1000);
    expect(track.stop).toHaveBeenCalledOnce();
    expect(callbacks.onError).toHaveBeenCalledExactlyOnceWith('interrupted');
    expect(decode).toHaveBeenCalledOnce();
  });

  it.each(['pagehide', 'ended'])('releases camera on %s', async (event) => {
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(0);
    (event === 'pagehide' ? window : track).dispatchEvent(new Event(event));
    expect(track.stop).toHaveBeenCalledOnce();
    expect(callbacks.onError).toHaveBeenCalledWith('interrupted');
  });

  it('times out a camera that never provides usable video frames', async () => {
    Object.defineProperty(video, 'readyState', { value: 0 });
    session = startLiveBarcodeScanner(video, callbacks);
    await vi.advanceTimersByTimeAsync(12_000);
    expect(track.stop).toHaveBeenCalledOnce();
    expect(callbacks.onError).toHaveBeenCalledExactlyOnceWith('unavailable');
    expect(decode).not.toHaveBeenCalled();
  });
});

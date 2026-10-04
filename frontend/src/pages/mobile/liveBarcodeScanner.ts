import { createBarcodeCanvasDecoder } from './barcodePhoto';

export type LiveScanFailure = 'secure-required' | 'unsupported' | 'permission' | 'unavailable' | 'interrupted';

export function liveCameraAvailability(): LiveScanFailure | undefined {
  if (!window.isSecureContext) return 'secure-required';
  if (!navigator.mediaDevices?.getUserMedia) return 'unsupported';
  return undefined;
}

/** No uploads, recording, business requests or audio capture. stop() works
 * even while the permission prompt / video.play promise is still pending. */
export function startLiveBarcodeScanner(video: HTMLVideoElement, callbacks: {
  onReady: () => void;
  onResult: (text: string) => void;
  onError: (reason: LiveScanFailure) => void;
}): { stop: () => void } {
  let stopped = false;
  let stream: MediaStream | undefined;
  let timer: number | undefined;
  let readyTimer: number | undefined;
  let frame = 0;
  let candidate = '';
  let candidateAt = 0;
  let ready = false;
  const canvas = document.createElement('canvas');
  const decode = createBarcodeCanvasDecoder();

  const stop = (): void => {
    if (stopped) return;
    stopped = true;
    window.clearTimeout(timer);
    window.clearTimeout(readyTimer);
    document.removeEventListener('visibilitychange', visibilityChanged);
    window.removeEventListener('pagehide', interrupted);
    video.removeEventListener('error', interrupted);
    stream?.getTracks().forEach((track) => {
      track.removeEventListener('ended', interrupted);
      track.stop();
    });
    video.pause();
    video.srcObject = null;
    canvas.width = canvas.height = 0;
  };
  const fail = (reason: LiveScanFailure): void => {
    if (stopped) return;
    stop();
    callbacks.onError(reason);
  };
  function interrupted(): void { fail('interrupted'); }
  function visibilityChanged(): void { if (document.hidden) interrupted(); }

  const scanFrame = (): void => {
    if (stopped) return;
    let text: string | undefined;
    if (video.readyState >= 2 && video.videoWidth > 0 && video.videoHeight > 0) {
      if (!ready) {
        ready = true;
        window.clearTimeout(readyTimer);
        callbacks.onReady();
        if (stopped) return;
      }
      try {
        // Alternate full view / lower resolution / center crop, without
        // allocating full-resolution photographs or overlapping decode loops.
        const crop = frame % 3 === 2 ? 0.75 : 1;
        const edge = frame % 3 === 1 ? 800 : 1280;
        const width = video.videoWidth * crop;
        const height = video.videoHeight * crop;
        const scale = Math.min(1, edge / Math.max(width, height));
        canvas.width = Math.max(1, Math.round(width * scale));
        canvas.height = Math.max(1, Math.round(height * scale));
        const context = canvas.getContext('2d', { willReadFrequently: true });
        if (!context) { fail('unavailable'); return; }
        context.drawImage(video, (video.videoWidth - width) / 2, (video.videoHeight - height) / 2,
          width, height, 0, 0, canvas.width, canvas.height);
        text = decode(canvas, { stage: 'decoding', attempts: 0 }, frame % 3 === 0);
      } catch {
        // A transient unreadable frame is normal; retry the next frame.
      }
      frame += 1;
    }
    if (text) {
      // Require the same payload in two recent frames; one session emits once.
      if (candidate === text && Date.now() - candidateAt <= 1800) {
        stop();
        callbacks.onResult(text);
        return;
      }
      candidate = text;
      candidateAt = Date.now();
    }
    timer = window.setTimeout(scanFrame, 250);
  };

  const acquire = async (): Promise<void> => {
    const unavailable = liveCameraAvailability();
    if (unavailable) { fail(unavailable); return; }
    document.addEventListener('visibilitychange', visibilityChanged);
    window.addEventListener('pagehide', interrupted);
    video.addEventListener('error', interrupted);
    try {
      const acquired = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 960 } },
      });
      if (stopped) { acquired.getTracks().forEach((track) => track.stop()); return; }
      stream = acquired;
      stream.getTracks().forEach((track) => track.addEventListener('ended', interrupted));
      video.muted = true;
      video.autoplay = true;
      video.playsInline = true;
      video.setAttribute('playsinline', '');
      video.srcObject = stream;
      readyTimer = window.setTimeout(() => fail('unavailable'), 12_000);
      await video.play();
    } catch (error) {
      fail(error instanceof Error && ['NotAllowedError', 'SecurityError'].includes(error.name)
        ? 'permission' : 'unavailable');
      return;
    }
    if (!stopped) scanFrame();
  };
  void acquire();
  return { stop };
}

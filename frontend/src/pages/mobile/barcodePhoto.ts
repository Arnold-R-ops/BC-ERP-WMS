import { BarcodeFormat, BrowserCodeReader, BrowserMultiFormatReader, BrowserQRCodeReader } from '@zxing/browser';
import { ChecksumException, FormatException, NotFoundException } from '@zxing/library';
import { recoverVersionOneQr } from './qrPerspective';

export type PhotoFailure = 'image-load' | 'image-render' | 'not-found';

export class BarcodePhotoError extends Error {
  constructor(public readonly kind: PhotoFailure) {
    super(kind);
    this.name = 'BarcodePhotoError';
  }
}

export interface PhotoDiagnostics {
  stage: 'loading' | 'decoding' | 'recognized' | PhotoFailure;
  width?: number;
  height?: number;
  canvasWidth?: number;
  canvasHeight?: number;
  attempts: number;
  perspectiveAttempts?: number;
  decoder?: 'qr' | 'qr-perspective' | 'multi-format';
  lastQrError?: string;
  lastDecoderError?: string;
}

const FORMATS = [
  BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
  BarcodeFormat.CODE_93, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
  BarcodeFormat.ITF, BarcodeFormat.CODABAR, BarcodeFormat.DATA_MATRIX,
];

// Normalize BEFORE invoking ZXing: its image-URL API allocates a canvas at
// the full camera resolution. Reuse one bounded canvas throughout retries.
const VARIANTS = [
  { edge: 1280, crop: 1, rotation: 0, contrast: false },
  { edge: 800, crop: 1, rotation: 0, contrast: false },
  { edge: 1600, crop: 0.82, rotation: 0, contrast: false },
  { edge: 1280, crop: 0.55, rotation: 0, contrast: false },
  { edge: 1280, crop: 1, rotation: 0, contrast: true },
  { edge: 1280, crop: 1, rotation: 90, contrast: false },
  { edge: 1280, crop: 1, rotation: 270, contrast: false },
  { edge: 1280, crop: 1, rotation: 180, contrast: false },
];

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const timer = window.setTimeout(() => finish(false), 15_000);
    function finish(loaded: boolean): void {
      window.clearTimeout(timer);
      image.onload = null;
      image.onerror = null;
      if (loaded && image.naturalWidth > 0 && image.naturalHeight > 0) resolve(image);
      else {
        image.removeAttribute('src');
        reject(new BarcodePhotoError('image-load'));
      }
    }
    image.onload = () => finish(true);
    image.onerror = () => finish(false);
    image.src = url;
  });
}

function drawVariant(canvas: HTMLCanvasElement, image: HTMLImageElement, variant: typeof VARIANTS[number]): void {
  const width = Math.max(1, Math.round(image.naturalWidth * variant.crop));
  const height = Math.max(1, Math.round(image.naturalHeight * variant.crop));
  const scale = Math.min(1, variant.edge / Math.max(width, height));
  const drawnWidth = Math.max(1, Math.round(width * scale));
  const drawnHeight = Math.max(1, Math.round(height * scale));
  const rotated = variant.rotation === 90 || variant.rotation === 270;
  canvas.width = rotated ? drawnHeight : drawnWidth;
  canvas.height = rotated ? drawnWidth : drawnHeight;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) throw new BarcodePhotoError('image-render');
  context.fillStyle = '#fff';
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = 'high';
  context.translate(canvas.width / 2, canvas.height / 2);
  context.rotate(variant.rotation * Math.PI / 180);
  context.drawImage(image, (image.naturalWidth - width) / 2, (image.naturalHeight - height) / 2,
    width, height, -drawnWidth / 2, -drawnHeight / 2, drawnWidth, drawnHeight);
  if (variant.contrast) {
    // Pixel processing does not depend on CanvasRenderingContext2D.filter
    // being implemented by the phone browser.
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
    for (let i = 0; i < pixels.data.length; i += 4) {
      const gray = pixels.data[i] * 0.299 + pixels.data[i + 1] * 0.587 + pixels.data[i + 2] * 0.114;
      const enhanced = Math.max(0, Math.min(255, (gray - 128) * 1.45 + 128));
      pixels.data[i] = pixels.data[i + 1] = pixels.data[i + 2] = enhanced;
    }
    context.putImageData(pixels, 0, 0);
  }
}

// Shared by still photos and live camera frames. Readers are reused for a
// session; the caller controls canvas size and the cost of geometric retries.
export function createBarcodeCanvasDecoder(): (
  canvas: HTMLCanvasElement, diagnostics: PhotoDiagnostics, allowPerspective?: boolean,
) => string | undefined {
  const qrReader = new BrowserQRCodeReader();
  const multiReader = new BrowserMultiFormatReader();
  multiReader.possibleFormats = FORMATS;
  return (canvas, diagnostics, allowPerspective = true) => {
    for (const reader of [qrReader, multiReader]) {
      diagnostics.attempts += 1;
      let text: string | undefined;
      let perspective = false;
      try {
        text = reader.decodeFromCanvas(canvas).getText().trim();
      } catch (error) {
        const kind = error instanceof ChecksumException ? 'ChecksumException'
          : error instanceof FormatException ? 'FormatException'
            : error instanceof NotFoundException ? 'NotFoundException'
              : error instanceof Error ? error.name : 'Error';
        diagnostics.lastDecoderError = `${kind}: ${error instanceof Error ? error.message ?? '' : String(error)}`;
        if (reader === qrReader) {
          diagnostics.lastQrError = diagnostics.lastDecoderError;
          if (allowPerspective && (error instanceof ChecksumException || error instanceof FormatException)) {
            diagnostics.perspectiveAttempts = (diagnostics.perspectiveAttempts ?? 0) + 1;
            try {
              text = recoverVersionOneQr(BrowserCodeReader.createBinaryBitmapFromCanvas(canvas).getBlackMatrix());
              perspective = !!text;
            } catch {
              // Preserve the original error; do not guess a payload.
            }
          }
        }
      }
      if (text) {
        diagnostics.stage = 'recognized';
        diagnostics.decoder = perspective ? 'qr-perspective' : reader === qrReader ? 'qr' : 'multi-format';
        return text;
      }
    }
    return undefined;
  };
}

export async function decodeBarcodePhoto(
  url: string,
  report?: (diagnostics: PhotoDiagnostics) => void,
): Promise<string> {
  const diagnostics: PhotoDiagnostics = { stage: 'loading', attempts: 0 };
  const publish = (): void => report?.({ ...diagnostics });
  publish();
  let image: HTMLImageElement | undefined;
  const canvas = document.createElement('canvas');
  try {
    image = await loadImage(url);
    diagnostics.width = image.naturalWidth;
    diagnostics.height = image.naturalHeight;
    const decodeCanvas = createBarcodeCanvasDecoder();
    for (const variant of VARIANTS) {
      diagnostics.stage = 'decoding';
      // Allow the loading indicator to paint between CPU-bound attempts.
      await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
      try {
        drawVariant(canvas, image, variant);
      } catch {
        throw new BarcodePhotoError('image-render');
      }
      diagnostics.canvasWidth = canvas.width;
      diagnostics.canvasHeight = canvas.height;
      const text = decodeCanvas(canvas, diagnostics);
      if (text) {
        publish();
        return text;
      }
      publish();
    }
    throw new BarcodePhotoError('not-found');
  } catch (error) {
    diagnostics.stage = error instanceof BarcodePhotoError ? error.kind : 'image-render';
    publish();
    throw error;
  } finally {
    // Safari may retain canvas backing stores until GC; release explicitly.
    canvas.width = 0;
    canvas.height = 0;
    image?.removeAttribute('src');
  }
}

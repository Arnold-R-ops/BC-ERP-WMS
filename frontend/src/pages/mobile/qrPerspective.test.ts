import { afterEach, describe, expect, it, vi } from 'vitest';
import { BinaryBitmap, BitMatrix, HybridBinarizer, QRCodeReader, RGBLuminanceSource } from '@zxing/library';
import { decodeBarcodePhoto, type PhotoDiagnostics } from './barcodePhoto';
import { recoverVersionOneQr } from './qrPerspective';
import fixture from './__fixtures__/safari-qr-perspective.json';

// Photographed QR region from the user's 2026-09-06 Safari failure screenshot.
// Only crop/resize/grayscale were applied: no perspective correction, binary
// threshold, module repair or expected payload was put into the fixture.
const compressed = Uint8Array.from(atob(fixture.grayGzipBase64), char => char.charCodeAt(0));
const photo = new Uint8ClampedArray(await new Response(
  new Response(compressed).body!.pipeThrough(new DecompressionStream('gzip')),
).arrayBuffer());
const { width, height } = fixture;
const expected = 'RCB-9EA067CFCEBF43CC';

function bitmap(gray: Uint8ClampedArray, w: number, h: number): BinaryBitmap {
  return new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(gray, w, h)));
}

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe('QR perspective recovery with real photo pixels', () => {
  it('reproduces the old checksum failure and decodes the same uncorrected pixels', () => {
    expect(photo.length).toBe(width * height);
    expect(() => new QRCodeReader().decode(bitmap(photo, width, height))).toThrow();
    expect(recoverVersionOneQr(bitmap(photo, width, height).getBlackMatrix())).toBe(expected);
  });

  it.each([1, 2, 3])('handles quarter-turn rotation %i without image-specific coordinates', (turns) => {
    let pixels = photo;
    let w = width;
    let h = height;
    for (let turn = 0; turn < turns; turn++) {
      const rotated = new Uint8ClampedArray(pixels.length);
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        rotated[x * h + h - 1 - y] = pixels[y * w + x];
      }
      pixels = rotated;
      [w, h] = [h, w];
    }
    expect(recoverVersionOneQr(bitmap(pixels, w, h).getBlackMatrix())).toBe(expected);
  });

  it('does not invent a code for blank pixels or an unreadable damaged QR', () => {
    expect(recoverVersionOneQr(new BitMatrix(240, 240))).toBeUndefined();
    const damaged = photo.slice();
    // Occlude much of the data while leaving the outside finder area intact.
    for (let y = 420; y < 560; y++) for (let x = 255; x < 465; x++) damaged[y * width + x] = 255;
    expect(recoverVersionOneQr(bitmap(damaged, width, height).getBlackMatrix())).toBeUndefined();
  });

  it('recovers through the shared photo entry using actual ZXing and reports the successful decoder', async () => {
    const rgba = new Uint8ClampedArray(width * height * 4);
    for (let i = 0; i < photo.length; i++) {
      rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = photo[i];
      rgba[i * 4 + 3] = 255;
    }
    class PhotoImage {
      naturalWidth = width;
      naturalHeight = height;
      onload: null | (() => void) = null;
      onerror: null | (() => void) = null;
      removeAttribute = vi.fn();
      set src(_url: string) { queueMicrotask(() => this.onload?.()); }
    }
    vi.stubGlobal('Image', PhotoImage);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(), fillRect: vi.fn(), translate: vi.fn(), rotate: vi.fn(),
      getImageData: () => ({ data: rgba, width, height }),
    } as unknown as CanvasRenderingContext2D);
    const states: PhotoDiagnostics[] = [];
    await expect(decodeBarcodePhoto('blob:regression-photo', state => states.push(state))).resolves.toBe(expected);
    expect(states.at(-1)).toMatchObject({
      stage: 'recognized', decoder: 'qr-perspective', attempts: 1, perspectiveAttempts: 1,
    });
    expect(states.at(-1)?.lastQrError).toContain('ChecksumException');
  });
});

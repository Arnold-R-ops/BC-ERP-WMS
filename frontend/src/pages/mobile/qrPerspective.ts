import { BitMatrix, DefaultGridSampler, PerspectiveTransform } from '@zxing/library';
import Decoder from '@zxing/library/esm/core/qrcode/decoder/Decoder';
import Detector from '@zxing/library/esm/core/qrcode/detector/Detector';

const OFFSETS = [0, -0.04, 0.04, -0.08, 0.08, -0.12, 0.12, -0.16, 0.16];

/**
 * Version 1 QR codes have no bottom-right alignment pattern. When a photo
 * has perspective distortion, ZXing extrapolates that corner as a
 * parallelogram, which can produce a checksum failure despite clear modules.
 * Search a small, bounded neighborhood in the detected QR's own coordinate
 * axes. This does not use any business code, known image region or expected
 * payload. Every candidate still passes the normal QR error-correction and
 * payload parser. Require two independent samples to agree, and reject any
 * conflicting decoded text rather than selecting an arbitrary result.
 */
export function recoverVersionOneQr(image: BitMatrix): string | undefined {
  let detected;
  try {
    detected = new Detector(image).detect(new Map());
  } catch {
    return undefined;
  }
  const dimension = detected.getBits().getWidth();
  const points = detected.getPoints();
  if (dimension !== 21 || points.length !== 3) return undefined;
  const [bottomLeft, topLeft, topRight] = points;
  const ux = topRight.getX() - topLeft.getX();
  const uy = topRight.getY() - topLeft.getY();
  const vx = bottomLeft.getX() - topLeft.getX();
  const vy = bottomLeft.getY() - topLeft.getY();
  const far = dimension - 3.5;
  const sampler = new DefaultGridSampler();
  const decoder = new Decoder();
  let agreed: string | undefined;
  let matches = 0;

  for (const x of OFFSETS) {
    for (const y of OFFSETS) {
      try {
        const transform = PerspectiveTransform.quadrilateralToQuadrilateral(
          3.5, 3.5, far, 3.5, far, far, 3.5, far,
          topLeft.getX(), topLeft.getY(), topRight.getX(), topRight.getY(),
          topLeft.getX() + (1 + x) * ux + (1 + y) * vx,
          topLeft.getY() + (1 + x) * uy + (1 + y) * vy,
          bottomLeft.getX(), bottomLeft.getY(),
        );
        const bits = sampler.sampleGridWithTransform(image, dimension, dimension, transform);
        const text = decoder.decodeBitMatrix(bits).getText().trim();
        if (!text) continue;
        if (agreed !== undefined && text !== agreed) return undefined;
        agreed = text;
        matches += 1;
      } catch {
        // A wrong geometric hypothesis must fail the normal QR validation.
      }
    }
  }
  return matches >= 2 ? agreed : undefined;
}

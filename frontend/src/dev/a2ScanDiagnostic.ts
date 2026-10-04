import { BarcodePhotoError, decodeBarcodePhoto, type PhotoDiagnostics } from '../pages/mobile/barcodePhoto';

// This HTML entry is served only by the local development server; it is not
// an entry in the production build. No photos or diagnostic data are posted.
const button = document.querySelector<HTMLButtonElement>('#self-test')!;
const capture = document.querySelector<HTMLInputElement>('#capture')!;
const album = document.querySelector<HTMLInputElement>('#album')!;
const photo = document.querySelector<HTMLImageElement>('#photo')!;
const photoResult = document.querySelector<HTMLElement>('#photo-result')!;
const details = document.querySelector<HTMLElement>('#diagnostics')!;
let previewUrl: string | undefined;

function busy(value: boolean): void {
  button.disabled = capture.disabled = album.disabled = value;
}

function description(error: unknown): string {
  if (error instanceof BarcodePhotoError) {
    if (error.kind === 'image-load') return '照片读取失败，浏览器未能加载这张图片。';
    if (error.kind === 'image-render') return '照片处理失败，浏览器未能创建或绘制识别画布。';
    return '图片已处理，但未识别到条码。';
  }
  return error instanceof Error ? error.message : String(error);
}

async function selfTest(): Promise<void> {
  busy(true);
  const result = document.querySelector<HTMLElement>('#self-result')!;
  const list = document.querySelector<HTMLUListElement>('#samples')!;
  list.replaceChildren();
  result.textContent = '正在检测内置样张…';
  try {
    const response = await fetch('/a2-mobile-barcodes.html', { cache: 'no-store' });
    if (!response.ok) throw new Error('无法读取测试样张页面');
    const page = new DOMParser().parseFromString(await response.text(), 'text/html');
    const stocktake = [...page.querySelectorAll('section')].find(section => section.querySelector('h2')?.textContent?.includes('Stocktake'));
    const cards = [...(stocktake?.querySelectorAll('.barcode-card') ?? [])];
    if (cards.length !== 4) throw new Error('盘点样张缺失，请检查测试页');
    const cases = cards.map((card, index) => ({ card, selector: '.qr svg', label: `二维码 · 批次 ${index + 1}` }));
    cases.push({ card: cards[0], selector: '.code128 svg', label: '条形码 · 批次 1' });
    let passed = 0;
    for (const test of cases) {
      const row = document.createElement('li');
      row.textContent = `${test.label}：检测中…`;
      list.append(row);
      const svg = test.card.querySelector(test.selector);
      if (!svg) throw new Error('缺少条码图像');
      if (test.selector === '.code128 svg') {
        // The source uses 1.35-unit bars. Rasterize at 3x (as a high-DPI
        // display would) so the self-test does not reduce narrow bars to 1px.
        svg.setAttribute('width', String(Number(svg.getAttribute('width')) * 3));
        svg.setAttribute('height', String(Number(svg.getAttribute('height')) * 3));
      }
      const expected = test.card.querySelector('code')!.textContent!.trim();
      const url = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' }));
      try {
        const actual = await decodeBarcodePhoto(url);
        if (actual !== expected) throw new Error(`结果不匹配：${actual}`);
        passed += 1;
        row.textContent = `${test.label}：通过 · ${actual}`;
      } catch (error) {
        row.textContent = `${test.label}：失败 · ${description(error)}`;
      } finally {
        URL.revokeObjectURL(url);
      }
    }
    result.textContent = `内置样张：${passed} / ${cases.length} 通过`;
  } catch (error) {
    result.textContent = `检测未完成：${description(error)}`;
  } finally {
    busy(false);
  }
}

async function checkPhoto(input: HTMLInputElement): Promise<void> {
  const file = input.files?.[0];
  if (!file) return;
  busy(true);
  if (previewUrl) URL.revokeObjectURL(previewUrl);
  previewUrl = URL.createObjectURL(file);
  photo.src = previewUrl;
  photo.hidden = false;
  photoResult.textContent = '正在检测照片…';
  details.hidden = false;
  const started = performance.now();
  let latest: PhotoDiagnostics | undefined;
  const showDetails = (): void => {
    details.textContent = JSON.stringify({
      version: 'A2-SCAN-0906-3', type: file.type || 'unknown', bytes: file.size,
      ...latest, elapsedMs: Math.round(performance.now() - started),
    }, null, 2);
  };
  try {
    const text = await decodeBarcodePhoto(previewUrl, (diagnostics) => { latest = diagnostics; showDetails(); });
    photoResult.textContent = `识别成功：${text}`;
  } catch (error) {
    photoResult.textContent = description(error);
  } finally {
    showDetails();
    input.value = '';
    busy(false);
  }
}

button.addEventListener('click', () => void selfTest());
capture.addEventListener('change', () => void checkPhoto(capture));
album.addEventListener('change', () => void checkPhoto(album));
document.querySelector<HTMLElement>('#device')!.textContent = navigator.userAgent;
window.addEventListener('pagehide', () => { if (previewUrl) URL.revokeObjectURL(previewUrl); });
void selfTest();

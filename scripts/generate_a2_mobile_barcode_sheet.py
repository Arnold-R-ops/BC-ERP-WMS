"""Generate the offline A2 mobile UAT barcode sheet served by Vite.

The page deliberately has no remote assets, so a phone can scan the computer
screen even when the UAT LAN has no Internet access.
"""

from __future__ import annotations

import hashlib
import re
from html import escape
from pathlib import Path

from reportlab.graphics import renderSVG
from reportlab.graphics.barcode import createBarcodeDrawing
from reportlab.graphics.barcode.qr import QrCodeWidget
from reportlab.graphics.shapes import Drawing


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "frontend" / "a2-mobile-barcodes.html"

SECTIONS = [
    (
        "1. 收货 / Receiving",
        [
            ("普通 SKU", "A2BAR002"),
            ("效期 SKU", "A2BAR003"),
            ("目标库位", "A2-WH-A-ZONE_A-A-01-001"),
        ],
    ),
    (
        "2. 拣货 / Picking",
        [
            ("来源库位", "A2-WH-A-ZONE_A-A-01-001"),
            ("批次 1", "RMA-1-1-CF157A08"),
            ("批次 2", "IQS-1-1-EDC5EB51"),
        ],
    ),
    (
        "3. 盘点 / Stocktake",
        [
            ("批次 1", "RCB-9EA067CFCEBF43CC"),
            ("批次 2", "RCB-9F3B5BDB5C7C4C75"),
            ("批次 3", "IQS-1-1-EDC5EB51"),
            ("批次 4", "RMA-1-1-CF157A08"),
        ],
    ),
]


def inline_svg(rendered: bytes | str, prefix: str) -> str:
    svg = rendered.decode("utf-8") if isinstance(rendered, bytes) else rendered
    svg = re.sub(r"<\?xml.*?\?>\s*", "", svg, flags=re.DOTALL)
    svg = re.sub(r"<!DOCTYPE svg.*?>\s*", "", svg, flags=re.DOTALL)
    # ReportLab gives every standalone SVG the same clip-path id. When many
    # SVGs are embedded into one HTML document, browser fragment resolution
    # can apply a different barcode's clip rectangle. The SVG viewport already
    # provides the required clipping, so remove the redundant clip path.
    svg = re.sub(r'<clipPath id="clip">.*?</clipPath>\s*', "", svg, flags=re.DOTALL)
    svg = svg.replace(' style="clip-path: url(#clip)"', "")
    svg = svg.replace('id="group"', f'id="{prefix}-group"')
    return svg


def svg_prefix(kind: str, value: str) -> str:
    digest = hashlib.sha256(f"{kind}:{value}".encode("utf-8")).hexdigest()[:12]
    return f"{kind}-{digest}"


def code128_svg(value: str, card_index: int) -> str:
    drawing = createBarcodeDrawing(
        "Code128",
        value=value,
        barHeight=76,
        barWidth=1.35,
        humanReadable=False,
    )
    rendered = renderSVG.drawToString(drawing)
    return inline_svg(rendered, svg_prefix("code128", f"{card_index}:{value}"))


def qr_svg(value: str, card_index: int) -> str:
    # The generated QR grid is 29 modules including its quiet zone. 348 px
    # keeps every module on an exact 12 px boundary when shown on screen.
    size = 348
    widget = QrCodeWidget(value)
    x1, y1, x2, y2 = widget.getBounds()
    width = x2 - x1
    height = y2 - y1
    drawing = Drawing(size, size, transform=[size / width, 0, 0, size / height, 0, 0])
    drawing.add(widget)
    rendered = renderSVG.drawToString(drawing)
    return inline_svg(rendered, svg_prefix("qr", f"{card_index}:{value}"))


def card(label: str, value: str, card_index: int) -> str:
    return f"""
      <article class="barcode-card">
        <div class="card-heading"><strong>{escape(label)}</strong><code>{escape(value)}</code></div>
        <div class="codes">
          <div class="qr">{qr_svg(value, card_index)}</div>
          <p>手机摄像头请只框住这一枚二维码。</p>
        </div>
        <details class="scanner-code">
          <summary>扫码枪兼容条码（后续设备验收使用）</summary>
          <div class="code128">{code128_svg(value, card_index)}</div>
        </details>
      </article>
    """


def main() -> None:
    rendered_sections = []
    card_index = 0
    for title, rows in SECTIONS:
        rendered_cards = []
        for label, value in rows:
            card_index += 1
            rendered_cards.append(card(label, value, card_index))
        rendered_sections.append(
            f"<section><h2>{escape(title)}</h2>{''.join(rendered_cards)}</section>"
        )
    sections = "\n".join(rendered_sections)
    html = f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>A2 手机扫码预验收条码</title>
  <style>
    :root {{ color-scheme: light; font-family: Inter, "Microsoft YaHei", sans-serif; }}
    body {{ margin: 0; padding: 28px; color: #172033; background: #eef2f8; }}
    main {{ max-width: 1180px; margin: 0 auto; }}
    header {{ margin-bottom: 24px; padding: 22px 26px; border-radius: 18px; background: #102a43; color: white; }}
    h1 {{ margin: 0 0 8px; font-size: 28px; }}
    header p {{ margin: 0; color: #d9e7f5; }}
    section {{ margin: 20px 0; padding: 20px; border-radius: 18px; background: white; box-shadow: 0 10px 30px rgba(16,42,67,.08); }}
    h2 {{ margin: 0 0 16px; font-size: 21px; }}
    .barcode-card {{ margin: 14px 0; padding: 18px; border: 2px solid #d8e2ee; border-radius: 14px; }}
    .card-heading {{ display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 14px; }}
    .card-heading strong {{ font-size: 18px; }}
    code {{ font-size: 16px; font-weight: 700; color: #005a9c; }}
    .codes {{ display: grid; justify-items: center; gap: 8px; }}
    .codes p {{ margin: 0 0 10px; color: #62748a; }}
    .code128 {{ min-height: 126px; padding: 18px; overflow: hidden; background: white; border: 1px solid #edf1f5; }}
    .code128 svg {{ display: block; width: 100%; height: 126px; }}
    .qr {{ padding: 8px; background: white; }}
    .qr svg {{ display: block; width: 348px; height: 348px; }}
    .scanner-code {{ margin-top: 12px; color: #62748a; }}
    .scanner-code summary {{ cursor: pointer; }}
    footer {{ padding: 12px 4px 36px; color: #62748a; }}
    @media (max-width: 720px) {{
      body {{ padding: 12px; }}
      .card-heading {{ align-items: flex-start; flex-direction: column; }}
      .qr svg {{ width: min(348px, calc(100vw - 88px)); height: auto; }}
    }}
    @media print {{ body {{ padding: 0; background: white; }} section {{ box-shadow: none; break-inside: avoid; }} }}
  </style>
</head>
<body>
  <main>
    <header>
      <h1>A2-3 手机扫码预验收条码</h1>
      <p>手机拍摄电脑屏幕时只扫描居中的大 QR Code；让单个码占取景框至少三分之一。下方一维码默认折叠，仅留作后续扫码枪验收。仅供隔离 UAT。</p>
    </header>
    {sections}
    <footer>Generated locally for A2 isolated mobile UAT. No external assets.</footer>
  </main>
</body>
</html>
"""
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(html, encoding="utf-8")
    print(OUTPUT)


if __name__ == "__main__":
    main()

# Safari QR regression sample

`safari-qr-perspective.json` stores a base64/gzip-compressed, 600 × 798,
row-major 8-bit luminance image. It contains only the photographed screen region from
the user-supplied 2026-09-06 failure screenshot. Browser chrome and diagnostic
text were excluded. The business data is the isolated A2 UAT batch code.

The source screenshot is 1280 × 2769. The crop starts at (192, 255), with size
896 × 1191, resized to fit 600 × 800. RGB luminance conversion matches ZXing.
No perspective correction, thresholding, module editing or payload repair
was applied. The existing QR reader fails its checksum on this image; the
bounded version-1 geometric recovery decodes `RCB-9EA067CFCEBF43CC`.

This fixture is consumed only by tests and is not placed in public assets.

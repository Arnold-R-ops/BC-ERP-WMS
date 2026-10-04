import { existsSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import viteConfig from '../../vite.config';

describe('production build boundary', () => {
  it('keeps UAT pages available only as development root pages and disables public maps', async () => {
    const config = typeof viteConfig === 'function'
      ? await viteConfig({ command: 'build', mode: 'production', isSsrBuild: false, isPreview: false })
      : await viteConfig;
    expect(config.build?.sourcemap).toBe(false);
    expect(Object.keys(config.build?.rollupOptions?.input ?? {})).toEqual(['company', 'platform']);
    expect(existsSync('public/a2-mobile-barcodes.html')).toBe(false);
    expect(existsSync('a2-mobile-barcodes.html')).toBe(true);
  });
});

import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
    // Ant Design/Pro components can cross Vitest's 5 s default when the
    // complete UI suite is importing and rendering in parallel.
    testTimeout: 10_000,
  },
});

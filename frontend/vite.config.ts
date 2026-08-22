import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

function projectEntry(fileName: string): string {
  return decodeURIComponent(new URL(`./${fileName}`, import.meta.url).pathname)
    .replace(/^\/([A-Za-z]:)/, '$1');
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  return {
    base: './',
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: 5173,
      proxy: {
        '/api': {
          target: env.WMS_API_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
        '/public': {
          target: env.WMS_API_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      // ProLayout's lazy-loaded icon vendor chunk is ~184 kB gzip.
      chunkSizeWarningLimit: 600,
      outDir: 'dist',
      sourcemap: true,
      rollupOptions: {
        input: {
          company: projectEntry('index.html'),
          platform: projectEntry('platform.html'),
        },
      },
    },
  };
});

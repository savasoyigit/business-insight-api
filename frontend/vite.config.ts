import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.VITE_PROXY_TARGET || 'http://localhost:8080';
  // Proxied calls are same-origin from the browser's view, so drop Origin to keep the API's CORS filter out of it.
  const proxy = {
    target,
    changeOrigin: true,
    configure: (p: { on: (e: 'proxyReq', cb: (req: { removeHeader: (h: string) => void }) => void) => void }) =>
      p.on('proxyReq', (req) => req.removeHeader('origin')),
  };
  return {
    plugins: [react()],
    // Relative base so the static build works on GitHub Pages / any sub-path.
    base: './',
    build: {
      rollupOptions: {
        output: { manualChunks: { charts: ['recharts'], react: ['react', 'react-dom', 'react-router-dom'] } },
      },
    },
    preview: { proxy: { '/api': proxy, '/actuator': proxy } },
    server: {
      port: 5173,
      proxy: {
        '/api': proxy,
        '/actuator': proxy,
      },
    },
  };
});

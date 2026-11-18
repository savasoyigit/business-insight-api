import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

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
  // The demo sandbox (mock API + sample data) ships in dev and `build:demo`, but not in the production build
  // unless VITE_ENABLE_DEMO=true. '@demo' resolves to a stub otherwise, so none of it is bundled.
  const demoEnabled = mode !== 'production' || env.VITE_ENABLE_DEMO === 'true';
  const demoEntry = demoEnabled ? './src/api/demo-available.ts' : './src/api/demo-unavailable.ts';
  return {
    plugins: [react()],
    resolve: { alias: { '@demo': fileURLToPath(new URL(demoEntry, import.meta.url)) } },
    // A build-time constant, so demo-only branches (login tab, personas) are dropped from production bundles too.
    define: { 'import.meta.env.VITE_DEMO_AVAILABLE': JSON.stringify(String(demoEnabled)) },
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

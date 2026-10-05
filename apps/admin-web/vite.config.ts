import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Admin Web (operations control plane) — Vite + React + TS.
// Proxies /api to local Spring Boot during dev to avoid CORS friction.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 5174
  }
});

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 백엔드 프록시 대상 — CORS 없이 /api 요청을 8080으로 전달한다
const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173, // 카카오 리다이렉트 URI가 5173 기준으로 등록되어 있음
    strictPort: true,
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        secure: false
      }
    }
  }
});

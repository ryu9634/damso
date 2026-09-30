import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from 'tailwindcss';
import autoprefixer from 'autoprefixer';

export default defineConfig({
  plugins: [react()],

  // postcss.config.js 를 따로 두지 않는다 — 그 파일의 내용은 "이 두 플러그인을 쓴다"가
  // 전부였고, 루트에 설정 파일만 하나 더 늘리는 값이었다.
  // 색·content 경로 같은 실제 설정은 tailwind.config.js 에 그대로 있다.
  css: {
    postcss: {
      plugins: [tailwindcss(), autoprefixer()],
    },
  },

  server: {
    // 기본은 예전처럼 5173. PORT 가 주어지면(미리보기 등) 그 포트로 뜬다.
    port: Number(process.env.PORT) || 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      // 소개팅 업로드 사진 (백엔드가 정적 제공)
      '/uploads': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
});

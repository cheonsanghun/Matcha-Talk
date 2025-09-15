import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // /api 로 시작하는 요청은 아래 target 주소로 전달합니다.
      '/api': {
        target: 'http://localhost:9999', // 백엔드 포트 9999로 수정
        changeOrigin: true,
      }
    }
  }
})

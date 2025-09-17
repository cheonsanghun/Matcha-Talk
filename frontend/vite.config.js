import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: true,
    allowedHosts: ['.ngrok-free.app'], // ngrok 도메인 허용
    proxy: {
      // /api로 시작하는 요청을 백엔드로 프록시
      '/api': {
        target: 'http://localhost:9999', // 실제 백엔드 포트에 맞게 수정
        changeOrigin: true,
        // 필요하다면 secure: false 추가
      },
      // 아래는 예시로 남김. 실제로는 위 target만 사용하면 됨.
      // '/api': 'http://localhost:8080',
    }
  }
})

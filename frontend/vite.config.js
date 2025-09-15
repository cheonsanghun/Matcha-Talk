import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
<<<<<<< HEAD
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
=======
  server: { proxy: { '/api': 'http://localhost:8080' },  port: 5173, open: true, allowedHosts: ['.ngrok-free.app'] // 또는 '.ngrok-free.app'
  },


>>>>>>> d2966bc67f81763680974d15cd8082e63a473da7
})

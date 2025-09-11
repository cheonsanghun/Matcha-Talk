import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': 'http://localhost:8080' },  port: 5173, open: true
  },
  allowedHosts: ['*.ngrok-free.app'] // 또는 '.ngrok-free.app'
  ,
  hmr: {
    host: '*.ngrok-free.app', // HMR가 ngrok 도메인으로 콜백
    clientPort: 443,                     // https 터널이면 443, http면 80
    // protocol: 'wss' // https 터널 환경에서 필요하면 사용
  },
})

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': 'http://localhost:8080' },  port: 5173, open: true, allowedHosts: ['.ngrok-free.app'] // 또는 '.ngrok-free.app'
  },


})

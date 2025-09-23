import fs from 'fs'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendOrigin = env.VITE_DEV_BACKEND_ORIGIN ?? 'http://localhost:8080'
  const lanHost = (env.VITE_DEV_ALLOWED_HOST || '').trim()
  const keyPath = (env.VITE_DEV_HTTPS_KEY || '').trim()
  const certPath = (env.VITE_DEV_HTTPS_CERT || '').trim()

  const allowedHosts = ['.ngrok-free.app']
  if (lanHost) {
    allowedHosts.push(lanHost)
  }

  let httpsConfig = false
  if (keyPath && certPath) {
    if (fs.existsSync(keyPath) && fs.existsSync(certPath)) {
      httpsConfig = {
        key: fs.readFileSync(keyPath),
        cert: fs.readFileSync(certPath),
      }
    } else {
      console.warn('[vite] 제공된 HTTPS 인증서 경로를 찾을 수 없습니다.', { keyPath, certPath })
    }
  } else if (keyPath || certPath) {
    console.warn('[vite] HTTPS 개발 서버를 위해서는 VITE_DEV_HTTPS_KEY와 VITE_DEV_HTTPS_CERT를 모두 지정해야 합니다.')
  }

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      open: true,
      allowedHosts,
      https: httpsConfig,
      proxy: {
        '/api': {
          target: backendOrigin,
          changeOrigin: true,
        },
        '/ws-stomp': {
          target: backendOrigin,
          changeOrigin: true,
          ws: true,
        },
      },
    },
  }
})

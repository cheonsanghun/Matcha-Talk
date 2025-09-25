import fs from 'fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

const frontendRoot = fileURLToPath(new URL('.', import.meta.url))

const resolvePath = (p) => {
  if (!p) return ''
  return path.isAbsolute(p) ? p : path.resolve(frontendRoot, p)
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendOrigin = env.VITE_DEV_BACKEND_ORIGIN ?? 'http://localhost:8080'
  const lanHostsEnv = (env.VITE_DEV_ALLOWED_HOSTS || env.VITE_DEV_ALLOWED_HOST || '').trim()
  const keyEnv = (env.VITE_DEV_HTTPS_KEY || '').trim()
  const certEnv = (env.VITE_DEV_HTTPS_CERT || '').trim()
  const keyPath = resolvePath(keyEnv)
  const certPath = resolvePath(certEnv)
  const devHost = (env.VITE_DEV_SERVER_HOST || '0.0.0.0').trim() || '0.0.0.0'
  const devPort = Number.parseInt(env.VITE_DEV_SERVER_PORT || '5173', 10)
  const hmrHost = (env.VITE_DEV_HMR_HOST || '').trim()
  const hmrPort = env.VITE_DEV_HMR_PORT ? Number.parseInt(env.VITE_DEV_HMR_PORT, 10) : undefined
  const hmrProtocol = (env.VITE_DEV_HMR_PROTOCOL || '').trim()
  const fallbackKeyPath = resolvePath('dev-key.pem')
  const fallbackCertPath = resolvePath('dev-cert.pem')

  const allowedHosts = new Set(['.ngrok-free.app', 'localhost', '127.0.0.1', '0.0.0.0', '[::1]'])
  if (lanHostsEnv) {
    lanHostsEnv
        .split(',')
        .map((host) => host.trim())
        .filter(Boolean)
        .forEach((host) => allowedHosts.add(host))
  }
  Object.values(os.networkInterfaces())
      .flatMap((netIfaces) => netIfaces ?? [])
      .filter((details) => Boolean(details) && !details.internal)
      .forEach((details) => {
        const host = details?.address?.trim()
        if (!host) return
        allowedHosts.add(host)
      })

  let httpsConfig = false
  if (keyPath && certPath) {
    if (fs.existsSync(keyPath) && fs.existsSync(certPath)) {
      httpsConfig = {
        key: fs.readFileSync(keyPath),
        cert: fs.readFileSync(certPath),
      }
    } else {
      console.warn('[vite] 제공된 HTTPS 인증서 경로를 찾을 수 없습니다.', {
        keyPath,
        certPath,
      })
    }
  } else if (keyEnv || certEnv) {
    console.warn('[vite] HTTPS 개발 서버를 위해서는 VITE_DEV_HTTPS_KEY와 VITE_DEV_HTTPS_CERT를 모두 지정해야 합니다.')
  } else if (fs.existsSync(fallbackKeyPath) && fs.existsSync(fallbackCertPath)) {
    httpsConfig = {
      key: fs.readFileSync(fallbackKeyPath),
      cert: fs.readFileSync(fallbackCertPath),
    }
    console.info('[vite] 개발용 기본 HTTPS 인증서를 사용합니다. 필요 시 VITE_DEV_HTTPS_KEY/VITE_DEV_HTTPS_CERT로 덮어쓸 수 있습니다.')
  }

  return {
    plugins: [vue()],
    server: {
      host: devHost,
      port: Number.isFinite(devPort) ? devPort : 5173,
      open: true,
      allowedHosts: Array.from(allowedHosts),
      https: httpsConfig,
      hmr: hmrHost || hmrPort || hmrProtocol
        ? {
            host: hmrHost || undefined,
            port: hmrPort,
            protocol: hmrProtocol || undefined,
          }
        : undefined,
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

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0', // 외부 접근 허용
    open: true,
    allowedHosts: [
      '.ngrok-free.app', // ngrok 도메인 허용
      //'192.168.0.165',   // ✅ 추가: 서버 IP 허용
      //'192.168.*',        // ✅ 추가: 같은 네트워크 대역 허용
      '192.0.0.2',       // ✅ 추가: 핫스팟 서버 IP 허용
      '192.0.0.*'        // ✅ 추가: 같은 핫스팟 네트워크 대역 허용
    ],
    proxy: {
      // /api로 시작하는 요청을 백엔드로 프록시
      '/api': {
        //target: 'http://192.168.0.165:8080', // ✅ 수정: 실제 서버 IP로 변경
        target: 'http://192.0.0.2:8080', // ✅ 수정: 실제 핫스팟 서버 IP로 변경
        changeOrigin: true,
        // 필요하다면 secure: false 추가
      },
      // 아래는 예시로 남김. 실제로는 위 target만 사용하면 됨.
      // '/api': 'http://localhost:8080',
    }
  }
})
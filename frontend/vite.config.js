//import {defineConfig} from 'vite'
import {defineConfig, loadEnv} from 'vite'
import vue from '@vitejs/plugin-vue'

//export default defineConfig({
  //  plugins: [vue()],
  //  server: {
  //      proxy: {'/api': 'http://192.168.0.165:8080'}, port: 5173, open: true, allowedHosts: ['.ngrok-free.app'] // 또는 '.ngrok-free.app'
  //  },
    export default defineConfig(({mode}) => {
        const env = loadEnv(mode, process.cwd(), '')
        const backendOrigin = env.VITE_DEV_BACKEND_ORIGIN ?? 'http://localhost:8080'
        const lanHost = env.VITE_DEV_ALLOWED_HOST ?? '192.168.0.165'
        const allowedHosts = ['.ngrok-free.app']
        if (lanHost) {
            allowedHosts.push(lanHost)
        }

        return {
            plugins: [vue()],
            server: {
                host: '0.0.0.0',
                port: 5173,
                open: true,
                allowedHosts,
                proxy: {
                    '/api': {
                        target: backendOrigin,
                        changeOrigin: true
                    },
                    '/ws-stomp': {
                        target: backendOrigin,
                        changeOrigin: true,
                        ws: true
                    }
                }
            }
        }

})

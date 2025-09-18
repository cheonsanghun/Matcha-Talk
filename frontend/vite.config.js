import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
    plugins: [vue()],
    server: {
        port: 5173,
        open: true,
        allowedHosts: ['.ngrok-free.app'],
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/ws-stomp': {
                target: 'http://localhost:8080',
                ws: true,
                changeOrigin: true,
            },
        },
    },

    build: {
        outDir: '../backend/src/main/resources/static',
        emptyOutDir: true,
        assetsDir: 'assets',
    },
})

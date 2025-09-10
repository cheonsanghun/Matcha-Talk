import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// 백엔드 WebSocket 엔드포인트는 WebSocketConfig.java의 registry.addEndpoint("...") 값을 확인해 넣으세요.
// 흔한 예: /ws-stomp  (정확한 path를 WebSocketConfig에서 확인 필요)
const WS_PATH = import.meta.env.VITE_WS_PATH || '/ws-stomp'

// axios baseURL에서 호스트 추출
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const origin = apiBase.replace(/\/api\/?$/, '')

export function createStompClient(token) {
    const client = new Client({
        webSocketFactory: () => new SockJS(origin + WS_PATH),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        reconnectDelay: 3000,
        debug: () => {} // 필요 시 콘솔 출력
    })
    return client
}

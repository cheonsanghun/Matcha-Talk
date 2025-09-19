import {Client} from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// 백엔드 WebSocket 엔드포인트는 WebSocketConfig.java의 registry.addEndpoint("...") 값을 확인해 넣으세요.
// 흔한 예: /ws-stomp  (정확한 path를 WebSocketConfig에서 확인 필요)
const WS_PATH = import.meta.env.VITE_WS_PATH || '/ws-stomp'

// axios baseURL에서 호스트 추출
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
/*const origin = apiBase.replace(/\/api\/?$/, '')

export function createStompClient(token) {
    const baseOrigin = origin || (typeof window !== 'undefined' ? window.location.origin : '')
    const wsUrl = new URL(WS_PATH, baseOrigin)
    if (token) {
        wsUrl.searchParams.set('access_token', token)
    }

    const client = new Client({
        webSocketFactory: () => new SockJS(wsUrl.toString()),
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        reconnectDelay: 3000,
        debug: () => {} // 필요 시 콘솔 출력
    })
    return client
}*/

const origin = apiBase.replace(/\/api\/?$/, '')

function buildSockJsUrl(token) {
    const baseOrigin = origin || (typeof window !== 'undefined' ? window.location.origin : '')
    const wsUrl = new URL(WS_PATH, baseOrigin)
    if (token) {
        wsUrl.searchParams.set('access_token', token)
    }
    return wsUrl.toString()
}

export function createStompClient(tokenProvider) {
    const resolveToken = typeof tokenProvider === 'function' ? tokenProvider : () => tokenProvider
    const client = new Client({
        reconnectDelay: 3000,
        debug: () => {
        }
    })

    client.beforeConnect = () => {
        const token = resolveToken()
        client.connectHeaders = token ? {Authorization: `Bearer ${token}`} : {}
        client.webSocketFactory = () => new SockJS(buildSockJsUrl(token))
    }
    client.heartbeatIncoming = 10000
    client.heartbeatOutgoing = 10000

    return client
}

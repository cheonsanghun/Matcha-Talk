import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { SOCK_JS_URL } from './endpoints'

export function createStompClient(token) {
  const client = new Client({
    webSocketFactory: () => new SockJS(SOCK_JS_URL),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 3000,
    debug: () => {}, // 필요 시 콘솔 출력
  })
  return client
}

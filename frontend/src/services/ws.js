import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { SOCK_JS_URL } from './endpoints'

export function createStompClient(token) {
  let resolvedToken = token
  if (!resolvedToken && typeof localStorage !== 'undefined') {
    resolvedToken = localStorage.getItem('token') || null
  }
  if (resolvedToken && resolvedToken.startsWith('Bearer ')) {
    resolvedToken = resolvedToken.slice('Bearer '.length)
  }

  const headers = {}
  if (resolvedToken) {
    const bearer = `Bearer ${resolvedToken}`
    headers.Authorization = bearer
    headers.authorization = bearer
  }

  const client = new Client({
    webSocketFactory: () => new SockJS(SOCK_JS_URL),
    connectHeaders: headers,
    reconnectDelay: 3000,
    debug: () => {}, // 필요 시 콘솔 출력
  })
  return client
}

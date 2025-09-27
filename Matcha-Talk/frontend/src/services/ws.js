import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

const DEFAULT_HTTP_ORIGIN = (() => {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin
  }
  return 'http://localhost:8080'
})()

function resolveHttpOrigin() {
  const configured = import.meta.env.VITE_API_BASE_URL
  if (configured) {
    try {
      const url = new URL(configured, DEFAULT_HTTP_ORIGIN)
      return url.origin
    } catch (error) {
      console.warn('[ws] VITE_API_BASE_URL 파싱에 실패하여 기본 오리진으로 대체합니다.', error)
    }
  }
  return DEFAULT_HTTP_ORIGIN
}

function ensureLeadingSlash(path) {
  if (!path) return '/ws'
  return path.startsWith('/') ? path : `/${path}`
}

const HTTP_ORIGIN = resolveHttpOrigin()
const WS_ENDPOINT_PATH = ensureLeadingSlash(import.meta.env.VITE_WS_PATH || '/ws')

function buildEndpointUrl() {
  const base = new URL(WS_ENDPOINT_PATH, HTTP_ORIGIN)
  return base.toString()
}

function ensureHandlerSet(map, key) {
  if (!map.has(key)) {
    map.set(key, new Set())
  }
  return map.get(key)
}

function createSockJsSocket(endpointUrl) {
  if (typeof window === 'undefined') {
    throw new Error('Realtime STOMP 클라이언트는 브라우저 환경에서만 사용할 수 있습니다.')
  }
  const Sock = typeof window.SockJS === 'function' ? window.SockJS : SockJS
  if (typeof Sock !== 'function') {
    throw new Error('SockJS 클라이언트를 초기화할 수 없습니다.')
  }
  return new Sock(endpointUrl, null, {
    transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
    withCredentials: true,
  })
}

class RealtimeStompClient {
  constructor(options = {}) {
    if (typeof options === 'string') {
      console.warn('[ws] loginId 인자는 더 이상 필요하지 않습니다. 세션 쿠키로 인증합니다.')
      options = {}
    }

    this.connectHeaders = { ...(options.connectHeaders ?? {}) }
    this.client = null
    this.connectPromise = null
    this.manualDisconnect = false

    this.eventHandlers = new Map()
    this.openHandlers = new Set()
    this.closeHandlers = new Set()
    this.errorHandlers = new Set()
    this.eventSubscription = null
  }

  setLoginId() {
    console.warn('[ws] loginId 설정은 사용되지 않습니다. 세션 쿠키를 통해 인증합니다.')
  }

  // 호환성을 위해 유지하되 더 이상 사용되지 않음을 안내
  setToken() {
    console.warn('[ws] 토큰 기반 STOMP 인증은 비활성화되었습니다. 세션 쿠키를 확인하세요.')
  }

  setConnectHeaders(headers = {}) {
    this.connectHeaders = { ...headers }
  }

  isConnected() {
    return !!this.client?.connected
  }

  async connect() {
    if (this.isConnected()) return
    if (this.connectPromise) return this.connectPromise

    const endpointUrl = buildEndpointUrl()
    this.manualDisconnect = false

    this.client = new Client({
      debug: () => {},
      reconnectDelay: 0,
      connectHeaders: { ...this.connectHeaders },
      webSocketFactory: () => createSockJsSocket(endpointUrl),
    })

    this.connectPromise = new Promise((resolve, reject) => {
      const client = this.client
      let settled = false

      client.onConnect = (frame) => {
        settled = true
        this.connectPromise = null
        this._ensureEventSubscription()
        this.openHandlers.forEach((handler) => {
          try {
            handler(frame)
          } catch (error) {
            console.error('[ws] onConnect 핸들러 실행 중 오류', error)
          }
        })
        resolve()
      }

      client.onStompError = (frame) => {
        this.errorHandlers.forEach((handler) => {
          try {
            handler(frame)
          } catch (error) {
            console.error('[ws] onStompError 핸들러 실행 중 오류', error)
          }
        })
      }

      client.onWebSocketError = (event) => {
        this.errorHandlers.forEach((handler) => {
          try {
            handler(event)
          } catch (error) {
            console.error('[ws] onWebSocketError 핸들러 실행 중 오류', error)
          }
        })
      }

      client.onWebSocketClose = (event) => {
        this.eventSubscription?.unsubscribe?.()
        this.eventSubscription = null
        this.closeHandlers.forEach((handler) => {
          try {
            handler(event)
          } catch (error) {
            console.error('[ws] onClose 핸들러 실행 중 오류', error)
          }
        })
        this.client = null
        if (!settled && !this.manualDisconnect) {
          reject(new Error(`웹소켓 연결이 완료되기 전에 종료되었습니다. code=${event?.code ?? 'unknown'}`))
        } else if (!settled) {
          reject(new Error('웹소켓 연결이 정상적으로 수립되지 못했습니다.'))
        }
        this.connectPromise = null
      }

      try {
        client.activate()
      } catch (error) {
        this.connectPromise = null
        this.client = null
        reject(error)
      }
    })

    return this.connectPromise
  }

  async disconnect() {
    this.manualDisconnect = true
    if (!this.client) return
    try {
      await this.client.deactivate()
    } finally {
      this.client = null
      this.eventSubscription = null
      this.connectPromise = null
    }
  }

  subscribe(destination, callback, headers = {}) {
    if (!this.client) {
      throw new Error('STOMP 클라이언트가 초기화되지 않았습니다. connect()를 먼저 호출하세요.')
    }
    if (!this.isConnected()) {
      throw new Error('연결이 완료되기 전에 구독을 요청했습니다.')
    }
    const subscription = this.client.subscribe(destination, (message) => {
      try {
        callback(message)
      } catch (error) {
        console.error(`[ws] ${destination} 구독 처리 중 오류`, error)
      }
    }, headers)

    return {
      unsubscribe() {
        try {
          subscription.unsubscribe()
        } catch (error) {
          console.warn('[ws] 구독 해제 중 오류', error)
        }
      },
    }
  }

  publish({ destination, body, headers = {} }) {
    if (!this.client) {
      throw new Error('STOMP 클라이언트가 초기화되지 않았습니다. connect()를 먼저 호출하세요.')
    }
    if (!this.isConnected()) {
      throw new Error('STOMP 연결이 활성화되어 있지 않아 메시지를 전송할 수 없습니다.')
    }
    const payload = typeof body === 'string' ? body : JSON.stringify(body ?? {})
    this.client.publish({ destination, body: payload, headers })
  }

  onEvent(eventName, handler) {
    const handlers = ensureHandlerSet(this.eventHandlers, eventName)
    handlers.add(handler)
    if (this.isConnected()) {
      this._ensureEventSubscription()
    }
    return () => handlers.delete(handler)
  }

  onOpen(handler) {
    this.openHandlers.add(handler)
    return () => this.openHandlers.delete(handler)
  }

  onClose(handler) {
    this.closeHandlers.add(handler)
    return () => this.closeHandlers.delete(handler)
  }

  onError(handler) {
    this.errorHandlers.add(handler)
    return () => this.errorHandlers.delete(handler)
  }

  _ensureEventSubscription() {
    if (!this.client || !this.client.connected) return
    if (this.eventSubscription) return

    this.eventSubscription = this.client.subscribe('/user/queue/events', (message) => {
      let parsed
      try {
        parsed = JSON.parse(message.body)
      } catch (error) {
        console.error('[ws] 이벤트 페이로드 파싱 실패', error, message.body)
        return
      }

      const eventName = parsed?.event
      if (!eventName) return

      const handlers = this.eventHandlers.get(eventName)
      if (!handlers?.size) return

      handlers.forEach((handler) => {
        try {
          handler(parsed.payload, parsed)
        } catch (error) {
          console.error(`[ws] ${eventName} 이벤트 처리 중 오류`, error)
        }
      })
    })
  }
}

function createRealtimeClient(options = {}) {
  if (typeof options === 'string') {
    return new RealtimeStompClient(options)
  }
  return new RealtimeStompClient(options)
}

export { RealtimeStompClient, RealtimeStompClient as RealtimeWebSocketClient, createRealtimeClient }

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
      console.warn('[ws] Failed to parse VITE_API_BASE_URL, fallback to default origin.', error)
    }
  }
  return DEFAULT_HTTP_ORIGIN
}

const HTTP_ORIGIN = resolveHttpOrigin()
let configuredEndpoint = import.meta.env.VITE_WS_PATH || '/ws/chat'
if (typeof configuredEndpoint === 'string' && configuredEndpoint.includes('ws-stomp')) {
  console.warn('[ws] Detected legacy VITE_WS_PATH ("%s"), falling back to /ws/chat.', configuredEndpoint)
  configuredEndpoint = '/ws/chat'
}
if (configuredEndpoint && !configuredEndpoint.startsWith('/')) {
  configuredEndpoint = '/' + configuredEndpoint
}
const WS_ENDPOINT_PATH = configuredEndpoint

function buildWebSocketUrl(queryParams = {}) {
  const wsOrigin = HTTP_ORIGIN.replace(/^http/, 'ws')
  const url = new URL(WS_ENDPOINT_PATH, wsOrigin.endsWith('/') ? wsOrigin : `${wsOrigin}/`)

  if (queryParams && typeof queryParams === 'object') {
    Object.entries(queryParams).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') {
        return
      }

      if (Array.isArray(value)) {
        value
          .filter((entry) => entry !== undefined && entry !== null && entry !== '')
          .forEach((entry) => url.searchParams.append(key, entry))
        return
      }

      url.searchParams.set(key, value)
    })
  }

  return url.toString()
}

function ensureHandlerSet(map, key) {
  if (!map.has(key)) {
    map.set(key, new Set())
  }
  return map.get(key)
}

class RealtimeWebSocketClient {
  constructor({ queryParams = {} } = {}) {
    this.queryParams = { ...queryParams }
    this.socket = null
    this.connectPromise = null
    this.manualClose = false

    this.eventHandlers = new Map()
    this.openHandlers = new Set()
    this.closeHandlers = new Set()
    this.errorHandlers = new Set()
    this.rawMessageHandlers = new Set()
  }

  setQueryParams(nextParams = {}) {
    if (!nextParams || typeof nextParams !== 'object') {
      this.queryParams = {}
      return
    }
    this.queryParams = { ...nextParams }
  }

  isConnected() {
    return this.socket?.readyState === WebSocket.OPEN
  }

  async connect() {
    if (this.isConnected()) {
      return
    }

    if (this.socket && this.socket.readyState === WebSocket.CONNECTING) {
      return this.connectPromise
    }

    const url = buildWebSocketUrl(this.queryParams)
    this.manualClose = false

    this.connectPromise = new Promise((resolve, reject) => {
      const socket = new WebSocket(url)
      this.socket = socket
      let opened = false

      socket.onopen = (event) => {
        opened = true
        this.connectPromise = null
        this.openHandlers.forEach((handler) => {
          try { handler(event) } catch (error) { console.error('[ws] onopen handler failed', error) }
        })
        resolve()
      }

      socket.onmessage = (event) => {
        this.rawMessageHandlers.forEach((handler) => {
          try { handler(event) } catch (error) { console.error('[ws] raw message handler failed', error) }
        })

        const handlePayload = (text) => {
          try {
            const parsed = JSON.parse(text)
            const eventName = parsed?.event
            const payload = parsed?.payload

            if (eventName) {
              const handlers = this.eventHandlers.get(eventName)
              if (handlers?.size) {
                handlers.forEach((handler) => {
                  try { handler(payload, parsed) } catch (error) { console.error(`[ws] handler for ${eventName} failed`, error) }
                })
              }
            }
          } catch (error) {
            console.error('[ws] Failed to parse WebSocket payload', error, text)
          }
        }

        if (typeof event.data === 'string') {
          handlePayload(event.data)
        } else if (event.data instanceof Blob) {
          event.data.text().then(handlePayload).catch((error) => {
            console.error('[ws] Failed to read Blob payload', error)
          })
        } else {
          console.warn('[ws] Unsupported message data type:', typeof event.data)
        }
      }

      socket.onerror = (event) => {
        this.errorHandlers.forEach((handler) => {
          try { handler(event) } catch (error) { console.error('[ws] onerror handler failed', error) }
        })
      }

      socket.onclose = (event) => {
        this.connectPromise = null
        this.socket = null

        this.closeHandlers.forEach((handler) => {
          try { handler(event) } catch (error) { console.error('[ws] onclose handler failed', error) }
        })

        if (!opened) {
          reject(new Error(`WebSocket connection closed before opening (code=${event.code})`))
        }
      }
    })

    return this.connectPromise
  }

  disconnect(code, reason) {
    this.manualClose = true
    if (this.socket) {
      try {
        this.socket.close(code ?? 1000, reason)
      } catch (error) {
        console.warn('[ws] Failed to close WebSocket cleanly', error)
      }
    }
    this.socket = null
    this.connectPromise = null
  }

  send(payload) {
    if (!this.isConnected()) {
      throw new Error('Cannot send message because WebSocket is not connected.')
    }
    try {
      const json = typeof payload === 'string' ? payload : JSON.stringify(payload)
      this.socket.send(json)
    } catch (error) {
      console.error('[ws] Failed to send WebSocket message', error)
      throw error
    }
  }

  onEvent(eventName, handler) {
    const handlers = ensureHandlerSet(this.eventHandlers, eventName)
    handlers.add(handler)
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

  onRawMessage(handler) {
    this.rawMessageHandlers.add(handler)
    return () => this.rawMessageHandlers.delete(handler)
  }
}

export function createRealtimeClient(options = {}) {
  return new RealtimeWebSocketClient(options)
}

export { RealtimeWebSocketClient }

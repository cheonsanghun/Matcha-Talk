import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { SOCK_JS_URL } from './endpoints'

const INITIAL_BACKOFF_MS = 1000
const MAX_BACKOFF_MS = 30000

function normalizeToken(rawToken) {
  if (!rawToken) {
    return null
  }
  return rawToken.startsWith('Bearer ') ? rawToken.slice('Bearer '.length) : rawToken
}

function buildHeaders(token) {
  if (!token) {
    return {}
  }
  const bearer = `Bearer ${token}`
  return {
    Authorization: bearer,
    authorization: bearer,
  }
}

export function createStompClient(options = {}) {
  const config =
    typeof options === 'string' || options instanceof String ? { token: options } : { ...(options || {}) }

  let resolvedToken = normalizeToken(
    config.token ?? (typeof localStorage !== 'undefined' ? localStorage.getItem('token') : null)
  )
  let connectHeaders = buildHeaders(resolvedToken)

  const client = new Client({
    webSocketFactory: () => new SockJS(SOCK_JS_URL),
    connectHeaders: { ...connectHeaders },
    reconnectDelay: 0,
    debug:
      typeof config.debug === 'function'
        ? config.debug
        : import.meta.env.DEV
          ? (...args) => console.log('[stomp]', ...args)
          : () => {},
  })

  const refreshTokenFn = typeof config.refreshToken === 'function' ? config.refreshToken : null
  const onTokenRefreshed = typeof config.onTokenRefreshed === 'function' ? config.onTokenRefreshed : null

  let refreshInFlight = null
  let reconnectDelay = INITIAL_BACKOFF_MS
  let reconnectTimeoutId = null
  let manualStop = false

  const logWarn = (...args) => {
    if (import.meta.env.DEV) {
      console.warn('[stomp]', ...args)
    }
  }

  const logDebug = (...args) => {
    if (import.meta.env.DEV) {
      console.debug('[stomp]', ...args)
    }
  }

  const applyHeadersFromToken = (token) => {
    resolvedToken = normalizeToken(token)
    connectHeaders = buildHeaders(resolvedToken)
    client.connectHeaders = { ...connectHeaders }
    if (typeof localStorage !== 'undefined') {
      if (resolvedToken) {
        localStorage.setItem('token', resolvedToken)
      } else {
        localStorage.removeItem('token')
      }
    }
    if (resolvedToken && onTokenRefreshed) {
      onTokenRefreshed(resolvedToken)
    }
  }

  async function attemptTokenRefresh(reason) {
    if (!refreshTokenFn) {
      return false
    }
    if (refreshInFlight) {
      return refreshInFlight
    }

    refreshInFlight = (async () => {
      try {
        const result = await refreshTokenFn({ reason })
        const nextToken =
          typeof result === 'string'
            ? result
            : result && typeof result === 'object'
              ? result.token ?? result.accessToken ?? null
              : null
        if (!nextToken) {
          logWarn('Token refresh did not return a token. reason=', reason)
          return false
        }
        applyHeadersFromToken(nextToken)
        return true
      } catch (error) {
        logWarn('Token refresh failed.', error)
        return false
      } finally {
        refreshInFlight = null
      }
    })()

    return refreshInFlight
  }

  const clearReconnectTimer = () => {
    if (reconnectTimeoutId) {
      clearTimeout(reconnectTimeoutId)
      reconnectTimeoutId = null
    }
  }

  const deactivateQuietly = async () => {
    try {
      if (client.active || client.connected) {
        await client.deactivate()
      }
    } catch (error) {
      logWarn('Failed to deactivate STOMP client during reconnect.', error)
    }
  }

  const scheduleReconnect = (source) => {
    if (manualStop) {
      return
    }
    if (reconnectTimeoutId) {
      return
    }
    const delay = reconnectDelay
    reconnectDelay = Math.min(reconnectDelay * 2, MAX_BACKOFF_MS)
    reconnectTimeoutId = setTimeout(async () => {
      reconnectTimeoutId = null
      if (manualStop) {
        return
      }
      logDebug('Attempting STOMP reconnect. source=%s delay=%dms', source, delay)
      await deactivateQuietly()
      try {
        client.activate()
      } catch (error) {
        logWarn('STOMP reactivation failed. source=%s', source, error)
        scheduleReconnect('retry')
      }
    }, delay)
    logWarn(`Scheduled STOMP reconnect in ${delay}ms (source=${source})`)
  }

  client.beforeConnect = () => {
    client.connectHeaders = { ...connectHeaders }
  }

  client.onConnect = (frame) => {
    manualStop = false
    reconnectDelay = INITIAL_BACKOFF_MS
    clearReconnectTimer()
    if (typeof client.__userOnConnect === 'function') {
      try {
        client.__userOnConnect(frame)
      } catch (error) {
        logWarn('User onConnect handler threw an error.', error)
      }
    }
  }

  client.onDisconnect = (frame) => {
    if (typeof client.__userOnDisconnect === 'function') {
      try {
        client.__userOnDisconnect(frame)
      } catch (error) {
        logWarn('User onDisconnect handler threw an error.', error)
      }
    }
  }

  client.onStompError = async (frame) => {
    const message = frame?.headers?.message || ''
    const body = frame?.body || ''
    const combined = `${message} ${body}`.toLowerCase()
    const authError = /unauthor|forbidden|denied|expired/.test(combined)

    if (typeof client.__userOnStompError === 'function') {
      try {
        client.__userOnStompError(frame)
      } catch (error) {
        logWarn('User onStompError handler threw an error.', error)
      }
    }

    if (authError) {
      const refreshed = await attemptTokenRefresh('stomp-error')
      if (refreshed) {
        scheduleReconnect('token-refreshed')
        return
      }
    }

    scheduleReconnect('stomp-error')
  }

  client.onWebSocketClose = async (event) => {
    const reason = `${event?.reason || ''}`.toLowerCase()
    const code = event?.code ?? 0
    const authHint = reason.includes('401') || reason.includes('unauthor') || code === 4001

    if (typeof client.__userOnWebSocketClose === 'function') {
      try {
        client.__userOnWebSocketClose(event)
      } catch (error) {
        logWarn('User onWebSocketClose handler threw an error.', error)
      }
    }

    if (authHint) {
      const refreshed = await attemptTokenRefresh('ws-close')
      if (refreshed) {
        scheduleReconnect('token-refreshed')
        return
      }
    }

    scheduleReconnect('ws-close')
  }

  client.onWebSocketError = (event) => {
    if (typeof client.__userOnWebSocketError === 'function') {
      try {
        client.__userOnWebSocketError(event)
      } catch (error) {
        logWarn('User onWebSocketError handler threw an error.', error)
      }
    }
  }

  const proxy = new Proxy(client, {
    get(target, prop, receiver) {
      if (prop === 'onConnect') return target.__userOnConnect
      if (prop === 'onDisconnect') return target.__userOnDisconnect
      if (prop === 'onStompError') return target.__userOnStompError
      if (prop === 'onWebSocketClose') return target.__userOnWebSocketClose
      if (prop === 'onWebSocketError') return target.__userOnWebSocketError
      if (prop === 'updateToken') return applyHeadersFromToken
      if (prop === 'stopAndWait') {
        return async () => {
          manualStop = true
          clearReconnectTimer()
          await deactivateQuietly()
        }
      }
      if (prop === 'activate') {
        return (...args) => {
          manualStop = false
          reconnectDelay = INITIAL_BACKOFF_MS
          return Reflect.get(target, prop).apply(target, args)
        }
      }
      if (prop === 'deactivate') {
        return async (...args) => {
          manualStop = true
          clearReconnectTimer()
          return Reflect.get(target, prop).apply(target, args)
        }
      }
      return Reflect.get(target, prop, receiver)
    },
    set(target, prop, value) {
      if (prop === 'onConnect') {
        target.__userOnConnect = value
        return true
      }
      if (prop === 'onDisconnect') {
        target.__userOnDisconnect = value
        return true
      }
      if (prop === 'onStompError') {
        target.__userOnStompError = value
        return true
      }
      if (prop === 'onWebSocketClose') {
        target.__userOnWebSocketClose = value
        return true
      }
      if (prop === 'onWebSocketError') {
        target.__userOnWebSocketError = value
        return true
      }
      return Reflect.set(target, prop, value)
    },
  })

  proxy.reconnectNow = async () => {
    manualStop = false
    clearReconnectTimer()
    await deactivateQuietly()
    client.activate()
  }

  return proxy
}

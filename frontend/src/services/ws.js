import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { getAccessToken } from './api'

const WS_ENDPOINT = import.meta.env.VITE_WS_ENDPOINT ?? '/ws-stomp'

let client = null
let connectPromise = null

const ensureClient = () => {
  if (client) {
    return client
  }

  client = new Client({
    webSocketFactory: () => new SockJS(WS_ENDPOINT),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: (msg) => {
      if (import.meta.env.DEV) {
        console.debug(`[STOMP] ${msg}`)
      }
    },
  })

  client.beforeConnect = () => {
    const token = getAccessToken()
    client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
  }

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers['message'], frame.body)
  }

  client.onDisconnect = () => {
    connectPromise = null
  }

  client.onWebSocketClose = () => {
    connectPromise = null
  }

  return client
}

export const connect = () => {
  const c = ensureClient()
  if (c.connected) {
    return Promise.resolve(c)
  }
  if (connectPromise) {
    return connectPromise
  }

  connectPromise = new Promise((resolve, reject) => {
    c.onConnect = () => {
      resolve(c)
    }
    c.onWebSocketError = (event) => {
      console.error('WebSocket error', event)
      if (!c.connected) {
        reject(event)
        connectPromise = null
      }
    }
    c.activate()
  })

  return connectPromise
}

export const disconnect = async () => {
  if (client) {
    await client.deactivate()
    client = null
    connectPromise = null
  }
}

export const subscribe = async (destination, callback, headers = {}) => {
  const c = await connect()
  const subscription = c.subscribe(
    destination,
    (message) => {
      let payload = message.body
      try {
        payload = JSON.parse(message.body)
      } catch (_) {
        payload = message.body
      }
      callback(payload, message)
    },
    headers,
  )

  return () => {
    try {
      subscription.unsubscribe()
    } catch (e) {
      if (import.meta.env.DEV) {
        console.warn('구독 해제 중 오류', e)
      }
    }
  }
}

export const send = async (destination, body, headers = {}) => {
  const c = await connect()
  const payload = typeof body === 'string' ? body : JSON.stringify(body)
  c.publish({ destination, body: payload, headers })
}

export const isConnected = () => Boolean(client?.connected)

export default {
  connect,
  disconnect,
  subscribe,
  send,
  isConnected,
}

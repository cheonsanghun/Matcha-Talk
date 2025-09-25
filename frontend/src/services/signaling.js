import { camelizeKeys } from '../utils/case'

function parseBody(message) {
  if (!message?.body) {
    return null
  }
  try {
    const raw = JSON.parse(message.body)
    return raw && typeof raw === 'object' ? camelizeKeys(raw) : raw
  } catch (error) {
    console.warn('[signaling] Failed to parse STOMP message body.', error)
    return null
  }
}

export function setupSignalRoutes(
  client,
  { me, onSignal, onError, subscribeDest } = {}
) {
  const destination = subscribeDest || '/user/queue/signals'
  const subscriptions = []

  const signalSubscription = client.subscribe(destination, (msg) => {
    const payload = parseBody(msg)
    if (payload) {
      onSignal?.(payload)
    } else {
      console.warn('[signaling] Received empty signaling payload. Ignored.')
    }
  })
  subscriptions.push(signalSubscription)

  const errorSubscription = client.subscribe('/user/queue/errors', (msg) => {
    const payload = parseBody(msg) || { code: 'UNKNOWN', message: msg.body }
    if (onError) {
      onError(payload)
    } else {
      console.warn('[signaling] Received error payload.', payload)
    }
  })
  subscriptions.push(errorSubscription)

  function sendSignal(signal = {}) {
    const { receiverLoginId, data, ...rest } = signal || {}
    const payload = { senderLoginId: me, ...rest }
    if (receiverLoginId !== undefined) {
      payload.receiverLoginId = receiverLoginId
    }
    if (data !== undefined) {
      payload.data = data
    }
    if (!payload.receiverLoginId) {
      console.error('[signaling] receiverLoginId is required but missing. Signal not sent.')
      return
    }
    client.publish({
      destination: '/app/signal',
      body: JSON.stringify(payload),
    })
  }

  function unsubscribe() {
    for (const subscription of subscriptions) {
      try {
        subscription?.unsubscribe?.()
      } catch (error) {
        console.warn('[signaling] Failed to unsubscribe from destination.', error)
      }
    }
  }

  return { sub: signalSubscription, errorSub: errorSubscription, subscriptions, unsubscribe, sendSignal }
}

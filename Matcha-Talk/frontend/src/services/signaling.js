export function setupSignalHandlers(client, { onSignal } = {}) {
  if (!client) {
    throw new Error('A WebSocket client instance is required to setup signaling routes.')
  }

  const unsubscribe = onSignal ? client.onEvent('signal', onSignal) : () => {}

  function resolveEventName(signal) {
    if (!signal) return null
    if (typeof signal.event === 'string' && signal.event.length > 0) {
      return signal.event
    }

    const normalizedType = typeof signal.type === 'string' ? signal.type.trim().toLowerCase() : ''
    switch (normalizedType) {
      case 'offer':
        return 'offer'
      case 'answer':
        return 'answer'
      case 'icecandidate':
      case 'ice-candidate':
        return 'iceCandidate'
      default:
        return null
    }
  }

  function buildPayload(signal) {
    if (!signal) return null
    if (signal.payload && typeof signal.payload === 'object') {
      return { ...signal.payload }
    }

    const payload = {
      receiverLoginId: signal.receiverLoginId,
      data: signal.data,
      senderLoginId: signal.senderLoginId
    }

    if (signal.metadata && typeof signal.metadata === 'object') {
      payload.metadata = { ...signal.metadata }
    }

    return payload
  }

  return {
    dispose() {
      unsubscribe?.()
    },
    async sendSignal(signal) {
      const eventName = resolveEventName(signal)
      if (!eventName) {
        throw new Error('event is required to send a WebRTC signal.')
      }

      const payload = buildPayload(signal)
      if (!payload?.receiverLoginId) {
        throw new Error('receiverLoginId is required to send a WebRTC signal.')
      }

      if (!payload.senderLoginId && typeof signal?.senderLoginId === 'string') {
        payload.senderLoginId = signal.senderLoginId
      }

      client.send({ event: eventName, payload })
    }
  }
}

import api from './api'

export function setupSignalHandlers(client, { onSignal } = {}) {
  if (!client) {
    throw new Error('A WebSocket client instance is required to setup signaling routes.')
  }

  const unsubscribe = onSignal ? client.onEvent('signal', onSignal) : () => {}

  return {
    dispose() {
      unsubscribe?.()
    },
    async sendSignal(signal) {
      if (!signal || !signal.receiverLoginId) {
        throw new Error('receiverLoginId is required to send a WebRTC signal.')
      }
      await api.post('/webrtc/signals', signal)
    }
  }
}

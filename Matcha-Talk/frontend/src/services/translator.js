import api from './api'

export async function translate({ roomId, messageId, signal } = {}) {
  if (!roomId) {
    throw new Error('roomId is required to translate a message')
  }
  if (!messageId) {
    throw new Error('messageId is required to translate a message')
  }

  const config = signal ? { signal } : undefined
  const response = await api.post(`/rooms/${roomId}/messages/${messageId}/translate`, {}, config)
  return response?.data
}

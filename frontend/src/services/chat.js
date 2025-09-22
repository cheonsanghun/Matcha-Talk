// src/services/chat.js
export function setupChat(client, roomId, { onChat } = {}) {
  if (!client) {
    throw new Error('STOMP 클라이언트가 필요합니다.')
  }
  if (!roomId) {
    throw new Error('roomId가 필요합니다.')
  }

  const subscription = client.subscribe(`/topic/rooms/${roomId}`, (frame) => {
    if (typeof onChat !== 'function') {
      return
    }
    try {
      const payload = JSON.parse(frame.body)
      onChat(payload)
    } catch (error) {
      console.error('채팅 메시지 파싱 실패', error)
    }
  })

  return {
    roomId,
    subscription,
    sendChat(payload = {}) {
      const body = JSON.stringify({ roomId, ...payload })
      client.publish({
        destination: `/app/chat.sendMessage/${roomId}`,
        body,
      })
    },
    unsubscribe() {
      subscription?.unsubscribe?.()
    },
  }
}

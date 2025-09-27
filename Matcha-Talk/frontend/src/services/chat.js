// src/services/chat.js
export function setupChat(client, roomId, { onChat } = {}) {
  if (!client) throw new Error('Realtime client is required')
  if (!roomId) throw new Error('roomId is required')

  const unsubscribe = onChat
    ? client.onEvent('chat', (payload) => {
        if (!payload) return
        const payloadRoomId = payload.roomId ?? payload.room_id
        if (String(payloadRoomId) === String(roomId)) {
          onChat(payload)
        }
      })
    : () => {}

  function sendChat(dto = {}) {
    const payload = {
      roomId: Number(dto.roomId ?? dto.room_id ?? roomId),
      content: dto.content ?? dto.message ?? '',
    }

    const clientMsgId = dto.clientMsgId ?? dto.client_msg_id
    if (clientMsgId) {
      payload.clientMsgId = clientMsgId
    }

    const languageCode = dto.senderLanguageCode ?? dto.languageCode
    if (languageCode) {
      payload.senderLanguageCode = languageCode
    }

    client.publish({
      destination: '/app/chat/send',
      body: payload,
    })
  }

  return {
    dispose: unsubscribe,
    sendChat,
  }
}

// src/services/chat.js
export function setupChat(client, roomId, { onChat }) {
  const subscription = client.subscribe(`/topic/rooms/${roomId}`, (msg) => {
    onChat(JSON.parse(msg.body))
  })

  function sendChat(dto) {
    client.publish({
      destination: `/app/chat.sendMessage/${roomId}`,
      body: JSON.stringify(dto),
    })
  }

  return { sub: subscription, sendChat }
}

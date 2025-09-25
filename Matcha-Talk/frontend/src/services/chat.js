// src/services/chat.js
export function setupChat(client, roomId, { onChat }) {
    const sub = client.subscribe(`/topic/chat/${roomId}`, (msg) => {
        onChat(JSON.parse(msg.body))
    })
    function sendChat(dto) {
        client.publish({
            destination: `/app/chat/${roomId}`,
            body: JSON.stringify(dto) // { message: "...", sender: "...", ... }
        })
    }
    return { sub, sendChat }
}

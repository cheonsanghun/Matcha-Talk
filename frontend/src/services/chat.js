import api from './api'

export function setupChat(client, roomId, { onChat }) {
  const destination = `/topic/rooms/${roomId}`
  const sub = client.subscribe(destination, (msg) => {
    onChat(JSON.parse(msg.body))
  })

  function sendChat(dto) {
    client.publish({
      destination: `/app/chat.sendMessage/${roomId}`,
      body: JSON.stringify(dto)
    })
  }

  return { sub, sendChat }
}

export async function fetchRoomMessages(roomId, params = {}) {
  const { data } = await api.get(`/rooms/${roomId}/messages`, {
    params
  })
  return data || []
}

export async function uploadRoomFile(roomId, file) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await api.post(`/rooms/${roomId}/files`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return data
}

export async function openPrivateRoom(targetLoginId) {
  const { data } = await api.post('/rooms/private', {
    targetLoginId
  })
  return data
}

export async function fetchMyRooms() {
  const { data } = await api.get('/rooms')
  return data || []
}

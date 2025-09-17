import api from './api'

export function uploadChatFile(roomId, file) {
  const formData = new FormData()
  formData.append('file', file)

  return api.post(`/rooms/${roomId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

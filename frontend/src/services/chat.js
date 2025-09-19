import { chatApi as httpChatApi } from './api'
import { subscribe, send } from './ws'

export const subscribeRoom = async (roomId, callback) => {
  return subscribe(`/topic/rooms/${roomId}`, callback)
}

export const sendMessage = async (roomId, content) => {
  const payload = typeof content === 'string' ? { content } : content
  await send(`/app/chat.sendMessage/${roomId}`, { ...payload, roomId })
}

export const chatApi = {
  async listRooms() {
    try {
      const rooms = await httpChatApi.fetchRooms()
      return Array.isArray(rooms) ? rooms : []
    } catch (error) {
      return []
    }
  },
  async loadMessages(roomId) {
    if (!roomId) return []
    try {
      const messages = await httpChatApi.fetchMessages(roomId)
      return Array.isArray(messages) ? messages : []
    } catch (error) {
      return []
    }
  },
  async createGroupRoom() {
    return httpChatApi.createGroupRoom()
  },
}

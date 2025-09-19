import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { chatApi, sendMessage as publishMessage, subscribeRoom } from '../services/chat'
import { useAuthStore } from './auth'

const defaultRooms = () => [
  { id: 1, name: 'Matcha 라운지', type: 'GROUP', last: '환영합니다!', participants: 42 },
  { id: 2, name: '개발자 모임', type: 'GROUP', last: '새로운 스택 공유해요.', participants: 18 },
  { id: 101, name: '오늘의 인연', type: 'DIRECT', last: '안녕하세요!', participants: 2 },
]

const normalizeRoom = (room) => ({
  id: room.roomId ?? room.id,
  name: room.name ?? `채팅방 #${room.roomId ?? room.id}`,
  type: room.type ?? (room.isGroup ? 'GROUP' : 'DIRECT'),
  last: room.lastMessage ?? room.last ?? '',
  participants: room.participants ?? room.memberCount ?? 2,
})

const normalizeMessage = (message, currentUser) => ({
  id: message.id ?? `${Date.now()}-${Math.random()}`,
  roomId: message.roomId,
  sender: message.senderNickName ?? message.sender ?? '알 수 없음',
  content: message.content ?? message.text ?? '',
  translatedContent: message.translatedContent ?? null,
  sentAt: message.sentAt ?? new Date().toISOString(),
  me:
    !!currentUser &&
    (message.senderLoginId === currentUser.loginId || message.senderNickName === currentUser.nickname),
})

export const useChatStore = defineStore('chat', () => {
  const rooms = ref([])
  const currentRoomId = ref(null)
  const messages = ref([])
  const loading = ref(false)
  const sending = ref(false)
  const error = ref(null)

  let unsubscribe = null

  const authStore = useAuthStore()

  const currentRoom = computed(() => rooms.value.find((room) => room.id === currentRoomId.value) ?? null)
  const isGroup = computed(() => currentRoom.value?.type === 'GROUP')

  const init = async () => {
    if (!rooms.value.length) {
      await fetchRooms()
    }
    if (!currentRoomId.value && rooms.value.length) {
      currentRoomId.value = rooms.value[0].id
      await selectRoom(currentRoomId.value)
    }
  }

  const fetchRooms = async () => {
    loading.value = true
    error.value = null
    try {
      const fetched = await chatApi.listRooms()
      if (Array.isArray(fetched) && fetched.length) {
        rooms.value = fetched.map(normalizeRoom)
      } else {
        rooms.value = defaultRooms()
      }
    } catch (err) {
      error.value = err?.message ?? '채팅방 목록을 불러오지 못했습니다.'
      rooms.value = defaultRooms()
    } finally {
      loading.value = false
    }
  }

  const fetchMessages = async (roomId) => {
    if (!roomId) {
      messages.value = []
      return
    }
    loading.value = true
    error.value = null
    try {
      const fetched = await chatApi.loadMessages(roomId)
      if (Array.isArray(fetched)) {
        messages.value = fetched.map((msg) => normalizeMessage(msg, authStore.user))
      } else {
        messages.value = []
      }
    } catch (err) {
      error.value = err?.message ?? '메시지를 불러오지 못했습니다.'
      messages.value = []
    } finally {
      loading.value = false
    }
  }

  const subscribeToRoom = async (roomId) => {
    if (unsubscribe) {
      unsubscribe()
      unsubscribe = null
    }
    if (!roomId) return
    unsubscribe = await subscribeRoom(roomId, (payload) => {
      const normalized = normalizeMessage(payload, authStore.user)
      messages.value.push(normalized)
    })
  }

  const selectRoom = async (roomId) => {
    currentRoomId.value = roomId
    await fetchMessages(roomId)
    await subscribeToRoom(roomId)
  }

  const sendMessage = async (content) => {
    if (!currentRoomId.value || !content?.trim()) {
      return
    }
    sending.value = true
    error.value = null
    try {
      await publishMessage(currentRoomId.value, { content })
      messages.value.push(
        normalizeMessage(
          {
            roomId: currentRoomId.value,
            senderNickName: authStore.user?.nickname ?? authStore.user?.loginId ?? '나',
            senderLoginId: authStore.user?.loginId,
            content,
            sentAt: new Date().toISOString(),
          },
          authStore.user,
        ),
      )
    } catch (err) {
      error.value = err?.message ?? '메시지를 전송하지 못했습니다.'
    } finally {
      sending.value = false
    }
  }

  const cleanup = () => {
    if (unsubscribe) {
      unsubscribe()
      unsubscribe = null
    }
  }

  return {
    rooms,
    currentRoomId,
    currentRoom,
    messages,
    loading,
    sending,
    error,
    isGroup,
    init,
    fetchRooms,
    selectRoom,
    sendMessage,
    cleanup,
  }
})

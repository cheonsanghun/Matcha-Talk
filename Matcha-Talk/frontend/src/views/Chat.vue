<template>
  <v-container fluid class="chat-page mt-4">
    <v-row no-gutters class="h-100">
      <!-- Sidebar -->
      <v-col cols="12" md="3" class="chat-sidebar d-flex flex-column">
        <div class="sidebar-header d-flex align-center px-4 py-3">
          <div class="text-h6 font-weight-medium">채팅</div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-message-plus-outline</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-account-multiple-plus</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-cog-outline</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-dots-vertical</v-icon></v-btn>
        </div>
        <div class="px-4 pb-2">
          <v-text-field
            v-model="query"
            placeholder="채팅방 검색"
            prepend-inner-icon="mdi-magnify"
            variant="solo"
            density="comfortable"
            hide-details
          />
        </div>
        <v-tabs v-model="tab" density="comfortable" class="px-4">
          <v-tab value="direct">1:1 채팅</v-tab>
          <v-tab value="group">그룹 채팅</v-tab>
        </v-tabs>
        <v-divider />
        <div class="flex-grow-1 overflow-y-auto">
          <v-list v-if="tab === 'direct'">
            <template v-if="filteredChats.length">
              <v-list-item
                v-for="item in filteredChats"
                :key="item.id"
                :active="current.id === item.id"
                @click="openChat(item)"
                lines="two"
              >
                <template #prepend>
                  <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
                </template>
                <v-list-item-title>{{ item.name }}</v-list-item-title>
                <v-list-item-subtitle>{{ item.last }}</v-list-item-subtitle>
              </v-list-item>
            </template>
            <v-list-item v-else>
              <v-list-item-title class="text-caption text-grey">
                참여 중인 1:1 채팅이 없습니다.
              </v-list-item-title>
            </v-list-item>
          </v-list>
          <v-list v-else>
            <template v-if="filteredGroups.length">
              <v-list-item
                v-for="item in filteredGroups"
                :key="item.id"
                :active="current.id === item.id"
                @click="openChat(item)"
                lines="two"
              >
                <template #prepend>
                  <v-avatar size="40"><v-icon color="primary">mdi-account-group</v-icon></v-avatar>
                </template>
                <v-list-item-title>{{ item.name }}</v-list-item-title>
                <v-list-item-subtitle>{{ item.participants.join(', ') }}</v-list-item-subtitle>
              </v-list-item>
            </template>
            <v-list-item v-else>
              <v-list-item-title class="text-caption text-grey">
                참여 중인 그룹 채팅이 없습니다.
              </v-list-item-title>
            </v-list-item>
          </v-list>
        </div>
      </v-col>

      <!-- Conversation -->
      <v-col cols="12" md="9" class="chat-main d-flex flex-column">
        <div class="chat-header d-flex align-center pa-4">
          <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
          <div class="ml-3">
            <div class="text-subtitle-1 font-weight-medium">{{ current.name || '채팅방을 선택하세요' }}</div>
            <div class="text-caption text-grey" v-if="isGroup">{{ groupParticipants }}</div>
          </div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-magnify</v-icon></v-btn>
          <v-btn v-if="!isGroup" icon variant="text"><v-icon>mdi-phone</v-icon></v-btn>
          <v-btn v-if="isGroup" icon variant="text" @click="inviteParticipant"><v-icon>mdi-account-plus</v-icon></v-btn>
          <v-btn v-if="isGroup" icon variant="text" @click="startVideoCall"><v-icon>mdi-video</v-icon></v-btn>
          <v-btn v-else icon variant="text"><v-icon>mdi-video</v-icon></v-btn>
        </div>
        <v-divider />
        <div class="chat-messages flex-grow-1 pa-4 overflow-y-auto" ref="chatMessagesContainer">
          <div
            v-if="isLoadingRooms && !current.id"
            class="d-flex align-center justify-center h-100 text-caption text-grey"
          >
            채팅방 정보를 불러오는 중입니다...
          </div>
          <div
            v-else-if="!current.id"
            class="d-flex align-center justify-center h-100 text-caption text-grey"
          >
            좌측 목록에서 채팅방을 선택하세요.
          </div>
          <template v-else>
            <div
              v-for="(m, i) in messages"
              :key="m.id || i"
              class="d-flex mb-4"
              :class="{ 'justify-end': m.me }"
            >
              <template v-if="!m.me">
                <v-avatar size="32" class="mr-2"><v-icon color="primary">mdi-account</v-icon></v-avatar>
                <div class="message-wrapper">
                  <div v-if="isGroup" class="text-caption font-weight-medium mb-1">{{ m.sender }}</div>
                  <div class="message-bubble bg-grey-lighten-4 text-body-2">{{ m.text }}</div>
                  <div v-if="m.translation" class="text-caption text-grey mt-1">
                    {{ m.translation }}
                    <v-icon size="16" class="ms-1 cursor-pointer" @click="saveWord(m)">mdi-content-save</v-icon>
                  </div>
                  <div class="message-tools">
                    <v-btn
                      icon
                      variant="text"
                      density="compact"
                      @click="translateMessage(m)"
                      :loading="m.translating"
                    >
                      <v-icon size="18">mdi-translate</v-icon>
                    </v-btn>
                    <span class="text-caption text-grey">{{ m.time }}</span>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="message-wrapper text-right ml-auto">
                  <div class="message-bubble bg-primary text-white text-body-2">{{ m.text }}</div>
                  <div v-if="m.translation" class="text-caption text-grey-lighten-2 mt-1">
                    {{ m.translation }}
                    <v-icon size="16" class="ms-1 cursor-pointer" @click="saveWord(m)">mdi-content-save</v-icon>
                  </div>
                  <div class="message-tools justify-end">
                    <v-btn
                      icon
                      variant="text"
                      density="compact"
                      color="white"
                      @click="translateMessage(m)"
                      :loading="m.translating"
                    >
                      <v-icon size="18">mdi-translate</v-icon>
                    </v-btn>
                    <span class="text-caption text-grey-lighten-1">{{ m.time }}</span>
                  </div>
                </div>
              </template>
            </div>
          </template>
        </div>
        <div class="chat-input d-flex align-center pa-4 ga-2">
          <v-btn icon variant="outlined" color="success"><v-icon>mdi-plus</v-icon></v-btn>
          <v-text-field
            v-model="draft"
            variant="outlined"
            density="comfortable"
            hide-details
            placeholder="메시지를 입력하세요..."
            class="flex-grow-1"
            @keydown.enter.prevent="send"
            :disabled="!current.id"
          />
          <v-btn icon variant="text"><v-icon>mdi-emoticon-outline</v-icon></v-btn>
          <v-btn icon color="success" @click="send" :disabled="!current.id || !draft.trim()">
            <v-icon>mdi-send</v-icon>
          </v-btn>
        </div>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { createRealtimeClient } from '../services/ws'
import api from '../services/api'
import { translate } from '../services/translator'
import { useVocabularyStore } from '../stores/vocabulary'
import { resolveClientIdentity } from '../utils/identity'

const query = ref('')
const tab = ref('direct')
const chats = ref([])
const groups = ref([])
const conversations = ref({})
const current = ref({})

const chatMessagesContainer = ref(null)
const isLoadingRooms = ref(false)

const auth = useAuthStore()
const vocabularyStore = useVocabularyStore()
const route = useRoute()
const router = useRouter()

let realtimeClient = null
let reconnectTimer = null
let manualDisconnect = false
let isConnecting = false
let reconnectAttempts = 0
const maxReconnectAttempts = 3
const teardownHandlers = []

onMounted(async () => {
  await loadRooms()
  ensureActiveRoomFromRoute()
  connectRealtime()
})

watch(() => route.query.roomId, () => {
  ensureActiveRoomFromRoute()
})

watch(
  () => auth.user?.userPid,
  async (userPid, prev) => {
    if (userPid && userPid !== prev) {
      await loadRooms()
      ensureActiveRoomFromRoute()
    }
  }
)

watch(
  () => current.value?.id,
  () => {
    scrollToBottom()
  }
)

onUnmounted(() => {
  manualDisconnect = true

  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  teardownHandlers.forEach((fn) => {
    try { fn?.() } catch (error) { console.warn('Failed to remove handler', error) }
  })
  teardownHandlers.length = 0

  if (realtimeClient) {
    try { realtimeClient.disconnect() } catch (error) {
      console.warn('Failed to disconnect WebSocket cleanly', error)
    }
    realtimeClient = null
  }
})

async function loadRooms() {
  try {
    isLoadingRooms.value = true
    const { data } = await api.get('/rooms/my')
    const meNickname = auth.user?.nickName || auth.user?.nickname
    const directRooms = []
    const groupRooms = []

    data.forEach((room) => {
      const entry = normalizeRoomListEntry(room, meNickname)
      ensureConversation(entry.id)
      if (entry.type === 'GROUP') {
        groupRooms.push(entry)
      } else {
        directRooms.push(entry)
      }
    })

    chats.value = directRooms
    groups.value = groupRooms

    if (current.value?.id) {
      selectRoomById(current.value.id, { skipRouteUpdate: true })
    }
  } catch (error) {
    console.error('[chat] Failed to load rooms', error)
  } finally {
    isLoadingRooms.value = false
  }
}

function normalizeRoomListEntry(room, meNickname) {
  const participants = room.memberNicknames ?? []
  const type = (room.roomType || 'PRIVATE').toString().toUpperCase()
  const others = meNickname ? participants.filter((nick) => nick !== meNickname) : participants

  let displayName
  if (type === 'GROUP') {
    displayName = participants.join(', ')
  } else {
    displayName = others[0] || participants[0] || `대화방 #${room.roomId}`
  }

  return {
    id: room.roomId,
    name: displayName,
    last: '',
    participants,
    type
  }
}

function normalizeRoomDetail(detail) {
  const participants = (detail.participants || []).map((participant) => participant.nickname)
  const type = (detail.roomType || 'PRIVATE').toString().toUpperCase()
  const meNickname = auth.user?.nickName || auth.user?.nickname
  const others = meNickname ? participants.filter((nick) => nick !== meNickname) : participants

  let displayName
  if (type === 'GROUP') {
    displayName = participants.join(', ')
  } else {
    displayName = others[0] || participants[0] || `대화방 #${detail.roomId}`
  }

  return {
    id: detail.roomId,
    name: displayName,
    last: '',
    participants,
    type
  }
}

function addOrUpdateRoom(entry) {
  const list = entry.type === 'GROUP' ? groups.value : chats.value
  const index = list.findIndex((room) => room.id === entry.id)
  if (index >= 0) {
    list[index] = { ...list[index], ...entry }
    return list[index]
  }
  list.push(entry)
  return entry
}

function findRoomById(roomId) {
  return chats.value.find((room) => room.id === roomId) || groups.value.find((room) => room.id === roomId)
}

function selectRoomById(roomId, options = {}) {
  const room = findRoomById(roomId)
  if (room) {
    openChat(room, options)
    return true
  }
  return false
}

function ensureActiveRoomFromRoute() {
  const rawId = route.query.roomId
  const parsedId = rawId ? Number(rawId) : NaN

  if (!Number.isNaN(parsedId) && parsedId) {
    if (selectRoomById(parsedId, { skipRouteUpdate: true })) {
      return
    }
  }

  if (!current.value?.id) {
    const fallback = chats.value[0] || groups.value[0]
    if (fallback) {
      openChat(fallback, { skipRouteUpdate: true })
    }
  }
}

const filteredChats = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return chats.value
  return chats.value.filter((room) => {
    return (
      room.name?.toLowerCase().includes(keyword) ||
      room.last?.toLowerCase().includes(keyword)
    )
  })
})

const filteredGroups = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return groups.value
  return groups.value.filter((room) => {
    const participants = room.participants.join(', ').toLowerCase()
    return room.name?.toLowerCase().includes(keyword) || participants.includes(keyword)
  })
})

const isGroup = computed(() => current.value?.type === 'GROUP')
const groupParticipants = computed(() => (current.value?.participants || []).join(', '))
const messages = computed(() => conversations.value[current.value?.id] ?? [])

function openChat(item, options = {}) {
  if (!item) return

  current.value = item
  tab.value = item.type === 'GROUP' ? 'group' : 'direct'
  ensureConversation(item.id)

  if (!options.skipRouteUpdate) {
    const newQuery = { ...route.query, roomId: String(item.id) }
    router.replace({ name: 'chat', query: newQuery })
  }

  scrollToBottom()
}

function ensureConversation(roomId) {
  if (!conversations.value[roomId]) {
    conversations.value[roomId] = []
  }
  return conversations.value[roomId]
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatMessagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

async function ensureRoomExists(roomId, fallbackName) {
  let room = findRoomById(roomId)
  if (room) {
    return room
  }

  try {
    const { data } = await api.get(`/rooms/${roomId}`)
    room = addOrUpdateRoom(normalizeRoomDetail(data))
  } catch (error) {
    console.warn(`[chat] Failed to fetch room ${roomId}, using fallback`, error)
    room = addOrUpdateRoom({
      id: roomId,
      name: fallbackName || `대화방 #${roomId}`,
      last: '',
      participants: fallbackName ? [fallbackName] : [],
      type: 'PRIVATE'
    })
  }

  ensureConversation(room.id)
  return room
}

async function handleIncomingMessage(payload) {
  if (!payload) return

  const roomKey = payload.roomId ?? payload.room_id
  if (!roomKey) return

  const content = payload.content ?? ''
  const senderNickname = payload.senderNickName || payload.senderNickname || '상대방'
  const messagesForRoom = ensureConversation(roomKey)
  const myNickname = auth.user?.nickName || auth.user?.nickname

  const message = {
    id: `${roomKey}-${Date.now()}-${messagesForRoom.length}`,
    text: content,
    time: formatTime(payload.sentAt),
    sender: senderNickname,
    me: myNickname ? senderNickname === myNickname : false,
    translation: null,
    translating: false,
    sentAt: payload.sentAt ?? null
  }

  messagesForRoom.push(message)

  const roomEntry = await ensureRoomExists(roomKey, senderNickname)
  roomEntry.last = content

  if (current.value?.id === roomKey) {
    scrollToBottom()
  }
}

async function connectRealtime() {
  if (isConnecting || manualDisconnect) return

  const loginId = resolveClientIdentity(auth)

  if (!realtimeClient) {
    realtimeClient = createRealtimeClient({ queryParams: { loginId } })
    teardownHandlers.push(
      realtimeClient.onOpen(() => {
        isConnecting = false
        reconnectAttempts = 0
      }),
      realtimeClient.onClose((event) => {
        console.log('[chat] WebSocket closed', event)
        isConnecting = false
        if (manualDisconnect) return
        scheduleReconnect()
      }),
      realtimeClient.onError((event) => {
        console.error('[chat] WebSocket error', event)
      }),
      realtimeClient.onEvent('chat', (payload) => {
        handleIncomingMessage(payload)
      }),
      realtimeClient.onEvent('match-result', async (payload) => {
        const roomId = payload?.roomId ?? payload?.room_id
        const partner = payload?.partnerNickName || payload?.partnerNickname
        if (roomId) {
          await ensureRoomExists(roomId, partner)
        }
        await loadRooms()
        if (roomId) {
          selectRoomById(Number(roomId))
        }
      })
    )
  } else {
    realtimeClient.setQueryParams({ loginId })
  }

  isConnecting = true
  try {
    await realtimeClient.connect()
  } catch (error) {
    console.error('[chat] Failed to connect WebSocket', error)
    isConnecting = false
  }
}

function scheduleReconnect() {
  if (manualDisconnect) return
  if (reconnectAttempts >= maxReconnectAttempts) {
    console.error('[chat] Max reconnect attempts reached')
    return
  }
  if (reconnectTimer) return

  reconnectAttempts += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectRealtime()
  }, 2000)
}

function formatTime(isoString) {
  if (!isoString) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

async function translateMessage(message) {
  if (!message || message.translating || message.translation) return

  message.translating = true
  try {
    const targetLang = auth.user?.languageCode || 'en'
    message.translation = await translate(message.text, targetLang)
  } catch (error) {
    console.error('문장을 번역하지 못했습니다.', error)
  } finally {
    message.translating = false
  }
}

function saveWord(message) {
  if (!message?.translation) return
  vocabularyStore.addWord(message.text, message.translation)
}

async function send() {
  const message = draft.value.trim()
  if (!message) return

  const roomKey = current.value?.id
  if (!roomKey) {
    alert('채팅방이 선택되지 않았습니다.')
    return
  }

  if (!realtimeClient?.isConnected()) {
    await connectRealtime()
    if (!realtimeClient?.isConnected()) {
      console.warn('WebSocket not connected. Message was not sent.')
      return
    }
  }

  try {
    realtimeClient.send({
      type: 'CHAT',
      roomId: Number(roomKey),
      content: message
    })
    draft.value = ''
    const roomEntry = findRoomById(roomKey)
    if (roomEntry) {
      roomEntry.last = message
    }
  } catch (error) {
    console.error('Failed to send chat message', error)
  }
}

function inviteParticipant() {
  if (!isGroup.value || !current.value?.id) return

  const group = groups.value.find((room) => room.id === current.value.id)
  if (!group) return
  if (group.participants.length >= 4) {
    alert('최대 4명까지 초대할 수 있습니다.')
    return
  }

  const name = prompt('초대할 사용자의 이름을 입력하세요:')
  if (name) {
    group.participants.push(name)
  }
}

function startVideoCall() {
  alert('영상 통화 기능은 준비 중입니다.')
}

watch(messages, () => scrollToBottom())
</script>

<style scoped>
.chat-page {
  height: calc(100vh - var(--v-layout-top));
}

.chat-sidebar {
  background: #fff;
  border-right: 2px solid #000000;
  height: 100%;
}

.chat-main {
  background: #fff;
  border-left: 2px solid #ffb6c1;
  height: 100%;
}

.chat-messages {
  background: #fff;
}

.chat-input {
  border-top: 1px solid #eee;
}

.message-wrapper {
  max-width: min(420px, 80%);
  display: flex;
  flex-direction: column;
}

.message-bubble {
  border-radius: 16px;
  padding: 12px;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.text-right .message-tools {
  justify-content: flex-end;
}

.cursor-pointer {
  cursor: pointer;
}

.ml-auto {
  margin-left: auto;
}
</style>

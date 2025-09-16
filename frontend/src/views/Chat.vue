<template>
  <v-container fluid class="chat-page mt-4">
    <v-row no-gutters class="h-100">
      <!-- Sidebar -->
      <v-col cols="12" md="3" class="chat-sidebar d-flex flex-column">
        <div class="sidebar-header d-flex align-center px-4 py-3">
          <div class="text-h6 font-weight-medium">채팅</div>
          <v-spacer />
          <v-btn icon variant="text" @click="createGroupRoom"><v-icon>mdi-account-multiple-plus</v-icon></v-btn>
          <v-btn icon variant="text" @click="refreshFriends"><v-icon>mdi-refresh</v-icon></v-btn>
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
            <v-list-item
              v-for="friend in filteredFriends"
              :key="friend.loginId"
              @click="openDirectChat(friend)"
              :active="current?.type === 'direct' && current?.id === friend.loginId"
              lines="two"
            >
              <template #prepend>
                <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
              </template>
              <v-list-item-title>{{ friend.nickName }}</v-list-item-title>
              <v-list-item-subtitle>{{ getLastSnippet(getDirectRoomId(friend)) }}</v-list-item-subtitle>
            </v-list-item>
            <v-list-item v-if="!filteredFriends.length">
              <v-list-item-title>팔로우한 사용자가 없습니다.</v-list-item-title>
            </v-list-item>
          </v-list>
          <v-list v-else>
            <v-list-item
              v-for="group in filteredGroups"
              :key="group.id"
              @click="openGroupChat(group)"
              :active="current?.type === 'group' && current?.id === group.id"
              lines="two"
            >
              <template #prepend>
                <v-avatar size="40"><v-icon color="primary">mdi-account-group</v-icon></v-avatar>
              </template>
              <v-list-item-title>{{ group.name }}</v-list-item-title>
              <v-list-item-subtitle>{{ getLastSnippet(group.roomId) }}</v-list-item-subtitle>
            </v-list-item>
            <v-list-item v-if="!filteredGroups.length">
              <v-list-item-title>생성된 그룹이 없습니다.</v-list-item-title>
            </v-list-item>
          </v-list>
        </div>
      </v-col>

      <!-- Conversation -->
      <v-col cols="12" md="9" class="chat-main d-flex flex-column">
        <div v-if="!current" class="h-100 d-flex align-center justify-center text-medium-emphasis">
          대화를 시작할 상대를 선택하세요.
        </div>

        <template v-else>
          <div class="chat-header d-flex align-center pa-4">
            <v-avatar size="40"><v-icon color="primary">{{ current.type === 'group' ? 'mdi-account-group' : 'mdi-account' }}</v-icon></v-avatar>
            <div class="ml-3">
              <div class="text-subtitle-1 font-weight-medium">{{ current.name }}</div>
              <div class="text-caption text-grey" v-if="current.type === 'group'">
                {{ current.participants.join(', ') }}
              </div>
              <div class="text-caption text-grey" v-else>온라인</div>
            </div>
            <v-spacer />
            <v-select
              v-model="targetLang"
              :items="availableLanguages"
              hide-details
              density="compact"
              variant="outlined"
              class="lang-select"
            />
            <v-btn
              icon
              variant="text"
              :color="translateEnabled ? 'pink' : undefined"
              @click="toggleTranslate"
            >
              <v-icon>{{ translateEnabled ? 'mdi-translate' : 'mdi-translate-off' }}</v-icon>
            </v-btn>
            <v-btn v-if="current.type === 'group'" icon variant="text" @click="inviteParticipant">
              <v-icon>mdi-account-plus</v-icon>
            </v-btn>
            <v-btn icon variant="text" @click="startVideoCall">
              <v-icon>mdi-video</v-icon>
            </v-btn>
          </div>
          <v-divider />
          <div class="chat-messages flex-grow-1 pa-4 overflow-y-auto" ref="messageContainer">
            <v-progress-linear v-if="chatLoading" indeterminate color="pink" class="mb-2" />
            <div
              v-for="(msg, i) in activeMessages"
              :key="msg.id ?? i"
              class="d-flex mb-4"
              :class="{ 'justify-end': msg.senderLoginId === myLoginId }"
            >
              <template v-if="msg.senderLoginId !== myLoginId">
                <v-avatar size="32" class="mr-2"><v-icon color="primary">mdi-account</v-icon></v-avatar>
                <div>
                  <div v-if="current.type === 'group'" class="text-caption font-weight-medium mb-1">{{ msg.senderNickName }}</div>
                  <div v-if="msg.contentType === 'TEXT'" class="pa-3 bg-grey-lighten-4 rounded-xl">
                    {{ msg.content }}
                    <div v-if="msg.translatedText" class="text-caption text-grey mt-1 d-flex align-center">
                      {{ msg.translatedText }}
                      <v-btn
                        size="x-small"
                        variant="text"
                        color="pink"
                        :loading="msg.saving"
                        class="ml-2"
                        @click="storeWord(msg)"
                      >저장</v-btn>
                    </div>
                  </div>
                  <div v-else class="pa-3 bg-grey-lighten-4 rounded-xl">
                    <template v-if="msg.contentType === 'IMAGE'">
                      <img :src="msg.fileUrl" :alt="msg.fileName" class="max-w-220" />
                    </template>
                    <template v-else>
                      <a :href="msg.fileUrl" target="_blank" rel="noopener">{{ msg.fileName }}</a>
                    </template>
                  </div>
                  <div class="text-caption text-grey mt-1">{{ formatTime(msg.sentAt) }}</div>
                </div>
              </template>
              <template v-else>
                <div>
                  <div v-if="msg.contentType === 'TEXT'" class="pa-3 bg-primary text-white rounded-xl">{{ msg.content }}</div>
                  <div v-else class="pa-3 bg-primary text-white rounded-xl">
                    <template v-if="msg.contentType === 'IMAGE'">
                      <img :src="msg.fileUrl" :alt="msg.fileName" class="max-w-220" />
                    </template>
                    <template v-else>
                      <a :href="msg.fileUrl" target="_blank" rel="noopener" class="text-white">{{ msg.fileName }}</a>
                    </template>
                  </div>
                  <div class="text-caption text-grey mt-1 text-right">{{ formatTime(msg.sentAt) }}</div>
                </div>
              </template>
            </div>
          </div>
          <div class="chat-input d-flex align-center pa-4 ga-2">
            <input ref="fileInput" type="file" class="d-none" accept="image/*,application/*" @change="uploadFile" />
            <v-btn icon variant="outlined" color="pink" :loading="uploading" @click="triggerFileSelect"><v-icon>mdi-paperclip</v-icon></v-btn>
            <v-text-field
              v-model="chatInput"
              variant="outlined"
              density="comfortable"
              hide-details
              placeholder="메시지를 입력하세요..."
              class="flex-grow-1"
              @keydown.enter.prevent="send"
            />
            <v-btn icon color="pink" :loading="sending" :disabled="!chatInput.trim()" @click="send"><v-icon>mdi-send</v-icon></v-btn>
          </div>
        </template>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useFriendsStore } from '../stores/friends'
import { createStompClient } from '../services/ws'
import { translate, saveVocabulary } from '../services/translator'
import api from '../services/api'

const query = ref('')
const tab = ref('direct')
const friendsStore = useFriendsStore()
const auth = useAuthStore()
const router = useRouter()

const groups = ref([])
const current = ref(null)
const conversations = reactive({})
const directRooms = reactive({})
const chatInput = ref('')
const translateEnabled = ref(false)
const targetLang = ref('en')
const availableLanguages = [
  { title: '영어', value: 'en' },
  { title: '한국어', value: 'ko' },
  { title: '일본어', value: 'ja' },
  { title: '중국어', value: 'zh-CN' },
  { title: '스페인어', value: 'es' },
]

const uploading = ref(false)
const sending = ref(false)
const chatLoading = ref(false)
const messageContainer = ref(null)
const fileInput = ref(null)

const chatClient = ref(null)
const isConnected = ref(false)
const connectionResolvers = []
const subscriptions = reactive({})

const myLoginId = computed(() => auth.user?.loginId || '')

const friends = computed(() => friendsStore.list)

const filteredFriends = computed(() =>
  friends.value.filter((friend) =>
    friend.nickName.toLowerCase().includes(query.value.toLowerCase()) ||
    friend.loginId.toLowerCase().includes(query.value.toLowerCase())
  )
)

const filteredGroups = computed(() =>
  groups.value.filter((group) =>
    group.name.toLowerCase().includes(query.value.toLowerCase())
  )
)

const activeMessages = computed(() => {
  if (!current.value) return []
  const conv = conversations[current.value.roomId]
  return conv ? conv.messages : []
})

function ensureConversation(roomId) {
  if (!conversations[roomId]) {
    conversations[roomId] = { messages: [], loaded: false }
  }
  return conversations[roomId]
}

function normalizeMessage(message) {
  return {
    id: message.messageId ?? `${Date.now()}-${Math.random()}`,
    roomId: message.roomId,
    senderLoginId: message.senderLoginId || '',
    senderNickName: message.senderNickName || '시스템',
    contentType: message.contentType || 'TEXT',
    content: message.content || '',
    fileUrl: message.fileUrl || '',
    fileName: message.fileName || '',
    mimeType: message.mimeType || '',
    sizeBytes: message.sizeBytes || 0,
    sentAt: message.sentAt || new Date().toISOString(),
    translatedText: message.translatedText || null,
    saving: false,
  }
}

function waitForConnection() {
  if (isConnected.value) return Promise.resolve()
  return new Promise((resolve) => {
    connectionResolvers.push(resolve)
  })
}

function connectStomp() {
  chatClient.value = createStompClient(auth.token)
  chatClient.value.onConnect = () => {
    isConnected.value = true
    while (connectionResolvers.length) {
      const resolve = connectionResolvers.shift()
      resolve?.()
    }
  }
  chatClient.value.onStompError = (frame) => {
    console.error('STOMP error', frame.headers, frame.body)
  }
  chatClient.value.onWebSocketError = (event) => {
    console.error('WebSocket error', event)
  }
  chatClient.value.activate()
}

async function subscribeRoom(roomId) {
  await waitForConnection()
  if (subscriptions[roomId]) return
  subscriptions[roomId] = chatClient.value.subscribe(`/topic/rooms/${roomId}`, (frame) => {
    try {
      const payload = JSON.parse(frame.body)
      handleIncomingMessage(roomId, payload)
    } catch (error) {
      console.error('Failed to process chat frame', error)
    }
  })
}

async function ensureTranslated(message) {
  if (!translateEnabled.value) return
  if (message.senderLoginId === myLoginId.value) return
  if (message.contentType !== 'TEXT' || !message.content || message.translatedText) return
  try {
    const { translatedText } = await translate(message.content, { targetLang: targetLang.value })
    message.translatedText = translatedText
  } catch (error) {
    console.error('번역 실패', error)
  }
}

async function handleIncomingMessage(roomId, payload) {
  const conv = ensureConversation(roomId)
  const message = normalizeMessage(payload)
  conv.messages.push(message)
  if (current.value?.roomId === roomId) {
    await ensureTranslated(message)
    scrollToBottom()
  }
}

async function loadHistory(roomId) {
  const conv = ensureConversation(roomId)
  chatLoading.value = true
  try {
    const { data } = await api.get(`/rooms/${roomId}/messages`, { params: { page: 0, size: 50 } })
    conv.messages = (data.messages || []).map(normalizeMessage)
    conv.loaded = true
    if (translateEnabled.value) {
      for (const message of conv.messages) {
        await ensureTranslated(message)
      }
    }
    scrollToBottom()
  } catch (error) {
    console.error('Failed to load history', error)
  } finally {
    chatLoading.value = false
  }
}

async function openDirectChat(friend) {
  try {
    await waitForConnection()
    const { data } = await api.post('/rooms/private', { targetLoginId: friend.loginId })
    const roomId = data.roomId
    directRooms[friend.loginId] = roomId
    current.value = {
      type: 'direct',
      id: friend.loginId,
      name: friend.nickName,
      roomId,
      loginId: friend.loginId,
      participants: [friend.nickName],
    }
    await subscribeRoom(roomId)
    await loadHistory(roomId)
  } catch (error) {
    console.error('Failed to open direct chat', error)
  }
}

async function openGroupChat(group) {
  try {
    await waitForConnection()
    current.value = group
    await subscribeRoom(group.roomId)
    await loadHistory(group.roomId)
  } catch (error) {
    console.error('Failed to open group chat', error)
  }
}

async function createGroupRoom() {
  try {
    const { data } = await api.post('/rooms')
    const roomId = data.roomId
    const name = `그룹 ${groups.value.length + 1}`
    const group = {
      id: `group-${roomId}`,
      name,
      roomId,
      type: 'group',
      participants: [auth.user?.nickName || '나'],
    }
    groups.value.push(group)
    tab.value = 'group'
    await openGroupChat(group)
  } catch (error) {
    console.error('그룹 생성 실패', error)
    alert('그룹 채팅방 생성에 실패했습니다.')
  }
}

function getDirectRoomId(friend) {
  return directRooms[friend.loginId]
}

function getLastSnippet(roomId) {
  if (!roomId) return ''
  const conv = conversations[roomId]
  if (!conv || !conv.messages.length) return ''
  const last = conv.messages[conv.messages.length - 1]
  if (last.contentType === 'TEXT') return last.content
  return last.fileName || '파일 공유'
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

async function send() {
  if (!chatClient.value?.connected || !current.value?.roomId || !chatInput.value.trim()) return
  sending.value = true
  try {
    chatClient.value.publish({
      destination: `/app/chat.sendMessage/${current.value.roomId}`,
      body: JSON.stringify({
        roomId: current.value.roomId,
        content: chatInput.value.trim(),
        contentType: 'TEXT',
      }),
    })
    chatInput.value = ''
  } catch (error) {
    console.error('메시지 전송 실패', error)
  } finally {
    sending.value = false
  }
}

function triggerFileSelect() {
  fileInput.value?.click()
}

async function uploadFile(event) {
  const file = event.target?.files?.[0]
  event.target.value = ''
  if (!file || !current.value?.roomId) return
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    await api.post(`/rooms/${current.value.roomId}/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  } catch (error) {
    console.error('파일 업로드 실패', error)
    alert('파일 업로드에 실패했습니다.')
  } finally {
    uploading.value = false
  }
}

function toggleTranslate() {
  translateEnabled.value = !translateEnabled.value
  if (translateEnabled.value) {
    activeMessages.value.forEach((message) => {
      ensureTranslated(message)
    })
  }
}

async function storeWord(message) {
  if (!message.translatedText) return
  message.saving = true
  try {
    await saveVocabulary(message.content, message.translatedText)
  } catch (error) {
    console.error('단어 저장 실패', error)
  } finally {
    message.saving = false
  }
}

async function inviteParticipant() {
  if (!current.value || current.value.type !== 'group') return
  const input = prompt('초대할 사용자의 로그인 ID를 입력하세요 (콤마로 구분, 최대 2명)')
  if (!input) return
  const targets = input.split(',').map((id) => id.trim()).filter(Boolean)
  if (!targets.length) return
  try {
    await api.post(`/rooms/${current.value.roomId}/invite`, { targets })
    targets.forEach((target) => {
      const friend = friends.value.find((f) => f.loginId === target)
      current.value.participants.push(friend?.nickName || target)
    })
  } catch (error) {
    console.error('초대 실패', error)
    alert('초대에 실패했습니다.')
  }
}

function startVideoCall() {
  if (!current.value?.roomId) return
  const partnerLogin = current.value.type === 'direct' ? current.value.loginId : ''
  router.push({
    name: 'match-video',
    params: { roomId: String(current.value.roomId) },
    query: {
      partnerNickname: current.value.name,
      partnerLoginId: partnerLogin,
    },
  })
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

async function refreshFriends() {
  try {
    await friendsStore.fetch()
  } catch (error) {
    console.error('친구 목록 갱신 실패', error)
  }
}

onMounted(async () => {
  await refreshFriends()
  connectStomp()
})

onBeforeUnmount(() => {
  try {
    Object.values(subscriptions).forEach((sub) => sub?.unsubscribe?.())
  } catch (error) {
    console.warn('Failed to unsubscribe from rooms', error)
  }
  chatClient.value?.deactivate?.()
})

watch(activeMessages, () => {
  scrollToBottom()
})

watch(targetLang, () => {
  if (translateEnabled.value) {
    activeMessages.value.forEach((message) => {
      message.translatedText = null
      ensureTranslated(message)
    })
  }
})
</script>

<style scoped>
.chat-page {
  height: calc(100vh - var(--v-layout-top));
}

.chat-sidebar {
  background: #fff;
  border-right: 2px solid #f5c6d6;
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

.sidebar-header {
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.lang-select {
  max-width: 120px;
  margin-right: 8px;
}

.max-w-220 {
  max-width: 220px;
  border-radius: 10px;
}
</style>

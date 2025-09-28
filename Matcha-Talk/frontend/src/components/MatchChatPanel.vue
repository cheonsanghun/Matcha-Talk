<template>
  <v-card variant="outlined" class="match-chat-panel d-flex flex-column">
    <div class="flex-grow-1 overflow-y-auto px-3 py-2" ref="messagesContainer">
      <div v-if="!messages.length" class="text-caption text-medium-emphasis text-center mt-4">
        {{ placeholderMessage }}
      </div>
      <div
        v-for="message in messages"
        :key="message.id"
        class="chat-message my-2"
        :class="{ 'chat-message--me': message.me }"
      >
        <div class="chat-message__meta text-caption text-medium-emphasis">
          <span v-if="message.sender" class="me-2">{{ message.sender }}</span>
          <span>{{ message.time }}</span>
        </div>
        <div class="chat-message__bubble">
          {{ message.text }}
        </div>
      </div>
    </div>
    <v-divider />
    <div class="px-3 py-2">
      <v-text-field
        v-model="draft"
        :disabled="!canSend"
        density="comfortable"
        hide-details
        variant="outlined"
        placeholder="메시지를 입력하세요"
        @keyup.enter.prevent="sendMessage"
      >
        <template #append-inner>
          <v-icon
            class="cursor-pointer"
            :color="draft.trim() && canSend ? 'pink' : undefined"
            @click="sendMessage"
          >mdi-send</v-icon>
        </template>
      </v-text-field>
      <div v-if="!connectionReady" class="text-caption text-medium-emphasis mt-1">
        {{ connectionStatusText }}
      </div>
      <div v-else class="text-caption text-medium-emphasis mt-1">
        실시간으로 연결되었습니다.
      </div>
    </div>
  </v-card>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useAuthStore } from '../stores/auth'
import { createRealtimeClient } from '../services/ws'
import { resolveClientIdentity } from '../utils/identity'
import { camelizeKeys } from '../utils/case'

const props = defineProps({
  roomId: {
    type: [Number, String],
    default: null,
  },
  partnerName: {
    type: String,
    default: '',
  },
})

const auth = useAuthStore()
const draft = ref('')
const messages = ref([])
const connectionState = ref('idle')
const connectionError = ref('')
const messagesContainer = ref(null)

let realtimeClient = null
let teardownHandlers = []
let manualDisconnect = false
let reconnectTimer = null

const normalizedRoomId = computed(() => {
  const value = Number(props.roomId)
  return Number.isFinite(value) ? value : null
})

const connectionReady = computed(() => connectionState.value === 'open')
const canSend = computed(() => connectionReady.value && normalizedRoomId.value != null)
const placeholderMessage = computed(() => {
  if (!normalizedRoomId.value) {
    return '채팅방이 활성화되면 메시지를 주고받을 수 있습니다.'
  }
  if (!connectionReady.value) {
    return '채팅 서버에 연결하는 중입니다...'
  }
  return `${props.partnerName || '상대방'}님과 첫 대화를 시작해보세요!`
})

const connectionStatusText = computed(() => {
  switch (connectionState.value) {
    case 'connecting':
      return '채팅 서버에 연결 중입니다...'
    case 'error':
      return connectionError.value || '연결 오류가 발생했습니다.'
    case 'closed':
      return '연결이 종료되었습니다. 잠시 후 다시 시도됩니다.'
    default:
      return '연결 준비 중입니다.'
  }
})

watch(normalizedRoomId, (nextRoom) => {
  resetMessages()
  if (nextRoom != null) {
    connect()
  } else {
    disconnect()
  }
}, { immediate: true })

function resetMessages () {
  messages.value = []
}

function pushMessage ({ text, sender, me, sentAt }) {
  const time = formatTime(sentAt)
  messages.value.push({
    id: `${sentAt || Date.now()}-${messages.value.length}`,
    text,
    sender,
    me,
    time,
  })
  scrollToBottom()
}

function formatTime (value) {
  if (!value) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function scrollToBottom () {
  nextTick(() => {
    const el = messagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function ensureClient (loginId) {
  if (!realtimeClient) {
    realtimeClient = createRealtimeClient({ queryParams: { loginId } })
    teardownHandlers.push(
      realtimeClient.onOpen(() => {
        connectionState.value = 'open'
        connectionError.value = ''
        manualDisconnect = false
      }),
      realtimeClient.onClose(() => {
        connectionState.value = 'closed'
        if (!manualDisconnect && normalizedRoomId.value != null) {
          scheduleReconnect()
        }
      }),
      realtimeClient.onError((event) => {
        connectionState.value = 'error'
        connectionError.value = event?.message || '연결 오류가 발생했습니다.'
      }),
      realtimeClient.onEvent('chat', handleIncomingMessage)
    )
  } else {
    realtimeClient.setQueryParams({ loginId })
  }
}

function scheduleReconnect () {
  if (reconnectTimer) return
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, 1500)
}

async function connect () {
  if (normalizedRoomId.value == null) {
    connectionState.value = 'idle'
    return
  }
  const loginId = resolveClientIdentity(auth)
  manualDisconnect = false
  ensureClient(loginId)
  connectionState.value = 'connecting'
  try {
    await realtimeClient.connect()
    connectionState.value = 'open'
  } catch (error) {
    connectionState.value = 'error'
    connectionError.value = error?.message || '연결에 실패했습니다.'
    scheduleReconnect()
  }
}

function disconnect () {
  manualDisconnect = true
  if (realtimeClient) {
    try {
      realtimeClient.disconnect()
    } catch (error) {
      console.warn('[MatchChatPanel] Failed to disconnect WebSocket', error)
    }
  }
  teardownHandlers.forEach((off) => {
    try { off?.() } catch (error) { console.warn('[MatchChatPanel] Failed to remove handler', error) }
  })
  teardownHandlers = []
  realtimeClient = null
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  connectionState.value = 'idle'
}

function handleIncomingMessage (payload) {
  if (!payload) return
  const normalized = camelizeKeys(payload)
  const incomingRoomId = Number(normalized.roomId ?? normalized.room_id)
  if (!Number.isFinite(incomingRoomId) || incomingRoomId !== normalizedRoomId.value) {
    return
  }

  const senderNickname = normalized.senderNickName || normalized.senderNickname || '상대방'
  const content = normalized.content ?? ''
  const meNickname = auth.user?.nickName || auth.user?.nickname
  pushMessage({
    text: content,
    sender: senderNickname,
    me: meNickname ? senderNickname === meNickname : false,
    sentAt: normalized.sentAt,
  })
}

async function sendMessage () {
  const text = draft.value.trim()
  if (!text || !canSend.value) return

  try {
    realtimeClient.send({
      type: 'CHAT',
      roomId: normalizedRoomId.value,
      content: text,
    })
    const myNickname = auth.user?.nickName || auth.user?.nickname || '나'
    pushMessage({
      text,
      sender: myNickname,
      me: true,
      sentAt: new Date().toISOString(),
    })
    draft.value = ''
  } catch (error) {
    connectionState.value = 'error'
    connectionError.value = error?.message || '메시지를 전송하지 못했습니다.'
  }
}

onMounted(() => {
  if (normalizedRoomId.value != null) {
    connect()
  }
})

onBeforeUnmount(() => {
  disconnect()
})
</script>

<style scoped>
.match-chat-panel {
  min-height: 320px;
}

.chat-message {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.chat-message--me {
  margin-left: auto;
  align-items: flex-end;
}

.chat-message__meta {
  margin-bottom: 2px;
}

.chat-message__bubble {
  background-color: rgba(255, 192, 203, 0.15);
  border-radius: 12px;
  padding: 8px 12px;
  word-break: break-word;
}

.chat-message--me .chat-message__bubble {
  background-color: rgba(255, 105, 180, 0.2);
}
</style>

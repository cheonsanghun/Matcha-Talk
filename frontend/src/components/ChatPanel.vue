<template>
  <div class="d-flex flex-column h-100">
    <div class="d-flex justify-end mb-1">
      <v-btn size="small" variant="outlined" @click="addFriend">친구 추가</v-btn>
    </div>
    <div class="flex-grow-1 overflow-y-auto pe-2" ref="messagesContainer">
      <template v-if="props.enabled">
        <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['chat-message', msg.isMine ? 'chat-message--mine' : 'chat-message--partner']"
        >
          <div v-if="!msg.isMine && msg.senderName" class="chat-message__sender">
            {{ msg.senderName }}
          </div>
          <div v-if="msg.fileUrl" class="chat-message__file">
            <img
                v-if="msg.isImage"
                :src="msg.fileUrl"
                :alt="msg.fileName"
                class="chat-image"
            />
            <a
                v-else
                :href="msg.fileUrl"
                target="_blank"
                rel="noopener"
                :download="msg.fileName"
            >
              {{ msg.fileName }}
            </a>
          </div>
          <div
              v-else
              class="chat-bubble"
              :class="msg.isMine ? 'chat-bubble--mine' : 'chat-bubble--partner'"
          >
            {{ msg.original }}
          </div>
          <div
              v-if="msg.translated"
              class="chat-translation d-flex align-center"
              :class="msg.isMine ? 'justify-end' : ''"
          >
            <span class="me-1">{{ msg.translated }}</span>
            <v-icon size="small" class="cursor-pointer" @click="saveWord(msg)">
              mdi-content-save
            </v-icon>
          </div>
        </div>
      </template>
      <div v-else class="chat-placeholder text-center text-caption pa-4">
        매칭을 서로 수락하면 채팅이 시작됩니다.
      </div>
    </div>
    <v-file-input
        v-model="file"
        prepend-icon="mdi-paperclip"
        hide-details
        density="compact"
        accept="image/*,application/*"
        :disabled="!isReady"
        @change="sendFile"
    />
    <v-text-field
        v-model="newMessage"
        @keyup.enter="send"
        placeholder="메시지를 입력하세요"
        density="compact"
        hide-details
        :disabled="!isReady"
    >
      <template #append-inner>
        <v-icon
            @click="toggleTranslate"
            :color="useTranslate ? 'primary' : undefined"
            class="me-1 cursor-pointer"
        >mdi-translate</v-icon>
        <v-icon @click="send" class="cursor-pointer">mdi-send</v-icon>
      </template>
    </v-text-field>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed, onMounted, onBeforeUnmount } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useVocabularyStore } from '../stores/vocabulary'
import followService from '../services/follow'
import { uploadChatFile } from '../services/chat'

const props = defineProps({
  partnerLoginId: { type: String, default: '' },
  partnerUserPid: { type: [Number, String], default: null },
  roomId: { type: Number, default: null },
  stompClient: { type: Object, default: null },
  connected: { type: Boolean, default: false },
  enabled: { type: Boolean, default: false },
})

const messages = ref([])
const newMessage = ref('')
const file = ref(null)
const messagesContainer = ref(null)
const useTranslate = ref(false)

const vocab = useVocabularyStore()
const auth = useAuthStore()

const myLoginId = computed(() => auth.user?.loginId ?? null)
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''
const isReady = computed(() => props.enabled && !!props.roomId && !!props.stompClient && props.connected)

let subscription = null
const seenMessageIds = new Set()

function scrollToBottom() {
  nextTick(() => {
    const el = messagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function resolveFileUrl(path) {
  if (!path) {
    return ''
  }
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  if (apiBaseUrl && apiBaseUrl.startsWith('http')) {
    const base = apiBaseUrl.replace(/\/api\/?$/, '')
    const normalized = path.startsWith('/') ? path : `/${path}`
    return `${base}${normalized}`
  }
  return path
}

function mapPayload(payload = {}) {
  const contentType = payload.contentType || 'TEXT'
  const isImage = contentType === 'IMAGE' || (payload.mimeType && payload.mimeType.startsWith('image/'))
  const isFileMessage = contentType === 'FILE' || contentType === 'IMAGE'

  const message = {
    id: payload.messageId ?? `${payload.roomId ?? 'room'}-${payload.sentAt ?? Date.now()}`,
    original: payload.content || (isFileMessage ? payload.fileName || '' : ''),
    translated: payload.translatedContent || '',
    senderName: payload.senderNickName || '',
    senderLoginId: payload.senderLoginId || '',
    fileUrl: isFileMessage ? resolveFileUrl(payload.fileUrl) : '',
    fileName: payload.fileName || '',
    isImage,
    mimeType: payload.mimeType || '',
    sentAt: payload.sentAt || null,
    contentType,
  }
  message.isMine = !!myLoginId.value && message.senderLoginId === myLoginId.value
  if (!message.original && message.fileName) {
    message.original = message.fileName
  }
  return message
}

function handleIncomingMessage(payload) {
  const parsed = mapPayload(payload)
  if (!parsed.id || seenMessageIds.has(parsed.id)) {
    return
  }
  seenMessageIds.add(parsed.id)
  messages.value.push(parsed)
  scrollToBottom()
}

function subscribeToRoom() {
  if (!isReady.value || !props.stompClient?.subscribe) {
    return
  }
  unsubscribeFromRoom()
  try {
    subscription = props.stompClient.subscribe(`/topic/rooms/${props.roomId}`, (frame) => {
      try {
        const body = typeof frame.body === 'string' ? JSON.parse(frame.body) : frame.body
        handleIncomingMessage(body)
      } catch (error) {
        console.error('Failed to parse chat message:', error)
      }
    })
  } catch (error) {
    console.error('Failed to subscribe to chat room:', error)
  }
}

function unsubscribeFromRoom() {
  if (subscription) {
    try {
      subscription.unsubscribe()
    } catch (error) {
      console.warn('Failed to unsubscribe from chat room:', error)
    }
    subscription = null
  }
}

watch(isReady, (ready) => {
  if (ready) {
    subscribeToRoom()
  } else {
    unsubscribeFromRoom()
  }
}, { immediate: true })

watch(() => props.roomId, (newRoom, oldRoom) => {
  if (newRoom !== oldRoom) {
    messages.value = []
    seenMessageIds.clear()
    if (isReady.value) {
      subscribeToRoom()
    }
  }
})

watch(() => props.stompClient, () => {
  if (isReady.value) {
    subscribeToRoom()
  }
})

function toggleTranslate() {
  useTranslate.value = !useTranslate.value
}

function saveWord(msg) {
  if (msg?.translated) {
    vocab.addWord(msg.original, msg.translated)
  }
}

async function send() {
  const text = newMessage.value.trim()
  if (!text) {
    return
  }
  if (!isReady.value) {
    alert('채팅 연결이 준비되지 않았습니다.')
    return
  }

  try {
    props.stompClient.publish({
      destination: `/app/chat.sendMessage/${props.roomId}`,
      body: JSON.stringify({
        content: text,
        translate: useTranslate.value,
      }),
    })
    newMessage.value = ''
  } catch (error) {
    console.error('메시지 전송 실패:', error)
    alert('메시지 전송에 실패했습니다.')
  }
}

async function sendFile() {
  const selectedFile = file.value
  if (!selectedFile) {
    return
  }
  if (!isReady.value) {
    alert('채팅 연결이 준비되지 않았습니다.')
    file.value = null
    return
  }

  try {
    const { data } = await uploadChatFile(props.roomId, selectedFile)
    if (data) {
      handleIncomingMessage(data)
    }
  } catch (error) {
    console.error('파일 전송 실패:', error)
    alert('파일 전송에 실패했습니다.')
  } finally {
    file.value = null
  }
}

async function addFriend() {
  if (!props.partnerUserPid) {
    alert('친구 추가할 대상을 알 수 없습니다.')
    return
  }
  try {
    const followeeId = Number(props.partnerUserPid)
    await followService.requestFollow(followeeId)
    const targetName = props.partnerLoginId || followeeId
    alert(`${targetName}님에게 팔로우 요청을 보냈습니다.`)
  } catch (error) {
    console.error('팔로우 요청 실패:', error)
    alert('팔로우 요청에 실패했습니다: ' + (error.response?.data?.message || error.message))
  }
}

onMounted(() => {
  scrollToBottom()
})

onBeforeUnmount(() => {
  unsubscribeFromRoom()
  seenMessageIds.clear()
})
</script>

<style scoped>
.chat-message {
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}

.chat-message--mine {
  align-items: flex-end;
}

.chat-message--partner {
  align-items: flex-start;
}

.chat-message__sender {
  font-size: 0.75rem;
  color: var(--v-theme-on-surface-variant);
  margin-bottom: 4px;
}

.chat-bubble {
  max-width: 100%;
  border-radius: 12px;
  padding: 8px 12px;
  line-height: 1.4;
  word-break: break-word;
}

.chat-bubble--mine {
  background-color: #f8bbd0;
  color: #212121;
}

.chat-bubble--partner {
  background-color: #f5f5f5;
  color: #212121;
}

.chat-translation {
  font-size: 0.75rem;
  color: #757575;
  margin-top: 4px;
}

.chat-message__file a {
  color: #1976d2;
  text-decoration: underline;
}

.chat-image {
  max-width: 100%;
  border-radius: 8px;
}

.max-w-100 {
  max-width: 100%;
}

.chat-placeholder {
  border: 1px dashed rgba(0, 0, 0, 0.12);
  border-radius: 8px;
  background-color: #fff5f8;
}
</style>

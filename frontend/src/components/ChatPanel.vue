<template>
  <div class="d-flex flex-column h-100">
    <div class="d-flex justify-end mb-1">
      <v-btn size="small" variant="outlined" @click="addFriend" :disabled="!partner">
        친구 추가
      </v-btn>
    </div>

    <v-alert
      v-if="errorMessage"
      type="error"
      density="compact"
      class="mb-2"
      variant="tonal"
      closable
      @click:close="errorMessage = ''"
    >
      {{ errorMessage }}
    </v-alert>

    <div class="flex-grow-1 overflow-y-auto pe-2" ref="messagesContainer">
      <div
        v-if="!computedMessages.length"
        class="text-caption text-medium-emphasis text-center mt-4"
      >
        아직 메시지가 없습니다. 인사를 건네보세요!
      </div>

      <div
        v-for="msg in computedMessages"
        :key="msg.key"
        class="mb-3 d-flex"
        :class="msg.fromMe ? 'justify-end' : 'justify-start'"
      >
        <div :class="['message-bubble', msg.fromMe ? 'from-me' : 'from-partner']">
          <div class="d-flex justify-space-between align-center text-caption message-meta">
            <span class="fw-medium">{{ msg.senderNickName || '알 수 없음' }}</span>
            <span class="ms-2">{{ msg.timeLabel }}</span>
          </div>

          <template v-if="msg.fileUrl">
            <div class="mt-2">
              <div class="d-flex align-center">
                <v-icon size="18" class="me-1">
                  {{ msg.isImage ? 'mdi-image' : 'mdi-file-download' }}
                </v-icon>
                <a
                  :href="msg.fileUrl"
                  target="_blank"
                  rel="noopener"
                  class="text-body-2 text-decoration-none"
                >
                  {{ msg.fileName || '첨부파일' }}
                </a>
              </div>
              <div v-if="msg.sizeLabel" class="text-caption text-medium-emphasis">
                {{ msg.sizeLabel }}
              </div>
              <img v-if="msg.isImage" :src="msg.fileUrl" class="attached-image mt-2" />
            </div>
          </template>
          <template v-else>
            <div class="message-content text-body-2 mt-2">
              {{ msg.content }}
            </div>
            <div
              v-if="showTranslation && msg.translatedContent"
              class="text-caption text-medium-emphasis mt-2 d-flex align-center"
            >
              {{ msg.translatedContent }}
              <v-icon
                size="16"
                class="ms-1 cursor-pointer"
                @click="saveWord(msg)"
              >
                mdi-content-save
              </v-icon>
            </div>
          </template>
        </div>
      </div>
    </div>

    <v-file-input
      v-if="hasFileUpload"
      v-model="file"
      prepend-icon="mdi-paperclip"
      hide-details
      density="compact"
      accept="image/*,application/*"
      @change="sendFile"
      :disabled="isUploading"
      :loading="isUploading"
      clearable
      class="mt-2"
    />

    <v-text-field
      v-model="newMessage"
      @keyup.enter="send"
      placeholder="메시지를 입력하세요"
      density="compact"
      hide-details
      :disabled="isSending"
    >
      <template #append-inner>
        <v-icon
          @click="toggleTranslate"
          :color="showTranslation ? 'primary' : undefined"
          class="me-1 cursor-pointer"
        >
          mdi-translate
        </v-icon>
        <v-icon
          @click="send"
          class="cursor-pointer"
          :class="{ 'text-disabled': isSending }"
        >
          mdi-send
        </v-icon>
      </template>
    </v-text-field>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useVocabularyStore } from '../stores/vocabulary'
import { useFriendsStore } from '../stores/friends'

const props = defineProps({
  partner: { type: String, default: '' },
  messages: { type: Array, default: () => [] },
  sending: { type: Boolean, default: false },
  uploading: { type: Boolean, default: false },
  onSend: { type: Function, required: true },
  onSendFile: { type: Function, default: null },
})

const vocab = useVocabularyStore()
const friends = useFriendsStore()

const errorMessage = ref('')
const newMessage = ref('')
const file = ref(null)
const messagesContainer = ref(null)
const showTranslation = ref(false)
const internalSending = ref(false)
const internalUploading = ref(false)

const isSending = computed(() => props.sending || internalSending.value)
const isUploading = computed(() => props.uploading || internalUploading.value)
const hasFileUpload = computed(() => typeof props.onSendFile === 'function')

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  hour: '2-digit',
  minute: '2-digit',
})

const computedMessages = computed(() =>
  props.messages.map((msg, index) => {
    const sentAt = msg.sentAt ? new Date(msg.sentAt) : null
    const contentType = (msg.contentType || '').toString().toUpperCase()
    const mimeType = msg.mimeType || ''
    const isImage = contentType === 'IMAGE' || mimeType.startsWith('image/')

    return {
      ...msg,
      key: msg.id ?? `${index}-${sentAt ? sentAt.getTime() : Date.now()}`,
      timeLabel: sentAt ? timeFormatter.format(sentAt) : '',
      isImage,
      sizeLabel: formatBytes(msg.sizeBytes),
    }
  })
)

watch(
  () => props.messages.length,
  () => {
    nextTick(scrollToBottom)
  }
)

onMounted(() => {
  scrollToBottom()
})

function scrollToBottom() {
  const el = messagesContainer.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function formatBytes(size) {
  if (typeof size !== 'number' || Number.isNaN(size) || size < 0) {
    return ''
  }
  const units = ['B', 'KB', 'MB', 'GB']
  let value = size
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  const fixed = value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1)
  return `${fixed} ${units[unitIndex]}`
}

function toggleTranslate() {
  showTranslation.value = !showTranslation.value
}

function saveWord(msg) {
  if (msg.translatedContent) {
    vocab.addWord(msg.content, msg.translatedContent)
  }
}

async function send() {
  const text = newMessage.value.trim()
  if (!text || isSending.value || typeof props.onSend !== 'function') {
    return
  }

  try {
    internalSending.value = true
    await props.onSend(text)
    newMessage.value = ''
  } catch (error) {
    console.error('채팅 메시지 전송 실패', error)
    displayError(error)
  } finally {
    internalSending.value = false
  }
}

async function sendFile() {
  const selected = file.value
  if (!selected || !hasFileUpload.value || isUploading.value) {
    return
  }

  try {
    internalUploading.value = true
    await props.onSendFile(selected)
    file.value = null
  } catch (error) {
    console.error('파일 업로드 실패', error)
    displayError(error)
  } finally {
    internalUploading.value = false
  }
}

function displayError(error) {
  if (!error) {
    return
  }
  const message =
    typeof error === 'string'
      ? error
      : error?.response?.data?.message || error?.message || '요청 처리 중 오류가 발생했습니다.'
  errorMessage.value = message
  window.setTimeout(() => {
    if (errorMessage.value === message) {
      errorMessage.value = ''
    }
  }, 5000)
}

function addFriend() {
  if (props.partner) {
    friends.upsert({ partnerNickName: props.partner })
  }
}

</script>

<style scoped>
.message-bubble {
  max-width: 100%;
  min-width: 40%;
  border-radius: 12px;
  padding: 10px 12px;
  background-color: #f5f5f5;
}

.message-bubble.from-me {
  background-color: #f8bbd0;
  color: #2d2d2d;
}

.message-bubble.from-partner {
  background-color: #ffffff;
  border: 1px solid #f5f5f5;
}

.message-meta {
  color: rgba(0, 0, 0, 0.45);
}

.attached-image {
  max-width: 100%;
  border-radius: 8px;
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

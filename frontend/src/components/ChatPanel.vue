<template>
  <div class="chat-panel d-flex flex-column h-100">
    <div class="chat-panel__header text-subtitle-2 mb-2">
      {{ partner ? `${partner}님과 대화` : '실시간 채팅' }}
    </div>
    <div ref="listRef" class="chat-panel__body flex-grow-1 overflow-y-auto">
      <div v-if="!messages.length" class="text-caption text-medium-emphasis text-center mt-4">
        아직 메시지가 없습니다.
      </div>
      <div
        v-for="item in messages"
        :key="item.id"
        class="chat-message"
        :class="{ 'chat-message--me': item.me }"
      >
        <div class="chat-message__bubble">
          <div class="chat-message__content">{{ item.content }}</div>
          <div v-if="item.translatedContent" class="chat-message__translation">
            {{ item.translatedContent }}
          </div>
          <div class="chat-message__meta">{{ formatTime(item.sentAt) }}</div>
        </div>
      </div>
    </div>
    <div class="chat-panel__input d-flex align-center ga-2 mt-3">
      <v-textarea
        v-model="draft"
        variant="outlined"
        density="comfortable"
        rows="2"
        hide-details
        auto-grow
        :placeholder="placeholder"
        @keydown.enter.exact.prevent="emitSend"
      />
      <v-btn color="pink" :loading="sending" :disabled="!draft.trim()" @click="emitSend">
        <v-icon>mdi-send</v-icon>
      </v-btn>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  partner: {
    type: String,
    default: '',
  },
  messages: {
    type: Array,
    default: () => [],
  },
  sending: {
    type: Boolean,
    default: false,
  },
  placeholder: {
    type: String,
    default: '메시지를 입력하세요...'
  },
})

const emit = defineEmits(['send'])

const draft = ref('')
const listRef = ref(null)

const formatTime = (value) => {
  if (!value) return ''
  try {
    const date = typeof value === 'string' ? new Date(value) : value
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch (e) {
    return value
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

const emitSend = () => {
  const text = draft.value.trim()
  if (!text) return
  emit('send', text)
  draft.value = ''
}

watch(
  () => props.messages,
  () => {
    scrollToBottom()
  },
  { deep: true }
)

watch(
  () => props.sending,
  (value, prev) => {
    if (prev && !value) {
      scrollToBottom()
    }
  }
)
</script>

<style scoped>
.chat-panel {
  min-height: 280px;
}

.chat-panel__header {
  color: rgba(0, 0, 0, 0.54);
}

.chat-message {
  display: flex;
  margin-bottom: 12px;
}

.chat-message--me {
  justify-content: flex-end;
}

.chat-message__bubble {
  max-width: 100%;
  padding: 10px 14px;
  border-radius: 12px;
  background-color: #f8bbd0;
  color: #4a148c;
}

.chat-message--me .chat-message__bubble {
  background-color: #f06292;
  color: white;
}

.chat-message__translation {
  margin-top: 4px;
  font-size: 0.75rem;
  color: rgba(0, 0, 0, 0.6);
}

.chat-message--me .chat-message__translation {
  color: rgba(255, 255, 255, 0.8);
}

.chat-message__meta {
  margin-top: 4px;
  font-size: 0.7rem;
  text-align: right;
  opacity: 0.7;
}

.chat-panel__input {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding-top: 8px;
}
</style>

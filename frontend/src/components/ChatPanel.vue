<template>
  <div class="d-flex flex-column h-100">
    <div class="d-flex justify-between align-center mb-2">
      <div class="text-subtitle-2 text-medium-emphasis">{{ partnerLabel }}</div>
      <v-btn
        v-if="showFriendButton"
        size="small"
        variant="outlined"
        :loading="friendLoading"
        @click="addFriend"
      >친구 추가</v-btn>
    </div>

    <div class="flex-grow-1 overflow-y-auto pe-2 message-list" ref="messagesContainer">
      <div v-if="loading" class="d-flex justify-center py-4">
        <v-progress-circular indeterminate color="primary" />
      </div>
      <div v-else-if="!messages.length" class="text-caption text-grey text-center py-4">
        아직 대화가 없습니다. 메시지를 보내 대화를 시작해보세요.
      </div>
      <template v-else>
        <div
          v-for="msg in messages"
          :key="msg.messageId"
          class="message-row d-flex"
          :class="{ 'justify-end': msg.isMine }"
        >
          <div :class="['message-bubble', msg.isMine ? 'mine' : 'peer']">
            <template v-if="msg.contentType === 'TEXT'">
              <div class="text-body-2 text-pre-wrap">{{ msg.content }}</div>
              <div
                v-if="shouldShowTranslation && msg.translated"
                class="text-caption text-grey mt-1 d-flex align-center"
              >
                <span class="text-pre-wrap">{{ msg.translated }}</span>
                <v-icon
                  size="18"
                  class="ms-1 cursor-pointer"
                  @click="saveWord(msg)"
                >mdi-content-save</v-icon>
              </div>
            </template>
            <template v-else-if="msg.contentType === 'IMAGE'">
              <img :src="msg.fileUrl" :alt="msg.fileName" class="attachment-image" />
            </template>
            <template v-else>
              <a :href="msg.fileUrl" target="_blank" class="text-body-2">
                <v-icon size="18" class="me-1">mdi-paperclip</v-icon>{{ msg.fileName }}
              </a>
            </template>
            <div class="text-caption text-disabled mt-1 text-right">
              {{ msg.displayTime }}
            </div>
          </div>
        </div>
      </template>
    </div>

    <v-file-input
      v-model="file"
      prepend-icon="mdi-paperclip"
      hide-details
      density="compact"
      accept="image/*,application/*"
      :disabled="!canChat"
      @change="sendFile"
    />

    <v-text-field
      v-model="newMessage"
      :disabled="!canChat"
      @keyup.enter="send"
      placeholder="메시지를 입력하세요"
      density="compact"
      hide-details
    >
      <template #append-inner>
        <v-icon
          @click="toggleTranslate"
          :color="useTranslate ? 'primary' : undefined"
          class="me-1 cursor-pointer"
        >mdi-translate</v-icon>
        <v-icon @click="send" :class="['cursor-pointer', { 'text-disabled': !newMessage.trim() }]">
          mdi-send
        </v-icon>
      </template>
    </v-text-field>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useVocabularyStore } from '../stores/vocabulary'
import { useFriendsStore } from '../stores/friends'
import { setupChat, fetchRoomMessages, uploadRoomFile } from '../services/chat'

const props = defineProps({
  partner: { type: String, default: '' },
  partnerLoginId: { type: String, default: '' },
  roomId: { type: [Number, String], default: null },
  client: { type: Object, default: null },
  connected: { type: Boolean, default: false },
  showAddFriend: { type: Boolean, default: true }
})

const auth = useAuthStore()
const vocab = useVocabularyStore()
const friends = useFriendsStore()

const messages = ref([])
const newMessage = ref('')
const file = ref(null)
const messagesContainer = ref(null)
const useTranslate = ref(false)
const loading = ref(false)
const friendLoading = ref(false)
let chatBridge = null

const partnerLabel = computed(() => props.partner || '상대방')
const currentLoginId = computed(() => auth.user?.loginId || auth.user?.login_id || null)
const shouldShowTranslation = computed(() => useTranslate.value)
const canChat = computed(() => !!props.roomId && props.connected && !!chatBridge)
const showFriendButton = computed(() => props.showAddFriend && !!props.partnerLoginId)

function scrollToBottom() {
  nextTick(() => {
    const el = messagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function normalizeMessage(payload) {
  const sentAt = payload.sentAt ? new Date(payload.sentAt) : new Date()
  const isMine = payload.senderLoginId && payload.senderLoginId === currentLoginId.value
  return {
    messageId: payload.messageId || `${payload.roomId}-${sentAt.getTime()}-${Math.random()}`,
    roomId: payload.roomId,
    senderLoginId: payload.senderLoginId,
    senderNickName: payload.senderNickName,
    contentType: payload.contentType || 'TEXT',
    content: payload.content || '',
    translated: payload.translatedContent || '',
    fileUrl: payload.fileUrl || '',
    fileName: payload.fileName || payload.content || '',
    mimeType: payload.mimeType || '',
    sizeBytes: payload.sizeBytes || 0,
    sentAt,
    displayTime: sentAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    isMine
  }
}

function handleIncoming(payload) {
  const message = normalizeMessage(payload)
  messages.value.push(message)
  scrollToBottom()
}

async function loadHistory() {
  if (!props.roomId) {
    messages.value = []
    return
  }
  loading.value = true
  try {
    const history = await fetchRoomMessages(props.roomId, { size: 100 })
    messages.value = history.map(normalizeMessage)
    scrollToBottom()
  } catch (error) {
    console.error('메시지 히스토리 불러오기 실패', error)
  } finally {
    loading.value = false
  }
}

function cleanupSubscription() {
  if (chatBridge?.sub) {
    chatBridge.sub.unsubscribe()
  }
  chatBridge = null
}

function ensureSubscription() {
  if (!props.client || !props.connected || !props.roomId) {
    cleanupSubscription()
    return
  }
  cleanupSubscription()
  chatBridge = setupChat(props.client, props.roomId, {
    onChat: handleIncoming
  })
}

async function send() {
  const text = newMessage.value.trim()
  if (!text || !chatBridge) {
    return
  }
  try {
    chatBridge.sendChat({ content: text })
    newMessage.value = ''
  } catch (error) {
    console.error('메시지 전송 실패', error)
  }
}

async function sendFile() {
  const selected = file.value
  if (!selected || !props.roomId) {
    return
  }
  try {
    await uploadRoomFile(props.roomId, selected)
  } catch (error) {
    console.error('파일 전송 실패', error)
    window.alert('파일 전송 중 문제가 발생했습니다.')
  } finally {
    file.value = null
  }
}

function toggleTranslate() {
  useTranslate.value = !useTranslate.value
}

function saveWord(msg) {
  if (msg.contentType === 'TEXT' && msg.translated) {
    vocab.addWord(msg.content, msg.translated)
  }
}

async function addFriend() {
  if (!props.partnerLoginId) return
  friendLoading.value = true
  try {
    await friends.follow(props.partnerLoginId)
    window.alert('친구 목록에 추가되었습니다.')
  } catch (error) {
    console.error('친구 추가 실패', error)
    window.alert(error?.response?.data?.message || '친구 추가에 실패했습니다.')
  } finally {
    friendLoading.value = false
  }
}

watch(
  () => [props.roomId, props.connected, props.client],
  () => {
    ensureSubscription()
    void loadHistory()
  },
  { immediate: true }
)

onMounted(() => {
  if (!friends.initialized) {
    void friends.fetch()
  }
})

onBeforeUnmount(() => {
  cleanupSubscription()
})

</script>

<style scoped>
.message-list {
  background-color: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
  padding: 8px;
}

.message-row {
  width: 100%;
}

.message-bubble {
  max-width: 100%;
  padding: 8px 12px;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.message-bubble.mine {
  background-color: #f8bbd0;
  color: #212121;
}

.message-bubble.peer {
  background-color: #ffffff;
}

.attachment-image {
  max-width: 180px;
  border-radius: 6px;
}

.text-pre-wrap {
  white-space: pre-wrap;
}
</style>

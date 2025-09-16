<template>
  <v-container class="py-8">
    <v-row justify="center">
      <v-col cols="12" xl="10">
        <v-card class="pa-4 video-chat-card">
          <div class="header-row mb-4">
            <div>
              <div class="text-h6 text-pink-darken-2">랜덤 영상 채팅</div>
              <div class="text-caption text-medium-emphasis">
                {{ partnerNickname ? `${partnerNickname}님과 연결 중` : '상대방을 기다리는 중입니다' }}
              </div>
            </div>
            <div class="d-flex flex-wrap gap-2">
              <v-btn
                v-if="partnerLoginId && !isFollowing"
                color="pink"
                variant="tonal"
                :disabled="friendLoading"
                @click="followPartner"
              >친구 추가</v-btn>
              <v-btn color="grey" variant="tonal" @click="leaveCall">나가기</v-btn>
            </div>
          </div>

          <v-row class="g-4">
            <v-col cols="12" md="7" class="d-flex">
              <div class="video-stage mb-4 flex-grow-1">
                <video ref="remoteVideo" autoplay playsinline class="remote-video bg-grey-lighten-3"></video>
                <video ref="localVideo" autoplay muted playsinline class="local-video bg-grey-lighten-1"></video>
                <div v-if="connecting && !connectionError" class="video-overlay d-flex align-center justify-center">
                  <v-progress-circular indeterminate color="pink" size="56" />
                </div>
              </div>

              <v-alert
                v-if="connectionError"
                type="error"
                variant="tonal"
                class="mb-4 w-100"
              >
                {{ connectionError }}
              </v-alert>

              <div class="control-buttons" v-if="!connectionError">
                <v-btn
                  color="pink"
                  variant="flat"
                  class="mr-2"
                  @click="toggleMute"
                  :prepend-icon="muted ? 'mdi-microphone-off' : 'mdi-microphone'"
                >
                  {{ muted ? '음소거 해제' : '음소거' }}
                </v-btn>
                <v-btn
                  color="pink"
                  variant="flat"
                  @click="toggleCamera"
                  :prepend-icon="cameraOff ? 'mdi-video-off' : 'mdi-video'"
                >
                  {{ cameraOff ? '카메라 켜기' : '카메라 끄기' }}
                </v-btn>
              </div>
            </v-col>

            <v-col cols="12" md="5">
              <section class="chat-panel">
                <header class="chat-header">
                  <div class="d-flex align-center gap-2">
                    <v-icon color="pink">mdi-chat-processing</v-icon>
                    <span class="text-subtitle-1">텍스트 채팅</span>
                  </div>
                  <div class="d-flex align-center gap-2">
                    <v-select
                      v-model="targetLang"
                      :items="availableLanguages"
                      density="compact"
                      variant="outlined"
                      hide-details
                      class="lang-select"
                    />
                    <v-btn
                      size="small"
                      color="pink"
                      variant="tonal"
                      :prepend-icon="translateEnabled ? 'mdi-translate' : 'mdi-translate-off'"
                      @click="toggleTranslate"
                    >
                      {{ translateEnabled ? '번역 끄기' : '번역 켜기' }}
                    </v-btn>
                  </div>
                </header>

                <div class="chat-messages" ref="messageContainer">
                  <v-progress-linear
                    v-if="chatLoading"
                    indeterminate
                    color="pink"
                    class="mb-4"
                  />
                  <div
                    v-for="msg in chatMessages"
                    :key="msg.id"
                    class="chat-message"
                    :class="{ mine: msg.senderLoginId === myLoginId }"
                  >
                    <div class="meta">
                      <span class="name">{{ msg.senderNickName }}</span>
                      <span class="time">{{ formatTime(msg.sentAt) }}</span>
                    </div>

                    <div v-if="msg.contentType === 'TEXT'" class="bubble">
                      <div>{{ msg.content }}</div>
                      <div
                        v-if="msg.translatedText && msg.senderLoginId !== myLoginId"
                        class="translated"
                      >
                        {{ msg.translatedText }}
                        <v-btn
                          v-if="!msg.saving"
                          size="x-small"
                          variant="text"
                          color="pink"
                          class="ml-2"
                          @click="storeWord(msg)"
                        >저장</v-btn>
                        <v-progress-circular
                          v-else
                          indeterminate
                          size="16"
                          color="pink"
                        />
                      </div>
                    </div>

                    <div v-else class="bubble file-bubble">
                      <template v-if="msg.contentType === 'IMAGE'">
                        <img :src="msg.fileUrl" :alt="msg.fileName" class="chat-image" />
                      </template>
                      <template v-else>
                        <a :href="msg.fileUrl" target="_blank" rel="noopener" class="file-link">
                          <v-icon class="mr-1">mdi-file</v-icon>
                          {{ msg.fileName }}
                        </a>
                      </template>
                    </div>
                  </div>
                </div>

                <footer class="chat-input">
                  <input
                    ref="fileInput"
                    type="file"
                    class="d-none"
                    accept="image/*,application/*"
                    @change="onFileSelected"
                  />
                  <v-btn
                    icon
                    variant="text"
                    color="pink"
                    :loading="uploading"
                    @click="triggerFileSelect"
                  >
                    <v-icon>mdi-paperclip</v-icon>
                  </v-btn>
                  <v-text-field
                    v-model="chatInput"
                    :disabled="!client?.connected"
                    density="comfortable"
                    variant="outlined"
                    placeholder="메시지를 입력하세요"
                    hide-details
                    class="flex-grow-1"
                    @keydown.enter.prevent="sendChat"
                  />
                  <v-btn
                    icon
                    color="pink"
                    variant="flat"
                    :disabled="sending || !chatInput.trim()"
                    :loading="sending"
                    @click="sendChat"
                  >
                    <v-icon>mdi-send</v-icon>
                  </v-btn>
                </footer>
              </section>
            </v-col>
          </v-row>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useFriendsStore } from '../stores/friends'
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'
import api from '../services/api'
import { translate, saveVocabulary } from '../services/translator'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const friendsStore = useFriendsStore()

const roomId = computed(() => Number(route.params.roomId || 0))
const partnerNickname = ref(route.query.partnerNickname || '')
const partnerLoginId = ref(route.query.partnerLoginId || '')

const localVideo = ref(null)
const remoteVideo = ref(null)
const connectionError = ref('')
const connecting = ref(true)
const muted = ref(false)
const cameraOff = ref(false)

const chatMessages = ref([])
const chatInput = ref('')
const chatLoading = ref(false)
const translateEnabled = ref(false)
const targetLang = ref('en')
const messageContainer = ref(null)
const fileInput = ref(null)
const uploading = ref(false)
const sending = ref(false)

const availableLanguages = [
  { title: '영어', value: 'en' },
  { title: '한국어', value: 'ko' },
  { title: '일본어', value: 'ja' },
  { title: '중국어', value: 'zh-CN' },
  { title: '스페인어', value: 'es' },
]

const myLoginId = computed(() => auth.user?.loginId || '')
const shouldInitiate = computed(() => {
  if (!myLoginId.value || !partnerLoginId.value) return false
  return myLoginId.value.localeCompare(partnerLoginId.value) < 0
})

const isFollowing = computed(() =>
  friendsStore.list.some((friend) => friend.loginId === partnerLoginId.value)
)
const friendLoading = computed(() => friendsStore.loading)

let client = null
let signal = null
let pc = null
let localStream = null
let hasSentOffer = false
let chatSubscription = null

function ensureValidRoute() {
  if (!roomId.value) {
    connectionError.value = '유효하지 않은 방 정보입니다.'
    connecting.value = false
    return false
  }
  return true
}

async function preparePeerConnection() {
  pc = new RTCPeerConnection({
    iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
  })

  pc.ontrack = (event) => {
    if (remoteVideo.value) {
      remoteVideo.value.srcObject = event.streams[0]
    }
  }

  pc.onicecandidate = (event) => {
    if (event.candidate && partnerLoginId.value && signal) {
      signal.sendSignal({
        type: 'ice-candidate',
        receiverLoginId: partnerLoginId.value,
        data: event.candidate,
      })
    }
  }
}

async function acquireMedia() {
  try {
    localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    localStream.getTracks().forEach((track) => pc.addTrack(track, localStream))
    if (localVideo.value) {
      localVideo.value.srcObject = localStream
    }
  } catch (err) {
    console.error('Failed to acquire media devices', err)
    connectionError.value = '카메라 또는 마이크에 접근할 수 없습니다.'
    connecting.value = false
    throw err
  }
}

async function createOffer() {
  if (!pc || !signal || !partnerLoginId.value || hasSentOffer) return
  try {
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    signal.sendSignal({
      type: 'offer',
      receiverLoginId: partnerLoginId.value,
      data: offer,
    })
    hasSentOffer = true
  } catch (err) {
    console.error('Failed to create or send offer', err)
  }
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

async function ensureTranslated(message) {
  if (!translateEnabled.value) return
  if (message.senderLoginId === myLoginId.value) return
  if (message.contentType !== 'TEXT' || !message.content) return
  if (message.translatedText) return
  try {
    const { translatedText } = await translate(message.content, { targetLang: targetLang.value })
    message.translatedText = translatedText
  } catch (error) {
    console.error('번역 실패', error)
  }
}

async function loadHistory() {
  chatLoading.value = true
  try {
    const { data } = await api.get(`/rooms/${roomId.value}/messages`, {
      params: { page: 0, size: 50 },
    })
    chatMessages.value = (data.messages || []).map(normalizeMessage)
    if (translateEnabled.value) {
      for (const message of chatMessages.value) {
        await ensureTranslated(message)
      }
    }
    scrollChatToBottom()
  } catch (error) {
    console.error('Failed to load chat history', error)
  } finally {
    chatLoading.value = false
  }
}

function subscribeChat() {
  if (!client?.connected || !roomId.value) return
  chatSubscription?.unsubscribe?.()
  chatSubscription = client.subscribe(`/topic/rooms/${roomId.value}`, (frame) => {
    try {
      const payload = JSON.parse(frame.body)
      const message = normalizeMessage(payload)
      chatMessages.value.push(message)
      ensureTranslated(message)
      scrollChatToBottom()
    } catch (error) {
      console.error('Failed to handle chat frame', error)
    }
  })
}

function scrollChatToBottom() {
  nextTick(() => {
    const el = messageContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

async function sendChat() {
  if (!client?.connected || !chatInput.value.trim()) {
    return
  }

  sending.value = true
  try {
    const payload = {
      roomId: roomId.value,
      content: chatInput.value.trim(),
      contentType: 'TEXT',
    }
    client.publish({
      destination: `/app/chat.sendMessage/${roomId.value}`,
      body: JSON.stringify(payload),
    })
    chatInput.value = ''
  } catch (error) {
    console.error('Failed to send chat message', error)
  } finally {
    sending.value = false
  }
}

function triggerFileSelect() {
  fileInput.value?.click()
}

async function onFileSelected(event) {
  const file = event.target?.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    await api.post(`/rooms/${roomId.value}/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  } catch (error) {
    console.error('Failed to upload file', error)
    alert('파일 업로드에 실패했습니다. 다시 시도해주세요.')
  } finally {
    uploading.value = false
  }
}

function toggleTranslate() {
  translateEnabled.value = !translateEnabled.value
  if (translateEnabled.value) {
    chatMessages.value.forEach((message) => {
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

async function followPartner() {
  if (!partnerLoginId.value) return
  try {
    await friendsStore.add(partnerLoginId.value)
  } catch (error) {
    console.error('친구 추가 실패', error)
    alert('친구 추가에 실패했습니다. 다시 시도해주세요.')
  }
}

async function handleSignalMessage(payload) {
  if (!pc || !payload || payload.senderLoginId === myLoginId.value) {
    return
  }

  if (!partnerLoginId.value) {
    partnerLoginId.value = payload.senderLoginId || partnerLoginId.value
  }

  if (payload.type === 'offer') {
    try {
      await pc.setRemoteDescription(payload.data)
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      signal.sendSignal({
        type: 'answer',
        receiverLoginId: payload.senderLoginId,
        data: answer,
      })
    } catch (err) {
      console.error('Failed to process offer', err)
    }
  } else if (payload.type === 'answer') {
    try {
      await pc.setRemoteDescription(payload.data)
    } catch (err) {
      console.error('Failed to process answer', err)
    }
  } else if (payload.type === 'ice-candidate') {
    try {
      await pc.addIceCandidate(payload.data)
    } catch (err) {
      console.warn('Failed to add ICE candidate', err)
    }
  }
}

function startStomp() {
  client = createStompClient(auth.token)

  client.onConnect = async () => {
    connecting.value = false

    signal = setupSignalRoutes(client, {
      me: myLoginId.value,
      subscribeDest: '/user/queue/signals',
      onSignal: handleSignalMessage,
    })

    subscribeChat()
    await loadHistory()

    if (shouldInitiate.value) {
      await createOffer()
    }
  }

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers, frame.body)
    connectionError.value =
      frame.headers?.message || '시그널 서버와 통신하는 중 오류가 발생했습니다.'
    connecting.value = false
  }

  client.onWebSocketError = (event) => {
    console.error('WebSocket error', event)
    connectionError.value = '시그널 서버와 연결할 수 없습니다.'
    connecting.value = false
  }

  client.activate()
}

async function setupCall() {
  if (!auth.isAuthenticated) {
    router.replace({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  if (!ensureValidRoute()) {
    return
  }

  await nextTick()
  await preparePeerConnection()
  await acquireMedia()
  startStomp()
}

function toggleMute() {
  if (!localStream) return
  muted.value = !muted.value
  localStream.getAudioTracks().forEach((track) => {
    track.enabled = !muted.value
  })
}

function toggleCamera() {
  if (!localStream) return
  cameraOff.value = !cameraOff.value
  localStream.getVideoTracks().forEach((track) => {
    track.enabled = !cameraOff.value
  })
}

function stopMedia() {
  if (localStream) {
    localStream.getTracks().forEach((track) => track.stop())
    localStream = null
  }
}

function cleanupConnections() {
  try {
    chatSubscription?.unsubscribe?.()
  } catch (err) {
    console.warn('Failed to unsubscribe from chat topic', err)
  }
  try {
    signal?.sub?.unsubscribe?.()
  } catch (err) {
    console.warn('Failed to unsubscribe from signaling channel', err)
  }
  try {
    client?.deactivate?.()
  } catch (err) {
    console.warn('Failed to deactivate STOMP client', err)
  }
  try {
    pc?.close?.()
  } catch (err) {
    console.warn('Failed to close peer connection', err)
  }
  stopMedia()
}

function leaveCall() {
  cleanupConnections()
  router.replace({ name: 'match' })
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  friendsStore.fetch()
  setupCall().catch((err) => {
    console.error('Failed to start call', err)
    cleanupConnections()
  })
})

onBeforeUnmount(() => {
  cleanupConnections()
})

watch(partnerLoginId, (value) => {
  if (value && client?.connected && shouldInitiate.value) {
    createOffer()
  }
})

watch(targetLang, () => {
  if (translateEnabled.value) {
    chatMessages.value.forEach((message) => {
      message.translatedText = null
      ensureTranslated(message)
    })
  }
})
</script>

<style scoped>
.video-chat-card {
  min-height: 520px;
}

.video-stage {
  position: relative;
  width: 100%;
  min-height: 360px;
  background-color: #f5f5f5;
  border-radius: 12px;
  overflow: hidden;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.local-video {
  position: absolute;
  bottom: 1rem;
  right: 1rem;
  width: 30%;
  max-width: 220px;
  border: 2px solid white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  object-fit: cover;
}

.video-overlay {
  position: absolute;
  inset: 0;
  background-color: rgba(255, 255, 255, 0.7);
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.control-buttons {
  display: flex;
  align-items: center;
  margin-top: 12px;
}

.chat-panel {
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 12px;
  padding: 16px;
  background-color: #fff;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  margin-bottom: 12px;
  max-height: 400px;
}

.chat-message {
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
}

.chat-message.mine {
  align-items: flex-end;
}

.chat-message .meta {
  font-size: 0.75rem;
  color: rgba(0, 0, 0, 0.54);
  margin-bottom: 4px;
}

.chat-message.mine .bubble {
  background-color: #f48fb1;
  color: #fff;
}

.bubble {
  padding: 10px 12px;
  background-color: rgba(0, 0, 0, 0.05);
  border-radius: 10px;
  max-width: 100%;
  word-break: break-word;
}

.translated {
  margin-top: 6px;
  font-size: 0.8rem;
  color: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
}

.file-bubble {
  background-color: rgba(0, 0, 0, 0.05);
}

.chat-image {
  max-width: 100%;
  border-radius: 8px;
}

.file-link {
  display: inline-flex;
  align-items: center;
  color: inherit;
  text-decoration: none;
}

.chat-input {
  display: flex;
  align-items: center;
  gap: 12px;
}

.lang-select {
  min-width: 110px;
}

@media (max-width: 960px) {
  .chat-messages {
    max-height: 280px;
  }
}

@media (max-width: 600px) {
  .local-video {
    width: 40%;
  }
}
</style>

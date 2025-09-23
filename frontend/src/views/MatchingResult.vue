<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="10">
        <v-card class="pa-6">
          <v-row class="align-center mb-6" v-if="isMatched">
            <v-avatar size="48" class="me-3">
              <v-img :src="partnerAvatarFallback" alt="partner" />
            </v-avatar>
            <div>
              <div class="text-h6 text-pink-darken-2">{{ partnerNameDisplay }}님과 매칭되었습니다</div>
              <div class="text-caption text-medium-emphasis">{{ statusMessage }}</div>
            </div>
          </v-row>
          <v-row class="align-center mb-6" v-else>
            <div class="text-h6 text-pink-darken-2">매칭 대기 중</div>
            <div class="text-caption text-medium-emphasis ms-4">{{ waitingStatusText }}</div>
          </v-row>

          <v-row>
            <v-col cols="12" md="9">
              <div v-if="isMatched" class="video-wrapper">
                <video ref="remoteVideo" class="remote-video" autoplay playsinline></video>
                <video ref="localVideo" class="local-video" muted autoplay playsinline></video>
                <div v-if="!hasRemoteStream" class="video-overlay d-flex align-center justify-center">
                  <v-progress-circular indeterminate color="pink" />
                </div>
              </div>
              <div
                v-else
                class="rounded-lg bg-pink-lighten-5 d-flex align-center justify-center media-placeholder"
              >
                <div class="text-subtitle-1">{{ waitingStatusText }}</div>
              </div>
            </v-col>
            <v-col cols="12" md="3">
              <v-card variant="outlined" class="pa-4 h-100 chat-wrapper d-flex flex-column">
                <ChatPanel
                  class="flex-grow-1"
                  :partner="partnerNameDisplay"
                  :messages="chatMessages"
                  :sending="isSendingChat"
                  :uploading="isUploadingFile"
                  :on-send="handleSendChatMessage"
                  :on-send-file="handleUploadFile"
                />
              </v-card>
            </v-col>
          </v-row>

          <div
            class="d-flex justify-center gap-4 mt-6"
            v-if="isMatched && !decisionFinalized"
          >
            <v-btn
              color="pink"
              variant="tonal"
              :loading="actionLoading.accept"
              :disabled="acceptDisabled"
              @click="acceptMatch"
            >
              수락
            </v-btn>
            <v-btn
              color="grey"
              variant="outlined"
              :loading="actionLoading.decline"
              :disabled="declineDisabled"
              @click="declineMatch"
            >
              거절
            </v-btn>
          </div>
          <div class="text-center text-caption mt-4" v-if="statusMessage">
            {{ statusMessage }}
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, reactive } from 'vue'
import { useRouter } from 'vue-router'
import ChatPanel from '../components/ChatPanel.vue'
import defaultAvatar from '../assets/default-avatar.svg'
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'
import { setupChat } from '../services/chat'
import { useAuthStore } from '../stores/auth'
import { useMatchStore } from '../stores/match'
import api from '../services/api'

const router = useRouter()
const auth = useAuthStore()
const matchStore = useMatchStore()

const localVideo = ref(null)
const remoteVideo = ref(null)
const localStream = ref(null)
const localMediaErrorMessage = ref('')
const hasRemoteStream = ref(false)
const chatMessages = ref([])
const isSendingChat = ref(false)
const isUploadingFile = ref(false)
const actionLoading = reactive({ accept: false, decline: false })

const partnerAvatarFallback = defaultAvatar

const meLoginId = computed(() => auth.user?.loginId || auth.user?.login_id || auth.user?.loginID || null)
const myNickname = computed(() => auth.user?.nickname || auth.user?.nickName || auth.user?.nick_name || '')
const partnerNameDisplay = computed(() => matchStore.partnerNickName || '상대 대기 중')
const isMatched = computed(() => matchStore.isMatched)
const waitingStatusText = computed(() =>
  matchStore.waitingCount > 0
    ? '매칭 중입니다. 잠시만 기다려주세요.'
    : '현재 대기 중인 사용자가 없습니다.'
)
const statusMessage = computed(() => {
  if (localMediaErrorMessage.value) {
    return localMediaErrorMessage.value
  }
  return (
    matchStore.statusMessage ||
    (isMatched.value ? '상대의 준비를 기다리는 중입니다.' : waitingStatusText.value)
  )
})
const decisionFinalized = computed(() => matchStore.sessionClosed || matchStore.bothConfirmed)
const acceptDisabled = computed(
  () =>
    !matchStore.requestId ||
    matchStore.myDecision === 'ACCEPTED' ||
    matchStore.sessionClosed ||
    actionLoading.accept ||
    actionLoading.decline
)
const declineDisabled = computed(
  () =>
    !matchStore.requestId ||
    matchStore.myDecision === 'ACCEPTED' ||
    matchStore.sessionClosed ||
    actionLoading.decline
)

const shouldInitialize = computed(() => !!matchStore.state)

const client = ref(null)
const connected = ref(false)
let matchSubscription = null
let signalRoute = null
let pc = null
let audioTransceiver = null
let videoTransceiver = null
const offerCreated = ref(false)
let chatRoute = null
let chatRoomId = null
const STATUS_POLL_INTERVAL = 2500
let statusPollTimer = null

if (!shouldInitialize.value) {
  router.replace('/match')
}

function stopStatusPolling() {
  if (statusPollTimer) {
    clearInterval(statusPollTimer)
    statusPollTimer = null
  }
}

async function requestStatusRefresh() {
  if (!matchStore.requestId || isMatched.value || matchStore.sessionClosed) {
    return
  }
  try {
    const { data } = await api.get(`/match/requests/${matchStore.requestId}`)
    if (data) {
      matchStore.setFromStartResponse(data)
      if (data.state === 'MATCHED') {
        stopStatusPolling()
        ensureChatRoute()
        void ensurePeerConnection()
      }
    }
  } catch (error) {
    console.error('매칭 상태 조회 실패', error)
  }
}

function ensureStatusPolling(immediate = false) {
  if (!matchStore.requestId || isMatched.value || matchStore.sessionClosed) {
    stopStatusPolling()
    return
  }
  if (!statusPollTimer) {
    statusPollTimer = setInterval(() => {
      if (!matchStore.requestId || isMatched.value || matchStore.sessionClosed) {
        stopStatusPolling()
        return
      }
      void requestStatusRefresh()
    }, STATUS_POLL_INTERVAL)
  }
  if (immediate) {
    void requestStatusRefresh()
  }
}

async function initLocalMedia() {
  if (typeof window === 'undefined') {
    return
  }

  const isSecure = window.isSecureContext
  const hasNavigator = typeof navigator !== 'undefined'
  const mediaDevices = hasNavigator ? navigator.mediaDevices : undefined
  const canUseGetUserMedia = !!(mediaDevices && typeof mediaDevices.getUserMedia === 'function')

  if (!isSecure) {
    const message = '보안 연결(HTTPS)에서 접속해야 카메라와 마이크를 사용할 수 있습니다.'
    localMediaErrorMessage.value = message
    matchStore.statusMessage = message
    console.warn('HTTPS가 아닌 연결에서는 getUserMedia를 사용할 수 없습니다.')
    return
  }

  if (!canUseGetUserMedia) {
    const message = '이 브라우저에서는 카메라 또는 마이크 접근을 지원하지 않습니다. 다른 브라우저에서 시도해 주세요.'
    localMediaErrorMessage.value = message
    matchStore.statusMessage = message
    console.warn('navigator.mediaDevices.getUserMedia가 지원되지 않습니다.')
    return
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    localStream.value = stream
    if (localVideo.value) {
      localVideo.value.srcObject = stream
    }
    const previousErrorMessage = localMediaErrorMessage.value
    localMediaErrorMessage.value = ''
    if (previousErrorMessage && matchStore.statusMessage === previousErrorMessage) {
      matchStore.statusMessage = ''
    }
    syncLocalTracksToPeerConnection()
  } catch (error) {
    console.error('로컬 미디어 초기화 실패', error)
    localStream.value = null
    if (localVideo.value) {
      localVideo.value.srcObject = null
    }
    let message = '카메라 또는 마이크 접근이 차단되었습니다. 브라우저 설정을 확인해 주세요.'
    if (error && typeof error === 'object') {
      const errorName = error.name
      if (errorName === 'NotAllowedError' || errorName === 'SecurityError') {
        message = '브라우저에서 카메라 또는 마이크 접근이 차단되었습니다. 권한을 허용한 뒤 다시 시도하세요.'
      } else if (errorName === 'NotFoundError' || errorName === 'OverconstrainedError') {
        message = '연결된 카메라 또는 마이크를 찾을 수 없습니다. 장치를 확인한 뒤 다시 시도하세요.'
      } else if (errorName === 'NotReadableError') {
        message = '다른 프로그램이 카메라 또는 마이크를 사용 중입니다. 사용 중인 앱을 종료하고 다시 시도하세요.'
      }
    }
    localMediaErrorMessage.value = message
    matchStore.statusMessage = message
  }
}

function syncLocalTracksToPeerConnection() {
  if (!pc) {
    return
  }

  const stream = localStream.value
  const audioTrack =
    stream && typeof stream.getAudioTracks === 'function'
      ? stream.getAudioTracks()[0] || null
      : null
  const videoTrack =
    stream && typeof stream.getVideoTracks === 'function'
      ? stream.getVideoTracks()[0] || null
      : null

  if (!audioTransceiver) {
    audioTransceiver = pc.addTransceiver('audio', { direction: audioTrack ? 'sendrecv' : 'recvonly' })
  }
  if (!videoTransceiver) {
    videoTransceiver = pc.addTransceiver('video', { direction: videoTrack ? 'sendrecv' : 'recvonly' })
  }

  if (audioTransceiver) {
    audioTransceiver.direction = audioTrack ? 'sendrecv' : 'recvonly'
    const sender = audioTransceiver.sender
    if (sender) {
      sender.replaceTrack(audioTrack).catch((replaceError) => {
        console.error('로컬 오디오 트랙 동기화 실패', replaceError)
      })
    }
  }

  if (videoTransceiver) {
    videoTransceiver.direction = videoTrack ? 'sendrecv' : 'recvonly'
    const sender = videoTransceiver.sender
    if (sender) {
      sender.replaceTrack(videoTrack).catch((replaceError) => {
        console.error('로컬 비디오 트랙 동기화 실패', replaceError)
      })
    }
  }
}

watch(localVideo, (element) => {
  if (element && localStream.value) {
    element.srcObject = localStream.value
  }
})

watch(localStream, (stream) => {
  if (localVideo.value) {
    localVideo.value.srcObject = stream || null
  }
  syncLocalTracksToPeerConnection()
})

watch(
  () => [matchStore.requestId, matchStore.state, matchStore.sessionClosed],
  ([requestId, state, sessionClosed], previous) => {
    if (!requestId || sessionClosed || state === 'MATCHED') {
      stopStatusPolling()
      return
    }
    const prevRequestId = previous ? previous[0] : null
    const prevState = previous ? previous[1] : null
    const immediate = !previous || requestId !== prevRequestId || prevState === 'MATCHED'
    ensureStatusPolling(immediate)
  },
  { immediate: true }
)

function handleMatchMessage(frame) {
  try {
    const payload = JSON.parse(frame.body)
    if (
      payload.eventType === 'MATCH_FOUND' &&
      matchStore.requestId &&
      payload.myRequestId &&
      payload.myRequestId !== matchStore.requestId
    ) {
      teardownPeerConnection()
      offerCreated.value = false
      hasRemoteStream.value = false
    }
    if (payload.eventType === 'MATCH_FOUND') {
      stopStatusPolling()
      offerCreated.value = false
      hasRemoteStream.value = false
      chatMessages.value = []
    }
    matchStore.applyMatchEvent(payload)
    ensureChatRoute()
    void ensurePeerConnection()
  } catch (error) {
    console.error('매칭 이벤트 처리 실패', error)
  }
}

function handleIncomingChatMessage(payload) {
  if (!payload) {
    return
  }
  try {
    const sentAt = payload.sentAt ? new Date(payload.sentAt) : new Date()
    let sizeValue = null
    if (typeof payload.sizeBytes === 'number') {
      sizeValue = payload.sizeBytes
    } else if (payload.sizeBytes !== null && payload.sizeBytes !== undefined) {
      const parsed = Number(payload.sizeBytes)
      if (Number.isFinite(parsed)) {
        sizeValue = parsed
      }
    }
    chatMessages.value.push({
      id: `${payload.roomId ?? ''}-${sentAt.getTime()}-${Math.random().toString(36).slice(2, 8)}`,
      roomId: payload.roomId,
      senderNickName: payload.senderNickName,
      content: payload.content ?? '',
      translatedContent: payload.translatedContent ?? '',
      contentType: payload.contentType ?? 'TEXT',
      fileName: payload.fileName ?? '',
      fileUrl: payload.fileUrl ?? '',
      mimeType: payload.mimeType ?? '',
      sizeBytes: Number.isFinite(sizeValue) ? sizeValue : null,
      sentAt,
      fromMe: !!payload.senderNickName && payload.senderNickName === myNickname.value,
    })
  } catch (error) {
    console.error('채팅 메시지 처리 실패', error)
  }
}

function ensureChatRoute() {
  if (!connected.value || !client.value || !matchStore.roomId || !isMatched.value) {
    if (!matchStore.roomId) {
      chatMessages.value = []
    }
    teardownChatRoute()
    return
  }

  if (chatRoute && chatRoomId === matchStore.roomId) {
    return
  }

  const shouldReset = chatRoomId !== matchStore.roomId
  teardownChatRoute()
  if (shouldReset) {
    chatMessages.value = []
  }
  chatRoute = setupChat(client.value, matchStore.roomId, {
    onChat: handleIncomingChatMessage,
  })
  chatRoomId = matchStore.roomId
}

function teardownChatRoute() {
  if (chatRoute?.unsubscribe) {
    chatRoute.unsubscribe()
  } else if (chatRoute?.subscription?.unsubscribe) {
    chatRoute.subscription.unsubscribe()
  }
  chatRoute = null
  chatRoomId = null
}

async function ensurePeerConnection() {
  if (!shouldInitialize.value || !connected.value || !isMatched.value) {
    return
  }
  if (!matchStore.partnerLoginId || !meLoginId.value) {
    return
  }

  if (!pc) {
    pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] })
    pc.ontrack = (event) => {
      const [stream] = event.streams
      if (stream && remoteVideo.value) {
        remoteVideo.value.srcObject = stream
        hasRemoteStream.value = true
      }
    }
    pc.onicecandidate = (event) => {
      if (event.candidate && signalRoute) {
        signalRoute.sendSignal({
          type: 'ice-candidate',
          receiverLoginId: matchStore.partnerLoginId,
          data: event.candidate,
        })
      }
    }
    pc.onconnectionstatechange = () => {
      if (pc && ['disconnected', 'failed', 'closed'].includes(pc.connectionState)) {
        hasRemoteStream.value = false
      }
    }
  }

  syncLocalTracksToPeerConnection()

  if (!signalRoute) {
    signalRoute = setupSignalRoutes(client.value, {
      me: meLoginId.value,
      onSignal: handleSignal,
    })
  }

  if (matchStore.shouldCreateOffer && !offerCreated.value) {
    await createOffer()
  }
}

async function createOffer() {
  if (!pc || !signalRoute || !matchStore.partnerLoginId) {
    return
  }
  try {
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    signalRoute.sendSignal({
      type: 'offer',
      receiverLoginId: matchStore.partnerLoginId,
      data: offer,
    })
    offerCreated.value = true
  } catch (error) {
    console.error('WebRTC Offer 생성 실패', error)
  }
}

async function handleSignal(message) {
  if (!message) {
    return
  }
  if (!pc) {
    await ensurePeerConnection()
  }
  if (!pc) {
    return
  }

  try {
    if (message.type === 'offer') {
      offerCreated.value = true
      await pc.setRemoteDescription(message.data)
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      signalRoute?.sendSignal({
        type: 'answer',
        receiverLoginId: matchStore.partnerLoginId,
        data: answer,
      })
    } else if (message.type === 'answer') {
      offerCreated.value = true
      await pc.setRemoteDescription(message.data)
    } else if (message.type === 'ice-candidate' && message.data) {
      await pc.addIceCandidate(message.data)
    }
  } catch (error) {
    console.error('시그널 처리 실패', error)
  }
}

function teardownPeerConnection() {
  if (signalRoute?.sub) {
    signalRoute.sub.unsubscribe()
  }
  signalRoute = null
  if (pc) {
    pc.close()
    pc = null
  }
  audioTransceiver = null
  videoTransceiver = null
  offerCreated.value = false
  if (remoteVideo.value) {
    remoteVideo.value.srcObject = null
  }
  hasRemoteStream.value = false
}

async function handleSendChatMessage(text) {
  if (!text) {
    return
  }
  if (!client.value || !connected.value || !matchStore.roomId) {
    throw new Error('채팅방이 아직 준비되지 않았습니다.')
  }

  try {
    isSendingChat.value = true
    if (chatRoute?.sendChat) {
      chatRoute.sendChat({ content: text })
    } else {
      client.value.publish({
        destination: `/app/chat.sendMessage/${matchStore.roomId}`,
        body: JSON.stringify({ roomId: matchStore.roomId, content: text }),
      })
    }
  } catch (error) {
    console.error('채팅 메시지 전송 실패', error)
    throw error instanceof Error ? error : new Error('채팅 메시지 전송에 실패했습니다.')
  } finally {
    isSendingChat.value = false
  }
}

async function handleUploadFile(file) {
  if (!file) {
    return
  }
  if (!matchStore.roomId) {
    throw new Error('채팅방이 아직 준비되지 않았습니다.')
  }

  const formData = new FormData()
  formData.append('file', file)

  try {
    isUploadingFile.value = true
    await api.post(`/chat/rooms/${matchStore.roomId}/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  } catch (error) {
    console.error('파일 업로드 실패', error)
    throw error instanceof Error ? error : new Error('파일 업로드 중 오류가 발생했습니다.')
  } finally {
    isUploadingFile.value = false
  }
}

async function acceptMatch() {
  if (acceptDisabled.value) {
    return
  }
  actionLoading.accept = true
  try {
    const { data } = await api.post(`/match/requests/${matchStore.requestId}/accept`)
    matchStore.setMyDecision('ACCEPTED', data?.message, data?.bothAccepted)
  } catch (error) {
    console.error('매칭 수락 실패', error)
    window.alert(error?.response?.data || '매칭 수락 중 오류가 발생했습니다.')
  } finally {
    actionLoading.accept = false
  }
}

async function declineMatch() {
  if (declineDisabled.value) {
    return
  }
  actionLoading.decline = true
  try {
    const { data } = await api.post(`/match/requests/${matchStore.requestId}/decline`)
    matchStore.setMyDecision('DECLINED', data?.message, false)
    teardownPeerConnection()
  } catch (error) {
    console.error('매칭 거절 실패', error)
    window.alert(error?.response?.data || '매칭 거절 중 오류가 발생했습니다.')
  } finally {
    actionLoading.decline = false
  }
}

watch(
  () => [connected.value, isMatched.value, matchStore.partnerLoginId, matchStore.shouldCreateOffer],
  () => {
    if (!connected.value) {
      return
    }
    if (!isMatched.value) {
      teardownPeerConnection()
      return
    }
    void ensurePeerConnection()
  }
)

watch(
  () => matchStore.sessionClosed,
  (closed) => {
    if (closed) {
      stopStatusPolling()
      teardownPeerConnection()
      teardownChatRoute()
      chatMessages.value = []
    }
  }
)

watch(
  () => [connected.value, matchStore.roomId],
  () => {
    if (!connected.value || !matchStore.roomId) {
      if (!matchStore.roomId) {
        chatMessages.value = []
      }
      teardownChatRoute()
      return
    }
    ensureChatRoute()
  }
)

watch(
  () => matchStore.state,
  (state) => {
    if (state !== 'MATCHED') {
      teardownChatRoute()
      chatMessages.value = []
    }
  }
)

onMounted(async () => {
  if (!shouldInitialize.value) {
    return
  }

  await initLocalMedia()

  client.value = createStompClient(auth.token)
  client.value.onConnect = () => {
    connected.value = true
    matchSubscription = client.value.subscribe('/user/queue/match-results', handleMatchMessage)
    ensureChatRoute()
    void ensurePeerConnection()
  }
  client.value.onDisconnect = () => {
    connected.value = false
    matchSubscription?.unsubscribe()
    matchSubscription = null
    teardownPeerConnection()
    teardownChatRoute()
  }
  client.value.activate()

  if (!isMatched.value) {
    ensureStatusPolling(true)
  }

  if (isMatched.value) {
    ensureChatRoute()
    void ensurePeerConnection()
  }
})

onBeforeUnmount(() => {
  stopStatusPolling()
  matchSubscription?.unsubscribe()
  matchSubscription = null
  if (signalRoute?.sub) {
    signalRoute.sub.unsubscribe()
  }
  signalRoute = null
  client.value?.deactivate?.()
  teardownPeerConnection()
  teardownChatRoute()
  if (localStream.value) {
    localStream.value.getTracks().forEach((track) => track.stop())
  }
})
</script>

<style scoped>
.video-wrapper {
  position: relative;
  background: #000;
  border-radius: 16px;
  overflow: hidden;
  min-height: 360px;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #000;
}

.local-video {
  position: absolute;
  right: 16px;
  bottom: 16px;
  width: 180px;
  height: 120px;
  object-fit: cover;
  border-radius: 12px;
  border: 2px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  background: #000;
}

.video-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
}

.media-placeholder {
  min-height: 360px;
  border-radius: 16px;
}

.chat-wrapper {
  max-height: 380px;
}

@media (max-width: 960px) {
  .local-video {
    width: 140px;
    height: 96px;
  }
}
</style>

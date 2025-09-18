<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="10">
        <v-card class="pa-6">
          <v-row class="align-center mb-6">
            <v-avatar size="40" class="me-3">
              <v-img :src="partnerAvatar" alt="avatar" />
            </v-avatar>
            <div>
              <div class="text-h6 text-pink-darken-2">
                <template v-if="isMatched">{{ partnerName }}님과 매칭되었습니다</template>
                <template v-else>매칭을 준비 중입니다</template>
              </div>
              <div class="text-caption text-medium-emphasis">{{ sessionStatus }}</div>
            </div>
          </v-row>

          <v-row>
            <v-col cols="12" md="9">
              <div
                  v-if="chatEnabled"
                  class="rounded-lg bg-grey-lighten-2 media-wrapper d-flex align-center justify-center overflow-hidden"
              >
                <video ref="remoteVideo" autoplay playsinline class="remote-video"></video>
                <video ref="localVideo" autoplay muted playsinline class="local-video"></video>
              </div>
              <v-sheet
                  v-else-if="isMatched"
                  class="rounded-lg bg-pink-lighten-5 d-flex align-center justify-center media-wrapper"
              >
                <div class="text-subtitle-1 text-center px-4">{{ matchPreviewMessage }}</div>
              </v-sheet>
              <v-img
                  v-else-if="mediaUrl"
                  :src="mediaUrl"
                  class="rounded-lg bg-grey-lighten-2 media-wrapper"
                  cover
              >
                <template #placeholder>
                  <v-row class="fill-height ma-0" align="center" justify="center">
                    <v-progress-circular indeterminate color="pink" />
                  </v-row>
                </template>
              </v-img>
              <div
                  v-else
                  class="rounded-lg bg-pink-lighten-5 d-flex align-center justify-center media-wrapper"
              >
                <div class="text-subtitle-1">매칭 이미지 / 영상이 없습니다.</div>
              </div>
            </v-col>
            <v-col cols="12" md="3">
              <v-card variant="outlined" class="pa-4 h-100 chat-wrapper d-flex flex-column">
                <ChatPanel
                    class="flex-grow-1"
                    :partner-login-id="partner || ''"
                    :partner-user-pid="match.partnerUserPid"
                    :room-id="match.roomId"
                    :stomp-client="stompClient"
                    :connected="clientConnected"
                    :enabled="chatEnabled"
                />
              </v-card>
            </v-col>
          </v-row>

          <div
              v-if="showDecisionButtons || showCancelButton"
              class="d-flex justify-center gap-4 mt-6"
          >
            <template v-if="showDecisionButtons">
              <v-btn
                  color="pink"
                  variant="tonal"
                  :disabled="!canAccept"
                  @click="acceptMatch"
              >수락</v-btn>
              <v-btn
                  color="grey"
                  variant="outlined"
                  :disabled="!canDecline"
                  @click="declineMatch"
              >{{ declineLabel }}</v-btn>
            </template>
            <v-btn
                v-else-if="showCancelButton"
                color="grey"
                variant="outlined"
                :disabled="!canDecline"
                @click="declineMatch"
            >{{ declineLabel }}</v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import api from '../services/api'
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'
import { useAuthStore } from '../stores/auth'
import { useMatchStore } from '../stores/match'
import ChatPanel from '../components/ChatPanel.vue'

const auth = useAuthStore()
const match = useMatchStore()
const router = useRouter()

const localVideo = ref(null)
const remoteVideo = ref(null)
const mediaUrl = ref('')
const clientConnected = ref(false)
const hasSentOffer = ref(false)
const signalRef = ref(null)
const stompClient = shallowRef(null)

let client = null
let pc = null
let localStream = null
const subs = []
let handledAuthFailure = false

function handleAuthFailure(message) {
  if (handledAuthFailure) {
    return
  }
  handledAuthFailure = true

  const alertMessage = message || '세션이 만료되었습니다. 다시 로그인해 주세요.'

  subs.splice(0).forEach((sub) => {
    try {
      sub?.unsubscribe?.()
    } catch (error) {
      console.warn('구독 해제 중 오류 발생:', error)
    }
  })

  try {
    client?.deactivate?.()
  } catch (error) {
    console.warn('STOMP 클라이언트 종료 중 오류 발생:', error)
  }

  client = null
  stompClient.value = null
  clientConnected.value = false
  signalRef.value = null
  cleanupPeerConnection()

  if (typeof window !== 'undefined' && typeof window.alert === 'function') {
    window.alert(alertMessage)
  } else {
    console.warn('Alert skipped (non-browser environment):', alertMessage)
  }

  auth.logout()
  router.replace({ name: 'login', query: { force: 1 } }).catch(() => {})
}

const me = computed(() => auth.user?.loginId ?? null)
const partner = computed(() => match.partnerLoginId ?? null)
const partnerName = computed(() => match.partnerNickName ?? match.partnerLoginId ?? '상대방')
const partnerAvatar = computed(() => {
  const seed = partnerName.value?.trim()
  return seed
    ? `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(seed)}`
    : 'https://via.placeholder.com/150'
})
const isMatched = computed(() => match.isMatched)
const chatEnabled = computed(() => match.bothConfirmed && !match.sessionClosed && !!match.roomId)
const connectionReady = computed(() =>
  clientConnected.value && chatEnabled.value && !!partner.value && !!me.value && !!signalRef.value
)
const sessionStatus = computed(() => {
  if (match.statusMessage) return match.statusMessage
  if (match.sessionClosed) return '매칭이 종료되었습니다.'
  if (match.partnerDecision === 'DECLINED') return '상대방이 매칭을 거절했습니다.'
  if (match.myDecision === 'DECLINED') return '매칭을 거절했습니다.'
  if (match.bothConfirmed) return '서로 매칭을 확정했습니다. 대화를 시작하세요!'
  if (match.isMatched) {
    if (match.myDecision === 'ACCEPTED') {
      return '매칭을 수락했습니다. 상대방의 응답을 기다리고 있습니다.'
    }
    if (match.partnerDecision === 'ACCEPTED') {
      return '상대방이 매칭을 수락했습니다. 수락하면 대화를 시작할 수 있어요.'
    }
    return '새로운 상대를 찾았습니다. 수락 후 대화를 시작하세요.'
  }
  if (match.isWaiting) {
    const waiters = match.waitingCount || 0
    return waiters > 0
      ? `대기 중... 현재 ${waiters}명이 기다리고 있습니다.`
      : '대기열에서 상대를 찾고 있습니다.'
  }
  return '매칭 정보를 불러오는 중입니다.'
})
const matchPreviewMessage = computed(() => {
  if (match.sessionClosed) {
    return '매칭이 종료되었습니다.'
  }
  if (!match.isMatched) {
    return '랜덤 매칭이 시작되면 영상이 여기에 표시됩니다.'
  }
  if (match.myDecision === 'ACCEPTED' && match.partnerDecision !== 'ACCEPTED') {
    return '상대방의 응답을 기다리고 있습니다. 잠시만 기다려 주세요.'
  }
  if (match.partnerDecision === 'ACCEPTED' && match.myDecision !== 'ACCEPTED') {
    return '상대방이 매칭을 수락했습니다. 수락 버튼을 눌러 대화를 시작해보세요.'
  }
  return '수락 버튼을 누르면 영상 채팅이 시작됩니다.'
})
const showDecisionButtons = computed(() => match.isMatched && !match.sessionClosed && !match.bothConfirmed)
const showCancelButton = computed(() => match.isWaiting && !match.sessionClosed)
const canAccept = computed(() => showDecisionButtons.value && match.myDecision !== 'ACCEPTED')
const canDecline = computed(() => (showDecisionButtons.value || showCancelButton.value) && match.myDecision !== 'DECLINED')
const declineLabel = computed(() => (match.isWaiting ? '대기 취소' : '거절'))

const confirmAction = (message) => {
  if (typeof window !== 'undefined' && typeof window.confirm === 'function') {
    return window.confirm(message)
  }

  console.info('Confirm dialog skipped in non-browser environment:', message)
  return true
}

function ensurePeerConnection() {
  if (pc) {
    return pc
  }

  pc = new RTCPeerConnection({
    iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
  })

  pc.ontrack = (event) => {
    const [stream] = event.streams || []
    if (remoteVideo.value && stream) {
      remoteVideo.value.srcObject = stream
    }
  }

  pc.onicecandidate = (event) => {
    if (!event.candidate || !signalRef.value || !partner.value) return
    signalRef.value.sendSignal({
      type: 'ice-candidate',
      receiverLoginId: partner.value,
      data: event.candidate
    })
  }

  return pc
}

async function ensureLocalMedia() {
  if (localStream || typeof navigator === 'undefined') {
    return localStream
  }

  if (!navigator.mediaDevices?.getUserMedia) {
    console.warn('Media devices are not available in this environment.')
    return null
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    localStream = stream
    const pcInstance = ensurePeerConnection()
    stream.getTracks().forEach((track) => pcInstance.addTrack(track, stream))
    if (localVideo.value) {
      localVideo.value.srcObject = stream
    }
    return stream
  } catch (error) {
    console.error('미디어 장치 접근 실패:', error)
    return null
  }
}

async function maybeSendOffer() {
  if (!connectionReady.value || !chatEnabled.value || !match.shouldCreateOffer || hasSentOffer.value) {
    return
  }

  try {
    const pcInstance = ensurePeerConnection()
    await ensureLocalMedia()
    const offer = await pcInstance.createOffer()
    await pcInstance.setLocalDescription(offer)
    signalRef.value?.sendSignal({
      type: 'offer',
      receiverLoginId: partner.value,
      data: offer
    })
    hasSentOffer.value = true
  } catch (error) {
    console.error('Failed to create or send offer:', error)
  }
}

async function handleSignalMessage(message = {}) {
  if (!message?.type || !partner.value) {
    return
  }

  try {
    const pcInstance = ensurePeerConnection()
    await ensureLocalMedia()

    if (message.type === 'offer') {
      await pcInstance.setRemoteDescription(message.data)
      const answer = await pcInstance.createAnswer()
      await pcInstance.setLocalDescription(answer)
      signalRef.value?.sendSignal({
        type: 'answer',
        receiverLoginId: partner.value,
        data: answer
      })
      hasSentOffer.value = true
    } else if (message.type === 'answer') {
      await pcInstance.setRemoteDescription(message.data)
      hasSentOffer.value = true
    } else if (message.type === 'ice-candidate' && message.data) {
      try {
        await pcInstance.addIceCandidate(message.data)
      } catch (error) {
        console.warn('Failed to add received ICE candidate:', error)
      }
    }
  } catch (error) {
    console.error('Error handling signaling message:', error)
  }
}

function handleMatchEvent(frame) {
  if (!frame?.body) return
  try {
    const payload = JSON.parse(frame.body)
    match.applyMatchEvent(payload)
    if (payload.eventType === 'MATCH_CANCELLED' || payload.eventType === 'PARTNER_DECLINED') {
      cleanupPeerConnection()
    }
  } catch (error) {
    console.error('Failed to process match event message:', error)
  }
}

function cleanupPeerConnection() {
  if (localStream) {
    localStream.getTracks().forEach((track) => {
      try { track.stop() } catch {}
    })
    localStream = null
  }

  if (localVideo.value) {
    localVideo.value.srcObject = null
  }
  if (remoteVideo.value) {
    remoteVideo.value.srcObject = null
  }

  if (pc) {
    try { pc.close() } catch {}
    pc = null
  }

  hasSentOffer.value = false
}

async function acceptMatch() {
  if (!showDecisionButtons.value || !canAccept.value || !match.requestId) {
    return
  }

  if (!confirmAction('매칭을 수락하시겠습니까?')) {
    return
  }

  try {
    const { data } = await api.post(`/match/requests/${match.requestId}/accept`)
    match.setMyDecision(
      'ACCEPTED',
      data?.message || '매칭을 수락했습니다.',
      {
        bothAccepted: !!data?.bothAccepted,
        shouldCreateOffer: data?.shouldCreateOffer ?? null
      }
    )
  } catch (error) {
    console.error('매칭 수락 실패:', error)
    alert(error?.response?.data?.message || '매칭 수락에 실패했습니다.')
  }
}

async function declineMatch() {
  if ((!showDecisionButtons.value && !showCancelButton.value) || !canDecline.value || !match.requestId) {
    return
  }

  const wasWaiting = match.isWaiting
  const confirmMessage = wasWaiting
    ? '대기열에서 나가시겠습니까?'
    : '매칭을 거절하시겠습니까?'

  if (!confirmAction(confirmMessage)) {
    return
  }

  try {
    const { data } = await api.post(`/match/requests/${match.requestId}/decline`)
    match.setMyDecision(
      'DECLINED',
      data?.message || (wasWaiting ? '대기열에서 제외되었습니다.' : '매칭을 거절했습니다.'),
      { bothAccepted: false, shouldCreateOffer: false }
    )
    if (!wasWaiting) {
      cleanupPeerConnection()
    }
  } catch (error) {
    console.error('매칭 거절 실패:', error)
    alert(error?.response?.data?.message || '매칭 거절에 실패했습니다.')
  }
}

onMounted(() => {
  if (!auth.token) {
    handleAuthFailure('세션이 만료되었습니다. 다시 로그인해 주세요.')
    return
  }

  if (!match.requestId) {
    alert('진행 중인 매칭이 없습니다. 매칭을 다시 시작해주세요.')
    router.replace({ name: 'match' })
    return
  }

  client = createStompClient(auth.token)
  stompClient.value = client
  client.onConnect = () => {
    clientConnected.value = true

    const matchSub = client.subscribe('/user/queue/match-results', handleMatchEvent)
    subs.push(matchSub)

    const { sub, sendSignal } = setupSignalRoutes(client, {
      me: me.value || '',
      subscribeDest: '/user/queue/signals',
      onSignal: handleSignalMessage
    })
    signalRef.value = { sendSignal }
    subs.push(sub)

    maybeSendOffer()
  }
  client.onDisconnect = () => {
    clientConnected.value = false
  }
  client.onStompError = (frame) => {
    console.error('STOMP error:', frame?.headers, frame?.body)
    const errorMessage = frame?.headers?.message || frame?.body || ''
    if (typeof errorMessage === 'string') {
      const normalized = errorMessage.toLowerCase()
      if (
        normalized.includes('authentication') ||
        normalized.includes('unauthorized') ||
        normalized.includes('failed to send message to executorsubscribablechannel')
      ) {
        handleAuthFailure('인증이 만료되어 연결이 종료되었습니다. 다시 로그인해 주세요.')
      }
    }
  }

  client.activate()
})

watch(connectionReady, (ready) => {
  if (ready) {
    hasSentOffer.value = false
    maybeSendOffer()
  }
})

watch(chatEnabled, (enabled, previous) => {
  if (!enabled && previous) {
    cleanupPeerConnection()
  }
  if (enabled) {
    hasSentOffer.value = false
  }
})

watch(() => match.shouldCreateOffer, (should) => {
  if (should && connectionReady.value) {
    hasSentOffer.value = false
    maybeSendOffer()
  } else if (!should) {
    hasSentOffer.value = false
  }
})

watch(
  () => auth.token,
  (token, previous) => {
    if (!token && previous) {
      handleAuthFailure('세션이 만료되었습니다. 다시 로그인해 주세요.')
    }
  }
)

watch(partner, (loginId, previous) => {
  if (!loginId || loginId === previous) {
    return
  }
  hasSentOffer.value = false
  if (connectionReady.value) {
    maybeSendOffer()
  }
})

watch(() => match.sessionClosed, (closed) => {
  if (closed) {
    cleanupPeerConnection()
  }
})

onBeforeUnmount(() => {
  subs.forEach((sub) => sub?.unsubscribe?.())
  subs.length = 0
  client?.deactivate?.()
  stompClient.value = null
  signalRef.value = null
  cleanupPeerConnection()
})
</script>


<style scoped>
.media-wrapper {
  max-height: 380px;
  min-height: 260px;
  position: relative;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background-color: #000;
}

.local-video {
  position: absolute;
  bottom: 16px;
  right: 16px;
  width: 160px;
  height: 120px;
  border-radius: 8px;
  border: 2px solid rgba(255, 255, 255, 0.8);
  object-fit: cover;
  background-color: rgba(0, 0, 0, 0.6);
}

.chat-wrapper {
  max-height: 380px;
}
</style>
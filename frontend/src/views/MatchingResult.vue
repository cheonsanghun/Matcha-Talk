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
                <ChatPanel class="flex-grow-1" :partner="partnerNameDisplay" />
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
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'
import { useAuthStore } from '../stores/auth'
import { useMatchStore } from '../stores/match'
import api from '../services/api'

const router = useRouter()
const auth = useAuthStore()
const matchStore = useMatchStore()

const localVideo = ref(null)
const remoteVideo = ref(null)
const localStream = ref(null)
const hasRemoteStream = ref(false)
const actionLoading = reactive({ accept: false, decline: false })

const partnerAvatarFallback = 'https://via.placeholder.com/96?text=User'

const meLoginId = computed(() => auth.user?.loginId || auth.user?.login_id || auth.user?.loginID || null)
const partnerNameDisplay = computed(() => matchStore.partnerNickName || '상대 대기 중')
const isMatched = computed(() => matchStore.isMatched)
const waitingStatusText = computed(() =>
  matchStore.waitingCount > 0
    ? '매칭 중입니다. 잠시만 기다려주세요.'
    : '현재 대기 중인 사용자가 없습니다.'
)
const statusMessage = computed(() =>
  matchStore.statusMessage || (isMatched.value ? '상대의 준비를 기다리는 중입니다.' : waitingStatusText.value)
)
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
const offerCreated = ref(false)

if (!shouldInitialize.value) {
  router.replace('/match')
}

async function initLocalMedia() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    localStream.value = stream
    if (localVideo.value) {
      localVideo.value.srcObject = stream
    }
  } catch (error) {
    console.error('로컬 미디어 초기화 실패', error)
    if (!matchStore.statusMessage) {
      matchStore.statusMessage = '카메라 또는 마이크 접근이 차단되었습니다.'
    }
  }
}

watch(localVideo, (element) => {
  if (element && localStream.value) {
    element.srcObject = localStream.value
  }
})

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
      offerCreated.value = false
      hasRemoteStream.value = false
    }
    matchStore.applyMatchEvent(payload)
    void ensurePeerConnection()
  } catch (error) {
    console.error('매칭 이벤트 처리 실패', error)
  }
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
    if (localStream.value) {
      localStream.value.getTracks().forEach((track) => pc.addTrack(track, localStream.value))
    }
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
  offerCreated.value = false
  if (remoteVideo.value) {
    remoteVideo.value.srcObject = null
  }
  hasRemoteStream.value = false
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
      teardownPeerConnection()
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
    void ensurePeerConnection()
  }
  client.value.onDisconnect = () => {
    connected.value = false
    matchSubscription?.unsubscribe()
    matchSubscription = null
    teardownPeerConnection()
  }
  client.value.activate()

  if (isMatched.value) {
    void ensurePeerConnection()
  }
})

onBeforeUnmount(() => {
  matchSubscription?.unsubscribe()
  matchSubscription = null
  if (signalRoute?.sub) {
    signalRoute.sub.unsubscribe()
  }
  signalRoute = null
  client.value?.deactivate?.()
  teardownPeerConnection()
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

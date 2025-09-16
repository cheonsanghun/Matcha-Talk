<template>
  <v-container class="py-8">
    <v-row justify="center">
      <v-col cols="12" md="10" lg="8">
        <v-card class="pa-4">
          <div class="header-row mb-4">
            <div>
              <div class="text-h6 text-pink-darken-2">영상 채팅</div>
              <div class="text-caption text-medium-emphasis">
                {{ partnerNickname ? `${partnerNickname}님과 연결 중` : '상대방을 기다리는 중입니다' }}
              </div>
            </div>
            <v-btn color="grey" variant="tonal" @click="leaveCall">나가기</v-btn>
          </div>

          <v-alert
            v-if="connectionError"
            type="error"
            variant="tonal"
            class="mb-4"
          >
            {{ connectionError }}
          </v-alert>

          <div class="video-stage mb-4">
            <video ref="remoteVideo" autoplay playsinline class="remote-video bg-grey-lighten-3"></video>
            <video ref="localVideo" autoplay muted playsinline class="local-video bg-grey-lighten-1"></video>
            <div v-if="connecting && !connectionError" class="video-overlay d-flex align-center justify-center">
              <v-progress-circular indeterminate color="pink" size="56" />
            </div>
          </div>

          <div class="control-buttons" v-if="!connectionError">
            <v-btn
              color="pink"
              variant="flat"
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
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roomId = computed(() => Number(route.params.roomId || 0))
const partnerNickname = ref(route.query.partnerNickname || '')
const partnerLoginId = ref(route.query.partnerLoginId || '')

const localVideo = ref(null)
const remoteVideo = ref(null)
const connectionError = ref('')
const connecting = ref(true)
const muted = ref(false)
const cameraOff = ref(false)

const myLoginId = computed(() => auth.user?.loginId || '')
const shouldInitiate = computed(() => {
  if (!myLoginId.value || !partnerLoginId.value) return false
  return myLoginId.value.localeCompare(partnerLoginId.value) < 0
})

let client = null
let signal = null
let pc = null
let localStream = null
let hasSentOffer = false

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

onMounted(() => {
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
</script>

<style scoped>
.video-stage {
  position: relative;
  width: 100%;
  min-height: 320px;
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
  justify-content: center;
  flex-wrap: wrap;
  gap: 16px;
}

@media (max-width: 600px) {
  .local-video {
    width: 40%;
  }
}
</style>

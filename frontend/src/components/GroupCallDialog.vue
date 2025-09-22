<template>
  <v-dialog v-model="internalOpen" persistent max-width="1100">
    <v-card class="pa-4">
      <div class="d-flex justify-between align-center mb-4">
        <div class="text-h6">그룹 영상 통화</div>
        <v-btn icon @click="close">
          <v-icon>mdi-close</v-icon>
        </v-btn>
      </div>

      <v-row class="video-grid" dense>
        <v-col cols="12" md="6">
          <div class="video-container">
            <video ref="localVideo" autoplay muted playsinline class="video-feed"></video>
            <div class="video-label">나</div>
          </div>
        </v-col>
        <v-col
          v-for="participant in remoteStreams"
          :key="participant.loginId"
          cols="12"
          md="6"
        >
          <div class="video-container">
            <video
              :ref="setRemoteVideoRef(participant.loginId)"
              autoplay
              playsinline
              class="video-feed"
            ></video>
            <div class="video-label">{{ participant.nickName || participant.loginId }}</div>
          </div>
        </v-col>
      </v-row>

      <div class="d-flex justify-center mt-4">
        <v-btn color="error" @click="close">통화 종료</v-btn>
      </div>
    </v-card>
  </v-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { setupSignalRoutes } from '../services/signaling'

const props = defineProps({
  open: { type: Boolean, default: false },
  room: { type: Object, default: null },
  client: { type: Object, default: null },
  connected: { type: Boolean, default: false },
  currentLoginId: { type: String, default: '' }
})

const emit = defineEmits(['update:open'])

const internalOpen = ref(false)
const localVideo = ref(null)
const localStream = ref(null)
const remoteStreams = ref([])
const remoteVideoRefs = new Map()
let signalRoute = null
const peerConnections = new Map()

const otherParticipants = computed(() => {
  if (!props.room?.participants) return []
  return props.room.participants.filter((p) => p.loginId !== props.currentLoginId)
})

watch(
  () => props.open,
  (value) => {
    internalOpen.value = value
    if (value) {
      void startCall()
    } else {
      endCall()
    }
  },
  { immediate: true }
)

watch(internalOpen, (value) => {
  emit('update:open', value)
  if (!value) {
    endCall()
  }
})

function setRemoteVideoRef(loginId) {
  return (el) => {
    if (!el) return
    const stream = remoteStreams.value.find((item) => item.loginId === loginId)?.stream
    if (stream) {
      el.srcObject = stream
    }
    remoteVideoRefs.set(loginId, el)
  }
}

async function startCall() {
  if (!props.client || !props.connected || !props.room) {
    return
  }
  try {
    localStream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    if (localVideo.value) {
      localVideo.value.srcObject = localStream.value
    }
  } catch (error) {
    console.error('로컬 미디어 접근 실패', error)
    return
  }

  signalRoute = setupSignalRoutes(props.client, {
    me: props.currentLoginId,
    onSignal: handleSignal
  })

  otherParticipants.value.forEach((participant) => {
    const shouldOffer = props.currentLoginId < participant.loginId
    const pc = ensurePeerConnection(participant.loginId)
    if (shouldOffer) {
      void createOfferFor(participant.loginId, pc)
    }
  })
}

function ensurePeerConnection(loginId) {
  if (peerConnections.has(loginId)) {
    return peerConnections.get(loginId)
  }
  const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] })
  peerConnections.set(loginId, pc)

  if (localStream.value) {
    localStream.value.getTracks().forEach((track) => pc.addTrack(track, localStream.value))
  }

  pc.ontrack = (event) => {
    const [stream] = event.streams
    if (!stream) return
    const existing = remoteStreams.value.find((item) => item.loginId === loginId)
    if (existing) {
      existing.stream = stream
    } else {
      remoteStreams.value.push({ loginId, nickName: findNickName(loginId), stream })
    }
    const el = remoteVideoRefs.get(loginId)
    if (el) {
      el.srcObject = stream
    }
  }

  pc.onicecandidate = (event) => {
    if (event.candidate) {
      sendSignal({
        type: 'ice-candidate',
        receiverLoginId: loginId,
        data: event.candidate
      })
    }
  }

  return pc
}

async function createOfferFor(loginId, pc) {
  try {
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    sendSignal({
      type: 'offer',
      receiverLoginId: loginId,
      data: offer
    })
  } catch (error) {
    console.error('오퍼 생성 실패', error)
  }
}

async function handleSignal(message) {
  if (!message || message.senderLoginId === props.currentLoginId) {
    return
  }
  if (message.roomId !== props.room?.roomId) {
    return
  }

  const sender = message.senderLoginId
  const pc = ensurePeerConnection(sender)

  try {
    if (message.type === 'offer') {
      await pc.setRemoteDescription(new RTCSessionDescription(message.data))
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      sendSignal({ type: 'answer', receiverLoginId: sender, data: answer })
    } else if (message.type === 'answer') {
      await pc.setRemoteDescription(new RTCSessionDescription(message.data))
    } else if (message.type === 'ice-candidate' && message.data) {
      await pc.addIceCandidate(new RTCIceCandidate(message.data))
    }
  } catch (error) {
    console.error('시그널 처리 중 오류', error)
  }
}

function sendSignal(payload) {
  if (!signalRoute) return
  signalRoute.sendSignal({
    ...payload,
    roomId: props.room?.roomId
  })
}

function findNickName(loginId) {
  return props.room?.participants?.find((p) => p.loginId === loginId)?.nickName || loginId
}

function close() {
  internalOpen.value = false
}

function endCall() {
  peerConnections.forEach((pc) => pc.close())
  peerConnections.clear()
  remoteStreams.value.splice(0)
  remoteVideoRefs.clear()
  if (signalRoute?.sub) {
    signalRoute.sub.unsubscribe()
  }
  signalRoute = null
  if (localStream.value) {
    localStream.value.getTracks().forEach((track) => track.stop())
    localStream.value = null
  }
  if (localVideo.value) {
    localVideo.value.srcObject = null
  }
}

onBeforeUnmount(() => {
  endCall()
})

</script>

<style scoped>
.video-grid {
  min-height: 400px;
}

.video-container {
  position: relative;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-feed {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-label {
  position: absolute;
  left: 12px;
  bottom: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.85rem;
}
</style>

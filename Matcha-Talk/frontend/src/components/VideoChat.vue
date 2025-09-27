<template>
  <div class="video-chat">
    <div class="video-surfaces">
      <video ref="remoteVideo" autoplay playsinline class="video-surface remote"></video>
      <video ref="localVideo" autoplay muted playsinline class="video-surface local"></video>
    </div>
    <div class="call-status">{{ statusMessage }}</div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useAuthStore } from '../stores/auth'
import { createRealtimeClient } from '../services/ws'
import { setupSignalHandlers } from '../services/signaling'
import { resolveClientIdentity } from '../utils/identity'

const auth = useAuthStore()

const localVideo = ref(null)
const remoteVideo = ref(null)
const statusMessage = ref('대기 중')
const identity = ref(resolveClientIdentity(auth))

let realtimeClient = null
let signalClient = null
let localStream = null
let remoteStream = null
let peerConnection = null
let activeReceiver = null
let initializing = null

function updateStatus(message) {
  statusMessage.value = message
}

function attachLocalTracks(stream, pc) {
  if (!stream) return
  const existingTracks = new Set(pc.getSenders().map((sender) => sender.track))
  stream.getTracks().forEach((track) => {
    if (!existingTracks.has(track)) {
      pc.addTrack(track, stream)
    }
  })
}

function ensurePeerConnection() {
  if (peerConnection) {
    return peerConnection
  }

  const pc = new RTCPeerConnection({
    iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
  })

  pc.onicecandidate = (event) => {
    if (event.candidate && signalClient) {
      signalClient.sendSignal({
        event: 'iceCandidate',
        receiverLoginId: activeReceiver,
        data: event.candidate,
        senderLoginId: identity.value
      }).catch((error) => {
        console.error('[webrtc] Failed to send ICE candidate', error)
      })
    }
  }

  pc.ontrack = (event) => {
    const [stream] = event.streams
    if (stream && remoteVideo.value) {
      remoteStream = stream
      remoteVideo.value.srcObject = stream
    }
  }

  if (localStream) {
    attachLocalTracks(localStream, pc)
  }

  peerConnection = pc
  return pc
}

async function ensureLocalStream() {
  if (localStream) {
    return localStream
  }

  try {
    localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true })
    if (localVideo.value) {
      localVideo.value.srcObject = localStream
    }
    const pc = ensurePeerConnection()
    attachLocalTracks(localStream, pc)
    updateStatus('카메라 연결됨')
    return localStream
  } catch (error) {
    updateStatus('미디어 권한을 확인해주세요')
    throw error
  }
}

async function initializeRealtime() {
  if (initializing) {
    return initializing
  }

  initializing = (async () => {
    const loginId = resolveClientIdentity(auth)
    identity.value = loginId

    if (!realtimeClient) {
      realtimeClient = createRealtimeClient({ queryParams: { loginId } })
      realtimeClient.onClose(() => updateStatus('실시간 연결이 종료되었습니다.'))
      realtimeClient.onError((event) => {
        console.error('[webrtc] WebSocket error', event)
        updateStatus('실시간 채널 오류')
      })
    } else {
      realtimeClient.setQueryParams({ loginId })
    }

    if (!realtimeClient.isConnected()) {
      try {
        await realtimeClient.connect()
        updateStatus('실시간 채널 연결됨')
      } catch (error) {
        console.error('[webrtc] Failed to connect realtime channel', error)
        updateStatus('실시간 채널 연결 실패')
        throw error
      }
    }

    if (signalClient) {
      signalClient.dispose()
    }

    signalClient = setupSignalHandlers(realtimeClient, {
      onSignal: handleIncomingSignal
    })
  })()

  try {
    await initializing
  } finally {
    initializing = null
  }
}

async function ensureRealtimeReady() {
  await initializeRealtime()
}

async function startCall(receiverLoginId) {
  if (!receiverLoginId) {
    throw new Error('receiverLoginId is required to start a call')
  }

  activeReceiver = receiverLoginId
  await ensureRealtimeReady()
  const pc = ensurePeerConnection()
  await ensureLocalStream()

  updateStatus('호출 중...')

  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)

  await signalClient.sendSignal({
    event: 'offer',
    receiverLoginId,
    data: offer,
    senderLoginId: identity.value
  })
}

async function handleIncomingSignal(message) {
  if (!message) return

  const eventType = message.event || message.type
  const pc = ensurePeerConnection()

  if (eventType === 'offer') {
    activeReceiver = message.senderLoginId
    await ensureLocalStream()
    await pc.setRemoteDescription(message.data)
    const answer = await pc.createAnswer()
    await pc.setLocalDescription(answer)
    await signalClient.sendSignal({
      event: 'answer',
      receiverLoginId: message.senderLoginId,
      data: answer,
      senderLoginId: identity.value
    })
    updateStatus('상대방과 연결 중')
  } else if (eventType === 'answer') {
    await pc.setRemoteDescription(message.data)
    updateStatus('통화 연결 완료')
  } else if (eventType === 'ice-candidate' || eventType === 'iceCandidate') {
    try {
      await pc.addIceCandidate(message.data)
    } catch (error) {
      console.warn('[webrtc] Failed to add ICE candidate', error)
    }
  }
}

function hangUp() {
  activeReceiver = null

  if (peerConnection) {
    try { peerConnection.close() } catch (error) {
      console.warn('[webrtc] Failed to close peer connection', error)
    }
    peerConnection = null
  }

  if (remoteStream) {
    remoteStream.getTracks().forEach((track) => track.stop())
    remoteStream = null
  }
  if (remoteVideo.value) {
    remoteVideo.value.srcObject = null
  }

  if (localStream) {
    localStream.getTracks().forEach((track) => track.stop())
    localStream = null
  }
  if (localVideo.value) {
    localVideo.value.srcObject = null
  }

  updateStatus('통화 종료됨')
}

watch(() => auth.loginId, () => {
  if (realtimeClient) {
    initializeRealtime().catch((error) => {
      console.warn('[webrtc] Failed to refresh realtime identity', error)
    })
  }
})

onMounted(() => {
  initializeRealtime().catch((error) => {
    console.warn('[webrtc] 초기화 실패', error)
  })
})

onBeforeUnmount(() => {
  hangUp()

  if (signalClient) {
    try { signalClient.dispose() } catch (error) {
      console.warn('[webrtc] Failed to dispose signal client', error)
    }
    signalClient = null
  }

  if (realtimeClient) {
    try { realtimeClient.disconnect() } catch (error) {
      console.warn('[webrtc] Failed to disconnect realtime client', error)
    }
    realtimeClient = null
  }
})

defineExpose({
  startCall,
  handleIncomingSignal,
  hangUp
})
</script>

<style scoped>
.video-chat {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.video-surfaces {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background-color: #1f1f1f;
  border-radius: 12px;
  overflow: hidden;
}

.video-surface {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  background-color: #2e2e2e;
}

.video-surface.local {
  width: 30%;
  height: 30%;
  right: 0.75rem;
  bottom: 0.75rem;
  left: auto;
  top: auto;
  border: 2px solid white;
  border-radius: 8px;
  object-fit: cover;
}

.call-status {
  font-size: 0.9rem;
  color: #666;
}
</style>

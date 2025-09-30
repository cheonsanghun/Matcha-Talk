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
let acquiringLocalStream = null
let makingOffer = false
let ignoreOffer = false
let isSettingRemoteAnswerPending = false

function isMediaDeviceSupported () {
  return typeof navigator !== 'undefined' &&
    navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === 'function'
}

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

function normalizeLoginId(value) {
  return value ? String(value).toLowerCase() : ''
}

function determinePoliteness(remoteLoginId) {
  const local = normalizeLoginId(identity.value)
  const remote = normalizeLoginId(remoteLoginId)
  if (!local || !remote) {
    return true
  }
  return local > remote
}

async function tryPlayVideo(element) {
  if (!element) return
  try {
    await element.play()
  } catch (error) {
    console.warn('[webrtc] Failed to autoplay video surface', error)
  }
}

async function attachRemoteStream(stream) {
  if (!stream) return
  remoteStream = stream
  if (remoteVideo.value) {
    remoteVideo.value.srcObject = stream
    await tryPlayVideo(remoteVideo.value)
  }
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
    if (stream) {
      void attachRemoteStream(stream)
    }
  }

  pc.onconnectionstatechange = () => {
    const state = pc.connectionState
    if (state === 'connected') {
      updateStatus('통화 연결 완료')
    } else if (state === 'failed' || state === 'disconnected') {
      updateStatus('통화 연결이 불안정합니다. 다시 시도해주세요.')
    }
  }

  if (localStream) {
    attachLocalTracks(localStream, pc)
  }

  peerConnection = pc
  return pc
}

function bindLocalStreamEvents (stream) {
  if (!stream) return
  stream.getTracks().forEach((track) => {
    track.onended = () => {
      if (localStream === stream) {
        updateStatus('카메라 연결이 종료되었습니다. 다시 시도해주세요.')
        localStream = null
        if (localVideo.value) {
          localVideo.value.srcObject = null
        }
      }
    }
  })
}

async function ensureLocalStream() {
  if (localStream) {
    if (localVideo.value && localVideo.value.srcObject !== localStream) {
      localVideo.value.srcObject = localStream
      void tryPlayVideo(localVideo.value)
    }
    return localStream
  }

  if (!isMediaDeviceSupported()) {
    updateStatus('브라우저에서 카메라를 사용할 수 없습니다.')
    throw new Error('Media devices are not supported in this environment.')
  }

  if (acquiringLocalStream) {
    return acquiringLocalStream
  }

  acquiringLocalStream = (async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: { facingMode: 'user' } })
      localStream = stream
      bindLocalStreamEvents(stream)
      if (localVideo.value) {
        localVideo.value.srcObject = stream
        void tryPlayVideo(localVideo.value)
      }
      const pc = ensurePeerConnection()
      attachLocalTracks(stream, pc)
      updateStatus('카메라 준비 완료')
      return stream
    } catch (error) {
      console.error('[webrtc] Failed to obtain local media stream', error)
      updateStatus('미디어 권한을 확인해주세요')
      throw error
    } finally {
      acquiringLocalStream = null
    }
  })()

  return acquiringLocalStream
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

  makingOffer = true
  let offer
  try {
    offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
  } finally {
    makingOffer = false
  }

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
    const senderLoginId = message.senderLoginId
    activeReceiver = senderLoginId
    const polite = determinePoliteness(senderLoginId)
    const offerCollision = makingOffer || pc.signalingState === 'have-local-offer' || isSettingRemoteAnswerPending

    ignoreOffer = !polite && offerCollision
    if (ignoreOffer) {
      console.warn('[webrtc] Ignoring offer because of collision and impolite role')
      return
    }

    try {
      await ensureLocalStream()
      if (offerCollision) {
        await Promise.all([
          pc.setLocalDescription({ type: 'rollback' }),
          pc.setRemoteDescription(message.data)
        ])
      } else {
        await pc.setRemoteDescription(message.data)
      }
      const answer = await pc.createAnswer()
      isSettingRemoteAnswerPending = true
      await pc.setLocalDescription(answer)
      await signalClient.sendSignal({
        event: 'answer',
        receiverLoginId: senderLoginId,
        data: answer,
        senderLoginId: identity.value
      })
      updateStatus('상대방과 연결 중')
    } catch (error) {
      console.error('[webrtc] Failed to process incoming offer', error)
    } finally {
      isSettingRemoteAnswerPending = false
    }
  } else if (eventType === 'answer') {
    if (ignoreOffer) {
      return
    }
    try {
      await pc.setRemoteDescription(message.data)
      updateStatus('통화 연결 완료')
    } catch (error) {
      console.error('[webrtc] Failed to apply remote answer', error)
    }
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
  makingOffer = false
  ignoreOffer = false
  isSettingRemoteAnswerPending = false

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

watch(localVideo, (element) => {
  if (element && localStream) {
    element.srcObject = localStream
    void tryPlayVideo(element)
  }
})

watch(remoteVideo, (element) => {
  if (element && remoteStream) {
    element.srcObject = remoteStream
    void tryPlayVideo(element)
  }
})

onMounted(() => {
  initializeRealtime().catch((error) => {
    console.warn('[webrtc] 초기화 실패', error)
  })

  ensureLocalStream().catch((error) => {
    console.warn('[webrtc] Local media preview failed', error)
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
  background-color: #1f1f1f;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 4 / 3;
  min-height: 420px;
}

@supports not (aspect-ratio: 1 / 1) {
  .video-surfaces {
    height: 0;
    padding-top: 75%;
  }
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

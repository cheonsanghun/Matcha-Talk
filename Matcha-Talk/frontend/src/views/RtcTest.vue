<template>
  <v-container class="py-4">
    <div v-if="!connected" class="d-flex flex-wrap gap-2 mb-4 align-center" style="max-width:500px">
      <v-text-field
          v-model="partner"
          label="상대 로그인ID"
          density="compact"
          hide-details
      />
      <v-text-field
          v-model="roomId"
          label="방 ID"
          density="compact"
          hide-details
          style="max-width:120px"
      />
      <v-checkbox
          v-model="isInitiator"
          label="발신자"
          hide-details
      />
      <v-btn color="primary" @click="start" :disabled="!partner">연결</v-btn>
    </div>

    <template v-else>
      <div class="video-wrapper mb-4">
        <video ref="remoteVideo" autoplay playsinline class="remote-video bg-grey-lighten-2"></video>
        <video ref="localVideo" autoplay muted playsinline class="local-video bg-grey-lighten-2"></video>
      </div>
      <div class="d-flex flex-column" style="max-width:400px">
        <div class="flex-grow-1 overflow-y-auto mb-2" style="height:200px;">
          <div v-for="(m,i) in chats" :key="i" class="mb-1">{{ m }}</div>
        </div>
        <v-text-field
            v-model="draft"
            @keyup.enter="sendChat"
            placeholder="메시지 입력"
            density="compact"
            hide-details
        >
          <template #append-inner>
            <v-icon class="cursor-pointer" @click="sendChat">mdi-send</v-icon>
          </template>
        </v-text-field>
      </div>
    </template>
  </v-container>
</template>

<script setup>
import { ref, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { createRealtimeClient } from '../services/ws'
import { setupSignalHandlers } from '../services/signaling'

const route = useRoute()
const auth = useAuthStore()

const me = ref(auth.user?.loginId || '')
const partner = ref(route.query.partner || '')
const roomId = ref(route.query.roomId || '1')
const isInitiator = ref(route.query.initiator === '1')
const connected = ref(false)

const localVideo = ref(null)
const remoteVideo = ref(null)
const chats = ref([])
const draft = ref('')

const pc = new RTCPeerConnection({
  iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
})

let realtimeClient = null
let signalClient = null
const teardownHandlers = []

async function start () {
  try {
    if (connected.value) return

    const token = auth.token || localStorage.getItem('token')
    if (!token) {
      console.error('JWT token not found. Cannot start WebRTC test.')
      return
    }

    ensureRealtimeClient(token)

    await realtimeClient.connect()
    connected.value = true

    await nextTick()

    if (signalClient) {
      signalClient.dispose()
    }
    signalClient = setupSignalHandlers(realtimeClient, {
      onSignal: handleIncomingSignal
    })

    await setupMedia()
    setupPeerCallbacks()

    if (isInitiator.value) {
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      await sendSignal({
        type: 'offer',
        receiverLoginId: partner.value,
        data: offer
      })
    }
  } catch (error) {
    console.error('start() failed:', error)
  }
}

function ensureRealtimeClient (token) {
  if (!realtimeClient) {
    realtimeClient = createRealtimeClient({ token })
    teardownHandlers.push(
      realtimeClient.onClose(() => {
        connected.value = false
      }),
      realtimeClient.onError((event) => {
        console.error('[rtc] WebSocket error', event)
      }),
      realtimeClient.onEvent('chat', handleIncomingChat)
    )
  } else {
    realtimeClient.setToken(token)
  }
}

async function setupMedia () {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    stream.getTracks().forEach(track => pc.addTrack(track, stream))
    if (localVideo.value) localVideo.value.srcObject = stream
  } catch (error) {
    console.error('getUserMedia failed:', error)
  }
}

function setupPeerCallbacks () {
  pc.ontrack = (event) => {
    if (remoteVideo.value) remoteVideo.value.srcObject = event.streams[0]
  }

  pc.onicecandidate = (event) => {
    if (event.candidate) {
      sendSignal({
        type: 'ice-candidate',
        receiverLoginId: partner.value,
        data: event.candidate
      })
    }
  }
}

async function handleIncomingSignal (msg) {
  try {
    if (msg.type === 'offer') {
      await pc.setRemoteDescription(msg.data)
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      await sendSignal({ type: 'answer', receiverLoginId: msg.senderLoginId, data: answer })
    } else if (msg.type === 'answer') {
      await pc.setRemoteDescription(msg.data)
    } else if (msg.type === 'ice-candidate') {
      try {
        await pc.addIceCandidate(msg.data)
      } catch (error) {
        console.warn('Failed to add ICE candidate:', error)
      }
    }
  } catch (error) {
    console.error('Failed to handle incoming signal:', error)
  }
}

async function sendSignal (signal) {
  if (!signalClient) {
    throw new Error('Signal client is not initialized.')
  }
  await signalClient.sendSignal(signal)
}

function handleIncomingChat (payload) {
  if (!payload) return
  const nick = payload.senderNickName ?? payload.senderNickname ?? 'unknown'
  const content = payload.content ?? ''
  chats.value.push(`${nick}: ${content}`)
}

async function sendChat () {
  const message = draft.value.trim()
  if (!message) return
  if (!realtimeClient?.isConnected()) {
    console.warn('Not connected; cannot send chat.')
    return
  }
  try {
    realtimeClient.send({
      type: 'CHAT',
      roomId: Number(roomId.value),
      content: message
    })
    draft.value = ''
  } catch (error) {
    console.error('Failed to send chat message:', error)
  }
}

onBeforeUnmount(() => {
  try { signalClient?.dispose?.() } catch {}
  teardownHandlers.forEach((fn) => {
    try { fn?.() } catch {}
  })
  try { realtimeClient?.disconnect?.() } catch {}
  try { pc?.close?.() } catch {}
})
</script>

<style scoped>
.video-wrapper {
  position: relative;
}

.remote-video {
  width: 100%;
  max-height: 360px;
}

.local-video {
  position: absolute;
  width: 30%;
  max-width: 200px;
  bottom: 0.5rem;
  right: 0.5rem;
  border: 2px solid white;
  border-radius: 4px;
}
</style>

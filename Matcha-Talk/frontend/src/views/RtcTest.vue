<template>
  <v-container class="py-4">
    <div v-if="!connected" class="d-flex flex-wrap gap-2 mb-4 align-center" style="max-width:500px">
      <v-text-field v-model="partner" label="상대 로그인ID" density="compact" hide-details />
      <v-text-field v-model="roomId" label="방 ID" density="compact" hide-details style="max-width:120px" />
      <v-checkbox v-model="isInitiator" label="발신자" hide-details />
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
import { ref, onBeforeUnmount, nextTick, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { createRealtimeClient } from '../services/ws'
import { setupSignalHandlers } from '../services/signaling'
import { getIceServers } from '../services/webrtc'

const route = useRoute()
const auth = useAuthStore()
const isAuthenticated = computed(() => auth.isAuthenticated)

const partner = ref(route.query.partner || '')
const roomId = ref(route.query.roomId || '1')
const isInitiator = ref(route.query.initiator === '1')
const negotiationRole = ref(resolveNegotiationRole(route.query))
const connected = ref(false)
const polite = ref(false)

const localVideo = ref(null)
const remoteVideo = ref(null)
const chats = ref([])
const draft = ref('')

let pc = null
let realtimeClient = null
let signalClient = null
let makingOffer = false
let ignoreOffer = false
const teardownHandlers = []

watch(() => route.query.role, () => {
  negotiationRole.value = resolveNegotiationRole(route.query)
  updatePoliteRole()
})

watch(() => route.query.negotiationRole, () => {
  negotiationRole.value = resolveNegotiationRole(route.query)
  updatePoliteRole()
})

watch(() => route.query.matchRole, () => {
  negotiationRole.value = resolveNegotiationRole(route.query)
  updatePoliteRole()
})

watch(isInitiator, () => {
  updatePoliteRole()
})

function resolveNegotiationRole (query) {
  const raw = query?.role ?? query?.negotiationRole ?? query?.matchRole ?? ''
  return typeof raw === 'string' ? raw.toUpperCase() : ''
}

function updatePoliteRole () {
  if (negotiationRole.value === 'HOST' || negotiationRole.value === 'IMPOLITE') {
    polite.value = false
  } else if (negotiationRole.value === 'MEMBER' || negotiationRole.value === 'POLITE') {
    polite.value = true
  } else {
    polite.value = !isInitiator.value
  }
}

updatePoliteRole()

async function start () {
  try {
    if (connected.value) return

    if (!isAuthenticated.value) {
      console.error('세션이 만료되었거나 로그인 상태가 아닙니다.')
      alert('로그인 후 이용해주세요.')
      return
    }

    updatePoliteRole()
    ensureRealtimeClient()

    await realtimeClient.connect()
    connected.value = true

    const iceServers = await getIceServers()
    pc = new RTCPeerConnection({ iceServers })
    setupPeerCallbacks()

    await nextTick()

    if (signalClient) {
      signalClient.dispose()
    }
    signalClient = setupSignalHandlers(realtimeClient, {
      onSignal: handleIncomingSignal
    })

    await setupMedia()
  } catch (error) {
    console.error('start() failed:', error)
  }
}

function ensureRealtimeClient () {
  if (!realtimeClient) {
    realtimeClient = createRealtimeClient()
    teardownHandlers.push(
      realtimeClient.onClose(() => {
        connected.value = false
      }),
      realtimeClient.onError((event) => {
        console.error('[rtc] WebSocket error', event)
      }),
      realtimeClient.onEvent('chat', handleIncomingChat)
    )
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

  pc.onnegotiationneeded = async () => {
    try {
      makingOffer = true
      await pc.setLocalDescription(await pc.createOffer())
      await sendSignal({
        type: 'offer',
        receiverLoginId: partner.value,
        data: pc.localDescription
      })
    } catch (error) {
      console.error('Negotiation failed', error)
    } finally {
      makingOffer = false
    }
  }

  pc.oniceconnectionstatechange = () => {
    if (pc.iceConnectionState === 'failed') {
      console.warn('ICE connection failed, restarting ICE')
      pc.restartIce()
    }
  }
}

async function handleIncomingSignal (msg) {
  if (!pc) return
  try {
    if (msg.type === 'offer') {
      const offerCollision = makingOffer || pc.signalingState !== 'stable'
      ignoreOffer = !polite.value && offerCollision
      if (ignoreOffer) {
        console.info('Ignoring offer to avoid glare')
        return
      }

      await pc.setRemoteDescription(new RTCSessionDescription(msg.data))
      await pc.setLocalDescription(await pc.createAnswer())
      await sendSignal({ type: 'answer', receiverLoginId: msg.senderLoginId, data: pc.localDescription })
    } else if (msg.type === 'answer') {
      await pc.setRemoteDescription(new RTCSessionDescription(msg.data))
    } else if (msg.type === 'ice-candidate') {
      if (!msg.data) return
      try {
        await pc.addIceCandidate(msg.data)
      } catch (error) {
        if (!ignoreOffer) {
          console.warn('Failed to add ICE candidate:', error)
        }
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
    realtimeClient.publish({
      destination: '/app/chat/send',
      body: {
        roomId: Number(roomId.value),
        content: message,
      },
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

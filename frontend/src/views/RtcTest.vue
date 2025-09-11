<template>
  <v-container class="py-4">
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
  </v-container>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'

const route = useRoute()
const me = ref(route.query.me || '')
const partner = ref(route.query.partner || '')
const roomId = ref(route.query.roomId || 1)
const isInitiator = route.query.initiator === '1'

const localVideo = ref(null)
const remoteVideo = ref(null)
const chats = ref([])
const draft = ref('')

const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] })
let client, signal, chatSub

onMounted(async () => {
  client = createStompClient(localStorage.getItem('token'))
  client.onConnect = async () => {
    signal = setupSignalRoutes(client, {
      me: me.value,
      subscribeDest: '/user/queue/signals',
      onSignal: async (msg) => {
        if (msg.type === 'offer') {
          await pc.setRemoteDescription(msg.data)
          const answer = await pc.createAnswer()
          await pc.setLocalDescription(answer)
          signal.sendSignal({ type: 'answer', receiverLoginId: msg.senderLoginId, data: answer })
        } else if (msg.type === 'answer') {
          await pc.setRemoteDescription(msg.data)
        } else if (msg.type === 'ice-candidate') {
          try { await pc.addIceCandidate(msg.data) } catch {}
        }
      }
    })

    chatSub = client.subscribe(`/topic/rooms/${roomId.value}`, (msg) => {
      const payload = JSON.parse(msg.body)
      chats.value.push(`${payload.senderNickname}: ${payload.content}`)
    })

    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    stream.getTracks().forEach(t => pc.addTrack(t, stream))
    localVideo.value.srcObject = stream

    pc.ontrack = (e) => {
      remoteVideo.value.srcObject = e.streams[0]
    }
    pc.onicecandidate = (e) => {
      if (e.candidate) {
        signal.sendSignal({ type: 'ice-candidate', receiverLoginId: partner.value, data: e.candidate })
      }
    }

    if (isInitiator) {
      const offer = await pc.createOffer()
      await pc.setLocalDescription(offer)
      signal.sendSignal({ type: 'offer', receiverLoginId: partner.value, data: offer })
    }
  }
  client.activate()
})

function sendChat () {
  if (!draft.value) return
  client.publish({
    destination: `/app/chat.sendMessage/${roomId.value}`,
    body: JSON.stringify({ content: draft.value })
  })
  draft.value = ''
}

onBeforeUnmount(() => {
  chatSub?.unsubscribe?.()
  signal?.sub?.unsubscribe?.()
  client?.deactivate?.()
  pc?.close?.()
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
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
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'

// 기본 상태
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

// WebRTC PeerConnection
const pc = new RTCPeerConnection({
  iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
})

// STOMP & signaling 핸들
let client = null
let signal = null
let chatSub = null

async function start () {
  try {
    if (connected.value) return

    // 1) STOMP 클라이언트를 먼저 생성
    if (!client) {
      client = createStompClient(auth.token)
    }

    // 2) 이벤트 핸들러를 설정
    client.onConnect = async () => {
      console.log('[STOMP] connected')
      connected.value = true

      // v-if 전환으로 비디오 노드가 DOM에 뜨도록 대기
      await nextTick()

      // 3) 시그널 라우트 설정 (구독 포함)
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
            try {
              await pc.addIceCandidate(msg.data)
            } catch (e) {
              console.warn('Failed to add ICE candidate:', e)
            }
          }
        }
      })

      // 4) 채팅 구독
      chatSub = client.subscribe(`/topic/rooms/${roomId.value}`, (msg) => {
        try {
          const payload = JSON.parse(msg.body || '{}')
          const nick = payload.senderNickname ?? 'unknown'
          const content = payload.content ?? ''
          chats.value.push(`${nick}: ${content}`)
        } catch (e) {
          console.warn('Invalid chat payload:', e)
        }
      })

      // 5) 미디어 스트림 획득 & 바인딩
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
        stream.getTracks().forEach(t => pc.addTrack(t, stream))
        if (localVideo.value) localVideo.value.srcObject = stream
      } catch (e) {
        console.error('getUserMedia failed:', e)
      }

      // 6) 원격 트랙/ICE 콜백
      pc.ontrack = (e) => {
        if (remoteVideo.value) remoteVideo.value.srcObject = e.streams[0]
      }
      pc.onicecandidate = (e) => {
        if (e.candidate) {
          signal?.sendSignal?.({
            type: 'ice-candidate',
            receiverLoginId: partner.value,
            data: e.candidate
          })
        }
      }

      // 7) 발신자면 offer 생성/전송
      if (isInitiator.value) {
        const offer = await pc.createOffer()
        await pc.setLocalDescription(offer)
        signal.sendSignal({
          type: 'offer',
          receiverLoginId: partner.value,
          data: offer
        })
      }
    }

    client.onStompError = (frame) => {
      console.error('STOMP broker error:', frame.headers?.message, frame.body)
    }
    client.onWebSocketError = (ev) => {
      console.error('WebSocket error:', ev)
    }

    // 3) 마지막으로 활성화
    if (!client.active) client.activate()
  } catch (err) {
    console.error('start() failed:', err)
  }
}

function sendChat () {
  if (!draft.value?.trim()) return
  if (!client || !client.connected) {
    console.warn('Not connected; cannot send chat.')
    return
  }
  client.publish({
    destination: `/app/chat.sendMessage/${roomId.value}`,
    body: JSON.stringify({ content: draft.value })
  })
  draft.value = ''
}

onBeforeUnmount(() => {
  try { chatSub?.unsubscribe?.() } catch {}
  try { signal?.sub?.unsubscribe?.() } catch {}
  try { client?.deactivate?.() } catch {}
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

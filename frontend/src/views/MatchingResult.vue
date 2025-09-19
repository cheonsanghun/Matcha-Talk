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
              <div class="text-caption text-medium-emphasis">{{ statusText }}</div>
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
                <ChatPanel
                  class="flex-grow-1"
                  :partner="partnerNameDisplay"
                  :messages="chatMessages"
                  :sending="chatSending"
                  :placeholder="chatPlaceholder"
                  @send="sendChatMessage"
                />
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
          <div class="text-center text-caption mt-4" v-if="statusText">
            {{ statusText }}
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import ChatPanel from '../components/ChatPanel.vue'
import { useMatchStore } from '../stores/match'
import { useAuthStore } from '../stores/auth'
import { subscribe as subscribeWs } from '../services/ws'
import { subscribeSignals, sendSignal } from '../services/signaling'
import { subscribeRoom, sendMessage as sendRoomMessage, chatApi } from '../services/chat'

const router = useRouter()
const matchStore = useMatchStore()
const authStore = useAuthStore()

const {
  partner,
  status,
  message: storeMessage,
  roomId,
  shouldCreateOffer,
  myRequestId,
} = storeToRefs(matchStore)

const isMatched = computed(() => ['MATCHED', 'CONFIRMED'].includes(status.value))
const waitingStatusText = computed(() => {
  if (storeMessage.value) return storeMessage.value
  return '상대방을 찾는 중입니다. 잠시만 기다려주세요.'
})
const statusText = computed(() => storeMessage.value)
const partnerNameDisplay = computed(
  () => partner.value?.nickname ?? partner.value?.loginId ?? '상대'
)
const partnerAvatarFallback = computed(() => '/src/assets/avatar-default.png')
const chatPlaceholder = computed(() => `${partnerNameDisplay.value}에게 메시지를 보내보세요`)

const actionLoading = reactive({ accept: false, decline: false })
const decisionFinalized = ref(false)
const chatMessages = ref([])
const chatSending = ref(false)
const hasRemoteStream = ref(false)
const localVideo = ref(null)
const remoteVideo = ref(null)

let matchUnsubscribe = null
let signalUnsubscribe = null
let chatUnsubscribe = null
let peerConnection = null
let localStream = null

const acceptDisabled = computed(() => decisionFinalized.value || actionLoading.accept)
const declineDisabled = computed(() => decisionFinalized.value || actionLoading.decline)

const normalizeChatMessage = (message) => ({
  id: message.id ?? `${Date.now()}-${Math.random()}`,
  content: message.content ?? '',
  translatedContent: message.translatedContent ?? '',
  sentAt: message.sentAt ?? new Date().toISOString(),
  me:
    message.senderLoginId === authStore.user?.loginId ||
    message.senderNickName === authStore.user?.nickname,
})

const ensurePeerConnection = async () => {
  if (!peerConnection) {
    peerConnection = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
    })

    peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        emitSignal('ICE', event.candidate)
      }
    }

    peerConnection.ontrack = (event) => {
      hasRemoteStream.value = true
      const [stream] = event.streams
      if (remoteVideo.value) {
        remoteVideo.value.srcObject = stream
      }
    }

    peerConnection.onconnectionstatechange = () => {
      if (['disconnected', 'failed', 'closed'].includes(peerConnection.connectionState)) {
        hasRemoteStream.value = false
      }
    }
  }

  if (!localStream) {
    try {
      localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true })
      if (localVideo.value) {
        localVideo.value.srcObject = localStream
      }
      localStream.getTracks().forEach((track) => peerConnection.addTrack(track, localStream))
    } catch (error) {
      console.warn('카메라/마이크 권한 요청 실패', error)
      storeMessage.value = '카메라 또는 마이크 권한을 확인해주세요.'
    }
  }
}

const emitSignal = async (type, data) => {
  if (!partner.value?.loginId) return
  await sendSignal({
    type,
    data,
    receiverLoginId: partner.value.loginId,
  })
}

const handleSignal = async (payload) => {
  if (!payload || payload.senderLoginId === authStore.user?.loginId) return
  await ensurePeerConnection()

  switch (payload.type) {
    case 'OFFER': {
      await peerConnection.setRemoteDescription(new RTCSessionDescription(payload.data))
      const answer = await peerConnection.createAnswer()
      await peerConnection.setLocalDescription(answer)
      await emitSignal('ANSWER', answer)
      break
    }
    case 'ANSWER': {
      await peerConnection.setRemoteDescription(new RTCSessionDescription(payload.data))
      break
    }
    case 'ICE': {
      if (payload.data) {
        try {
          await peerConnection.addIceCandidate(new RTCIceCandidate(payload.data))
        } catch (error) {
          console.error('ICE candidate 추가 실패', error)
        }
      }
      break
    }
    default:
      break
  }
}

const createOfferIfNeeded = async () => {
  if (!shouldCreateOffer.value || !partner.value?.loginId) return
  await ensurePeerConnection()
  const offer = await peerConnection.createOffer()
  await peerConnection.setLocalDescription(offer)
  await emitSignal('OFFER', offer)
}

const subscribeMatchEvents = async () => {
  matchUnsubscribe = await subscribeWs('/user/queue/match-results', async (event) => {
    matchStore.applyEvent(event)
    if (event.eventType === 'MATCH_FOUND') {
      await createOfferIfNeeded()
    }
    if (event.eventType === 'BOTH_CONFIRMED' || event.eventType === 'PARTNER_DECLINED') {
      decisionFinalized.value = true
    }
  })
}

const subscribeSignalQueue = async () => {
  signalUnsubscribe = await subscribeSignals((signal) => {
    handleSignal(signal)
  })
}

const subscribeChatRoom = async (id) => {
  if (chatUnsubscribe) {
    chatUnsubscribe()
    chatUnsubscribe = null
  }
  if (!id) {
    chatMessages.value = []
    return
  }
  try {
    const history = await chatApi.loadMessages(id)
    chatMessages.value = history.map(normalizeChatMessage)
  } catch (error) {
    chatMessages.value = []
  }
  chatUnsubscribe = await subscribeRoom(id, (payload) => {
    chatMessages.value.push(normalizeChatMessage(payload))
  })
}

const acceptMatch = async () => {
  if (!myRequestId.value) return
  actionLoading.accept = true
  try {
    await matchStore.acceptMatch()
    decisionFinalized.value = status.value === 'CONFIRMED'
  } catch (error) {
    console.error(error)
  } finally {
    actionLoading.accept = false
  }
}

const declineMatch = async () => {
  if (!myRequestId.value) return
  actionLoading.decline = true
  try {
    await matchStore.declineMatch()
    decisionFinalized.value = true
  } catch (error) {
    console.error(error)
  } finally {
    actionLoading.decline = false
  }
}

const sendChatMessage = async (text) => {
  if (!roomId.value) return
  chatSending.value = true
  try {
    await sendRoomMessage(roomId.value, { content: text })
    chatMessages.value.push(
      normalizeChatMessage({
        content: text,
        senderNickName: authStore.user?.nickname,
        senderLoginId: authStore.user?.loginId,
        sentAt: new Date().toISOString(),
      }),
    )
    await nextTick()
  } catch (error) {
    console.error('메시지 전송 실패', error)
  } finally {
    chatSending.value = false
  }
}

watch(status, (value) => {
  if (['CONFIRMED', 'DECLINED', 'CANCELLED'].includes(value)) {
    decisionFinalized.value = true
  }
})

watch(roomId, async (value) => {
  await subscribeChatRoom(value)
})

watch(
  () => shouldCreateOffer.value,
  async (value) => {
    if (value) {
      await createOfferIfNeeded()
    }
  },
)

onMounted(async () => {
  if (!status.value || status.value === 'IDLE') {
    router.replace({ name: 'match' })
    return
  }
  await subscribeMatchEvents()
  await subscribeSignalQueue()
  if (roomId.value) {
    await subscribeChatRoom(roomId.value)
  }
  if (shouldCreateOffer.value) {
    await createOfferIfNeeded()
  }
})

onBeforeUnmount(() => {
  if (matchUnsubscribe) matchUnsubscribe()
  if (signalUnsubscribe) signalUnsubscribe()
  if (chatUnsubscribe) chatUnsubscribe()
  if (peerConnection) {
    peerConnection.close()
    peerConnection = null
  }
  if (localStream) {
    localStream.getTracks().forEach((track) => track.stop())
    localStream = null
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

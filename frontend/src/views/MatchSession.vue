<template>
  <v-container class="session-container py-6">
    <v-row justify="center">
      <v-col cols="12" lg="11" xl="10">
        <v-card class="session-card pa-6">
          <div class="session-header d-flex align-center">
            <v-avatar size="48" class="me-3">
              <v-img :src="partnerAvatar" alt="partner" />
            </v-avatar>
            <div class="d-flex flex-column">
              <span class="text-subtitle-1 font-weight-medium text-pink-darken-2">
                {{ partnerNameDisplay }}님과 연결되었습니다
              </span>
              <span class="text-caption text-medium-emphasis">
                {{ headerStatusText }}
              </span>
            </div>
            <v-spacer />
            <v-btn variant="outlined" color="grey" @click="leaveSession">
              나가기
            </v-btn>
          </div>

          <div class="session-content">
            <div class="stage-wrapper">
              <div class="stage-surface">
                <video
                  ref="remoteVideo"
                  class="remote-video"
                  autoplay
                  playsinline
                ></video>

                <div v-if="!hasRemoteStream" class="stage-placeholder">
                  <v-progress-circular indeterminate color="pink" size="32" />
                  <span class="text-body-2 text-medium-emphasis mt-3">
                    상대의 연결을 기다리는 중이에요…
                  </span>
                </div>

                <video
                  ref="localVideo"
                  class="local-video"
                  muted
                  autoplay
                  playsinline
                ></video>

                <div class="follow-action-group">
                  <template v-if="followActionRequired">
                    <v-btn
                      class="follow-btn"
                      color="success"
                      variant="flat"
                      prepend-icon="mdi-heart"
                      :loading="followActionLoading && followActionType === 'accept'"
                      :disabled="followActionLoading"
                      @click="() => respondFollow(true)"
                    >
                      수락
                    </v-btn>
                    <v-btn
                      class="follow-btn decline"
                      color="grey"
                      variant="outlined"
                      prepend-icon="mdi-close"
                      :loading="followActionLoading && followActionType === 'decline'"
                      :disabled="followActionLoading"
                      @click="() => respondFollow(false)"
                    >
                      거절
                    </v-btn>
                  </template>
                  <v-chip
                    v-else-if="followAccepted"
                    color="pink-darken-1"
                    variant="flat"
                    prepend-icon="mdi-heart"
                    class="font-weight-semibold px-4 py-2"
                  >
                    팔로우 완료
                  </v-chip>
                  <v-btn
                    v-else
                    class="follow-btn"
                    :color="followButtonColor"
                    variant="flat"
                    :prepend-icon="followButtonIcon"
                    :loading="followLoading"
                    :disabled="followButtonDisabled || followLoading"
                    @click="handleFollow"
                  >
                    {{ followButtonLabel }}
                  </v-btn>
                </div>
              </div>
            </div>

            <div class="chat-column">
              <v-sheet class="chat-shell" color="#fff8fb" rounded="xl">
                <v-card
                  variant="outlined"
                  class="chat-card pa-4 d-flex flex-column"
                  rounded="xl"
                >
                  <ChatPanel
                    class="flex-grow-1"
                    :partner="partnerNameDisplay"
                    :messages="chatMessages"
                    :sending="isSendingChat"
                    :uploading="isUploadingFile"
                    :on-send="handleSendChatMessage"
                    :on-send-file="handleUploadFile"
                  />
                  <v-divider class="my-3" />
                  <div class="debug-panel">
                    <v-btn
                      size="small"
                      variant="text"
                      color="primary"
                      @click="fetchMatchStatus"
                    >
                      상태 확인
                    </v-btn>
                    <v-btn
                      size="small"
                      variant="text"
                      color="primary"
                      @click="logLocalState"
                    >
                      로그 출력
                    </v-btn>
                  </div>
                </v-card>
              </v-sheet>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ChatPanel from '../components/ChatPanel.vue'
import defaultAvatar from '../assets/default-avatar.svg'
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'
import { setupChat } from '../services/chat'
import { getIceServers } from '../services/turn'
import api from '../services/api'
import { useAuthStore } from '../stores/auth'
import { useFriendsStore } from '../stores/friends'
import { useMatchStore } from '../stores/match'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const matchStore = useMatchStore()
const friendsStore = useFriendsStore()

const localVideo = ref(null)
const remoteVideo = ref(null)
const localStream = ref(null)
const localMediaErrorMessage = ref('')
const hasRemoteStream = ref(false)
const chatMessages = ref([])
const isSendingChat = ref(false)
const isUploadingFile = ref(false)
const followLoading = ref(false)
const followActionLoading = ref(false)
const followActionType = ref(null)

const client = ref(null)
const connected = ref(false)
const lastSignal = ref(null)
const lastOffer = ref(null)
const lastAnswer = ref(null)
const lastIceCandidates = ref([])
const signalError = ref(null)

const makingOffer = ref(false)
const ignoreOffer = ref(false)
const settingRemoteAnswerPending = ref(false)
let iceRestartTimeout = null

function debugLog(label, payload) {
  // eslint-disable-next-line no-console
  console.log(`[match-session] ${label}`, payload)
}

function logLocalState() {
  debugLog('local-state', {
    shouldCreateOffer: matchStore.shouldCreateOffer,
    effectiveShouldCreateOffer: effectiveShouldCreateOffer.value,
    offerCreated: offerCreated.value,
    bothConfirmed: matchStore.bothConfirmed,
    lastSignal: lastSignal.value,
    lastOffer: lastOffer.value,
    lastAnswer: lastAnswer.value,
    iceCount: lastIceCandidates.value.length,
    requestId: matchStore.requestId,
    partnerRequestId: matchStore.partnerRequestId,
  })
}

function handleTokenRefresh(nextToken) {
  if (!nextToken) {
    return
  }
  auth.login({ token: nextToken, user: auth.user })
}
let matchSubscription = null
let followSubscription = null
let signalRoute = null
let pc = null
let audioTransceiver = null
let videoTransceiver = null
const offerCreated = ref(false)
const remoteDescriptionSet = ref(false)
let chatRoute = null
let chatRoomId = null

const initializationReady = ref(false)
const initializing = ref(false)
const sessionCleanupDone = ref(false)
const sessionClosedNavigated = ref(false)

const partnerAvatar = computed(() => defaultAvatar)
const partnerNameDisplay = computed(() => matchStore.partnerNickName || '상대 준비 중')
const isMatched = computed(() => matchStore.isMatched)
const headerStatusText = computed(() => matchStore.statusMessage || '대화를 시작해 보세요.')
const shouldInitialize = computed(
  () =>
    initializationReady.value &&
    !!matchStore.roomId &&
    !!matchStore.partnerLoginId &&
    !matchStore.sessionClosed
)
const effectiveShouldCreateOffer = computed(() => {
  if (matchStore.shouldCreateOffer) {
    return true
  }
  const myRequestId = matchStore.requestId
  const partnerRequestId = matchStore.partnerRequestId
  if (!myRequestId || !partnerRequestId) {
    return false
  }
  return myRequestId > partnerRequestId
})
const meLoginId = computed(() => auth.user?.loginId || auth.user?.login_id || auth.user?.loginID || null)
const meNickName = computed(() => auth.user?.nickName || auth.user?.nickname || auth.user?.nick_name || '')
const isPolitePeer = computed(() => {
  const myId = meLoginId.value || ''
  const partnerId = matchStore.partnerLoginId || ''
  if (!myId || !partnerId) {
    return false
  }
  return myId.localeCompare(partnerId) > 0
})
const followStatus = computed(() => matchStore.followStatus)
const followDirection = computed(() => matchStore.followDirection)
const followActionRequired = computed(() => matchStore.needsFollowAction)
const followAccepted = computed(() => matchStore.followAccepted)
const followButtonLabel = computed(() => {
  if (followAccepted.value) {
    return '팔로우 완료'
  }
  if (followStatus.value === 'PENDING') {
    if (followDirection.value === 'OUTGOING') {
      return '수락 대기중'
    }
    if (followDirection.value === 'INCOMING') {
      return '팔로우 요청됨'
    }
    return '팔로우 대기중'
  }
  if (followStatus.value === 'DECLINED') {
    return '다시 팔로우'
  }
  return '팔로우'
})
const followButtonDisabled = computed(() => {
  if (followAccepted.value) {
    return true
  }
  if (followStatus.value === 'PENDING') {
    if (followDirection.value === 'OUTGOING') {
      return true
    }
    if (followDirection.value === 'INCOMING') {
      return true
    }
  }
  return false
})
const followButtonIcon = computed(() => (followAccepted.value ? 'mdi-heart' : 'mdi-heart-outline'))
const followButtonColor = computed(() => (followAccepted.value ? 'pink-darken-1' : 'success'))

async function initializeSession() {
  if (initializing.value) {
    return false
  }
  initializing.value = true
  try {
    const routeRoomId = Number.parseInt(route.params.roomId, 10)
    const requestIdParam = route.query.requestId ? Number.parseInt(route.query.requestId, 10) : null
    let activeRequestId = matchStore.requestId ?? requestIdParam

    if (!activeRequestId) {
      router.replace({ name: 'match' })
      return false
    }

    if (!matchStore.roomId || (Number.isFinite(routeRoomId) && matchStore.roomId !== routeRoomId)) {
      const { data } = await api.get(`/match/requests/${activeRequestId}`)
      if (!data || data.state !== 'MATCHED' || !data.roomId) {
        router.replace({ name: 'match-result' })
        return false
      }
      matchStore.setFromStartResponse(data)
    }

    if (Number.isFinite(routeRoomId) && matchStore.roomId && matchStore.roomId !== routeRoomId) {
      router.replace({ name: 'match-result' })
      return false
    }

    if (!matchStore.roomId || !matchStore.partnerLoginId) {
      router.replace({ name: 'match-result' })
      return false
    }

    if (!matchStore.bothConfirmed) {
      router.replace({ name: 'match-result' })
      return false
    }

    initializationReady.value = true
    return true
  } catch (error) {
    console.error('매칭 세션 초기화 실패', error)
    router.replace({ name: 'match-result' })
    return false
  } finally {
    initializing.value = false
  }
}

async function initLocalMedia() {
  if (typeof window === 'undefined') {
    return
  }

  const isSecure = window.isSecureContext
  const hasNavigator = typeof navigator !== 'undefined'
  const mediaDevices = hasNavigator ? navigator.mediaDevices : undefined
  const canUseGetUserMedia = !!(mediaDevices && typeof mediaDevices.getUserMedia === 'function')

  if (!isSecure) {
    const message = '보안 연결(HTTPS)에서 접속해야 카메라와 마이크를 사용할 수 있습니다.'
    localMediaErrorMessage.value = message
    matchStore.statusMessage = message
    return
  }

  if (!canUseGetUserMedia) {
    const message = '이 브라우저에서는 카메라 또는 마이크 접근을 지원하지 않습니다. 다른 브라우저에서 시도해 주세요.'
    localMediaErrorMessage.value = message
    matchStore.statusMessage = message
    return
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    localStream.value = stream
    if (localVideo.value) {
      localVideo.value.srcObject = stream
      await nextTick()
      try {
        await localVideo.value.play()
      } catch (error) {
        console.warn('로컬 영상 자동 재생 실패', error)
      }
    }
    const previousErrorMessage = localMediaErrorMessage.value
    localMediaErrorMessage.value = ''
    if (previousErrorMessage && matchStore.statusMessage === previousErrorMessage) {
      matchStore.statusMessage = ''
    }
    syncLocalTracksToPeerConnection()
  } catch (error) {
    console.error('로컬 미디어 초기화 실패', error)
    localStream.value = null
    if (localVideo.value) {
      localVideo.value.srcObject = null
    }
    let message = '카메라 또는 마이크 접근이 차단되었습니다. 브라우저 설정을 확인해 주세요.'
    if (error && typeof error === 'object') {
      const errorName = error.name
      if (errorName === 'NotAllowedError' || errorName === 'SecurityError') {
        message = '브라우저에서 카메라 또는 마이크 접근이 차단되었습니다. 권한을 허용한 뒤 다시 시도하세요.'
      } else if (errorName === 'NotFoundError' || errorName === 'OverconstrainedError') {
        message = '연결된 카메라 또는 마이크를 찾을 수 없습니다. 장치를 확인한 뒤 다시 시도하세요.'
      } else if (errorName === 'NotReadableError') {
        message = '다른 프로그램이 카메라 또는 마이크를 사용 중입니다. 사용 중인 앱을 종료하고 다시 시도하세요.'
      }
    }
    localMediaErrorMessage.value = message
    matchStore.statusMessage = message
  }
}

function syncLocalTracksToPeerConnection() {
  if (!pc) {
    return
  }

  const stream = localStream.value
  const audioTrack =
    stream && typeof stream.getAudioTracks === 'function'
      ? stream.getAudioTracks()[0] || null
      : null
  const videoTrack =
    stream && typeof stream.getVideoTracks === 'function'
      ? stream.getVideoTracks()[0] || null
      : null

  if (!audioTransceiver) {
    audioTransceiver = pc.addTransceiver('audio', { direction: audioTrack ? 'sendrecv' : 'recvonly' })
  }
  if (!videoTransceiver) {
    videoTransceiver = pc.addTransceiver('video', { direction: videoTrack ? 'sendrecv' : 'recvonly' })
  }

  if (audioTransceiver) {
    audioTransceiver.direction = audioTrack ? 'sendrecv' : 'recvonly'
    const sender = audioTransceiver.sender
    if (sender) {
      sender
        .replaceTrack(audioTrack)
        .catch((replaceError) => {
          console.error('로컬 오디오 트랙 동기화 실패', replaceError)
        })
      if (typeof sender.setStreams === 'function') {
        if (stream && audioTrack) {
          sender.setStreams(stream)
        } else if (!audioTrack) {
          sender.setStreams()
        }
      }
    }
  }

  if (videoTransceiver) {
    videoTransceiver.direction = videoTrack ? 'sendrecv' : 'recvonly'
    const sender = videoTransceiver.sender
    if (sender) {
      sender
        .replaceTrack(videoTrack)
        .catch((replaceError) => {
          console.error('로컬 비디오 트랙 동기화 실패', replaceError)
        })
      if (typeof sender.setStreams === 'function') {
        if (stream && videoTrack) {
          sender.setStreams(stream)
        } else if (!videoTrack) {
          sender.setStreams()
        }
      }
    }
  }
}

function ensureChatRoute() {
  if (!connected.value || !client.value || !matchStore.roomId || !shouldInitialize.value) {
    if (!matchStore.roomId) {
      chatMessages.value = []
    }
    teardownChatRoute()
    return
  }

  if (chatRoute && chatRoomId === matchStore.roomId) {
    return
  }

  const shouldReset = chatRoomId !== matchStore.roomId
  teardownChatRoute()
  if (shouldReset) {
    chatMessages.value = []
  }
  chatRoute = setupChat(client.value, matchStore.roomId, {
    onChat: handleIncomingChatMessage,
  })
  chatRoomId = matchStore.roomId
}

function teardownChatRoute() {
  if (chatRoute?.unsubscribe) {
    chatRoute.unsubscribe()
  } else if (chatRoute?.subscription?.unsubscribe) {
    chatRoute.subscription.unsubscribe()
  }
  chatRoute = null
  chatRoomId = null
}

async function ensurePeerConnection() {
  if (!shouldInitialize.value || !connected.value) {
    return
  }
  if (!matchStore.partnerLoginId || !meLoginId.value) {
    return
  }

  if (!pc) {
    const iceServers = await getIceServers()

    if (pc) {
      // 다른 비동기 ensurePeerConnection 호출이 이미 RTCPeerConnection을 준비했습니다.
      // 아래 로직은 기존 연결에 대해 계속 진행합니다.
    } else {
      pc = new RTCPeerConnection({ iceServers })
      pc.onnegotiationneeded = async () => {
        if (!pc || !matchStore.partnerLoginId) {
          return
        }
        if (!isPolitePeer.value && pc.signalingState !== 'stable') {
          debugLog('negotiation-skipped', { reason: 'impolite-and-unstable' })
          return
        }
        await renegotiate({ iceRestart: false })
      }
      pc.ontrack = (event) => {
        const track = event.track
        let [stream] = event.streams

        if (!stream) {
          const current = remoteVideo.value?.srcObject
          if (current instanceof MediaStream) {
            stream = current
          } else {
            stream = new MediaStream()
          }
          if (track && !stream.getTracks().includes(track)) {
            stream.addTrack(track)
          }
        }

        if (remoteVideo.value && stream) {
          if (remoteVideo.value.srcObject !== stream) {
            remoteVideo.value.srcObject = stream
          }
          const hasActiveTrack = stream.getTracks().some((mediaTrack) => mediaTrack.readyState !== 'ended')
          hasRemoteStream.value = hasActiveTrack || stream.getTracks().length > 0
          Promise.resolve()
            .then(() => remoteVideo.value?.play?.())
            .catch((error) => {
              console.warn('원격 영상 자동 재생 실패', error)
            })
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
      pc.oniceconnectionstatechange = () => {
        if (!pc) {
          return
        }
        const state = pc.iceConnectionState
        debugLog('ice-state', state)
        if (state === 'failed' || state === 'disconnected') {
          hasRemoteStream.value = false
          scheduleIceRestart(state)
        } else if (state === 'closed') {
          hasRemoteStream.value = false
        } else if (state === 'connected' || state === 'completed') {
          if (iceRestartTimeout) {
            clearTimeout(iceRestartTimeout)
            iceRestartTimeout = null
          }
        }
      }
    }
  }

  syncLocalTracksToPeerConnection()

  if (!signalRoute) {
    signalRoute = setupSignalRoutes(client.value, {
      me: meLoginId.value,
      onSignal: handleSignal,
      onError: handleSignalError,
    })
  }

  if (effectiveShouldCreateOffer.value && !offerCreated.value) {
    await renegotiate({ iceRestart: false })
  }
}

function scheduleIceRestart(reason) {
  if (iceRestartTimeout || !pc) {
    return
  }
  iceRestartTimeout = setTimeout(async () => {
    iceRestartTimeout = null
    if (!pc) {
      return
    }
    debugLog('ice-restart', reason)
    try {
      if (typeof pc.restartIce === 'function') {
        pc.restartIce()
      }
    } catch (error) {
      console.warn('ICE restart invocation failed', error)
    }
    await renegotiate({ iceRestart: true })
  }, 1500)
}

async function renegotiate({ iceRestart = false } = {}) {
  if (!pc || !signalRoute || !matchStore.partnerLoginId) {
    return
  }
  if (makingOffer.value) {
    return
  }
  try {
    makingOffer.value = true
    const offer = await pc.createOffer(iceRestart ? { iceRestart: true } : undefined)
    lastOffer.value = offer
    debugLog('create-offer', { offer, iceRestart })
    await pc.setLocalDescription(offer)
    const description = pc.localDescription || offer
    signalRoute.sendSignal({
      type: 'offer',
      receiverLoginId: matchStore.partnerLoginId,
      data: description,
    })
    offerCreated.value = true
  } catch (error) {
    console.error('WebRTC negotiation 실패', error)
  } finally {
    makingOffer.value = false
  }
}

async function handleSignal(message) {
  if (!message) {
    return
  }
  lastSignal.value = message
  debugLog('signal-received', message)
  signalError.value = null
  if (!pc) {
    await ensurePeerConnection()
  }
  if (!pc) {
    return
  }

  try {
    if (message.type === 'offer' && message.data) {
      const offerCollision = makingOffer.value || pc.signalingState !== 'stable'
      ignoreOffer.value = !isPolitePeer.value && offerCollision
      debugLog('offer-received', { offerCollision, ignore: ignoreOffer.value })
      if (ignoreOffer.value) {
        return
      }

      settingRemoteAnswerPending.value = true
      await pc.setRemoteDescription(message.data)
      remoteDescriptionSet.value = true
      const answer = await pc.createAnswer()
      lastAnswer.value = answer
      debugLog('create-answer', answer)
      await pc.setLocalDescription(answer)
      const description = pc.localDescription || answer
      signalRoute?.sendSignal({
        type: 'answer',
        receiverLoginId: matchStore.partnerLoginId,
        data: description,
      })
      offerCreated.value = true
      ignoreOffer.value = false
    } else if (message.type === 'answer' && message.data) {
      if (ignoreOffer.value) {
        debugLog('answer-ignored', message)
        return
      }
      await pc.setRemoteDescription(message.data)
      remoteDescriptionSet.value = true
      offerCreated.value = true
      ignoreOffer.value = false
    } else if (message.type === 'ice-candidate' && message.data) {
      lastIceCandidates.value = [...lastIceCandidates.value, message.data]
      debugLog('ice-candidate-received', message.data)
      const applyCandidate = async () => {
        try {
          await pc.addIceCandidate(message.data)
        } catch (error) {
          console.error('ICE candidate 적용 실패', error)
        }
      }
      if (!remoteDescriptionSet.value) {
        setTimeout(() => {
          void applyCandidate()
        }, 100)
        return
      }
      await applyCandidate()
    }
  } catch (error) {
    console.error('시그널 처리 실패', error)
    signalError.value = error
  } finally {
    if (message.type === 'offer') {
      settingRemoteAnswerPending.value = false
    }
  }
}

function handleSignalError(payload) {
  signalError.value = payload
  const message = payload?.message || '시그널 처리 중 오류가 발생했습니다.'
  matchStore.statusMessage = message
  if (import.meta.env.DEV) {
    console.warn('[match-session] signal-error', payload)
  }
}

function teardownPeerConnection() {
  if (signalRoute?.unsubscribe) {
    signalRoute.unsubscribe()
  } else {
    signalRoute?.sub?.unsubscribe?.()
    signalRoute?.errorSub?.unsubscribe?.()
  }
  signalRoute = null
  if (iceRestartTimeout) {
    clearTimeout(iceRestartTimeout)
    iceRestartTimeout = null
  }
  if (pc) {
    try {
      pc.close()
    } catch (error) {
      console.warn('RTCPeerConnection close failed', error)
    }
    pc = null
  }
  audioTransceiver = null
  videoTransceiver = null
  offerCreated.value = false
  makingOffer.value = false
  ignoreOffer.value = false
  settingRemoteAnswerPending.value = false
  signalError.value = null
  if (remoteVideo.value) {
    remoteVideo.value.srcObject = null
  }
  hasRemoteStream.value = false
  remoteDescriptionSet.value = false
}

async function handleSendChatMessage(text) {
  if (!text) {
    return
  }
  if (!client.value || !connected.value || !matchStore.roomId) {
    throw new Error('채팅방이 아직 준비되지 않았습니다.')
  }

  try {
    isSendingChat.value = true
    if (chatRoute?.sendChat) {
      chatRoute.sendChat({ content: text })
    } else {
      client.value.publish({
        destination: `/app/chat.sendMessage/${matchStore.roomId}`,
        body: JSON.stringify({ roomId: matchStore.roomId, content: text }),
      })
    }
  } catch (error) {
    console.error('채팅 메시지 전송 실패', error)
    throw error instanceof Error ? error : new Error('채팅 메시지 전송에 실패했습니다.')
  } finally {
    isSendingChat.value = false
  }
}

async function handleUploadFile(file) {
  if (!file) {
    return
  }
  if (!matchStore.roomId) {
    throw new Error('채팅방이 아직 준비되지 않았습니다.')
  }

  const formData = new FormData()
  formData.append('file', file)

  try {
    isUploadingFile.value = true
    await api.post(`/chat/rooms/${matchStore.roomId}/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  } catch (error) {
    console.error('파일 업로드 실패', error)
    throw error instanceof Error ? error : new Error('파일 업로드 중 오류가 발생했습니다.')
  } finally {
    isUploadingFile.value = false
  }
}

function handleIncomingChatMessage(payload) {
  if (!payload) {
    return
  }
  try {
    const sentAt = payload.sentAt ? new Date(payload.sentAt) : new Date()
    const messageId = payload.messageId ?? payload.message_id ?? null
    const senderLoginId = payload.senderLoginId ?? payload.sender_login_id ?? null
    let sizeValue = null
    if (typeof payload.sizeBytes === 'number') {
      sizeValue = payload.sizeBytes
    } else if (payload.sizeBytes !== null && payload.sizeBytes !== undefined) {
      const parsed = Number(payload.sizeBytes)
      if (Number.isFinite(parsed)) {
        sizeValue = parsed
      }
    }
    const computedId =
      messageId ?? `${payload.roomId ?? ''}-${sentAt.getTime()}-${Math.random().toString(36).slice(2, 8)}`
    const isFromMe = senderLoginId
      ? senderLoginId === meLoginId.value
      : !!payload.senderNickName && payload.senderNickName === meNickName.value
    chatMessages.value.push({
      id: computedId,
      messageId,
      roomId: payload.roomId,
      senderLoginId,
      senderNickName: payload.senderNickName,
      content: payload.content ?? '',
      translatedContent: payload.translatedContent ?? '',
      contentType: payload.contentType ?? 'TEXT',
      fileName: payload.fileName ?? '',
      fileUrl: payload.fileUrl ?? '',
      mimeType: payload.mimeType ?? '',
      sizeBytes: Number.isFinite(sizeValue) ? sizeValue : null,
      sentAt,
      fromMe: isFromMe,
    })
  } catch (error) {
    console.error('채팅 메시지 처리 실패', error)
  }
}

function handleMatchMessage(frame) {
  try {
    const payload = JSON.parse(frame.body)
    matchStore.applyMatchEvent(payload)
    if (matchStore.sessionClosed) {
      handleSessionClosed()
      return
    }
    ensureChatRoute()
    void ensurePeerConnection()
  } catch (error) {
    console.error('매칭 이벤트 처리 실패', error)
  }
}

function handleFollowMessage(frame) {
  try {
    const payload = JSON.parse(frame.body)
    matchStore.applyFollowEvent(payload)
    if (payload?.eventType === 'ACCEPTED' || payload?.follow?.accepted) {
      void friendsStore.refreshFromServer().catch(() => {})
    }
  } catch (error) {
    console.error('팔로우 이벤트 처리 실패', error)
  }
}

function cleanupSession() {
  if (sessionCleanupDone.value) {
    return
  }
  sessionCleanupDone.value = true
  initializationReady.value = false
  teardownPeerConnection()
  teardownChatRoute()
  stopLocalStream()
  matchStore.shouldCreateOffer = false
}

function handleSessionClosed() {
  if (sessionClosedNavigated.value) {
    return
  }
  sessionClosedNavigated.value = true
  cleanupSession()
  router.replace({ name: 'match-result' })
}

function stopLocalStream() {
  if (localStream.value) {
    localStream.value.getTracks().forEach((track) => track.stop())
    localStream.value = null
  }
  if (localVideo.value) {
    localVideo.value.srcObject = null
  }
}

async function loadFollowStatus() {
  if (!matchStore.roomId) {
    matchStore.resetFollow()
    return
  }
  try {
    const { data } = await api.get(`/follows/rooms/${matchStore.roomId}`)
    if (data) {
      matchStore.setFollowState(data)
      if (data.accepted) {
        await friendsStore.refreshFromServer().catch(() => {})
      }
    }
  } catch (error) {
    console.error('팔로우 상태 조회 실패', error)
  }
}

async function respondFollow(accept) {
  if (!matchStore.followRequestId) {
    return
  }
  if (followActionLoading.value) {
    return
  }
  followActionLoading.value = true
  followActionType.value = accept ? 'accept' : 'decline'
  try {
    const endpoint = `/follows/${matchStore.followRequestId}/${accept ? 'accept' : 'decline'}`
    const { data } = await api.post(endpoint)
    if (data) {
      matchStore.setFollowState(data)
      if (data.accepted) {
        await friendsStore.refreshFromServer().catch(() => {})
      }
    }
  } catch (error) {
    console.error('팔로우 응답 실패', error)
    const message =
      error?.response?.data?.message ||
      error?.message ||
      (accept ? '팔로우 수락에 실패했습니다.' : '팔로우 거절에 실패했습니다.')
    window.alert(message)
  } finally {
    followActionLoading.value = false
    followActionType.value = null
  }
}

async function handleFollow() {
  if (followLoading.value || followButtonDisabled.value) {
    return
  }
  if (!matchStore.roomId) {
    window.alert('채팅방이 준비되지 않았습니다.')
    return
  }
  followLoading.value = true
  try {
    const { data } = await api.post('/follows', { roomId: matchStore.roomId })
    if (data) {
      matchStore.setFollowState(data)
    }
  } catch (error) {
    console.error('팔로우 요청 실패', error)
    const message = error?.response?.data?.message || error?.message || '팔로우 요청에 실패했습니다.'
    window.alert(message)
  } finally {
    followLoading.value = false
  }
}

async function fetchMatchStatus() {
  if (!matchStore.requestId) {
    window.alert('매칭 요청 ID가 없습니다.')
    return
  }
  try {
    const { data } = await api.get(`/match/requests/${matchStore.requestId}`)
    debugLog('match-status', data)
  } catch (error) {
    console.error('매칭 상태 조회 실패', error)
  }
}

function leaveSession() {
  sessionClosedNavigated.value = true
  cleanupSession()
  matchStore.reset()
  router.replace({ name: 'match' })
}

watch(
  () => [connected.value, shouldInitialize.value, matchStore.partnerLoginId, effectiveShouldCreateOffer.value],
  () => {
    if (!shouldInitialize.value || !connected.value) {
      return
    }
    void ensurePeerConnection()
  }
)

watch(
  () => matchStore.sessionClosed,
  (closed) => {
    if (closed) {
      handleSessionClosed()
    }
  }
)

watch(
  () => [connected.value, matchStore.roomId, shouldInitialize.value],
  () => {
    if (!shouldInitialize.value) {
      teardownChatRoute()
      return
    }
    ensureChatRoute()
  }
)

watch(
  () => matchStore.roomId,
  (roomId) => {
    if (roomId && shouldInitialize.value) {
      void loadFollowStatus()
    } else if (!roomId) {
      matchStore.resetFollow()
    }
  }
)

watch(
  () => shouldInitialize.value,
  (ready) => {
    if (ready && matchStore.roomId) {
      void loadFollowStatus()
    }
  }
)

onMounted(async () => {
  const ready = await initializeSession()
  if (!ready) {
    return
  }

  await loadFollowStatus()
  await initLocalMedia()

  const refreshTokenFn = typeof auth.refreshToken === 'function' ? auth.refreshToken.bind(auth) : undefined

  client.value = createStompClient({
    token: auth.token,
    refreshToken: refreshTokenFn,
    onTokenRefreshed: handleTokenRefresh,
  })
  client.value.onConnect = () => {
    connected.value = true
    matchSubscription = client.value.subscribe('/user/queue/match-results', handleMatchMessage)
    followSubscription = client.value.subscribe('/user/queue/follow-events', handleFollowMessage)
    ensureChatRoute()
    void ensurePeerConnection()
  }
  client.value.onDisconnect = () => {
    connected.value = false
    matchSubscription?.unsubscribe()
    matchSubscription = null
    followSubscription?.unsubscribe()
    followSubscription = null
    teardownPeerConnection()
    teardownChatRoute()
  }
  client.value.activate()

  ensureChatRoute()
  void ensurePeerConnection()
})

onBeforeUnmount(() => {
  matchSubscription?.unsubscribe()
  matchSubscription = null
  followSubscription?.unsubscribe()
  followSubscription = null
  if (signalRoute?.unsubscribe) {
    signalRoute.unsubscribe()
  } else {
    signalRoute?.sub?.unsubscribe?.()
    signalRoute?.errorSub?.unsubscribe?.()
  }
  signalRoute = null
  client.value?.deactivate?.()
  cleanupSession()
})
</script>

<style scoped>
.session-card {
  background: linear-gradient(135deg, #ffffff 0%, #fff6fb 100%);
  border-radius: 28px;
  box-shadow: 0 24px 60px rgba(243, 198, 217, 0.35);
}

.session-header {
  column-gap: 16px;
  margin-bottom: 28px;
}

.session-content {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
}

.stage-wrapper {
  flex: 1 1 520px;
  min-width: 0;
}

.stage-surface {
  position: relative;
  background: rgba(255, 247, 250, 0.92);
  border: 2px solid #ffdbe6;
  border-radius: 28px;
  padding: 28px;
  min-height: 430px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 20px;
  background: #080808;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.45);
}

.stage-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.88);
  text-align: center;
  padding: 24px;
  z-index: 2;
}

.local-video {
  position: absolute;
  right: 32px;
  bottom: 32px;
  width: 220px;
  height: 150px;
  object-fit: cover;
  border-radius: 20px;
  border: 3px solid rgba(255, 255, 255, 0.85);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.45);
  background: #dfffea;
  z-index: 3;
}

.follow-action-group {
  position: absolute;
  left: 32px;
  bottom: 32px;
  display: flex;
  gap: 12px;
  align-items: center;
  z-index: 3;
}

.follow-btn {
  border-radius: 999px;
  padding-inline: 22px;
  font-weight: 600;
  box-shadow: 0 18px 32px rgba(42, 157, 143, 0.18);
}

.follow-btn.decline {
  color: #5f6368 !important;
  background: #ffffff !important;
  box-shadow: none;
}

.chat-column {
  flex: 0 0 320px;
  max-width: 100%;
}

.chat-shell {
  height: 100%;
  padding: 18px;
  background: rgba(255, 240, 248, 0.85);
  backdrop-filter: blur(12px);
}

.chat-card {
  height: 100%;
  border-color: #ffd7e6;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: none;
}

.debug-panel {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

@media (max-width: 1264px) {
  .session-content {
    flex-direction: column;
  }

  .chat-column {
    flex: 1 1 auto;
  }
}

@media (max-width: 960px) {
  .stage-surface {
    padding: 18px;
  }

  .local-video {
    width: 160px;
    height: 110px;
    right: 20px;
    bottom: 20px;
  }

  .follow-btn {
    left: 20px;
    bottom: 20px;
  }
}
</style>

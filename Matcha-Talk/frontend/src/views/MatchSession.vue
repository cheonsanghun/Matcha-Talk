<template>
  <v-container fluid class="match-session py-6">
    <v-row class="mb-4">
      <v-col cols="12">
        <v-card class="pa-4 session-header" variant="outlined">
          <div class="d-flex align-center flex-wrap ga-4">
            <div class="d-flex align-center ga-3">
              <v-avatar size="56">
                <v-icon size="36" color="pink-darken-1">mdi-account-heart</v-icon>
              </v-avatar>
              <div>
                <div class="text-h6">{{ partnerName || '매칭 상대 확인 중' }}</div>
                <div class="text-caption text-medium-emphasis">
                  {{ partnerLoginId || '상대 아이디 정보를 기다리고 있습니다.' }}
                </div>
              </div>
            </div>
            <v-chip
              color="pink"
              variant="tonal"
              class="text-caption font-weight-medium"
            >
              {{ sessionChipLabel }}
            </v-chip>
            <v-spacer />
            <div class="d-flex ga-2 flex-wrap">
              <v-btn
                :color="followButtonConfig.color"
                :variant="followButtonConfig.variant"
                :loading="followButtonConfig.loading"
                :disabled="followButtonConfig.disabled"
                @click="handleFollowButtonClick"
              >
                {{ followButtonConfig.label }}
              </v-btn>
              <v-btn color="secondary" variant="outlined" @click="goBackToMatching">
                새 매칭 찾기
              </v-btn>
            </div>
          </div>
          <div class="text-caption text-medium-emphasis mt-3">
            {{ sessionStatusMessage }}
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="12">
        <v-card class="session-card" variant="outlined">
          <div class="session-content">
            <div class="video-pane">
              <VideoChat ref="videoChatRef" class="session-video" />
              <div class="video-actions">
                <v-btn
                  class="session-pill video-start-btn"
                  variant="flat"
                  rounded="pill"
                  :loading="callRequestInFlight || callStartInProgress"
                  :disabled="!canStartCall || callRequestInFlight || callStartInProgress || callActive"
                  @click="startVideoCall"
                >
                  <v-icon start size="20">mdi-video</v-icon>
                  <span>
                    {{
                      callActive
                        ? '통화 중'
                        : callReady
                          ? (remoteCallReady ? '통화 연결 중...' : '상대 대기 중...')
                          : '영상 통화 시작'
                    }}
                  </span>
                </v-btn>
                <v-btn
                  class="session-pill hangup-btn"
                  variant="flat"
                  rounded="pill"
                  :disabled="!callActive"
                  @click="hangUpCall"
                >
                  <v-icon start size="20">mdi-phone-hangup</v-icon>
                  <span>통화 종료</span>
                </v-btn>
              </div>
            </div>
            <div class="chat-pane">
              <div class="chat-header d-flex align-center justify-space-between pa-4">
                <div>
                  <div class="text-subtitle-1 font-weight-medium">
                    {{ partnerName || '대화를 시작해보세요' }}
                  </div>
                  <div class="text-caption text-medium-emphasis">
                    채팅방 번호: {{ roomIdDisplay }}
                  </div>
                </div>
              </div>
              <div class="chat-messages" ref="chatMessagesContainer">
                <div
                  v-if="!roomId"
                  class="h-100 d-flex align-center justify-center text-caption text-medium-emphasis"
                >
                  채팅방 정보를 불러오는 중입니다.
                </div>
                <div v-else class="chat-messages__list">
                  <div
                    v-for="(message, index) in messages"
                    :key="message.id || index"
                    class="message-row"
                    :class="{ 'message-row--me': message.me }"
                  >
                    <div class="message-bubble" :class="bubbleClass(message)">
                      <template v-if="message.contentType === 'IMAGE' && message.fileUrl">
                        <img :src="message.fileUrl" :alt="message.fileName || '이미지'" />
                        <div v-if="message.fileName" class="text-caption mt-1">
                          {{ message.fileName }}
                        </div>
                      </template>
                      <template v-else-if="message.contentType === 'FILE' && message.fileUrl">
                        <a
                          :href="message.fileUrl"
                          class="file-link"
                          target="_blank"
                          rel="noopener"
                          @click.prevent="downloadAttachment(message)"
                        >
                          <v-icon size="18" class="me-1">mdi-paperclip</v-icon>
                          {{ message.fileName || message.text || '파일 다운로드' }}
                        </a>
                      </template>
                      <template v-else>
                        {{ message.text }}
                      </template>
                    </div>
                    <div class="text-caption text-medium-emphasis mt-1" :class="{ 'text-right': message.me }">
                      <span v-if="!message.me && message.sender" class="me-2">{{ message.sender }}</span>
                      <span>{{ message.time }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="chat-input pa-4">
                <input type="file" ref="fileInput" class="d-none" @change="handleFileSelect" />
                <div class="d-flex ga-2 align-center">
                  <v-btn
                    class="session-pill chat-action-btn session-file-btn"
                    variant="flat"
                    rounded="pill"
                    :disabled="!roomId"
                    @click="triggerFilePicker"
                  >
                    <v-icon size="22">mdi-file-outline</v-icon>
                  </v-btn>
                  <v-text-field
                    v-model="draft"
                    variant="outlined"
                    density="comfortable"
                    hide-details
                    class="flex-grow-1"
                    :disabled="!roomId"
                    placeholder="메시지를 입력하세요..."
                    @keydown.enter.prevent="send"
                  />
                  <v-btn
                    class="session-pill chat-action-btn session-send-btn"
                    variant="flat"
                    rounded="pill"
                    :disabled="!roomId || !draft.trim()"
                    @click="send"
                  >
                    <v-icon start size="18">mdi-send</v-icon>
                    <span>보내기</span>
                  </v-btn>
                </div>
              </div>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VideoChat from '../components/VideoChat.vue'
import { useAuthStore } from '../stores/auth'
import { useMatchStore } from '../stores/match'
import api from '../services/api'
import followService from '../services/follow'
import { camelizeKeys } from '../utils/case'
import { resolveClientIdentity } from '../utils/identity'
import {
  createFileSelectHandler,
  createIncomingMessageHandler,
  normalizeAttachmentUrl,
  useRealtimeChatClient,
} from '../composables/useChatClient'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const matchStore = useMatchStore()

const roomId = ref(null)
const partnerName = ref('')
const partnerLoginId = ref('')
const partnerUserPid = ref(null)
const sessionStatus = ref('매칭 세션을 불러오는 중입니다...')
const handshakeStatus = ref(null)
const conversations = ref({})
const roomInfo = ref(null)
const participantLoginIds = ref([])

const draft = ref('')
const chatMessagesContainer = ref(null)
const fileInput = ref(null)
const videoChatRef = ref(null)
const callActive = ref(false)
const callRequestInFlight = ref(false)
const callStartInProgress = ref(false)
const callReady = ref(false)
const remoteCallReady = ref(false)
const historyLoadedRooms = ref(new Set())

const followState = reactive({
  status: null,
  relationId: null,
  incomingId: null,
  incomingStatus: null,
  outgoingId: null,
  outgoingStatus: null,
  mutual: false,
})
const followRequestLoading = ref(false)
const followAcceptLoading = ref(false)
const pendingIncomingId = ref(null)
const roomTemporary = ref(null)
const cleanupState = reactive({ running: false, completed: false })

const messages = computed(() => {
  if (!roomId.value) return []
  return conversations.value[roomId.value] || []
})

const roomIdDisplay = computed(() => {
  if (!roomId.value) return '확인 중'
  return `#${roomId.value}`
})

const handshakeStatusUpper = computed(() => (handshakeStatus.value || '').toString().toUpperCase())

const handshakeStatusLabel = computed(() => {
  switch (handshakeStatusUpper.value) {
    case 'CONFIRMED':
      return '채팅 진행 중'
    case 'MATCHED':
      return '상대 수락 대기'
    case 'CANCELLED':
      return '매칭 종료'
    case 'DECLINED':
      return '매칭 거절됨'
    default:
      return '상태 확인 중'
  }
})

const followStatusUpper = computed(() => (followState.status || '').toString().toUpperCase())
const incomingStatusUpper = computed(() => (followState.incomingStatus || '').toString().toUpperCase())
const outgoingStatusUpper = computed(() => (followState.outgoingStatus || '').toString().toUpperCase())
const hasMutualFollow = computed(() => {
  if (followState.mutual) return true
  if (incomingStatusUpper.value === 'ACCEPTED' && outgoingStatusUpper.value === 'ACCEPTED') {
    return true
  }
  return followStatusUpper.value === 'ACCEPTED'
})
const canRequestFollow = computed(() => {
  if (partnerUserPid.value == null) return false
  if (hasMutualFollow.value) return false
  const outgoing = outgoingStatusUpper.value
  if (outgoing === 'PENDING' || outgoing === 'ACCEPTED') return false
  return true
})
const canAcceptFollow = computed(() => {
  if (incomingStatusUpper.value !== 'PENDING') return false
  return Boolean(followState.incomingId || followState.relationId || pendingIncomingId.value)
})

const followInteractionStage = computed(() => {
  if (hasMutualFollow.value) return 'COMPLETED'
  if (incomingStatusUpper.value === 'PENDING') return 'INCOMING'
  if (outgoingStatusUpper.value === 'PENDING') return 'OUTGOING'
  if (
    (handshakeStatusUpper.value === 'CONFIRMED' || Boolean(matchStore.bootstrap?.chatReady)) &&
    canRequestFollow.value
  ) {
    return 'READY'
  }
  return null
})

const sessionChipLabel = computed(() => {
  switch (followInteractionStage.value) {
    case 'COMPLETED':
      return '서로 팔로우 완료'
    case 'INCOMING':
      return '팔로우 요청 수신'
    case 'OUTGOING':
      return '팔로우 요청 보냄'
    case 'READY':
      return '팔로우 요청 전'
    default:
      return handshakeStatusLabel.value
  }
})

const sessionStatusMessage = computed(() => {
  switch (followInteractionStage.value) {
    case 'COMPLETED':
      return '서로 팔로우 되었습니다! 대화를 이어가보세요.'
    case 'INCOMING':
      return '상대방이 팔로우 요청을 보냈습니다. 수락해보세요.'
    case 'OUTGOING':
      return '팔로우 응답을 기다리고 있습니다.'
    case 'READY':
      return '서로를 팔로우하여 대화를 이어가보세요.'
    default:
      return sessionStatus.value
  }
})

const followButtonConfig = computed(() => {
  switch (followInteractionStage.value) {
    case 'COMPLETED':
      return {
        color: 'success',
        variant: 'tonal',
        label: '팔로우 되었습니다',
        disabled: true,
        loading: false,
        handler: null,
      }
    case 'INCOMING':
      return {
        color: 'success',
        variant: 'tonal',
        label: '팔로우 요청 수락',
        disabled: followAcceptLoading.value,
        loading: followAcceptLoading.value,
        handler: acceptFollowRequest,
      }
    case 'OUTGOING':
      return {
        color: 'pink',
        variant: 'tonal',
        label: '팔로우 요청 보냄',
        disabled: true,
        loading: followRequestLoading.value,
        handler: null,
      }
    case 'READY':
      return {
        color: 'pink',
        variant: 'tonal',
        label: '팔로우 요청',
        disabled: followRequestLoading.value || !canRequestFollow.value,
        loading: followRequestLoading.value,
        handler: requestFollow,
      }
    default:
      return {
        color: 'pink',
        variant: 'tonal',
        label: '팔로우 요청',
        disabled: true,
        loading: false,
        handler: null,
      }
  }
})

const isTemporaryRoom = computed(() => {
  if (roomTemporary.value != null) {
    return Boolean(roomTemporary.value)
  }
  if (roomInfo.value?.type) {
    return String(roomInfo.value.type).toUpperCase() === 'RANDOM'
  }
  const bootstrapTemporary = matchStore.bootstrap?.roomTemporary
  if (bootstrapTemporary != null) {
    return Boolean(bootstrapTemporary)
  }
  return null
})

const canStartCall = computed(() => {
  if (!roomId.value) return false
  return participantLoginIds.value.some((loginId) => loginId && loginId !== resolveClientIdentity(auth))
})

function toFiniteNumber (value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function ensureConversation(roomKey) {
  if (!conversations.value[roomKey]) {
    conversations.value[roomKey] = []
  }
  return conversations.value[roomKey]
}

function resetCallHandshake() {
  callReady.value = false
  remoteCallReady.value = false
  callRequestInFlight.value = false
  callStartInProgress.value = false
}

function applyCallReadyState(readyMembers = [], allReady = false) {
  const myLogin = resolveClientIdentity(auth)
  const normalizedReady = Array.isArray(readyMembers)
    ? readyMembers
      .map((login) => (login ? String(login).toLowerCase() : null))
      .filter(Boolean)
    : []
  const normalizedMyLogin = myLogin ? String(myLogin).toLowerCase() : null

  if (normalizedMyLogin) {
    callReady.value = normalizedReady.includes(normalizedMyLogin)
  } else {
    callReady.value = false
  }

  remoteCallReady.value = normalizedReady.some((login) => login !== normalizedMyLogin)

  if (!allReady && callReady.value && !remoteCallReady.value) {
    callStartInProgress.value = false
  }
}

function scrollToBottom(roomKey) {
  if (roomId.value !== roomKey) return
  nextTick(() => {
    const el = chatMessagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function normalizeHistoryMessage(entry, roomKey) {
  if (!entry) return null
  const myLogin = resolveClientIdentity(auth)
  const senderLogin = entry.senderLoginId || entry.senderLoginID || entry.sender_login_id || null
  const normalizedSender = senderLogin ? String(senderLogin).toLowerCase() : null
  const normalizedMyLogin = myLogin ? String(myLogin).toLowerCase() : null

  const messageId = entry.messageId || entry.message_id || `${roomKey}-${entry.sentAt || Date.now()}`
  return {
    id: messageId,
    text: entry.content || '',
    time: formatTime(entry.sentAt),
    sender: entry.senderNickName || entry.senderNickname || senderLogin || '상대방',
    senderLoginId: senderLogin,
    me: Boolean(normalizedMyLogin && normalizedSender && normalizedMyLogin === normalizedSender),
    contentType: (entry.contentType || 'TEXT').toUpperCase(),
    fileName: entry.fileName || null,
    fileUrl: normalizeAttachmentUrl(entry.fileUrl || entry.file_url || null),
    translation: null,
    translating: false,
    sentAt: entry.sentAt || null,
  }
}

function mergeMessageHistory(roomKey, historyMessages) {
  const existing = ensureConversation(roomKey)
  if (!Array.isArray(historyMessages) || historyMessages.length === 0) {
    if (!existing.length) {
      conversations.value[roomKey] = []
    }
    return
  }

  const map = new Map()
  const keyFor = (message) => {
    if (!message) return null
    return message.id || `${message.sentAt ?? ''}-${message.senderLoginId ?? ''}-${message.text ?? ''}`
  }

  historyMessages.forEach((message) => {
    const key = keyFor(message)
    if (!key) return
    map.set(key, message)
  })

  existing.forEach((message) => {
    const key = keyFor(message)
    if (!key) return
    const stored = map.get(key)
    if (stored) {
      map.set(key, { ...stored, ...message })
    } else {
      map.set(key, message)
    }
  })

  const sorted = Array.from(map.values()).sort((a, b) => {
    const aTime = a?.sentAt ? new Date(a.sentAt).getTime() : NaN
    const bTime = b?.sentAt ? new Date(b.sentAt).getTime() : NaN
    if (Number.isNaN(aTime) && Number.isNaN(bTime)) return 0
    if (Number.isNaN(aTime)) return -1
    if (Number.isNaN(bTime)) return 1
    return aTime - bTime
  })

  conversations.value[roomKey] = sorted
}

async function ensureMessageHistory(roomKey) {
  if (!roomKey) return
  if (historyLoadedRooms.value.has(roomKey)) return

  try {
    const { data } = await api.get(`/rooms/${roomKey}/messages`, { params: { limit: 100 } })
    const normalized = Array.isArray(data)
      ? data
        .map((entry) => normalizeHistoryMessage(entry, roomKey))
        .filter(Boolean)
      : []
    mergeMessageHistory(roomKey, normalized)
    historyLoadedRooms.value.add(roomKey)
    scrollToBottom(roomKey)
  } catch (error) {
    console.warn('[match-session] Failed to load message history', error)
  }
}

async function ensureRoomExists(roomKey, fallbackName) {
  if (!roomKey) return null
  if (roomInfo.value && roomInfo.value.id === roomKey) {
    return roomInfo.value
  }

  try {
    const { data } = await api.get(`/rooms/${roomKey}`)
    const participants = (data.participants || []).map((participant) => participant.loginId).filter(Boolean)
    const roomType = data.roomType || data.room_type || null
    const temporary = roomType ? String(roomType).toUpperCase() === 'RANDOM' : undefined
    const myLoginId = resolveClientIdentity(auth)
    const partnerDetail = (data.participants || []).find((participant) => {
      const loginId = participant.loginId || participant.login_id || participant.username
      if (!loginId) return false
      if (!myLoginId) return true
      return String(loginId).toLowerCase() !== String(myLoginId).toLowerCase()
    })
    if (temporary !== undefined) {
      roomTemporary.value = temporary
    }
    roomInfo.value = {
      id: data.roomId,
      name: data.roomName || fallbackName || `대화방 #${data.roomId}`,
      type: roomType,
      temporary,
      participantLogins: participants,
      participantsDetail: data.participants || [],
    }
    participantLoginIds.value = participants
    if (partnerDetail) {
      const loginId = partnerDetail.loginId || partnerDetail.login_id || partnerDetail.username
      const nickname = partnerDetail.nickName || partnerDetail.nickname || partnerDetail.name
      const userPid = partnerDetail.userPid ?? partnerDetail.user_pid ?? null
      if (loginId) {
        partnerLoginId.value = loginId
      }
      if (nickname) {
        partnerName.value = nickname
      }
      const numericPid = Number(userPid)
      if (Number.isFinite(numericPid) && numericPid > 0) {
        partnerUserPid.value = numericPid
      }
    }
    await ensureMessageHistory(roomKey)
    return roomInfo.value
  } catch (error) {
    console.warn('[match-session] Failed to fetch room detail', error)
    if (!roomInfo.value || roomInfo.value.id !== roomKey) {
      roomInfo.value = {
        id: roomKey,
        name: fallbackName || `대화방 #${roomKey}`,
        participantLogins: participantLoginIds.value,
        participantsDetail: [],
        type: roomInfo.value?.type ?? null,
        temporary: roomTemporary.value ?? null,
      }
    }
    return roomInfo.value
  }
}

async function initiateVideoCall() {
  if (callActive.value || callStartInProgress.value) {
    return
  }
  if (!roomId.value) {
    return
  }
  if (!videoChatRef.value?.startCall) {
    console.warn('[match-session] VideoChat component is not ready')
    return
  }

  callStartInProgress.value = true
  try {
    if (!participantLoginIds.value.length) {
      await fetchRoomParticipants(roomId.value)
    }
    const myLoginId = resolveClientIdentity(auth)
    const targets = participantLoginIds.value.filter((loginId) => loginId && loginId !== myLoginId)
    if (!targets.length) {
      alert('통화할 상대가 아직 입장하지 않았습니다.')
      return
    }
    for (const loginId of targets) {
      await videoChatRef.value.startCall(loginId)
    }
    callActive.value = true
  } catch (error) {
    console.error('[match-session] Failed to start call', error)
    alert('영상 통화를 시작하지 못했습니다.')
  } finally {
    callStartInProgress.value = false
    callRequestInFlight.value = false
  }
}

function formatTime(value) {
  if (!value) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const handleIncomingMessage = createIncomingMessageHandler({
  auth,
  ensureConversation,
  ensureRoomExists,
  scrollToBottom,
  formatTime,
})

const handleFileSelect = createFileSelectHandler({
  getRoomId: () => roomId.value,
})

async function handleMatchResultEvent(payload) {
  if (!payload) return
  const normalized = camelizeKeys(payload)
  matchStore.mergeBootstrap(normalized)
  applyBootstrap(matchStore.bootstrap)
  if (roomId.value) {
    await ensureRoomExists(roomId.value, matchStore.bootstrap?.partnerName)
  }
}

async function handleFollowUpdateEvent(payload) {
  if (!payload) return
  const normalized = camelizeKeys(payload)
  matchStore.mergeBootstrap(normalized)
  applyBootstrap(matchStore.bootstrap)
  if (roomId.value) {
    await ensureRoomExists(roomId.value, matchStore.bootstrap?.partnerName)
  }
}

async function refreshFollowSnapshot() {
  try {
    const { data } = await api.get('/match/results/latest', { skipSnakifyParams: true })
    if (data) {
      const normalized = camelizeKeys(data)
      matchStore.mergeBootstrap(normalized)
      applyBootstrap(matchStore.bootstrap)
    }
  } catch (error) {
    console.warn('[match-session] Failed to refresh follow snapshot', error)
  }
  return followState.incomingId ?? followState.relationId ?? null
}

async function resolveIncomingFollowId() {
  if (incomingStatusUpper.value === 'PENDING') {
    const candidate = followState.incomingId ?? followState.relationId ?? pendingIncomingId.value
    if (candidate) {
      pendingIncomingId.value = candidate
      return candidate
    }
  }

  const refreshed = await refreshFollowSnapshot()
  if (incomingStatusUpper.value === 'PENDING') {
    const candidate = followState.incomingId ?? followState.relationId ?? refreshed ?? pendingIncomingId.value
    if (candidate) {
      pendingIncomingId.value = candidate
      return candidate
    }
  }

  return null
}

async function handleRoomPromotedEvent(payload) {
  const normalized = camelizeKeys(payload || {})
  const patch = {
    ...normalized,
    roomTemporary: false,
    mutualFollow: true,
    followStatus: normalized.followStatus || 'ACCEPTED',
  }
  matchStore.mergeBootstrap(patch)
  applyBootstrap(matchStore.bootstrap)
  if (roomId.value) {
    await ensureRoomExists(roomId.value, matchStore.bootstrap?.partnerName)
  }
}

function handleCallReadyEvent(payload) {
  const roomKey = Number(payload?.roomId ?? payload?.room_id ?? 0)
  if (!roomKey || Number(roomId.value) !== roomKey) {
    return
  }
  const readyMembers = payload?.readyMembers ?? payload?.ready_members ?? []
  const allReady = Boolean(payload?.allReady ?? payload?.all_ready)
  applyCallReadyState(readyMembers, allReady)
  if (allReady) {
    void initiateVideoCall()
  }
}

function handleCallEndedEvent(payload) {
  const roomKey = Number(payload?.roomId ?? payload?.room_id ?? 0)
  if (!roomKey || Number(roomId.value) !== roomKey) {
    return
  }
  if (videoChatRef.value?.hangUp) {
    try {
      videoChatRef.value.hangUp()
    } catch (error) {
      console.warn('[match-session] Failed to hang up call from event', error)
    }
  }
  callActive.value = false
  resetCallHandshake()
  if (payload?.roomRemoved) {
    historyLoadedRooms.value.delete(roomKey)
  }
  const redirect = Boolean(payload?.redirectToMatch)
  if (redirect) {
    cleanupState.completed = true
    matchStore.mergeBootstrap({ roomId: null, chatReady: false })
    roomId.value = null
    roomInfo.value = null
    const currentRoute = router.currentRoute?.value?.name
    if (currentRoute !== 'match') {
      router.push({ name: 'match' })
    }
  }
}

const {
  realtimeClient,
  connect: connectRealtime,
  disconnect: disconnectRealtime,
  setManualDisconnect,
} = useRealtimeChatClient(auth, {
  maxReconnectAttempts: 3,
  onChat: (payload) => handleIncomingMessage(payload),
  onMatchResult: (payload) => handleMatchResultEvent(payload),
  events: {
    'match-follow-updated': (payload) => { void handleFollowUpdateEvent(payload) },
    'match-room-promoted': (payload) => { void handleRoomPromotedEvent(payload) },
    'room-call-ready': (payload) => { handleCallReadyEvent(payload) },
    'room-call-ended': (payload) => { handleCallEndedEvent(payload) },
  },
})

function applyRouteContext() {
  const { roomId: roomIdParam, partnerLoginId: partnerLogin, partnerName: partnerParam, partnerUserPid: partnerPid } = route.query
  const parsedRoomId = Number(roomIdParam)
  if (Number.isFinite(parsedRoomId)) {
    roomId.value = parsedRoomId
  }
  if (typeof partnerParam === 'string') {
    partnerName.value = partnerParam
  }
  if (typeof partnerLogin === 'string') {
    partnerLoginId.value = partnerLogin
  }
  const parsedPid = Number(partnerPid)
  if (Number.isFinite(parsedPid)) {
    partnerUserPid.value = parsedPid
  }
}

function applyFollowSnapshot (patch = {}) {
  if (!patch || typeof patch !== 'object') return

  if (Object.prototype.hasOwnProperty.call(patch, 'followStatus')) {
    followState.status = patch.followStatus ? patch.followStatus.toString().toUpperCase() : null
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'incomingFollowStatus')) {
    const nextStatus = patch.incomingFollowStatus
    followState.incomingStatus = nextStatus ? nextStatus.toString().toUpperCase() : null
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'outgoingFollowStatus')) {
    const nextStatus = patch.outgoingFollowStatus
    followState.outgoingStatus = nextStatus ? nextStatus.toString().toUpperCase() : null
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'incomingFollowId')) {
    followState.incomingId = toFiniteNumber(patch.incomingFollowId)
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'outgoingFollowId')) {
    followState.outgoingId = toFiniteNumber(patch.outgoingFollowId)
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'followRelationId')) {
    followState.relationId = toFiniteNumber(patch.followRelationId)
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'mutualFollow')) {
    followState.mutual = Boolean(patch.mutualFollow)
  }

  followState.status = followState.status ? followState.status.toString().toUpperCase() : null
  followState.incomingStatus = followState.incomingStatus ? followState.incomingStatus.toUpperCase() : null
  followState.outgoingStatus = followState.outgoingStatus ? followState.outgoingStatus.toUpperCase() : null
  followState.relationId = followState.incomingId ?? followState.outgoingId ?? followState.relationId ?? null
  pendingIncomingId.value = followState.incomingId ?? null
}

function applyBootstrap(payload) {
  if (!payload) return
  callActive.value = false
  resetCallHandshake()
  partnerName.value = payload.partnerName || partnerName.value
  partnerLoginId.value = payload.partnerLoginId || partnerLoginId.value
  partnerUserPid.value = payload.partnerUserPid ?? partnerUserPid.value
  roomId.value = payload.roomId ?? roomId.value
  handshakeStatus.value = payload.status || payload.handshake?.status || handshakeStatus.value
  if (Object.prototype.hasOwnProperty.call(payload, 'roomTemporary')) {
    roomTemporary.value = payload.roomTemporary
  } else if (payload.roomType) {
    roomTemporary.value = String(payload.roomType).toUpperCase() === 'RANDOM'
  }
  applyFollowSnapshot(payload)
  if (payload.chatReady) {
    sessionStatus.value = '실시간 대화를 진행해보세요!'
  } else if (payload.handshakeReady) {
    sessionStatus.value = '상대방이 입장할 때까지 잠시만 기다려주세요.'
  } else if ((payload.status || '').toUpperCase() === 'MATCHED') {
    sessionStatus.value = '상대방의 수락을 기다리는 중입니다.'
  } else if ((payload.status || '').toUpperCase() === 'DECLINED') {
    sessionStatus.value = '상대방이 매칭을 거절했습니다.'
  } else if ((payload.status || '').toUpperCase() === 'CANCELLED') {
    sessionStatus.value = '매칭이 종료되었습니다.'
  }
}

async function refreshBootstrapFromStore() {
  applyBootstrap(matchStore.bootstrap)
  if (roomId.value) {
    await ensureRoomExists(roomId.value)
  }
}

async function initializeSession() {
  applyRouteContext()
  await refreshBootstrapFromStore()
  setManualDisconnect(false)
  await connectRealtime()
}

async function fetchRoomParticipants(roomKey) {
  if (!roomKey) return
  await ensureRoomExists(roomKey)
}

watch(() => matchStore.bootstrap, () => {
  void refreshBootstrapFromStore()
})

watch(() => route.query, () => {
  applyRouteContext()
  if (roomId.value) {
    void fetchRoomParticipants(roomId.value)
    const numericRoomId = Number(roomId.value)
    if (Number.isFinite(numericRoomId) && numericRoomId > 0) {
      void ensureMessageHistory(numericRoomId)
    }
  }
})

watch(roomId, (nextRoom) => {
  if (nextRoom) {
    void fetchRoomParticipants(nextRoom)
    const numericRoomId = Number(nextRoom)
    if (Number.isFinite(numericRoomId) && numericRoomId > 0) {
      void ensureMessageHistory(numericRoomId)
    }
  }
  resetCallHandshake()
  callActive.value = false
})

watch(messages, () => {
  scrollToBottom(roomId.value)
})

onMounted(async () => {
  await initializeSession()
})

onUnmounted(() => {
  hangUpCall()
  setManualDisconnect(true)
  disconnectRealtime()
  void cleanupTemporaryRoom('component-unmount')
})

function bubbleClass(message) {
  const classes = []
  if (message.contentType && message.contentType !== 'TEXT') {
    classes.push('attachment')
  } else if (message.me) {
    classes.push('me')
  }
  return classes
}

async function send() {
  const message = draft.value.trim()
  if (!message || !roomId.value) return

  if (!realtimeClient.value?.isConnected()) {
    await connectRealtime()
    if (!realtimeClient.value?.isConnected()) {
      console.warn('[match-session] realtime client not connected. Message aborted.')
      return
    }
  }

  try {
    realtimeClient.value.send({
      type: 'CHAT',
      roomId: Number(roomId.value),
      content: message,
    })
    draft.value = ''
  } catch (error) {
    console.error('[match-session] Failed to send chat message', error)
  }
}

function triggerFilePicker() {
  if (!roomId.value) return
  fileInput.value?.click()
}

async function downloadAttachment(message) {
  if (!message?.fileUrl) return
  const normalizedUrl = normalizeAttachmentUrl(message.fileUrl)
  if (!normalizedUrl) {
    alert('다운로드할 파일 경로를 확인할 수 없습니다.')
    return
  }

  try {
    const response = await api.get(normalizedUrl, {
      responseType: 'blob',
      skipSnakifyParams: true,
    })
    const blob = response.data
    const blobUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = message.fileName || 'attachment'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(blobUrl)
  } catch (error) {
    console.error('[match-session] Failed to download attachment', error)
    alert('파일을 다운로드하지 못했습니다. 잠시 후 다시 시도하세요.')
  }
}

async function startVideoCall() {
  if (!roomId.value) {
    alert('채팅방 정보를 확인할 수 없습니다.')
    return
  }
  if (callRequestInFlight.value || callStartInProgress.value) {
    return
  }

  callRequestInFlight.value = true
  try {
    const { data } = await api.post(`/rooms/${roomId.value}/call/ready`)
    const readyMembers = Array.isArray(data?.readyMembers)
      ? [...data.readyMembers]
      : []
    const myLogin = resolveClientIdentity(auth)
    if (myLogin && !readyMembers.some((login) => login && String(login).toLowerCase() === String(myLogin).toLowerCase())) {
      readyMembers.push(myLogin)
    }
    applyCallReadyState(readyMembers, Boolean(data?.allReady))
    if (data?.allReady) {
      await initiateVideoCall()
    }
  } catch (error) {
    console.error('[match-session] Failed to mark call ready', error)
    alert('영상 통화를 준비하지 못했습니다.')
    resetCallHandshake()
  } finally {
    callRequestInFlight.value = false
  }
}

function hangUpCall() {
  if (videoChatRef.value?.hangUp) {
    try {
      videoChatRef.value.hangUp()
    } catch (error) {
      console.warn('[match-session] Failed to terminate call', error)
    }
  }
  callActive.value = false
  resetCallHandshake()
  if (!roomId.value) {
    return
  }
  void api.post(`/rooms/${roomId.value}/call/hangup`).catch((error) => {
    console.warn('[match-session] Failed to notify hangup', error)
  })
}

async function requestFollow() {
  if (!canRequestFollow.value || followRequestLoading.value) return
  const followeeId = Number(partnerUserPid.value)
  if (!Number.isFinite(followeeId) || followeeId <= 0) {
    alert('팔로우 대상 정보를 확인할 수 없습니다.')
    return
  }
  followRequestLoading.value = true
  try {
    const { data } = await followService.requestFollow(followeeId)
    followState.outgoingId = toFiniteNumber(data?.followId) ?? followState.outgoingId
    followState.outgoingStatus = data?.status ? data.status.toString().toUpperCase() : 'PENDING'
    followState.status = 'PENDING_OUTGOING'
    followState.relationId = followState.outgoingId ?? followState.relationId
    matchStore.mergeBootstrap({
      followStatus: followState.status,
      followRelationId: followState.relationId,
      outgoingFollowId: followState.outgoingId,
      outgoingFollowStatus: followState.outgoingStatus,
    })
    sessionStatus.value = '팔로우 응답을 기다리고 있습니다.'
  } catch (error) {
    console.error('Failed to send follow request', error)
    alert('팔로우 요청에 실패했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  } finally {
    followRequestLoading.value = false
  }
}

async function acceptFollowRequest() {
  if (!canAcceptFollow.value || followAcceptLoading.value) return
  let followId = followState.incomingId ?? (incomingStatusUpper.value === 'PENDING' ? followState.relationId : null) ?? pendingIncomingId.value
  if (!followId) {
    followId = await resolveIncomingFollowId()
  }
  if (!followId) {
    alert('수락할 팔로우 요청을 찾을 수 없습니다.')
    return
  }
  followAcceptLoading.value = true
  try {
    await followService.acceptFollow(followId)
    followState.incomingStatus = 'ACCEPTED'
    followState.status = hasMutualFollow.value ? 'ACCEPTED' : 'ACCEPTED_INCOMING'
    followState.relationId = followState.incomingId ?? followId
    followState.mutual = hasMutualFollow.value
    pendingIncomingId.value = null
    matchStore.mergeBootstrap({
      followStatus: followState.status,
      followRelationId: followState.relationId,
      incomingFollowId: followState.incomingId ?? followId,
      incomingFollowStatus: followState.incomingStatus,
      mutualFollow: followState.mutual,
    })
    sessionStatus.value = '서로 팔로우 되었습니다! 대화를 이어가보세요.'
  } catch (error) {
    console.error('Failed to accept follow request', error)
    alert('팔로우 수락에 실패했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  } finally {
    followAcceptLoading.value = false
  }
}

function handleFollowButtonClick() {
  const config = followButtonConfig.value
  if (!config || typeof config.handler !== 'function' || config.disabled) {
    return
  }
  config.handler()
}

async function cleanupTemporaryRoom(reason = 'navigation') {
  if (cleanupState.running || cleanupState.completed) return false
  if (!roomId.value) return false

  if (isTemporaryRoom.value === null) {
    await ensureRoomExists(roomId.value)
  }

  if (isTemporaryRoom.value === false || hasMutualFollow.value) {
    cleanupState.completed = true
    return false
  }

  cleanupState.running = true
  try {
    await api.delete(`/rooms/${roomId.value}/temporary`, { params: { reason } })
    cleanupState.completed = true
    const cleanedRoom = roomId.value
    roomId.value = null
    roomInfo.value = null
    roomTemporary.value = null
    matchStore.mergeBootstrap({ roomId: null, chatReady: false, roomTemporary: null })
    return Boolean(cleanedRoom)
  } catch (error) {
    console.warn('[match-session] Failed to cleanup temporary room', error)
    return false
  } finally {
    cleanupState.running = false
  }
}

function goBackToMatching() {
  hangUpCall()
  cleanupTemporaryRoom('user-navigation').finally(() => {
    router.push({ name: 'match' })
  })
}

</script>

<style scoped>
.match-session {
  min-height: calc(100vh - var(--v-layout-top));
  display: flex;
  flex-direction: column;
}

.session-header {
  background: #fff;
}

.session-card {
  background: linear-gradient(180deg, #fffdf4 0%, #ffffff 55%, #f3fbf7 100%);
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 220px);
  height: auto;
  max-height: none;
  overflow: visible;
}

.session-content {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  gap: 24px;
  padding: 24px;
  height: 100%;
  min-height: 0;
  flex: 1;
  overflow: visible;
  align-content: stretch;
  align-items: stretch;
}

.video-pane {
  flex: 1 1 52%;
  max-width: 52%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  gap: 16px;
}

.session-video {
  flex: 1;
  min-height: 300px;
  aspect-ratio: 16 / 9;
  width: 100%;
}

.video-actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.chat-pane {
  flex: 1 1 48%;
  max-width: 48%;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #ecf2e5;
  min-height: 0;
  height: 100%;
  gap: 0;
}

.chat-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: linear-gradient(180deg, #fffef5 0%, #f4fff8 100%);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-height: 0;
  scrollbar-gutter: stable;
}

.chat-messages__list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  justify-content: flex-end;
  flex: 1 1 auto;
  min-height: 100%;
}

.message-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.message-row--me {
  align-items: flex-end;
}

.message-bubble {
  max-width: 90%;
  padding: 12px 16px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  word-break: break-word;
  white-space: pre-wrap;
}

.message-bubble.me {
  background: #1976d2;
  color: #fff;
}

.message-bubble.attachment {
  background: transparent;
  box-shadow: none;
  padding: 0;
}

.message-bubble img {
  max-width: 100%;
  border-radius: 12px;
}

.file-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: inherit;
  text-decoration: none;
}

.chat-input {
  border-top: 1px solid #f4efe0;
  background: #fff8d6;
}

.session-pill {
  border-radius: 999px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: -0.2px;
  padding: 0 18px;
  height: 44px;
  min-width: 44px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.session-pill :deep(.v-btn__content) {
  gap: 6px;
}

.session-pill:not([disabled]):hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px rgba(0, 0, 0, 0.12);
}

.session-pill[disabled] {
  filter: grayscale(0.35);
  box-shadow: none;
}

.video-start-btn {
  background: linear-gradient(135deg, #fff3a0, #ffd84d);
  color: #3b2a07;
}

.hangup-btn {
  background: linear-gradient(135deg, #ff9a9e, #ff6f61);
  color: #fff;
}

.chat-action-btn {
  padding-inline: 18px;
}

.session-file-btn {
  background: linear-gradient(135deg, #ffeb99, #ffd43b);
  color: #3b2f10;
  min-width: 48px;
  padding-inline: 14px;
}

.session-send-btn {
  background: linear-gradient(135deg, #06c755, #00a884);
  color: #fff;
}

.session-send-btn[disabled] {
  background: #c8f5df;
  color: #5ca98a;
}

</style>

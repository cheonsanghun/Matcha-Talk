<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="10">
        <v-card class="pa-6">
          <div v-if="isPending" class="text-center py-8">
            <v-progress-circular
              indeterminate
              color="pink"
              size="64"
              class="mb-4"
            />
            <div class="text-h6 mb-2">매칭 상대를 찾고 있습니다...</div>
            <div class="text-caption text-medium-emphasis">{{ sessionStatus }}</div>
          </div>

          <div v-else-if="matchDeclined">
            <v-alert type="error" variant="tonal" class="mb-6">
              상대방이 매칭을 거절했습니다. 새로운 인연을 찾아볼까요?
            </v-alert>
            <div class="text-center">
              <v-btn color="pink" variant="tonal" @click="restartMatching">다시 매칭 시도</v-btn>
            </div>
          </div>

          <div v-else>
            <v-row class="align-center mb-4" no-gutters>
              <v-avatar size="48" class="me-3">
                <v-img :src="partnerAvatar" alt="avatar" />
              </v-avatar>
              <div>
                <div class="text-h6 text-pink-darken-2">{{ partnerName }}님과 매칭되었습니다</div>
                <div class="text-caption text-medium-emphasis">
                  {{ partnerLoginId || '로그인 아이디 확인 중' }}
                </div>
              </div>
              <v-spacer />
              <div class="text-caption text-medium-emphasis">
                {{ handshakeStatusLabel }}
              </div>
            </v-row>

            <v-alert
              v-if="promotionNotice"
              type="success"
              variant="tonal"
              class="mb-4 promotion-alert"
            >
              서로 팔로우를 완료했습니다. 채팅 탭에서도 대화를 이어갈 수 있어요!
              <template #append>
                <v-btn
                  v-if="chatReady && roomId"
                  color="success"
                  variant="flat"
                  size="small"
                  class="ms-2"
                  @click="goToChatRoom"
                >
                  채팅 탭 열기
                </v-btn>
              </template>
            </v-alert>

            <v-alert type="info" variant="tonal" class="mb-4">
              {{ sessionStatus }}
              <div v-if="handshakeReady && countdown" class="text-caption text-medium-emphasis countdown">
                수락 마감까지 {{ countdown }}
              </div>
            </v-alert>

            <v-row class="gap-y-4">
              <v-col cols="12" md="7">
                <v-card variant="outlined" class="pa-4 h-100 status-card">
                  <div class="text-subtitle-2 mb-3">매칭 정보</div>
                  <div class="text-body-2 mb-2">
                    <span class="text-medium-emphasis">상대 닉네임</span>
                    : {{ partnerName || '알 수 없음' }}
                  </div>
                  <div class="text-body-2 mb-2" v-if="partnerLoginId">
                    <span class="text-medium-emphasis">상대 아이디</span>
                    : {{ partnerLoginId }}
                  </div>
                  <div class="text-body-2 mb-2" v-if="handshakeInfo?.handshakeKey">
                    <span class="text-medium-emphasis">핸드셰이크 키</span>
                    : {{ handshakeInfo.handshakeKey }}
                  </div>
                  <div class="text-body-2 mb-2" v-if="roomId">
                    <span class="text-medium-emphasis">채팅방 번호</span>
                    : {{ roomId }}
                  </div>
                  <div class="text-body-2 mb-2" v-if="handshakeInfo?.partnerRequestId">
                    <span class="text-medium-emphasis">상대 요청 ID</span>
                    : {{ handshakeInfo.partnerRequestId }}
                  </div>
                  <div class="text-body-2 mb-2" v-if="followStatusMessage">
                    <span class="text-medium-emphasis">팔로우 상태</span>
                    : {{ followStatusMessage }}
                  </div>
                  <div class="text-body-2 text-medium-emphasis mt-3" v-if="chatReady">
                    이제 바로 이 화면에서 대화를 시작해보세요!
                  </div>
                  <div class="text-body-2 text-medium-emphasis mt-3" v-else-if="handshakeReady">
                    상대방도 수락하면 실시간 채팅이 열립니다.
                  </div>
                </v-card>
              </v-col>
              <v-col cols="12" md="5">
                <MatchChatPanel
                  v-if="chatReady && roomId"
                  :room-id="roomId"
                  :partner-name="partnerName"
                />
                <v-card
                  v-else
                  variant="outlined"
                  class="pa-4 h-100 d-flex align-center justify-center text-center text-body-2 text-medium-emphasis"
                >
                  양측이 수락하면 실시간 채팅을 이용할 수 있습니다.
                </v-card>
              </v-col>
            </v-row>

            <div v-if="handshakeReady" class="d-flex justify-center mt-6 gap-4 match-action-buttons">
              <v-btn
                color="pink"
                variant="flat"
                size="large"
                :loading="acceptLoading"
                @click="acceptMatch"
              >
                매칭 수락
              </v-btn>
              <v-btn
                color="grey"
                variant="outlined"
                size="large"
                :loading="declineLoading"
                @click="declineMatch"
              >
                거절
              </v-btn>
            </div>

            <div class="d-flex justify-center gap-4 mt-6 follow-actions">
              <v-btn
                v-if="canRequestFollow"
                color="pink"
                variant="tonal"
                :loading="followRequestLoading"
                @click="requestFollow"
              >
                팔로우 요청 보내기
              </v-btn>
              <v-btn
                v-if="canAcceptFollow"
                color="success"
                variant="tonal"
                :loading="followAcceptLoading"
                @click="acceptFollowRequest"
              >
                팔로우 수락
              </v-btn>
              <v-btn
                v-if="chatReady && roomId"
                color="primary"
                variant="text"
                @click="goToChatRoom"
              >
                채팅 탭으로 이동
              </v-btn>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { createRealtimeClient } from '../services/ws'
import { camelizeKeys } from '../utils/case'
import { resolveClientIdentity } from '../utils/identity'
import api from '../services/api'
import { useMatchStore } from '../stores/match'
import followService from '../services/follow'
import MatchChatPanel from '../components/MatchChatPanel.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const matchStore = useMatchStore()

const partnerName = ref('')
const partnerAvatar = ref('https://via.placeholder.com/150')
const partnerLoginId = ref(null)
const partnerUserPid = ref(null)
const sessionStatus = ref('매칭 대기 중입니다...')
const roomId = ref(null)
const handshakeInfo = ref(null)
const handshakeStatus = ref(null)
const handshakeReady = ref(false)
const chatReady = ref(false)
const matchReady = ref(false)
const matchDeclined = ref(false)
const countdown = ref('')
const promotionNotice = ref(false)

const followState = reactive({
  status: null,
  relationId: null,
})
const followRequestLoading = ref(false)
const followAcceptLoading = ref(false)
const acceptLoading = ref(false)
const declineLoading = ref(false)

let countdownTimer = null

const isPending = computed(() => !matchReady.value && !matchDeclined.value)
const handshakeStatusLabel = computed(() => {
  if (!handshakeStatus.value) return '상태 확인 중'
  switch (handshakeStatus.value) {
    case 'MATCHED':
      return '상대 수락 대기중'
    case 'CONFIRMED':
      return '채팅 준비 완료'
    case 'DECLINED':
      return '매칭 거절됨'
    case 'ARCHIVED':
      return '대화 이력 보관됨'
    default:
      return handshakeStatus.value
  }
})
const followStatusUpper = computed(() => (followState.status || '').toString().toUpperCase())
const followStatusMessage = computed(() => {
  if (!followState.status) return ''
  if (followStatusUpper.value.includes('ACCEPT')) {
    return '서로 팔로우 상태입니다.'
  }
  if (followStatusUpper.value.includes('INCOMING') || followStatusUpper.value.includes('RECEIVED')) {
    return '상대방이 팔로우 요청을 보냈습니다.'
  }
  if (followStatusUpper.value.includes('PENDING') || followStatusUpper.value.includes('REQUEST')) {
    return '팔로우 응답을 기다리고 있습니다.'
  }
  return followState.status
})
const canRequestFollow = computed(() => {
  if (partnerUserPid.value == null) return false
  if (!followStatusUpper.value) return true
  if (followStatusUpper.value.includes('ACCEPT')) return false
  if (followStatusUpper.value.includes('OUTGOING')) return false
  if (followStatusUpper.value.includes('PENDING') &&
    !followStatusUpper.value.includes('INCOMING') &&
    !followStatusUpper.value.includes('RECEIVED')) {
    return false
  }
  return true
})
const canAcceptFollow = computed(() => {
  if (!followState.relationId) return false
  if (!followStatusUpper.value) return true
  if (followStatusUpper.value.includes('ACCEPT')) return false
  return followStatusUpper.value.includes('INCOMING') ||
    followStatusUpper.value.includes('RECEIVED') ||
    followStatusUpper.value === 'PENDING'
})

const chatRouteQuery = computed(() => {
  if (!roomId.value) return {}
  return {
    roomId: String(roomId.value),
    partner: partnerName.value || undefined,
  }
})

watch(() => matchStore.bootstrap, (next) => {
  applyBootstrap(next)
}, { immediate: true })

watch(chatReady, (ready) => {
  if (!ready) {
    promotionNotice.value = false
  }
})

function startCountdown (expiresAt) {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  if (!expiresAt || !handshakeReady.value) {
    countdown.value = ''
    return
  }

  const target = new Date(expiresAt)
  if (Number.isNaN(target.getTime())) {
    countdown.value = ''
    return
  }

  const update = () => {
    const diff = target.getTime() - Date.now()
    if (diff <= 0) {
      countdown.value = '만료됨'
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
      return
    }
    const minutes = Math.floor(diff / 60000)
    const seconds = Math.floor((diff % 60000) / 1000)
    countdown.value = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }

  update()
  countdownTimer = setInterval(update, 1000)
}

function applyBootstrap (payload, options = {}) {
  if (!payload) {
    partnerName.value = ''
    partnerLoginId.value = null
    partnerUserPid.value = null
    roomId.value = null
    handshakeInfo.value = null
    handshakeStatus.value = null
    handshakeReady.value = false
    chatReady.value = false
    matchReady.value = false
    matchDeclined.value = false
    followState.status = null
    followState.relationId = null
    sessionStatus.value = options.statusMessage || '매칭 대기 중입니다...'
    startCountdown(null)
    return
  }

  partnerName.value = payload.partnerName || partnerName.value || ''
  partnerLoginId.value = payload.partnerLoginId || payload.partnerLoginID || partnerLoginId.value || null
  partnerUserPid.value = payload.partnerUserPid ?? partnerUserPid.value ?? null
  roomId.value = payload.roomId ?? payload.handshake?.roomId ?? roomId.value ?? null
  handshakeInfo.value = payload.handshake || handshakeInfo.value || null
  handshakeStatus.value = payload.status || payload.handshake?.status || handshakeStatus.value || null
  followState.status = payload.followStatus ?? followState.status ?? null
  followState.relationId = payload.followRelationId ?? payload.followId ?? followState.relationId ?? null

  handshakeReady.value = !!payload.handshakeReady
  chatReady.value = !!payload.chatReady || (roomId.value != null && (handshakeStatus.value === 'CONFIRMED' || handshakeStatus.value === 'ARCHIVED'))
  matchReady.value = handshakeReady.value || chatReady.value
  matchDeclined.value = handshakeStatus.value === 'DECLINED'

  if (matchDeclined.value) {
    sessionStatus.value = options.statusMessage || '매칭이 거절되었습니다.'
  } else if (chatReady.value) {
    sessionStatus.value = options.statusMessage || '채팅이 가능합니다!'
  } else if (handshakeReady.value) {
    sessionStatus.value = options.statusMessage || '상대방의 수락을 기다리는 중입니다.'
  } else {
    sessionStatus.value = options.statusMessage || '매칭 대기 중입니다...'
  }

  startCountdown(payload.handshake?.expiresAt ?? handshakeInfo.value?.expiresAt ?? null)
}

function ingestMatchPayload (payload, options = {}) {
  if (!payload) return
  const normalized = camelizeKeys(payload)
  const patch = {
    matchFound: true,
    partnerName: normalized.partnerNickName || normalized.partnerNickname || partnerName.value,
    partnerLoginId: normalized.partnerLoginId ?? partnerLoginId.value ?? null,
    partnerUserPid: normalized.partnerUserPid ?? partnerUserPid.value ?? null,
    roomId: normalized.roomId ?? roomId.value ?? null,
    status: normalized.status ?? handshakeStatus.value ?? null,
    followStatus: normalized.followStatus ?? followState.status ?? null,
    followRelationId: normalized.followRelationId ?? normalized.followId ?? followState.relationId ?? null,
    handshake: {
      myRequestId: normalized.myRequestId ?? handshakeInfo.value?.myRequestId ?? null,
      partnerRequestId: normalized.partnerRequestId ?? handshakeInfo.value?.partnerRequestId ?? null,
      handshakeKey: normalized.handshakeKey ?? handshakeInfo.value?.handshakeKey ?? null,
      expiresAt: normalized.expiresAt ?? handshakeInfo.value?.expiresAt ?? null,
      status: normalized.status ?? handshakeInfo.value?.status ?? null,
      roomId: normalized.roomId ?? handshakeInfo.value?.roomId ?? null,
      followStatus: normalized.followStatus ?? handshakeInfo.value?.followStatus ?? null,
      followRelationId: normalized.followRelationId ?? handshakeInfo.value?.followRelationId ?? null,
    },
  }
  matchStore.mergeBootstrap(patch)
  applyBootstrap(matchStore.bootstrap, { statusMessage: options.statusMessage })
}

function handleMatchFound (payload) {
  ingestMatchPayload(payload, { statusMessage: '새로운 매칭이 성사되었습니다. 수락을 진행해주세요.' })
}

function handleMatchRoomReady (payload) {
  promotionNotice.value = false
  ingestMatchPayload(payload, { statusMessage: '채팅방이 준비되었습니다!' })
}

function handleMatchDeclined (payload) {
  ingestMatchPayload(payload, { statusMessage: '상대방이 매칭을 거절했습니다.' })
  matchDeclined.value = true
}

function handleRoomPromoted (payload) {
  const normalized = camelizeKeys(payload || {})
  if (normalized.roomId) {
    matchStore.mergeBootstrap({
      roomId: normalized.roomId,
      chatReady: true,
    })
    roomId.value = normalized.roomId
  }
  followState.status = 'ACCEPTED'
  matchStore.mergeBootstrap({
    followStatus: 'ACCEPTED',
    followRelationId: normalized.followRelationId ?? matchStore.bootstrap?.followRelationId ?? null,
  })
  promotionNotice.value = true
}

function handleMatchStatus (payload) {
  if (typeof payload === 'string') {
    sessionStatus.value = payload || sessionStatus.value
    return
  }
  if (payload) {
    const normalized = camelizeKeys(payload)
    sessionStatus.value = normalized.message || sessionStatus.value
  }
}

async function acceptMatch () {
  if (acceptLoading.value) return
  const requestId = handshakeInfo.value?.myRequestId ?? matchStore.bootstrap?.handshake?.myRequestId
  if (!requestId) {
    alert('매칭 요청 정보를 찾을 수 없습니다.')
    return
  }
  acceptLoading.value = true
  try {
    const loginId = resolveClientIdentity(auth)
    const { data } = await api.post(`/match/requests/${requestId}/accept`, {}, {
      headers: {
        'X-Login-Id': loginId,
      },
      skipSnakifyParams: true,
    })
    if (data) {
      ingestMatchPayload(data, { statusMessage: '매칭을 수락했습니다. 상대방을 기다리는 중입니다.' })
    }
  } catch (error) {
    console.error('Failed to accept match', error)
    alert('매칭 수락에 실패했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  } finally {
    acceptLoading.value = false
  }
}

async function declineMatch () {
  if (declineLoading.value) return
  const requestId = handshakeInfo.value?.myRequestId ?? matchStore.bootstrap?.handshake?.myRequestId
  if (!requestId) {
    alert('매칭 요청 정보를 찾을 수 없습니다.')
    return
  }
  if (!confirm('매칭을 거절하시겠습니까?')) return
  declineLoading.value = true
  try {
    const loginId = resolveClientIdentity(auth)
    const { data } = await api.post(`/match/requests/${requestId}/decline`, {}, {
      headers: {
        'X-Login-Id': loginId,
      },
      skipSnakifyParams: true,
    })
    ingestMatchPayload(data, { statusMessage: '매칭을 거절했습니다.' })
    matchDeclined.value = true
  } catch (error) {
    console.error('Failed to decline match', error)
    alert('매칭 거절에 실패했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  } finally {
    declineLoading.value = false
  }
}

async function requestFollow () {
  if (!canRequestFollow.value || followRequestLoading.value) return
  const followeeId = Number(partnerUserPid.value)
  if (!Number.isFinite(followeeId) || followeeId <= 0) {
    alert('팔로우 요청 대상 정보를 확인할 수 없습니다.')
    return
  }
  followRequestLoading.value = true
  try {
    await followService.requestFollow(followeeId)
    followState.status = 'PENDING_OUTGOING'
    matchStore.mergeBootstrap({ followStatus: followState.status })
    sessionStatus.value = '팔로우 요청을 전송했습니다.'
  } catch (error) {
    console.error('Failed to send follow request', error)
    alert('팔로우 요청에 실패했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  } finally {
    followRequestLoading.value = false
  }
}

async function acceptFollowRequest () {
  if (!canAcceptFollow.value || followAcceptLoading.value) return
  const followId = followState.relationId
  followAcceptLoading.value = true
  try {
    await followService.acceptFollow(followId)
    followState.status = 'ACCEPTED'
    matchStore.mergeBootstrap({ followStatus: 'ACCEPTED', followRelationId: followId })
    promotionNotice.value = true
  } catch (error) {
    console.error('Failed to accept follow request', error)
    alert('팔로우 수락에 실패했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  } finally {
    followAcceptLoading.value = false
  }
}

function goToChatRoom () {
  if (!roomId.value) return
  router.push({ name: 'chat', query: { ...chatRouteQuery.value } })
}

function restartMatching () {
  matchStore.clearBootstrap()
  router.push({ name: 'match' })
}

async function fetchLatestMatchFromRest () {
  try {
    const loginId = resolveClientIdentity(auth)
    if (!loginId) return
    const response = await api.get('/match/results/latest', {
      headers: {
        'X-Login-Id': loginId,
      },
      skipSnakifyParams: true,
    })
    if (response.status === 204 || !response.data) {
      return
    }
    ingestMatchPayload(response.data, { statusMessage: '최근 매칭 정보를 불러왔습니다.' })
  } catch (error) {
    console.warn('Failed to fetch latest match result', error?.response?.data || error?.message)
  }
}

function extractBootstrapFromRoute () {
  const { matched, roomId: routeRoomId, partner, status, requestId } = route.query
  if (matched === '1' || matched === 'true') {
    const parsedRoomId = typeof routeRoomId !== 'undefined' ? Number(routeRoomId) : null
    const parsedRequestId = typeof requestId !== 'undefined' ? Number(requestId) : null
    return {
      matchFound: true,
      partnerName: typeof partner === 'string' ? partner : '',
      roomId: Number.isFinite(parsedRoomId) ? parsedRoomId : null,
      status: typeof status === 'string' ? status : null,
      handshake: {
        myRequestId: Number.isFinite(parsedRequestId) ? parsedRequestId : null,
        status: typeof status === 'string' ? status : null,
        roomId: Number.isFinite(parsedRoomId) ? parsedRoomId : null,
      },
    }
  }
  return null
}

function isQueuedFromRoute () {
  const { queued } = route.query
  return queued === '1' || queued === 'true'
}

const connectionAttempts = ref(0)
const maxConnectionAttempts = 3
const isConnecting = ref(false)
let websocketClient = null
let reconnectTimer = null
let manualDisconnect = false
const teardownHandlers = []

async function connectWebSocket () {
  if (manualDisconnect) return

  if (websocketClient?.isConnected?.()) {
    isConnecting.value = false
    return
  }

  if (isConnecting.value) return

  const loginId = resolveClientIdentity(auth)

  if (!websocketClient) {
    websocketClient = createRealtimeClient({ queryParams: { loginId } })
    teardownHandlers.push(
      websocketClient.onOpen(() => {
        isConnecting.value = false
        connectionAttempts.value = 0
        sessionStatus.value = chatReady.value ? '채팅이 가능합니다!' : '매칭 서버에 연결되었습니다.'
      }),
      websocketClient.onClose((event) => {
        isConnecting.value = false
        if (manualDisconnect) return
        if (!event.wasClean) {
          scheduleReconnect()
        }
      }),
      websocketClient.onError((event) => {
        console.error('❌ WebSocket error:', event)
      }),
      websocketClient.onEvent('match-found', handleMatchFound),
      websocketClient.onEvent('match-room-ready', handleMatchRoomReady),
      websocketClient.onEvent('match-declined', handleMatchDeclined),
      websocketClient.onEvent('match-room-promoted', handleRoomPromoted),
      websocketClient.onEvent('match-status', handleMatchStatus),
      websocketClient.onEvent('connected', () => {
        sessionStatus.value = '매칭 서버에 연결되었습니다.'
      }),
    )
  } else {
    websocketClient.setQueryParams({ loginId })
    if (websocketClient.isConnected()) {
      isConnecting.value = false
      return
    }
  }

  isConnecting.value = true
  try {
    await websocketClient.connect()
  } catch (error) {
    console.error('❌ Failed to establish WebSocket connection:', error)
    isConnecting.value = false
    scheduleReconnect()
  }
}

function scheduleReconnect () {
  if (manualDisconnect) return
  if (connectionAttempts.value >= maxConnectionAttempts) {
    sessionStatus.value = '매칭 서버 연결에 실패했습니다. 잠시 후 다시 시도해주세요.'
    return
  }
  if (reconnectTimer) return

  connectionAttempts.value += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectWebSocket()
  }, 2000)
}

onMounted(async () => {
  const routeBootstrap = extractBootstrapFromRoute()
  const queuedFromRoute = isQueuedFromRoute()
  if (routeBootstrap) {
    matchStore.mergeBootstrap(routeBootstrap)
  } else if (queuedFromRoute) {
    matchStore.clearBootstrap()
    applyBootstrap(null, { statusMessage: '매칭 대기열에 등록되었습니다.' })
  } else if (matchStore.bootstrap) {
    applyBootstrap(matchStore.bootstrap)
  }

  const websocketPromise = connectWebSocket()

  if (!matchReady.value) {
    await fetchLatestMatchFromRest()
  }

  await websocketPromise
})

onBeforeUnmount(() => {
  manualDisconnect = true

  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  teardownHandlers.forEach((teardown) => {
    try {
      teardown?.()
    } catch (error) {
      console.warn('⚠️ Failed to remove WebSocket handler during cleanup', error)
    }
  })
  teardownHandlers.length = 0

  if (websocketClient) {
    try {
      websocketClient.disconnect()
    } catch (error) {
      console.warn('Failed to disconnect WebSocket cleanly', error)
    }
    websocketClient = null
  }

  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
</script>

<style scoped>
.status-card {
  min-height: 240px;
}

.match-action-buttons {
  gap: 16px;
}

.follow-actions {
  flex-wrap: wrap;
}

.countdown {
  font-variant-numeric: tabular-nums;
  margin-top: 8px;
}

.promotion-alert {
  border-radius: 12px;
}
</style>

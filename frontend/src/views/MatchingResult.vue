<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8" lg="6">
        <v-card class="pa-8 text-center">
          <div class="text-h5 text-pink-darken-2 mb-4">랜덤 매칭 진행 중</div>

          <v-alert
            v-if="errorMessage"
            type="error"
            variant="tonal"
            class="mb-6 text-start"
          >
            <div class="mb-2">{{ errorMessage }}</div>
            <ul v-if="errorDetails.length" class="error-detail-list">
              <li
                v-for="(detail, index) in errorDetails"
                :key="`${index}-${detail}`"
              >
                {{ detail }}
              </li>
            </ul>
          </v-alert>

          <div v-if="isWaiting && !errorMessage" class="py-8">
            <v-progress-circular indeterminate color="pink" size="56" class="mb-4" />
            <div class="text-subtitle-1 text-medium-emphasis">
              매칭 가능한 상대를 찾는 중입니다...
            </div>
            <div
              v-if="waitingMessage"
              class="text-body-2 text-medium-emphasis mt-3"
            >
              {{ waitingMessage }}
            </div>
          </div>

          <div v-else-if="!isWaiting && !errorMessage" class="py-6">
            <div class="text-h6 text-pink-darken-2 mb-2">
              {{ partnerNickname }}님과 매칭되었어요!
            </div>
            <div class="text-body-2 text-medium-emphasis mb-6">
              지금 바로 영상 채팅을 시작해보세요.
            </div>

            <div class="action-buttons">
              <v-btn
                color="pink"
                size="large"
                variant="flat"
                :disabled="!roomId"
                @click="acceptMatch"
              >
                수락
              </v-btn>
              <v-btn
                color="grey"
                size="large"
                variant="outlined"
                @click="declineMatch"
              >
                거절
              </v-btn>
            </div>
          </div>

          <div v-else-if="errorMessage" class="py-6 text-start">
            <div class="text-body-2 text-medium-emphasis mb-4">
              문제가 계속되면 잠시 후 다시 시도해주세요. 오류가 반복되면 아래 안내를
              참고해 관리자에게 전달해주세요.
            </div>
            <ul v-if="errorDetails.length" class="error-detail-list mb-4">
              <li
                v-for="(detail, index) in errorDetails"
                :key="`secondary-${index}-${detail}`"
              >
                {{ detail }}
              </li>
            </ul>
            <v-btn
              class="mt-4"
              color="pink"
              variant="tonal"
              @click="returnToMatch"
            >
              매칭 조건 다시 선택
            </v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { createStompClient } from '../services/ws'

const router = useRouter()
const auth = useAuthStore()

const isWaiting = ref(true)
const partnerNickname = ref('')
const partnerLoginId = ref('')
const roomId = ref(null)
const errorMessage = ref('')
const errorDetails = ref([])
const waitingMessage = ref('')

const stompErrorHelp = Object.freeze([
  'STOMP 메시지가 서버 clientInboundChannel로 전달되지 못했습니다.',
  '가능한 원인:',
  '• 스레드 풀 또는 작업 큐가 포화 상태입니다.',
  '• 메시지 형식이 잘못되었거나 필수 헤더가 누락되었습니다.',
  '• 서버 측 핸들러(@MessageMapping 등)에서 예외가 발생했습니다.',
  '• STOMP 브로커 또는 연결 설정에 문제가 있습니다.',
  '권장 조치:',
  '• 에러 발생 시점의 서버 로그를 확인하고 필요하면 WebSocket 로그 레벨을 DEBUG로 높여 원인을 파악합니다.',
  '• clientInboundChannel에 사용하는 TaskExecutor의 스레드 및 큐 설정을 조정합니다.',
  '• 클라이언트에서 전송하는 STOMP 프레임과 헤더(Authorization 등)를 검증합니다.',
  '• 브로커 또는 WebSocket 엔드포인트 설정과 인증 정보를 다시 확인합니다.',
])

let client = null
let subscription = null

function goToLogin() {
  router.replace({
    name: 'login',
    query: { redirect: router.currentRoute.value.fullPath },
  })
}

function handleMatchMessage(message) {
  try {
    const payload = JSON.parse(message.body || '{}')
    partnerNickname.value = payload.partnerNickName ?? ''
    partnerLoginId.value = payload.partnerLoginId ?? ''
    roomId.value = payload.roomId ?? null

    errorMessage.value = ''
    errorDetails.value = []
    waitingMessage.value = ''

    if (!roomId.value || !partnerNickname.value) {
      throw new Error('Incomplete match information')
    }

    isWaiting.value = false

    try {
      subscription?.unsubscribe?.()
      subscription = null
    } catch (unsubError) {
      console.warn('Failed to unsubscribe from match queue after receiving result', unsubError)
    }
  } catch (err) {
    console.error('Failed to process match result message:', err)
    errorMessage.value = '매칭 결과를 불러오는 중 오류가 발생했습니다.'
    errorDetails.value = []
    isWaiting.value = false
  }
}

function hydrateFromMatchStartResponse(response) {
  if (!response || typeof response !== 'object') {
    waitingMessage.value = ''
    return
  }

  const { status, message, match } = response

  waitingMessage.value = typeof message === 'string' && message.trim().length > 0 ? message : ''

  if (status === 'MATCHED' && match) {
    partnerNickname.value = match.partnerNickName ?? ''
    partnerLoginId.value = match.partnerLoginId ?? ''
    roomId.value = match.roomId ?? null

    if (roomId.value && partnerNickname.value) {
      errorMessage.value = ''
      errorDetails.value = []
      waitingMessage.value = ''
      isWaiting.value = false
    }
  } else if (status === 'QUEUED' || status === 'ALREADY_IN_QUEUE') {
    isWaiting.value = true
  }
}

function applyInitialMatchState() {
  try {
    const raw = sessionStorage.getItem('matchStartResponse')
    if (!raw) {
      waitingMessage.value = ''
      return
    }

    sessionStorage.removeItem('matchStartResponse')
    const parsed = JSON.parse(raw)
    hydrateFromMatchStartResponse(parsed)
  } catch (err) {
    console.warn('Failed to restore initial match state from sessionStorage', err)
  }
}

function setupStomp() {
  client = createStompClient(auth.token)

  client.onConnect = () => {
    subscription = client.subscribe('/user/queue/match-results', handleMatchMessage)
  }

  client.onWebSocketError = (event) => {
    console.error('WebSocket error', event)
    errorMessage.value = '매칭 서버와 연결할 수 없습니다.'
    errorDetails.value = [
      '네트워크 상태를 확인한 뒤 다시 시도해주세요.',
      '문제가 지속되면 관리자에게 해당 상황과 함께 문의해주세요.',
    ]
    isWaiting.value = false
  }

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers, frame.body)
    errorMessage.value = '매칭 서버에서 STOMP 메시지를 처리하는 중 문제가 발생했습니다.'
    errorDetails.value = [...stompErrorHelp]
    isWaiting.value = false
  }

  client.activate()
}

function acceptMatch() {
  if (!roomId.value) {
    return
  }

  if (!partnerLoginId.value) {
    alert('상대방 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.')
    return
  }

  router.push({
    name: 'match-video',
    params: { roomId: String(roomId.value) },
    query: {
      partnerNickname: partnerNickname.value,
      partnerLoginId: partnerLoginId.value,
    },
  })
}

function declineMatch() {
  if (window.confirm('매칭을 거절하시겠습니까? 다시 조건을 선택할 수 있습니다.')) {
    router.replace({ name: 'match' })
  }
}

function returnToMatch() {
  router.replace({ name: 'match' })
}

onMounted(() => {
  if (!auth.isAuthenticated) {
    goToLogin()
    return
  }

  applyInitialMatchState()
  setupStomp()
})

onBeforeUnmount(() => {
  try {
    subscription?.unsubscribe?.()
  } catch (e) {
    console.warn('Failed to unsubscribe from match result queue', e)
  }
  try {
    client?.deactivate?.()
  } catch (e) {
    console.warn('Failed to deactivate STOMP client', e)
  }
})
</script>

<style scoped>
.action-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}

.error-detail-list {
  padding-left: 20px;
  margin: 0;
  text-align: left;
  font-size: 0.9rem;
  line-height: 1.5;
}
</style>

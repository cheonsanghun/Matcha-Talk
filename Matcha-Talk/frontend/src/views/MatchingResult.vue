<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="10">
        <v-card class="pa-6">
          <!-- 매칭 상태 표시 -->
          <div v-if="!matchFound" class="text-center py-8">
            <v-progress-circular indeterminate color="pink" size="64" class="mb-4"></v-progress-circular>
            <div class="text-h6 mb-2">매칭 상대를 찾고 있습니다...</div>
            <div class="text-caption text-medium-emphasis">{{ sessionStatus }}</div>
          </div>

          <!-- 매칭 성공 시 -->
          <div v-else>
            <v-row class="align-center mb-6">
              <v-avatar size="40" class="me-3">
                <v-img :src="partnerAvatar" alt="avatar" />
              </v-avatar>
              <div>
                <div class="text-h6 text-pink-darken-2">{{ partnerName }}님과 매칭되었습니다</div>
                <div class="text-caption text-medium-emphasis">{{ sessionStatus }}</div>
              </div>
            </v-row>

            <v-row>
              <v-col cols="12" md="9">
                <v-img
                  v-if="mediaUrl"
                  :src="mediaUrl"
                  class="rounded-lg bg-grey-lighten-2 media-wrapper"
                  cover
                >
                  <template #placeholder>
                    <v-row class="fill-height ma-0" align="center" justify="center">
                      <v-progress-circular indeterminate color="pink" />
                    </v-row>
                  </template>
                </v-img>
                <div
                  v-else
                  class="rounded-lg bg-pink-lighten-5 d-flex align-center justify-center media-wrapper"
                >
                  <div class="text-subtitle-1">매칭 이미지 / 영상이 없습니다.</div>
                </div>
              </v-col>
              <v-col cols="12" md="3">
                <v-card variant="outlined" class="pa-4 h-100 chat-wrapper d-flex flex-column">
                  <div class="text-subtitle-2 mb-3">채팅</div>
                  <div class="flex-grow-1 overflow-y-auto">
                    <div class="text-caption text-center text-grey">{{ partnerName }}님과 대화를 시작해보세요!</div>
                  </div>
                </v-card>
              </v-col>
            </v-row>

            <div class="d-flex justify-center gap-4 mt-6">
              <v-btn color="pink" variant="tonal" @click="acceptMatch">채팅방 입장</v-btn>
              <v-btn color="grey" variant="outlined" @click="declineMatch">거절</v-btn>
            </div>
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
import { createRealtimeClient } from '../services/ws'
import { camelizeKeys } from '../utils/case'
import { resolveClientIdentity } from '../utils/identity'

const router = useRouter()
const auth = useAuthStore()

const matchFound = ref(false)
const partnerName = ref('')
const partnerAvatar = ref('https://via.placeholder.com/150')
const sessionStatus = ref('대기 중')
const mediaUrl = ref('')
const roomId = ref(null)

const connectionAttempts = ref(0)
const maxConnectionAttempts = 3
const isConnecting = ref(false)

let websocketClient = null
let reconnectTimer = null
let manualDisconnect = false
const teardownHandlers = []

onMounted(() => {
  connectWebSocket()
})

async function connectWebSocket () {
  if (isConnecting.value || manualDisconnect) return

  const loginId = resolveClientIdentity(auth)

  if (!websocketClient) {
    websocketClient = createRealtimeClient({ queryParams: { loginId } })
    teardownHandlers.push(
      websocketClient.onOpen(() => {
        console.log('✅ WebSocket connected for matching results')
        isConnecting.value = false
        connectionAttempts.value = 0
        sessionStatus.value = '매칭 서버에 연결되었습니다.'
      }),
      websocketClient.onClose((event) => {
        console.log('🔌 WebSocket connection closed:', event)
        isConnecting.value = false
        if (manualDisconnect) return
        if (!event.wasClean) {
          scheduleReconnect()
        }
      }),
      websocketClient.onError((event) => {
        console.error('❌ WebSocket error:', event)
      }),
      websocketClient.onEvent('match-result', handleMatchResult),
      websocketClient.onEvent('match-status', handleMatchStatus),
      websocketClient.onEvent('connected', () => {
        sessionStatus.value = '매칭 대기 중입니다...'
      })
    )
  } else {
    websocketClient.setQueryParams({ loginId })
  }

  isConnecting.value = true

  try {
    await websocketClient.connect()
  } catch (error) {
    console.error('❌ Failed to establish WebSocket connection:', error)
    isConnecting.value = false
  }
}

function scheduleReconnect () {
  if (manualDisconnect) return
  if (connectionAttempts.value >= maxConnectionAttempts) {
    console.error('❌ Max connection attempts reached')
    sessionStatus.value = '매칭 서버 연결에 실패했습니다. 잠시 후 다시 시도해주세요.'
    return
  }

  if (reconnectTimer) {
    console.log('⏳ Reconnect already scheduled, skip new timer')
    return
  }

  connectionAttempts.value += 1
  console.log(`🔄 Retrying WebSocket connection (${connectionAttempts.value}/${maxConnectionAttempts})`)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectWebSocket()
  }, 2000)
}

function handleMatchResult (payload) {
  if (!payload) return

  const normalized = camelizeKeys(payload)

  console.log('🎉 Match found!', normalized)
  matchFound.value = true
  partnerName.value = normalized.partnerNickName || normalized.partnerNickname || '상대방'
  roomId.value = normalized.roomId ?? normalized.room_id ?? null
  sessionStatus.value = '매칭 성공!'
}

function handleMatchStatus (payload) {
  if (typeof payload === 'string') {
    sessionStatus.value = payload || '매칭 대기 중입니다...'
  } else if (payload) {
    const normalized = camelizeKeys(payload)
    sessionStatus.value = normalized.message || '매칭 대기 중입니다...'
  } else {
    sessionStatus.value = '매칭 대기 중입니다...'
  }
}

function acceptMatch () {
  if (!roomId.value) {
    alert('채팅방 정보가 없습니다.')
    return
  }

  manualDisconnect = true
  router.push({
    name: 'chat',
    query: {
      roomId: String(roomId.value),
      partner: partnerName.value || undefined
    }
  })
}

function declineMatch () {
  if (confirm('매칭을 거절하시겠습니까?')) {
    router.push({ name: 'match' })
  }
}

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
    websocketClient.disconnect()
    websocketClient = null
  }
})
</script>

<style scoped>
.media-wrapper {
  max-height: 380px;
}

.chat-wrapper {
  max-height: 380px;
}
</style>

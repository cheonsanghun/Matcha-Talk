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
import { createStompClient } from '../services/ws'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const router = useRouter()
const auth = useAuthStore()

// 매칭 상태
const matchFound = ref(false)
const partnerName = ref('')
const partnerAvatar = ref('https://via.placeholder.com/150')
const sessionStatus = ref('대기 중')
const mediaUrl = ref('')
const roomId = ref(null)

// WebSocket 관련
let stompClient = null
const connectionAttempts = ref(0)
const maxConnectionAttempts = 3

onMounted(async () => {
  await setupWebSocket()
})

async function setupWebSocket() {
  const token = localStorage.getItem('token')
  if (!token) {
    console.error('JWT token not found')
    router.push('/login')
    return
  }

  console.log('Setting up WebSocket connection...')
  stompClient = createStompClient(token)
  
  stompClient.onConnect = (frame) => {
    console.log('✅ Connected to WebSocket for matching results')
    console.log('Connection frame:', frame)
    connectionAttempts.value = 0
    
    try {
      // 매칭 결과 구독 - 더 안전한 방식으로 구독
      const subscription = stompClient.subscribe('/user/queue/match-results', (message) => {
        console.log('📨 Received match result:', message.body)
        
        try {
          const matchResult = JSON.parse(message.body)
          handleMatchResult(matchResult)
        } catch (error) {
          console.error('❌ Error parsing match result:', error)
        }
      }, {
        // 구독 헤더에 토큰 추가 (필요한 경우)
        'Authorization': `Bearer ${token}`
      })
      
      console.log('✅ Subscribed to /user/queue/match-results')
      
      // 테스트용 공통 토픽도 구독 (매칭 상대가 없을 때 테스트용)
      const testSubscription = stompClient.subscribe('/topic/match-results', (message) => {
        console.log('📨 Received test match result:', message.body)
        try {
          const matchResult = JSON.parse(message.body)
          handleMatchResult(matchResult)
        } catch (error) {
          console.error('❌ Error parsing test match result:', error)
        }
      })
      
      // 대기 상태 알림 구독
      const statusSubscription = stompClient.subscribe('/topic/match-status', (message) => {
        console.log('📋 Received match status:', message.body)
        sessionStatus.value = message.body || '매칭 대기 중...'
      })
      
      const userStatusSubscription = stompClient.subscribe('/user/queue/match-status', (message) => {
        console.log('📋 Received user match status:', message.body)
        sessionStatus.value = message.body || '매칭 대기 중...'
      })
      
    } catch (subscribeError) {
      console.error('❌ Subscription error:', subscribeError)
    }
  }

  stompClient.onStompError = (frame) => {
    console.error('❌ STOMP error:', frame.headers?.message || 'Unknown error')
    console.error('Details:', frame.body)
    console.error('Full frame:', frame)
    
    // 연결 재시도
    if (connectionAttempts.value < maxConnectionAttempts) {
      setTimeout(() => {
        connectionAttempts.value++
        console.log(`🔄 Retrying WebSocket connection (${connectionAttempts.value}/${maxConnectionAttempts})`)
        setupWebSocket()
      }, 2000)
    } else {
      console.error('❌ Max connection attempts reached')
      alert('매칭 서버 연결에 실패했습니다. 페이지를 새로고침해주세요.')
    }
  }

  stompClient.onWebSocketError = (event) => {
    console.error('❌ WebSocket error:', event)
  }

  stompClient.onWebSocketClose = (event) => {
    console.log('🔌 WebSocket connection closed:', event)
  }

  // 연결 전 디버그 정보
  stompClient.debug = (str) => {
    console.log('🔍 STOMP Debug:', str)
  }

  try {
    stompClient.activate()
    console.log('🚀 WebSocket activation initiated')
  } catch (error) {
    console.error('❌ Failed to activate WebSocket:', error)
  }
}

function handleMatchResult(matchResult) {
  console.log('🎉 Match found!', matchResult)
  
  matchFound.value = true
  partnerName.value = matchResult.partnerNickName || '상대방'
  roomId.value = matchResult.roomId
  sessionStatus.value = '매칭 성공!'
  
  // TODO: 파트너 아바타 정보가 있으면 설정
  // partnerAvatar.value = matchResult.partnerAvatar || partnerAvatar.value
}

function acceptMatch() {
  if (!roomId.value) {
    alert('채팅방 정보가 없습니다.')
    return
  }
  
  // 채팅방으로 이동
  router.push({
    name: 'chat',
    query: { roomId: roomId.value }
  })
}

function declineMatch() {
  if (confirm('매칭을 거절하시겠습니까?')) {
    // TODO: 매칭 거절 API 호출
    router.push({ name: 'match' })
  }
}

onBeforeUnmount(() => {
  if (stompClient && stompClient.connected) {
    stompClient.deactivate()
    console.log('🔌 Disconnected from WebSocket')
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

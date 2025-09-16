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
            {{ errorMessage }}
          </v-alert>

          <div v-if="isWaiting && !errorMessage" class="py-8">
            <v-progress-circular indeterminate color="pink" size="56" class="mb-4" />
            <div class="text-subtitle-1 text-medium-emphasis">
              매칭 가능한 상대를 찾는 중입니다...
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

          <div v-else-if="errorMessage" class="py-6">
            <div class="text-body-2 text-medium-emphasis">
              문제가 계속되면 잠시 후 다시 시도해주세요.
            </div>
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
    isWaiting.value = false
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
    isWaiting.value = false
  }

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers, frame.body)
    errorMessage.value =
      frame.headers?.message || '매칭 서버에서 오류가 발생했습니다.'
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
</style>

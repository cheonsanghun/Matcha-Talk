<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8">
        <v-card class="pa-8 text-center" elevation="2">
          <v-icon size="56" color="pink" class="mb-4">mdi-heart-outline</v-icon>
          <div class="text-h5 text-pink-darken-2 mb-2">{{ statusTitle }}</div>
          <div class="text-body-2 text-medium-emphasis mb-8">{{ statusDescription }}</div>

          <transition name="fade">
            <div v-if="isWaiting" key="waiting" class="d-flex flex-column align-center waiting-block">
              <v-progress-circular indeterminate size="64" color="pink" class="mb-4" />
              <div class="text-body-2 text-medium-emphasis">
                새로운 인연을 찾는 중입니다. 잠시만 기다려 주세요.
              </div>
            </div>
          </transition>

          <transition name="fade">
            <div v-if="matchedUser" key="matched" class="matched-block">
              <div class="text-h4 font-weight-medium mb-2">{{ matchedUser.nickname }}</div>
              <div class="text-body-2 text-medium-emphasis">님과 연결되었습니다.</div>
            </div>
          </transition>

          <div v-if="matchedUser" class="d-flex justify-center flex-wrap gap-4 mt-10">
            <v-btn
              color="pink"
              size="large"
              variant="flat"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="acceptMatch"
            >
              수락
            </v-btn>
            <v-btn
              color="grey"
              variant="outlined"
              size="large"
              :loading="actionLoading"
              :disabled="actionLoading"
              @click="declineMatch"
            >
              거절
            </v-btn>
          </div>

          <div v-if="errorMessage" class="text-error text-body-2 mt-8">{{ errorMessage }}</div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../services/api'

const router = useRouter()
const route = useRoute()

const matchedUser = ref(null)
const isWaiting = ref(true)
const errorMessage = ref('')
const actionLoading = ref(false)
const sessionId = ref(null)
const pollerId = ref(null)

const requestId = computed(() => route.query.requestId ?? null)

const statusTitle = computed(() => (isWaiting.value ? '매칭 대기 중입니다' : '매칭이 완료되었어요'))

const statusDescription = computed(() =>
  isWaiting.value
    ? '회원님의 조건에 맞는 상대를 찾고 있습니다.'
    : '상대방과 연결하기 전에 매칭을 수락하거나 거절할 수 있습니다.'
)

function stopPolling() {
  if (pollerId.value) {
    clearInterval(pollerId.value)
    pollerId.value = null
  }
}

async function fetchMatchStatus() {
  try {
    const { data } = await api.get('/match/result', {
      params: requestId.value ? { requestId: requestId.value } : undefined,
    })

    if (data?.status === 'MATCHED' && data?.partner) {
      matchedUser.value = {
        nickname: data.partner.nickname ?? '상대방',
        userId: data.partner.userId ?? data.partner.id ?? null,
      }
      sessionId.value = data.sessionId ?? null
      isWaiting.value = false
      errorMessage.value = ''
      stopPolling()
    } else if (data?.status === 'WAITING') {
      isWaiting.value = true
      errorMessage.value = ''
    } else if (data?.status === 'CANCELLED') {
      isWaiting.value = false
      errorMessage.value = '매칭이 취소되었어요. 다시 시도해 주세요.'
      stopPolling()
    }
  } catch (error) {
    console.error('failed to fetch match status', error)
    if (!matchedUser.value) {
      errorMessage.value = '매칭 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
    }
  }
}

function startPolling() {
  fetchMatchStatus()
  pollerId.value = setInterval(fetchMatchStatus, 4000)
}

async function acceptMatch() {
  if (!matchedUser.value) return
  actionLoading.value = true
  try {
    await api.post('/match/accept', {
      requestId: requestId.value,
      partnerId: matchedUser.value.userId,
    })
  } catch (error) {
    console.warn('failed to accept match', error)
  } finally {
    actionLoading.value = false
  }

  await router.push({
    name: 'video-chat',
    query: {
      nickname: matchedUser.value.nickname,
      sessionId: sessionId.value ?? undefined,
    },
  })
}

async function declineMatch() {
  if (!matchedUser.value) return
  actionLoading.value = true
  try {
    await api.post('/match/decline', {
      requestId: requestId.value,
      partnerId: matchedUser.value.userId,
    })
  } catch (error) {
    console.warn('failed to decline match', error)
  } finally {
    actionLoading.value = false
  }

  await router.push({ name: 'match' })
}

onMounted(() => {
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.waiting-block {
  min-height: 120px;
}

.matched-block {
  min-height: 80px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

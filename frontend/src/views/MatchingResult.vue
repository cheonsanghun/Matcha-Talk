<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8">
        <v-card class="pa-6 d-flex flex-column gap-6">
          <div class="d-flex align-center gap-4">
            <v-avatar size="56">
              <v-img :src="partnerAvatar" alt="partner" />
            </v-avatar>
            <div>
              <div class="text-h6 text-pink-darken-2" v-if="isMatched">
                {{ partnerNameDisplay }}님과 매칭되었습니다
              </div>
              <div class="text-h6 text-pink-darken-2" v-else>매칭 대기 중</div>
              <div class="text-body-2 text-medium-emphasis">{{ subHeaderText }}</div>
            </div>
          </div>

          <v-alert type="info" variant="tonal" border="start" color="pink-darken-1">
            {{ statusMessage }}
          </v-alert>

          <div v-if="isMatched" class="text-body-2 text-medium-emphasis">
            두 사람이 모두 "수락"하면 자동으로 영상/채팅 페이지로 이동합니다.
            <span v-if="matchStore.myDecision === 'ACCEPTED'"> 상대의 응답을 기다리는 중입니다.</span>
          </div>

          <div v-else class="text-body-2 text-medium-emphasis">
            매칭이 성사되면 이 화면에서 상대 정보를 확인하고 수락 여부를 결정할 수 있습니다.
          </div>

          <div class="d-flex flex-column gap-2">
            <v-chip
              color="pink-darken-1"
              variant="tonal"
              class="text-body-2 align-self-start"
              v-if="matchStore.partnerDecision === 'ACCEPTED' && !matchStore.bothConfirmed"
            >
              상대가 먼저 수락했습니다. 나도 수락하면 바로 연결돼요.
            </v-chip>
            <v-chip
              color="success"
              variant="tonal"
              class="text-body-2 align-self-start"
              v-if="matchStore.bothConfirmed"
            >
              매칭이 확정되었습니다. 대화 페이지로 이동 중…
            </v-chip>
            <v-chip
              color="grey"
              variant="tonal"
              class="text-body-2 align-self-start"
              v-if="matchStore.sessionClosed"
            >
              세션이 종료되었습니다. 새로운 매칭을 시작해 보세요.
            </v-chip>
          </div>

          <div class="d-flex justify-center gap-4" v-if="isMatched && !decisionFinalized">
            <v-btn
              color="pink"
              variant="tonal"
              size="large"
              :loading="actionLoading.accept"
              :disabled="acceptDisabled"
              @click="acceptMatch"
            >
              수락
            </v-btn>
            <v-btn
              color="grey"
              variant="outlined"
              size="large"
              :loading="actionLoading.decline"
              :disabled="declineDisabled"
              @click="declineMatch"
            >
              거절
            </v-btn>
          </div>

          <div class="text-caption text-medium-emphasis" v-if="!isMatched">
            현재 대기 중인 사용자가 없으면 새로운 상대가 들어올 때까지 자동으로 대기합니다.
          </div>

          <v-divider />

          <div class="text-caption text-disabled">
            문제가 지속되면 새로고침하거나 매칭을 다시 시작해 주세요.
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import defaultAvatar from '../assets/default-avatar.svg'
import { createStompClient } from '../services/ws'
import api from '../services/api'
import { useAuthStore } from '../stores/auth'
import { useMatchStore } from '../stores/match'

const router = useRouter()
const auth = useAuthStore()
const matchStore = useMatchStore()

const actionLoading = reactive({ accept: false, decline: false })
const client = ref(null)
const connected = ref(false)
let matchSubscription = null
const STATUS_POLL_INTERVAL = 2500
let statusPollTimer = null
const navigatedToSession = ref(false)

const partnerAvatar = computed(() => defaultAvatar)
const partnerNameDisplay = computed(() => matchStore.partnerNickName || '상대 대기 중')
const isMatched = computed(() => matchStore.isMatched)
const waitingStatusText = computed(() =>
  matchStore.waitingCount > 0
    ? '매칭 중입니다. 잠시만 기다려주세요.'
    : '현재 대기 중인 사용자가 없습니다.'
)
const subHeaderText = computed(() =>
  isMatched.value ? '수락 또는 거절을 선택해 주세요.' : waitingStatusText.value
)
const statusMessage = computed(() => matchStore.statusMessage || waitingStatusText.value)
const decisionFinalized = computed(() => matchStore.sessionClosed || matchStore.bothConfirmed)
const acceptDisabled = computed(
  () =>
    !matchStore.requestId ||
    matchStore.myDecision === 'ACCEPTED' ||
    matchStore.sessionClosed ||
    actionLoading.accept ||
    actionLoading.decline
)
const declineDisabled = computed(
  () =>
    !matchStore.requestId ||
    matchStore.myDecision === 'ACCEPTED' ||
    matchStore.sessionClosed ||
    actionLoading.decline
)

if (!matchStore.state) {
  router.replace({ name: 'match' })
}

function stopStatusPolling() {
  if (statusPollTimer) {
    clearInterval(statusPollTimer)
    statusPollTimer = null
  }
}

async function requestStatusRefresh() {
  if (!matchStore.requestId || matchStore.sessionClosed || matchStore.bothConfirmed) {
    return
  }
  try {
    const { data } = await api.get(`/match/requests/${matchStore.requestId}`)
    if (data) {
      matchStore.setFromStartResponse(data)
      checkSessionTransition()
    }
  } catch (error) {
    console.error('매칭 상태 조회 실패', error)
  }
}

function ensureStatusPolling(immediate = false) {
  if (!matchStore.requestId || matchStore.sessionClosed || matchStore.bothConfirmed) {
    stopStatusPolling()
    return
  }
  if (!statusPollTimer) {
    statusPollTimer = setInterval(() => {
      if (!matchStore.requestId || matchStore.sessionClosed || matchStore.bothConfirmed) {
        stopStatusPolling()
        return
      }
      void requestStatusRefresh()
    }, STATUS_POLL_INTERVAL)
  }
  if (immediate) {
    void requestStatusRefresh()
  }
}

async function acceptMatch() {
  if (acceptDisabled.value) {
    return
  }
  actionLoading.accept = true
  try {
    const { data } = await api.post(`/match/requests/${matchStore.requestId}/accept`)
    matchStore.setMyDecision('ACCEPTED', data?.message, data?.bothAccepted)
    if (!data?.bothAccepted) {
      ensureStatusPolling(true)
    }
    checkSessionTransition()
  } catch (error) {
    console.error('매칭 수락 실패', error)
    window.alert(error?.response?.data || '매칭 수락 중 오류가 발생했습니다.')
  } finally {
    actionLoading.accept = false
  }
}

async function declineMatch() {
  if (declineDisabled.value) {
    return
  }
  actionLoading.decline = true
  try {
    const { data } = await api.post(`/match/requests/${matchStore.requestId}/decline`)
    matchStore.setMyDecision('DECLINED', data?.message, false)
  } catch (error) {
    console.error('매칭 거절 실패', error)
    window.alert(error?.response?.data || '매칭 거절 중 오류가 발생했습니다.')
  } finally {
    actionLoading.decline = false
  }
}

function handleMatchMessage(frame) {
  try {
    const payload = JSON.parse(frame.body)
    if (payload.eventType === 'MATCH_FOUND') {
      stopStatusPolling()
    }
    matchStore.applyMatchEvent(payload)
    checkSessionTransition()
  } catch (error) {
    console.error('매칭 이벤트 처리 실패', error)
  }
}

function checkSessionTransition() {
  if (navigatedToSession.value) {
    return
  }
  if (matchStore.bothConfirmed && matchStore.roomId) {
    navigatedToSession.value = true
    stopStatusPolling()
    router.replace({
      name: 'match-session',
      params: { roomId: matchStore.roomId },
      query: { requestId: matchStore.requestId ?? undefined },
    })
  }
}

watch(
  () => matchStore.bothConfirmed,
  (confirmed) => {
    if (confirmed) {
      stopStatusPolling()
      checkSessionTransition()
    }
  }
)

watch(
  () => [matchStore.requestId, matchStore.state, matchStore.sessionClosed, matchStore.bothConfirmed],
  ([requestId, state, sessionClosed, bothConfirmed], previous) => {
    if (!requestId || sessionClosed || bothConfirmed) {
      stopStatusPolling()
      return
    }
    const prevRequestId = previous ? previous[0] : null
    const prevState = previous ? previous[1] : null
    const prevBothConfirmed = previous ? previous[3] : null
    const immediate =
      !previous ||
      requestId !== prevRequestId ||
      prevState !== state ||
      prevBothConfirmed !== bothConfirmed
    ensureStatusPolling(immediate)
  },
  { immediate: true }
)

onMounted(() => {
  ensureStatusPolling(true)

  client.value = createStompClient(auth.token)
  client.value.onConnect = () => {
    connected.value = true
    matchSubscription = client.value.subscribe('/user/queue/match-results', handleMatchMessage)
  }
  client.value.onDisconnect = () => {
    connected.value = false
    matchSubscription?.unsubscribe()
    matchSubscription = null
  }
  client.value.activate()

  checkSessionTransition()
})

onBeforeUnmount(() => {
  stopStatusPolling()
  matchSubscription?.unsubscribe()
  matchSubscription = null
  client.value?.deactivate?.()
})
</script>

<style scoped>
.v-container {
  min-height: 60vh;
}
</style>

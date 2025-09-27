<!-- src/views/Login.vue -->
<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="6" lg="5">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">로그인</div>

          <v-form @submit.prevent="onLogin">
            <v-text-field
              v-model="login_id"
              label="아이디 또는 이메일"
              variant="outlined"
              :disabled="disabled"
              autocomplete="username"
            />
            <v-text-field
              v-model="password"
              type="password"
              label="비밀번호"
              variant="outlined"
              :disabled="disabled"
              autocomplete="current-password"
            />

            <v-expand-transition>
              <div v-if="message" class="mb-2">
                <v-alert
                  :type="messageType"
                  variant="tonal"
                  density="compact"
                  border="start"
                  class="alert-compact text-body-2"
                >
                  <div class="d-flex align-center ga-2">
                    <span>{{ message }}</span>
                    <span v-if="locked && remainingSec > 0" class="countdown">⏱ {{ countdown }}</span>
                  </div>
                </v-alert>
              </div>
            </v-expand-transition>

            <div class="d-flex ga-3 mt-4">
              <v-btn color="pink" type="submit" :disabled="disabled" :loading="loading">
                <span v-if="!locked">로그인</span>
                <span v-else>잠금 해제 대기중 ({{ countdown }})</span>
              </v-btn>

              <v-spacer />

              <v-btn variant="tonal" color="pink" :disabled="loading || locked" to="/find-id">
                아이디 찾기
              </v-btn>
              <v-btn variant="tonal" color="pink" :disabled="loading || locked" to="/reset-password">
                비밀번호 찾기
              </v-btn>
            </div>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../services/api'

const router = useRouter()
const route = useRoute()
const store = useAuthStore()

const login_id = ref('')
const password = ref('')

// 로딩/메시지/잠금 상태
const loading = ref(false)
const message = ref('')
const messageType = ref('error')
const locked = ref(false)
const remainingSec = ref(0)
const countdown = ref('')
let timerId = null

const disabled = computed(() => loading.value || locked.value)

function formatMMSS (sec) {
  const m = Math.floor(sec / 60).toString().padStart(2, '0')
  const s = Math.floor(sec % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}
function clearTimer () { if (timerId) { clearInterval(timerId); timerId = null } }
function startTimer (seconds) {
  clearTimer()
  remainingSec.value = seconds
  countdown.value = formatMMSS(remainingSec.value)
  timerId = setInterval(() => {
    remainingSec.value--
    countdown.value = formatMMSS(remainingSec.value)
    if (remainingSec.value <= 0) {
      clearTimer(); locked.value = false; message.value = ''
    }
  }, 1000)
}
onBeforeUnmount(clearTimer)

onMounted(fetchCsrfToken)

async function fetchCsrfToken () {
  try {
    await api.get('/auth/csrf', { params: { t: Date.now() }, skipSnakifyParams: true })
  } catch (error) {
    console.warn('CSRF 토큰을 갱신하지 못했습니다.', error?.response?.status)
  }
}

async function onLogin () {
  if (!login_id.value || !password.value) {
    messageType.value = 'error'
    message.value = '아이디/비밀번호를 입력하세요.'
    return
  }
  if (disabled.value) return

  loading.value = true
  message.value = ''

  try {
    // snake/camel 둘 다 전송해서 백엔드 매핑 차이 흡수
    const payload = {
      login_id: login_id.value.trim(),
      loginId:  login_id.value.trim(),
      password: password.value,
    }
    await fetchCsrfToken()
    const { data } = await api.post('/auth/login', payload)

    const userRaw = data.user ?? data
    const role = userRaw.roleName ?? userRaw.rolename ?? userRaw.role ?? (userRaw.loginId === 'admin' ? 'ROLE_ADMIN' : null)
    const user = { ...userRaw, roleName: role ?? userRaw.roleName }

    if (typeof store.setSession === 'function') {
      await store.setSession(user)
    } else if (typeof store.login === 'function') {
      await store.login({ user })
    }

    if (typeof store.hydrateMeIfNeeded === 'function') {
      await store.hydrateMeIfNeeded()
    }

    // 리다이렉트: redirect 쿼리 > 관리자면 /admin > /
    const redirect = route.query?.redirect || (user.roleName === 'ROLE_ADMIN' ? '/admin' : '/')
    await nextTick()
    await router.replace(redirect)
  } catch (err) {
    const res = err?.response
    const status = res?.status

    if (status === 423 && res?.data?.code === 'ACCOUNT_LOCKED') {
      locked.value = true
      messageType.value = 'error'
      message.value = '계정이 잠겨 있습니다. 잠금 해제까지 대기하세요.'
      startTimer(Number(res.data.remainingSeconds ?? 600))
    } else if (status === 401 && res?.data?.code === 'BAD_CREDENTIALS') {
      messageType.value = 'error'
      const left = res?.data?.remainingAttempts
      message.value = res.data.message ||
        (typeof left === 'number'
          ? `아이디 또는 비밀번호가 올바르지 않습니다. (남은 시도: ${left}회)`
          : '아이디 또는 비밀번호가 올바르지 않습니다.')
    } else if (status === 403) {
      messageType.value = 'error'
      message.value = res?.data?.message || '비활성화된 계정입니다. 관리자에게 문의하세요.'
    } else {
      messageType.value = 'error'
      message.value = res?.data?.message || `로그인 중 오류가 발생했습니다. (${status ?? 'network'})`
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.alert-compact { padding-top: 8px; padding-bottom: 8px; }
.alert-compact :deep(.v-alert__content) { line-height: 1.2; }
.countdown { font-variant-numeric: tabular-nums; font-weight: 600; }
</style>

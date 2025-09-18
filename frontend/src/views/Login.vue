<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="6" lg="5">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">로그인</div>
          <v-form @submit.prevent="onLogin">
            <v-text-field v-model="login_id" label="아이디" variant="outlined"/>
            <v-text-field v-model="password" type="password" label="비밀번호" variant="outlined"/>
            <div class="d-flex ga-3 mt-4">
              <v-btn color="pink" type="submit">로그인</v-btn>
              <v-spacer/>
              <v-btn variant="tonal" color="pink">비밀번호 찾기</v-btn>
            </div>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import {ref, nextTick } from 'vue'
import {useAuthStore} from '../stores/auth'
import {useRouter} from 'vue-router'
import api from '../services/api'

const router = useRouter()
const store = useAuthStore()
const login_id = ref('')
const password = ref('')

const hasUserPayload = (payload) => {
  if (!payload || typeof payload !== 'object') return false
  const candidateKeys = [
    'loginId',
    'login_id',
    'username',
    'userName',
    'nickname',
    'nickName',
    'name',
    'email',
    'id',
    'userPid',
    'user_id',
    'userId',
  ]
  return candidateKeys.some((key) => payload[key] !== undefined && payload[key] !== null)
}

const normalizeUserPayload = (raw) => {
  if (!raw || typeof raw !== 'object') return null

  const normalized = { ...raw }

  const idCandidate = raw.id ?? raw.userPid ?? raw.user_id ?? raw.userId
  if (idCandidate !== undefined) normalized.id = idCandidate

  const loginIdCandidate = raw.loginId ?? raw.login_id ?? raw.username ?? raw.userName
  if (loginIdCandidate !== undefined) normalized.loginId = loginIdCandidate

  const nicknameCandidate = raw.nickname ?? raw.nickName ?? raw.name
  if (nicknameCandidate !== undefined) normalized.nickname = nicknameCandidate

  const emailCandidate = raw.email ?? raw.mail
  if (emailCandidate !== undefined) normalized.email = emailCandidate

  if (normalized.id === undefined) delete normalized.id
  if (normalized.loginId === undefined) delete normalized.loginId
  if (normalized.nickname === undefined) delete normalized.nickname
  if (normalized.email === undefined) delete normalized.email

  delete normalized.login_id
  delete normalized.nickName
  delete normalized.userPid
  delete normalized.user_id
  delete normalized.userId
  delete normalized.userName
  delete normalized.username
  delete normalized.mail
  delete normalized.token
  delete normalized.accessToken
  delete normalized.refreshToken
  delete normalized.jwt

  return normalized
}

async function onLogin() {
  const loginIdValue = login_id.value.trim()
  const passwordValue = password.value

  if (!loginIdValue || !passwordValue) {
    return alert('아이디/비밀번호를 입력하세요')
  }
  try {
    const {data} = await api.post('/auth/login', {
      loginId: loginIdValue,
      password: passwordValue,
    })

    let token = data?.token ?? data?.accessToken ?? data?.jwt ?? null
    if (typeof token === 'string') {
      token = token.trim()
      if (!token) token = null
    }

    const rawUser = data?.user ?? data?.profile ?? (hasUserPayload(data) ? data : null)
    const user = normalizeUserPayload(rawUser)

    const loginSuccess = store.login({ token, user })
    if (!loginSuccess) {
      alert('로그인 토큰을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.')
      return
    }

    // Pinia 상태가 반응형으로 전파된 다음 라우팅 (가드가 다시 /login으로 되돌리는 현상 방지)
    await nextTick()

    // 라우팅
    //await router.replace({ name: 'home' })

    const target = user?.roleName === 'ROLE_ADMIN' ? { name: 'admin-dashboard' } : { name: 'home' }
    await router.replace(target)

    // 최후의 수단(가드/상태 꼬임이 있으면 강제 이동)
    // window.location.href = '/'
  } catch (err) {
    const status = err.response?.status ?? 'network'
    alert(err.response?.data?.message || `로그인 실패 (${status})`)
    console.error('login error:', status, err.response?.data || err)
  }
}
</script>

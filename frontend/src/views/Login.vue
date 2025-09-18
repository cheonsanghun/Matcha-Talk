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

async function onLogin() {
  if (!login_id.value || !password.value) {
    return alert('아이디/비밀번호를 입력하세요')
  }
  try {
    const {data} = await api.post('/auth/login', {
      login_id: login_id.value,
      password: password.value,
    })

    const token = data.token ?? null
    const user = data.user ?? data

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

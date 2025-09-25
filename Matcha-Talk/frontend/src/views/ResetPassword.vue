<!-- frontend/src/views/ResetPassword.vue -->
<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="6" lg="5">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">
            비밀번호 재설정
          </div>

          <v-form autocomplete="off" @submit.prevent="onSubmit">
            <!-- STEP 1: 이메일 입력 후 인증번호 발송 -->
            <template v-if="step === 1">
              <v-text-field
                v-model.trim="email"
                name="account-email"
                label="가입한 이메일"
                variant="outlined"
                autocomplete="email"
                :disabled="loading"
                :rules="[v => !!v || '이메일을 입력하세요.']"
              />
              <v-btn
                color="pink"
                class="mt-2"
                :loading="loading"
                :disabled="!email"
                @click="sendCode"
              >
                인증번호 보내기
              </v-btn>
            </template>

            <!-- STEP 2: 인증번호 + 새 비밀번호 설정 -->
            <template v-else>
              <div class="text-body-2 mb-3">
                인증 대상 이메일: <b>{{ email }}</b>
              </div>

              <!-- 토큰: 숫자 6자리 -->
              <v-text-field
                v-model.trim="token"
                name="otp"
                label="인증번호 (이메일로 전송됨)"
                variant="outlined"
                :disabled="loading"
                :rules="tokenRules"
                type="tel"
                inputmode="numeric"
                :maxlength="6"
                :counter="6"
                autocomplete="one-time-code"
                autocapitalize="off"
                spellcheck="false"
                autofocus
              />

              <v-text-field
                v-model.trim="pw1"
                :type="showPw1 ? 'text' : 'password'"
                label="새 비밀번호 (8~64자)"
                variant="outlined"
                :append-inner-icon="showPw1 ? 'mdi-eye-off' : 'mdi-eye'"
                @click:append-inner="showPw1 = !showPw1"
                :disabled="loading"
                :rules="pwRules"
                hint="영문 대/소문자·숫자·특수문자를 모두 포함하세요."
                persistent-hint
                autocomplete="new-password"
              />

              <v-text-field
                v-model.trim="pw2"
                :type="showPw2 ? 'text' : 'password'"
                label="새 비밀번호 확인"
                variant="outlined"
                :append-inner-icon="showPw2 ? 'mdi-eye-off' : 'mdi-eye'"
                @click:append-inner="showPw2 = !showPw2"
                :disabled="loading"
                :rules="[v => v === pw1 || '비밀번호가 일치하지 않습니다.']"
                autocomplete="new-password"
              />

              <v-btn
                color="pink"
                class="mt-2"
                type="submit"
                :loading="loading"
                :disabled="!canSubmit"
              >
                인증 및 변경
              </v-btn>
              <v-btn variant="text" class="mt-2" @click="backToStep1" :disabled="loading">
                이메일 다시 입력
              </v-btn>
            </template>

            <v-expand-transition>
              <div v-if="message" class="mt-4 text-body-2">
                {{ message }}
              </div>
            </v-expand-transition>

            <v-expand-transition>
              <div v-if="success" class="mt-4 text-body-2">
                변경된 비밀번호로 <router-link to="/login">로그인</router-link> 하세요.
              </div>
            </v-expand-transition>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
// 🔧 별칭 없이 상대 경로로 (vite.config.js에 alias 없으니까)
import api from '../services/api'

const email   = ref('')
const token   = ref('')
const pw1     = ref('')
const pw2     = ref('')
const step    = ref(1)
const loading = ref(false)
const message = ref('')
const success = ref(false)

const showPw1 = ref(false)
const showPw2 = ref(false)

// 강력한 비밀번호 규칙(회원가입/로그인과 일치): 8~64자, 대/소문자/숫자/특 1개 이상
const strongPw = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}\[\]|:;"'<>,.?/]).{8,64}$/
// 토큰: 숫자 6자리
const tokenRegex = /^\d{6}$/

// 토큰은 숫자만 유지 + 6자리 제한
watch(token, (v) => {
  const only = String(v || '').replace(/\D/g, '').slice(0, 6)
  if (only !== v) token.value = only
})

const pwRules = [
  v => !!v || '비밀번호를 입력하세요.',
  v => (v && v.length >= 8) || '8자 이상 입력하세요.',
  v => (v && v.length <= 64) || '64자 이하로 입력하세요.',
  v => strongPw.test(v || '') || '영문 대/소문자·숫자·특수문자를 모두 포함하세요.'
]

const tokenRules = [
  v => !!v || '인증번호를 입력하세요.',
  v => tokenRegex.test(String(v || '').trim()) || '인증번호 형식이 올바르지 않습니다.'
]

const canSubmit = computed(() =>
  tokenRegex.test(String(token.value || '').trim()) &&
  strongPw.test(String(pw1.value || '').trim()) &&
  String(pw1.value || '').trim() === String(pw2.value || '').trim()
)

const sendCode = async () => {
  loading.value = true
  message.value = ''
  success.value = false
  try {
    const { data } = await api.post(
      '/auth/password-reset/request',
      { email: String(email.value || '').trim() },
      { headers: { 'Content-Type': 'application/json' } }
    )
    token.value = ''
    pw1.value = ''
    pw2.value = ''
    message.value = '인증번호를 이메일로 보냈습니다.'
    if (data?.dev_token) message.value += ` (테스트용 토큰: ${data.dev_token})`
    step.value = 2
  } catch (e) {
    message.value = e?.response?.data?.message || '요청 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
  }
}

const onSubmit = async () => {
  if (step.value !== 2) return
  if (!canSubmit.value) {
    message.value = '입력값을 확인하세요.'
    return
  }
  loading.value = true
  message.value = ''
  success.value = false
  try {
    await api.post(
      '/auth/password-reset/confirm',
      {
        email: String(email.value || '').trim(),
        token: String(token.value || '').trim(),
        newPassword: String(pw1.value || '').trim()
      },
      { headers: { 'Content-Type': 'application/json' } }
    )
    message.value = '비밀번호가 변경되었습니다.'
    success.value = true
  } catch (e) {
    message.value = e?.response?.data?.message || '변경 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
  }
}

const backToStep1 = () => {
  token.value = ''
  pw1.value = ''
  pw2.value = ''
  message.value = ''
  success.value = false
  step.value = 1
}
</script>

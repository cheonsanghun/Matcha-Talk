<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="6" lg="5">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">로그인</div>
          <v-alert
            v-if="errorMessage"
            type="error"
            variant="tonal"
            class="mb-4"
          >
            {{ errorMessage }}
          </v-alert>
          <v-form @submit.prevent="onLogin">
            <v-text-field
              v-model="form.loginId"
              label="아이디"
              variant="outlined"
              :disabled="loading"
              autocomplete="username"
              required
            />
            <v-text-field
              v-model="form.password"
              type="password"
              label="비밀번호"
              variant="outlined"
              :disabled="loading"
              autocomplete="current-password"
              required
            />
            <div class="d-flex ga-3 mt-4">
              <v-btn color="pink" type="submit" :loading="loading" block>로그인</v-btn>
            </div>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { loading } = storeToRefs(authStore)

const form = reactive({
  loginId: '',
  password: '',
})

const errorMessage = ref('')

const onLogin = async () => {
  if (!form.loginId || !form.password) {
    errorMessage.value = '아이디와 비밀번호를 입력하세요.'
    return
  }
  errorMessage.value = ''
  try {
    await authStore.login({ loginId: form.loginId, password: form.password })
    const redirect = route.query.redirect ?? { name: 'home' }
    router.replace(redirect)
  } catch (error) {
    errorMessage.value = error?.message ?? '로그인에 실패했습니다. 입력 정보를 확인해주세요.'
  }
}
</script>

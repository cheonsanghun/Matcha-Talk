<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="6" lg="5">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">
            아이디 찾기
          </div>

          <v-form @submit.prevent="onFindId">
            <v-text-field
              v-model="email"
              label="가입한 이메일"
              variant="outlined"
              autocomplete="email"
              :disabled="loading"
            />
            <v-btn type="submit" color="pink" block class="mt-4"
                   :loading="loading" :disabled="!email">
              아이디 찾기
            </v-btn>
          </v-form>

          <v-expand-transition>
            <div v-if="message" class="mt-4 text-center text-body-2">
              {{ message }}
            </div>
          </v-expand-transition>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref } from 'vue'
import api from '../services/api'

const email = ref('')
const message = ref('')
const loading = ref(false)

const onFindId = async () => {
  loading.value = true
  message.value = ''
  try {
    const res = await api.post('/auth/find-id', { email: email.value })
    message.value = res.data.message
  } catch (e) {
    message.value = e.response?.data?.message || '에러 발생'
  } finally {
    loading.value = false
  }
}
</script>

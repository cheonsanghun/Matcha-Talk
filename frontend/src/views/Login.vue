<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="6" lg="5">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">로그인</div>
          <v-form @submit.prevent="onLogin">
            <v-text-field v-model="login_id" label="아이디" variant="outlined" />
            <v-text-field v-model="password" type="password" label="비밀번호" variant="outlined" />
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
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const router = useRouter()
const store = useAuthStore()
const login_id = ref('')
const password = ref('')

async function onLogin(){
  if(!login_id.value || !password.value){
    return alert('아이디/비밀번호를 입력하세요')
  }
  const fakeToken = 'demo-token'
  const user = { user_pid: 1, login_id: login_id.value, nick_name: 'Guest', email:'guest@example.com' }
  store.login({ token: fakeToken, user })
  router.push('/')
}
</script>

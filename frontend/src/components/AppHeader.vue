<template>
  <v-app-bar flat class="bg-white-lighten-5">
    <v-container class="d-flex align-center justify-space-between">
      <!-- 로고/홈 -->
      <div class="d-flex align-center">
        <v-icon class="me-2" color="pink">mdi-flower</v-icon>
        <RouterLink :to="{ name: 'home' }" class="text-h6 text-decoration-none text-pink-darken-2">
          Matcha Talk
        </RouterLink>
      </div>

      <!-- 비로그인 -->
      <div v-if="!isAuth" class="d-flex ga-3">
        <v-btn variant="outlined" color="pink" :to="{ name: 'login' }">로그인</v-btn>
        <v-btn variant="outlined" color="pink" :to="{ name: 'register' }">회원가입</v-btn>
      </div>

      <!-- 로그인 -->
      <div v-else class="d-flex ga-3">
        <!-- 안내용 비활성 버튼: 라우팅 제거 -->
        <v-btn variant="tonal" color="pink" disabled>상점</v-btn>
        <v-btn color="pink" variant="flat" :to="{ name: 'profile' }">프로필</v-btn>
      </div>
    </v-container>
  </v-app-bar>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth'

const store = useAuthStore()
// isAuthenticated 게터가 token || user 기준인지 확인!
const { isAuthenticated: isAuth } = storeToRefs(store)
</script>

<style scoped>
.text-decoration-none { text-decoration: none; }
</style>

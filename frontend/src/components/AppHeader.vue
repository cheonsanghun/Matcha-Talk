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

      <!-- 비로그인 상태 -->
      <div v-if="!isAuth" class="d-flex ga-3">
        <v-btn variant="outlined" color="pink" :to="{ name: 'login' }">로그인</v-btn>
        <v-btn variant="outlined" color="pink" :to="{ name: 'register' }">회원가입</v-btn>
      </div>

      <!-- 로그인 상태 -->
      <div v-else class="d-flex ga-3">
        <!-- 관리자 전용 버튼 -->
        <v-btn
          v-if="isAdmin"
          color="pink"
          variant="tonal"
          :to="{ name: 'admin' }"
        >
          관리자
        </v-btn>

        <!-- 문의 버튼 -->
        <v-btn
          color="pink"
          variant="flat"
          :to="{ name: 'support-inquiry' }"
        >
          문의
        </v-btn>

        <!-- 신고 버튼 -->
        <v-btn
          color="pink"
          variant="flat"
          :to="{ name: 'support-report' }"
        >
          신고
        </v-btn>

        <!-- 프로필 페이지로 이동하는 버튼 -->
        <v-btn color="pink" variant="flat" :to="{ name: 'profile' }">프로필</v-btn>

        <!-- 로그아웃 버튼 -->
        <v-btn color="pink" variant="flat" @click="logout">로그아웃</v-btn>
      </div>
    </v-container>
  </v-app-bar>
</template>

<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth' // 상대경로 유지

const store = useAuthStore()
const { isAuthenticated: isAuth, user } = storeToRefs(store)

// 관리자 판별: roleName(카멜) 또는 rolename(스네이크) 모두 대응
const isAdmin = computed(() => {
  const role = user.value?.roleName ?? user.value?.rolename ?? user.value?.role
  return role === 'ROLE_ADMIN'
})

// 로그아웃 기능
function logout() {
  store.logout()  // Pinia에서 관리하는 상태 초기화
  // 로그아웃 후, 홈으로 리다이렉션
  window.location.href = '/'
}
</script>

<style scoped>
.text-decoration-none { text-decoration: none; }
</style>

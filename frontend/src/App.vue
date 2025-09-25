<template>
  <v-app>
    <!-- 헤더 -->
    <AppHeader />

    <!-- 본문 -->
    <v-main class="bg-pink-lighten-5">
      <router-view />
    </v-main>

    <!-- 푸터 상단 정보 -->
    <v-container class="py-8">
      <v-row>
        <v-col cols="12" md="4" class="mb-6 mb-md-0">
          <div class="text-h6 text-pink-darken-2 mb-2">Matcha Talk</div>
          <div class="text-body-2">문화 교류 랜덤 채팅 플랫폼</div>
        </v-col>
        <v-col cols="12" md="4" class="mb-6 mb-md-0">
          <div class="text-subtitle-1 font-weight-bold mb-2">서비스</div>
          <div class="text-body-2">매칭</div>
          <div class="text-body-2">채팅</div>
          <div class="text-body-2">상점</div>
        </v-col>
        <v-col cols="12" md="4">
          <div class="text-subtitle-1 font-weight-bold mb-2">고객지원</div>
          <div class="text-body-2">support@matchatalk.com</div>
          <div class="text-body-2 mt-1">FAQ</div>
        </v-col>
      </v-row>
    </v-container>

    <!-- 푸터 -->
    <v-footer class="bg-white">
      <v-row class="mt-8">
        <v-col cols="12" class="text-center">
          <div class="text-caption">&copy; 2025 Matcha Talk. All rights reserved.</div>
        </v-col>
      </v-row>
    </v-footer>
  </v-app>
</template>

<script setup>
import { onMounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import { useAuthStore } from '@/stores/auth'

// 앱 시작 시 한 번 사용자 정보(me) 하이드레이트
const auth = useAuthStore()
onMounted(async () => {
  try {
    if (typeof auth.hydrateMeIfNeeded === 'function') {
      await auth.hydrateMeIfNeeded()
    } else if (typeof auth.me === 'function') {
      // 스토어에 hydrate 함수가 없으면 me() 호출 시도
      await auth.me()
    }
  } catch (e) {
    // 사용자 정보가 없거나 토큰 만료 시 조용히 통과
    // 필요하면 여기서 콘솔 로그 추가 가능
  }
})
</script>

<style>
html, body, #app {
  height: 100%;
}
</style>

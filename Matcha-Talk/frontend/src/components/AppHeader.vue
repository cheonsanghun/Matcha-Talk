<template>
  <v-app-bar flat class="bg-white-lighten-5">
    <v-container class="d-flex align-center justify-space-between">
      <div class="d-flex align-center">
        <v-icon class="me-2" color="pink">mdi-flower</v-icon>
        <router-link to="/" class="text-h6 text-decoration-none text-pink-darken-2">
          Matcha Talk
        </router-link>
      </div>

      <div v-if="!isAuth" class="d-flex ga-3">
        <v-btn variant="outlined" color="pink" to="/login">로그인</v-btn>
        <v-btn variant="outlined" color="pink" to="/register">회원가입</v-btn>
      </div>

      <div v-else class="d-flex ga-3">
        <v-btn variant="tonal" color="pink" to="/match">매칭</v-btn>
        <v-btn variant="tonal" color="pink" to="/chat">채팅</v-btn>
        <v-btn variant="tonal" color="pink" to="/rtc-test">영상테스트</v-btn>
        <v-btn variant="tonal" color="pink" to="/vocabulary">단어장</v-btn>
        <v-btn variant="tonal" color="pink" to="/support/inquiry">문의하기</v-btn>
        <v-btn variant="tonal" color="pink" to="/support/report">신고하기</v-btn>
        <v-btn color="pink" to="/profile">프로필</v-btn>
        <v-btn v-if="isAdmin" color="pink" variant="outlined" to="/admin">관리</v-btn>
      </div>
    </v-container>
  </v-app-bar>
</template>

<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth'

const store = useAuthStore()
const { isAuthenticated: isAuth } = storeToRefs(store)
const isAdmin = computed(() => store.isAdmin())
</script>

<style scoped>
.text-decoration-none { text-decoration: none; }
</style>

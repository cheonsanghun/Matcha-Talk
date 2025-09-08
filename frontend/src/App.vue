<template>
  <v-app>
    <AppHeader/>
    <v-main class="bg-pink-lighten-5">
      <router-view/>
    </v-main>
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
import AppHeader from './components/AppHeader.vue'
</script>

<style>
html, body, #app {
  height: 100%;
}
</style>

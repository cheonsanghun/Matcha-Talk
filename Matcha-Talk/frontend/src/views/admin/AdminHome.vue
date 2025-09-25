<template>
  <v-container class="py-10">
    <div class="text-h5 mb-4">관리자 대시보드</div>

    <v-alert type="info" variant="tonal" class="mb-6">
      안녕하세요, <b>{{ user?.loginId || 'admin' }}</b> 님. (권한: {{ roleText }})
    </v-alert>

    <v-row class="ga-4">
      <!-- 사용자 관리 -->
      <v-col cols="12" md="4">
        <v-card class="pa-4">
          <div class="text-subtitle-1 mb-2">사용자 관리</div>
          <div class="text-body-2 mb-4">계정 잠금/활성, 기본정보 변경</div>
          <v-btn color="pink" variant="tonal" :to="{ name: 'admin-users' }">열기</v-btn>
        </v-card>
      </v-col>

      <!-- 문의 관리 -->
      <v-col cols="12" md="4">
        <v-card class="pa-4">
          <div class="text-subtitle-1 mb-2">문의 관리</div>
          <div class="text-body-2 mb-4">문의 확인 및 답변/종결</div>
          <v-btn color="pink" variant="tonal" :to="{ name: 'admin-inquiries' }">열기</v-btn>
        </v-card>
      </v-col>

      <!-- 신고 관리 -->
      <v-col cols="12" md="4">
        <v-card class="pa-4">
          <div class="text-subtitle-1 mb-2">신고 관리</div>
          <div class="text-body-2 mb-4">신고 검토, 제재/반려</div>
          <v-btn color="pink" variant="tonal" :to="{ name: 'admin-reports' }">열기</v-btn>
        </v-card>
      </v-col>
    </v-row>

    <div class="d-flex ga-3 mt-8">
      <v-btn color="pink" variant="elevated" :to="{ name: 'home' }">홈으로</v-btn>
      <v-btn color="pink" variant="tonal" :to="{ name: 'profile' }">프로필</v-btn>
    </div>
  </v-container>
</template>

<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const { user } = storeToRefs(auth)
const roleText = computed(() => user.value?.roleName ?? user.value?.rolename ?? '-')
</script>
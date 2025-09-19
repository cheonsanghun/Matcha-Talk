<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8">
        <v-card class="pa-6">
          <div class="d-flex align-center ga-4">
            <v-avatar size="64" class="bg-pink-lighten-4"><v-icon color="pink">mdi-account</v-icon></v-avatar>
            <div>
              <div class="text-subtitle-1">{{ profileName }}</div>
              <div class="text-caption">{{ profileEmail }}</div>
            </div>
            <v-spacer/>
            <v-btn color="pink" variant="tonal" @click="onLogout">로그아웃</v-btn>
          </div>
          <v-divider class="my-4"/>
          <v-alert
            v-if="errorMessage"
            type="error"
            variant="tonal"
            class="mb-4"
          >
            {{ errorMessage }}
          </v-alert>
          <v-progress-linear
            v-if="loading"
            indeterminate
            color="pink"
            class="mb-4"
          />
          <div class="text-caption">
            프로필/친구/차단/언어 설정 등은 추후 확장 예정입니다.
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const { user, loading } = storeToRefs(authStore)

const errorMessage = ref('')

const profileName = computed(() => user.value?.nickname ?? user.value?.loginId ?? 'Guest')
const profileEmail = computed(() => user.value?.email ?? '이메일 정보 없음')

const fetchProfile = async () => {
  try {
    await authStore.fetchProfile()
    errorMessage.value = ''
  } catch (err) {
    errorMessage.value = err?.message ?? '프로필 정보를 불러오지 못했습니다.'
  }
}

onMounted(() => {
  if (!user.value) {
    fetchProfile()
  }
})

const onLogout = () => {
  authStore.logout()
  router.replace({ name: 'home' })
}
</script>

<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="10" lg="8">
        <v-card class="pa-8 video-chat-card" elevation="2">
          <div class="d-flex flex-column align-center text-center">
            <v-icon size="64" color="pink" class="mb-4">mdi-video-wireless</v-icon>
            <div class="text-h5 text-pink-darken-2 mb-2">영상 채팅 대기 중</div>
            <div class="text-body-2 text-medium-emphasis mb-6">
              {{ partnerHeadline }}
            </div>

            <div class="video-stage d-flex flex-column flex-md-row justify-center ga-4 mb-8">
              <div class="video-frame remote d-flex flex-column align-center justify-center">
                <v-icon size="48" color="grey-lighten-2">mdi-account-circle-outline</v-icon>
                <div class="text-caption mt-2">상대 영상</div>
              </div>
              <div class="video-frame local d-flex flex-column align-center justify-center">
                <v-icon size="48" color="grey-lighten-1">mdi-account</v-icon>
                <div class="text-caption mt-2">내 영상</div>
              </div>
            </div>

            <div class="d-flex flex-wrap justify-center ga-4">
              <v-btn color="error" variant="tonal" size="large" @click="endCall">
                통화 종료
              </v-btn>
              <v-btn color="grey" variant="outlined" size="large" @click="returnToHome">
                나가기
              </v-btn>
            </div>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const partnerHeadline = computed(() => {
  const nickname = route.query.nickname
  if (nickname) {
    return `${nickname}님과 연결 준비 중입니다. 통화를 시작해보세요!`
  }
  return '상대방과 연결을 준비하고 있습니다. 잠시만 기다려 주세요.'
})

async function endCall() {
  await router.push({ name: 'chat' })
}

async function returnToHome() {
  await router.push({ name: 'home' })
}
</script>

<style scoped>
.video-chat-card {
  border-radius: 16px;
}

.video-stage {
  width: 100%;
}

.video-frame {
  flex: 1 1 280px;
  min-height: 220px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 192, 203, 0.2), rgba(255, 192, 203, 0.05));
  border: 1px dashed rgba(255, 105, 180, 0.4);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.video-frame.remote:hover,
.video-frame.local:hover {
  transform: translateY(-4px);
  box-shadow: 0 14px 28px rgba(255, 182, 193, 0.35);
}

.video-frame.remote {
  background: linear-gradient(135deg, rgba(255, 240, 245, 0.9), rgba(255, 228, 225, 0.8));
}

.video-frame.local {
  background: linear-gradient(135deg, rgba(255, 235, 238, 0.9), rgba(255, 205, 210, 0.8));
}
</style>

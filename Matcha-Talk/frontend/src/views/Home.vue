<template>
  <div>
    <section class="py-10 bg-pink-lighten-5 sakura-section">
      <div class="sakura-bg">
        <span
            v-for="(p, i) in petals"
            :key="i"
            :style="{
            left: p.left,
            animationDelay: p.delay,
            animationDuration: p.duration,
            width: p.size,
            height: p.size,
            opacity: p.opacity,
            '--move': p.move + 'px'
          }"
        ></span>
      </div>
      <v-container class="sakura-content">
        <div class="text-center">
          <h1 class="text-h3 text-pink-darken-2 font-weight-bold">MatchaTalk</h1>
          <div class="text-subtitle-1 text-pink-darken-1 mt-2">문화 교류 랜덤 채팅</div>
          <v-row justify="center" align="stretch" no-gutters>
            <v-col cols="12" sm="4" class="pa-4">
              <v-card class="pa-6" elevation="2" rounded="lg">
                <v-img
                    src="/src/assets/sakura-hero.png"
                    class="my-8 mx-auto"
                    height="240"
                    contain
                    style="max-width: 600px;"
                />
              </v-card>
            </v-col>
          </v-row>
          <p class="text-body-2">
            Matcha Talk에서 당신의 관심사와 취향이 맞는 특별한 인연을 찾아보세요.
            벚꽃처럼 아름다운 만남이 기다리고 있습니다.
          </p>
          <h3 class="text-h6 mt-8 text-pink-darken-2 font-weight-bold">새로운 인연을 만나는 가장 아름다운 방법</h3>
          <div v-if="!isAuth">
            <v-btn class="mt-4 rounded-pill px-8" color="pink" size="x-large" :to="ctaTo">가입 하기</v-btn>
          </div>
          <div v-if="isAuth">
            <v-btn class="mt-4 rounded-pill px-8" color="pink" size="x-large" to="/match">매칭 시작</v-btn>
          </div>
        </div>
      </v-container>
    </section>

    <v-container class="py-10">
      <v-row justify="center" align="stretch" no-gutters>
        <v-col cols="12" sm="4" class="pa-4">
          <v-card class="pa-6" elevation="2" rounded="lg">
            <div class="text-center">
              <v-icon size="36" color="pink">mdi-account-multiple</v-icon>
              <div class="text-subtitle-1 font-weight-bold mt-3">맞춤형 매칭</div>
              <div class="text-body-2 mt-2">관심사와 취향을 분석하여 가장 맞는 인연을 소개합니다.</div>
            </div>
          </v-card>
        </v-col>
        <v-col cols="12" sm="4" class="pa-4">
          <v-card class="pa-6" elevation="2" rounded="lg">
            <div class="text-center">
              <v-icon size="36" color="pink">mdi-chat-processing</v-icon>
              <div class="text-subtitle-1 font-weight-bold mt-3">실시간 채팅</div>
              <div class="text-body-2 mt-2">편안하고 안전한 환경에서 새로운 인연과 대화해보세요.</div>
            </div>
          </v-card>
        </v-col>
        <v-col cols="12" sm="4" class="pa-4">
          <v-card class="pa-6" elevation="2" rounded="lg">
            <div class="text-center">
              <v-icon size="36" color="pink">mdi-shield-check</v-icon>
              <div class="text-subtitle-1 font-weight-bold mt-3">안전한 채팅</div>
              <div class="text-body-2 mt-2">강력한 보안 시스템으로 개인정보를 보호합니다.</div>
            </div>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script setup>
import {storeToRefs} from 'pinia'
import {useAuthStore} from '../stores/auth'

const store = useAuthStore()
const {isAuthenticated: isAuth} = storeToRefs(store)

const ctaTo = '/register'

const petals = Array.from({length: 20}).map(() => ({
  left: `${Math.random() * 100}%`,
  delay: `${-Math.random() * 20}s`,
  duration: `${10 + Math.random() * 10}s`,
  size: `${8 + Math.random() * 8}px`,
  opacity: 0.5 + Math.random() * 0.5,
  move: Math.random() * 100 - 50,
}))


</script>

<style scoped>
.sakura-section {
  position: relative;
  overflow: hidden;
}

.sakura-content {
  position: relative;
  z-index: 1;
}

.sakura-bg {
  pointer-events: none;
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;
}

.sakura-bg span {
  position: absolute;
  top: -10%;
  background: url('src/assets/sakura-petal.svg') no-repeat center/contain;
  animation-name: fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  transform-origin: center;
}

@keyframes fall {
  0% {
    transform: translateX(0) translateY(0) rotate(0deg);
  }
  100% {
    transform: translateX(var(--move)) translateY(110%) rotate(360deg);
  }
}
</style>

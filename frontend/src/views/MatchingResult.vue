<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="10">
        <v-card class="pa-6">
          <v-row class="align-center mb-6" v-if="isMatched">
            <v-avatar size="48" class="me-3">
              <v-img :src="partnerAvatarFallback" alt="partner" />
            </v-avatar>
            <div>
              <div class="text-h6 text-pink-darken-2">{{ partnerNameDisplay }}님과 매칭되었습니다</div>
              <div class="text-caption text-medium-emphasis">{{ statusMessage }}</div>
            </div>
          </v-row>
          <v-row class="align-center mb-6" v-else>
            <div class="text-h6 text-pink-darken-2">매칭 대기 중</div>
            <div class="text-caption text-medium-emphasis ms-4">{{ waitingStatusText }}</div>
          </v-row>

          <v-row>
            <v-col cols="12" md="9">
              <div v-if="isMatched" class="video-wrapper">
                <video ref="remoteVideo" class="remote-video" autoplay playsinline></video>
                <video ref="localVideo" class="local-video" muted autoplay playsinline></video>
                <div v-if="!hasRemoteStream" class="video-overlay d-flex align-center justify-center">
                  <v-progress-circular indeterminate color="pink" />
                </div>
              </div>
              <div
                v-else
                class="rounded-lg bg-pink-lighten-5 d-flex align-center justify-center media-placeholder"
              >
                <div class="text-subtitle-1">{{ waitingStatusText }}</div>
              </div>
            </v-col>
            <v-col cols="12" md="3">
              <v-card variant="outlined" class="pa-4 h-100 chat-wrapper d-flex flex-column">
                <ChatPanel class="flex-grow-1" :partner="partnerNameDisplay" />
              </v-card>
            </v-col>
          </v-row>

          <div
            class="d-flex justify-center gap-4 mt-6"
            v-if="isMatched && !decisionFinalized"
          >
            <v-btn
              color="pink"
              variant="tonal"
              :loading="actionLoading.accept"
              :disabled="acceptDisabled"
              @click="acceptMatch"
            >
              수락
            </v-btn>
            <v-btn
              color="grey"
              variant="outlined"
              :loading="actionLoading.decline"
              :disabled="declineDisabled"
              @click="declineMatch"
            >
              거절
            </v-btn>
          </div>
          <div class="text-center text-caption mt-4" v-if="statusMessage">
            {{ statusMessage }}
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>

</script>

<style scoped>
.video-wrapper {
  position: relative;
  background: #000;
  border-radius: 16px;
  overflow: hidden;
  min-height: 360px;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #000;
}

.local-video {
  position: absolute;
  right: 16px;
  bottom: 16px;
  width: 180px;
  height: 120px;
  object-fit: cover;
  border-radius: 12px;
  border: 2px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  background: #000;
}

.video-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
}

.media-placeholder {
  min-height: 360px;
  border-radius: 16px;
}

.chat-wrapper {
  max-height: 380px;
}

@media (max-width: 960px) {
  .local-video {
    width: 140px;
    height: 96px;
  }
}
</style>

<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="10">
        <v-card class="pa-6">
          <v-row class="align-center mb-6">
            <v-avatar size="40" class="me-3">
              <v-img :src="partnerAvatar" alt="avatar" />
            </v-avatar>
            <div>
              <div class="text-h6 text-pink-darken-2">{{ partnerName }}님과 매칭되었습니다</div>
              <div class="text-caption text-medium-emphasis">{{ sessionStatus }}</div>
            </div>
          </v-row>

          <v-row>
            <v-col cols="12" md="9">
              <v-img
                v-if="mediaUrl"
                :src="mediaUrl"
                class="rounded-lg bg-grey-lighten-2 media-wrapper"
                cover
              >
                <template #placeholder>
                  <v-row class="fill-height ma-0" align="center" justify="center">
                    <v-progress-circular indeterminate color="pink" />
                  </v-row>
                </template>
              </v-img>
              <div
                v-else
                class="rounded-lg bg-pink-lighten-5 d-flex align-center justify-center media-wrapper"
              >
                <div class="text-subtitle-1">매칭 이미지 / 영상이 없습니다.</div>
              </div>
            </v-col>
            <v-col cols="12" md="3">
              <v-card variant="outlined" class="pa-4 h-100 chat-wrapper d-flex flex-column">
                <ChatPanel class="flex-grow-1" />
              </v-card>
            </v-col>
          </v-row>

          <div class="d-flex justify-center gap-4 mt-6">
            <v-btn color="pink" variant="tonal" @click="acceptMatch">수락</v-btn>
            <v-btn color="grey" variant="outlined" @click="declineMatch">거절</v-btn>
          </div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref } from 'vue';
import ChatPanel from '../components/ChatPanel.vue';

const partnerName = ref('홍길동');
const partnerAvatar = ref('https://via.placeholder.com/150');
const sessionStatus = ref('대기 중');
const mediaUrl = ref('https://via.placeholder.com/640x360');

function acceptMatch() {
  if (window.confirm('매칭을 수락하시겠습니까?')) {
    // TODO: 매칭 수락 로직
  }
}

function declineMatch() {
  if (window.confirm('매칭을 거절하시겠습니까?')) {
    // TODO: 매칭 거절 로직
  }
}
</script>

<style scoped>
.media-wrapper {
  max-height: 380px;
}

.chat-wrapper {
  max-height: 380px;
}
</style>

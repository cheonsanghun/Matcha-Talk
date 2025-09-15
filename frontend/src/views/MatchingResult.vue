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
                  <ChatPanel class="flex-grow-1" :partner="partnerName" />
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

<!--script setup>
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
</script-->

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { createStompClient } from '../services/ws'
import { setupSignalRoutes } from '../services/signaling'
import { useAuthStore } from '../stores/auth'
// import { setupChat } ...

  const me = ref(/* 로그인한 사용자 loginId */)
  const partner = ref(/* 매칭된 상대 loginId */)
  const partnerName = ref('홍길동')
  const partnerAvatar = ref('https://via.placeholder.com/150')
  const sessionStatus = ref('대기 중')
  const mediaUrl = ref('')
  const pc = new RTCPeerConnection({
  iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
})
const localVideo = ref(null)
const remoteVideo = ref(null)
const auth = useAuthStore()
let client, signal, subs = []

onMounted(async () => {
  // 1) STOMP 연결
  client = createStompClient(auth.token)
  client.onConnect = async () => {
    // 2) 시그널 구독
    signal = setupSignalRoutes(client, {
      me: me.value,
      subscribeDest: `/topic/signal.${me.value}`, // 컨트롤러 전송 목적지와 일치시킬 것
      onSignal: async (msg) => {
        if (msg.type === 'offer') {
          await pc.setRemoteDescription(msg.data)
          const answer = await pc.createAnswer()
          await pc.setLocalDescription(answer)
          signal.sendSignal({ type:'answer', senderLoginId: me.value, receiverLoginId: partner.value, data: answer })
        } else if (msg.type === 'answer') {
          await pc.setRemoteDescription(msg.data)
        } else if (msg.type === 'ice-candidate') {
          try { await pc.addIceCandidate(msg.data) } catch {}
        }
      }
    })

    // 3) 미디어
    const stream = await navigator.mediaDevices.getUserMedia({ video:true, audio:true })
    stream.getTracks().forEach(t => pc.addTrack(t, stream))
    localVideo.value.srcObject = stream

    pc.ontrack = (e) => {
      remoteVideo.value.srcObject = e.streams[0]
    }
    pc.onicecandidate = (e) => {
      if (e.candidate) {
        signal.sendSignal({
          type:'ice-candidate',
          senderLoginId: me.value,
          receiverLoginId: partner.value,
          data: e.candidate
        })
      }
    }

    // 4) 내가 발신자라면 Offer 생성
    // (역할은 매칭 API 결과에 따라 결정)
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    signal.sendSignal({ type:'offer', senderLoginId: me.value, receiverLoginId: partner.value, data: offer })
  }
  client.activate()
})

onBeforeUnmount(() => {
  subs.forEach(s => s?.unsubscribe?.())
  client?.deactivate?.()
  pc?.close?.()
})
</script>


<style scoped>
.media-wrapper {
  max-height: 380px;
}

.chat-wrapper {
  max-height: 380px;
}
</style>

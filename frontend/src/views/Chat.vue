<template>
  <v-container fluid class="chat-page  mt-4">
    <v-row no-gutters class="h-100">
      <!-- Sidebar -->
      <v-col cols="12" md="3" class="chat-sidebar d-flex flex-column">
        <div class="sidebar-header d-flex align-center px-4 py-3">
          <div class="text-h6 font-weight-medium">채팅</div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-message-plus-outline</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-account-multiple-plus</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-cog-outline</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-dots-vertical</v-icon></v-btn>
        </div>
        <div class="px-4 pb-2">
          <v-text-field
              v-model="query"
              placeholder="채팅방 검색 바"
              prepend-inner-icon="mdi-magnify"
              variant="solo"
              density="comfortable"
              hide-details
          />
        </div>
        <v-tabs v-model="tab" density="comfortable" class="px-4">
          <v-tab value="direct">1:1 채팅</v-tab>
          <v-tab value="group">그룹 채팅</v-tab>
        </v-tabs>
        <v-divider />
        <div class="flex-grow-1 overflow-y-auto">
          <v-list v-if="tab === 'direct'">
            <v-list-item
                v-for="item in filteredChats"
                :key="item.id"
                @click="openChat(item)"
                lines="two"
            >
              <template #prepend>
                <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
              </template>
              <v-list-item-title>{{ item.name }}</v-list-item-title>
              <v-list-item-subtitle>{{ item.last }}</v-list-item-subtitle>
            </v-list-item>
          </v-list>
          <v-list v-else>
            <v-list-item
                v-for="item in filteredGroups"
                :key="item.id"
                @click="openChat(item)"
                lines="two"
            >
              <template #prepend>
                <v-avatar size="40"><v-icon color="primary">mdi-account-group</v-icon></v-avatar>
              </template>
              <v-list-item-title>{{ item.name }}</v-list-item-title>
              <v-list-item-subtitle>{{ item.last }}</v-list-item-subtitle>
            </v-list-item>
          </v-list>
        </div>
      </v-col>

      <!-- Conversation -->
      <v-col cols="12" md="9" class="chat-main d-flex flex-column">
        <div class="chat-header d-flex align-center pa-4">
          <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
          <div class="ml-3">
            <div class="text-subtitle-1 font-weight-medium">{{ current.name }}</div>
            <div class="text-caption text-grey" v-if="!isGroup">온라인</div>
            <div class="text-caption text-grey" v-else>{{ groupParticipants }}</div>
          </div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-magnify</v-icon></v-btn>
          <v-btn v-if="!isGroup" icon variant="text"><v-icon>mdi-phone</v-icon></v-btn>
          <v-btn v-if="isGroup" icon variant="text" @click="inviteParticipant"><v-icon>mdi-account-plus</v-icon></v-btn>
          <v-btn v-if="isGroup" icon variant="text" @click="startVideoCall"><v-icon>mdi-video</v-icon></v-btn>
          <v-btn v-else icon variant="text"><v-icon>mdi-video</v-icon></v-btn>
        </div>
        <v-divider />
        <div class="chat-messages flex-grow-1 pa-4 overflow-y-auto" ref="chatMessagesContainer">
          <div class="text-center my-4 text-caption text-grey">2023년 1월 18일</div>
          <div
              v-for="(m, i) in messages"
              :key="i"
              class="d-flex mb-4"
              :class="{ 'justify-end': m.me }"
          >
            <template v-if="!m.me">
              <v-avatar size="32" class="mr-2"><v-icon color="primary">mdi-account</v-icon></v-avatar>
              <div>
                <div v-if="isGroup" class="text-caption font-weight-medium mb-1">{{ m.sender }}</div>
                <div class="pa-3 bg-grey-lighten-4 rounded-xl">{{ m.text }}</div>
                <div class="text-caption text-grey mt-1">{{ m.time }}</div>
              </div>
            </template>
            <template v-else>
              <div>
                <div class="pa-3 bg-primary text-white rounded-xl">{{ m.text }}</div>
                <div class="text-caption text-grey mt-1 text-right">{{ m.time }}</div>
              </div>
            </template>
          </div>
        </div>
        <div class="chat-input d-flex align-center pa-4 ga-2">
          <v-btn icon variant="outlined" color="success"><v-icon>mdi-plus</v-icon></v-btn>
          <v-text-field
              v-model="draft"
              variant="outlined"
              density="comfortable"
              hide-details
              placeholder="메시지를 입력하세요..."
              class="flex-grow-1"
              @keydown.enter.prevent="send"
          />
          <v-btn icon variant="text"><v-icon>mdi-emoticon-outline</v-icon></v-btn>
          <v-btn icon color="success" @click="send"><v-icon>mdi-send</v-icon></v-btn>

        </div>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch, onUnmounted } from 'vue' // Added onUnmounted
import { useFriendsStore } from '../stores/friends'
import { createStompClient } from '../services/ws' // Added import for createStompClient

const query = ref('')
const tab = ref('direct')
const chats = ref([])
const groups = ref([
  { id: 3, name: '스터디 모임', last: '다음 주 모임 시간 안내', participants: ['김서연', '대학 동기'] }
])

const current = ref({})
const draft = ref('')
const conversations = ref({
  3: []
})

const chatMessagesContainer = ref(null)
const stompClient = ref(null); // Declared stompClient ref

const friendsStore = useFriendsStore()

onMounted(() => {
  chats.value = friendsStore.list.map((name, idx) => ({ id: idx + 1, name, last: '' }))
  current.value = chats.value[0] || groups.value[0]
  scrollToBottom()

  // WebSocket Connection Logic
  const token = localStorage.getItem('token'); // Get token from localStorage
  if (!token) {
    console.error("JWT token not found in localStorage. Cannot establish WebSocket connection.");
    return;
  }

  stompClient.value = createStompClient(token);

  stompClient.value.onConnect = () => {
    console.log('Connected to WebSocket');
    // Subscribe to public chat topic
    stompClient.value.subscribe('/topic/public', onMessageReceived);
    // Subscribe to user-specific queue for private messages/notifications
    // Assuming user's loginId is available, e.g., from a user store or decoded from token
    // For now, we'll use a placeholder or assume it's part of the user object in localStorage
    const user = JSON.parse(localStorage.getItem('user') || 'null');
    if (user && user.loginId) {
      stompClient.value.subscribe(`/user/${user.loginId}/queue/messages`, onMessageReceived);
    } else {
      console.warn("User loginId not found in localStorage. Cannot subscribe to private queue.");
    }
  };

  stompClient.value.onStompError = (frame) => {
    console.error('Broker reported error: ' + frame.headers['message']);
    console.error('Details: ' + frame.body);
  };

  stompClient.value.onWebSocketError = (event) => {
    console.error('WebSocket Error: ', event);
  };

  stompClient.value.activate();
})

onUnmounted(() => {
  if (stompClient.value && stompClient.value.connected) {
    stompClient.value.deactivate();
    console.log('Disconnected from WebSocket');
  }
})

friendsStore.$subscribe((_, state) => {
  chats.value = state.list.map((name, idx) => ({ id: idx + 1, name, last: '' }))
})


const filteredChats = computed(() =>
    chats.value.filter(c =>
        c.name.includes(query.value) || c.last?.includes(query.value)
    )
)
const filteredGroups = computed(() =>
    groups.value.filter(c =>
        c.name.includes(query.value) || c.last?.includes(query.value)
    )
)

const isGroup = computed(() =>
    groups.value.some(g => g.id === current.value.id)
)
const groupParticipants = computed(() => {
  const g = groups.value.find(g => g.id === current.value.id)
  return g ? g.participants.join(', ') : ''
})

const messages = computed(() => conversations.value[current.value.id] || [])

function scrollToBottom() {
  nextTick(() => {
    const el = chatMessagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function openChat(item) {
  current.value = item
  if (!conversations.value[item.id]) conversations.value[item.id] = []
  scrollToBottom()
}

function inviteParticipant() {
  const group = groups.value.find(g => g.id === current.value.id)
  if (!group) return
  if (group.participants.length >= 4) {
    alert('최대 4명까지 초대할 수 있습니다.')
    return
  }
  const name = prompt('초대할 사용자의 이름을 입력하세요:')
  if (name) group.participants.push(name)
}

function startVideoCall() {
  alert('영상 통화를 시작합니다')
}

function send() {
  if (!draft.value) return
  const formatted = new Date().toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit'
  })
  const msg = { text: draft.value, time: formatted, me: true }
  conversations.value[current.value.id] = conversations.value[current.value.id] || []
  conversations.value[current.value.id].push(msg)
  let chat = chats.value.find(c => c.id === current.value.id)
  if (!chat) chat = groups.value.find(c => c.id === current.value.id)
  if (chat) chat.last = draft.value

  draft.value = ''
  scrollToBottom()
}

function onMessageReceived(payload) {
  const message = JSON.parse(payload.body);
  console.log("Received message:", message);

  // Determine if it's a message for the current chat or another chat
  // This logic needs to be refined based on your backend message structure
  // For now, let's assume all messages are for the current chat for simplicity
  const formattedTime = new Date().toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit'
  });

  const msg = {
    text: message.content, // Assuming message has a 'content' field
    time: formattedTime,
    me: message.senderId === JSON.parse(localStorage.getItem('user') || 'null').id // Assuming senderId and user.id
  };

  // Add message to the correct conversation
  // This part needs to be dynamic based on message.roomId or message.senderId
  // For now, adding to current chat
  conversations.value[current.value.id] = conversations.value[current.value.id] || [];
  conversations.value[current.value.id].push(msg);
  scrollToBottom();
}

watch(messages, () => scrollToBottom())
</script>

<style scoped>
.chat-page {
  height: calc(100vh - var(--v-layout-top));
}
.chat-sidebar {
  background: #fff;
  border-right: 2px solid #000000;

  height: 100%;
}
.chat-main {
  background: #fff;
  border-left: 2px solid #ffb6c1;
  height: 100%;
}
.chat-messages {
  background: #fff;
}
.chat-input {
  border-top: 1px solid #eee;

}
</style>
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
            <div class="text-subtitle-1 font-weight-medium">{{ currentRoomDisplay.name }}</div>
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
          <div
            v-for="m in messages"
            :key="m.id"
            class="d-flex mb-4"
            :class="{ 'justify-end': m.me }"
          >
            <template v-if="!m.me">
              <v-avatar size="32" class="mr-2"><v-icon color="primary">mdi-account</v-icon></v-avatar>
              <div>
                <div v-if="isGroup" class="text-caption font-weight-medium mb-1">{{ m.sender }}</div>
                <div class="pa-3 bg-grey-lighten-4 rounded-xl">{{ m.content }}</div>
                <div v-if="m.translatedContent" class="text-caption text-primary mt-1">{{ m.translatedContent }}</div>
                <div class="text-caption text-grey mt-1">{{ formatTime(m.sentAt) }}</div>
              </div>
            </template>
            <template v-else>
              <div>
                <div class="pa-3 bg-primary text-white rounded-xl">{{ m.content }}</div>
                <div v-if="m.translatedContent" class="text-caption text-lighten-4 mt-1">{{ m.translatedContent }}</div>
                <div class="text-caption text-grey mt-1 text-right">{{ formatTime(m.sentAt) }}</div>
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
          <v-btn icon color="success" :loading="sending" @click="send"><v-icon>mdi-send</v-icon></v-btn>

        </div>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useChatStore } from '../stores/chat'

const chatStore = useChatStore()
const { rooms, currentRoom, messages, loading, sending, isGroup } = storeToRefs(chatStore)

const query = ref('')
const tab = ref('direct')
const draft = ref('')
const chatMessagesContainer = ref(null)

const filteredChats = computed(() => {
  const keyword = query.value.toLowerCase()
  return rooms.value
    .filter((room) => room.type !== 'GROUP')
    .filter((room) => room.name.toLowerCase().includes(keyword))
})

const filteredGroups = computed(() => {
  const keyword = query.value.toLowerCase()
  return rooms.value
    .filter((room) => room.type === 'GROUP')
    .filter((room) => room.name.toLowerCase().includes(keyword))
})

const currentRoomDisplay = computed(() =>
  currentRoom.value ?? { name: '채팅방', participants: 0 }
)

const groupParticipants = computed(() =>
  `${currentRoomDisplay.value.participants ?? 0}명 참여중`
)

const openChat = (room) => {
  chatStore.selectRoom(room.id)
}

const formatTime = (value) => {
  if (!value) return ''
  const date = typeof value === 'string' ? new Date(value) : value
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const send = () => {
  if (!draft.value.trim()) return
  chatStore.sendMessage(draft.value.trim())
  draft.value = ''
}

const inviteParticipant = () => {}
const startVideoCall = () => {}

watch(
  messages,
  async () => {
    await nextTick()
    if (chatMessagesContainer.value) {
      chatMessagesContainer.value.scrollTop = chatMessagesContainer.value.scrollHeight
    }
  },
  { deep: true }
)

onMounted(() => {
  chatStore.init()
})

onBeforeUnmount(() => {
  chatStore.cleanup()
})
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


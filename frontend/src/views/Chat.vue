<template>
  <v-container fluid class="chat-page pa-0">
    <v-row no-gutters>
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
            <div class="text-caption text-grey">온라인</div>
          </div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-magnify</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-phone</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-video</v-icon></v-btn>
        </div>
        <v-divider />
        <div class="chat-messages flex-grow-1 pa-4 overflow-y-auto">
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
import { ref, computed } from 'vue'

const query = ref('')
const tab = ref('direct')
const chats = ref([
  { id: 1, name: '김서연', last: '안녕하세요! 오늘 날씨가 정말 좋네요' },
  { id: 2, name: '대학 동기', last: '오늘은 다음 주 모임 관련 이야기' }
])
const groups = ref([
  { id: 3, name: '스터디 모임', last: '다음 주 모임 시간 안내' }
])

const current = ref(chats.value[0])
const draft = ref('')
const conversations = ref({
  1: [
    { text: '영재씨에게 연락 넣어놨어', time: '오전 9:01', me: false },
    { text: '확인되면 바로 알려줘', time: '오전 9:02', me: true },
    { text: '내일 몇 시에 만나는게 좋을까?', time: '오전 9:05', me: false },
    { text: '3시에 카페 앞에서 만나자', time: '오전 9:06', me: true }
  ],
  2: [],
  3: []
})

const filteredChats = computed(() =>
  chats.value.filter(c =>
    c.name.includes(query.value) || c.last.includes(query.value)
  )
)
const filteredGroups = computed(() =>
  groups.value.filter(c =>
    c.name.includes(query.value) || c.last.includes(query.value)
  )
)

const messages = computed(() => conversations.value[current.value.id] || [])


function openChat(item) {
  current.value = item
  if (!conversations.value[item.id]) conversations.value[item.id] = []
}

function send() {
  if (!draft.value) return
  const formatted = new Date().toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit'
  })
  const msg = { text: draft.value, time: formatted, me: true }
  conversations.value[current.value.id].push(msg)
  let chat = chats.value.find(c => c.id === current.value.id)
  if (!chat) chat = groups.value.find(c => c.id === current.value.id)
  if (chat) chat.last = draft.value

  draft.value = ''
}
</script>

<style scoped>
.chat-page {
  height: 100%;
}
.chat-sidebar {
  background: #ffeef5;
  border-right: 1px solid #ffd6e8;

  height: 100%;
}
.chat-main {
  background: #fff;
  height: 100%;
}
.chat-messages {
  background: #fff;
}
.chat-input {
  border-top: 1px solid #eee;

}
</style>


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
            <div class="text-subtitle-1 font-weight-medium">{{ currentName }}</div>
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
            v-for="(m, i) in messages"
            :key="m.id || i"
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
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useFriendsStore } from '../stores/friends'

const query = ref('')
const tab = ref('direct')
const groups = ref([
  { id: 3, type: 'group', name: '스터디 모임', last: '다음 주 모임 시간 안내', participants: ['김서연', '대학 동기'] }
])

const current = ref(null)
const draft = ref('')
const conversations = ref({
  3: []
})

const chatMessagesContainer = ref(null)

const friendsStore = useFriendsStore()
const auth = useAuthStore()

const meLoginId = computed(() => auth.user?.loginId || auth.user?.login_id || auth.user?.loginID || null)
const meNickName = computed(() => auth.user?.nickName || auth.user?.nickname || auth.user?.nick_name || '')

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  hour: '2-digit',
  minute: '2-digit',
})

const directChats = computed(() =>
  friendsStore.list.map((friend) => ({
    id:
      friend.id ??
      friend.roomId ??
      friend.partnerLoginId ??
      friend.partnerNickName ??
      `friend-${Math.random().toString(36).slice(2, 10)}`,
    type: 'direct',
    name: friend.partnerNickName || friend.partnerLoginId || '알 수 없음',
    last: summarizeLastMessage(friend),
    friend,
  }))
)

const filteredChats = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) {
    return directChats.value
  }
  return directChats.value.filter(
    (chat) =>
      (chat.name && chat.name.includes(keyword)) ||
      (chat.last && chat.last.includes(keyword))
  )
})

const filteredGroups = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) {
    return groups.value
  }
  return groups.value.filter(
    (group) =>
      (group.name && group.name.includes(keyword)) ||
      (group.last && group.last.includes(keyword))
  )
})

const isGroup = computed(() => current.value?.type === 'group')

const groupParticipants = computed(() => {
  if (!isGroup.value) {
    return ''
  }
  const group = groups.value.find((g) => g.id === current.value?.id)
  return group ? group.participants.join(', ') : ''
})

const currentName = computed(() => current.value?.name || '대화 상대 없음')

const messages = computed(() => {
  if (current.value?.type === 'group') {
    return conversations.value[current.value.id] || []
  }
  if (current.value?.type === 'direct') {
    return formatFriendMessages(current.value.friend?.messages || [])
  }
  return []
})

function summarizeLastMessage(friend) {
  if (!friend || !Array.isArray(friend.messages) || friend.messages.length === 0) {
    return ''
  }
  const last = friend.messages[friend.messages.length - 1]
  const contentType = (last.contentType || '').toString().toUpperCase()
  if (contentType === 'IMAGE') {
    return '[이미지]'
  }
  if (contentType === 'FILE') {
    return last.fileName ? `[파일] ${last.fileName}` : '[파일]'
  }
  return last.content || ''
}

function formatFriendMessages(items = []) {
  return items.map((item) => {
    const contentType = (item.contentType || '').toString().toUpperCase()
    let text = item.content || ''
    if (contentType === 'IMAGE') {
      text = '[이미지]'
    } else if (contentType === 'FILE') {
      text = item.fileName ? `[파일] ${item.fileName}` : '[파일]'
    }
    const sentAt = item.sentAt ? new Date(item.sentAt) : null
    const fromMe = item.senderLoginId
      ? item.senderLoginId === meLoginId.value
      : item.fromMe ?? (item.senderNickName && item.senderNickName === meNickName.value)

    return {
      text,
      me: !!fromMe,
      sender: item.senderNickName || '',
      time: sentAt ? timeFormatter.format(sentAt) : '',
      id: item.id ?? item.messageId ?? `${text}-${Math.random().toString(36).slice(2, 8)}`,
    }
  })
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatMessagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function openChat(item) {
  if (!item) {
    return
  }
  current.value = item
  if (item.type === 'group' && !conversations.value[item.id]) {
    conversations.value[item.id] = []
  }
  scrollToBottom()
}

function inviteParticipant() {
  const group = groups.value.find((g) => g.id === current.value?.id)
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
  if (current.value?.type !== 'group') {
    draft.value = ''
    return
  }
  const formatted = new Date().toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  })
  const msg = { text: draft.value, time: formatted, me: true }
  conversations.value[current.value.id] = conversations.value[current.value.id] || []
  conversations.value[current.value.id].push(msg)
  const group = groups.value.find((g) => g.id === current.value.id)
  if (group) {
    group.last = draft.value
  }

  draft.value = ''
  scrollToBottom()
}

watch(messages, () => scrollToBottom())

watch(
  directChats,
  (newChats) => {
    if (tab.value !== 'direct') {
      return
    }
    if (!newChats.length) {
      if (groups.value.length) {
        current.value = groups.value[0]
        tab.value = 'group'
      }
      return
    }
    if (!current.value || current.value.type !== 'direct') {
      current.value = newChats[0]
      return
    }
    const exists = newChats.some((chat) => chat.id === current.value.id)
    if (!exists) {
      current.value = newChats[0]
    }
  },
  { immediate: true }
)

watch(tab, (value) => {
  if (value === 'direct') {
    if (directChats.value.length) {
      if (current.value?.type !== 'direct') {
        current.value = directChats.value[0]
      }
    }
  } else if (value === 'group') {
    if (groups.value.length) {
      if (current.value?.type !== 'group') {
        current.value = groups.value[0]
      }
    }
  }
})

onMounted(async () => {
  try {
    await friendsStore.refreshFromServer()
  } catch (error) {
    console.error('채팅 목록 초기화 실패', error)
  }

  if (directChats.value.length) {
    current.value = directChats.value[0]
    tab.value = 'direct'
  } else if (groups.value.length) {
    current.value = groups.value[0]
    tab.value = 'group'
  }

  scrollToBottom()
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


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
            <div class="text-caption text-grey">{{ connectionStatusText }}</div>
          </div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-magnify</v-icon></v-btn>
          <v-btn v-if="!isGroup" icon variant="text"><v-icon>mdi-phone</v-icon></v-btn>
          <v-btn v-if="isGroup" icon variant="text" @click="inviteParticipant"><v-icon>mdi-account-plus</v-icon></v-btn>
          <v-btn v-if="isGroup" icon variant="text" @click="startVideoCall"><v-icon>mdi-video</v-icon></v-btn>
          <v-btn v-else icon variant="text"><v-icon>mdi-video</v-icon></v-btn>
        </div>
        <v-divider />
        <v-alert
          v-if="chatError"
          type="error"
          density="compact"
          class="mx-4 mt-3"
          variant="tonal"
          closable
          @click:close="chatError = ''"
        >
          {{ chatError }}
        </v-alert>
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
            :disabled="inputDisabled"
            @keydown.enter.prevent="canSend && send()"
          />
          <v-btn icon variant="text"><v-icon>mdi-emoticon-outline</v-icon></v-btn>
          <v-btn
            icon
            color="success"
            @click="send"
            :disabled="!canSend"
            :loading="sending"
          >
            <v-icon>mdi-send</v-icon>
          </v-btn>

        </div>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useFriendsStore } from '../stores/friends'
import { createStompClient } from '../services/ws'
import { setupChat } from '../services/chat'

const query = ref('')
const tab = ref('direct')
const groups = ref([
  { id: 3, type: 'group', name: '스터디 모임', last: '다음 주 모임 시간 안내', participants: ['김서연', '대학 동기'] },
])

const activeChat = reactive({ type: null, id: null })
const draft = ref('')
const chatMessagesContainer = ref(null)
const chatError = ref('')
const sending = ref(false)

const friendsStore = useFriendsStore()
const auth = useAuthStore()

const meLoginId = computed(() => auth.user?.loginId || auth.user?.login_id || auth.user?.loginID || null)

const client = ref(null)
const connected = ref(false)
const chatRoute = ref(null)
const subscribedRoomId = ref(null)

const groupConversations = ref({})

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  hour: '2-digit',
  minute: '2-digit',
})

const directChats = computed(() =>
  friendsStore.list.map((friend, index) => {
    const identifier =
      friend.roomId ??
      friend.partnerLoginId ??
      friend.partnerNickName ??
      friend.id ??
      `friend-${index}`
    return {
      id: identifier,
      type: 'direct',
      name: friend.partnerNickName || friend.partnerLoginId || '알 수 없음',
      last: summarizeLastMessage(friend),
      roomId: friend.roomId ?? null,
    }
  })
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

const isGroup = computed(() => activeChat.type === 'group')

const currentFriend = computed(() => {
  if (activeChat.type !== 'direct' || activeChat.id == null) {
    return null
  }
  return (
    friendsStore.list.find((friend) => {
      const candidates = [
        friend.roomId,
        friend.id,
        friend.partnerLoginId,
        friend.partnerNickName,
      ].filter((value) => value != null)
      return candidates.includes(activeChat.id)
    }) || null
  )
})

const currentRoomId = computed(() => currentFriend.value?.roomId ?? null)

const currentName = computed(() => {
  if (isGroup.value) {
    const group = groups.value.find((g) => g.id === activeChat.id)
    return group?.name ?? '대화 상대 없음'
  }
  const friend = currentFriend.value
  if (!friend) {
    return '대화 상대 없음'
  }
  return friend.partnerNickName || friend.partnerLoginId || '대화 상대 없음'
})

const groupParticipants = computed(() => {
  if (!isGroup.value) {
    return ''
  }
  const group = groups.value.find((g) => g.id === activeChat.id)
  return group ? group.participants.join(', ') : ''
})

const connectionReady = computed(() => {
  if (isGroup.value) {
    return true
  }
  return Boolean(connected.value && currentRoomId.value)
})

const connectionStatusText = computed(() => {
  if (isGroup.value) {
    return groupParticipants.value || '그룹 채팅'
  }
  if (!currentRoomId.value) {
    return '대화 상대를 선택하세요'
  }
  return connectionReady.value ? '온라인' : '연결 중...'
})

const messages = computed(() => {
  if (isGroup.value) {
    return groupConversations.value[activeChat.id] ?? []
  }
  const friend = currentFriend.value
  if (!friend?.messages) {
    return []
  }
  return friend.messages.map(mapFriendMessage)
})

const inputDisabled = computed(() => !isGroup.value && !connectionReady.value)

const canSend = computed(() => {
  if (!draft.value.trim()) {
    return false
  }
  if (isGroup.value) {
    return true
  }
  return connectionReady.value && !sending.value
})

function summarizeLastMessage(friend) {
  if (!friend || !Array.isArray(friend.messages) || friend.messages.length === 0) {
    return ''
  }
  const last = friend.messages[friend.messages.length - 1]
  const contentType = (last.contentType || last.content_type || '').toString().toUpperCase()
  if (contentType === 'IMAGE') {
    return '[이미지]'
  }
  if (contentType === 'FILE') {
    return last.fileName ? `[파일] ${last.fileName}` : '[파일]'
  }
  return last.content || last.text || ''
}

function mapFriendMessage(item, index) {
  const contentType = (item.contentType || item.content_type || '').toString().toUpperCase()
  let text = item.content ?? item.text ?? ''
  if (contentType === 'IMAGE') {
    text = '[이미지]'
  } else if (contentType === 'FILE') {
    text = item.fileName ? `[파일] ${item.fileName}` : '[파일]'
  }
  const sentAtValue = item.sentAt ?? item.sent_at ?? null
  const sentAt = sentAtValue ? new Date(sentAtValue) : null
  const senderLoginId = item.senderLoginId ?? item.sender_login_id ?? null
  return {
    id: item.id ?? item.messageId ?? `${index}-${sentAt ? sentAt.getTime() : Date.now()}`,
    text,
    me: senderLoginId ? senderLoginId === meLoginId.value : Boolean(item.fromMe),
    sender: item.senderNickName ?? item.sender_nick_name ?? '',
    time: sentAt ? timeFormatter.format(sentAt) : '',
  }
}

function openChat(item) {
  if (!item) {
    return
  }
  chatError.value = ''
  if (item.type === 'group') {
    activeChat.type = 'group'
    activeChat.id = item.id
    groupConversations.value[item.id] = groupConversations.value[item.id] ?? []
  } else {
    activeChat.type = 'direct'
    activeChat.id = item.roomId ?? item.id ?? null
  }
  nextTick(scrollToBottom)
}

function scrollToBottom() {
  const el = chatMessagesContainer.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function inviteParticipant() {
  const group = groups.value.find((g) => g.id === activeChat.id)
  if (!group) {
    return
  }
  if (group.participants.length >= 4) {
    window.alert('최대 4명까지 초대할 수 있습니다.')
    return
  }
  const name = window.prompt('초대할 사용자의 이름을 입력하세요:')
  if (name) {
    group.participants.push(name)
  }
}

function startVideoCall() {
  window.alert('영상 통화를 시작합니다')
}

function appendGroupMessage(text) {
  if (!isGroup.value || activeChat.id == null) {
    return
  }
  const formatted = timeFormatter.format(new Date())
  const message = { text, time: formatted, me: true, id: `${Date.now()}-me` }
  groupConversations.value[activeChat.id] = groupConversations.value[activeChat.id] ?? []
  groupConversations.value[activeChat.id].push(message)
  const group = groups.value.find((g) => g.id === activeChat.id)
  if (group) {
    group.last = text
  }
  nextTick(scrollToBottom)
}

function normalizeIncomingMessage(payload) {
  if (!payload) {
    return null
  }
  const sentAtValue = payload.sentAt ?? payload.sent_at ?? new Date().toISOString()
  const sizeValue = payload.sizeBytes ?? payload.size_bytes
  let sizeBytes = null
  if (typeof sizeValue === 'number') {
    sizeBytes = Number.isFinite(sizeValue) ? sizeValue : null
  } else if (sizeValue != null) {
    const parsed = Number(sizeValue)
    sizeBytes = Number.isFinite(parsed) ? parsed : null
  }
  return {
    id:
      payload.id ??
      payload.messageId ??
      `${payload.roomId ?? 'room'}-${sentAtValue}-${Math.random().toString(36).slice(2, 8)}`,
    messageId: payload.messageId ?? null,
    roomId: payload.roomId ?? null,
    senderLoginId: payload.senderLoginId ?? payload.sender_login_id ?? null,
    senderNickName: payload.senderNickName ?? payload.sender_nick_name ?? '',
    content: payload.content ?? payload.text ?? '',
    translatedContent: payload.translatedContent ?? payload.translated_content ?? '',
    contentType: payload.contentType ?? payload.content_type ?? (payload.fileUrl ? 'FILE' : 'TEXT'),
    fileName: payload.fileName ?? payload.file_name ?? '',
    fileUrl: payload.fileUrl ?? payload.file_url ?? '',
    mimeType: payload.mimeType ?? payload.mime_type ?? '',
    sizeBytes,
    sentAt: sentAtValue,
  }
}

function handleIncomingChatMessage(payload) {
  const normalized = normalizeIncomingMessage(payload)
  if (!normalized || !normalized.roomId) {
    return
  }

  const friend = friendsStore.list.find((item) => item.roomId === normalized.roomId) ?? null
  const existingMessages = friend?.messages ?? []
  const alreadyExists =
    normalized.messageId &&
    existingMessages.some((item) => item.messageId && item.messageId === normalized.messageId)

  if (!alreadyExists) {
    const isFromMe =
      normalized.senderLoginId && normalized.senderLoginId === meLoginId.value
    const partnerNickName =
      friend?.partnerNickName ?? (!isFromMe ? normalized.senderNickName : friend?.partnerNickName ?? '')
    const partnerLoginId =
      friend?.partnerLoginId ?? (!isFromMe ? normalized.senderLoginId : friend?.partnerLoginId ?? null)

    friendsStore.upsert({
      roomId: normalized.roomId,
      partnerNickName,
      partnerLoginId,
      messages: [...existingMessages, normalized],
    })
  }

  if (currentRoomId.value === normalized.roomId && !alreadyExists) {
    nextTick(scrollToBottom)
  }
}

function ensureChatRoute() {
  if (isGroup.value) {
    teardownChatRoute()
    return
  }
  const roomId = currentRoomId.value
  if (!connectionReady.value || !client.value || !roomId) {
    teardownChatRoute()
    return
  }
  if (subscribedRoomId.value === roomId) {
    return
  }
  teardownChatRoute()
  chatRoute.value = setupChat(client.value, roomId, {
    onChat: handleIncomingChatMessage,
  })
  subscribedRoomId.value = roomId
}

function teardownChatRoute() {
  if (chatRoute.value?.unsubscribe) {
    chatRoute.value.unsubscribe()
  } else if (chatRoute.value?.subscription?.unsubscribe) {
    chatRoute.value.subscription.unsubscribe()
  }
  chatRoute.value = null
  subscribedRoomId.value = null
}

function initStompClient() {
  if (client.value) {
    return
  }
  client.value = createStompClient(auth.token)
  client.value.onConnect = () => {
    connected.value = true
    ensureChatRoute()
  }
  client.value.onDisconnect = () => {
    connected.value = false
    teardownChatRoute()
  }
  client.value.onStompError = (frame) => {
    console.error('STOMP error', frame)
    chatError.value =
      frame?.headers?.message || '채팅 연결 중 오류가 발생했습니다.'
  }
  client.value.onWebSocketError = (event) => {
    console.error('WebSocket error', event)
  }
  client.value.activate()
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) {
    return
  }
  chatError.value = ''

  if (isGroup.value) {
    appendGroupMessage(text)
    draft.value = ''
    return
  }

  const roomId = currentRoomId.value
  if (!roomId) {
    chatError.value = '채팅방이 선택되지 않았습니다.'
    return
  }
  if (!connectionReady.value || !client.value) {
    chatError.value = '채팅 연결이 준비되지 않았습니다.'
    return
  }

  try {
    sending.value = true
    if (chatRoute.value?.sendChat) {
      chatRoute.value.sendChat({ content: text })
    } else {
      client.value.publish({
        destination: `/app/chat.sendMessage/${roomId}`,
        body: JSON.stringify({ roomId, content: text }),
      })
    }
    draft.value = ''
  } catch (error) {
    console.error('채팅 메시지 전송 실패', error)
    chatError.value =
      error?.response?.data?.message ||
      error?.message ||
      '메시지 전송에 실패했습니다.'
  } finally {
    sending.value = false
  }
}

watch(messages, () => {
  nextTick(scrollToBottom)
})

watch([connected, currentRoomId, isGroup], () => {
  ensureChatRoute()
})

watch(directChats, (chats) => {
  if (tab.value !== 'direct') {
    return
  }
  if (!chats.length) {
    if (groups.value.length) {
      tab.value = 'group'
      activeChat.type = 'group'
      activeChat.id = groups.value[0].id
    } else {
      activeChat.type = null
      activeChat.id = null
    }
    return
  }
  const exists = chats.some((chat) => chat.roomId === activeChat.id || chat.id === activeChat.id)
  if (activeChat.type !== 'direct' || !exists) {
    const first = chats[0]
    activeChat.type = 'direct'
    activeChat.id = first.roomId ?? first.id
  }
}, { immediate: true })

watch(currentFriend, (friend) => {
  if (!friend && tab.value === 'direct' && directChats.value.length) {
    const first = directChats.value[0]
    activeChat.type = 'direct'
    activeChat.id = first.roomId ?? first.id
  }
})

watch(tab, (value) => {
  if (value === 'direct') {
    if (!directChats.value.length && groups.value.length) {
      tab.value = 'group'
      return
    }
    if (directChats.value.length && activeChat.type !== 'direct') {
      const first = directChats.value[0]
      activeChat.type = 'direct'
      activeChat.id = first.roomId ?? first.id
    }
  } else if (value === 'group') {
    if (!groups.value.length) {
      tab.value = 'direct'
      return
    }
    activeChat.type = 'group'
    activeChat.id = groups.value[0].id
  }
})

onMounted(async () => {
  try {
    await friendsStore.refreshFromServer()
  } catch (error) {
    console.error('채팅 목록 초기화 실패', error)
  }

  if (!activeChat.type) {
    if (directChats.value.length) {
      const first = directChats.value[0]
      activeChat.type = 'direct'
      activeChat.id = first.roomId ?? first.id
      tab.value = 'direct'
    } else if (groups.value.length) {
      activeChat.type = 'group'
      activeChat.id = groups.value[0].id
      tab.value = 'group'
    }
  }

  initStompClient()
  nextTick(scrollToBottom)
})

onBeforeUnmount(() => {
  teardownChatRoute()
  if (client.value) {
    try {
      client.value.deactivate()
    } catch (error) {
      console.warn('STOMP 클라이언트 종료 중 오류', error)
    }
  }
  client.value = null
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

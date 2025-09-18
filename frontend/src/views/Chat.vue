<template>
  <v-container fluid class="chat-page mt-4">
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
          <div v-if="loadingRooms" class="d-flex justify-center py-6">
            <v-progress-circular indeterminate size="24" color="primary" />
          </div>
          <div v-else-if="roomsError" class="text-caption text-error text-center px-4 py-6">
            {{ roomsError }}
          </div>
          <template v-else>
            <v-list v-if="tab === 'direct'">
              <v-list-item
                  v-for="room in filteredDirectRooms"
                  :key="room.roomId"
                  :class="['chat-room-item', { active: room.roomId === selectedRoomId }]"
                  @click="openRoom(room)"
                  lines="two"
              >
                <template #prepend>
                  <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
                </template>
                <v-list-item-title>{{ getRoomTitle(room) }}</v-list-item-title>
                <v-list-item-subtitle>{{ getRoomSubtitle(room) }}</v-list-item-subtitle>
              </v-list-item>
            </v-list>
            <v-list v-else>
              <v-list-item
                  v-for="room in filteredGroupRooms"
                  :key="room.roomId"
                  :class="['chat-room-item', { active: room.roomId === selectedRoomId }]"
                  @click="openRoom(room)"
                  lines="two"
              >
                <template #prepend>
                  <v-avatar size="40"><v-icon color="primary">mdi-account-group</v-icon></v-avatar>
                </template>
                <v-list-item-title>{{ getRoomTitle(room) }}</v-list-item-title>
                <v-list-item-subtitle>{{ getRoomSubtitle(room) }}</v-list-item-subtitle>
              </v-list-item>
            </v-list>
            <div
                v-if="(tab === 'direct' ? filteredDirectRooms : filteredGroupRooms).length === 0"
                class="text-caption text-medium-emphasis text-center px-4 py-6"
            >
              참여 중인 채팅방이 없습니다.
            </div>
          </template>
        </div>
      </v-col>

      <!-- Conversation -->
      <v-col cols="12" md="9" class="chat-main d-flex flex-column">
        <template v-if="currentRoom">
          <div class="chat-header d-flex align-center pa-4">
            <v-avatar size="40"><v-icon color="primary">{{ isGroup ? 'mdi-account-group' : 'mdi-account' }}</v-icon></v-avatar>
            <div class="ml-3">
              <div class="text-subtitle-1 font-weight-medium">{{ currentRoomTitle }}</div>
              <div v-if="currentRoomSubtitle" class="text-caption text-grey">{{ currentRoomSubtitle }}</div>
            </div>
            <v-spacer />
            <v-btn icon variant="text"><v-icon>mdi-magnify</v-icon></v-btn>
            <v-btn v-if="!isGroup" icon variant="text" @click="startVoiceCall"><v-icon>mdi-phone</v-icon></v-btn>
            <v-btn v-if="isGroup" icon variant="text" @click="inviteParticipant"><v-icon>mdi-account-plus</v-icon></v-btn>
            <v-btn icon variant="text" @click="startVideoCall"><v-icon>mdi-video</v-icon></v-btn>
          </div>
          <v-divider />
          <div class="chat-content flex-grow-1 d-flex flex-column">
            <div v-if="loadingRoomDetail" class="flex-grow-1 d-flex align-center justify-center">
              <v-progress-circular indeterminate size="32" color="primary" />
            </div>
            <div
                v-else-if="roomDetailError"
                class="flex-grow-1 d-flex align-center justify-center text-caption text-error px-4 text-center"
            >
              {{ roomDetailError }}
            </div>
            <div v-else class="chat-panel-wrapper flex-grow-1 d-flex flex-column">
              <ChatPanel
                  class="flex-grow-1 h-100"
                  :room-id="selectedRoomId"
                  :stomp-client="stompClient"
                  :connected="isConnected"
                  :enabled="chatEnabled"
                  :partner-login-id="partnerLoginId"
              />
            </div>
          </div>
        </template>
        <div v-else class="chat-empty flex-grow-1 d-flex align-center justify-center text-medium-emphasis">
          참여 중인 채팅방이 없습니다. 좌측에서 다른 탭을 확인해 보세요.
        </div>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, shallowRef } from 'vue'
import ChatPanel from '../components/ChatPanel.vue'
import api from '../services/api'
import { createStompClient } from '../services/ws'
import { useAuthStore } from '../stores/auth'

const query = ref('')
const tab = ref('direct')
const rooms = ref([])
const loadingRooms = ref(false)
const roomsError = ref('')

const selectedRoomId = ref(null)
const roomDetails = ref(null)
const loadingRoomDetail = ref(false)
const roomDetailError = ref('')

const auth = useAuthStore()

const stompClient = shallowRef(null)
const isConnected = ref(false)

const myNickname = computed(() => auth.user?.nickName ?? '')
const myLoginId = computed(() => auth.user?.loginId ?? '')

const directRooms = computed(() => rooms.value.filter(room => room.roomType !== 'GROUP'))
const groupRooms = computed(() => rooms.value.filter(room => room.roomType === 'GROUP'))

const filteredDirectRooms = computed(() => filterRooms(directRooms.value))
const filteredGroupRooms = computed(() => filterRooms(groupRooms.value))

const currentRoom = computed(() => rooms.value.find(room => room.roomId === selectedRoomId.value) || null)
const isGroup = computed(() => currentRoom.value?.roomType === 'GROUP')

const currentParticipants = computed(() => roomDetails.value?.participants ?? [])
const participantNicknames = computed(() => currentParticipants.value.map(p => p.nickname).filter(Boolean))
const otherParticipant = computed(() => {
  if (!currentParticipants.value.length) return null
  return currentParticipants.value.find(participant => participant.loginId && participant.loginId !== myLoginId.value) || null
})

const currentRoomTitle = computed(() => {
  if (!currentRoom.value) return ''
  if (participantNicknames.value.length) {
    if (isGroup.value) return participantNicknames.value.join(', ')
    const others = participantNicknames.value.filter(name => name !== myNickname.value)
    return others.length ? others.join(', ') : (participantNicknames.value[0] || `방 #${currentRoom.value.roomId}`)
  }
  const fallback = (currentRoom.value.memberNicknames || []).filter(Boolean)
  if (!fallback.length) return `방 #${currentRoom.value.roomId}`
  if (isGroup.value) return fallback.join(', ')
  const others = fallback.filter(name => name !== myNickname.value)
  return others.length ? others.join(', ') : fallback[0]
})

const currentRoomSubtitle = computed(() => {
  if (!currentRoom.value) return ''
  if (isGroup.value) {
    const participants = participantNicknames.value.length ? participantNicknames.value : (currentRoom.value.memberNicknames || [])
    if (participants.length) return `${participants.length}명 참여 중`
    if (typeof currentRoom.value.memberCount === 'number') return `${currentRoom.value.memberCount}명 참여 중`
    return ''
  }
  const others = participantNicknames.value.filter(name => name !== myNickname.value)
  if (others.length) return others.join(', ')
  const fallback = (currentRoom.value.memberNicknames || []).filter(name => name !== myNickname.value)
  return fallback.join(', ')
})

const chatEnabled = computed(() => !!selectedRoomId.value && !roomDetailError.value)
const partnerLoginId = computed(() => (isGroup.value ? '' : otherParticipant.value?.loginId ?? ''))

function filterRooms(list = []) {
  if (!Array.isArray(list)) return []
  const term = query.value.trim().toLowerCase()
  if (!term) return list
  return list.filter(room => {
    const names = (room.memberNicknames || []).join(' ').toLowerCase()
    const type = (room.roomType || '').toLowerCase()
    return names.includes(term) || type.includes(term) || String(room.roomId).includes(term)
  })
}

function getRoomTitle(room) {
  if (!room) return ''
  const names = (room.memberNicknames || []).filter(Boolean)
  if (room.roomType === 'GROUP') return names.length ? names.join(', ') : `그룹 채팅 #${room.roomId}`
  const others = names.filter(name => name !== myNickname.value)
  if (others.length) return others.join(', ')
  if (names.length) return names[0]
  return `방 #${room.roomId}`
}

function getRoomSubtitle(room) {
  if (!room) return ''
  if (room.roomType === 'GROUP') {
    if (typeof room.memberCount === 'number') return `${room.memberCount}명 참여 중`
    return (room.memberNicknames || []).join(', ')
  }
  const names = (room.memberNicknames || []).filter(name => name !== myNickname.value)
  return names.join(', ')
}

function openRoom(room) {
  if (!room || room.roomId === selectedRoomId.value) return
  selectedRoomId.value = room.roomId
}

async function fetchRooms() {
  loadingRooms.value = true
  roomsError.value = ''
  try {
    const { data } = await api.get('/rooms/my')
    rooms.value = Array.isArray(data) ? data : []
  } catch (error) {
    rooms.value = []
    roomsError.value = error?.response?.data?.message || '채팅방 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
    console.error('Failed to load chat rooms:', error)
  } finally {
    loadingRooms.value = false
  }
}

let detailRequestToken = 0
watch(selectedRoomId, async (roomId) => {
  detailRequestToken += 1
  const token = detailRequestToken
  roomDetails.value = null
  roomDetailError.value = ''
  if (!roomId) {
    loadingRoomDetail.value = false
    return
  }
  loadingRoomDetail.value = true
  try {
    const { data } = await api.get(`/rooms/${roomId}`)
    if (token === detailRequestToken) {
      roomDetails.value = data
    }
  } catch (error) {
    if (token === detailRequestToken) {
      roomDetails.value = null
      roomDetailError.value = error?.response?.data?.message || '채팅방 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
    }
    console.error('Failed to load room details:', error)
  } finally {
    if (token === detailRequestToken) {
      loadingRoomDetail.value = false
    }
  }
})

watch(
  () => rooms.value,
  (list) => {
    if (!Array.isArray(list) || list.length === 0) {
      if (selectedRoomId.value !== null) selectedRoomId.value = null
      return
    }
    const hasCurrent = list.some(room => room.roomId === selectedRoomId.value)
    if (hasCurrent) return
    const preferred = tab.value === 'group'
      ? list.find(room => room.roomType === 'GROUP')
      : list.find(room => room.roomType !== 'GROUP')
    const fallback = preferred || list[0]
    selectedRoomId.value = fallback ? fallback.roomId : null
  }
)

watch(tab, (value) => {
  const list = value === 'group' ? groupRooms.value : directRooms.value
  if (!list.length) {
    selectedRoomId.value = null
    return
  }
  if (!list.some(room => room.roomId === selectedRoomId.value)) {
    selectedRoomId.value = list[0].roomId
  }
})

let clientInstance = null
function setupStomp() {
  const token = auth.token || localStorage.getItem('token')
  clientInstance = createStompClient(token)
  stompClient.value = clientInstance
  clientInstance.onConnect = () => {
    isConnected.value = true
  }
  clientInstance.onDisconnect = () => {
    isConnected.value = false
  }
  clientInstance.onStompError = (frame) => {
    console.error('STOMP error:', frame?.headers?.message, frame?.body)
  }
  clientInstance.onWebSocketError = (event) => {
    console.error('WebSocket error:', event)
  }
  clientInstance.activate()
}

function inviteParticipant() {
  alert('그룹 초대 기능은 준비 중입니다.')
}

function startVoiceCall() {
  alert('음성 통화 기능은 준비 중입니다.')
}

function startVideoCall() {
  alert('영상 통화 기능은 준비 중입니다.')
}

onMounted(() => {
  fetchRooms()
  setupStomp()
})

onBeforeUnmount(() => {
  detailRequestToken += 1
  clientInstance?.deactivate?.()
  stompClient.value = null
  isConnected.value = false
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
  min-height: 0;
}

.chat-content {
  min-height: 0;
}

.chat-panel-wrapper {
  min-height: 0;
}

.chat-empty {
  background: #fff;
}

.chat-room-item {
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.chat-room-item.active {
  background-color: rgba(255, 182, 193, 0.2);
}

.chat-room-item:hover {
  background-color: rgba(255, 182, 193, 0.15);
}
</style>

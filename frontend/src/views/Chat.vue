<template>
  <v-container fluid class="chat-page mt-4">
    <v-row no-gutters class="h-100">
      <v-col cols="12" md="3" class="chat-sidebar d-flex flex-column">
        <div class="sidebar-header d-flex align-center px-4 py-3">
          <div class="text-h6 font-weight-medium">채팅</div>
          <v-spacer />
          <v-btn icon variant="text" @click="loadRooms"><v-icon>mdi-refresh</v-icon></v-btn>
        </div>
        <div class="px-4 pb-2">
          <v-text-field
            v-model="query"
            placeholder="채팅방 검색"
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
              v-for="friend in filteredFriends"
              :key="friend.loginId"
              @click="openFriendChat(friend)"
              :active="currentRoom && getPartnerLogin(currentRoom) === friend.loginId"
            >
              <template #prepend>
                <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
              </template>
              <v-list-item-title>{{ friend.nickName || friend.loginId }}</v-list-item-title>
              <v-list-item-subtitle>친구</v-list-item-subtitle>
            </v-list-item>
            <v-list-item
              v-for="room in filteredDirectRooms"
              :key="room.roomId"
              @click="selectRoom(room)"
              :active="currentRoom?.roomId === room.roomId"
            >
              <template #prepend>
                <v-avatar size="40"><v-icon color="primary">mdi-account-circle</v-icon></v-avatar>
              </template>
              <v-list-item-title>{{ resolveRoomTitle(room) }}</v-list-item-title>
              <v-list-item-subtitle>최근 대화방</v-list-item-subtitle>
            </v-list-item>
            <div v-if="!filteredFriends.length && !filteredDirectRooms.length" class="text-caption text-grey text-center py-4">
              팔로우한 사용자가 없습니다. 랜덤 채팅에서 친구를 추가해보세요.
            </div>
          </v-list>
          <v-list v-else>
            <v-list-item
              v-for="room in filteredGroups"
              :key="room.roomId"
              @click="selectRoom(room)"
              :active="currentRoom?.roomId === room.roomId"
            >
              <template #prepend>
                <v-avatar size="40"><v-icon color="primary">mdi-account-group</v-icon></v-avatar>
              </template>
              <v-list-item-title>{{ resolveRoomTitle(room) }}</v-list-item-title>
              <v-list-item-subtitle>{{ room.participants.length }}명 참여</v-list-item-subtitle>
            </v-list-item>
            <div v-if="!filteredGroups.length" class="text-caption text-grey text-center py-4">
              참여 중인 그룹 채팅이 없습니다.
            </div>
          </v-list>
        </div>
      </v-col>

      <v-col cols="12" md="9" class="chat-main d-flex flex-column">
        <template v-if="currentRoom">
          <div class="chat-header d-flex align-center pa-4">
            <v-avatar size="40">
              <v-icon color="primary">{{ currentRoom.roomType === 'GROUP' ? 'mdi-account-group' : 'mdi-account' }}</v-icon>
            </v-avatar>
            <div class="ml-3">
              <div class="text-subtitle-1 font-weight-medium">{{ resolveRoomTitle(currentRoom) }}</div>
              <div class="text-caption text-grey">
                {{ currentRoom.roomType === 'GROUP' ? participantSummary : '온라인' }}
              </div>
            </div>
            <v-spacer />
            <v-btn icon variant="text" @click="startVideoCall">
              <v-icon>mdi-video</v-icon>
            </v-btn>
          </div>
          <v-divider />
          <div class="flex-grow-1 d-flex flex-column">
            <ChatPanel
              class="flex-grow-1"
              :partner="resolveRoomTitle(currentRoom)"
              :partner-login-id="getPartnerLogin(currentRoom)"
              :room-id="currentRoom.roomId"
              :client="client"
              :connected="connected"
              :show-add-friend="false"
            />
          </div>
        </template>
        <div v-else class="flex-grow-1 d-flex align-center justify-center text-grey">
          대화할 채팅방을 선택하세요.
        </div>
      </v-col>
    </v-row>

    <GroupCallDialog
      :open="groupCallOpen"
      :room="currentRoom"
      :client="client"
      :connected="connected"
      :current-login-id="currentLoginId"
      @update:open="groupCallOpen = $event"
    />
  </v-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ChatPanel from '../components/ChatPanel.vue'
import GroupCallDialog from '../components/GroupCallDialog.vue'
import { useFriendsStore } from '../stores/friends'
import { useAuthStore } from '../stores/auth'
import { createStompClient } from '../services/ws'
import { fetchMyRooms, openPrivateRoom } from '../services/chat'

const query = ref('')
const tab = ref('direct')
const rooms = ref([])
const currentRoom = ref(null)
const groupCallOpen = ref(false)

const friendsStore = useFriendsStore()
const auth = useAuthStore()

const client = ref(null)
const connected = ref(false)

const currentLoginId = computed(() => auth.user?.loginId || auth.user?.login_id || '')

const friendsList = computed(() => friendsStore.list)

const filteredFriends = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) return friendsList.value
  return friendsList.value.filter(
    (friend) => friend.nickName?.includes(keyword) || friend.loginId?.includes(keyword)
  )
})

const directRooms = computed(() => rooms.value.filter((room) => room.roomType === 'PRIVATE'))
const filteredDirectRooms = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) return directRooms.value
  return directRooms.value.filter((room) =>
    resolveRoomTitle(room).toLowerCase().includes(keyword.toLowerCase())
  )
})

const groupRooms = computed(() => rooms.value.filter((room) => room.roomType === 'GROUP'))
const filteredGroups = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) return groupRooms.value
  return groupRooms.value.filter((room) =>
    resolveRoomTitle(room).toLowerCase().includes(keyword.toLowerCase())
  )
})

const participantSummary = computed(() => {
  if (!currentRoom.value) return ''
  return currentRoom.value.participants
    .filter((p) => p.loginId !== currentLoginId.value)
    .map((p) => p.nickName || p.loginId)
    .join(', ')
})

function getPartnerInfo(room) {
  if (!room || room.roomType !== 'PRIVATE') return null
  return room.participants?.find((p) => p.loginId !== currentLoginId.value) || null
}

function getPartnerLogin(room) {
  return getPartnerInfo(room)?.loginId || ''
}

function resolveRoomTitle(room) {
  if (!room) return ''
  if (room.roomType === 'PRIVATE') {
    const partner = getPartnerInfo(room)
    return partner?.nickName || partner?.loginId || '1:1 채팅'
  }
  return room.participants
    .filter((p) => p.loginId !== currentLoginId.value)
    .map((p) => p.nickName || p.loginId)
    .join(', ')
}

async function openFriendChat(friend) {
  try {
    const detail = await openPrivateRoom(friend.loginId)
    mergeRoom(detail)
    currentRoom.value = detail
    tab.value = 'direct'
  } catch (error) {
    console.error('친구 채팅방 열기 실패', error)
    window.alert('채팅방을 불러오지 못했습니다.')
  }
}

function mergeRoom(detail) {
  const index = rooms.value.findIndex((room) => room.roomId === detail.roomId)
  if (index >= 0) {
    rooms.value.splice(index, 1, detail)
  } else {
    rooms.value.push(detail)
  }
}

function selectRoom(room) {
  currentRoom.value = room
  tab.value = room.roomType === 'GROUP' ? 'group' : 'direct'
}

async function loadRooms() {
  try {
    const data = await fetchMyRooms()
    rooms.value = Array.isArray(data) ? data : []
    if (currentRoom.value) {
      const updated = rooms.value.find((room) => room.roomId === currentRoom.value.roomId)
      if (updated) {
        currentRoom.value = updated
      }
    }
  } catch (error) {
    console.error('채팅방 목록 불러오기 실패', error)
  }
}

function startVideoCall() {
  if (currentRoom.value?.roomType === 'GROUP') {
    groupCallOpen.value = true
  } else {
    window.alert('1:1 영상 통화는 랜덤 채팅 화면에서 이용해 주세요.')
  }
}

function initStomp() {
  client.value = createStompClient(auth.token)
  client.value.onConnect = () => {
    connected.value = true
  }
  client.value.onDisconnect = () => {
    connected.value = false
  }
  client.value.activate()
}

onMounted(async () => {
  if (!friendsStore.initialized) {
    await friendsStore.fetch()
  }
  await loadRooms()
  initStomp()
})

onBeforeUnmount(() => {
  client.value?.deactivate?.()
})

</script>

<style scoped>
.chat-page {
  height: calc(100vh - var(--v-layout-top));
}

.chat-sidebar {
  background: #fff;
  border-right: 2px solid #f48fb1;
  height: 100%;
}

.chat-main {
  background: #fff;
  border-left: 2px solid #f48fb1;
  height: 100%;
}

.chat-header {
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

</style>

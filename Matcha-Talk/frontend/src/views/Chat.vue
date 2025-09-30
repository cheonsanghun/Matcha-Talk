<template>
  <v-container fluid class="chat-page mt-4">
    <v-row no-gutters class="h-100">
      <!-- Sidebar -->
      <v-col cols="12" md="3" class="chat-sidebar d-flex flex-column">
        <div class="sidebar-header d-flex align-center px-4 py-3">
          <div class="text-h6 font-weight-medium">채팅</div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-message-plus-outline</v-icon></v-btn>
          <v-btn icon variant="text" @click="openGroupDialog"><v-icon>mdi-account-multiple-plus</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-cog-outline</v-icon></v-btn>
          <v-btn icon variant="text"><v-icon>mdi-dots-vertical</v-icon></v-btn>
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
        <div class="px-4 pb-3 d-flex ga-2 flex-wrap follow-stats">
          <v-chip size="small" variant="tonal" color="primary" class="text-caption">
            팔로잉 {{ followings.length }}
          </v-chip>
          <v-chip size="small" variant="tonal" color="pink" class="text-caption">
            팔로워 {{ followers.length }}
          </v-chip>
        </div>
        <v-tabs v-model="tab" density="comfortable" class="px-4">
          <v-tab value="direct">1:1 채팅</v-tab>
          <v-tab value="group">그룹 채팅</v-tab>
        </v-tabs>
        <v-divider />
        <div class="flex-grow-1 overflow-y-auto">
          <v-list v-if="tab === 'direct'">
            <template v-if="filteredDirectChats.length">
              <v-list-subheader
                v-if="filteredFollowShortcuts.length"
                class="text-caption text-medium-emphasis"
              >
                참여 중인 1:1 채팅
              </v-list-subheader>
              <v-list-item
                v-for="item in filteredDirectChats"
                :key="item.id"
                :active="current.id === item.id"
                @click="openChat(item)"
                lines="two"
              >
                <template #prepend>
                  <v-avatar size="40">
                    <template v-if="item.avatarUrl">
                      <v-img :src="item.avatarUrl" alt="프로필 이미지" cover />
                    </template>
                    <template v-else>
                      <v-icon color="primary">mdi-account</v-icon>
                    </template>
                  </v-avatar>
                </template>
                <v-list-item-title>{{ item.name }}</v-list-item-title>
                <v-list-item-subtitle>{{ item.last }}</v-list-item-subtitle>
              </v-list-item>
            </template>

            <template v-if="filteredFollowShortcuts.length">
              <v-list-subheader class="text-caption text-medium-emphasis">
                팔로워 · 팔로잉 친구
              </v-list-subheader>
              <v-list-item
                v-for="item in filteredFollowShortcuts"
                :key="item.id"
                :active="current.id === item.id"
                @click="openChat(item)"
                lines="two"
              >
                <template #prepend>
                  <v-avatar size="40">
                    <template v-if="item.avatarUrl">
                      <v-img :src="item.avatarUrl" alt="프로필 이미지" cover />
                    </template>
                    <template v-else>
                      <v-icon color="pink">mdi-account-heart</v-icon>
                    </template>
                  </v-avatar>
                </template>
                <v-list-item-title>{{ item.name }}</v-list-item-title>
                <v-list-item-subtitle>{{ item.last }}</v-list-item-subtitle>
              </v-list-item>
            </template>

            <v-list-item v-if="!filteredDirectChats.length && !filteredFollowShortcuts.length">
              <v-list-item-title class="text-caption text-grey">
                참여 중인 1:1 채팅이 없습니다.
              </v-list-item-title>
            </v-list-item>
          </v-list>
          <v-list v-else>
            <template v-if="filteredGroups.length">
              <v-list-item
                v-for="item in filteredGroups"
                :key="item.id"
                :active="current.id === item.id"
                @click="openChat(item)"
                lines="two"
              >
                <template #prepend>
                  <v-avatar size="40"><v-icon color="primary">mdi-account-group</v-icon></v-avatar>
                </template>
                <v-list-item-title>{{ item.name }}</v-list-item-title>
                <v-list-item-subtitle>{{ item.participants.join(', ') }}</v-list-item-subtitle>
              </v-list-item>
            </template>
            <v-list-item v-else>
              <v-list-item-title class="text-caption text-grey">
                참여 중인 그룹 채팅이 없습니다.
              </v-list-item-title>
            </v-list-item>
          </v-list>
        </div>
      </v-col>

      <!-- Conversation -->
      <v-col cols="12" md="9" class="chat-main d-flex flex-column">
        <div class="chat-header d-flex align-center pa-4">
          <v-avatar size="40"><v-icon color="primary">mdi-account</v-icon></v-avatar>
          <div class="ml-3">
            <div class="text-subtitle-1 font-weight-medium">{{ current.name || '채팅방을 선택하세요' }}</div>
            <div class="text-caption text-grey" v-if="isGroup">{{ groupParticipants }}</div>
          </div>
          <v-spacer />
          <v-btn icon variant="text"><v-icon>mdi-magnify</v-icon></v-btn>
          <v-btn v-if="isGroup" icon variant="text" @click="inviteParticipant"><v-icon>mdi-account-plus</v-icon></v-btn>
          <v-btn
            class="messenger-pill toolbar-btn video-call-btn"
            variant="flat"
            elevation="2"
            rounded="pill"
            :disabled="!current.id || callRequestInFlight || callStartInProgress || callActive"
            :loading="callRequestInFlight || callStartInProgress"
            @click="startVideoCall"
          >
            <v-icon size="18" class="mr-1">mdi-video</v-icon>
            <span>
              {{
                callActive
                  ? '통화 중'
                  : callReady
                    ? (remoteCallReady ? '통화 연결 중...' : '상대 대기 중...')
                    : '영상통화'
              }}
            </span>
          </v-btn>
          <v-btn icon variant="text" :disabled="!canHangUp" @click="hangUpCall"><v-icon>mdi-phone-hangup</v-icon></v-btn>
        </div>
        <v-divider />
        <div class="chat-body d-flex flex-grow-1">
          <div class="video-pane" v-if="current.id">
            <VideoChat v-if="videoPaneVisible" ref="videoChatRef" />
            <div v-else class="video-placeholder d-flex flex-column align-center justify-center ga-3">
              <v-icon size="56" color="primary">mdi-video-outline</v-icon>
              <div class="text-subtitle-2 text-medium-emphasis text-center">
                영상 통화를 시작하려면 상단의 버튼을 눌러주세요.
              </div>
            </div>
          </div>
          <div class="chat-messages flex-grow-1 pa-4 overflow-y-auto" ref="chatMessagesContainer">
            <div
              v-if="isLoadingRooms && !current.id"
              class="d-flex align-center justify-center h-100 text-caption text-grey"
            >
              채팅방 정보를 불러오는 중입니다...
            </div>
            <div
              v-else-if="!current.id"
              class="d-flex align-center justify-center h-100 text-caption text-grey"
            >
              좌측 목록에서 채팅방을 선택하세요.
            </div>
            <div v-else class="chat-messages__list">
              <div
                v-for="(m, i) in messages"
                :key="m.id || i"
                class="d-flex mb-4"
                :class="{ 'justify-end': m.me }"
              >
                <template v-if="!m.me">
                  <v-avatar size="32" class="mr-2"><v-icon color="primary">mdi-account</v-icon></v-avatar>
                  <div class="message-wrapper">
                    <div v-if="isGroup" class="text-caption font-weight-medium mb-1">{{ m.sender }}</div>
                    <div class="message-bubble" :class="bubbleClass(m)">
                      <template v-if="m.contentType === 'IMAGE' && m.fileUrl">
                        <img :src="m.fileUrl" :alt="m.fileName || '이미지'" class="message-image" />
                        <div v-if="m.fileName" class="text-caption mt-1">{{ m.fileName }}</div>
                      </template>
                      <template v-else-if="m.contentType === 'FILE' && m.fileUrl">
                        <a
                          :href="m.fileUrl"
                          target="_blank"
                          rel="noopener"
                          class="file-link"
                          @click.prevent="downloadAttachment(m)"
                        >
                          <v-icon size="18" class="mr-1">mdi-paperclip</v-icon>
                          {{ m.fileName || m.text || '파일 다운로드' }}
                        </a>
                      </template>
                      <template v-else>
                        {{ m.text }}
                      </template>
                    </div>
                    <div v-if="m.translation" class="text-caption text-grey mt-1">
                      {{ m.translation }}
                      <v-icon size="16" class="ms-1 cursor-pointer" @click="saveWord(m)">mdi-content-save</v-icon>
                    </div>
                    <div class="message-tools">
                      <v-btn
                        v-if="m.contentType === 'TEXT'"
                        icon
                        variant="text"
                        density="compact"
                        @click="translateMessage(m)"
                        :loading="m.translating"
                      >
                        <v-icon size="18">mdi-translate</v-icon>
                      </v-btn>
                      <span class="text-caption text-grey">{{ m.time }}</span>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <div class="message-wrapper text-right ml-auto">
                    <div class="message-bubble" :class="bubbleClass(m)">
                      <template v-if="m.contentType === 'IMAGE' && m.fileUrl">
                        <img :src="m.fileUrl" :alt="m.fileName || '이미지'" class="message-image" />
                        <div v-if="m.fileName" class="text-caption mt-1">{{ m.fileName }}</div>
                      </template>
                      <template v-else-if="m.contentType === 'FILE' && m.fileUrl">
                        <a
                          :href="m.fileUrl"
                          target="_blank"
                          rel="noopener"
                          class="file-link text-white"
                          @click.prevent="downloadAttachment(m)"
                        >
                          <v-icon size="18" class="mr-1">mdi-paperclip</v-icon>
                          {{ m.fileName || m.text || '파일 다운로드' }}
                        </a>
                      </template>
                      <template v-else>
                        {{ m.text }}
                      </template>
                    </div>
                    <div v-if="m.translation" class="text-caption text-grey-lighten-2 mt-1">
                      {{ m.translation }}
                      <v-icon size="16" class="ms-1 cursor-pointer" @click="saveWord(m)">mdi-content-save</v-icon>
                    </div>
                    <div class="message-tools justify-end">
                      <v-btn
                        v-if="m.contentType === 'TEXT'"
                        icon
                        variant="text"
                        density="compact"
                        color="white"
                        @click="translateMessage(m)"
                        :loading="m.translating"
                      >
                        <v-icon size="18">mdi-translate</v-icon>
                      </v-btn>
                      <span class="text-caption text-grey-lighten-1">{{ m.time }}</span>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
        <div class="chat-input d-flex align-center pa-4 ga-2">
          <input type="file" ref="fileInput" class="d-none" @change="handleFileSelect" />
          <v-btn
            class="messenger-pill chat-action-btn file-upload-btn"
            variant="flat"
            elevation="3"
            rounded="pill"
            :disabled="!current.id"
            @click="triggerFilePicker"
          >
            <v-icon size="22">mdi-file-outline</v-icon>
          </v-btn>
          <v-text-field
            v-model="draft"
            variant="outlined"
            density="comfortable"
            hide-details
            placeholder="메시지를 입력하세요..."
            class="flex-grow-1"
            @keydown.enter="handleEnterKey"
            @compositionstart="onCompositionStart"
            @compositionend="onCompositionEnd"
            :disabled="!current.id"
          />
          <v-btn icon variant="text"><v-icon>mdi-emoticon-outline</v-icon></v-btn>
          <v-btn
            class="messenger-pill chat-action-btn send-btn"
            variant="flat"
            elevation="4"
            rounded="pill"
            :disabled="!current.id || !draft.trim()"
            @click="send"
          >
            <v-icon size="18" class="mr-1">mdi-send</v-icon>
            <span>보내기</span>
          </v-btn>
        </div>
      </v-col>
    </v-row>
  </v-container>

  <v-dialog v-model="createGroupDialog" max-width="480">
    <v-card>
      <v-card-title class="text-h6">새 그룹 채팅 만들기</v-card-title>
      <v-card-text class="d-flex flex-column ga-4">
        <v-text-field
          v-model="groupForm.name"
          label="그룹 이름"
          placeholder="그룹 이름을 입력하세요 (선택)"
          variant="outlined"
          clearable
        />
        <v-autocomplete
          v-model="groupForm.members"
          :items="groupMemberOptions"
          label="초대할 친구 선택"
          item-title="label"
          item-value="userPid"
          multiple
          chips
          closable-chips
          variant="outlined"
          :counter="maxAdditionalGroupMembers"
          :disabled="groupMemberOptions.length === 0"
          :hint="groupMemberOptions.length ? `최대 ${maxAdditionalGroupMembers}명까지 선택할 수 있습니다.` : '초대할 수 있는 친구가 없습니다.'"
          persistent-hint
          :no-data-text="'초대할 수 있는 친구가 없습니다.'"
        >
          <template #chip="{ props, item }">
            <v-chip
              v-bind="props"
              :prepend-avatar="item.raw.avatarUrl || undefined"
              class="text-truncate"
            >
              {{ item.raw.label }}
            </v-chip>
          </template>
          <template #item="{ props, item }">
            <v-list-item
              v-bind="props"
              :prepend-avatar="item.raw.avatarUrl || undefined"
              :title="item.raw.label"
              :subtitle="item.raw.description"
            />
          </template>
        </v-autocomplete>
      </v-card-text>
      <v-card-actions class="justify-end">
        <v-btn variant="text" @click="closeGroupDialog">취소</v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :loading="groupCreationLoading"
          :disabled="groupMemberOptions.length === 0 || groupForm.members.length === 0 || groupCreationLoading"
          @click="submitGroupCreation"
        >
          생성
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../services/api'
import { translate } from '../services/translator'
import { useVocabularyStore } from '../stores/vocabulary'
import { resolveClientIdentity } from '../utils/identity'
import VideoChat from '../components/VideoChat.vue'
import followService from '../services/follow'
import {
  createFileSelectHandler,
  createIncomingMessageHandler,
  normalizeAttachmentUrl,
  useRealtimeChatClient,
} from '../composables/useChatClient'

const query = ref('')
const tab = ref('direct')
const chats = ref([])
const groups = ref([])
const conversations = ref({})
const current = ref({})
const draft = ref('')
const isComposing = ref(false)
const followings = ref([])
const followers = ref([])
const followRelationshipMap = ref(new Map())
const createGroupDialog = ref(false)
const groupCreationLoading = ref(false)
const groupForm = reactive({ name: '', members: [] })
const maxAdditionalGroupMembers = 3


const chatMessagesContainer = ref(null)
const fileInput = ref(null)
const videoChatRef = ref(null)
const videoPaneVisible = ref(false)
const callActive = ref(false)
const callRequestInFlight = ref(false)
const callStartInProgress = ref(false)
const callReady = ref(false)
const remoteCallReady = ref(false)
const historyLoadedRooms = ref(new Set())
const isLoadingRooms = ref(false)

const auth = useAuthStore()
const vocabularyStore = useVocabularyStore()
const route = useRoute()
const router = useRouter()
const myUserPid = computed(() => auth.user?.userPid ?? auth.user?.user_pid ?? null)

function resolveCurrentRoomId () {
  return current.value?.id
}

const handleIncomingMessage = createIncomingMessageHandler({
  auth,
  ensureConversation,
  ensureRoomExists,
  scrollToBottom: (roomKey) => {
    if (current.value?.id === roomKey) {
      scrollToBottom()
    }
  },
  formatTime,
})

const handleFileSelect = createFileSelectHandler({
  getRoomId: resolveCurrentRoomId,
})

const {
  realtimeClient,
  connect: connectRealtime,
  disconnect: disconnectRealtime,
  setManualDisconnect,
} = useRealtimeChatClient(auth, {
  maxReconnectAttempts: 3,
  onChat: (payload) => handleIncomingMessage(payload),
  onMatchResult: (payload) => handleMatchResultEvent(payload),
  events: {
    'match-follow-updated': () => { void loadRooms({ preserveCurrent: true }) },
    'match-room-promoted': () => { void loadRooms({ preserveCurrent: true }) },
    'room-call-ready': (payload) => { handleCallReadyEvent(payload) },
    'room-call-ended': (payload) => { handleCallEndedEvent(payload) },
  },
})

onMounted(async () => {
  await loadRooms()
  await ensureActiveRoomFromRoute()
  setManualDisconnect(false)
  connectRealtime()
})

watch(() => route.query.roomId, () => {
  void ensureActiveRoomFromRoute()
})

watch(
  () => auth.user?.userPid,
  async (userPid, prev) => {
    if (userPid && userPid !== prev) {
      await loadRooms({ preserveCurrent: true })
      await ensureActiveRoomFromRoute()
    }
  }
)

watch(
  () => current.value?.id,
  (nextRoom) => {
    if (videoChatRef.value?.hangUp) {
      try {
        videoChatRef.value.hangUp()
      } catch (error) {
        console.warn('Failed to terminate active call when switching rooms', error)
      }
    }
    callActive.value = false
    resetCallHandshake()
    videoPaneVisible.value = false
    const numericRoomId = Number(nextRoom)
    if (Number.isFinite(numericRoomId) && numericRoomId > 0) {
      void ensureMessageHistory(numericRoomId)
    }
    scrollToBottom()
  }
)

watch(
  () => groupForm.members,
  (members) => {
    if (!Array.isArray(members)) return
    const unique = Array.from(new Set(members.map((value) => Number(value))))
      .filter((value) => Number.isFinite(value))
    if (unique.length > maxAdditionalGroupMembers) {
      unique.splice(maxAdditionalGroupMembers)
    }
    if (unique.length !== members.length || !members.every((value, index) => value === unique[index])) {
      groupForm.members = unique
    }
  },
  { deep: true }
)

watch(createGroupDialog, (open) => {
  if (!open) {
    resetGroupForm()
  }
})

onUnmounted(() => {
  setManualDisconnect(true)
  disconnectRealtime()
})

async function loadRooms(options = {}) {
  try {
    isLoadingRooms.value = true
    const { preserveCurrent = false } = options
    const [roomsResponse, followData] = await Promise.all([
      api.get('/rooms/my'),
      fetchFollowLists(),

    ])

    const data = Array.isArray(roomsResponse?.data) ? roomsResponse.data : []
    const meNickname = auth.user?.nickName || auth.user?.nickname
    const directRooms = []
    const groupRooms = []

    data.forEach((room) => {
      const entry = normalizeRoomListEntry(room, meNickname)
      if (!entry || entry.type === 'RANDOM' || entry.temporary) {
        return
      }
      ensureConversation(entry.id)
      if (entry.type === 'GROUP') {
        groupRooms.push(entry)
      } else {
        directRooms.push(entry)
      }
    })

    const excludePid = Number.isFinite(Number(myUserPid.value)) ? Number(myUserPid.value) : null
    followings.value = normalizeFollowList(followData?.following, excludePid)
    followers.value = normalizeFollowList(followData?.followers, excludePid)
    followRelationshipMap.value = buildFollowRelationshipMap(followings.value, followers.value)
    const followEntries = buildFollowEntries(followRelationshipMap.value, directRooms)

    chats.value = [...directRooms, ...followEntries]
    groups.value = groupRooms

    const hasActiveRoom = Boolean(current.value?.id)

    if (hasActiveRoom) {
      await selectRoomById(current.value.id, { skipRouteUpdate: true })
    } else if (!preserveCurrent) {
      const fallback = [...chats.value.filter((room) => !room.virtual), ...groups.value][0]
      if (fallback) {
        await openChat(fallback, { skipRouteUpdate: true })
      }
    }
  } catch (error) {
    console.error('[chat] Failed to load rooms', error)
  } finally {
    isLoadingRooms.value = false
  }
}

async function fetchFollowLists() {
  const userPid = auth.user?.userPid ?? auth.user?.user_pid ?? null
  if (!userPid) {
    return { following: [], followers: [] }
  }

  try {
    const [following, follower] = await Promise.all([
      followService.getFollowing(userPid),
      followService.getFollowers(userPid),
    ])
    return {
      following: Array.isArray(following) ? following : [],
      followers: Array.isArray(follower) ? follower : [],
    }
  } catch (error) {
    console.warn('[chat] Failed to load follow list', error)
    return { following: [], followers: [] }
  }
}

function normalizeFollowUser(entry) {
  const userPid = Number(entry?.userPid ?? entry?.user_pid)
  if (!Number.isFinite(userPid)) {
    return null
  }
  const loginId = entry?.loginId || entry?.login_id || null
  const nickName = entry?.nickName || entry?.nickname || loginId || `사용자 ${userPid}`
  return {
    userPid,
    loginId,
    nickName,
    avatarUrl: entry?.avatarUrl || entry?.avatar_url || null,
    email: entry?.email || entry?.emailAddress || null,
  }
}

function normalizeFollowList(list, excludePid) {
  if (!Array.isArray(list)) {
    return []
  }

  return list
    .map(normalizeFollowUser)
    .filter((entry) => entry && (excludePid == null || entry.userPid !== excludePid))
    .sort((a, b) => a.nickName.localeCompare(b.nickName, 'ko'))
}

function buildFollowRelationshipMap(followingList, followerList) {
  const map = new Map()

  const register = (list, role) => {
    if (!Array.isArray(list)) return
    list.forEach((entry) => {
      if (!entry) return
      const existing = map.get(entry.userPid) || {
        userPid: entry.userPid,
        loginId: entry.loginId,
        nickName: entry.nickName,
        avatarUrl: entry.avatarUrl,
        email: entry.email || null,
        following: false,
        follower: false,
      }
      if (!existing.loginId && entry.loginId) existing.loginId = entry.loginId
      if (!existing.avatarUrl && entry.avatarUrl) existing.avatarUrl = entry.avatarUrl
      if (!existing.nickName && entry.nickName) existing.nickName = entry.nickName
      existing[role] = true
      map.set(entry.userPid, existing)
    })
  }

  register(followingList, 'following')
  register(followerList, 'follower')

  return map
}

function buildFollowEntries(relationshipMap, directRooms) {
  if (!relationshipMap || relationshipMap.size === 0) {
    return []
  }

  const existingLogins = new Set()
  const existingUserPids = new Set()

  directRooms.forEach((room) => {
    const participantLogins = Array.isArray(room.participantLogins)
      ? room.participantLogins
      : []
    participantLogins.forEach((login) => {
      if (login) {
        existingLogins.add(String(login).toLowerCase())
      }
    })
    const participantsDetail = Array.isArray(room.participantsDetail)
      ? room.participantsDetail
      : []
    participantsDetail.forEach((participant) => {
      const pid = participant?.userPid ?? participant?.user_pid ?? null
      if (pid != null) {
        existingUserPids.add(Number(pid))
      }
    })
  })

  return Array.from(relationshipMap.values())
    .filter((candidate) => candidate && candidate.following && candidate.follower)
    .filter((candidate) => {
      if (existingUserPids.has(candidate.userPid)) return false
      if (candidate.loginId && existingLogins.has(String(candidate.loginId).toLowerCase())) {
        return false
      }
      return true
    })
    .map((candidate) => {
      const mutual = true
      const subtitle = '서로 팔로우 중입니다. 클릭하여 채팅을 시작하세요.'

      return {
        id: `follow-${candidate.userPid}`,
        name: candidate.nickName,
        last: subtitle,
        participants: [candidate.nickName],
        participantLogins: candidate.loginId ? [candidate.loginId] : [],
        loginId: candidate.loginId || null,
        participantsDetail: [],
        type: 'DIRECT',
        virtual: true,
        targetUserPid: candidate.userPid,
        avatarUrl: candidate.avatarUrl,
        mutual,
      }
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
}

function normalizeIdentifier(value) {
  if (value == null) return ''
  return String(value).trim().toLowerCase()
}

function buildIdentifierSet(values) {
  const set = new Set()
  if (!Array.isArray(values)) {
    values = [values]
  }
  values.forEach((value) => {
    const normalized = normalizeIdentifier(value)
    if (normalized) {
      set.add(normalized)
    }
  })
  return set
}

function findExistingDirectRoomForFollowShortcut(item) {
  if (!item) return null

  const candidateLogins = buildIdentifierSet([
    ...(Array.isArray(item.participantLogins) ? item.participantLogins : []),
    item.loginId,
  ])
  const candidateNames = buildIdentifierSet([
    ...(Array.isArray(item.participants) ? item.participants : []),
    item.name,
  ])

  const myLogin = normalizeIdentifier(resolveClientIdentity(auth))
  if (myLogin) {
    candidateLogins.delete(myLogin)
  }
  const myNickname = normalizeIdentifier(auth.user?.nickName || auth.user?.nickname)
  if (myNickname) {
    candidateNames.delete(myNickname)
  }

  if (!candidateLogins.size && !candidateNames.size) {
    return null
  }

  const matchesRoom = (room) => {
    if (!room || room.virtual || room.type === 'GROUP') {
      return false
    }

    const roomLogins = buildIdentifierSet(room.participantLogins || [])
    for (const login of roomLogins) {
      if (candidateLogins.has(login)) {
        return true
      }
    }

    const detailEntries = Array.isArray(room.participantsDetail) ? room.participantsDetail : []
    for (const participant of detailEntries) {
      const login = normalizeIdentifier(participant?.loginId || participant?.login_id)
      if (login && candidateLogins.has(login)) {
        return true
      }
      const nickname = normalizeIdentifier(participant?.nickname || participant?.nickName)
      if (nickname && candidateNames.has(nickname)) {
        return true
      }
    }

    const roomNames = buildIdentifierSet([
      ...(Array.isArray(room.participants) ? room.participants : []),
      room.name,
    ])
    for (const name of roomNames) {
      if (candidateNames.has(name)) {
        return true
      }
    }

    return false
  }

  const directRooms = chats.value.filter((room) => room && !room.virtual && room.type !== 'GROUP')
  return directRooms.find((room) => matchesRoom(room)) || null
}

function normalizeRoomListEntry(room, meNickname) {
  const participants = room.memberNicknames ?? []
  const type = (room.roomType || 'PRIVATE').toString().toUpperCase()
  const temporary = Boolean(room.temporary ?? room.temp ?? false)
  const others = meNickname ? participants.filter((nick) => nick !== meNickname) : participants

  let displayName
  if (type === 'GROUP') {
    displayName = participants.join(', ')
  } else {
    const fallbackName = Number.isFinite(Number(room.roomId)) && Number(room.roomId) > 0
      ? `대화방 #${room.roomId}`
      : '이름 없는 채팅방'
    displayName = others[0] || participants[0] || fallbackName
  }

  return {
    id: room.roomId,
    name: displayName,
    last: '',
    participants,
    participantLogins: Array.isArray(room.memberLoginIds) ? room.memberLoginIds : [],
    participantsDetail: [],
    type,
    temporary,
  }
}

function normalizeRoomDetail(detail) {
  const participants = (detail.participants || []).map((participant) => participant.nickname)
  const type = (detail.roomType || 'PRIVATE').toString().toUpperCase()
  const meNickname = auth.user?.nickName || auth.user?.nickname
  const others = meNickname ? participants.filter((nick) => nick !== meNickname) : participants

  let displayName
  if (type === 'GROUP') {
    displayName = participants.join(', ')
  } else {
    const fallbackName = Number.isFinite(Number(detail.roomId)) && Number(detail.roomId) > 0
      ? `대화방 #${detail.roomId}`
      : '이름 없는 채팅방'
    displayName = others[0] || participants[0] || fallbackName
  }

  return {
    id: detail.roomId,
    name: displayName,
    last: '',
    participants,
    participantLogins: (detail.participants || []).map((participant) => participant.loginId).filter(Boolean),
    participantsDetail: detail.participants || [],
    type
  }
}

function addOrUpdateRoom(entry) {
  const list = entry.type === 'GROUP' ? groups.value : chats.value
  const index = list.findIndex((room) => room.id === entry.id)
  if (index >= 0) {
    list[index] = { ...list[index], ...entry }
    return list[index]
  }
  list.push(entry)
  return entry
}

function findRoomById(roomId) {
  return chats.value.find((room) => room.id === roomId) || groups.value.find((room) => room.id === roomId)
}

async function selectRoomById(roomId, options = {}) {
  const room = findRoomById(roomId)
  if (room) {
    await openChat(room, options)
    return true
  }
  return false
}

async function ensureActiveRoomFromRoute() {
  const rawId = route.query.roomId
  const parsedId = rawId ? Number(rawId) : NaN

  if (!Number.isNaN(parsedId) && parsedId) {
    if (await selectRoomById(parsedId, { skipRouteUpdate: true })) {
      return
    }
  }

  if (!current.value?.id) {
    const fallback = [...chats.value.filter((room) => !room.virtual), ...groups.value][0]
    if (fallback) {
      await openChat(fallback, { skipRouteUpdate: true })
    }
  }
}

const groupMemberOptions = computed(() =>
  Array.from(followRelationshipMap.value.values())
    .map((entry) => ({
      ...entry,
      label: entry.nickName,
      description: entry.following && entry.follower
        ? '서로 팔로우 중'
        : entry.following
          ? '팔로잉 중'
          : '팔로워',
    }))
    .sort((a, b) => a.label.localeCompare(b.label, 'ko'))
)

const filteredChats = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return chats.value
  return chats.value.filter((room) => {
    return (
      room.name?.toLowerCase().includes(keyword) ||
      room.last?.toLowerCase().includes(keyword)
    )
  })
})

const filteredDirectChats = computed(() => filteredChats.value.filter((room) => !room.virtual))
const filteredFollowShortcuts = computed(() =>
  filteredChats.value.filter((room) => room.virtual && room.mutual === true)
)

const filteredGroups = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  if (!keyword) return groups.value
  return groups.value.filter((room) => {
    const participants = room.participants.join(', ').toLowerCase()
    return room.name?.toLowerCase().includes(keyword) || participants.includes(keyword)
  })
})

const isGroup = computed(() => current.value?.type === 'GROUP')
const groupParticipants = computed(() => (current.value?.participants || []).join(', '))
const messages = computed(() => conversations.value[current.value?.id] ?? [])
const canHangUp = computed(
  () => callActive.value || callReady.value || callRequestInFlight.value || callStartInProgress.value
)

async function openChat(item, options = {}) {
  if (!item) return

  const { skipRouteUpdate = false, forceHistory = false } = options

  if (item.virtual && item.targetUserPid) {
    await openDirectFollowRoom(item, options)
    return
  }

  const room = await ensureRoomExists(item.id, item.name)
  current.value = room
  videoPaneVisible.value = false
  tab.value = item.type === 'GROUP' ? 'group' : 'direct'
  ensureConversation(item.id)
  const numericRoomId = Number(item.id)
  if (Number.isFinite(numericRoomId) && numericRoomId > 0) {
    await ensureMessageHistory(numericRoomId, { force: forceHistory })
  }

  if (!skipRouteUpdate) {
    const newQuery = { ...route.query, roomId: String(item.id) }
    router.replace({ name: 'chat', query: newQuery })
  }

  scrollToBottom()
}

async function openDirectFollowRoom(item, options = {}) {
  const existingRoom = findExistingDirectRoomForFollowShortcut(item)
  if (existingRoom) {
    chats.value = chats.value.filter((chat) => chat.id !== item.id)
    await openChat(existingRoom, options)
    return
  }

  try {
    const { data } = await api.post('/rooms/direct', { targetUserPid: item.targetUserPid })
    const entry = normalizeRoomDetail(data)

    chats.value = chats.value.filter((chat) => chat.id !== item.id)
    const persisted = addOrUpdateRoom(entry)
    await openChat(persisted, { ...options, forceHistory: true })
  } catch (error) {
    console.error('[chat] Failed to prepare direct chat room', error)
    alert('채팅방을 준비하지 못했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  }
}

function ensureConversation(roomId) {
  if (!conversations.value[roomId]) {
    conversations.value[roomId] = []
  }
  return conversations.value[roomId]
}

function resetCallHandshake() {
  callReady.value = false
  remoteCallReady.value = false
  callRequestInFlight.value = false
  callStartInProgress.value = false
}

async function ensureVideoPaneReady() {
  if (!current.value?.id) {
    return false
  }
  if (!videoPaneVisible.value) {
    videoPaneVisible.value = true
    await nextTick()
  }
  return Boolean(videoChatRef.value)
}

function hideVideoPane() {
  videoPaneVisible.value = false
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatMessagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function applyCallReadyState(readyMembers = [], allReady = false) {
  const myLogin = resolveClientIdentity(auth)
  const normalizedReady = Array.isArray(readyMembers)
    ? readyMembers
      .map((login) => (login ? String(login).toLowerCase() : null))
      .filter(Boolean)
    : []
  const normalizedMyLogin = myLogin ? String(myLogin).toLowerCase() : null

  if (normalizedMyLogin) {
    callReady.value = normalizedReady.includes(normalizedMyLogin)
  } else {
    callReady.value = false
  }

  remoteCallReady.value = normalizedReady.some((login) => login !== normalizedMyLogin)

  if (!allReady && callReady.value && !remoteCallReady.value) {
    // 내가 준비 완료이고 상대 대기 중
    callStartInProgress.value = false
  }
}

function normalizeHistoryMessage(entry, roomKey) {
  if (!entry) return null
  const myLogin = resolveClientIdentity(auth)
  const senderLogin = entry.senderLoginId || entry.senderLoginID || entry.sender_login_id || null
  const normalizedSender = senderLogin ? String(senderLogin).toLowerCase() : null
  const normalizedMyLogin = myLogin ? String(myLogin).toLowerCase() : null

  const messageId = entry.messageId || entry.message_id || `${roomKey}-${entry.sentAt || Date.now()}`
  return {
    id: messageId,
    text: entry.content || '',
    time: formatTime(entry.sentAt),
    sender: entry.senderNickName || entry.senderNickname || senderLogin || '상대방',
    senderLoginId: senderLogin,
    me: Boolean(normalizedMyLogin && normalizedSender && normalizedMyLogin === normalizedSender),
    contentType: (entry.contentType || 'TEXT').toUpperCase(),
    fileName: entry.fileName || null,
    fileUrl: normalizeAttachmentUrl(entry.fileUrl || entry.file_url || null),
    translation: null,
    translating: false,
    sentAt: entry.sentAt || null,
  }
}

function mergeMessageHistory(roomId, historyMessages) {
  const existing = ensureConversation(roomId)
  if (!Array.isArray(historyMessages) || historyMessages.length === 0) {
    if (!existing.length) {
      conversations.value[roomId] = []
    }
    return
  }

  const map = new Map()
  const keyFor = (message) => {
    if (!message) return null
    return message.id || `${message.sentAt ?? ''}-${message.senderLoginId ?? ''}-${message.text ?? ''}`
  }

  historyMessages.forEach((message) => {
    const key = keyFor(message)
    if (!key) return
    map.set(key, message)
  })

  existing.forEach((message) => {
    const key = keyFor(message)
    if (!key) return
    const stored = map.get(key)
    if (stored) {
      map.set(key, { ...stored, ...message })
    } else {
      map.set(key, message)
    }
  })

  const sorted = Array.from(map.values()).sort((a, b) => {
    const aTime = a?.sentAt ? new Date(a.sentAt).getTime() : NaN
    const bTime = b?.sentAt ? new Date(b.sentAt).getTime() : NaN
    if (Number.isNaN(aTime) && Number.isNaN(bTime)) return 0
    if (Number.isNaN(aTime)) return -1
    if (Number.isNaN(bTime)) return 1
    return aTime - bTime
  })

  conversations.value[roomId] = sorted

  const latest = [...sorted].reverse().find((message) => {
    if (!message) return false
    if (message.contentType && message.contentType !== 'TEXT') {
      return true
    }
    return Boolean(message.text && message.text.trim().length)
  })

  if (latest) {
    const room = findRoomById(roomId)
    if (room && Object.prototype.hasOwnProperty.call(room, 'last')) {
      if (latest.contentType && latest.contentType !== 'TEXT') {
        room.last = latest.fileName || latest.text || '[첨부파일]'
      } else {
        room.last = latest.text
      }
    }
  }
}

async function ensureMessageHistory(roomId, options = {}) {
  const numericRoomId = Number(roomId)
  if (!Number.isFinite(numericRoomId) || numericRoomId <= 0) {
    return
  }

  const { force = false } = options

  if (!force && historyLoadedRooms.value.has(numericRoomId)) return

  try {
    const { data } = await api.get(`/rooms/${numericRoomId}/messages`, { params: { limit: 100 } })
    const normalized = Array.isArray(data)
      ? data
        .map((entry) => normalizeHistoryMessage(entry, numericRoomId))
        .filter(Boolean)
      : []
    mergeMessageHistory(numericRoomId, normalized)
    historyLoadedRooms.value.add(numericRoomId)
    scrollToBottom()
  } catch (error) {
    console.warn('[chat] Failed to load message history', error)
  }
}

async function ensureRoomExists(roomId, fallbackName) {
  let room = findRoomById(roomId)
  const requiresHydration = !room || !(room.participantLogins && room.participantLogins.length)

  if (requiresHydration) {
    try {
      const { data } = await api.get(`/rooms/${roomId}`)
      room = addOrUpdateRoom(normalizeRoomDetail(data))
    } catch (error) {
      console.warn(`[chat] Failed to fetch room ${roomId}, using fallback`, error)
      if (!room) {
        const fallback = fallbackName || (Number.isFinite(Number(roomId)) && Number(roomId) > 0
          ? `대화방 #${roomId}`
          : '이름 없는 채팅방')
        room = addOrUpdateRoom({
          id: roomId,
          name: fallback,
          last: '',
          participants: fallbackName ? [fallbackName] : [],
          participantLogins: [],
          participantsDetail: [],
          type: 'PRIVATE'
        })
      }
    }
  }

  if (!room) {
    const fallback = fallbackName || (Number.isFinite(Number(roomId)) && Number(roomId) > 0
      ? `대화방 #${roomId}`
      : '이름 없는 채팅방')
    room = addOrUpdateRoom({
      id: roomId,
      name: fallback,
      last: '',
      participants: fallbackName ? [fallbackName] : [],
      participantLogins: [],
      participantsDetail: [],
      type: 'PRIVATE'
    })
  }

  ensureConversation(room.id)
  return room
}

async function initiateVideoCall() {
  if (callActive.value || callStartInProgress.value) {
    return
  }
  if (!current.value?.id) {
    return
  }
  const paneReady = await ensureVideoPaneReady()
  if (!paneReady || !videoChatRef.value?.startCall) {
    console.warn('VideoChat component is not ready')
    return
  }

  callStartInProgress.value = true
  try {
    const participantLogins = await resolveParticipantLogins(current.value.id)
    const myLoginId = resolveClientIdentity(auth)
    const targets = participantLogins.filter((loginId) => loginId && loginId !== myLoginId)

    if (!targets.length) {
      console.warn('[chat] No target available for video call')
      return
    }

    for (const loginId of targets) {
      await videoChatRef.value.startCall(loginId)
    }
    callActive.value = true
  } catch (error) {
    console.error('[chat] Failed to start video call', error)
    alert('영상 통화를 시작하지 못했습니다.')
  } finally {
    callStartInProgress.value = false
    callRequestInFlight.value = false
  }
}

async function handleMatchResultEvent(payload) {
  const roomId = payload?.roomId ?? payload?.room_id
  const partner = payload?.partnerNickName || payload?.partnerNickname
  if (roomId) {
    await ensureRoomExists(roomId, partner)
  }
  await loadRooms({ preserveCurrent: true })
  if (roomId) {
    selectRoomById(Number(roomId))
  }
}

function handleCallReadyEvent(payload) {
  const roomId = Number(payload?.roomId ?? payload?.room_id ?? 0)
  if (!roomId || Number(current.value?.id) !== roomId) {
    return
  }
  const readyMembers = payload?.readyMembers ?? payload?.ready_members ?? []
  const allReady = Boolean(payload?.allReady ?? payload?.all_ready)
  applyCallReadyState(readyMembers, allReady)
  if (allReady) {
    void initiateVideoCall()
  }
}

function handleCallEndedEvent(payload) {
  const roomId = Number(payload?.roomId ?? payload?.room_id ?? 0)
  if (!roomId || Number(current.value?.id) !== roomId) {
    return
  }
  if (videoChatRef.value?.hangUp) {
    try {
      videoChatRef.value.hangUp()
    } catch (error) {
      console.warn('Failed to hang up call from event', error)
    }
  }
  callActive.value = false
  resetCallHandshake()
  hideVideoPane()
}

function bubbleClass(message) {
  const classes = []
  if (message.contentType && message.contentType !== 'TEXT') {
    classes.push('attachment-bubble')
  } else if (message.me) {
    classes.push('bg-primary', 'text-white', 'text-body-2')
  } else {
    classes.push('bg-grey-lighten-4', 'text-body-2')
  }
  return classes
}

function formatTime(isoString) {
  if (!isoString) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  const date = new Date(isoString)
  if (Number.isNaN(date.getTime())) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

async function translateMessage(message) {
  if (!message || message.translating || message.translation || message.contentType !== 'TEXT') return

  message.translating = true
  try {
    const targetLang = auth.user?.languageCode || 'en'
    message.translation = await translate(message.text, targetLang)
  } catch (error) {
    console.error('문장을 번역하지 못했습니다.', error)
  } finally {
    message.translating = false
  }
}

function saveWord(message) {
  if (!message?.translation || message.contentType !== 'TEXT') return
  vocabularyStore.addWord(message.text, message.translation)
}

function onCompositionStart () {
  isComposing.value = true
}

function onCompositionEnd () {
  isComposing.value = false
}

function handleEnterKey(event) {
  if (!event) return
  if (event.shiftKey) {
    return
  }
  if (event.isComposing || isComposing.value) {
    return
  }
  event.preventDefault()
  void send()
}

async function send() {
  if (isComposing.value) {
    return
  }
  const message = draft.value.trim()
  if (!message) return

  const roomKey = current.value?.id
  if (!roomKey) {
    alert('채팅방이 선택되지 않았습니다.')
    return
  }

  if (!realtimeClient.value?.isConnected()) {
    await connectRealtime()
    if (!realtimeClient.value?.isConnected()) {
      console.warn('WebSocket not connected. Message was not sent.')
      return
    }
  }

  try {
    realtimeClient.value.send({
      type: 'CHAT',
      roomId: Number(roomKey),
      content: message
    })
    draft.value = ''
    const roomEntry = findRoomById(roomKey)
    if (roomEntry) {
      roomEntry.last = message
    }
  } catch (error) {
    console.error('Failed to send chat message', error)
  }
}

function triggerFilePicker() {
  if (!current.value?.id) return
  fileInput.value?.click()
}

async function downloadAttachment(message) {
  if (!message?.fileUrl) return
  const normalizedUrl = normalizeAttachmentUrl(message.fileUrl)
  if (!normalizedUrl) {
    alert('다운로드할 파일 경로를 확인할 수 없습니다.')
    return
  }

  try {
    const response = await api.get(normalizedUrl, {
      responseType: 'blob',
      skipSnakifyParams: true,
    })
    const blobUrl = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = message.fileName || 'attachment'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(blobUrl)
  } catch (error) {
    console.error('[chat] Failed to download attachment', error)
    alert('파일을 다운로드하지 못했습니다. 잠시 후 다시 시도하세요.')
  }
}

function openGroupDialog() {
  resetGroupForm()
  createGroupDialog.value = true
}

function closeGroupDialog() {
  createGroupDialog.value = false
}

function resetGroupForm() {
  groupForm.name = ''
  groupForm.members = []
}

async function submitGroupCreation() {
  if (groupCreationLoading.value) return

  const members = Array.from(new Set(groupForm.members.map((value) => Number(value))))
    .filter((value) => Number.isFinite(value))

  if (groupForm.members.length !== members.length || !groupForm.members.every((value, index) => Number(value) === members[index])) {
    groupForm.members = members
  }

  if (!members.length) {
    alert('초대할 친구를 선택해주세요.')
    return
  }

  if (members.length > maxAdditionalGroupMembers) {
    alert(`그룹 채팅은 최대 ${maxAdditionalGroupMembers + 1}명까지 참여할 수 있습니다.`)
    groupForm.members = members.slice(0, maxAdditionalGroupMembers)
    return
  }

  groupCreationLoading.value = true
  try {
    const payload = {
      name: groupForm.name?.trim() || undefined,
      memberUserPids: members,
    }
    const { data } = await api.post('/rooms/group', payload)
    const entry = normalizeRoomDetail(data)
    const persisted = addOrUpdateRoom(entry)
    groups.value = [persisted, ...groups.value.filter((room) => room.id !== persisted.id)]
    await openChat(persisted)
    createGroupDialog.value = false
  } catch (error) {
    console.error('[chat] Failed to create group room', error)
    alert('그룹 채팅방을 생성하지 못했습니다: ' + (error?.response?.data?.message || error?.message || '알 수 없는 오류'))
  } finally {
    groupCreationLoading.value = false
  }
}

function inviteParticipant() {
  if (!isGroup.value || !current.value?.id) return

  const group = groups.value.find((room) => room.id === current.value.id)
  if (!group) return
  if (group.participants.length >= 4) {
    alert('최대 4명까지 초대할 수 있습니다.')
    return
  }

  const name = prompt('초대할 사용자의 이름을 입력하세요:')
  if (name) {
    group.participants.push(name)
  }
}

async function resolveParticipantLogins(roomId) {
  const room = await ensureRoomExists(roomId)
  return room.participantLogins?.filter(Boolean) ?? []
}

async function startVideoCall() {
  if (!current.value?.id) {
    alert('채팅방이 선택되지 않았습니다.')
    return
  }
  if (callRequestInFlight.value || callStartInProgress.value) {
    return
  }

  const paneReady = await ensureVideoPaneReady()
  if (!paneReady) {
    alert('영상 통화 화면을 준비하지 못했습니다.')
    return
  }

  callRequestInFlight.value = true
  try {
    const { data } = await api.post(`/rooms/${current.value.id}/call/ready`)
    const readyMembers = Array.isArray(data?.readyMembers)
      ? [...data.readyMembers]
      : []
    const myLogin = resolveClientIdentity(auth)
    if (myLogin && !readyMembers.some((login) => login && String(login).toLowerCase() === String(myLogin).toLowerCase())) {
      readyMembers.push(myLogin)
    }
    applyCallReadyState(readyMembers, Boolean(data?.allReady))
    if (data?.allReady) {
      await initiateVideoCall()
    }
  } catch (error) {
    console.error('[chat] Failed to mark call ready', error)
    alert('영상 통화를 준비하지 못했습니다.')
    resetCallHandshake()
  } finally {
    callRequestInFlight.value = false
  }
}

function hangUpCall() {
  if (videoChatRef.value?.hangUp) {
    try {
      videoChatRef.value.hangUp()
    } catch (error) {
      console.warn('Failed to hang up call', error)
    }
  }
  callActive.value = false
  resetCallHandshake()
  hideVideoPane()
  if (!current.value?.id) {
    return
  }
  void api.post(`/rooms/${current.value.id}/call/hangup`).catch((error) => {
    console.warn('[chat] Failed to notify call hangup', error)
  })
}

watch(messages, () => scrollToBottom())
</script>

<style scoped>
.chat-page {
  height: calc(100vh - var(--v-layout-top));
}

.chat-sidebar {
  background: #fff;
  border-right: 1px solid #f1e7c6;
  height: 100%;
}

.follow-stats {
  border-bottom: 1px solid #f5f5f5;
}

.follow-stats .v-chip {
  font-weight: 500;
}

.chat-main {
  background: linear-gradient(180deg, #fffdf4 0%, #ffffff 55%, #f7fbff 100%);
  border-left: 1px solid #e3f1e5;
  height: 100%;
}

.chat-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px;
}

.video-pane {
  flex: 1 1 55%;
  max-width: 55%;
  display: flex;
  flex-direction: column;
  min-height: 420px;
}

.video-pane :deep(.video-chat) {
  flex: 1;
  width: 100%;
}

.video-placeholder {
  flex: 1;
  width: 100%;
  border: 2px dashed rgba(255, 169, 0, 0.35);
  border-radius: 16px;
  background: rgba(255, 248, 214, 0.35);
  padding: 32px 24px;
  color: #8c8676;
}

.chat-messages {
  background: #fff;
  flex: 1 1 45%;
  max-width: 45%;
  display: flex;
  flex-direction: column;
  min-height: 420px;
}

.chat-messages__list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: auto;
}

.chat-input {
  border-top: 1px solid #f4efe0;
  background: #fff8d6;
}

.messenger-pill {
  border-radius: 999px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: -0.2px;
  padding: 0 18px;
  height: 44px;
  min-width: 44px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.messenger-pill :deep(.v-btn__content) {
  gap: 6px;
}

.messenger-pill:not([disabled]):hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 18px rgba(0, 0, 0, 0.12);
}

.messenger-pill[disabled] {
  filter: grayscale(0.35);
  box-shadow: none;
}

.toolbar-btn {
  padding-inline: 16px;
}

.video-call-btn {
  background: linear-gradient(135deg, #fff3a0, #ffe066);
  color: #3a2a08;
  box-shadow: 0 8px 18px rgba(255, 215, 86, 0.35);
}

.video-call-btn:not([disabled]):hover {
  box-shadow: 0 14px 24px rgba(255, 215, 86, 0.45);
}

.video-call-btn[disabled] {
  background: #f5f5f5;
  color: #bdbdbd;
}

.chat-action-btn {
  padding-inline: 18px;
}

.file-upload-btn {
  background: linear-gradient(135deg, #ffeb99, #ffd43b);
  color: #3b2f10;
  min-width: 48px;
  padding-inline: 14px;
  box-shadow: 0 8px 18px rgba(255, 212, 59, 0.32);
}

.file-upload-btn:not([disabled]):hover {
  box-shadow: 0 12px 24px rgba(255, 212, 59, 0.42);
}

.send-btn {
  background: linear-gradient(135deg, #06c755, #00a884);
  color: #ffffff;
  box-shadow: 0 12px 24px rgba(0, 168, 132, 0.35);
}

.send-btn:not([disabled]):hover {
  box-shadow: 0 16px 28px rgba(0, 168, 132, 0.45);
}

.send-btn[disabled] {
  background: #c8f5df;
  color: #5ca98a;
}

.message-wrapper {
  max-width: min(420px, 80%);
  display: flex;
  flex-direction: column;
}

.message-bubble {
  border-radius: 16px;
  padding: 12px;
  word-break: break-word;
  white-space: pre-wrap;
}

.attachment-bubble {
  background: transparent;
  padding: 0;
}

.message-image {
  max-width: 100%;
  border-radius: 12px;
  display: block;
}

.file-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  color: inherit;
}

.message-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.text-right .message-tools {
  justify-content: flex-end;
}

.cursor-pointer {
  cursor: pointer;
}

.ml-auto {
  margin-left: auto;
}
</style>

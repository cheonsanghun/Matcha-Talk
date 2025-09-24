import { defineStore } from 'pinia'
import api from '../services/api'

const STORAGE_KEY = 'friends'

function normalizeMessage(raw = {}) {
  const messageId = raw.messageId ?? raw.id ?? null
  const sentAt = raw.sentAt ?? null
  const sizeValue = raw.sizeBytes ?? raw.size_bytes
  let normalizedSize = null
  if (typeof sizeValue === 'number') {
    normalizedSize = Number.isFinite(sizeValue) ? sizeValue : null
  } else if (sizeValue != null) {
    const parsed = Number(sizeValue)
    normalizedSize = Number.isFinite(parsed) ? parsed : null
  }

  const base = {
    id:
      messageId ??
      `${raw.roomId ?? ''}-${sentAt ?? Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    messageId: messageId,
    roomId: raw.roomId ?? null,
    senderLoginId: raw.senderLoginId ?? raw.sender_login_id ?? null,
    senderNickName: raw.senderNickName ?? raw.sender_nick_name ?? raw.sender ?? '',
    content: raw.content ?? raw.text ?? '',
    translatedContent: raw.translatedContent ?? raw.translated_content ?? '',
    contentType: (raw.contentType ?? raw.content_type ?? (raw.fileUrl ? 'FILE' : 'TEXT') ?? 'TEXT').toString().toUpperCase(),
    fileName: raw.fileName ?? raw.file_name ?? '',
    fileUrl: raw.fileUrl ?? raw.file_url ?? '',
    mimeType: raw.mimeType ?? raw.mime_type ?? '',
    sizeBytes: normalizedSize,
    sentAt,
  }

  return base
}

function normalizeFriend(raw = {}) {
  if (typeof raw === 'string') {
    return normalizeFriend({ partnerNickName: raw })
  }

  const messages = Array.isArray(raw.messages) ? raw.messages.map(normalizeMessage) : []
  messages.sort((a, b) => {
    const left = a.sentAt ? new Date(a.sentAt).getTime() : 0
    const right = b.sentAt ? new Date(b.sentAt).getTime() : 0
    return left - right
  })

  const partnerNickName = raw.partnerNickName ?? raw.partner_nick_name ?? raw.name ?? ''
  const partnerLoginId = raw.partnerLoginId ?? raw.partner_login_id ?? null
  const roomId = raw.roomId ?? raw.room_id ?? null

  let identifier = raw.id ?? roomId ?? partnerLoginId
  if (!identifier || (typeof identifier === 'string' && !identifier.trim())) {
    identifier = partnerNickName && partnerNickName.trim()
      ? partnerNickName
      : `friend-${Math.random().toString(36).slice(2, 10)}`
  }

  return {
    id: identifier,
    followRequestId: raw.followRequestId ?? raw.follow_request_id ?? null,
    roomId,
    partnerLoginId,
    partnerNickName,
    acceptedAt: raw.acceptedAt ?? raw.accepted_at ?? null,
    messages,
  }
}

function summaryToFriend(summary = {}) {
  const friend = {
    followRequestId: summary.followRequestId ?? null,
    roomId: summary.roomId ?? null,
    partnerLoginId: summary.partnerLoginId ?? null,
    partnerNickName: summary.partnerNickName ?? summary.partnerNickname ?? '',
    acceptedAt: summary.acceptedAt ?? null,
    messages: Array.isArray(summary.recentMessages) ? summary.recentMessages : [],
  }
  return normalizeFriend(friend)
}

function loadInitialState() {
  if (typeof localStorage === 'undefined') {
    return []
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed.map(normalizeFriend)
  } catch (error) {
    console.warn('친구 목록 복원 실패', error)
    return []
  }
}

function persist(list) {
  if (typeof localStorage === 'undefined') {
    return
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch (error) {
    console.warn('친구 목록 저장 실패', error)
  }
}

export const useFriendsStore = defineStore('friends', {
  state: () => ({
    list: loadInitialState(),
    loading: false,
  }),
  getters: {
    byRoomId: (state) =>
      state.list.reduce((acc, friend) => {
        if (friend.roomId != null) {
          acc[friend.roomId] = friend
        }
        return acc
      }, {}),
  },
  actions: {
    setAll(friends = []) {
      this.list = friends.map(normalizeFriend)
      persist(this.list)
    },
    upsert(friend) {
      const normalized = normalizeFriend(friend)
      const index = this.list.findIndex((item) => {
        if (normalized.roomId != null && item.roomId != null) {
          return item.roomId === normalized.roomId
        }
        if (normalized.partnerLoginId && item.partnerLoginId) {
          return item.partnerLoginId === normalized.partnerLoginId
        }
        if (normalized.partnerNickName && item.partnerNickName) {
          return item.partnerNickName === normalized.partnerNickName
        }
        return false
      })
      if (index >= 0) {
        const existing = this.list[index]
        const mergedMessages = normalized.messages.length ? normalized.messages : existing.messages
        this.list.splice(index, 1, {
          ...existing,
          ...normalized,
          messages: mergedMessages,
        })
      } else {
        this.list.push(normalized)
      }
      persist(this.list)
    },
    add(friend) {
      this.upsert(friend)
    },
    remove(identifier) {
      if (!identifier) {
        return
      }
      if (typeof identifier === 'object') {
        const { roomId, partnerLoginId, partnerNickName } = identifier
        this.list = this.list.filter((item) => {
          if (roomId != null && item.roomId === roomId) {
            return false
          }
          if (partnerLoginId && item.partnerLoginId === partnerLoginId) {
            return false
          }
          if (partnerNickName && item.partnerNickName === partnerNickName) {
            return false
          }
          return true
        })
      } else {
        this.list = this.list.filter((item) => item.partnerNickName !== identifier)
      }
      persist(this.list)
    },
    removeByRoom(roomId) {
      if (roomId == null) {
        return
      }
      this.list = this.list.filter((item) => item.roomId !== roomId)
      persist(this.list)
    },
    clear() {
      this.list = []
      persist(this.list)
    },
    async refreshFromServer() {
      try {
        this.loading = true
        const { data } = await api.get('/follows/accepted')
        if (Array.isArray(data)) {
          this.setAll(data.map(summaryToFriend))
        }
      } catch (error) {
        console.error('친구 목록 갱신 실패', error)
        throw error
      } finally {
        this.loading = false
      }
    },
  },
})

import { defineStore } from 'pinia'

const STORAGE_KEY = 'match-bootstrap'

function hasWindow () {
  return typeof window !== 'undefined'
}

function readFromStorage () {
  if (!hasWindow()) return null
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? normalizeBootstrap(JSON.parse(raw)) : null
  } catch (error) {
    console.warn('Failed to parse stored match bootstrap payload', error)
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

function writeToStorage (payload) {
  if (!hasWindow()) return
  if (!payload) {
    sessionStorage.removeItem(STORAGE_KEY)
    return
  }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  } catch (error) {
    console.warn('Failed to persist match bootstrap payload', error)
  }
}

function toNumberOrNull (value) {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function normalizeBootstrap (payload) {
  if (!payload || typeof payload !== 'object') {
    return null
  }

  const partnerName = payload.partnerName || payload.partnerNickName || payload.partnerNickname || ''
  const partnerLoginId = payload.partnerLoginId ?? payload.partnerLoginID ?? null
  const partnerUserPid = toNumberOrNull(payload.partnerUserPid ?? payload.partnerUserId ?? payload.partnerPid)
  const rawRoomId = payload.roomId ?? payload.roomID ?? payload.matchRoomId

  const handshakeSource = typeof payload.handshake === 'object' && payload.handshake
    ? payload.handshake
    : {}

  const handshake = {
    myRequestId: toNumberOrNull(payload.myRequestId ?? payload.requestId ?? handshakeSource.myRequestId),
    partnerRequestId: toNumberOrNull(payload.partnerRequestId ?? handshakeSource.partnerRequestId),
    handshakeKey: payload.handshakeKey ?? handshakeSource.handshakeKey ?? null,
    expiresAt: payload.expiresAt ?? handshakeSource.expiresAt ?? null,
    status: (payload.status || handshakeSource.status || '').toString().toUpperCase() || null,
    roomId: toNumberOrNull(handshakeSource.roomId ?? rawRoomId),
  }

  const roomId = toNumberOrNull(rawRoomId ?? handshake.roomId)
  const status = handshake.status

  const computedHandshakeReady = status === 'MATCHED' && !roomId
  const computedChatReady = roomId != null && (status === 'CONFIRMED' || status === 'ARCHIVED' || status === 'MATCHED' || payload.chatReady === true)

  const followRelationId = toNumberOrNull(payload.followRelationId ?? payload.followId ?? handshakeSource.followRelationId)
  const followStatus = payload.followStatus || handshakeSource.followStatus || null

  return {
    matchFound: payload.matchFound ?? (computedHandshakeReady || computedChatReady),
    handshakeReady: payload.handshakeReady ?? computedHandshakeReady,
    chatReady: payload.chatReady ?? computedChatReady,
    partnerName,
    partnerLoginId: partnerLoginId || null,
    partnerUserPid,
    roomId,
    status,
    handshake,
    followStatus,
    followRelationId,
  }
}

function mergeBootstrapPayload (current, patch) {
  if (!current) {
    return normalizeBootstrap(patch)
  }
  const merged = { ...current, ...patch }
  merged.partnerName = patch?.partnerName || current.partnerName || ''
  merged.partnerLoginId = patch?.partnerLoginId ?? current.partnerLoginId ?? null
  merged.partnerUserPid = patch?.partnerUserPid ?? current.partnerUserPid ?? null
  merged.roomId = patch?.roomId ?? current.roomId ?? null
  merged.status = patch?.status ?? current.status ?? null
  merged.matchFound = patch?.matchFound ?? current.matchFound ?? false
  merged.handshakeReady = patch?.handshakeReady ?? current.handshakeReady ?? false
  merged.chatReady = patch?.chatReady ?? current.chatReady ?? false
  merged.followStatus = patch?.followStatus ?? current.followStatus ?? null
  merged.followRelationId = patch?.followRelationId ?? current.followRelationId ?? null

  const nextHandshake = {
    ...(current.handshake || {}),
    ...(patch?.handshake || {}),
  }
  if (patch?.myRequestId !== undefined) {
    nextHandshake.myRequestId = patch.myRequestId
  }
  if (patch?.partnerRequestId !== undefined) {
    nextHandshake.partnerRequestId = patch.partnerRequestId
  }
  if (patch?.handshakeKey !== undefined) {
    nextHandshake.handshakeKey = patch.handshakeKey
  }
  if (patch?.expiresAt !== undefined) {
    nextHandshake.expiresAt = patch.expiresAt
  }
  if (patch?.status !== undefined) {
    nextHandshake.status = patch.status
  }
  if (patch?.roomId !== undefined) {
    nextHandshake.roomId = patch.roomId
  }
  merged.handshake = nextHandshake

  return normalizeBootstrap(merged)
}

export const useMatchStore = defineStore('match', {
  state: () => ({
    bootstrap: readFromStorage(),
  }),

  actions: {
    setBootstrap (payload) {
      this.bootstrap = normalizeBootstrap(payload)
      writeToStorage(this.bootstrap)
    },
    mergeBootstrap (payload) {
      this.bootstrap = mergeBootstrapPayload(this.bootstrap, payload)
      writeToStorage(this.bootstrap)
    },
    updateHandshake (payload) {
      const next = { handshake: payload }
      this.mergeBootstrap(next)
    },
    clearBootstrap () {
      this.bootstrap = null
      writeToStorage(null)
    },
  },
})

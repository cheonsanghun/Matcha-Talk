import { onUnmounted, ref } from 'vue'
import api from '../services/api'
import { createRealtimeClient } from '../services/ws'
import { resolveClientIdentity } from '../utils/identity'

export function normalizeAttachmentUrl (url) {
  if (!url) return null

  const stringUrl = String(url)
  if (/^https?:\/\//i.test(stringUrl)) {
    return stringUrl
  }

  const base = api?.defaults?.baseURL || ''
  let resolvedBase = base

  if (typeof window !== 'undefined') {
    try {
      const origin = window.location?.origin || ''
      resolvedBase = base ? new URL(base, origin).href : origin
    } catch (error) {
      console.warn('[chat] Failed to resolve attachment base URL', error)
      resolvedBase = base || ''
    }
  }

  try {
    return new URL(stringUrl, resolvedBase || undefined).href
  } catch (error) {
    console.warn('[chat] Failed to normalize attachment URL', error)
    return stringUrl
  }
}

export function createIncomingMessageHandler ({ auth, ensureConversation, ensureRoomExists, scrollToBottom, formatTime }) {
  if (!auth) {
    throw new Error('auth store is required to handle incoming messages')
  }
  if (typeof ensureConversation !== 'function') {
    throw new Error('ensureConversation callback is required')
  }
  if (typeof ensureRoomExists !== 'function') {
    throw new Error('ensureRoomExists callback is required')
  }
  const timeFormatter = typeof formatTime === 'function'
    ? formatTime
    : () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

  return async function handleIncomingMessage (payload) {
    if (!payload) return

    const roomKey = payload.roomId ?? payload.room_id
    if (!roomKey) return

    const content = payload.content ?? ''
    const senderNickname = payload.senderNickName ?? payload.senderNickname ?? null
    const senderLoginId = payload.senderLoginId ??
      payload.senderLoginID ??
      payload.sender_login_id ??
      payload.senderLogin ??
      null
    const messageId = payload.messageId ?? payload.message_id ?? null
    if (!senderLoginId) {
      console.warn('[chat] Received message without senderLoginId', payload)
    }
    const messagesForRoom = ensureConversation(roomKey)
    if (messageId && messagesForRoom.some((existing) => existing?.id === messageId)) {
      return
    }
    const myLoginId = resolveClientIdentity(auth)
    const normalizedMyLogin = myLoginId ? String(myLoginId).toLowerCase() : null
    const normalizedSenderLogin = senderLoginId ? String(senderLoginId).toLowerCase() : null
    const isMine = Boolean(normalizedMyLogin && normalizedSenderLogin && normalizedMyLogin === normalizedSenderLogin)
    const displayName = senderNickname || senderLoginId || '상대방'

    const message = {
      id: messageId || `${roomKey}-${Date.now()}-${messagesForRoom.length}`,
      text: content,
      time: timeFormatter(payload.sentAt),
      sender: displayName,
      senderLoginId,
      me: isMine,
      contentType: (payload.contentType || 'TEXT').toUpperCase(),
      fileName: payload.fileName || null,
      fileUrl: normalizeAttachmentUrl(payload.fileUrl || payload.file_url || null),
      mimeType: payload.mimeType || null,
      sizeBytes: payload.sizeBytes || null,
      translation: null,
      translating: false,
      sentAt: payload.sentAt ?? null,
    }

    messagesForRoom.push(message)

    const roomEntry = await ensureRoomExists(roomKey, displayName)
    if (roomEntry && Object.prototype.hasOwnProperty.call(roomEntry, 'last')) {
      if (message.contentType === 'TEXT') {
        roomEntry.last = content
      } else {
        roomEntry.last = message.fileName || content || '[첨부파일]'
      }
    }

    if (typeof scrollToBottom === 'function') {
      scrollToBottom(roomKey)
    }
  }
}

export function createFileSelectHandler ({ getRoomId, onBeforeUpload, onAfterUpload, onUploadError } = {}) {
  if (typeof getRoomId !== 'function') {
    throw new Error('getRoomId callback is required for file selection handler')
  }

  return async function handleFileSelect (event) {
    const target = event?.target
    const files = target?.files || []
    const [file] = files

    if (target) {
      target.value = ''
    }

    if (!file) return

    const roomId = getRoomId()
    if (!roomId) {
      alert('채팅방이 선택되지 않았습니다.')
      return
    }

    try {
      if (typeof onBeforeUpload === 'function') {
        onBeforeUpload(file)
      }

      const formData = new FormData()
      formData.append('file', file)

      await api.post(`/rooms/${roomId}/attachments`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })

      if (typeof onAfterUpload === 'function') {
        onAfterUpload(file)
      }
    } catch (error) {
      console.error('[chat] 파일 업로드 실패', error)
      if (typeof onUploadError === 'function') {
        onUploadError(error)
      } else {
        alert('파일을 업로드하지 못했습니다. 잠시 후 다시 시도하세요.')
      }
    }
  }
}

export function useRealtimeChatClient (auth, handlers = {}) {
  if (!auth) {
    throw new Error('auth store is required to initialize realtime chat client')
  }

  const realtimeClient = ref(null)
  const isConnecting = ref(false)
  const manualDisconnect = ref(false)
  const reconnectAttempts = ref(0)
  const reconnectTimer = ref(null)
  const teardownHandlers = []
  const maxReconnectAttempts = handlers.maxReconnectAttempts ?? 3

  function clearReconnectTimer () {
    if (reconnectTimer.value) {
      clearTimeout(reconnectTimer.value)
      reconnectTimer.value = null
    }
  }

  function disposeHandlers () {
    while (teardownHandlers.length) {
      const disposer = teardownHandlers.pop()
      try {
        disposer?.()
      } catch (error) {
        console.warn('Failed to dispose realtime handler', error)
      }
    }
  }

  function scheduleReconnect () {
    if (manualDisconnect.value) return
    if (reconnectTimer.value) return
    if (reconnectAttempts.value >= maxReconnectAttempts) {
      console.error('[chat] Max reconnect attempts reached')
      return
    }
    reconnectAttempts.value += 1
    reconnectTimer.value = setTimeout(() => {
      reconnectTimer.value = null
      void connect()
    }, 2000)
    handlers.onReconnectScheduled?.(reconnectAttempts.value)
  }

  function ensureClient () {
    const loginId = resolveClientIdentity(auth)
    if (!loginId) {
      return
    }

    if (!realtimeClient.value) {
      realtimeClient.value = createRealtimeClient({ queryParams: { loginId } })
      const client = realtimeClient.value
      teardownHandlers.push(
        client.onOpen(() => {
          isConnecting.value = false
          reconnectAttempts.value = 0
          handlers.onOpen?.()
        }),
        client.onClose((event) => {
          isConnecting.value = false
          handlers.onClose?.(event)
          if (!manualDisconnect.value) {
            scheduleReconnect()
          }
        }),
        client.onError((event) => {
          handlers.onError?.(event)
        }),
      )

      if (typeof handlers.onChat === 'function') {
        teardownHandlers.push(client.onEvent('chat', handlers.onChat))
      }
      if (typeof handlers.onMatchResult === 'function') {
        teardownHandlers.push(client.onEvent('match-result', handlers.onMatchResult))
      }
      if (handlers.events && typeof handlers.events === 'object') {
        for (const [eventName, callback] of Object.entries(handlers.events)) {
          if (typeof callback === 'function') {
            teardownHandlers.push(client.onEvent(eventName, callback))
          }
        }
      }
    } else {
      realtimeClient.value.setQueryParams({ loginId })
    }
  }

  async function connect () {
    if (isConnecting.value || manualDisconnect.value) return

    ensureClient()

    if (!realtimeClient.value) {
      console.warn('[chat] realtime client could not be initialized')
      return
    }

    isConnecting.value = true
    try {
      await realtimeClient.value.connect()
      handlers.onConnect?.()
    } catch (error) {
      console.error('[chat] Failed to connect realtime channel', error)
      handlers.onConnectError?.(error)
    } finally {
      isConnecting.value = false
    }
  }

  function disconnect () {
    clearReconnectTimer()
    if (realtimeClient.value) {
      try {
        realtimeClient.value.disconnect()
      } catch (error) {
        console.warn('Failed to disconnect realtime client', error)
      }
    }
  }

  onUnmounted(() => {
    manualDisconnect.value = true
    clearReconnectTimer()
    disposeHandlers()
    disconnect()
  })

  return {
    realtimeClient,
    connect,
    disconnect,
    scheduleReconnect,
    manualDisconnect,
    setManualDisconnect (value) {
      manualDisconnect.value = Boolean(value)
      if (manualDisconnect.value) {
        clearReconnectTimer()
      }
    },
    isConnecting,
    reconnectAttempts,
  }
}

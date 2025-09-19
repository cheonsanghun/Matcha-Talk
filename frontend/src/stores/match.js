import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { matchApi } from '../services/api'

const normalizePartner = (payload) => {
  if (!payload) return null
  return {
    loginId: payload.partnerLoginId ?? payload.loginId ?? null,
    nickname: payload.partnerNickName ?? payload.nickName ?? payload.nickname ?? null,
    requestId: payload.partnerRequestId ?? payload.requestId ?? null,
  }
}

export const useMatchStore = defineStore('match', () => {
  const status = ref('IDLE')
  const message = ref('')
  const waitingCount = ref(0)
  const myRequestId = ref(null)
  const partner = ref(null)
  const roomId = ref(null)
  const shouldCreateOffer = ref(false)
  const loading = ref(false)
  const lastEvent = ref(null)

  const isMatched = computed(() => status.value === 'MATCHED')
  const isWaiting = computed(() => status.value === 'WAITING' || status.value === 'ALREADY_WAITING')

  const applyResponse = (payload) => {
    if (!payload) return
    if (payload.state) {
      status.value = payload.state
    }
    myRequestId.value = payload.myRequestId ?? myRequestId.value
    waitingCount.value = payload.waitingCount ?? waitingCount.value
    roomId.value = payload.roomId ?? roomId.value
    shouldCreateOffer.value = Boolean(payload.shouldCreateOffer)
    partner.value = normalizePartner(payload) ?? partner.value
    if (payload.message) {
      message.value = payload.message
    }
  }

  const applyEvent = (event) => {
    if (!event) return
    lastEvent.value = event
    switch (event.eventType) {
      case 'MATCH_FOUND':
        status.value = 'MATCHED'
        partner.value = normalizePartner(event)
        roomId.value = event.roomId ?? roomId.value
        shouldCreateOffer.value = Boolean(event.shouldCreateOffer)
        myRequestId.value = event.myRequestId ?? myRequestId.value
        if (event.message) message.value = event.message
        break
      case 'PARTNER_ACCEPTED':
        if (event.message) message.value = event.message
        break
      case 'PARTNER_DECLINED':
        if (event.message) message.value = event.message
        status.value = 'DECLINED'
        break
      case 'BOTH_CONFIRMED':
        if (event.message) message.value = event.message
        status.value = 'CONFIRMED'
        shouldCreateOffer.value = false
        break
      case 'MATCH_CANCELLED':
        status.value = 'CANCELLED'
        message.value = event.message ?? '매칭이 종료되었습니다.'
        roomId.value = null
        partner.value = null
        break
      default:
        break
    }
  }

  const startMatch = async (request) => {
    loading.value = true
    message.value = ''
    try {
      const response = await matchApi.start({
        choiceGender: request.choiceGender,
        minAge: request.minAge,
        maxAge: request.maxAge,
        regionCode: request.regionCode,
        interests: request.interests,
      })
      applyResponse(response)
      return response
    } catch (error) {
      message.value = error?.message ?? '매칭 요청 중 오류가 발생했습니다.'
      throw error
    } finally {
      loading.value = false
    }
  }

  const acceptMatch = async () => {
    if (!myRequestId.value) {
      throw new Error('매칭 요청 ID가 없습니다.')
    }
    loading.value = true
    try {
      const response = await matchApi.accept(myRequestId.value)
      applyResponse(response)
      return response
    } catch (error) {
      message.value = error?.message ?? '수락 처리 중 오류가 발생했습니다.'
      throw error
    } finally {
      loading.value = false
    }
  }

  const declineMatch = async () => {
    if (!myRequestId.value) {
      throw new Error('매칭 요청 ID가 없습니다.')
    }
    loading.value = true
    try {
      const response = await matchApi.decline(myRequestId.value)
      applyResponse(response)
      status.value = 'DECLINED'
      return response
    } catch (error) {
      message.value = error?.message ?? '거절 처리 중 오류가 발생했습니다.'
      throw error
    } finally {
      loading.value = false
    }
  }

  const reset = () => {
    status.value = 'IDLE'
    message.value = ''
    waitingCount.value = 0
    myRequestId.value = null
    partner.value = null
    roomId.value = null
    shouldCreateOffer.value = false
    lastEvent.value = null
  }

  return {
    status,
    message,
    waitingCount,
    myRequestId,
    partner,
    roomId,
    shouldCreateOffer,
    loading,
    lastEvent,
    isMatched,
    isWaiting,
    startMatch,
    acceptMatch,
    declineMatch,
    applyEvent,
    applyResponse,
    reset,
  }
})

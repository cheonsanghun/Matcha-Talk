import { defineStore } from 'pinia'

export const useMatchStore = defineStore('match', {
  state: () => ({
    state: null,
    requestId: null,
    partnerRequestId: null,
    roomId: null,
    partnerLoginId: null,
    partnerNickName: null,
    partnerUserPid: null,
    waitingCount: 0,
    shouldCreateOffer: false,
    statusMessage: '',
    myDecision: null,
    partnerDecision: null,
    bothConfirmed: false,
    sessionClosed: false,
  }),
  getters: {
    isMatched: (state) => state.state === 'MATCHED',
    isWaiting: (state) => state.state === 'WAITING' || state.state === 'ALREADY_WAITING',
  },
  actions: {
    setFromStartResponse(payload = {}) {
      this.state = payload.state || null
      this.requestId = payload.myRequestId ?? null
      this.partnerRequestId = payload.partnerRequestId ?? null
      this.roomId = payload.roomId ?? null
      this.partnerLoginId = payload.partnerLoginId ?? null
      this.partnerNickName = payload.partnerNickName ?? null
      this.partnerUserPid = payload.partnerUserPid ?? null
      this.waitingCount = payload.waitingCount ?? 0
      this.shouldCreateOffer = !!payload.shouldCreateOffer
      this.statusMessage = payload.message || ''
      if (this.state !== 'MATCHED') {
        this.myDecision = null
        this.partnerDecision = null
        this.bothConfirmed = false
        this.sessionClosed = false
      }
    },
    applyMatchEvent(event = {}) {
      if (event.message) {
        this.statusMessage = event.message
      }

      switch (event.eventType) {
        case 'MATCH_FOUND':
          this.state = 'MATCHED'
          this.requestId = event.myRequestId ?? this.requestId
          this.partnerRequestId = event.partnerRequestId ?? this.partnerRequestId
          this.roomId = event.roomId ?? this.roomId
          this.partnerLoginId = event.partnerLoginId ?? this.partnerLoginId
          this.partnerNickName = event.partnerNickName ?? this.partnerNickName
          this.partnerUserPid = event.partnerUserPid ?? this.partnerUserPid
          this.shouldCreateOffer = !!event.shouldCreateOffer
          this.myDecision = null
          this.partnerDecision = null
          this.bothConfirmed = false
          this.sessionClosed = false
          break
        case 'PARTNER_ACCEPTED':
          this.partnerDecision = 'ACCEPTED'
          break
        case 'PARTNER_DECLINED':
          this.partnerDecision = 'DECLINED'
          this.sessionClosed = true
          break
        case 'BOTH_CONFIRMED':
          this.bothConfirmed = true
          if (!this.myDecision) this.myDecision = 'ACCEPTED'
          if (!this.partnerDecision) this.partnerDecision = 'ACCEPTED'
          this.sessionClosed = false
          break
        case 'MATCH_CANCELLED':
          this.sessionClosed = true
          break
      }
    },
    setMyDecision(decision, message = '', bothAccepted = false) {
      this.myDecision = decision
      if (message) {
        this.statusMessage = message
      }
      if (decision === 'DECLINED') {
        this.sessionClosed = true
      }
      if (bothAccepted) {
        this.bothConfirmed = true
        if (!this.partnerDecision) {
          this.partnerDecision = 'ACCEPTED'
        }
      }
    },
    reset() {
      this.state = null
      this.requestId = null
      this.partnerRequestId = null
      this.roomId = null
      this.partnerLoginId = null
      this.partnerNickName = null
      this.partnerUserPid = null
      this.waitingCount = 0
      this.shouldCreateOffer = false
      this.statusMessage = ''
      this.myDecision = null
      this.partnerDecision = null
      this.bothConfirmed = false
      this.sessionClosed = false
    },
  },
})

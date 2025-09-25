// src/stores/auth.js
import { defineStore } from 'pinia'
import api from '../services/api' // axios 인스턴스

function safeParse(json, fallback = null) {
  try { return JSON.parse(json) } catch { return fallback }
}
function hasWindow() { return typeof window !== 'undefined' }

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: hasWindow() ? localStorage.getItem('token') : null,
    user : hasWindow() ? safeParse(localStorage.getItem('user')) : null,
  }),

  getters: {
    // 토큰 또는 사용자 정보만 있어도 로그인으로 간주
    isAuthenticated: (s) => !!(s.token || s.user),

    // 다양한 키 네이밍 대응해서 PID 뽑기
    userPid: (s) =>
      s.user?.userPid ??
      s.user?.user_pid ??
      s.user?.id ??
      s.user?.pid ??
      s.user?.user?.userPid ??
      s.user?.user?.user_pid ??
      null,

    // 로그인 아이디·역할 보정
    loginId: (s) => s.user?.loginId ?? s.user?.login_id ?? s.user?.username ?? null,
    role:    (s) => s.user?.roleName ?? s.user?.rolename ?? s.user?.role ?? null,

    // ✅ 타입 표기 없는 JS 메서드
    isAdmin() {
      const role = this.role
      const loginId = this.loginId
      return role === 'ROLE_ADMIN' || loginId === 'admin'
    },
  },

  actions: {
    _persist() {
      if (!hasWindow()) return
      if (this.token) localStorage.setItem('token', this.token)
      else localStorage.removeItem('token')

      if (this.user) localStorage.setItem('user', JSON.stringify(this.user))
      else localStorage.removeItem('user')
    },

    setToken(token) { this.token = token ?? null; this._persist() },
    setUser(user)   { this.user  = user  ?? null; this._persist() },

    // 응답 형태 유연 처리 (token/user, accessToken/data 등)
    login(payload = {}) {
      const token = payload.token ?? payload.accessToken ?? payload.access_token ?? null
      const user  = payload.user  ?? payload.profile     ?? payload.data          ?? null
      this.token = token
      this.user  = user
      this._persist()
    },

    logout() {
      this.token = null
      this.user  = null
      this._persist()
    },

    initializeFromStorage() {
      if (!hasWindow()) return
      this.token = localStorage.getItem('token')
      this.user  = safeParse(localStorage.getItem('user'))
    },

    // 토큰만 있고 userPid가 비면 내 정보로 보강
    async hydrateMeIfNeeded() {
      try {
        if (!this.token || this.userPid) return
        const { data } = await api.get('/users/profile') // 프로젝트에 맞게 필요시 경로 변경
        this.user = data?.user ?? data?.data ?? data
        this._persist()
      } catch (e) {
        console.warn('hydrateMeIfNeeded failed:', e?.response?.data || e?.message)
      }
    },
  },
})


// src/stores/auth.js
import { defineStore } from 'pinia'
import api from '../services/api' // axios 인스턴스

function safeParse(json, fallback = null) {
  try { return JSON.parse(json) } catch { return fallback }
}
function hasWindow() { return typeof window !== 'undefined' }

function ensureUserPid(user) {
  if (!user) return null

  if (user.userPid != null) return user

  const fallbackPid =
    user.user_pid ??
    user.id ??
    user.pid ??
    user.user?.userPid ??
    user.user?.user_pid ??
    null

  if (fallbackPid != null) {
    return { ...user, userPid: fallbackPid }
  }

  if (hasWindow()) {
    console.info(
      '[auth] Cached session did not include userPid. Clearing stale cache. ',
      'If the issue persists after deployment, manually remove the "user" entry in localStorage and log in again.',
    )
  }

  return null
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: hasWindow() ? safeParse(localStorage.getItem('user')) : null,
  }),

  getters: {
    // 사용자 정보가 있으면 로그인으로 간주
    isAuthenticated: (s) => !!s.user,

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
      localStorage.removeItem('token')
      if (this.user) localStorage.setItem('user', JSON.stringify(this.user))
      else localStorage.removeItem('user')
    },

    setUser(user) {
      this.user = ensureUserPid(user)
      this._persist()
    },

    setSession(user) {
      this.user = ensureUserPid(user)
      this._persist()
    },

    // 응답 형태 유연 처리 (user, profile, data 등)
    login(payload = {}) {
      const user =
        payload.user ??
        payload.profile ??
        payload.data ??
        payload ??
        null
      this.user = ensureUserPid(user)
      this._persist()
    },

    logout() {
      this.user = null
      this._persist()
    },

    initializeFromStorage() {
      if (!hasWindow()) return
      localStorage.removeItem('token')
      const storedUser = safeParse(localStorage.getItem('user'))
      const normalized = ensureUserPid(storedUser)

      if (!normalized && storedUser) {
        localStorage.removeItem('user')
      }

      this.user = normalized
    },

    // 사용자 정보가 없으면 세션 기반 API로 조회해 보강
    async hydrateMeIfNeeded() {
      try {
        if (this.userPid) return
        if (this.user) return
        const { data } = await api.get('/users/profile') // 프로젝트에 맞게 필요시 경로 변경
        const nextUser = data?.user ?? data?.data ?? data
        this.user = ensureUserPid(nextUser)
        this._persist()
      } catch (e) {
        console.warn('hydrateMeIfNeeded failed:', e?.response?.data || e?.message)
      }
    },
  },
})

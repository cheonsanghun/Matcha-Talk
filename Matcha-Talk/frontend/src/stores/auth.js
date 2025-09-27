// src/stores/auth.js
import { defineStore } from 'pinia'
import api from '../services/api'

function safeParse(json, fallback = null) {
  try { return JSON.parse(json) } catch { return fallback }
}
function hasWindow() { return typeof window !== 'undefined' }

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: hasWindow() ? safeParse(localStorage.getItem('user')) : null,
  }),

  getters: {
    isAuthenticated: (s) => !!s.user,

    userPid: (s) =>
      s.user?.userPid ??
      s.user?.user_pid ??
      s.user?.id ??
      s.user?.pid ??
      s.user?.user?.userPid ??
      s.user?.user?.user_pid ??
      null,

    loginId: (s) =>
      s.user?.loginId ??
      s.user?.login_id ??
      s.user?.username ??
      s.user?.user?.loginId ??
      null,

    role: (s) => s.user?.roleName ?? s.user?.rolename ?? s.user?.role ?? null,

    isAdmin() {
      const role = this.role
      const loginId = this.loginId
      return role === 'ROLE_ADMIN' || loginId === 'admin'
    },
  },

  actions: {
    _persist() {
      if (!hasWindow()) return
      if (this.user) localStorage.setItem('user', JSON.stringify(this.user))
      else localStorage.removeItem('user')
    },

    setSession(user) {
      this.user = user ?? null
      this._persist()
    },

    setUser(user) { this.user = user ?? null; this._persist() },

    login(payload = {}) {
      const user = payload.user ?? payload.profile ?? payload.data ?? payload ?? null
      this.user = user ?? null
      this._persist()
    },

    logout() {
      this.user = null
      this._persist()
    },

    initializeFromStorage() {
      if (!hasWindow()) return
      this.user = safeParse(localStorage.getItem('user'))
    },

    async hydrateMeIfNeeded() {
      try {
        if (this.userPid) return
        const { data } = await api.get('/users/profile')
        this.user = data?.user ?? data?.data ?? data
        this._persist()
      } catch (e) {
        const status = e?.response?.status
        if (status === 401) {
          this.logout()
        } else {
          console.warn('hydrateMeIfNeeded 실패:', e?.response?.data || e?.message)
        }
      }
    },
  },
})

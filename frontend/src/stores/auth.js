import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token'),
    user: JSON.parse(localStorage.getItem('user') || 'null'),
  }),
  getters: {
    isAuthenticated: (s) => Boolean(s.token),
    hasUserSnapshot: (s) => Boolean(s.user),
  },
  actions: {
    login({ token, user }) {
      this.token = token ?? null
      this.user  = user  ?? null
      if (this.token) localStorage.setItem('token', this.token); else localStorage.removeItem('token')
      if (this.user)  localStorage.setItem('user', JSON.stringify(this.user)); else localStorage.removeItem('user')
    },
    logout() {
      this.token = null
      this.user  = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})

import { defineStore } from 'pinia'

const initialToken = localStorage.getItem('token') || null
const initialUser = initialToken
  ? JSON.parse(localStorage.getItem('user') || 'null')
  : null

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: initialToken,
    user: initialUser,
  }),
  getters: {
    // JWT 토큰이 있어야 보호된 API에 접근할 수 있으므로 토큰 존재만으로 인증 여부를 판단한다
    isAuthenticated: (s) => !!s.token,
  },
  actions: {
    login({ token, user }) {
      this.token = token ?? null
      this.user = token ? (user ?? null) : null

      if (this.token) {
        localStorage.setItem('token', this.token)
      } else {
        localStorage.removeItem('token')
      }

      if (this.user) {
        localStorage.setItem('user', JSON.stringify(this.user))
      } else {
        localStorage.removeItem('user')
      }
    },
    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
  },
})

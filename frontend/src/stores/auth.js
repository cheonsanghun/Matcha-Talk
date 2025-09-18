import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token'),
    user: JSON.parse(localStorage.getItem('user') || 'null'),
  }),
  getters: {
    // JWT 토큰이 존재할 때만 인증된 것으로 간주한다.
    isAuthenticated: (s) => !!s.token,
  },
  actions: {
    login({ token, user }) {
      if (!token) {
        console.warn('로그인 응답에 JWT 토큰이 포함되지 않았습니다. 인증 상태를 초기화합니다.')
        this.logout()
        return false
      }

      this.token = token
      this.user = user ?? null

      localStorage.setItem('token', this.token)
      if (this.user) {
        localStorage.setItem('user', JSON.stringify(this.user))
      } else {
        localStorage.removeItem('user')
      }

      return true
    },
    logout() {
      this.token = null
      this.user  = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})

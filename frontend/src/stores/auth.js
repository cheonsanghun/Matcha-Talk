import { defineStore } from 'pinia'
import { API_BASE_URL } from '../services/endpoints'

function joinApiPath(base, path) {
  if (!base) {
    return path
  }
  const hasTrailing = base.endsWith('/')
  const hasLeading = path.startsWith('/')
  if (hasTrailing && hasLeading) {
    return `${base}${path.slice(1)}`
  }
  if (!hasTrailing && !hasLeading) {
    return `${base}/${path}`
  }
  return `${base}${path}`
}

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
      this.user = user ?? null
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
    async refreshToken() {
      if (!this.token) {
        return null
      }

      const endpoint = joinApiPath(API_BASE_URL, '/auth/refresh')
      const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${this.token}` }

      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers,
          credentials: 'include',
        })

        if (!response.ok) {
          console.warn('토큰 갱신 요청 실패', response.status)
          if (response.status === 401) {
            this.logout()
          }
          return null
        }

        const body = await response.json().catch(() => null)
        const nextToken =
          typeof body?.token === 'string'
            ? body.token
            : typeof body?.accessToken === 'string'
              ? body.accessToken
              : null
        const nextUser = body?.user && typeof body.user === 'object' ? body.user : this.user

        if (!nextToken) {
          console.warn('토큰 갱신 응답에 토큰이 없습니다.', body)
          return null
        }

        this.login({ token: nextToken, user: nextUser })
        return nextToken
      } catch (error) {
        console.warn('토큰 갱신 중 오류 발생', error)
        return null
      }
    },
  },
})

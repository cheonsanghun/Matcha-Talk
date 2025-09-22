import { defineStore } from 'pinia'
import api from '../services/api'

export const useFriendsStore = defineStore('friends', {
  state: () => ({
    list: [],
    loading: false,
    initialized: false
  }),
  actions: {
    async fetch() {
      if (this.loading) return
      this.loading = true
      try {
        const { data } = await api.get('/friends')
        this.list = Array.isArray(data) ? data : []
        this.initialized = true
      } finally {
        this.loading = false
      }
    },
    async follow(loginId) {
      if (!loginId) return null
      const { data } = await api.post(`/friends/${loginId}`)
      const exists = this.list.find((friend) => friend.loginId === data.loginId)
      if (!exists) {
        this.list.push(data)
      }
      return data
    },
    async unfollow(loginId) {
      if (!loginId) return
      await api.delete(`/friends/${loginId}`)
      this.list = this.list.filter((friend) => friend.loginId !== loginId)
    }
  }
})

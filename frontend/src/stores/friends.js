import { defineStore } from 'pinia'
import api from '../services/api'

export const useFriendsStore = defineStore('friends', {
  state: () => ({
    list: [],
    loading: false,
  }),
  actions: {
    async fetch() {
      this.loading = true
      try {
        const { data } = await api.get('/follows')
        this.list = data
      } finally {
        this.loading = false
      }
    },
    async add(loginId) {
      if (!loginId) return
      await api.post(`/follows/${encodeURIComponent(loginId)}`)
      await this.fetch()
    },
    async remove(loginId) {
      if (!loginId) return
      await api.delete(`/follows/${encodeURIComponent(loginId)}`)
      await this.fetch()
    },
  },
})

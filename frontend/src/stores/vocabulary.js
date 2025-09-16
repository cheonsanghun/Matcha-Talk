import { defineStore } from 'pinia'
import api from '../services/api'

export const useVocabularyStore = defineStore('vocabulary', {
  state: () => ({
    words: [],
    loading: false,
  }),
  actions: {
    async fetch() {
      this.loading = true
      try {
        const { data } = await api.get('/vocabulary')
        this.words = data
      } finally {
        this.loading = false
      }
    },
    async addWord(original, translated) {
      await api.post('/vocabulary', { original, translated })
      await this.fetch()
    },
  },
})

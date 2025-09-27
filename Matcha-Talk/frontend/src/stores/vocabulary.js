import { defineStore } from 'pinia'
import api from '../services/api'

export const useVocabularyStore = defineStore('vocabulary', {
  state: () => ({
    words: [],
    loading: false,
  }),
  actions: {
    async fetchWords(limit = 50, before = null) {
      this.loading = true
      try {
        const params = { limit }
        if (before) params.before = before
        const { data } = await api.get('/words', { params })
        this.words = Array.isArray(data) ? data : []
      } catch (error) {
        console.warn('단어장 목록을 불러오지 못했습니다.', error?.response?.status)
        this.words = []
        throw error
      } finally {
        this.loading = false
      }
    },
    async removeWord(wordId) {
      const original = [...this.words]
      this.words = this.words.filter((w) => w.wordId !== wordId)
      try {
        await api.delete(`/words/${wordId}`)
      } catch (error) {
        this.words = original
        console.error('단어 삭제 중 오류가 발생했습니다.', error?.response?.status)
        throw error
      }
    }
  }
})

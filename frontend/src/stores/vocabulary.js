import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'matcha-talk/vocabulary'

export const useVocabularyStore = defineStore('vocabulary', () => {
  const words = ref([])
  const initialized = ref(false)

  const load = () => {
    if (typeof window === 'undefined') return
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) {
          words.value = parsed
        }
      }
    } catch (error) {
      console.warn('단어장 데이터를 불러오지 못했습니다.', error)
    } finally {
      initialized.value = true
    }
  }

  const persist = () => {
    if (typeof window === 'undefined' || !initialized.value) return
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(words.value))
  }

  const addWord = (entry) => {
    if (!entry?.original || !entry?.translated) return
    words.value.push({
      original: entry.original,
      translated: entry.translated,
      addedAt: entry.addedAt ?? new Date().toISOString(),
    })
  }

  const removeWord = (index) => {
    if (index < 0 || index >= words.value.length) return
    words.value.splice(index, 1)
  }

  const clear = () => {
    words.value = []
  }

  load()

  watch(
    words,
    () => {
      persist()
    },
    { deep: true }
  )

  return {
    words,
    addWord,
    removeWord,
    clear,
    load,
  }
})

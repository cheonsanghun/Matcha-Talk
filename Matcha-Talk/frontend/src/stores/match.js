import { defineStore } from 'pinia'

const STORAGE_KEY = 'match-bootstrap'

function hasWindow () {
  return typeof window !== 'undefined'
}

function readFromStorage () {
  if (!hasWindow()) return null
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    console.warn('Failed to parse stored match bootstrap payload', error)
    sessionStorage.removeItem(STORAGE_KEY)
    return null
  }
}

function writeToStorage (payload) {
  if (!hasWindow()) return
  if (!payload) {
    sessionStorage.removeItem(STORAGE_KEY)
    return
  }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  } catch (error) {
    console.warn('Failed to persist match bootstrap payload', error)
  }
}

export const useMatchStore = defineStore('match', {
  state: () => ({
    bootstrap: readFromStorage(),
  }),

  actions: {
    setBootstrap (payload) {
      this.bootstrap = payload || null
      writeToStorage(this.bootstrap)
    },
    clearBootstrap () {
      this.bootstrap = null
      writeToStorage(null)
    },
  },
})

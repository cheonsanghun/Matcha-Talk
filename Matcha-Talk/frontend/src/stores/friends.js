import { defineStore } from 'pinia'

export const useFriendsStore = defineStore('friends', {
  state: () => ({
    list: JSON.parse(localStorage.getItem('friends') || '[]')
  }),
  actions: {
    add(name) {
      if (name && !this.list.includes(name)) {
        this.list.push(name)
        localStorage.setItem('friends', JSON.stringify(this.list))
      }
    },
    remove(name) {
      this.list = this.list.filter(f => f !== name)
      localStorage.setItem('friends', JSON.stringify(this.list))
    }
  }
})

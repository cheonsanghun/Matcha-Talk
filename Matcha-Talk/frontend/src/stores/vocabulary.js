import { defineStore } from 'pinia';

export const useVocabularyStore = defineStore('vocabulary', {
  state: () => ({
    words: JSON.parse(localStorage.getItem('vocabulary') || '[]')
  }),
  actions: {
    addWord(original, translated) {
      this.words.push({ original, translated });
      localStorage.setItem('vocabulary', JSON.stringify(this.words));
    }
  }
});

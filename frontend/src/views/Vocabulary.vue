<template>
  <v-container class="py-4">
    <h2 class="text-h5 mb-4">단어장</h2>
    <v-form @submit.prevent="add">
      <v-row class="mb-4" align="center" no-gutters>
        <v-col cols="12" md="4" class="pe-md-2">
          <v-text-field v-model="original" label="원문" variant="outlined" required />
        </v-col>
        <v-col cols="12" md="4" class="pe-md-2">
          <v-text-field v-model="translated" label="번역" variant="outlined" required />
        </v-col>
        <v-col cols="12" md="4">
          <v-btn type="submit" color="pink" block>추가</v-btn>
        </v-col>
      </v-row>
    </v-form>
    <v-list>
      <v-list-item v-for="(w, i) in words" :key="w.addedAt ?? i">
        <v-list-item-title>{{ w.original }} - {{ w.translated }}</v-list-item-title>
        <template #append>
          <v-btn icon variant="text" @click="remove(i)"><v-icon>mdi-delete</v-icon></v-btn>
        </template>
      </v-list-item>
      <v-list-item v-if="!words.length">
        <v-list-item-title>저장된 단어가 없습니다.</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-container>
</template>

<script setup>
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useVocabularyStore } from '../stores/vocabulary'

const vocabularyStore = useVocabularyStore()
const { words } = storeToRefs(vocabularyStore)

const original = ref('')
const translated = ref('')

const add = () => {
  if (!original.value.trim() || !translated.value.trim()) return
  vocabularyStore.addWord({
    original: original.value.trim(),
    translated: translated.value.trim(),
  })
  original.value = ''
  translated.value = ''
}

const remove = (index) => {
  vocabularyStore.removeWord(index)
}
</script>

<style scoped>
</style>

<template>
  <v-container class="py-4">
    <h2 class="text-h5 mb-4">단어장</h2>
    <v-list>
      <v-list-item v-if="loading">
        <v-list-item-title>단어장을 불러오는 중입니다...</v-list-item-title>
      </v-list-item>
      <v-list-item v-for="(w, i) in words" :key="w.wordId ?? i">
        <v-list-item-title>{{ w.sourceText }} - {{ w.translatedText }}</v-list-item-title>
      </v-list-item>
      <v-list-item v-if="!loading && !words.length">
        <v-list-item-title>저장된 단어가 없습니다.</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-container>
</template>

<script setup>
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useVocabularyStore } from '../stores/vocabulary'

const store = useVocabularyStore()
const { words, loading } = storeToRefs(store)

onMounted(() => {
  store.fetchWords().catch((error) => {
    console.error('단어장 조회 실패', error)
  })
})
</script>

<style scoped>
</style>

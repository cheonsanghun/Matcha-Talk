<template>
  <v-container class="py-4">
    <h2 class="text-h5 mb-4">단어장</h2>
    <v-progress-circular v-if="loading" indeterminate color="pink" class="my-6" />
    <v-list v-else>
      <v-list-item v-for="(w, i) in words" :key="w.id ?? i">
        <v-list-item-title>{{ w.originalText ?? w.original }} - {{ w.translatedText ?? w.translated }}</v-list-item-title>
      </v-list-item>
      <v-list-item v-if="!words.length">
        <v-list-item-title>저장된 단어가 없습니다.</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-container>
</template>

<script setup>
import { onMounted } from 'vue';
import { storeToRefs } from 'pinia';
import { useVocabularyStore } from '../stores/vocabulary';

const store = useVocabularyStore();
const { words, loading } = storeToRefs(store);

onMounted(() => {
  store.fetch();
});
</script>

<style scoped>
</style>

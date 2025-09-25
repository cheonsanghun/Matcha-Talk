<template>
  <v-container class="py-4">
    <h2 class="text-h5 mb-4">단어장</h2>
    <v-list>
      <v-list-item v-for="(w, i) in words" :key="i">
        <v-list-item-title>{{ w.original }} - {{ w.translated }}</v-list-item-title>
      </v-list-item>
      <v-list-item v-if="!words.length">
        <v-list-item-title>저장된 단어가 없습니다.</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-container>
</template>

<script setup>
import { storeToRefs } from 'pinia';
import { useVocabularyStore } from '../stores/vocabulary';

const store = useVocabularyStore();
const { words } = storeToRefs(store);
</script>

<style scoped>
</style>

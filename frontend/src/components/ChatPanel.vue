<template>
  <div class="d-flex flex-column h-100">
    <div class="flex-grow-1 overflow-y-auto pe-2" ref="messagesContainer">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="my-1"
      >
        <span class="text-body-2">{{ msg }}</span>
      </div>
    </div>
    <v-text-field
      v-model="newMessage"
      @keyup.enter="send"
      placeholder="메시지를 입력하세요"
      density="compact"
      hide-details
    >
      <template #append-inner>
        <v-icon @click="send" class="cursor-pointer">mdi-send</v-icon>
      </template>
    </v-text-field>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue';

const messages = ref(['안녕하세요!', '테스트 메시지']);
const newMessage = ref('');
const messagesContainer = ref(null);

function scrollToBottom() {
  nextTick(() => {
    const el = messagesContainer.value;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  });
}

function send() {
  if (newMessage.value.trim()) {
    messages.value.push(newMessage.value.trim());
    newMessage.value = '';
    scrollToBottom();
  }
}
</script>

<style scoped>
</style>

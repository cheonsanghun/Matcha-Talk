<template>
  <div class="d-flex flex-column h-100">
    <div class="d-flex justify-end mb-1">
      <v-btn size="small" variant="outlined" @click="addFriend">친구 추가</v-btn>
    </div>
    <div class="flex-grow-1 overflow-y-auto pe-2" ref="messagesContainer">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="my-1"
      >
        <template v-if="msg.fileUrl">
          <div v-if="msg.isImage">
            <img :src="msg.fileUrl" :alt="msg.original" class="max-w-100" />
          </div>
          <div v-else>
            <a :href="msg.fileUrl" :download="msg.original">{{ msg.original }}</a>
          </div>
        </template>
        <template v-else>
          <div class="text-body-2">{{ msg.original }}</div>
          <div v-if="msg.translated" class="text-caption text-grey">
            {{ msg.translated }}
            <v-icon size="small" class="ms-1 cursor-pointer" @click="saveWord(msg)">mdi-content-save</v-icon>
          </div>
        </template>
      </div>
    </div>
    <v-file-input
      v-model="file"
      prepend-icon="mdi-paperclip"
      hide-details
      density="compact"
      accept="image/*,application/*"
      @change="sendFile"
    />
    <v-text-field
      v-model="newMessage"
      @keyup.enter="send"
      placeholder="메시지를 입력하세요"
      density="compact"
      hide-details
    >
      <template #append-inner>
        <v-icon @click="toggleTranslate" :color="useTranslate ? 'primary' : undefined" class="me-1 cursor-pointer">mdi-translate</v-icon>
        <v-icon @click="send" class="cursor-pointer">mdi-send</v-icon>
      </template>
    </v-text-field>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue';
import { translate } from '../services/translator';
import { useVocabularyStore } from '../stores/vocabulary';
import { useFriendsStore } from '../stores/friends';

const props = defineProps({
  partner: { type: String, default: '' }
})

const messages = ref([
  { original: '안녕하세요!', translated: '' },
  { original: '테스트 메시지', translated: '' }
]);
const newMessage = ref('');
const file = ref(null);
const messagesContainer = ref(null);
const useTranslate = ref(false);
const vocab = useVocabularyStore();
const friends = useFriendsStore();

function scrollToBottom() {
  nextTick(() => {
    const el = messagesContainer.value;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  });
}

async function send() {
  const text = newMessage.value.trim();
  if (text) {
    let translated = '';
    if (useTranslate.value) {
      translated = await translate(text);
    }
    messages.value.push({ original: text, translated });
    newMessage.value = '';
    scrollToBottom();
  }
}

function toggleTranslate() {
  useTranslate.value = !useTranslate.value;
}

function saveWord(msg) {
  if (msg.translated) {
    vocab.addWord(msg.original, msg.translated);
  }
}

function sendFile() {
  const f = file.value;
  if (!f) return;
  const reader = new FileReader();
  reader.onload = () => {
    messages.value.push({
      original: f.name,
      fileUrl: reader.result,
      isImage: f.type.startsWith('image/')
    });
    file.value = null;
    scrollToBottom();
  };
  reader.readAsDataURL(f);
}

function addFriend() {
  friends.add(props.partner);
}

onMounted(scrollToBottom);

</script>

<style scoped>
.max-w-100 {
  max-width: 100%;
}
</style>

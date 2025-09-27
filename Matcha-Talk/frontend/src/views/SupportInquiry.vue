<!-- src/views/SupportInquiry.vue -->
<template>
  <v-container class="py-10" style="max-width:720px">
    <div class="text-h6 mb-6 text-pink-darken-2">1:1 문의</div>

    <!-- 로그인 전 -->
    <v-alert v-if="!mePid" type="warning" class="mb-4" density="comfortable">
      로그인 후 이용해주세요.
    </v-alert>

    <!-- 로그인 후: 문의 작성 -->
    <v-form v-else @submit.prevent="submit" :disabled="loading">
      <!-- 🔥 PID 입력칸 제거, 내부에서 자동 주입 -->
      <v-select :items="categories" v-model="category" label="카테고리" variant="outlined" required />
      <v-text-field v-model="title"   label="제목"   variant="outlined" required />
      <v-textarea   v-model="content" label="내용"   variant="outlined" auto-grow required />

      <div class="d-flex ga-3 mt-4">
        <v-btn color="pink" :loading="loading" type="submit">제출</v-btn>
        <v-btn variant="text" @click="reset">초기화</v-btn>
      </div>

      <v-alert v-if="message" type="success" class="mt-4" density="compact">{{ message }}</v-alert>
      <v-alert v-if="error"   type="error"   class="mt-2" density="compact">{{ error }}</v-alert>
    </v-form>

    <v-divider class="my-8" />

    <!-- 내가 올린 문의 목록 -->
    <div class="text-subtitle-1 mb-3">내가 올린 문의(최근)</div>
    <v-data-table :items="myList" :headers="headers" density="compact" :items-per-page="5">
      <template #item.user="{ item }">{{ rowOf(item)?.user?.loginId ?? '-' }}</template>
      <template #item.createdAt="{ item }">{{ pretty(rowOf(item)?.createdAt) }}</template>
      <template #item.answeredAt="{ item }">{{ pretty(rowOf(item)?.answeredAt) }}</template>
    </v-data-table>
  </v-container>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '../stores/auth'
import { supportApi } from '../services/support'

/** ─────────────────────────────
 *  Auth: 로그인 사용자 PID 자동 주입
 *  - auth.userPid 게터 사용(여러 스키마 대응)
 *  - 토큰만 있고 user가 비었으면 hydrate로 보강
 *  ──────────────────────────── */
const auth = useAuthStore()
const { userPid } = storeToRefs(auth)
const mePid = computed(() => userPid.value)
onMounted(() => { auth.hydrateMeIfNeeded?.() })

/** UI 상태 */
const loading = ref(false)
const message = ref('')
const error   = ref('')

/** 폼 값 */
const category = ref('ACCOUNT')
const title    = ref('')
const content  = ref('')
const categories = ['ACCOUNT', 'BUG', 'ETC']

/** 목록 테이블 */
const headers = [
  { title: 'ID',       key: 'inquiryId',  width: 70 },
  { title: '작성자',    key: 'user',       width: 120 },
  { title: '카테고리',  key: 'category',   width: 120 },
  { title: '제목',      key: 'title',      minWidth: 200 },
  { title: '상태',      key: 'status',     width: 110 },
  { title: '답변시각',  key: 'answeredAt', width: 160 },
  { title: '등록시각',  key: 'createdAt',  width: 160 },
]
const myList = ref([])

/** 유틸 */
function rowOf(item){ return item?.raw ?? item }
function pretty(dt){ return dt?.replace('T',' ').slice(0,19) ?? '-' }

/** 내 문의 목록 로드 */
async function loadMy(){
  if(!mePid.value) return
  const { data } = await supportApi.myInquiries()
  myList.value = data
}
watch(mePid, loadMy, { immediate: true })

/** 폼 리셋 */
function reset(){
  category.value = 'ACCOUNT'
  title.value    = ''
  content.value  = ''
  message.value  = ''
  error.value    = ''
}

/** 제출 */
async function submit(){
  if(!mePid.value) return
  loading.value = true; message.value=''; error.value=''
  try{
    // 🔥 userPid는 UI 없이 내부에서 주입
    await supportApi.createInquiry({
      category: category.value,
      title: title.value,
      content: content.value,
    })
    message.value = '문의가 접수되었습니다.'
    reset()
    await loadMy()
  }catch(e){
    error.value = e?.response?.data?.message || '등록 중 오류가 발생했습니다.'
  }finally{
    loading.value = false
  }
}
</script>

<template>
  <v-container class="py-10" style="max-width:960px">
    <div class="text-h6 mb-4 text-pink-darken-2">문의 관리</div>

    <div class="d-flex ga-3 mb-4">
      <v-select
        v-model="status"
        :items="['OPEN','ANSWERED','CLOSED']"
        label="상태"
        style="max-width:260px"
        density="comfortable"
        variant="outlined"
      />
      <v-btn color="pink" @click="load" :loading="loading">조회</v-btn>
    </div>

    <v-data-table
      :items="items"
      :headers="headers"
      :loading="loading"
      item-key="inquiryId"
      density="compact"
    >
      <template #item.createdAt="{ item }">{{ dt(row(item).createdAt) }}</template>
      <template #item.answeredAt="{ item }">{{ dt(row(item).answeredAt) }}</template>

      <template #item.action="{ item }">
        <div class="d-flex ga-2 align-center">
          <v-text-field
            v-model="row(item)._answer"
            placeholder="답변"
            variant="outlined"
            density="compact"
            hide-details
            style="min-width:220px"
          />
          <v-btn
            size="small"
            @click="answer(row(item))"
            :loading="busyId===row(item).inquiryId"
            :disabled="!row(item)._answer"
          >등록</v-btn>
          <v-btn
            size="small"
            variant="tonal"
            @click="closeRow(row(item))"
            :loading="busyId===row(item).inquiryId"
          >종결</v-btn>
        </div>
      </template>
    </v-data-table>

    <v-snackbar v-model="snack.show" :timeout="2000">
      {{ snack.msg }}
    </v-snackbar>
  </v-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/services/admin'

const status = ref('OPEN')
const items = ref([])
const loading = ref(false)
const busyId = ref(null)

const headers = [
  { title: 'ID', key: 'inquiryId', width: 80 },
  { title: '제목', key: 'title', minWidth: 220 },
  { title: '카테고리', key: 'category', width: 120 },
  { title: '상태', key: 'status', width: 110 },
  { title: '답변시각', key: 'answeredAt', width: 170 },
  { title: '등록시각', key: 'createdAt', width: 170 },
  { title: '액션', key: 'action', width: 320 },
]

const snack = ref({ show:false, msg:'' })
const toast = (m)=>{ snack.value={show:true,msg:m} }

const row = (i)=> i?.raw ?? i
const dt = (s)=> s ? s.replace('T',' ').slice(0,19) : '-'

async function load(){
  loading.value = true
  try{
    const { data } = await adminApi.listInquiries(status.value)
    // 서버 DTO 그대로 사용, 답변 입력 임시필드 추가
    items.value = data.map(d => ({ ...d, _answer: '' }))
  }catch(e){
    console.error(e)
    toast(e?.response?.data?.message || '목록 조회 실패')
  }finally{
    loading.value = false
  }
}

async function answer(r){
  if(!r?.inquiryId || !r?._answer) return
  busyId.value = r.inquiryId
  try{
    await adminApi.answerInquiry(r.inquiryId, r._answer)
    toast('답변이 등록되었습니다.')
    await load()
  }catch(e){
    console.error(e)
    toast(e?.response?.data?.message || '답변 처리 실패')
  }finally{
    busyId.value = null
  }
}

async function closeRow(r){
  if(!r?.inquiryId) return
  busyId.value = r.inquiryId
  try{
    await adminApi.closeInquiry(r.inquiryId)
    toast('문의가 종결되었습니다.')
    await load()
  }catch(e){
    console.error(e)
    toast(e?.response?.data?.message || '종결 처리 실패')
  }finally{
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <v-container class="py-10" style="max-width:1100px">
    <div class="text-h6 mb-4 text-pink-darken-2">신고 관리</div>

    <div class="d-flex ga-3 mb-4">
      <v-select
        v-model="status"
        :items="['OPEN','REVIEWING','ACTIONED','DISMISSED']"
        label="상태"
        style="max-width:280px"
        variant="outlined"
        density="comfortable"
      />
      <v-btn color="pink" @click="load" :loading="loading">조회</v-btn>
    </div>

    <v-data-table
      :items="items"
      :headers="headers"
      :loading="loading"
      item-key="reportId"
      density="compact"
    >
      <template #item.createdAt="{ item }">{{ dt(row(item).createdAt) }}</template>

      <template #item.parties="{ item }">
        <div>
          <div>신고자: <b>{{ row(item).reporterLoginId }}</b> (#{{ row(item).reporterPid }})</div>
          <div>피신고: <b>{{ row(item).reportedLoginId }}</b> (#{{ row(item).reportedPid }})</div>
        </div>
      </template>

      <template #item.action="{ item }">
        <div class="d-flex ga-2 align-center">
          <v-select
            v-model="row(item)._penalty"
            :items="['WARN','SUSPEND','BAN']"
            label="제재"
            density="compact"
            variant="outlined"
            hide-details
            style="width:120px"
          />
          <v-text-field
            v-model.number="row(item)._days"
            type="number"
            min="1"
            label="일수"
            density="compact"
            variant="outlined"
            hide-details
            style="width:90px"
            :disabled="row(item)._penalty!=='SUSPEND'"
          />
          <v-text-field
            v-model="row(item)._reason"
            placeholder="사유"
            variant="outlined"
            density="compact"
            hide-details
            style="min-width:220px"
          />
          <v-btn
            size="small"
            @click="action(row(item))"
            :loading="busyId===row(item).reportId"
          >제재</v-btn>
          <v-btn
            size="small"
            variant="tonal"
            @click="dismiss(row(item))"
            :loading="busyId===row(item).reportId"
          >반려</v-btn>
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
import { adminApi } from '../../services/admin'

const status = ref('OPEN')
const items = ref([])
const loading = ref(false)
const busyId = ref(null)

const headers = [
  { title: 'ID', key: 'reportId', width: 80 },
  { title: '참여자', key: 'parties', minWidth: 260 },
  { title: '사유', key: 'reason', minWidth: 180 },
  { title: '상세', key: 'detail', minWidth: 220 },
  { title: '상태', key: 'status', width: 120 },
  { title: '신고시각', key: 'createdAt', width: 170 },
  { title: '액션', key: 'action', width: 460 },
]

const snack = ref({ show:false, msg:'' })
const toast = (m)=>{ snack.value={show:true,msg:m} }

const row = (i)=> i?.raw ?? i
const dt = (s)=> s ? s.replace('T',' ').slice(0,19) : '-'

async function load(){
  loading.value = true
  try{
    const { data } = await adminApi.listReports(status.value)
    items.value = data.map(d => ({
      ...d,
      _penalty: 'WARN',
      _days: 7,
      _reason: '',
    }))
  }catch(e){
    console.error(e)
    toast(e?.response?.data?.message || '목록 조회 실패')
  }finally{
    loading.value = false
  }
}

async function action(r){
  if(!r?.reportId) return
  busyId.value = r.reportId
  try{
    await adminApi.actionReport(r.reportId, {
      penaltyType: r._penalty,
      days: r._penalty==='SUSPEND' ? (r._days || 7) : null,
      reason: r._reason || ''
    })
    toast('제재가 적용되었습니다.')
    await load()
  }catch(e){
    console.error(e)
    toast(e?.response?.data?.message || '제재 처리 실패')
  }finally{
    busyId.value = null
  }
}

async function dismiss(r){
  if(!r?.reportId) return
  busyId.value = r.reportId
  try{
    await adminApi.dismissReport(r.reportId, { reason: r._reason || '' })
    toast('신고가 반려되었습니다.')
    await load()
  }catch(e){
    console.error(e)
    toast(e?.response?.data?.message || '반려 처리 실패')
  }finally{
    busyId.value = null
  }
}

onMounted(load)
</script>

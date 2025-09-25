<template>
  <v-container class="py-10" style="max-width:720px">
    <div class="text-h6 mb-6 text-pink-darken-2">신고하기</div>

    <v-alert v-if="!mePid" type="warning" class="mb-4" density="comfortable">
      로그인 후 이용해주세요.
    </v-alert>

    <v-form @submit.prevent="submit" :disabled="loading">
      <!-- ✅ 피신고자 '아이디' 직접 입력 -->
      <v-text-field
        v-model="reportedLoginId"
        label="피신고자 아이디"
        variant="outlined"
        required
      />

      <v-text-field
        v-model="reason"
        label="사유(간단)"
        variant="outlined"
        required
      />
      <v-textarea
        v-model="detail"
        label="상세 내용(선택)"
        variant="outlined"
        auto-grow
      />

      <div class="d-flex ga-3 mt-2">
        <v-btn color="pink" type="submit" :loading="loading" :disabled="!mePid">제출</v-btn>
        <v-btn variant="text" @click="reset" :disabled="loading">초기화</v-btn>
      </div>

      <v-alert v-if="message" type="success" class="mt-4" density="comfortable">{{ message }}</v-alert>
      <v-alert v-if="error" type="error" class="mt-4" density="comfortable">{{ error }}</v-alert>
    </v-form>

    <!-- 내 신고 목록 (서버 500이어도 화면은 사용 가능) -->
    <div v-if="mePid" class="mt-10">
      <div class="text-subtitle-1 font-weight-bold mb-3">내 신고 목록(최신)</div>
      <v-alert v-if="loadMyError" type="warning" class="mb-3" density="comfortable">
        목록을 불러오지 못했습니다(서버 500). 신고 제출은 정상 동작합니다.
      </v-alert>
      <v-table density="compact">
        <thead>
          <tr>
            <th>ID</th><th>피신고자</th><th>사유</th><th>상태</th><th>시간</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in myReportsList" :key="r.reportId">
            <td>{{ r.reportId }}</td>
            <td>{{ r.reportedLoginId || r.reportedPid }}</td>
            <td>{{ r.reason }}</td>
            <td>{{ r.status }}</td>
            <td>{{ formatTime(r.createdAt) }}</td>
          </tr>
          <tr v-if="myReportsList.length === 0">
            <td colspan="5" class="text-grey">등록된 신고가 없습니다.</td>
          </tr>
        </tbody>
      </v-table>
    </div>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import { supportApi } from '../services/support'

const store = useAuthStore()
const mePid = computed(() => {
  const u = store?.user || {}
  return u.userPid ?? u.user_pid ?? u.id ?? u.pid ?? null
})

const reportedLoginId = ref('')
const reason = ref('')
const detail = ref('')
const loading = ref(false)
const message = ref('')
const error = ref('')

const myReportsList = ref([])
const loadMyError = ref(false)

function reset() {
  reportedLoginId.value = ''
  reason.value = ''
  detail.value = ''
  message.value = ''
  error.value = ''
}

function formatTime(t) { try { return new Date(t).toLocaleString() } catch { return t } }

async function loadMy() {
  if (!mePid.value) return
  loadMyError.value = false
  try {
    const { data } = await supportApi.myReports(mePid.value)
    myReportsList.value = Array.isArray(data) ? data : []
  } catch { loadMyError.value = true }
}

async function submit() {
  if (!mePid.value) { error.value = '로그인이 필요합니다.'; return }
  if (!reportedLoginId.value?.trim()) { error.value = '피신고자 아이디를 입력하세요.'; return }
  if (!reason.value?.trim()) { error.value = '사유를 입력하세요.'; return }

  loading.value = true; error.value = ''; message.value = ''
  try {
    await supportApi.createReportByLogin({
      reporterPid: mePid.value,
      reportedLoginId: reportedLoginId.value.trim(),
      reason: reason.value.trim(),
      detail: (detail.value || '').trim() || null
    })
    message.value = '신고가 접수되었습니다.'
    reset()
    await loadMy()
  } catch (e) {
    const res = e?.response?.data
    error.value = res?.message || res || '등록 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (typeof store.hydrateMeIfNeeded === 'function') await store.hydrateMeIfNeeded()
  else if (typeof store.me === 'function') await store.me()
  await loadMy()
})
</script>

<template>
  <v-container class="py-10">
    <!-- 검색 -->
    <div class="d-flex ga-3 mb-4">
      <v-text-field v-model="q" label="아이디/이메일 검색" @keyup.enter="load" variant="outlined" />
      <v-btn color="pink" @click="load" :loading="loading">검색</v-btn>
    </div>

    <v-data-table
      :items="users"
      :headers="headers"
      :loading="loading"
      :items-per-page="10"
      item-key="userPid"
      density="compact"
      class="elevation-1"
    >
      <!-- PID (키 통일) -->
      <template #item.userPid="{ item }">
        {{ pidOf(rowOf(item)) ?? '-' }}
      </template>

      <!-- 활성 칩 -->
      <template #item.enabled="{ item }">
        <v-chip :color="rowOf(item).enabled ? 'green' : 'grey'" size="small" variant="flat">
          {{ rowOf(item).enabled ? 'true' : 'false' }}
        </v-chip>
      </template>

      <!-- 잠금 상태 + 남은 시간 -->
      <template #item.lockedUntil="{ item }">
        <div class="d-flex align-center ga-2">
          <v-chip
            :color="rowOf(item).lockedUntil ? 'red' : 'blue-grey'"
            size="small"
            variant="flat"
            :title="formatDate(rowOf(item).lockedUntil) || ''"
          >
            {{ rowOf(item).lockedUntil ? '잠금중' : '없음' }}
          </v-chip>
          <span class="text-caption text-blue-grey-darken-2">
            {{ remainText(rowOf(item)) }}
          </span>
        </div>
      </template>

      <!-- 액션 -->
      <template #item.actions="{ item }">
        <v-btn size="small" variant="tonal" color="pink" class="me-2"
               :loading="opLoadingId === 'enable:'+pidOf(rowOf(item))"
               @click="toggleEnable(rowOf(item))">
          {{ rowOf(item).enabled ? '비활성' : '활성' }}
        </v-btn>

        <v-btn size="small" variant="tonal" color="deep-purple" class="me-2"
               :loading="opLoadingId === 'lock:'+pidOf(rowOf(item))"
               @click="openLock(rowOf(item))">
          잠금
        </v-btn>

        <v-btn size="small" color="primary"
               :loading="opLoadingId === 'edit:'+pidOf(rowOf(item))"
               @click="openEdit(rowOf(item))">
          수정
        </v-btn>
      </template>
    </v-data-table>

    <!-- 잠금(분) 입력 다이얼로그 -->
    <v-dialog v-model="lockDialog.show" max-width="420">
      <v-card>
        <v-card-title>계정 잠금</v-card-title>
        <v-card-text>
          <div class="text-body-2 mb-2">잠금 분(minute) 입력</div>
          <v-text-field v-model.number="lockDialog.minutes" type="number" min="1" variant="outlined" />
          <div class="text-caption text-blue-grey-darken-1">
            기본 10분. 1보다 작게 입력하면 10분으로 처리됩니다.
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="lockDialog.show=false">취소</v-btn>
          <v-btn color="deep-purple" @click="doLock"
                 :loading="opLoadingId === 'lock:'+pidOf(lockDialog.user || {})">
            잠금하기
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ✅ 잠금 완료 팝업 -->
    <v-dialog v-model="lockSuccess.show" max-width="460">
      <v-card>
        <v-card-title class="text-success">잠금이 완료되었습니다</v-card-title>
        <v-card-text>
          <div class="mb-2">해제 예정: <b>{{ formatDate(lockSuccess.until) }}</b></div>
          <div class="text-blue-grey-darken-2">남은 시간: {{ lockSuccess.remainText }}</div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" @click="lockSuccess.show=false">확인</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 수정 다이얼로그 -->
    <v-dialog v-model="editDialog.show" max-width="520">
      <v-card>
        <v-card-title>사용자 수정</v-card-title>
        <v-card-text>
          <v-form ref="editForm" @submit.prevent="doEdit">
            <v-text-field v-model="editDialog.form.nickName" label="닉네임" variant="outlined" />
            <v-text-field v-model="editDialog.form.email" label="이메일" variant="outlined" />
            <v-select v-model="editDialog.form.roleName" :items="roleItems" label="권한(Role)" variant="outlined"/>
          </v-form>
          <div class="text-caption text-blue-grey-darken-1 mt-2">
            * 권한 예시: ROLE_USER, ROLE_ADMIN
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer/>
          <v-btn variant="text" @click="editDialog.show=false">닫기</v-btn>
          <v-btn color="primary" @click="doEdit"
                 :loading="opLoadingId === 'edit:'+pidOf(editDialog.user || {})">
            저장
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 스낵바(옵션) -->
    <v-snackbar v-model="snack.show" timeout="2200">
      {{ snack.msg }}
    </v-snackbar>
  </v-container>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue"
import { adminApi } from "../../services/admin"

const q = ref("")
const users = ref([])
const loading = ref(false)
const opLoadingId = ref(null)

const headers = [
  { title: "PID",     key: "userPid", width: 80 },
  { title: "아이디",   key: "loginId" },
  { title: "이메일",   key: "email" },
  { title: "닉네임",   key: "nickName" },
  { title: "권한",     key: "roleName" },
  { title: "활성",     key: "enabled", width: 90 },
  { title: "잠금",     key: "lockedUntil", width: 220 }, // 남은시간 포함
  { title: "Actions",  key: "actions", sortable: false, width: 240 },
]

// Vuetify 3 슬롯 보호
function rowOf(item) { return (item && item.raw) ? item.raw : (item || {}) }
// 다양한 키를 userPid로 통일
function pidOf(row) { return row.userPid ?? row.user_pid ?? row.id ?? row.userId ?? null }

// 서버 → 뷰모델 정규화 (snake/camel 혼재 대비)
function normalize(u) {
  return {
    userPid     : u.userPid     ?? u.user_pid ?? u.id ?? u.userId ?? null,
    loginId     : u.loginId     ?? u.login_id,
    email       : u.email,
    nickName    : u.nickName    ?? u.nick_name,
    roleName    : u.roleName    ?? u.rolename ?? u.role_name,
    enabled     : typeof u.enabled === 'boolean' ? u.enabled : !!u.enabled,
    lockedUntil : u.lockedUntil ?? u.locked_until ?? null,
    createdAt   : u.createdAt   ?? u.created_at ?? null,
    updatedAt   : u.updatedAt   ?? u.updated_at ?? null,
  }
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.listUsers(q.value || undefined)
    users.value = (Array.isArray(data) ? data : []).map(normalize)
  } finally {
    loading.value = false
  }
}

/* ===== 활성/비활성 토글 ===== */
async function toggleEnable(row) {
  const pid = pidOf(row); if (!pid) return
  opLoadingId.value = 'enable:'+pid
  try {
    const { data } = await adminApi.enableUser(pid, !row.enabled)
    replaceRow(normalize(data))
    snackIt(data.enabled ? '활성으로 전환' : '비활성으로 전환')
  } finally {
    opLoadingId.value = null
  }
}

/* ===== 잠금 ===== */
const lockDialog = ref({ show:false, user:null, minutes:10 })
const lockSuccess = ref({ show:false, until:null, remainText:'' })

function openLock(u) {
  lockDialog.value = { show:true, user:u, minutes:10 }
}

async function doLock() {
  const u = lockDialog.value.user
  const pid = pidOf(u); if (!pid) return
  const minutes = lockDialog.value.minutes > 0 ? lockDialog.value.minutes : 10
  opLoadingId.value = 'lock:'+pid
  try {
    const { data } = await adminApi.lockUser(pid, minutes)
    const updated = normalize(data)
    replaceRow(updated)
    lockDialog.value.show = false

    // 팝업 표시
    lockSuccess.value.until = updated.lockedUntil
    lockSuccess.value.remainText = remainText(updated)
    lockSuccess.value.show = true
  } finally {
    opLoadingId.value = null
  }
}

/* ===== 수정 ===== */
const editDialog = ref({
  show:false,
  user:null,
  form:{ nickName:'', email:'', roleName:'' }
})
const roleItems = ['ROLE_USER', 'ROLE_ADMIN']

function openEdit(u) {
  editDialog.value = {
    show: true,
    user: u,
    form: {
      nickName: u.nickName || '',
      email   : u.email    || '',
      roleName: u.roleName || 'ROLE_USER'
    }
  }
}

async function doEdit() {
  const u = editDialog.value.user
  const pid = pidOf(u); if (!pid) return
  opLoadingId.value = 'edit:'+pid
  try {
    const body = { ...editDialog.value.form }
    const { data } = await adminApi.updateUser(pid, body)
    replaceRow(normalize(data))
    snackIt('수정 저장 완료')
    editDialog.value.show = false
  } finally {
    opLoadingId.value = null
  }
}

/* ===== 공통 유틸 ===== */
function replaceRow(updated) {
  const idx = users.value.findIndex(r => r.userPid === updated.userPid)
  if (idx >= 0) users.value[idx] = updated
}

function formatDate(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 남은 시간 텍스트: "N시간 M분 남음" / "만료" / "없음" */
const now = ref(Date.now())
let timer = null
function remainText(u) {
  if (!u.lockedUntil) return '없음'
  const untilMs = new Date(u.lockedUntil).getTime()
  const diff = untilMs - now.value
  if (diff <= 0) return '만료'
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  if (h <= 0 && m > 0) return `${m}분 남음`
  if (m === 0) return `${h}시간 남음`
  return `${h}시간 ${m}분 남음`
}

/* ===== 스낵바 ===== */
const snack = ref({ show:false, msg:'' })
function snackIt(msg) { snack.value = { show:true, msg } }

onMounted(() => {
  load()
  // 매 30초마다 now 갱신 → 남은시간 자동 업데이트
  timer = setInterval(() => { now.value = Date.now() }, 30000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

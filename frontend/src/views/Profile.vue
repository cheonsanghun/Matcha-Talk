<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8" lg="7">
        <v-card class="pa-6">
          <div class="d-flex align-center ga-4">
            <v-avatar size="64" class="bg-pink-lighten-4">
              <v-icon>mdi-account</v-icon>
            </v-avatar>
            <div>
              <div class="text-subtitle-1">{{ displayName }}</div>
              <div class="text-caption">{{ form.email }}</div>
            </div>
            <v-spacer/>
            <v-btn color="pink" variant="tonal" @click="logout">로그아웃</v-btn>
          </div>

          <v-divider class="my-6" />

          <v-form ref="formRef" @submit.prevent="onSave">
            <v-row dense>
              <v-col cols="12" md="6">
                <v-text-field v-model="form.nickName" label="닉네임" variant="outlined" />
              </v-col>

              <v-col cols="12" md="6">
                <v-text-field v-model="form.email" label="이메일" variant="outlined" />
              </v-col>

              <v-col cols="12" md="4">
                <v-text-field v-model="form.countryCode" label="국가코드 (예: KR, JP)" variant="outlined" />
              </v-col>

              <v-col cols="12" md="4">
                <v-select
                  v-model="form.gender"
                  :items="['M','F','U']"
                  label="성별(M/F/U)"
                  variant="outlined"
                />
              </v-col>

              <v-col cols="12" md="4">
                <v-text-field
                  v-model="form.birthDate"
                  label="생년월일 (YYYY-MM-DD)"
                  placeholder="1999-05-09"
                  variant="outlined"
                />
              </v-col>
            </v-row>

            <div class="d-flex ga-3 mt-6">
              <v-btn color="primary" :loading="saving" type="submit">저장</v-btn>
              <v-btn variant="text" @click="resetForm" :disabled="saving">되돌리기</v-btn>
              <v-spacer/>
              <v-chip size="small" variant="tonal">로그인ID: {{ base.loginId }}</v-chip>
              <v-chip size="small" variant="tonal">권한: {{ base.roleName || 'ROLE_USER' }}</v-chip>
            </div>
          </v-form>
        </v-card>
      </v-col>
    </v-row>

    <v-snackbar v-model="snack.show" timeout="2000">
      {{ snack.msg }}
    </v-snackbar>
  </v-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/services/user'

/* === 로그인/유저 기본 정보 === */
const router = useRouter()
const store  = useAuthStore()
const { user } = storeToRefs(store) // UserSummary: { id, loginId, nickname, email }

const base = ref({ userPid:null, loginId:'', roleName:'' })
const form = ref({ nickName:'', email:'', countryCode:'', gender:'U', birthDate:'' })
const initial = ref(null)
const formRef = ref()
const saving = ref(false)
const snack  = ref({ show:false, msg:'' })

const displayName = computed(() => form.value.nickName || user.value?.nickname || 'Guest')

function logout () {
  store.logout()
  router.replace({ name: 'home' })
}

// 다양한 키를 userPid로 통일(관리자 화면과 동일 컨벤션)
function pidOf(u) { return u?.userPid ?? u?.user_pid ?? u?.id ?? u?.userId ?? null }

/* === 원복 === */
function resetForm() {
  if (!initial.value) return
  form.value = { ...initial.value }
}

/* === 저장 === */
async function onSave() {
  const id = base.value.userPid
  if (!id) return
  saving.value = true
  try {
    // 빈 문자열을 null로 정리(부분 수정)
    const body = Object.fromEntries(Object.entries(form.value).map(([k,v]) => [k, v === '' ? null : v]))
    const { data } = await userApi.update(id, body)
    // 서버 응답 반영
    initial.value = {
      nickName:    data.nickName,
      email:       data.email,
      countryCode: data.countryCode,
      gender:      data.gender,
      birthDate:   data.birthDate,
    }
    form.value = { ...initial.value }
    // 핀리아 저장된 요약도 갱신(닉네임/이메일만)
    const nextUser = { ...(user.value || {}), nickname: data.nickName, email: data.email, id }
    store.login({ token: store.token, user: nextUser })
    snack.value = { show:true, msg:'저장되었습니다.' }
  } catch (e) {
    const msg = e?.response?.data?.message || '저장에 실패했습니다.'
    snack.value = { show:true, msg }
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  // 1) auth store의 요약에서 pid를 얻고
  const id = pidOf(user.value)
  if (!id) {
    // 가드도 있지만 혹시 몰라서
    return router.replace({ name: 'login', query: { redirect: '/profile' } })
  }

  // 2) 서버에서 상세 조회
  const { data } = await userApi.get(id)

  base.value = {
    userPid : pidOf(data),
    loginId : data.loginId,
    roleName: data.roleName || 'ROLE_USER'
  }

  initial.value = {
    nickName:    data.nickName || user.value?.nickname || '',
    email:       data.email    || user.value?.email    || '',
    countryCode: data.countryCode || '',
    gender:      data.gender || 'U',
    birthDate:   data.birthDate || '',
  }
  form.value = { ...initial.value }
})
</script>

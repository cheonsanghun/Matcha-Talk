<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8" lg="6">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">나에게 맞는 인연 찾기</div>

          <v-sheet class="mb-6 pa-4 selection-box">
            <div class="text-subtitle-2 mb-2">나이 범위</div>
            <v-range-slider
              v-model="ageRange"
              :min="20"
              :max="99"
              :step="1"
              thumb-label
              color="pink"
              track-color="pink-lighten-4"
            />
            <div class="text-caption">{{ ageRange[0] }} - {{ ageRange[1] }} 세</div>
          </v-sheet>

          <v-sheet class="mb-6 pa-4 selection-box">
            <div class="text-subtitle-2 mb-2">성별</div>
            <v-btn-toggle v-model="gender" color="pink" class="w-100">
              <v-btn value="M" variant="outlined">남성</v-btn>
              <v-btn value="F" variant="outlined">여성</v-btn>
              <v-btn value="A" variant="outlined">상관없음</v-btn>
            </v-btn-toggle>
          </v-sheet>

          <v-sheet class="mb-6 pa-4 selection-box">
            <div class="text-subtitle-2 mb-2">희망 지역</div>
            <v-select
              v-model="region"
              :items="regions"
              item-title="title"
              item-value="value"
              variant="outlined"
              density="comfortable"
              color="pink"
              style="width: 100%"
              placeholder="지역을 선택하세요"
            />
          </v-sheet>

          <v-sheet class="mb-6 pa-4 selection-box text-center">
            <div class="text-subtitle-2 mb-2">관심사</div>
            <v-btn color="pink" variant="tonal" @click="dialog=true">관심사 선택</v-btn>
            <v-chip-group v-if="interests.length" class="mt-2" multiple>
              <v-chip
                v-for="i in interests"
                :key="i"
                class="ma-1"
                color="pink"
                variant="tonal"
              >{{ i }}</v-chip>
            </v-chip-group>
          </v-sheet>

          <v-btn
            color="pink"
            block
            size="large"
            :loading="loading"
            :disabled="loading || !isValid"
            @click="startMatch"
          >매칭 시작</v-btn>
          <div class="text-center text-caption mt-2">
            <span v-if="!isValid">필수 정보를 모두 입력하세요</span>
            <span v-else>매칭 시작 시 대기열에 입력합니다</span>
          </div>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog v-model="dialog" width="420">
      <v-card>
        <v-card-title>관심사 선택</v-card-title>
        <v-card-text>
          <v-sheet max-height="200" class="overflow-y-auto">
            <v-chip-group
              v-model="interests"
              multiple
              selected-class="bg-pink text-white"
            >
              <v-chip
                v-for="i in interestPool"
                :key="i"
                :value="i"
                class="ma-1"
                variant="outlined"
              >{{ i }}</v-chip>
            </v-chip-group>
          </v-sheet>
        </v-card-text>
        <v-card-actions>
          <v-spacer/>
          <v-btn color="pink" variant="tonal" @click="dialog=false">확인</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import api from '../services/api'
import { useRouter } from 'vue-router'

const router = useRouter()
const ageRange = ref([20, 30])
const gender = ref('A')
const regions = [
  { title: '서울', value: 'SEOUL' },
  { title: '부산', value: 'BUSAN' },
  { title: '도쿄', value: 'TOKYO' },
  { title: '오사카', value: 'OSAKA' },
  { title: '후쿠오카', value: 'FUKUOKA' },
  { title: '제주', value: 'JEJU' },
  { title: '기타', value: 'OTHER' }
]
const region = ref('')
const dialog = ref(false)
const interestPool = ['음악','영화','게임','여행','요리','운동','독서']
const interests = ref([])
const loading = ref(false)
const isValid = computed(() => !!gender.value && !!region.value && interests.value.length > 0)

async function startMatch(){
  if (!isValid.value) return
  loading.value = true
  const payload = {
    choice_gender: gender.value,
    min_age: ageRange.value[0],
    max_age: ageRange.value[1],
    region_code: region.value,
    interests_json: interests.value,
  }
  try{
    // await api.post('/match/requests', payload)
    router.push('/match/result')
  }catch(e){
    alert('매칭 실패: ' + (e?.response?.data?.message || e.message))
  }finally{
    loading.value = false
  }
}
</script>

<style scoped>
.selection-box {
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 8px;
}
.selection-box :deep(.v-btn--variant-outlined) {
  border-color: rgba(0, 0, 0, 0.2);
}
.selection-box :deep(.v-field__outline) {
  border-color: rgba(0, 0, 0, 0.2);
}
</style>

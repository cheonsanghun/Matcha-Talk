<template>
  <v-container class="py-10 bg-pink-lighten-5">
    <v-row justify="center">
      <v-col cols="12" md="6">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">회원가입</div>

          <v-alert v-if="submitError" type="error" variant="tonal" class="mb-4">
            {{ submitError }}
          </v-alert>

          <v-form @submit.prevent="onSubmit">
            <v-text-field
              v-model="form.nickName"
              label="이름"
              variant="outlined"
              class="mb-4"
              :error-messages="errors.nickName"
              :disabled="submitting"
              @blur="validate('nickName')"
            />

            <div class="d-flex align-end mb-4">
              <v-text-field
                v-model="form.loginId"
                label="아이디"
                variant="outlined"
                class="flex-grow-1 me-2"
                :error-messages="errors.loginId"
                :disabled="submitting"
                @blur="validate('loginId')"
              />
              <v-btn
                variant="outlined"
                color="pink"
                @click="checkLoginId"
                :disabled="submitting || !form.loginId"
              >
                중복 확인
              </v-btn>
            </div>
            <div class="text-caption text-green-darken-2 mb-2" v-if="loginIdAvailable">
              사용 가능한 아이디입니다.
            </div>

            <div class="d-flex align-end mb-4">
              <v-text-field
                v-model="form.email"
                label="이메일"
                variant="outlined"
                class="flex-grow-1 me-2"
                :error-messages="errors.email"
                :disabled="emailVerified || submitting"
                @blur="validate('email')"
              />
              <v-btn
                variant="outlined"
                color="pink"
                @click="requestEmailVerify"
                :disabled="emailVerified || submitting || !form.email"
              >
                이메일 인증
              </v-btn>
            </div>

            <div class="d-flex align-end mb-4" v-if="verificationSent && !emailVerified">
              <v-text-field
                v-model="verificationCode"
                label="인증번호"
                variant="outlined"
                class="flex-grow-1 me-2"
                :disabled="submitting"
              />
              <v-btn variant="outlined" color="pink" @click="confirmEmailVerify" :disabled="submitting || !verificationCode">
                확인
              </v-btn>
            </div>
            <div class="text-caption text-green-darken-2 mb-4" v-if="emailVerified">
              이메일 인증 완료
            </div>

            <v-text-field
              v-model="form.password"
              type="password"
              label="비밀번호"
              variant="outlined"
              class="mb-4"
              :error-messages="errors.password"
              :disabled="submitting"
              @blur="validate('password')"
            />

            <v-text-field
              v-model="form.confirmPassword"
              type="password"
              label="비밀번호 확인"
              variant="outlined"
              class="mb-4"
              :error-messages="errors.confirmPassword"
              :disabled="submitting"
              @blur="validate('confirmPassword')"
            />

            <div class="mb-4">
              <div class="d-flex" style="gap: 8px;">
                <v-select
                  v-model="birth.year"
                  :items="yearItems"
                  label="년도"
                  variant="outlined"
                  class="flex-grow-1"
                  :disabled="submitting"
                  @blur="validate('birth')"
                />
                <v-select
                  v-model="birth.month"
                  :items="monthItems"
                  label="월"
                  variant="outlined"
                  class="flex-grow-1"
                  :disabled="submitting"
                  @blur="validate('birth')"
                />
                <v-select
                  v-model="birth.day"
                  :items="dayItems"
                  label="일"
                  variant="outlined"
                  class="flex-grow-1"
                  :disabled="submitting"
                  @blur="validate('birth')"
                />
              </div>
              <span class="text-caption text-pink-darken-2">{{ errors.birth }}</span>
            </div>

            <v-select
              v-model="form.gender"
              :items="genderItems"
              label="성별"
              variant="outlined"
              class="mb-4"
              :error-messages="errors.gender"
              :disabled="submitting"
              @blur="validate('gender')"
            />

            <v-select
              v-model="form.languageCode"
              :items="languageItems"
              item-title="title"
              item-value="value"
              label="선호 언어"
              variant="outlined"
              class="mb-4"
              :error-messages="errors.languageCode"
              :disabled="submitting"
              @blur="validate('languageCode')"
            />

            <v-select
              v-model="form.countryCode"
              :items="countryItems"
              item-title="title"
              item-value="value"
              label="국적"
              variant="outlined"
              class="mb-6"
              :error-messages="errors.countryCode"
              :disabled="submitting"
              @blur="validate('countryCode')"
            />
            <v-btn type="submit" color="pink" block :disabled="!isValid || submitting" :loading="submitting">
              회원가입
            </v-btn>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '../services/api'

const router = useRouter()

const form = reactive({
  loginId: '',
  password: '',
  confirmPassword: '',
  nickName: '',
  email: '',
  countryCode: '',
  gender: '',
  languageCode: 'ko',
})

const birth = reactive({
  year: '',
  month: '',
  day: '',
})

const errors = reactive({
  nickName: '',
  loginId: '',
  email: '',
  password: '',
  confirmPassword: '',
  birth: '',
  gender: '',
  languageCode: '',
  countryCode: '',
})

const verificationCode = ref('')
const verificationSent = ref(false)
const emailVerified = ref(false)
const loginIdAvailable = ref(false)
const submitting = ref(false)
const submitError = ref('')

const currentYear = new Date().getFullYear()
const yearItems = Array.from({ length: 70 }, (_, index) => `${currentYear - index}`)
const monthItems = Array.from({ length: 12 }, (_, index) => `${index + 1}`.padStart(2, '0'))
const dayItems = Array.from({ length: 31 }, (_, index) => `${index + 1}`.padStart(2, '0'))

const genderItems = [
  { title: '남성', value: 'M' },
  { title: '여성', value: 'F' },
]

const languageItems = [
  { title: '한국어', value: 'ko' },
  { title: '일본어', value: 'ja' },
]

const countryItems = [
  { title: '대한민국', value: 'KR' },
  { title: '일본', value: 'JP' },
  { title: '미국', value: 'US' },
  { title: '영국', value: 'GB' },
]

const isValid = computed(() => {
  return (
    form.loginId &&
    form.password &&
    form.confirmPassword &&
    form.nickName &&
    form.email &&
    form.gender &&
    form.languageCode &&
    form.countryCode &&
    birth.year &&
    birth.month &&
    birth.day &&
    !Object.values(errors).some((value) => value)
  )
})

const validate = (field) => {
  switch (field) {
    case 'nickName':
      errors.nickName = form.nickName ? '' : '닉네임을 입력하세요.'
      break;
    case 'loginId':
      errors.loginId = form.loginId ? '' : '아이디를 입력하세요.'
      loginIdAvailable.value = false
      break
    case 'email':
      errors.email = /.+@.+/.test(form.email) ? '' : '유효한 이메일을 입력하세요.'
      emailVerified.value = false
      verificationSent.value = false
      break
    case 'password':
      errors.password =
        form.password.length >= 8 ? '' : '비밀번호는 8자 이상이어야 합니다.'
      break
    case 'confirmPassword':
      errors.confirmPassword =
        form.confirmPassword === form.password ? '' : '비밀번호가 일치하지 않습니다.'
      break
    case 'birth':
      errors.birth = birth.year && birth.month && birth.day ? '' : '생년월일을 선택하세요.'
      break
    case 'gender':
      errors.gender = form.gender ? '' : '성별을 선택하세요.'
      break
    case 'languageCode':
      errors.languageCode = form.languageCode ? '' : '선호 언어를 선택하세요.'
      break
    case 'countryCode':
      errors.countryCode = form.countryCode ? '' : '국적을 선택하세요.'
      break
    default:
      break
  }
}

const checkLoginId = async () => {
  validate('loginId')
  if (errors.loginId) return
  try {
    const { exists } = await userApi.checkLoginId(form.loginId)
    loginIdAvailable.value = !exists
    errors.loginId = exists ? '이미 사용 중인 아이디입니다.' : ''
  } catch (error) {
    errors.loginId = error?.message ?? '아이디 중복 확인 중 오류가 발생했습니다.'
  }
}

const requestEmailVerify = async () => {
  validate('email')
  if (errors.email) return
  try {
    await userApi.requestEmailVerification(form.email)
    verificationSent.value = true
    submitError.value = ''
  } catch (error) {
    submitError.value = error?.message ?? '이메일 인증 요청에 실패했습니다.'
  }
}

const confirmEmailVerify = async () => {
  if (!verificationCode.value) {
    submitError.value = '인증번호를 입력하세요.'
    return
  }
  try {
    await userApi.confirmEmailVerification(form.email, verificationCode.value)
    emailVerified.value = true
    submitError.value = ''
  } catch (error) {
    submitError.value = error?.message ?? '인증번호가 올바르지 않습니다.'
  }
}

const buildBirthDate = () => `${birth.year}-${birth.month}-${birth.day}`

const onSubmit = async () => {
  Object.keys(errors).forEach((key) => validate(key))
  validate('birth')

  if (!isValid.value) {
    submitError.value = '필수 항목을 모두 입력하고 오류를 수정하세요.'
    return
  }

  if (!emailVerified.value) {
    submitError.value = '이메일 인증을 완료하세요.'
    return
  }

  submitting.value = true
  submitError.value = ''

  const payload = {
    loginId: form.loginId,
    password: form.password,
    confirmPassword: form.confirmPassword,
    nickName: form.nickName,
    email: form.email,
    countryCode: form.countryCode,
    gender: form.gender,
    birthDate: buildBirthDate(),
    languageCode: form.languageCode,
    verificationCode: verificationCode.value,
  }

  try {
    await userApi.register(payload)
    router.replace({ name: 'login', query: { registered: '1' } })
  } catch (error) {
    if (error?.errors) {
      Object.entries(error.errors).forEach(([field, message]) => {
        if (errors[field] !== undefined) {
          errors[field] = Array.isArray(message) ? message.join(', ') : message
        }
      })
    }
    submitError.value = error?.message ?? '회원가입에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

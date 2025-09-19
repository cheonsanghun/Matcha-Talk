<template>
  <v-container class="py-10 bg-pink-lighten-5">
    <v-row justify="center">
      <v-col cols="12" md="6">
        <v-card class="pa-8">
          <div class="text-center text-h6 text-pink-darken-2 mb-6">회원가입</div>

          <v-form @submit.prevent="onSubmit">
            <v-text-field
                v-model="form.nick_name"
                label="이름"
                variant="outlined"
                class="mb-4"
                :error-messages="errors.nick_name"
                @blur="validate('nick_name')"
            />

            <div class="d-flex align-end mb-4">
              <v-text-field
                  v-model="form.loginId"
                  label="아이디"
                  variant="outlined"
                  class="flex-grow-1 me-2"
                  :error-messages="errors.loginId"
                  @blur="validate('loginId')"
              />
              <v-btn variant="outlined" color="pink" @click="checkLoginId" :disabled="loginIdAvailable">중복 확인</v-btn>
            </div>

            <div class="d-flex align-end mb-4">
              <v-text-field
                  v-model="form.email"
                  label="이메일"
                  variant="outlined"
                  class="flex-grow-1 me-2"
                  :error-messages="errors.email"
                  @blur="validate('email')"
                  :disabled="emailVerified"
              />
              <v-btn
                  variant="outlined"
                  color="pink"
                  @click="requestEmailVerify"
                  :disabled="emailVerified"
              >이메일 인증
              </v-btn>
            </div>

            <div class="d-flex align-end mb-4" v-if="verificationSent && !emailVerified">
              <v-text-field
                  v-model="verificationCode"
                  label="인증번호"
                  variant="outlined"
                  class="flex-grow-1 me-2"
              />
              <v-btn variant="outlined" color="pink" @click="confirmEmailVerify">확인</v-btn>
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
                @blur="validate('password')"
            />

            <v-text-field
                v-model="form.password2"
                type="password"
                label="비밀번호 확인"
                variant="outlined"
                class="mb-4"
                :error-messages="errors.password2"
                @blur="validate('password2')"
            />

            <div class="mb-4">
              <div class="d-flex" style="gap: 8px;">
                <v-select
                    v-model="birth.year"
                    :items="yearItems"
                    label="년도"
                    variant="outlined"
                    class="flex-grow-1"
                    @blur="validate('birth')"
                />
                <v-select
                    v-model="birth.month"
                    :items="monthItems"
                    label="월"
                    variant="outlined"
                    class="flex-grow-1"
                    @blur="validate('birth')"
                />
                <v-select
                    v-model="birth.day"
                    :items="dayItems"
                    label="일"
                    variant="outlined"
                    class="flex-grow-1"
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
                @blur="validate('gender')"
            />

            <v-select
                v-model="form.language_code"
                :items="languageItems"
                item-title="title"
                item-value="value"
                label="선호 언어"
                variant="outlined"
                class="mb-4"
                :error-messages="errors.language_code"
                @blur="validate('language_code')"
            />

            <v-select
                v-model="form.country_code"
                :items="countryItems"
                label="국적"
                variant="outlined"
                class="mb-6"
                :error-messages="errors.country_code"
                @blur="validate('country_code')"
            />
            <v-btn type="submit" color="pink" block :disabled="!valid">회원가입</v-btn>
          </v-form>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>

</script>

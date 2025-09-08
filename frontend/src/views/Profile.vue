<template>
  <v-container class="py-10">
    <v-row justify="center">
      <v-col cols="12" md="8">
        <v-card class="pa-6">
          <div class="d-flex align-center ga-4">
            <v-avatar size="64" class="bg-pink-lighten-4"><v-icon color="pink">mdi-account</v-icon></v-avatar>
            <div>
              <div class="text-subtitle-1">{{ user?.nick_name || 'Guest' }}</div>
              <div class="text-caption">{{ user?.email }}</div>
            </div>
            <v-spacer/>
            <v-btn color="pink" variant="tonal" @click="logout" to="/">로그아웃</v-btn>
          </div>
          <v-divider class="my-4"/>
          <div class="text-caption">프로필/친구/차단/언어 설정 등은 추후 확장</div>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup>
import { useAuthStore } from '../stores/auth'
import { storeToRefs } from 'pinia'
const store = useAuthStore()
const { user } = storeToRefs(store)
function logout(){ store.logout() }
</script>

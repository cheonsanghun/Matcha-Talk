<template>
  <v-container class="py-6">
    <v-tabs v-model="tab" color="pink">
      <v-tab value="inquiries">문의/신고</v-tab>
      <v-tab value="users">계정 관리</v-tab>
    </v-tabs>

    <v-window v-model="tab">
      <v-window-item value="inquiries">
        <v-card class="mt-4">
          <v-card-title>문의/신고 목록</v-card-title>
          <v-data-table :items="inquiries" :headers="inquiryHeaders" class="elevation-1">
            <template #item.actions="{ item }">
              <v-btn size="small" @click="viewInquiry(item)">상세보기</v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-window-item>

      <v-window-item value="users">
        <v-card class="mt-4">
          <v-card-title>사용자 관리</v-card-title>
          <v-data-table :items="users" :headers="userHeaders" class="elevation-1">
            <template #item.actions="{ item }">
              <v-btn size="small" color="red" v-if="!item.suspended" @click="suspendUser(item)">정지</v-btn>
              <v-btn size="small" color="green" v-else @click="unsuspendUser(item)">해제</v-btn>
              <v-btn size="small" @click="editUser(item)">수정</v-btn>
            </template>
          </v-data-table>
        </v-card>
      </v-window-item>
    </v-window>
  </v-container>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAdminStore } from '../../stores/admin'

const adminStore = useAdminStore()
const { inquiries, users } = storeToRefs(adminStore)
const tab = ref('inquiries')

const inquiryHeaders = [
  { title: '제목', key: 'title' },
  { title: '분류', key: 'category' },
  { title: '상태', key: 'status' },
  { title: '작성일', key: 'createdAt' },
  { title: '작업', key: 'actions', sortable: false },
]

const userHeaders = [
  { title: '아이디', key: 'loginId' },
  { title: '이메일', key: 'email' },
  { title: '권한', key: 'roleName' },
  { title: '정지 여부', key: 'suspended' },
  { title: '작업', key: 'actions', sortable: false },
]

const viewInquiry = (item) => {
  window.alert(`문의 상세보기\n\n${item.title}`)
}

const suspendUser = (item) => {
  adminStore.suspendUser(item)
}

const unsuspendUser = (item) => {
  adminStore.unsuspendUser(item)
}

const editUser = (item) => {
  console.log('edit user', item)
}

onMounted(() => {
  adminStore.initialize()
})
</script>
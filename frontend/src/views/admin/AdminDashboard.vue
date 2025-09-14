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
import { ref } from 'vue'

const tab = ref('inquiries')

const inquiries = ref([
  { id: 1, type: '문의', content: '사용 방법 문의', status: '대기' },
  { id: 2, type: '신고', content: '스팸 신고', status: '처리중' },
])

const inquiryHeaders = [
  { title: 'ID', key: 'id' },
  { title: '유형', key: 'type' },
  { title: '내용', key: 'content' },
  { title: '상태', key: 'status' },
  { title: '작업', key: 'actions', sortable: false },
]

const users = ref([
  { id: 1, nickname: 'user1', role: 'USER', suspended: false },
  { id: 2, nickname: 'user2', role: 'USER', suspended: true },
])

const userHeaders = [
  { title: 'ID', key: 'id' },
  { title: '닉네임', key: 'nickname' },
  { title: '역할', key: 'role' },
  { title: '정지여부', key: 'suspended' },
  { title: '작업', key: 'actions', sortable: false },
]

function viewInquiry(item) {
  alert(`${item.type} #${item.id}: ${item.content}`)
}

function suspendUser(item) {
  item.suspended = true
}

function unsuspendUser(item) {
  item.suspended = false
}

function editUser(item) {
  alert(`${item.nickname} 정보 수정`)
}
</script>
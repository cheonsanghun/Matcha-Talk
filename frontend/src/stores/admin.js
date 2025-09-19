import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminApi } from '../services/api'

const mockInquiries = [
  {
    id: 1,
    title: '신고: 스팸 메시지',
    category: 'REPORT',
    status: 'OPEN',
    createdAt: '2024-07-01T10:00:00Z',
  },
  {
    id: 2,
    title: '결제 관련 문의',
    category: 'QUESTION',
    status: 'IN_PROGRESS',
    createdAt: '2024-07-02T12:30:00Z',
  },
]

const mockUsers = [
  {
    id: 10,
    loginId: 'matcha_master',
    email: 'master@matchatalk.com',
    roleName: 'ROLE_ADMIN',
    suspended: false,
  },
  {
    id: 11,
    loginId: 'hana',
    email: 'hana@example.com',
    roleName: 'ROLE_USER',
    suspended: false,
  },
  {
    id: 12,
    loginId: 'sato',
    email: 'sato@example.com',
    roleName: 'ROLE_USER',
    suspended: true,
  },
]

export const useAdminStore = defineStore('admin', () => {
  const inquiries = ref([])
  const users = ref([])
  const loading = ref(false)
  const error = ref(null)

  const fetchInquiries = async () => {
    loading.value = true
    error.value = null
    try {
      const data = await adminApi.fetchInquiries()
      if (Array.isArray(data) && data.length) {
        inquiries.value = data
      } else {
        inquiries.value = mockInquiries
      }
    } catch (err) {
      error.value = err?.message ?? '문의 목록을 불러오지 못했습니다.'
      inquiries.value = mockInquiries
    } finally {
      loading.value = false
    }
  }

  const fetchUsers = async () => {
    loading.value = true
    error.value = null
    try {
      const data = await adminApi.fetchUsers()
      if (Array.isArray(data) && data.length) {
        users.value = data
      } else {
        users.value = mockUsers
      }
    } catch (err) {
      error.value = err?.message ?? '사용자 목록을 불러오지 못했습니다.'
      users.value = mockUsers
    } finally {
      loading.value = false
    }
  }

  const suspendUser = (user) => {
    user.suspended = true
  }

  const unsuspendUser = (user) => {
    user.suspended = false
  }

  const initialize = async () => {
    await Promise.all([fetchInquiries(), fetchUsers()])
  }

  return {
    inquiries,
    users,
    loading,
    error,
    fetchInquiries,
    fetchUsers,
    suspendUser,
    unsuspendUser,
    initialize,
  }
})

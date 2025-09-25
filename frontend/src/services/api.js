// frontend/src/services/api.js
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  // 토큰(Bearer)로 인증하면 쿠키는 보낼 필요 없으니 false.
  // 세션/쿠키 인증을 같이 쓰면 true 로 바꿔.
  withCredentials: false,
})

api.interceptors.request.use((config) => {
  // Pinia에서 먼저 읽고, 초기화 전이면 localStorage로 폴백
  let token
  try {
    const store = useAuthStore()
    token = store?.token
  } catch (_) {
    token = null
  }
  if (!token) token = localStorage.getItem('token')

  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default api

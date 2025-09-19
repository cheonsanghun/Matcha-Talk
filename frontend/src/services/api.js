import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

const toSnakeCase = (value) =>
  value
    .replace(/([A-Z])/g, '_$1')
    .replace(/[\s-]+/g, '_')
    .replace(/__+/g, '_')
    .toLowerCase()

const isPlainObject = (value) =>
  Object.prototype.toString.call(value) === '[object Object]'

const snakeCaseKeys = (input) => {
  if (Array.isArray(input)) {
    return input.map((item) => snakeCaseKeys(item))
  }

  if (!input || !isPlainObject(input)) {
    return input
  }

  return Object.entries(input).reduce((acc, [key, value]) => {
    const normalizedKey = typeof key === 'string' ? toSnakeCase(key) : key
    acc[normalizedKey] = snakeCaseKeys(value)
    return acc
  }, {})
}

const shouldTransform = (value) => Array.isArray(value) || isPlainObject(value)

let accessToken = null
let guestPid = null

export const setAuthToken = (token) => {
  accessToken = token || null
}

export const getAccessToken = () => accessToken

export const clearAuthToken = () => {
  accessToken = null
}

export const setGuestPid = (pid) => {
  guestPid = pid ?? null
}

export const getGuestPid = () => guestPid

const buildError = (error) => {
  const status = error?.response?.status
  const data = error?.response?.data
  if (data && typeof data === 'object') {
    const message = data.message || data.error || error.message || '요청 처리 중 오류가 발생했습니다.'
    return Promise.reject({ ...data, status, message })
  }
  const message =
    typeof data === 'string'
      ? data
      : error.message || '요청 처리 중 오류가 발생했습니다.'
  return Promise.reject({ status, message })
}

api.interceptors.request.use((config) => {
  config.headers = config.headers ?? {}
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  } else if (guestPid && !config.headers['X-USER-PID']) {
    config.headers['X-USER-PID'] = guestPid
  }
  if (shouldTransform(config.data)) {
    config.data = snakeCaseKeys(config.data)
  }
  if (shouldTransform(config.params)) {
    config.params = snakeCaseKeys(config.params)
  }
  return config
})

api.interceptors.response.use((response) => response, buildError)

const unwrap = (response) => response.data

export const authApi = {
  /**
   * 로그인 요청. 서버 스펙에 따라 UserSummary 또는 { accessToken, user } 형태를 모두 허용한다.
   */
  login(payload) {
    return api.post('/auth/login', payload).then(unwrap)
  },
}

export const userApi = {
  register(payload) {
    return api.post('/users/signup', payload).then(unwrap)
  },
  fetchProfile() {
    return api.get('/users/profile').then(unwrap)
  },
  checkLoginId(loginId) {
    return api
      .get('/users/exists', { params: { loginId } })
      .then(unwrap)
  },
  checkEmail(email) {
    return api
      .get('/users/exists', { params: { email } })
      .then(unwrap)
  },
  requestEmailVerification(email) {
    return api.post('/users/email/verify/request', { email }).then(unwrap)
  },
  confirmEmailVerification(email, token) {
    return api
      .post('/users/email/verify/confirm', { email, token })
      .then(unwrap)
  },
}

export const matchApi = {
  start(request) {
    return api.post('/match/requests', request).then(unwrap)
  },
  accept(requestId) {
    return api.post(`/match/requests/${requestId}/accept`).then(unwrap)
  },
  decline(requestId) {
    return api.post(`/match/requests/${requestId}/decline`).then(unwrap)
  },
}

export const chatApi = {
  fetchRooms() {
    return api.get('/rooms').then(unwrap)
  },
  fetchMessages(roomId) {
    return api.get(`/rooms/${roomId}/messages`).then(unwrap)
  },
  createGroupRoom() {
    return api.post('/rooms').then(unwrap)
  },
}

export const adminApi = {
  fetchInquiries() {
    return api.get('/admin/inquiries').then(unwrap)
  },
  fetchUsers() {
    return api.get('/admin/users').then(unwrap)
  },
}

export default api

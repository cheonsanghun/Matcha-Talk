import axios from 'axios'
import { camelizeKeys, snakifyKeys, isTransformable } from '../utils/case'
import { API_BASE_URL } from './endpoints'
import router from '../router'
import { useAuthStore } from '../stores/auth'

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`

  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (user?.id) config.headers['X-USER-PID'] = user.id

  const { skipSnakifyParams } = config

  if (config.params && !skipSnakifyParams && isTransformable(config.params)) {
    config.params = snakifyKeys(config.params)
  }

  if (config.data && isTransformable(config.data)) {
    config.data = snakifyKeys(config.data)
  }

  if (skipSnakifyParams) {
    delete config.skipSnakifyParams
  }

  return config
})

api.interceptors.response.use(
  (response) => {
    if (response?.data && isTransformable(response.data)) {
      response.data = camelizeKeys(response.data)
    }
    return response
  },
  (error) => {
    if (error?.response?.data && isTransformable(error.response.data)) {
      error.response.data = camelizeKeys(error.response.data)
    }

    if (error?.response?.status === 401) {
      const auth = useAuthStore()
      if (auth.isAuthenticated || auth.hasUserSnapshot) {
        auth.logout()
        if (router.currentRoute.value.name !== 'login') {
          router.push({ name: 'login', query: { expired: '1' } }).catch(() => {})
        }
      }
    }
    return Promise.reject(error)
  }
)

export default api

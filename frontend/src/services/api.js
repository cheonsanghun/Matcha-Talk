import axios from 'axios'
import { camelizeKeys, snakifyKeys, isTransformable } from '../utils/case'
import { useAuthStore } from '../stores/auth'


const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
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

    const status = error?.response?.status
    const message = error?.response?.data?.message
    if (
        status === 401 &&
        (message === undefined || message === '인증이 필요한 요청입니다.')
    ) {
      try {
        const auth = useAuthStore()
        auth?.logout?.()
      } catch (_) {
        // ignore, fall back to manual clearing below
      }

      localStorage.removeItem('token')
      localStorage.removeItem('user')

      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default api

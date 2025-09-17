import axios from 'axios'
import { camelizeKeys, snakifyKeys, isTransformable } from '../utils/case'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`

  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (user?.id) config.headers['X-USER-PID'] = user.id

  if (config.params && isTransformable(config.params)) {
    config.params = snakifyKeys(config.params)
  }

  if (config.data && isTransformable(config.data)) {
    config.data = snakifyKeys(config.data)
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
    return Promise.reject(error)
  }
)

export default api

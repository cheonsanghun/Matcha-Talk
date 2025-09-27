import axios from 'axios'
import { camelizeKeys, snakifyKeys, isTransformable } from '../utils/case'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

api.interceptors.request.use((config) => {
  if (config.params && !config.skipSnakifyParams && isTransformable(config.params)) {
    config.params = snakifyKeys(config.params)
  }

  if (config.data && isTransformable(config.data)) {
    config.data = snakifyKeys(config.data)
  }

  if (config.skipSnakifyParams) {
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
    return Promise.reject(error)
  }
)

export default api

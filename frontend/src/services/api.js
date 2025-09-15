import axios from 'axios'
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
})
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (user?.id) config.headers['X-USER-PID'] = user.id
  return config
})
export default api

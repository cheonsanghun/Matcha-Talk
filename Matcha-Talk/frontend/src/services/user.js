import api from './api'

export const userApi = {
  get:    (id)        => api.get(`/users/${id}`),
  update: (id, body)  => api.patch(`/users/${id}`, body),
}

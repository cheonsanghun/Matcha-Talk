import api from './api'

export default {
  // 특정 사용자의 팔로잉 목록 조회
  getFollowingList(userId) {
    return api.get(`/users/${userId}/following`)
  },

  async getFollowing(userId) {
    const response = await this.getFollowingList(userId)
    const payload = response?.data ?? response
    return Array.isArray(payload) ? payload : []
  },

  // 특정 사용자의 팔로워 목록 조회
  getFollowerList(userId) {
    return api.get(`/users/${userId}/followers`)
  },

  async getFollowers(userId) {
    const response = await this.getFollowerList(userId)
    const payload = response?.data ?? response
    return Array.isArray(payload) ? payload : []
  },

  // 팔로우 요청 생성
  requestFollow(followeeId) {
    return api.post('/follows', { followeeId })
  },

  // 팔로우 요청 수락
  acceptFollow(followId) {
    return api.put(`/follows/${followId}/status`, { status: 'ACCEPTED' })
  },

  // 팔로우 요청 거절
  rejectFollow(followId) {
    return api.put(`/follows/${followId}/status`, { status: 'REJECTED' })
  },

  // 팔로우 관계 삭제 (언팔로우)
  unfollow(followId) {
    return api.delete(`/follows/${followId}`)
  },
}

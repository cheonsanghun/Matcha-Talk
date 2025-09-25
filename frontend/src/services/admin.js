// src/services/admin.js
import api from '@/services/api'

/**
 * 관리자 API 모듈
 * 모든 함수는 Axios Promise 를 반환합니다(then/await로 data 사용).
 */
export const adminApi = {
  // ---------------------------
  // Users
  // ---------------------------

  /** GET /api/admin/users?q=keyword */
  listUsers: (q) =>
    api.get('/admin/users', { params: { q } }),

  /** PATCH /api/admin/users/{id}  { nickName?, email?, roleName? } */
  updateUser: (id, body) =>
    api.patch(`/admin/users/${id}`, body),

  /** POST /api/admin/users/{id}/lock  { minutes } */
  lockUser: (id, minutes) =>
    api.post(`/admin/users/${id}/lock`, { minutes }),

  /** PATCH /api/admin/users/{id}/enable  { enabled } */
  enableUser: (id, enabled) =>
    api.patch(`/admin/users/${id}/enable`, { enabled }),

  /** GET /api/admin/penalties?userPid=... */
  penaltiesOf: (userPid) =>
    api.get('/admin/penalties', { params: { userPid } }),

  // ---------------------------
  // Inquiries
  // ---------------------------

  /** GET /api/admin/inquiries?status=OPEN|ANSWERED|CLOSED */
  listInquiries: (status) =>
    api.get('/admin/inquiries', { params: { status } }),

  /** POST /api/admin/inquiries/{id}/answer  { answer } */
  answerInquiry: (id, answer) =>
    api.post(`/admin/inquiries/${id}/answer`, { answer }),

  /** POST /api/admin/inquiries/{id}/close  (no body) */
  closeInquiry: (id) =>
    api.post(`/admin/inquiries/${id}/close`),

  // ---------------------------
  // Reports
  // ---------------------------

  /** GET /api/admin/reports?status=OPEN|REVIEWING|ACTIONED|DISMISSED */
  listReports: (status) =>
    api.get('/admin/reports', { params: { status } }),

  /**
   * POST /api/admin/reports/{id}/action
   * body: { penaltyType: 'WARN'|'SUSPEND'|'BAN', days?: number, reason?: string }
   * - SUSPEND가 아닐 땐 days는 보내지 않아도 됩니다.
   */
  actionReport: (id, { penaltyType, days, reason } = {}) =>
    api.post(`/admin/reports/${id}/action`, { penaltyType, days, reason }),

  /** POST /api/admin/reports/{id}/dismiss  { reason } */
  dismissReport: (id, reason) =>
    api.post(`/admin/reports/${id}/dismiss`, { reason }),
}

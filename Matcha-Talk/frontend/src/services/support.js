import api from './api'

export const supportApi = {
  createInquiry: (payload) => api.post('/support/inquiries', payload),
  myInquiries:   (userPid)  => api.get('/support/inquiries', { params: { userPid } }),

  createReport:        (payload) => api.post('/support/reports', payload),              // 기존 PID 방식
  createReportByLogin: (payload) => api.post('/support/reports/by-login', payload),    // ✅ 아이디 방식
  myReports:     (reporterPid) => api.get('/support/reports', { params: { reporterPid } }),
}

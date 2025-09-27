import api from './api'

export const supportApi = {
  createInquiry: ({ category, title, content }) =>
    api.post('/support/inquiries', { category, title, content }),
  myInquiries:   () => api.get('/support/inquiries'),

  createReport: ({ reportedPid, reason, detail }) =>
    api.post('/support/reports', { reportedPid, reason, detail }),
  createReportByLogin: ({ reportedLoginId, reason, detail }) =>
    api.post('/support/reports/by-login', { reportedLoginId, reason, detail }),
  myReports: () => api.get('/support/reports'),
}

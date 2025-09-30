// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue') },
  { path: '/find-id', name: 'find-id', component: () => import('../views/FindId.vue') },
  { path: '/reset-password', name: 'reset-password', component: () => import('../views/ResetPassword.vue') },

  { path: '/profile', name: 'profile', component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },

  { path: '/support/inquiry', name: 'support-inquiry', component: () => import('../views/SupportInquiry.vue'), meta: { requiresAuth: true } },
  { path: '/support/report', name: 'support-report', component: () => import('../views/SupportReport.vue'), meta: { requiresAuth: true } },

  { path: '/match', name: 'match', component: () => import('../views/MatchingSetup.vue'), meta: { requiresAuth: true } },
  { path: '/match/result', name: 'match-result', component: () => import('../views/MatchingResult.vue'), meta: { requiresAuth: true } },
  { path: '/match/session', name: 'match-session', component: () => import('../views/MatchSession.vue'), meta: { requiresAuth: true } },
  { path: '/chat', name: 'chat', component: () => import('../views/Chat.vue'), meta: { requiresAuth: true } },
  { path: '/rtc-test', name: 'rtc-test', component: () => import('../views/RtcTest.vue'), meta: { requiresAuth: true } },
  { path: '/vocabulary', name: 'vocabulary', component: () => import('../views/Vocabulary.vue'), meta: { requiresAuth: true } },

  { path: '/admin', name: 'admin-home', component: () => import('../views/admin/AdminHome.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/users', name: 'admin-users', component: () => import('../views/admin/AdminUsers.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/inquiries', name: 'admin-inquiries', component: () => import('../views/admin/AdminInquiries.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/reports', name: 'admin-reports', component: () => import('../views/admin/AdminReports.vue'), meta: { requiresAuth: true, requiresAdmin: true } },

  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  if (to.name === 'login' && auth.isAuthenticated && !to.query.force) {
    return next({ name: 'home', replace: true })
  }

  if (to.meta?.requiresAuth && !auth.isAuthenticated) {
    return next({ name: 'login', replace: true, query: { redirect: to.fullPath } })
  }

  if (to.meta?.requiresAdmin && !auth.isAdmin) {
    return next({ name: 'home', replace: true })
  }

  next()
})

export default router

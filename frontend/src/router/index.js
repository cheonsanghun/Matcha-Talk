// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// 라우트 테이블
const routes = [
  // 홈/인증
  { path: '/',               name: 'home',            component: () => import('../views/Home.vue') },
  { path: '/login',          name: 'login',           component: () => import('../views/Login.vue') },
  { path: '/register',       name: 'register',        component: () => import('../views/Register.vue') },

  // 아이디 찾기 / 비번 재설정
  { path: '/find-id',        name: 'find-id',         component: () => import('../views/FindId.vue') },
  { path: '/reset-password', name: 'reset-password',  component: () => import('../views/ResetPassword.vue') },

  // 프로필 (로그인 필요)
  { path: '/profile',        name: 'profile',         component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },

  // ✅ 사용자용 문의/신고 (로그인 필요)
  { path: '/support/inquiry', name: 'support-inquiry', component: () => import('../views/SupportInquiry.vue'), meta: { requiresAuth: true } },
  { path: '/support/report',  name: 'support-report',  component: () => import('../views/SupportReport.vue'),  meta: { requiresAuth: true } },

  // 관리자 (관리자만)
  { path: '/admin',            name: 'admin',            component: () => import('../views/admin/AdminHome.vue'),       meta: { requiresAdmin: true } },
  { path: '/admin/users',      name: 'admin-users',      component: () => import('../views/admin/AdminUsers.vue'),      meta: { requiresAdmin: true } },
  { path: '/admin/inquiries',  name: 'admin-inquiries',  component: () => import('../views/admin/AdminInquiries.vue'),  meta: { requiresAdmin: true } },
  { path: '/admin/reports',    name: 'admin-reports',    component: () => import('../views/admin/AdminReports.vue'),    meta: { requiresAdmin: true } },

  // ⚠️ catch-all은 반드시 맨 마지막
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

// 라우터 인스턴스
const router = createRouter({
  // 배포 서브경로가 있으면 import.meta.env.BASE_URL 넣어도 됨: createWebHistory(import.meta.env.BASE_URL)
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

// 공통: 관리자 판별 (role 필드 다양한 케이스 방어)
function isAdminUser(u) {
  if (!u) return false
  const role = u.roleName ?? u.rolename ?? u.role
  const loginId = u.loginId ?? u.login_id
  return role === 'ROLE_ADMIN' || loginId === 'admin'
}

// 전역 가드
router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  // 이미 로그인한 사용자가 /login 접근하면 홈으로 (단, ?force=1 이면 허용)
  const forceParam = to.query?.force
  const force = forceParam === '1' || forceParam === 1 || forceParam === true
  if (to.name === 'login' && auth.isAuthenticated && !force) {
    return next({ name: 'home', replace: true })
  }

  // 관리자 보호
  if (to.meta?.requiresAdmin) {
    if (!auth.isAuthenticated) {
      return next({ name: 'login', query: { redirect: to.fullPath }, replace: true })
    }
    if (!isAdminUser(auth.user)) {
      return next({ name: 'home', replace: true })
    }
  }

  // 일반 보호
  if (to.meta?.requiresAuth && !auth.isAuthenticated) {
    return next({ name: 'login', query: { redirect: to.fullPath }, replace: true })
  }

  next()
})

export default router

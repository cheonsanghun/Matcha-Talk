// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/',        name: 'home',     component: () => import('../views/Home.vue') },
  { path: '/login',   name: 'login',    component: () => import('../views/Login.vue') },
  { path: '/register',name: 'register', component: () => import('../views/Register.vue') },

  // ✅ 프로필 라우트
  { path: '/profile', name: 'profile',  component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },

  { path: '/match',         name: 'match',         component: () => import('../views/MatchingSetup.vue'),  meta: { requiresAuth: true } },
  { path: '/match/result',  name: 'match-result',  component: () => import('../views/MatchingResult.vue'), meta: { requiresAuth: true } },
  { path: '/chat',          name: 'chat',          component: () => import('../views/Chat.vue'),           meta: { requiresAuth: true } },

  { path: '/rtc-test',      name: 'rtc-test',      component: () => import('../views/RtcTest.vue'),       meta: { requiresAuth: true } },

  { path: '/vocabulary',    name: 'vocabulary',    component: () => import('../views/Vocabulary.vue'),   meta: { requiresAuth: true } },

  { path: '/admin', name: 'admin-dashboard', component: () => import('../views/admin/AdminDashboard.vue'), meta: { requiresAuth: true, requiresAdmin: true } },

  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  // 이미 로그인인데 /login 가면 홈으로 (단, ?force=1이면 통과)
  if (to.name === 'login' && auth.isAuthenticated && !to.query.force) {
    return next({ name: 'home', replace: true })
  }

  // 보호 라우트
  if (to.meta?.requiresAuth && !auth.isAuthenticated) {
    return next({ name: 'login', replace: true })
  }

  if (to.meta?.requiresAdmin && auth.user?.roleName !== 'ROLE_ADMIN') {
    return next({ name: 'home', replace: true })
  }

  next()
})

export default router

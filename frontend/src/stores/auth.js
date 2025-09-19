import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { authApi, userApi, setAuthToken, clearAuthToken, setGuestPid } from '../services/api'

const STORAGE_KEY = 'matcha-talk/auth'

const normalizeUser = (payload) => {
  if (!payload) return null
  const raw = payload.user ?? payload.userSummary ?? payload
  if (!raw) return null
  return {
    id: raw.userPid ?? raw.id ?? raw.user_id ?? null,
    loginId: raw.loginId ?? raw.login_id ?? null,
    nickname: raw.nickName ?? raw.nick_name ?? raw.nickname ?? null,
    email: raw.email ?? null,
    countryCode: raw.countryCode ?? raw.country_code ?? null,
    gender: raw.gender ?? null,
    birthDate: raw.birthDate ?? raw.birth_date ?? null,
    roleName: raw.roleName ?? raw.role_name ?? null,
    enabled: raw.enabled ?? true,
    raw,
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(null)
  const loading = ref(false)
  const error = ref(null)

  const hydrated = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value || user.value))

  const stateForStorage = computed(() => ({
    user: user.value,
    token: token.value,
  }))

  const persist = () => {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(stateForStorage.value))
  }

  const hydrate = () => {
    if (hydrated.value) return
    if (typeof window === 'undefined') return
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const parsed = JSON.parse(raw)
      if (parsed?.user) {
        user.value = parsed.user
        setGuestPid(parsed.user?.id ?? null)
      }
      if (parsed?.token) {
        token.value = parsed.token
        setAuthToken(parsed.token)
      }
    } catch (err) {
      console.warn('Failed to hydrate auth store', err)
      window.localStorage.removeItem(STORAGE_KEY)
    } finally {
      hydrated.value = true
    }
  }

  hydrate()

  watch(
    stateForStorage,
    () => {
      if (!hydrated.value) return
      persist()
    },
    { deep: true }
  )

  const setSession = (payload) => {
    const normalizedUser = normalizeUser(payload)
    user.value = normalizedUser
    const extractedToken = payload?.accessToken ?? payload?.token ?? null
    token.value = extractedToken
    if (extractedToken) {
      setAuthToken(extractedToken)
    } else {
      clearAuthToken()
    }
    setGuestPid(normalizedUser?.id ?? null)
    hydrated.value = true
    persist()
  }

  const login = async ({ loginId, password }) => {
    loading.value = true
    error.value = null
    try {
      const response = await authApi.login({ loginId, password })
      setSession(response)
      return user.value
    } catch (err) {
      error.value = err?.message ?? '로그인에 실패했습니다.'
      throw err
    } finally {
      loading.value = false
    }
  }

  const logout = () => {
    user.value = null
    token.value = null
    clearAuthToken()
    setGuestPid(null)
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(STORAGE_KEY)
    }
  }

  const fetchProfile = async () => {
    if (!token.value && !user.value) return null
    loading.value = true
    error.value = null
    try {
      const profile = await userApi.fetchProfile()
      setSession({ user: profile, accessToken: token.value })
      return profile
    } catch (err) {
      error.value = err?.message ?? '프로필을 불러오지 못했습니다.'
      throw err
    } finally {
      loading.value = false
    }
  }

  return {
    user,
    token,
    loading,
    error,
    isAuthenticated,
    login,
    logout,
    fetchProfile,
    hydrate,
  }
})

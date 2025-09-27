const GUEST_STORAGE_KEY = 'matcha-talk-guest-id'

export function resolveClientIdentity(authStore) {
  if (authStore) {
    const loginId = authStore.loginId || authStore.user?.loginId || authStore.user?.login_id || authStore.user?.username
    if (loginId) {
      return loginId
    }
  }

  if (typeof window !== 'undefined') {
    const existing = sessionStorage.getItem(GUEST_STORAGE_KEY)
    if (existing) {
      return existing
    }
    const generated = `guest-${Math.random().toString(36).slice(2, 10)}`
    sessionStorage.setItem(GUEST_STORAGE_KEY, generated)
    return generated
  }

  return `guest-${Date.now()}`
}

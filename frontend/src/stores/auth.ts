import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { bindTokenGetter } from '@/api/http'
import type { UserProfile } from '@/types'

/* Auth store: token and user live in memory only.
   A page refresh requires logging in again - a deliberate simplification. */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<UserProfile | null>(null)
  const mustChangePassword = ref(false)

  bindTokenGetter(() => token.value)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isStudent = computed(() => user.value?.role === 'STUDENT')

  async function login(account: string, password: string): Promise<void> {
    const resp = await authApi.login({ account: account.trim(), password })
    token.value = resp.accessToken
    user.value = resp.user
    mustChangePassword.value = resp.mustChangePassword
  }

  async function refreshMe(): Promise<void> {
    user.value = await authApi.fetchMe()
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } finally {
      clearSession()
    }
  }

  function clearSession(): void {
    token.value = null
    user.value = null
    mustChangePassword.value = false
  }

  async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
    await authApi.changePassword(oldPassword, newPassword)
    clearSession()
  }

  return {
    token,
    user,
    mustChangePassword,
    isLoggedIn,
    isAdmin,
    isStudent,
    login,
    refreshMe,
    logout,
    clearSession,
    changePassword
  }
})

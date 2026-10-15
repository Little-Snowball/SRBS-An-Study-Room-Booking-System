import http from './http'
import type { LoginResponse, UserProfile } from '@/types'

export interface LoginPayload {
  account: string
  password: string
}

export async function login(payload: LoginPayload): Promise<LoginResponse> {
  return http.post('/auth/login', payload)
}

export async function logout(): Promise<null> {
  return http.post('/auth/logout')
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<null> {
  return http.post('/auth/change-password', { oldPassword, newPassword })
}

export async function fetchMe(): Promise<UserProfile> {
  return http.get('/me')
}

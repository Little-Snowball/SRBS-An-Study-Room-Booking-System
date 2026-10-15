import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'
import type { LoginResponse, UserProfile } from '@/types'

/* token 只进内存：store 不接触任何 Web 存储，这里同时 mock http 绑定与 auth API */
vi.mock('@/api/http', () => ({ bindTokenGetter: vi.fn() }))
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  fetchMe: vi.fn(),
  changePassword: vi.fn()
}))

const student: UserProfile = {
  id: '1',
  account: '20230001',
  name: '张三',
  college: '计算机学院',
  role: 'STUDENT',
  creditScore: 95,
  creditRestricted: false,
  blocked: false
}

const admin: UserProfile = { ...student, id: '2', account: 'admin', role: 'ADMIN' }

function loginResp(user: UserProfile, mustChangePassword = false): LoginResponse {
  return { accessToken: 'token-abc', expiresAt: '2026-10-14T12:00:00+08:00', user, mustChangePassword }
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('auth store 初始状态', () => {
  it('未登录时全部为初始值', () => {
    const auth = useAuthStore()
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.mustChangePassword).toBe(false)
    expect(auth.isLoggedIn).toBe(false)
    expect(auth.isAdmin).toBe(false)
    expect(auth.isStudent).toBe(false)
  })
})

describe('auth store login', () => {
  it('登录成功后写入内存 token 与用户信息', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(student))
    const auth = useAuthStore()

    await auth.login(' 20230001 ', 'pwd')

    expect(authApi.login).toHaveBeenCalledWith({ account: '20230001', password: 'pwd' })
    expect(auth.token).toBe('token-abc')
    expect(auth.user).toEqual(student)
    expect(auth.isLoggedIn).toBe(true)
    expect(auth.isStudent).toBe(true)
    expect(auth.isAdmin).toBe(false)
    expect(auth.mustChangePassword).toBe(false)
  })

  it('登录失败时状态保持不变', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('学号或密码错误'))
    const auth = useAuthStore()

    await expect(auth.login('20230001', 'bad')).rejects.toThrow('学号或密码错误')
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.isLoggedIn).toBe(false)
  })

  it('管理员登录后 isAdmin 为真', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(admin))
    const auth = useAuthStore()

    await auth.login('admin', 'pwd')

    expect(auth.isAdmin).toBe(true)
    expect(auth.isStudent).toBe(false)
  })

  it('mustChangePassword 透传', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(student, true))
    const auth = useAuthStore()

    await auth.login('20230001', 'pwd')

    expect(auth.mustChangePassword).toBe(true)
  })
})

describe('auth store refreshMe', () => {
  it('拉取并覆盖当前用户信息', async () => {
    vi.mocked(authApi.fetchMe).mockResolvedValue({ ...student, creditScore: 80 })
    const auth = useAuthStore()

    await auth.refreshMe()

    expect(auth.user?.creditScore).toBe(80)
  })
})

describe('auth store clearSession', () => {
  it('清空 token、用户与改密标记', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(student, true))
    const auth = useAuthStore()
    await auth.login('20230001', 'pwd')

    auth.clearSession()

    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(auth.mustChangePassword).toBe(false)
    expect(auth.isLoggedIn).toBe(false)
  })
})

describe('auth store logout', () => {
  it('已登录时调用后端并清空会话', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(student))
    vi.mocked(authApi.logout).mockResolvedValue(null)
    const auth = useAuthStore()
    await auth.login('20230001', 'pwd')

    await auth.logout()

    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
  })

  it('未登录时不调用后端', async () => {
    const auth = useAuthStore()

    await auth.logout()

    expect(authApi.logout).not.toHaveBeenCalled()
  })

  it('后端登出失败也会清空本地会话', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(student))
    vi.mocked(authApi.logout).mockRejectedValue(new Error('网络异常'))
    const auth = useAuthStore()
    await auth.login('20230001', 'pwd')

    await expect(auth.logout()).rejects.toThrow('网络异常')
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
  })
})

describe('auth store changePassword', () => {
  it('成功后清除会话（后端已撤销全部会话）', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(student))
    vi.mocked(authApi.changePassword).mockResolvedValue(null)
    const auth = useAuthStore()
    await auth.login('20230001', 'pwd')

    await auth.changePassword('old-password', 'new-password-123')

    expect(authApi.changePassword).toHaveBeenCalledWith('old-password', 'new-password-123')
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
  })

  it('失败时保留当前会话', async () => {
    vi.mocked(authApi.login).mockResolvedValue(loginResp(student))
    vi.mocked(authApi.changePassword).mockRejectedValue(new Error('当前密码错误'))
    const auth = useAuthStore()
    await auth.login('20230001', 'pwd')

    await expect(auth.changePassword('wrong', 'new-password-123')).rejects.toThrow('当前密码错误')
    expect(auth.token).toBe('token-abc')
  })
})

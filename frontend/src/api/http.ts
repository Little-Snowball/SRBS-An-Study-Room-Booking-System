import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiEnvelope } from '@/types'

/* Unified response handling:
   - success envelopes unwrap to data
   - 401 clears the in-memory token and redirects to login (single prompt)
   - 403 no permission; 404 privacy-safe hint; 409 state changed; 422 business rule;
     429 rate limited with retryAfter; 503 busy retry
   - write requests are never auto-retried without the same Idempotency-Key */

let serverTimeOffsetMs = 0
let latestServerTime = ''
let redirectingToLogin = false

export function setServerTime(serverTime: string) {
  if (!serverTime) return
  latestServerTime = serverTime
  const parsed = Date.parse(serverTime)
  if (!Number.isNaN(parsed)) {
    serverTimeOffsetMs = parsed - Date.now()
  }
}

export function getServerNow(): Date {
  return new Date(Date.now() + serverTimeOffsetMs)
}

export function getLatestServerTime(): string {
  return latestServerTime
}

export function clearAuthAndRedirect() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  void import('@/stores/auth').then(({ useAuthStore }) => {
    useAuthStore().clearSession()
  })
  const current = window.location.pathname + window.location.search
  if (!current.startsWith('/login')) {
    window.location.assign('/login?redirect=' + encodeURIComponent(current))
  }
  setTimeout(() => {
    redirectingToLogin = false
  }, 500)
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

http.interceptors.request.use((config) => {
  const token = getInMemoryToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse<ApiEnvelope<unknown> | Blob>) => {
    // 二进制响应（私有凭证图片）直接透传，不套 JSON 信封
    const responseType = response.config.responseType
    if (responseType === 'blob' || responseType === 'arraybuffer') {
      return response.data as never
    }
    const envelope = response.data as ApiEnvelope<unknown>
    if (envelope && typeof envelope === 'object' && 'serverTime' in envelope) {
      setServerTime(envelope.serverTime)
    }
    if (envelope && envelope.code === 'OK') {
      return envelope.data as never
    }
    return Promise.reject(asBusinessError(envelope, response))
  },
  async (error: AxiosError<ApiEnvelope<unknown> | Blob>) => {
    const status = error.response?.status
    const raw = error.response?.data
    // 二进制请求的错误响应也是 Blob，需先解析出 JSON 信封再按错误码处理
    let envelope: ApiEnvelope<unknown> | undefined
    if (raw instanceof Blob) {
      try {
        const text = await raw.text()
        envelope = JSON.parse(text) as ApiEnvelope<unknown>
      } catch {
        envelope = undefined
      }
    } else {
      envelope = raw
    }
    if (envelope?.serverTime) {
      setServerTime(envelope.serverTime)
    }
    if (!error.response) {
      ElMessage.error('网络连接失败，请检查网络后重试')
      return Promise.reject(error)
    }
    switch (status) {
      case 401:
        if (envelope?.code === 'PASSWORD_CHANGE_REQUIRED') {
          if (!window.location.pathname.startsWith('/change-password')) {
            window.location.assign('/change-password')
          }
        } else {
          ElMessage.error('登录状态已失效，请重新登录')
          clearAuthAndRedirect()
        }
        break
      case 403:
        ElMessage.error(envelope?.message || '无权限执行该操作')
        break
      case 404:
        ElMessage.error(envelope?.message || '资源不存在或无权查看')
        break
      case 409:
        ElMessage.error(envelope?.message || '状态已变化，请刷新后重试')
        break
      case 422:
        ElMessage.error(envelope?.message || '不满足业务规则')
        break
      case 429:
        ElMessage.error(envelope?.message || '操作过于频繁，请稍后再试')
        break
      case 503:
        ElMessage.error('系统繁忙，请稍后重试')
        break
      default:
        ElMessage.error(envelope?.message || '服务内部错误')
    }
    return Promise.reject(asBusinessError(envelope, error.response))
  }
)

export interface BusinessError extends Error {
  code: string
  status: number
  errorDetails?: unknown
  retryAfter?: number
}

export function asBusinessError(
  envelope: ApiEnvelope<unknown> | undefined,
  response: AxiosResponse
): BusinessError {
  const err = new Error(envelope?.message || '请求失败') as BusinessError
  err.code = envelope?.code || 'INTERNAL_ERROR'
  err.status = response.status
  err.errorDetails = envelope?.errorDetails
  const retryAfter = response.headers['retry-after']
  if (retryAfter) {
    err.retryAfter = Number(retryAfter)
  }
  return err
}

/* The token lives in memory (Pinia) only; never in localStorage/sessionStorage. */
let tokenHolder: (() => string | null) | null = null

export function bindTokenGetter(getter: () => string | null) {
  tokenHolder = getter
}

function getInMemoryToken(): string | null {
  return tokenHolder ? tokenHolder() : null
}

/* One idempotency key per logical click; reuse the same key when retrying a timeout. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export function withIdempotencyKey(config: AxiosRequestConfig = {}): AxiosRequestConfig {
  return {
    ...config,
    headers: { ...(config.headers || {}), 'Idempotency-Key': newIdempotencyKey() }
  }
}

export default http

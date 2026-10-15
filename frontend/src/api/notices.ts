import http from './http'
import type { NoticeItem, PublicRule } from '@/types'

export async function fetchNotices(page = 1, size = 10): Promise<{ items: NoticeItem[]; total: number }> {
  return http.get('/notices', { params: { page, size } })
}

export async function fetchNotice(id: string): Promise<NoticeItem> {
  return http.get(`/notices/${id}`)
}

export async function fetchPublicRule(): Promise<PublicRule> {
  return http.get('/rules/public')
}

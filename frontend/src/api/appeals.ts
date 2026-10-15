import http from './http'
import type { AppealItem, PageResult, ViolationItem } from '@/types'

export async function submitAppeal(payload: {
  violationId: string
  reason: string
  fileIds?: string[]
}): Promise<AppealItem> {
  return http.post('/appeals', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() }
  })
}

export async function fetchMyAppeals(
  status: string | undefined,
  page = 1,
  size = 20
): Promise<PageResult<AppealItem>> {
  return http.get('/me/appeals', { params: { status, page, size } })
}

/* 我的违约处罚记录（信用页展示生效中处罚与申诉入口） */
export async function fetchMyViolations(page = 1, size = 20): Promise<PageResult<ViolationItem>> {
  return http.get('/me/violations', { params: { page, size } })
}

export async function fetchAppeal(id: string): Promise<AppealItem> {
  return http.get(`/appeals/${id}`)
}

export async function uploadEvidence(file: File): Promise<{ fileId: string; mimeType: string; size: number }> {
  const form = new FormData()
  form.append('file', file)
  return http.post('/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/* Private evidence content: fetch as Blob through the authenticated axios instance.
   Never expose a bare public link that bypasses access control. */
export async function fetchEvidenceBlob(fileId: string): Promise<Blob> {
  return http.get(`/files/${fileId}/content`, { responseType: 'blob' })
}

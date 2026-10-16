import http, { withIdempotencyKey } from './http'
import type { AxiosRequestConfig } from 'axios'
import type {
  AdminUserItem,
  AdminReservationListItem,
  AdminRuleVersion,
  AdminAppealItem,
  AdminNoticeItem,
  AuditLogItem,
  StatsOverview,
  StatsTraffic,
  RoomUsageItem,
  PageResult,
  RoomListItem,
  RoomDetail,
  AppealItem
} from '@/types'

/* All write commands attach a fresh Idempotency-Key (one per logical click).
   Callers may pass their own config (e.g. to reuse a key when retrying a timeout). */

/* ---------- rooms & seats ---------- */

export interface RoomCreatePayload {
  code: string
  name: string
  campus: string
  building: string
  openTime: string
  closeTime: string
  rows: number
  cols: number
  checkinCode: string
}

export async function fetchAdminRooms(params: Record<string, unknown> = {}): Promise<PageResult<RoomListItem>> {
  return http.get('/admin/rooms', { params })
}

export async function createRoom(payload: RoomCreatePayload, config?: AxiosRequestConfig): Promise<RoomDetail> {
  return http.post('/admin/rooms', payload, withIdempotencyKey(config))
}

export async function updateRoom(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<RoomDetail> {
  return http.patch(`/admin/rooms/${id}`, payload, withIdempotencyKey(config))
}

export async function updateRoomStatus(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<unknown> {
  return http.post(`/admin/rooms/${id}/status`, payload, withIdempotencyKey(config))
}

export async function updateRoomCheckinCode(
  id: string,
  payload: { newCode: string; expectedVersion: string },
  config?: AxiosRequestConfig
): Promise<null> {
  return http.post(`/admin/rooms/${id}/checkin-code`, payload, withIdempotencyKey(config))
}

export async function fetchRoomSeats(roomId: string): Promise<{ seats: unknown[] }> {
  return http.get(`/admin/rooms/${roomId}/seats`)
}

export async function batchGenerateSeats(roomId: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<unknown> {
  return http.post(`/admin/rooms/${roomId}/seats/batch`, payload, withIdempotencyKey(config))
}

export async function updateSeat(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<unknown> {
  return http.patch(`/admin/seats/${id}`, payload, withIdempotencyKey(config))
}

export async function updateSeatStatus(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<unknown> {
  return http.post(`/admin/seats/${id}/status`, payload, withIdempotencyKey(config))
}

export async function fetchRoomImpact(roomId: string, params: Record<string, unknown> = {}): Promise<unknown> {
  return http.get(`/admin/rooms/${roomId}/impact`, { params })
}

export async function fetchSeatImpact(seatId: string, params: Record<string, unknown> = {}): Promise<unknown> {
  return http.get(`/admin/seats/${seatId}/impact`, { params })
}

/* ---------- reservations ---------- */

export async function fetchAdminReservations(
  params: Record<string, unknown> = {}
): Promise<PageResult<AdminReservationListItem>> {
  return http.get('/admin/reservations', { params })
}

/* ---------- users ---------- */

export async function fetchAdminUsers(params: Record<string, unknown> = {}): Promise<PageResult<AdminUserItem>> {
  return http.get('/admin/users', { params })
}

export async function createStudent(payload: { account: string; name: string; college?: string }, config?: AxiosRequestConfig): Promise<{
  user: AdminUserItem
  oneTimePassword: string
}> {
  return http.post('/admin/users', payload, withIdempotencyKey(config))
}

/** 修改学生身份信息（学号/姓名/学院）。学号变更会使该学生会话失效；学号冲突 409。 */
export async function updateAdminUser(id: string, payload: { account: string; name: string; college?: string | null }, config?: AxiosRequestConfig): Promise<AdminUserItem> {
  return http.patch(`/admin/users/${id}`, payload, withIdempotencyKey(config))
}

/** 删除学生账号：仅无业务历史（预约/违约/流水/申诉）时成功，否则 409 并给出计数。 */
export async function deleteAdminUser(id: string, config?: AxiosRequestConfig): Promise<void> {
  return http.delete(`/admin/users/${id}`, config)
}

export async function updateUserStatus(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<AdminUserItem> {
  return http.post(`/admin/users/${id}/status`, payload, withIdempotencyKey(config))
}

export async function updateUserBlock(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<AdminUserItem> {
  return http.post(`/admin/users/${id}/block`, payload, withIdempotencyKey(config))
}

export async function resetUserPassword(id: string, payload: { reason: string }, config?: AxiosRequestConfig): Promise<{ oneTimePassword: string }> {
  return http.post(`/admin/users/${id}/reset-password`, payload, withIdempotencyKey(config))
}

export async function adjustUserCredit(id: string, payload: { targetScore: number; reason: string }, config?: AxiosRequestConfig): Promise<unknown> {
  return http.post(`/admin/users/${id}/credit-adjust`, payload, withIdempotencyKey(config))
}

/* ---------- rules ---------- */

export async function fetchAdminRule(): Promise<AdminRuleVersion> {
  return http.get('/admin/rules')
}

export async function fetchRuleHistory(page = 1, size = 20): Promise<PageResult<AdminRuleVersion>> {
  return http.get('/admin/rules/history', { params: { page, size } })
}

export async function publishRule(payload: { config: Record<string, unknown>; baseVersion: number; reason: string }, config?: AxiosRequestConfig): Promise<AdminRuleVersion> {
  return http.post('/admin/rules', payload, withIdempotencyKey(config))
}

/* ---------- appeals ---------- */

export async function fetchAdminAppeals(params: Record<string, unknown> = {}): Promise<PageResult<AdminAppealItem>> {
  return http.get('/admin/appeals', { params })
}

export async function reviewAppeal(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<AppealItem> {
  return http.post(`/admin/appeals/${id}/review`, payload, withIdempotencyKey(config))
}

/* ---------- notices ---------- */

export async function fetchAdminNotices(params: Record<string, unknown> = {}): Promise<PageResult<AdminNoticeItem>> {
  return http.get('/admin/notices', { params })
}

export async function createNotice(payload: { title: string; content: string; pinned?: boolean }, config?: AxiosRequestConfig): Promise<AdminNoticeItem> {
  return http.post('/admin/notices', payload, withIdempotencyKey(config))
}

export async function updateNotice(id: string, payload: Record<string, unknown>, config?: AxiosRequestConfig): Promise<AdminNoticeItem> {
  return http.patch(`/admin/notices/${id}`, payload, withIdempotencyKey(config))
}

export async function publishNotice(id: string, expectedVersion: string, config?: AxiosRequestConfig): Promise<AdminNoticeItem> {
  return http.post(`/admin/notices/${id}/publish`, { expectedVersion }, withIdempotencyKey(config))
}

export async function withdrawNotice(id: string, payload: { expectedVersion: string; reason: string }, config?: AxiosRequestConfig): Promise<AdminNoticeItem> {
  return http.post(`/admin/notices/${id}/withdraw`, payload, withIdempotencyKey(config))
}

/* ---------- stats ---------- */

export async function fetchStatsOverview(date: string): Promise<StatsOverview> {
  return http.get('/admin/stats/overview', { params: { date } })
}

export async function fetchStatsTraffic(date: string, roomId?: string): Promise<StatsTraffic> {
  return http.get('/admin/stats/traffic', { params: { date, roomId } })
}

export async function fetchRoomUsage(params: {
  dateFrom: string
  dateTo: string
  page?: number
  size?: number
}): Promise<PageResult<RoomUsageItem>> {
  return http.get('/admin/stats/room-usage', { params })
}

/* ---------- audits ---------- */

export async function fetchAudits(params: Record<string, unknown> = {}): Promise<PageResult<AuditLogItem>> {
  return http.get('/admin/audits', { params })
}

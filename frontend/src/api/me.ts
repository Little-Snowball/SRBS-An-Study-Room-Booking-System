import http from './http'
import type { PageResult, ReservationListItem, CreditLogItem, NotificationItem } from '@/types'
import type { ReservationStatus } from '@/types'

export interface ReservationQuery {
  status?: ReservationStatus
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}

export async function fetchMyReservations(
  query: ReservationQuery = {}
): Promise<PageResult<ReservationListItem>> {
  return http.get('/me/reservations', { params: query })
}

export async function fetchMyCredits(
  type: string | undefined,
  page = 1,
  size = 20
): Promise<PageResult<CreditLogItem>> {
  return http.get('/me/credits', { params: { type, page, size } })
}

export async function fetchMyNotifications(
  unread: boolean | undefined,
  page = 1,
  size = 20
): Promise<PageResult<NotificationItem>> {
  return http.get('/me/notifications', { params: { unread, page, size } })
}

export async function markNotificationRead(id: string): Promise<null> {
  return http.post(`/me/notifications/${id}/read`)
}

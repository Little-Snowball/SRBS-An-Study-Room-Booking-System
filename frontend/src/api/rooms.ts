import http from './http'
import type { PageResult, RoomListItem, RoomDetail, RoomAvailability } from '@/types'

export interface RoomQuery {
  campus?: string
  building?: string
  date?: string
  startAt?: string
  endAt?: string
  hasPower?: boolean
  nearWindow?: boolean
  quietZone?: boolean
  page?: number
  size?: number
}

export async function fetchRooms(query: RoomQuery = {}): Promise<PageResult<RoomListItem>> {
  return http.get('/rooms', { params: query })
}

export async function fetchRoom(id: string): Promise<RoomDetail> {
  return http.get(`/rooms/${id}`)
}

export async function fetchAvailability(
  roomId: string,
  startAt: string,
  endAt: string
): Promise<RoomAvailability> {
  return http.get(`/rooms/${roomId}/availability`, { params: { startAt, endAt } })
}

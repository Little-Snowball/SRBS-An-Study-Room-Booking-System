import http, { withIdempotencyKey, newIdempotencyKey } from './http'
import type { ReservationDetail } from '@/types'

export interface CreateReservationPayload {
  seatId: string
  startAt: string
  endAt: string
}

export async function createReservation(
  payload: CreateReservationPayload,
  idempotencyKey: string
): Promise<ReservationDetail> {
  return http.post('/reservations', payload, {
    headers: { 'Idempotency-Key': idempotencyKey }
  })
}

export async function fetchReservation(id: string): Promise<ReservationDetail> {
  return http.get(`/reservations/${id}`)
}

export interface CommandResult {
  reservationId: string
  status: string
  checkInAt?: string | null
  leaveDueAt?: string | null
  finishedAt?: string | null
  leaveCount?: number
  eligibleSeconds?: number
  rewardRequested?: number
  rewardActual?: number
}

export function reservationCommand(
  id: string,
  action: 'cancel' | 'check-in' | 'leave' | 'return' | 'check-out',
  body: unknown = {},
  idempotencyKey: string = newIdempotencyKey()
): Promise<CommandResult> {
  return http.post(`/reservations/${id}/${action}`, body, {
    headers: { 'Idempotency-Key': idempotencyKey }
  })
}

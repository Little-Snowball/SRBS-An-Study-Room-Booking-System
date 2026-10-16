/* API types mirroring the backend DTO contract (docs/openapi.json).
   All IDs are strings (JSON serializes BIGINT as string).
   All date-times are ISO-8601 with +08:00 offset. */

export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
  serverTime: string
  errorDetails?: unknown
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export type UserRole = 'STUDENT' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export type ReservationStatus =
  | 'RESERVED'
  | 'IN_USE'
  | 'LEFT_TEMP'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'VIOLATED'

export type PeriodStatus = 'FREE' | 'RESERVED' | 'OCCUPIED' | 'UNAVAILABLE'
export type SeatCurrentStatus =
  | 'FREE'
  | 'RESERVED'
  | 'IN_USE'
  | 'LEFT_TEMP'
  | 'MAINTENANCE'
  | 'CLOSED'

export interface UserProfile {
  id: string
  account: string
  name: string
  college: string | null
  role: UserRole
  creditScore: number
  creditRestricted: boolean
  blocked: boolean
}

export interface LoginResponse {
  accessToken: string
  expiresAt: string
  user: UserProfile
}

export interface RoomListItem {
  id: string
  code: string
  name: string
  campus: string
  building: string
  openTime: string
  closeTime: string
  rows: number
  cols: number
  status: 'OPEN' | 'CLOSED'
  matchingSeatCount: number
}

export interface RoomDetail {
  id: string
  code: string
  name: string
  campus: string
  building: string
  openTime: string
  closeTime: string
  rows: number
  cols: number
  status: 'OPEN' | 'CLOSED'
  version: string
}

export interface AvailabilitySeat {
  seatId: string
  code: string
  rowNo: number
  colNo: number
  hasPower: boolean
  nearWindow: boolean
  quietZone: boolean
  available: boolean
  periodStatus: PeriodStatus
  currentStatus: SeatCurrentStatus
}

export interface RoomAvailability {
  roomId: string
  rows: number
  cols: number
  startAt: string
  endAt: string
  serverTime: string
  seats: AvailabilitySeat[]
}

export interface ReservationDetail {
  id: string
  orderNo: string
  userId: string
  roomId: string
  roomName: string
  seatId: string
  seatCode: string
  startAt: string
  endAt: string
  status: ReservationStatus
  terminationReason: string | null
  checkInAt: string | null
  checkinDueAt: string
  cancelDueAt: string
  leaveDueAt: string | null
  leaveCount: number
  finishedAt: string | null
  version: string
  allowedActions: string[]
  ruleSnapshotSummary?: string | null
}

export interface ReservationListItem {
  id: string
  orderNo: string
  roomName: string
  seatCode: string
  startAt: string
  endAt: string
  status: ReservationStatus
  terminationReason: string | null
  allowedActions: string[]
}

export interface CreditLogItem {
  id: string
  type: string
  requestedDelta: number
  actualDelta: number
  cappedDelta: number
  balanceBefore: number
  balanceAfter: number
  reason: string
  reservationId: string | null
  violationId: string | null
  createdAt: string
}

export interface NotificationItem {
  id: string
  type: string
  title: string
  content: string
  reservationId: string | null
  violationId: string | null
  read: boolean
  publishedAt: string
}

export interface NoticeItem {
  id: string
  title: string
  content: string
  pinned: boolean
  publishedAt: string
}

export interface PublicRule {
  advanceDays: number
  slotMinutes: number
  minMinutes: number
  maxMinutes: number
  cancelBeforeMinutes: number
  checkinEarlyMinutes: number
  checkinLateMinutes: number
  leaveMaxMinutes: number
  leaveMaxCount: number
  noShowPenalty: number
  leaveTimeoutPenalty: number
  minCredit: number
  rewardStartMinutes: number
  rewardBase: number
  rewardStepMinutes: number
  rewardStepScore: number
  rewardCap: number
}

export interface AppealItem {
  id: string
  violationId: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reason: string
  fileIds: string[]
  reviewComment: string | null
  submittedAt: string
  reviewedAt: string | null
  violationSummary?: {
    reason: string
    eventAt: string
    deducted: number
    orderNo: string
    seatCode: string
    roomName: string
  } | null
}

export interface ViolationItem {
  id: string
  reservationId: string
  reason: 'NO_SHOW' | 'LEAVE_TIMEOUT'
  status: 'ACTIVE' | 'REVOKED'
  eventAt: string
  notifiedAt: string
  deducted: number
  appealable: boolean
  orderNo?: string
}

/* ---------- admin ---------- */

export interface AdminUserItem {
  id: string
  account: string
  name: string
  college: string | null
  role: UserRole
  status: UserStatus
  blocked: boolean
  creditScore: number
  /** 乐观锁版本：禁用/改密后会 +1，状态类操作需回传当前值 */
  authVersion: number
  createdAt: string
}

export interface AdminReservationListItem {
  id: string
  orderNo: string
  account: string
  userName: string
  roomName: string
  seatCode: string
  startAt: string
  endAt: string
  status: ReservationStatus
  terminationReason: string | null
}

export interface AdminRuleVersion {
  id: string
  versionNo: number
  source: string
  createdBy: string | null
  createdAt: string
  config: Record<string, unknown>
}

export interface AdminAppealItem {
  id: string
  violationId: string
  account: string
  userName: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reason: string
  fileIds: string[]
  submittedAt: string
}

export interface AdminNoticeItem {
  id: string
  title: string
  content: string
  pinned: boolean
  status: 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN'
  publishedAt: string | null
  version: string
  createdAt: string
}

export interface AuditLogItem {
  id: string
  actorId: string | null
  action: string
  targetType: string
  targetId: string
  traceId: string
  createdAt: string
}

export interface StatsOverview {
  date: string
  todayBookings: number
  cancelledCount: number
  distinctStudentCount: number
  occupancyRate: number | null
  occupancyNumerator: number
  occupancyDenominator: number
  keptActiveSeats: number
  weekStart: string
  weekEnd: string
  asOfAt: string
  violationRate: number | null
  violationNumerator: number
  violationDenominator: number
  settlementPendingCount: number
}

export interface StatsTraffic {
  date: string
  buckets: { startAt: string; count: number }[]
}

export interface RoomUsageItem {
  roomId: string
  roomName: string
  usageSeconds: number
  serviceSeconds: number
  usageRate: number | null
}

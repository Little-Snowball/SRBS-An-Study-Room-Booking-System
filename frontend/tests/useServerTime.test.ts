import { afterEach, describe, expect, it, vi } from 'vitest'
import { getServerNow, setServerTime } from '@/api/http'
import { useServerTime } from '@/composables/useServerTime'

/* 用假计时器固定本地时钟，再通过 setServerTime 注入服务端时间偏移，
   验证 msUntil/formatCountdown 均以服务端 serverTime 为基准。 */
afterEach(() => {
  vi.useRealTimers()
})

describe('useServerTime', () => {
  it('服务端偏移为 0 时按本地时钟计算', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-14T02:00:00Z'))
    setServerTime('2026-10-14T10:00:00+08:00') // 与本地同一瞬时，偏移 0

    const { now, msUntil } = useServerTime()
    expect(now().toISOString()).toBe('2026-10-14T02:00:00.000Z')
    expect(msUntil('2026-10-14T12:00:00+08:00')).toBe(2 * 60 * 60 * 1000)
    expect(msUntil('2026-10-14T01:00:00Z')).toBe(-60 * 60 * 1000)
  })

  it('存在服务端偏移时以 serverTime 为基准', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-14T01:00:00Z'))
    setServerTime('2026-10-14T10:00:00+08:00') // 服务端 02:00Z，偏移 +1 小时

    const { now, msUntil } = useServerTime()
    expect(getServerNow().toISOString()).toBe('2026-10-14T02:00:00.000Z')
    // 目标 12:00(+08:00)=04:00Z：04:00 - 01:00 - 偏移 1h = 2h
    expect(msUntil('2026-10-14T12:00:00+08:00')).toBe(2 * 60 * 60 * 1000)
  })

  it('formatCountdown 渲染剩余分钟/小时/天', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-14T01:00:00Z'))
    setServerTime('2026-10-14T10:00:00+08:00') // 偏移 +1 小时

    const { formatCountdown } = useServerTime()
    // 本地 01:00Z、服务端 02:00Z（偏移 +1h）：剩余 = 目标 - 服务端此刻
    expect(formatCountdown('2026-10-14T10:30:00+08:00')).toBe('30 分') // 目标 02:30Z - 02:00Z
    expect(formatCountdown('2026-10-14T12:30:00+08:00')).toBe('2 小时 30 分') // 目标 04:30Z - 02:00Z
    expect(formatCountdown('2026-10-15T12:00:00+08:00')).toBe('1 天 2 小时') // 目标 15 日 04:00Z - 02:00Z
  })

  it('到点或过期显示“已截止”，不做本地强结', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-14T01:00:00Z'))
    setServerTime('2026-10-14T10:00:00+08:00') // 偏移 +1 小时

    const { formatCountdown } = useServerTime()
    expect(formatCountdown('2026-10-14T02:00:00Z')).toBe('已截止') // 恰好等于服务端此刻
    expect(formatCountdown('2026-10-13T10:00:00+08:00')).toBe('已截止')
  })

  it('setServerTime 空值不改变已有偏移', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-14T01:00:00Z'))
    setServerTime('2026-10-14T10:00:00+08:00') // 偏移 +1 小时
    setServerTime('')

    const { now } = useServerTime()
    expect(now().toISOString()).toBe('2026-10-14T02:00:00.000Z')
  })
})

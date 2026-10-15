import { getServerNow } from '@/api/http'

/* Server-time countdown: never force a local settlement when the countdown hits 0;
   re-sync serverTime when the network recovers or the page returns to foreground. */
export function useServerTime() {
  function now(): Date {
    return getServerNow()
  }

  function msUntil(iso: string): number {
    return Date.parse(iso) - Date.now() - serverOffsetMs()
  }

  function serverOffsetMs(): number {
    return getServerNow().getTime() - Date.now()
  }

  function formatCountdown(iso: string): string {
    const ms = msUntil(iso)
    if (ms <= 0) return '已截止'
    const totalMinutes = Math.floor(ms / 60000)
    const days = Math.floor(totalMinutes / (60 * 24))
    const hours = Math.floor((totalMinutes % (60 * 24)) / 60)
    const minutes = totalMinutes % 60
    if (days > 0) return `${days} 天 ${hours} 小时`
    if (hours > 0) return `${hours} 小时 ${minutes} 分`
    return `${minutes} 分`
  }

  return { now, msUntil, formatCountdown }
}

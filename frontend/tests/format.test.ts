import { describe, expect, it } from 'vitest'
import {
  addDays,
  businessDateOf,
  formatDate,
  formatDateTime,
  formatTime,
  minutesBetween,
  toBusinessIso,
  todayBusinessDate
} from '@/utils/format'

/* 服务端时间一律为带 +08:00 偏移的 ISO-8601；格式化统一渲染为 Asia/Shanghai。 */
describe('formatDateTime', () => {
  it('按 +08:00 渲染日期时间', () => {
    expect(formatDateTime('2026-10-14T09:30:00+08:00')).toBe('2026-10-14 09:30')
  })

  it('把 UTC 瞬时转换为 +08:00 展示', () => {
    expect(formatDateTime('2026-10-14T01:30:00Z')).toBe('2026-10-14 09:30')
    expect(formatDateTime('2026-10-14T16:30:00Z')).toBe('2026-10-15 00:30')
  })

  it('跨日边界正确进位', () => {
    expect(formatDateTime('2026-12-31T23:59:00+08:00')).toBe('2026-12-31 23:59')
    expect(formatDateTime('2027-01-01T00:00:00+08:00')).toBe('2027-01-01 00:00')
  })

  it('null/undefined 返回占位符', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
    expect(formatDateTime('')).toBe('—')
  })

  it('无法解析的输入原样返回（不抛错）', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
  })
})

describe('formatDate / formatTime', () => {
  it('formatDate 只渲染日期部分', () => {
    expect(formatDate('2026-10-14T09:30:00+08:00')).toBe('2026-10-14')
    expect(formatDate('2026-10-14T16:30:00Z')).toBe('2026-10-15')
    expect(formatDate(null)).toBe('—')
  })

  it('formatTime 只渲染时分部分', () => {
    expect(formatTime('2026-10-14T09:30:00+08:00')).toBe('09:30')
    expect(formatTime('2026-10-14T01:30:00Z')).toBe('09:30')
    expect(formatTime(undefined)).toBe('—')
  })

  it('businessDateOf 与 formatDate 一致', () => {
    expect(businessDateOf('2026-10-14T20:00:00+08:00')).toBe('2026-10-14')
  })
})

describe('todayBusinessDate', () => {
  it('按 +08:00 计算业务日期', () => {
    expect(todayBusinessDate(new Date('2026-10-14T15:59:59Z'))).toBe('2026-10-14')
    expect(todayBusinessDate(new Date('2026-10-14T16:00:00Z'))).toBe('2026-10-15')
  })

  it('默认取当前时刻', () => {
    const expected = formatDate(new Date().toISOString())
    expect(todayBusinessDate()).toBe(expected)
  })
})

describe('toBusinessIso', () => {
  it('由业务日期与 HH:mm 生成 +08:00 ISO 串', () => {
    expect(toBusinessIso('2026-10-14', '09:30')).toBe('2026-10-14T09:30:00+08:00')
    expect(toBusinessIso('2026-10-14', '00:00')).toBe('2026-10-14T00:00:00+08:00')
  })
})

describe('addDays', () => {
  it('按日历天加减', () => {
    expect(addDays('2026-10-14', 2)).toBe('2026-10-16')
    expect(addDays('2026-10-14', 0)).toBe('2026-10-14')
    expect(addDays('2026-10-14', -1)).toBe('2026-10-13')
  })

  it('跨月与跨年正确进位', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2027-01-01', -1)).toBe('2026-12-31')
  })

  it('闰年二月天数正确', () => {
    expect(addDays('2024-02-28', 1)).toBe('2024-02-29')
    expect(addDays('2026-02-28', 1)).toBe('2026-03-01')
  })
})

describe('minutesBetween', () => {
  it('计算两个 +08:00 时刻的分钟差', () => {
    expect(minutesBetween('2026-10-14T09:00:00+08:00', '2026-10-14T10:30:00+08:00')).toBe(90)
    expect(minutesBetween('2026-10-14T09:00:00+08:00', '2026-10-14T09:00:00+08:00')).toBe(0)
  })

  it('结束早于开始时为负值', () => {
    expect(minutesBetween('2026-10-14T10:00:00+08:00', '2026-10-14T09:00:00+08:00')).toBe(-60)
  })

  it('跨天与跨时区表示计算结果一致', () => {
    expect(minutesBetween('2026-10-14T23:00:00+08:00', '2026-10-15T01:00:00+08:00')).toBe(120)
    expect(minutesBetween('2026-10-14T01:00:00Z', '2026-10-14T03:00:00Z')).toBe(120)
  })
})

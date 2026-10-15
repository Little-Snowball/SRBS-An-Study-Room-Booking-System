import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRuleStore } from '@/stores/rule'
import { fetchPublicRule } from '@/api/notices'
import type { PublicRule } from '@/types'

vi.mock('@/api/notices', () => ({ fetchPublicRule: vi.fn() }))

const rule: PublicRule = {
  advanceDays: 2,
  slotMinutes: 30,
  minMinutes: 30,
  maxMinutes: 240,
  cancelBeforeMinutes: 15,
  checkinEarlyMinutes: 15,
  checkinLateMinutes: 15,
  leaveMaxMinutes: 30,
  leaveMaxCount: 2,
  noShowPenalty: 10,
  leaveTimeoutPenalty: 10,
  minCredit: 60,
  rewardStartMinutes: 120,
  rewardBase: 2,
  rewardStepMinutes: 30,
  rewardStepScore: 1,
  rewardCap: 5
}

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

describe('rule store', () => {
  it('首次 load 拉取公开规则并缓存', async () => {
    vi.mocked(fetchPublicRule).mockResolvedValue(rule)
    const store = useRuleStore()

    const loaded = await store.load()

    expect(loaded).toEqual(rule)
    expect(store.rule).toEqual(rule)
    expect(store.loadedAt).toBeGreaterThan(0)
    expect(fetchPublicRule).toHaveBeenCalledTimes(1)
  })

  it('缓存命中时不再重复请求', async () => {
    vi.mocked(fetchPublicRule).mockResolvedValue(rule)
    const store = useRuleStore()

    await store.load()
    await store.load()
    await store.load()

    expect(fetchPublicRule).toHaveBeenCalledTimes(1)
  })

  it('force=true 时强制刷新', async () => {
    vi.mocked(fetchPublicRule).mockResolvedValue(rule)
    const store = useRuleStore()

    await store.load()
    await store.load(true)

    expect(fetchPublicRule).toHaveBeenCalledTimes(2)
  })

  it('invalidate 后下次 load 重新拉取', async () => {
    vi.mocked(fetchPublicRule).mockResolvedValue(rule)
    const store = useRuleStore()

    await store.load()
    store.invalidate()
    expect(store.rule).toBeNull()
    expect(store.loadedAt).toBe(0)

    await store.load()
    expect(fetchPublicRule).toHaveBeenCalledTimes(2)
  })

  it('拉取失败时向上抛出且不写缓存', async () => {
    vi.mocked(fetchPublicRule).mockRejectedValue(new Error('服务内部错误'))
    const store = useRuleStore()

    await expect(store.load()).rejects.toThrow('服务内部错误')
    expect(store.rule).toBeNull()
    expect(store.loadedAt).toBe(0)
  })
})

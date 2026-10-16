<template>
  <div class="page">
    <div v-if="loading" class="card">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="loadError" class="card error-card">
      <p class="error-text">加载失败：{{ loadError }}</p>
      <el-button type="primary" @click="reload">重试</el-button>
    </div>
    <template v-else-if="room && availability">
      <div class="card">
        <div class="flex-between room-head">
          <span class="room-name">{{ room.name }}</span>
          <el-tag :type="room.status === 'OPEN' ? 'success' : 'info'" size="small">
            {{ room.status === 'OPEN' ? '开放' : '关闭' }}
          </el-tag>
        </div>
        <div class="text-sub">
          {{ room.campus }} · {{ room.building }} · 开放 {{ room.openTime }}—{{ room.closeTime }}
        </div>
        <div class="period-line">
          <span>所选时段：{{ formatDateTime(startAt) }} — {{ formatDateTime(endAt) }}</span>
          <span class="text-sub">数据时间（服务端）：{{ formatDateTime(availability.serverTime) }}</span>
        </div>
        <div class="seat-summary">
          <span v-if="availableCount > 0" class="seat-ok">该时段可约座位 {{ availableCount }} 个</span>
          <span v-else class="seat-none">该时段暂无可约座位</span>
          <el-button size="small" :loading="refreshing" @click="reload">刷新余位</el-button>
        </div>
      </div>

      <div class="card">
        <SeatMap
          :seats="availability.seats"
          :rows="availability.rows"
          :cols="availability.cols"
          :selectable="room.status === 'OPEN'"
          :selected-seat-id="selectedSeat?.seatId ?? null"
          @select="onSeatSelect"
        />
        <div class="attr-legend">
          <span class="attr-item"><el-icon><Lightning /></el-icon>插座</span>
          <span class="attr-item"><el-icon><Sunny /></el-icon>靠窗</span>
          <span class="attr-item"><el-icon><MuteNotification /></el-icon>静音区</span>
          <span class="text-sub">将鼠标移到座位上可查看该座位属性</span>
        </div>
      </div>
    </template>

    <el-dialog v-model="dialogVisible" title="确认预约" width="92%" :close-on-click-modal="false">
      <template v-if="selectedSeat">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="自习室">{{ room?.name }}</el-descriptions-item>
          <el-descriptions-item label="座位">{{ selectedSeat.code }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(startAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatDateTime(endAt) }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ durationMinutes }} 分钟</el-descriptions-item>
          <el-descriptions-item label="取消截止">{{ formatDateTime(cancelDueAt) }}（此前取消不扣信用分）</el-descriptions-item>
          <el-descriptions-item label="签到截止">{{ formatDateTime(checkinDueAt) }}（此前未签到将视为违约）</el-descriptions-item>
        </el-descriptions>
        <p v-if="creditRestricted" class="warn-line">当前信用分低于限制线，可能无法完成预约。</p>
        <p class="dialog-hint">提交后座位将为您锁定，请在签到截止时间前到馆签到。</p>
      </template>
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">再想想</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="onConfirmReserve">立即预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lightning, MuteNotification, Sunny } from '@element-plus/icons-vue'
import SeatMap from '@/components/SeatMap.vue'
import { fetchAvailability, fetchRoom } from '@/api/rooms'
import { createReservation } from '@/api/reservations'
import { useRuleStore } from '@/stores/rule'
import { useAuthStore } from '@/stores/auth'
import { getServerNow, newIdempotencyKey } from '@/api/http'
import { minutesBetween, todayBusinessDate, toBusinessIso, formatDateTime } from '@/utils/format'
import type { AvailabilitySeat, RoomAvailability, RoomDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const ruleStore = useRuleStore()
const auth = useAuthStore()

const roomId = route.params.id as string
const loading = ref(true)
const refreshing = ref(false)
const loadError = ref('')
const room = ref<RoomDetail | null>(null)
const availability = ref<RoomAvailability | null>(null)

const selectedSeat = ref<AvailabilitySeat | null>(null)
const dialogVisible = ref(false)
const submitting = ref(false)

/* 起止时间：优先使用路由查询参数，缺失时按服务端时间生成默认时段并回写 URL */
function resolveTimes(): { startAt: string; endAt: string; fromQuery: boolean } {
  const qStart = typeof route.query.startAt === 'string' ? route.query.startAt : ''
  const qEnd = typeof route.query.endAt === 'string' ? route.query.endAt : ''
  if (qStart && qEnd && Date.parse(qEnd) > Date.parse(qStart)) {
    return { startAt: qStart, endAt: qEnd, fromQuery: true }
  }
  const step = ruleStore.rule?.slotMinutes ?? 30
  const maxM = ruleStore.rule?.maxMinutes ?? 240
  const today = todayBusinessDate(getServerNow())
  const now = getServerNow()
  const m = now.getHours() * 60 + now.getMinutes()
  const startM = Math.min(Math.floor(m / step) * step + step, 24 * 60 - step * 2)
  const endM = Math.min(startM + 120, startM + maxM, 24 * 60 - step)
  return {
    startAt: toBusinessIso(today, toHHmm(startM)),
    endAt: toBusinessIso(today, toHHmm(endM)),
    fromQuery: false
  }
}

function toHHmm(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

const times = resolveTimes()
const startAt = times.startAt
const endAt = times.endAt

const availableCount = computed(() => availability.value?.seats.filter((s) => s.available).length ?? 0)
const durationMinutes = computed(() => minutesBetween(startAt, endAt))
const creditRestricted = computed(() => {
  const rule = ruleStore.rule
  if (!auth.user) return false
  if (rule && auth.user.creditScore < rule.minCredit) return true
  return auth.user.creditRestricted
})

/* 截止时间按公开规则派生，提交后的权威值以预约详情为准 */
const cancelBeforeMinutes = computed(() => ruleStore.rule?.cancelBeforeMinutes ?? 15)
const checkinLateMinutes = computed(() => ruleStore.rule?.checkinLateMinutes ?? 15)
const cancelDueAt = computed(() => new Date(Date.parse(startAt) - cancelBeforeMinutes.value * 60000).toISOString())
const checkinDueAt = computed(() => new Date(Date.parse(startAt) + checkinLateMinutes.value * 60000).toISOString())

async function loadData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [roomResp, availabilityResp] = await Promise.all([fetchRoom(roomId), fetchAvailability(roomId, startAt, endAt)])
    room.value = roomResp
    availability.value = availabilityResp
  } catch (err) {
    loadError.value = (err as Error)?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

async function reload(): Promise<void> {
  refreshing.value = true
  try {
    availability.value = await fetchAvailability(roomId, startAt, endAt)
  } catch (err) {
    ElMessage.error((err as Error)?.message || '刷新失败，请重试')
  } finally {
    refreshing.value = false
  }
}

function onSeatSelect(seat: AvailabilitySeat): void {
  selectedSeat.value = seat
  dialogVisible.value = true
}

/* 网络超时类错误（无 HTTP 响应）才使用同一幂等键重试一次；服务端错误不重试 */
function isTimeoutLike(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false
  const e = err as { code?: string; status?: number }
  if (typeof e.status === 'number') return false
  return true
}

async function onConfirmReserve(): Promise<void> {
  if (!selectedSeat.value || submitting.value) return
  const seatId = selectedSeat.value.seatId
  const key = newIdempotencyKey()
  submitting.value = true
  try {
    const detail = await createReservation({ seatId, startAt, endAt }, key)
    dialogVisible.value = false
    ElMessage.success('预约成功')
    await reload().catch(() => undefined)
    await router.push(`/student/reservations/${detail.id}`)
  } catch (err) {
    if (isTimeoutLike(err)) {
      /* 超时后复用同一 key 重试，避免重复预约 */
      try {
        const detail = await createReservation({ seatId, startAt, endAt }, key)
        dialogVisible.value = false
        ElMessage.success('预约成功')
        await reload().catch(() => undefined)
        await router.push(`/student/reservations/${detail.id}`)
        return
      } catch {
        /* 重试失败：提示已由 http 拦截器给出 */
        return
      }
    }
    /* 409 等状态冲突：重新查询可用性，倒计时与座位状态以服务端为准 */
    void reload()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    await ruleStore.load()
  } catch {
    /* 规则加载失败不阻塞选页，截止时间按默认值展示 */
  }
  if (!times.fromQuery) {
    void router.replace({ path: route.path, query: { startAt, endAt } })
  }
  await loadData()
})
</script>

<style scoped>
.room-head {
  margin-bottom: 4px;
}
.room-name {
  font-size: 17px;
  font-weight: 700;
}
.period-line {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 8px;
  font-size: 13px;
}
.seat-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  font-size: 13px;
}
.seat-ok {
  color: var(--color-success);
  font-weight: 600;
}
.seat-none {
  color: var(--color-warning);
  font-weight: 600;
}
.attr-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-sub);
}
.attr-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.warn-line {
  margin: 12px 0 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: #fef3c7;
  color: #92400e;
  font-size: 13px;
}
.dialog-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}
.error-card {
  text-align: center;
}
.error-text {
  color: var(--color-danger);
}
</style>

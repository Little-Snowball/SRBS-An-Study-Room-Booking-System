<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <div class="field">
          <label class="field-label">日期</label>
          <el-select v-model="date" style="width: 130px" :disabled="ruleLoading" @change="onDateChange">
            <el-option v-for="opt in dateOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <div class="field">
          <label class="field-label">开始时间</label>
          <el-select v-model="startTime" style="width: 104px" :disabled="ruleLoading">
            <el-option v-for="t in timeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </div>
        <div class="field">
          <label class="field-label">结束时间</label>
          <el-select v-model="endTime" style="width: 104px" :disabled="ruleLoading">
            <el-option v-for="t in timeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </div>
        <div class="field">
          <label class="field-label">校区</label>
          <el-input v-model="campus" placeholder="如：中心校区" clearable style="width: 130px" />
        </div>
        <div class="field">
          <label class="field-label">楼宇</label>
          <el-input v-model="building" placeholder="如：图书馆" clearable style="width: 130px" />
        </div>
        <div class="field">
          <label class="field-label">座位标签</label>
          <div class="tag-box">
            <el-checkbox v-model="hasPower">插座</el-checkbox>
            <el-checkbox v-model="nearWindow">靠窗</el-checkbox>
            <el-checkbox v-model="quietZone">静音</el-checkbox>
          </div>
        </div>
        <el-button type="primary" :loading="loading" :disabled="ruleLoading" @click="load">查询</el-button>
      </div>
      <p class="rule-hint text-sub">
        可预约今后 {{ advanceDays }} 天；时段须落在 {{ slotMinutes }} 分钟格点上，单次 {{ minMinutes }}—{{ maxMinutes }} 分钟。
      </p>
    </div>

    <div v-if="loading" class="room-list">
      <el-skeleton v-for="n in 3" :key="n" :rows="2" animated class="card" />
    </div>
    <div v-else-if="loadError" class="card error-card">
      <p class="error-text">加载失败：{{ loadError }}</p>
      <el-button type="primary" @click="load">重试</el-button>
    </div>
    <div v-else-if="!rooms.length" class="card">
      <el-empty description="没有符合条件的自习室，请调整筛选条件或时段">
        <el-button type="primary" @click="resetFilters">重置筛选</el-button>
      </el-empty>
    </div>
    <div v-else class="room-list">
      <div
        v-for="room in rooms"
        :key="room.id"
        class="card room-card"
        role="button"
        @click="goDetail(room)"
      >
        <div class="flex-between room-head">
          <span class="room-name">{{ room.name }}</span>
          <el-tag :type="room.status === 'OPEN' ? 'success' : 'info'" size="small">
            {{ room.status === 'OPEN' ? '开放' : '关闭' }}
          </el-tag>
        </div>
        <div class="text-sub">
          {{ room.campus }} · {{ room.building }} · 开放 {{ room.openTime }}—{{ room.closeTime }}
        </div>
        <div class="room-seats">
          <span v-if="room.matchingSeatCount > 0" class="seat-ok">该时段可约座位 {{ room.matchingSeatCount }} 个</span>
          <span v-else class="seat-none">该时段暂无余位</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchRooms } from '@/api/rooms'
import { useRuleStore } from '@/stores/rule'
import { getServerNow } from '@/api/http'
import { addDays, minutesBetween, todayBusinessDate, toBusinessIso } from '@/utils/format'
import type { RoomListItem } from '@/types'

const router = useRouter()
const ruleStore = useRuleStore()

const loading = ref(false)
const ruleLoading = ref(true)
const loadError = ref('')
const rooms = ref<RoomListItem[]>([])

const todayStr = todayBusinessDate(getServerNow())
const date = ref(todayStr)
const startTime = ref('')
const endTime = ref('')
const campus = ref('')
const building = ref('')
const hasPower = ref(false)
const nearWindow = ref(false)
const quietZone = ref(false)

const slotMinutes = computed(() => ruleStore.rule?.slotMinutes ?? 30)
const minMinutes = computed(() => ruleStore.rule?.minMinutes ?? 30)
const maxMinutes = computed(() => ruleStore.rule?.maxMinutes ?? 240)
const advanceDays = computed(() => ruleStore.rule?.advanceDays ?? 2)

const dateOptions = computed(() => {
  const names = ['今天', '明天', '后天']
  const options: { value: string; label: string }[] = []
  for (let i = 0; i <= advanceDays.value; i++) {
    const value = addDays(todayStr, i)
    const [, m, d] = value.split('-')
    options.push({ value, label: `${names[i] ?? ''}${Number(m)}月${Number(d)}日` })
  }
  return options
})

/* 严格 slotMinutes 格点的时间选项 */
const timeOptions = computed(() => {
  const step = slotMinutes.value
  const options: string[] = []
  for (let m = 0; m < 24 * 60; m += step) {
    options.push(toHHmm(m))
  }
  return options
})

function toHHmm(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/* 当前时刻之后的下一个格点（保证开始时间不在过去）；最晚开始预留一个时段 */
function nextGridAfterNow(): string {
  const step = slotMinutes.value
  const now = getServerNow()
  const m = now.getHours() * 60 + now.getMinutes()
  let next = Math.floor(m / step) * step + step
  const maxStart = 24 * 60 - step * 2
  next = Math.min(next, maxStart)
  return toHHmm(next)
}

function applyDefaultTimes(forDate: string): void {
  if (forDate === todayStr) {
    startTime.value = nextGridAfterNow()
  } else if (!startTime.value) {
    startTime.value = '08:00'
  }
  if (!endTime.value || endTime.value <= startTime.value) {
    const [sh, sm] = startTime.value.split(':').map(Number)
    const startM = sh * 60 + sm
    const endM = Math.min(startM + 120, startM + maxMinutes.value, 24 * 60 - slotMinutes.value)
    endTime.value = toHHmm(Math.max(endM, startM + minMinutes.value))
  }
}

function onDateChange(value: string): void {
  if (!value) return
  if (value === todayStr) {
    const earliest = nextGridAfterNow()
    if (startTime.value < earliest) {
      startTime.value = earliest
      const [sh, sm] = earliest.split(':').map(Number)
      const startM = sh * 60 + sm
      const endM = Math.min(startM + 120, startM + maxMinutes.value, 24 * 60 - slotMinutes.value)
      endTime.value = toHHmm(Math.max(endM, startM + minMinutes.value))
    }
  }
}

function resetFilters(): void {
  campus.value = ''
  building.value = ''
  hasPower.value = false
  nearWindow.value = false
  quietZone.value = false
  date.value = todayStr
  startTime.value = todayStr === date.value ? nextGridAfterNow() : '08:00'
  applyDefaultTimes(date.value)
  void load()
}

async function load(): Promise<void> {
  if (!date.value || !startTime.value || !endTime.value) {
    ElMessage.warning('请选择日期与起止时间')
    return
  }
  const startAt = toBusinessIso(date.value, startTime.value)
  const endAt = toBusinessIso(date.value, endTime.value)
  const mins = minutesBetween(startAt, endAt)
  if (mins < minMinutes.value) {
    ElMessage.warning(`预约时长不得少于 ${minMinutes.value} 分钟`)
    return
  }
  if (mins > maxMinutes.value) {
    ElMessage.warning(`单次预约时长不得超过 ${maxMinutes.value} 分钟`)
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const resp = await fetchRooms({
      date: date.value,
      startAt,
      endAt,
      campus: campus.value.trim() || undefined,
      building: building.value.trim() || undefined,
      hasPower: hasPower.value || undefined,
      nearWindow: nearWindow.value || undefined,
      quietZone: quietZone.value || undefined,
      page: 1,
      size: 50
    })
    rooms.value = resp.items || []
  } catch (err) {
    loadError.value = (err as Error)?.message || '请稍后重试'
    rooms.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(room: RoomListItem): void {
  const startAt = toBusinessIso(date.value, startTime.value)
  const endAt = toBusinessIso(date.value, endTime.value)
  void router.push({ path: `/student/rooms/${room.id}`, query: { startAt, endAt } })
}

onMounted(async () => {
  try {
    await ruleStore.load()
  } catch {
    loadError.value = '规则加载失败，请刷新重试'
    return
  } finally {
    ruleLoading.value = false
  }
  applyDefaultTimes(date.value)
  await load()
})
</script>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-label {
  font-size: 12px;
  color: var(--text-sub);
}
.tag-box {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
  min-height: 32px;
}
.rule-hint {
  margin: 0;
}
.room-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.room-card {
  cursor: pointer;
  margin-bottom: 12px;
}
.room-card:hover {
  border-color: var(--color-primary);
}
.room-head {
  margin-bottom: 4px;
}
.room-name {
  font-size: 15px;
  font-weight: 700;
}
.room-seats {
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
.error-card {
  text-align: center;
}
.error-text {
  color: var(--color-danger);
}
</style>

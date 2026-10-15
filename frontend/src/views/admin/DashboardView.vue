<template>
  <div class="page">
    <div class="toolbar card">
      <div class="toolbar-left">
        <span class="toolbar-label">统计日期</span>
        <el-date-picker
          v-model="selectedDate"
          type="date"
          placeholder="选择日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          @change="onDateChange"
        />
      </div>
      <el-button :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <div v-loading="loading" class="stats-block">
      <el-alert v-if="loadError" type="error" :closable="false" show-icon class="mb-16">
        <template #default>
          <div class="flex gap-8 error-alert">
            <span>统计数据加载失败：{{ loadError }}</span>
            <el-button size="small" @click="loadAll">重试</el-button>
          </div>
        </template>
      </el-alert>

      <div class="stat-grid">
        <div class="card stat-card">
          <div class="stat-title">今日预约总人次</div>
          <div class="stat-value">{{ overview ? overview.todayBookings : '—' }}</div>
          <div class="stat-sub">按开始时间落在当日、未取消的订单计，非去重人数</div>
        </div>
        <div class="card stat-card">
          <div class="stat-title">今日取消订单</div>
          <div class="stat-value">{{ overview ? overview.cancelledCount : '—' }}</div>
          <div class="stat-sub">当日被取消的订单数</div>
        </div>
        <div class="card stat-card">
          <div class="stat-title">今日去重学生数</div>
          <div class="stat-value">{{ overview ? overview.distinctStudentCount : '—' }}</div>
          <div class="stat-sub">当日有未取消订单的独立学生账号数</div>
        </div>
        <div class="card stat-card">
          <div class="stat-title">当前在座率</div>
          <div class="stat-value">{{ occupancyText }}</div>
          <div class="stat-sub">
            实际在座（IN_USE）占开放房间可用座位比例；暂离不计在座
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-title">保留服务中座位</div>
          <div class="stat-value">{{ overview ? overview.keptActiveSeats : '—' }}</div>
          <div class="stat-sub">资源停用/闭馆后按 KEEP_ACTIVE 保留的占用座位，不混入在座率分母</div>
        </div>
        <div class="card stat-card">
          <div class="stat-title">本周违约率</div>
          <div class="stat-value">{{ violationRateText }}</div>
          <div class="stat-sub">
            分子 {{ overview ? overview.violationNumerator : '—' }} / 分母
            {{ overview ? overview.violationDenominator : '—' }}；已撤销违约不计入
          </div>
        </div>
        <div class="card stat-card">
          <div class="stat-title">待结算订单</div>
          <div class="stat-value">{{ overview ? overview.settlementPendingCount : '—' }}</div>
          <div class="stat-sub">已到截止但结算任务尚未处理的订单数</div>
        </div>
        <div class="card stat-card stat-card--meta">
          <div class="stat-title">数据口径</div>
          <div class="meta-line">统计日期：{{ overview?.date || selectedDate }}</div>
          <div class="meta-line">
            本周区间：{{ overview ? `${overview.weekStart} 至 ${overview.weekEnd}` : '—' }}
          </div>
          <div class="meta-line">
            统计时点：{{ overview ? formatDateTime(overview.asOfAt) : '—' }}
          </div>
          <div class="stat-sub">统计为查询时点视图，非跨接口原子快照</div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <span class="card-title">签到流量（30 分钟粒度）</span>
          <span class="text-sub">各时间桶内发生的签到次数，按实际签到事件统计</span>
        </div>
        <div v-if="traffic && traffic.buckets.length > 0" class="chart-wrap">
          <svg
            :viewBox="`0 0 ${CHART_W} ${CHART_H}`"
            class="traffic-chart"
            role="img"
            aria-label="30 分钟签到流量折线图"
            preserveAspectRatio="xMidYMid meet"
          >
            <!-- horizontal gridlines + y labels -->
            <g>
              <line
                v-for="tick in yTicks"
                :key="'grid-' + tick"
                :x1="PAD.left"
                :x2="CHART_W - PAD.right"
                :y1="tick.y"
                :y2="tick.y"
                class="grid-line"
              />
              <text
                v-for="tick in yTicks"
                :key="'ylabel-' + tick"
                :x="PAD.left - 6"
                :y="tick.y + 4"
                class="axis-text"
                text-anchor="end"
              >{{ tick.label }}</text>
            </g>
            <!-- area + line -->
            <polygon v-if="areaPoints" :points="areaPoints" class="chart-area" />
            <polyline v-if="linePoints" :points="linePoints" class="chart-line" />
            <circle
              v-for="(p, idx) in pointList"
              :key="'pt-' + idx"
              :cx="p.x"
              :cy="p.y"
              r="2.5"
              class="chart-point"
            >
              <title>{{ p.label }}：{{ p.count }} 次签到</title>
            </circle>
            <!-- x labels every 4 buckets (2h) -->
            <text
              v-for="label in xLabels"
              :key="'x-' + label.text"
              :x="label.x"
              :y="CHART_H - 8"
              class="axis-text"
              text-anchor="middle"
            >{{ label.text }}</text>
          </svg>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </div>

      <div class="card">
        <div class="card-header">
          <span class="card-title">房间使用率排行</span>
          <span class="text-sub">按当日实际在座时长占可服务时长比例，暂离不计；默认前 10 名</span>
        </div>
        <el-table :data="usageList" v-loading="usageLoading" border empty-text="暂无数据">
          <el-table-column type="index" label="排名" width="70" align="center" />
          <el-table-column prop="roomName" label="房间" min-width="160" show-overflow-tooltip />
          <el-table-column label="使用率" min-width="140" align="center">
            <template #default="{ row }">
              {{ formatRate(row.usageRate) }}
            </template>
          </el-table-column>
          <el-table-column label="实际在座时长" min-width="150" align="center">
            <template #default="{ row }">
              {{ formatDuration(row.usageSeconds) }}
            </template>
          </el-table-column>
          <el-table-column label="可服务时长" min-width="150" align="center">
            <template #default="{ row }">
              {{ formatDuration(row.serviceSeconds) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  fetchStatsOverview,
  fetchStatsTraffic,
  fetchRoomUsage
} from '@/api/admin'
import { todayBusinessDate } from '@/utils/format'
import { formatDateTime } from '@/utils/format'
import type { StatsOverview, StatsTraffic, RoomUsageItem } from '@/types'

const selectedDate = ref(todayBusinessDate())
const loading = ref(false)
const usageLoading = ref(false)
const loadError = ref('')

const overview = ref<StatsOverview | null>(null)
const traffic = ref<StatsTraffic | null>(null)
const usageList = ref<RoomUsageItem[]>([])

/* Rates are expected as 0-1 fractions; tolerate percent-style values defensively. */
function formatRate(rate: number | null): string {
  if (rate === null || rate === undefined || Number.isNaN(rate)) return '—'
  const percent = rate > 1 ? rate : rate * 100
  return `${percent.toFixed(1)}%`
}

const occupancyText = computed(() => {
  if (!overview.value) return '—'
  if (overview.value.occupancyRate === null) return '无开放座位'
  return formatRate(overview.value.occupancyRate)
})

const violationRateText = computed(() => {
  if (!overview.value) return '—'
  if (overview.value.violationRate === null) return '暂无到期订单'
  return formatRate(overview.value.violationRate)
})

function formatDuration(seconds: number): string {
  if (!seconds || seconds <= 0) return '0 分钟'
  const totalMinutes = Math.round(seconds / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours > 0) return `${hours} 小时 ${minutes} 分`
  return `${minutes} 分钟`
}

/* ---------- SVG traffic chart (no chart library) ---------- */
const CHART_W = 720
const CHART_H = 240
const PAD = { left: 40, right: 14, top: 14, bottom: 30 }

const maxCount = computed(() => {
  if (!traffic.value) return 0
  return traffic.value.buckets.reduce((max, b) => Math.max(max, b.count), 0)
})

const yTicks = computed(() => {
  const max = Math.max(maxCount.value, 1)
  const ticks: { y: number; label: string }[] = []
  const steps = 4
  const plotH = CHART_H - PAD.top - PAD.bottom
  for (let i = 0; i <= steps; i++) {
    const value = Math.round((max * i) / steps)
    const y = CHART_H - PAD.bottom - (plotH * i) / steps
    ticks.push({ y, label: String(value) })
  }
  return ticks
})

interface ChartPoint {
  x: number
  y: number
  label: string
  count: number
}

const pointList = computed<ChartPoint[]>(() => {
  const buckets = traffic.value?.buckets || []
  if (buckets.length === 0) return []
  const plotW = CHART_W - PAD.left - PAD.right
  const plotH = CHART_H - PAD.top - PAD.bottom
  const max = Math.max(maxCount.value, 1)
  const step = buckets.length > 1 ? plotW / (buckets.length - 1) : 0
  return buckets.map((b, idx) => ({
    x: PAD.left + step * idx,
    y: CHART_H - PAD.bottom - (plotH * b.count) / max,
    label: bucketLabel(b.startAt),
    count: b.count
  }))
})

const linePoints = computed(() =>
  pointList.value.map((p) => `${p.x},${p.y}`).join(' ')
)
const areaPoints = computed(() => {
  if (pointList.value.length === 0) return ''
  const baseY = CHART_H - PAD.bottom
  const first = pointList.value[0]
  const last = pointList.value[pointList.value.length - 1]
  return `${first.x},${baseY} ${linePoints.value} ${last.x},${baseY}`
})

const xLabels = computed(() => {
  const buckets = traffic.value?.buckets || []
  if (buckets.length === 0) return []
  const plotW = CHART_W - PAD.left - PAD.right
  const step = buckets.length > 1 ? plotW / (buckets.length - 1) : 0
  // one label every 4 buckets (2h at 30-min granularity), always include the first
  const labels: { x: number; text: string }[] = []
  buckets.forEach((b, idx) => {
    if (idx % 4 === 0) {
      labels.push({ x: PAD.left + step * idx, text: bucketLabel(b.startAt) })
    }
  })
  return labels
})

function bucketLabel(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const shifted = new Date(d.getTime() + 8 * 3600 * 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(shifted.getUTCHours())}:${pad(shifted.getUTCMinutes())}`
}

/* ---------- data loading ---------- */
async function loadAll(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [ov, tr] = await Promise.all([
      fetchStatsOverview(selectedDate.value),
      fetchStatsTraffic(selectedDate.value)
    ])
    overview.value = ov
    traffic.value = tr
  } catch (err) {
    overview.value = null
    traffic.value = null
    loadError.value = err instanceof Error ? err.message : '未知错误'
  } finally {
    loading.value = false
  }
  await loadUsage()
}

async function loadUsage(): Promise<void> {
  usageLoading.value = true
  try {
    const res = await fetchRoomUsage({
      dateFrom: selectedDate.value,
      dateTo: selectedDate.value,
      page: 1,
      size: 10
    })
    usageList.value = res.items || []
  } catch {
    usageList.value = []
  } finally {
    usageLoading.value = false
  }
}

function onDateChange(): void {
  void loadAll()
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-label {
  font-size: 14px;
  color: var(--text-sub);
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  margin-bottom: 0;
}
.stat-title {
  font-size: 13px;
  color: var(--text-sub);
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  margin: 6px 0 4px;
  word-break: break-all;
}
.stat-sub {
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.5;
}
.stat-card--meta .meta-line {
  font-size: 13px;
  margin: 2px 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.card-title {
  font-weight: 600;
}
.chart-wrap {
  width: 100%;
  overflow-x: auto;
}
.traffic-chart {
  width: 100%;
  min-width: 560px;
  height: auto;
}
.grid-line {
  stroke: var(--border);
  stroke-width: 1;
}
.axis-text {
  font-size: 11px;
  fill: var(--text-sub);
}
.chart-area {
  fill: rgba(47, 107, 255, 0.08);
  stroke: none;
}
.chart-line {
  fill: none;
  stroke: var(--color-primary);
  stroke-width: 2;
}
.chart-point {
  fill: var(--color-primary);
}
</style>

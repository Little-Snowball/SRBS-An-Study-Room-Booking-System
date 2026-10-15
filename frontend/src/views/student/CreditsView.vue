<template>
  <div class="page">
    <div v-if="violationsLoading" class="card">
      <el-skeleton :rows="2" animated />
    </div>
    <div v-else-if="activeViolations.length" class="card penalty-card">
      <h3 class="card-title">生效中的处罚</h3>
      <div v-for="v in activeViolations" :key="v.id" class="penalty-item">
        <div class="flex-between">
          <span class="penalty-reason">{{ reasonLabel(v.reason) }}（扣 {{ v.deducted }} 分）</span>
          <StatusTag :status="v.status" />
        </div>
        <div class="text-sub">
          发生时间：{{ formatDateTime(v.eventAt) }}<span v-if="v.orderNo"> · 订单 {{ v.orderNo }}</span>
        </div>
        <div class="text-sub">申诉截止：{{ formatDateTime(appealDeadline(v)) }}（{{ appealCountdown(v) }}）</div>
        <div class="penalty-action">
          <el-button v-if="v.appealable" size="small" type="primary" @click="goAppeal(v.id)">申诉</el-button>
          <span v-else class="text-sub">申诉窗口已结束</span>
        </div>
      </div>
    </div>
    <div v-else-if="violationsError" class="card">
      <p class="text-sub">处罚记录加载失败：{{ violationsError }}</p>
      <el-button size="small" @click="loadViolations">重试</el-button>
    </div>

    <div class="card">
      <div class="flex-between card-head">
        <h3 class="card-title">信用流水</h3>
        <el-button size="small" :loading="loading" @click="loadCredits">刷新</el-button>
      </div>
      <div v-if="loading" class="card">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="loadError" class="error-card">
        <p class="error-text">加载失败：{{ loadError }}</p>
        <el-button type="primary" @click="loadCredits">重试</el-button>
      </div>
      <div v-else-if="!items.length" class="card">
        <el-empty description="暂无信用流水记录" :image-size="60" />
      </div>
      <div v-else class="table-wrap">
        <el-table :data="items">
          <el-table-column label="时间" width="130">
            <template #default="{ row }">
              <span class="cell-main">{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <span class="cell-main">{{ typeLabel(row.type) }}</span>
              <span class="text-sub cell-sub">{{ row.type }}</span>
            </template>
          </el-table-column>
          <el-table-column label="实际入账" width="110">
            <template #default="{ row }">
              <span class="cell-main delta" :class="row.actualDelta < 0 ? 'delta--minus' : 'delta--plus'">
                {{ row.actualDelta > 0 ? '+' : '' }}{{ row.actualDelta }}
              </span>
              <span v-if="hasCap(row)" class="text-sub cell-sub">
                请求 {{ row.requestedDelta > 0 ? '+' : '' }}{{ row.requestedDelta }} · 封顶部分 {{ row.cappedDelta > 0 ? '+' : '' }}{{ row.cappedDelta }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="余额变化" width="130">
            <template #default="{ row }">
              <span class="cell-main">{{ row.balanceBefore }} → {{ row.balanceAfter }}</span>
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="140">
            <template #default="{ row }">
              <span class="cell-main reason-text">{{ row.reason }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关联" width="100">
            <template #default="{ row }">
              <router-link v-if="row.reservationId" :to="`/student/reservations/${row.reservationId}`">查看订单</router-link>
              <span v-else-if="row.violationId" class="text-sub">违约记录</span>
              <span v-else class="text-sub">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            :page-size="size"
            :total="total"
            layout="prev, pager, next"
            small
            background
            @current-change="onPageChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMyCredits } from '@/api/me'
import { fetchMyViolations } from '@/api/appeals'
import { useServerTime } from '@/composables/useServerTime'
import { formatDateTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import type { CreditLogItem, ViolationItem } from '@/types'

const router = useRouter()
const { formatCountdown } = useServerTime()

const page = ref(1)
const size = 10
const items = ref<CreditLogItem[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

const violations = ref<ViolationItem[]>([])
const violationsLoading = ref(true)
const violationsError = ref('')

const tick = ref(Date.now())
let timer: number | undefined

const activeViolations = computed(() => violations.value.filter((v) => v.status === 'ACTIVE'))

const APPEAL_WINDOW_MS = 48 * 60 * 60 * 1000

function appealDeadline(v: ViolationItem): string {
  return new Date(Date.parse(v.notifiedAt) + APPEAL_WINDOW_MS).toISOString()
}

function appealCountdown(v: ViolationItem): string {
  void tick.value
  if (!v.appealable) return '已截止'
  return formatCountdown(appealDeadline(v))
}

function typeLabel(type: string): string {
  if (type.includes('REWARD')) return '提前签退奖励'
  if (type.includes('NO_SHOW')) return '违约扣分（未签到）'
  if (type.includes('LEAVE_TIMEOUT')) return '违约扣分（暂离超时）'
  if (type.includes('VIOLATION')) return '违约扣分'
  if (type.includes('REVOKE')) return '处罚撤销返还'
  if (type.includes('ADJUST')) return '管理员调整'
  return type
}

function reasonLabel(reason: string): string {
  if (reason === 'NO_SHOW') return '未签到违约'
  if (reason === 'LEAVE_TIMEOUT') return '暂离超时违约'
  return reason
}

function hasCap(row: CreditLogItem): boolean {
  return row.requestedDelta !== row.actualDelta || row.cappedDelta !== 0
}

async function loadCredits(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const resp = await fetchMyCredits(undefined, page.value, size)
    items.value = resp.items || []
    total.value = resp.total || 0
  } catch (err) {
    loadError.value = (err as Error)?.message || '请稍后重试'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadViolations(): Promise<void> {
  violationsLoading.value = true
  violationsError.value = ''
  try {
    const resp = await fetchMyViolations(1, 20)
    violations.value = resp.items || []
  } catch (err) {
    /* 处罚摘要加载失败不阻塞信用流水，仅记录原因 */
    violationsError.value = (err as Error)?.message || '处罚记录加载失败'
  } finally {
    violationsLoading.value = false
  }
}

function goAppeal(violationId: string): void {
  void router.push({ path: '/student/appeals', query: { violationId } })
}

function onPageChange(next: number): void {
  page.value = next
  void loadCredits()
}

onMounted(() => {
  timer = window.setInterval(() => {
    tick.value = Date.now()
  }, 30000)
  void Promise.all([loadCredits(), loadViolations()])
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.penalty-card {
  border-color: #fca5a5;
}
.card-title {
  font-size: 15px;
  margin: 0 0 12px;
}
.penalty-item {
  border-top: 1px solid var(--border);
  padding: 10px 0;
  font-size: 13px;
}
.penalty-item:first-of-type {
  border-top: none;
}
.penalty-reason {
  font-weight: 600;
}
.penalty-action {
  margin-top: 8px;
}
.card-head {
  margin-bottom: 12px;
}
.cell-main {
  display: block;
  font-size: 13px;
}
.cell-sub {
  display: block;
}
.delta {
  font-weight: 700;
}
.delta--plus {
  color: var(--color-success);
}
.delta--minus {
  color: var(--color-danger);
}
.reason-text {
  word-break: break-all;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.error-card {
  text-align: center;
}
.error-text {
  color: var(--color-danger);
}
</style>

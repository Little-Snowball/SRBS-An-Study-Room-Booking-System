<template>
  <div class="page">
    <div v-if="loading" class="card">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="loadError" class="card error-card">
      <p class="error-text">加载失败：{{ loadError }}</p>
      <el-button type="primary" @click="load">重试</el-button>
    </div>
    <template v-else-if="detail">
      <div class="card">
        <div class="flex-between detail-head">
          <span class="detail-room">{{ detail.roomName }} · {{ detail.seatCode }}</span>
          <StatusTag :status="detail.status" />
        </div>
        <div class="text-sub">订单号：{{ detail.orderNo }}</div>
        <el-descriptions :column="1" border class="detail-desc">
          <el-descriptions-item label="开始时间">{{ formatDateTime(detail.startAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatDateTime(detail.endAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.checkInAt" label="签到时间">{{ formatDateTime(detail.checkInAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.finishedAt" label="结束时间（签退）">{{ formatDateTime(detail.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.terminationReason" label="终止原因">
            <span class="termination">{{ detail.terminationReason }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.ruleSnapshotSummary" label="规则快照">{{ detail.ruleSnapshotSummary }}</el-descriptions-item>
        </el-descriptions>

        <div class="countdown-list">
          <div v-if="detail.cancelDueAt" class="countdown-item">
            <span class="countdown-label">取消截止</span>
            <span>{{ formatDateTime(detail.cancelDueAt) }}</span>
            <span class="countdown-value">{{ countdown(detail.cancelDueAt) }}</span>
          </div>
          <div v-if="detail.checkinDueAt" class="countdown-item">
            <span class="countdown-label">签到截止</span>
            <span>{{ formatDateTime(detail.checkinDueAt) }}</span>
            <span class="countdown-value">{{ countdown(detail.checkinDueAt) }}</span>
          </div>
          <div v-if="detail.leaveDueAt" class="countdown-item">
            <span class="countdown-label">回座截止</span>
            <span>{{ formatDateTime(detail.leaveDueAt) }}</span>
            <span class="countdown-value">{{ countdown(detail.leaveDueAt) }}</span>
          </div>
          <div v-if="detail.leaveCount > 0" class="countdown-item">
            <span class="countdown-label">暂离次数</span>
            <span>{{ detail.leaveCount }} / {{ leaveMaxCount }} 次</span>
            <span class="countdown-value" />
          </div>
        </div>
        <p class="text-sub countdown-note">倒计时以服务端时间为基准；到点后状态由服务端结算，请以下一次查询结果为准。</p>
      </div>

      <div v-if="detail.allowedActions.length" class="card actions-card">
        <h3 class="card-title">可执行操作</h3>
        <div class="action-buttons">
          <el-button v-if="can('cancel')" type="danger" plain :loading="busy('cancel')" :disabled="!!actionLoading" @click="cancelDialog = true">取消预约</el-button>
          <el-button v-if="can('check-in')" type="primary" :loading="busy('check-in')" :disabled="!!actionLoading" @click="openCheckin">签到</el-button>
          <el-button v-if="can('leave')" type="warning" plain :loading="busy('leave')" :disabled="!!actionLoading" @click="leaveDialog = true">暂离</el-button>
          <el-button v-if="can('return')" type="success" plain :loading="busy('return')" :disabled="!!actionLoading" @click="returnDialog = true">回座</el-button>
          <el-button v-if="can('check-out')" type="primary" plain :loading="busy('check-out')" :disabled="!!actionLoading" @click="checkoutDialog = true">签退</el-button>
        </div>
      </div>
    </template>

    <!-- 取消预约 -->
    <el-dialog v-model="cancelDialog" title="取消预约" width="92%">
      <p>取消后座位将立即释放，且不可恢复。</p>
      <p>在取消截止时间（{{ detail ? formatDateTime(detail.cancelDueAt) : '—' }}）前取消不扣除信用分；截止后取消可能被视为违约。</p>
      <template #footer>
        <el-button :disabled="!!actionLoading" @click="cancelDialog = false">再想想</el-button>
        <el-button type="danger" :loading="busy('cancel')" :disabled="!!actionLoading" @click="runCommand('cancel')">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- 签到 -->
    <el-dialog v-model="checkinDialog" title="签到" width="92%" :close-on-click-modal="false">
      <p>请输入现场 6 位签到码（签到码仅在此处输入，不会进入地址栏）。</p>
      <el-input
        v-model="checkinCode"
        type="text"
        inputmode="numeric"
        maxlength="6"
        placeholder="请输入 6 位数字签到码"
        autocomplete="off"
        show-word-limit
        :disabled="!!actionLoading"
        @keyup.enter="submitCheckin"
      />
      <p class="text-sub">签到截止：{{ detail ? formatDateTime(detail.checkinDueAt) : '—' }}（{{ countdown(detail?.checkinDueAt ?? null) }}）</p>
      <template #footer>
        <el-button :disabled="!!actionLoading" @click="checkinDialog = false">取消</el-button>
        <el-button type="primary" :loading="busy('check-in')" :disabled="!!actionLoading" @click="submitCheckin">确认签到</el-button>
      </template>
    </el-dialog>

    <!-- 暂离 -->
    <el-dialog v-model="leaveDialog" title="暂离座位" width="92%">
      <p>暂离后请在回座截止时间前回座，超时未回座将按违约扣 {{ leaveTimeoutPenalty }} 分。</p>
      <p>本次预约已暂离 {{ detail?.leaveCount ?? 0 }} 次，最多 {{ leaveMaxCount }} 次；单次暂离最长 {{ leaveMaxMinutes }} 分钟。</p>
      <template #footer>
        <el-button :disabled="!!actionLoading" @click="leaveDialog = false">再想想</el-button>
        <el-button type="warning" :loading="busy('leave')" :disabled="!!actionLoading" @click="runCommand('leave')">确认暂离</el-button>
      </template>
    </el-dialog>

    <!-- 回座 -->
    <el-dialog v-model="returnDialog" title="回座" width="92%">
      <p>回座后座位将继续为您保留至预约结束时间。</p>
      <template #footer>
        <el-button :disabled="!!actionLoading" @click="returnDialog = false">取消</el-button>
        <el-button type="success" :loading="busy('return')" :disabled="!!actionLoading" @click="runCommand('return')">确认回座</el-button>
      </template>
    </el-dialog>

    <!-- 签退 -->
    <el-dialog v-model="checkoutDialog" title="提前签退" width="92%">
      <p>签退后座位将立即释放，不可恢复。</p>
      <p class="text-sub">
        奖励规则：使用满 {{ rewardStartMinutes }} 分钟起，每满 {{ rewardStepMinutes }} 分钟奖励 {{ rewardStepScore }} 分，单次上限 {{ rewardCap }} 分。
      </p>
      <template #footer>
        <el-button :disabled="!!actionLoading" @click="checkoutDialog = false">再想想</el-button>
        <el-button type="primary" :loading="busy('check-out')" :disabled="!!actionLoading" @click="runCommand('check-out')">确认签退</el-button>
      </template>
    </el-dialog>

    <!-- 操作结果 -->
    <el-dialog v-model="resultDialog" title="操作结果" width="92%">
      <p>{{ resultText }}</p>
      <template #footer>
        <el-button type="primary" @click="resultDialog = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useRuleStore } from '@/stores/rule'
import { fetchReservation, reservationCommand, type CommandResult } from '@/api/reservations'
import { newIdempotencyKey } from '@/api/http'
import { useServerTime } from '@/composables/useServerTime'
import { formatDateTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import type { ReservationDetail } from '@/types'

type CommandAction = 'cancel' | 'check-in' | 'leave' | 'return' | 'check-out'

const route = useRoute()
const ruleStore = useRuleStore()
const { formatCountdown } = useServerTime()

const reservationId = route.params.id as string
const detail = ref<ReservationDetail | null>(null)
const loading = ref(true)
const loadError = ref('')
const actionLoading = ref<CommandAction | ''>('')

const cancelDialog = ref(false)
const checkinDialog = ref(false)
const checkinCode = ref('')
const leaveDialog = ref(false)
const returnDialog = ref(false)
const checkoutDialog = ref(false)
const resultDialog = ref(false)
const resultText = ref('')

const tick = ref(Date.now())
let timer: number | undefined

const leaveMaxCount = computed(() => ruleStore.rule?.leaveMaxCount ?? 2)
const leaveMaxMinutes = computed(() => ruleStore.rule?.leaveMaxMinutes ?? 30)
const leaveTimeoutPenalty = computed(() => ruleStore.rule?.leaveTimeoutPenalty ?? 10)
const rewardStartMinutes = computed(() => ruleStore.rule?.rewardStartMinutes ?? 120)
const rewardStepMinutes = computed(() => ruleStore.rule?.rewardStepMinutes ?? 30)
const rewardStepScore = computed(() => ruleStore.rule?.rewardStepScore ?? 1)
const rewardCap = computed(() => ruleStore.rule?.rewardCap ?? 5)

function can(action: CommandAction): boolean {
  return !!detail.value?.allowedActions.includes(action)
}
function busy(action: CommandAction): boolean {
  return actionLoading.value === action
}
function countdown(iso: string | null | undefined): string {
  void tick.value
  if (!iso) return '—'
  return formatCountdown(iso)
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await fetchReservation(reservationId)
  } catch (err) {
    loadError.value = (err as Error)?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

/* 网络超时类错误（无 HTTP 响应）才使用同一幂等键重试一次 */
function isTimeoutLike(err: unknown): boolean {
  if (!err || typeof err !== 'object') return false
  const e = err as { code?: string; status?: number }
  if (typeof e.status === 'number') return false
  return true
}

function isEarlyCheckinError(err: unknown): boolean {
  const e = err as { code?: string; message?: string } | null
  const text = `${e?.code || ''} ${e?.message || ''}`
  return /PREVIOUS|EARLY|尚未结束|未结束|NOT_YET/.test(text)
}

function handleResult(action: CommandAction, result: CommandResult): void {
  if (action === 'leave' && result.leaveDueAt) {
    resultText.value = `已暂离，请于 ${formatDateTime(result.leaveDueAt)} 前回座，超时未回座将扣除 ${leaveTimeoutPenalty.value} 分。`
    resultDialog.value = true
  }
  if (action === 'check-out') {
    const minutes = result.eligibleSeconds != null ? Math.round(result.eligibleSeconds / 60) : null
    const lines = ['签退成功，座位已释放。']
    if (minutes != null) lines.push(`本次计入时长：${minutes} 分钟`)
    if (result.rewardRequested != null) lines.push(`奖励请求值：${result.rewardRequested} 分`)
    if (result.rewardActual != null) lines.push(`实际入账：${result.rewardActual} 分`)
    resultText.value = lines.join('\n')
    resultDialog.value = true
  }
}

/* 一次点击只生成一个幂等键；超时重试复用同一键，避免重复执行 */
async function runCommand(action: CommandAction, body: unknown = {}): Promise<void> {
  if (!detail.value || actionLoading.value) return
  const key = newIdempotencyKey()
  actionLoading.value = action
  try {
    const result = await reservationCommand(detail.value.id, action, body, key)
    cancelDialog.value = false
    leaveDialog.value = false
    returnDialog.value = false
    checkoutDialog.value = false
    ElMessage.success('操作成功')
    handleResult(action, result)
    await load()
  } catch (err) {
    if (isTimeoutLike(err)) {
      try {
        const result = await reservationCommand(detail.value.id, action, body, key)
        cancelDialog.value = false
        leaveDialog.value = false
        returnDialog.value = false
        checkoutDialog.value = false
        ElMessage.success('操作成功')
        handleResult(action, result)
        await load()
      } catch {
        /* 重试失败：提示已由 http 拦截器给出 */
      }
      return
    }
    if (action === 'check-in' && isEarlyCheckinError(err)) {
      ElMessage.warning('前一使用权尚未结束，请到签到开放时间后再试')
    }
  } finally {
    actionLoading.value = ''
  }
}

function openCheckin(): void {
  checkinCode.value = ''
  checkinDialog.value = true
}

function submitCheckin(): void {
  const code = checkinCode.value.trim()
  if (!/^\d{6}$/.test(code)) {
    ElMessage.warning('请输入 6 位数字签到码')
    return
  }
  checkinDialog.value = false
  void runCommand('check-in', { code })
}

onMounted(async () => {
  timer = window.setInterval(() => {
    tick.value = Date.now()
  }, 15000)
  try {
    await ruleStore.load()
  } catch {
    /* 规则加载失败时按默认值展示说明，不阻塞详情 */
  }
  await load()
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.detail-head {
  margin-bottom: 4px;
}
.detail-room {
  font-size: 17px;
  font-weight: 700;
}
.detail-desc {
  margin-top: 12px;
}
.termination {
  color: var(--color-danger);
}
.countdown-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.countdown-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  font-size: 13px;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 12px;
}
.countdown-label {
  color: var(--text-sub);
  min-width: 72px;
}
.countdown-value {
  margin-left: auto;
  font-weight: 700;
  color: var(--color-primary);
}
.countdown-note {
  margin: 8px 0 0;
}
.card-title {
  font-size: 15px;
  margin: 0 0 12px;
}
.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.action-buttons .el-button {
  flex: 1 1 120px;
  min-width: 0;
}
.error-card {
  text-align: center;
}
.error-text {
  color: var(--color-danger);
}
</style>

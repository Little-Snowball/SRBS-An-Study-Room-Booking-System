<template>
  <div class="page">
    <div class="toolbar card">
      <div class="toolbar-left">
        <el-button @click="goBack">返回房间列表</el-button>
        <span v-if="room" class="room-heading">
          {{ room.name }}（{{ room.code }}）· {{ room.campus }} {{ room.building }} {{ room.floorNo }} 层 ·
          {{ room.openTime }} — {{ room.closeTime }} · {{ room.rows }} 行 × {{ room.cols }} 列
        </span>
      </div>
      <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
    </div>

    <div v-loading="loading" class="card table-wrap">
      <div class="card-header">
        <span class="card-title">座位布局</span>
        <el-button type="primary" size="small" @click="openBatchDialog">批量生成座位</el-button>
      </div>
      <el-table :data="seats" border empty-text="暂无座位，请先批量生成">
        <el-table-column prop="rowNo" label="行号" width="80" align="center" />
        <el-table-column prop="colNo" label="列号" width="80" align="center" />
        <el-table-column prop="code" label="编号" min-width="120" show-overflow-tooltip />
        <el-table-column label="标签" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="row.hasPower" size="small" type="success" class="seat-tag">电源</el-tag>
            <el-tag v-if="row.nearWindow" size="small" type="warning" class="seat-tag">靠窗</el-tag>
            <el-tag v-if="row.quietZone" size="small" class="seat-tag">静音</el-tag>
            <span v-if="!row.hasPower && !row.nearWindow && !row.quietZone" class="text-sub">无</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'MAINTENANCE' ? 'danger' : 'success'" size="small">
              {{ row.status === 'MAINTENANCE' ? '停用中' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑标签</el-button>
            <el-button
              v-if="row.status !== 'MAINTENANCE'"
              link
              type="danger"
              size="small"
              @click="openStatusDialog(row, 'MAINTENANCE')"
            >停用</el-button>
            <el-button
              v-else
              link
              type="success"
              size="small"
              @click="openStatusDialog(row, 'NORMAL')"
            >恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="text-sub mt-16">
        共 {{ seats.length }} 个座位；停用中的座位仍显示在布局中，不物理删除。
      </div>
    </div>

    <!-- 批量生成座位 -->
    <el-dialog v-model="batchDialogVisible" title="批量生成座位" width="560px" @closed="resetBatchDialog">
      <el-alert type="info" :closable="false" show-icon class="mb-16">
        <template #default>
          <div>
            编号规则：<code>前缀-R两位行号-C两位列号</code>（行/列超过 99 时保持完整数字），例如
            <code>A-R01-C02</code>。前缀仅允许大写字母、数字、短横线，最多 16 字符。
          </div>
          <div class="mt-8">
            矩形范围内任何位置已有座位、或生成的编号已存在时，整批失败（409），不会跳过或覆盖已有座位。
          </div>
        </template>
      </el-alert>
      <el-alert
        v-if="batchError"
        type="error"
        :closable="false"
        show-icon
        :title="batchError"
        class="mb-16"
      />
      <el-form ref="batchFormRef" :model="batchForm" :rules="batchRules" label-width="110px">
        <div class="batch-grid">
          <el-form-item label="起始行" prop="startRow">
            <el-input-number v-model="batchForm.startRow" :min="1" :max="100" controls-position="right" />
          </el-form-item>
          <el-form-item label="结束行" prop="endRow">
            <el-input-number v-model="batchForm.endRow" :min="1" :max="100" controls-position="right" />
          </el-form-item>
          <el-form-item label="起始列" prop="startCol">
            <el-input-number v-model="batchForm.startCol" :min="1" :max="100" controls-position="right" />
          </el-form-item>
          <el-form-item label="结束列" prop="endCol">
            <el-input-number v-model="batchForm.endCol" :min="1" :max="100" controls-position="right" />
          </el-form-item>
        </div>
        <el-form-item label="编号前缀" prop="codePrefix">
          <el-input v-model="batchForm.codePrefix" placeholder="如 A" maxlength="16" style="width: 200px" />
        </el-form-item>
        <el-form-item label="默认标签">
          <el-switch v-model="batchForm.hasPower" active-text="电源" inline-prompt />
          <el-switch v-model="batchForm.nearWindow" active-text="靠窗" inline-prompt class="ml-16" />
          <el-switch v-model="batchForm.quietZone" active-text="静音" inline-prompt class="ml-16" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBatch">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 编辑座位标签 -->
    <el-dialog v-model="editDialogVisible" title="编辑座位标签" width="420px" @closed="resetEditDialog">
      <el-alert
        v-if="editError"
        type="error"
        :closable="false"
        show-icon
        :title="editError"
        class="mb-16"
      />
      <p class="dialog-line">座位编号：{{ editingSeat?.code }}</p>
      <el-form label-width="90px">
        <el-form-item label="电源">
          <el-switch v-model="editForm.hasPower" />
        </el-form-item>
        <el-form-item label="靠窗">
          <el-switch v-model="editForm.nearWindow" />
        </el-form-item>
        <el-form-item label="静音区">
          <el-switch v-model="editForm.quietZone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 停用 / 恢复座位 -->
    <el-dialog
      v-model="statusDialogVisible"
      :title="statusTarget === 'MAINTENANCE' ? '停用座位' : '恢复座位'"
      width="560px"
      @closed="resetStatusDialog"
    >
      <el-alert
        v-if="statusError"
        type="error"
        :closable="false"
        show-icon
        :title="statusError"
        class="mb-16"
      />
      <template v-if="statusTarget === 'MAINTENANCE'">
        <p class="dialog-line">停用「{{ statusSeat?.code }}」后：</p>
        <ul class="dialog-list">
          <li>该座位禁止新增预约；</li>
          <li>尚未获得实际使用权的预约（RESERVED）无责取消；</li>
          <li>正在使用/暂离的订单按所选策略处理。</li>
        </ul>
        <el-form label-width="96px">
          <el-form-item label="订单策略" required>
            <el-select v-model="statusOrderPolicy" placeholder="请选择处理策略" style="width: 100%">
              <el-option label="保留进行中（KEEP_ACTIVE）：进行中/暂离订单保留至预约结束" value="KEEP_ACTIVE" />
              <el-option label="强制终止（FORCE_END）：无责终止进行中/暂离订单，不扣分不奖励" value="FORCE_END" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作原因" required>
            <el-input
              v-model="statusReason"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="请填写停用原因，将记入审计日志"
            />
          </el-form-item>
          <el-form-item label="影响预览">
            <el-button size="small" :loading="impactLoading" @click="previewSeatImpact">
              查询预估受影响订单
            </el-button>
            <div v-if="seatImpact" class="impact-panel">
              <div v-for="row in seatImpact" :key="row.label" class="impact-row">
                <span>{{ row.label }}</span>
                <strong>{{ row.value }}</strong>
              </div>
              <div class="field-hint">预估仅用于确认；执行时服务端会重新查询，以前端预估名单为准无效。</div>
            </div>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <p class="dialog-line">
          确认恢复座位「{{ statusSeat?.code }}」？恢复后该座位重新接受预约，不影响已有订单。
        </p>
        <el-form label-width="96px">
          <el-form-item label="操作原因">
            <el-input
              v-model="statusReason"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="选填，将记入审计日志"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="statusTarget === 'MAINTENANCE' && !statusOrderPolicy"
          @click="confirmStatusChange"
        >二次确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  fetchRoomSeats,
  batchGenerateSeats,
  updateSeat,
  updateSeatStatus,
  fetchSeatImpact
} from '@/api/admin'
import { fetchRoom } from '@/api/rooms'
import type { RoomDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const roomId = computed(() => String(route.params.id || ''))

const loading = ref(false)
const submitting = ref(false)
const room = ref<RoomDetail | null>(null)

interface AdminSeat {
  id: string
  code: string
  rowNo: number
  colNo: number
  hasPower: boolean
  nearWindow: boolean
  quietZone: boolean
  status: string
  version: string
}

/* fetchRoomSeats 返回 unknown[]，按字段名防御性解析 */
function parseSeats(raw: unknown[]): AdminSeat[] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      const s = (item || {}) as Record<string, unknown>
      const rowNo = Number(s.rowNo ?? s.row_no ?? 0)
      const colNo = Number(s.colNo ?? s.col_no ?? 0)
      return {
        id: String(s.id ?? ''),
        code: String(s.code ?? ''),
        rowNo: Number.isFinite(rowNo) ? rowNo : 0,
        colNo: Number.isFinite(colNo) ? colNo : 0,
        hasPower: Boolean(s.hasPower ?? s.has_power ?? false),
        nearWindow: Boolean(s.nearWindow ?? s.near_window ?? false),
        quietZone: Boolean(s.quietZone ?? s.quiet_zone ?? false),
        status: String(s.status ?? 'NORMAL'),
        version: s.version === undefined || s.version === null ? '' : String(s.version)
      }
    })
    .filter((s) => !!s.id)
    .sort((a, b) => a.rowNo - b.rowNo || a.colNo - b.colNo)
}

const seats = ref<AdminSeat[]>([])

async function loadAll(): Promise<void> {
  if (!roomId.value) return
  loading.value = true
  try {
    const [roomDetail, seatRes] = await Promise.all([
      fetchRoom(roomId.value),
      fetchRoomSeats(roomId.value)
    ])
    room.value = roomDetail
    seats.value = parseSeats((seatRes as { seats?: unknown[] })?.seats || [])
  } catch {
    room.value = null
    seats.value = []
  } finally {
    loading.value = false
  }
}

function goBack(): void {
  void router.push('/admin/rooms')
}

/* ---------- generic impact renderer ---------- */
interface KeyValueRow {
  label: string
  value: string
}

const IMPACT_LABELS: Record<string, string> = {
  affectedReserved: '待签到预约（无责取消）',
  affectedInUse: '使用中订单',
  affectedLeftTemp: '暂离中订单',
  affectedCancelled: '无责取消订单',
  affectedKept: '保留服务订单',
  affectedEnded: '强制终止订单',
  totalAffected: '合计受影响订单',
  total: '合计受影响订单',
  newStatus: '目标状态'
}

const IMPACT_KEY_PATTERN = /affected|reserved|inuse|in_use|lefttemp|left_temp|total|cancel|kept|ended|count|order|status/i

function toKeyValueRows(data: unknown): KeyValueRow[] {
  if (!data || typeof data !== 'object') return []
  return Object.entries(data as Record<string, unknown>)
    .filter(([key, value]) => IMPACT_KEY_PATTERN.test(key) && (typeof value === 'number' || typeof value === 'string'))
    .map(([key, value]) => ({ label: IMPACT_LABELS[key] || key, value: String(value) }))
}

/* ---------- batch generate ---------- */
const batchDialogVisible = ref(false)
const batchError = ref('')
const batchFormRef = ref<FormInstance>()
const batchForm = reactive({
  startRow: 1,
  endRow: 4,
  startCol: 1,
  endCol: 6,
  codePrefix: 'A',
  hasPower: false,
  nearWindow: false,
  quietZone: false
})

const PREFIX_PATTERN = /^[A-Z0-9-]{1,16}$/

const batchRules: FormRules<typeof batchForm> = {
  codePrefix: [
    { required: true, message: '请输入编号前缀', trigger: 'blur' },
    { pattern: PREFIX_PATTERN, message: '前缀仅允许大写字母、数字、短横线，最多 16 字符', trigger: 'blur' }
  ]
}

function resetBatchDialog(): void {
  batchFormRef.value?.clearValidate()
  batchError.value = ''
}

function openBatchDialog(): void {
  if (room.value) {
    batchForm.startRow = 1
    batchForm.endRow = room.value.rows
    batchForm.startCol = 1
    batchForm.endCol = room.value.cols
  }
  batchError.value = ''
  batchDialogVisible.value = true
}

async function submitBatch(): Promise<void> {
  if (!batchFormRef.value) return
  const valid = await batchFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (batchForm.startRow > batchForm.endRow || batchForm.startCol > batchForm.endCol) {
    batchError.value = '起始行/列不能大于结束行/列'
    return
  }
  if (room.value && (batchForm.endRow > room.value.rows || batchForm.endCol > room.value.cols)) {
    batchError.value = `结束行/列不能超出房间布局（${room.value.rows} 行 × ${room.value.cols} 列）`
    return
  }
  batchError.value = ''
  submitting.value = true
  try {
    const res = await batchGenerateSeats(roomId.value, {
      startRow: batchForm.startRow,
      endRow: batchForm.endRow,
      startCol: batchForm.startCol,
      endCol: batchForm.endCol,
      codePrefix: batchForm.codePrefix,
      hasPower: batchForm.hasPower,
      nearWindow: batchForm.nearWindow,
      quietZone: batchForm.quietZone
    })
    const generated = Number((res as { generated?: number })?.generated ?? 0)
    ElMessage.success(
      Number.isFinite(generated) && generated > 0 ? `已生成 ${generated} 个座位` : '座位生成完成'
    )
    batchDialogVisible.value = false
    await loadAll()
  } catch {
    // 冲突时后端返回 409 整批失败，http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- edit seat labels ---------- */
const editDialogVisible = ref(false)
const editError = ref('')
const editingSeat = ref<AdminSeat | null>(null)
const editForm = reactive({ hasPower: false, nearWindow: false, quietZone: false })

function openEditDialog(seat: AdminSeat): void {
  editingSeat.value = seat
  editForm.hasPower = seat.hasPower
  editForm.nearWindow = seat.nearWindow
  editForm.quietZone = seat.quietZone
  editError.value = ''
  editDialogVisible.value = true
}

function resetEditDialog(): void {
  editingSeat.value = null
}

async function submitEdit(): Promise<void> {
  if (!editingSeat.value) return
  submitting.value = true
  editError.value = ''
  try {
    const payload: Record<string, unknown> = {
      hasPower: editForm.hasPower,
      nearWindow: editForm.nearWindow,
      quietZone: editForm.quietZone
    }
    if (editingSeat.value.version) payload.expectedVersion = editingSeat.value.version
    await updateSeat(editingSeat.value.id, payload)
    editDialogVisible.value = false
    await loadAll()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- seat status ---------- */
const statusDialogVisible = ref(false)
const statusTarget = ref<'MAINTENANCE' | 'NORMAL'>('MAINTENANCE')
const statusSeat = ref<AdminSeat | null>(null)
const statusOrderPolicy = ref('')
const statusReason = ref('')
const statusError = ref('')
const seatImpact = ref<KeyValueRow[] | null>(null)
const impactLoading = ref(false)

function openStatusDialog(seat: AdminSeat, target: 'MAINTENANCE' | 'NORMAL'): void {
  statusSeat.value = seat
  statusTarget.value = target
  statusOrderPolicy.value = target === 'MAINTENANCE' ? 'KEEP_ACTIVE' : ''
  statusReason.value = ''
  statusError.value = ''
  seatImpact.value = null
  statusDialogVisible.value = true
}

function resetStatusDialog(): void {
  statusSeat.value = null
  seatImpact.value = null
}

async function previewSeatImpact(): Promise<void> {
  if (!statusSeat.value) return
  impactLoading.value = true
  try {
    const data = await fetchSeatImpact(statusSeat.value.id, { newStatus: statusTarget.value })
    seatImpact.value = toKeyValueRows(data)
    if (seatImpact.value.length === 0) {
      seatImpact.value = [{ label: '预估结果', value: '暂无受影响订单' }]
    }
  } catch {
    seatImpact.value = null
  } finally {
    impactLoading.value = false
  }
}

async function confirmStatusChange(): Promise<void> {
  if (!statusSeat.value) return
  if (statusTarget.value === 'MAINTENANCE') {
    if (!statusOrderPolicy.value) {
      statusError.value = '停用座位必须选择订单处理策略'
      return
    }
    if (!seatImpact.value) {
      statusError.value = '请先查询影响预览后再执行'
      return
    }
  }
  const seatCode = statusSeat.value.code
  let message =
    statusTarget.value === 'MAINTENANCE'
      ? `确认停用座位「${seatCode}」？策略：${
          statusOrderPolicy.value === 'FORCE_END' ? '强制终止' : '保留进行中'
        }。该操作将影响现存订单，不可撤销。`
      : `确认恢复座位「${seatCode}」？恢复后重新接受预约。`
  if (seatImpact.value) {
    message += ` 预估影响：${seatImpact.value.map((r) => `${r.label} ${r.value}`).join('；')}。`
  }
  const confirmed = await ElMessageBox.confirm(message, '二次确认', {
    confirmButtonText: '确认执行',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  submitting.value = true
  statusError.value = ''
  try {
    const payload: Record<string, unknown> = { status: statusTarget.value }
    if (statusReason.value.trim()) payload.reason = statusReason.value.trim()
    if (statusTarget.value === 'MAINTENANCE') payload.orderPolicy = statusOrderPolicy.value
    if (statusSeat.value.version) payload.expectedVersion = statusSeat.value.version
    await updateSeatStatus(statusSeat.value.id, payload)
    statusDialogVisible.value = false
    await loadAll()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.room-heading {
  font-size: 14px;
  color: var(--text-sub);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.card-title {
  font-weight: 600;
}
.batch-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.batch-grid .el-form-item {
  margin-bottom: 18px;
}
.seat-tag {
  margin-right: 6px;
}
.dialog-line {
  margin: 0 0 8px;
  font-size: 14px;
}
.dialog-list {
  margin: 0 0 12px;
  padding-left: 20px;
  color: var(--text-sub);
  font-size: 13px;
  line-height: 1.8;
}
.impact-panel {
  margin-top: 8px;
  padding: 10px 12px;
  background: var(--bg-page);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.impact-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 2px 0;
}
.field-hint {
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.5;
}
code {
  background: var(--bg-page);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0 4px;
}
</style>

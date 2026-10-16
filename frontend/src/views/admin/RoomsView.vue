<template>
  <div class="page">
    <div class="toolbar card">
      <div class="toolbar-left">
        <el-input
          v-model="filters.keyword"
          placeholder="编号 / 名称"
          clearable
          style="width: 200px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="filters.status" placeholder="房间状态" clearable style="width: 140px">
          <el-option label="开放中" value="OPEN" />
          <el-option label="已闭馆" value="CLOSED" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreateDialog">新建房间</el-button>
    </div>

    <div class="card table-wrap">
      <el-table :data="rooms" v-loading="loading" border empty-text="暂无数据">
        <el-table-column prop="code" label="编号" min-width="110" fixed show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="campus" label="校区" min-width="100" show-overflow-tooltip />
        <el-table-column prop="building" label="楼宇" min-width="100" show-overflow-tooltip />
        <el-table-column label="开放时间" min-width="150" align="center">
          <template #default="{ row }">{{ row.openTime }} — {{ row.closeTime }}</template>
        </el-table-column>
        <el-table-column label="布局" width="100" align="center">
          <template #default="{ row }">{{ row.rows }} 行 × {{ row.cols }} 列</template>
        </el-table-column>
        <el-table-column prop="matchingSeatCount" label="座位数" width="90" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'success' : 'info'" size="small">
              {{ row.status === 'OPEN' ? '开放中' : '已闭馆' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row)">座位布局</el-button>
            <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              v-if="row.status === 'OPEN'"
              link
              type="danger"
              size="small"
              @click="openStatusDialog(row, 'CLOSED')"
            >闭馆</el-button>
            <el-button
              v-else
              link
              type="success"
              size="small"
              @click="openStatusDialog(row, 'OPEN')"
            >开放</el-button>
            <el-button link type="warning" size="small" @click="openCodeDialog(row)">重设现场码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadRooms"
          @current-change="loadRooms"
        />
      </div>
    </div>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="editingId ? '编辑房间' : '新建房间'"
      width="560px"
      @closed="resetFormDialog"
    >
      <el-alert
        v-if="formError"
        type="error"
        :closable="false"
        show-icon
        :title="formError"
        class="mb-16"
      />
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="96px">
        <el-form-item label="编号" prop="code">
          <el-input v-model="form.code" placeholder="房间唯一编号，如 LIB-301" maxlength="32" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="房间名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="校区" prop="campus">
          <el-input v-model="form.campus" placeholder="如 中心校区" maxlength="64" />
        </el-form-item>
        <el-form-item label="楼宇" prop="building">
          <el-input v-model="form.building" placeholder="如 图书馆" maxlength="64" />
        </el-form-item>
        <el-form-item label="开放时间" required>
          <div class="time-range">
            <el-form-item prop="openTime">
              <el-time-picker
                v-model="form.openTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="开始时间"
              />
            </el-form-item>
            <span class="time-sep">至</span>
            <el-form-item prop="closeTime">
              <el-time-picker
                v-model="form.closeTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="结束时间"
              />
            </el-form-item>
          </div>
        </el-form-item>
        <el-form-item label="座位行数" prop="rows">
          <el-input-number v-model="form.rows" :min="1" :max="100" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="座位列数" prop="cols">
          <el-input-number v-model="form.cols" :min="1" :max="100" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="现场签到码" prop="checkinCode">
          <el-input
            v-model="form.checkinCode"
            placeholder="6 位数字"
            maxlength="6"
            style="width: 160px"
          />
          <div class="field-hint">仅允许设置，任何接口不回读现场码</div>
        </el-form-item>

        <!-- 编辑时缩短开放时间必须给出订单处理策略 -->
        <template v-if="editingId && hoursChanged">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="开放时间已修改"
            description="缩短开放时间会影响不再完全处于新开放范围内的订单，必须选择处理策略并确认影响预览。"
            class="mb-16"
          />
          <el-form-item label="订单策略" prop="orderPolicy">
            <el-select v-model="form.orderPolicy" placeholder="请选择处理策略" style="width: 100%">
              <el-option
                label="保留进行中（KEEP_ACTIVE）：进行中/暂离订单保留至预约结束，未获得使用权的预约无责取消"
                value="KEEP_ACTIVE"
              />
              <el-option
                label="强制终止（FORCE_END）：取消未开始预约并无责终止进行中/暂离订单，不扣分不奖励"
                value="FORCE_END"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="影响预览">
            <el-button size="small" :loading="impactLoading" @click="previewEditImpact">
              查询预估受影响订单
            </el-button>
            <div v-if="editImpact" class="impact-panel">
              <div v-for="row in editImpact" :key="row.label" class="impact-row">
                <span>{{ row.label }}</span>
                <strong>{{ row.value }}</strong>
              </div>
              <div class="field-hint">预估仅用于确认；执行时服务端会重新查询，以前端预估名单为准无效。</div>
            </div>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 闭馆 / 开放对话框 -->
    <el-dialog
      v-model="statusDialogVisible"
      :title="statusTarget === 'CLOSED' ? '闭馆房间' : '开放房间'"
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
      <template v-if="statusTarget === 'CLOSED'">
        <p class="dialog-line">
          将对「{{ statusRoom?.name }}（{{ statusRoom?.code }}）」执行闭馆。闭馆后：
        </p>
        <ul class="dialog-list">
          <li>所有尚未获得实际使用权的预约（RESERVED）无责取消；</li>
          <li>已提前签到或正在使用的订单按所选策略处理；</li>
          <li>闭馆后不可新增预约，保留中的订单仍可回座、签退。</li>
        </ul>
        <el-form label-width="96px">
          <el-form-item label="订单策略" required>
            <el-select v-model="statusOrderPolicy" placeholder="请选择处理策略" style="width: 100%">
              <el-option
                label="保留进行中（KEEP_ACTIVE）：进行中/暂离订单保留至预约结束"
                value="KEEP_ACTIVE"
              />
              <el-option
                label="强制终止（FORCE_END）：无责终止进行中/暂离订单，不扣分不奖励"
                value="FORCE_END"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="操作原因">
            <el-input
              v-model="statusReason"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="闭馆原因（选填），将记入审计日志"
            />
          </el-form-item>
          <el-form-item label="影响预览">
            <el-button size="small" :loading="impactLoading" @click="previewStatusImpact">
              查询预估受影响订单
            </el-button>
            <div v-if="statusImpact" class="impact-panel">
              <div v-for="row in statusImpact" :key="row.label" class="impact-row">
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
          将对「{{ statusRoom?.name }}（{{ statusRoom?.code }}）」执行开放。开放后房间恢复可预约。
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
          :disabled="statusTarget === 'CLOSED' && !statusOrderPolicy"
          @click="confirmStatusChange"
        >二次确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 状态变更结果 -->
    <el-dialog v-model="resultDialogVisible" title="执行结果" width="440px">
      <el-result v-if="resultData" icon="success" title="操作已完成">
        <template #sub-title>
          <div class="result-rows">
            <div v-for="row in resultData" :key="row.label" class="impact-row">
              <span>{{ row.label }}</span>
              <strong>{{ row.value }}</strong>
            </div>
          </div>
        </template>
      </el-result>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 重设现场码对话框 -->
    <el-dialog v-model="codeDialogVisible" title="重设现场签到码" width="440px" @closed="resetCodeDialog">
      <el-alert
        v-if="codeError"
        type="error"
        :closable="false"
        show-icon
        :title="codeError"
        class="mb-16"
      />
      <p class="dialog-line">
        为「{{ codeRoom?.name }}（{{ codeRoom?.code }}）」设置新的现场签到码。设置后任何接口都不会回读码值，请线下告知自习室使用者。
      </p>
      <el-form ref="codeFormRef" :model="codeForm" :rules="codeRules" label-width="96px">
        <el-form-item label="新签到码" prop="newCode">
          <el-input
            v-model="codeForm.newCode"
            placeholder="6 位数字"
            maxlength="6"
            style="width: 180px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="codeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitNewCode">确认重设</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  fetchAdminRooms,
  createRoom,
  updateRoom,
  updateRoomStatus,
  updateRoomCheckinCode,
  fetchRoomImpact
} from '@/api/admin'
import { fetchRoom } from '@/api/rooms'
import type { RoomListItem, RoomDetail } from '@/types'

const router = useRouter()

/* ---------- list ---------- */
const rooms = ref<RoomListItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive<{ keyword: string; status: string }>({ keyword: '', status: '' })

async function loadRooms(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    if (filters.status) params.status = filters.status
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    const res = await fetchAdminRooms(params)
    rooms.value = res.items || []
    total.value = res.total || 0
  } catch {
    rooms.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  void loadRooms()
}

function goDetail(row: RoomListItem): void {
  void router.push(`/admin/rooms/${row.id}`)
}

/* ---------- generic renderers for impact / result payloads ---------- */
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
    .map(([key, value]) => ({
      label: IMPACT_LABELS[key] || key,
      value: String(value)
    }))
}

/* ---------- create / edit ---------- */
/* 当前操作房间的版本号（来自 RoomDetail），编辑/闭馆/重设码共用 */
const currentVersion = ref('')

interface RoomFormState {
  code: string
  name: string
  campus: string
  building: string
  openTime: string
  closeTime: string
  rows: number
  cols: number
  checkinCode: string
  orderPolicy: string
}

const defaultForm = (): RoomFormState => ({
  code: '',
  name: '',
  campus: '',
  building: '',
  openTime: '07:00',
  closeTime: '22:30',
  rows: 4,
  cols: 6,
  checkinCode: '',
  orderPolicy: 'KEEP_ACTIVE'
})

const formDialogVisible = ref(false)
const editingId = ref('')
const originalHours = ref({ openTime: '', closeTime: '' })
const formError = ref('')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = ref<RoomFormState>(defaultForm())

const hoursChanged = computed(
  () =>
    !!editingId.value &&
    (form.value.openTime !== originalHours.value.openTime ||
      form.value.closeTime !== originalHours.value.closeTime)
)

const CHECKIN_CODE_PATTERN = /^[0-9]{6}$/
const CODE_PATTERN = /^[A-Za-z0-9_-]{1,32}$/

const formRules: FormRules<RoomFormState> = {
  code: [
    { required: true, message: '请输入房间编号', trigger: 'blur' },
    { pattern: CODE_PATTERN, message: '仅允许字母、数字、短横线、下划线，1—32 字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入房间名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过 100 字', trigger: 'blur' }
  ],
  campus: [
    { required: true, message: '请输入校区', trigger: 'blur' },
    { max: 64, message: '校区不能超过 64 字', trigger: 'blur' }
  ],
  building: [
    { required: true, message: '请输入楼宇', trigger: 'blur' },
    { max: 64, message: '楼宇不能超过 64 字', trigger: 'blur' }
  ],
  openTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  closeTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  checkinCode: [
    { required: true, message: '请输入 6 位现场签到码', trigger: 'blur' },
    { pattern: CHECKIN_CODE_PATTERN, message: '现场签到码必须为 6 位数字', trigger: 'blur' }
  ],
  orderPolicy: [
    {
      validator: (_rule, value, callback) => {
        if (hoursChanged.value && !value) callback(new Error('修改开放时间必须选择订单处理策略'))
        else callback()
      },
      trigger: 'change'
    }
  ]
}

function resetFormDialog(): void {
  formRef.value?.clearValidate()
  formError.value = ''
  editImpact.value = null
}

function openCreateDialog(): void {
  editingId.value = ''
  originalHours.value = { openTime: '', closeTime: '' }
  form.value = defaultForm()
  formDialogVisible.value = true
}

async function openEditDialog(row: RoomListItem): Promise<void> {
  editingId.value = row.id
  formError.value = ''
  editImpact.value = null
  formDialogVisible.value = true
  try {
    const detail = await fetchRoom(row.id)
    applyDetailToForm(detail)
  } catch {
    // http 层已提示；用列表数据兜底，版本校验时若失败会提示重新加载
    applyListItemToForm(row)
  }
}

function applyDetailToForm(detail: RoomDetail): void {
  currentVersion.value = detail.version
  form.value = {
    code: detail.code,
    name: detail.name,
    campus: detail.campus,
    building: detail.building,
    openTime: detail.openTime,
    closeTime: detail.closeTime,
    rows: detail.rows,
    cols: detail.cols,
    checkinCode: '',
    orderPolicy: 'KEEP_ACTIVE'
  }
  originalHours.value = { openTime: detail.openTime, closeTime: detail.closeTime }
}

function applyListItemToForm(row: RoomListItem): void {
  form.value = {
    code: row.code,
    name: row.name,
    campus: row.campus,
    building: row.building,
    openTime: row.openTime,
    closeTime: row.closeTime,
    rows: row.rows,
    cols: row.cols,
    checkinCode: '',
    orderPolicy: 'KEEP_ACTIVE'
  }
  originalHours.value = { openTime: row.openTime, closeTime: row.closeTime }
}

/* impact preview inside the edit dialog */
const impactLoading = ref(false)
const editImpact = ref<KeyValueRow[] | null>(null)

async function previewEditImpact(): Promise<void> {
  if (!editingId.value) return
  impactLoading.value = true
  try {
    const data = await fetchRoomImpact(editingId.value, {
      openTime: form.value.openTime,
      closeTime: form.value.closeTime
    })
    editImpact.value = toKeyValueRows(data)
    if (editImpact.value.length === 0) editImpact.value = [{ label: '预估结果', value: '暂无受影响订单' }]
  } catch {
    editImpact.value = null
  } finally {
    impactLoading.value = false
  }
}

async function submitForm(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.value.openTime >= form.value.closeTime) {
    formError.value = '开放开始时间必须早于结束时间'
    return
  }
  formError.value = ''

  if (editingId.value) {
    if (!currentVersion.value) {
      formError.value = '无法获取房间最新版本，请关闭对话框后重试'
      return
    }
    // 缩短开放时间：必须有策略 + 影响预览 + 二次确认
    if (hoursChanged.value) {
      if (!form.value.orderPolicy) {
        formError.value = '修改开放时间必须选择订单处理策略'
        return
      }
      if (!editImpact.value) {
        formError.value = '请先查询影响预览后再提交'
        return
      }
      const impactText = editImpact.value.map((r) => `${r.label}：${r.value}`).join('；')
      const confirmed = await confirmAction(
        '确认修改开放时间',
        `新开放时间 ${form.value.openTime} — ${form.value.closeTime}，订单策略：${
          form.value.orderPolicy === 'FORCE_END' ? '强制终止' : '保留进行中'
        }。预估影响：${impactText}。执行时服务端将重新核算，确定提交吗？`
      )
      if (!confirmed) return
    }
    submitting.value = true
    try {
      const payload: Record<string, unknown> = {
        code: form.value.code.trim(),
        name: form.value.name,
        campus: form.value.campus,
        building: form.value.building,
        openTime: form.value.openTime,
        closeTime: form.value.closeTime,
        rows: form.value.rows,
        cols: form.value.cols,
        expectedVersion: currentVersion.value
      }
      if (hoursChanged.value) payload.orderPolicy = form.value.orderPolicy
      await updateRoom(editingId.value, payload)
      formDialogVisible.value = false
      await loadRooms()
    } catch {
      // http 层已提示；409 时提示重新加载
    } finally {
      submitting.value = false
    }
    return
  }

  submitting.value = true
  try {
    await createRoom({
      code: form.value.code.trim(),
      name: form.value.name.trim(),
      campus: form.value.campus.trim(),
      building: form.value.building.trim(),
      openTime: form.value.openTime,
      closeTime: form.value.closeTime,
      rows: form.value.rows,
      cols: form.value.cols,
      checkinCode: form.value.checkinCode
    })
    formDialogVisible.value = false
    await loadRooms()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* current version of the room being edited (from RoomDetail) */
function confirmAction(title: string, message: string): Promise<boolean> {
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: '确认执行',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => true)
    .catch(() => false)
}

/* ---------- status change (open / close) ---------- */
const statusDialogVisible = ref(false)
const statusTarget = ref<'OPEN' | 'CLOSED'>('CLOSED')
const statusRoom = ref<RoomListItem | null>(null)
const statusOrderPolicy = ref('')
const statusReason = ref('')
const statusError = ref('')
const statusImpact = ref<KeyValueRow[] | null>(null)
const resultDialogVisible = ref(false)
const resultData = ref<KeyValueRow[] | null>(null)

function openStatusDialog(row: RoomListItem, target: 'OPEN' | 'CLOSED'): void {
  statusRoom.value = row
  statusTarget.value = target
  statusOrderPolicy.value = target === 'CLOSED' ? 'KEEP_ACTIVE' : ''
  statusReason.value = ''
  statusError.value = ''
  statusImpact.value = null
  currentVersion.value = ''
  statusDialogVisible.value = true
  // 版本号来自详情，编辑/状态执行都需要
  void fetchRoom(row.id)
    .then((detail) => {
      currentVersion.value = detail.version
    })
    .catch(() => {
      statusError.value = '无法获取房间最新版本，请刷新列表后重试'
    })
}

function resetStatusDialog(): void {
  statusRoom.value = null
  statusImpact.value = null
}

async function previewStatusImpact(): Promise<void> {
  if (!statusRoom.value) return
  impactLoading.value = true
  try {
    const params: Record<string, unknown> = { newStatus: statusTarget.value }
    const data = await fetchRoomImpact(statusRoom.value.id, params)
    statusImpact.value = toKeyValueRows(data)
    if (statusImpact.value.length === 0) {
      statusImpact.value = [{ label: '预估结果', value: '暂无受影响订单' }]
    }
  } catch {
    statusImpact.value = null
  } finally {
    impactLoading.value = false
  }
}

async function confirmStatusChange(): Promise<void> {
  if (!statusRoom.value) return
  if (statusTarget.value === 'CLOSED' && !statusOrderPolicy.value) {
    statusError.value = '闭馆必须选择订单处理策略'
    return
  }
  if (!statusImpact.value && statusTarget.value === 'CLOSED') {
    statusError.value = '请先查询影响预览后再执行'
    return
  }
  if (!currentVersion.value) {
    statusError.value = '无法获取房间最新版本，请刷新列表后重试'
    return
  }
  const roomName = statusRoom.value.name
  let message =
    statusTarget.value === 'CLOSED'
      ? `确认闭馆「${roomName}」？策略：${
          statusOrderPolicy.value === 'FORCE_END' ? '强制终止' : '保留进行中'
        }。该操作将影响现存订单，不可撤销。`
      : `确认开放「${roomName}」？开放后恢复接受新预约。`
  if (statusImpact.value) {
    message += ` 预估影响：${statusImpact.value.map((r) => `${r.label} ${r.value}`).join('；')}。`
  }
  const confirmed = await confirmAction('二次确认', message)
  if (!confirmed) return

  submitting.value = true
  statusError.value = ''
  try {
    const payload: Record<string, unknown> = {
      status: statusTarget.value,
      expectedVersion: currentVersion.value
    }
    if (statusReason.value.trim()) payload.reason = statusReason.value.trim()
    if (statusTarget.value === 'CLOSED') payload.orderPolicy = statusOrderPolicy.value
    const result = await updateRoomStatus(statusRoom.value.id, payload)
    const rows = toKeyValueRows(result)
    resultData.value =
      rows.length > 0 ? rows : [{ label: '状态', value: statusTarget.value === 'CLOSED' ? '已闭馆' : '已开放' }]
    statusDialogVisible.value = false
    resultDialogVisible.value = true
    await loadRooms()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- reset checkin code ---------- */
const codeDialogVisible = ref(false)
const codeRoom = ref<RoomListItem | null>(null)
const codeError = ref('')
const codeFormRef = ref<FormInstance>()
const codeForm = ref({ newCode: '' })
const codeRules: FormRules<{ newCode: string }> = {
  newCode: [
    { required: true, message: '请输入新的 6 位签到码', trigger: 'blur' },
    { pattern: CHECKIN_CODE_PATTERN, message: '现场签到码必须为 6 位数字', trigger: 'blur' }
  ]
}

function openCodeDialog(row: RoomListItem): void {
  codeRoom.value = row
  codeForm.value = { newCode: '' }
  codeError.value = ''
  currentVersion.value = ''
  codeDialogVisible.value = true
  void fetchRoom(row.id)
    .then((detail) => {
      currentVersion.value = detail.version
    })
    .catch(() => {
      codeError.value = '无法获取房间最新版本，请刷新列表后重试'
    })
}

function resetCodeDialog(): void {
  codeFormRef.value?.clearValidate()
}

async function submitNewCode(): Promise<void> {
  if (!codeFormRef.value || !codeRoom.value) return
  const valid = await codeFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (!currentVersion.value) {
    codeError.value = '无法获取房间最新版本，请刷新列表后重试'
    return
  }
  const confirmed = await confirmAction(
    '二次确认',
    `确认重设「${codeRoom.value.name}」的现场签到码？旧码立即失效，请线下告知使用者。`
  )
  if (!confirmed) return
  submitting.value = true
  codeError.value = ''
  try {
    await updateRoomCheckinCode(codeRoom.value.id, {
      newCode: codeForm.value.newCode,
      expectedVersion: currentVersion.value
    })
    codeDialogVisible.value = false
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadRooms()
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
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.time-range {
  display: flex;
  align-items: center;
  gap: 8px;
}
.time-range .el-form-item {
  margin-bottom: 0;
}
.time-sep {
  color: var(--text-sub);
}
.field-hint {
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.5;
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
.result-rows {
  text-align: left;
  display: inline-block;
}
</style>

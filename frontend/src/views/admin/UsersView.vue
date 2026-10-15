<template>
  <div class="page">
    <div class="toolbar card">
      <div class="toolbar-left">
        <el-input
          v-model="filters.account"
          placeholder="学号 / 账号"
          clearable
          style="width: 150px"
          @keyup.enter="onSearch"
        />
        <el-input
          v-model="filters.name"
          placeholder="姓名"
          clearable
          style="width: 120px"
          @keyup.enter="onSearch"
        />
        <el-input
          v-model="filters.college"
          placeholder="学院"
          clearable
          style="width: 150px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="正常" value="ACTIVE" />
          <el-option label="已禁用" value="DISABLED" />
        </el-select>
        <el-select v-model="filters.blocked" placeholder="黑名单" clearable style="width: 110px">
          <el-option label="黑名单中" :value="true" />
          <el-option label="非黑名单" :value="false" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
      </div>
      <el-button type="primary" @click="createDialogVisible = true">新建学生账号</el-button>
    </div>

    <div class="card table-wrap">
      <el-table :data="users" v-loading="loading" border empty-text="暂无数据">
        <el-table-column prop="account" label="账号" min-width="120" fixed show-overflow-tooltip />
        <el-table-column prop="name" label="姓名" min-width="90" show-overflow-tooltip />
        <el-table-column prop="college" label="学院" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.college || '—' }}</template>
        </el-table-column>
        <el-table-column label="账号状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="黑名单" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.blocked" type="danger" size="small">黑名单中</el-tag>
            <el-tag v-else type="info" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="信用分" width="150" align="center">
          <template #default="{ row }">
            <span :class="{ 'credit-low': creditLow(row.creditScore) }">{{ row.creditScore }}</span>
            <el-tag v-if="creditLow(row.creditScore)" type="warning" size="small" class="credit-tag">
              低于门槛
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="首次改密" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.mustChangePassword" type="warning" size="small">待修改</el-tag>
            <span v-else class="text-sub">已完成</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="danger"
              size="small"
              @click="openStatusDialog(row, 'DISABLED')"
            >禁用</el-button>
            <el-button
              v-else
              link
              type="success"
              size="small"
              @click="openStatusDialog(row, 'ACTIVE')"
            >启用</el-button>
            <el-button
              v-if="!row.blocked"
              link
              type="danger"
              size="small"
              @click="openBlockDialog(row, true)"
            >加入黑名单</el-button>
            <el-button
              v-else
              link
              type="success"
              size="small"
              @click="openBlockDialog(row, false)"
            >解除黑名单</el-button>
            <el-button link type="warning" size="small" @click="openResetDialog(row)">重置密码</el-button>
            <el-button link type="primary" size="small" @click="openCreditDialog(row)">信用调整</el-button>
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
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </div>

    <!-- 新建学生 -->
    <el-dialog v-model="createDialogVisible" title="新建学生账号" width="480px" @closed="resetCreateDialog">
      <el-alert
        v-if="createError"
        type="error"
        :closable="false"
        show-icon
        :title="createError"
        class="mb-16"
      />
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px">
        <el-form-item label="账号（学号）" prop="account">
          <el-input v-model="createForm.account" placeholder="学生学号" maxlength="32" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="createForm.name" placeholder="学生姓名" maxlength="64" />
        </el-form-item>
        <el-form-item label="学院" prop="college">
          <el-input v-model="createForm.college" placeholder="选填" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 一次性密码展示（创建 / 重置共用） -->
    <el-dialog v-model="passwordVisible" title="一次性密码" width="440px" :show-close="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="该密码仅本次可见，请线下安全交付"
        description="关闭本对话框后不再展示；若遗失请使用「重置密码」重新生成，旧密码立即失效。首次登录仅允许修改密码或退出。"
        class="mb-16"
      />
      <div class="password-box">
        <span class="password-text">{{ oneTimePassword }}</span>
        <el-button size="small" @click="copyPassword">复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="passwordVisible = false">我已线下记录</el-button>
      </template>
    </el-dialog>

    <!-- 禁用 / 启用 -->
    <el-dialog
      v-model="statusDialogVisible"
      :title="statusTarget === 'DISABLED' ? '禁用学生账号' : '启用学生账号'"
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
      <template v-if="statusTarget === 'DISABLED'">
        <p class="dialog-line">
          禁用「{{ statusUser?.name }}（{{ statusUser?.account }}）」后，该账号禁止登录和全部新增业务写操作。
        </p>
        <ul class="dialog-list">
          <li>若存在尚未获得实际使用权的预约，将无责取消；</li>
          <li>进行中 / 暂离订单如选择无责终止，将不扣分、不奖励；</li>
          <li>该学生的全部会话立即作废；已禁用用户的后台超时结算仍会继续执行。</li>
        </ul>
        <div v-if="activeOrdersLoading" class="text-sub mb-16">正在核查有效订单…</div>
        <el-alert
          v-else-if="activeOrderCount > 0"
          type="warning"
          :closable="false"
          show-icon
          :title="`该学生当前有 ${activeOrderCount} 笔有效订单，禁用时需要确认无责终止`"
          class="mb-16"
        />
        <el-form label-width="96px">
          <el-form-item label="操作原因" required>
            <el-input
              v-model="statusReason"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="请填写禁用原因，将记入审计日志"
            />
          </el-form-item>
          <el-form-item v-if="activeOrderCount > 0" label="无责终止" required>
            <el-checkbox v-model="statusConfirmTerminate">
              确认无责取消未开始预约并无责终止进行中 / 暂离订单（不扣分、不奖励）
            </el-checkbox>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <p class="dialog-line">
          启用「{{ statusUser?.name }}（{{ statusUser?.account }}）」后恢复登录和新增业务写操作。解除人工限制不会绕过信用门槛，信用不足仍禁止新增预约。
        </p>
        <el-form label-width="96px">
          <el-form-item label="操作原因" required>
            <el-input
              v-model="statusReason"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="请填写启用原因，将记入审计日志"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="statusTarget === 'DISABLED' && activeOrderCount > 0 && !statusConfirmTerminate"
          @click="confirmStatusChange"
        >二次确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 黑名单开关 -->
    <el-dialog
      v-model="blockDialogVisible"
      :title="blockTarget ? '加入黑名单' : '解除黑名单'"
      width="480px"
      @closed="resetBlockDialog"
    >
      <el-alert
        v-if="blockError"
        type="error"
        :closable="false"
        show-icon
        :title="blockError"
        class="mb-16"
      />
      <p class="dialog-line">
        <template v-if="blockTarget">
          将「{{ blockUser?.name }}（{{ blockUser?.account }}）」加入黑名单：禁止登录和全部新增业务写操作，已有订单仍会正常结算。
        </template>
        <template v-else>
          解除「{{ blockUser?.name }}（{{ blockUser?.account }}）」的人工黑名单：仅解除人工限制，不改变信用分，也不绕过信用门槛。
        </template>
      </p>
      <el-form label-width="96px">
        <el-form-item label="操作原因" required>
          <el-input
            v-model="blockReason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请填写原因，将记入审计日志"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmBlockChange">二次确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="resetDialogVisible" title="重置学生密码" width="480px" @closed="resetResetDialog">
      <el-alert
        v-if="resetError"
        type="error"
        :closable="false"
        show-icon
        :title="resetError"
        class="mb-16"
      />
      <p class="dialog-line">
        为「{{ resetUser?.name }}（{{ resetUser?.account }}）」生成新的一次性密码。旧密码立即失效，全部旧会话作废。
      </p>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="关于重试"
        description="若响应丢失，请再次发起重置（会生成新的一次性密码）；同一操作的重复提交不会二次改密，也不会重新展示密码。"
        class="mb-16"
      />
      <el-form label-width="96px">
        <el-form-item label="操作原因" required>
          <el-input
            v-model="resetReason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请填写重置原因，将记入审计日志"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmReset">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 信用调整 -->
    <el-dialog v-model="creditDialogVisible" title="信用调整" width="480px" @closed="resetCreditDialog">
      <el-alert
        v-if="creditError"
        type="error"
        :closable="false"
        show-icon
        :title="creditError"
        class="mb-16"
      />
      <p class="dialog-line">
        学生：{{ creditUser?.name }}（{{ creditUser?.account }}）· 当前信用分
        <strong>{{ creditUser?.creditScore }}</strong>
      </p>
      <el-form ref="creditFormRef" :model="creditForm" :rules="creditRules" label-width="96px">
        <el-form-item label="目标分数" prop="targetScore">
          <el-input-number v-model="creditForm.targetScore" :min="0" :max="100" :step="1" controls-position="right" />
          <span class="text-sub ml-8">调整后：{{ creditDiffLabel }}</span>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input
            v-model="creditForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写 5—500 字的调整原因，将记入审计日志与信用流水"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="creditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCredit">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  fetchAdminUsers,
  createStudent,
  updateUserStatus,
  updateUserBlock,
  resetUserPassword,
  adjustUserCredit,
  fetchAdminReservations
} from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import { useRuleStore } from '@/stores/rule'
import type { AdminUserItem, UserStatus } from '@/types'

const ruleStore = useRuleStore()

/* ---------- list ---------- */
const users = ref<AdminUserItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({
  account: '',
  name: '',
  college: '',
  status: '' as UserStatus | '',
  blocked: '' as boolean | ''
})

const minCredit = computed(() => ruleStore.rule?.minCredit ?? null)
function creditLow(score: number): boolean {
  return minCredit.value !== null && score < minCredit.value
}

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    if (filters.account.trim()) params.account = filters.account.trim()
    if (filters.name.trim()) params.name = filters.name.trim()
    if (filters.college.trim()) params.college = filters.college.trim()
    if (filters.status) params.status = filters.status
    if (typeof filters.blocked === 'boolean') params.blocked = filters.blocked
    const res = await fetchAdminUsers(params)
    users.value = res.items || []
    total.value = res.total || 0
  } catch {
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  void loadUsers()
}

/* ---------- shared helpers ---------- */
const submitting = ref(false)

function rowAuthVersion(row: AdminUserItem): string {
  const raw = (row as unknown as Record<string, unknown>).authVersion
  return raw === undefined || raw === null ? '' : String(raw)
}

/* ---------- create student ---------- */
const createDialogVisible = ref(false)
const createError = ref('')
const createFormRef = ref<FormInstance>()
const createForm = reactive({ account: '', name: '', college: '' })
const createRules: FormRules<typeof createForm> = {
  account: [
    { required: true, message: '请输入学生学号', trigger: 'blur' },
    { max: 32, message: '学号不能超过 32 字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入学生姓名', trigger: 'blur' },
    { max: 64, message: '姓名不能超过 64 字符', trigger: 'blur' }
  ],
  college: [{ max: 100, message: '学院不能超过 100 字符', trigger: 'blur' }]
}

function resetCreateDialog(): void {
  createFormRef.value?.clearValidate()
  createError.value = ''
  createForm.account = ''
  createForm.name = ''
  createForm.college = ''
}

async function submitCreate(): Promise<void> {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  createError.value = ''
  try {
    const res = await createStudent({
      account: createForm.account.trim(),
      name: createForm.name.trim(),
      college: createForm.college.trim() || undefined
    })
    oneTimePassword.value = res.oneTimePassword || ''
    createDialogVisible.value = false
    passwordVisible.value = true
    await loadUsers()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- one-time password display ---------- */
const passwordVisible = ref(false)
const oneTimePassword = ref('')

async function copyPassword(): Promise<void> {
  try {
    await navigator.clipboard.writeText(oneTimePassword.value)
    ElMessage.success('已复制到剪贴板，请妥善保管')
  } catch {
    ElMessage.warning('复制失败，请手动记录')
  }
}

/* ---------- disable / enable ---------- */
const statusDialogVisible = ref(false)
const statusTarget = ref<'DISABLED' | 'ACTIVE'>('DISABLED')
const statusUser = ref<AdminUserItem | null>(null)
const statusReason = ref('')
const statusError = ref('')
const statusConfirmTerminate = ref(false)
const activeOrderCount = ref(0)
const activeOrdersLoading = ref(false)

function resetStatusDialog(): void {
  statusUser.value = null
  statusReason.value = ''
  statusConfirmTerminate.value = false
  activeOrderCount.value = 0
}

async function openStatusDialog(row: AdminUserItem, target: 'DISABLED' | 'ACTIVE'): Promise<void> {
  statusUser.value = row
  statusTarget.value = target
  statusReason.value = ''
  statusError.value = ''
  statusConfirmTerminate.value = false
  activeOrderCount.value = 0
  statusDialogVisible.value = true
  if (target === 'DISABLED') {
    await checkActiveOrders(row.account)
  }
}

/* 禁用前核查有效订单（未终态）数量 */
async function checkActiveOrders(account: string): Promise<void> {
  activeOrdersLoading.value = true
  try {
    const res = await fetchAdminReservations({ account, page: 1, size: 100 })
    const active = (res.items || []).filter((item) =>
      ['RESERVED', 'IN_USE', 'LEFT_TEMP'].includes(item.status)
    )
    activeOrderCount.value = active.length
  } catch {
    activeOrderCount.value = 0
  } finally {
    activeOrdersLoading.value = false
  }
}

async function confirmStatusChange(): Promise<void> {
  if (!statusUser.value) return
  if (!statusReason.value.trim()) {
    statusError.value = '请填写操作原因'
    return
  }
  if (statusTarget.value === 'DISABLED' && activeOrderCount.value > 0 && !statusConfirmTerminate.value) {
    statusError.value = '该学生有有效订单，需确认无责终止后才能禁用'
    return
  }
  const userName = statusUser.value.name
  const message =
    statusTarget.value === 'DISABLED'
      ? `确认禁用「${userName}」？${activeOrderCount.value > 0 ? `将无责取消/终止 ${activeOrderCount.value} 笔有效订单，` : ''}该账号将禁止登录。`
      : `确认启用「${userName}」？`
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
    const payload: Record<string, unknown> = {
      status: statusTarget.value,
      reason: statusReason.value.trim()
    }
    const authVersion = rowAuthVersion(statusUser.value)
    if (authVersion) payload.expectedAuthVersion = authVersion
    if (statusTarget.value === 'DISABLED' && statusConfirmTerminate.value) {
      payload.confirmTerminate = true
    }
    await updateUserStatus(statusUser.value.id, payload)
    statusDialogVisible.value = false
    await loadUsers()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- block toggle ---------- */
const blockDialogVisible = ref(false)
const blockTarget = ref(true)
const blockUser = ref<AdminUserItem | null>(null)
const blockReason = ref('')
const blockError = ref('')

function resetBlockDialog(): void {
  blockUser.value = null
  blockReason.value = ''
  blockError.value = ''
}

function openBlockDialog(row: AdminUserItem, target: boolean): void {
  blockUser.value = row
  blockTarget.value = target
  blockReason.value = ''
  blockError.value = ''
  blockDialogVisible.value = true
}

async function confirmBlockChange(): Promise<void> {
  if (!blockUser.value) return
  if (!blockReason.value.trim()) {
    blockError.value = '请填写操作原因'
    return
  }
  const userName = blockUser.value.name
  const confirmed = await ElMessageBox.confirm(
    blockTarget.value ? `确认将「${userName}」加入黑名单？` : `确认解除「${userName}」的人工黑名单？`,
    '二次确认',
    { confirmButtonText: '确认执行', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  submitting.value = true
  blockError.value = ''
  try {
    const payload: Record<string, unknown> = {
      blocked: blockTarget.value,
      reason: blockReason.value.trim()
    }
    const authVersion = rowAuthVersion(blockUser.value)
    if (authVersion) payload.expectedAuthVersion = authVersion
    await updateUserBlock(blockUser.value.id, payload)
    blockDialogVisible.value = false
    await loadUsers()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- reset password ---------- */
const resetDialogVisible = ref(false)
const resetUser = ref<AdminUserItem | null>(null)
const resetReason = ref('')
const resetError = ref('')

function resetResetDialog(): void {
  resetUser.value = null
  resetReason.value = ''
  resetError.value = ''
}

function openResetDialog(row: AdminUserItem): void {
  resetUser.value = row
  resetReason.value = ''
  resetError.value = ''
  resetDialogVisible.value = true
}

async function confirmReset(): Promise<void> {
  if (!resetUser.value) return
  if (!resetReason.value.trim()) {
    resetError.value = '请填写操作原因'
    return
  }
  const confirmed = await ElMessageBox.confirm(
    `确认为「${resetUser.value.name}」重置密码？旧密码立即失效，全部旧会话作废。`,
    '二次确认',
    { confirmButtonText: '确认重置', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  submitting.value = true
  resetError.value = ''
  try {
    const res = await resetUserPassword(resetUser.value.id, { reason: resetReason.value.trim() })
    oneTimePassword.value = res.oneTimePassword || ''
    resetDialogVisible.value = false
    passwordVisible.value = true
    await loadUsers()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- credit adjust ---------- */
const creditDialogVisible = ref(false)
const creditUser = ref<AdminUserItem | null>(null)
const creditError = ref('')
const creditFormRef = ref<FormInstance>()
const creditForm = reactive({ targetScore: 100, reason: '' })
const creditRules: FormRules<typeof creditForm> = {
  targetScore: [
    { required: true, message: '请输入目标分数', trigger: 'blur' },
    { type: 'number', min: 0, max: 100, message: '目标分数必须在 0—100 之间', trigger: 'blur' }
  ],
  reason: [
    { required: true, message: '请填写调整原因', trigger: 'blur' },
    { min: 5, max: 500, message: '原因需 5—500 字', trigger: 'blur' }
  ]
}

const creditDiffLabel = computed(() => {
  if (!creditUser.value) return '—'
  const delta = creditForm.targetScore - creditUser.value.creditScore
  return `${delta >= 0 ? '+' : ''}${delta} 分`
})

function resetCreditDialog(): void {
  creditFormRef.value?.clearValidate()
  creditUser.value = null
  creditError.value = ''
  creditForm.reason = ''
}

function openCreditDialog(row: AdminUserItem): void {
  creditUser.value = row
  creditForm.targetScore = row.creditScore
  creditForm.reason = ''
  creditError.value = ''
  creditDialogVisible.value = true
}

async function submitCredit(): Promise<void> {
  if (!creditFormRef.value || !creditUser.value) return
  const valid = await creditFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (creditForm.targetScore === creditUser.value.creditScore) {
    creditError.value = '目标分数与当前信用分相同，无需调整'
    return
  }
  const before = creditUser.value.creditScore
  const after = creditForm.targetScore
  const confirmed = await ElMessageBox.confirm(
    `确认将「${creditUser.value.name}」的信用分从 ${before} 调整为 ${after}？该操作记入信用流水与审计日志。`,
    '二次确认',
    { confirmButtonText: '确认调整', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  submitting.value = true
  creditError.value = ''
  try {
    const res = await adjustUserCredit(creditUser.value.id, {
      targetScore: creditForm.targetScore,
      reason: creditForm.reason.trim()
    })
    // 展示前后余额（后端返回优先，否则用本地值）
    const data = (res || {}) as Record<string, unknown>
    const balanceBefore = data.balanceBefore !== undefined ? Number(data.balanceBefore) : before
    const balanceAfter = data.balanceAfter !== undefined ? Number(data.balanceAfter) : after
    let resultText = `信用调整完成：${balanceBefore} → ${balanceAfter}`
    if (data.actualDelta !== undefined && Number.isFinite(Number(data.actualDelta))) {
      const delta = Number(data.actualDelta)
      resultText += `（实际入账 ${delta >= 0 ? '+' : ''}${delta} 分）`
    }
    creditDialogVisible.value = false
    await ElMessageBox.alert(resultText, '调整结果', {
      confirmButtonText: '知道了',
      type: 'success'
    })
    await loadUsers()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void ruleStore.load().catch(() => null)
  void loadUsers()
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
  gap: 8px;
  flex-wrap: wrap;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.credit-low {
  color: var(--color-danger);
  font-weight: 700;
}
.credit-tag {
  margin-left: 6px;
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
.password-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border: 1px dashed var(--color-warning);
  border-radius: 8px;
  background: #fffbeb;
}
.password-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
  word-break: break-all;
}
.ml-8 {
  margin-left: 8px;
}
</style>

<template>
  <div class="page">
    <div class="card">
      <h3 class="card-title">新建申诉</h3>
      <div v-if="selectableViolations.length" class="appeal-form">
        <div class="field">
          <label class="field-label">选择处罚</label>
          <el-select v-model="form.violationId" style="width: 100%" placeholder="请选择要申诉的生效处罚">
            <el-option v-for="v in selectableViolations" :key="v.id" :label="violationLabel(v)" :value="v.id">
              <span class="option-main">{{ violationLabel(v) }}</span>
              <span class="option-sub text-sub">申诉截止：{{ formatDateTime(appealDeadline(v)) }}（{{ appealCountdown(v) }}）</span>
            </el-option>
          </el-select>
        </div>
        <div class="field">
          <label class="field-label">申诉理由（10—1000 字）</label>
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="请客观说明情况，例如当时已到馆但签到设备异常等"
            :disabled="submitting"
          />
        </div>
        <div class="field">
          <label class="field-label">凭证（可选，最多 3 张）</label>
          <el-upload
            v-model:file-list="fileList"
            list-type="picture-card"
            accept="image/jpeg,image/png"
            :limit="3"
            :http-request="customUpload"
            :before-upload="beforeUpload"
            :on-remove="onRemove"
            :on-exceed="onExceed"
            :disabled="submitting"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <p class="text-sub">仅支持 JPEG/PNG，每张不超过 5MiB；上传后仍可删除重传。</p>
        </div>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="onSubmit">提交申诉</el-button>
      </div>
      <el-empty v-else-if="!violationsLoading" description="当前没有可申诉的处罚" :image-size="60" />
      <el-skeleton v-else :rows="2" animated />
    </div>

    <div class="card">
      <div class="flex-between card-head">
        <h3 class="card-title">我的申诉</h3>
        <el-select v-model="statusFilter" style="width: 130px" @change="onFilterChange">
          <el-option label="全部状态" value="ALL" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </div>
      <div v-if="loading" class="card">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="loadError" class="error-card">
        <p class="error-text">加载失败：{{ loadError }}</p>
        <el-button type="primary" @click="loadList">重试</el-button>
      </div>
      <div v-else-if="!items.length" class="card">
        <el-empty description="暂无申诉记录" :image-size="60" />
      </div>
      <div v-else class="table-wrap">
        <el-table :data="items" row-class-name="clickable-row" @row-click="onRowClick">
          <el-table-column label="处罚" min-width="140">
            <template #default="{ row }">
              <span class="cell-main">{{ row.violationSummary ? reasonLabel(row.violationSummary.reason) : '违约处罚' }}</span>
              <span v-if="row.violationSummary" class="text-sub cell-sub">
                {{ row.violationSummary.roomName }} · {{ row.violationSummary.seatCode }} · 扣 {{ row.violationSummary.deducted }} 分
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <StatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="130">
            <template #default="{ row }">
              <span class="cell-main">{{ formatDateTime(row.submittedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="审核结果" min-width="120">
            <template #default="{ row }">
              <span v-if="row.reviewComment" class="cell-main">{{ row.reviewComment }}</span>
              <span v-else class="text-sub">{{ row.status === 'PENDING' ? '审核中' : '—' }}</span>
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

    <el-dialog v-model="detailDialog" title="申诉详情" width="92%" @closed="closeDetail">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="状态">
            <StatusTag :status="detail.status" />
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(detail.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reviewedAt" label="审核时间">{{ formatDateTime(detail.reviewedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.violationSummary" label="关联处罚">
            {{ reasonLabel(detail.violationSummary.reason) }} · {{ detail.violationSummary.roomName }} · {{ detail.violationSummary.seatCode }} · 扣 {{ detail.violationSummary.deducted }} 分（订单 {{ detail.violationSummary.orderNo }}）
          </el-descriptions-item>
          <el-descriptions-item label="申诉理由">
            <span class="reason-text">{{ detail.reason }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.reviewComment" label="审核意见">{{ detail.reviewComment }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.fileIds.length" class="evidence-block">
          <p class="evidence-title">凭证材料</p>
          <div v-if="evidenceLoading" class="evidence-loading"><el-icon class="is-loading"><Loading /></el-icon> 正在加载凭证…</div>
          <div v-else-if="evidenceUrls.length" class="evidence-list">
            <img v-for="(url, idx) in evidenceUrls" :key="idx" :src="url" class="evidence-img" alt="申诉凭证" />
          </div>
          <p v-else class="text-sub">凭证加载失败，请稍后重试。</p>
        </div>
      </template>
      <template #footer>
        <el-button type="primary" @click="closeDetail">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Plus } from '@element-plus/icons-vue'
import {
  fetchAppeal,
  fetchEvidenceBlob,
  fetchMyAppeals,
  fetchMyViolations,
  submitAppeal,
  uploadEvidence
} from '@/api/appeals'
import { useServerTime } from '@/composables/useServerTime'
import { formatDateTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import type { UploadFile, UploadRequestOptions, UploadUserFile } from 'element-plus'
import type { AppealItem, ViolationItem } from '@/types'

const route = useRoute()
const { formatCountdown } = useServerTime()

/* ---------- 新建申诉 ---------- */
const violations = ref<ViolationItem[]>([])
const violationsLoading = ref(true)
const selectableViolations = computed(() => violations.value.filter((v) => v.status === 'ACTIVE' && v.appealable))
const form = reactive({ violationId: '', reason: '' })
const fileList = ref<UploadUserFile[]>([])
const fileIds = ref<string[]>([])
const uidToFileId = new Map<string, string>()
const submitting = ref(false)

const APPEAL_WINDOW_MS = 48 * 60 * 60 * 1000
const tick = ref(Date.now())
let timer: number | undefined

function appealDeadline(v: ViolationItem): string {
  return new Date(Date.parse(v.notifiedAt) + APPEAL_WINDOW_MS).toISOString()
}
function appealCountdown(v: ViolationItem): string {
  void tick.value
  return formatCountdown(appealDeadline(v))
}
function violationLabel(v: ViolationItem): string {
  const reason = v.reason === 'NO_SHOW' ? '未签到违约' : v.reason === 'LEAVE_TIMEOUT' ? '暂离超时违约' : v.reason
  return `${reason}（扣 ${v.deducted} 分）`
}
function reasonLabel(reason: string): string {
  if (reason === 'NO_SHOW') return '未签到违约'
  if (reason === 'LEAVE_TIMEOUT') return '暂离超时违约'
  return reason
}

function beforeUpload(rawFile: File): boolean {
  const okType = rawFile.type === 'image/jpeg' || rawFile.type === 'image/png'
  if (!okType) {
    ElMessage.error('凭证仅支持 JPEG/PNG 格式')
    return false
  }
  if (rawFile.size > 5 * 1024 * 1024) {
    ElMessage.error('单张凭证不能超过 5MiB')
    return false
  }
  return true
}

function onExceed(): void {
  ElMessage.warning('最多上传 3 张凭证')
}

function onRemove(file: UploadFile): void {
  const fid = uidToFileId.get(String(file.uid))
  if (fid) {
    fileIds.value = fileIds.value.filter((id) => id !== fid)
    uidToFileId.delete(String(file.uid))
  }
}

/* 立即上传并记录 fileId；失败时从列表移除，允许重新选择 */
async function customUpload(options: UploadRequestOptions): Promise<void> {
  try {
    const resp = await uploadEvidence(options.file)
    fileIds.value.push(resp.fileId)
    uidToFileId.set(String(options.file.uid), resp.fileId)
    options.onSuccess(resp)
  } catch {
    /* 标记失败并从列表移除，允许重新选择上传 */
    options.onError(new Error('凭证上传失败') as never)
    fileList.value = fileList.value.filter((f) => f.uid !== options.file.uid)
  }
}

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  if (!form.violationId) {
    ElMessage.warning('请选择要申诉的处罚')
    return
  }
  const reason = form.reason.trim()
  if (reason.length < 10 || reason.length > 1000) {
    ElMessage.warning('申诉理由需为 10—1000 字')
    return
  }
  submitting.value = true
  try {
    await submitAppeal({ violationId: form.violationId, reason, fileIds: fileIds.value })
    ElMessage.success('申诉已提交，请等待审核')
    form.violationId = ''
    form.reason = ''
    fileIds.value = []
    fileList.value = []
    uidToFileId.clear()
    page.value = 1
    await Promise.all([loadList(), loadViolations()])
  } catch {
    /* 失败原因已由 http 拦截器提示 */
  } finally {
    submitting.value = false
  }
}

/* ---------- 列表 ---------- */
const statusFilter = ref<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL')
const page = ref(1)
const size = 10
const items = ref<AppealItem[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')

async function loadList(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const resp = await fetchMyAppeals(statusFilter.value === 'ALL' ? undefined : statusFilter.value, page.value, size)
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

function onFilterChange(): void {
  page.value = 1
  void loadList()
}

function onPageChange(next: number): void {
  page.value = next
  void loadList()
}

/* ---------- 详情（凭证经认证通道取 Blob，用完即 revoke） ---------- */const detailDialog = ref(false)
const detail = ref<AppealItem | null>(null)
const evidenceUrls = ref<string[]>([])
const evidenceLoading = ref(false)

async function openDetail(row: AppealItem): Promise<void> {
  detail.value = row
  detailDialog.value = true
  evidenceUrls.value = []
  if (!row.fileIds.length) return
  evidenceLoading.value = true
  for (const fid of row.fileIds) {
    try {
      const blob = await fetchEvidenceBlob(fid)
      evidenceUrls.value.push(URL.createObjectURL(blob))
    } catch {
      /* 单张失败不影响其余凭证展示 */
    }
  }
  evidenceLoading.value = false
}

function closeDetail(): void {
  detailDialog.value = false
  evidenceUrls.value.forEach((url) => URL.revokeObjectURL(url))
  evidenceUrls.value = []
  detail.value = null
}

function onRowClick(row: AppealItem): void {
  void fetchAppeal(row.id)
    .then((full) => openDetail(full))
    .catch(() => openDetail(row))
}

/* ---------- 初始化 ---------- */
async function loadViolations(): Promise<void> {
  violationsLoading.value = true
  try {
    const resp = await fetchMyViolations(1, 20)
    violations.value = resp.items || []
  } catch {
    violations.value = []
  } finally {
    violationsLoading.value = false
  }
}

onMounted(() => {
  timer = window.setInterval(() => {
    tick.value = Date.now()
  }, 30000)
  const preselect = route.query.violationId
  void Promise.all([loadViolations(), loadList()]).then(() => {
    if (typeof preselect === 'string' && selectableViolations.value.some((v) => v.id === preselect)) {
      form.violationId = preselect
    }
  })
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  evidenceUrls.value.forEach((url) => URL.revokeObjectURL(url))
  evidenceUrls.value = []
})
</script>

<style scoped>
.card-title {
  font-size: 15px;
  margin: 0 0 12px;
}
.appeal-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-label {
  font-size: 12px;
  color: var(--text-sub);
}
.option-main {
  display: block;
  font-size: 13px;
}
.option-sub {
  display: block;
  font-size: 12px;
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
.reason-text {
  white-space: pre-wrap;
  word-break: break-word;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.evidence-block {
  margin-top: 12px;
}
.evidence-title {
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 8px;
}
.evidence-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.evidence-img {
  max-width: 160px;
  max-height: 160px;
  border: 1px solid var(--border);
  border-radius: 8px;
  object-fit: cover;
}
.evidence-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-sub);
}
.error-card {
  text-align: center;
}
.error-text {
  color: var(--color-danger);
}
:deep(.clickable-row) {
  cursor: pointer;
}
</style>

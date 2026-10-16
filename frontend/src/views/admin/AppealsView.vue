<template>
  <div class="page">
    <div class="toolbar card">
      <div class="toolbar-left">
        <el-input
          v-model="filters.account"
          placeholder="学生账号"
          clearable
          style="width: 160px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="onSearch">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
      </div>
    </div>

    <div class="card table-wrap">
      <el-table :data="appeals" v-loading="loading" border empty-text="暂无数据">
        <el-table-column prop="id" label="申诉编号" min-width="100" show-overflow-tooltip />
        <el-table-column prop="account" label="学生账号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="userName" label="姓名" min-width="90" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="申诉理由" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason }}</template>
        </el-table-column>
        <el-table-column label="凭证" width="80" align="center">
          <template #default="{ row }">{{ row.fileIds?.length || 0 }} 张</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="success"
              size="small"
              :disabled="reviewedIds.has(row.id)"
              @click="openReview(row, 'APPROVED')"
            >审核</el-button>
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
          @size-change="loadAppeals"
          @current-change="loadAppeals"
        />
      </div>
    </div>

    <!-- 申诉详情（只读） -->
    <el-dialog v-model="detailVisible" title="申诉详情" width="640px" @closed="closeDetail">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="申诉编号">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="学生">
              {{ detail.account }}（{{ detail.userName }}）
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatDateTime(detail.submittedAt) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <StatusTag :status="detail.status" />
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">关联处罚</div>
          <el-descriptions :column="2" border size="small">
            <template v-if="detail.violationSummary">
              <el-descriptions-item label="处罚原因">{{ violationReasonText(detail.violationSummary.reason) }}</el-descriptions-item>
              <el-descriptions-item label="扣除信用分">{{ detail.violationSummary.deducted }}</el-descriptions-item>
              <el-descriptions-item label="订单号">{{ detail.violationSummary.orderNo }}</el-descriptions-item>
              <el-descriptions-item label="座位">{{ detail.violationSummary.roomName }} {{ detail.violationSummary.seatCode }}</el-descriptions-item>
              <el-descriptions-item label="处罚时间">{{ formatDateTime(detail.violationSummary.eventAt) }}</el-descriptions-item>
            </template>
            <template v-else>
              <el-descriptions-item label="处罚编号">{{ detail.violationId }}</el-descriptions-item>
              <el-descriptions-item label="说明">处罚详情未随列表返回</el-descriptions-item>
            </template>
          </el-descriptions>

          <div class="section-title">申诉理由</div>
          <div class="reason-box">{{ detail.reason }}</div>

          <div class="section-title">凭证（{{ evidenceUrls.length }} / {{ detail.fileIds?.length || 0 }}）</div>
          <div v-if="evidenceUrls.length > 0" class="evidence-list">
            <el-image
              v-for="item in evidenceUrls"
              :key="item.fileId"
              :src="item.url"
              :preview-src-list="evidenceUrls.map((e) => e.url)"
              fit="cover"
              class="evidence-thumb"
              hide-on-click-modal
            />
          </div>
          <el-empty v-else description="暂无凭证" :image-size="50" />
          <div class="text-sub mt-8">凭证为私有文件，经鉴权下载后仅在当前会话展示。</div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 审核对话框 -->
    <el-dialog v-model="reviewVisible" title="申诉审核" width="640px" @closed="closeReview">
      <el-alert
        v-if="reviewError"
        type="error"
        :closable="false"
        show-icon
        :title="reviewError"
        class="mb-16"
      />
      <template v-if="reviewRow">
        <el-descriptions :column="2" border size="small" class="mb-16">
          <el-descriptions-item label="学生">
            {{ reviewRow.account }}（{{ reviewRow.userName }}）
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(reviewRow.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="申诉理由" :span="2">{{ reviewRow.reason }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-width="96px">
          <el-form-item label="审核决定" required>
            <el-radio-group v-model="reviewDecision">
              <el-radio value="APPROVED">通过（撤销处罚并返还实际扣分）</el-radio>
              <el-radio value="REJECTED">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核意见">
            <el-input
              v-model="reviewComment"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              :placeholder="reviewDecision === 'REJECTED' ? '驳回理由（选填），将展示给学生' : '选填，将展示给学生'"
            />
          </el-form-item>
        </el-form>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          description="通过后处罚标记为已撤销并生成返还流水，订单保持违约状态；每笔处罚仅可审核一次，重复提交不会二次返分。"
        />
      </template>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { fetchAdminAppeals, reviewAppeal } from '@/api/admin'
import { fetchEvidenceBlob } from '@/api/appeals'
import { formatDateTime } from '@/utils/format'
import type { AdminAppealItem } from '@/types'

/* ---------- list ---------- */
const appeals = ref<AdminAppealItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ account: '', status: '' as string })

async function loadAppeals(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    if (filters.account.trim()) params.account = filters.account.trim()
    if (filters.status) params.status = filters.status
    const res = await fetchAdminAppeals(params)
    appeals.value = res.items || []
    total.value = res.total || 0
  } catch {
    appeals.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  void loadAppeals()
}

/* ---------- evidence blob urls (revoke on close/unmount) ---------- */
interface EvidenceUrl {
  fileId: string
  url: string
}

const evidenceUrls = ref<EvidenceUrl[]>([])

function revokeEvidence(): void {
  evidenceUrls.value.forEach((item) => URL.revokeObjectURL(item.url))
  evidenceUrls.value = []
}

async function loadEvidence(fileIds: string[]): Promise<void> {
  revokeEvidence()
  for (const fileId of fileIds || []) {
    try {
      const blob = await fetchEvidenceBlob(fileId)
      evidenceUrls.value.push({ fileId, url: URL.createObjectURL(blob) })
    } catch {
      // 单个凭证失败不影响其他凭证展示
    }
  }
}

onBeforeUnmount(() => {
  revokeEvidence()
})

/* ---------- detail ---------- */
interface AppealDetail extends AdminAppealItem {
  violationSummary: {
    reason: string
    eventAt: string
    deducted: number
    orderNo: string
    seatCode: string
    roomName: string
  } | null
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AppealDetail | null>(null)

function violationReasonText(reason: string): string {
  return reason === 'NO_SHOW' ? '未签到（爽约）' : reason === 'LEAVE_TIMEOUT' ? '暂离超时' : reason
}

async function openDetail(row: AdminAppealItem): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const raw = (row as unknown as Record<string, unknown>).violationSummary
    detail.value = {
      ...row,
      violationSummary: (raw as AppealDetail['violationSummary']) ?? null
    }
    await loadEvidence(row.fileIds || [])
  } finally {
    detailLoading.value = false
  }
}

function closeDetail(): void {
  detail.value = null
  revokeEvidence()
}

/* ---------- review ---------- */
const reviewVisible = ref(false)
const reviewRow = ref<AdminAppealItem | null>(null)
const reviewDecision = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const reviewComment = ref('')
const reviewError = ref('')
const submitting = ref(false)
/* 已提交审核的行（提交后到刷新前禁用按钮，防重复提交） */
const reviewedIds = ref<Set<string>>(new Set())

function rowVersion(row: AdminAppealItem): string {
  const raw = (row as unknown as Record<string, unknown>).version
  return raw === undefined || raw === null ? '' : String(raw)
}

function openReview(row: AdminAppealItem, decision: 'APPROVED' | 'REJECTED'): void {
  reviewRow.value = row
  reviewDecision.value = decision
  reviewComment.value = ''
  reviewError.value = ''
  reviewVisible.value = true
}

function closeReview(): void {
  reviewRow.value = null
  reviewError.value = ''
}

async function submitReview(): Promise<void> {
  if (!reviewRow.value) return
  const comment = reviewComment.value.trim()
  if (comment.length > 1000) {
    reviewError.value = '审核意见不能超过 1000 字'
    return
  }
  const decisionText = reviewDecision.value === 'APPROVED' ? '通过' : '驳回'
  const confirmed = await ElMessageBox.confirm(
    `确认${decisionText}该申诉？审核结果记入审计日志，每笔处罚仅可审核一次。`,
    '二次确认',
    { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  submitting.value = true
  reviewError.value = ''
  const appealId = reviewRow.value.id
  try {
    const payload: Record<string, unknown> = {
      decision: reviewDecision.value,
      comment
    }
    const version = rowVersion(reviewRow.value)
    if (version) payload.expectedVersion = version
    await reviewAppeal(appealId, payload)
    reviewedIds.value = new Set([...reviewedIds.value, appealId])
    reviewVisible.value = false
    await loadAppeals()
  } catch {
    // http 层已提示；409/重复审核时保持列表可重试
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadAppeals()
})
</script>

<style scoped>
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
.section-title {
  font-weight: 600;
  margin: 16px 0 8px;
}
.reason-box {
  padding: 10px 12px;
  background: var(--bg-page);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.evidence-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.evidence-thumb {
  width: 120px;
  height: 120px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
</style>

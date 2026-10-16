<template>
  <div class="page">
    <div class="toolbar card">
      <div class="toolbar-left">
        <el-input
          v-model="filters.title"
          placeholder="标题"
          clearable
          style="width: 200px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="onSearch">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已撤回" value="WITHDRAWN" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreateDialog">新建公告</el-button>
    </div>

    <div class="card table-wrap">
      <el-table :data="notices" v-loading="loading" border empty-text="暂无数据">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="置顶" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.pinned" type="warning" size="small">置顶</el-tag>
            <span v-else class="text-sub">—</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="150">
          <template #default="{ row }">
            {{ row.publishedAt ? formatDateTime(row.publishedAt) : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openPreview(row)">预览</el-button>
            <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 'PUBLISHED'"
              link
              type="success"
              size="small"
              @click="onPublishNotice(row)"
            >发布</el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              link
              type="danger"
              size="small"
              @click="openWithdrawDialog(row)"
            >撤回</el-button>
            <el-button
              v-if="row.pinned"
              link
              type="info"
              size="small"
              @click="togglePin(row, false)"
            >取消置顶</el-button>
            <el-button
              v-else
              link
              type="warning"
              size="small"
              @click="togglePin(row, true)"
            >置顶</el-button>
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
          @size-change="loadNotices"
          @current-change="loadNotices"
        />
      </div>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑公告' : '新建公告'"
      width="640px"
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
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅支持纯文本"
        description="公告内容不支持 HTML 或富文本，将按纯文本原样展示；标题不超过 100 字，正文不超过 5000 字。"
        class="mb-16"
      />
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="72px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="公告标题" />
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            maxlength="5000"
            show-word-limit
            placeholder="公告正文（纯文本）"
          />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.pinned" />
          <span class="text-sub ml-8">置顶后展示在公告列表最前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 纯文本预览 -->
    <el-dialog v-model="previewVisible" title="公告预览（纯文本）" width="640px">
      <template v-if="previewRow">
        <div class="preview-title">
          {{ previewRow.title }}
          <el-tag v-if="previewRow.pinned" type="warning" size="small" class="ml-8">置顶</el-tag>
        </div>
        <div class="preview-content">{{ previewRow.content }}</div>
      </template>
      <template #footer>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 撤回 -->
    <el-dialog v-model="withdrawVisible" title="撤回公告" width="480px" @closed="resetWithdrawDialog">
      <el-alert
        v-if="withdrawError"
        type="error"
        :closable="false"
        show-icon
        :title="withdrawError"
        class="mb-16"
      />
      <p class="dialog-line">
        撤回后公告对学生不可见，已发布记录保留以备审计。标题：{{ withdrawRow?.title }}
      </p>
      <el-form label-width="72px">
        <el-form-item label="撤回原因">
          <el-input
            v-model="withdrawReason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="撤回原因（选填），将记入审计日志"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmWithdraw">确认撤回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import {
  fetchAdminNotices,
  createNotice,
  updateNotice,
  publishNotice,
  withdrawNotice
} from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import type { AdminNoticeItem } from '@/types'

/* ---------- list ---------- */
const notices = ref<AdminNoticeItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ title: '', status: '' as string })

async function loadNotices(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    if (filters.title.trim()) params.title = filters.title.trim()
    if (filters.status) params.status = filters.status
    const res = await fetchAdminNotices(params)
    notices.value = res.items || []
    total.value = res.total || 0
  } catch {
    notices.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  void loadNotices()
}

/* ---------- create / edit ---------- */
const formVisible = ref(false)
const editingId = ref('')
const editingVersion = ref('')
const formError = ref('')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ title: '', content: '', pinned: false })

const formRules: FormRules<typeof form> = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入公告正文', trigger: 'blur' },
    { max: 5000, message: '正文不能超过 5000 字', trigger: 'blur' }
  ]
}

function resetFormDialog(): void {
  formRef.value?.clearValidate()
  formError.value = ''
  editingId.value = ''
  editingVersion.value = ''
  form.title = ''
  form.content = ''
  form.pinned = false
}

function openCreateDialog(): void {
  resetFormDialog()
  formVisible.value = true
}

function openEditDialog(row: AdminNoticeItem): void {
  resetFormDialog()
  editingId.value = row.id
  editingVersion.value = row.version
  form.title = row.title
  form.content = row.content
  form.pinned = row.pinned
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  formError.value = ''
  try {
    if (editingId.value) {
      const payload: Record<string, unknown> = {
        title: form.title.trim(),
        content: form.content,
        pinned: form.pinned,
        expectedVersion: editingVersion.value
      }
      await updateNotice(editingId.value, payload)
    } else {
      await createNotice({
        title: form.title.trim(),
        content: form.content,
        pinned: form.pinned
      })
    }
    formVisible.value = false
    await loadNotices()
  } catch {
    // http 层已提示；409 时提示重新加载
  } finally {
    submitting.value = false
  }
}

/* ---------- publish / pin / withdraw ---------- */
async function onPublishNotice(row: AdminNoticeItem): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `确认发布「${row.title}」？发布后立即对学生可见。`,
    '二次确认',
    { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  submitting.value = true
  try {
    await publishNotice(row.id, row.version)
    await loadNotices()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

async function togglePin(row: AdminNoticeItem, pinned: boolean): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    pinned ? `确认置顶「${row.title}」？` : `确认取消置顶「${row.title}」？`,
    '二次确认',
    { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return
  submitting.value = true
  try {
    await updateNotice(row.id, { pinned, expectedVersion: row.version })
    await loadNotices()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

const withdrawVisible = ref(false)
const withdrawRow = ref<AdminNoticeItem | null>(null)
const withdrawReason = ref('')
const withdrawError = ref('')

function resetWithdrawDialog(): void {
  withdrawRow.value = null
  withdrawReason.value = ''
  withdrawError.value = ''
}

function openWithdrawDialog(row: AdminNoticeItem): void {
  withdrawRow.value = row
  withdrawReason.value = ''
  withdrawError.value = ''
  withdrawVisible.value = true
}

async function confirmWithdraw(): Promise<void> {
  if (!withdrawRow.value) return
  submitting.value = true
  withdrawError.value = ''
  try {
    await withdrawNotice(withdrawRow.value.id, {
      expectedVersion: withdrawRow.value.version,
      reason: withdrawReason.value.trim()
    })
    withdrawVisible.value = false
    await loadNotices()
  } catch {
    // http 层已提示
  } finally {
    submitting.value = false
  }
}

/* ---------- preview (plain text only) ---------- */
const previewVisible = ref(false)
const previewRow = ref<AdminNoticeItem | null>(null)

function openPreview(row: AdminNoticeItem): void {
  previewRow.value = row
  previewVisible.value = true
}

onMounted(() => {
  void loadNotices()
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
.ml-8 {
  margin-left: 8px;
}
.dialog-line {
  margin: 0 0 8px;
  font-size: 14px;
}
.preview-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 12px;
}
.preview-content {
  padding: 12px;
  background: var(--bg-page);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

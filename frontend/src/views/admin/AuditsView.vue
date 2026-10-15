<template>
  <div class="page">
    <div class="toolbar card">
      <div class="toolbar-left">
        <el-input
          v-model="filters.actorId"
          placeholder="操作人 ID"
          clearable
          style="width: 200px"
          @keyup.enter="onSearch"
        />
        <el-input
          v-model="filters.action"
          placeholder="操作类型，如 RULE_PUBLISHED"
          clearable
          style="width: 230px"
          @keyup.enter="onSearch"
        />
        <el-select
          v-model="filters.targetType"
          placeholder="全部对象类型"
          clearable
          style="width: 150px"
        >
          <el-option label="房间" value="ROOM" />
          <el-option label="座位" value="SEAT" />
          <el-option label="用户" value="USER" />
          <el-option label="规则" value="RULE" />
          <el-option label="申诉" value="APPEAL" />
          <el-option label="公告" value="NOTICE" />
          <el-option label="预约" value="RESERVATION" />
          <el-option label="信用" value="CREDIT" />
        </el-select>
        <el-date-picker
          v-model="filters.dateFrom"
          type="date"
          placeholder="开始日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
        />
        <span class="text-sub">至</span>
        <el-date-picker
          v-model="filters.dateTo"
          type="date"
          placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
        />
        <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
      </div>
    </div>

    <el-alert
      v-if="rangeError"
      type="warning"
      :closable="false"
      show-icon
      :title="rangeError"
      class="mb-16"
    />

    <div class="card table-wrap">
      <el-table :data="audits" v-loading="loading" border empty-text="暂无数据">
        <el-table-column prop="id" label="编号" width="90" align="center" />
        <el-table-column prop="actorId" label="操作人 ID" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.actorId || '系统' }}</template>
        </el-table-column>
        <el-table-column prop="action" label="操作类型" min-width="180" show-overflow-tooltip />
        <el-table-column prop="targetType" label="对象类型" min-width="100" align="center" />
        <el-table-column prop="targetId" label="对象 ID" min-width="110" show-overflow-tooltip />
        <el-table-column prop="traceId" label="链路追踪" min-width="200" show-overflow-tooltip />
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadAudits"
          @current-change="loadAudits"
        />
      </div>
      <div class="text-sub mt-8">查询区间上限 31 天；审计日志只读，仅管理员可查看。</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { fetchAudits } from '@/api/admin'
import { todayBusinessDate, addDays, formatDateTime } from '@/utils/format'
import type { AuditLogItem } from '@/types'

const MAX_RANGE_DAYS = 31

const audits = ref<AuditLogItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rangeError = ref('')

const filters = reactive({
  actorId: '',
  action: '',
  targetType: '',
  dateFrom: addDays(todayBusinessDate(), -30),
  dateTo: todayBusinessDate()
})

function daysBetween(from: string, to: string): number {
  const start = Date.parse(`${from}T00:00:00+08:00`)
  const end = Date.parse(`${to}T00:00:00+08:00`)
  if (Number.isNaN(start) || Number.isNaN(end)) return Number.NaN
  return Math.round((end - start) / 86400000)
}

async function loadAudits(): Promise<void> {
  if (!filters.dateFrom || !filters.dateTo) {
    rangeError.value = '请选择完整的日期范围'
    return
  }
  const span = daysBetween(filters.dateFrom, filters.dateTo)
  if (!Number.isFinite(span) || span < 0) {
    rangeError.value = '开始日期不能晚于结束日期'
    return
  }
  if (span > MAX_RANGE_DAYS) {
    rangeError.value = `查询区间上限 ${MAX_RANGE_DAYS} 天，请缩小范围后重试`
    return
  }
  rangeError.value = ''
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: page.value,
      size: size.value,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo
    }
    if (filters.actorId.trim()) params.actorId = filters.actorId.trim()
    if (filters.action.trim()) params.action = filters.action.trim()
    if (filters.targetType) params.targetType = filters.targetType
    const res = await fetchAudits(params)
    audits.value = res.items || []
    total.value = res.total || 0
  } catch {
    audits.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  void loadAudits()
}

onMounted(() => {
  void loadAudits()
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
</style>

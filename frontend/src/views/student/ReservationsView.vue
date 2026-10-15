<template>
  <div class="page">
    <div class="card toolbar-card">
      <el-radio-group v-model="tab" size="default" @change="onFilterChange">
        <el-radio-button value="current">当前预约</el-radio-button>
        <el-radio-button value="history">历史预约</el-radio-button>
      </el-radio-group>
      <el-select v-if="tab === 'history'" v-model="statusFilter" style="width: 130px" placeholder="状态筛选" @change="onFilterChange">
        <el-option label="全部状态" value="ALL" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="已违约" value="VIOLATED" />
      </el-select>
    </div>

    <div v-if="loading" class="card">
      <el-skeleton :rows="3" animated />
    </div>
    <div v-else-if="loadError" class="card error-card">
      <p class="error-text">加载失败：{{ loadError }}</p>
      <el-button type="primary" @click="load">重试</el-button>
    </div>
    <div v-else-if="!items.length" class="card">
      <el-empty :description="tab === 'current' ? '当前没有进行中的预约' : '暂无历史预约记录'">
        <el-button v-if="tab === 'current'" type="primary" @click="router.push('/student/rooms')">去选座</el-button>
      </el-empty>
    </div>
    <div v-else class="card">
      <div class="table-wrap">
        <el-table :data="items" row-class-name="clickable-row" @row-click="onRowClick">
          <el-table-column prop="roomName" label="自习室" min-width="120">
            <template #default="{ row }">
              <span class="cell-main">{{ row.roomName }}</span>
              <span class="text-sub cell-sub">座位 {{ row.seatCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="170">
            <template #default="{ row }">
              <span class="cell-main">{{ formatDateTime(row.startAt) }}</span>
              <span class="text-sub cell-sub">至 {{ formatTime(row.endAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <StatusTag :status="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="终止原因" min-width="110">
            <template #default="{ row }">
              <span v-if="row.terminationReason" class="termination">{{ row.terminationReason }}</span>
              <span v-else class="text-sub">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
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
      <p v-if="maybeMore" class="text-sub more-hint">记录较多，仅展示最近部分，请按状态筛选查看更早记录。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMyReservations } from '@/api/me'
import { formatDateTime, formatTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import type { ReservationListItem, ReservationStatus } from '@/types'

const router = useRouter()

const tab = ref<'current' | 'history'>('current')
const statusFilter = ref<'ALL' | ReservationStatus>('ALL')
const page = ref(1)
const size = 10

const items = ref<ReservationListItem[]>([])
const total = ref(0)
const maybeMore = ref(false)
const loading = ref(false)
const loadError = ref('')

const ACTIVE_STATUSES: ReservationStatus[] = ['RESERVED', 'IN_USE', 'LEFT_TEMP']
const HISTORY_STATUSES: ReservationStatus[] = ['COMPLETED', 'CANCELLED', 'VIOLATED']

function statusesOf(): ReservationStatus[] {
  if (tab.value === 'current') return ACTIVE_STATUSES
  if (statusFilter.value === 'ALL') return HISTORY_STATUSES
  return [statusFilter.value]
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const statuses = statusesOf()
    if (statuses.length === 1) {
      /* 单状态：服务端分页 */
      maybeMore.value = false
      const resp = await fetchMyReservations({ status: statuses[0], page: page.value, size })
      items.value = resp.items || []
      total.value = resp.total || 0
    } else {
      /* 多状态合并：各取最近一页后本地排序与分页 */
      const results = await Promise.all(
        statuses.map((s) => fetchMyReservations({ status: s, page: 1, size: 20 }))
      )
      const merged = results
        .flatMap((r) => r.items || [])
        .sort((a, b) => Date.parse(b.startAt) - Date.parse(a.startAt))
      maybeMore.value = results.some((r) => r.total > (r.items?.length ?? 0))
      total.value = merged.length
      items.value = merged.slice((page.value - 1) * size, page.value * size)
    }
  } catch (err) {
    loadError.value = (err as Error)?.message || '请稍后重试'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onRowClick(row: ReservationListItem): void {
  void router.push(`/student/reservations/${row.id}`)
}

function onFilterChange(): void {
  page.value = 1
  void load()
}

function onPageChange(next: number): void {
  page.value = next
  void load()
}

load()
</script>

<style scoped>
.toolbar-card {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.cell-main {
  display: block;
  font-size: 13px;
}
.cell-sub {
  display: block;
}
.termination {
  font-size: 12px;
  color: var(--color-danger);
  word-break: break-all;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.more-hint {
  margin: 8px 0 0;
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

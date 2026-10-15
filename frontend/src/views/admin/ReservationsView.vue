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
        <el-select
          v-model="filters.roomId"
          placeholder="全部房间"
          clearable
          filterable
          style="width: 200px"
        >
          <el-option v-for="r in roomOptions" :key="r.id" :label="`${r.name}（${r.code}）`" :value="r.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option
            v-for="opt in STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
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

    <div class="card table-wrap">
      <el-table :data="reservations" v-loading="loading" border empty-text="暂无数据">
        <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="account" label="学生账号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="userName" label="姓名" min-width="90" show-overflow-tooltip />
        <el-table-column prop="roomName" label="房间" min-width="130" show-overflow-tooltip />
        <el-table-column prop="seatCode" label="座位" min-width="90" show-overflow-tooltip />
        <el-table-column label="开始时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.startAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.endAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
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
          @size-change="loadReservations"
          @current-change="loadReservations"
        />
      </div>
    </div>

    <!-- 只读详情 -->
    <el-dialog v-model="detailVisible" title="预约详情（只读）" width="560px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border size="small">
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="学生">
            {{ detailAccount ? `${detailAccount}${detailName ? `（${detailName}）` : ''}` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="房间">{{ detail.roomName }}</el-descriptions-item>
          <el-descriptions-item label="座位">{{ detail.seatCode }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(detail.startAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatDateTime(detail.endAt) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :status="detail.status" />
          </el-descriptions-item>
          <el-descriptions-item label="实际签到时间">
            {{ detail.checkInAt ? formatDateTime(detail.checkInAt) : '未签到' }}
          </el-descriptions-item>
          <el-descriptions-item label="签到截止">{{ formatDateTime(detail.checkinDueAt) }}</el-descriptions-item>
          <el-descriptions-item label="可取消截止">{{ formatDateTime(detail.cancelDueAt) }}</el-descriptions-item>
          <el-descriptions-item label="暂离截止">
            {{ detail.leaveDueAt ? formatDateTime(detail.leaveDueAt) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="暂离次数">{{ detail.leaveCount }}</el-descriptions-item>
          <el-descriptions-item label="结束时间（终态）">
            {{ detail.finishedAt ? formatDateTime(detail.finishedAt) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="终止原因">
            {{ detail.terminationReason || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="规则快照摘要">
            {{ detail.ruleSnapshotSummary || '—' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!detailLoading" description="详情加载失败，请重试" :image-size="60" />
      </div>
      <template #footer>
        <span class="text-sub">管理员只读查看，不提供代签等操作</span>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import StatusTag from '@/components/StatusTag.vue'
import { fetchAdminReservations, fetchAdminRooms } from '@/api/admin'
import { fetchReservation } from '@/api/reservations'
import { todayBusinessDate, formatDateTime } from '@/utils/format'
import type {
  AdminReservationListItem,
  ReservationDetail,
  RoomListItem,
  ReservationStatus
} from '@/types'

const STATUS_OPTIONS: { label: string; value: ReservationStatus }[] = [
  { label: '待签到', value: 'RESERVED' },
  { label: '使用中', value: 'IN_USE' },
  { label: '暂离中', value: 'LEFT_TEMP' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已违约', value: 'VIOLATED' }
]

const reservations = ref<AdminReservationListItem[]>([])
const roomOptions = ref<RoomListItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const filters = reactive({
  account: '',
  roomId: '',
  status: '' as ReservationStatus | '',
  dateFrom: todayBusinessDate(),
  dateTo: todayBusinessDate()
})

async function loadRoomOptions(): Promise<void> {
  try {
    const res = await fetchAdminRooms({ page: 1, size: 100 })
    roomOptions.value = res.items || []
  } catch {
    roomOptions.value = []
  }
}

async function loadReservations(): Promise<void> {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: page.value,
      size: size.value,
      dateFrom: filters.dateFrom,
      dateTo: filters.dateTo
    }
    if (filters.account.trim()) params.account = filters.account.trim()
    if (filters.roomId) params.roomId = filters.roomId
    if (filters.status) params.status = filters.status
    const res = await fetchAdminReservations(params)
    reservations.value = res.items || []
    total.value = res.total || 0
  } catch {
    reservations.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  page.value = 1
  void loadReservations()
}

/* ---------- detail ---------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ReservationDetail | null>(null)
const detailAccount = ref('')
const detailName = ref('')

async function openDetail(row: AdminReservationListItem): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  detailAccount.value = row.account
  detailName.value = row.userName
  try {
    detail.value = await fetchReservation(row.id)
  } catch {
    // http 层已提示；保留列表行信息，详情字段区域显示加载失败
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  void loadRoomOptions()
  void loadReservations()
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

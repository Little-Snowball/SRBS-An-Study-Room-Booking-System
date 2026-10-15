<template>
  <span class="status-tag" :class="'status-' + normalized">
    <el-icon v-if="icon" :size="12"><component :is="icon" /></el-icon>
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ReservationStatus, PeriodStatus, SeatCurrentStatus } from '@/types'

const props = defineProps<{
  status: ReservationStatus | PeriodStatus | SeatCurrentStatus | string
}>()

const LABELS: Record<string, string> = {
  RESERVED: '待签到',
  IN_USE: '使用中',
  LEFT_TEMP: '暂离中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  VIOLATED: '已违约',
  FREE: '空闲',
  OCCUPIED: '占用中',
  UNAVAILABLE: '不可用',
  MAINTENANCE: '维修中',
  CLOSED: '闭馆',
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  ACTIVE: '生效中',
  REVOKED: '已撤销',
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  WITHDRAWN: '已撤回'
}

const label = computed(() => LABELS[props.status] || props.status)
const normalized = computed(() => {
  const map: Record<string, string> = {
    RESERVED: 'reserved',
    IN_USE: 'in-use',
    LEFT_TEMP: 'left-temp',
    COMPLETED: 'completed',
    CANCELLED: 'cancelled',
    VIOLATED: 'violated',
    FREE: 'free',
    OCCUPIED: 'in-use',
    UNAVAILABLE: 'maintenance',
    MAINTENANCE: 'maintenance',
    CLOSED: 'closed'
  }
  return map[props.status] || 'cancelled'
})
const icon = computed(() => (props.status === 'LEFT_TEMP' ? 'Coffee' : ''))
</script>

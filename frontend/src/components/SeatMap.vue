<template>
  <div class="seat-map">
    <div class="legend">
      <span><i class="dot dot--free" />可约</span>
      <span><i class="dot dot--reserved" />已约</span>
      <span><i class="dot dot--in-use" />使用中</span>
      <span><i class="dot dot--left" />暂离</span>
      <span><i class="dot dot--maintenance" />停用</span>
    </div>
    <div class="grid" :style="{ gridTemplateColumns: `repeat(${cols}, auto)` }">
      <template v-for="seat in orderedSeats" :key="seat.seatId">
        <div
          class="seat"
          :class="seatClass(seat)"
          role="button"
          :aria-disabled="!seat.available || !selectable"
          :title="seatTitle(seat)"
          @click="onSeatClick(seat)"
        >
          <span class="seat-code">{{ seat.code }}</span>
          <span class="seat-state">{{ stateText(seat) }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AvailabilitySeat, SeatCurrentStatus } from '@/types'

const props = defineProps<{
  seats: AvailabilitySeat[]
  rows: number
  cols: number
  selectable?: boolean
  selectedSeatId?: string | null
}>()

const emit = defineEmits<{
  (e: 'select', seat: AvailabilitySeat): void
}>()

const orderedSeats = computed(() =>
  [...props.seats].sort((a, b) => a.rowNo - b.rowNo || a.colNo - b.colNo)
)

function currentState(seat: AvailabilitySeat): SeatCurrentStatus {
  return seat.currentStatus
}

function seatClass(seat: AvailabilitySeat): string {
  if (currentState(seat) === 'MAINTENANCE' || currentState(seat) === 'CLOSED') return 'seat--maintenance'
  if (props.selectedSeatId === seat.seatId) return 'seat--selected'
  if (currentState(seat) === 'IN_USE') return 'seat--in-use'
  if (currentState(seat) === 'LEFT_TEMP') return 'seat--left'
  if (currentState(seat) === 'RESERVED') return 'seat--reserved'
  return 'seat--free'
}

function stateText(seat: AvailabilitySeat): string {
  switch (currentState(seat)) {
    case 'IN_USE':
      return '使用中'
    case 'LEFT_TEMP':
      return '暂离'
    case 'RESERVED':
      return '已约'
    case 'MAINTENANCE':
      return '停用'
    case 'CLOSED':
      return '闭馆'
    default:
      return props.selectable ? '可约' : '空闲'
  }
}

function seatTitle(seat: AvailabilitySeat): string {
  const tags = [
    seat.hasPower ? '有插座' : '',
    seat.nearWindow ? '靠窗' : '',
    seat.quietZone ? '静音区' : ''
  ].filter(Boolean)
  return `${seat.code} ${tags.join(' · ')}`.trim()
}

function onSeatClick(seat: AvailabilitySeat) {
  if (!props.selectable || !seat.available) return
  emit('select', seat)
}
</script>

<style scoped>
.legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 12px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 4px;
}
.dot--free { background: var(--seat-free); }
.dot--reserved { background: var(--seat-reserved); }
.dot--in-use { background: var(--seat-in-use); }
.dot--maintenance { background: var(--seat-maintenance); }
.grid {
  display: grid;
  gap: 8px;
  justify-content: start;
}
.seat--left { background: #f97316; color: #fff; cursor: not-allowed; }
.seat-code { font-weight: 600; }
.seat-state { font-size: 10px; opacity: 0.9; }
</style>

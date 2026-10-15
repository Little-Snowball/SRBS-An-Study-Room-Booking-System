<template>
  <div class="page">
    <div class="card toolbar-card">
      <el-radio-group v-model="readFilter" @change="onFilterChange">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="UNREAD">未读</el-radio-button>
        <el-radio-button value="READ">已读</el-radio-button>
      </el-radio-group>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div v-if="loading" class="card">
      <el-skeleton :rows="3" animated />
    </div>
    <div v-else-if="loadError" class="card error-card">
      <p class="error-text">加载失败：{{ loadError }}</p>
      <el-button type="primary" @click="load">重试</el-button>
    </div>
    <div v-else-if="!items.length" class="card">
      <el-empty :description="readFilter === 'UNREAD' ? '暂无未读通知' : '暂无通知'" :image-size="60" />
    </div>
    <div v-else class="card">
      <div class="notice-list">
        <div
          v-for="item in items"
          :key="item.id"
          class="notice-item"
          :class="{ 'notice-item--unread': !item.read }"
          role="button"
          @click="onItemClick(item)"
        >
          <div class="notice-head">
            <span v-if="!item.read" class="unread-dot" aria-label="未读" />
            <span class="notice-title">{{ item.title }}</span>
            <span class="text-sub notice-time">{{ formatDateTime(item.publishedAt) }}</span>
          </div>
          <p class="notice-content">{{ item.content }}</p>
          <div class="notice-foot">
            <el-button
              v-if="!item.read"
              size="small"
              :loading="marking === item.id"
              @click.stop="markRead(item)"
            >
              标为已读
            </el-button>
            <span v-if="item.reservationId" class="text-sub">关联订单 {{ item.reservationId }}</span>
          </div>
        </div>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMyNotifications, markNotificationRead } from '@/api/me'
import { formatDateTime } from '@/utils/format'
import type { NotificationItem } from '@/types'

const router = useRouter()

const readFilter = ref<'ALL' | 'UNREAD' | 'READ'>('ALL')
const page = ref(1)
const size = 10
const items = ref<NotificationItem[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const marking = ref('')

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const unread = readFilter.value === 'ALL' ? undefined : readFilter.value === 'UNREAD'
    const resp = await fetchMyNotifications(unread, page.value, size)
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

/* 标读是幂等操作：重复调用无副作用；本地同步更新，避免整页刷新 */
async function markRead(item: NotificationItem): Promise<void> {
  if (item.read || marking.value) return
  marking.value = item.id
  try {
    await markNotificationRead(item.id)
    item.read = true
    if (readFilter.value === 'UNREAD') {
      await load()
    }
  } catch {
    /* 失败原因已由 http 拦截器提示 */
  } finally {
    marking.value = ''
  }
}

/* 点击关联订单跳转详情；未读的顺手标读 */
function onItemClick(item: NotificationItem): void {
  if (!item.read) {
    void markRead(item)
  }
  if (item.reservationId) {
    void router.push(`/student/reservations/${item.reservationId}`)
  }
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
.notice-item {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  cursor: pointer;
}
.notice-item:hover {
  border-color: var(--color-primary);
}
.notice-item--unread {
  background: #f0f6ff;
}
.notice-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
  flex: none;
}
.notice-title {
  font-size: 14px;
  font-weight: 600;
}
.notice-time {
  margin-left: auto;
}
.notice-content {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.notice-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.error-card {
  text-align: center;
}
.error-text {
  color: var(--color-danger);
}
</style>

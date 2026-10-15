<template>
  <div class="page">
    <div v-if="loading" class="card">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="loadError" class="card error-card">
      <p class="error-text">加载失败：{{ loadError }}</p>
      <el-button type="primary" @click="loadAll">重试</el-button>
    </div>
    <template v-else>
      <div class="card profile-card">
        <div class="profile-main">
          <div class="profile-name">{{ user?.name ?? '—' }}</div>
          <div class="text-sub">学号：{{ user?.account ?? '—' }} · 学院：{{ user?.college || '未填写' }}</div>
        </div>
        <div class="credit-box" :class="restricted ? 'credit-box--warn' : 'credit-box--ok'">
          <div class="credit-score">{{ user?.creditScore ?? '—' }}</div>
          <div class="credit-label">信用分 · {{ restricted ? '受限' : '正常' }}</div>
          <div class="text-sub">限制线 {{ minCredit ?? '—' }} 分</div>
        </div>
      </div>

      <div class="card">
        <div class="flex-between card-head">
          <h3 class="card-title">未读通知</h3>
          <router-link to="/student/notifications">查看全部</router-link>
        </div>
        <p v-if="unread > 0" class="unread-line">
          您有 <b class="unread-count">{{ unread }}</b> 条未读通知
        </p>
        <p v-else class="text-sub">暂无未读通知</p>
      </div>

      <div class="card">
        <div class="flex-between card-head">
          <h3 class="card-title">最近预约</h3>
          <router-link to="/student/reservations">全部预约</router-link>
        </div>
        <div v-if="recent.length" class="recent-list">
          <div
            v-for="item in recent"
            :key="item.id"
            class="recent-item"
            role="button"
            @click="router.push(`/student/reservations/${item.id}`)"
          >
            <div class="flex-between">
              <span class="recent-room">{{ item.roomName }} · {{ item.seatCode }}</span>
              <StatusTag :status="item.status" />
            </div>
            <div class="text-sub">{{ formatDateTime(item.startAt) }} — {{ formatTime(item.endAt) }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无预约记录">
          <el-button type="primary" @click="router.push('/student/rooms')">去选座</el-button>
        </el-empty>
      </div>

      <div class="card">
        <h3 class="card-title">公告</h3>
        <div v-if="notices.length" class="notice-list">
          <div v-for="notice in notices" :key="notice.id" class="notice-item">
            <div class="notice-head">
              <el-tag v-if="notice.pinned" type="warning" size="small">置顶</el-tag>
              <span class="notice-title">{{ notice.title }}</span>
              <span class="text-sub notice-time">{{ formatDateTime(notice.publishedAt) }}</span>
            </div>
            <p class="notice-content">{{ notice.content }}</p>
          </div>
        </div>
        <el-empty v-else description="暂无公告" :image-size="60" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useRuleStore } from '@/stores/rule'
import { fetchNotices } from '@/api/notices'
import { fetchMyNotifications, fetchMyReservations } from '@/api/me'
import { formatDateTime, formatTime } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import type { NoticeItem, ReservationListItem } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const ruleStore = useRuleStore()

const loading = ref(true)
const loadError = ref('')
const notices = ref<NoticeItem[]>([])
const recent = ref<ReservationListItem[]>([])
const unread = ref(0)
const minCredit = ref<number | null>(null)

const user = computed(() => auth.user)
/* 受限状态：以规则 minCredit 派生，并结合后端下发的 creditRestricted 标记 */
const restricted = computed(() => {
  if (!user.value) return false
  if (minCredit.value !== null && user.value.creditScore < minCredit.value) return true
  return user.value.creditRestricted
})

function sortNotices(items: NoticeItem[]): NoticeItem[] {
  return [...items].sort((a, b) => Number(b.pinned) - Number(a.pinned))
}

async function loadAll(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const [meResult, ruleResult, noticeResult, reservationResult, unreadResult] = await Promise.allSettled([
      auth.refreshMe(),
      ruleStore.load(),
      fetchNotices(1, 5),
      fetchMyReservations({ page: 1, size: 5 }),
      fetchMyNotifications(true, 1, 1)
    ])
    /* 个人信息失败视为关键错误，整体展示重试；其余数据失败时降级为空 */
    if (meResult.status === 'rejected') throw meResult.reason
    if (ruleResult.status === 'fulfilled' && ruleResult.value) {
      minCredit.value = ruleResult.value.minCredit
    }
    if (noticeResult.status === 'fulfilled') {
      notices.value = sortNotices(noticeResult.value.items || [])
    }
    if (reservationResult.status === 'fulfilled') {
      recent.value = (reservationResult.value.items || []).slice(0, 5)
    }
    if (unreadResult.status === 'fulfilled') {
      unread.value = unreadResult.value.total || 0
    }
  } catch (err) {
    loadError.value = (err as Error)?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.profile-card {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}
.profile-name {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 4px;
}
.credit-box {
  text-align: center;
  border-radius: 10px;
  padding: 12px 20px;
  min-width: 120px;
}
.credit-box--ok {
  background: #dcfce7;
  color: #166534;
}
.credit-box--warn {
  background: #fee2e2;
  color: #991b1b;
}
.credit-score {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
.credit-label {
  font-size: 13px;
  margin: 2px 0;
}
.card-head {
  margin-bottom: 8px;
}
.card-title {
  font-size: 15px;
  margin: 0;
}
.unread-line {
  margin: 0;
}
.unread-count {
  color: var(--color-danger);
}
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.recent-item {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
}
.recent-item:hover {
  border-color: var(--color-primary);
}
.recent-room {
  font-size: 14px;
  font-weight: 600;
}
.notice-item {
  border-bottom: 1px solid var(--border);
  padding: 10px 0;
}
.notice-item:last-child {
  border-bottom: none;
}
.notice-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.notice-title {
  font-size: 14px;
  font-weight: 600;
}
.notice-time {
  margin-left: auto;
}
.notice-content {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-main);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.error-card {
  text-align: center;
}
.error-text {
  color: var(--color-danger);
}
</style>

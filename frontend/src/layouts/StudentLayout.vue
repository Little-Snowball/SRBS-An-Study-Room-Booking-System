<template>
  <div class="student-layout">
    <header class="top-bar">
      <div class="brand">自习室预约</div>
      <nav class="nav">
        <router-link to="/student/home">首页</router-link>
        <router-link to="/student/rooms">选座</router-link>
        <router-link to="/student/reservations">预约</router-link>
        <router-link to="/student/credits">信用</router-link>
        <router-link to="/student/appeals">申诉</router-link>
        <router-link to="/student/notifications">
          通知
          <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
        </router-link>
      </nav>
      <el-dropdown trigger="click" @command="onCommand">
        <span class="user-entry">
          {{ auth.user?.name }}（信用 {{ auth.user?.creditScore }}）
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="me">个人信息</el-dropdown-item>
            <el-dropdown-item command="change-password">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { fetchMyNotifications } from '@/api/me'

const auth = useAuthStore()
const router = useRouter()
const unread = ref(0)
let timer: number | undefined

async function refreshUnread() {
  try {
    const resp = await fetchMyNotifications(true, 1, 1)
    unread.value = resp.total
  } catch {
    /* silent: badge is a hint only */
  }
}

function onCommand(command: string) {
  if (command === 'logout') {
    void auth.logout()
  }
  if (command === 'me') {
    ElMessage.info(`${auth.user?.name} · ${auth.user?.college || '未填写学院'}`)
  }
  if (command === 'change-password') {
    void router.push('/change-password')
  }
}

onMounted(() => {
  void refreshUnread()
  // 60s polling and after-tab-visible refresh per the interaction spec
  timer = window.setInterval(refreshUnread, 60000)
  document.addEventListener('visibilitychange', onVisible)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisible)
})

function onVisible() {
  if (document.visibilityState === 'visible') {
    void refreshUnread()
  }
}
</script>

<style scoped>
.top-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}
.brand { font-weight: 700; font-size: 16px; }
.nav { display: flex; gap: 4px; flex: 1; flex-wrap: wrap; }
.nav a {
  padding: 6px 10px;
  border-radius: 6px;
  color: var(--text-main);
  font-size: 14px;
}
.nav a.router-link-active { background: #eef4ff; color: var(--color-primary); }
.badge {
  display: inline-block;
  min-width: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-danger);
  color: #fff;
  font-size: 11px;
  text-align: center;
}
.user-entry { cursor: pointer; font-size: 14px; display: inline-flex; align-items: center; gap: 4px; }
.content { max-width: 960px; margin: 0 auto; padding: 16px; }
</style>

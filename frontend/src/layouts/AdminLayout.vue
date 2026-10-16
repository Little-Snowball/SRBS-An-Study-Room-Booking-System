<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="brand">管理后台</div>
      <el-menu :default-active="activeMenu" router class="side-menu">
        <el-menu-item index="/admin/dashboard">运营看板</el-menu-item>
        <el-menu-item index="/admin/rooms">自习室管理</el-menu-item>
        <el-menu-item index="/admin/reservations">预约查询</el-menu-item>
        <el-menu-item index="/admin/users">学生账号</el-menu-item>
        <el-menu-item index="/admin/rules">规则配置</el-menu-item>
        <el-menu-item index="/admin/appeals">申诉审核</el-menu-item>
        <el-menu-item index="/admin/notices">公告管理</el-menu-item>
        <el-menu-item index="/admin/audits">审计日志</el-menu-item>
      </el-menu>
    </aside>
    <div class="main">
      <header class="top-bar">
        <span class="page-title">{{ route.meta.title || '管理后台' }}</span>
        <el-dropdown trigger="click" @command="onCommand">
          <span class="user-entry">
            {{ auth.user?.name }}（管理员）
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
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
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeMenu = computed(() => route.path)

function onCommand(command: string) {
  if (command === 'logout') {
    void auth.logout()
  }
  if (command === 'change-password') {
    void router.push('/change-password')
  }
}
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; }
.sidebar {
  width: 200px;
  background: #fff;
  border-right: 1px solid var(--border);
  flex-shrink: 0;
}
.brand { padding: 14px 16px; font-weight: 700; border-bottom: 1px solid var(--border); }
.side-menu { border-right: none; }
.main { flex: 1; min-width: 0; }
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 10;
}
.page-title { font-weight: 600; }
.user-entry { cursor: pointer; font-size: 14px; display: inline-flex; align-items: center; gap: 4px; }
.content { padding: 20px; }
@media (max-width: 900px) {
  .admin-layout { flex-direction: column; }
  .sidebar { width: 100%; }
}
</style>

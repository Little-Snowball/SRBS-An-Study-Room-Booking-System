import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('@/views/ChangePasswordView.vue'),
    meta: { title: '修改密码' }
  },
  {
    path: '/student',
    component: () => import('@/layouts/StudentLayout.vue'),
    meta: { role: 'STUDENT' },
    children: [
      { path: '', redirect: '/student/home' },
      { path: 'home', name: 'student-home', component: () => import('@/views/student/HomeView.vue'), meta: { title: '个人概览' } },
      { path: 'rooms', name: 'student-rooms', component: () => import('@/views/student/RoomsView.vue'), meta: { title: '筛选自习室' } },
      { path: 'rooms/:id', name: 'student-room-detail', component: () => import('@/views/student/RoomDetailView.vue'), meta: { title: '选座' } },
      { path: 'reservations', name: 'student-reservations', component: () => import('@/views/student/ReservationsView.vue'), meta: { title: '我的预约' } },
      { path: 'reservations/:id', name: 'student-reservation-detail', component: () => import('@/views/student/ReservationDetailView.vue'), meta: { title: '预约详情' } },
      { path: 'credits', name: 'student-credits', component: () => import('@/views/student/CreditsView.vue'), meta: { title: '信用流水' } },
      { path: 'appeals', name: 'student-appeals', component: () => import('@/views/student/AppealsView.vue'), meta: { title: '违约申诉' } },
      { path: 'notifications', name: 'student-notifications', component: () => import('@/views/student/NotificationsView.vue'), meta: { title: '站内通知' } }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { role: 'ADMIN' },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue'), meta: { title: '运营看板' } },
      { path: 'rooms', name: 'admin-rooms', component: () => import('@/views/admin/RoomsView.vue'), meta: { title: '自习室管理' } },
      { path: 'rooms/:id', name: 'admin-room-detail', component: () => import('@/views/admin/RoomDetailView.vue'), meta: { title: '座位布局' } },
      { path: 'reservations', name: 'admin-reservations', component: () => import('@/views/admin/ReservationsView.vue'), meta: { title: '预约查询' } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UsersView.vue'), meta: { title: '学生账号管理' } },
      { path: 'rules', name: 'admin-rules', component: () => import('@/views/admin/RulesView.vue'), meta: { title: '规则配置' } },
      { path: 'appeals', name: 'admin-appeals', component: () => import('@/views/admin/AppealsView.vue'), meta: { title: '申诉审核' } },
      { path: 'notices', name: 'admin-notices', component: () => import('@/views/admin/NoticesView.vue'), meta: { title: '公告管理' } },
      { path: 'audits', name: 'admin-audits', component: () => import('@/views/admin/AuditsView.vue'), meta: { title: '审计日志' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (auth.mustChangePassword && to.name !== 'change-password') {
    return { name: 'change-password' }
  }
  const requiredRole = to.meta.role as string | undefined
  if (requiredRole === 'ADMIN' && !auth.isAdmin) {
    return { name: auth.isStudent ? 'student-home' : 'login' }
  }
  if (requiredRole === 'STUDENT' && !auth.isStudent) {
    return { name: auth.isAdmin ? 'admin-dashboard' : 'login' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 自习室预约系统` : '自习室预约系统'
})

export default router

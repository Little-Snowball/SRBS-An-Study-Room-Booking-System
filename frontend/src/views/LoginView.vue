<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">高校自习室预约与考勤管理系统</h1>
      <p class="login-subtitle">学生端登录</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="学号" prop="account">
          <el-input
            v-model="form.account"
            maxlength="32"
            placeholder="请输入学号"
            autocomplete="username"
            :disabled="submitting"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            maxlength="64"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            :disabled="submitting"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button
          class="login-submit"
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="submitting"
          @click="onSubmit"
        >
          登 录
        </el-button>
      </el-form>
      <p class="login-hint">学号或密码错误时系统会给出统一提示；如连续失败被限流，请稍后再试。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({ account: '', password: '' })

const rules: FormRules = {
  account: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/* Only allow same-site absolute paths to avoid open redirects. */
function safeRedirect(): string {
  const raw = route.query.redirect
  const target = typeof raw === 'string' ? raw : ''
  if (target.startsWith('/') && !target.startsWith('//')) return target
  return ''
}

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  const valid = await formRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await auth.login(form.account, form.password)
    if (auth.mustChangePassword) {
      await router.replace('/change-password')
      return
    }
    const redirect = safeRedirect()
    if (redirect) {
      await router.replace(redirect)
      return
    }
    await router.replace(auth.user?.role === 'ADMIN' ? '/admin/dashboard' : '/student/home')
  } catch {
    // 学号错误与密码错误已由后端统一提示（http 拦截器弹出），此处仅恢复交互状态
    form.password = ''
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: var(--bg-page);
}
.login-card {
  width: 100%;
  max-width: 400px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 24px;
}
.login-title {
  font-size: 18px;
  line-height: 1.4;
  margin: 0 0 4px;
  text-align: center;
}
.login-subtitle {
  text-align: center;
  color: var(--text-sub);
  font-size: 13px;
  margin: 0 0 16px;
}
.login-submit {
  width: 100%;
}
.login-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--text-sub);
  line-height: 1.6;
}
</style>

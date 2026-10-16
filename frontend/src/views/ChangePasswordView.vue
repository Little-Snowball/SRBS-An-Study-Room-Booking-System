<template>
  <div class="page change-page">
    <div class="card change-card">
      <h2 class="change-title">修改密码</h2>
      <p class="change-desc">
        为了账号安全，请先设置新密码。修改成功后，后端会撤销全部已登录会话，需使用新密码重新登录。
      </p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            maxlength="64"
            placeholder="请输入当前密码"
            autocomplete="current-password"
            show-password
            :disabled="submitting"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            maxlength="64"
            placeholder="请输入新密码（6—12 位）"
            autocomplete="new-password"
            show-password
            :disabled="submitting"
          />
          <p class="field-hint">新密码需为 6—12 位。</p>
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            maxlength="64"
            placeholder="请再次输入新密码"
            autocomplete="new-password"
            show-password
            :disabled="submitting"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button
          class="change-submit"
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="submitting"
          @click="onSubmit"
        >
          确认修改
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

/* 密码内容不打印、不进入错误提示，仅做长度提示；最终规则以后端为准 */
function validateNewPassword(value: string): string | null {
  if (!value) return '请输入新密码'
  if (value.length < 6 || value.length > 12) return '新密码长度需为 6—12 位'
  return null
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        const msg = validateNewPassword(value)
        callback(msg ? new Error(msg) : undefined)
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback: (error?: Error) => void) => {
        if (!value) {
          callback(new Error('请再次输入新密码'))
          return
        }
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  const valid = await formRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false)
  if (!valid) return
  const newMsg = validateNewPassword(form.newPassword)
  if (newMsg) {
    ElMessage.warning(newMsg)
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  submitting.value = true
  try {
    await auth.changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    await router.replace('/login')
  } catch {
    // 失败原因（如当前密码错误）已由 http 拦截器统一提示；此处仅恢复交互状态
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.change-page {
  display: flex;
  justify-content: center;
}
.change-card {
  width: 100%;
  max-width: 480px;
  margin-top: 48px;
}
.change-title {
  font-size: 18px;
  margin: 0 0 8px;
}
.change-desc {
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.6;
  margin: 0 0 16px;
}
.field-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-sub);
}
.change-submit {
  width: 100%;
}
</style>

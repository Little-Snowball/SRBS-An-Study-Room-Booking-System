<template>
  <div class="page">
    <!-- 当前生效规则（只读） -->
    <div class="card" v-loading="loading">
      <div class="card-header">
        <span class="card-title">当前生效规则</span>
        <el-button size="small" :loading="loading" @click="loadCurrent">刷新</el-button>
      </div>
      <template v-if="current">
        <div class="version-meta">
          <el-tag type="primary" size="small">版本 V{{ current.versionNo }}</el-tag>
          <span class="text-sub">来源：{{ current.source === 'SYSTEM_SEED' ? '系统初始化' : '管理员发布' }}</span>
          <span class="text-sub">发布时间：{{ formatDateTime(current.createdAt) }}</span>
        </div>
        <el-descriptions :column="3" border size="small" class="mt-16">
          <el-descriptions-item
            v-for="field in ALL_FIELDS"
            :key="field.key"
            :label="field.label"
          >
            {{ displayValue(field.key) }}
            <span v-if="FIXED_FIELDS.includes(field.key)" class="fixed-note">（固定策略，不可编辑）</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else-if="!loading" description="暂无生效规则" :image-size="60" />
    </div>

    <!-- 发布新版本 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">发布新规则版本</span>
        <span class="text-sub">新规则以完整版本发布，发布后立即生效，不支持定时生效；历史版本只读</span>
      </div>
      <el-alert
        v-if="publishConflict"
        type="warning"
        :closable="false"
        show-icon
        :title="publishConflict"
        class="mb-16"
      />
      <el-alert
        v-if="publishError"
        type="error"
        :closable="false"
        show-icon
        :title="publishError"
        class="mb-16"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="150px" class="rule-form">
        <el-divider content-position="left">预约时间</el-divider>
        <div class="form-grid">
          <el-form-item label="提前可约天数" prop="advanceDays">
            <el-input-number v-model="form.advanceDays" :min="0" :max="30" controls-position="right" />
            <span class="field-unit">天（0—30，0 表示仅当天）</span>
          </el-form-item>
          <el-form-item label="最短预约时长" prop="minMinutes">
            <el-input-number v-model="form.minMinutes" :min="30" :max="720" :step="30" controls-position="right" />
            <span class="field-unit">分钟（30 的倍数）</span>
          </el-form-item>
          <el-form-item label="最长预约时长" prop="maxMinutes">
            <el-input-number v-model="form.maxMinutes" :min="30" :max="720" :step="30" controls-position="right" />
            <span class="field-unit">分钟（30 的倍数，≥最短）</span>
          </el-form-item>
        </div>

        <el-divider content-position="left">签到与取消窗口</el-divider>
        <div class="form-grid">
          <el-form-item label="可提前签到" prop="checkinEarlyMinutes">
            <el-input-number v-model="form.checkinEarlyMinutes" :min="0" :max="60" controls-position="right" />
            <span class="field-unit">分钟（0—60）</span>
          </el-form-item>
          <el-form-item label="签到宽限（迟到）" prop="checkinLateMinutes">
            <el-input-number v-model="form.checkinLateMinutes" :min="0" :max="60" controls-position="right" />
            <span class="field-unit">分钟（0—60，且必须小于最短预约时长）</span>
          </el-form-item>
          <el-form-item label="可取消窗口" prop="cancelBeforeMinutes">
            <el-input-number v-model="form.cancelBeforeMinutes" :min="0" :max="60" controls-position="right" />
            <span class="field-unit">分钟（开始前 0—60 分钟可取消）</span>
          </el-form-item>
        </div>

        <el-divider content-position="left">暂离</el-divider>
        <div class="form-grid">
          <el-form-item label="暂离次数上限" prop="leaveMaxCount">
            <el-input-number v-model="form.leaveMaxCount" :min="0" :max="10" controls-position="right" />
            <span class="field-unit">次（0—10）</span>
          </el-form-item>
          <el-form-item label="单次暂离时长" prop="leaveMaxMinutes">
            <el-input-number v-model="form.leaveMaxMinutes" :min="1" :max="120" controls-position="right" />
            <span class="field-unit">分钟（1—120）</span>
          </el-form-item>
        </div>

        <el-divider content-position="left">违约扣分与信用门槛</el-divider>
        <div class="form-grid">
          <el-form-item label="爽约扣分" prop="noShowPenalty">
            <el-input-number v-model="form.noShowPenalty" :min="0" :max="100" controls-position="right" />
            <span class="field-unit">分（0—100）</span>
          </el-form-item>
          <el-form-item label="暂离超时扣分" prop="leaveTimeoutPenalty">
            <el-input-number v-model="form.leaveTimeoutPenalty" :min="0" :max="100" controls-position="right" />
            <span class="field-unit">分（0—100）</span>
          </el-form-item>
          <el-form-item label="预约信用门槛" prop="minCredit">
            <el-input-number v-model="form.minCredit" :min="0" :max="100" controls-position="right" />
            <span class="field-unit">分（低于该值禁止新增预约）</span>
          </el-form-item>
        </div>

        <el-divider content-position="left">学习奖励</el-divider>
        <div class="form-grid">
          <el-form-item label="奖励起点时长" prop="rewardStartMinutes">
            <el-input-number v-model="form.rewardStartMinutes" :min="1" :max="720" controls-position="right" />
            <span class="field-unit">分钟（在座满该时长起奖励）</span>
          </el-form-item>
          <el-form-item label="基础奖励分" prop="rewardBase">
            <el-input-number v-model="form.rewardBase" :min="0" :max="100" controls-position="right" />
            <span class="field-unit">分（0—100）</span>
          </el-form-item>
          <el-form-item label="递增步长时长" prop="rewardStepMinutes">
            <el-input-number v-model="form.rewardStepMinutes" :min="1" :max="720" controls-position="right" />
            <span class="field-unit">分钟（每满该时长递增）</span>
          </el-form-item>
          <el-form-item label="每步递增分" prop="rewardStepScore">
            <el-input-number v-model="form.rewardStepScore" :min="0" :max="100" controls-position="right" />
            <span class="field-unit">分（0—100）</span>
          </el-form-item>
          <el-form-item label="奖励上限" prop="rewardCap">
            <el-input-number v-model="form.rewardCap" :min="0" :max="100" controls-position="right" />
            <span class="field-unit">分（0—100）</span>
          </el-form-item>
        </div>

        <el-divider content-position="left">固定策略（不可编辑）</el-divider>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="结构版本">1（schemaVersion，本期固定）</el-descriptions-item>
          <el-descriptions-item label="时间格点">30 分钟（slotMinutes，本期固定）</el-descriptions-item>
          <el-descriptions-item label="信用上限">100 分（creditCap，本期固定）</el-descriptions-item>
          <el-descriptions-item label="到期策略">到期自动完成且不奖励（endPolicy，本期固定）</el-descriptions-item>
        </el-descriptions>

        <el-form-item label="发布原因" prop="reason" class="mt-16">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="请填写本次规则变更原因，将记入审计日志"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="publishing" @click="submitPublish">发布新版本</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 历史版本（只读） -->
    <div class="card table-wrap">
      <div class="card-header">
        <span class="card-title">历史版本</span>
      </div>
      <el-table :data="history" v-loading="historyLoading" border empty-text="暂无历史版本">
        <el-table-column prop="versionNo" label="版本" width="90" align="center">
          <template #default="{ row }">V{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="来源" width="130" align="center">
          <template #default="{ row }">
            {{ row.source === 'SYSTEM_SEED' ? '系统初始化' : '管理员发布' }}
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showConfig(row)">查看配置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="historyPage"
          v-model:page-size="historySize"
          :total="historyTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </div>

    <!-- 历史版本配置（只读） -->
    <el-dialog v-model="configVisible" :title="`V${configVersion?.versionNo ?? ''} 规则配置`" width="560px">
      <pre class="config-json">{{ configJson }}</pre>
      <template #footer>
        <el-button type="primary" @click="configVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { fetchAdminRule, fetchRuleHistory, publishRule } from '@/api/admin'
import { useRuleStore } from '@/stores/rule'
import { formatDateTime } from '@/utils/format'
import type { AdminRuleVersion } from '@/types'

const ruleStore = useRuleStore()

interface RuleField {
  key: string
  label: string
  unit?: string
}

/* 全部可配置 + 固定字段（展示顺序即表单顺序） */
const ALL_FIELDS: RuleField[] = [
  { key: 'advanceDays', label: '提前可约天数', unit: '天' },
  { key: 'minMinutes', label: '最短预约时长', unit: '分钟' },
  { key: 'maxMinutes', label: '最长预约时长', unit: '分钟' },
  { key: 'cancelBeforeMinutes', label: '可取消窗口', unit: '分钟' },
  { key: 'checkinEarlyMinutes', label: '可提前签到', unit: '分钟' },
  { key: 'checkinLateMinutes', label: '签到宽限（迟到）', unit: '分钟' },
  { key: 'leaveMaxCount', label: '暂离次数上限', unit: '次' },
  { key: 'leaveMaxMinutes', label: '单次暂离时长', unit: '分钟' },
  { key: 'noShowPenalty', label: '爽约扣分', unit: '分' },
  { key: 'leaveTimeoutPenalty', label: '暂离超时扣分', unit: '分' },
  { key: 'minCredit', label: '预约信用门槛', unit: '分' },
  { key: 'rewardStartMinutes', label: '奖励起点时长', unit: '分钟' },
  { key: 'rewardBase', label: '基础奖励分', unit: '分' },
  { key: 'rewardStepMinutes', label: '递增步长时长', unit: '分钟' },
  { key: 'rewardStepScore', label: '每步递增分', unit: '分' },
  { key: 'rewardCap', label: '奖励上限', unit: '分' },
  { key: 'schemaVersion', label: '结构版本' },
  { key: 'slotMinutes', label: '时间格点', unit: '分钟' },
  { key: 'creditCap', label: '信用上限', unit: '分' },
  { key: 'endPolicy', label: '到期策略' }
]

/* 本期固定策略，仅展示不可编辑 */
const FIXED_FIELDS = ['schemaVersion', 'slotMinutes', 'creditCap', 'endPolicy']

const FIXED_VALUES: Record<string, string> = {
  schemaVersion: '1',
  slotMinutes: '30',
  creditCap: '100',
  endPolicy: 'AUTO_COMPLETE_NO_REWARD（到期自动完成且不奖励）'
}

/* ---------- current rule ---------- */
const loading = ref(false)
const current = ref<AdminRuleVersion | null>(null)

function displayValue(key: string): string {
  if (FIXED_FIELDS.includes(key)) return FIXED_VALUES[key] || '—'
  const config = current.value?.config || {}
  const value = config[key]
  if (value === undefined || value === null) return '—'
  const unit = ALL_FIELDS.find((f) => f.key === key)?.unit
  return unit ? `${value} ${unit}` : String(value)
}

async function loadCurrent(): Promise<void> {
  loading.value = true
  try {
    current.value = await fetchAdminRule()
    initFormFromCurrent()
  } catch {
    current.value = null
  } finally {
    loading.value = false
  }
}

/* ---------- publish form ---------- */
interface RuleFormState {
  advanceDays: number
  minMinutes: number
  maxMinutes: number
  cancelBeforeMinutes: number
  checkinEarlyMinutes: number
  checkinLateMinutes: number
  leaveMaxCount: number
  leaveMaxMinutes: number
  noShowPenalty: number
  leaveTimeoutPenalty: number
  minCredit: number
  rewardStartMinutes: number
  rewardBase: number
  rewardStepMinutes: number
  rewardStepScore: number
  rewardCap: number
  reason: string
}

const defaultForm = (): RuleFormState => ({
  advanceDays: 2,
  minMinutes: 30,
  maxMinutes: 240,
  cancelBeforeMinutes: 15,
  checkinEarlyMinutes: 15,
  checkinLateMinutes: 15,
  leaveMaxCount: 2,
  leaveMaxMinutes: 30,
  noShowPenalty: 10,
  leaveTimeoutPenalty: 10,
  minCredit: 60,
  rewardStartMinutes: 120,
  rewardBase: 2,
  rewardStepMinutes: 30,
  rewardStepScore: 1,
  rewardCap: 5,
  reason: ''
})

const form = ref<RuleFormState>(defaultForm())
const formRef = ref<FormInstance>()
const publishing = ref(false)
const publishError = ref('')
const publishConflict = ref('')

function configNumber(config: Record<string, unknown>, key: string, fallback: number): number {
  const value = Number(config[key])
  return Number.isFinite(value) ? value : fallback
}

function initFormFromCurrent(): void {
  const config = current.value?.config || {}
  const base = defaultForm()
  form.value = {
    advanceDays: configNumber(config, 'advanceDays', base.advanceDays),
    minMinutes: configNumber(config, 'minMinutes', base.minMinutes),
    maxMinutes: configNumber(config, 'maxMinutes', base.maxMinutes),
    cancelBeforeMinutes: configNumber(config, 'cancelBeforeMinutes', base.cancelBeforeMinutes),
    checkinEarlyMinutes: configNumber(config, 'checkinEarlyMinutes', base.checkinEarlyMinutes),
    checkinLateMinutes: configNumber(config, 'checkinLateMinutes', base.checkinLateMinutes),
    leaveMaxCount: configNumber(config, 'leaveMaxCount', base.leaveMaxCount),
    leaveMaxMinutes: configNumber(config, 'leaveMaxMinutes', base.leaveMaxMinutes),
    noShowPenalty: configNumber(config, 'noShowPenalty', base.noShowPenalty),
    leaveTimeoutPenalty: configNumber(config, 'leaveTimeoutPenalty', base.leaveTimeoutPenalty),
    minCredit: configNumber(config, 'minCredit', base.minCredit),
    rewardStartMinutes: configNumber(config, 'rewardStartMinutes', base.rewardStartMinutes),
    rewardBase: configNumber(config, 'rewardBase', base.rewardBase),
    rewardStepMinutes: configNumber(config, 'rewardStepMinutes', base.rewardStepMinutes),
    rewardStepScore: configNumber(config, 'rewardStepScore', base.rewardStepScore),
    rewardCap: configNumber(config, 'rewardCap', base.rewardCap),
    reason: ''
  }
}

const intRange =
  (min: number, max: number, message: string) =>
  (_rule: unknown, value: number, callback: (err?: Error) => void) => {
    if (!Number.isInteger(value) || value < min || value > max) {
      callback(new Error(message))
    } else {
      callback()
    }
  }

const rules: FormRules<RuleFormState> = {
  advanceDays: [
    { required: true, message: '请输入提前可约天数', trigger: 'blur' },
    { validator: intRange(0, 30, '提前可约天数须为 0—30 的整数'), trigger: 'blur' }
  ],
  minMinutes: [
    { required: true, message: '请输入最短预约时长', trigger: 'blur' },
    { validator: intRange(30, 720, '最短预约时长须为 30—720 的整数'), trigger: 'blur' },
    {
      validator: (_rule, value: number, callback) => {
        if (value % 30 !== 0) callback(new Error('最短预约时长须为 30 的倍数'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  maxMinutes: [
    { required: true, message: '请输入最长预约时长', trigger: 'blur' },
    { validator: intRange(30, 720, '最长预约时长须为 30—720 的整数'), trigger: 'blur' },
    {
      validator: (_rule, value: number, callback) => {
        if (value % 30 !== 0) callback(new Error('最长预约时长须为 30 的倍数'))
        else if (value < form.value.minMinutes) callback(new Error('最长预约时长不得小于最短预约时长'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  cancelBeforeMinutes: [
    { required: true, message: '请输入可取消窗口', trigger: 'blur' },
    { validator: intRange(0, 60, '可取消窗口须为 0—60 的整数'), trigger: 'blur' }
  ],
  checkinEarlyMinutes: [
    { required: true, message: '请输入可提前签到时长', trigger: 'blur' },
    { validator: intRange(0, 60, '可提前签到须为 0—60 的整数'), trigger: 'blur' }
  ],
  checkinLateMinutes: [
    { required: true, message: '请输入签到宽限时长', trigger: 'blur' },
    { validator: intRange(0, 60, '签到宽限须为 0—60 的整数'), trigger: 'blur' },
    {
      validator: (_rule, value: number, callback) => {
        if (value >= form.value.minMinutes) {
          callback(new Error('签到宽限必须小于最短预约时长，否则新单可能没有有效签到机会'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  leaveMaxCount: [
    { required: true, message: '请输入暂离次数上限', trigger: 'blur' },
    { validator: intRange(0, 10, '暂离次数上限须为 0—10 的整数'), trigger: 'blur' }
  ],
  leaveMaxMinutes: [
    { required: true, message: '请输入单次暂离时长', trigger: 'blur' },
    { validator: intRange(1, 120, '单次暂离时长须为 1—120 的整数'), trigger: 'blur' }
  ],
  noShowPenalty: [
    { required: true, message: '请输入爽约扣分', trigger: 'blur' },
    { validator: intRange(0, 100, '爽约扣分须为 0—100 的整数'), trigger: 'blur' }
  ],
  leaveTimeoutPenalty: [
    { required: true, message: '请输入暂离超时扣分', trigger: 'blur' },
    { validator: intRange(0, 100, '暂离超时扣分须为 0—100 的整数'), trigger: 'blur' }
  ],
  minCredit: [
    { required: true, message: '请输入预约信用门槛', trigger: 'blur' },
    { validator: intRange(0, 100, '预约信用门槛须为 0—100 的整数'), trigger: 'blur' }
  ],
  rewardStartMinutes: [
    { required: true, message: '请输入奖励起点时长', trigger: 'blur' },
    { validator: intRange(1, 720, '奖励起点时长须为 1—720 的整数'), trigger: 'blur' }
  ],
  rewardBase: [
    { required: true, message: '请输入基础奖励分', trigger: 'blur' },
    { validator: intRange(0, 100, '基础奖励分须为 0—100 的整数'), trigger: 'blur' }
  ],
  rewardStepMinutes: [
    { required: true, message: '请输入递增步长时长', trigger: 'blur' },
    { validator: intRange(1, 720, '递增步长时长须为 1—720 的整数'), trigger: 'blur' }
  ],
  rewardStepScore: [
    { required: true, message: '请输入每步递增分', trigger: 'blur' },
    { validator: intRange(0, 100, '每步递增分须为 0—100 的整数'), trigger: 'blur' }
  ],
  rewardCap: [
    { required: true, message: '请输入奖励上限', trigger: 'blur' },
    { validator: intRange(0, 100, '奖励上限须为 0—100 的整数'), trigger: 'blur' }
  ],
  reason: [
    { required: true, message: '请填写发布原因', trigger: 'blur' },
    { max: 500, message: '发布原因不能超过 500 字', trigger: 'blur' }
  ]
}

function buildConfig(): Record<string, unknown> {
  const f = form.value
  return {
    schemaVersion: 1,
    advanceDays: f.advanceDays,
    slotMinutes: 30,
    minMinutes: f.minMinutes,
    maxMinutes: f.maxMinutes,
    cancelBeforeMinutes: f.cancelBeforeMinutes,
    checkinEarlyMinutes: f.checkinEarlyMinutes,
    checkinLateMinutes: f.checkinLateMinutes,
    leaveMaxMinutes: f.leaveMaxMinutes,
    leaveMaxCount: f.leaveMaxCount,
    noShowPenalty: f.noShowPenalty,
    leaveTimeoutPenalty: f.leaveTimeoutPenalty,
    minCredit: f.minCredit,
    creditCap: 100,
    rewardStartMinutes: f.rewardStartMinutes,
    rewardBase: f.rewardBase,
    rewardStepMinutes: f.rewardStepMinutes,
    rewardStepScore: f.rewardStepScore,
    rewardCap: f.rewardCap,
    endPolicy: 'AUTO_COMPLETE_NO_REWARD'
  }
}

async function submitPublish(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!current.value) {
    publishError.value = '尚未加载当前生效规则，无法确定基线版本，请刷新后重试'
    return
  }
  publishError.value = ''
  publishConflict.value = ''
  const baseVersion = current.value.versionNo
  const summary = [
    `提前 ${form.value.advanceDays} 天可约`,
    `时长 ${form.value.minMinutes}—${form.value.maxMinutes} 分钟`,
    `签到窗口 提前 ${form.value.checkinEarlyMinutes}/宽限 ${form.value.checkinLateMinutes} 分钟`,
    `取消窗口 ${form.value.cancelBeforeMinutes} 分钟`,
    `暂离 ${form.value.leaveMaxCount} 次 × ${form.value.leaveMaxMinutes} 分钟`,
    `爽约扣 ${form.value.noShowPenalty} 分 / 暂离超时扣 ${form.value.leaveTimeoutPenalty} 分`,
    `信用门槛 ${form.value.minCredit} 分`,
    `满 ${form.value.rewardStartMinutes} 分钟奖 ${form.value.rewardBase} 分，每 ${form.value.rewardStepMinutes} 分钟加 ${form.value.rewardStepScore} 分，上限 ${form.value.rewardCap} 分`
  ].join('；')
  const confirmed = await ElMessageBox.confirm(
    `将基于当前 V${baseVersion} 发布新规则版本，发布后立即生效且不可撤销，已成立订单按各自快照执行。${summary}。确认发布吗？`,
    '二次确认发布',
    { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  publishing.value = true
  try {
    await publishRule({
      config: buildConfig(),
      baseVersion,
      reason: form.value.reason.trim()
    })
    // 发布后立即使展示缓存失效
    ruleStore.invalidate()
    publishConflict.value = ''
    await Promise.all([loadCurrent(), loadHistory()])
    await ElMessageBox.alert('新规则版本已发布并立即生效。', '发布成功', {
      confirmButtonText: '知道了',
      type: 'success'
    })
  } catch (err) {
    // 409 基线冲突：不静默覆盖，重新加载最新规则让发布人确认后再发
    const status = (err as { status?: number })?.status
    publishConflict.value =
      status === 409
        ? '发布失败：当前生效规则已变为其他版本（基线冲突）。已为你重新加载最新规则，请核对后再发布。'
        : '发布失败，请修正后重试。'
    await Promise.all([loadCurrent(), loadHistory()])
  } finally {
    publishing.value = false
  }
}

/* ---------- history ---------- */
const history = ref<AdminRuleVersion[]>([])
const historyLoading = ref(false)
const historyPage = ref(1)
const historySize = ref(20)
const historyTotal = ref(0)

async function loadHistory(): Promise<void> {
  historyLoading.value = true
  try {
    const res = await fetchRuleHistory(historyPage.value, historySize.value)
    history.value = res.items || []
    historyTotal.value = res.total || 0
  } catch {
    history.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

const configVisible = ref(false)
const configVersion = ref<AdminRuleVersion | null>(null)
const configJson = computed(() =>
  configVersion.value ? JSON.stringify(configVersion.value.config, null, 2) : ''
)

function showConfig(row: AdminRuleVersion): void {
  configVersion.value = row
  configVisible.value = true
}

onMounted(() => {
  void loadCurrent()
  void loadHistory()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.card-title {
  font-weight: 600;
}
.version-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.fixed-note {
  color: var(--text-sub);
  font-size: 12px;
}
.rule-form {
  max-width: 860px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 0 24px;
}
.field-unit {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-sub);
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.config-json {
  margin: 0;
  padding: 12px;
  background: var(--bg-page);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

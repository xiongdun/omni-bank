<template>
  <div class="panel task-header">
    <div class="title-row">
      <span class="task-no">{{ detail.taskNo }}</span>
      <span class="task-name">{{ detail.enterprise?.enterpriseName || detail.taskName || '尽调任务' }}</span>
      <el-tag :type="statusMeta.type" effect="light">
        {{ detail.taskStatusDesc || status?.taskStatusDesc || statusMeta.label }}
      </el-tag>
      <span class="sse" :class="store.sseState">
        <i class="dot" />{{ sseText }}
      </span>
      <span class="progress-text">进度 {{ clampProgress(store.progress) }}%</span>
    </div>

    <el-progress
      :percentage="clampProgress(store.progress)"
      :show-text="false"
      :stroke-width="6"
      :status="progressStatus"
      class="progress"
    />

    <div class="info-row muted">
      <span>阶段：{{ status?.currentStage || detail.currentStage || '—' }}</span>
      <span>上下文 v{{ detail.contextVersion ?? '-' }}</span>
      <span>分析 v{{ detail.analysisVersion ?? '-' }}</span>
      <span>提交：{{ formatDateTime(detail.submittedAt) }}</span>
      <span>完成：{{ formatDateTime(detail.completedAt) }}</span>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="`${detail.lastErrorCode || '任务异常'}：${errorMessage}`"
      type="error"
      :closable="false"
      show-icon
      class="error"
    />

    <div class="actions">
      <el-button v-if="canSubmit" type="primary" size="small" :loading="busy" @click="submit">
        提交任务
      </el-button>
      <el-button v-if="canResume" type="warning" size="small" :loading="busy" @click="resume">
        断点恢复
      </el-button>
      <el-button size="small" :loading="busy" @click="reidentify">重新识别场景</el-button>
      <el-button size="small" @click="emit('open-plan')">查看计划</el-button>
      <el-button size="small" @click="emit('open-evidence')">证据溯源</el-button>
      <el-button size="small" :icon="Refresh" circle title="刷新" @click="refresh" />
      <el-button
        v-if="canCancel"
        type="danger"
        plain
        size="small"
        class="right"
        :loading="busy"
        @click="cancel"
      >
        取消任务
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useTaskStore } from '../stores/task'
import {
  canCancelTask,
  clampProgress,
  formatDateTime,
  taskStatusMeta
} from '../utils/dd'

const emit = defineEmits<{
  (event: 'open-plan'): void
  (event: 'open-evidence'): void
}>()

const store = useTaskStore()
const busy = ref(false)

const detail = computed(() => store.detail!)
const status = computed(() => store.status)
const currentStatus = computed(() => status.value?.taskStatus ?? detail.value.taskStatus)
const statusMeta = computed(() => taskStatusMeta(currentStatus.value))

const canSubmit = computed(() => currentStatus.value === 'DRAFT')
const canResume = computed(() => currentStatus.value === 'FAILED')
const canCancel = computed(() => canCancelTask(currentStatus.value))
const errorMessage = computed(() => detail.value.lastErrorMessage || status.value?.lastErrorMessage || '')

const progressStatus = computed<'success' | 'exception' | undefined>(() => {
  if (currentStatus.value === 'COMPLETED') {
    return 'success'
  }
  if (currentStatus.value === 'FAILED' || currentStatus.value === 'CANCELLED') {
    return 'exception'
  }
  return undefined
})

const sseText = computed(() => {
  switch (store.sseState) {
    case 'connected':
      return '实时推送'
    case 'reconnecting':
      return '连接中断，重连中'
    case 'closed':
      return '推送已结束'
    default:
      return '未连接'
  }
})

async function run(action: () => Promise<unknown>) {
  busy.value = true
  try {
    await action()
  } catch {
    await store.refreshCurrent()
  } finally {
    busy.value = false
  }
}

function submit() {
  void run(() => store.submitTask())
}

function resume() {
  void run(() => store.resumeTask())
}

function reidentify() {
  void run(() => store.reidentifyScenario())
}

function refresh() {
  void run(() => store.refreshCurrent())
}

async function cancel() {
  try {
    await ElMessageBox.confirm('取消后任务不可恢复，确认取消？', '取消任务', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回'
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await store.cancelTask()
  } catch {
    ElMessage.warning('取消失败，请刷新后重试')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.task-header {
  margin-bottom: 12px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.task-no {
  font-size: 15px;
  font-weight: 600;
}

.task-name {
  font-size: 13px;
}

.sse {
  font-size: 12px;
  color: #909399;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
}

.sse .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
}

.sse.connected .dot {
  background: #67c23a;
}

.sse.reconnecting .dot {
  background: #e6a23c;
}

.progress-text {
  margin-left: auto;
  font-size: 12px;
  color: #606266;
}

.progress {
  margin: 10px 0 6px;
}

.info-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
}

.error {
  margin-top: 8px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.actions .right {
  margin-left: auto;
}
</style>

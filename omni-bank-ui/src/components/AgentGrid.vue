<template>
  <div v-loading="loading" class="agent-grid">
    <div
      v-for="code in AGENT_CODES"
      :key="code"
      class="agent-cell"
      :class="statusClass(latestByAgent.get(code)?.executionStatus)"
    >
      <span class="agent-name">{{ agentLabel(code) }}</span>
      <el-tag :type="executionStatusMeta(latestByAgent.get(code)?.executionStatus).type" size="small" effect="plain">
        {{ latestByAgent.get(code) ? executionStatusMeta(latestByAgent.get(code)?.executionStatus).label : '未执行' }}
      </el-tag>
      <span class="agent-time">
        {{ formatDuration(latestByAgent.get(code)?.durationMs) }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ddApi } from '../api/dd'
import type { AgentExecutionVO, Id } from '../types/dd'
import { AGENT_CODES, agentLabel, executionStatusMeta, formatDuration } from '../utils/dd'

const props = defineProps<{
  taskId: Id
  tick: number
}>()

const executions = ref<AgentExecutionVO[]>([])
const loading = ref(false)

const latestByAgent = computed(() => {
  const map = new Map<string, AgentExecutionVO>()
  for (const execution of executions.value) {
    if (!execution.agentCode) {
      continue
    }
    const previous = map.get(execution.agentCode)
    if (!previous || (execution.executionNo ?? 0) >= (previous.executionNo ?? 0)) {
      map.set(execution.agentCode, execution)
    }
  }
  return map
})

function statusClass(status?: string | null) {
  return status ? status.toLowerCase() : 'none'
}

async function load() {
  loading.value = true
  try {
    const page = await ddApi.pageExecutions(props.taskId, { current: 1, size: 100 })
    executions.value = page.records ?? []
  } catch {
    executions.value = []
  } finally {
    loading.value = false
  }
}

watch(() => [props.taskId, props.tick], load, { immediate: true })
</script>

<style scoped>
.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.agent-cell {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 6px 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.agent-cell.succeeded {
  border-color: #b3e19d;
  background: #f0f9eb;
}

.agent-cell.running {
  border-color: #f3d19e;
  background: #fdf6ec;
}

.agent-cell.failed {
  border-color: #fab6b6;
  background: #fef0f0;
}

.agent-name {
  font-weight: 500;
}

.agent-time {
  margin-left: auto;
  color: #909399;
  font-size: 11px;
}
</style>

<template>
  <div class="panel">
    <div class="toolbar">
      <el-select v-model="query.executionStatus" placeholder="全部状态" clearable style="width: 140px" @change="search">
        <el-option
          v-for="item in EXECUTION_STATUS_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-input v-model="query.nodeId" placeholder="节点 ID" clearable style="width: 180px" @keyup.enter="search" />
      <el-button type="primary" size="small" @click="search">查询</el-button>
      <span class="spacer" />
      <span class="muted">共 {{ total }} 条执行记录</span>
    </div>

    <el-table v-loading="loading" :data="records" size="small" border stripe>
      <el-table-column prop="nodeId" label="节点" min-width="150" show-overflow-tooltip />
      <el-table-column label="Agent" min-width="120">
        <template #default="{ row }">{{ agentLabel(row.agentCode) }}</template>
      </el-table-column>
      <el-table-column prop="executionNo" label="序号" width="70" align="center" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="executionStatusMeta(row.executionStatus).type" size="small" effect="light">
            {{ executionStatusMeta(row.executionStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90" align="right">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
      <el-table-column label="错误" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMessage" class="error-text">{{ row.errorMessage }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.current"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ddApi } from '../api/dd'
import type { AgentExecutionVO, Id } from '../types/dd'
import {
  EXECUTION_STATUS_OPTIONS,
  agentLabel,
  executionStatusMeta,
  formatDateTime,
  formatDuration
} from '../utils/dd'

const props = defineProps<{
  taskId: Id
  tick: number
}>()

const loading = ref(false)
const records = ref<AgentExecutionVO[]>([])
const total = ref(0)
const query = reactive({
  executionStatus: '',
  nodeId: '',
  current: 1,
  size: 10
})

async function load() {
  loading.value = true
  try {
    const page = await ddApi.pageExecutions(props.taskId, {
      current: query.current,
      size: query.size,
      executionStatus: query.executionStatus || undefined,
      nodeId: query.nodeId || undefined
    })
    records.value = page.records ?? []
    total.value = page.total ?? 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.current = 1
  void load()
}

function changePage(page: number) {
  query.current = page
  void load()
}

watch(
  () => props.taskId,
  () => {
    query.executionStatus = ''
    query.nodeId = ''
    query.current = 1
    void load()
  },
  { immediate: true }
)

watch(() => props.tick, load)
</script>

<style scoped>
.error-text {
  color: #f56c6c;
}
</style>

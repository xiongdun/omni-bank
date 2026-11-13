<template>
  <div class="panel">
    <div class="panel-title">证据</div>
    <div class="toolbar">
      <el-select v-model="query.evidenceType" placeholder="证据类型" clearable style="width: 150px" @change="search">
        <el-option v-for="item in EVIDENCE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.sourceType" placeholder="来源类型" clearable style="width: 150px" @change="search">
        <el-option v-for="item in EVIDENCE_SOURCE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <span class="spacer" />
      <span class="muted">共 {{ total }} 条</span>
    </div>

    <el-table
      v-loading="loading"
      :data="records"
      size="small"
      border
      highlight-current-row
      @row-click="openDetail"
    >
      <el-table-column label="证据" width="110">
        <template #default="{ row }">{{ shortId(row.evidenceId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ evidenceTypeLabel(row.evidenceType) }}</template>
      </el-table-column>
      <el-table-column label="来源" width="120">
        <template #default="{ row }">{{ evidenceSourceLabel(row.sourceType) }}</template>
      </el-table-column>
      <el-table-column prop="sourceSystem" label="来源系统" width="120" show-overflow-tooltip />
      <el-table-column label="模拟数据" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isMock" type="warning" size="small" effect="plain">模拟</el-tag>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="contentSummary" label="内容摘要" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="evidenceStatusMeta(row.status).type" size="small" effect="plain">
            {{ evidenceStatusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="采集时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.collectedAt) }}</template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        size="small"
        layout="prev, pager, next"
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
import type { EvidenceListVO, Id } from '../types/dd'
import {
  EVIDENCE_SOURCE_OPTIONS,
  EVIDENCE_TYPE_OPTIONS,
  evidenceSourceLabel,
  evidenceStatusMeta,
  evidenceTypeLabel,
  formatDateTime,
  shortId
} from '../utils/dd'

const props = defineProps<{
  taskId: Id
  tick: number
}>()

const emit = defineEmits<{
  (event: 'open-evidence', evidenceId: Id): void
}>()

const loading = ref(false)
const records = ref<EvidenceListVO[]>([])
const total = ref(0)
const query = reactive({
  evidenceType: '',
  sourceType: '',
  current: 1,
  size: 10
})

async function load() {
  loading.value = true
  try {
    const page = await ddApi.pageEvidences(props.taskId, {
      current: query.current,
      size: query.size,
      evidenceType: query.evidenceType || undefined,
      sourceType: query.sourceType || undefined
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

function openDetail(row: EvidenceListVO) {
  emit('open-evidence', row.evidenceId)
}

watch(
  () => props.taskId,
  () => {
    query.evidenceType = ''
    query.sourceType = ''
    query.current = 1
    void load()
  },
  { immediate: true }
)

watch(() => props.tick, load)
</script>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}
</style>

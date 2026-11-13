<template>
  <div class="panel">
    <div class="panel-title">风险线索</div>
    <div class="toolbar">
      <el-select v-model="query.severity" placeholder="严重程度" clearable style="width: 120px" @change="search">
        <el-option v-for="item in SEVERITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="query.status" placeholder="线索状态" clearable style="width: 130px" @change="search">
        <el-option v-for="item in FINDING_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <span class="spacer" />
      <span class="muted">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="records" size="small" border>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand">
            <p><span class="label">线索说明：</span>{{ row.description || '-' }}</p>
            <p><span class="label">核实建议：</span>{{ row.verificationSuggestion || '-' }}</p>
            <p v-if="row.relatedFindingCodes?.length">
              <span class="label">关联线索：</span>{{ row.relatedFindingCodes.join('、') }}
            </p>
            <p v-if="row.evidenceRefs?.length">
              <span class="label">证据引用：</span>
              <el-button
                v-for="ref in row.evidenceRefs"
                :key="ref.evidenceId"
                link
                type="primary"
                size="small"
                @click="emit('open-evidence', ref.evidenceId)"
              >
                {{ ref.evidenceType || '证据' }} · {{ shortId(ref.evidenceId) }}
              </el-button>
            </p>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="程度" width="80">
        <template #default="{ row }">
          <el-tag :type="severityMeta(row.severity).type" size="small" effect="dark">
            {{ severityMeta(row.severity).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="线索" min-width="220">
        <template #default="{ row }">
          <div class="finding-title">{{ row.title || row.findingCode }}</div>
          <div class="muted">{{ row.findingCode }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="120" show-overflow-tooltip />
      <el-table-column label="Agent" width="110">
        <template #default="{ row }">{{ agentLabel(row.agentCode) }}</template>
      </el-table-column>
      <el-table-column label="置信度" width="80" align="right">
        <template #default="{ row }">{{ row.confidence ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="findingStatusMeta(row.status).type" size="small" effect="plain">
            {{ findingStatusMeta(row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发现时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
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
import type { FindingListVO, Id } from '../types/dd'
import {
  FINDING_STATUS_OPTIONS,
  SEVERITY_OPTIONS,
  agentLabel,
  findingStatusMeta,
  formatDateTime,
  severityMeta,
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
const records = ref<FindingListVO[]>([])
const total = ref(0)
const query = reactive({ severity: '', status: '', current: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const page = await ddApi.pageFindings(props.taskId, {
      current: query.current,
      size: query.size,
      severity: query.severity || undefined,
      status: query.status || undefined
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
    query.severity = ''
    query.status = ''
    query.current = 1
    void load()
  },
  { immediate: true }
)

watch(() => props.tick, load)
</script>

<style scoped>
.expand {
  padding: 4px 12px;
  font-size: 12px;
  line-height: 1.7;
}

.expand p {
  margin: 2px 0;
}

.label {
  color: #909399;
}

.finding-title {
  font-weight: 500;
}
</style>

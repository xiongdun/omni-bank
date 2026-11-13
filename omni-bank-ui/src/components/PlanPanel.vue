<template>
  <div class="panel">
    <div class="panel-title">
      当前尽调计划
      <el-button class="extra" link type="primary" size="small" @click="load">刷新</el-button>
    </div>
    <PlanDetail v-if="current" :plan="current" />
    <el-empty v-else :image-size="50" description="任务尚未生成尽调计划" />

    <el-divider />

    <div class="panel-title">计划版本</div>
    <el-table v-loading="loading" :data="versions" size="small" border>
      <el-table-column label="版本" width="70" align="center">
        <template #default="{ row }">v{{ row.planVersion }}</template>
      </el-table-column>
      <el-table-column prop="plannerMode" label="规划模式" width="120" />
      <el-table-column prop="businessScenario" label="业务场景" min-width="140" show-overflow-tooltip />
      <el-table-column label="置信度" width="80" align="right">
        <template #default="{ row }">{{ row.confidence ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="生成时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="70" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="emit('open-plan', row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        size="small"
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ddApi } from '../api/dd'
import type { Id, PlanVO } from '../types/dd'
import { formatDateTime } from '../utils/dd'
import PlanDetail from './PlanDetail.vue'

const props = defineProps<{
  taskId: Id
  tick: number
}>()

const emit = defineEmits<{
  (event: 'open-plan', plan: PlanVO): void
}>()

const current = ref<PlanVO | null>(null)
const versions = ref<PlanVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [planPage, currentPlan] = await Promise.all([
      ddApi.pagePlans(props.taskId, { current: currentPage.value, size: pageSize }),
      ddApi.currentPlan(props.taskId).catch(() => null)
    ])
    versions.value = planPage.records ?? []
    total.value = planPage.total ?? 0
    current.value = currentPlan
  } catch {
    versions.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changePage(page: number) {
  currentPage.value = page
  void load()
}

watch(
  () => props.taskId,
  () => {
    currentPage.value = 1
    current.value = null
    void load()
  },
  { immediate: true }
)

watch(() => props.tick, load)
</script>

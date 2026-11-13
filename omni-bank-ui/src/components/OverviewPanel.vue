<template>
  <div class="overview">
    <div class="top">
      <div class="panel">
        <div class="panel-title">
          Agent 执行进度
          <span class="extra">按 Agent 取最新一次执行</span>
        </div>
        <AgentGrid :task-id="taskId" :tick="tick" />
      </div>

      <div class="panel">
        <div class="panel-title">
          业务场景
          <el-tag
            v-if="scenario"
            :type="scenarioStatusMeta(scenario.identifyStatus).type"
            size="small"
            effect="light"
            class="extra"
          >
            {{ scenarioStatusMeta(scenario.identifyStatus).label }}
          </el-tag>
        </div>
        <template v-if="scenario">
          <div class="scenario-title">
            {{ scenario.businessTypeDesc || scenario.businessType || '未识别' }}
            <span class="muted">v{{ scenario.scenarioVersion ?? '-' }}</span>
          </div>
          <div class="kv">
            <span>产品类型</span><span>{{ scenario.productType || '-' }}</span>
            <span>行业</span><span>{{ scenario.industryName || scenario.industryCode || '-' }}</span>
            <span>贷款用途</span><span>{{ scenario.loanPurposeType || '-' }}</span>
            <span>融资模式</span><span>{{ scenario.financingMode || '-' }}</span>
            <span>担保方式</span><span>{{ scenario.guaranteeTypes?.join('、') || '-' }}</span>
            <span>置信度</span><span>{{ scenario.confidence ?? '-' }}</span>
          </div>
          <el-alert
            v-if="scenario.uncertainItems?.length"
            type="warning"
            :closable="false"
            show-icon
            class="uncertain"
            :title="`待确认：${scenario.uncertainItems.join('；')}`"
          />
          <el-button link type="primary" size="small" @click="emit('open-plan')">查看计划</el-button>
        </template>
        <el-empty v-else :image-size="50" description="尚无场景识别结果" />
      </div>
    </div>

    <div class="bottom">
      <div class="panel">
        <div class="panel-title">
          报告
          <el-button class="extra" link type="primary" size="small" @click="emit('open-report')">
            查看报告
          </el-button>
        </div>
        <template v-if="latestReport">
          <div class="report-title">
            v{{ latestReport.reportVersion }} · {{ latestReport.title || '尽调报告' }}
            <el-tag :type="reportStatusMeta(latestReport.reportStatus).type" size="small" effect="light">
              {{ reportStatusMeta(latestReport.reportStatus).label }}
            </el-tag>
          </div>
          <div class="stats">
            <span>证据 {{ latestReport.evidenceCount ?? 0 }}</span>
            <span>数据缺口 {{ latestReport.dataGapCount ?? 0 }}</span>
            <span>补充材料 {{ latestReport.supplementCount ?? 0 }}</span>
            <span>{{ formatDateTime(latestReport.generatedAt) }}</span>
          </div>
        </template>
        <el-empty v-else :image-size="50" description="尚未生成报告" />
      </div>

      <div class="panel">
        <div class="panel-title">
          风险线索
          <span class="extra">TOP {{ findings.length }}</span>
        </div>
        <div v-if="findings.length" class="findings">
          <div
            v-for="finding in findings"
            :key="finding.findingId"
            class="finding-row"
            @click="openFindingEvidence(finding)"
          >
            <el-tag :type="severityMeta(finding.severity).type" size="small" effect="dark">
              {{ severityMeta(finding.severity).label }}
            </el-tag>
            <span class="finding-title">{{ finding.title || finding.findingCode }}</span>
            <el-tag :type="findingStatusMeta(finding.status).type" size="small" effect="plain">
              {{ findingStatusMeta(finding.status).label }}
            </el-tag>
          </div>
        </div>
        <el-empty v-else :image-size="50" description="暂无风险线索" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ddApi } from '../api/dd'
import type { FindingListVO, Id, ReportVersionVO, TaskDetailVO } from '../types/dd'
import AgentGrid from './AgentGrid.vue'
import {
  findingStatusMeta,
  formatDateTime,
  reportStatusMeta,
  scenarioStatusMeta,
  severityMeta
} from '../utils/dd'

const props = defineProps<{
  taskId: Id
  detail: TaskDetailVO
  tick: number
}>()

const emit = defineEmits<{
  (event: 'open-plan'): void
  (event: 'open-report'): void
  (event: 'open-evidence', evidenceId: Id): void
}>()

const latestReport = ref<ReportVersionVO | null>(null)
const findings = ref<FindingListVO[]>([])

const scenario = computed(() => props.detail.latestScenario)

async function load() {
  try {
    const [reportPage, findingPage] = await Promise.all([
      ddApi.pageReports(props.taskId, { current: 1, size: 1 }),
      ddApi.pageFindings(props.taskId, { current: 1, size: 5 })
    ])
    latestReport.value = reportPage.records?.[0] ?? null
    findings.value = findingPage.records ?? []
  } catch {
    // 错误提示由 http 层统一处理
  }
}

function openFindingEvidence(finding: FindingListVO) {
  const evidenceId = finding.evidenceRefs?.[0]?.evidenceId
  if (evidenceId) {
    emit('open-evidence', evidenceId)
  }
}

watch(
  () => [props.taskId, props.tick],
  load,
  { immediate: true }
)
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.top,
.bottom {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.bottom {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.scenario-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.kv {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 4px 8px;
  font-size: 12px;
}

.kv span:nth-child(odd) {
  color: #909399;
}

.uncertain {
  margin-top: 8px;
}

.report-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
}

.stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #606266;
}

.findings {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.finding-row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 12px;
}

.finding-row:hover .finding-title {
  color: #409eff;
}

.finding-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

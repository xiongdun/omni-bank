<template>
  <el-drawer
    :model-value="modelValue"
    size="640px"
    title="证据溯源"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="evidence-drawer">
      <template v-if="detail">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="证据 ID">{{ detail.evidenceId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="evidenceStatusMeta(detail.status).type" size="small" effect="plain">
              {{ evidenceStatusMeta(detail.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="证据类型">{{ evidenceTypeLabel(detail.evidenceType) }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{ evidenceSourceLabel(detail.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="来源系统">{{ detail.sourceSystem || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源引用">{{ detail.sourceReference || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数据期间">{{ detail.dataPeriod || '-' }}</el-descriptions-item>
          <el-descriptions-item label="采集时间">{{ formatDateTime(detail.collectedAt) }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ detail.confidence ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="模拟数据">{{ detail.isMock ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="内容哈希" :span="2">{{ detail.contentHash || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section">
          <div class="section-title">内容摘要</div>
          <div class="summary">{{ detail.contentSummary || '无' }}</div>
        </div>

        <div class="section">
          <div class="section-title">引用该证据的风险线索（{{ references?.findings?.length ?? 0 }}）</div>
          <div v-if="references?.findings?.length" class="ref-list">
            <div v-for="finding in references.findings" :key="finding.findingId" class="ref-row">
              <el-tag :type="severityMeta(finding.severity).type" size="small" effect="dark">
                {{ severityMeta(finding.severity).label }}
              </el-tag>
              <span class="ref-title">{{ finding.title || finding.findingCode }}</span>
              <el-tag :type="findingStatusMeta(finding.status).type" size="small" effect="plain">
                {{ findingStatusMeta(finding.status).label }}
              </el-tag>
            </div>
          </div>
          <span v-else class="muted">无</span>
        </div>

        <div class="section">
          <div class="section-title">引用该证据的报告版本（{{ references?.reports?.length ?? 0 }}）</div>
          <div v-if="references?.reports?.length" class="ref-list">
            <div v-for="report in references.reports" :key="report.id" class="ref-row">
              <span class="ref-title">v{{ report.reportVersion }} · {{ report.title || '尽调报告' }}</span>
              <el-tag :type="reportStatusMeta(report.reportStatus).type" size="small" effect="plain">
                {{ reportStatusMeta(report.reportStatus).label }}
              </el-tag>
            </div>
          </div>
          <span v-else class="muted">无</span>
        </div>

        <div class="section">
          <div class="section-title">复核留痕（{{ references?.reviews?.length ?? 0 }}）</div>
          <div v-if="references?.reviews?.length" class="ref-list">
            <div v-for="review in references.reviews" :key="review.id" class="ref-row">
              <span class="ref-title">
                {{ REPORT_ACTION_LABELS[review.actionType ?? ''] || review.actionType }}
              </span>
              <span class="muted">{{ review.reviewer || '-' }} · {{ formatDateTime(review.createTime) }}</span>
            </div>
          </div>
          <span v-else class="muted">无</span>
        </div>
      </template>
      <el-empty v-else :image-size="60" description="请选择一条证据" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ddApi } from '../api/dd'
import type { EvidenceDetailVO, EvidenceReferenceVO, Id } from '../types/dd'
import {
  REPORT_ACTION_LABELS,
  evidenceSourceLabel,
  evidenceStatusMeta,
  evidenceTypeLabel,
  findingStatusMeta,
  formatDateTime,
  reportStatusMeta,
  severityMeta
} from '../utils/dd'

const props = defineProps<{
  modelValue: boolean
  evidenceId: Id | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const detail = ref<EvidenceDetailVO | null>(null)
const references = ref<EvidenceReferenceVO | null>(null)
const loading = ref(false)

async function load(evidenceId: Id) {
  loading.value = true
  try {
    const [detailData, referenceData] = await Promise.all([
      ddApi.evidenceDetail(evidenceId),
      ddApi.evidenceReferences(evidenceId).catch(() => null)
    ])
    detail.value = detailData
    references.value = referenceData
  } catch {
    detail.value = null
    references.value = null
  } finally {
    loading.value = false
  }
}

watch(
  [() => props.modelValue, () => props.evidenceId],
  ([visible, evidenceId]) => {
    if (!visible) {
      detail.value = null
      references.value = null
      return
    }
    if (evidenceId) {
      void load(evidenceId)
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.evidence-drawer {
  min-height: 200px;
}

.section {
  margin-top: 14px;
}

.section-title {
  font-weight: 600;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary {
  font-size: 12px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}

.ref-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ref-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.ref-title {
  flex: 1;
}
</style>

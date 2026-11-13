<template>
  <div class="plan-detail">
    <el-descriptions :column="3" size="small" border>
      <el-descriptions-item label="计划版本">v{{ plan.planVersion ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="规划模式">{{ plan.plannerMode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="置信度">{{ plan.confidence ?? '-' }}</el-descriptions-item>
      <el-descriptions-item label="业务场景">{{ plan.businessScenario || '-' }}</el-descriptions-item>
      <el-descriptions-item label="子场景">{{ plan.subScenario || '-' }}</el-descriptions-item>
      <el-descriptions-item label="生成时间">{{ formatDateTime(plan.createTime) }}</el-descriptions-item>
      <el-descriptions-item label="模型">{{ plan.modelName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Prompt 版本">{{ plan.promptVersion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="计划 ID">{{ shortId(plan.planId) }}</el-descriptions-item>
    </el-descriptions>

    <div class="section">
      <div class="section-title">规划说明</div>
      <div class="reasoning">{{ plan.reasoningSummary || '无' }}</div>
    </div>

    <div class="section">
      <div class="section-title">Agent 计划</div>
      <div class="tags">
        <el-tag v-for="code in plan.agentPlan ?? []" :key="code" size="small" effect="plain">
          {{ agentLabel(code) }}
        </el-tag>
        <span v-if="!plan.agentPlan?.length" class="muted">无</span>
      </div>
    </div>

    <div class="section">
      <div class="section-title">依赖关系</div>
      <div v-if="dependencyEntries.length" class="deps">
        <div v-for="[code, upstream] in dependencyEntries" :key="code" class="dep-row">
          <span class="dep-code">{{ agentLabel(code) }}</span>
          <span class="muted">←</span>
          <span>{{ upstream.length ? upstream.map(agentLabel).join('、') : '无上游' }}</span>
        </div>
      </div>
      <span v-else class="muted">无</span>
    </div>

    <div class="section">
      <div class="section-title">数据采集计划</div>
      <pre class="json-block">{{ toJson(plan.dataPlan) }}</pre>
    </div>

    <div class="section">
      <div class="section-title">材料计划</div>
      <pre class="json-block">{{ toJson(plan.materialPlan) }}</pre>
    </div>

    <div class="section">
      <div class="section-title">补充材料计划</div>
      <pre class="json-block">{{ toJson(plan.supplementPlan) }}</pre>
    </div>

    <div class="section">
      <div class="section-title">执行配置</div>
      <pre class="json-block">{{ toJson(plan.executionConfig) }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PlanVO } from '../types/dd'
import { agentLabel, formatDateTime, shortId } from '../utils/dd'

const props = defineProps<{ plan: PlanVO }>()

const dependencyEntries = computed(() => Object.entries(props.plan.dependencyPlan ?? {}))

function toJson(value: unknown): string {
  if (value === null || value === undefined) {
    return '无'
  }
  return JSON.stringify(value, null, 2)
}
</script>

<style scoped>
.plan-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  font-weight: 600;
  margin-bottom: 6px;
  font-size: 12px;
}

.reasoning {
  font-size: 12px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}

.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.deps {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.dep-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.dep-code {
  font-weight: 500;
  min-width: 120px;
}
</style>

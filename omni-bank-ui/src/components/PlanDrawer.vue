<template>
  <el-drawer
    :model-value="modelValue"
    size="680px"
    :title="resolved ? `尽调计划 v${resolved.planVersion}` : '当前尽调计划'"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <PlanDetail v-if="resolved" :plan="resolved" />
      <el-empty v-else :image-size="60" description="任务尚未生成尽调计划" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ddApi } from '../api/dd'
import type { Id, PlanVO } from '../types/dd'
import PlanDetail from './PlanDetail.vue'

const props = defineProps<{
  modelValue: boolean
  taskId: Id | null
  plan: PlanVO | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const localPlan = ref<PlanVO | null>(null)
const loading = ref(false)

const resolved = computed(() => props.plan ?? localPlan.value)

watch(
  [() => props.modelValue, () => props.plan],
  async ([visible, plan]) => {
    if (!visible || plan || !props.taskId) {
      if (!visible) {
        localPlan.value = null
      }
      return
    }
    loading.value = true
    try {
      localPlan.value = await ddApi.currentPlan(props.taskId).catch(() => null)
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>

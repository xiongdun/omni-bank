<template>
  <div class="workbench">
    <aside class="sidebar">
      <TaskListPanel />
    </aside>

    <main class="content">
      <template v-if="detail">
        <TaskHeader @open-plan="planDrawerVisible = true" @open-evidence="openEvidenceTab" />
        <el-tabs v-model="activeTab" class="tabs">
          <el-tab-pane label="概览" name="overview" lazy>
            <OverviewPanel
              :task-id="taskId"
              :detail="detail"
              :tick="store.refreshTick"
              @open-plan="planDrawerVisible = true"
              @open-report="activeTab = 'report'"
              @open-evidence="openEvidence"
            />
          </el-tab-pane>
          <el-tab-pane label="执行记录" name="execution" lazy>
            <ExecutionPanel :task-id="taskId" :tick="store.refreshTick" />
          </el-tab-pane>
          <el-tab-pane label="报告" name="report" lazy>
            <ReportPanel
              :task-id="taskId"
              :tick="store.refreshTick"
              @open-evidence="openEvidence"
              @changed="store.refreshCurrent()"
            />
          </el-tab-pane>
          <el-tab-pane label="线索与补充" name="finding" lazy>
            <div class="split">
              <FindingPanel :task-id="taskId" :tick="store.refreshTick" />
              <SupplementPanel
                :task-id="taskId"
                :tick="store.refreshTick"
                @changed="store.refreshCurrent()"
              />
            </div>
          </el-tab-pane>
          <el-tab-pane label="计划与证据" name="archive" lazy>
            <div class="split">
              <PlanPanel :task-id="taskId" :tick="store.refreshTick" @open-plan="openPlan" />
              <EvidencePanel
                :task-id="taskId"
                :tick="store.refreshTick"
                @open-evidence="openEvidence"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="请从左侧选择任务，或新建尽调任务" />
    </main>

    <PlanDrawer v-model="planDrawerVisible" :task-id="store.currentTaskId" :plan="drawerPlan" />
    <EvidenceDrawer v-model="evidenceDrawerVisible" :evidence-id="selectedEvidenceId" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTaskStore } from '../stores/task'
import type { Id, PlanVO } from '../types/dd'
import TaskListPanel from '../components/TaskListPanel.vue'
import TaskHeader from '../components/TaskHeader.vue'
import OverviewPanel from '../components/OverviewPanel.vue'
import ExecutionPanel from '../components/ExecutionPanel.vue'
import ReportPanel from '../components/ReportPanel.vue'
import FindingPanel from '../components/FindingPanel.vue'
import SupplementPanel from '../components/SupplementPanel.vue'
import PlanPanel from '../components/PlanPanel.vue'
import EvidencePanel from '../components/EvidencePanel.vue'
import PlanDrawer from '../components/PlanDrawer.vue'
import EvidenceDrawer from '../components/EvidenceDrawer.vue'

const store = useTaskStore()
const taskId = computed(() => store.currentTaskId as Id)
const detail = computed(() => store.detail!)
const activeTab = ref('overview')
const planDrawerVisible = ref(false)
const drawerPlan = ref<PlanVO | null>(null)
const evidenceDrawerVisible = ref(false)
const selectedEvidenceId = ref<Id | null>(null)

function openEvidenceTab() {
  activeTab.value = 'archive'
}

function openPlan(plan: PlanVO | null) {
  drawerPlan.value = plan
  planDrawerVisible.value = true
}

function openEvidence(evidenceId?: Id) {
  selectedEvidenceId.value = evidenceId ?? null
  evidenceDrawerVisible.value = true
}
</script>

<style scoped>
.workbench {
  display: flex;
  height: 100vh;
  padding: 12px;
  gap: 12px;
}

.sidebar {
  width: 280px;
  flex: 0 0 280px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content {
  flex: 1;
  min-width: 0;
  overflow: auto;
}

.tabs {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 0 12px 12px;
}

.split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}
</style>

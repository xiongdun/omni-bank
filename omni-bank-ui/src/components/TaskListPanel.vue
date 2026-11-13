<template>
  <div class="panel-head">
    <span class="panel-title" style="margin: 0">尽调任务</span>
    <el-button type="primary" size="small" :icon="Plus" @click="createVisible = true">新建</el-button>
  </div>

  <div class="filters">
    <el-input
      v-model="store.query.taskNo"
      placeholder="任务编号 / 企业"
      clearable
      :prefix-icon="Search"
      @keyup.enter="store.searchTasks()"
      @clear="store.searchTasks()"
    />
    <el-select
      v-model="store.query.taskStatus"
      placeholder="全部状态"
      clearable
      @change="store.searchTasks()"
    >
      <el-option
        v-for="item in TASK_STATUS_OPTIONS"
        :key="item.value"
        :label="item.label"
        :value="item.value"
      />
    </el-select>
  </div>

  <div v-loading="store.listLoading" class="task-list">
    <div
      v-for="task in store.tasks"
      :key="task.taskId"
      class="task-card"
      :class="{ active: task.taskId === store.currentTaskId }"
      @click="select(task.taskId)"
    >
      <div class="row">
        <span class="task-no">{{ task.taskNo }}</span>
        <el-tag :type="taskStatusMeta(task.taskStatus).type" size="small" effect="light">
          {{ task.taskStatusDesc || taskStatusMeta(task.taskStatus).label }}
        </el-tag>
      </div>
      <div class="task-name">{{ task.taskName || '未命名尽调任务' }}</div>
      <div class="row meta">
        <span>{{ task.currentStage || '—' }}</span>
        <span>{{ clampProgress(task.progress) }}%</span>
      </div>
    </div>
    <el-empty v-if="!store.listLoading && store.tasks.length === 0" :image-size="60" description="暂无任务" />
  </div>

  <div class="pager">
    <el-pagination
      size="small"
      layout="prev, pager, next"
      :total="store.total"
      :page-size="store.query.size"
      :current-page="store.query.current"
      @current-change="store.changeTaskPage"
    />
  </div>

  <CreateTaskDialog v-model="createVisible" @created="onCreated" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { useTaskStore } from '../stores/task'
import type { Id } from '../types/dd'
import { clampProgress, taskStatusMeta, TASK_STATUS_OPTIONS } from '../utils/dd'
import CreateTaskDialog from './CreateTaskDialog.vue'

const store = useTaskStore()
const createVisible = ref(false)

async function select(taskId: Id) {
  await store.selectTask(taskId)
}

function onCreated(taskId: Id) {
  void taskId
}

onMounted(async () => {
  await store.fetchTasks()
  if (!store.currentTaskId && store.tasks.length > 0) {
    await store.selectTask(store.tasks[0].taskId)
  }
})
</script>

<style scoped>
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.task-list {
  flex: 1;
  overflow: auto;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.task-card {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
}

.task-card:hover {
  border-color: #c6e2ff;
}

.task-card.active {
  border-color: #409eff;
  background: #f5faff;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.task-no {
  font-weight: 600;
  font-size: 12px;
}

.task-name {
  color: #606266;
  margin: 4px 0;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  color: #909399;
  font-size: 12px;
}
</style>

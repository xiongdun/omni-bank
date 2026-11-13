import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { ddApi } from '../api/dd'
import { parseJson } from '../api/http'
import type { Id, ScenarioVO, TaskDetailVO, TaskListVO, TaskStatusVO } from '../types/dd'
import { isTerminalTaskStatus } from '../utils/dd'
import { shouldRetrySse, SSE_RETRY_DELAY_MS } from '../utils/sse'

export type SseState = 'idle' | 'connected' | 'reconnecting' | 'closed'

interface TaskState {
  tasks: TaskListVO[]
  total: number
  query: {
    taskNo: string
    taskStatus: string
    current: number
    size: number
  }
  listLoading: boolean
  currentTaskId: Id | null
  detail: TaskDetailVO | null
  status: TaskStatusVO | null
  detailLoading: boolean
  sseState: SseState
  /** 状态变化计数：面板据此刷新各自数据 */
  refreshTick: number
}

let source: EventSource | null = null
let retryTimer: ReturnType<typeof setTimeout> | null = null
let activeTaskId: Id | null = null

export const useTaskStore = defineStore('dd-task', {
  state: (): TaskState => ({
    tasks: [],
    total: 0,
    query: {
      taskNo: '',
      taskStatus: '',
      current: 1,
      size: 10
    },
    listLoading: false,
    currentTaskId: null,
    detail: null,
    status: null,
    detailLoading: false,
    sseState: 'idle',
    refreshTick: 0
  }),

  getters: {
    progress(state): number {
      return state.status?.progress ?? 0
    },
    currentStatus(state): string {
      return state.status?.taskStatus ?? state.detail?.taskStatus ?? ''
    }
  },

  actions: {
    async fetchTasks(showLoading = true) {
      if (showLoading) {
        this.listLoading = true
      }
      try {
        const page = await ddApi.pageTasks({
          current: this.query.current,
          size: this.query.size,
          taskNo: this.query.taskNo || undefined,
          taskStatus: this.query.taskStatus || undefined
        })
        this.tasks = page.records ?? []
        this.total = page.total ?? 0
      } catch {
        // 错误提示已由 http 层统一处理，保留当前列表
      } finally {
        this.listLoading = false
      }
    },

    searchTasks() {
      this.query.current = 1
      return this.fetchTasks()
    },

    changeTaskPage(page: number) {
      this.query.current = page
      return this.fetchTasks()
    },

    async createTask(body: Parameters<typeof ddApi.createTask>[0]) {
      const created = await ddApi.createTask(body)
      ElMessage.success(`任务已创建：${created.taskNo}`)
      await this.searchTasks()
      await this.selectTask(created.taskId)
      return created
    },

    async selectTask(taskId: Id) {
      if (this.currentTaskId === taskId) {
        return
      }
      this.disconnectSse()
      this.currentTaskId = taskId
      this.detail = null
      this.status = null
      await Promise.all([this.loadDetail(), this.loadStatus()])
      this.connectSse(taskId)
    },

    async loadDetail() {
      if (!this.currentTaskId) {
        return
      }
      this.detailLoading = true
      try {
        this.detail = await ddApi.taskDetail(this.currentTaskId)
      } catch {
        // 保留旧数据
      } finally {
        this.detailLoading = false
      }
    },

    async loadStatus() {
      if (!this.currentTaskId) {
        return
      }
      try {
        this.status = await ddApi.taskStatus(this.currentTaskId)
      } catch {
        // 保留旧数据
      }
    },

    /** 写操作后统一刷新：状态、详情、列表 */
    async refreshCurrent() {
      await Promise.all([this.loadStatus(), this.loadDetail(), this.fetchTasks(false)])
    },

    async submitTask() {
      if (!this.currentTaskId) {
        return
      }
      this.status = await ddApi.submitTask(this.currentTaskId)
      ElMessage.success('任务已提交，Agent 开始执行')
      await this.refreshCurrent()
    },

    async cancelTask() {
      if (!this.currentTaskId) {
        return
      }
      await ddApi.cancelTask(this.currentTaskId)
      ElMessage.success('任务已取消')
      await this.refreshCurrent()
      this.refreshTick += 1
    },

    async resumeTask() {
      if (!this.currentTaskId) {
        return
      }
      this.status = await ddApi.resumeTask(this.currentTaskId)
      ElMessage.success('已触发断点恢复')
      await this.refreshCurrent()
    },

    async reidentifyScenario(): Promise<ScenarioVO> {
      if (!this.currentTaskId) {
        throw new Error('未选择任务')
      }
      const scenario = await ddApi.reidentifyScenario(this.currentTaskId)
      if (this.detail) {
        this.detail = { ...this.detail, latestScenario: scenario }
      }
      ElMessage.success('已重新识别业务场景')
      return scenario
    },

    applyStatus(next: TaskStatusVO) {
      const previous = this.status?.taskStatus
      this.status = next
      if (previous !== next.taskStatus) {
        this.refreshTick += 1
        void this.loadDetail()
        void this.fetchTasks(false)
      }
      if (isTerminalTaskStatus(next.taskStatus)) {
        this.disconnectSse()
        this.sseState = 'closed'
        void this.loadDetail()
      }
    },

    connectSse(taskId: Id) {
      this.disconnectSse()
      activeTaskId = taskId
      this.sseState = 'reconnecting'
      this.openSse(taskId, 0)
    },

    openSse(taskId: Id, attempts: number) {
      if (activeTaskId !== taskId) {
        return
      }
      const next = new EventSource(`/api/v1/dd/tasks/${taskId}/events`)
      source = next
      next.onopen = () => {
        if (source !== next) {
          return
        }
        this.sseState = 'connected'
      }
      next.addEventListener('task-status', (event) => {
        if (source !== next) {
          return
        }
        try {
          const payload = parseJson((event as MessageEvent<string>).data) as TaskStatusVO
          if (payload && payload.taskStatus) {
            this.applyStatus(payload)
          }
        } catch {
          // 单条事件解析失败不影响后续事件
        }
      })
      next.onerror = () => {
        if (source !== next) {
          return
        }
        next.close()
        source = null
        if (activeTaskId !== taskId) {
          return
        }
        if (!shouldRetrySse(attempts)) {
          this.sseState = 'closed'
          ElMessage.warning('任务进度推送已中断，请手动刷新状态')
          return
        }
        this.sseState = 'reconnecting'
        retryTimer = setTimeout(() => {
          retryTimer = null
          if (activeTaskId !== taskId) {
            return
          }
          void this.loadStatus()
          this.openSse(taskId, attempts + 1)
        }, SSE_RETRY_DELAY_MS)
      }
    },

    disconnectSse() {
      activeTaskId = null
      if (retryTimer) {
        clearTimeout(retryTimer)
        retryTimer = null
      }
      if (source) {
        source.close()
        source = null
      }
      if (this.sseState !== 'idle') {
        this.sseState = 'idle'
      }
    }
  }
})

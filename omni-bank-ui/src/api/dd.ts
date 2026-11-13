import { apiGet, apiPost } from './http'
import type {
  AgentExecutionQuery,
  AgentExecutionVO,
  CompleteSupplementReq,
  CreateSupplementReq,
  CreateTaskReq,
  EvidenceDetailVO,
  EvidenceListVO,
  EvidenceQuery,
  EvidenceReferenceVO,
  FindingListVO,
  FindingQuery,
  Id,
  PageQuery,
  PageResult,
  PlanVO,
  ReportDetailVO,
  ReportQuery,
  ReportReviewReq,
  ReportReviewVO,
  ReportVersionVO,
  ScenarioVO,
  SupplementQuery,
  SupplementRequestVO,
  TaskContextVO,
  TaskCreateVO,
  TaskDetailVO,
  TaskListVO,
  TaskQuery,
  TaskStatusVO
} from '../types/dd'

const TASKS = '/v1/dd/tasks'

export const ddApi = {
  // ---------- 任务 ----------
  pageTasks: (params: TaskQuery) => apiGet<PageResult<TaskListVO>>(TASKS, params),
  createTask: (body: CreateTaskReq) => apiPost<TaskCreateVO>(TASKS, body),
  taskDetail: (taskId: Id) => apiGet<TaskDetailVO>(`${TASKS}/${taskId}`),
  taskStatus: (taskId: Id) => apiGet<TaskStatusVO>(`${TASKS}/${taskId}/status`),
  taskContext: (taskId: Id) => apiGet<TaskContextVO>(`${TASKS}/${taskId}/context`),
  submitTask: (taskId: Id) => apiPost<TaskStatusVO>(`${TASKS}/${taskId}/submit`),
  cancelTask: (taskId: Id) => apiPost<void>(`${TASKS}/${taskId}/cancel`),
  resumeTask: (taskId: Id) => apiPost<TaskStatusVO>(`${TASKS}/${taskId}/resume`),

  // ---------- 场景 ----------
  latestScenario: (taskId: Id) => apiGet<ScenarioVO>(`${TASKS}/${taskId}/scenario`),
  reidentifyScenario: (taskId: Id) => apiPost<ScenarioVO>(`${TASKS}/${taskId}/scenario/reidentify`),

  // ---------- 执行记录 ----------
  pageExecutions: (taskId: Id, params: AgentExecutionQuery) =>
    apiGet<PageResult<AgentExecutionVO>>(`${TASKS}/${taskId}/executions`, params),

  // ---------- 风险线索 ----------
  pageFindings: (taskId: Id, params: FindingQuery) =>
    apiGet<PageResult<FindingListVO>>(`${TASKS}/${taskId}/findings`, params),

  // ---------- 补充材料 ----------
  pageSupplements: (taskId: Id, params: SupplementQuery) =>
    apiGet<PageResult<SupplementRequestVO>>(`${TASKS}/${taskId}/supplements`, params),
  createSupplement: (taskId: Id, body: CreateSupplementReq) =>
    apiPost<SupplementRequestVO>(`${TASKS}/${taskId}/supplements`, body),
  completeSupplement: (taskId: Id, requestId: Id, body: CompleteSupplementReq) =>
    apiPost<SupplementRequestVO>(`${TASKS}/${taskId}/supplements/${requestId}/complete`, body),

  // ---------- 报告 ----------
  pageReports: (taskId: Id, params: ReportQuery) =>
    apiGet<PageResult<ReportVersionVO>>(`${TASKS}/${taskId}/reports`, params),
  reportDetail: (taskId: Id, reportVersion: number) =>
    apiGet<ReportDetailVO>(`${TASKS}/${taskId}/reports/${reportVersion}`),
  reviewReport: (taskId: Id, reportVersion: number, body: ReportReviewReq) =>
    apiPost<ReportDetailVO>(`${TASKS}/${taskId}/reports/${reportVersion}/review`, body),
  reportReviews: (taskId: Id, reportVersion: number) =>
    apiGet<ReportReviewVO[]>(`${TASKS}/${taskId}/reports/${reportVersion}/reviews`),

  // ---------- 计划 ----------
  /** 任务尚未生成计划时后端返回 PLAN_NOT_FOUND，静默处理避免无意义提示 */
  currentPlan: (taskId: Id) => apiGet<PlanVO>(`${TASKS}/${taskId}/plans/current`, undefined, true),
  pagePlans: (taskId: Id, params: PageQuery) =>
    apiGet<PageResult<PlanVO>>(`${TASKS}/${taskId}/plans`, params),
  planDetail: (planId: Id) => apiGet<PlanVO>(`/v1/dd/plans/${planId}`),

  // ---------- 证据 ----------
  pageEvidences: (taskId: Id, params: EvidenceQuery) =>
    apiGet<PageResult<EvidenceListVO>>(`${TASKS}/${taskId}/evidences`, params),
  evidenceDetail: (evidenceId: Id) => apiGet<EvidenceDetailVO>(`/v1/dd/evidences/${evidenceId}`),
  evidenceReferences: (evidenceId: Id) =>
    apiGet<EvidenceReferenceVO>(`/v1/dd/evidences/${evidenceId}/references`)
}

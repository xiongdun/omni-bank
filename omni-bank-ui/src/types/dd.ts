/**
 * 尽调域前端类型：与 agent 域 Dd* DTO/VO 对齐。
 * 雪花 ID 由 json-bigint 以字符串返回，统一用 Id（string）表示，避免 JS Number 精度丢失。
 */
export type Id = string

export interface PageQuery {
  current?: number
  size?: number
}

export interface PageResult<T> {
  total: number
  current: number
  size: number
  pages: number
  records: T[]
}

export interface EnterpriseVO {
  id?: Id
  customerId?: string
  creditCode?: string
  enterpriseName?: string
  legalPerson?: string
  registeredAddress?: string
  businessScope?: string
  registeredCapital?: number
  establishmentDate?: string
  businessStatus?: string
  industryCode?: string
  industryName?: string
  dataSource?: string
  lastSyncAt?: string
}

export interface LoanApplicationVO {
  id?: Id
  applicationNo?: string
  applicationType?: string
  productCode?: string
  productName?: string
  applyAmount?: number
  currency?: string
  applyTerm?: number
  termUnit?: string
  loanPurpose?: string
  repaymentMethod?: string
  guaranteeType?: string
}

export interface ScenarioVO {
  id?: Id
  scenarioVersion?: number
  businessType?: string
  businessTypeDesc?: string
  productType?: string
  industryCode?: string
  industryName?: string
  loanPurposeType?: string
  financingMode?: string
  guaranteeTypes?: string[]
  confidence?: number
  identifyStatus?: string
  scenarioDetail?: Record<string, unknown>
  evidenceIds?: string[]
  uncertainItems?: string[]
  modelName?: string
  promptVersion?: string
  createTime?: string
}

export interface TaskListVO {
  taskId: Id
  taskNo: string
  taskName?: string
  taskStatus: string
  taskStatusDesc?: string
  currentStage?: string
  progress?: number
  enterpriseId?: Id
  applicationId?: Id
  createTime?: string
  updateTime?: string
}

export interface TaskDetailVO {
  id: Id
  taskNo: string
  taskName?: string
  taskStatus: string
  taskStatusDesc?: string
  currentStage?: string
  contextVersion?: number
  analysisVersion?: number
  lastErrorCode?: string
  lastErrorMessage?: string
  submittedAt?: string
  completedAt?: string
  createTime?: string
  version?: number
  enterprise?: EnterpriseVO
  application?: LoanApplicationVO
  latestScenario?: ScenarioVO
}

export interface TaskStatusVO {
  taskId: Id
  taskNo: string
  taskStatus: string
  taskStatusDesc?: string
  currentStage?: string
  contextVersion?: number
  analysisVersion?: number
  progress?: number
  lastErrorCode?: string
  lastErrorMessage?: string
  updateTime?: string
}

export interface TaskContextVO {
  taskId: Id
  enterpriseId?: Id
  customerId?: string
  creditCode?: string
  contextVersion?: number
  analysisVersion?: number
  enterpriseInfo?: EnterpriseVO
  loanApplication?: LoanApplicationVO
  businessScenario?: ScenarioVO
  evidenceIds?: string[]
}

export interface EnterpriseReq {
  creditCode: string
  customerId?: string
  enterpriseName?: string
}

export interface LoanApplicationReq {
  applicationNo?: string
  applicationType?: string
  productCode?: string
  productName?: string
  applyAmount: number
  currency?: string
  applyTerm: number
  termUnit?: string
  loanPurpose?: string
  repaymentMethod?: string
  guaranteeType?: string
}

export interface CreateTaskReq {
  enterprise: EnterpriseReq
  application: LoanApplicationReq
  taskName?: string
}

export interface TaskCreateVO {
  taskId: Id
  taskNo: string
  taskStatus: string
}

export interface TaskQuery extends PageQuery {
  taskNo?: string
  taskStatus?: string
  enterpriseId?: Id
}

export interface AgentExecutionVO {
  executionId: Id
  taskId: Id
  planId?: Id
  graphCode?: string
  nodeId?: string
  agentCode?: string
  executionNo?: number
  executionStatus?: string
  summary?: string
  errorMessage?: string
  modelVersion?: string
  promptVersion?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
}

export interface AgentExecutionQuery extends PageQuery {
  executionStatus?: string
  nodeId?: string
}

export interface EvidenceRef {
  evidenceId: string
  evidenceType?: string
  sourceSystem?: string
  sourceReference?: string
  dataPeriod?: string
  excerpt?: string
}

export interface FindingListVO {
  findingId: Id
  taskId: Id
  planId?: Id
  executionId?: Id
  agentCode?: string
  nodeId?: string
  findingCode?: string
  category?: string
  severity?: string
  title?: string
  description?: string
  confidence?: number
  status?: string
  evidenceRefs?: EvidenceRef[]
  relatedFindingCodes?: string[]
  verificationSuggestion?: string
  createTime?: string
}

export interface FindingQuery extends PageQuery {
  agentCode?: string
  category?: string
  severity?: string
  status?: string
}

export interface SupplementRequestVO {
  requestId: Id
  taskId: Id
  planId?: Id
  executionId?: Id
  agentCode?: string
  dataCode?: string
  dataName?: string
  reason?: string
  suggestedAction?: string
  status?: string
  source?: string
  remark?: string
  completedAt?: string
  createTime?: string
}

export interface SupplementQuery extends PageQuery {
  agentCode?: string
  status?: string
  source?: string
}

export interface CreateSupplementReq {
  dataCode: string
  dataName?: string
  reason?: string
  suggestedAction?: string
  agentCode?: string
}

export interface CompleteSupplementReq {
  remark?: string
}

export interface ReportVersionVO {
  id: Id
  taskId: Id
  reportNo?: string
  reportVersion?: number
  reportStatus?: string
  reviewStatus?: string
  title?: string
  evidenceCount?: number
  dataGapCount?: number
  supplementCount?: number
  exportStatus?: string
  modelVersion?: string
  promptVersion?: string
  generatedAt?: string
}

export interface ReportChapter {
  chapterNo?: number
  chapterCode?: string
  title?: string
  content?: string
  status?: string
  evidenceIds?: string[]
  dataGaps?: string[]
  limitation?: string
  [key: string]: unknown
}

export interface ReportDetailVO extends ReportVersionVO {
  chapters?: ReportChapter[]
  evidenceIds?: string[]
}

export interface ReportQuery extends PageQuery {
  reportStatus?: string
}

export type ReportReviewAction =
  | 'SUBMIT'
  | 'CONFIRM'
  | 'SUPPLEMENT'
  | 'AMEND'
  | 'REJECT'
  | 'REQUEST_MATERIAL'
  | 'REANALYZE'
  | 'FINALIZE'

export interface ReportReviewReq {
  actionType: ReportReviewAction
  fieldKey?: string
  amendedValue?: string
  reason?: string
  remark?: string
  evidenceIds?: string[]
}

export interface ReportReviewVO {
  id: Id
  actionType?: string
  fromStatus?: string
  toStatus?: string
  fieldKey?: string
  originalValue?: string
  amendedValue?: string
  reviewer?: string
  reason?: string
  remark?: string
  evidenceIds?: string[]
  createTime?: string
}

export interface PlanVO {
  planId: Id
  taskId: Id
  planVersion?: number
  plannerMode?: string
  businessScenario?: string
  subScenario?: string
  confidence?: number
  reasoningSummary?: string
  agentPlan?: string[]
  dataPlan?: unknown
  materialPlan?: unknown
  dependencyPlan?: Record<string, string[]>
  supplementPlan?: unknown
  executionConfig?: unknown
  modelName?: string
  promptVersion?: string
  createTime?: string
}

export interface EvidenceListVO {
  evidenceId: Id
  taskId: Id
  evidenceType?: string
  sourceType?: string
  sourceSystem?: string
  collectedAt?: string
  isMock?: boolean
  contentSummary?: string
  status?: string
}

export interface EvidenceQuery extends PageQuery {
  evidenceType?: string
  sourceType?: string
  sourceSystem?: string
}

export interface EvidenceDetailVO extends EvidenceListVO {
  enterpriseId?: Id
  sourceReference?: string
  subjectId?: string
  dataPeriod?: string
  contentHash?: string
  confidence?: number
}

export interface EvidenceReferenceVO {
  evidenceId: Id
  taskId: Id
  findings?: FindingListVO[]
  reports?: ReportVersionVO[]
  reviews?: ReportReviewVO[]
}

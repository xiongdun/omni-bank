/** 尽调域展示映射：状态标签、Agent 名称、时间格式化等纯函数，便于单测覆盖 */

export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export interface StatusMeta {
  label: string
  type: TagType
}

export const AGENT_LABELS: Record<string, string> = {
  SUBJECT_VERIFICATION: '主体信息核验',
  FINANCIAL_ANALYSIS: '财务分析',
  JUDICIAL_RISK: '司法风险',
  CREDIT_DEBT_ANALYSIS: '征信债务',
  SITE_VISIT: '现场调查',
  DOCUMENT_RECOGNITION: '材料识别',
  INDUSTRY_EXPERT: '行业专家',
  LOAN_PURPOSE_ANALYSIS: '贷款用途',
  FRAUD_DETECTION: '反欺诈',
  POLICY_MATCHING: '政策匹配',
  COMPREHENSIVE_RISK_ASSESSMENT: '综合风险',
  DUE_DILIGENCE_REPORT: '尽调报告'
}

/** Agent 展示顺序与后端 DdAgentCatalog 保持一致 */
export const AGENT_CODES = Object.keys(AGENT_LABELS)

export function agentLabel(code?: string | null): string {
  if (!code) {
    return '汇聚节点'
  }
  return AGENT_LABELS[code] ?? code
}

const TASK_STATUS_META: Record<string, StatusMeta> = {
  DRAFT: { label: '草稿', type: 'info' },
  INITIALIZING: { label: '初始化', type: 'primary' },
  SCENARIO_IDENTIFYING: { label: '场景识别', type: 'primary' },
  PLANNING: { label: '生成计划', type: 'primary' },
  DATA_COLLECTING: { label: '数据采集', type: 'primary' },
  ANALYZING: { label: 'Agent 分析', type: 'primary' },
  WAITING_FOR_SUPPLEMENT: { label: '等待补充材料', type: 'warning' },
  REANALYZING: { label: '增量重分析', type: 'warning' },
  RISK_ASSESSING: { label: '综合风险评估', type: 'primary' },
  REPORT_GENERATING: { label: '生成报告', type: 'primary' },
  WAITING_FOR_REVIEW: { label: '等待人工复核', type: 'warning' },
  RETURNED: { label: '退回', type: 'danger' },
  COMPLETED: { label: '复核完成', type: 'success' },
  FAILED: { label: '任务失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const TASK_STATUS_OPTIONS = Object.entries(TASK_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label
}))

export function taskStatusMeta(status?: string | null): StatusMeta {
  return TASK_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

const TERMINAL_TASK_STATUS = new Set(['COMPLETED', 'FAILED', 'CANCELLED'])

export function isTerminalTaskStatus(status?: string | null): boolean {
  return TERMINAL_TASK_STATUS.has(status ?? '')
}

export function canCancelTask(status?: string | null): boolean {
  return !!status && !isTerminalTaskStatus(status)
}

const EXECUTION_STATUS_META: Record<string, StatusMeta> = {
  PENDING: { label: '待执行', type: 'info' },
  RUNNING: { label: '执行中', type: 'primary' },
  SUCCEEDED: { label: '执行成功', type: 'success' },
  FAILED: { label: '执行失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const EXECUTION_STATUS_OPTIONS = Object.entries(EXECUTION_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label
}))

export function executionStatusMeta(status?: string | null): StatusMeta {
  return EXECUTION_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

const REPORT_STATUS_META: Record<string, StatusMeta> = {
  DRAFT: { label: '草稿', type: 'info' },
  AI_GENERATED: { label: 'AI 生成完成', type: 'primary' },
  WAITING_FOR_REVIEW: { label: '等待人工复核', type: 'warning' },
  RETURNED_FOR_SUPPLEMENT: { label: '退回补充材料', type: 'danger' },
  REANALYZING: { label: '重新分析中', type: 'warning' },
  REVIEWED: { label: '复核通过', type: 'success' },
  FINALIZED: { label: '已定稿', type: 'success' }
}

export function reportStatusMeta(status?: string | null): StatusMeta {
  return REPORT_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

const REPORT_REVIEW_STATUS_META: Record<string, StatusMeta> = {
  NOT_SUBMITTED: { label: '未提交复核', type: 'info' },
  PENDING_MANUAL_REVIEW: { label: '等待人工复核', type: 'warning' },
  RETURNED_FOR_SUPPLEMENT: { label: '已退回补充', type: 'danger' },
  REANALYZING: { label: '重新分析中', type: 'warning' },
  REVIEWED: { label: '复核通过', type: 'success' },
  FINALIZED: { label: '已定稿', type: 'success' }
}

export function reportReviewStatusMeta(status?: string | null): StatusMeta {
  return REPORT_REVIEW_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

export const REPORT_ACTION_LABELS: Record<string, string> = {
  SUBMIT: '提交复核',
  CONFIRM: '复核确认',
  SUPPLEMENT: '补充说明',
  AMEND: '修正章节',
  REJECT: '复核驳回',
  REQUEST_MATERIAL: '要求补充材料',
  REANALYZE: '要求重新分析',
  FINALIZE: '定稿'
}

/** 需要填写原因的动作（与后端校验一致） */
export const REPORT_ACTION_REQUIRES_REASON = new Set(['AMEND', 'REJECT', 'REQUEST_MATERIAL', 'REANALYZE'])

const SUPPLEMENT_STATUS_META: Record<string, StatusMeta> = {
  PENDING: { label: '待补充', type: 'warning' },
  COMPLETED: { label: '已补充', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const SUPPLEMENT_STATUS_OPTIONS = Object.entries(SUPPLEMENT_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label
}))

export function supplementStatusMeta(status?: string | null): StatusMeta {
  return SUPPLEMENT_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

export function supplementSourceLabel(source?: string | null): string {
  if (source === 'AGENT') {
    return 'Agent 提出'
  }
  if (source === 'MANUAL') {
    return '手工创建'
  }
  return source || '-'
}

const SCENARIO_STATUS_META: Record<string, StatusMeta> = {
  IDENTIFIED: { label: '已识别', type: 'success' },
  NEEDS_CONFIRMATION: { label: '待人工确认', type: 'warning' },
  CONFIRMED_BY_USER: { label: '人工已确认', type: 'success' },
  FAILED: { label: '识别失败', type: 'danger' }
}

export function scenarioStatusMeta(status?: string | null): StatusMeta {
  return SCENARIO_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

const SEVERITY_META: Record<string, StatusMeta> = {
  HIGH: { label: '高', type: 'danger' },
  MEDIUM: { label: '中', type: 'warning' },
  LOW: { label: '低', type: 'info' },
  UNKNOWN: { label: '未知', type: 'info' }
}

export const SEVERITY_OPTIONS = Object.entries(SEVERITY_META).map(([value, meta]) => ({
  value,
  label: meta.label
}))

export function severityMeta(severity?: string | null): StatusMeta {
  return SEVERITY_META[severity ?? ''] ?? { label: severity || '未知', type: 'info' }
}

const FINDING_STATUS_META: Record<string, StatusMeta> = {
  CONFIRMED: { label: '已确认', type: 'danger' },
  NEEDS_VERIFICATION: { label: '待核实', type: 'warning' },
  DISMISSED: { label: '已排除', type: 'info' }
}

export const FINDING_STATUS_OPTIONS = Object.entries(FINDING_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label
}))

export function findingStatusMeta(status?: string | null): StatusMeta {
  return FINDING_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

const EVIDENCE_STATUS_META: Record<string, StatusMeta> = {
  ACTIVE: { label: '有效', type: 'success' },
  SUPERSEDED: { label: '已被替代', type: 'info' },
  INVALID: { label: '已失效', type: 'danger' }
}

export function evidenceStatusMeta(status?: string | null): StatusMeta {
  return EVIDENCE_STATUS_META[status ?? ''] ?? { label: status || '未知', type: 'info' }
}

export const EVIDENCE_TYPE_LABELS: Record<string, string> = {
  ENTERPRISE_PROFILE: '企业主体信息',
  FINANCIAL_STATEMENT: '财务报表',
  CREDIT_REPORT: '征信报告',
  JUDICIAL_RECORD: '司法记录',
  SITE_INVESTIGATION: '现场调查',
  CUSTOMER_DOCUMENT: '客户上传材料',
  CALCULATION: '系统计算结果',
  POLICY_DOCUMENT: '制度文件',
  INDUSTRY_RESEARCH: '行业研究',
  MANUAL_INPUT: '人工录入'
}

export const EVIDENCE_SOURCE_LABELS: Record<string, string> = {
  BANK_INTERFACE: '银行接口',
  EXTERNAL_DATA: '外部数据接口',
  CUSTOMER_UPLOAD: '客户上传文件',
  OCR: 'OCR 结果',
  FINANCIAL_CALCULATION: '财务计算结果',
  INDUSTRY_RESEARCH: '行业研究资料',
  POLICY_DOCUMENT: '银行制度文件',
  MANUAL_INVESTIGATION: '人工调查记录'
}

export const EVIDENCE_TYPE_OPTIONS = Object.entries(EVIDENCE_TYPE_LABELS).map(([value, label]) => ({
  value,
  label
}))

export const EVIDENCE_SOURCE_OPTIONS = Object.entries(EVIDENCE_SOURCE_LABELS).map(([value, label]) => ({
  value,
  label
}))

export function evidenceTypeLabel(type?: string | null): string {
  if (!type) {
    return '-'
  }
  return EVIDENCE_TYPE_LABELS[type] ?? type
}

export function evidenceSourceLabel(type?: string | null): string {
  if (!type) {
    return '-'
  }
  return EVIDENCE_SOURCE_LABELS[type] ?? type
}

/** 后端 LocalDateTime（ISO-8601，无时区）转 yyyy-MM-dd HH:mm:ss */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

export function formatDuration(ms?: number | null): string {
  if (ms === null || ms === undefined) {
    return '-'
  }
  if (ms < 1000) {
    return `${ms} ms`
  }
  return `${(ms / 1000).toFixed(1)} s`
}

export function clampProgress(value?: number | null): number {
  if (!value || value < 0) {
    return 0
  }
  return Math.min(value, 100)
}

/** 雪花 ID 只展示后 8 位，避免卡片过宽 */
export function shortId(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.length > 8 ? `…${value.slice(-8)}` : value
}

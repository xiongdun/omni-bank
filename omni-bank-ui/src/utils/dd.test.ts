import { describe, expect, it } from 'vitest'
import {
  agentLabel,
  clampProgress,
  evidenceTypeLabel,
  formatDateTime,
  formatDuration,
  isTerminalTaskStatus,
  severityMeta,
  shortId,
  taskStatusMeta
} from './dd'

describe('状态标签映射', () => {
  it('已知任务状态返回中文标签与标签类型', () => {
    expect(taskStatusMeta('ANALYZING')).toEqual({ label: 'Agent 分析', type: 'primary' })
    expect(taskStatusMeta('COMPLETED')).toEqual({ label: '复核完成', type: 'success' })
  })

  it('未知状态回退原值且不影响渲染', () => {
    expect(taskStatusMeta('SOMETHING_NEW')).toEqual({ label: 'SOMETHING_NEW', type: 'info' })
    expect(taskStatusMeta(null)).toEqual({ label: '未知', type: 'info' })
  })

  it('终态判定只包含完成/失败/取消', () => {
    expect(isTerminalTaskStatus('COMPLETED')).toBe(true)
    expect(isTerminalTaskStatus('FAILED')).toBe(true)
    expect(isTerminalTaskStatus('CANCELLED')).toBe(true)
    expect(isTerminalTaskStatus('ANALYZING')).toBe(false)
    expect(isTerminalTaskStatus(undefined)).toBe(false)
  })

  it('风险等级与证据类型映射', () => {
    expect(severityMeta('HIGH')).toEqual({ label: '高', type: 'danger' })
    expect(severityMeta('LOW')).toEqual({ label: '低', type: 'info' })
    expect(evidenceTypeLabel('CREDIT_REPORT')).toBe('征信报告')
    expect(evidenceTypeLabel('UNMAPPED')).toBe('UNMAPPED')
  })

  it('Agent 名称未知时回退编码', () => {
    expect(agentLabel('FINANCIAL_ANALYSIS')).toBe('财务分析')
    expect(agentLabel('NEW_AGENT')).toBe('NEW_AGENT')
    expect(agentLabel(null)).toBe('汇聚节点')
  })
})

describe('格式化工具', () => {
  it('时间格式化为 yyyy-MM-dd HH:mm:ss', () => {
    expect(formatDateTime('2026-11-13T10:20:30')).toBe('2026-11-13 10:20:30')
    expect(formatDateTime('2026-11-13T10:20:30.123')).toBe('2026-11-13 10:20:30')
    expect(formatDateTime(null)).toBe('-')
  })

  it('耗时与进度展示', () => {
    expect(formatDuration(800)).toBe('800 ms')
    expect(formatDuration(1500)).toBe('1.5 s')
    expect(formatDuration(null)).toBe('-')
    expect(clampProgress(120)).toBe(100)
    expect(clampProgress(-5)).toBe(0)
    expect(clampProgress(undefined)).toBe(0)
  })

  it('雪花 ID 截断展示', () => {
    expect(shortId('1958000000000000001')).toBe('…00000001')
    expect(shortId('123')).toBe('123')
    expect(shortId(null)).toBe('-')
  })
})

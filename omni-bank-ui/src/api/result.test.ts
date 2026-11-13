import { describe, expect, it } from 'vitest'
import { BizError, SUCCESS_CODE, unwrapResult } from './result'

describe('unwrapResult', () => {
  it('成功码返回 data', () => {
    expect(unwrapResult({ code: SUCCESS_CODE, msg: 'ok', data: { value: 1 } })).toEqual({ value: 1 })
  })

  it('业务失败抛出 BizError 并保留错误码', () => {
    try {
      unwrapResult({ code: '230102', msg: '任务状态不允许', data: null })
      expect.unreachable('应抛出 BizError')
    } catch (error) {
      expect(error).toBeInstanceOf(BizError)
      expect((error as BizError).code).toBe('230102')
      expect((error as BizError).message).toBe('任务状态不允许')
    }
  })

  it('空响应或格式异常给出统一错误', () => {
    expect(() => unwrapResult(null)).toThrowError('服务返回格式异常')
    expect(() => unwrapResult({} as never)).toThrowError('服务返回格式异常')
  })
})

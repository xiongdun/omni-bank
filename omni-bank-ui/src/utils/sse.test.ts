import { describe, expect, it } from 'vitest'
import { SSE_MAX_RETRIES, shouldRetrySse } from './sse'

describe('SSE 重连策略', () => {
  it('未达上限继续重连', () => {
    expect(shouldRetrySse(0)).toBe(true)
    expect(shouldRetrySse(SSE_MAX_RETRIES - 1)).toBe(true)
  })

  it('达到上限停止重连', () => {
    expect(shouldRetrySse(SSE_MAX_RETRIES)).toBe(false)
    expect(shouldRetrySse(SSE_MAX_RETRIES + 1)).toBe(false)
  })

  it('支持自定义上限', () => {
    expect(shouldRetrySse(2, 2)).toBe(false)
    expect(shouldRetrySse(1, 2)).toBe(true)
  })
})

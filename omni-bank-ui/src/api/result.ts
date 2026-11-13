/** 后端统一响应：000200 成功 */
export interface Result<T> {
  code: string
  msg: string
  data: T
}

export const SUCCESS_CODE = '000200'

export class BizError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}

export function unwrapResult<T>(body: Result<T> | null | undefined): T {
  if (!body || typeof body.code !== 'string') {
    throw new BizError('INVALID_RESPONSE', '服务返回格式异常')
  }
  if (body.code !== SUCCESS_CODE) {
    throw new BizError(body.code, body.msg || '请求失败')
  }
  return body.data
}

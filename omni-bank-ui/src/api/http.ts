import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import JSONbig from 'json-bigint'
import { ElMessage } from 'element-plus'
import { BizError, unwrapResult, type Result } from './result'

/** agent 域不做鉴权，固定审计头仅用于留痕 */
export const AUDIT_HEADERS = {
  'X-Username': 'admin',
  'X-User-Id': '1'
}

const jsonBig = JSONbig({ storeAsString: true })

/** 雪花 ID 超出 JS 安全整数，统一用 json-bigint 解析（大整数转字符串） */
export function parseJson(text: string): unknown {
  try {
    return jsonBig.parse(text)
  } catch {
    return text
  }
}

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
  headers: AUDIT_HEADERS,
  transformResponse: [
    (data: unknown) => (typeof data === 'string' ? parseJson(data) : data)
  ]
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (!error.response) {
      ElMessage.error('服务不可用，请确认 agent 服务（8109）已启动')
    } else if (error.response.status >= 500) {
      ElMessage.error(`服务异常（${error.response.status}）`)
    }
    return Promise.reject(error)
  }
)

async function request<T>(config: AxiosRequestConfig, silent = false): Promise<T> {
  const response = await http.request<Result<T>>(config)
  try {
    return unwrapResult(response.data)
  } catch (error) {
    if (!silent && error instanceof BizError) {
      ElMessage.error(error.message)
    }
    throw error
  }
}

export function apiGet<T>(url: string, params?: unknown, silent = false): Promise<T> {
  return request<T>({ method: 'GET', url, params }, silent)
}

export function apiPost<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'POST', url, data })
}

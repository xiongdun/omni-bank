/** SSE 断线重连策略：固定 3s 退避，最多重试 5 次，超限后转人工刷新 */
export const SSE_MAX_RETRIES = 5
export const SSE_RETRY_DELAY_MS = 3000

export function shouldRetrySse(attempts: number, max = SSE_MAX_RETRIES): boolean {
  return attempts < max
}

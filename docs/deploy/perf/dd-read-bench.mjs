// 无 k6 环境的兜底读接口压测脚本（Node 18+ 内置 fetch，无第三方依赖）
// 运行：TASK_ID=<任务ID> node docs/deploy/perf/dd-read-bench.mjs
// 环境变量：BASE_URL / TASK_ID / CONCURRENCY(默认20) / TOTAL(默认400)
const BASE_URL = process.env.BASE_URL || 'http://localhost:8109';
const TASK_ID = process.env.TASK_ID || '';
const CONCURRENCY = Number(process.env.CONCURRENCY || 20);
const TOTAL = Number(process.env.TOTAL || 400);

const HEADERS = {
  'X-Username': process.env.USERNAME || 'admin',
  'X-User-Id': process.env.USER_ID || '1',
};

const paths = ['/v1/dd/tasks?current=1&size=10'];
if (TASK_ID) {
  paths.push(`/v1/dd/tasks/${TASK_ID}`, `/v1/dd/tasks/${TASK_ID}/status`);
  paths.push(`/v1/dd/tasks/${TASK_ID}/executions?current=1&size=10`);
}

const latencies = [];
let failures = 0;
let issued = 0;

async function once() {
  const path = paths[issued % paths.length];
  issued += 1;
  const started = performance.now();
  try {
    const res = await fetch(BASE_URL + path, { headers: HEADERS });
    const body = await res.json();
    if (!res.ok || body.code !== '000200') failures += 1;
  } catch {
    failures += 1;
  } finally {
    latencies.push(performance.now() - started);
  }
}

async function worker() {
  while (issued < TOTAL) {
    await once();
  }
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

const startedAt = performance.now();
await Promise.all(Array.from({ length: CONCURRENCY }, () => worker()));
const cost = performance.now() - startedAt;
const sorted = latencies.slice().sort((a, b) => a - b);

console.log(JSON.stringify({
  baseUrl: BASE_URL,
  concurrency: CONCURRENCY,
  requests: latencies.length,
  failures,
  failureRate: Number((failures / latencies.length).toFixed(4)),
  qps: Number((latencies.length / (cost / 1000)).toFixed(1)),
  avgMs: Number((latencies.reduce((a, b) => a + b, 0) / latencies.length).toFixed(1)),
  p50Ms: Number(percentile(sorted, 50).toFixed(1)),
  p95Ms: Number(percentile(sorted, 95).toFixed(1)),
  p99Ms: Number(percentile(sorted, 99).toFixed(1)),
  maxMs: Number(sorted[sorted.length - 1].toFixed(1)),
}, null, 2));

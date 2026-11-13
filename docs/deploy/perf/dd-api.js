// 尽调 Agent 接口压测脚本（k6）
// 运行：k6 run -e BASE_URL=http://localhost:8109 -e TASK_ID=<任务ID> docs/deploy/perf/dd-api.js
// 说明：只压读接口（分页/状态/详情）；创建任务会触发 Graph 全链路调度，不纳入常规压测。
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8109';
const TASK_ID = __ENV.TASK_ID || '';

// agent 域本期不做鉴权，仍按审计头约定携带操作人
const HEADERS = {
  'X-Username': __ENV.USERNAME || 'admin',
  'X-User-Id': __ENV.USER_ID || '1',
};

export const options = {
  scenarios: {
    read_paths: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '60s', target: 20 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    // 对应 REQUIREMENTS.md：常规分页查询 P95 < 500ms
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

function assertResult(res, name) {
  check(res, {
    [`${name} HTTP 200`]: (r) => r.status === 200,
    [`${name} 业务码成功`]: (r) => r.json('code') === '000200',
  });
}

export default function () {
  assertResult(http.get(`${BASE_URL}/v1/dd/tasks?current=1&size=10`, { headers: HEADERS }), '任务分页');

  if (TASK_ID) {
    assertResult(http.get(`${BASE_URL}/v1/dd/tasks/${TASK_ID}`, { headers: HEADERS }), '任务详情');
    assertResult(http.get(`${BASE_URL}/v1/dd/tasks/${TASK_ID}/status`, { headers: HEADERS }), '任务状态');
    assertResult(
      http.get(`${BASE_URL}/v1/dd/tasks/${TASK_ID}/executions?current=1&size=10`, { headers: HEADERS }),
      '执行记录',
    );
  }

  sleep(1);
}

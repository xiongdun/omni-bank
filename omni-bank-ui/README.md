# omni-bank-ui · 尽调工作台

对公信贷尽调 Agent 的可视化工作台，独立于 Maven 聚合构建的前端工程。

- 技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + axios
- 后端：`omni-bank-service-agent`（端口 8109），接口前缀 `/v1/dd`
- 覆盖场景：任务列表与详情、Agent 执行进度（SSE）、报告查看与人工复核、风险线索、补充材料、尽调计划与证据溯源、业务场景重新识别

## 快速开始

```bash
# 需要 Node 20+（本工程验证环境 Node 24）
npm install
npm run dev      # http://localhost:5173/workbench
npm run build    # vue-tsc 类型检查 + 产物输出到 dist/
npm test         # Vitest（Result 解包、状态映射、SSE 重连策略）
```

启动前请确认 agent 服务已运行在 `http://localhost:8109`，且已执行 `docs/sql/*.sql` 建库。

## 开发代理

[vite.config.ts](vite.config.ts) 将 `/api` 代理到 `http://localhost:8109` 并剥离 `/api` 前缀，与网关 `StripPrefix=1` 行为一致：

- 浏览器请求 `/api/v1/dd/tasks`
- 实际转发 `http://localhost:8109/v1/dd/tasks`

生产部署时将 `dist/` 交由静态服务器托管，并把 `/api` 反向代理到网关或 agent 服务即可。

## 约定

- **统一响应**：后端 `Result<T>`，`code === "000200"` 视为成功，其余抛出 `BizError` 并展示后端 `msg`；网络异常提示确认 8109 是否启动。
- **雪花 ID**：ID 超出 JS 安全整数，使用 `json-bigint`（`storeAsString`）解析，类型统一为 `string`。
- **审计头**：agent 域不做鉴权，axios 统一携带 `X-Username: admin`、`X-User-Id: 1` 仅用于审计留痕。
- **SSE**：订阅 `/v1/dd/tasks/{taskId}/events` 的 `task-status` 事件更新进度；断线按 3s 间隔重连，最多 5 次，终态或切换任务时主动关闭。
- **刷新策略**：任务状态变化（`refreshTick`）或写操作成功后，各面板按需刷新自身数据。

## 目录结构

```
src/
├── api/http.ts          # axios 实例、Result 解包、审计头、json-bigint
├── api/dd.ts            # 尽调接口封装
├── types/dd.ts          # 与后端 DTO/VO 对齐的类型
├── utils/dd.ts          # 状态标签、Agent 名称、格式化等纯函数
├── utils/sse.ts         # SSE 重连策略
├── stores/task.ts       # 任务列表、当前任务、SSE 连接
├── views/Workbench.vue  # 左右分栏主页面
└── components/          # 任务列表、头部、概览、执行、报告、线索、补充、计划、证据及抽屉
```

接口路径与字段以 agent 域 `Dd*Controller` 及对应 DTO/VO 为准，后端调整时同步更新 `src/types/dd.ts` 与 `src/api/dd.ts`。

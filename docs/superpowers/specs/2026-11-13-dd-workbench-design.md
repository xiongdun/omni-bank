# 尽调工作台前端设计（P8）

日期：2026-11-13
状态：已确认（用户批准方案 A：左右分栏工作台）

## 1. 目标与范围

为 omni-bank 尽调 Agent 提供可视化管理端，覆盖信审人员日常操作：查看任务、观察 Agent 执行、审阅报告、处理补充材料、追溯证据。

首版包含：

- 任务列表（搜索、状态筛选、分页）与任务详情
- Agent 执行进度（SSE 实时刷新）
- 报告版本查看与人工复核（提交/确认/定稿/驳回/要求重分析、字段级修正）
- 风险线索分页与筛选；补充材料创建与完成（完成后触发增量重分析）
- 计划查看（当前计划、版本分页、计划详情）与证据溯源
- 业务场景查看与重新识别

不做（明确排除）：登录页、报告 Word/PDF 导出、真实外部数据接口、大模型接入。

## 2. 技术选型

| 项 | 选择 |
|---|---|
| 框架 | Vue 3 + TypeScript |
| 构建 | Vite |
| UI | Element Plus |
| 状态 | Pinia |
| 路由 | Vue Router |
| 请求 | axios |

工程位置：仓库根目录新增 `omni-bank-ui/`，与 Java 模块解耦，独立构建部署。

## 3. 架构与目录

```
omni-bank-ui/
├── index.html
├── vite.config.ts          # /api 代理到 http://localhost:8109，剥离 /api 前缀
├── tsconfig.json
├── package.json
└── src/
    ├── main.ts
    ├── App.vue
    ├── router/index.ts
    ├── api/http.ts         # axios 实例、X-Username 审计头、Result 解包
    ├── api/dd.ts           # 尽调接口封装
    ├── types/dd.ts         # 与后端 DTO/VO 对齐的类型
    ├── stores/task.ts      # 任务列表、当前任务、SSE 连接
    ├── views/Workbench.vue # 左右分栏主页面
    └── components/
        ├── TaskListPanel.vue
        ├── TaskHeader.vue
        ├── AgentGrid.vue
        ├── ReportPanel.vue
        ├── FindingPanel.vue
        ├── SupplementPanel.vue
        ├── PlanDrawer.vue
        └── EvidenceDrawer.vue
```

后端接口以 `DdTaskController`、`DdReportController`、`DdScenarioController`、`DdPlanController`、`DdEvidenceController` 为准，实施前先读源码核对路径与字段。

## 4. 数据流

- 统一响应：后端 `Result<T>`，`code === "000200"` 视为成功，`data` 返回调用方；其他 code 用 `ElMessage.error(message)` 提示。
- 身份：请求统一带 `X-Username: admin`、`X-User-Id: 1`（agent 域仅用于审计，不做校验）。SSE 走 EventSource，无法加自定义头，后端亦不要求。
- SSE：订阅 `/api/v1/dd/tasks/{taskId}/events`，监听 `task-status` 事件更新任务状态与进度；切换任务时关闭旧连接，异常断开按 3s 退避重连，最多 5 次。
- 刷新策略：任务状态变化后按需刷新执行记录、报告版本与线索/补充列表；写操作（提交、取消、复核、完成补充）成功后立即刷新当前 Tab 数据。

## 5. 页面结构

单页 `/workbench`：

- 左栏 `TaskListPanel`：关键字 + 状态筛选、任务卡片（任务号、企业、状态、进度）、分页。
- 右栏 `TaskHeader`：任务标题、状态标签、进度条、操作按钮（提交/取消/重新识别场景/查看计划/证据溯源）。
- Tab：
  - 概览：`AgentGrid`（12 个 Agent 状态卡片）+ 业务场景摘要 + 报告摘要 + 风险线索摘要
  - 执行：执行记录表（节点、Agent、状态、耗时、摘要），失败任务提供断点恢复
  - 报告：`ReportPanel`（版本切换、章节正文、复核动作、字段修正）
  - 线索与补充：`FindingPanel` + `SupplementPanel`
  - 计划与证据：计划版本与详情（`PlanDrawer`）、证据列表与引用溯源（`EvidenceDrawer`）

## 6. 错误处理

- 网络/超时：提示"服务不可用，请确认 8109 已启动"，保留当前页面数据。
- 业务错误码（如 230102 状态冲突、230136 状态不允许）：直接展示后端 message，并刷新任务状态避免界面与真实状态不一致。
- SSE 断开：状态区标记"连接中断"，重连成功后以最新快照覆盖。

## 7. 验证方式

1. `npm run build`（含 `vue-tsc` 类型检查）通过。
2. Vitest 覆盖 Result 解包、状态标签映射、SSE 重连判断等纯逻辑。
3. 浏览器实测：创建并提交任务 → 观察执行进度 → 查看报告并复核 → 完成补充触发重分析 → 查看计划与证据溯源。

## 8. 文档同步

- 新增 `omni-bank-ui/README.md`（启动方式、代理配置、页面说明）。
- `AGENTS.md` 第 7 节结构树增加 `omni-bank-ui/` 并说明前后端分离方式。
- 根 `README.md` 增加前端启动说明。
- `.gitignore` 增加 `node_modules/`、`dist/`、`.superpowers/`。

# P4 Planner 与 Graph 调度实现计划

## Context

P4 基础设施已交付：Prompt Manager（`dd/prompt`）、Spring AI `ChatClient`（`dd/llm`，MOCK/OPENAI_COMPATIBLE 显式切换）、Spring AI Alibaba Graph 执行器（`dd/graph/GraphExecutor`）。但链路仍止于 `PLANNING`，没有任何 Planner 代码，`DdTask.currentPlanId`、`DdScenarioService` 注释均预留了 Planner 接入点。

本次目标（依据需求文档 §4.1 Planner、§4.3 推荐流程、规格文档 §5.1 依赖表/§5.2 并行/§5.3 条件执行）：

1. Planner 以 **Spring AI ChatClient + planner Prompt** 生成尽调计划；MOCK 模式退回规则生成并显式标记 `plannerMode=MOCK`，不做静默真实调用。
2. 计划持久化为多版本（`dd_plan`），任务表 `current_plan_id` 指向当前版本。
3. 计划转换为 Graph 并由 **Spring AI Alibaba Graph** 调度：仅已注册 Agent 真正执行，未交付 Agent 标记 `NOT_DELIVERED`，整图继续，不伪造结果。
4. 任务状态按状态机推进：`PLANNING → DATA_COLLECTING → ANALYZING`。

已确认方案：新增 `dd_plan` 表；计划与调度在 `TASK_SUBMITTED` 事件内同步执行；调度完成后推进到 `ANALYZING`。

## 关键约束

- Graph 必须使用 Spring AI Alibaba `StateGraph`/`CompiledGraph`，不得自研拓扑调度；LLM 必须使用 Spring AI `ChatClient`。
- SAA 图约束（AGENTS.md 已知坑 14）：一个节点的多个后继必须汇聚到同一直接后继。Planner 计划是按层级的通用 DAG（01/06 → 02/03/04/05 → 07 → 08 → 09 → 10 → 11 → 12），直接用 `agent→agent` 边会编译失败，因此引入**汇聚节点（join）**：每层 Agent 并行执行后统一汇聚到 join，再由 join 扇出到下一层。
- join 只做同步点，不因上游失败阻断整图（需求 §5.3：单个 Agent 失败不能终止整张 Graph）；Agent 节点仍按现有规则，上游未成功则 `SKIPPED`。
- 未注册 Agent 在 Graph 节点内直接标记 `NOT_DELIVERED`，不调用 `AgentRuntime`，避免伪装成 FAILED。
- 禁止 TODO/空实现；错误码延续 230126 起；DDL 需可重复执行并同步 H2 测试 schema。

## 实施步骤

### 1. 计划模型与持久化

- 新增 `docs/sql/omni_bank_dd.sql` 尾部：`dd_plan` 表（`CREATE TABLE IF NOT EXISTS`），字段：`task_id`、`plan_version`、`planner_mode`、`business_scenario`、`sub_scenario`、`confidence`、`reasoning_summary`、`agent_plan`/`data_plan`/`material_plan`/`dependency_plan`/`supplement_plan`/`execution_config`（JSON）、`model_name`、`prompt_version` + `BaseEntity` 审计列；唯一键 `(task_id, plan_version)`。
- 同步 `src/test/resources/dd-schema-h2.sql`（JSON 用 `VARCHAR/CLOB`）。
- 新增 `dd/entity/DdPlan.java`（继承 `BaseEntity`，`@TableName("dd_plan")`，JSON 列以 String 承载）与 `dd/mapper/DdPlanMapper.java`。
- 新增 `dd/enums/PlannerMode.java`：`LLM` / `MOCK`（MOCK 表示规则生成，显式可见）。

### 2. Agent 目录与 Planner 生成

- 新增 `dd/plan/DdAgentCatalog.java`：按规格文档 §5.1 登记 12 个 Agent 编码、名称与依赖（Planner 依赖视为外部，不进入 Agent 图），作为计划校验、规则兜底与图构建的唯一来源。
- 新增 `dd/plan/PlannerOutput.java`（LLM 结构化输出模型）：`businessScenario`、`subScenario`、`confidence`、`scenarioStatus`、`reasoningSummary`、`requiredAgents`、`requiredData`、`requiredDocuments`、`optionalDocuments`、`dependencyPlan`、`supplementPlan`、`executionConfig`、`unknowns`。
- 新增 Prompt 资源 `src/main/resources/dd/prompts/planner/v1/{system-prompt.txt,user-template.txt,output-schema.json}`，沿用 `SUBJECT_VERIFICATION` 的书写风格；变量：企业信息、授信申请、场景识别结果、证据摘要、可用 Agent 清单。通过 `PromptManager.require("PLANNER", null)` 取最新版本（目录即版本，不硬编码版本号）。
- 新增 `dd/plan/DdPlanner.java`：
  - `DdLlmProperties.mode == OPENAI_COMPATIBLE`：`PromptManager.render` → `DdChatService.completeForEntity(..., PlannerOutput)` → 校验（编码属于目录、依赖闭包合法、无环、置信度 0-1）→ `plannerMode=LLM`。调用/解析/校验失败抛 `230126/230127`，**不静默回退规则**。
  - `MOCK`：按场景与申请信息用目录生成规则计划，`plannerMode=MOCK`，`modelVersion=MOCK_RULE`。
  - 计划写入 `dd_plan`（`plan_version` 任务内自增），并通过 `DdTaskService.updateCurrentPlan` 更新 `current_plan_id`。
  - 证据摘要通过 `DdEvidenceMapper` 按 `task_id` 查询后拼接（类型/来源/Mock 标记），不新增查询接口。

### 3. Plant → Graph 调度

- 新增 `dd/plan/DdPlanGraphFactory.java`：按 `dependencyPlan`（缺省回退目录依赖）计算拓扑层级，生成 `GraphDefinition`：
  - 每层节点 `GraphNode.of(agentCode, agentCode, null, 上一层 join)`；
  - 层后生成 `GraphNode.join("join.{level}", 本层节点ID)`；最后一层 join → `END`；
  - 空计划 / 未知编码抛 `230127`。
- 扩展 `dd/graph/GraphNode.java`：允许 `agentCode == null` 的汇聚节点，新增 `GraphNode.join(nodeId, dependsOn...)` 工厂。
- 扩展 `dd/graph/GraphNodeStatus.java`：新增 `NOT_DELIVERED`。
- 修改 `dd/graph/GraphExecutor.java`：
  - 注入 `AgentRegistry`；Agent 节点执行前 `contains(agentCode)` 判定，未注册直接 `NOT_DELIVERED`（摘要说明未交付，不调用运行时）；
  - join 节点恒为 `SUCCEEDED`（同步点），摘要记录成功/失败/未交付计数；`GraphNodeResult.data` 聚合上游 `agentCode → AgentResult`；
  - 下游 Agent 的上游结果展开 join 聚合，保持 `AgentContext.upstreamResults` 可追溯；
  - `validate` 放宽 join 节点的 `agentCode` 校验。
- `dd/ai/AgentRegistry.java` 新增 `contains(String)`。

### 4. 任务状态与链路接入

- `dd/service/DdTaskService.java` + `dd/service/impl/DdTaskServiceImpl.java`：
  - 新增 `boolean beginDataCollecting(Long taskId)`（`PLANNING → DATA_COLLECTING` 条件更新 + SSE）与 `void markAnalyzing(Long taskId)`（`DATA_COLLECTING → ANALYZING`）；
  - 新增 `void updateCurrentPlan(Long taskId, Long planId)`；
  - `markFailed` 允许状态扩展为 `INITIALIZING/SCENARIO_IDENTIFYING/PLANNING/DATA_COLLECTING/ANALYZING`。
- 新增 `dd/plan/DdPlanScheduler.java`：`beginDataCollecting` → `DdPlanner.plan` → `DdPlanGraphFactory.build` → `GraphExecutor.execute` → `markAnalyzing`；计划为空或图执行为空时抛业务异常，由调用方标失败。
- 修改 `dd/service/DdTaskInitHandler.java`：`markPlanning` 后调用 `DdPlanScheduler.schedule(taskId)`，异常仍走现有 `markFailed`。
- 更新 `DdTask.currentPlanId` 注释、`DdScenarioService` 接口注释（场景识别仍为规则，Planner 以其结果为输入）。

### 5. 错误码

`omni-bank-service-agent-api` 的 `DdErrorCode` 追加：

| 错误码 | 含义 |
|---|---|
| 230126 | 尽调计划生成失败 |
| 230127 | 尽调计划校验未通过 |

### 6. 测试

新增 `src/test/java/.../dd/DdPlannerIntegrationTest.java`（`@SpringBootTest(DdTestApplication)` + `h2`）：

1. MOCK 模式生成计划：`plannerMode=MOCK`、12 个 Agent、`dependencyPlan` 含 02←01/06、12←01..11，计划已落库且 `current_plan_id` 已更新。
2. Plan → Graph：`SUBJECT_VERIFICATION` 为 `SUCCEEDED`，其余 11 个为 `NOT_DELIVERED`，join 节点 `SUCCEEDED`，图整体不抛异常。
3. 完整链路：create → submit → `publishPending` 后任务状态为 `ANALYZING`，`lastErrorCode` 为空。

现有 `DdPromptLlmGraphIntegrationTest` 等测试保持通过。

### 7. 文档同步

- `omni-bank-service-agent/README.md`：P4 增加 Planner/计划持久化/调度说明；错误码表补 230126/230127；任务状态说明改为链路止于 `ANALYZING`；更新测试统计与未完成事项。
- `docs/sql` 表清单在 README 中同步提及 `dd_plan`（如有表清单）。

## 验证

```bash
mvn -ntp -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot -am test
mvn -ntp clean install -T 1C
```

预期：模块全部测试通过（含新增 3 个用例），全量构建 BUILD SUCCESS，无 TODO/空实现。

## 不在本次范围（写入 README 未完成事项）

- 12 个专项 Agent 中除 01 外的分析逻辑与 Prompt。
- 场景识别的 LLM 化（当前仍为配置+规则，Planner 读取其结果）。
- Graph 节点执行记录持久化、断点恢复、真实异步任务队列、增量重分析、风险测算与报告生成。

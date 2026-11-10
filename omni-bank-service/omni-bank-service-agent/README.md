# 银行对公信贷智能尽调 Agent（P0-P4 交付说明）

本说明对应 REQUIREMENTS 第 12 条交付要求，覆盖当前已完成范围：**P0 工程骨架 + P1 模块一（尽调任务与企业上下文）+ P2 证据中心与 Mock 数据适配 + P3 Agent 框架底座（Tool Registry + Agent Runtime + Agent 01）+ P4 Prompt Manager、大模型调用、Planner 计划生成与 Graph 编排调度**。
12 个专项 Agent 仅落地契约与 Agent 01，其余分析逻辑不包含；未交付 Agent 在调度中显式标记 `NOT_DELIVERED`，不伪造结果。

## 1. 启动方式

前置条件：JDK 21、Maven 3.9+、MySQL 8.x（库名 `omni_bank`）。

```bash
# 1. 初始化数据库（可重复执行）
mysql -u root -p omni_bank < docs/sql/omni_bank_ai.sql
mysql -u root -p omni_bank < docs/sql/omni_bank_dd.sql

# 2. 启动服务（端口 8109，Dubbo triple 20889）
mvn spring-boot:run -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot

# 3. 探活
curl http://127.0.0.1:8109/actuator/health
```

- 缺省激活 `local` profile：已关闭 Nacos 注册发现并设置 `dubbo.registry.address=N/A`，本地无需中间件。
- 数据库账号口令走环境变量 `MYSQL_USERNAME` / `MYSQL_PASSWORD`（缺省 root/root）。
- 本地无 MySQL 时 HikariCP 会阻断启动，请先执行上述 SQL。
- 测试不依赖本地 MySQL：`application-h2.yml` + `dd-schema-h2.sql` 使用 H2 内存库。

```bash
# 编译与测试
mvn -ntp test -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-api,omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot
```

## 2. 接口文档

服务内路径前缀 `/v1/dd/tasks`；经网关转发时对外为 `/api/agent/v1/dd/tasks/**`（网关已登记 `agent` 显式路由，`StripPrefix=1` 去前缀）。
统一响应体为项目 `Result<T>`；分页出参为 `PageResult<T>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/dd/tasks` | 创建尽调任务（草稿） |
| GET | `/v1/dd/tasks` | 任务分页（taskNo / taskStatus / enterpriseId） |
| GET | `/v1/dd/tasks/{taskId}` | 任务详情 |
| GET | `/v1/dd/tasks/{taskId}/status` | 任务状态与进度 |
| GET | `/v1/dd/tasks/{taskId}/events` | SSE 实时状态推送（`text/event-stream`） |
| POST | `/v1/dd/tasks/{taskId}/submit` | 提交任务 |
| POST | `/v1/dd/tasks/{taskId}/cancel` | 取消任务 |
| GET | `/v1/dd/tasks/{taskId}/context` | 企业上下文装配结果 |
| GET | `/v1/dd/tasks/{taskId}/scenario` | 最新业务场景识别结果 |
| POST | `/v1/dd/tasks/{taskId}/scenario/reidentify` | 重新识别业务场景（生成新版本） |
| GET | `/v1/dd/tasks/{taskId}/evidences` | 任务证据分页（evidenceType / sourceType / sourceSystem） |
| GET | `/v1/dd/evidences/{evidenceId}` | 证据详情与溯源字段 |

### 创建任务示例

```json
{
  "enterprise": {
    "creditCode": "91310000MA1FL0XXXX",
    "customerId": "C0001",
    "enterpriseName": "示例科技有限公司"
  },
  "application": {
    "applicationNo": "APP20261109001",
    "productCode": "CORPORATE_WORKING_CAPITAL",
    "productName": "流动资金贷款",
    "applyAmount": 5000000,
    "currency": "CNY",
    "applyTerm": 12,
    "termUnit": "MONTH",
    "loanPurpose": "原材料采购",
    "repaymentMethod": "EQUAL_PRINCIPAL_INTEREST",
    "guaranteeType": "MORTGAGE"
  },
  "taskName": "示例科技流动资金尽调"
}
```

企业按 `creditCode` 复用；企业名称仅在首次入库时必填。申请信息以 JSON 快照写入 `dd_loan_application.application_snapshot`。

### Mock 数据适配器（P2）

- 银行与外部数据接口未确认前，只启用 `MockBankDataAdapter`，由 `omni.dd.adapter.mock.enabled` 控制（缺省 `true`）。
- 场景识别完成后自动采集 3 条基础证据：工商（`MOCK_ENTERPRISE_REGISTRY`）、司法（`MOCK_JUDICIAL_PLATFORM`）、征信（`MOCK_CREDIT_BUREAU`）。
- 所有 Mock 证据 `isMock=true`，来源系统以 `MOCK_` 开头，内容摘要带【MOCK】前缀；`contentHash` 为摘要的 SHA-256。
- 采集幂等键为 `taskId + sourceSystem + sourceReference`，重复执行不会重复入库。
- 关闭开关后不采集证据，任务仍会继续生成计划并调度，任务证据列表为空。

### Agent 框架底座（P3）

- 工具契约：`AgentTool` + `ToolDefinition` / `ToolRequest` / `ToolResult`，由 `ToolRegistry` 统一注册与查找；重复注册启动期失败，未知工具返回 `230117`。
- 已注册工具：
  - `enterprise.queryEnterprise`（QUERY，Mock）：读取证据中心已采集的工商证据，结果带 mock 标记与证据 ID。
  - `enterprise.verifyCreditCode`（RULE，非 Mock）：按 GB 32100-2015 校验统一社会信用代码长度、字符集与校验位。
- Agent 契约沿用 `CreditAgent` / `AgentDefinition` / `AgentContext` / `AgentResult`；`AgentRegistry` 负责注册，`AgentContextBuilder` 装配证据引用与工具清单，`AgentRuntime` 负责执行与结果兜底。
- 已实现 Agent 01 `SUBJECT_VERIFICATION`：规则校验主体身份与申报/登记一致性，输出 Finding、DataGap、SupplementRequest 与 EvidenceRef；Mock 模式下自动附加告警。
- 当前未暴露 Agent 执行 HTTP 接口，后续由 Planner/Graph 异步调度；参数 Schema 校验、权限边界与超时控制尚未实现。

### Prompt Manager（P4）

- 模板按 Agent 独立管理：`classpath:dd/prompts/{agent 目录}/v{版本}/`，包含 `system-prompt.txt`、`user-template.txt`、`output-schema.json`。
- `PromptManager` 负责版本发现（目录即版本，历史版本只增不覆盖）、加载与 `{{变量}}` 渲染；模板缺失返回 `230120`，变量缺失返回 `230121`。
- 已提供 `SUBJECT_VERIFICATION`、`PLANNER` v1 模板；Agent 01 当前仍使用规则引擎，模板供后续切换模型时直接复用。

### 大模型调用（P4）

- 基于 **Spring AI 1.1.2**：统一使用 `ChatClient` / `ChatModel` 抽象，业务 Agent 不直接依赖供应商私有协议；`DdChatService` 仅封装 `ChatClient` 并把异常转换为业务错误码。
- 模型实现由 `omni.dd.llm.mode` 显式选择，缺省 `MOCK`，不与真实调用静默切换：
  - `MOCK`：`MockChatModel` 实现 `ChatModel`，不发起任何外部请求，返回固定 JSON，`mock=true`、`model=MOCK_CHAT_MODEL`。
  - `OPENAI_COMPATIBLE`：`OpenAiChatModel`（`spring-ai-openai`）对接 OpenAI 兼容网关，补全路径缺省 `/v1/chat/completions`；API Key 只从环境变量注入，缺少必要配置启动即失败。
- 结构化输出使用 `ChatClient.call().entity(ParameterizedTypeReference<T>)`，由 Spring AI `BeanOutputConverter` 完成反序列化与格式校验；调用失败返回 `230123`，输出无法解析返回 `230124`。
- 配置项（`application.yml` 已登记，密钥留空走环境变量）：

```yaml
omni:
  dd:
    llm:
      mode: MOCK
      base-url: ${OMNI_DD_LLM_BASE_URL:}
      api-key: ${OMNI_DD_LLM_API_KEY:}
      model: ${OMNI_DD_LLM_MODEL:}
      completions-path: /v1/chat/completions
      connect-timeout-ms: 3000
      request-timeout-ms: 30000
      temperature: 0.20
      max-tokens: 2048
```

### Graph 编排（P4）

- 基于 **Spring AI Alibaba 1.1.2.4-security-fix** 的 `StateGraph` / `CompiledGraph`：业务 `GraphDefinition` 转换为框架图，依赖等待与分支并行由框架调度，不再自研拓扑执行。
- `GraphDefinition` / `GraphNode`：节点对应一次 Agent 执行，`dependsOn` 声明依赖；多依赖节点以 `addEdge(dependsOn, nodeId)` 表达汇合。
- 并行执行统一通过 `RunnableConfig.defaultParallelExecutor(ttlExecutor)` 注入 TTL 装饰的业务线程池。
- 上游结果以节点ID为键注入下游 `AgentContext.upstreamResults`，值为上游 `AgentResult`，保证可追溯。
- 单个节点失败不终止整图：节点动作内捕获异常并保留失败结果，下游依据上游状态标记 `SKIPPED`，其他分支继续执行；整体状态为 `SUCCEEDED` / `PARTIAL_FAILED` / `FAILED`。
- 节点结果在框架执行过程中直接收集：`CompiledGraph` 返回的终态会经 `StateSerializer` 序列化克隆，自定义类型会被还原为 `Map`，因此不依赖终态按类型读取。
- 重复节点ID、未知依赖、自依赖、循环依赖以及框架不支持的分支形态（扇出分支必须汇聚到同一后继节点）返回 `230122`。
- 未交付 Agent（编码在目录中但未注册运行时）标记为 `NOT_DELIVERED`，不调用 `AgentRuntime`，下游汇聚节点仍放行，整图继续执行。
- 当前为内存编排，节点执行记录持久化与断点恢复尚未实现。

### 尽调计划与调度（P4）

- `dd_plan` 按 `plan_version` 版本化保存计划，历史版本不覆盖；`dd_task.current_plan_id` 指向最新版本。
- 计划包含需求文档 §4.1 的六类输出：`agent_plan`、`data_plan`、`material_plan`、`dependency_plan`、`supplement_plan`、`execution_config`，并记录 `planner_mode`、`model_name`、`prompt_version`。
- `DdAgentCatalog` 登记规格文档 §5.1 的 12 个 Agent 与初始依赖，作为计划校验、规则兜底与 Graph 构建的唯一来源。
- `DdPlanner` 按 `omni.dd.llm.mode` 显式选择生成方式：
  - `OPENAI_COMPATIBLE`：渲染 `PLANNER` Prompt 后经 Spring AI `ChatClient` 生成结构化计划，Agent 编码、依赖闭包、无环与置信度校验失败返回 `230127`；不静默回退规则。
  - `MOCK`：按 Agent 目录规则生成，`planner_mode=MOCK`、`model_name=MOCK_RULE`，并在 `reasoning_summary` 标注规则生成与 Mock 证据数量。
- `DdPlanGraphFactory` 按依赖计算拓扑层级，同层 Agent 并行，层后插入 `join.{level}` 汇聚节点，规避 Spring AI Alibaba Graph 的扇出汇聚约束。
- `DdPlanScheduler` 在 `TASK_SUBMITTED` 消费链路内同步执行：`PLANNING → DATA_COLLECTING`，生成计划后执行 Graph，完成后推进到 `ANALYZING`；失败统一标记任务 `FAILED`。

### 任务状态

`DRAFT(0) → INITIALIZING(10) → SCENARIO_IDENTIFYING(20) → PLANNING(30) → DATA_COLLECTING(40) → ANALYZING(60) → RISK_ASSESSING(80) → REPORT_GENERATING(90) → WAITING_FOR_REVIEW(95) → COMPLETED(100)`。

P4 实际链路止于 `ANALYZING`（场景识别、基础证据采集、计划生成与 Graph 调度完成后进入分析阶段）；`WAITING_FOR_SUPPLEMENT(60)`、`REANALYZING(70)`、`RETURNED(80)`、`FAILED(0)`、`CANCELLED(0)` 为后续阶段预留。非法流转返回 `230102`，并发更新冲突返回 `230103`。

### 错误码

| 错误码 | 含义 |
|---|---|
| 230101 | 任务不存在 |
| 230102 | 当前状态不允许该操作 |
| 230103 | 任务状态并发冲突 |
| 230104 | 任务提交信息不完整 |
| 230105 | 任务不可取消 |
| 230106 | 企业身份信息缺失 |
| 230107 | 企业不存在 |
| 230108 | 企业主体冲突 |
| 230109 | 授信申请不存在 |
| 230110 | 业务场景不存在 |
| 230111 | 业务场景识别失败 |
| 230112 | 上下文装配失败 |
| 230113 | Outbox 事件发布失败 |
| 230114 | JSON 处理失败 |
| 230115 | 证据不存在 |
| 230116 | 证据采集失败 |
| 230117 | 工具不存在 |
| 230118 | Agent 不存在 |
| 230119 | Agent 执行失败 |
| 230120 | Prompt 模板不存在 |
| 230121 | Prompt 渲染失败 |
| 230122 | Graph 定义非法 |
| 230123 | 大模型调用失败 |
| 230124 | 大模型输出解析失败 |
| 230125 | Graph 执行失败 |
| 230126 | 尽调计划生成失败 |
| 230127 | 尽调计划校验未通过 |

## 3. 数据库说明

DDL：`docs/sql/omni_bank_dd.sql`（MySQL 8，可重复执行）。

| 表 | 用途 | 关键约束 |
|---|---|---|
| `dd_task` | 尽调任务主表 | `uk_task_no`；`idx_enterprise_id`、`idx_application_id`、`idx_task_status_create_time` |
| `dd_enterprise` | 企业主体信息 | `uk_credit_code`；`idx_customer_id`、`idx_enterprise_name` |
| `dd_loan_application` | 授信申请与原始快照 | `uk_task_id`（一任务一申请）；`idx_application_no`、`idx_product_code` |
| `dd_business_scenario` | 业务场景识别结果（多版本） | `uk_task_scenario_version`；`idx_task_id`、`idx_business_type` |
| `dd_evidence` | 证据中心：证据元信息与溯源字段（`source_type`、`is_mock`、`confidence`、`enterprise_id`） | `idx_task_id`、`idx_task_type`、`idx_enterprise_id` |
| `dd_outbox_event` | 异步事件 Outbox | `uk_event_id`；`idx_publish_status_next_retry` |
| `dd_plan` | 尽调计划（P4，多版本） | `uk_task_plan_version`；`idx_task_id` |

公共约定：雪花 `BIGINT` 主键；`version` 乐观锁；`del_flag` 逻辑删除；`creator/updater/create_time/update_time` 自动填充；时间 `DATETIME(3)`；JSON 列存业务扩展。

P2 对已建库环境提供 `dd_evidence_p2_upgrade` 存储过程按需补列，脚本整体可重复执行。

## 4. 异步机制与一致性

- 提交任务在同一事务内写 `dd_task` 状态与 `dd_outbox_event`（`TASK_SUBMITTED`）。
- `DdOutboxPublisher` 每 1s 扫描 `PENDING` 且到期的记录（批量 20），发布 Spring 本地事件后置 `PUBLISHED`；失败累加 `retry_count`，最多 5 次，指数退避（60s 起，上限 300s）。
- `DdTaskInitHandler` 消费 `TASK_SUBMITTED`：条件更新抢占 `INITIALIZING → SCENARIO_IDENTIFYING`，执行规则识别与基础证据采集后进入 `PLANNING`，随后同步生成计划并调度 Graph，完成后推进到 `ANALYZING`；重复投递因状态不符被忽略（幂等）。
- 所有状态变更使用 `WHERE id = ? AND version = ?` 条件更新，影响行数不为 1 即判定并发冲突。
- SSE 为单实例内存注册表（`DdSseRegistry`），断线后前端应回落到 `/status` 接口恢复。

## 5. 测试结果

```
DdAgentFrameworkIntegrationTest     Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
DdEvidenceIntegrationTest           Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
DdPlannerIntegrationTest            Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
DdPromptLlmGraphIntegrationTest     Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
SpringAiChatClientOpenAiTest        Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
DdTaskControllerIntegrationTest     Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
DdTaskFlowIntegrationTest           Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
TaskStateMachineTest                Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
合计                                 Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
全量 mvn clean install              BUILD SUCCESS
```

覆盖：企业复用与申请快照、提交→Outbox→场景识别→证据采集→计划生成→Graph 调度→ANALYZING 全链路、事件重复投递幂等、规则未命中进入待确认、取消后不可提交、状态机合法/非法流转、详情与分页过滤、场景重新识别版本递增、SSE 端点状态快照推送；Mock 证据标记与来源、采集幂等、证据分页三类过滤、证据详情溯源字段、证据不存在（230115）与任务不存在（230101）；Agent 框架的工具发现/未知工具（230117）、统一社会信用代码三类校验、Mock 查询工具证据返回、主体核验 Agent 的结构化结果与待核实线索、未知 Agent（230118）；Prompt 版本发现与变量渲染、模板/变量缺失错误码（230120/230121）、Spring AI ChatClient Mock 标记与结构化输出（BeanOutputConverter）、OpenAI 兼容协议请求/响应映射（鉴权头、模型参数、500→230123、非法输出→230124、缺配置启动失败）、Spring AI Alibaba Graph 依赖分层执行与上游结果传递、未交付节点标记 `NOT_DELIVERED` 且下游跳过、循环依赖、空图与框架不支持的分支形态校验（230122）；Planner 规则计划生成与版本落库、`planner_mode=MOCK` 显式标记、计划转 Graph 后仅 Agent 01 执行其余标记未交付、重复生成计划版本递增；以及 12 个 HTTP 端点的路径映射、查询参数绑定、`Result` 包装、参数校验错误码（000400）与业务错误码返回。

> 加固说明：测试发现 `omni-bank-common-mybatis` 的 `MybatisPlusConfig`（分页插件）与 `AuditMetaObjectHandler`（审计填充）未登记自动装配，导致分页 `total` 恒为 0、`creator/updater` 不填充。已新增 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 修复，全仓模块统一生效。

## 6. 未完成事项与待确认

1. **12 个专项 Agent 仅实现 Agent 01**：其余 11 个仅有契约模型，分析逻辑、Prompt、输入输出 Schema 与专属工具待后续阶段逐个交付。
2. **P4 之后链路未实现**：Graph 节点执行记录持久化与断点恢复、真实异步任务队列、增量分析、风险测算、报告生成、人工复核。
3. **Agent 尚未接入大模型**：Agent 01 仍为规则引擎；Prompt 模板与 Spring AI `ChatClient` 已就绪，切换需在 Agent 内组装 Prompt、校验输出并记录 `modelVersion` / `promptVersion`。
4. **模型网关联调未完成**：`OPENAI_COMPATIBLE` 模式仅完成协议适配与本地模拟网关测试，真实网关地址、模型名称与配额待确认。
5. **真实银行/外部数据接口未对接**：当前仅 `MockBankDataAdapter`，切换真实接口需新增 `ExternalDataAdapter` 实现并关闭 `omni.dd.adapter.mock.enabled`。
6. **证据关联查询未实现**：`/v1/dd/evidences/{evidenceId}/references` 依赖 P3 的 Agent 结果、Finding、报告章节与人工复核表。
7. **场景识别规则待业务确认**：当前按产品编码 + 贷款用途关键词配置在 `omni.dd.scenario.rules`，置信度阈值 0.60；场景识别的模型化改造未实施，P4 Planner 仅以规则识别结果为输入，规则口径与阈值需银行业务部门确认。
8. **鉴权未在 dd 端点启用**：当前依赖网关与 `UserContextFilter` 透传 `X-User-*`，内网直连 8109 时无鉴权。
9. **网关路由已登记但鉴权未打通**：`/api/agent/**` 显式路由已就位，但 system 域路由仍未登记，管理端完整链路需待 system 路由与 Sa-Token 鉴权贯通。
10. **Outbox 未接 MQ**：当前为单库自扫描 + Spring 本地事件，多实例部署时需替换为 MQ 或加分布式锁。
11. **SSE 多实例不互通**：内存注册表仅适用于单实例，水平扩展需引入 Redis Pub/Sub。
12. **H2 测试 DDL 与 MySQL DDL 双份维护**：`src/test/resources/dd-schema-h2.sql` 需随 `docs/sql/omni_bank_dd.sql` 同步。
13. **待确认字段**：`current_plan_id` 已由 P4 Planner 维护（指向 `dd_plan` 最新版本），`analysis_version` 的推进规则、`dd_evidence` 的证据类型/来源类型枚举扩展仍待后续阶段规格明确。
14. **工具层能力待补齐**：参数 Schema 校验、权限边界、超时控制与调用审计尚未实现；Agent 执行结果暂未持久化。

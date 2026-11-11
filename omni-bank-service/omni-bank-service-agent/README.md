# 银行对公信贷智能尽调 Agent（P0-P6 交付说明）

本说明对应 REQUIREMENTS 第 12 条交付要求，覆盖当前已完成范围：**P0 工程骨架 + P1 模块一（尽调任务与企业上下文）+ P2 证据中心与 Mock 数据适配 + P3 Agent 框架底座（Tool Registry + Agent Runtime + Agent 01）+ P4 Prompt Manager、大模型调用、Planner 计划生成与 Graph 编排调度 + P5 专业 Agent（Agent 01–07）与节点执行记录持久化、断点恢复 + P6 专业 Agent（Agent 08–10）**。
12 个专项 Agent 已交付前 10 个（主体核验、财务分析、司法风险、征信债务、现场调查、材料识别、行业专家、贷款用途、反欺诈、政策匹配），统一采用确定性规则引擎，不调用大模型；其余 2 个（综合风险、尽调报告）在调度中显式标记 `NOT_DELIVERED`，不伪造结果。

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
| GET | `/v1/dd/tasks/{taskId}/executions` | Agent 节点执行记录分页（executionStatus / nodeId） |
| POST | `/v1/dd/tasks/{taskId}/resume` | 失败任务断点恢复：复用成功节点，重跑失败与跳过节点 |
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
- 每个 Agent 节点执行结果（状态、摘要、输入快照、`AgentResult` 全文、模型/Prompt 版本、耗时）落 `dd_agent_execution`；汇聚节点不记录。

### 尽调计划与调度（P4）

- `dd_plan` 按 `plan_version` 版本化保存计划，历史版本不覆盖；`dd_task.current_plan_id` 指向最新版本。
- 计划包含需求文档 §4.1 的六类输出：`agent_plan`、`data_plan`、`material_plan`、`dependency_plan`、`supplement_plan`、`execution_config`，并记录 `planner_mode`、`model_name`、`prompt_version`。
- `DdAgentCatalog` 登记规格文档 §5.1 的 12 个 Agent 与初始依赖，作为计划校验、规则兜底与 Graph 构建的唯一来源。
- `DdPlanner` 按 `omni.dd.llm.mode` 显式选择生成方式：
  - `OPENAI_COMPATIBLE`：渲染 `PLANNER` Prompt 后经 Spring AI `ChatClient` 生成结构化计划，Agent 编码、依赖闭包、无环与置信度校验失败返回 `230127`；不静默回退规则。
  - `MOCK`：按 Agent 目录规则生成，`planner_mode=MOCK`、`model_name=MOCK_RULE`，并在 `reasoning_summary` 标注规则生成与 Mock 证据数量。
- `DdPlanGraphFactory` 按依赖计算拓扑层级，同层 Agent 并行，层后插入 `join.{level}` 汇聚节点，规避 Spring AI Alibaba Graph 的扇出汇聚约束。
- `DdPlanScheduler` 在 `TASK_SUBMITTED` 消费链路内同步执行：`PLANNING → DATA_COLLECTING`，生成计划后执行 Graph，完成后推进到 `ANALYZING`；失败统一标记任务 `FAILED`。

### 执行记录与断点恢复（P5）

- `dd_agent_execution` 按 `task_id + graph_code + node_id + execution_no` 唯一，同一节点每次真正执行都新增一行，`execution_no` 递增；断点复用的节点不重复写记录。
- 记录内容：执行状态（`SUCCEEDED` / `FAILED` / `SKIPPED` / `NOT_DELIVERED`）、摘要、失败原因、节点输入快照、`AgentResult` 结果快照（含 findings / dataGaps / evidenceRefs）、`model_version`、`prompt_version`、起止时间与耗时。
- 恢复入口 `POST /v1/dd/tasks/{taskId}/resume`：仅 `FAILED` 且存在 `current_plan_id` 的任务可恢复，否则返回 `230128`；恢复时条件更新 `FAILED → ANALYZING` 并清空 `last_error_*`，重新抛出异常时再次标记 `FAILED`。
- 恢复执行时按任务 + Graph 读取每个节点最新一条 `SUCCEEDED` 记录直接复用（`GraphNodeResult.reused=true`），`FAILED` / `SKIPPED` / `NOT_DELIVERED` 节点重新执行；未关联计划的历史执行不会跨 Graph 复用。
- 复用结果从 JSON 还原为 `AgentResult`，结构化 `data` 为 Map 形态；P5 专业 Agent 读取上游结果时同时兼容对象与 Map 两种形态。
- 执行记录只做审计与恢复依据，不替代证据中心：`AgentResult.evidenceRefs` 仍指向 `dd_evidence`。

### 专业 Agent（P5）

P5 交付 Agent 01–07，全部为确定性规则引擎：指标计算、阈值判断与勾稽在 Java 内完成，`AgentResult.modelVersion=rule-engine`、`promptVersion=rule-1.0`。数据缺失只产生 `dataGap` / `supplementRequest`，不使 Agent 失败；Mock 结果一律在 `warnings` 中显式提示。

| Agent | 规则要点 | 工具编码 | 证据类型 |
|---|---|---|---|
| `SUBJECT_VERIFICATION`（已有） | 统一社会信用代码校验、名称/登记信息一致性 | `enterprise.queryEnterprise`、`enterprise.verifyCreditCode` | 工商登记 |
| `FINANCIAL_ANALYSIS` | 盈利/偿债/营运/现金流指标、多期趋势、纳税申报收入与报表收入勾稽、银行流水经营流入覆盖率 | `financial.getFinancialStatements`、`financial.getTaxData`、`financial.getBankStatements` | 财务报表、纳税、银行流水 |
| `CREDIT_DEBT_ANALYSIS` | 征信使用率、逾期、对外担保占净资产、债务期限结构、6 个月集中到期、债务/营收比 | `credit.getEnterpriseCreditReport`、`credit.getDebtMaturity` | 征信报告、债务 |
| `JUDICIAL_RISK` | 涉诉角色与未结金额、被执行/失信、行政处罚整改、主体模糊匹配显式降级 | `judicial.getJudicialRecords`、`judicial.getEnforcementRecords`、`judicial.getAdministrativePenalties` | 司法记录 |
| `SITE_VISIT` | 现场地址与注册地址比对、设备开机率、照片完整性、访谈陈述（STATEMENT）与现场事实（FACT）偏差 | `siteVisit.getInvestigationRecords`、`siteVisit.getInterviewRecords` | 现场调查 |
| `DOCUMENT_RECOGNITION` | 材料分类、必备材料完整性、重复文件哈希、有效期检查；显式标注 Mock 材料源，不伪装 OCR | `document.listMaterials`、`document.classifyDocument`、`document.createEvidence` | 客户材料 |
| `INDUSTRY_EXPERT` | 行业归属、行业增速与周期、政策导向、CR5 集中度、企业毛利率/资产负债率与行业基准对比 | `industry.getIndustryProfile`、`industry.getIndustryBenchmark` | 行业研究（`INDUSTRY_RESEARCH`） |

- 工具只负责取数并登记证据，指标与风险判断在 Agent 内完成；证据统一经 `DdEvidenceService.recordEvidence(taskId, enterpriseId, ExternalDataItem)` 幂等写入（幂等键 `taskId + sourceSystem + sourceReference`），构造使用 `DdMockEvidence.item(...)`，`isMock=true`、`contentHash` 为摘要 SHA-256。
- Mock 工具数据由统一社会信用代码哈希确定性派生（`DdDeterministicMockData`），同一主体重复执行结果一致，禁止随机数；输出中的比率统一保留 2 位 `HALF_UP`，除法前判零。
- 财务分析的上游结果支持对象与断点恢复后的 `Map` 两种形态（`CREDIT_DEBT_ANALYSIS` 读取营业收入/净资产，`INDUSTRY_EXPERT` 读取毛利率/资产负债率）。
- 材料识别定义基础必备材料 6 类，担保场景追加担保合同与抵押物权证 2 类；过期、重复、无法归类与缺失均产出 Finding，缺失同时生成 `dataGap` 与 `supplementRequest`。
- 现场调查严格区分观察事实（`FACT`）与访谈陈述（`STATEMENT`），访谈内容只作为待核实线索，不输出已确认结论。

### 专业 Agent（P6）

P6 交付 Agent 08–10，仍为确定性规则引擎：需求测算、规则命中与条款匹配在 Java 内完成，`AgentResult.modelVersion=rule-engine`、`promptVersion=rule-1.0`。Mock 数据由统一社会信用代码确定性派生；Mock 结果一律在 `warnings` 中显式提示。

| Agent | 规则要点 | 工具编码 | 证据类型 |
|---|---|---|---|
| `LOAN_PURPOSE_ANALYSIS` | 用途分类（原材料采购/经营周转/设备购置/项目建设/贸易融资/其他）、流动资金与项目融资公式分开测算、金额与期限合理性、合同订单发票核验、资金流向线索、第一/第二还款来源分开评价；不输出最终审批金额 | `loanPurpose.classifyLoanPurpose`、`loanPurpose.calculateWorkingCapitalDemand`、`loanPurpose.calculateProjectFundingDemand`、`loanPurpose.analyzeContract`、`loanPurpose.analyzeFundFlow` | 计算（`CALCULATION`）、客户材料 |
| `FRAUD_DETECTION` | 主体、关联、财务、交易、资金、材料 9 类线索；每条命中含规则编号、规则版本、命中数据、证据、命中时间与人工核查结果占位；资金回流/金额相近/时间接近只作线索 | `fraud.checkIdentityConsistency`、`fraud.checkRelatedPartyTransactions`、`fraud.checkFinancialAnomalies`、`fraud.checkContractAnomalies`、`fraud.checkFundFlowAnomalies` | 工商、财务报表、客户材料、计算 |
| `POLICY_MATCHING` | Mock 制度库条款检索，输出制度名/版本/生效日期/条款编号/原文/页码/来源文件/文件哈希/向量 ID、适用性、限制条款、审批权限关注点、制度冲突与待人工确认项；不做合规结论 | `policy.searchApplicablePolicies`、`policy.retrievePolicyClauses` | 制度原文（`POLICY_DOCUMENT`） |

- 贷款用途：流动资金公式 `年营业收入 ÷ 360 × 营运资金周转天数 − 自有资金`（来源 `MOCK_CONFIG:DD_LOAN_PURPOSE_WORKING_CAPITAL_V1`），项目融资公式 `项目总投资 − 项目自有资金`（来源 `MOCK_CONFIG:DD_LOAN_PURPOSE_PROJECT_FUNDING_V1`），两类公式严格分开；金额合理性按申请金额/测算需求比例判定，期限按用途场景区间判定。
- 反欺诈：所有 Finding 状态固定 `NEEDS_VERIFICATION`，不输出欺诈认定；财务/税务/流水差异同时列出统计期间、未开票收入、现金结算、关联交易、重复统计等可能合理原因；人工核查结果字段保持为空，由人工回填。
- 政策匹配：制度检索基于本地 Mock 制度库（`MOCK_POLICY_REGISTRY`），条款逐条登记 `POLICY_DOCUMENT` 证据，输出可回溯原文；`applicabilityNote` 固定说明“相关性 ≠ 适用性 ≠ 合规结论”，数值阈值与审批权限全部进入待人工确认项。

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
| 230128 | 任务当前状态不可恢复执行 |

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
| `dd_agent_execution` | Agent 节点执行记录（P5） | `uk_task_graph_node_no`；`idx_task_id`、`idx_task_status` |

公共约定：雪花 `BIGINT` 主键；`version` 乐观锁；`del_flag` 逻辑删除；`creator/updater/create_time/update_time` 自动填充；时间 `DATETIME(3)`；JSON 列存业务扩展。

P2 对已建库环境提供 `dd_evidence_p2_upgrade` 存储过程按需补列，脚本整体可重复执行。

## 4. 异步机制与一致性

- 提交任务在同一事务内写 `dd_task` 状态与 `dd_outbox_event`（`TASK_SUBMITTED`）。
- `DdOutboxPublisher` 每 1s 扫描 `PENDING` 且到期的记录（批量 20），发布 Spring 本地事件后置 `PUBLISHED`；失败累加 `retry_count`，最多 5 次，指数退避（60s 起，上限 300s）。
- `DdTaskInitHandler` 消费 `TASK_SUBMITTED`：条件更新抢占 `INITIALIZING → SCENARIO_IDENTIFYING`，执行规则识别与基础证据采集后进入 `PLANNING`，随后同步生成计划并调度 Graph，完成后推进到 `ANALYZING`；重复投递因状态不符被忽略（幂等）。
- Graph 节点执行结果逐节点写 `dd_agent_execution`，与任务状态更新同一调用链路；恢复执行由 `POST /tasks/{taskId}/resume` 触发，任务需先处于 `FAILED`。
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
DdExecutionPersistenceIntegrationTest Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
DdFinancialCreditAgentIntegrationTest Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
DdJudicialSiteAgentIntegrationTest  Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
DdDocumentIndustryAgentIntegrationTest Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
DdLoanPurposeFraudPolicyAgentIntegrationTest Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
TaskStateMachineTest                Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
合计                                 Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
全量 mvn clean install              BUILD SUCCESS
```

覆盖：企业复用与申请快照、提交→Outbox→场景识别→证据采集→计划生成→Graph 调度→ANALYZING 全链路、事件重复投递幂等、规则未命中进入待确认、取消后不可提交、状态机合法/非法流转、详情与分页过滤、场景重新识别版本递增、SSE 端点状态快照推送；Mock 证据标记与来源、采集幂等、证据分页三类过滤、证据详情溯源字段、证据不存在（230115）与任务不存在（230101）；Agent 框架的工具发现/未知工具（230117）、统一社会信用代码三类校验、Mock 查询工具证据返回、主体核验 Agent 的结构化结果与待核实线索、未知 Agent（230118）；Prompt 版本发现与变量渲染、模板/变量缺失错误码（230120/230121）、Spring AI ChatClient Mock 标记与结构化输出（BeanOutputConverter）、OpenAI 兼容协议请求/响应映射（鉴权头、模型参数、500→230123、非法输出→230124、缺配置启动失败）、Spring AI Alibaba Graph 依赖分层执行与上游结果传递、未交付节点标记 `NOT_DELIVERED` 且下游跳过、循环依赖、空图与框架不支持的分支形态校验（230122）；Planner 规则计划生成与版本落库、`planner_mode=MOCK` 显式标记、计划转 Graph 后 10 个已交付 Agent 全部 `SUCCEEDED` 且未交付 Agent 标记 `NOT_DELIVERED`、重复生成计划版本递增；以及 14 个 HTTP 端点的路径映射、查询参数绑定、`Result` 包装、参数校验错误码（000400）与业务错误码返回。执行记录相关覆盖：节点结果落库与分页过滤、重复执行序号递增、恢复复用成功节点且重跑失败节点、非失败任务恢复返回 230128、执行记录接口不返回结果全文。专业 Agent 相关覆盖：财务分析指标计算与纳税/流水勾稽、征信债务的逾期/担保/期限结构与上游财务复用、司法风险的涉诉/执行/处罚与主体模糊匹配降级、现场调查的 FACT/STATEMENT 区分与访谈偏差、材料识别的必备材料缺失/过期/重复/无法归类、行业专家的行业识别与基准对比、贷款用途的分类/需求测算/合同与资金流向线索/两类还款来源、反欺诈的规则编号与版本/人工核查结果占位/待核实线索、政策匹配的制度条款原文与页码版本/待人工确认项/适用性边界；各 Agent 均校验 Mock 告警、证据登记与数据缺口输出。

> 加固说明：测试发现 `omni-bank-common-mybatis` 的 `MybatisPlusConfig`（分页插件）与 `AuditMetaObjectHandler`（审计填充）未登记自动装配，导致分页 `total` 恒为 0、`creator/updater` 不填充。已新增 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 修复，全仓模块统一生效。

## 6. 未完成事项与待确认

1. **Agent 11–12 未实现**：综合风险、尽调报告（P7）仅有契约模型与依赖登记，分析逻辑、Prompt、输入输出 Schema 与专属工具待后续阶段交付；调度中标记 `NOT_DELIVERED`。
2. **P6 之后链路未实现**：真实异步任务队列、增量分析（仅支持失败恢复，不支持按数据版本增量重跑）、风险测算、报告生成、人工复核、Finding / 补充资料 / 报告章节落库。
3. **Agent 01–10 尚未接入大模型**：当前均为确定性规则引擎；Prompt 模板与 Spring AI `ChatClient` 已就绪，切换需在 Agent 内组装 Prompt、校验输出并记录 `modelVersion` / `promptVersion`。
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
14. **工具层能力待补齐**：参数 Schema 校验、权限边界、超时控制与调用审计尚未实现。

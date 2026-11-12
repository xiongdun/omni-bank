# 银行对公信贷智能尽调 Agent（P0-P9 交付说明）

本说明对应 REQUIREMENTS 第 12 条交付要求，覆盖当前已完成范围：**P0 工程骨架 + P1 模块一（尽调任务与企业上下文）+ P2 证据中心与 Mock 数据适配 + P3 Agent 框架底座（Tool Registry + Agent Runtime + Agent 01）+ P4 Prompt Manager、大模型调用、Planner 计划生成与 Graph 编排调度 + P5 专业 Agent（Agent 01–07）与节点执行记录持久化、断点恢复 + P6 专业 Agent（Agent 08–10）+ P7 综合风险评估（Agent 11）、尽调报告（Agent 12）与报告人工复核闭环（状态机、字段级修正留痕、复核接口）+ P9 线索/补充材料落库、计划查询、证据关联溯源、状态闭环与增量重分析**。
12 个专项 Agent 已全部交付（主体核验、财务分析、司法风险、征信债务、现场调查、材料识别、行业专家、贷款用途、反欺诈、政策匹配、综合风险评估、尽调报告），统一采用确定性规则引擎，不调用大模型；报告章节与版本已落库，人工复核全流程已接入，Word/PDF 导出尚未接入并在结果中显式标注。

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

服务内路径前缀为 `/v1/dd`（任务类接口在 `/v1/dd/tasks`，计划与证据接口在 `/v1/dd/plans`、`/v1/dd/evidences`）；经网关转发时对外为 `/api/agent/v1/dd/**`（网关已登记 `agent` 显式路由，`StripPrefix=1` 去前缀）。
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
| GET | `/v1/dd/tasks/{taskId}/findings` | 风险线索分页（agentCode / category / severity / status） |
| GET | `/v1/dd/tasks/{taskId}/supplements` | 补充材料请求分页（agentCode / status / source） |
| POST | `/v1/dd/tasks/{taskId}/supplements` | 手工创建补充材料请求（source=MANUAL） |
| POST | `/v1/dd/tasks/{taskId}/supplements/{requestId}/complete` | 完成补充材料请求，触发受影响 Agent 增量重分析 |
| GET | `/v1/dd/tasks/{taskId}/plans/current` | 任务当前尽调计划 |
| GET | `/v1/dd/tasks/{taskId}/plans` | 任务计划版本分页（planVersion 倒序） |
| GET | `/v1/dd/plans/{planId}` | 计划详情（六类计划输出） |
| POST | `/v1/dd/tasks/{taskId}/resume` | 失败任务断点恢复：复用成功节点，重跑失败与跳过节点 |
| GET | `/v1/dd/tasks/{taskId}/reports` | 报告版本分页（reportStatus） |
| GET | `/v1/dd/tasks/{taskId}/reports/{reportVersion}` | 报告详情：状态、章节内容与引用证据 |
| POST | `/v1/dd/tasks/{taskId}/reports/{reportVersion}/review` | 报告人工复核：提交/确认/补充说明/修正/驳回/要求补材料/要求重新分析/定稿 |
| GET | `/v1/dd/tasks/{taskId}/reports/{reportVersion}/reviews` | 报告复核留痕 |
| GET | `/v1/dd/evidences/{evidenceId}` | 证据详情与溯源字段 |
| GET | `/v1/dd/evidences/{evidenceId}/references` | 证据关联溯源：引用该证据的线索、报告版本与复核留痕 |

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
- 未交付 Agent（编码在目录中但未注册运行时）标记为 `NOT_DELIVERED`，不调用 `AgentRuntime`，下游汇聚节点仍放行，整图继续执行；当前 12 个 Agent 已全部交付，该分支不再触发，作为通用兜底能力保留。
- 每个 Agent 节点执行结果（状态、摘要、输入快照、`AgentResult` 全文、模型/Prompt 版本、耗时）落 `dd_agent_execution`；汇聚节点不记录。

### 尽调计划与调度（P4）

- `dd_plan` 按 `plan_version` 版本化保存计划，历史版本不覆盖；`dd_task.current_plan_id` 指向最新版本。
- 计划包含需求文档 §4.1 的六类输出：`agent_plan`、`data_plan`、`material_plan`、`dependency_plan`、`supplement_plan`、`execution_config`，并记录 `planner_mode`、`model_name`、`prompt_version`。
- `DdAgentCatalog` 登记规格文档 §5.1 的 12 个 Agent 与初始依赖，作为计划校验、规则兜底与 Graph 构建的唯一来源。
- `DdPlanner` 按 `omni.dd.llm.mode` 显式选择生成方式：
  - `OPENAI_COMPATIBLE`：渲染 `PLANNER` Prompt 后经 Spring AI `ChatClient` 生成结构化计划，Agent 编码、依赖闭包、无环与置信度校验失败返回 `230127`；不静默回退规则。
  - `MOCK`：按 Agent 目录规则生成，`planner_mode=MOCK`、`model_name=MOCK_RULE`，并在 `reasoning_summary` 标注规则生成与 Mock 证据数量。
- `DdPlanGraphFactory` 按依赖计算拓扑层级，同层 Agent 并行，层后插入 `join.{level}` 汇聚节点，规避 Spring AI Alibaba Graph 的扇出汇聚约束。
- `DdPlanScheduler` 在 `TASK_SUBMITTED` 消费链路内同步执行：`PLANNING → DATA_COLLECTING`，生成计划后执行 Graph，完成后推进到 `ANALYZING`，再按是否存在待补充材料推进到 `WAITING_FOR_SUPPLEMENT` 或 `RISK_ASSESSING → REPORT_GENERATING → WAITING_FOR_REVIEW`（P9）；失败统一标记任务 `FAILED`。

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

### 专业 Agent（P7）

P7 交付 Agent 11–12，仍为确定性规则引擎，`AgentResult.modelVersion=rule-engine`、`promptVersion=rule-1.0`。上游结果统一经 `UpstreamAgentResults` 归一化，同时兼容断点恢复后的 `Map` 形态；数据缺失只产生 `dataGap` / `supplementRequest`，不使 Agent 失败。

| Agent | 规则要点 | 工具编码 | 证据类型 |
|---|---|---|---|
| `COMPREHENSIVE_RISK_ASSESSMENT` | 汇总 10 个上游 Agent：主体/经营/财务/征信/司法/反欺诈/用途/担保/政策 9 个维度评估、跨 Agent Finding 去重、风险关系识别、第一/第二还款来源与担保分析、缓释建议、金额与期限关注点、数据完整性 | 无专属取数工具（读取 `AgentContext.upstreamResults`） | 汇总上游 `evidenceRefs` |
| `DUE_DILIGENCE_REPORT` | 按固定模板 18 章组装报告（必备章节 1/2/6/7/11/14，其中 2/6/7/11 必须有证据）；章节状态、证据引用、数据缺口、局限性说明；校验后按任务版本化落库 | `report.getTemplate`、`report.validateReport`、`report.createReportVersion` | 汇总上游 `evidenceRefs` |

- 综合风险：`riskLevel` 固定 `NOT_RATED` 并附说明“未提供本行正式风险评级标准，AI 不输出风险等级”；不输出最终审批金额与合规结论，`approvalNote` 固定标注“不代替最终审批人决策”。缺少财务/征信/用途三个必需上游时 `assessable=false`，原因逐条列出，仍输出各维度 `UNAVAILABLE` 状态与数据缺口。Finding 按共享 `evidenceId` 或 `category + 规范化标题` 用并查集去重合并，跨 Agent 同一事实生成 `CROSS_AGENT_SAME_FACT` 关系；HIGH/CRITICAL 风险转为 `NEEDS_VERIFICATION` Finding。置信度：可评估时 `min(0.90, 0.50 + 0.04 × 成功上游数)`，否则 `min(0.50, 0.20 + 0.03 × 成功上游数)`；`dataCompleteness = 成功上游数 / 10`。
- 尽调报告：模板 `REPORT_TEMPLATE_V1`（`ReportTemplateCatalog`）固定 18 章，章节来源 Agent 与 `DdAgentCatalog` 常量绑定；必备章节缺失时输出 `dataGap` / `supplementRequest` 并置章节 `MISSING_INPUT`，`draftComplete=false`；生成后 `reportStatus=AI_GENERATED`、`reviewStatus=NOT_SUBMITTED`，`generationNote` 标注“须客户经理复核确认”“不输出最终审批金额”。
- 报告持久化：`dd_due_diligence_report` 按 `task_id + report_version` 唯一，版本号由 `DdReportService.latest(taskId) + 1` 递增，历史版本不覆盖；`reportNo = RPT{taskId}-V{version}`，章节 JSON 存 `content_json`，证据 ID 存 `evidence_ids`。

### 报告人工复核（P7）

报告状态机：`DRAFT → AI_GENERATED → WAITING_FOR_REVIEW → REVIEWED → FINALIZED`，另有 `RETURNED_FOR_SUPPLEMENT`、`REANALYZING` 分支；`FINALIZED` 为终态。复核动作与流转：

| 动作 | 允许的起始状态 | 目标状态 | 必填 |
|---|---|---|---|
| `SUBMIT` | `AI_GENERATED` | `WAITING_FOR_REVIEW` | — |
| `CONFIRM` | `WAITING_FOR_REVIEW` | `REVIEWED` | — |
| `SUPPLEMENT` | `WAITING_FOR_REVIEW` / `REVIEWED` | 状态不变 | `remark` |
| `AMEND` | `WAITING_FOR_REVIEW` / `REVIEWED` | `WAITING_FOR_REVIEW` | `fieldKey`、`amendedValue`、`reason` |
| `REJECT`、`REQUEST_MATERIAL` | `WAITING_FOR_REVIEW` / `REVIEWED` | `RETURNED_FOR_SUPPLEMENT` | `reason` |
| `REANALYZE` | `RETURNED_FOR_SUPPLEMENT` | `REANALYZING` | `reason` |
| `FINALIZE` | `REVIEWED` | `FINALIZED` | — |

- 留痕：每次动作写 `dd_report_review`，记录动作、前后状态、复核人（取 `UserContext`，未登录回落 `system`）、原因、意见与关联证据；非法流转返回 `230130`，参数缺失返回 `230133`。
- 修正：`AMEND` 的 `fieldKey` 传章节编码，服务端读取并保存修正前章节内容到 `original_value`，同时把 `amended_value` 写回 `content_json`，实现字段级修正可追溯；章节不存在返回 `230131`。
- 并发：报告更新使用 `id + version` 条件更新，冲突返回 `230132`；`FINALIZED` 后拒绝任何复核动作。
- 边界：定稿仅表示尽调报告定稿，不代表信贷审批通过；Word/PDF 导出仍未接入，`exportInfo` 固定 `NOT_AVAILABLE`。
- 导出边界：Word/PDF 导出未接入（不引入 POI/PDF 依赖），`exportInfo.wordStatus` / `pdfStatus` 固定 `NOT_AVAILABLE` 并说明原因，仅产出导出元信息。

### 线索、补充材料与增量重分析（P9）

- 线索落库：Agent 节点执行成功且返回结果时，`DdAgentExecutionServiceImpl` 在同一调用链路内把 `AgentResult.findings` 写入 `dd_finding`、`supplementRequests` 写入 `dd_supplement_request`。同一 `task_id + agent_code` 每次真正执行先逻辑删除旧线索再写入，保留当前有效结论；`finding_code` 缺失时按 `FND-{executionId}-{序号}` 生成。
- 幂等：补充材料请求按 `task_id + data_code` 去重，已存在 `PENDING` 时跳过，重复投递事件不会重复生成。
- 查询：`GET /tasks/{taskId}/findings` 支持 Agent、分类、严重级别与状态过滤；`GET /tasks/{taskId}/supplements` 支持 Agent、状态与来源（`AGENT` / `MANUAL`）过滤。
- 手工补充：`POST /tasks/{taskId}/supplements` 创建 `source=MANUAL` 的待补充请求；`POST /tasks/{taskId}/supplements/{requestId}/complete` 仅允许 `PENDING` 完成，写入 `remark` 与 `completed_at`，请求不存在返回 `230135`，状态不符返回 `230136`，并发冲突返回 `230103`。
- 状态推进：Graph 执行完成后，存在 `PENDING` 补充请求则停在 `WAITING_FOR_SUPPLEMENT`；否则依次 `RISK_ASSESSING → REPORT_GENERATING → WAITING_FOR_REVIEW`。报告复核联动仅在任务处于 `WAITING_FOR_REVIEW` 时生效：`FINALIZE → COMPLETED`，`REJECT` / `REQUEST_MATERIAL → RETURNED`。
- 增量重分析：补充材料完成（任务处于 `WAITING_FOR_SUPPLEMENT` / `RETURNED`）或报告 `REANALYZE` 时发布 `DdReanalysisRequestedEvent`，由 `DdPlanScheduler` 计算「受影响 Agent + 传递依赖它的下游」闭包，任务进入 `REANALYZING` 后按当前计划重跑 Graph：闭包内节点重新执行，其余复用历史 `SUCCEEDED` 结果，完成后按上述规则重新推进状态并生成新的报告版本。影响集合为空时退化为全量重跑。
- 计划查询：`PlanVO` 拆解 `agent_plan`、`data_plan`、`material_plan`、`dependency_plan`、`supplement_plan`、`execution_config`，其余元信息原样返回；任务无当前计划返回 `230137`。
- 证据溯源：`GET /evidences/{evidenceId}/references` 返回引用该证据的线索、报告版本（按版本倒序）与复核留痕（按时间正序），匹配依据为 `evidenceRefs` / `evidence_ids` JSON 中的证据 ID。

### 任务状态

`DRAFT(0) → INITIALIZING(10) → SCENARIO_IDENTIFYING(20) → PLANNING(30) → DATA_COLLECTING(40) → ANALYZING(60) → WAITING_FOR_SUPPLEMENT(60) 或 RISK_ASSESSING(80) → REPORT_GENERATING(90) → WAITING_FOR_REVIEW(95) → COMPLETED(100)`。

分析完成后若存在待补充材料停在 `WAITING_FOR_SUPPLEMENT(60)`，补充完成后经 `REANALYZING(70)` 回到上述推进链路；复核退回进入 `RETURNED(80)`，再次重分析后重新生成报告。`FAILED(0)`、`CANCELLED(0)` 为异常与终止状态。非法流转返回 `230102`，并发更新冲突返回 `230103`。

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
| 230129 | 尽调报告不存在 |
| 230130 | 报告当前状态不允许该复核操作 |
| 230131 | 报告章节不存在 |
| 230132 | 报告已被并发修改 |
| 230133 | 复核参数不完整或非法 |
| 230134 | 风险线索不存在 |
| 230135 | 补充材料请求不存在 |
| 230136 | 补充材料请求当前状态不允许该操作 |
| 230137 | 尽调计划不存在 |

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
| `dd_due_diligence_report` | 尽调报告与版本（P7） | `uk_task_report_version`；`idx_task_id` |
| `dd_report_review` | 报告人工复核留痕（P7） | `idx_review_task_version`；`idx_review_report_id` |
| `dd_finding` | 风险线索（P9） | `idx_finding_task_agent`、`idx_finding_task_severity` |
| `dd_supplement_request` | 补充材料请求（P9） | `idx_supplement_task_status`、`idx_supplement_task_data` |

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
DdComprehensiveReportAgentIntegrationTest Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
DdReportReviewIntegrationTest        Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
DdFindingSupplementIntegrationTest   Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
TaskStateMachineTest                Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
合计                                 Tests run: 71, Failures: 0, Errors: 0, Skipped: 0
mvn -pl ...-agent-boot -am test      BUILD SUCCESS
```

覆盖：企业复用与申请快照、提交→Outbox→场景识别→证据采集→计划生成→Graph 调度→ANALYZING 全链路、事件重复投递幂等、规则未命中进入待确认、取消后不可提交、状态机合法/非法流转、详情与分页过滤、场景重新识别版本递增、SSE 端点状态快照推送；Mock 证据标记与来源、采集幂等、证据分页三类过滤、证据详情溯源字段、证据不存在（230115）与任务不存在（230101）；Agent 框架的工具发现/未知工具（230117）、统一社会信用代码三类校验、Mock 查询工具证据返回、主体核验 Agent 的结构化结果与待核实线索、未知 Agent（230118）；Prompt 版本发现与变量渲染、模板/变量缺失错误码（230120/230121）、Spring AI ChatClient Mock 标记与结构化输出（BeanOutputConverter）、OpenAI 兼容协议请求/响应映射（鉴权头、模型参数、500→230123、非法输出→230124、缺配置启动失败）、Spring AI Alibaba Graph 依赖分层执行与上游结果传递、未交付节点标记 `NOT_DELIVERED` 且下游跳过、循环依赖、空图与框架不支持的分支形态校验（230122）；Planner 规则计划生成与版本落库、`planner_mode=MOCK` 显式标记、计划转 Graph 后 12 个 Agent 全部 `SUCCEEDED` 且无 `NOT_DELIVERED` 节点、重复生成计划版本递增；以及 HTTP 端点的路径映射、查询参数绑定、`Result` 包装、参数校验错误码（000400）与业务错误码返回。执行记录相关覆盖：节点结果落库与分页过滤、重复执行序号递增、恢复复用成功节点且重跑失败节点、非失败任务恢复返回 230128、执行记录接口不返回结果全文。专业 Agent 相关覆盖：财务分析指标计算与纳税/流水勾稽、征信债务的逾期/担保/期限结构与上游财务复用、司法风险的涉诉/执行/处罚与主体模糊匹配降级、现场调查的 FACT/STATEMENT 区分与访谈偏差、材料识别的必备材料缺失/过期/重复/无法归类、行业专家的行业识别与基准对比、贷款用途的分类/需求测算/合同与资金流向线索/两类还款来源、反欺诈的规则编号与版本/人工核查结果占位/待核实线索、政策匹配的制度条款原文与页码版本/待人工确认项/适用性边界；各 Agent 均校验 Mock 告警、证据登记与数据缺口输出。综合风险与报告相关覆盖：缺上游时 `assessable=false` 与原因逐条列出、`riskLevel=NOT_RATED`、9 个维度、10 个缺失上游、还款来源标注“无法评估”、不输出最终审批金额与不代替审批人决策告警；18 章模板、`AI_GENERATED` / `NOT_SUBMITTED`、`draftComplete=false`、校验问题清单、导出 `NOT_AVAILABLE`、报告版本递增且历史不被覆盖、`content_json` 落库。报告复核相关覆盖：提交→确认→定稿流转与留痕顺序、定稿后拒绝复核（230130）、修正章节保留原始值与修正值并写回 `content_json`、驳回缺少原因（230133）、退回后不可确认、要求重新分析、报告分页/详情/复核接口与报告不存在（230129）。P9 相关覆盖：Agent 结果落库为可检索线索与待补充请求、重复投递不重复生成待补充请求、完成补充请求触发增量重分析并生成新的报告版本、计划查询与证据关联溯源接口返回 `Result` 包装。

> 加固说明：测试发现 `omni-bank-common-mybatis` 的 `MybatisPlusConfig`（分页插件）与 `AuditMetaObjectHandler`（审计填充）未登记自动装配，导致分页 `total` 恒为 0、`creator/updater` 不填充。已新增 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 修复，全仓模块统一生效。

## 6. 未完成事项与待确认

1. **报告导出未接入**：`dd_due_diligence_report` 已持久化章节与版本，`dd_report_review` 已支持复核留痕与字段级修正；Word/PDF 导出（固定 `NOT_AVAILABLE`，未引入 POI/PDF 依赖）待后续阶段。
2. **增量分析粒度有限**：当前按 Agent 依赖闭包重跑，不区分单条数据版本；`RISK_ASSESSING` / `REPORT_GENERATING` / `WAITING_FOR_REVIEW` 为确定性状态推进，未接入独立的风险评估与报告生成任务；真实异步任务队列未接入；正式风险评级标准缺失（当前 `riskLevel` 固定 `NOT_RATED`）。
3. **Agent 01–12 尚未接入大模型**：当前均为确定性规则引擎；Prompt 模板与 Spring AI `ChatClient` 已就绪，切换需在 Agent 内组装 Prompt、校验输出并记录 `modelVersion` / `promptVersion`。
4. **模型网关联调未完成**：`OPENAI_COMPATIBLE` 模式仅完成协议适配与本地模拟网关测试，真实网关地址、模型名称与配额待确认。
5. **真实银行/外部数据接口未对接**：当前仅 `MockBankDataAdapter`，切换真实接口需新增 `ExternalDataAdapter` 实现并关闭 `omni.dd.adapter.mock.enabled`。
6. **证据关联查询基于 JSON 匹配**：`/v1/dd/evidences/{evidenceId}/references` 通过 `evidenceRefs` / `evidence_ids` JSON 中的证据 ID 匹配，未建独立关联表；证据量级上升后建议改造为关联表。
7. **场景识别规则待业务确认**：当前按产品编码 + 贷款用途关键词配置在 `omni.dd.scenario.rules`，置信度阈值 0.60；场景识别的模型化改造未实施，P4 Planner 仅以规则识别结果为输入，规则口径与阈值需银行业务部门确认。
8. **鉴权未在 dd 端点启用**：当前依赖网关与 `UserContextFilter` 透传 `X-User-*`，内网直连 8109 时无鉴权。
9. **网关路由已登记但鉴权未打通**：`/api/agent/**` 显式路由已就位，但 system 域路由仍未登记，管理端完整链路需待 system 路由与 Sa-Token 鉴权贯通。
10. **Outbox 未接 MQ**：当前为单库自扫描 + Spring 本地事件，多实例部署时需替换为 MQ 或加分布式锁。
11. **SSE 多实例不互通**：内存注册表仅适用于单实例，水平扩展需引入 Redis Pub/Sub。
12. **H2 测试 DDL 与 MySQL DDL 双份维护**：`src/test/resources/dd-schema-h2.sql` 需随 `docs/sql/omni_bank_dd.sql` 同步。
13. **待确认字段**：`current_plan_id` 已由 P4 Planner 维护（指向 `dd_plan` 最新版本），`analysis_version` 的推进规则、`dd_evidence` 的证据类型/来源类型枚举扩展仍待后续阶段规格明确。
14. **工具层能力待补齐**：参数 Schema 校验、权限边界、超时控制与调用审计尚未实现。

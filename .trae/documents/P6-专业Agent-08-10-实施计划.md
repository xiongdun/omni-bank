# P6 实施计划：专业 Agent 08–10

## Context

P5 已交付 Agent 01–07（确定性规则引擎）与执行记录持久化、断点恢复，模块 57 个用例全绿。按规格文档 §4.8–4.10 / 主文档 §3.9–3.11，P6 交付剩余三个专业 Agent：

- `LOAN_PURPOSE_ANALYSIS`（Agent 08 贷款用途合理性）
- `FRAUD_DETECTION`（Agent 09 反欺诈）
- `POLICY_MATCHING`（Agent 10 审批政策匹配）

仍不接真实 LLM、向量库与外部接口：Mock 数据由统一社会信用代码确定性派生，制度检索使用本地 Mock 制度库，所有 Mock 结果在 `warnings` 显式提示。完成后 Planner/Graph 中仅 `COMPREHENSIVE_RISK_ASSESSMENT`、`DUE_DILIGENCE_REPORT` 保持 `NOT_DELIVERED`。

## 实现约定（沿用 P5）

- Agent：`@Component` 实现 `CreditAgent<Void, XxxData>`，`AgentResult.modelVersion=rule-engine`、`promptVersion=rule-1.0`。
- 工具：`@Component` 实现 `AgentTool<T>`，`ToolDefinition.of(code, name, desc, category, mock=true, timeout)`；数据由 `DdDeterministicMockData` 派生，禁止随机；金额 2 位 `HALF_UP`，除法判零。
- 证据：`DdMockEvidence.item(...)` 构造，`DdEvidenceService.recordEvidence(taskId, enterpriseId, item)` 幂等登记；幂等键 `taskId + sourceSystem + sourceReference`。
- 数据缺失只产出 `AgentDataGap` / `AgentSupplementRequest`，Agent 仍返回 `SUCCEEDED`；上游结果读取同时兼容对象与断点恢复后的 `Map` 形态。
- 不做最终审批结论、不编造制度阈值、资金回流/金额相近/时间接近只作线索。

## 一、Agent 08 贷款用途合理性

### 新增文件（`dd/ai/agent`、`dd/tool/impl`）

- `LoanPurposeAnalysisAgent.java`
- `LoanPurposeAnalysisData.java`
- `LoanPurposeSnapshots.java`（集中定义 5 个工具快照 record，减少碎片文件）
- `MockLoanPurposeClassifyTool.java`（`loanPurpose.classifyLoanPurpose`）
- `MockLoanPurposeWorkingCapitalTool.java`（`loanPurpose.calculateWorkingCapitalDemand`）
- `MockLoanPurposeProjectTool.java`（`loanPurpose.calculateProjectFundingDemand`）
- `MockLoanPurposeContractTool.java`（`loanPurpose.analyzeContract`）
- `MockLoanPurposeFundFlowTool.java`（`loanPurpose.analyzeFundFlow`）

### 分析规则

1. **用途分类**：按 `loanPurpose` + 产品编码关键词映射 `RAW_MATERIAL_PURCHASE / WORKING_CAPITAL / EQUIPMENT_PURCHASE / PROJECT_CONSTRUCTION / TRADE_FINANCE / OTHER`；与经营范围/主营比对，不一致输出 `NEEDS_VERIFICATION` 线索。
2. **需求测算**：流动资金类调 `calculateWorkingCapitalDemand`，公式 `年营业收入 ÷ 360 × 营运资金周转天数 − 自有资金`，来源固定为 `MOCK_CONFIG:DD_LOAN_PURPOSE_WORKING_CAPITAL_V1`；项目建设/设备类调 `calculateProjectFundingDemand`，公式 `项目总投资 − 自有资金`，来源 `MOCK_CONFIG:DD_LOAN_PURPOSE_PROJECT_FUNDING_V1`；两类公式严格分开。
3. **金额与缺口**：`资金缺口 = 测算需求 − 申请金额`；金额合理性分 `RATIONAL / SLIGHTLY_HIGH / EXCESSIVE / INSUFFICIENT / UNKNOWN`，超出需求 20% 输出 Finding（MEDIUM）。
4. **期限合理性**：流动资金类 3–12 个月、项目/设备类 12–60 个月；非 MONTH 单位或无期限记 `UNKNOWN` + DataGap。
5. **交易背景**：合同/订单/发票数量、金额、交易对手、用途一致性；不一致只输出待核实线索。
6. **资金流向**：关联账户、异常转账、回流线索，Finding 状态固定 `NEEDS_VERIFICATION`。
7. **还款来源**：第一还款来源读上游 `FINANCIAL_ANALYSIS` 的经营现金流/净利润，第二还款来源取担保方式；字段分开，`secondaryCannotReplacePrimary=true`。
8. **输出边界**：Data 不设“最终审批金额”字段，只保留 `approvalAmountNote`（AI 不输出最终审批金额）；另输出重点关注金额区间。

## 二、Agent 09 反欺诈

### 新增文件

- `FraudDetectionAgent.java`
- `FraudDetectionData.java`
- `FraudCheckSnapshots.java`
- `MockFraudIdentityTool.java`（`fraud.checkIdentityConsistency`）
- `MockFraudRelatedPartyTool.java`（`fraud.checkRelatedPartyTransactions`）
- `MockFraudFinancialTool.java`（`fraud.checkFinancialAnomalies`）
- `MockFraudContractTool.java`（`fraud.checkContractAnomalies`）
- `MockFraudFundFlowTool.java`（`fraud.checkFundFlowAnomalies`）

### 分析规则

- 快照统一输出 `RuleHit(ruleCode, ruleVersion, category, severity, title, hitData, description, suggestion)`；规则版本固定 `1.0`。
- 规则覆盖：主体名称模糊匹配、隐性关联关系、报表/纳税/流水差异、重复合同哈希、资金回流（金额相近 + 时间接近 + 路径）、异常转账。
- Agent 将 RuleHit 转为 `FraudRuleRecord`：规则编号、版本、命中数据、证据引用、命中时间、`manualVerificationResult=null`。
- 所有欺诈 Finding 状态固定 `NEEDS_VERIFICATION`，不输出“已认定欺诈”；财务/税务/流水差异同时列出可能合理原因（统计期间、未开票收入、现金结算、非本行账户、关联交易、重复统计）。
- 读取上游 7 个 Agent 结果（对象或 Map），上游缺失记 DataGap；不阻断执行。
- 输出关系路径、影响分析、待核实事项、调查建议。

## 三、Agent 10 审批政策匹配

### 新增文件

- `PolicyMatchingAgent.java`
- `PolicyMatchingData.java`
- `PolicySnapshots.java`
- `MockPolicyCatalog.java`（本地 Mock 制度库：3 部制度、每部 2 条条款，含版本/生效日期/页码/原文/来源文件/文件哈希/向量 ID）
- `MockPolicySearchTool.java`（`policy.searchApplicablePolicies`）
- `MockPolicyClauseTool.java`（`policy.retrievePolicyClauses`）

### 分析规则

1. 检索：按产品编码、贷款用途、行业、金额匹配 Mock 制度库，返回制度元数据；无命中输出 DataGap + 补充请求，仍返回 `SUCCEEDED`。
2. 条款：按制度 ID 返回条款原文、条款号、页码、版本、生效日期、来源文件、文件哈希、向量 ID；工具登记 `POLICY_DOCUMENT` 证据。
3. 适用性：仅关键词直配标记 `APPLICABLE`，其余 `NEEDS_MANUAL_CONFIRMATION`；`applicabilityNote` 固定说明“相关性 ≠ 适用性 ≠ 合规结论”。
4. 检查清单：客户准入、行业准入、产品适用、用途限制、金额/期限限制、担保条件、审批权限、例外审批。
5. 所有数值阈值与审批权限只引用 Mock 制度原文，列入 `pendingManualConfirmations`；不做合规结论。
6. 冲突：同一用途跨多部制度或条款口径不一致时输出 `policyConflicts`。

## 四、测试与既有用例适配

### 新增测试

`DdLoanPurposeFraudPolicyAgentIntegrationTest`（3 个用例）：

1. 贷款用途 Agent：分类非空、需求测算与缺口存在、合同/资金流向结果存在、无最终审批金额字段语义、Mock 告警、证据非空。
2. 反欺诈 Agent：规则记录非空且每条含规则编号/版本、`manualVerificationResult` 为空、资金类线索状态 `NEEDS_VERIFICATION`、Mock 告警、证据非空。
3. 政策匹配 Agent：适用制度与条款原文非空、条款有页码/版本、`pendingManualConfirmations` 非空、`applicabilityNote` 含“不等于”语义、Mock 告警、证据非空。

测试沿用 `@SpringBootTest(classes = DdTestApplication.class)` + `@ActiveProfiles("h2")` + `@Transactional`，通过 `AgentRuntime.execute(code, taskId, null)` 直接执行。

### 适配

- `DdPlannerIntegrationTest`：已交付列表加入 3 个新 Agent；`NOT_DELIVERED` 仅断言 `COMPREHENSIVE_RISK_ASSESSMENT`、`DUE_DILIGENCE_REPORT`。
- `DdExecutionPersistenceIntegrationTest`：未交付节点断言由 `LOAN_PURPOSE_ANALYSIS` 改为 `COMPREHENSIVE_RISK_ASSESSMENT`。

## 五、文档

- `omni-bank-service-agent/README.md`：
  - 标题与首段更新为 P0–P6，已交付 10 个 Agent，剩余 2 个。
  - 新增「专业 Agent（P6）」表格：3 个 Agent 的规则要点、工具编码、证据类型。
  - 未完成事项第 1 条改为仅 Agent 11–12（P7）。
  - 测试结果更新为 60 个用例，并补充 P6 覆盖说明。

## 六、验证

```bash
# 模块全量测试（H2，不依赖本地 MySQL）
mvn -ntp -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot test
```

成功标准：

1. 新增 3 个 Agent 用例通过，模块用例总数 60 且 0 失败。
2. `DdPlannerIntegrationTest` 中 10 个已交付 Agent 节点均为 `SUCCEEDED`，仅 2 个未交付节点为 `NOT_DELIVERED`，整图 `SUCCEEDED`。
3. 执行记录测试中未交付节点改判为 `COMPREHENSIVE_RISK_ASSESSMENT` 后通过。
4. 全量测试无 ArchUnit / 编译告警失败。

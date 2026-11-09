# 对公信贷智能尽调与风险审批 Agent
# Agent 开发规格说明书（Agent Development Specification）

- 文档版本：V1.0
- 适用范围：Planner + 12 个专业 Agent
- Agent 框架：Spring AI + Spring AI Alibaba
- 业务架构：主动式 Agent + Tool Calling + Graph Workflow
- 核心原则：结构化输出、证据驱动、可解释、可恢复、人工可复核

---

# 第一部分：Agent 总体设计

## 1.1 Agent 系统建设目标

本系统不是简单的多轮对话应用，而是一个面向银行对公信贷业务的专业 Agent 系统。

Agent 必须具备：

1. 业务场景理解能力。
2. 任务自主规划能力。
3. 工具选择和调用能力。
4. 多来源数据整合能力。
5. 专业领域分析能力。
6. 风险线索识别能力。
7. 证据关联能力。
8. 数据缺口判断能力。
9. 补充资料任务生成能力。
10. 结构化结果输出能力。

Agent 不能只根据用户输入生成一段自然语言。

每个 Agent 的输出必须是可被后续 Agent 消费的结构化数据。

## 1.2 Agent 架构分层

建议分为五层：

```text
Agent Graph
    |
    v
Agent Runtime
    |
    +----------------------+
    |                      |
    v                      v
Agent Context          Prompt Manager
    |
    v
Professional Agent
    |
    v
Tool Registry
    |
    v
Domain Tool
    |
    v
Adapter
    |
    v
External Systems
```

### Agent Graph

负责：

- 节点注册。
- 节点依赖。
- 并行执行。
- 条件路由。
- 状态持久化。
- 失败重试。
- 暂停和恢复。
- 增量重跑。

### Agent Runtime

负责：

- Agent 生命周期。
- 执行上下文。
- 模型调用。
- 工具调用。
- 超时控制。
- Token 统计。
- 结果校验。

### Agent Context

负责为每个 Agent 提供：

- taskId。
- enterpriseId。
- 授信申请信息。
- 企业主体上下文。
- 业务场景。
- 已采集数据。
- Evidence 引用。
- 上游 Agent 结果。
- 可用 Tool。
- 数据版本。
- 执行限制。

### Prompt Manager

负责：

- Prompt 版本管理。
- 模板渲染。
- 场景化 Prompt。
- 输出 Schema。
- Prompt 测试。
- Prompt 变更审计。

### Tool Registry

负责统一工具发现、参数校验、权限边界、调用、异常处理和结果标准化。

---

# 第二部分：Agent 标准开发规范

## 2.1 Agent 统一接口

所有 Agent 必须实现统一抽象接口。

```java
public interface CreditAgent<I, O> {

    String getAgentCode();

    String getAgentName();

    AgentDefinition getDefinition();

    AgentResult<O> execute(
        AgentContext context,
        I input
    );

}
```

对于长时间运行的 Agent，实际运行应通过异步 Runtime 提交任务，不要求 HTTP 请求同步等待 Agent 完成。

Agent 接口是逻辑抽象，不应将执行线程、重试和持久化全部交给 Agent 自己实现。

## 2.2 AgentDefinition

```java
public class AgentDefinition {

    private String agentCode;

    private String agentName;

    private String description;

    private String version;

    private String promptVersion;

    private String inputSchema;

    private String outputSchema;

    private List<String> dependencies;

    private List<String> requiredTools;

    private boolean enabled;

    private boolean supportsIncrementalExecution;

}
```

## 2.3 AgentContext

```java
public class AgentContext {

    private String taskId;

    private String enterpriseId;

    private String executionId;

    private String businessScenario;

    private String subScenario;

    private EnterpriseProfileDTO enterpriseProfile;

    private CreditApplicationDTO creditApplication;

    private List<EvidenceDTO> evidences;

    private Map<String, Object> upstreamResults;

    private List<String> availableTools;

    private Map<String, String> inputVersions;

    private String traceId;

    private boolean mockMode;

}
```

AgentContext 不能无限制地装入所有企业资料。

应根据 Agent 的职责，通过上下文组装器只提供相关数据，避免上下文膨胀、数据泄露和 Token 浪费。

## 2.4 AgentResult

统一结果：

```java
public class AgentResult<T> {

    private String agentCode;

    private String executionId;

    private String status;

    private String summary;

    private T data;

    private List<AgentFindingDTO> findings;

    private List<DataGapDTO> dataGaps;

    private List<SupplementRequestDTO> supplementRequests;

    private List<EvidenceReferenceDTO> evidenceRefs;

    private BigDecimal confidence;

    private BigDecimal evidenceCompleteness;

    private List<String> warnings;

    private String modelVersion;

    private String promptVersion;

    private LocalDateTime generatedAt;

}
```

## 2.5 Finding 统一模型

```java
public class AgentFindingDTO {

    private String findingCode;

    private String category;

    private String severity;

    private String title;

    private String description;

    private BigDecimal confidence;

    private String status;

    private List<EvidenceReferenceDTO> evidenceRefs;

    private List<String> relatedFindingCodes;

    private String verificationSuggestion;

}
```

severity：

- LOW
- MEDIUM
- HIGH
- CRITICAL
- UNKNOWN

status：

- CONFIRMED：有充分证据并经必要核实。
- NEEDS_VERIFICATION：需要进一步核实。
- MODEL_INFERENCE：模型推断。
- REJECTED：人工复核后不成立。
- RESOLVED：问题已得到处理。

AI 不得擅自把 NEEDS_VERIFICATION 转换为 CONFIRMED。

## 2.6 数据缺口统一模型

```java
public class DataGapDTO {

    private String dataCode;

    private String dataName;

    private String description;

    private boolean required;

    private String sourceType;

    private String impact;

    private String suggestedAction;

}
```

数据缺口必须区分：

- REQUIRED：关键必需数据。
- OPTIONAL：可选数据。
- UNAVAILABLE：接口不可用。
- NOT_PROVIDED：客户未提供。
- NOT_APPLICABLE：业务不适用。
- UNKNOWN：尚不能判断。

## 2.7 Agent 执行原则

### 原则一：不允许伪造事实

不得凭空生成：

- 企业经营数据。
- 财务指标。
- 征信结果。
- 司法记录。
- 银行内部政策。
- 审批权限。
- 贷款余额。
- 资金流水。
- 客户调查事实。

### 原则二：结论必须关联证据

每条重要 Finding 必须关联 Evidence。

没有证据的结论必须标记为模型推断或待核实。

### 原则三：不将接口失败等同于无风险

例如：

征信接口超时，不代表企业没有逾期。

工商接口不可用，不代表企业主体不存在。

财务材料缺失，不代表企业财务正常。

### 原则四：结构化优先

Agent 先输出符合 JSON Schema 的结构化结果。

前端展示、报告生成和下游 Agent 消费均基于结构化结果。

自然语言内容仅作为展示层表达。

### 原则五：可增量执行

Agent 必须记录：

- 输入数据版本。
- 使用 Evidence。
- 上游 Agent 版本。
- Prompt 版本。
- 模型版本。
- 输出版本。

输入发生变化时，可以只重新执行受影响部分。

---

# 第三部分：Planner 开发规格

## 3.1 定位

Planner 是公共规划组件，不是第 13 个专业 Agent。

负责：

- 自动识别业务场景。
- 自动制定尽调计划。
- 选择专业 Agent。
- 识别数据需求。
- 识别材料需求。
- 规划数据采集顺序。
- 识别场景不确定性。

## 3.2 输入

- 授信申请上下文。
- 企业主体信息。
- 产品信息。
- 贷款用途。
- 申请金额。
- 申请期限。
- 企业经营信息。
- 已上传材料。
- 已获取数据。
- 可用工具。
- 场景分类配置。

## 3.3 业务场景识别

至少支持：

1. 流动资金贷款。
2. 固定资产贷款。
3. 小微经营贷款。
4. 贸易融资。
5. 综合授信。
6. 其他待确认场景。

识别依据：

- 产品编码和产品名称。
- 贷款用途。
- 授信申请内容。
- 企业经营业务。
- 交易背景。
- 相关材料。

产品名称不能作为唯一分类依据。

例如产品名称为“综合授信”，但实际业务用途为原材料采购，Planner 需要结合授信结构和申请信息识别其具体场景，不能只按产品名称分类。

## 3.4 Planner 输出

```json
{
  "businessScenario": "WORKING_CAPITAL_LOAN",
  "subScenario": "RAW_MATERIAL_PURCHASE",
  "confidence": 0.92,
  "scenarioStatus": "CONFIRMED_BY_MODEL",
  "reasoningSummary": "结合授信产品和用途识别",
  "requiredAgents": [
    "SUBJECT_VERIFICATION",
    "FINANCIAL_ANALYSIS",
    "CREDIT_DEBT_ANALYSIS",
    "LOAN_PURPOSE",
    "FRAUD_DETECTION"
  ],
  "requiredData": [
    "ENTERPRISE_PROFILE",
    "FINANCIAL_STATEMENTS",
    "EXISTING_DEBT",
    "LOAN_APPLICATION"
  ],
  "requiredDocuments": [
    "BUSINESS_LICENSE",
    "FINANCIAL_STATEMENT"
  ],
  "optionalDocuments": [
    "BANK_STATEMENT",
    "AUDIT_REPORT"
  ],
  "unknowns": []
}
```

## 3.5 Planner 业务约束

1. 识别结果必须带置信度。
2. 低置信度不能强行执行专属业务分析。
3. 场景分类由配置管理。
4. 不能只依赖 Prompt 中的硬编码枚举。
5. 可以允许客户经理纠正结果。
6. 人工纠正后重新生成计划。
7. 每次规划生成独立版本。
8. 计划必须支持后续增量调整。

---

# 第四部分：12 个专业 Agent 开发规格

# Agent 01：主体信息核验 Agent

## 4.1.1 基本信息

- agentCode：SUBJECT_VERIFICATION
- agentName：主体信息核验 Agent
- 主要目标：确认授信申请企业的主体身份，识别企业登记信息差异、股权结构和关联关系线索。

## 4.1.2 输入数据

- 企业名称。
- 统一社会信用代码。
- 行内企业客户编号。
- CRM 信息。
- 工商登记信息。
- 信贷系统企业信息。
- 股东及关联企业数据。
- 企业营业执照和章程等材料。

## 4.1.3 工具依赖

- enterprise.queryEnterprise
- enterprise.queryCrmCustomer
- enterprise.queryBankCustomer
- enterprise.queryShareholders
- enterprise.queryRelatedEnterprises
- enterprise.verifyIdentityConsistency

以上为系统内部工具命名建议，具体实现由 Tool Registry 注册。

## 4.1.4 分析任务

1. 识别企业主体。
2. 校验统一社会信用代码。
3. 比对行内客户信息和工商信息。
4. 核验企业名称、法定代表人、注册资本、成立时间。
5. 识别股权结构。
6. 识别实际控制人线索。
7. 识别分支机构。
8. 识别关联企业。
9. 检查企业经营状态。
10. 识别不同来源信息冲突。

## 4.1.5 输出结果

- 企业主体标准信息。
- 多来源字段对比。
- 股东结构。
- 实际控制人线索。
- 关联企业线索。
- 主体一致性分析。
- 主体风险 Finding。
- 数据缺口。
- 待核实事项。

## 4.1.6 特殊约束

- 名称相似不代表主体相同。
- 工商经营范围不代表实际主营业务。
- 多来源数据必须保留来源。
- 主体身份不确定时不得继续把不确定主体的风险数据作为确定事实。

---

# Agent 02：财务分析 Agent

## 4.2.1 基本信息

- agentCode：FINANCIAL_ANALYSIS
- agentName：财务分析 Agent
- 主要目标：分析企业财务质量、盈利能力、偿债能力、现金流和经营状况。

## 4.2.2 输入数据

- 资产负债表。
- 利润表。
- 现金流量表。
- 财务附注。
- 审计报告。
- 纳税申报数据。
- 银行流水。
- 应收账款。
- 应付账款。
- 存货。
- 固定资产。
- 借款明细。
- Agent 01 的企业主体结果。

## 4.2.3 工具依赖

- financial.getFinancialStatements
- financial.getTaxData
- financial.getBankStatements
- financial.extractFinancialIndicators
- financial.calculateDebtRatios
- financial.calculateProfitability
- financial.calculateOperatingEfficiency
- financial.analyzeCashFlow
- financial.checkFinancialConsistency
- financial.compareFinancialPeriods

## 4.2.4 分析任务

### A. 偿债能力

分析：

- 资产负债结构。
- 流动资产和流动负债。
- 短期偿债压力。
- 长期负债结构。
- 利息负担。
- 现金及现金等价物。

### B. 盈利能力

分析：

- 营业收入。
- 营业成本。
- 毛利率。
- 净利润。
- 利润变化。
- 盈利稳定性。

### C. 营运能力

分析：

- 应收账款周转。
- 存货周转。
- 应付账款变化。
- 经营周期。

### D. 现金流分析

重点关注：

- 经营活动现金流。
- 投资活动现金流。
- 筹资活动现金流。
- 净利润与经营现金流差异。
- 现金流持续性。

### E. 财务质量

分析：

- 报表勾稽关系。
- 财务数据异常波动。
- 财务、税务和银行流水差异。
- 异常收入和成本。
- 应收账款和存货异常增长。

## 4.2.5 输出结果

- 财务指标。
- 指标计算过程。
- 历史趋势。
- 财务质量评价。
- 现金流分析。
- 偿债能力分析。
- 财务异常 Finding。
- 数据缺口。
- 补充资料建议。

## 4.2.6 特殊约束

1. 不同期间不能直接混算。
2. 合并报表和单体报表必须区分。
3. 金额单位和币种必须明确。
4. OCR 数据需要保留置信度。
5. 缺少数据不能自动填充。
6. 不能把通用财务指标阈值当成银行准入规则。

---

# Agent 03：司法风险 Agent

## 4.3.1 基本信息

- agentCode：JUDICIAL_RISK
- agentName：司法风险 Agent
- 目标：识别企业及其相关主体的司法、执行和行政处罚风险。

## 4.3.2 输入数据

- 企业主体信息。
- 法定代表人。
- 股东。
- 实际控制人。
- 关联企业。
- 司法数据。
- 行政处罚数据。
- 工商异常数据。

## 4.3.3 工具依赖

- judicial.searchLawsuits
- judicial.searchEnforcement
- judicial.searchDishonestExecutions
- judicial.searchRestrictionHighConsumption
- judicial.searchCourtAnnouncements
- judicial.searchJudgments
- judicial.searchAdministrativePenalties
- judicial.searchEquityFreeze
- judicial.verifySubjectMatch

## 4.3.4 分析任务

1. 识别诉讼记录。
2. 识别被执行记录。
3. 识别失信被执行记录。
4. 识别限制高消费。
5. 识别行政处罚。
6. 识别经营异常。
7. 识别股权冻结。
8. 分析案件类型、金额、角色和阶段。
9. 识别企业及关联方之间的司法风险关联。

## 4.3.5 输出结果

每条司法记录至少包含：

- 案件类型。
- 当事人角色。
- 涉案金额。
- 案件阶段。
- 发生时间。
- 法院或主管机构。
- 数据来源。
- 主体匹配依据。
- Evidence。
- 风险影响。
- 待核实事项。

## 4.3.6 特殊约束

不能仅凭企业名称模糊命中直接形成风险结论。

必须区分：

- 原告。
- 被告。
- 申请执行人。
- 被执行人。
- 第三人。
- 其他诉讼参与人。

---

# Agent 04：征信与债务分析 Agent

## 4.4.1 基本信息

- agentCode：CREDIT_DEBT_ANALYSIS
- agentName：征信与债务分析 Agent
- 目标：分析企业融资结构、债务压力、逾期欠息、担保关系和集中到期风险。

## 4.4.2 输入数据

- 企业征信报告。
- 现有授信。
- 贷款余额。
- 贷款期限。
- 逾期信息。
- 欠息信息。
- 展期信息。
- 借新还旧信息。
- 对外担保。
- 企业财务数据。
- 银行流水。

## 4.4.3 工具依赖

- credit.queryEnterpriseCreditReport
- credit.queryFinancingBalance
- credit.queryGuarantees
- credit.queryOverdueRecords
- credit.queryDebtMaturity
- credit.analyzeDebtStructure
- credit.analyzeGuaranteeChain
- credit.compareDebtWithFinancialData

## 4.4.4 分析任务

1. 分析企业融资结构。
2. 分析贷款余额。
3. 分析融资机构分布。
4. 分析集中到期。
5. 分析逾期和欠息。
6. 分析对外担保。
7. 分析担保链。
8. 分析融资变化趋势。
9. 对比征信、财务和流水数据。

## 4.4.5 输出结果

- 现有融资结构。
- 债务期限结构。
- 债务到期计划。
- 对外担保情况。
- 逾期欠息情况。
- 债务异常 Finding。
- 数据完整性。
- 补充调查建议。

## 4.4.6 特殊约束

没有获取征信数据时必须输出数据未获取。

不能将接口失败解释为企业不存在逾期或负债。

---

# Agent 05：现场调查 Agent

## 4.5.1 基本信息

- agentCode：SITE_VISIT
- agentName：现场调查 Agent
- 目标：分析客户经理提供的现场调查记录、访谈、图片和视频等材料。

## 4.5.2 输入数据

- 客户经理调查记录。
- 企业访谈纪要。
- 现场照片。
- 视频摘要。
- 经营场所信息。
- 生产设备信息。
- 库存信息。
- 员工信息。
- 上下游情况。
- Agent 01、02 的结果。

## 4.5.3 工具依赖

- siteVisit.getInvestigationRecords
- siteVisit.analyzeImages
- siteVisit.analyzeVideoSummary
- siteVisit.extractInterviewFacts
- siteVisit.compareBusinessWithRegistry
- siteVisit.compareWithFinancialData

## 4.5.4 分析任务

1. 识别现场材料内容。
2. 提取企业经营情况。
3. 分析生产经营场所。
4. 识别设备、库存和人员线索。
5. 分析访谈内容。
6. 比对工商经营范围。
7. 比对财务和经营数据。
8. 识别现场调查异常。

## 4.5.5 输出结果

- 现场调查摘要。
- 企业实际经营线索。
- 经营场所观察。
- 生产设备和库存线索。
- 访谈关键内容。
- 与其他数据的差异。
- 风险 Finding。
- 待核实事项。

## 4.5.6 特殊约束

Agent 不得声称实际到场调查。

照片中的可见内容只能作为观察证据，不应超出图片本身进行确定性推断。

---

# Agent 06：影像与材料识别 Agent

## 4.6.1 基本信息

- agentCode：DOCUMENT_RECOGNITION
- agentName：影像与材料识别 Agent
- 目标：完成材料分类、OCR、字段抽取、质量检测和证据结构化。

## 4.6.2 输入数据

- PDF。
- 图片。
- Word。
- Excel。
- 其他受支持文档。

## 4.6.3 工具依赖

- document.classifyDocument
- document.extractText
- document.performOCR
- document.extractStructuredFields
- document.checkDocumentQuality
- document.detectDuplicateDocument
- document.validateDocumentCompleteness
- document.createEvidence

## 4.6.4 分析任务

1. 文件格式识别。
2. 文档分类。
3. OCR 文字识别。
4. 结构化字段抽取。
5. 表格解析。
6. 页码和坐标提取。
7. 重复材料识别。
8. 材料质量检测。
9. 必需材料完整性判断。
10. Evidence 生成。

## 4.6.5 输出结果

- 文档类别。
- 文档结构。
- 抽取字段。
- OCR 原文。
- 页码和坐标。
- 字段置信度。
- 材料质量问题。
- 重复材料线索。
- Evidence 列表。
- 材料缺口。

## 4.6.6 特殊约束

OCR 结果不能直接证明文件真实有效。

材料重复识别需要结合文件 Hash、内容相似度和业务字段。

---

# Agent 07：行业专家 Agent

## 4.7.1 基本信息

- agentCode：INDUSTRY_EXPERT
- agentName：行业专家 Agent
- 目标：分析企业所处行业、行业环境、行业周期、产业链和竞争力。

## 4.7.2 输入数据

- 企业工商经营范围。
- 实际主营业务。
- 产品信息。
- 财务数据。
- 毛利率。
- 主要客户。
- 主要供应商。
- 经营区域。
- 行业研究报告。
- 行业政策。
- 行业统计数据。
- 原材料价格趋势。

## 4.7.3 工具依赖

- industry.classifyIndustry
- industry.getIndustryProfile
- industry.searchIndustryKnowledge
- industry.getIndustryResearch
- industry.getIndustryPolicy
- industry.getIndustryStatistics
- industry.getIndustryPriceTrend
- industry.getIndustryChain
- industry.compareEnterpriseWithIndustry
- industry.analyzeIndustryRisk

## 4.7.4 分析任务

1. 识别企业实际所属行业。
2. 分析行业发展阶段。
3. 分析行业周期。
4. 分析行业竞争格局。
5. 分析上下游产业链。
6. 分析行业政策变化。
7. 分析原材料和产品价格趋势。
8. 分析企业竞争力。
9. 对比企业财务指标与行业参考数据。
10. 识别行业集中度和周期性风险。

## 4.7.5 输出结果

- 行业分类。
- 实际主营业务。
- 行业发展环境。
- 行业政策。
- 行业周期。
- 产业链分析。
- 上下游风险。
- 企业竞争力。
- 行业指标对比。
- 风险线索。
- 授信关注事项。

## 4.7.6 特殊约束

工商经营范围不能直接等同于实际主营业务。

行业数据必须记录来源、发布时间、统计期间、区域和分类口径。

不能使用过期行业数据冒充最新数据。

---

# Agent 08：贷款用途合理性分析 Agent

## 4.8.1 基本信息

- agentCode：LOAN_PURPOSE_ANALYSIS
- agentName：贷款用途合理性分析 Agent
- 目标：分析申请金额、贷款用途、资金需求、交易背景、贷款期限和还款来源的合理性。

## 4.8.2 输入数据

- 授信申请信息。
- 产品信息。
- 申请金额。
- 申请期限。
- 贷款用途。
- 企业经营情况。
- 财务数据。
- 银行流水。
- 合同。
- 订单。
- 发票。
- 现有贷款余额。
- 债务到期计划。
- 现场调查结果。
- 行业经营周期。

## 4.8.3 工具依赖

- loanPurpose.classifyLoanPurpose
- loanPurpose.getBusinessScenario
- loanPurpose.getWorkingCapitalData
- loanPurpose.calculateWorkingCapitalDemand
- loanPurpose.calculateProjectFundingDemand
- loanPurpose.analyzeContract
- loanPurpose.verifyTransactionBackground
- loanPurpose.analyzeFundFlow
- loanPurpose.calculateRepaymentCapacity
- loanPurpose.matchExistingFinancing
- loanPurpose.analyzeLoanTerm

## 4.8.4 分析任务

### A. 贷款用途识别

识别：

- 原材料采购。
- 日常经营周转。
- 设备购置。
- 项目建设。
- 贸易融资。
- 其他用途。

### B. 资金需求分析

根据业务场景调用对应计算工具。

流动资金贷款、固定资产贷款、贸易融资等不能共用一个简单计算公式。

### C. 交易背景分析

比对：

- 合同。
- 订单。
- 发票。
- 交易对手。
- 企业主营业务。
- 资金支付对象。

### D. 资金流向分析

分析：

- 资金用途。
- 交易对手。
- 关联账户。
- 异常转账。
- 资金回流线索。

### E. 还款来源分析

第一还款来源：

- 经营现金流。
- 销售回款。
- 项目收入。

第二还款来源：

- 抵押物。
- 质押物。
- 保证人。

两者必须分开评价。

## 4.8.5 输出结果

- 申请金额。
- 贷款用途分类。
- 用途合理性。
- 资金需求测算。
- 资金缺口。
- 金额合理性。
- 期限合理性。
- 合同订单核验。
- 交易背景。
- 资金流向。
- 第一还款来源。
- 第二还款来源。
- 偿债能力。
- 用途异常。
- 补充调查建议。

## 4.8.6 特殊约束

1. 不能直接给出最终审批金额。
2. 不得编造银行流动资金测算公式。
3. 计算公式、参数和适用范围必须有配置来源。
4. 第二还款来源不能替代第一还款来源。
5. 资金回流只能作为风险线索，不能直接认定违规。

---

# Agent 09：反欺诈 Agent

## 4.9.1 基本信息

- agentCode：FRAUD_DETECTION
- agentName：反欺诈 Agent
- 目标：识别企业主体、关联关系、经营、财务、交易、资金和材料异常。

## 4.9.2 输入数据

- 主体核验结果。
- 股权关联关系。
- 财务分析结果。
- 税务数据。
- 银行流水。
- 司法信息。
- 征信信息。
- 合同。
- 订单。
- 发票。
- 现场调查结果。
- 贷款用途分析结果。

## 4.9.3 工具依赖

- fraud.checkIdentityConsistency
- fraud.checkRelatedPartyTransactions
- fraud.checkCircularTransactions
- fraud.checkFinancialAnomalies
- fraud.checkContractAnomalies
- fraud.checkDuplicateDocuments
- fraud.checkFundFlowAnomalies
- fraud.analyzeTransactionNetwork
- fraud.matchHistoricalRiskPatterns
- fraud.evaluateFraudClues

## 4.9.4 分析任务

1. 主体身份异常。
2. 关联关系异常。
3. 财务数据异常。
4. 虚增收入线索。
5. 虚构交易线索。
6. 资金回流线索。
7. 重复融资线索。
8. 异常担保线索。
9. 材料造假线索。
10. 多来源数据之间的矛盾。

## 4.9.5 反欺诈分析架构

反欺诈不能完全依赖 LLM。

建议：

- 确定性规则：执行明确规则。
- 图关系分析：识别企业、股东、交易对手、账户关系。
- 数据分析：分析金额、时间、频率和分布。
- 多模态模型：识别材料和影像异常线索。
- LLM：综合解释和关联分析。

## 4.9.6 输出结果

- 主体欺诈线索。
- 关联关系异常。
- 财务异常。
- 交易异常。
- 资金异常。
- 材料异常。
- 规则命中记录。
- 关系路径。
- Evidence。
- 风险影响。
- 待核实事项。
- 调查建议。

## 4.9.7 特殊约束

资金闭环、金额相近、时间接近、关联关系均只能构成风险线索。

必须保存规则编号、版本、命中数据和人工核查结果。

---

# Agent 10：审批政策匹配 Agent

## 4.10.1 基本信息

- agentCode：POLICY_MATCHING
- agentName：审批政策匹配 Agent
- 目标：通过 RAG 检索银行制度文件，找出与当前授信业务相关的政策和制度条款。

## 4.10.2 第一阶段范围

只实现制度文件 RAG 检索。

不将结构化规则引擎作为第一期硬依赖。

## 4.10.3 输入数据

- 业务场景。
- 产品类型。
- 贷款用途。
- 申请金额。
- 申请期限。
- 担保方式。
- 企业所属行业。
- 前置 Agent 分析结果。
- 已上传的银行制度文件。

## 4.10.4 工具依赖

- policy.searchApplicablePolicies
- policy.getPolicyVersion
- policy.retrievePolicyClauses
- policy.parsePolicyConditions
- policy.generatePolicyChecklist

## 4.10.5 RAG 流程

1. 接收业务上下文。
2. 生成检索查询。
3. 在 Milvus 中进行向量检索。
4. 可选使用全文检索补充。
5. 根据制度版本、生效时间和适用范围过滤。
6. 返回相关条款。
7. LLM 归纳条款内容。
8. 关联当前业务信息。
9. 输出匹配情况和待人工确认事项。

## 4.10.6 输出结果

- 相关制度。
- 制度版本。
- 生效时间。
- 相关条款。
- 原文片段。
- 来源页码。
- 适用性说明。
- 限制条款。
- 审批权限关注点。
- 制度冲突。
- 待人工确认事项。

## 4.10.7 特殊约束

所有政策判断必须能回溯制度原文。

不能根据通用金融知识编造银行内部政策。

RAG 的相关性不等于政策适用性，更不等于确定性的合规结论。

未来结构化规则引擎可以接入独立 Rule Tool，不改变 Agent 的基本输入输出契约。

---

# Agent 11：综合风险评估 Agent

## 4.11.1 基本信息

- agentCode：COMPREHENSIVE_RISK_ASSESSMENT
- agentName：综合风险评估 Agent
- 目标：综合前十个 Agent 的结构化结果，形成企业综合风险分析。

## 4.11.2 输入数据

- Agent 01—10 的结果。
- 所有相关 Evidence。
- 数据缺口。
- 人工复核结果。
- 风险缓释信息。
- 已取得的银行风险政策和风险偏好数据（如果提供）。

## 4.11.3 工具依赖

- risk.aggregateFindings
- risk.deduplicateFindings
- risk.calculateRiskIndicators
- risk.evaluateRiskRules
- risk.evaluateDataCompleteness
- risk.evaluateRepaymentCapacity
- risk.evaluateGuaranteeCoverage
- risk.calculateRecommendedExposure
- risk.evaluateRiskMitigation
- risk.generateRiskAssessment

## 4.11.4 分析任务

### A. 风险汇总

按照以下维度汇总：

- 主体。
- 经营。
- 财务。
- 信用。
- 司法。
- 欺诈。
- 用途。
- 担保。
- 政策。

### B. 风险去重

识别不同 Agent 是否对同一个事实产生重复 Finding。

例如：

司法 Agent 发现被执行记录。

征信 Agent 发现同一笔相关债务。

反欺诈 Agent 又引用了该债务。

综合评估时应识别它们之间的关系，不能简单累计三次风险。

### C. 还款能力

分别分析：

- 第一还款来源。
- 第二还款来源。

### D. 风险缓释

分析：

- 经营改善。
- 追加资料。
- 追加担保。
- 贷款用途控制。
- 贷后监测建议。
- 其他风险缓释措施。

## 4.11.5 输出结果

- 综合风险摘要。
- 各风险维度评价。
- 重大风险。
- 风险关联关系。
- 第一还款来源分析。
- 第二还款来源分析。
- 担保分析。
- 风险缓释建议。
- 金额和期限关注事项。
- 数据完整性。
- 评估可信程度。
- 是否具备形成综合评估的条件。

## 4.11.6 特殊约束

1. 允许输出暂不具备评估条件。
2. 不允许数据不足时强行给出风险等级。
3. 风险等级、证据完整程度和分析可信程度必须分开。
4. 没有银行正式评级标准时，不得虚构银行内部风险等级规则。
5. AI 不得代替最终审批人员决策。

---

# Agent 12：尽调报告生成 Agent

## 4.12.1 基本信息

- agentCode：DUE_DILIGENCE_REPORT
- agentName：尽调报告生成 Agent
- 目标：把上游结构化分析结果组织成完整、可追溯、可人工复核的尽调报告。

## 4.12.2 输入数据

- 授信申请信息。
- 企业主体信息。
- 股权及关联关系。
- Agent 01—11 结果。
- Evidence。
- 数据缺口。
- 补充资料任务。
- 人工复核意见。

## 4.12.3 工具依赖

- report.getTemplate
- report.buildReportData
- report.generateReportContent
- report.validateReport
- report.renderWord
- report.renderPdf
- report.createReportVersion
- report.getEvidenceReferences
- report.updateReport

## 4.12.4 报告生成流程

1. 获取当前有效 Agent 结果。
2. 检查分析结果完整性。
3. 组织报告章节。
4. 生成自然语言内容。
5. 校验必要字段。
6. 检查 Evidence 引用。
7. 检查数据缺口。
8. 生成交互式报告数据。
9. 渲染 Word/PDF。
10. 创建报告版本。

## 4.12.5 输出结果

- reportId。
- taskId。
- reportVersion。
- reportStatus。
- 报告章节。
- Evidence 引用。
- 数据缺口。
- 补充资料任务。
- 人工复核状态。
- 文件导出信息。

## 4.12.6 人工复核

客户经理可以：

- 确认。
- 驳回。
- 补充说明。
- 修正数据。
- 要求重新分析。
- 申请补充材料。

人工修正必须保留：

- 原始值。
- 修正值。
- 修正人。
- 修正时间。
- 修正原因。
- 关联 Evidence。

报告状态：

- DRAFT
- AI_GENERATED
- WAITING_FOR_REVIEW
- RETURNED_FOR_SUPPLEMENT
- REANALYZING
- REVIEWED
- FINALIZED

FINALIZED 仅表示 AI 辅助报告定稿，不代表授信审批通过。

---

# 第五部分：Agent 间协作与依赖

## 5.1 依赖关系

| Agent | 上游依赖 |
|---|---|
| 01 主体核验 | Planner |
| 02 财务分析 | 01、06 |
| 03 司法风险 | 01 |
| 04 征信债务 | 01 |
| 05 现场调查 | 06、01 |
| 06 材料识别 | Planner、任务材料 |
| 07 行业专家 | 01、02 |
| 08 贷款用途 | 02、07、授信申请 |
| 09 反欺诈 | 01、02、03、04、05、06、08 |
| 10 政策匹配 | 08、09 |
| 11 综合风险 | 01—10 |
| 12 报告生成 | 01—11 |

依赖是初始编排方案，Graph 必须允许按业务场景动态调整，不得将所有 Agent 都设计成固定强依赖。

## 5.2 并行执行

可以并行执行：

- Agent 01 和 Agent 06。
- Agent 03 和 Agent 04。
- Agent 02 和 Agent 07（前提是已取得必要主体上下文）。
- 与其他节点无强依赖的数据采集任务。

## 5.3 条件执行

例如：

没有财务报表：

- Agent 02 可以输出 INSUFFICIENT_DATA。
- Agent 07 可以继续行业分析。
- Agent 03 可以继续司法分析。
- Agent 04 可以继续征信分析。
- Agent 08 必须明确资金需求测算受限。
- Agent 11 需要判断是否具备综合评估条件。

不能因为 Agent 02 失败就终止整张 Graph。

## 5.4 增量执行

新增 Evidence 后：

1. 找到依赖该 Evidence 的 Agent。
2. 判断其输入版本是否发生变化。
3. 仅重新执行受影响节点。
4. 重新执行下游依赖节点。
5. 旧执行结果保留。
6. 生成新结果版本。

---

# 第六部分：Prompt 和模型管理

## 6.1 Prompt 管理

每个 Agent 必须独立管理 Prompt。

建议结构：

```text
prompts/
    planner/
        v1/
            system-prompt.txt
            user-template.txt
            output-schema.json

    subject-verification/
        v1/
            system-prompt.txt
            user-template.txt
            output-schema.json

    financial-analysis/
        v1/
            system-prompt.txt
            user-template.txt
            output-schema.json
```

## 6.2 Prompt 版本规范

每次修改 Prompt：

- 增加版本号。
- 记录修改原因。
- 保留历史版本。
- 执行回归测试。
- 记录对应模型版本。

不得直接覆盖生产使用的历史 Prompt。

## 6.3 模型管理

模型统一通过模型服务层接入。

需要支持：

- 云端模型。
- 企业内部模型。
- 本地 Ollama 模型。
- 不同模型的路由。
- 模型超时。
- 失败重试。
- 降级策略。
- Token 统计。

不允许业务 Agent 直接绑定某一个模型供应商的私有接口。

## 6.4 模型调用策略

不同任务可以选择不同模型：

| 任务 | 模型能力要求 |
|---|---|
| 业务场景识别 | 结构化理解 |
| 财务分析 | 数字理解和推理 |
| 司法风险 | 信息抽取和关联理解 |
| 行业分析 | 长上下文和知识归纳 |
| 反欺诈 | 复杂关系推理 |
| 政策检索 | RAG + 条款归纳 |
| 报告生成 | 长文本组织和结构化生成 |

模型选择通过配置管理，不写死在 Agent 代码中。

---

# 第七部分：Agent 运行与异常控制

## 7.1 超时控制

每个 Agent 和 Tool 均需配置独立超时时间。

不能所有 Agent 使用同一个超时值。

## 7.2 重试

仅对可重试错误执行重试：

- 网络超时。
- 模型服务暂时不可用。
- 外部接口暂时不可用。
- 临时限流。

以下错误通常不应盲目重试：

- 参数错误。
- 主体标识错误。
- 无权限。
- 数据格式不合法。
- 业务前置条件不满足。

## 7.3 熔断与降级

某个外部数据接口连续失败时：

- 熔断该接口。
- 记录失败状态。
- 允许其他 Agent 继续。
- 将缺失数据标记为 UNAVAILABLE。
- 支持恢复后重新采集。

## 7.4 断点恢复

每个 Graph 节点执行必须持久化必要状态。

任务重启后：

- 已完成节点不重复执行。
- 执行中断节点根据幂等策略恢复。
- 未执行节点继续执行。
- 失败节点按策略重试。

## 7.5 人工介入

以下情况需要允许人工介入：

- 业务场景不明确。
- 企业主体不一致。
- 关键材料缺失。
- 关键证据冲突。
- Agent 分析结果存在争议。
- 政策条款适用性不明确。
- 综合风险评估条件不足。
- 报告内容需要修正。

---

# 第八部分：Agent 可解释性和证据要求

## 8.1 结论必须可追溯

每条重要 Finding 应支持：

- 查看证据。
- 查看来源。
- 查看原文。
- 查看数据采集时间。
- 查看数据有效期间。
- 查看模型推断说明。
- 查看人工复核状态。

## 8.2 数据来源分级

建议：

- INTERNAL_SYSTEM：行内系统。
- AUTHORITATIVE_EXTERNAL：外部权威来源。
- CUSTOMER_DOCUMENT：客户上传材料。
- MANUAL_INVESTIGATION：人工调查。
- MANUAL_INPUT：人工录入。
- MODEL_INFERENCE：模型推断。
- MOCK：模拟数据。

## 8.3 Mock 结果

所有 Mock 结果必须贯穿整个 Graph。

下游 Agent 不能因为上游 Agent 对 Mock 数据进行了分析，就将结果转换为真实数据。

正式报告中必须显示 Mock 数据提示，演示数据不能冒充真实尽调结果。

---

# 第九部分：Agent 测试规范

## 9.1 单元测试

每个 Agent 必须测试：

- 正常输入。
- 缺失数据。
- 异常数据。
- 多来源冲突。
- Tool 失败。
- 模型异常。
- 结构化输出失败。
- Evidence 引用缺失。
- 重复执行。
- 增量执行。

## 9.2 场景测试

至少构建以下 Mock 测试企业：

### 场景 A：正常经营企业

- 企业主体资料完整。
- 财务报表完整。
- 征信正常数据可获取。
- 无明显司法异常。
- 申请流动资金贷款。

预期：正常完成主要 Agent 流程。

### 场景 B：财务资料缺失

- 主体信息完整。
- 缺少财务报表。
- 有部分工商和司法数据。

预期：财务分析标记数据不足，其他 Agent 继续执行。

### 场景 C：多来源主体信息冲突

- CRM 与工商名称不一致。
- 法定代表人信息不同。

预期：不覆盖原始值，生成主体差异 Finding。

### 场景 D：资金回流线索

- 存在交易对手关联关系。
- 交易金额相近。
- 资金转移时间接近。

预期：反欺诈输出待核实线索，不直接认定欺诈。

### 场景 E：审批制度缺失

- 没有上传对应制度文件。

预期：政策匹配 Agent 输出制度依据不足，不得根据通用金融知识伪造本行政策。

### 场景 F：外部接口不可用

- 工商接口超时。
- 司法接口正常。
- 征信接口正常。

预期：其他数据采集和分析继续，工商数据标记未获取。

### 场景 G：补充材料后增量重跑

- 初次缺少财务报表。
- 后续上传财务报表。

预期：重跑财务分析及其下游依赖，不重复执行无关 Agent。

## 9.3 评估指标

每个 Agent 应建立独立评估集。

建议评估：

- 结构化输出通过率。
- 字段抽取准确率。
- 证据引用完整率。
- 风险线索召回率。
- 风险线索准确率。
- 无依据结论比例。
- 工具调用成功率。
- 平均执行时间。
- Token 消耗。
- 人工修正率。

不能只通过模型回答是否流畅来评价 Agent 质量。

---

# 第十部分：Coding Agent 开发要求

## 10.1 开发原则

Coding Agent 必须先阅读当前项目代码和现有工程结构。

如果仓库为空，按照项目总体架构建立基础工程。

不允许重新设计已经明确的系统边界。

不允许擅自删除、合并或改变 12 个 Agent 的业务职责。

## 10.2 开发顺序

严格按照以下顺序：

### 第一阶段：基础工程

- 项目多模块结构。
- 前后端工程。
- 统一异常和响应。
- MySQL。
- MinIO。
- Redis。
- Docker Compose。
- OpenAPI。
- Mock Adapter。

### 第二阶段：模块一闭环

- 尽调任务中心。
- 企业主体上下文。
- 材料上传。
- Evidence Center。
- Tool Registry。
- Planner。
- 场景自动识别。
- 尽调计划生成。
- 前端任务详情。

### 第三阶段：Agent Runtime 和 Graph

- Agent 抽象接口。
- Agent Context。
- Agent Result。
- Prompt Manager。
- Tool Calling。
- Graph 节点。
- 并行执行。
- 状态持久化。
- 断点恢复。
- 增量重跑。

### 第四阶段：专业 Agent

建议顺序：

1. Agent 01 主体信息核验。
2. Agent 06 影像与材料识别。
3. Agent 02 财务分析。
4. Agent 03 司法风险。
5. Agent 04 征信与债务分析。
6. Agent 05 现场调查。
7. Agent 07 行业专家。
8. Agent 08 贷款用途合理性分析。
9. Agent 09 反欺诈。
10. Agent 10 审批政策匹配。
11. Agent 11 综合风险评估。
12. Agent 12 尽调报告生成。

### 第五阶段：业务闭环

- 补充材料闭环。
- 增量重跑。
- 综合风险评估。
- 报告生成。
- 人工复核。
- Word/PDF 导出。

## 10.3 数据库设计边界

本次不要求提前设计所有 Agent 的业务表。

数据库设计应遵循：

1. 模块一只设计基础任务、主体、材料、证据和工具调用所需的数据结构。
2. 开发具体 Agent 时，根据其实际输入、输出、Finding、Evidence、执行版本和业务关系设计对应数据模型。
3. 不能为了预先覆盖所有 Agent 而创建大量空泛字段。
4. 不能把所有 Agent 输出都塞进一个不可维护的大 JSON 字段。
5. 可以使用 JSON 保存适合扩展的模型上下文或非核心扩展数据，但核心业务字段应保持可查询、可校验。
6. 每个 Agent 的数据库变更通过 Flyway 版本迁移。
7. 数据模型设计需要配套 ER 图、字段说明、索引和迁移脚本。
8. Agent 开发阶段必须考虑历史执行记录和版本兼容。

## 10.4 每个 Agent 的交付物

每个 Agent 完成时必须交付：

- Agent 实现代码。
- AgentDefinition。
- 输入 DTO。
- 输出 DTO。
- Prompt 文件。
- JSON Schema。
- Tool 定义。
- Adapter Mock。
- 数据模型设计文档。
- Flyway 迁移脚本。
- 单元测试。
- 集成测试。
- 场景测试。
- README。
- API 文档更新。
- 运行和调试说明。

## 10.5 严禁事项

1. 不允许只输出伪代码。
2. 不允许只创建类和接口却没有实现。
3. 不允许编造银行真实接口。
4. 不允许编造银行审批制度。
5. 不允许将模型推断作为真实业务事实。
6. 不允许没有 Evidence 的重要风险结论直接进入正式报告。
7. 不允许一个 Agent 失败导致整个 Graph 无差别终止。
8. 不允许所有业务判断都写进 Prompt。
9. 不允许所有 Agent 使用同一个超大 Prompt。
10. 不允许人工修正覆盖原始数据。
11. 不允许用 Mock 数据生成无 Mock 标识的正式结果。
12. 不允许未经校验直接使用 LLM 返回的 JSON。
13. 不允许将数据库访问权限直接交给 LLM。

## 10.6 最终验收标准

项目最终必须达到：

1. 可以独立部署。
2. 可以本地 Mock 运行。
3. 能自动识别授信业务场景。
4. 能自动制定尽调计划。
5. 能调用 12 个专业 Agent。
6. 能调用行内和外部 Adapter。
7. 能并行执行专业 Agent。
8. 能记录完整证据链。
9. 能识别数据缺口。
10. 能生成补充材料任务。
11. 能实现增量重跑。
12. 能形成综合风险评估。
13. 能检索银行制度文件。
14. 能生成交互式尽调报告。
15. 能导出 Word 和 PDF。
16. 能支持客户经理人工复核。
17. 能记录模型、Prompt、工具和数据版本。
18. 能从任务中断状态恢复。
19. 能完整区分真实数据与 Mock 数据。
20. 所有关键结论均可追溯。

---

# 第十一部分：项目待银行确认事项

以下内容由实际银行接口和业务制度决定，Coding Agent 不得自行假设：

1. 信贷系统授信申请查询接口。
2. CRM 企业客户查询接口。
3. 行内客户信息查询接口。
4. 征信系统授权及查询接口。
5. 工商、税务、司法数据供应商。
6. 财务和银行流水数据获取能力。
7. 影像平台接口。
8. 企业客户编号与统一社会信用代码映射规则。
9. 银行对公信贷产品目录。
10. 各产品对应的真实尽调材料清单。
11. 流动资金测算适用制度和参数。
12. 固定资产贷款资金需求测算方法。
13. 真实审批制度文件及有效版本。
14. 银行风险评级和授信政策。
15. 生产环境模型部署要求。
16. 生产环境性能和 SLA。
17. 数据脱敏、存储期限和数据出域要求。
18. 外部数据查询授权及合规依据。

这些待确认项不应阻塞 Mock 模式下的工程开发。

---

# 最终开发指令

请将本文档作为 Agent 系统开发的业务规格和技术约束。

你的任务不是重新设计项目，而是按照本文档逐步实现可运行的软件系统。

优先完成基础工程和模块一，然后建设 Agent Runtime、Graph 和专业 Agent。

遇到真实银行接口、真实审批规则、真实数据字段等未提供信息时：

- 定义标准接口。
- 实现 Mock Adapter。
- 在文档中标记待确认事项。
- 不得自行编造银行业务规范。

遇到具体 Agent 的数据库设计问题时，必须结合该 Agent 的实际业务输入、输出和证据关系设计，而不是提前创建一个通用大而全的数据模型。

每个开发阶段都必须提供可运行代码、测试结果、配置文件、迁移脚本和开发说明。

最终目标是交付一套真正能够运行、扩展、调试和持续迭代的银行对公信贷智能尽调与风险审批 Agent 系统，而不是一套只具备演示性质的聊天机器人。
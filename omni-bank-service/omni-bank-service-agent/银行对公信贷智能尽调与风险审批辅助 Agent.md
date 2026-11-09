# 银行对公信贷智能尽调与风险审批辅助 Agent
# 项目需求规格说明书与技术设计基线

**文档版本：** V1.0  
**文档日期：** 2026-11-04  
**项目名称：** Corporate Credit Due Diligence Agent  
**项目定位：** 独立部署的银行对公信贷智能尽调与风险审批辅助应用  
**文档用途：** 作为 Coding Agent 开发整个项目的需求基线、架构约束、数据模型和验收依据。

---

# 第一部分：项目概述

## 1.1 项目背景

银行对公信贷业务涉及大量企业信息收集、资料审核、财务分析、司法风险查询、征信分析、行业分析、贷款用途核查、反欺诈分析、审批政策匹配和尽调报告编制工作。

传统尽调工作通常需要客户经理在多个业务系统之间切换，手动收集企业数据、整理材料、分析财务指标、核对政策条款，并最终形成尽调报告。

这一过程存在以下问题：

1. 企业数据分散在多个银行内部系统和外部数据平台。
2. 企业资料种类繁多，PDF、Word、Excel、图片、扫描件等格式混杂。
3. 财务、征信、司法、工商、税务等数据需要人工交叉核对。
4. 不同贷款业务场景需要不同的尽调重点和材料要求。
5. 银行制度和产品办法数量较多，人工查找相关政策条款效率较低。
6. 不同业务人员的经验和专业能力存在差异，尽调分析质量不完全一致。
7. 尽调报告编制耗时较长，风险分析结论和原始证据之间缺少统一关联。
8. 企业补充材料后，往往需要人工重新检查和更新报告。

因此，建设一套独立的 AI Agent 应用，通过 AI Agent、RAG、Tool Calling、工作流编排、数据分析和文档生成能力，辅助客户经理完成企业尽职调查及风险分析。

## 1.2 项目建设目标

建设一套独立的银行对公信贷智能尽调 Agent 应用，实现：

1. 企业主体信息自动采集和识别。
2. 授信申请业务类型、产品类型、贷款用途及融资场景自动识别。
3. 根据企业情况和业务场景自动生成尽调计划。
4. 自动调用银行现有系统和外部数据接口采集企业数据。
5. 支持企业资料上传、OCR、文件解析和结构化信息提取。
6. 通过多个专业 Agent 完成企业全方位尽调分析。
7. 通过 RAG 检索银行内部制度、产品办法和相关政策文件。
8. 识别企业经营、财务、司法、信用、关联关系、资金用途和反欺诈风险线索。
9. 自动检查尽调资料完整性，识别数据缺失和需要进一步核实的问题。
10. 支持客户经理补充材料，触发受影响 Agent 增量重分析。
11. 自动生成完整的对公信贷尽调报告。
12. 提供交互式报告页面，支持查看分析依据、原始证据和风险线索。
13. 支持报告导出为 Word 和 PDF。
14. 支持客户经理人工复核、修正、确认和退回。

## 1.3 项目定位和边界

本项目是一个独立的 AI Agent 应用，不是完整的银行信贷业务系统。

本项目不负责：

- 代替银行原有信贷系统。
- 代替原有授信申请、审批、合同和放款流程。
- 建设银行核心账务系统。
- 建设银行征信、工商、司法、税务等数据服务。
- 重新建设银行已有客户管理和企业信息管理系统。
- 直接作出具有银行业务效力的最终授信审批决定。
- 自动执行贷款发放、资金划转或其他账务操作。

本项目负责：

- 对接银行已有系统和外部数据服务。
- 获取企业主体、财务、征信、司法、工商、税务、现场调查等资料。
- 对企业资料进行智能分析。
- 识别风险线索和数据缺口。
- 检索和分析银行内部制度。
- 生成尽调分析结论和风险建议。
- 生成尽调报告，交由客户经理复核确认。

**最终业务边界：L3 审批辅助。**

AI 可以输出风险分析、政策匹配结果、风险提示、建议关注事项和报告内容，但不得把 AI 分析结果表述为银行最终审批结论。

报告定稿仅表示本应用内 AI 辅助尽调报告完成客户经理复核，不代表授信审批通过。

## 1.4 用户角色

第一期暂不实现登录、用户权限和组织机构管理，但业务模型应预留相关字段。

当前业务角色：

| 角色 | 职责 |
|---|---|
| 客户经理 | 创建尽调任务、提交资料、查看分析结果、补充材料、修正信息、复核报告 |
| AI Agent | 识别场景、采集数据、分析企业、发现风险、生成报告 |
| 系统管理员 | 管理模型、Prompt、数据源配置、制度文件和系统参数 |
| 业务配置人员 | 维护业务类型、产品目录、材料要求、制度文件等配置 |

第一期不建设用户认证和权限体系，但不得把后续用户身份、操作人、操作时间、操作原因等审计字段设计死。

---

# 第二部分：已确认的业务和技术决策

## 2.1 已确认业务决策

| 项目 | 确认方案 |
|---|---|
| 系统形态 | 独立部署的 AI Agent 应用 |
| 业务场景组织 | 全部业务类型由 Agent 自动识别 |
| 尽调模式 | 主动式，由系统主动采集数据并组织分析 |
| AI 决策边界 | L3 审批辅助 |
| 政策接入方式 | 第一阶段仅使用 RAG 检索制度文件 |
| 报告形式 | 交互式报告 + Word/PDF 导出 |
| 前后端 | 完全分离 |
| 前端部署 | 可独立部署 |
| 后端服务 | 业务服务独立、接口标准化 |
| 登录和权限 | 第一阶段暂不建设 |
| 现有系统集成 | 第一阶段不嵌入原有信贷系统，采用接口适配方式连接 |
| 企业识别 | 优先使用统一社会信用代码，同时兼容银行企业客户编号 |

## 2.2 已确认技术栈

| 技术领域 | 技术选型 |
|---|---|
| 编程语言 | Java 21 |
| 后端框架 | Spring Boot 3.x |
| Agent 框架 | Spring AI + Spring AI Alibaba |
| Agent 编排 | Spring AI Alibaba Graph |
| 关系数据库 | MySQL 8.0 |
| 向量数据库 | Milvus |
| 缓存 | Redis |
| 消息队列 | RocketMQ |
| 文件存储 | MinIO 或银行现有对象存储 |
| 前端 | Vue 3 + TypeScript |
| 前端 UI | Element Plus 或同类 Vue 组件库 |
| 前端状态管理 | Pinia |
| 前端路由 | Vue Router |
| 前后端通信 | REST API |
| 实时执行状态 | SSE |
| 文档生成 | Word 模板引擎 + PDF 转换 |
| 数据访问 | MyBatis-Plus 或 MyBatis |
| 参数校验 | Jakarta Validation |
| API 文档 | OpenAPI / Swagger |
| 日志 | SLF4J + Logback |
| 测试 | JUnit 5 + Mockito + Testcontainers（适用时） |

具体 Spring Boot、Spring AI、Spring AI Alibaba、Milvus SDK 等依赖版本，应在项目初始化时选择相互兼容的稳定版本，并统一锁定，不允许不同模块随意使用不兼容版本。

## 2.3 项目总体架构原则

1. 模块化单体优先，不因使用 Agent 就立即拆成微服务。
2. Agent 负责分析和决策辅助，业务规则和数据处理由 Java 服务负责。
3. LLM 不直接访问数据库。
4. LLM 不直接调用未经授权的银行接口。
5. 所有银行数据访问统一通过 Tool、Adapter 实现。
6. 所有重要分析结论必须关联证据。
7. 原始数据、标准化数据、AI 分析结果必须分层保存。
8. 不允许 AI 修改原始业务数据。
9. 业务场景识别和风险分析必须支持多版本。
10. Agent 执行必须支持异步、并行、重试、异常恢复和增量重分析。
11. 数据缺失不能直接等同于风险异常。
12. 不同数据来源、统计期间和数据口径必须保留。
13. 所有制度检索结果必须保留制度版本和原始条款。
14. 任何未经银行确认的接口、阈值和制度规则不得作为真实业务规则上线。

---

# 第三部分：项目功能总体规划

## 3.1 12 个专业 Agent

第一期包含以下 12 个专业 Agent。

| 编号 | Agent | 核心职责 |
|---|---|---|
| 01 | 企业主体识别 Agent | 企业身份识别、工商信息核验、主体一致性分析 |
| 02 | 财务分析 Agent | 财务报表解析、指标计算、偿债能力和财务异常分析 |
| 03 | 司法风险 Agent | 企业及关联主体司法风险分析 |
| 04 | 征信分析 Agent | 企业征信、负债、逾期、担保等信用风险分析 |
| 05 | 现场调查 Agent | 现场调查资料解析、调查结论和经营真实性分析 |
| 06 | 影像材料 Agent | 文件分类、OCR、信息提取、材料完整性和一致性检查 |
| 07 | 行业专家 Agent | 行业识别、行业环境、周期、竞争力及产业链风险分析 |
| 08 | 贷款用途合理性分析 Agent | 贷款用途、资金需求、交易背景、金额期限和还款来源分析 |
| 09 | 反欺诈 Agent | 主体、关联、财务、交易、资金和材料欺诈线索识别 |
| 10 | 审批政策匹配 Agent | 检索银行制度并结合企业信息进行政策匹配分析 |
| 11 | 综合风险评估 Agent | 汇总各 Agent 结果，形成综合风险分析 |
| 12 | 尽调报告生成 Agent | 将结构化分析和证据组织成完整尽调报告 |

上述 Agent 必须采用统一输入、统一输出、统一证据引用规范。

## 3.2 Agent 01：企业主体识别 Agent

### 职责

识别企业主体，核验企业基本信息及主体一致性。

### 输入数据

- 统一社会信用代码。
- 银行企业客户编号。
- 企业名称。
- 法定代表人。
- 工商注册信息。
- 股东及股权信息。
- 经营范围。
- 企业经营状态。
- 关联企业信息。

### 需要调用的工具

建议工具：

- `enterprise.getEnterpriseByCreditCode`
- `enterprise.getEnterpriseByCustomerId`
- `enterprise.getBasicInfo`
- `enterprise.getShareholders`
- `enterprise.getRelatedEnterprises`
- `enterprise.getBusinessScope`
- `enterprise.verifyIdentity`
- `enterprise.compareIdentityInformation`

实际接口字段由银行现有系统和外部数据服务提供，未确认前使用 Mock Adapter。

### 分析内容

1. 统一社会信用代码与企业名称是否匹配。
2. 银行内部客户编号与外部企业主体是否一致。
3. 企业是否正常存续。
4. 法定代表人、注册资本、成立日期等信息。
5. 股东及股权结构。
6. 关联企业和控制关系。
7. 企业经营范围与实际经营情况是否存在差异。
8. 不同数据源是否存在信息冲突。

### 输出

- 企业主体基本信息。
- 工商信息。
- 股权结构。
- 关联主体。
- 主体一致性结果。
- 主体异常线索。
- 数据冲突。
- 证据 ID。
- 待核实事项。

## 3.3 Agent 02：财务分析 Agent

### 职责

解析企业财务报表，计算关键财务指标，分析盈利能力、偿债能力、经营效率和财务异常。

### 输入数据

- 资产负债表。
- 利润表。
- 现金流量表。
- 审计报告。
- 财务附注。
- 税务数据。
- 银行流水。
- 应收账款明细。
- 应付账款明细。
- 存货明细。
- 借款和债务信息。
- 历史财务数据。

其中部分企业可以通过接口获取财务数据和交易流水，部分企业无法获取。系统必须支持接口数据和人工上传资料两种来源。

### 需要调用的工具

- `financial.parseFinancialStatement`
- `financial.normalizeFinancialData`
- `financial.calculateFinancialIndicators`
- `financial.analyzeProfitability`
- `financial.analyzeSolvency`
- `financial.analyzeOperatingEfficiency`
- `financial.analyzeCashFlow`
- `financial.compareFinancialPeriods`
- `financial.compareTaxAndFinancialData`
- `financial.analyzeFinancialAnomalies`

### 分析内容

1. 资产负债结构。
2. 营业收入及增长趋势。
3. 毛利率和净利润。
4. 资产负债率。
5. 流动比率和速动比率。
6. 应收账款周转情况。
7. 存货周转情况。
8. 经营活动现金流。
9. 利息保障能力。
10. 短期和长期偿债能力。
11. 财务数据连续性。
12. 财务、税务、流水之间的差异。

财务指标的计算必须由 Java 业务服务完成，LLM 负责解释和分析。

具体公式、数据口径和适用条件必须配置并保留版本，不能由 LLM 临时创造。

### 输出

- 标准化财务数据。
- 财务指标。
- 财务趋势。
- 盈利能力分析。
- 偿债能力分析。
- 现金流分析。
- 财务异常线索。
- 数据缺失。
- 补充调查建议。
- 证据引用。

## 3.4 Agent 03：司法风险 Agent

### 职责

分析企业及关联主体司法、执行和其他法律风险。

### 输入数据

- 企业工商主体。
- 法定代表人。
- 股东。
- 关联企业。
- 被执行人信息。
- 失信被执行人信息。
- 裁判文书。
- 开庭公告。
- 限制高消费信息。
- 行政处罚信息。
- 其他合法可获取的司法数据。

### 工具

- `judicial.getJudicialCases`
- `judicial.getExecutionRecords`
- `judicial.getDishonestRecords`
- `judicial.getCourtAnnouncements`
- `judicial.getAdministrativePenalties`
- `judicial.getRestrictionRecords`
- `judicial.analyzeCaseContent`
- `judicial.evaluateJudicialRisk`

### 分析内容

1. 企业被执行情况。
2. 企业失信情况。
3. 重大诉讼。
4. 企业作为原告或被告的情况。
5. 案件金额及案件进展。
6. 法定代表人和股东关联司法风险。
7. 司法案件与经营活动的关联。
8. 司法事件对企业偿债能力的潜在影响。

不能仅凭诉讼数量判断企业存在重大风险，必须结合案件性质、金额、进展和关联程度分析。

### 输出

- 司法风险摘要。
- 司法案件列表。
- 案件类型。
- 涉案金额。
- 案件状态。
- 风险线索。
- 关联主体。
- 证据引用。
- 待核实事项。

## 3.5 Agent 04：征信分析 Agent

### 职责

分析企业及相关主体信用记录、负债、逾期和对外担保情况。

### 输入数据

- 企业征信报告。
- 银行内部授信信息。
- 当前贷款余额。
- 历史借款记录。
- 逾期记录。
- 对外担保。
- 关联融资信息。
- 债务到期计划。

### 工具

- `credit.getEnterpriseCreditReport`
- `credit.getLoanRecords`
- `credit.getOverdueRecords`
- `credit.getGuaranteeRecords`
- `credit.getDebtSummary`
- `credit.analyzeCreditHistory`
- `credit.evaluateCreditRisk`

### 分析内容

1. 当前融资余额。
2. 历史借款情况。
3. 逾期和欠息记录。
4. 对外担保。
5. 多头融资。
6. 融资集中度。
7. 债务到期分布。
8. 关联企业融资风险。
9. 银行内部历史合作情况。

### 输出

- 企业信用摘要。
- 当前债务情况。
- 逾期记录。
- 对外担保。
- 债务到期计划。
- 信用异常线索。
- 证据引用。
- 补充核实建议。

## 3.6 Agent 05：现场调查 Agent

### 职责

分析客户经理现场调查记录、现场照片、视频、调查报告和经营资料。

### 输入数据

- 现场调查报告。
- 现场照片。
- 现场视频。
- 企业生产场地信息。
- 仓储信息。
- 设备信息。
- 员工及经营情况。
- 主要客户和供应商调查资料。
- 现场核查结论。

### 工具

- `investigation.parseInvestigationReport`
- `investigation.analyzeSiteImages`
- `investigation.extractBusinessFacts`
- `investigation.compareWithRegistrationInfo`
- `investigation.compareWithFinancialData`
- `investigation.evaluateBusinessAuthenticity`

### 分析内容

1. 企业是否实际经营。
2. 经营场所是否与登记信息相符。
3. 生产设备和仓储情况。
4. 员工及经营规模。
5. 生产经营情况与财务报表是否匹配。
6. 现场调查记录是否完整。
7. 客户经理描述与其他数据是否存在矛盾。

AI 不能仅根据图片直接认定企业真实经营情况，必须明确区分图片观察事实和推断结论。

### 输出

- 现场调查摘要。
- 经营场所情况。
- 生产经营情况。
- 现场发现。
- 与财务和工商信息的差异。
- 待核实事项。
- 证据引用。

## 3.7 Agent 06：影像材料 Agent

### 职责

对企业上传的各类尽调资料进行分类、解析、信息提取和完整性检查。

### 支持材料

- 营业执照。
- 公司章程。
- 股东会决议。
- 财务报表。
- 审计报告。
- 纳税申报资料。
- 银行流水。
- 购销合同。
- 发票。
- 订单。
- 资产权属证明。
- 抵押物资料。
- 担保资料。
- 现场调查资料。
- 其他尽调文件。

### 工具

- `document.upload`
- `document.classify`
- `document.ocr`
- `document.parsePdf`
- `document.parseWord`
- `document.parseExcel`
- `document.extractFields`
- `document.validateMaterial`
- `document.compareDocuments`
- `document.detectDuplicate`

### 分析内容

1. 文件类型识别。
2. OCR 和文本提取。
3. 表格结构解析。
4. 关键字段提取。
5. 材料有效性检查。
6. 文件内容一致性检查。
7. 材料重复检测。
8. 材料缺失检测。
9. 证据归档。

### 输出

- 文件类型。
- 文件解析状态。
- 结构化字段。
- OCR 文本。
- 材料完整性结果。
- 重复材料。
- 材料异常。
- 证据 ID。
- 解析置信度。

OCR 提取结果不能直接当作真实业务数据，重要字段应保留原始文本、页码、位置、置信度和人工修正记录。

## 3.8 Agent 07：行业专家 Agent

### 职责

识别企业实际主营行业，分析行业发展环境、政策、周期、竞争和上下游风险。

### 输入数据

- 工商经营范围。
- 企业实际主营业务。
- 主要产品。
- 产品收入构成。
- 毛利率。
- 主要客户和供应商。
- 经营区域。
- 行业研究报告。
- 行业政策。
- 行业统计数据。
- 原材料价格。

必须区分工商登记经营范围与实际主营业务。

### 工具

- `industry.classifyIndustry`
- `industry.getIndustryProfile`
- `industry.searchIndustryKnowledge`
- `industry.getIndustryResearch`
- `industry.getIndustryPolicy`
- `industry.getIndustryStatistics`
- `industry.getIndustryPriceTrend`
- `industry.getIndustryChain`
- `industry.compareEnterpriseWithIndustry`
- `industry.analyzeIndustryRisk`

### 分析内容

1. 实际主营行业。
2. 行业发展阶段。
3. 行业周期。
4. 行业政策。
5. 竞争格局。
6. 上下游产业链。
7. 原材料价格波动。
8. 企业行业竞争力。
9. 企业经营指标与行业平均水平比较。
10. 行业系统性风险。

行业比较必须注明统计时间、地域、样本范围和指标口径。

### 输出

- 行业分类。
- 实际主营业务。
- 行业发展环境。
- 行业周期。
- 行业政策。
- 产业链分析。
- 企业竞争力。
- 行业风险。
- 授信关注事项。
- 证据引用。

## 3.9 Agent 08：贷款用途合理性分析 Agent

### 职责

分析贷款用途真实性、资金需求、金额和期限合理性、交易背景及还款来源。

### 输入数据

- 授信申请金额。
- 申请期限。
- 申请用途。
- 产品类型。
- 企业财务数据。
- 企业经营数据。
- 银行流水（如可获取）。
- 采购合同。
- 销售合同。
- 发票。
- 订单。
- 现有融资余额。
- 债务到期计划。
- 行业经营周期。
- 现场调查资料。

### 工具

- `loanPurpose.classifyLoanPurpose`
- `loanPurpose.getBusinessScenario`
- `loanPurpose.getWorkingCapitalData`
- `loanPurpose.calculateWorkingCapitalDemand`
- `loanPurpose.calculateProjectFundingDemand`
- `loanPurpose.analyzeContract`
- `loanPurpose.verifyTransactionBackground`
- `loanPurpose.analyzeFundFlow`
- `loanPurpose.calculateRepaymentCapacity`
- `loanPurpose.matchExistingFinancing`
- `loanPurpose.analyzeLoanTerm`

### 分析内容

1. 贷款用途分类。
2. 贷款用途与企业经营情况是否匹配。
3. 资金需求测算。
4. 实际资金缺口。
5. 申请金额合理性。
6. 贷款期限合理性。
7. 合同、订单和发票真实性线索。
8. 交易背景核验。
9. 资金流向分析。
10. 第一还款来源。
11. 第二还款来源。
12. 现有融资与重复融资风险。

第一还款来源主要是经营现金流、销售回款、项目收入等。

第二还款来源主要是抵押物、质押物和保证人等。

抵押物不能等同于企业自身偿债能力。

流动资金测算、项目融资测算等必须根据适用监管要求和银行制度配置。不同贷款场景不能套用同一个计算公式。

输出必须区分：

- 申请金额。
- 测算资金需求。
- 实际资金缺口。
- 需要重点关注的金额区间。
- 制度约束。
- 最终审批金额。

AI 不得直接确定最终审批金额。

### 输出

- 申请用途。
- 用途分类。
- 用途合理性。
- 资金需求测算。
- 金额合理性。
- 期限合理性。
- 合同订单核验。
- 交易背景。
- 资金流向分析。
- 第一还款来源。
- 第二还款来源。
- 偿债能力。
- 异常线索。
- 补充调查建议。

## 3.10 Agent 09：反欺诈 Agent

### 职责

识别企业主体、关联关系、财务、交易、资金流向和资料中的欺诈风险线索。

该 Agent 不能完全依赖 LLM，应使用规则、关系分析、数据分析、OCR 和 LLM 的组合方案。

### 输入数据

- 企业主体信息。
- 股东和关联关系。
- 财务数据。
- 税务数据。
- 银行流水。
- 司法信息。
- 征信信息。
- 合同、订单、发票。
- 现场调查。
- 交易对手信息。
- 贷款用途分析。

### 工具

- `fraud.checkIdentityConsistency`
- `fraud.checkRelatedPartyTransactions`
- `fraud.checkCircularTransactions`
- `fraud.checkFinancialAnomalies`
- `fraud.checkContractAnomalies`
- `fraud.checkDuplicateDocuments`
- `fraud.checkFundFlowAnomalies`
- `fraud.analyzeTransactionNetwork`
- `fraud.matchHistoricalRiskPatterns`
- `fraud.evaluateFraudClues`

### 分析内容

1. 虚假主体线索。
2. 隐瞒关联关系线索。
3. 虚假经营线索。
4. 虚增收入线索。
5. 虚构交易线索。
6. 资金回流线索。
7. 重复融资线索。
8. 异常担保线索。
9. 材料造假线索。

资金回流不能仅凭交易金额相近或时间接近直接认定，需要结合交易路径、关联关系、业务背景和其他证据。

财务、税务、流水存在差异时，需要检查统计期间、未开票收入、现金结算、非本行账户、关联交易和重复统计等可能原因。

### 输出

- 欺诈风险线索。
- 主体异常。
- 关联关系异常。
- 财务异常。
- 交易异常。
- 资金流向异常。
- 材料异常。
- 命中规则。
- 关系路径。
- 证据。
- 影响分析。
- 待核实事项。
- 调查建议。

每条风险记录必须保存规则编号、规则版本、命中数据、证据、时间和人工核查结果。

## 3.11 Agent 10：审批政策匹配 Agent

### 职责

根据银行制度文件和产品办法，结合企业信息、授信申请和业务场景，进行政策匹配分析。

第一期只使用 RAG，不建设独立结构化政策规则引擎。

### 输入数据

- 授信申请。
- 企业主体。
- 行业信息。
- 财务信息。
- 贷款用途。
- 征信信息。
- 司法信息。
- 担保信息。
- 反欺诈分析结果。
- 银行制度文件。

### 工具

- `policy.searchApplicablePolicies`
- `policy.getPolicyVersion`
- `policy.retrievePolicyClauses`
- `policy.searchPolicyByKeyword`
- `policy.searchPolicyByVector`
- `policy.rerankPolicyChunks`
- `policy.analyzePolicyMatch`
- `policy.generatePolicyChecklist`

### RAG 设计

制度处理流程：

1. 上传制度文件。
2. PDF、Word、Excel 解析。
3. 扫描件 OCR。
4. 章节和条款提取。
5. 制度元数据识别。
6. 按条款和语义切片。
7. Embedding 向量化。
8. 存入 Milvus。
9. 制度元数据及原文保存到 MySQL 或对象存储。
10. 根据业务场景检索相关制度。
11. 关键词和向量混合检索。
12. Reranking 重排序。
13. 获取完整制度原文和上下文。
14. LLM 结合企业信息进行匹配分析。

每个制度 Chunk 必须保留：

- 制度 ID。
- 制度名称。
- 版本。
- 生效时间。
- 失效时间。
- 文件状态。
- 章节编号。
- 条款编号。
- 页码。
- 原始文本。
- 向量 ID。
- 来源文件。
- 文件哈希。

### 分析内容

1. 客户准入条件。
2. 行业准入条件。
3. 产品适用条件。
4. 贷款用途限制。
5. 金额和期限限制。
6. 担保条件。
7. 限制性条款。
8. 例外审批要求。
9. 审批权限提示。
10. 政策冲突和信息缺失。

RAG 检索到制度条款不等于完成确定性规则执行。

涉及数值阈值、计算公式、审批权限等内容时，必须展示制度原文和匹配依据。若无法可靠识别或验证，输出待人工确认，不得擅自补充阈值。

### 输出

- 适用制度。
- 制度版本。
- 条款编号。
- 条款原文。
- 企业数据依据。
- 客户准入分析。
- 行业准入分析。
- 产品适用分析。
- 用途匹配分析。
- 担保条件分析。
- 政策限制。
- 政策冲突。
- 待人工确认事项。

## 3.12 Agent 11：综合风险评估 Agent

### 职责

汇总前面各专业 Agent 的结构化结果，完成综合风险分析。

不得简单将各 Agent 的文本交给 LLM 总结。

### 输入数据

- Agent 01–10 分析结果。
- 统一证据中心。
- 银行风险偏好资料（如已提供）。
- 已确认的风险指标和风险口径（如已提供）。

### 工具

- `risk.aggregateFindings`
- `risk.deduplicateFindings`
- `risk.calculateRiskIndicators`
- `risk.evaluateDataCompleteness`
- `risk.evaluateRepaymentCapacity`
- `risk.evaluateGuaranteeCoverage`
- `risk.evaluateRiskMitigation`
- `risk.generateRiskAssessment`

### 分析内容

1. 风险汇总。
2. 风险线索去重。
3. 风险之间的关联分析。
4. 主体风险。
5. 经营风险。
6. 财务风险。
7. 信用风险。
8. 司法风险。
9. 欺诈风险。
10. 贷款用途风险。
11. 担保风险。
12. 政策风险。
13. 第一还款来源。
14. 第二还款来源。
15. 风险缓释建议。

必须区分：

- 风险等级。
- 证据充分程度。
- 分析可信程度。

这三个维度不能使用一个分数代替。

缺少关键征信、财务或用途证据时，应输出“暂不具备完整评估条件”，不能强行生成风险等级。

### 输出

- 综合风险摘要。
- 各风险维度评价。
- 重大风险线索。
- 第一还款来源分析。
- 第二还款来源分析。
- 担保分析。
- 风险缓释建议。
- 额度和期限参考意见。
- 审批关注事项。
- 数据完整性。
- 评估限制。
- 证据引用。

## 3.13 Agent 12：尽调报告生成 Agent

### 职责

将上游结构化分析、风险线索、证据和补充调查要求组织成完整尽调报告。

不得重新分析原始企业数据，不得编造缺失事实。

### 工具

- `report.getTemplate`
- `report.buildReportData`
- `report.generateReportContent`
- `report.validateReport`
- `report.renderWord`
- `report.renderPdf`
- `report.createReportVersion`
- `report.getEvidenceReferences`
- `report.updateReport`

### 报告目录

1. 授信申请基本情况。
2. 企业主体信息。
3. 股权结构及关联关系。
4. 企业经营情况。
5. 行业分析。
6. 财务分析及偿债能力。
7. 征信及债务情况。
8. 司法风险分析。
9. 现场调查情况。
10. 尽调材料完整性。
11. 贷款用途及资金需求分析。
12. 反欺诈分析。
13. 审批政策匹配。
14. 综合风险评估。
15. 金额及期限参考意见。
16. 风险缓释措施。
17. 补充材料及调查事项。
18. 客户经理复核意见。

### 人工复核

客户经理支持：

- 确认章节。
- 驳回章节。
- 补充说明。
- 修正数据。
- 要求重新分析。
- 申请补充材料。
- 导出报告。

人工修正必须保存：

- 原始值。
- 修正值。
- 修改人。
- 修改时间。
- 修改原因。
- 影响范围。
- 重新分析记录。

不能覆盖原始证据。

报告状态建议：

- DRAFT
- AI_GENERATED
- WAITING_FOR_REVIEW
- RETURNED_FOR_SUPPLEMENT
- REANALYZING
- REVIEWED
- FINALIZED

FINALIZED 仅表示 AI 辅助报告定稿，不表示银行授信审批通过。

---

# 第四部分：Planner 与 Agent Graph 工作流

## 4.1 Planner 设计

Planner 是主控 Graph 中的公共规划节点，不计入 12 个专业 Agent。

它负责根据企业情况、业务场景、产品、用途和资料完整程度动态生成尽调计划。

输入：

- 企业主体信息。
- 授信申请信息。
- 已有材料。
- 初步数据采集结果。
- 自动识别的业务场景。
- 产品目录。
- 已配置的尽调材料要求。
- 已配置的业务规则和场景模板。

输出：

1. `agentPlan`：需要执行的 Agent。
2. `dataPlan`：需要采集的数据。
3. `materialPlan`：需要的材料。
4. `dependencyPlan`：Agent 依赖关系。
5. `supplementPlan`：当前资料缺口。
6. `executionConfig`：并行和执行策略。

## 4.2 业务场景自动识别

识别维度：

- 企业类型。
- 实际主营行业。
- 融资业务类型。
- 产品类型。
- 资金用途。
- 融资模式。
- 担保方式。
- 经营场景。

识别流程：

1. 获取企业基本信息。
2. 获取授信申请资料。
3. 解析已有材料。
4. 提取企业实际经营情况。
5. LLM 识别业务场景。
6. 使用银行产品目录和业务配置进行校验。
7. 输出识别结果和置信度。
8. 对存在冲突或不确定的事项进行标记。
9. 必要时要求客户经理确认。

自动识别并不意味着所有企业都必须得到唯一分类。

支持复合融资场景，例如同时涉及流动资金和固定资产融资。

## 4.3 推荐 Graph 执行流程

```text
任务提交
   |
   v
初始化 EnterpriseContext
   |
   v
企业主体识别 Agent 01
   |
   +---------------------------+
   |                           |
   v                           v
影像材料 Agent 06          场景识别
   |                           |
   +-------------+-------------+
                 |
                 v
             Planner
                 |
                 v
              数据采集
                 |
       +---------+---------+
       |         |         |
       v         v         v
    财务02    司法03    征信04
       |         |         |
       +---------+---------+
                 |
       +---------+---------+
       |                   |
       v                   v
    现场05              行业07
       |                   |
       +---------+---------+
                 |
                 v
             用途分析08
                 |
                 v
             反欺诈09
                 |
                 v
             政策匹配10
                 |
                 v
             数据完整性检查
                 |
          +------+------+
          |             |
          v             v
        有缺口         无关键缺口
          |             |
          v             v
    补充材料任务     综合风险11
          |             |
          v             v
    等待补充材料     报告生成12
          |             |
          v             v
    增量重新分析     客户经理复核
          |             |
          +----->-------+
```

以上为逻辑依赖示意。具体执行时允许 Agent 并行，某个接口失败不能自动导致整项任务失败。

## 4.4 增量重分析

补充材料上传或数据更新后：

1. 创建新的材料或证据版本。
2. 更新企业上下文版本。
3. 判断发生变化的数据类型。
4. 根据 Agent 依赖关系计算受影响节点。
5. 仅重新执行受影响的 Agent。
6. 重新执行下游依赖节点。
7. 更新综合风险结果。
8. 生成新的报告版本。
9. 保留旧版本分析结果。

示例：

新增采购合同后，可能需要重新执行：

- 影像材料 Agent。
- 贷款用途 Agent。
- 反欺诈 Agent。
- 政策匹配 Agent（如果用途信息变化影响政策匹配）。
- 综合风险 Agent。
- 报告生成 Agent。

不需要重新执行与该材料无关的征信分析。

---

# 第五部分：Tool Registry 与数据适配层

## 5.1 分层架构

```text
Agent
  |
  v
Domain Tool
  |
  v
Tool Registry
  |
  v
Adapter
  |
  v
银行现有系统 / 外部数据平台
```

## 5.2 Tool 分类

| 类型 | 作用 | 示例 |
|---|---|---|
| 查询类 Tool | 查询企业和业务数据 | 查询工商、征信、司法 |
| 分析类 Tool | 执行计算和分析 | 财务指标计算 |
| 规则类 Tool | 执行确定性判断 | 材料完整性检查 |
| 操作类 Tool | 修改业务状态或创建任务 | 创建补充材料任务 |

LLM 只能在授权范围内选择查询工具。

操作类 Tool 必须进行业务校验，必要时要求人工确认。

LLM 不得直接执行数据库 SQL。

## 5.3 统一 ToolResult

建议统一定义：

```java
public class ToolResult<T> {

    private boolean success;

    private String status;

    private T data;

    private String source;

    private LocalDateTime queryTime;

    private String errorCode;

    private String errorMessage;

    private List<String> evidenceIds;

    private Map<String, Object> metadata;
}
```

建议状态：

- SUCCESS
- PARTIAL_SUCCESS
- NOT_FOUND
- NOT_AVAILABLE
- TIMEOUT
- FAILED
- UNAUTHORIZED
- DATA_INCOMPLETE

每次 Tool 调用需要记录：

- 调用 ID。
- 任务 ID。
- Agent ID。
- Tool 名称。
- 数据源。
- 请求摘要。
- 响应摘要。
- 调用时间。
- 耗时。
- 状态。
- 重试次数。
- 幂等键。
- 错误信息。
- 证据 ID。

---

# 第六部分：统一上下文与证据中心

## 6.1 EnterpriseContext

每个尽调任务拥有一个统一上下文：

```java
@Data
@Builder
public class EnterpriseContext {

    private String taskId;

    private String enterpriseId;

    private String customerId;

    private String creditCode;

    private EnterpriseBasicInfo enterpriseInfo;

    private LoanApplicationInfo loanApplication;

    private BusinessScenario businessScenario;

    private Integer contextVersion;

    private Integer analysisVersion;

    private List<String> evidenceIds;

    private List<DataGapSummary> dataGaps;
}
```

注意：

EnterpriseContext 只保存必要的业务信息、摘要和索引。

不允许将全部财务报表、征信报告、银行流水明细、原始文件和大体量数据直接放进 LLM Prompt。

各 Agent 必须通过 AgentContextFactory 获取职责范围内的上下文。

## 6.2 证据中心

证据中心是所有 Agent 共用的基础设施。

每条证据至少保存：

- evidenceId。
- taskId。
- evidenceType。
- sourceSystem。
- sourceReference。
- subjectId。
- dataPeriod。
- collectedAt。
- contentSummary。
- contentHash。
- status。

证据来源包括：

- 银行接口。
- 外部数据接口。
- 客户上传文件。
- OCR 结果。
- 财务计算结果。
- 行业研究资料。
- 银行制度文件。
- 人工调查记录。

每个 Agent 的重要结论必须引用 evidenceIds。

风险线索至少保存：

- findingId。
- findingType。
- description。
- riskLevel。
- evidenceIds。
- analysisBasis。
- suggestions。
- sourceAgent。
- analysisVersion。

证据与分析结论必须能够双向追溯。

---

# 第七部分：报告和交互式工作台

## 7.1 前端功能模块

1. 尽调任务列表。
2. 创建尽调任务。
3. 企业资料管理。
4. 业务场景识别结果。
5. 尽调计划展示。
6. Agent 执行进度。
7. Agent 分析结果。
8. 风险线索中心。
9. 证据查看中心。
10. 数据缺口管理。
11. 补充材料任务。
12. 交互式尽调报告。
13. 客户经理复核。
14. Word 导出。
15. PDF 导出。

## 7.2 交互式报告

每个章节需要支持：

- 查看 AI 分析结论。
- 查看原始数据。
- 查看引用证据。
- 查看风险线索。
- 查看分析限制。
- 查看数据来源。
- 查看统计期间。
- 人工补充说明。
- 人工修正。
- 确认章节。
- 驳回章节。
- 重新分析。

## 7.3 SSE 实时进度

前端通过 SSE 接收：

- TASK_STARTED。
- CONTEXT_INITIALIZED。
- SCENARIO_IDENTIFYING。
- SCENARIO_IDENTIFIED。
- PLAN_GENERATED。
- DATA_COLLECTION_STARTED。
- DATA_COLLECTION_COMPLETED。
- AGENT_STARTED。
- AGENT_PROGRESS。
- AGENT_COMPLETED。
- AGENT_FAILED。
- SUPPLEMENT_REQUIRED。
- RISK_ASSESSMENT_COMPLETED。
- REPORT_GENERATED。
- TASK_COMPLETED。

事件必须包含 taskId、eventId、eventType、timestamp 和必要的业务数据。

SSE 断开后，前端必须能够通过任务状态查询接口恢复当前进度。

---

# 第八部分：模块一——尽调任务与企业上下文详细设计

本部分是当前已经讨论到的具体开发模块，属于第一阶段 P0。

Coding Agent 应优先完成本模块，并通过测试后再进入模块二。

## 8.1 模块目标

实现：

1. 创建尽调任务。
2. 企业主体信息管理。
3. 授信申请信息管理。
4. 任务状态管理。
5. 任务提交和异步初始化。
6. 企业上下文构建。
7. 业务场景识别结果保存。
8. 上下文版本管理。
9. 任务状态查询。
10. 场景识别结果查询。
11. 任务幂等。
12. 并发更新控制。

第一期不实现完整的 Agent 分析流程，但必须预留 Graph、Planner、证据中心接口。

## 8.2 模块一领域关系

```text
DDTask
  |
  +---- Enterprise
  |
  +---- LoanApplication
  |
  +---- BusinessScenario
  |
  +---- EnterpriseContext
```

设计原则：

- 一个企业可以有多个尽调任务。
- 一个尽调任务第一期对应一条授信申请。
- 一个尽调任务可以有多个业务场景识别版本。
- 一个尽调任务拥有独立的上下文版本。
- 一个尽调任务可以有多个分析版本。
- 企业最新信息不能覆盖历史尽调任务的历史证据。

第一期如需支持一个申请中多个融资分项，应通过申请明细扩展，不得将多种融资场景强行压缩成单一业务类型。

## 8.3 任务状态

```java
public enum TaskStatus {

    DRAFT,

    INITIALIZING,

    SCENARIO_IDENTIFYING,

    PLANNING,

    DATA_COLLECTING,

    ANALYZING,

    WAITING_FOR_SUPPLEMENT,

    REANALYZING,

    RISK_ASSESSING,

    REPORT_GENERATING,

    WAITING_FOR_REVIEW,

    RETURNED,

    COMPLETED,

    FAILED,

    CANCELLED
}
```

状态说明：

| 状态 | 含义 |
|---|---|
| DRAFT | 草稿 |
| INITIALIZING | 初始化 |
| SCENARIO_IDENTIFYING | 业务场景识别 |
| PLANNING | 生成尽调计划 |
| DATA_COLLECTING | 数据采集 |
| ANALYZING | Agent 分析 |
| WAITING_FOR_SUPPLEMENT | 等待补充材料 |
| REANALYZING | 增量重分析 |
| RISK_ASSESSING | 综合风险评估 |
| REPORT_GENERATING | 生成报告 |
| WAITING_FOR_REVIEW | 等待人工复核 |
| RETURNED | 退回 |
| COMPLETED | 人工复核完成 |
| FAILED | 任务失败 |
| CANCELLED | 任务取消 |

必须集中管理状态流转，不允许 Controller 随意修改任务状态。

## 8.4 数据库表

### 表一：dd_task

```sql
CREATE TABLE dd_task (
    id                  VARCHAR(32) NOT NULL COMMENT '任务ID',
    task_no             VARCHAR(40) NOT NULL COMMENT '任务编号',

    enterprise_id       VARCHAR(32) NOT NULL COMMENT '企业ID',
    application_id      VARCHAR(32) DEFAULT NULL COMMENT '授信申请ID',

    task_name           VARCHAR(200) DEFAULT NULL COMMENT '任务名称',

    task_status         VARCHAR(40) NOT NULL DEFAULT 'DRAFT'
                        COMMENT '任务状态',

    current_stage       VARCHAR(40) DEFAULT NULL COMMENT '当前执行阶段',

    current_plan_id     VARCHAR(32) DEFAULT NULL COMMENT '当前计划ID',

    context_version     INT NOT NULL DEFAULT 1 COMMENT '上下文版本',

    analysis_version    INT NOT NULL DEFAULT 0 COMMENT '分析版本',

    last_error_code     VARCHAR(64) DEFAULT NULL,

    last_error_message  VARCHAR(1000) DEFAULT NULL,

    submitted_at        DATETIME(3) DEFAULT NULL,

    completed_at        DATETIME(3) DEFAULT NULL,

    created_by          VARCHAR(64) DEFAULT NULL,

    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),

    version             INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',

    deleted             TINYINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    UNIQUE KEY uk_task_no (task_no),

    KEY idx_enterprise_id (enterprise_id),

    KEY idx_application_id (application_id),

    KEY idx_task_status_created (task_status, created_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业尽调任务';
```

### 表二：dd_enterprise

```sql
CREATE TABLE dd_enterprise (
    id                      VARCHAR(32) NOT NULL COMMENT '企业ID',

    customer_id             VARCHAR(64) DEFAULT NULL COMMENT '银行企业客户编号',

    credit_code             VARCHAR(32) NOT NULL COMMENT '统一社会信用代码',

    enterprise_name         VARCHAR(256) NOT NULL COMMENT '企业名称',

    legal_person            VARCHAR(128) DEFAULT NULL COMMENT '法定代表人',

    registered_address      VARCHAR(500) DEFAULT NULL COMMENT '注册地址',

    business_scope          TEXT COMMENT '经营范围',

    registered_capital      DECIMAL(20,2) DEFAULT NULL COMMENT '注册资本',

    establishment_date      DATE DEFAULT NULL COMMENT '成立日期',

    business_status         VARCHAR(64) DEFAULT NULL COMMENT '经营状态',

    industry_code           VARCHAR(64) DEFAULT NULL,

    industry_name           VARCHAR(128) DEFAULT NULL,

    enterprise_profile      JSON DEFAULT NULL COMMENT '扩展信息',

    data_source             VARCHAR(64) DEFAULT NULL,

    last_sync_at            DATETIME(3) DEFAULT NULL,

    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),

    version                 INT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    UNIQUE KEY uk_credit_code (credit_code),

    KEY idx_customer_id (customer_id),

    KEY idx_enterprise_name (enterprise_name)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业主体信息';
```

### 表三：dd_loan_application

```sql
CREATE TABLE dd_loan_application (
    id                      VARCHAR(32) NOT NULL COMMENT '申请ID',

    task_id                 VARCHAR(32) NOT NULL COMMENT '任务ID',

    application_no          VARCHAR(64) DEFAULT NULL COMMENT '原信贷系统申请编号',

    application_type        VARCHAR(64) DEFAULT NULL COMMENT '原始申请业务类型',

    product_code            VARCHAR(64) DEFAULT NULL,

    product_name            VARCHAR(128) DEFAULT NULL,

    apply_amount            DECIMAL(20,2) DEFAULT NULL,

    currency                VARCHAR(8) DEFAULT 'CNY',

    apply_term              INT DEFAULT NULL,

    term_unit               VARCHAR(16) DEFAULT NULL,

    loan_purpose            VARCHAR(1000) DEFAULT NULL,

    repayment_method        VARCHAR(64) DEFAULT NULL,

    guarantee_type          VARCHAR(64) DEFAULT NULL,

    application_snapshot    JSON DEFAULT NULL COMMENT '原始申请快照',

    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),

    version                 INT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    UNIQUE KEY uk_task_id (task_id),

    KEY idx_application_no (application_no),

    KEY idx_product_code (product_code)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授信申请信息';
```

### 表四：dd_business_scenario

```sql
CREATE TABLE dd_business_scenario (
    id                      VARCHAR(32) NOT NULL COMMENT '场景ID',

    task_id                 VARCHAR(32) NOT NULL COMMENT '任务ID',

    scenario_version        INT NOT NULL COMMENT '识别版本',

    business_type           VARCHAR(64) DEFAULT NULL,

    product_type            VARCHAR(64) DEFAULT NULL,

    industry_code           VARCHAR(64) DEFAULT NULL,

    industry_name           VARCHAR(128) DEFAULT NULL,

    loan_purpose_type       VARCHAR(64) DEFAULT NULL,

    financing_mode          VARCHAR(64) DEFAULT NULL,

    guarantee_types         JSON DEFAULT NULL,

    confidence              DECIMAL(5,4) DEFAULT NULL,

    identify_status         VARCHAR(32) NOT NULL,

    scenario_detail         JSON DEFAULT NULL,

    evidence_ids            JSON DEFAULT NULL,

    uncertain_items         JSON DEFAULT NULL,

    model_name              VARCHAR(128) DEFAULT NULL,

    prompt_version          VARCHAR(64) DEFAULT NULL,

    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),

    UNIQUE KEY uk_task_scenario_version (task_id, scenario_version),

    KEY idx_task_id (task_id),

    KEY idx_business_type (business_type)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务场景识别结果';
```

数据库设计要求：

1. 所有表使用 InnoDB。
2. 统一使用 utf8mb4。
3. 主键由应用生成。
4. 业务主表必须具备创建时间和更新时间。
5. 重要业务表必须具备版本控制字段。
6. 所有历史分析数据必须保留。
7. 不能使用物理删除破坏证据链。
8. JSON 只用于扩展信息，不应把所有重要查询字段都塞进 JSON。
9. 必须为业务查询场景建立索引。
10. 生产环境禁止随意修改已经使用的历史 DDL，后续通过数据库迁移脚本升级。

## 8.5 Java 项目目录结构

建议采用 Maven 多模块项目：

```text
corporate-credit-agent
│
├── pom.xml
│
├── credit-agent-app
│   └── Spring Boot 启动模块
│
├── credit-agent-common
│   ├── Result.java
│   ├── ErrorCode.java
│   ├── IdGenerator.java
│   └── BusinessException.java
│
├── credit-agent-domain
│   ├── task
│   │   ├── entity
│   │   ├── enums
│   │   ├── repository
│   │   └── service
│   │
│   ├── enterprise
│   │   ├── entity
│   │   ├── repository
│   │   └── service
│   │
│   ├── application
│   │   ├── entity
│   │   └── repository
│   │
│   └── scenario
│       ├── entity
│       └── repository
│
├── credit-agent-application
│   ├── task
│   │   ├── controller
│   │   ├── service
│   │   └── dto
│   │
│   ├── context
│   │   ├── EnterpriseContext.java
│   │   ├── AgentContext.java
│   │   └── ContextAssembler.java
│   │
│   └── scenario
│       ├── ScenarioIdentifyService.java
│       └── ScenarioResultHandler.java
│
├── credit-agent-ai
│   ├── planner
│   ├── graph
│   ├── agent
│   └── prompt
│
├── credit-agent-infrastructure
│   ├── persistence
│   ├── adapter
│   ├── llm
│   └── messaging
│
└── credit-agent-interface
    ├── rest
    └── event
```

分层约束：

- domain 不依赖 Spring AI。
- domain 不依赖 Controller。
- application 负责业务用例编排。
- ai 负责 Agent、Prompt、Graph 和 Planner。
- infrastructure 负责数据库、消息队列、银行接口适配和模型接入。
- interface 负责 REST 和消息事件入口。

## 8.6 核心 Java 枚举

```java
public enum BusinessType {

    WORKING_CAPITAL,

    FIXED_ASSET,

    TRADE_FINANCE,

    PROJECT_FINANCE,

    SUPPLY_CHAIN,

    COMPREHENSIVE_CREDIT,

    OTHER
}
```

枚举必须配备中文展示名称和业务说明，不允许直接依赖枚举名称作为前端展示文本。

## 8.7 创建任务请求 DTO

```java
@Data
public class CreateTaskRequest {

    @NotNull
    @Valid
    private EnterpriseRequest enterprise;

    @NotNull
    @Valid
    private LoanApplicationRequest application;

    @Data
    public static class EnterpriseRequest {

        @NotBlank
        private String creditCode;

        private String customerId;
    }

    @Data
    public static class LoanApplicationRequest {

        private String applicationNo;

        @NotNull
        @DecimalMin("0.01")
        private BigDecimal applyAmount;

        private String currency = "CNY";

        @NotNull
        @Min(1)
        private Integer applyTerm;

        @NotBlank
        private String termUnit;

        @NotBlank
        private String loanPurpose;

        private String guaranteeType;
    }
}
```

创建草稿时允许部分字段暂缺；正式提交时执行场景化必填校验。

## 8.8 REST API

| API | 方法 | 功能 |
|---|---|---|
| `/api/dd/tasks` | POST | 创建任务 |
| `/api/dd/tasks/{taskId}` | GET | 查询任务 |
| `/api/dd/tasks/{taskId}/submit` | POST | 提交任务 |
| `/api/dd/tasks/{taskId}/status` | GET | 查询状态 |
| `/api/dd/tasks/{taskId}/context` | GET | 查询上下文摘要 |
| `/api/dd/tasks/{taskId}/scenario` | GET | 查询场景识别结果 |
| `/api/dd/tasks/{taskId}/scenario/reidentify` | POST | 重新识别场景 |
| `/api/dd/tasks/{taskId}/cancel` | POST | 取消任务 |

### 创建任务示例

请求：

```json
{
  "enterprise": {
    "creditCode": "91360000XXXXXXXXXX",
    "customerId": "BANK_CUSTOMER_001"
  },
  "application": {
    "applicationNo": "APP202611040001",
    "applyAmount": 10000000,
    "currency": "CNY",
    "applyTerm": 12,
    "termUnit": "MONTH",
    "loanPurpose": "采购生产经营所需原材料",
    "guaranteeType": "CREDIT"
  }
}
```

返回：

```json
{
  "success": true,
  "code": "0000",
  "message": "创建成功",
  "data": {
    "taskId": "DD20261104000001",
    "taskNo": "DD20261104000001",
    "taskStatus": "DRAFT"
  }
}
```

### 提交任务

```http
POST /api/dd/tasks/{taskId}/submit
```

提交后异步执行，不允许在 HTTP 请求中等待整个 Agent Graph 完成。

### 查询任务状态

```http
GET /api/dd/tasks/{taskId}/status
```

返回任务状态、当前阶段、上下文版本、分析版本和根据当前执行计划计算的进度。

## 8.9 Service 设计

核心 Service：

- `DueDiligenceTaskService`
- `EnterpriseService`
- `LoanApplicationService`
- `TaskStateMachine`
- `ContextAssembler`
- `ScenarioIdentifyService`
- `ScenarioResultHandler`

创建任务：

1. 根据统一社会信用代码查询企业。
2. 不存在时创建企业主体。
3. 存在时复用企业主体。
4. 创建尽调任务。
5. 创建授信申请。
6. 保存原始申请快照。
7. 返回任务编号。

企业唯一性必须通过数据库唯一索引保证。并发创建时，先查询再插入并不足够，必须处理唯一键冲突并重新查询。

提交任务：

1. 查询任务。
2. 校验任务状态。
3. 校验最低提交条件。
4. 更新任务状态为 INITIALIZING。
5. 保存提交时间。
6. 使用乐观锁更新任务。
7. 在同一数据库事务中写入 Outbox 事件。
8. 事务提交。
9. Outbox 发布器异步发布任务事件。
10. 消费者幂等处理事件并启动初始化流程。

禁止在数据库事务中直接执行耗时 LLM 请求。

## 8.10 Outbox 事件机制

为避免数据库事务和 RocketMQ 发送不一致，采用 Outbox。

新增 `dd_outbox_event` 表，至少包含：

- id。
- event_id。
- aggregate_type。
- aggregate_id。
- event_type。
- payload。
- publish_status。
- retry_count。
- next_retry_at。
- created_at。
- published_at。

任务提交事件和任务状态更新必须在同一数据库事务内完成。

发布器定时扫描未发布事件，成功后更新发布状态。

消息消费者必须实现幂等处理。

重复收到 TASK_SUBMITTED 事件不能重复启动相同分析版本。

## 8.11 ContextAssembler

负责组装 EnterpriseContext。

```java
@Service
@RequiredArgsConstructor
public class ContextAssembler {

    private final EnterpriseRepository enterpriseRepository;

    private final LoanApplicationRepository applicationRepository;

    private final EvidenceRepository evidenceRepository;

    private final BusinessScenarioRepository scenarioRepository;

    public EnterpriseContext assemble(String taskId) {

        DueDiligenceTask task = getTask(taskId);

        Enterprise enterprise = enterpriseRepository
                .findById(task.getEnterpriseId())
                .orElseThrow();

        LoanApplication application = applicationRepository
                .findByTaskId(taskId)
                .orElseThrow();

        BusinessScenario scenario = scenarioRepository
                .findLatestByTaskId(taskId)
                .orElse(null);

        List<String> evidenceIds =
                evidenceRepository.findIdsByTaskId(taskId);

        return EnterpriseContext.builder()
                .taskId(taskId)
                .enterpriseId(enterprise.getId())
                .customerId(enterprise.getCustomerId())
                .creditCode(enterprise.getCreditCode())
                .enterpriseInfo(EnterpriseBasicInfo.from(enterprise))
                .loanApplication(LoanApplicationInfo.from(application))
                .businessScenario(scenario)
                .contextVersion(task.getContextVersion())
                .analysisVersion(task.getAnalysisVersion())
                .evidenceIds(evidenceIds)
                .build();
    }
}
```

该代码为接口和流程基线，实际实现必须补充空值、数据权限、异常处理和版本一致性校验。

## 8.12 Agent 专属上下文

不同 Agent 不应接收完全相同的上下文。

统一接口：

```java
public interface AgentContextFactory<T> {

    T create(EnterpriseContext context);
}
```

例如：

```java
@Data
@Builder
public class FinancialAgentContext {

    private String taskId;

    private EnterpriseBasicInfo enterpriseInfo;

    private LoanApplicationInfo loanApplication;

    private List<String> financialEvidenceIds;

    private List<String> taxEvidenceIds;

    private List<String> transactionEvidenceIds;
}
```

后续为每个 Agent 建立专属上下文工厂。

## 8.13 模块一异常处理

必须处理：

| 异常场景 | 处理要求 |
|---|---|
| 企业不存在 | 创建待补充企业主体或进入人工确认流程 |
| 企业信息接口失败 | 保存采集失败状态，不直接结束任务 |
| 授信申请缺少字段 | 草稿允许保存，提交时按场景校验 |
| 企业重复创建 | 唯一索引冲突后重新查询 |
| 任务重复提交 | 返回已有状态或明确拒绝，不重复启动 |
| 数据库更新失败 | 事务回滚 |
| Outbox 发布失败 | 重试并记录错误 |
| 消息重复消费 | 幂等处理 |
| 上下文构建失败 | 保存任务错误状态和错误详情 |
| 场景识别失败 | 标记待确认，不能伪造识别结果 |
| 并发修改 | 乐观锁控制 |
| 任务取消 | 检查状态并通知工作流停止后续可取消节点 |

## 8.14 模块一验收标准

必须通过以下验收：

1. 同一家企业可以创建多个尽调任务。
2. 同一任务只能有一条当前授信申请记录。
3. 统一社会信用代码重复时复用企业主体。
4. 企业创建具备并发安全性。
5. 创建任务和申请数据具备事务一致性。
6. 草稿任务可以提交。
7. 非草稿任务不能重复提交。
8. 任务状态必须遵守状态机。
9. 任务提交通过 Outbox 异步发布。
10. 重复消息不会重复启动同一轮任务。
11. EnterpriseContext 能正确组装。
12. 上下文能查询对应企业、申请和证据。
13. 场景识别结果支持版本保存。
14. 企业数据更新后可以生成新的上下文版本。
15. 历史任务能保留当时的分析版本和数据引用。
16. 并发修改不会静默覆盖。
17. 关键异常均有日志和错误码。
18. 提供单元测试和集成测试。

---

# 第九部分：非功能需求

## 9.1 稳定性

1. 单个银行数据接口失败不应导致整个尽调任务失败。
2. Agent 执行支持超时、重试和异常隔离。
3. 消息队列支持重复消费。
4. 任务支持异常恢复。
5. 长时间未完成的任务支持监控和告警。
6. 报告生成失败不应丢失前序分析结果。

## 9.2 性能

第一期建议以可配置的目标进行压测，以下仅作为工程初始目标，不代表银行正式生产 SLA：

- 普通任务创建接口 P95 不超过 500ms。
- 任务状态查询 P95 不超过 500ms。
- 文件上传支持大文件和分片扩展。
- Agent 分析采用异步执行。
- 多个独立 Agent 支持并行。
- 数据查询必须有分页和数量限制。
- 大体量流水和文件不能直接进入 LLM 上下文。

## 9.3 安全

银行企业数据属于敏感业务数据，必须：

1. 使用合法授权的数据源。
2. 对敏感字段进行必要脱敏。
3. 保护企业征信、财务和交易信息。
4. 文件访问使用受控的访问方式。
5. 不将企业敏感数据写入普通应用日志。
6. 不将银行数据发送给未经银行批准的模型服务。
7. 支持模型服务地址、部署方式和数据传输策略配置。
8. 对重要数据访问和人工修改保留审计记录。
9. 对 Prompt Injection 和恶意文件内容进行防护。
10. Agent 不能因为文件中出现指令就突破系统权限或调用额外工具。

## 9.4 可观测性

必须记录：

- taskId。
- analysisVersion。
- agentId。
- executionId。
- toolCallId。
- traceId。
- modelName。
- promptVersion。
- token 使用量。
- 模型耗时。
- Tool 耗时。
- Agent 执行状态。
- 失败原因。
- 重试次数。

应支持按任务查看完整执行链路。

## 9.5 模型可替换

模型调用统一封装。

支持通过配置切换不同模型服务，避免 Agent 业务逻辑直接绑定某一个模型厂商。

模型调用需要记录：

- 模型名称。
- 模型版本（如果服务提供）。
- Prompt 版本。
- 调用时间。
- Token 使用量。
- 结构化输出校验结果。

## 9.6 审计和可复现性

对于重要分析结果，应保存：

- 输入上下文版本。
- 使用的证据版本。
- 使用的制度版本。
- 模型名称。
- Prompt 版本。
- Tool 调用记录。
- 结构化输出。
- 分析时间。
- 人工修正和确认记录。

由于 LLM 具有非确定性，系统不保证同一模型每次生成完全相同文本，但必须保证历史输入、证据、制度和分析版本可追溯。

---

# 第十部分：数据模型和业务约束

## 10.1 核心数据分层

| 数据层 | 数据内容 | 存储位置 |
|---|---|---|
| 原始数据层 | 原始文件、接口响应、原始申请信息 | 对象存储 / MySQL |
| 标准化数据层 | 企业信息、财务指标、征信摘要等 | MySQL |
| 证据层 | 证据索引、来源、版本和关联 | MySQL |
| 向量层 | 制度、行业资料等 Embedding | Milvus |
| 分析层 | Agent 结构化结果和风险线索 | MySQL |
| 报告层 | 报告章节、版本、文件 | MySQL / 对象存储 |

## 10.2 数据缺口模型

建议定义：

- NOT_COLLECTED：尚未采集。
- NOT_AVAILABLE：数据源不提供。
- COLLECTION_FAILED：采集失败。
- NOT_PROVIDED：客户未提供。
- PARSE_FAILED：解析失败。
- INCOMPLETE：数据不完整。
- CONFLICTED：不同来源存在冲突。
- EXPIRED：数据已过期。
- NEEDS_MANUAL_VERIFICATION：需要人工核实。

不能把所有情况统一记成 NULL。

## 10.3 风险线索与风险等级

风险线索风险级别可以采用：

- LOW
- MEDIUM
- HIGH
- CRITICAL
- UNDETERMINED

风险等级只是系统分析分类，实际定义和映射标准需要银行业务部门确认。

数据完整程度和分析可信度单独保存，不能和风险等级混为一谈。

---

# 第十一部分：项目开发阶段与交付物

## 11.1 开发路线

| 阶段 | 工作内容 | 主要交付物 |
|---|---|---|
| P0 | 工程骨架和基础设施 | Maven 工程、配置、数据库连接 |
| P1 | 尽调任务和企业上下文 | 模块一完整实现 |
| P2 | 证据中心和材料管理 | 文件上传、解析、证据版本 |
| P3 | Tool Registry 和 Adapter | 统一数据工具和 Mock 接口 |
| P4 | Planner 和 Graph | 场景识别、计划生成和调度 |
| P5 | Agent 01–07 | 主体、财务、司法、征信、现场、影像、行业 |
| P6 | Agent 08–10 | 用途、反欺诈、政策匹配 |
| P7 | Agent 11–12 | 综合风险、报告生成 |
| P8 | 前端工作台 | 任务管理、Agent 进度、交互式报告 |
| P9 | 补充材料和增量分析 | 完整闭环 |
| P10 | 测试和部署 | 自动化测试、压测、部署文档 |

## 11.2 开发原则

1. 先实现工程基础，再实现 Agent。
2. 先实现标准化数据模型，再接入真实银行接口。
3. 先完成一个业务闭环，再扩大 Agent 能力。
4. 每个 Agent 必须有独立测试。
5. 每个 Tool 必须有接口契约和 Mock 实现。
6. 每个重要分析结果必须有证据。
7. 所有分析结果必须结构化。
8. Prompt 需要版本管理。
9. 所有重要业务参数必须配置化。
10. 每个阶段完成后必须运行测试并输出交付说明。

---

# 第十二部分：测试和验收方案

## 12.1 功能测试

测试场景至少包含：

1. 正常经营制造企业。
2. 批发零售企业。
3. 企业财务资料不完整。
4. 企业存在重大诉讼。
5. 企业存在逾期记录。
6. 企业存在关联交易。
7. 企业流水存在资金回流线索。
8. 企业财务和税务数据不一致。
9. 企业现场资料和工商信息不一致。
10. 企业贷款用途不明确。
11. 企业申请金额缺少充分资金需求依据。
12. 制度文件没有匹配条款。
13. 制度版本存在冲突。
14. 银行接口超时。
15. 银行接口返回部分数据。
16. 文件 OCR 失败。
17. 客户经理补充资料后触发重分析。
18. 客户经理人工修正数据后重新分析。
19. 报告生成失败后恢复。
20. 同一任务重复提交。

## 12.2 Agent 测试指标

不能只看 LLM 是否生成了流畅文本。

必须评估：

- 数据提取准确率。
- 关键字段准确率。
- 财务指标计算准确率。
- 证据引用准确率。
- 风险线索召回率。
- 风险线索误报率。
- 制度条款检索准确率。
- 制度匹配结论可追溯率。
- 数据缺失识别准确率。
- 报告字段完整率。
- Agent 失败恢复能力。

## 12.3 模块一专项测试

必须验证：

- 企业主体唯一性。
- 多任务支持。
- 任务创建事务。
- 任务提交幂等。
- Outbox 可靠发布。
- 消息重复消费。
- 状态机合法转换。
- 乐观锁。
- 上下文版本。
- 场景版本。
- 历史任务可追溯。
- 异常状态处理。

---

# 第十三部分：未确认事项和禁止臆造清单

以下内容目前没有真实银行资料，Coding Agent 不得自行编造。

## 13.1 银行接口

待确认：

- 信贷系统 API。
- CRM API。
- 企业客户信息 API。
- 核心系统 API。
- 征信接口。
- 工商接口。
- 税务接口。
- 司法接口。
- 影像平台接口。
- 企业财务数据接口。
- 企业交易流水接口。

实现方式：

先定义统一 Adapter 接口和 Mock 实现。

真实接口接入时，通过配置切换 Adapter，不修改 Agent 业务逻辑。

## 13.2 银行制度和政策

待确认：

- 对公授信基本制度。
- 产品管理办法。
- 行业准入政策。
- 客户准入政策。
- 贷款用途管理制度。
- 流动资金测算口径。
- 项目融资测算口径。
- 担保管理制度。
- 审批权限。
- 风险分类标准。
- 例外审批要求。

没有制度文件时，只允许运行 RAG 空库或演示数据，必须明确显示“未配置正式银行制度”。

## 13.3 业务阈值

以下均不能自行假定：

- 企业经营年限要求。
- 注册资本要求。
- 资产负债率阈值。
- 营业收入要求。
- 贷款金额上限。
- 贷款期限上限。
- 担保覆盖率。
- 行业风险等级。
- 逾期认定标准。
- 风险预警阈值。

示例数据只能标注为演示数据，不得作为真实银行规则。

## 13.4 数据和隐私要求

待银行确认：

- 模型是否允许调用外部云服务。
- 是否必须使用本地模型。
- 企业数据脱敏要求。
- 数据保留期限。
- 文件存储位置。
- 征信数据使用授权。
- 外部数据服务授权范围。
- 日志审计规范。

---

# 第十四部分：Coding Agent 开发执行规范

本部分是给 Cursor、Claude Code、Codex 等 Coding Agent 的执行要求。

## 14.1 开发角色

你是一名资深 Java 架构师、银行对公信贷业务专家和 AI Agent 工程师。

你负责根据本需求文档完成整个项目的设计实现。

必须理解银行对公信贷业务和 Java 工程实践，不能只实现一个简单的 LLM 聊天 Demo。

## 14.2 严格遵守的约束

1. 本文档是项目需求基线。
2. 不得随意改变已经确认的技术栈和业务边界。
3. 不得擅自删减 12 个 Agent。
4. 不得把系统改造成完整信贷系统。
5. 不得让 AI 代替银行最终审批。
6. 不得编造银行真实接口。
7. 不得编造银行授信政策。
8. 不得编造风险阈值和计算公式。
9. 不得把 LLM 输出当成确定性计算结果。
10. 不得让 LLM 直接访问数据库。
11. 不得让 Agent 绕过 Tool Registry 调用银行系统。
12. 不得覆盖原始数据和历史证据。
13. 不得把数据缺失直接判断成风险。
14. 不得把所有 Agent 设计成一个巨大的 Prompt。
15. 不得只生成前端静态页面而没有真实业务 API。
16. 不得只生成接口定义而没有业务实现。
17. 不得省略异常处理、日志和测试。
18. 不得使用未经确认的银行数据作为生产测试基准。

## 14.3 开发流程

每个开发阶段必须按照以下流程：

第一步：阅读本需求文档，明确当前阶段范围。

第二步：检查当前代码工程，理解已有实现，禁止无理由推倒重建。

第三步：输出本阶段具体实现计划。

第四步：列出需要新增和修改的文件。

第五步：完成代码实现。

第六步：生成数据库迁移脚本。

第七步：编译项目。

第八步：执行单元测试和集成测试。

第九步：修复错误，直到测试通过。

第十步：更新项目 README 和开发文档。

第十一步：输出本阶段交付清单和未完成事项。

## 14.4 编码质量要求

- 使用 Java 21。
- 遵守统一代码风格。
- 使用构造器注入，避免字段注入。
- Controller 不写复杂业务逻辑。
- Service 不直接处理 HTTP 请求。
- Domain 不依赖 AI 框架。
- DTO、Entity、Domain Object 必须区分。
- 关键业务逻辑必须有单元测试。
- 数据库变更使用版本化迁移。
- 重要方法必须有必要注释。
- 不允许用大量空实现或 TODO 冒充完成。
- Mock 实现必须显式标识，不得伪装成真实银行接口。
- 所有配置外置。
- 密钥和密码不得硬编码。

## 14.5 阶段验收规则

一个阶段只有同时满足以下条件才算完成：

1. 代码已经实现。
2. 编译成功。
3. 测试通过。
4. 数据库脚本完整。
5. API 文档完整。
6. 异常处理完整。
7. README 更新。
8. 未完成事项明确列出。
9. 没有用虚构业务规则冒充真实实现。

如果遇到需求不明确：

- 先检查本文档中的已确认决策。
- 如果属于工程实现细节，选择符合架构约束的合理方案。
- 如果涉及真实银行制度、接口、字段、阈值和业务口径，必须标记为待确认。
- 不允许通过猜测补全真实银行业务规则。

## 14.6 第一条开发任务

当前立即执行：

**P0 + P1：工程骨架与模块一完整落地。**

交付：

1. Maven 多模块工程。
2. Spring Boot 启动模块。
3. MySQL 配置。
4. Flyway 或 Liquibase 数据库迁移。
5. 统一响应对象。
6. 统一异常处理。
7. 任务、企业、授信申请、业务场景 Entity。
8. Mapper 和 Repository。
9. 任务创建接口。
10. 任务查询接口。
11. 任务提交接口。
12. 任务状态机。
13. Outbox 事件机制。
14. RocketMQ 适配接口及本地 Mock 实现。
15. EnterpriseContext。
16. ContextAssembler。
17. 场景识别接口和 Mock 实现。
18. 场景识别版本保存。
19. 单元测试。
20. 集成测试。
21. README。
22. API 文档。

第一阶段完成后，必须能够实际运行以下流程：

```text
启动 Spring Boot
      |
      v
创建企业主体
      |
      v
创建授信申请
      |
      v
创建尽调任务
      |
      v
提交尽调任务
      |
      v
Outbox 发布事件
      |
      v
异步消费者接收事件
      |
      v
初始化 EnterpriseContext
      |
      v
调用场景识别接口（Mock）
      |
      v
保存业务场景识别结果
      |
      v
更新任务状态
      |
      v
前端或 API 查询任务结果
```

这一闭环必须使用真实的数据库持久化和异步事件机制，不能用内存 Map 代替数据库，也不能用同步方法假装异步任务。

---

# 第十五部分：项目最终交付目标

项目最终应交付：

1. 完整 Java 后端工程。
2. 完整 Vue 前端工程。
3. MySQL 数据库脚本。
4. Milvus 向量检索集成。
5. 文件存储和材料解析。
6. 统一 Tool Registry。
7. 银行系统 Adapter。
8. 12 个专业 Agent。
9. Planner 和 Graph 工作流。
10. 企业上下文管理。
11. 证据中心。
12. RAG 制度检索。
13. 风险线索管理。
14. 补充材料闭环。
15. 增量重分析。
16. 交互式尽调报告。
17. Word/PDF 导出。
18. 测试用例和测试报告。
19. 部署文档。
20. 系统配置说明。
21. API 接口文档。
22. Prompt 版本管理说明。
23. Agent 和 Tool 扩展说明。

最终系统应实现：

**客户经理提交企业和授信申请资料 → AI 自动识别业务场景 → 自动生成尽调计划 → 调用银行现有系统采集数据 → 12 个专业 Agent 协同分析 → 自动识别资料缺口 → 客户经理补充材料 → 增量重新分析 → 综合风险评估 → 生成交互式尽调报告 → 客户经理复核 → 导出 Word/PDF。**

系统输出的所有重要分析结论都必须能够追溯到企业原始数据、文件证据、制度条款或明确标记的 AI 推断。

本项目的核心不是让 AI 写出一份看起来专业的尽调报告，而是建立一套：

**数据可追溯、分析有依据、政策可核验、过程可审计、结果可复核、业务可持续扩展的银行对公信贷智能尽调 Agent 系统。**
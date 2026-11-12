-- ============================================================
-- 对公信贷智能尽调域（dd_*）建表脚本
-- 所属服务：omni-bank-service-agent（端口 8109）
-- 说明：模块一（尽调任务与企业上下文）、P2 证据中心、P4 尽调计划、P5 节点执行记录、P7 报告版本与人工复核留痕所需表集，可重复执行
-- ============================================================

CREATE TABLE IF NOT EXISTS dd_task (
    id                  BIGINT       NOT NULL COMMENT '主键（雪花）',
    task_no             VARCHAR(40)  NOT NULL COMMENT '任务编号',
    enterprise_id       BIGINT       NOT NULL COMMENT '企业ID',
    application_id      BIGINT       DEFAULT NULL COMMENT '授信申请ID',
    task_name           VARCHAR(200) DEFAULT NULL COMMENT '任务名称',
    task_status         VARCHAR(40)  NOT NULL DEFAULT 'DRAFT' COMMENT '任务状态',
    current_stage       VARCHAR(40)  DEFAULT NULL COMMENT '当前执行阶段',
    current_plan_id     BIGINT       DEFAULT NULL COMMENT '当前计划ID（P4 预留）',
    context_version     INT          NOT NULL DEFAULT 1 COMMENT '上下文版本',
    analysis_version    INT          NOT NULL DEFAULT 0 COMMENT '分析版本',
    last_error_code     VARCHAR(64)  DEFAULT NULL COMMENT '最近错误码',
    last_error_message  VARCHAR(1000) DEFAULT NULL COMMENT '最近错误信息',
    submitted_at        DATETIME(3)  DEFAULT NULL COMMENT '提交时间',
    completed_at        DATETIME(3)  DEFAULT NULL COMMENT '完成时间',
    creator             VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater             VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag            TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_enterprise_id (enterprise_id),
    KEY idx_application_id (application_id),
    KEY idx_task_status_create_time (task_status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '企业尽调任务';

CREATE TABLE IF NOT EXISTS dd_enterprise (
    id                  BIGINT        NOT NULL COMMENT '主键（雪花）',
    customer_id         VARCHAR(64)   DEFAULT NULL COMMENT '银行企业客户编号',
    credit_code         VARCHAR(32)   NOT NULL COMMENT '统一社会信用代码',
    enterprise_name     VARCHAR(256)  NOT NULL COMMENT '企业名称',
    legal_person        VARCHAR(128)  DEFAULT NULL COMMENT '法定代表人',
    registered_address  VARCHAR(500)  DEFAULT NULL COMMENT '注册地址',
    business_scope      TEXT          COMMENT '经营范围',
    registered_capital  DECIMAL(20,2) DEFAULT NULL COMMENT '注册资本',
    establishment_date  DATE          DEFAULT NULL COMMENT '成立日期',
    business_status     VARCHAR(64)   DEFAULT NULL COMMENT '经营状态',
    industry_code       VARCHAR(64)   DEFAULT NULL COMMENT '行业编码',
    industry_name       VARCHAR(128)  DEFAULT NULL COMMENT '行业名称',
    enterprise_profile  JSON          DEFAULT NULL COMMENT '扩展信息',
    data_source         VARCHAR(64)   DEFAULT NULL COMMENT '数据来源',
    last_sync_at        DATETIME(3)   DEFAULT NULL COMMENT '最近同步时间',
    creator             VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater             VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version             INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag            TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_credit_code (credit_code),
    KEY idx_customer_id (customer_id),
    KEY idx_enterprise_name (enterprise_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '企业主体信息';

CREATE TABLE IF NOT EXISTS dd_loan_application (
    id                   BIGINT        NOT NULL COMMENT '主键（雪花）',
    task_id              BIGINT        NOT NULL COMMENT '任务ID',
    application_no       VARCHAR(64)   DEFAULT NULL COMMENT '原信贷系统申请编号',
    application_type     VARCHAR(64)   DEFAULT NULL COMMENT '原始申请业务类型',
    product_code         VARCHAR(64)   DEFAULT NULL COMMENT '产品编码',
    product_name         VARCHAR(128)  DEFAULT NULL COMMENT '产品名称',
    apply_amount         DECIMAL(20,2) DEFAULT NULL COMMENT '申请金额',
    currency             VARCHAR(8)    DEFAULT 'CNY' COMMENT '币种',
    apply_term           INT           DEFAULT NULL COMMENT '申请期限',
    term_unit            VARCHAR(16)   DEFAULT NULL COMMENT '期限单位',
    loan_purpose         VARCHAR(1000) DEFAULT NULL COMMENT '贷款用途',
    repayment_method     VARCHAR(64)   DEFAULT NULL COMMENT '还款方式',
    guarantee_type       VARCHAR(64)   DEFAULT NULL COMMENT '担保方式',
    application_snapshot JSON          DEFAULT NULL COMMENT '原始申请快照',
    creator              VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater              VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version              INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag             TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_application_no (application_no),
    KEY idx_product_code (product_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '授信申请信息';

CREATE TABLE IF NOT EXISTS dd_business_scenario (
    id                 BIGINT       NOT NULL COMMENT '主键（雪花）',
    task_id            BIGINT       NOT NULL COMMENT '任务ID',
    scenario_version   INT          NOT NULL COMMENT '识别版本',
    business_type      VARCHAR(64)  DEFAULT NULL COMMENT '业务类型',
    product_type       VARCHAR(64)  DEFAULT NULL COMMENT '产品类型',
    industry_code      VARCHAR(64)  DEFAULT NULL COMMENT '行业编码',
    industry_name      VARCHAR(128) DEFAULT NULL COMMENT '行业名称',
    loan_purpose_type  VARCHAR(64)  DEFAULT NULL COMMENT '贷款用途类型',
    financing_mode     VARCHAR(64)  DEFAULT NULL COMMENT '融资模式',
    guarantee_types    JSON         DEFAULT NULL COMMENT '担保方式集合',
    confidence         DECIMAL(5,4) DEFAULT NULL COMMENT '识别置信度',
    identify_status    VARCHAR(32)  NOT NULL COMMENT '识别状态',
    scenario_detail    JSON         DEFAULT NULL COMMENT '识别明细',
    evidence_ids       JSON         DEFAULT NULL COMMENT '证据ID集合',
    uncertain_items    JSON         DEFAULT NULL COMMENT '待确认事项',
    model_name         VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    prompt_version     VARCHAR(64)  DEFAULT NULL COMMENT 'Prompt 版本',
    creator            VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater            VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    update_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag           TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_scenario_version (task_id, scenario_version),
    KEY idx_task_id (task_id),
    KEY idx_business_type (business_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '业务场景识别结果';

CREATE TABLE IF NOT EXISTS dd_evidence (
    id               BIGINT       NOT NULL COMMENT '主键（雪花）',
    task_id          BIGINT       NOT NULL COMMENT '任务ID',
    enterprise_id    BIGINT       DEFAULT NULL COMMENT '企业ID',
    evidence_type    VARCHAR(64)  NOT NULL COMMENT '证据类型',
    source_type      VARCHAR(64)  DEFAULT NULL COMMENT '来源类型',
    source_system    VARCHAR(64)  DEFAULT NULL COMMENT '来源系统',
    source_reference VARCHAR(500) DEFAULT NULL COMMENT '来源引用',
    subject_id       VARCHAR(64)  DEFAULT NULL COMMENT '主体标识',
    data_period      VARCHAR(64)  DEFAULT NULL COMMENT '数据期间',
    collected_at     DATETIME(3)  DEFAULT NULL COMMENT '采集时间',
    content_summary  VARCHAR(2000) DEFAULT NULL COMMENT '内容摘要',
    content_hash     VARCHAR(64)  DEFAULT NULL COMMENT '内容哈希',
    confidence       DECIMAL(5,4) DEFAULT NULL COMMENT '证据置信度',
    is_mock          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否模拟数据：0 真实 1 模拟',
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '证据状态',
    creator          VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater          VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag         TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_task_type (task_id, evidence_type),
    KEY idx_enterprise_id (enterprise_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '尽调证据索引（P2 证据中心）';

-- P2 增量：P1 已建库的环境补齐证据中心字段（MySQL 8 无 ADD COLUMN IF NOT EXISTS，用存储过程保证幂等）
DROP PROCEDURE IF EXISTS dd_evidence_p2_upgrade;
DELIMITER $$
CREATE PROCEDURE dd_evidence_p2_upgrade()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dd_evidence'
                     AND COLUMN_NAME = 'enterprise_id') THEN
        ALTER TABLE dd_evidence ADD COLUMN enterprise_id BIGINT DEFAULT NULL COMMENT '企业ID' AFTER task_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dd_evidence'
                     AND COLUMN_NAME = 'source_type') THEN
        ALTER TABLE dd_evidence ADD COLUMN source_type VARCHAR(64) DEFAULT NULL COMMENT '来源类型' AFTER evidence_type;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dd_evidence'
                     AND COLUMN_NAME = 'confidence') THEN
        ALTER TABLE dd_evidence ADD COLUMN confidence DECIMAL(5,4) DEFAULT NULL COMMENT '证据置信度' AFTER content_hash;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dd_evidence'
                     AND COLUMN_NAME = 'is_mock') THEN
        ALTER TABLE dd_evidence ADD COLUMN is_mock TINYINT NOT NULL DEFAULT 0 COMMENT '是否模拟数据：0 真实 1 模拟' AFTER confidence;
    END IF;
END$$
DELIMITER ;
CALL dd_evidence_p2_upgrade();
DROP PROCEDURE dd_evidence_p2_upgrade;

CREATE TABLE IF NOT EXISTS dd_outbox_event (
    id             BIGINT       NOT NULL COMMENT '主键（雪花）',
    event_id       VARCHAR(64)  NOT NULL COMMENT '事件ID（幂等键）',
    aggregate_type VARCHAR(64)  NOT NULL COMMENT '聚合类型',
    aggregate_id   BIGINT       NOT NULL COMMENT '聚合ID',
    event_type     VARCHAR(64)  NOT NULL COMMENT '事件类型',
    payload        JSON         NOT NULL COMMENT '事件负载',
    publish_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '发布状态',
    retry_count    INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_at  DATETIME(3)  DEFAULT NULL COMMENT '下次重试时间',
    published_at   DATETIME(3)  DEFAULT NULL COMMENT '发布时间',
    creator        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater        VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag       TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_publish_status_next_retry (publish_status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '尽调域 Outbox 事件';

CREATE TABLE IF NOT EXISTS dd_plan (
    id                 BIGINT       NOT NULL COMMENT '主键（雪花）',
    task_id            BIGINT       NOT NULL COMMENT '任务ID',
    plan_version       INT          NOT NULL COMMENT '计划版本（任务内自增）',
    planner_mode       VARCHAR(32)  NOT NULL COMMENT '计划生成模式：LLM / MOCK',
    business_scenario  VARCHAR(64)  DEFAULT NULL COMMENT '识别业务场景',
    sub_scenario       VARCHAR(64)  DEFAULT NULL COMMENT '子场景',
    confidence         DECIMAL(5, 4) DEFAULT NULL COMMENT '场景置信度',
    reasoning_summary  VARCHAR(2000) DEFAULT NULL COMMENT '规划说明',
    agent_plan         JSON         DEFAULT NULL COMMENT 'Agent 计划',
    data_plan          JSON         DEFAULT NULL COMMENT '数据采集计划',
    material_plan      JSON         DEFAULT NULL COMMENT '材料计划',
    dependency_plan    JSON         DEFAULT NULL COMMENT 'Agent 依赖关系',
    supplement_plan    JSON         DEFAULT NULL COMMENT '资料缺口计划',
    execution_config   JSON         DEFAULT NULL COMMENT '执行配置',
    model_name         VARCHAR(128) DEFAULT NULL COMMENT '模型名称（规则生成为 MOCK_RULE）',
    prompt_version     VARCHAR(64)  DEFAULT NULL COMMENT 'Prompt 版本',
    creator            VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    create_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater            VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    update_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag           TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_plan_version (task_id, plan_version),
    KEY idx_task_id (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '尽调计划（P4 Planner）';

CREATE TABLE IF NOT EXISTS dd_agent_execution (
    id               BIGINT        NOT NULL COMMENT '主键（雪花）',
    task_id          BIGINT        NOT NULL COMMENT '任务ID',
    plan_id          BIGINT        DEFAULT NULL COMMENT '计划ID（未关联计划的直接调度为空）',
    graph_code       VARCHAR(128)  NOT NULL COMMENT 'Graph 编码',
    node_id          VARCHAR(128)  NOT NULL COMMENT '节点ID',
    agent_code       VARCHAR(64)   DEFAULT NULL COMMENT 'Agent 编码（汇聚节点为空）',
    execution_no     INT           NOT NULL DEFAULT 1 COMMENT '同一任务同一节点的执行序号，恢复重跑递增',
    execution_status VARCHAR(32)   NOT NULL COMMENT '执行状态：SUCCEEDED / FAILED / SKIPPED / NOT_DELIVERED',
    summary          VARCHAR(1000) DEFAULT NULL COMMENT '结果摘要',
    error_message    VARCHAR(1000) DEFAULT NULL COMMENT '失败或跳过原因',
    input_snapshot   JSON          DEFAULT NULL COMMENT '节点输入快照',
    result_content   JSON          DEFAULT NULL COMMENT 'AgentResult 结果快照（含 findings / dataGaps / evidenceRefs）',
    model_version    VARCHAR(128)  DEFAULT NULL COMMENT '模型版本',
    prompt_version   VARCHAR(64)   DEFAULT NULL COMMENT 'Prompt 版本',
    started_at       DATETIME(3)   DEFAULT NULL COMMENT '开始时间',
    finished_at      DATETIME(3)   DEFAULT NULL COMMENT '结束时间',
    duration_ms      BIGINT        DEFAULT NULL COMMENT '耗时（毫秒）',
    creator          VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater          VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag         TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_graph_node_no (task_id, graph_code, node_id, execution_no),
    KEY idx_task_id (task_id),
    KEY idx_task_status (task_id, execution_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Agent 节点执行记录（P5 持久化与断点恢复）';

CREATE TABLE IF NOT EXISTS dd_due_diligence_report (
    id               BIGINT        NOT NULL COMMENT '主键（雪花）',
    task_id          BIGINT        NOT NULL COMMENT '任务ID',
    report_no        VARCHAR(64)   NOT NULL COMMENT '报告编号',
    report_version   INT           NOT NULL DEFAULT 1 COMMENT '报告版本，同一任务递增',
    report_status    VARCHAR(32)   NOT NULL COMMENT '报告状态：DRAFT / AI_GENERATED / WAITING_FOR_REVIEW / RETURNED_FOR_SUPPLEMENT / REANALYZING / REVIEWED / FINALIZED',
    review_status    VARCHAR(32)   NOT NULL COMMENT '复核状态：NOT_SUBMITTED / PENDING_MANUAL_REVIEW / RETURNED_FOR_SUPPLEMENT / REANALYZING / REVIEWED / FINALIZED',
    title            VARCHAR(256)  DEFAULT NULL COMMENT '报告标题',
    content_json     JSON          DEFAULT NULL COMMENT '报告章节内容（结构化 JSON）',
    evidence_ids     JSON          DEFAULT NULL COMMENT '报告引用的证据ID集合',
    evidence_count   INT           NOT NULL DEFAULT 0 COMMENT '引用证据数量',
    data_gap_count   INT           NOT NULL DEFAULT 0 COMMENT '数据缺口数量',
    supplement_count INT           NOT NULL DEFAULT 0 COMMENT '补充材料请求数量',
    export_status    VARCHAR(32)   DEFAULT NULL COMMENT '导出状态：NOT_AVAILABLE 等',
    model_version    VARCHAR(128)  DEFAULT NULL COMMENT '模型版本',
    prompt_version   VARCHAR(64)   DEFAULT NULL COMMENT 'Prompt 版本',
    generated_at     DATETIME(3)   DEFAULT NULL COMMENT '生成时间',
    creator          VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater          VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag         TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_report_version (task_id, report_version),
    KEY idx_report_task_id (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '尽调报告版本（P7）';

CREATE TABLE IF NOT EXISTS dd_report_review (
    id               BIGINT        NOT NULL COMMENT '主键（雪花）',
    task_id          BIGINT        NOT NULL COMMENT '任务ID',
    report_id        BIGINT        NOT NULL COMMENT '报告ID',
    report_version   INT           NOT NULL COMMENT '报告版本',
    action_type      VARCHAR(32)   NOT NULL COMMENT '复核动作：SUBMIT / CONFIRM / SUPPLEMENT / AMEND / REJECT / REQUEST_MATERIAL / REANALYZE / FINALIZE',
    from_status      VARCHAR(32)   NOT NULL COMMENT '动作前报告状态',
    to_status        VARCHAR(32)   NOT NULL COMMENT '动作后报告状态',
    field_key        VARCHAR(64)   DEFAULT NULL COMMENT '修正章节编码（仅修正动作）',
    original_value   TEXT          COMMENT '修正前内容',
    amended_value    TEXT          COMMENT '修正后内容',
    reviewer         VARCHAR(64)   NOT NULL COMMENT '复核人',
    reason           VARCHAR(500)  DEFAULT NULL COMMENT '复核原因',
    remark           VARCHAR(1000) DEFAULT NULL COMMENT '复核意见',
    evidence_ids     JSON          DEFAULT NULL COMMENT '关联证据ID集合',
    creator          VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater          VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    del_flag         TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_review_task_version (task_id, report_version),
    KEY idx_review_report_id (report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '尽调报告人工复核留痕（P7）';

CREATE TABLE IF NOT EXISTS dd_finding (
    id                       BIGINT        NOT NULL COMMENT '主键（雪花）',
    task_id                  BIGINT        NOT NULL COMMENT '任务ID',
    plan_id                  BIGINT        DEFAULT NULL COMMENT '计划ID',
    execution_id             BIGINT        DEFAULT NULL COMMENT 'Agent 执行记录ID（dd_agent_execution.id）',
    agent_code               VARCHAR(64)   DEFAULT NULL COMMENT 'Agent 编码',
    node_id                  VARCHAR(128)  DEFAULT NULL COMMENT 'Graph 节点ID',
    finding_code             VARCHAR(64)   NOT NULL COMMENT '线索编码',
    category                 VARCHAR(64)   DEFAULT NULL COMMENT '线索分类',
    severity                 VARCHAR(32)   DEFAULT NULL COMMENT '严重程度：LOW / MEDIUM / HIGH / CRITICAL / UNKNOWN',
    title                    VARCHAR(256)  DEFAULT NULL COMMENT '标题',
    description              VARCHAR(4000) DEFAULT NULL COMMENT '描述',
    confidence               DECIMAL(5, 4) DEFAULT NULL COMMENT '置信度',
    status                   VARCHAR(32)   NOT NULL COMMENT '线索状态：CONFIRMED / NEEDS_VERIFICATION / MODEL_INFERENCE / REJECTED / RESOLVED',
    evidence_refs            JSON          DEFAULT NULL COMMENT '关联证据引用',
    related_finding_codes    JSON          DEFAULT NULL COMMENT '关联线索编码',
    verification_suggestion  VARCHAR(1000) DEFAULT NULL COMMENT '核实建议',
    creator                  VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater                  VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version                  INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag                 TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_finding_task_agent (task_id, agent_code),
    KEY idx_finding_task_severity (task_id, severity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '尽调风险线索（P9）';

CREATE TABLE IF NOT EXISTS dd_supplement_request (
    id                BIGINT        NOT NULL COMMENT '主键（雪花）',
    task_id           BIGINT        NOT NULL COMMENT '任务ID',
    plan_id           BIGINT        DEFAULT NULL COMMENT '计划ID',
    execution_id      BIGINT        DEFAULT NULL COMMENT 'Agent 执行记录ID（dd_agent_execution.id）',
    agent_code        VARCHAR(64)   DEFAULT NULL COMMENT '提出请求的 Agent 编码，手工创建可为空',
    data_code         VARCHAR(64)   NOT NULL COMMENT '数据编码',
    data_name         VARCHAR(256)  DEFAULT NULL COMMENT '数据名称',
    reason            VARCHAR(1000) DEFAULT NULL COMMENT '请求原因',
    suggested_action  VARCHAR(1000) DEFAULT NULL COMMENT '建议补充方式',
    status            VARCHAR(32)   NOT NULL COMMENT '请求状态：PENDING / COMPLETED / CANCELLED',
    source            VARCHAR(32)   NOT NULL COMMENT '来源：AGENT / MANUAL',
    remark            VARCHAR(1000) DEFAULT NULL COMMENT '完成说明',
    completed_at      DATETIME(3)   DEFAULT NULL COMMENT '完成时间',
    creator           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updater           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag          TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标志：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_supplement_task_status (task_id, status),
    KEY idx_supplement_task_data (task_id, data_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '尽调补充材料请求（P9）';

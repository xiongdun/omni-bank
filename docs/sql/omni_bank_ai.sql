-- =====================================================================
-- omni-bank 智能体域建表脚本（MySQL 8.x）
-- 库：omni_bank ｜ 前缀：ai_ ｜ 公共字段：id/create_time/update_time/creator/updater/del_flag
-- =====================================================================

-- ----------------------------
-- 1. 智能体定义表
-- ----------------------------
CREATE TABLE `ai_agent` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `agent_code`    VARCHAR(64)  NOT NULL COMMENT '智能体编码',
    `agent_name`    VARCHAR(100) NOT NULL COMMENT '智能体名称',
    `description`   VARCHAR(500) NULL COMMENT '描述',
    `model_name`    VARCHAR(64)  NOT NULL COMMENT '模型名称（如 qwen-max）',
    `system_prompt` TEXT         NULL COMMENT '系统提示词',
    `temperature`   DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '采样温度（0-2）',
    `max_tokens`    INT          NOT NULL DEFAULT 2048 COMMENT '单次回复最大 token 数',
    `status`        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `remark`        VARCHAR(500) NULL COMMENT '备注',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`       VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`       VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_code` (`agent_code`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '智能体定义表';

-- ----------------------------
-- 2. 会话表
-- ----------------------------
CREATE TABLE `ai_conversation` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `agent_id`          BIGINT       NOT NULL COMMENT '智能体 ID',
    `title`             VARCHAR(200) NOT NULL COMMENT '会话标题',
    `user_id`           BIGINT       NOT NULL DEFAULT 0 COMMENT '归属用户 ID（0 系统/匿名）',
    `status`            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 进行中 1 已结束）',
    `last_message_time` DATETIME     NULL COMMENT '最后一条消息时间',
    `del_flag`          TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`           VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`           VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '智能体会话表';

-- ----------------------------
-- 3. 会话消息表
-- ----------------------------
CREATE TABLE `ai_message` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `conversation_id`   BIGINT       NOT NULL COMMENT '会话 ID',
    `role`              VARCHAR(16)  NOT NULL COMMENT '角色（user 用户 / assistant 助手 / system 系统）',
    `content`           MEDIUMTEXT   NOT NULL COMMENT '消息内容',
    `prompt_tokens`     INT          NULL COMMENT '输入 token 数',
    `completion_tokens` INT          NULL COMMENT '输出 token 数',
    `latency_ms`        BIGINT       NULL COMMENT '耗时（毫秒）',
    `error_msg`         VARCHAR(1000) NULL COMMENT '错误信息（模型调用失败时记录）',
    `del_flag`          TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`           VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`           VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '智能体会话消息表';

-- ----------------------------
-- 4. 初始化数据：信贷业务助手
-- ----------------------------
INSERT INTO `ai_agent` (`id`, `agent_code`, `agent_name`, `description`, `model_name`, `system_prompt`, `temperature`, `max_tokens`, `status`, `creator`, `updater`)
VALUES (1, 'loan-assistant', '信贷业务助手',
        '面向客户经理的信贷业务智能问答助手',
        'qwen-max',
        '你是 omni-bank 信贷业务助手，熟悉进件、授信、额度、借据、还款与贷后管理流程。回答需基于行内制度，不确定时明确说明并建议人工复核。',
        0.30, 2048, '0', 'system', 'system');

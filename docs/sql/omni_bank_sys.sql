-- =====================================================================
-- omni-bank 系统管理域建表脚本（MySQL 8.x）
-- 库：omni_bank ｜ 前缀：sys_ ｜ 公共字段：id/create_time/update_time/creator/updater/del_flag
-- 初始账号：admin / admin123（BCrypt 存储，首次登录后请修改）
-- =====================================================================

-- ----------------------------
-- 1. 机构/部门表（单表树：org_type 区分 1 机构 2 部门）
-- ----------------------------
CREATE TABLE `sys_org` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父级 ID（0 为根）',
    `ancestors`   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖级路径（逗号分隔，含自身上级）',
    `org_name`    VARCHAR(50)  NOT NULL COMMENT '名称',
    `org_type`    CHAR(1)      NOT NULL DEFAULT '2' COMMENT '类型（1 机构 2 部门）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `leader`      VARCHAR(50)  NULL COMMENT '负责人',
    `phone`       VARCHAR(20)  NULL COMMENT '联系电话',
    `email`       VARCHAR(100) NULL COMMENT '邮箱',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '机构/部门表';

-- ----------------------------
-- 2. 用户表
-- ----------------------------
CREATE TABLE `sys_user` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `org_id`      BIGINT       NOT NULL COMMENT '所属机构/部门 ID',
    `username`    VARCHAR(30)  NOT NULL COMMENT '登录账号',
    `nickname`    VARCHAR(50)  NOT NULL COMMENT '用户昵称',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
    `user_type`   CHAR(1)      NOT NULL DEFAULT '2' COMMENT '用户类型（1 系统用户 2 业务用户）',
    `phone`       VARCHAR(20)  NULL COMMENT '手机号',
    `email`       VARCHAR(100) NULL COMMENT '邮箱',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_org_id` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ----------------------------
-- 3. 岗位表
-- ----------------------------
CREATE TABLE `sys_post` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `post_code`   VARCHAR(50) NOT NULL COMMENT '岗位编码',
    `post_name`   VARCHAR(50) NOT NULL COMMENT '岗位名称',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`      CHAR(1)     NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    `del_flag`    TINYINT     NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_code` (`post_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '岗位表';

-- ----------------------------
-- 4. 用户-岗位关联表
-- ----------------------------
CREATE TABLE `sys_user_post` (
    `id`          BIGINT   NOT NULL COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 ID',
    `post_id`     BIGINT   NOT NULL COMMENT '岗位 ID',
    `del_flag`    TINYINT  NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-岗位关联表';

-- ----------------------------
-- 5. 角色表（data_scope：1 全部 2 自定义 3 本机构 4 本部门及以下 5 仅本人）
-- ----------------------------
CREATE TABLE `sys_role` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `role_name`   VARCHAR(50)  NOT NULL COMMENT '角色名称',
    `role_code`   VARCHAR(50)  NOT NULL COMMENT '角色编码（如 super_admin）',
    `data_scope`  CHAR(1)      NOT NULL DEFAULT '1' COMMENT '数据权限（1 全部 2 自定义 3 本机构 4 本部门及以下 5 仅本人）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ----------------------------
-- 6. 用户-角色关联表
-- ----------------------------
CREATE TABLE `sys_user_role` (
    `id`          BIGINT   NOT NULL COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 ID',
    `role_id`     BIGINT   NOT NULL COMMENT '角色 ID',
    `del_flag`    TINYINT  NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联表';

-- ----------------------------
-- 7. 菜单/权限点表（menu_type：M 目录 C 菜单 F 按钮；permission 如 sys:user:add）
-- ----------------------------
CREATE TABLE `sys_menu` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父级 ID（0 为根）',
    `menu_name`   VARCHAR(50)  NOT NULL COMMENT '菜单/权限点名称',
    `menu_type`   CHAR(1)      NOT NULL DEFAULT 'C' COMMENT '类型（M 目录 C 菜单 F 按钮）',
    `permission`  VARCHAR(100) NULL COMMENT '权限标识（如 sys:user:add，按钮类型必填）',
    `route_path`  VARCHAR(200) NULL COMMENT '路由地址',
    `component`   VARCHAR(200) NULL COMMENT '前端组件路径',
    `icon`        VARCHAR(100) NULL COMMENT '图标',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `visible`     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否可见（0 显示 1 隐藏）',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单/权限点表';

-- ----------------------------
-- 8. 角色-菜单关联表
-- ----------------------------
CREATE TABLE `sys_role_menu` (
    `id`          BIGINT   NOT NULL COMMENT '主键',
    `role_id`     BIGINT   NOT NULL COMMENT '角色 ID',
    `menu_id`     BIGINT   NOT NULL COMMENT '菜单 ID',
    `del_flag`    TINYINT  NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64) NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-菜单关联表';

-- ----------------------------
-- 9. 字典类型表
-- ----------------------------
CREATE TABLE `sys_dict_type` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `dict_name`   VARCHAR(50)  NOT NULL COMMENT '字典名称',
    `dict_type`   VARCHAR(50)  NOT NULL COMMENT '字典类型（唯一键）',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型表';

-- ----------------------------
-- 10. 字典数据表
-- ----------------------------
CREATE TABLE `sys_dict_data` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `dict_type`   VARCHAR(50)  NOT NULL COMMENT '字典类型',
    `dict_label`  VARCHAR(100) NOT NULL COMMENT '字典标签',
    `dict_value`  VARCHAR(100) NOT NULL COMMENT '字典键值',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 停用）',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_dict_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典数据表';

-- ----------------------------
-- 11. 系统设置（参数配置）表
-- ----------------------------
CREATE TABLE `sys_config` (
    `id`           BIGINT       NOT NULL COMMENT '主键',
    `config_name`  VARCHAR(100) NOT NULL COMMENT '参数名称',
    `config_key`   VARCHAR(100) NOT NULL COMMENT '参数键（唯一）',
    `config_value` VARCHAR(500) NOT NULL COMMENT '参数值',
    `remark`       VARCHAR(500) NULL COMMENT '备注',
    `del_flag`     TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`      VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`      VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统设置（参数配置）表';

-- ----------------------------
-- 12. 登录日志表（无审计字段，仅 login_time）
-- ----------------------------
CREATE TABLE `sys_login_log` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `username`    VARCHAR(30)  NOT NULL COMMENT '登录账号',
    `ip`          VARCHAR(64)  NULL COMMENT '登录 IP',
    `browser`     VARCHAR(64)  NULL COMMENT '浏览器',
    `os`          VARCHAR(64)  NULL COMMENT '操作系统',
    `status`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 成功 1 失败）',
    `msg`         VARCHAR(255) NULL COMMENT '提示信息',
    `login_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_login_time` (`login_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录日志表';

-- ----------------------------
-- 13. 操作日志表（无审计字段，仅 oper_time）
-- ----------------------------
CREATE TABLE `sys_oper_log` (
    `id`             BIGINT       NOT NULL COMMENT '主键',
    `title`          VARCHAR(50)  NOT NULL COMMENT '模块标题（如 用户管理）',
    `oper_type`      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '操作类型（0 其他 1 新增 2 修改 3 删除 4 授权 5 导出）',
    `method`         VARCHAR(200) NULL COMMENT '方法名（类#方法）',
    `request_url`    VARCHAR(255) NULL COMMENT '请求 URL',
    `request_method` VARCHAR(10)  NULL COMMENT '请求方式（GET/POST...）',
    `oper_param`     TEXT         NULL COMMENT '请求参数',
    `result_code`    VARCHAR(10)  NULL COMMENT '响应码',
    `result_msg`     VARCHAR(500) NULL COMMENT '响应消息',
    `oper_ip`        VARCHAR(64)  NULL COMMENT '操作 IP',
    `oper_user`      VARCHAR(64)  NULL COMMENT '操作人（登录账号）',
    `status`         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0 成功 1 失败）',
    `error_msg`      TEXT         NULL COMMENT '错误信息',
    `oper_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_title` (`title`),
    KEY `idx_oper_time` (`oper_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

-- ----------------------------
-- 14. 通知公告表
-- ----------------------------
CREATE TABLE `sys_notice` (
    `id`            BIGINT      NOT NULL COMMENT '主键',
    `notice_title`  VARCHAR(100) NOT NULL COMMENT '公告标题',
    `notice_type`   CHAR(1)     NOT NULL DEFAULT '1' COMMENT '类型（1 通知 2 公告）',
    `notice_content` TEXT       NULL COMMENT '公告内容',
    `status`        CHAR(1)     NOT NULL DEFAULT '0' COMMENT '状态（0 正常 1 关闭）',
    `del_flag`      TINYINT     NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME    NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`       VARCHAR(64) NULL DEFAULT 'system' COMMENT '创建人',
    `updater`       VARCHAR(64) NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知公告表';

-- ----------------------------
-- 15. 附件表
-- ----------------------------
CREATE TABLE `sys_file` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `file_name`   VARCHAR(200) NOT NULL COMMENT '原始文件名',
    `file_path`   VARCHAR(500) NOT NULL COMMENT '存储路径/对象键',
    `file_suffix` VARCHAR(20)  NULL COMMENT '后缀（pdf/png...）',
    `file_size`   BIGINT       NULL COMMENT '文件大小（字节）',
    `biz_type`    VARCHAR(50)  NULL COMMENT '业务类型（预留）',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志（0 未删 1 已删）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creator`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '创建人',
    `updater`     VARCHAR(64)  NULL DEFAULT 'system' COMMENT '更新人',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '附件表';

-- =====================================================================
-- 初始化数据
-- =====================================================================

-- 机构/部门：总行 + 总行本部
INSERT INTO `sys_org` (`id`, `parent_id`, `ancestors`, `org_name`, `org_type`, `sort`) VALUES
(1, 0, '0',    'omni-bank 总行', '1', 0),
(2, 1, '0,1',  '总行本部',       '2', 1);

-- 用户：超级管理员（初始密码 admin123，BCrypt，首次登录后修改）
INSERT INTO `sys_user` (`id`, `org_id`, `username`, `nickname`, `password`, `user_type`) VALUES
(1, 2, 'admin', '超级管理员', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '1');

-- 岗位示例
INSERT INTO `sys_post` (`id`, `post_code`, `post_name`, `sort`) VALUES
(1, 'ceo',    '行长',   1),
(2, 'mgr',    '部门经理', 2);

-- 角色：超级管理员（数据权限=全部；代码层约定 super_admin 拥有全部权限点）
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `data_scope`, `sort`, `remark`) VALUES
(1, '超级管理员', 'super_admin', '1', 1, '系统内置角色，拥有全部权限');

-- 用户-角色
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES (1, 1, 1);

-- 菜单树：系统管理目录 + 8 个菜单 + 用户管理按钮权限示例
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `route_path`, `sort`) VALUES
(100, 0,   '系统管理', 'M', NULL,          '/system',        1),
(101, 100, '用户管理', 'C', 'sys:user:list', 'system/user',  1),
(102, 100, '角色管理', 'C', 'sys:role:list', 'system/role',  2),
(103, 100, '菜单管理', 'C', 'sys:menu:list', 'system/menu',  3),
(104, 100, '机构部门', 'C', 'sys:org:list',  'system/org',   4),
(105, 100, '岗位管理', 'C', 'sys:post:list', 'system/post',  5),
(106, 100, '字典管理', 'C', 'sys:dict:list', 'system/dict',  6),
(107, 100, '参数设置', 'C', 'sys:config:list', 'system/config', 7),
(108, 100, '日志管理', 'C', 'sys:log:list',  'system/log',   8),
(109, 100, '通知公告', 'C', 'sys:notice:list', 'system/notice', 9),
(1001, 101, '用户查询', 'F', 'sys:user:query', NULL, 1),
(1002, 101, '用户新增', 'F', 'sys:user:add',   NULL, 2),
(1003, 101, '用户修改', 'F', 'sys:user:edit',  NULL, 3),
(1004, 101, '用户删除', 'F', 'sys:user:remove', NULL, 4),
(1005, 101, '重置密码', 'F', 'sys:user:resetPwd', NULL, 5),
(1006, 102, '角色查询', 'F', 'sys:role:query', NULL, 1),
(1007, 102, '角色新增', 'F', 'sys:role:add',   NULL, 2),
(1008, 102, '角色修改', 'F', 'sys:role:edit',  NULL, 3),
(1009, 102, '角色删除', 'F', 'sys:role:remove', NULL, 4);

-- 角色-菜单：超级管理员绑定系统管理目录及全部子级
INSERT INTO `sys_role_menu` (`id`, `role_id`, `menu_id`) VALUES
(1, 1, 100), (2, 1, 101), (3, 1, 102), (4, 1, 103), (5, 1, 104), (6, 1, 105),
(7, 1, 106), (8, 1, 107), (9, 1, 108), (10, 1, 109), (11, 1, 1001), (12, 1, 1002),
(13, 1, 1003), (14, 1, 1004), (15, 1, 1005), (16, 1, 1006), (17, 1, 1007),
(18, 1, 1008), (19, 1, 1009);

-- 字典：通用状态
INSERT INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `remark`) VALUES
(1, '系统状态', 'sys_normal_disable', '通用启用/停用状态');

INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `sort`) VALUES
(1, 'sys_normal_disable', '正常', '0', 1),
(2, 'sys_normal_disable', '停用', '1', 2);

-- 系统设置：用户重置密码的初始密码
INSERT INTO `sys_config` (`id`, `config_name`, `config_key`, `config_value`, `remark`) VALUES
(1, '用户初始密码', 'sys.user.init.password', '123456', '重置密码时使用的初始密码，用户首次登录后应修改');

-- 通知公告示例
INSERT INTO `sys_notice` (`id`, `notice_title`, `notice_type`, `notice_content`) VALUES
(1, '系统上线通知', '1', 'omni-bank 管理系统已上线，初始账号 admin / admin123，请及时修改密码。');

# 银行对公信贷智能尽调 Agent（模块一交付说明）

本说明对应 REQUIREMENTS 第 12 条交付要求，覆盖当前已完成范围：**P0 工程骨架 + P1 模块一（尽调任务与企业上下文）**。
12 个专项 Agent 仅落地契约接口，不包含分析逻辑。

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

服务内路径前缀 `/v1/dd/tasks`；经网关转发时对外为 `/api/v1/dd/tasks/**`（网关路由登记见第 6 节）。
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

### 任务状态

`DRAFT(0) → INITIALIZING(10) → SCENARIO_IDENTIFYING(20) → PLANNING(30) → DATA_COLLECTING(40) → ANALYZING(60) → RISK_ASSESSING(80) → REPORT_GENERATING(90) → WAITING_FOR_REVIEW(95) → COMPLETED(100)`。

P1 实际链路止于 `PLANNING`；`WAITING_FOR_SUPPLEMENT(60)`、`REANALYZING(70)`、`RETURNED(80)`、`FAILED(0)`、`CANCELLED(0)` 为后续阶段预留。非法流转返回 `230102`，并发更新冲突返回 `230103`。

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

## 3. 数据库说明

DDL：`docs/sql/omni_bank_dd.sql`（MySQL 8，可重复执行）。

| 表 | 用途 | 关键约束 |
|---|---|---|
| `dd_task` | 尽调任务主表 | `uk_task_no`；`idx_enterprise_id`、`idx_application_id`、`idx_task_status_create_time` |
| `dd_enterprise` | 企业主体信息 | `uk_credit_code`；`idx_customer_id`、`idx_enterprise_name` |
| `dd_loan_application` | 授信申请与原始快照 | `uk_task_id`（一任务一申请）；`idx_application_no`、`idx_product_code` |
| `dd_business_scenario` | 业务场景识别结果（多版本） | `uk_task_scenario_version`；`idx_task_id`、`idx_business_type` |
| `dd_evidence` | 证据索引（模块一最小结构，P2 证据中心扩展） | `idx_task_id`、`idx_task_type` |
| `dd_outbox_event` | 异步事件 Outbox | `uk_event_id`；`idx_publish_status_next_retry` |

公共约定：雪花 `BIGINT` 主键；`version` 乐观锁；`del_flag` 逻辑删除；`creator/updater/create_time/update_time` 自动填充；时间 `DATETIME(3)`；JSON 列存业务扩展。

## 4. 异步机制与一致性

- 提交任务在同一事务内写 `dd_task` 状态与 `dd_outbox_event`（`TASK_SUBMITTED`）。
- `DdOutboxPublisher` 每 1s 扫描 `PENDING` 且到期的记录（批量 20），发布 Spring 本地事件后置 `PUBLISHED`；失败累加 `retry_count`，最多 5 次，指数退避（60s 起，上限 300s）。
- `DdTaskInitHandler` 消费 `TASK_SUBMITTED`：条件更新抢占 `INITIALIZING → SCENARIO_IDENTIFYING`，执行规则识别后进入 `PLANNING`；重复投递因状态不符被忽略（幂等）。
- 所有状态变更使用 `WHERE id = ? AND version = ?` 条件更新，影响行数不为 1 即判定并发冲突。
- SSE 为单实例内存注册表（`DdSseRegistry`），断线后前端应回落到 `/status` 接口恢复。

## 5. 测试结果

```
DdTaskFlowIntegrationTest   Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
TaskStateMachineTest        Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
合计                         Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖：企业复用与申请快照、提交→Outbox→场景识别→PLANNING 全链路、事件重复投递幂等、规则未命中进入待确认、取消后不可提交、状态机合法/非法流转。

## 6. 未完成事项与待确认

1. **12 个专项 Agent 未实现**：`dd/ai` 仅提供 `CreditAgent`、`AgentContext`、`AgentDefinition`、`AgentResult` 等契约模型，无任何分析逻辑。
2. **P1 之后链路未实现**：数据采集、证据中心、分析编排（Graph）、风险测算、报告生成、人工复核。
3. **银行接口 Mock Adapter 未落地**：模块一未调用外部银行接口；后续对接必须显式标注 Mock。
4. **场景识别规则待业务确认**：当前按产品编码 + 贷款用途关键词配置在 `omni.dd.scenario.rules`，置信度阈值 0.60；规则口径与阈值需银行业务部门确认。
5. **鉴权未在 dd 端点启用**：当前依赖网关与 `UserContextFilter` 透传 `X-User-*`，内网直连 8109 时无鉴权。
6. **网关路由未登记**：根 README 已注明 system 与 agent 路由待登记，`/api/v1/dd/**` 暂需直连 8109 验证。
7. **Outbox 未接 MQ**：当前为单库自扫描 + Spring 本地事件，多实例部署时需替换为 MQ 或加分布式锁。
8. **SSE 多实例不互通**：内存注册表仅适用于单实例，水平扩展需引入 Redis Pub/Sub。
9. **H2 测试 DDL 与 MySQL DDL 双份维护**：`src/test/resources/dd-schema-h2.sql` 需随 `docs/sql/omni_bank_dd.sql` 同步。
10. **待确认字段**：`current_plan_id`、`analysis_version` 的推进规则，`dd_evidence` 的证据类型枚举扩展，均待 P2/P4 规格明确。

# AGENTS.md

> **本文件是本项目的技术规范与协作约定唯一事实源**，面向所有参与的开发者与 AI 编码助手（Claude Code / Trae / Cursor 等）。
> `CLAUDE.md` 保留行为准则并引用本文件；业务需求见 [REQUIREMENTS.md](REQUIREMENTS.md)；工程使用说明见 [README.md](README.md)。
> 修改项目结构、端口、错误码、公共组件或技术规范时，**必须同步更新本文件**。

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## 5. 项目概览

**omni-bank** 是银行信贷业务线上化平台，覆盖客户准入 → 进件审批 → 授信 → 放款 → 还款 → 贷后的完整链路，并提供账务、支付、对账、风控、AI 智能体等支撑能力。

- 语言/运行时：Java 21
- 架构风格：微服务（按业务域拆分）+ 域内分层（信贷域 DDD、管理与智能体域 MVC）
- 对外入口：`omni-bank-gateway`（Spring Cloud Gateway，HTTP）
- 服务间通信：统一 Dubbo RPC（triple 协议 + Nacos 注册），不允许 Feign/RestTemplate 直连下游
- 数据存储：MySQL 8.x + MyBatis-Plus
- 认证授权：Sa-Token（system 域签发 token，其他域经 Dubbo 契约校验权限）

## 6. 技术版本基线

| 组件 | 版本 | 说明 |
|---|---|---|
| Java | 21 | 使用 `@Serial`、record 等新特性 |
| Spring Boot | 3.5.16 | 升级需同步 parent 与 BOM 两处 |
| Spring Cloud | 2025.0.3 | |
| Spring Cloud Alibaba | 2025.0.0.0 | |
| Dubbo | 3.3.6 | SCA BOM 不纳管，走 `dubbo-bom` |
| MyBatis-Plus | 3.5.17 | **包路径已迁移，见第 9.3 节** |
| Sa-Token | 1.46.0 | |
| Spring AI | 1.1.2 | Agent 框架统一模型抽象（`ChatClient` / `ChatModel`），BOM 需在 SAA BOM 之前 import |
| Spring AI Alibaba | 1.1.2.4-security-fix | Agent Graph 编排（`StateGraph` / `CompiledGraph`），对应 Spring AI 1.1.2 |
| TransmittableThreadLocal | 2.14.5 | 用户上下文跨线程传递 |
| ArchUnit | 1.5.1 | 仅用原生 API，见第 11 节 |

**版本变更纪律**：

1. 三方版本只在 `omni-bank-dependencies` 修改，业务模块 POM 不写 `<version>`。
2. Spring Boot 升级需同步 `omni-bank-parent` 的 `spring-boot-starter-parent` 与 BOM 的 `spring-boot.version`。
3. Lombok / MapStruct 升级需同步 `omni-bank-parent` 中 `annotationProcessorPaths` 的硬编码版本。
4. 新增第三方库必须在 BOM 登记，并确认不与企业安全基线冲突（许可证、CVE）。

## 7. 仓库结构与模块职责

```
omni-bank/
├── pom.xml                       # 聚合根（不做依赖管理，仅 modules + 仓库声明）
├── omni-bank-dependencies/       # 纯 BOM，刻意不声明 parent，可被任意工程 import
├── omni-bank-parent/             # 以 spring-boot-starter-parent 为父；import BOM；纳管内部构件版本
├── omni-bank-common/             # 公共组件聚合
│   ├── omni-bank-common-core     # Result/错误码/异常/UserContext/分页/基类/校验分组/BaseEnum
│   ├── omni-bank-common-web      # 全局异常处理/BaseController/BaseEnum Jackson 模块
│   ├── omni-bank-common-mybatis  # 分页插件/BaseEntity/审计填充/PageResults
│   ├── omni-bank-common-local-cache
│   ├── omni-bank-common-remote-cache
│   ├── omni-bank-common-mq
│   ├── omni-bank-common-idempotent
│   ├── omni-bank-common-lock
│   ├── omni-bank-common-excel
│   └── omni-bank-common-threadpool   # TTL 装饰的统一业务线程池
├── omni-bank-gateway/            # SCG + Sentinel 限流熔断 + Nacos 发现路由
├── omni-bank-ui/                 # 尽调工作台前端（Vue 3 + TS + Vite + Element Plus，独立构建）
└── omni-bank-service/            # 业务域
    ├── omni-bank-service-customer     # 8101
    ├── omni-bank-service-product      # 8102
    ├── omni-bank-service-credit       # 8103（含 credit/loan/repay/limit/mortgage/postloan 场景模块）
    ├── omni-bank-service-system       # 8104（api + boot，MVC 包结构）
    ├── omni-bank-service-accounting   # 8105
    ├── omni-bank-service-payment      # 8106
    ├── omni-bank-service-recon        # 8107
    ├── omni-bank-service-risk         # 8108
    └── omni-bank-service-agent        # 8109（api + boot，MVC 包结构）
```

**两种域内组织方式，按域复杂度选择，不要混用**：

- **场景型域（如 credit）**：`父模块` + `场景子模块` + `boot`。场景模块内部 DDD 分层（`application` / `domain` / `infrastructure`），**不含 controller 与 rpc**；`adapter.web` / `adapter.rpc` 统一收口在 boot。
- **简单型域（system / agent）**：`api` + `boot` 双模块，业务代码全部放 boot 内 MVC 包结构（controller / service / mapper / entity / dto），按业务包名区分场景，不再拆场景子模块。

**前端工程（omni-bank-ui）**：Vue 3 + TypeScript + Vite + Element Plus，独立于 Maven 聚合构建。开发态 `/api` 代理到 agent 服务 8109 并剥离前缀（与网关 `StripPrefix=1` 对齐）；生产构建产物 `dist/` 由静态服务器托管。前端仅通过 HTTP 访问后端，不得引入跨域直连或 Dubbo 调用。

**端口与错误码号段**：

| 域 | HTTP | Dubbo(triple) | 错误码 |
|---|---|---|---|
| customer | 8101 | 20881 | 100001-109999 |
| product | 8102 | 20882 | 110001-119999 |
| credit(进件/授信) | 8103 | 20883 | 120001-129999 |
| loan | — | — | 130001-139999 |
| accounting | 8105 | 20885 | 140001-149999 |
| payment | 8106 | 20886 | 150001-159999 |
| recon | 8107 | 20887 | 160001-169999 |
| risk | 8108 | 20888 | 170001-179999 |
| repay | — | — | 180001-189999 |
| limit | — | — | 190001-199999 |
| mortgage | — | — | 200001-209999 |
| postloan | — | — | 210001-219999 |
| system | 8104 | 20884 | 220001-229999 |
| agent | 8109 | 20889 | 230001-239999 |

新增域必须在此表登记，并同步 `REQUIREMENTS.md`、`README.md` 与网关路由。

## 8. 常用命令

```bash
# 全量构建（含本地安装，模块间可互相引用）
mvn -ntp clean install -T 1C

# 仅构建某模块（依赖需已在本地仓库）
mvn -ntp install -pl omni-bank-common/omni-bank-common-core

# 构建模块及其上游依赖（首次引入新模块时用）
mvn -ntp install -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot -am

# 单元测试（含 ArchUnit 架构守护）
mvn -ntp -pl <模块路径> test

# 本地启动网关（默认 8080，无 Nacos 时默认关闭注册发现）
mvn spring-boot:run -pl omni-bank-gateway

# 本地启动业务服务（以 agent 为例，端口 8109）
mvn spring-boot:run -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot

# 无 MySQL/Docker 时用 H2 内存库启动 agent（数据重启清空，仅功能联调）
mvn -Plocal-h2 -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot spring-boot:run -Dspring-boot.run.profiles=h2

# 尽调工作台前端（Node 20+，默认 http://localhost:5173/workbench；/api 代理到 8109）
cd omni-bank-ui && npm install && npm run dev
```

**本地环境要求**：JDK 21、Maven 3.9+、MySQL 8.x（库名 `omni_bank`）。首次运行需执行 `docs/sql/*.sql`，否则 HikariCP 初始化会阻断启动。

## 9. 编码规范

### 9.1 分层与依赖边界

- **场景型域**：场景模块只依赖 `common-*` 与自身领域代码，禁止依赖其它场景模块；跨场景协作在 boot 的 application 层编排。
- **简单型域**：controller → service → mapper 单向依赖，controller 不写业务逻辑，service 不返回 `IPage` 给 controller 之外的下游。
- **跨域调用**：只允许经 `outbound` 端口（场景型域）或本域 service（简单型域）封装 `@DubboReference`；适配层不得直接调用下游客户端。
- **ArchUnit 守护**：boot 模块内置边界测试，新增场景必须同步扩展 `ArchitectureTest` 的包名正则；违规即构建失败。

### 9.2 公共组件与自动装配

- 所有 `omni-bank-common-*` 组件通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动装配。
- **业务 boot 模块禁止再往启动类加 `scanBasePackages` 扫描 common 包**；启动类只扫描本域业务包（`com.omnibank.service.{域}`）。
- 公共组件不得依赖任何业务模块；新增第三方依赖前先评估是否可下沉到 BOM。

### 9.3 数据访问（MyBatis-Plus 3.5.17 特别注意）

- `IService` / `ServiceImpl` 位于 **`com.baomidou.mybatisplus.spring.service` / `...spring.service.impl`**（不是旧版 `extension.service`）。
- `IRepository.getById(Serializable)` 与自定义 `getById(Long)` 是**重载不是覆盖**，接口重声明时**不能标 `@Override`**。
- 实体统一继承 `com.omnibank.common.mybatis.entity.BaseEntity`（雪花 ID + 审计字段 + 逻辑删除），表名用 `@TableName("...")`。
- 分页查询统一 `Page<T>`；service 层返回 `IPage<T>`，controller 层用 `PageResults.from(ipage)` 转 `PageResult<T>`。
- 关联表或非本 Service 实体的批量操作使用 `Db` 工具类（`com.baomidou.mybatisplus.extension.toolkit.Db`），避免注入本无归属的 Mapper。
- Mapper 接口标 `@Mapper` 并由启动类 `@MapperScan(..., annotationClass = Mapper.class)` 扫描。

### 9.4 Web 层规范

```java
// 统一响应
return Result.success(data);
throw new BizException(AgentErrorCode.AGENT_NOT_FOUND);

// 分页入参继承 PageQuery（current 从 1 开始，size 上限 100）
public class AgentQueryReq extends PageQuery { ... }

// 分页出参
public Result<PageResult<AiAgent>> page(AgentQueryReq req) {
    return Result.success(PageResults.from(agentService.page(req)));
}
```

- 请求体 DTO 继承 `BaseDTO`，并加 `@EqualsAndHashCode(callSuper = true)`（Lombok 会告警）。
- 视图对象继承 `BaseVO`；实体/PO 使用 `BaseEntity`，**不单独造 BasePO**。
- Controller 需要操作人时继承 `BaseController`，用 `getOperator()` / `getCurrentUser()`，不要直接读 Sa-Token。
- 参数校验用 `jakarta.validation` + `@Valid`；新增/修改分组用 `ValidGroup.Create` / `ValidGroup.Update`。
- 枚举实现 `BaseEnum`（`@JsonValue Integer getCode()`），请求体传数字 code 自动反序列化，业务枚举零配置。
- 探活统一 `/actuator/health`，不要手写 ping 接口。

### 9.5 错误码

- 全局通用码在 `GlobalErrorCode`；业务域错误码在各自 `api` 模块实现 `ErrorCode` 接口，按号段续号，不重复、不跳段滥用。
- 错误信息面向用户可读，敏感信息（SQL、堆栈、内部地址）不得出现在响应中。

### 9.6 用户上下文与审计

- 请求入口由各服务的 `UserContextFilter` 写入 `UserContext`（TTL 实现），`finally` 必须 `clear()`。
- 异步任务使用 `omni-bank-common-threadpool` 的线程池（已用 `TtlRunnable` 装饰），不要自建 `Executors`。
- 审计字段由 `AuditMetaObjectHandler` 自动填充，操作人取 `UserContext`，未登录回落 `system`；业务代码不要手工赋值。

### 9.7 配置与数据脚本

- `application.yml` 只放公共配置；环境差异拆 `application-local.yml` / `application-prod.yml`，`SPRING_PROFILES_ACTIVE` 切换，缺省 local。
- 密钥、数据库口令、模型 API Key 一律走环境变量，**禁止写入仓库**。
- 建表与初始化脚本放 `docs/sql/`，脚本需可重复执行（`CREATE TABLE IF NOT EXISTS` / 幂等 INSERT），并同步更新该域 DDL 文件。

### 9.8 日志

- 使用 SLF4J 占位符，禁止字符串拼接；异常日志必须带上下文（业务号、关键参数）。
- 不打印完整证件号、卡号、手机号；必要处脱敏。
- 资金、审批、账务等关键动作需结构化记录（操作人、业务号、结果）。

## 10. 协作流程

**分支**：`main` 受保护，只能通过 PR 合入。分支命名 `feature/{域}-{简述}` / `fix/{域}-{简述}` / `docs/{简述}`。

**提交信息**（Conventional Commits，type 英文 + 描述中文）：

```
feat(credit): 新增进件提交审批接口
fix(common-mybatis): 修正审计填充操作人取值
docs: 补充 agent 域接口说明
```

**提交前自检**：

1. `mvn -ntp -pl <改动模块> -am test` 通过（含 ArchUnit）。
2. 新增/修改公共组件时，全量 `mvn -ntp clean install -T 1C` 通过。
3. 无调试代码、无 `System.out`、无注释掉的大段代码、无硬编码密钥。
4. 改动涉及表结构时，DDL 脚本已提交且可重复执行。

**PR 要求**：说明变更目的、影响模块、验证方式（命令或接口调用示例）、是否涉及数据库/配置变更。评审关注：边界是否被破坏、是否重复造轮子、异常是否被吞、是否幂等、日志是否含敏感信息。

**文档同步触发条件**（缺一不可）：

| 变更 | 需同步 |
|---|---|
| 新增/调整业务域或模块 | `AGENTS.md` 第 7 节、`README.md` 结构树、`REQUIREMENTS.md` 域表 |
| 新增端口或 Dubbo 端口 | `AGENTS.md` 第 7 节、`README.md` |
| 新增错误码号段 | `AGENTS.md` 第 7 节、`REQUIREMENTS.md` |
| 新增公共组件 | `README.md` 公共组件章节、`AGENTS.md` 结构树 |
| 技术版本升级 | `AGENTS.md` 第 6 节、`README.md` |
| 表结构变更 | `docs/sql/` 对应脚本 |

## 11. 禁止事项与常见坑

**禁止**：

- 跨域直连数据库或跨域 Feign/RestTemplate（统一 Dubbo）。
- 在 common 模块引入业务依赖或业务域专属实体。
- 组件扫描写 `com.omnibank` 或 `com.omnibank.common`（会引入无关 Bean 或与自动装配冲突）。
- 资金类接口无幂等保护；外部回调无验签与去重。
- 物理删除业务数据（统一逻辑删除）。
- 绕过网关把管理接口直接暴露到公网端口。
- 擅自升级 Boot/Cloud/Dubbo 大版本，或引入未在 BOM 登记的三方库。

**已知坑（踩过，别再踩）**：

1. **MP 包迁移**：`com.baomidou.mybatisplus.extension.service` 在 3.5.17 已空壳，`IService`/`ServiceImpl` 在 `com.baomidou.mybatisplus.spring.service(.impl)`。
2. **`getById(Long)` 不可 `@Override`**：与 `getById(Serializable)` 构成重载。
3. **ArchUnit 用原生 API**：`archunit-junit5` 引擎在 surefire 下会出现 discover 无堆栈失败，改用 `ClassFileImporter` + 普通 `@Test`。
4. **ArchUnit 正则禁止三连点**：`"...application.."` 拼出 `...application..` 会匹配失败，拆分单段常量。
5. **`NoResourceFoundException` 需单独处理**：否则 404 被通用异常兜底吞成 200 + 系统错误码。
6. **`TtlRunnable` 在 `com.alibaba.ttl` 根包**（`TtlExecutors` 才在 `threadpool` 子包）。
7. **nacos-client 3.x 强制校验服务端**：本地无 Nacos 时需 `spring.cloud.nacos.discovery.enabled=false` 且 `dubbo.registry.address=N/A`，否则启动阻断。
8. **新模块 parent 的 `relativePath`**：挂在聚合根下必须显式写 `../omni-bank-parent`，默认 `../pom.xml` 会指向聚合根。
9. **common 组件新增 Bean 必须登记 `AutoConfiguration.imports`**，否则业务方扫不到。
10. **本地无 MySQL 会阻断启动**（HikariCP），先建库执行 `docs/sql/*.sql`。
11. **继承 `BaseDTO` 的 DTO 要加 `@EqualsAndHashCode(callSuper = true)`**，否则 Lombok 告警。
12. **Dubbo 依赖需显式引入**：`dubbo-spring-boot-starter` + `dubbo-registry-nacos`（SCA BOM 不纳管，版本由 `dubbo-bom` 提供）。
13. **网关路由前缀**：显式路由 `StripPrefix=1`，下游 Controller 不要重复写 `/api/{域}` 前缀。
14. **SAA Graph 并行扇出形态受限**：一个节点直接连多个后继时，各后继必须收敛到**同一个**直接后继节点（否则编译抛 `GraphStateException`）；分支长度不一致的 DAG 需改造图结构，不能指望 `ParallelEdgeProcessor`（该版本未接入）。
15. **SAA Graph 终态不可按自定义类型读取**：`CompiledGraph` 返回的终态经 `StateSerializer` 序列化克隆，自定义对象会变成 `Map`；节点结果应在节点动作内直接收集，不要从终态 `value(key, YourType.class)` 取。

## 12. 文档索引

| 文件 | 用途 |
|---|---|
| [REQUIREMENTS.md](REQUIREMENTS.md) | 业务需求、域职责、功能清单、非功能要求、里程碑、待确认事项 |
| [AGENTS.md](AGENTS.md) | 技术规范、分层边界、协作流程（本文件，唯一事实源） |
| [CLAUDE.md](CLAUDE.md) | 行为准则 + 关键约束摘要，项目规范以本文件为准 |
| [README.md](README.md) | 工程结构、版本管理机制、公共组件用法、快速开始 |
| `docs/sql/omni_bank_sys.sql` | 系统管理域 15 张表 + 初始化数据（admin/admin123） |
| `docs/sql/omni_bank_ai.sql` | 智能体域 3 张表 + 初始智能体 |
| `docs/sql/omni_bank_dd.sql` | 对公信贷尽调域 12 张表（含计划、执行记录、尽调报告与复核留痕、风险线索、补充材料请求及 Outbox 事件表） |
| `docs/deploy/README.md` | 构建与镜像、配置项、端到端联调检查清单、压测与排障手册（含 `docker-compose.yml`、`perf/dd-api.js`） |

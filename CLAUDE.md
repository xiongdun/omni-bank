# CLAUDE.md

> **项目规范唯一事实源：[AGENTS.md](AGENTS.md)**。本文件保留通用行为准则，并摘录最高频的硬约束；细节（模块地图、端口、错误码、编码规范、协作流程、已知坑）一律以 AGENTS.md 为准。
> 业务需求见 [REQUIREMENTS.md](REQUIREMENTS.md)，工程说明见 [README.md](README.md)。

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

## 5. omni-bank 项目硬约束（摘要，全量见 AGENTS.md）

**技术基线**：Java 21 / Spring Boot 3.5.16 / Spring Cloud 2025.0.3 / SCA 2025.0.0.0 / Dubbo 3.3.6 / MyBatis-Plus 3.5.17 / Sa-Token 1.46.0。三方版本只在 `omni-bank-dependencies` 改，业务 POM 不写版本号。

**架构**：

- 网关对外 HTTP，**服务之间统一 Dubbo**，禁止跨域 Feign/RestTemplate、禁止跨域直连数据库。
- 域内两种组织方式不可混用：场景型域（credit）= 场景模块 DDD（application/domain/infrastructure，无 controller/rpc）+ boot 收口适配层；简单型域（system/agent）= api + boot，全 MVC 包结构。
- common 组件走 Spring Boot 自动装配（`AutoConfiguration.imports`），业务启动类**不要**扫描 common 包。
- 探活统一 `/actuator/health`；配置按 `local` / `prod` profile 拆分，缺省 local。

**编码**：

- MP 3.5.17：`IService`/`ServiceImpl` 在 `com.baomidou.mybatisplus.spring.service(.impl)`；`getById(Long)` 是重载，不可 `@Override`。
- 实体继承 `BaseEntity`，DTO 继承 `BaseDTO`（并加 `@EqualsAndHashCode(callSuper = true)`），VO 继承 `BaseVO`；不另造 BasePO。
- 分页：入参继承 `PageQuery`，出参 `PageResult`，controller 用 `PageResults.from(...)` 转换。
- 错误码：全局在 `GlobalErrorCode`，业务域在各自 `api` 模块实现 `ErrorCode`，按号段续号。
- 枚举实现 `BaseEnum`，请求体传数字 code 自动反序列化。
- 审计字段自动填充，操作人取 `UserContext`；异步任务用 `common-threadpool`（TTL 已装饰），不要 `Executors` 自建。
- 资金类接口必须幂等；业务数据只逻辑删除；密钥/口令/API Key 只走环境变量。

**验证与文档**：

- 提交前 `mvn -ntp -pl <模块> -am test`；改公共组件需全量 `mvn -ntp clean install -T 1C`。
- 改模块/端口/错误码/公共组件/表结构，必须同步 `AGENTS.md` 第 7 节、`README.md`、（必要时）`REQUIREMENTS.md`，DDL 放 `docs/sql/`。

**已知坑**（详见 AGENTS.md 第 11 节）：ArchUnit 用原生 API 且正则不能三连点、`NoResourceFoundException` 需单独 404 处理、`TtlRunnable` 在 `com.alibaba.ttl` 根包、新模块 `relativePath` 必须写 `../omni-bank-parent`、本地无 MySQL 会阻断启动、nacos-client 3.x 无服务端需关闭注册。

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

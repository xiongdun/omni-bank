# 尽调 Agent 部署与运行手册（P10）

面向 `omni-bank-service-agent`（HTTP 8109 / Dubbo triple 20889）的构建、部署、端到端联调与压测说明。
业务背景与接口契约见 [agent 模块 README](../../omni-bank-service/omni-bank-service-agent/README.md)；前端见 [omni-bank-ui/README.md](../../omni-bank-ui/README.md)。

## 1. 前置条件

| 场景 | 依赖 |
|---|---|
| 构建 | JDK 21、Maven 3.9+ |
| 容器化运行 | Docker 24+、Docker Compose v2 |
| 本地裸启动 | MySQL 8.x（库名 `omni_bank`） |
| 前端联调 | Node 20+、npm |

首次建库必须执行 `docs/sql/` 下脚本，否则 HikariCP 初始化会阻断启动：

| 脚本 | 内容 |
|---|---|
| `omni_bank_sys.sql` | 系统管理域表 + 初始化数据（admin/admin123） |
| `omni_bank_ai.sql` | 智能体域 3 张表 |
| `omni_bank_dd.sql` | 对公信贷尽调域 12 张表（含 Outbox 事件表） |

## 2. 构建

```bash
# 仓库根目录：构建 boot 模块及其上游依赖（跳过测试）
mvn -ntp -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot -am package -DskipTests

# 产物
omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot/target/omni-bank-service-agent-boot-1.0.0-SNAPSHOT.jar
```

镜像构建使用 boot 模块下的 [Dockerfile](../../omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot/Dockerfile)，要求 jar 已存在：

```bash
docker build -f omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot/Dockerfile \
  -t omni-bank-agent:1.0.0 \
  omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot
```

## 3. 配置项

配置按 profile 拆分：`application.yml`（公共）+ `application-local.yml` / `application-prod.yml`，`SPRING_PROFILES_ACTIVE` 切换，缺省 `local`。密钥一律走环境变量。

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` 关闭 Nacos 注册发现、Dubbo 注册 `N/A`；`prod` 需接入 Nacos |
| `SPRING_DATASOURCE_URL` | 本机 `omni_bank` | 容器内需覆盖为 `jdbc:mysql://mysql:3306/omni_bank?...` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | `root` / `root` | 数据库账号，禁止写入仓库 |
| `OMNI_DD_LLM_MODE` | `MOCK` | `MOCK` 不发起外部请求；`OPENAI_COMPATIBLE` 时需下面的模型变量 |
| `OMNI_DD_LLM_BASE_URL` | 空 | 模型网关地址 |
| `OMNI_DD_LLM_API_KEY` | 空 | 模型 API Key |
| `OMNI_DD_LLM_MODEL` | 空 | 模型名称 |

## 4. 方式一：Docker Compose（推荐联调）

[compose 文件](docker-compose.yml) 会启动 MySQL 8（自动执行三个 SQL 脚本）与 agent 服务。

```bash
# 1. 先构建 jar（compose build 只负责打包镜像）
mvn -ntp -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot -am package -DskipTests

# 2. 启动
docker compose -f docs/deploy/docker-compose.yml up -d --build

# 3. 验证
curl -s http://localhost:8109/actuator/health

# 4. 查看日志 / 停止
docker compose -f docs/deploy/docker-compose.yml logs -f agent
docker compose -f docs/deploy/docker-compose.yml down

# 重置数据库（连同数据卷）
docker compose -f docs/deploy/docker-compose.yml down -v
```

> `docker-entrypoint-initdb.d` 只在数据卷为空时执行；改表结构后需 `down -v` 重建或手工执行脚本（脚本本身幂等）。

## 5. 方式二：本地裸启动（无 Docker）

```bash
# 1. 建库并执行脚本（mysql 客户端）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS omni_bank DEFAULT CHARACTER SET utf8mb4;"
mysql -uroot -p omni_bank < docs/sql/omni_bank_sys.sql
mysql -uroot -p omni_bank < docs/sql/omni_bank_ai.sql
mysql -uroot -p omni_bank < docs/sql/omni_bank_dd.sql

# 2. 启动服务（local profile 默认关闭 Nacos 注册）
mvn spring-boot:run -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot
```

服务启动后可访问 `http://localhost:8109/actuator/health`；数据库连接失败时 HikariCP 会直接阻断启动。

## 6. 前端联调

```bash
cd omni-bank-ui && npm install && npm run dev
# 浏览器打开 http://localhost:5173/workbench
```

开发态 `/api` 代理到 `http://localhost:8109` 并剥离前缀（与网关 `StripPrefix=1` 对齐），因此前端请求 `/api/v1/dd/...` 实际打到 `http://localhost:8109/v1/dd/...`。

## 7. 端到端联调检查清单

以下命令均为 HTTP 直连（不经过网关）；agent 域本期不做鉴权，保留审计头约定。

```bash
# 1. 创建任务
curl -s -X POST http://localhost:8109/v1/dd/tasks \
  -H "Content-Type: application/json" -H "X-Username: admin" -H "X-User-Id: 1" \
  -d '{"enterprise":{"creditCode":"91310000MA1FL0000X","enterpriseName":"示例科技有限公司"},
       "application":{"productCode":"CORPORATE_WORKING_CAPITAL","productName":"流动资金贷款",
                      "applyAmount":5000000,"applyTerm":12,"loanPurpose":"原材料采购"}}'

# 2. 提交并触发识别 → 计划 → Graph 执行（异步）
curl -s -X POST http://localhost:8109/v1/dd/tasks/{taskId}/submit -H "X-Username: admin"

# 3. 查询状态（前端用 SSE /events 实时刷新，断线后回落到轮询此接口）
curl -s http://localhost:8109/v1/dd/tasks/{taskId}/status -H "X-Username: admin"

# 4. 执行记录 / 风险线索 / 补充材料
curl -s "http://localhost:8109/v1/dd/tasks/{taskId}/executions?current=1&size=10" -H "X-Username: admin"
curl -s "http://localhost:8109/v1/dd/tasks/{taskId}/findings?current=1&size=10" -H "X-Username: admin"
curl -s "http://localhost:8109/v1/dd/tasks/{taskId}/supplements?current=1&size=10" -H "X-Username: admin"
```

检查项：

1. 任务创建返回 `code=000200`，`data.taskId` 为雪花 ID（前端必须按字符串处理）。
2. 提交后状态按 `INITIALIZING → SCENARIO_IDENTIFYING → PLANNING → DATA_COLLECTING → ANALYZING → RISK_ASSESSING → REPORT_GENERATING → WAITING_FOR_REVIEW` 推进（存在待补充材料时进入 `WAITING_FOR_SUPPLEMENT`）。
3. SSE `/v1/dd/tasks/{taskId}/events` 持续推送 `task-status` 事件，终态后连接关闭。
4. 概览页 12 个 Agent 网格均有最新执行记录；失败/跳过的节点在“执行记录”可看到原因。
5. 报告页可切换版本、提交复核动作（PASS/AMEND/REJECT/REQUEST_MATERIAL/REANALYZE 等），留痕出现在时间线。
6. 补充材料标记完成后触发增量重分析，任务回到分析链路。
7. 计划抽屉与证据溯源抽屉可打开，证据能反查引用线索。

## 8. 压测与性能基线

脚本：[`perf/dd-api.js`](perf/dd-api.js)（k6）。只压读接口，创建任务会触发 Graph 全链路，不纳入常规压测。

```bash
# 安装 k6 后执行；TASK_ID 可选，提供后会额外压任务详情/状态/执行记录
k6 run -e BASE_URL=http://localhost:8109 -e TASK_ID={taskId} docs/deploy/perf/dd-api.js
```

阈值（与 `REQUIREMENTS.md` 非功能要求一致）：

| 指标 | 目标 |
|---|---|
| `http_req_duration` P95 | < 500ms（常规分页/状态查询） |
| `http_req_failed` | < 1% |

基线记录模板（每轮压测补一行，注明环境与数据量）：

| 日期 | 环境 | 数据量（任务数） | VU | P95(ms) | 失败率 | 备注 |
|---|---|---|---|---|---|---|
| 待执行 | 本机/联调环境 | 待执行 | 20 | 待执行 | 待执行 | 需先完成第 7 节联调 |

> 当前仓库环境（本机无 Docker、无 MySQL）未能执行压测，表中数据需在联调环境就绪后回填；不要把未实测数值写成结论。

## 9. 常见故障排查

| 现象 | 原因与处理 |
|---|---|
| 启动即失败，日志含 HikariPool | 未建库或未执行 `docs/sql/*.sql`；先按第 1/5 节初始化 |
| 启动报 Nacos 连接/校验失败 | `local` profile 已关闭注册发现；自定义 profile 需设 `spring.cloud.nacos.discovery.enabled=false` 且 `dubbo.registry.address=N/A` |
| 端口占用 | 8109（HTTP）或 20889（Dubbo）被占；`netstat -ano | findstr :8109` 定位 |
| 接口返回 404 但路径正确 | 直连服务时不要带 `/api` 前缀（`/api` 是网关/前端代理层）；也不要在 Controller 重复写 `/api/{域}` |
| 前端雪花 ID 精度丢失 | 响应必须经 `json-bigint` 解析，ID 统一用 `string`，禁止 `Number()` |
| 报告/计划接口报未找到 | 任务尚未推进到对应阶段；先确认状态与执行记录 |

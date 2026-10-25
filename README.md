# Omni Bank · 银行信贷业务线上化平台

覆盖 **信贷、账务、风控、支付** 的银行信贷业务线上化系统。基于 Java 21 + Spring Boot 3.5 + Spring Cloud Alibaba 构建，目标为生产级真实业务。

> 当前处于 **基础工程阶段**：依赖管理体系与公共组件层已就绪并通过全量编译，业务服务尚未开始。

## 技术栈

| 分类 | 组件 | 版本 |
|---|---|---|
| 运行时 | Java | 21 |
| 核心框架 | Spring Boot | 3.5.16 |
| | Spring Cloud | 2025.0.3 |
| | Spring Cloud Alibaba | 2025.0.0.0（内置 Nacos / Sentinel 1.8.9 / Seata 2.5.0 / RocketMQ 5.3.1 客户端管理） |
| RPC | Apache Dubbo（triple 协议，服务间调用） | 3.3.6（官方 dubbo-bom 纳管；SCA BOM 不含 Dubbo） |
| 数据访问 | MyBatis-Plus（+ jsqlparser） | 3.5.17 |
| | ShardingSphere JDBC（预留，接入时评估） | 5.5.3 |
| 缓存 | Redisson | 3.52.0（3.x 末版，适配 Boot 3；4.x 面向 Boot 4） |
| 工具链 | Hutool / Lombok / MapStruct / TTL / EasyExcel | 5.8.48 / 1.18.48 / 1.6.3 / 2.14.5 / 4.0.3 |
| 认证 / 文档 | JJWT / Knife4j | 0.13.0 / 4.5.0 |

所有版本统一在 `omni-bank-dependencies` 中声明，业务模块引用依赖一律 **不写版本号**。

## 工程结构

```
omni-bank
├── pom.xml                          # 聚合根：仅负责聚合构建，不承载依赖管理
├── omni-bank-dependencies           # 基础 BOM：三方依赖版本唯一来源（无 parent，可被任意工程 import）
├── omni-bank-parent                 # 父 POM：所有模块的 parent（编译配置/内部构件版本/私服发布）
├── omni-bank-common                 # 公共组件聚合模块（按需引用子模块，不直接依赖本模块）
    ├── omni-bank-common-core        # 统一响应体 Result / 错误码体系 / 业务异常 BizException
    ├── omni-bank-common-web         # 全局异常处理（业务/参数校验/兜底 → 统一响应）
    ├── omni-bank-common-mybatis     # MyBatis-Plus 支撑：分页插件 / BaseEntity（雪花 ID + 审计字段填充）
    ├── omni-bank-common-local-cache # Caffeine 本地缓存封装
    ├── omni-bank-common-remote-cache# Redisson 远程缓存封装
    ├── omni-bank-common-mq          # RocketMQ 消息发送封装
    ├── omni-bank-common-idempotent  # 幂等防重：@Idempotent 注解 + 切面
    ├── omni-bank-common-lock        # 分布式锁：@DistributedLock 注解 + 切面
    ├── omni-bank-common-excel       # EasyExcel 导入导出工具
    ├── omni-bank-common-threadpool  # 统一业务线程池：参数可配 + TTL 上下文装饰 + 优雅关闭（自动装配）
├── omni-bank-gateway                # API 网关：统一对外入口（Nacos 发现路由转发 + Sentinel 限流熔断 + 全局跨域）
└── omni-bank-service                # 业务服务聚合模块（8 大业务域，每域 api + boot 双模块；信贷域另含 6 个场景子模块）
    ├── omni-bank-service-customer      # 客户域：客户/KYC（8101）
    ├── omni-bank-service-product       # 产品域：信贷产品工厂（8102）
    ├── omni-bank-service-credit        # 信贷域：进件/授信/额度/借据/还款/抵押/贷后（8103，域内按场景拆 6 个子模块：credit/loan/repay/limit/mortgage/postloan）
    ├── omni-bank-service-accounting    # 账务域：会计引擎/总账（8105）
    ├── omni-bank-service-payment       # 支付域：渠道网关/代扣代付（8106）
    ├── omni-bank-service-recon         # 对账域：T+1 对账/差错处理（8107）
    ├── omni-bank-service-risk          # 风控域：决策引擎/名单（8108）
    └── omni-bank-service-system        # 系统管理域：认证(Sa-Token)/用户/角色/菜单权限/数据权限/机构部门/岗位/字典/参数/日志/通知/附件（8104，MVC 包结构）
```

业务服务统一按 **api + boot 双模块**组织：`-api` 放 DTO/域错误码/`{域}DubboService` RPC 接口（其他服务跨域调用只依赖它，版本在 parent 统一纳管），`-boot` 为唯一启动器与配置载体。域内复杂业务（如信贷域）再按业务场景拆**场景子模块**（物理 Maven 模块，由 boot 装配为单一服务；域内私有，不进 parent 全局纳管，其他服务只允许依赖对应 api）。业务错误码统一 6 位号段：通用域 `00`（`000200` 成功、`0004xx` 客户端错误、`0005xx` 服务端错误）；业务域/场景前两位为域号——customer `100001-109999`、product `110001-119999`、credit 授信场景 `120001-129999`、loan 借据场景 `130001-139999`（随 credit 服务）、accounting `140001-149999`、payment `150001-159999`、recon `160001-169999`、risk `170001-179999`、repay `180001-189999`、limit `190001-199999`、mortgage `200001-209999`、postloan `210001-219999`、system `220001-229999`。

场景子模块内部采用 **DDD 分层**：`application`（应用服务，用例编排入口）/ `domain`（聚合根/实体/仓储接口/领域服务）/ `infrastructure`（仓储实现、外部适配）；**web 与 rpc 适配层统一收口在 boot**（`adapter.web` / `adapter.rpc`，委托领域模块应用服务），领域模块不依赖 spring-web 与 dubbo。

**出站端口**：跨域调用的 `@DubboReference` 只允许出现在 `outbound` 端口实现中（如 `CustomerGateway`），适配层经端口调用、不直连下游；演示代码以 `@Profile("demo")` 隔离（`SPRING_PROFILES_ACTIVE=demo,local` 启用），相关依赖标 `optional` 保证生产包不携带。

**架构守护**：boot 内置 ArchUnit 测试固化边界——领域模块不依赖 web/servlet/Dubbo、场景间互不依赖、adapter 不得直达 domain、application 不得依赖 infrastructure；随 `mvn test` 执行，违规即构建失败（新增场景需同步扩展 `ArchitectureTest` 场景正则）。

**探活**：统一走 `/actuator/health`（liveness/readiness），不提供手写 ping 接口。

**配置与环境**：`application.yml` 只放公共配置，环境差异拆到 `application-local.yml` / `application-prod.yml`，通过 `SPRING_PROFILES_ACTIVE` 切换（缺省 local）。

**服务间调用统一走 Dubbo RPC**（架构：网关保持 HTTP 转发，服务之间全 Dubbo）：

- 接口契约在各域 api 模块（`{域}DubboService`，域内多场景接口共存），实现统一收口在 boot 的 `adapter.rpc` 以 `@DubboService` 暴露；调用方经 `outbound` 端口封装 `@DubboReference`，适配层不直连下游
- 协议 triple（HTTP/2），端口 20881-20888 按 customer→risk 顺序分配；注册中心复用 Nacos
- 无 Nacos 时 `dubbo.registry.check=false` + `dubbo.consumer.check=false` 允许裸启动，调用期失败由全局异常处理兜底
- 跨域演示：credit 的 `/credit/rpc-ping` 通过 Dubbo 调用 customer（联调需 Nacos + customer 在线）

## 版本管理机制

三层结构，各司其职：

1. **omni-bank-dependencies**：三方版本唯一来源。import Spring Boot / Spring Cloud / Spring Cloud Alibaba 三个官方 BOM，再纳管项目三方库。刻意不声明 `<parent>`，保证可被任意工程干净引入，避免与 parent 形成循环引用。
2. **omni-bank-parent**：以 `spring-boot-starter-parent` 为父（白拿 Boot 插件管理与资源过滤），import 上述 BOM，并纳管项目内部构件版本（`${omni-bank.version}`）、JDK 21 注解处理器（Lombok + MapStruct）、私服 `distributionManagement`。
3. **业务模块**：以 `omni-bank-parent` 为 parent，引依赖不写版本号。

### 外部工程引入 BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.omnibank</groupId>
            <artifactId>omni-bank-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 新增业务模块

```xml
<parent>
    <groupId>com.omnibank</groupId>
    <artifactId>omni-bank-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <!-- 重要：模块挂在聚合根下时禁止使用默认 relativePath（默认 ../pom.xml 指向聚合根），
         必须显式指向 omni-bank-parent 目录 -->
    <relativePath>../omni-bank-parent</relativePath>
</parent>
```

同时在聚合根 `<modules>` 中登记子目录。

## 公共组件使用

**约定**：common 组件基于 **Spring Boot 自动装配**（`META-INF/spring/...AutoConfiguration.imports`），业务 boot 模块引依赖即生效，启动类无需扫描 common 包；服务自身的 Bean 位于 `com.omnibank.service.{域}` 包下由启动类扫描。

### 统一响应与异常

```java
// 成功响应：{"code":"200","msg":"成功","data":{...}}
return Result.success(data);

// 业务规则不满足时抛出，web 层自动转换为错误响应
throw new BizException(GlobalErrorCode.PARAM_ERROR, "手机号格式不正确");

// 业务模块自有错误码：实现 ErrorCode 接口扩展，不在 GlobalErrorCode 中堆叠
public enum LoanErrorCode implements ErrorCode { ... }
```

### 幂等防重（资金类操作建议必加）

```java
@Idempotent(key = "#req.orderNo", ttl = 10, timeUnit = TimeUnit.MINUTES)
@PostMapping("/order/create")
public Result<Void> createOrder(@RequestBody @Valid OrderCreateReq req) { ... }
```

### 分布式锁

```java
@DistributedLock(key = "#req.accountId", waitTime = 3, leaseTime = -1)  // leaseTime<=0 启用看门狗续期
public void deductQuota(DeductReq req) { ... }
```

### 实体基类与审计字段

```java
@TableName("loan_apply")
public class LoanApply extends BaseEntity { ... }
// createTime / updateTime / creator / updater 自动填充；雪花主键自动生成
```

## 快速开始

**环境要求**：JDK 21+、Maven 3.9+（开发环境参考：JDK 21.0.10 + Maven 3.9.6）

项目 POM 已内置 **Aliyun 国内仓库声明**（`aliyun-public` + `aliyun-snapshots`，声明于聚合根/parent/dependencies 三处），克隆后无需在 settings.xml 配置镜像即可走国内源加速构建。

```bash
# 全量构建（含本地安装，业务模块即可互相引用）
mvn clean install

# 仅构建某一模块（如公共组件）
mvn install -pl omni-bank-common/omni-bank-common-core

# 启动网关（默认 8080；本地无 Nacos 时默认关闭注册发现，NACOS_ENABLED=true 打开）
mvn spring-boot:run -pl omni-bank-gateway

# 启动业务服务（以信贷域为例，端口 8103；探活统一 /actuator/health）
mvn spring-boot:run -pl omni-bank-service/omni-bank-service-credit/omni-bank-service-credit-boot
```

**网关说明**（`omni-bank-gateway`，统一对外入口）：

- **路由转发**：Nacos 服务发现自动路由（`discovery.locator`，`/{服务名}/**` → `lb://{服务名}`）+ 8 条显式业务路由（`/api/{域}/**` → `lb://omni-bank-service-{域}`，`StripPrefix=1` 去前缀），新服务在 `application.yml` 按模板登记
- **限流熔断**：Sentinel（路由维度 / API 分组维度），兜底响应统一为 Result 结构、HTTP 429；规则由 Sentinel Dashboard（默认 `127.0.0.1:8858`）下发，持久化后续接 Nacos 数据源
- **当前阶段**：本地无 Nacos 时注册发现默认关闭（`NACOS_ENABLED=true` 开启），网关可独立启动；下游服务不存在时转发返回 503 属预期

## 私服发布

`distributionManagement` 采用属性占位（默认值为 `nexus.example.com` 占位地址），私服就绪后二选一：

```bash
# 方式一：命令行注入
mvn deploy \
  -Domni.repo.release.url=http://your-nexus/repository/maven-releases/ \
  -Domni.repo.snapshot.url=http://your-nexus/repository/maven-snapshots/

# 方式二：settings.xml 中定义 omni.repo.release.url / omni.repo.snapshot.url 同名属性
```

## 开发约定与维护注意点

**数据访问（credit 已接入）**：boot 模块引入 `omni-bank-common-mybatis` + `mysql-connector-j`（runtime），启动类声明 `@MapperScan(basePackages = "com.omnibank.service.{域}", annotationClass = Mapper.class)`；Mapper 放各场景模块 `infrastructure.persistence.mapper`，实体继承 `BaseEntity`（雪花 ID + 审计字段自动填充 + 逻辑删除）。datasource 配置按 profile 拆分（local 本地占位 / prod 环境变量注入）；**本地无 MySQL 会因 HikariCP 初始化阻断启动**，需先建库 `omni_bank` 并执行 `docs/sql/omni_bank_sys.sql`（系统管理域全量表 + 初始化数据，初始账号 admin/admin123）。业务线程池由 `omni-bank-common-threadpool` 自动装配，`omni.thread-pool.*` 可配。**认证鉴权**：system 服务基于 Sa-Token（登录签发 token，管理端 Header `Authorization` 携带；`@SaCheckPermission` 注解鉴权；super_admin 角色通配全部权限点），其他服务跨域鉴权经 `system-api` 的 Dubbo 契约（`PermissionDubboService`/`UserDubboService`）。

- **Spring Boot 升级**需两处同步：`omni-bank-parent` 的 `spring-boot-starter-parent` 版本 + BOM 的 `spring-boot.version`
- **Lombok / MapStruct 版本升级**需与 `omni-bank-parent` 中 compiler 插件 `annotationProcessorPaths` 的三个硬编码版本同步（import 的 BOM 不传递属性）
- **错误码规范**：`0/2xx` 风格全局码在 `GlobalErrorCode`；业务模块错误码在各自模块实现 `ErrorCode` 接口
- 新增公共组件以 `omni-bank-common-xxx` 子模块形式扩展，并在 parent `dependencyManagement` 登记
- 提示：Spring Boot 3.5.x 的开源维护窗口已于 2026 年中结束，长期安全补丁需商业订阅；后续可评估 Boot 4.1 + SCA 2025.1.0.0 的整体升级

## 路线图

| 阶段 | 内容 |
|---|---|
| P1 平台底座 | Nacos/Sentinel/Seata/RocketMQ 中间件就位 + 客户/产品/进件审批，管理端跑通完整审批流 |
| P2 资金闭环 | 支付网关（mock 渠道）+ 会计引擎 + Outbox/Saga，放款还款全链路打通 |
| P3 风控与日终 | 决策引擎接入审批流 + 日终批处理（计息/计提/对账） |
| P4 生产对接 | 真实支付渠道、电子签约、催收、监管报送 |

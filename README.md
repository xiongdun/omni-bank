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
└── omni-bank-common                 # 公共组件聚合模块（按需引用子模块，不直接依赖本模块）
    ├── omni-bank-common-core        # 统一响应体 Result / 错误码体系 / 业务异常 BizException
    ├── omni-bank-common-web         # 全局异常处理（业务/参数校验/兜底 → 统一响应）
    ├── omni-bank-common-mybatis     # MyBatis-Plus 支撑：分页插件 / BaseEntity（雪花 ID + 审计字段填充）
    ├── omni-bank-common-local-cache # Caffeine 本地缓存封装
    ├── omni-bank-common-remote-cache# Redisson 远程缓存封装
    ├── omni-bank-common-mq          # RocketMQ 消息发送封装
    ├── omni-bank-common-idempotent  # 幂等防重：@Idempotent 注解 + 切面
    ├── omni-bank-common-lock        # 分布式锁：@DistributedLock 注解 + 切面
    └── omni-bank-common-excel       # EasyExcel 导入导出工具
```

后续规划的业务域服务（暂不拆分，公共层稳定后逐步启动）：
`gateway` / `customer` / `product` / `credit` / `loan` / `accounting` / `payment` / `recon` / `risk`。

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

**约定**：公共模块的 Bean 通过组件扫描发现，服务应用启动类需位于 `com.omnibank` 包下。

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

**环境要求**：JDK 21+、Maven 3.9+（开发环境参考：JDK 21.0.10 + Maven 3.9.6，镜像走阿里云）

```bash
# 全量构建（含本地安装，业务模块即可互相引用）
mvn clean install

# 仅构建某一模块（如公共组件）
mvn install -pl omni-bank-common/omni-bank-common-core
```

当前仓库无可启动应用；首个业务服务模块落地后在此处补充启动说明与配置示例。

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

# 工程骨架说明

## 1. 为什么由 Codex 搭建

创建目录、填写 Maven 坐标和重复编写配置的面试价值较低。真正需要掌握的是：

1. 为什么选择当前版本。
2. 每个依赖解决什么问题。
3. Spring Boot 如何从 `main` 方法启动。
4. 配置文件如何按环境切换。
5. HTTP 请求如何经过 Filter、Security 和 Controller。
6. 数据库为什么必须通过 Flyway 演进。

骨架由 Codex 创建，但这些问题必须能够独立解释。

## 2. 为什么选择模块化单体

项目当前只有一个可执行应用和一个数据库，但代码按领域拆分：

```text
iam → 身份与认证
transport → 车辆、路线、班次和座位
booking → 预约、订单和超时
payment → 模拟支付
shared → 最小化的通用基础设施
```

选择理由：

- 先用本地事务保证锁座和创建订单的一致性。
- 减少网络、注册中心和分布式事务带来的学习噪声。
- 领域边界已经存在，后续可以按模块拆成服务。
- 可以对比单体事务和微服务最终一致性的差异。

`shared` 不能变成存放所有代码的杂物包。业务规则必须留在所属领域模块。

## 3. 版本选择

### Java 21

Java 21 是 LTS 版本，能够使用 Record、模式匹配和现代 JVM 能力。系统默认 Java 8 暂时保留，项目终端显式使用 Java 21。

### Spring Boot 3.5

项目使用 Spring Boot 3 的稳定维护线，兼容 Java 21、MyBatis 3 和后续 Spring Cloud 方案。当前不直接选择 Boot 4，避免学习初期同时处理生态大版本迁移。

### MyBatis

项目需要展示 SQL、索引和条件更新的并发设计，因此选择 MyBatis，而不是隐藏 SQL 细节的全自动 ORM。

### Flyway

数据库设计不能只存在 Markdown 中。Flyway 在应用启动时按版本执行迁移，使开发、测试和部署环境得到相同结构。

## 4. 启动链路

```text
SchoolBusApplication.main
  → SpringApplication.run
  → 读取 application.yml 和环境 Profile
  → 创建 DataSource
  → Flyway 校验并执行迁移
  → 初始化 MyBatis、Security、Web MVC、Actuator
  → 启动嵌入式 Tomcat
```

如果数据库不可用，`local` 环境启动应该失败，而不是假装服务健康。

## 5. 请求链路

```text
HTTP 请求
  → TraceIdFilter
  → Spring Security
  → Controller
  → Application Service
  → Domain
  → Repository / MyBatis
  → Controller 返回 ApiResponse
```

异常由 `GlobalExceptionHandler` 转换为稳定错误结构，不能把堆栈或 SQL 返回给客户端。

## 6. 当前已经实现

- Maven 构建和 Java 21 编译。
- 按领域划分的顶层包。
- `local`、`test`、`prod` 配置。
- 统一成功响应。
- 统一错误响应和异常处理入口。
- `X-Request-Id` 生成和透传。
- 无状态 Spring Security 骨架。
- Actuator 健康检查。
- Swagger UI 和 OpenAPI。
- Flyway V1 基线表。
- Spring 上下文和 MockMvc 测试。

## 7. 当前有意未实现

- 注册和登录。
- JWT 签发与校验。
- 车辆、路线和班次业务。
- 创建订单和支付。
- Redis 与 RocketMQ。
- Spring Cloud 微服务组件。

这些内容按业务纵向逐步实现，避免一次加入大量无法解释的代码。

## 8. 第一轮必须会回答的问题

1. `@SpringBootApplication` 包含哪些核心能力？
2. 为什么项目使用 Java 21，但电脑默认 Java 仍是 8？
3. 为什么现在是一个应用，而不是四个微服务？
4. 为什么不用数据库自增 ID 作为 API 标识？
5. Flyway 如何知道某个迁移是否执行过？
6. 为什么生产环境不开放 Swagger UI？
7. TraceId 在排查问题时有什么作用？
8. Filter、Spring Security 和 Controller 的执行顺序是什么？
9. 为什么 Controller 不能直接调用 Mapper？
10. 为什么选择 MyBatis，而不是只说“因为简历要求”？

## 9. 下一步

下一步实现身份与认证的第一个纵向切片：

```text
Flyway 数据
  → Account 领域模型
  → MyBatis Mapper
  → 注册应用服务
  → 注册 Controller
  → 密码哈希
  → 登录与 JWT
  → 单元测试和集成测试
```

## 10. 可执行验证记录

2026-07-30 已完成：

| 验证项 | 结果 |
|---|---|
| Java 版本 | Java 21.0.6 |
| Spring Boot 版本 | 3.5.16 |
| `mvn package` | 成功 |
| 自动化测试 | 3 个测试全部通过 |
| `/actuator/health` | HTTP 200，状态 `UP` |
| `/api/v1/system/ping` | HTTP 200，TraceId 正确透传 |
| `/v3/api-docs` | HTTP 200 |
| Flyway | 成功执行 V1 |
| MySQL | 12 张业务表和 1 张 Flyway 历史表 |

真实启动使用端口 `18080` 完成冒烟测试，测试进程随后已经停止。后续正常开发仍使用配置中的默认端口 `8080`。

# School Bus Platform

面向高校学生的班车预约与支付平台，覆盖统一认证、班次与座位查询、并发锁座、支付退款、订单取消和超时关单。项目从模块化单体起步，通过 Strangler Fig（绞杀者）模式逐步演进为 Spring Cloud 微服务。

A school bus booking and payment platform covering OIDC SSO, trip and seat discovery, concurrent seat reservation, payment/refund, cancellation and timeout processing. It starts as a modular monolith and is being incrementally extracted into Spring Cloud services with the Strangler Fig pattern.

> **项目状态 / Status：持续开发中（WIP）。** 学生端核心业务闭环、IAM / Booking / Payment / Transport Query 服务提取、Booking↔Payment 事件解耦及主要基础设施验收已经完成；学生端与管理端两个 OIDC 客户端、管理控制台、真实浏览器 SSO 联调及 Gateway 全局 Token 撤销已经完成。Transport Command 第一阶段已提取车辆与路线管理写路径；班次写路径与独立数据库仍在推进。

## 技术栈 / Tech Stack

- Java 21
- Spring Boot 3.5.16
- Spring Cloud Gateway、Nacos、Spring Cloud LoadBalancer
- Maven 3.9+
- MyBatis Spring Boot Starter 3.0.4
- MySQL 8、Flyway、Redis 6+
- RabbitMQ 4+、Transactional Outbox
- Spring Security、Spring Authorization Server、OAuth 2.1 / OIDC / JWT
- Springdoc OpenAPI
- Spring Boot Actuator
- Sentinel 1.8.10
- Canal 1.1.8（Binlog CDC，当前为影子一致性链路）
- Vue 3、TypeScript、Vite、Pinia、Element Plus

## 核心能力 / Highlights

- **统一认证**：基于 Spring Authorization Server 实现 OIDC Discovery/JWK、Authorization Code + PKCE 与 RP-Initiated Logout；学生端和管理端共享 IAM 登录会话。IAM 使用 Redis 记录用户级撤销水位，Gateway 在入口统一拒绝登出前签发的 JWT，实现跨应用 Access Token 实时失效。
- **并发预约**：同一 MySQL 事务内完成条件更新锁座、`version` 乐观锁扣减库存、订单与 Outbox 落库，通过幂等请求号和唯一索引防止重复下单与超卖。
- **事件驱动支付**：Booking 与 Payment 通过 `PaymentSucceeded`、`RefundRequested`、`PaymentRefunded` 事件协作，结合 Transactional Outbox、消费幂等、有限重试与 DLQ 实现最终一致性。
- **超时与补偿**：RabbitMQ TTL + DLX 处理未支付订单，数据库定时扫描兜底；取消时在本地事务内释放具体座位并恢复汇总库存。
- **服务治理**：Gateway + Nacos 完成服务发现、动态路由和 Query 双实例负载均衡；只对幂等 GET 配置超时与有限重试，写请求不盲目重试。
- **缓存一致性**：Redis List 缓存可预约班次，Spring 定时任务推进班次状态；Canal 监听 MySQL Binlog，经 RabbitMQ 异步失效或投影 Redis，MySQL 仍作为最终正确性边界。
- **稳定性与验证**：Sentinel 保护登录、下单和支付回调热点入口；提供 k6、PowerShell、架构守卫及真实 MySQL / Redis / RabbitMQ / Nacos 验收脚本。

## 当前进度 / Progress

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 学生端业务闭环 | ✅ 已完成 | 注册登录、查班次/座位、并发下单、订单查询/取消、模拟支付退款与超时关单 |
| Transport Query 服务 | ✅ 已提取并验收 | Nacos 注册发现、双实例负载分布、故障摘除、幂等 GET 有限重试 |
| IAM 服务与双客户端 SSO | ✅ 已完成 | 两端均使用 PKCE；真实 Chrome 已验证跨应用免密授权；Gateway + Redis 实现用户级 Token 实时撤销 |
| Booking 服务 | ✅ 已提取并验收 | HTTP 写链路、支付/过期/班次取消消息职责由独立服务承接 |
| Payment 服务 | ✅ 已提取并验收 | 支付回调、退款 Outbox Relay、RabbitMQ Consumer、retry/DLQ |
| Booking ↔ Payment 解耦 | ✅ 已验收 | 云模式使用领域事件，不再跨领域直接写对方业务表 |
| Canal CDC 缓存链路 | 🟡 影子运行 | 真实联调已通过，暂时保留应用侧缓存失效作为保护 |
| Transport 写路径与管理端 | 🟡 第一阶段已提取 | 车辆与路线管理由 Transport Command 承接；班次发布/取消仍由 Core 提供，独立管理 SPA 已完成 |
| 数据库自治 | 📋 待完成 | 当前服务仍共享 MySQL 物理实例；下一阶段逐步收紧跨服务表访问 |

## 当前架构 / Architecture

```text
Vue 3 Student SPA ─┐
                   ├─ OIDC / REST
Vue 3 Admin SPA ───┘
                   ▼
Spring Cloud Gateway (:8080)
        ├── school-bus-iam (:8084)
        ├── school-bus-transport-query (:8082 / :8083)
        ├── school-bus-payment (:8085)
        ├── school-bus-booking (:8087)
        ├── school-bus-transport-command (:8088, vehicle/route admin)
        └── school-bus-core (:8081, trip commands and remaining paths)

Nacos ─ service discovery/configuration
MySQL ─ transactional source of truth
Redis ─ sessions, trip cache and idempotency fast path
RabbitMQ + Outbox ─ payment/refund/expiration domain events
Canal ─ Binlog CDC cache projection (shadow mode)
```

渐进拆分阶段仍共享 MySQL，但已通过 ownership 开关、Gateway 路由和架构守卫限制职责回流；项目不宣称已经完成拆库或分布式事务。

双客户端 SSO 已增加服务端共享 Session 集成测试及真实 Chrome 验收脚本。2026-08-28 在真实 IAM、Gateway、MySQL、Redis 和 Nacos 环境中完成验收：学生端登录后管理端无需再次输入密码，两个客户端获得不同 Token 但拥有相同 `sub`，统一登出后新的授权会重新要求登录。随后增加用户级 Token 撤销水位：任一客户端登出会使该用户此前签发的 Access Token 在 Gateway 入口立即失效，新登录签发的 Token 不受影响。

## 设计与验收文档 / Engineering Notes

- [Nacos + Gateway 基础](docs/08-nacos-gateway-foundation.md)
- [Transport Query 绞杀者提取](docs/09-transport-query-strangler.md)、[双实例 HA](docs/10-transport-query-ha.md)、[路由韧性](docs/12-transport-query-resilience.md)
- [IAM 服务提取](docs/13-iam-strangler.md)、[OIDC Authorization Server](docs/21-iam-sso-authorization-server.md)、[学生端 SSO](docs/22-student-sso-frontend.md)、[统一登出](docs/23-sso-rp-initiated-logout.md)、[管理端 OIDC 客户端](docs/24-admin-sso-frontend.md)、[跨应用 Token 撤销](docs/25-sso-token-revocation.md)
- [Payment 服务提取](docs/14-payment-strangler.md)、[退款消息职责迁移](docs/15-payment-refund-messaging-extraction.md)
- [Canal CDC 缓存一致性](docs/18-canal-cache-consistency.md)
- [Booking 服务提取](docs/19-booking-strangler.md)、[Booking↔Payment 事件解耦](docs/20-booking-payment-event-decoupling.md)
- [Transport Command 第一阶段](docs/26-transport-command-foundation.md)

## Swagger 端到端演示

1. 启动中间件（含 RabbitMQ）：

```powershell
E:\HS1\school-bus-runtime\start-runtime.ps1
```

2. 启动应用（另开终端）：

```powershell
$env:JAVA_HOME='E:\jdk-21_windows-x64_bin\jdk-21.0.6'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd E:\HS1\projects\school-bus-platform
mvn spring-boot:run
```

3. 运行演示脚本（注册 → 登录 → 查班次 → 座位图 → 下单 → 支付）：

```powershell
.\scripts\swagger-e2e-demo.ps1
```

脚本会自动写入 demo 班次（tripId=9001），并在 Swagger UI 可复现相同请求：`http://localhost:8080/swagger-ui.html`

## 消息链路验收（Testcontainers）

需 Docker Desktop。运行 Outbox 中继、TTL/DLX 真实验收：

```powershell
.\scripts\run-messaging-acceptance.ps1
```

或：

```powershell
$env:RUN_MESSAGING_ACCEPTANCE_TESTS='true'
mvn test "-Dtest=BookingExpirationMessagingIntegrationTest,BookingExpirationRabbitTopologyIntegrationTest,RabbitMqTopologyIntegrationTest"
```

覆盖：

- **Outbox**：下单写入 `event_outbox` → 手动/定时 relay → RabbitMQ 发布 → `PUBLISHED`
- **TTL/DLX**：delay 队列消息过期 → processing 队列 → Listener 取消订单；拒收消息 → DLQ

## Payment 退款消息真实验收（Cloud）

需 Docker Desktop、Nacos、MySQL、RabbitMQ（含 Management API :15672）。每次运行使用独立 Exchange/Queue/Retry/DLQ 拓扑，避免污染业务队列：

```powershell
.\scripts\cloud\verify-payment-refund-messaging.ps1
```

**当前状态**：代码迁移、单元测试与真实 RabbitMQ retry/DLQ 验收均已完成。2026-08-18 的验收报告为 `target/payment-refund-messaging-20260818-184248/report.json`，状态 `PASSED`。脚本仅在全部验证项（含重复消费幂等、retry/DLQ、ownership 与临时资源清理）通过后标记 `PASSED`。

PowerShell 辅助逻辑单元测试：

```powershell
.\scripts\cloud\verify-payment-refund-messaging.tests.ps1
```

## Canal CDC 缓存一致性（影子链路）

CDC 服务监听 MySQL 提交后的 Binlog：班次变化只删除 Redis List，后续读取按 Cache Aside 从 MySQL 重建；`event_consumed` 插入则投影为带 TTL 的 Redis `DONE` 标记。Redis 只负责加速，MySQL 唯一键仍是消息幂等的最终保证。

```powershell
.\scripts\cdc\prepare-canal-mysql.ps1
docker compose -f .\cloud\docker-compose-cdc.yml up -d

$env:CANAL_CLIENT_ENABLED='true'
mvn -f .\cloud\cdc-cache-sync-service\pom.xml spring-boot:run
```

当前仍处于影子运行阶段，不应删除已有的应用侧班次缓存失效逻辑。设计、验收证据和切换条件见 `docs/18-canal-cache-consistency.md`。

## 本地启动

项目不修改系统默认 Java。打开 PowerShell 后，在当前终端指定 Java 21：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

启动本地基础设施：

```powershell
docker compose -f E:\HS1\school-bus-runtime\compose.yaml up -d
```

运行测试：

```powershell
cd E:\HS1\projects\school-bus-platform
mvn test
```

启动应用：

```powershell
mvn spring-boot:run
```

验证地址：

- 健康检查：`http://localhost:8080/actuator/health`
- 骨架接口：`http://localhost:8080/api/v1/system/ping`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

## 配置

本地默认连接独立数据库 `school_bus_platform`，不会使用旧项目的 `school_bus` 数据库。

可通过环境变量覆盖：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/school_bus_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='root'
```

生产环境不得使用仓库中的本地默认密码，必须通过环境变量或密钥系统传递。

## 目录

```text
src/main/java/com/schoolbus
├─ iam
├─ transport
├─ booking
├─ payment
└─ shared
```

每个业务包后续继续划分：

```text
domain
application
infrastructure
interfaces
```

## 设计文档

- `docs/01-requirements.md`
- `docs/02-domain-model.md`
- `docs/03-database-design.md`
- `docs/04-api-design.md`
- `docs/05-project-skeleton.md`

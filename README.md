# School Bus Platform

校园班车预约平台，从模块化单体开始实现，再逐步演进为 Spring Cloud 微服务。

## 技术基线

- Java 21
- Spring Boot 3.5.16
- Maven 3.9+
- MyBatis Spring Boot Starter 3.0.4
- MySQL 8
- Flyway
- Spring Security
- Springdoc OpenAPI
- Spring Boot Actuator
- Sentinel 1.8.10
- Redis 6+
- RabbitMQ 4+
- Canal 1.1.8（Binlog CDC，当前为影子一致性链路）

## 当前状态

- IAM：独立服务 `school-bus-iam`（:8084）承接注册 / 登录 / refresh / logout / me（绞杀者第二刀）；本地单体默认仍可嵌入
- Payment：独立服务 `school-bus-payment`（:8085）承接支付回调与 **退款 Outbox Relay + RabbitMQ 消费者**（绞杀者第三刀第二阶段）；本地单体默认 Core 仍嵌入退款消息链路
- Booking（绞杀者第四刀，第一阶段，**真实验收已通过**）：独立服务 `school-bus-booking`（:8087）承接 `/api/v1/bookings/**` 与 Booking 侧消息消费（支付成功 / 订单过期 / 班次取消）；Cloud Core 通过 `school-bus.booking.embedded.enabled=false` 关闭嵌入式 Booking 入口，本地单体默认仍嵌入
- Transport 写路径与管理端：仍在 core
- Transport Query（绞杀者第一刀）：独立服务 `school-bus-transport-query`（可多实例，如 :8082/:8083）承接学生端只读
  - `GET /api/v1/trips`
  - `GET /api/v1/trips/{tripNumber}/seats`
- Gateway（:8080）经 Nacos + Spring Cloud LoadBalancer：
  - 上述 GET → `lb://school-bus-transport-query`
  - `POST /api/v1/accounts`、`/api/v1/auth/**` → `lb://school-bus-iam`
  - `POST /api/v1/payments/**` → `lb://school-bus-payment`（不配置自动重试）
  - `/api/v1/bookings/**` → `lb://school-bus-booking`（不配置自动重试）
  - 其余 `/api/**` → core（:8081）
- Cloud Core：关闭嵌入式 IAM、支付回调与 **退款 Relay/Consumer**（`school-bus.payment.refund-messaging.embedded=false`）；只校验 JWT 公钥
- Query 双实例 HA 验收脚本：`scripts/cloud/verify-transport-query-ha.ps1`（见 `docs/10-transport-query-ha.md`）
- Query GET 路由级超时 + 有限重试（仅 502/503/504，最多 2 次调用）：见 `docs/12-transport-query-resilience.md`；对照脚本 `scripts/cloud/verify-transport-query-resilience.ps1`
- Booking / Payment 仍共享 MySQL；Payment 过渡期直接更新 `payment_record` 与 `booking_order`，Booking 的班次取消结算适配器也直接读写 `payment_record`；Booking 还直接读取 Transport 表。这些是第一阶段明确保留的技术债
- Stability：Sentinel 保护登录、下单和支付回调入口，统一返回 HTTP 429（登录限流仍挂在 Core 入口路径；迁至 IAM 为后续项）
- Flyway 仍由 core 执行；query / iam / payment 过渡期只读或读写共享库，独立服务均关闭 Flyway
- **Payment 退款消息迁移**：代码迁移、单元测试与真实 RabbitMQ retry/DLQ 验收均已完成（脚本 `scripts/cloud/verify-payment-refund-messaging.ps1`；Core `/actuator/info` → `refundMessagingOwner=disabled`，Payment → `payment`）
- **CDC 缓存一致性**：新增 `school-bus-cdc-cache-sync`，监听 `transport_trip` 与 `event_consumed` Binlog，经 RabbitMQ 投影到 Redis；2026-08-21 已完成一次真实联调，当前保留应用侧缓存失效作为影子期保护
- **Booking 拆分状态**：Core 537、Booking 53、Gateway 42 项测试全绿；Core ownership 测试覆盖 24 个 Booking 旧 Bean。修订后的真实验收已在 Nacos + MySQL + RabbitMQ + Gateway 上跑通（报告 `target/booking-service-extraction-20260821-165445/report.json`），覆盖路由、写路径无重试、ownership、下单幂等、401、支付成功、过期与班次取消。验收使用 9 个 run-scoped 队列和 7 个交换机，停止服务后逐项删除，不清空共享业务队列。前置检查失败记 `BLOCKED`，业务断言失败记 `FAILED`/`PARTIAL`

详见：`docs/08-nacos-gateway-foundation.md`、`docs/09-transport-query-strangler.md`、`docs/10-transport-query-ha.md`、`docs/12-transport-query-resilience.md`、`docs/13-iam-strangler.md`、`docs/14-payment-strangler.md`、`docs/15-payment-refund-messaging-extraction.md`、`docs/18-canal-cache-consistency.md`、`docs/19-booking-strangler.md`

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

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

## 当前状态

- IAM：独立服务 `school-bus-iam`（:8084）承接注册 / 登录 / refresh / logout / me（绞杀者第二刀）；本地单体默认仍可嵌入
- Transport 写路径与管理端：仍在 core
- Transport Query（绞杀者第一刀）：独立服务 `school-bus-transport-query`（可多实例，如 :8082/:8083）承接学生端只读
  - `GET /api/v1/trips`
  - `GET /api/v1/trips/{tripNumber}/seats`
- Gateway（:8080）经 Nacos + Spring Cloud LoadBalancer：
  - 上述 GET → `lb://school-bus-transport-query`
  - `POST /api/v1/accounts`、`/api/v1/auth/**` → `lb://school-bus-iam`
  - 其余 `/api/**` → core（:8081）
- Cloud Core：`school-bus.iam.embedded.enabled=false`，只校验 JWT 公钥，不再签发
- Query 双实例 HA 验收脚本：`scripts/cloud/verify-transport-query-ha.ps1`（见 `docs/10-transport-query-ha.md`）
- Query GET 路由级超时 + 有限重试（仅 502/503/504，最多 2 次调用）：见 `docs/12-transport-query-resilience.md`；对照脚本 `scripts/cloud/verify-transport-query-resilience.ps1`
- Booking / Payment：仍在 core，锁座与库存事务未改为远程调用
- Stability：Sentinel 保护登录、下单和支付回调入口，统一返回 HTTP 429（登录限流仍挂在 Core 入口路径；迁至 IAM 为后续项）
- Flyway 仍由 core 执行；query / iam 过渡期只读或读写共享库（iam Flyway 关闭）

详见：`docs/08-nacos-gateway-foundation.md`、`docs/09-transport-query-strangler.md`、`docs/10-transport-query-ha.md`、`docs/12-transport-query-resilience.md`、`docs/13-iam-strangler.md`

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

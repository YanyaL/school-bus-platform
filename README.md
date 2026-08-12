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

## 当前状态

### 学生端（已实现）

- IAM：注册、登录、JWT、Redis 会话
- Transport：可预约班次列表、**座位图**、Redis 班次缓存
- Booking：下单（幂等）、我的订单、**详情**、**主动取消**
- Payment：支付回调（HMAC 验签）、超时退款 Outbox

### 管理端（已实现）

- `POST /api/v1/admin/trips` — 创建班次草稿（`DRAFT`）
- `GET /api/v1/admin/trips/{tripNo}` — 班次详情
- `POST /api/v1/admin/trips/{tripNo}/publish` — 发布班次：校验车辆/路线、初始化 `transport_trip_seat` 快照与 `booking_trip_inventory` 库存，状态变为 `OPEN_FOR_BOOKING`

管理端接口需 `ADMIN` 角色 JWT。本地可通过数据库为账号追加 `iam_account_role` 记录获得管理员权限。

### 基础设施

- Flyway V1–V5 基线 schema
- RabbitMQ：支付超时 Outbox relay、TTL/DLX 延迟取消
- Testcontainers 集成测试（需 Docker）

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
- 管理端发布班次：`POST /api/v1/admin/trips` → `POST /api/v1/admin/trips/{tripNo}/publish`
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

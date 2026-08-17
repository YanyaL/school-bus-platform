# IAM 绞杀者拆分（第二刀）

## 1. 拆分前后架构

拆分前（Query 已拆出后）：

```text
Vue -> Gateway :8080
         ├─ GET trips / seats -> school-bus-transport-query
         └─ 其它 /api/**（含 auth、accounts、bookings）
                -> school-bus-core :8081
```

拆分后：

```text
Vue -> Gateway :8080
         ├─ GET /api/v1/trips**
         │       -> lb://school-bus-transport-query :8082
         ├─ POST /api/v1/accounts
         │  /api/v1/auth/**
         │       -> lb://school-bus-iam :8084
         └─ 其它 /api/**（bookings、admin、payment…）
                -> lb://school-bus-core :8081
```

前端 HTTP 契约不变：`userId` 仍为 JSON 字符串；JWT claims（`sub` / `roles` / `sid` / issuer / audience）不变。

## 2. 为什么拆 IAM

认证与发签是横切能力，Booking/Payment 只需要校验 Access Token。将签发迁出后：

- Core / Query 只需公钥校验（无私钥）
- 私钥仅驻留在 `school-bus-iam`
- Gateway 不持有密钥，只做路由

本阶段**不**拆 Booking / Payment，也不引入 SSO。

## 3. Core 嵌入式 IAM 开关

| 场景 | `school-bus.iam.embedded.enabled` | Core 行为 |
|------|-----------------------------------|-----------|
| 本地模块化单体（默认） | `true`（`matchIfMissing`） | 仍提供 register/login/signing |
| Cloud 绞杀者 | `false`（`application-cloud.yml` / Nacos） | 关闭 IAM HTTP 与签发；仅公钥 `JwtDecoder` |

直连 Core `:8081` 在 cloud 模式下也不会再暴露旧的 register/login/refresh（控制器与签发 Bean 均未加载），不是仅靠 Gateway 遮罩。

## 4. 服务与端口

| 服务 | spring.application.name | 默认端口 |
|------|-------------------------|----------|
| Gateway | school-bus-gateway | 8080 |
| Core | school-bus-core | 8081 |
| Transport Query | school-bus-transport-query | 8082 |
| IAM | school-bus-iam | 8084 |

Nacos Data ID：

- `school-bus-core.yml`
- `school-bus-gateway.yml`
- `school-bus-transport-query.yml`
- `school-bus-iam.yml`

```powershell
.\scripts\cloud\initialize-nacos.ps1 -AdminPassword nacos
```

## 5. Gateway 路由

优先于 core fallback（order 更小）：

1. `GET /api/v1/trips` → Query（-20）
2. `GET /api/v1/trips/*/seats` → Query（-19）
3. `POST /api/v1/accounts` → IAM（-15）
4. `/api/v1/auth/**` → IAM（-14）
5. `/api/**` → Core（0）

可配置：`school-bus.gateway.iam-service-id`（默认 `school-bus-iam`）。

## 6. 数据与 Flyway

过渡期：

- IAM 与 Core **共享**同一 MySQL 中的 `iam_account` / `iam_account_role`
- **仅 Core Flyway** 管理 schema；IAM `spring.flyway.enabled=false`，避免双写迁移竞态
- Redis 会话键前缀与 Core 嵌入式时代一致（`school-bus:login-session:*`）

生产目标：IAM 独占账户库；当前共享库为绞杀者过渡方案。

## 7. JWT 密钥分工

| 组件 | 公钥 | 私钥 |
|------|------|------|
| school-bus-iam | 需要 | 需要（签发） |
| school-bus-core（cloud） | 需要 | 不需要 |
| school-bus-transport-query | 需要 | 不需要 |
| school-bus-gateway | 不需要 | 不需要 |

本地密钥仍由 `scripts/cloud/generate-local-jwt-keys.ps1` 生成到 gitignored 的 `cloud/dev-keys/`。入库前执行：

```powershell
.\scripts\security\check-no-private-keys.ps1
```

## 8. 本地启动 IAM

```powershell
$env:JWT_PUBLIC_KEY_LOCATION = "file:E:\HS1\projects\school-bus-platform\cloud\dev-keys\local-dev-public.pem"
$env:JWT_PRIVATE_KEY_LOCATION = "file:E:\HS1\projects\school-bus-platform\cloud\dev-keys\local-dev-private.pem"
cd cloud\iam-service
mvn spring-boot:run
```

与 Core（`cloud` profile）、Gateway、Query 一并注册到 Nacos 后，经 Gateway `:8080` 走完整认证链路。

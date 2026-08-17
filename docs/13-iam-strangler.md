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

## 9. 真实基础设施验收

自动化验收脚本：

```powershell
.\scripts\cloud\verify-iam-strangler.ps1
```

前置条件：

- Docker Desktop 已启动；
- Nacos 3 容器名为 `school-bus-nacos-3`；
- MySQL 容器名默认为 `school-bus-mysql`；
- Redis 容器名为 `school-bus-redis`；
- 端口 `8080`、`8081`、`8082`、`8084` 空闲。

脚本会构建并启动四个真实进程，然后验证：

1. Nacos 中 Core、Transport Query、IAM 各有一个健康实例；
2. 注册、登录、`/auth/me` 均通过 Gateway 路由到 IAM；
3. IAM 签发的 JWT 可分别被 Query 和 Core 本地公钥验证；
4. cloud Core 直连注册、登录端点返回 `404`，证明嵌入式 IAM 已关闭；
5. IAM 停止后登录、刷新返回 `503`，但已有 Access Token 仍可访问 Query 和 Core；
6. IAM 重启后被 Nacos 重新发现，Refresh Token 可以继续轮换；
7. 旧 Refresh Token 不能重放，登出后 Redis 中的 Refresh Session 失效；
8. 只有 IAM 启动进程获得私钥路径，Core、Query、Gateway 均没有私钥环境变量。

结果写入（不进入 Git）：

```text
target/iam-acceptance-reports/iam-acceptance-<timestamp>.json
```

报告只保存 HTTP 状态、布尔值和 Nacos 收敛时间，不保存密码、Access Token、Refresh Token、Nacos Token 或 PEM 内容。

### 登出语义

当前架构采用短期无状态 Access Token 和 Redis 有状态 Refresh Token：

- 登出会删除 Redis 登录会话，使 Refresh Token 立即失效；
- 已签发 Access Token 不查询 Redis，因此在剩余 TTL 内仍可用；
- 若业务要求“登出后 Access Token 立即失效”，需要增加黑名单或每次请求查询会话，但会引入额外网络调用和可用性依赖。

### 当前验收状态

2026-08-17 已在本地 Nacos 3.0.3、MySQL 8.0.36、Redis 6.2.19 上真实执行并通过。证据报告：

```text
target/iam-acceptance-reports/iam-acceptance-20260817-213450.json
```

关键结果：

| 验收项 | 结果 |
|--------|------|
| 注册 / 登录 | `201` / `200` |
| IAM JWT 被 Query / Core 接受 | 通过 / 通过 |
| Core 直连旧注册 / 登录接口 | `404` / `404` |
| IAM 下线后的登录 / 刷新 | `503` / `503` |
| IAM 下线后已有 JWT 访问 Query / Core | `200` / `200` |
| Nacos IAM 健康摘除 | 约 `2.0s` |
| IAM 重启并恢复注册 | 通过 |
| 旧 Refresh Token 重放 | `401` |
| 登出后 Refresh Token | `401` |
| 登出后未过期 Access Token | `200`（无状态设计） |
| 私钥运行时边界 | 仅 IAM 持有 |

报告保留在 Git 忽略的 `target/` 目录，不提交任何 Token、密码、Nacos Token 或 PEM 内容。

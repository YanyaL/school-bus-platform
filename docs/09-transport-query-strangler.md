# Transport Query 绞杀者拆分（第一刀）

## 1. 拆分前后架构

拆分前：

```text
Vue -> Gateway :8080 -> school-bus-core :8081
                         ├─ IAM / Booking / Payment / Admin Transport
                         └─ 学生端 GET /api/v1/trips**
```

拆分后：

```text
Vue -> Gateway :8080
         ├─ GET /api/v1/trips
         │  GET /api/v1/trips/{tripNumber}/seats
         │       -> lb://school-bus-transport-query :8082
         └─ 其它 /api/**（含 admin trips、bookings；auth 已迁出见 docs/13）
                -> lb://school-bus-core :8081
```

> IAM（accounts / auth）的后续拆分见 `docs/13-iam-strangler.md`。
## 2. 为什么先拆只读查询

班次列表与座位图是高频只读路径，不参与本地事务锁座。先拆读路径可以验证：

- Nacos 多服务注册发现
- Gateway 精确路由
- JWT 在下游独立校验
- Redis List 缓存键兼容

同时不触碰超卖防护最敏感的写路径。

## 3. 为什么锁座、库存、订单仍留在 core

Booking 下单事务依赖本地 MySQL 事务同时完成：

- `transport_trip_seat` 条件更新锁座
- 汇总库存扣减
- `booking_order` 写入
- Outbox 同事务

若把锁座改成远程调用，会破坏现有原子性与防超卖。本阶段禁止把写操作迁出 core。

## 4. Nacos 服务注册与发现

| 服务 | spring.application.name | 默认端口 |
|------|-------------------------|----------|
| Gateway | school-bus-gateway | 8080 |
| Core | school-bus-core | 8081 |
| Transport Query | school-bus-transport-query | 8082 |

配置 Data ID：

- `school-bus-core.yml`
- `school-bus-gateway.yml`
- `school-bus-transport-query.yml`

初始化：

```powershell
docker compose -f cloud/docker-compose.yml up -d
.\scripts\cloud\initialize-nacos.ps1 -AdminPassword nacos
```

## 5. Gateway 路由规则

优先于 core fallback：

1. `GET /api/v1/trips` → `lb://school-bus-transport-query`
2. `GET /api/v1/trips/*/seats` → `lb://school-bus-transport-query`
3. `/api/**` → `lb://school-bus-core`

可配置：

- `school-bus.gateway.core-service-id`
- `school-bus.gateway.transport-query-service-id`

仍删除不可信身份头：`X-User-Id` / `X-User-Roles` / `X-Authenticated-User`。

## 6. JWT 如何在查询服务独立验证

Transport Query 使用 OAuth2 Resource Server，只加载 **公钥** 验证 Access Token：

- issuer / audience / RS256 与 core 一致
- `roles` claim → `ROLE_*`
- `/api/v1/trips/**` 必须 authenticated
- **不包含 JWT 私钥**，不能签发令牌

本地/cloud联调需让 core 与 query 使用同一把 RSA 密钥：

```powershell
.\scripts\cloud\generate-local-jwt-keys.ps1
$env:JWT_PUBLIC_KEY_LOCATION = "file:E:\HS1\projects\school-bus-platform\cloud\dev-keys\local-dev-public.pem"
$env:JWT_PRIVATE_KEY_LOCATION = "file:E:\HS1\projects\school-bus-platform\cloud\dev-keys\local-dev-private.pem"
```

`cloud/dev-keys/` 已 gitignore，私钥不入库。

## 7. Redis List 缓存键和失效协议

| 项 | 值 |
|----|----|
| Key | `school-bus:transport:bookable-trips` |
| 结构 | Redis List，元素为 `BookableTripView` JSON（含内部 `tripId`） |
| 空列表 | 单元素 `__EMPTY__` |
| TTL | `school-bus.trip-list-cache.ttl`（默认 `PT1M`） |

Query 服务命中/回源/写失败降级行为与 core 一致。Core 班次写操作后的缓存失效仍删除同一 key，因此两边必须共用该键与 JSON 结构。座位图不加 Redis 缓存。

## 8. 共享数据库只是过渡方案

Query 服务直接读 `transport_trip` / `transport_trip_seat`，Hikari `read-only=true`，**不执行 Flyway**。

本地可暂时复用同一 MySQL 账号；生产应使用只读账号。这不是物理库隔离。

## 9. 数据库写所有权仍属于 core

Schema migration、班次发布/取消、锁座、库存更新的写所有权仍在 `school-bus-core`。

## 10. 回滚方案

Core 中的 `TripQueryController` 等查询入口**暂时保留**作为回滚路径。

若 query 服务严重故障：

1. 调整 Gateway，将两条 GET 路由改回 `lb://school-bus-core`；或
2. 临时下线 query 路由优先级，使请求落入 `/api/**` fallback。

稳定后再删除 core 中重复的学生端查询 HTTP 入口。这不是最终架构。

## 11. 本地启动命令

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

# 中间件：MySQL / Redis / RabbitMQ / Nacos
docker compose -f cloud/docker-compose.yml up -d
.\scripts\cloud\initialize-nacos.ps1
.\scripts\cloud\generate-local-jwt-keys.ps1

$keys = 'E:\HS1\projects\school-bus-platform\cloud\dev-keys'
$env:JWT_PUBLIC_KEY_LOCATION  = "file:$keys\local-dev-public.pem"
$env:JWT_PRIVATE_KEY_LOCATION = "file:$keys\local-dev-private.pem"
$env:NACOS_CONFIG_ENABLED='true'
$env:NACOS_DISCOVERY_ENABLED='true'

# Core
$env:SPRING_PROFILES_ACTIVE='local,cloud'
mvn spring-boot:run

# Transport Query（另开终端，同样设置 JAVA_HOME / JWT / Nacos）
cd cloud\transport-query-service
$env:NACOS_CONFIG_ENABLED='true'
$env:NACOS_DISCOVERY_ENABLED='true'
mvn spring-boot:run

# Gateway（另开终端）
cd cloud\gateway-service
mvn spring-boot:run
```

## 12. 实际验收结果

自动化测试（本机 JDK 21）：

| 模块 | 结果 |
|------|------|
| `mvn test`（core） | 504 run / 0 fail / 0 error / 8 skipped |
| `mvn test -f cloud/gateway-service/pom.xml` | 10 run / 0 fail |
| `mvn test -f cloud/transport-query-service/pom.xml` | 29 run / 0 fail |

真实联调（Gateway + Core + Query + Nacos 注册发现）：**已完成**（2026-08-15，本机 JDK 21）。

验收时先停止占用 8848 的本地 Nacos 1.4.2，再通过
`cloud/docker-compose.yml` 启动 Nacos 3.0.3，并成功发布三个 Data ID。

| 验收项 | 实际结果 |
|--------|----------|
| Nacos 注册发现 | `school-bus-gateway`、`school-bus-core`、`school-bus-transport-query` 各 1 个健康实例 |
| 服务健康 | Gateway 8080、Core 8081、Query 8082 均为 `UP` |
| 未认证访问 | Gateway 与 Query 的 `/api/v1/trips` 均返回 401 |
| JWT 查询 | 通过 Gateway 注册、登录取得 JWT 后，班次列表返回 200 |
| 查询契约 | Gateway 与直连 Query 的班次列表及座位图数据完全一致 |
| 精确路由 | Trips 与 Seats 路由到 Query；Auth 与 Bookings 继续路由 Core |
| Redis List | 缓存键类型为 `list`，存在 TTL；Redis 停止时 Query 回源 MySQL 并返回 200 |
| Query 故障 | 停止 Query 后 Gateway 查询返回 503 |
| 服务恢复 | Query 重启并重新注册 Nacos 后，Gateway 无需重启即恢复 200 |

本次验收已证明单个 Query 实例的注册发现、路由、降级与恢复。

**多实例负载均衡与单实例摘除恢复**已在 `docs/10-transport-query-ha.md` 完成自动化验收
（脚本 `scripts/cloud/verify-transport-query-ha.ps1`）。Sentinel 熔断仍属于后续阶段。

## 13. 已知限制

- 共享数据库，非读写库物理隔离
- Core 与 Query 短期重复查询实现
- 本地 JWT 需显式共享密钥文件
- Booking 写路径未拆分
- 前端未改，仍访问 Gateway `/api`

## 14. 下一阶段建议

1. 观察 query 多实例与 LB cache TTL 的生产权衡后，删除 core 学生端查询 HTTP 回滚入口
2. 引入只读库账号 / 只读副本
3. 评估座位图缓存或 CQRS 投影表
4. 再考虑拆分其它只读边界；写路径保持本地事务直到明确分布式事务方案
5. 按需评估仅对幂等 GET 的有限重试（不要给下单/支付 POST 加自动重试）

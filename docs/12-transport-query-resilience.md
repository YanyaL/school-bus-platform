# Transport Query Gateway 超时与有限重试

## 为什么会出现传播窗口

双实例 HA 中，停止 Query :8082 后：

1. 进程已退出；
2. Nacos 需要心跳超时/摘除才能把 healthy 降到 1；
3. Gateway 内 Spring Cloud LoadBalancer 还有实例缓存 TTL。

在这两段传播完成前，Gateway 仍可能选中已死亡实例，导致客户端看到瞬时 5xx。

本阶段用两类手段覆盖残余窗口：

- **短 LoadBalancer 缓存 TTL**（默认 `2s`，可用 `SPRING_CLOUD_LOADBALANCER_CACHE_TTL` 覆盖）；
- **仅幂等 GET 的有限重试**（最多再试 1 次，总调用 ≤ 2）。

不能依赖无限重试掩盖错误拓扑。

## 只重试哪些路由

| Route ID | 方法/路径 | Retry |
|----------|-----------|-------|
| `school-bus-transport-query-trips` | `GET /api/v1/trips` | 是 |
| `school-bus-transport-query-seats` | `GET /api/v1/trips/*/seats` | 是 |
| `school-bus-core-api` | 其余 `/api/**` | **否** |

可重试状态：`502`、`503`、`504`。
明确不重试：`400`、`401`、`403`、`404`、`409`、`422`、`429`、`500`。

默认 `retries: 1`（在首次请求之外再试 1 次）。同时保留对 `IOException` / `TimeoutException` 的重试，以覆盖连接已断开但状态码尚未返回的竞态。

## connect timeout 与 response timeout

- **connect-timeout**：与下游建立连接的上限（默认 `500ms`）。
- **response-timeout**：等待完整响应的上限（默认 `2s`，必须大于 connect-timeout）。

二者通过 Gateway route metadata 下发到 Query 两条 GET 路由，不影响 Core fallback。

## 为什么只重试幂等 GET，不重试 POST

HTTP GET 在本平台的班次/座位查询语义上是只读、可重复的。
POST 注册、登录、下单、支付、管理写操作即使业务层有 `Idempotency-Key`，本阶段也**禁止** Gateway 统一自动重试，避免：

- 重试风暴放大故障；
- 非幂等写被执行两次；
- 把“需要显式幂等设计”的问题伪装成网关策略。

HTTP 幂等 ≠ 应用层 Idempotency-Key：后者是业务协议，前者是方法语义。Gateway 本阶段只依赖 GET 语义。

## 退避

`first-backoff: 50ms`，`max-backoff: 200ms`，指数因子 2。短退避只为错开瞬时 LB 选择，不是长时间排队。

## 配置

前缀：`school-bus.gateway.transport-query-resilience`

| 项 | 默认 |
|----|------|
| enabled | true |
| retries | 1 |
| connect-timeout | 500ms |
| response-timeout | 2s |
| first-backoff | 50ms |
| max-backoff | 200ms |

非法配置（超时非正、response ≤ connect、enabled 但 retries=0 等）在启动期绑定校验失败。

环境变量示例：

- `TRANSPORT_QUERY_RESILIENCE_ENABLED`
- `TRANSPORT_QUERY_RESILIENCE_RETRIES`
- `SPRING_CLOUD_LOADBALANCER_CACHE_TTL`
- `SCHOOL_BUS_GATEWAY_TRANSPORT_QUERY_RESILIENCE_CONNECT_TIMEOUT`（Boot 松散绑定）

## 可观测性

Micrometer：

- `school_bus_gateway_query_retry_total{route,outcome}`
- `school_bus_gateway_query_retry_exhausted_total{route,outcome}`

标签仅 `route` / `outcome`，不含 userId、tripNumber、Token。
默认不暴露 `metrics`；验收脚本可通过 `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` 临时打开（仅本机）。

可控集成测试：`cloud/gateway-service` 中 `TransportQueryRetry*`（统计真实下游调用次数）。

```powershell
.\scripts\cloud\verify-transport-query-resilience.ps1
.\scripts\cloud\verify-transport-query-resilience.ps1 -ResilienceDisabled -SkipBuild
```

## 真实对照验收结果（本机）

执行时间：2026-08-16

| 项 | 开启重试 | 关闭重试 |
|----|----------|----------|
| 报告 | `resilience-report-20260816-211645.json` | `resilience-report-20260816-211744.json` |
| 故障窗口请求 | 40 | 40 |
| 窗口最终失败 | **0** | **0** |
| Gateway retry metric Δ | **0**（本轮未观察到内部重试） | 0 |
| P95 (ms) | 25.2 | 120.0 |
| P99 (ms) | 331.4 | 206.1 |
| Nacos 收敛到 1 | 1.3s | 1.4s |
| 摘除后 20 次 | 20/20 | 20/20 |
| 无 JWT | 401 | 401 |

说明：本轮关闭重试时窗口内也未出现客户端失败（短 TTL + 探测节奏下未打中死亡实例）。
**未伪造重试数据**。策略有效性由 Gateway 集成测试证明：下游第一次 503、第二次 200 时客户端 200 且调用次数恰为 2；持续 503 时最多 2 次；400/401/403/404/429/500 与 Core POST 均不重试。

## 已知限制

- 重试不能替代健康的服务发现与合理 TTL。
- Gateway 本机验收临时暴露 metrics 时无 JWT（与 Query 不同）；生产应另做管控。
- Core 查询回滚入口仍保留；本阶段不回退查询到 Core。

## 下一阶段（不在本次）

- IAM 拆分
- 写路径显式幂等与有限重试评估（独立任务，不默认开）

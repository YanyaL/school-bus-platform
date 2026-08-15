# Transport Query 双实例与 Gateway 负载均衡验收

## 双实例架构

```text
Vue / Client
    -> Gateway :8080
         lb://school-bus-transport-query
              ├─ Query instance A :8082
              └─ Query instance B :8083
    其它 /api/** -> Core :8081
```

两个 Query 进程使用相同的 `spring.application.name=school-bus-transport-query`，
以不同端口注册到同一 Nacos group，形成同一服务下的两个实例。

## Nacos 服务名与实例

| 项 | 值 |
|----|-----|
| serviceName | `school-bus-transport-query` |
| group | `DEFAULT_GROUP` |
| 实例区分 | IP + port（本机 `8082` / `8083`） |
| 健康实例数（启动后） | 2 |

不要为每个端口创建不同服务名，否则 Gateway `lb://` 无法在同一服务内做负载均衡。

## Gateway 的 `lb://` 含义

`lb://school-bus-transport-query` 表示：

1. 通过服务发现解析服务名；
2. 由 **Spring Cloud LoadBalancer（运行在 Gateway 进程内）** 在健康实例中选择一个；
3. 将请求转发到选中实例。

负载均衡不在 Nacos 服务端完成，而在 Gateway 客户端完成。

## 为什么 JWT 无状态适合多实例

Access Token 由 core 签发，Query 仅用公钥验证。任意实例都能独立验签，
不依赖本地 `HttpSession`，因此请求落到 8082 或 8083 均可。

双实例验证：

- 无 JWT → Gateway 班次接口 **401**
- 有效 JWT → **200**
- Query 启动环境仅注入公钥，不注入私钥

## 自动化脚本

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\scripts\cloud\verify-transport-query-ha.ps1
```

脚本会：发布 Nacos 配置、复用 `cloud/dev-keys` JWT、启动 Core/Gateway/双 Query、
用 **精确 tag** 的 Actuator Metrics（JWT 保护）统计 `/api/v1/trips` 分布，
必要时统一回退到双实例 access log；摘除单实例并验证恢复。

计数规则（`scripts/cloud/ha-request-counting.ps1`）：

1. 前后均使用同一 meter：`uri=/api/v1/trips`、`method=GET`、`status=200`；缺失则计 0，不回退宽泛 tag。
2. 优先 metrics：两实例增量之和等于 HTTP 成功数则采用 metrics；短暂重试后仍不一致则两边统一改用 access log。
3. 禁止混用「一实例 metrics + 另一实例 access log」；最终分布之和必须等于 RequestCount。

验收时 Gateway 使用较短 LoadBalancer 缓存 TTL（`SPRING_CLOUD_LOADBALANCER_CACHE_TTL=2s`），
以便 Nacos 摘除后尽快停止向已下线实例转发。生产如需更短收敛，应单独评估该配置。

## 实际执行结果（本机）

执行时间：2026-08-15（报告 `target/ha-reports/ha-report-20260815-175351.json`）

| 项 | 结果 |
|----|------|
| Query 端口 | 8082、8083 |
| Nacos healthyInstanceCount（启动后） | **2** |
| HTTP 成功请求数 | **60 / 60（100%）** |
| metrics 总数 | **60** |
| access-log 总数 | **60** |
| 最终证据来源 | **metrics** |
| 实例分布 | **8082: 30**，**8083: 30** |
| 停止端口 | 8082 |
| Nacos 收敛到 1 的时间 | **2.8s** |
| 收敛期间瞬时 5xx/失败探针 | **1** |
| 摘除完成后 20 次请求 | **全部 200（100%）** |
| 重启后 healthy | **2**，Gateway 查询成功 |
| 无 JWT | **401** |

分布合计与 HTTP 成功数一致（30+30=60），不要求严格 30:30，但本轮恰好均分。

## 是否需要有限重试

本轮在缩短 Gateway LB 缓存 TTL 后，Nacos 摘除完成后的 20 次请求无失败；
收敛期间出现 1 次瞬时探针失败，完成后全部成功。

**暂不建议**给 POST 下单/支付加自动重试。若生产保留较长 LB cache TTL，
可考虑仅对 **幂等 GET 查询**做有限重试；需另开任务评估，不在本阶段实现。

## 已知限制

- 共享 MySQL / Redis，非物理只读副本隔离
- Core 查询回滚入口仍保留
- HA 脚本默认清理其启动的 Java 进程，保留 Nacos/MySQL/Redis
- Metrics 仅通过环境变量临时暴露，且仍需 JWT，不在公开白名单
- `target/ha-logs`、`target/ha-reports` 不入库

## 下一阶段建议

- 只读副本 / 读写分离
- 查询侧限流与缓存观测
- 生产环境 LoadBalancer 缓存与健康检查参数评估

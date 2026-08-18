# Payment 绞杀者拆分（第三刀，第一阶段）

## 1. 本阶段边界

独立进程 `school-bus-payment`（默认 `:8085`）接管：

```text
POST /api/v1/payments/callback
```

Gateway 使用 `lb://school-bus-payment` 从 Nacos 选择实例。Cloud Core 设置：

```yaml
school-bus:
  payment:
    embedded:
      enabled: false
```

因此 Core 不再暴露旧支付回调；本地模块化单体默认仍启用嵌入式实现。

本阶段尚未迁移 RabbitMQ 退款消费者。退款 Outbox 仍由 Core relay 发布并由
Core 消费，下一阶段再把消息所有权迁入 Payment。

## 2. 为什么暂时共享数据库

原单体支付成功依赖一个本地事务：

```text
验证回调幂等
→ 锁定 booking_order
→ transport_trip_seat: LOCKED → SOLD
→ 插入 payment_record
→ booking_order: PENDING_PAYMENT → PAID
→ 提交
```

如果支付过期、订单已结束或座位锁丢失，则同一事务执行：

```text
插入 REFUND_PENDING payment_record
→ 插入 PaymentRefundRequired Outbox
→ 提交
```

直接把这些写操作改成多个同步 HTTP 调用，会失去本地事务原子性，并产生
“支付成功但订单未更新”等中间状态。因此第三刀第一阶段采用共享 Schema 的
绞杀者过渡：先完成进程、路由、密钥和故障边界迁移，同时保持原有事务语义。

这不是最终微服务形态。下一阶段目标是 Payment 独占支付库，通过
`PaymentSucceeded / PaymentRefundRequired` 事件与 Booking Saga 协作。

## 3. 幂等与并发

支付回调同时使用：

- `payment_record.request_no` 唯一索引：同一回调请求幂等；
- `payment_record.payment_no` 唯一索引：支付平台流水号不可重复；
- 回放时比对订单号、金额、支付时间和支付号，载荷不同返回 `409`；
- `SELECT ... FOR UPDATE` 串行化同一订单的支付确认；
- 订单更新仍检查 `status=PENDING_PAYMENT` 与 `version`；
- 座位使用条件更新，只允许当前订单将 `LOCKED` 改为 `SOLD`。

数据库唯一约束是最终防线。并发插入产生 `DataIntegrityViolationException` 时，
新事务重新查询已有支付记录并返回原结果，而不是重复扣款。

## 4. 为什么 Gateway 不重试支付回调

支付回调是写请求。Gateway 对 `POST /api/v1/payments/**` 不配置 Retry：

- 网络超时不能证明下游没有提交；
- 自动重试可能制造重复支付确认；
- 幂等键用于安全处理“支付平台主动重发”，不能成为网关盲目重试的理由；
- 支付平台应使用同一个 requestNumber/paymentNumber 按协议重发。

Transport Query 的 GET 可以有限重试，但支付、登录、下单等写请求不能照搬。

## 5. HMAC 和 HTTP ID

Payment 使用 HMAC-SHA256 对原始 JSON 字节验签，并用
`MessageDigest.isEqual` 做常量时间比较。必须先验签原始字符串再反序列化，
否则 JSON 格式化、字段顺序或数字表示变化会破坏签名语义。

数据库内部 `payment_record.id` 使用 Snowflake `long`，HTTP 响应将其序列化为
JSON string，避免 JavaScript `Number` 超过 `2^53-1` 后精度丢失。

## 6. 配置和运行

Nacos Data ID：

```text
school-bus-payment.yml
```

关键环境变量：

```powershell
$env:PAYMENT_SERVER_PORT='8085'
$env:PAYMENT_CALLBACK_SECRET='replace-with-a-secret'
$env:SCHOOL_BUS_PAYMENT_WORKER_ID='2'
```

Payment 关闭 Flyway；过渡期只有 Core 执行共享 Schema 迁移，避免多个服务同时
争抢迁移锁和产生不明确的表所有权。

## 7. 自动验收

```powershell
.\scripts\cloud\verify-payment-strangler.ps1
```

脚本使用真实 Nacos 3 和 MySQL，验证：

1. Payment 在 Nacos 中有一个健康实例；
2. Gateway 支付回调返回 `200`；
3. HMAC 回调后 `payment=SUCCEEDED`、`booking=PAID`、`seat=SOLD`；
4. Payment Snowflake ID 在 JSON 中是字符串；
5. 直连 Cloud Core 旧回调返回 `404`；
6. Payment 下线并被 Nacos 摘除后，Gateway 回调返回 `503`，且不会重试写请求；
7. 临时订单、支付记录和座位状态在 finally 中清理。

## 8. 后续迁移

下一阶段按以下顺序消除共享写库：

1. Payment 独占 `payment_record` 与 Payment Outbox；
2. Payment 接收回调后发布 `PaymentSucceeded`；
3. Booking 幂等消费事件并确认订单、座位；
4. Booking 失败时发布 `PaymentRefundRequired`；
5. Payment 幂等退款并发布 `PaymentRefunded`；
6. Booking 消费退款完成事件，形成可补偿 Saga；
7. 加入对账任务处理长时间未收敛的中间状态。

## 9. 本次真实验收结果

2026-08-17 在本地 Nacos 3、MySQL 和真实 Gateway 环境执行通过：

| 验收项 | 结果 |
|---|---:|
| Nacos Payment 健康实例 | 1 |
| Gateway 支付回调 | 200 |
| Cloud Core 旧支付回调 | 404 |
| Payment 下线后 Gateway 回调 | 503 |
| 支付记录 | SUCCEEDED |
| 订单状态 | PAID |
| 座位状态 | SOLD |
| HTTP paymentId 类型 | String |

报告生成在被 Git 忽略的 `target/payment-strangler-*/payment-strangler-report.json`。
验收使用临时订单并在 `finally` 中清理，没有把密钥、回调签名或业务 Token
写入报告。

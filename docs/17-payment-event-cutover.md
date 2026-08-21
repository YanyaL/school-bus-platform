# Payment 事件模式切流设计与验收边界

## 当前实现

Payment 支持以下运行模式：

| 模式 | Payment 是否读取 Booking | Payment 是否更新订单/座位 | 用途 |
|---|---:|---:|---|
| `DIRECT` | 是 | 是 | 默认兼容模式，同时发布影子事件 |
| `EVENT` | 否 | 否 | 事件模式，只写 `payment_record + event_outbox` |

配置：

```powershell
$env:PAYMENT_BOOKING_WRITE_MODE='EVENT'
```

默认仍为 `DIRECT`。首次真实 RabbitMQ 主链路验收已经完成；故障注入、DLQ 和并发
竞争验收完成前，暂不修改生产默认值。

## EVENT 模式调用链

```text
支付回调
  -> Payment: payment_record(SUCCEEDED)
  -> Payment: PaymentSucceeded Outbox
  -> RabbitMQ
  -> Booking 幂等消费者
       -> 可接受：座位 SOLD、订单 PAID
       -> 不可接受：PaymentRefundRequired Outbox
  -> Payment 退款消费者
       -> SUCCEEDED -> REFUND_PENDING
       -> 调用退款网关（paymentNumber 作为幂等键）
       -> REFUNDED
```

`event_consumed` 与 Booking 状态变更或退款 Outbox 位于同一个事务。业务拒绝后
返回 `REFUND_REQUIRED` 并 ACK 原消息，不进入无意义重试。

## 重试分类

| 失败类型 | 例子 | 行为 |
|---|---|---|
| 消息格式错误 | 非法 JSON、未知 schemaVersion | 直接进入 DLQ |
| 业务拒绝 | 订单不存在、金额不匹配、订单超时、座位锁丢失 | 写退款 Outbox并 ACK |
| 技术性失败 | MySQL 暂时不可用、事务冲突 | TTL 队列有限重试 |
| 重试发布失败 | RabbitMQ Confirm 失败 | NACK 原消息并重新入队 |
| 超出重试次数 | 持续性技术故障 | 进入 DLQ，等待人工处理 |

## 已完成的真实验收

2026-08-21 使用真实 MySQL、RabbitMQ 和 Nacos 执行：

```powershell
.\scripts\cloud\verify-payment-event-cutover.ps1
```

最新报告：`target/payment-event-cutover-20260821-132529/report.json`。

| 场景 | 真实结果 |
|---|---|
| Outbox 发布前 | Payment `SUCCEEDED`；Booking `PENDING_PAYMENT`；Seat `LOCKED` |
| PaymentSucceeded 消费后 | Booking `PAID`；Seat `SOLD`；Outbox `PUBLISHED` |
| 同一 `eventId` 重复投递 | `event_consumed=1`；Booking/Seat 版本号均未变化 |
| 金额不匹配 | Payment `REFUNDED`；Booking 仍为 `PENDING_PAYMENT`；Seat 仍为 `LOCKED` |
| 清理 | 临时数据库数据和 RabbitMQ 隔离拓扑均已删除 |

退款完成时，只有原因 `TRIP_CANCELLED` 才会将 Booking 从
`REFUND_PENDING` 推进为 `REFUNDED`。支付金额不一致、支付窗口过期、座位锁丢失等
原因属于“异常入款补偿”，只能退款，不能误改原 Booking。

## 尚未完成的真实验收

必须在 Docker、MySQL、Redis、RabbitMQ、Nacos 可用时验证：

1. 注入一次数据库异常后，消息经过 retry queue 并成功恢复。
2. 持续异常超过 3 次后进入 PaymentSucceeded DLQ。
3. 支付与主动取消、超时取消并发时只有一个合法状态迁移获胜。

目前可以描述为“已完成 PaymentSucceeded 主链路、重复消费和业务补偿的真实
RabbitMQ 验收”。仍不能描述为“已经完成生产级微服务数据解耦”，因为 Payment 与
Booking 仍共享数据库，且故障注入与并发验收尚未全部完成。

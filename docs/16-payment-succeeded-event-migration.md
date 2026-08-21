# PaymentSucceeded 事件兼容迁移

## 目标

本阶段建立 Payment 到 Booking 的支付成功事件链路，但暂不立即删除已经稳定运行的共享数据库更新路径。

```text
Payment callback
  -> payment_record
  -> booking_order / transport_trip_seat（旧路径，暂时保留）
  -> event_outbox: PaymentSucceeded
  -> RabbitMQ: payment.succeeded
  -> Booking consumer
  -> event_consumed 幂等记录
  -> 已更新订单返回 ALREADY_APPLIED
```

消费者同时具备处理 `PENDING_PAYMENT` 订单的能力。现在可通过
`PAYMENT_BOOKING_WRITE_MODE=EVENT` 关闭 Payment 的 Booking 直接写路径：

```text
Payment callback
  -> payment_record + PaymentSucceeded Outbox（同一事务）
  -> RabbitMQ
  -> Booking transaction
       -> LOCKED seat -> SOLD
       -> PENDING_PAYMENT order -> PAID
       -> event_consumed
```

## 为什么采用兼容迁移

- 先生产事件，再观察消费者，避免一次切换造成支付主链路不可用。
- 旧路径已经将订单更新为 `PAID` 时，消费者校验支付号与支付时间后返回 `ALREADY_APPLIED`，不会重复扣减。
- `event_consumed (consumer_name, event_id)` 是消费幂等的数据库防线。
- Outbox 与 `payment_record` 在同一事务中写入，避免“支付成功但事件丢失”的数据库与消息双写问题。
- RabbitMQ 采用持久化消息、Publisher Confirm、独立队列和 DLQ。
- 技术性失败进入 TTL 有限重试队列，默认最多重试 3 次。
- 业务性拒绝不重试；Booking 在消费事务内写入
  `PaymentRefundRequired` Outbox，交给 Payment 补偿退款。

## 事件契约

`PaymentSucceeded` 当前契约版本为 `schemaVersion=1`：

```json
{
  "schemaVersion": 1,
  "paymentNumber": "UUID",
  "bookingNumber": "UUID",
  "amount": 12.50,
  "paidAt": "2026-08-21T09:59:50Z",
  "occurredAt": "2026-08-21T10:00:00Z"
}
```

事件只传递 Booking 完成状态迁移所需的最小数据，不暴露内部 Snowflake 主键。

## 当前边界与下一阶段

代码已具备两种迁移模式：

- `DIRECT`（默认）：Payment 继续更新 Booking，并发布影子事件；消费者返回
  `ALREADY_APPLIED`。
- `EVENT`：Payment 只保存支付记录和 Outbox，不读取或更新 Booking 表；Booking
  消费事件完成订单和座位状态迁移。

当前仍不宣称已经完成生产切流：`EVENT` 模式尚未执行真实 RabbitMQ 并发验收，
且 Payment 与 Booking 仍共享同一个 MySQL schema。

下一阶段验收：

1. 在隔离环境开启 `PAYMENT_BOOKING_WRITE_MODE=EVENT`。
2. 验证正常支付、重复投递、业务拒绝退款、技术性重试及 DLQ。
3. 完成支付、超时取消、主动取消三方竞争的真实并发验收。
4. 验收稳定后再把默认值切换为 `EVENT`，最终删除 Direct 分支。

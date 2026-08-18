# Payment 退款消息链路绞杀者拆分

## 1. 迁移前调用链（Core 单体 / cloud Core）

```text
业务事务（支付确认 / 班次取消）
  → INSERT event_outbox (PaymentRefundRequired)
  → COMMIT

PaymentRefundOutboxRelayScheduler (@Scheduled)
  → MyBatisOutboxRelayRepository.claimReady (乐观锁 version)
  → RabbitOutboxEventPublisher.publish + Publisher Confirm
  → markPublished / markFailed

RabbitMQ schoolbus.payment.events
  routing-key payment.refund.required
  → queue schoolbus.payment.refund

PaymentRefundListener (@RabbitListener, manual ack)
  → PaymentRefundApplicationService
  → SimulatedRefundGateway（本地）
  → PaymentRefundTransaction (REQUIRES_NEW)
       → INSERT event_consumed (幂等)
       → UPDATE payment_record → REFUNDED
       → LocalRefundedBookingAdapter → booking_order REFUNDED
  → basicAck

失败：retry queue (TTL) → 原 queue；超限 → DLQ
```

Core cloud 模式下 **Relay + Consumer 均在此进程**；与 Payment 回调拆分前相同。

## 2. 迁移后调用链（cloud）

```text
Core / Payment 回调仍写 event_outbox（共享 MySQL）
PaymentRefundOutboxRelay + Scheduler 仅在 school-bus-payment
  → 同一 Exchange / Routing Key / Queue

PaymentRefundListener 仅在 school-bus-payment
  → SharedDatabaseRefundedBookingAdapter 直接更新 booking_order

Core cloud:
  school-bus.payment.refund-messaging.embedded=false
  → 不加载退款 Relay / Consumer / 退款应用服务
  → 保留共享 RabbitMQ 配置与 Outbox Repository，供 Booking/Transport 使用
```

Outbox **写入**仍可在 Core（如班次取消 `LocalTripCancellationRefundAdapter`）或 Payment 回调路径；**发布与消费**归 Payment。

## 3. 所有权开关

| 模式 | `refund-messaging.embedded` | Relay | Consumer |
|------|----------------------------|-------|----------|
| 本地单体（默认） | `true` | Core | Core |
| cloud Core | `false` | 无 | 无 |
| school-bus-payment | N/A（始终启用） | Payment | Payment |

注解：`@ConditionalOnEmbeddedRefundMessaging`（Core）。

## 4. 一致性要点

- **Outbox**：解决 DB 提交与 MQ 发送的双写；仍属 **至少一次** 投递。
- **Publisher Confirm**：仅确认 Broker 接收，不代替 DB 事务。
- **消费幂等**：`event_consumed (consumer_name, event_id)` + 业务状态校验；与退款更新同事务（`REQUIRES_NEW`）。
- **retry / DLQ**：可恢复基础设施异常进 retry queue；格式/业务冲突 reject 进 DLQ，不无限重试。

## 5. 当前限制

- 仍 **共享 MySQL**；Payment 直接 UPDATE `booking_order` / `payment_record`。
- 非完整 Saga；非独立 Payment 库。
- 不宣称 Exactly Once。

## 6. 下一阶段

通过领域事件解除 Payment 对 Booking 表的直接写入：

- `PaymentSucceeded` / `PaymentFailed`
- `RefundSucceeded` / `RefundFailed`

由 Booking 消费事件更新订单、座位与库存；Saga 与最终一致性在后续阶段完成。

验收脚本：`scripts/cloud/verify-payment-refund-messaging.ps1`

## 7. 验收状态

**当前状态**：代码迁移、单元测试与真实 RabbitMQ retry/DLQ 验收均已完成。

脚本要求（全部通过才为 `PASSED`）：

| 验证项 | 说明 |
|--------|------|
| Nacos Payment 健康 | `school-bus-payment` 在 Nacos 注册且 healthy |
| Outbox + Confirm | `event_outbox` → `PUBLISHED` |
| 消费 + 幂等 | 支付/订单 `REFUNDED`，`event_consumed` 唯一 |
| Retry | 缺订单可恢复失败 → 隔离 retry queue → TTL 后成功 |
| DLQ | 非法消息 → Management API 确认 DLQ 含 message ID |
| Ownership | Core `refundMessagingOwner=disabled`；Payment `=payment`（Actuator `/actuator/info`，非日志推断） |

每次运行使用 `verify-<runId>` 后缀的临时拓扑，结束后删除。报告路径：`target/payment-refund-messaging-<runId>/report.json`。

2026-08-18 真实验收报告：`target/payment-refund-messaging-20260818-184248/report.json`。

- 状态：`PASSED`
- Nacos：`school-bus-payment` healthy=1
- Ownership：Core=`disabled`，Payment=`payment`
- Outbox / Publisher Confirm / 正常退款：通过
- 相同 `eventId` 重复投递：`event_consumed` 仍为 1，退款版本与更新时间不变
- Retry：Management API 观察到隔离 Retry Queue 消息，TTL 后支付与订单均为 `REFUNDED`
- DLQ：Management API 按唯一 message ID 确认非法消息进入隔离 DLQ
- 临时业务数据与 RabbitMQ 拓扑：清理完成

Retry 消息回到主队列后被消费者迅速处理，因此 Management API 采样未在主队列捕获 `x-death`；Retry Queue 中间态和最终 `REFUNDED` 状态共同构成本次验收证据。

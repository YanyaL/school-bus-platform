# Booking ↔ Payment 事件驱动解耦

状态：**代码、单元/架构测试与真实验收均已完成**（本机 Nacos/MySQL/Redis/RabbitMQ 可用时实测 `PASSED`）。
最近验收报告：`target/booking-payment-event-decoupling-20260824-002521/report.json`。
环境不可用时脚本记 `BLOCKED`，不伪造 `PASSED`。

## 目标

切断 Booking 对 `payment_record` 的读写，以及 Payment 在 EVENT 模式下对
`booking_order` 的直接更新，改为 Outbox + RabbitMQ：

| 方向 | 事件 | 生产者 | 消费者 |
|------|------|--------|--------|
| Booking → Payment | `RefundRequested`（payload 兼容原 `PaymentRefundRequiredMessage`） | Booking Outbox（context=`booking`） | Payment `PaymentRefundListener`（同时接受旧类型 `PaymentRefundRequired`） |
| Payment → Booking | `PaymentSucceeded`（已有） | Payment Outbox | Booking `PaymentSucceededListener` |
| Payment → Booking | `PaymentRefunded` | Payment Outbox（EVENT 模式） | Booking `PaymentRefundedListener` |

## 写路径模式

- `school-bus.payment.migration.booking-write-mode`
  - **EVENT（云默认）**：Payment 确认支付只写 `payment_record` + `PaymentSucceeded`；退款完成写 `PaymentRefunded`，不更新 `booking_order`
  - **DIRECT**：保留给显式测试 / 回退；`SharedDatabaseRefundedBookingAdapter` 仅在 DIRECT 激活

## 关键流

### 支付成功（EVENT）

1. Payment callback → 插入 SUCCEEDED 支付 + `PaymentSucceeded` Outbox
2. Relay → `payment.succeeded`
3. Booking 消费 → 确认座位 / `PAID`；失败补偿写 `RefundRequested`

### 用户取消已支付订单

1. Booking：`PAID` → `REFUND_PENDING`（`USER_CANCELLED`），释放已售座位，追加 `RefundRequested`
2. Payment：`prepareRefund`（SUCCEEDED→REFUND_PENDING）→ 网关退款 → `completeRefund` → `PaymentRefunded` Outbox
3. Booking：`PaymentRefunded` → `confirmRefund` → `REFUNDED`（用户取消不走班次取消 saga）

### 班次取消已支付订单

1. Booking：`requestRefundBecauseTripWasCancelled` + `RefundRequested`（不再改 `payment_record`）
2. Payment 同上完成退款并发布 `PaymentRefunded`
3. Booking：确认退款，并在 `TRIP_CANCELLED` 时 `completeRefund` saga / 可能追加 `TripCancellationBookingsSettled`

## 本地单体兼容

- Core `LocalTripCancellationRefundAdapter`、`LocalRefundedBookingAdapter` 保留
- Core `BookingCancellationTransaction` 同步支持 PAID 取消写退款 Outbox
- 本地 profile（嵌入 Booking/Payment）不因云 EVENT 默认而破坏

## 验收

```powershell
.\scripts\cloud\verify-booking-payment-event-decoupling.tests.ps1
.\scripts\cloud\verify-booking-payment-event-decoupling.ps1
```

报告目录：`target/booking-payment-event-decoupling-<runId>/report.json`
状态：`PASSED` / `BLOCKED` / `FAILED` / `PARTIAL`。

验收会暂时停止 Booking 服务，再触发 Payment callback：此时必须观察到
`payment_record=SUCCEEDED`、`PaymentSucceeded Outbox=PUBLISHED`，同时
`booking_order=PENDING_PAYMENT`。Booking 重启并消费事件后，订单才允许变为
`PAID`。这组前后状态用于证明 Payment 在 EVENT 模式下没有直接更新 Booking
表。脚本还会从 `/actuator/info` 核对 `paymentBookingWriteMode=EVENT`，并在
结束时按本次 eventId 清理幂等记录、查询确认残留为 0。

## 剩余债

- Booking 仍共享 MySQL，并直接读写 Transport 座位表
- Payment DIRECT 适配器与补偿路径仍保留
- Core 单体退款 Outbox 事件类型仍可为历史 `PaymentRefundRequired`（云 Booking 已切 `RefundRequested`）
- 真实验收依赖完整班次/座位 schema 与 Gateway 路由；若种子表结构与环境漂移，脚本会 `FAILED`/`BLOCKED` 而非虚报成功

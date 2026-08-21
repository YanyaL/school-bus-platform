# Booking 绞杀者拆分（第一阶段）

## 1. 本阶段边界

独立进程 `school-bus-booking`（默认 `:8087`）接管 Booking HTTP 与 Booking 侧消息消费：

```text
GET/POST /api/v1/bookings/**
Booking 过期 delay → processing / DLQ
PaymentSucceeded → 订单确认 / 退款 Outbox
TripCancellationRequested → 订单结算 / 退款 Outbox
```

Gateway 使用 `lb://school-bus-booking`。Cloud Core 设置：

```yaml
school-bus:
  booking:
    embedded:
      enabled: false
```

因此 Core 不再暴露 BookingController 与 Booking 拥有的 listeners / schedulers；
`MyBatisBookingOrderRepository` 等共享库仓储可继续留给 Transport 适配器使用。

本地模块化单体默认 `embedded=true`（`@ConditionalOnEmbeddedBooking`，matchIfMissing）。

## 2. 为什么暂时共享数据库

下单、支付成功确认、过期取消、班次取消结算仍依赖本地事务：

```text
锁库存 / 锁座位 / 写 booking_order / 写 outbox
```

若立刻拆成同步跨服务写，会失去原子性并引入“座位已锁但订单未落库”等中间态。
本阶段采用共享 Schema 的绞杀者过渡：先完成进程、路由、JWT 公钥校验与消息所有权迁移。

这不是最终微服务形态。

## 3. 所有权信号

| 组件 | `/actuator/info` |
|------|------------------|
| school-bus-booking | `bookingOwner=booking` |
| Cloud Core（embedded=false） | Booking HTTP/Listener 不加载 |

## 4. 已完成（代码 / 单测）

- `cloud/booking-service` 可独立编译：`mvn -f cloud/booking-service test`
- JWT resource-server（公钥校验）、Nacos discovery/config、Rabbit / Redis / MySQL 配置骨架
- PaymentSucceeded / BookingExpiration / TripCancellationRequested 的 Booking
  消费端拓扑声明；TripCancellationSettled 拓扑仍由 Transport 所有
- 关键单元测试：contextLoads、Controller、Creation/PaymentSucceeded 事务、Ownership contributor
- Core：`EmbeddedBookingConfigurationTest`（embedded=false 去掉 Controller，保留 Repository）
- Nacos 样例：`cloud/nacos-config/school-bus-booking.yml`
- 真实验收脚本：`scripts/cloud/verify-booking-service-extraction.ps1`
  （状态判定单测：`scripts/cloud/verify-booking-service-extraction.tests.ps1`）

## 5. 真实验收结果

**已通过（PASSED），并已完成隔离拓扑加固后的重新实跑。**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\cloud\verify-booking-service-extraction.ps1
```

运行环境：Docker 中的 Nacos 3 `:8848`、MySQL `:3306`、Redis `:6379`、
RabbitMQ `:5672` / 管理端 `:15672`；本机 Java 21。脚本自行发布 Nacos 配置、
`clean package` 五个模块，并拉起 Core `:8081`、Transport Query `:8082`、
IAM `:8084`、Booking `:8087`、Gateway `:8080`。

修订后报告：`target/booking-service-extraction-20260821-165445/report.json`，
`status=PASSED`，`failureCategory=verification_succeeded`。审查发现旧脚本曾清空
固定的 Booking 过期延迟队列，可能影响共享开发环境；现已改用带 `runId` 的 9 个
独立队列和 7 个独立交换机，并在停止服务后逐项删除、逐项确认不存在。修订版已
完成真实重跑，不再把共享业务队列作为测试清理目标。

| 标记 | 值 | 真实证据 |
|------|----|----------|
| `nacosBookingHealthy` | true | Nacos `school-bus-booking` healthy=1 |
| `gatewayRoutesBookingToService` | true | 路由 `lb://school-bus-booking`；Gateway `/api/v1/bookings` 200，直连 Core `:8081` 同一路径 404，直连 Booking `:8087` 200 |
| `gatewayBookingRouteHasNoRetry` | true | Booking 路由无 Retry filter（同表 transport-query 路由有 Retry 作对照）；一次 POST 后 `booking_order` 行数为 1 |
| `coreBookingEmbeddedDisabled` | true | Core `/actuator/info` `bookingOwner=disabled` |
| `bookingServiceOwnershipReported` | true | Booking `/actuator/info` `bookingOwner=booking` |
| `createBookingVerified` | true | 注册/登录取 JWT → 列车次 → 看座位图 → 创建（201）→ 同 `Idempotency-Key` 重放返回同一 `bookingNumber` 且 `Idempotency-Replayed: true` → 列表 → 详情 → 取消；座位 `LOCKED→AVAILABLE`，库存 `3→4`，`booking_order.status=CANCELLED / USER_CANCELLED` |
| `unauthenticatedBookingRejected` | true | 无 JWT 的 GET 与 POST 均 401，且未落任何 `booking_order` |
| `paymentSucceededConsumedByBookingOnly` | true | 订单转 `PAID`、座位转 `SOLD`；`event_consumed` 恰好 1 行，`consumer_name=booking-payment-succeeded-consumer`（Core 未消费）；重投后仍为 1 行且版本不变 |
| `bookingExpirationVerified` | true | 由 outbox → delay 队列 → 死信 → processing 队列的真实链路触发（`trigger=delay-queue`），订单 `CANCELLED / PAYMENT_TIMEOUT`，座位与库存归还；重投后 version/updated_at 不变，DLQ 为空 |
| `tripCancellationSettlementVerified` | true | `TripCancellationRequested` 结算订单为 `CANCELLED / TRIP_CANCELLED`，saga `SETTLED`，`TripCancellationBookingsSettled` outbox 1 行；重投幂等 |
| `temporaryDataCleaned` | true | 清理后 trip / seat / inventory / order / saga / outbox / consumed / iam 账号残留均为 0 |
| `temporaryTopologyCleaned` | true | 9 个 run-scoped 队列和 7 个交换机均在服务停止后删除并确认不存在；未清空固定业务队列 |

验收约定：Java / Docker / 基础设施 / 端口这类前置检查失败记 `BLOCKED`；
业务断言失败记 `FAILED` 或 `PARTIAL`，绝不因环境问题掩盖业务失败，也不会伪造标记。

两点说明（脚本内已记录）：

- Booking 过期链路按设计不写 `event_consumed`，幂等性以重投后 `booking_order`
  的 `version` / `updated_at` 不变来证明。
- 过期延迟队列使用逐条消息 TTL，队头长 TTL 会阻塞后续短 TTL。验收脚本不能因此
  清空共享业务队列，而是为每次运行创建独立 delay / processing / DLQ 拓扑，结束时
  停止消费者后删除该拓扑。

## 6. 下一阶段

- 逐步解除 Booking 对 Transport/Payment 表的直接读写，改为领域事件协作。目前
  `SharedDatabaseBookableTripGateway` 直接读取 `transport_trip`，班次取消结算适配器
  还会读取并更新 `payment_record`；Payment 的 DIRECT 模式也会直接更新
  `booking_order`。这些都是第一阶段共享数据库下明确保留的过渡耦合。

# Booking 库存就绪门控 / Inventory readiness shadow

## 状态与边界

本阶段在 `TripPublished` 影子投影之后增加默认关闭的库存就绪核对，解决“收到班次发布事件不等于座位与汇总库存已经可用”的问题。它只读取当前共享库中的 `transport_trip_seat` 和 `booking_trip_inventory`，只写 Booking 自己的 `booking_trip_inventory_readiness` 观察表。

它没有初始化座位、没有扣减库存、没有改变 `BookableTripGateway`，也没有阻断或放行当前下单请求。因此这仍是影子验证，不是生产切换、拆库或异步库存自治。

```text
TripPublished 影子快照
        ↓ 定时有限批次核对
期望座位集合 ─────┐
具体座位只读查询 ─┼─ 精确集合与数量比较 ─→ WAITING / READY
汇总库存只读查询 ─┘
        ↓
booking_trip_inventory_readiness（Booking 所有）
```

## 核对规则

只有以下条件全部满足才记录 `READY`：

1. `booking_trip_inventory` 存在；
2. `total_seats` 等于发布快照中的座位数量；
3. `transport_trip_seat` 没有重复座位行；
4. 实际座位集合与发布快照完全一致。

不满足时保持 `WAITING`，并记录稳定的诊断码：`INVENTORY_MISSING`、`INVENTORY_TOTAL_MISMATCH`、`DUPLICATE_SEAT_ROWS` 或 `SEAT_SET_MISMATCH`。后续扫描会重新核对，因此同步初始化稍晚完成时可以从 `WAITING` 收敛到 `READY`。

## 并发与版本

候选查询以 `booking_trip_publication_shadow.trip_version` 为发布版本。就绪记录的 upsert 只允许相同或更高发布版本覆盖；旧扫描结果不能回写覆盖新版本。每个候选在独立 `REQUIRES_NEW` 事务中核对，一个班次失败不会回滚整批。

`READY` 只证明“某一发布版本在某次核对时，座位模板和汇总总量一致”。它不代表座位永远可用，也不替代下单时的条件更新、库存乐观锁和订单唯一约束。

## 开关与运行

默认关闭：

```yaml
school-bus:
  booking:
    inventory-readiness-shadow:
      enabled: false
      batch-size: 100
      initial-delay-ms: 15000
      fixed-delay-ms: 10000
```

仅在已应用 V8、V9 且 TripPublished 影子消费已启用的受控环境中设置：

```powershell
$env:BOOKING_INVENTORY_READINESS_SHADOW_ENABLED='true'
```

## 测试与事实边界

- Booking 非容器回归覆盖 READY、库存缺失、总量不符、座位集合不符、损坏快照、批次故障隔离和默认关闭行为。
- `InventoryReadinessBoundaryTest` 防止核对组件向正式座位或库存表写入。
- `InventoryReadinessIntegrationTest` 使用一次性 MySQL 验证 WAITING→READY、正式库存不被修改、同版本 READY 不降级及新版本可重新进入 WAITING，并已加入 `verify-trip-publication-shadow.ps1`。
- 2026-09-02 在 Docker Desktop 4.89.0 与 MySQL 8.4 Testcontainers 上完成真实执行；Booking 完整回归为 116 项、0 失败、0 错误、0 跳过。该结果证明本就绪核对的数据库行为，不代表生产端 Outbox 到消费者的全部切换条件已经满足。

## 下一阶段退出条件

在真正切换库存所有权前，必须先满足：

1. 生产端 Outbox、Booking Inbox 和本就绪核对的真实 MySQL/RabbitMQ 验收全部通过；
2. 重复、乱序、服务重启与暂时性数据库失败不会产生错误 READY；
3. 定义班次取消先到、发布事件晚到时的状态机；
4. 确认只有 Booking 初始化库存，并让未 READY 的班次无法下单；
5. 提供回退开关和历史班次受控回填方案。

## 面试知识点

**为什么收到 TripPublished 还不能直接开放下单？** MQ 的 ACK 只证明消息已被处理，无法证明具体座位和汇总库存满足下单不变量。把事件消费结果与资源就绪状态分开，能够避免“控制面已发布、数据面未准备好”的竞态窗口。

**为什么影子核对只读正式表？** 当前同步事务仍是唯一权威初始化者。若影子消费者也写座位和库存，会形成双写者并引入重复初始化和所有权冲突。先观察并积累一致性证据，再切换写入者。

**为什么不是一次扫描一个大事务？** 每个班次使用独立新事务，单个脏数据或锁冲突不会拖垮整批；失败候选保留，后续可以重试和排查。

简历上可描述为：在 Strangler Fig 渐进拆分过程中，为 Transport→Booking 的事件迁移设计版本化库存就绪核对，使用只读影子验证和显式退出条件控制切换风险。当前不能写成“已完成库存异步自治或拆库”。

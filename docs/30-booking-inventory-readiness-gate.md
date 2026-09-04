# Booking 库存就绪强制门禁 / Inventory readiness enforcement

## 目标与边界

本阶段把此前只用于观察的库存就绪结果接入 Booking 新下单路径。门禁默认关闭，
不会改变现有同步初始化与下单行为；显式开启后，只有当前 `TripPublished` 最新版本
对应的就绪记录为 `READY`，才允许进入锁座、扣库存和订单写入。

这一步仍不初始化座位或库存，也没有切换库存写入所有权。它提供的是切流前必须具备的
“失败关闭”能力，而不是宣称已经完成 Transport 与 Booking 拆库。

```text
幂等请求命中旧订单 ─────────────→ 返回原结果

新下单
  → 校验班次可预约
  → 检查最新发布版本是否 READY
      ├─ 否：409 TRIP_INVENTORY_NOT_READY，零业务写入
      └─ 是：重复预约检查 → 锁座 → 扣库存 → 订单与 Outbox 落库
```

## 为什么门禁位于事务内部

就绪判断和后续锁座位于同一次 `REQUIRES_NEW` 下单尝试中，避免在事务外检查后等待很久
再写入。门禁只是迁移安全条件；真正防止并发超卖的仍是座位条件更新、库存 `version`
乐观锁和数据库唯一约束。

相同 `Idempotency-Key` 已经创建成功时优先返回原订单，不重新检查门禁。否则一次成功请求
可能因后续配置切换而无法获得原结果，破坏 HTTP 幂等语义。

## 查询规则

强制实现使用一条 MyBatis 查询同时要求：

1. `booking_trip_inventory_readiness.status = 'READY'`；
2. 就绪记录的 `publication_version` 等于影子投影中的最新 `trip_version`；
3. 两条记录属于同一个 `trip_id`。

没有记录、仍为 `WAITING` 或仅旧版本曾经 READY 都返回未就绪。数据库异常不会被当成
READY，因而保持失败关闭。

## 配置与上线顺序

默认值：

```yaml
school-bus:
  booking:
    inventory-readiness-gate:
      enabled: false
```

受控环境应先开启 TripPublished 影子消费与 readiness 扫描，观察最新发布版本稳定收敛，
再设置 `BOOKING_INVENTORY_READINESS_GATE_ENABLED=true`。回退时关闭门禁即可恢复原下单路径；
不要删除观察数据来掩盖不一致。

## 验证状态

- Booking 回归：121 项，0 失败，0 错误，13 项因 Docker 未运行而跳过；实际通过 108 项。
- 单元测试覆盖默认放行、显式启用数据库门禁、未 READY 时零业务写入、HTTP 409 错误码，
  以及最新发布版本查询适配器。
- Testcontainers 用例已扩展为验证 WAITING 时拒绝、READY 时放行、新版本重新 WAITING 后
  再次拒绝；本轮 Docker 未运行，因此这些新增真实 MySQL 断言尚未执行。

## 面试知识点

**为什么消息消费成功还要 readiness gate？** RabbitMQ ACK 只能证明消费者完成了一次处理，
不能证明下单依赖的座位集合与汇总库存已经满足业务不变量。门禁把“收到事件”和“资源可用”
分成两个状态，避免班次已经可见但库存尚未准备好的竞态窗口。

**为什么默认关闭？** 渐进迁移需要可观测、可灰度和可回退。先积累影子结果，再开启强制门禁，
出现异常可以关闭门禁而不回滚数据库结构或恢复旧代码。

**为什么不把 READY 当作防超卖手段？** READY 是发布阶段的一次完整性证明；下单阶段的并发
正确性仍由条件更新、乐观锁和唯一索引保证。两者解决的是不同时间点的问题。

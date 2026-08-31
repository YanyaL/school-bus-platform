# 班次发布事件 / Trip publication Outbox (shadow phase)

## 状态与范围 / Status and scope

2026-08-31：事件生产、事务 Outbox、RabbitMQ 投递及自动化测试代码已完成。
**真实 MySQL + RabbitMQ 联合验收尚未执行通过**：本轮 Docker 引擎不可用，严格验收脚本输出 `BLOCKED`，不能用 Maven 的跳过结果替代真实验收。

The producer is implemented behind disabled-by-default feature flags. Real MySQL/RabbitMQ acceptance is blocked by the local Docker engine. This is a shadow event stream, not asynchronous inventory ownership or database separation.

本分支接续 Transport Command 车辆/路线管理第一阶段。班次发布 HTTP 和事务仍在 Core；没有新增服务副本，没有修改 Gateway 路由或前端，也没有把同步库存初始化改成远程调用。

## 为什么先做生产端

现有发布操作同步创建具体座位和 Booking 汇总库存。若直接迁出事务，再发送 MQ，会出现“班次已开放预约，但库存尚未初始化”的窗口。

这一步先建立可恢复的事件记录与版本化契约；消费端、就绪确认、乱序处理和所有权切换单独实施。在这些条件完成之前，同步初始化仍是唯一权威写入路径，影子队列没有业务消费者。

```text
管理员发布班次
  └─ 同一个 MySQL 事务
       ├─ DRAFT → OPEN_FOR_BOOKING
       ├─ 初始化 transport_trip_seat
       ├─ 初始化 booking_trip_inventory（保留现状）
       └─ 插入 TripPublished Outbox（开关开启时）
             ↓ 提交成功后，可被独立 Relay 读取
       CAS 认领 → RabbitMQ Confirm / Return → 更新 Outbox 状态
             ↓
       有界影子队列（暂不初始化或修改业务库存）
```

## 事件契约 v1

Outbox 的 `event_id` 和 AMQP `messageId` 使用同一个 UUID；重试不得重新生成。AMQP `type=TripPublished`，header 包含 `eventId`、`eventType`、`schemaVersion` 和可选 `traceId`。

| Payload 字段 | JSON 类型 | 含义 |
| --- | --- | --- |
| schemaVersion | number | 消息格式版本，当前 1 |
| tripId | string | 内部 Snowflake ID；避免非 Java 消费者的数字精度损失 |
| tripNumber | string | 对外班次 UUID |
| tripVersion | number | 发布时的聚合版本，不是消息格式版本 |
| seatNumbers | string[] | 发布时的不可变、非空且无重复座位快照 |
| totalSeats | number | 从座位快照计算，不独立接受另一个数量 |
| price | string | 两位小数金额，避免浮点误差 |
| bookingDeadline / departureTime / publishedAt | string | UTC ISO-8601 时间 |

`TripPublishedEvent` 做防御性拷贝和基本校验。事件保存发布时的值，Relay 不重新读取可变的班次或车辆模板拼装消息。消息不包含学生信息、密码或 Token。

`schemaVersion` 管格式演进，`tripVersion` 供未来消费者判断聚合事件顺序。当前不宣称已实现 Booking 消费去重/乱序校验。

## 事务与投递语义

- `TripPublicationApplicationService.publish()` 原有 `@Transactional` 保留。
- `MyBatisTripPublicationOutbox.append()` 使用 `MANDATORY`：必须加入调用方事务，禁止误用成独立写入。
- 库存初始化失败或 Outbox 插入失败，班次状态、具体座位、库存和事件一起回滚。
- Relay 不持有发布事务等待网络；认领和结果标记使用现有短事务及 `version` CAS。
- 仅扫描 `context_name=transport AND event_type=TripPublished`，不会抢其他事件类型。
- 消息持久化；Confirm ACK **且没有 Return** 后才标记 `PUBLISHED`。ACK 不是业务消费完成凭证。
- NACK、不可路由的 Return、Confirm 超时、连接错误均保留失败记录，按指数退避重试；达到配置上限后 `FAILED + next_retry_at=NULL`，需要排查并人工补偿，不会静默删除。
- Broker 已接收但回写 MySQL 失败时可能再次发送同一事件。因此语义为 **at-least-once**，不是 exactly-once；下一阶段消费者必须在自己的本地事务中做幂等。
- Relay 崩溃留下的 `PROCESSING` 可在租约到期后重新认领；多实例/网络超时仍可能造成重复，CAS 不等于跨系统原子性。

复用的 `OutboxMapper`、`MyBatisOutboxRelayRepository` 等通用技术组件当前仍位于 `payment.infrastructure` 包中。这是既有包结构债务，本轮未复制一套通用实现，也未借机大范围重构。

## 开关和影子拓扑

Core 默认不开启新行为：

```yaml
school-bus:
  transport:
    publication-events:
      enabled: false
      relay-enabled: false
```

| 设置 | 行为 |
| --- | --- |
| enabled=false（默认） | no-op Outbox 端口，不创建新 Rabbit 拓扑；原发布流程继续同步工作 |
| enabled=true，relay-enabled=false | 发布事务记录事件并声明影子拓扑，不自动投递 |
| 两者均 true | 自动投递；还受既有 `school-bus.messaging.outbox-relay.enabled` 总开关约束 |

通过环境变量 `TRIP_PUBLICATION_EVENTS_ENABLED=true` 和 `TRIP_PUBLICATION_RELAY_ENABLED=true` 在 Core 中显式开启。共享重试参数仍使用 `school-bus.messaging.outbox-relay.*`。

默认拓扑（可用同一 prefix 的配置覆盖）：

- durable TopicExchange：`schoolbus.transport.publication.events`
- routing key：`trip.published.v1`
- durable queue：`schoolbus.transport.trip-published.shadow`
- 上限 10,000 条，`x-overflow=reject-publish`，不设置过期丢弃策略。

影子队列没有业务消费者，长时间运行会积压。满队列时 Broker 拒绝发布，事件保留在 Outbox 等待重试；应监控队列深度与失败记录，限制影子运行范围。当前普通 durable queue 不宣称具备 RabbitMQ quorum 集群高可用。

关闭 `relay-enabled` 可暂停投递而继续记录；关闭 `enabled` 则后续发布不再产生事件。重新开启不回填关闭期间的历史发布，需要后续明确的回填方案。不要删除失败事件来掩盖异常。

## 测试和复现

单元测试覆盖：不可变契约、超过 JavaScript 安全整数的 ID、默认关闭行为、双开关、总停机开关、持久化插入校验、ACK/NACK/Return/超时/中断、同 ID 重投、退避上限与失败记录异常。

扩展 `TripPublicationTransactionIntegrationTest`，使用一次性 MySQL/RabbitMQ Testcontainers 验证：

1. 班次、具体座位、库存与 Outbox 同时提交；库存失败全部回滚。
2. 用隔离数据库触发器制造 Outbox 插入失败，验证前面的业务写入回滚。
3. 重复发布不产生第二条事件；事务外调用 Outbox 被拒绝。
4. 真实 Rabbit 消息身份/持久化属性和契约，投递后 Outbox 为 `PUBLISHED`。
5. 删除测试 binding 产生真实 Return，事件保持 `FAILED`；恢复 binding 后使用原 `eventId` 重试成功。

严格验收入口（自动创建测试容器，不使用项目业务数据库，不启动/重置 Docker）：

```powershell
.\scripts\cloud\verify-trip-publication-outbox.ps1 -JavaHome 'C:\Program Files\Java\jdk-21'
```

可用 `-Maven` 指定 Maven 可执行文件。该入口要求新产生的 Surefire 报告、全部必需用例、零跳过/失败/错误，不能把旧报告或全部跳过判成成功；结果写入被 Git 忽略的 `target/trip-publication-outbox-*/`。

本轮记录：

- Core 回归：574 tests，0 failures，0 errors，64 skipped（即 510 实际通过）。
- Transport Command 回归：62 tests，0 failures/errors/skips。
- Gateway 回归：60 tests，0 failures/errors/skips。
- 严格验收：`target/trip-publication-outbox-20260831-185258-698944f7/report.json`，`BLOCKED`，Docker engine pipe 不存在。
- 本轮没有真实运行班次事件投递或回滚验收，不宣称压测、吞吐量、线上可靠性或数据库自治已完成。

## 下一阶段（本轮未实现）

1. 先解除 Docker 阻塞并跑通上述真实验收。
2. Booking 增加幂等的影子消费者，按事件身份/聚合版本校验投影，不重复初始化现有库存。
3. 定义 InventoryReady / 发布就绪门控及超时补偿，覆盖重复、乱序、取消先到、消费失败与服务重启。
4. 在证明“只有 Booking 一个初始化者、未就绪班次不可下单”之后再切换写入所有权。
5. 最后迁移班次 HTTP/状态调度到 Transport Command，并逐步消除共享表访问。

## 面试表达（基于已写代码）

**为什么不用事务提交后直接发 MQ？** 提交与发送之间进程崩溃会丢通知；Outbox 把“发布成功”和“待投递事件”放进一个本地事务，再由独立 Relay 补发。提交后监听器仍用于已有缓存失效，但不能取代持久化事件。

**为什么 Confirm ACK 还不够？** ACK 表示 Broker 接受本次发布，不保证一定路由到队列，更不代表 Booking 业务完成。本实现同时检查 mandatory Return；消费确认和业务就绪需要下一阶段独立处理。

**为什么会有重复？** Broker 成功但数据库状态更新失败会重投。这一步保证稳定 eventId；消费者必须用幂等记录和业务更新的本地事务兜底，不能只靠 Redis 或承诺 exactly-once。

**为什么没有一次拆完？** 发布、座位和库存目前共用本地事务。直接异步化会改变业务不变量。先用影子链路验证契约和可靠投递，再引入就绪状态与消费幂等，最后切换权威写入者，是有明确退出条件的渐进迁移。

简历对应：Spring Cloud 渐进拆分、Transactional Outbox/RabbitMQ 可靠事件投递、班次管理三个已有方向的深化。当前应写“实现班次发布 Outbox 与可开关影子投递”，不要写“已完成 Transport/Booking 拆库及库存异步自治”。

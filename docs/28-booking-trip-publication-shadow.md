# Booking 班次发布影子消费者 / TripPublished shadow consumer

## 状态 / Status

2026-08-31：Booking 影子消费者、事务 Inbox、版本化快照和失败处理已实现；默认关闭。
**2026-09-02 已在 Docker Desktop 4.89.0 上完成真实 MySQL/RabbitMQ 消费端测试；尚未完成生产端 Outbox 整链路验收，也未进行云环境消费切换。**

The Booking service now has an opt-in TripPublished observation consumer. It writes only isolated shadow tables, not live orders, seats or inventory. Container acceptance remains blocked by the local Docker engine. This is not inventory autonomy or a production cutover.

本阶段接续 [生产端 Outbox](27-trip-publication-outbox.md)。生产端仍由 Core 同事务发布班次、创建座位与库存、记录事件；既有业务入口和消费职责不变。

## 为什么现在做

事件可靠产生以后，还需要证明消费端能识别重投、拒绝冲突、不被旧版本覆盖，并正确处理事务失败。直接把原同步库存初始化替换成异步消费，会引入“班次开放但库存未就绪”的窗口。本阶段先积累独立观察状态，不作为任何下单依据。

```text
Core TripPublished Outbox → RabbitMQ publication exchange
                                 ├─ 既有 Transport 影子队列（保留）
                                 └─ 新 Booking 影子队列
                                       ↓ 严格契约解析
                                    本地新事务
                                       ├─ Inbox 去重 + 内容指纹
                                       ├─ 班次版本判断 + 影子快照
                                       └─ 完成 Inbox 处理结果
                                       ↓ 提交后返回，容器 AUTO ACK
```

## 代码与表职责

所有消费端 Java 位于 `cloud/booking-service`，不向 Core 再复制一份消费者：

- `application/trippublication/TripPublicationSnapshot`：发布时不可变事实及规范化序列化。
- `TripPublicationEnvelope`：规范 UUID 消息身份。
- `TripPublicationShadowTransaction`：`REQUIRES_NEW`，编排 Inbox 与快照的同一事务。
- `TripPublicationShadowStore`：仅定义影子状态操作；MyBatis 实现使用 `MANDATORY` 加入事务。
- `infrastructure/persistence/trippublication`：唯一键插入、当前读和受版本约束的更新。
- `infrastructure/messaging/trippublication`：Decoder、Listener、独立 Rabbit 工厂、条件配置。

新增 `V8__add_booking_trip_publication_shadow.sql`，只创建两张表：

| 表 | 用途 |
| --- | --- |
| booking_trip_publication_inbox | `event_id` 主键、tripId、SHA-256 内容指纹、处理结果、收到时间 |
| booking_trip_publication_shadow | `trip_id` 主键、唯一 tripNo、当前发布版本、JSON 快照、指纹、最后事件 ID、创建/更新时间 |

共享库过渡阶段，Flyway migration 仍由 Core 管理；Booking 不新增一个竞争迁移者。DDL 是增量建表，不修改历史迁移或既有业务表。本轮未在用户业务数据库执行迁移。

影子组件不访问 `booking_order`、`booking_trip_inventory`、`transport_trip_seat` 或共享 `event_consumed`，也不访问 Redis 消费标记。架构守卫检查这些依赖；真实数据库测试仅创建两张观察表，若意外访问业务表会直接失败。

## 两个不同的幂等维度

| 输入情形 | 结果 | 快照变化 |
| --- | --- | --- |
| 新事件、新班次 | APPLIED | 创建 |
| 相同 eventId、相同规范内容 | DUPLICATE | 不更新，包括更新时间 |
| 相同 eventId、不同规范内容 | 永久冲突 → DLQ | 不更新 |
| 新 eventId、相同当前版本和内容 | ALREADY_APPLIED | 不更新 |
| 新 eventId、相同当前版本但不同内容 | 永久冲突 → DLQ | 事务回滚 |
| 新 eventId、较低发布版本 | STALE | 记录观察结果，不覆盖当前快照 |
| 新 eventId、较高发布版本 | APPLIED | 版本条件更新 |
| 同一 tripId 的 tripNo 改变，或 tripNo 被其他 tripId 占用 | 永久冲突 → DLQ | 事务回滚 |

版本比较针对 **本投影保留的 TripPublished 版本**，不是所有班次事件的全局水位。它没有消费取消事件，不知道真实班次是否已取消，也不据此开放预约。
同版本内容冲突检查针对当前保留快照；旧版本只有“不覆盖”的保证，不宣称完整历史版本审计。

Inbox 插入只把 MySQL `DuplicateKeyException` 解释为已存在，其他数据库异常继续抛出。重复事件通过 `FOR SHARE` 当前读比较已提交的指纹；投影通过 `FOR UPDATE` 当前读和版本条件保护，避免使用旧快照。
并发不同事件可能产生死锁/锁等待，属于有限重试范围；不能把唯一索引或行锁说成“永远不会冲突”。

### SHA-256 在这里为什么出现

它是**规范化业务内容的指纹**，不是密码加密，也不是本轮的 Refresh Token 安全处理。

比较前使用固定字段、排序对象键、规范 UUID/时间/金额，避免 JSON 空白或字段顺序导致误报。专用不可变序列化器与 HTTP ObjectMapper 的格式化/自定义配置隔离。座位数组顺序保留并参与比较。此摘要不提供消息签名或来源认证，Broker 的账号权限、网络隔离仍是部署要求。

影子 Inbox 不复用正式业务消费者的去重命名空间，防止未来正式消费者把“观察过”误当成“库存初始化完成”。当前没有 Redis 快路径，也没有自动清理 Inbox；保留周期必须覆盖允许的重放窗口，后续再设计归档和清理。

## 消息契约与边界校验

共享样例是仓库根目录 `contracts/trip-published-v1.json`。生产端测试断言实际 Outbox Payload 与它一致，Booking 测试从同一文件读取，避免双方分别写一个“看似相同”的样例。

- 支持 `schemaVersion=1`，AMQP `type=TripPublished`，规范 UUID `messageId`；可选 eventId/schemaVersion header 必须一致。
- Snowflake `tripId` 必须是 JSON string，拒绝 JSON number、溢出、前导零等非规范 ID。
- `tripVersion` 必须是正整数；金额为非负两位小数字符串。
- 座位数量必须与数组一致，非空、无重复；时间满足发布早于预约截止、预约截止早于发车。
- 拒绝重复 JSON 键、尾随 JSON、未知 v1 字段、超出 64 KiB 的 Body。
- v1 当前使用严格格式；新增字段要先协调契约/兼容策略，不能单方面假设消费者会忽略。

## ACK、重试和 DLQ

仅新监听器使用独立 `tripPublicationShadowContainerFactory`，不修改原有支付/超时/退款监听器的手动 ACK 配置。

1. 新容器使用 Spring `AUTO` ACK：不是 Rabbit 的 no-ack 模式，而是监听方法成功返回后才确认。
2. 监听器调用另一个 Bean 的事务代理；提交成功才返回。失败时异常交给容器 advice，不提前 ACK。
3. 只有明确的暂时性数据库异常与连接资源失败进行短周期重试，默认**最多 3 次尝试，包含首次**，间隔 500ms；每次进入一个新事务。
4. 畸形消息、内容冲突、SQL 用法错误不盲目重试；重试耗尽通过 `RejectAndDontRequeueRecoverer` 拒绝到独立 DLQ。
5. 原消息在本地重试期间保持未确认，本阶段不另建 TTL retry 队列，不发生“转发成功与 ACK”之间的新双写窗口。

限制：重试上限按一次容器 delivery 计算。进程重启/连接断开可能重新投递并重置该预算，不是跨重启的永久最大次数。当前为普通 durable queue；DLX 本身的集群故障保障、DLQ 告警/运营重放仍需后续完善，不宣称 exactly-once 或 MQ 零丢失。

Metric `schoolbus.booking.trip_publication.shadow` 仅以结果枚举作为 tag，不使用用户/班次/消息 ID 高基数标签；它在事务成功后计数。没有改变 Actuator 暴露或鉴权配置。

## 开关、部署顺序与回退

```yaml
school-bus:
  booking:
    trip-publication-shadow:
      enabled: false
```

通过 Booking 环境变量 `BOOKING_TRIP_PUBLICATION_SHADOW_ENABLED=true` 显式开启；默认关闭时不创建新监听器、事务服务、影子 Rabbit 拓扑。

默认源 Exchange / key 与生产端一致：`schoolbus.transport.publication.events` / `trip.published.v1`。
新队列：`schoolbus.booking.trip-published.shadow`；DLX/DLQ 分别为同名前缀 `.dlx` / `.dlq`。
配置项 `exchange`、`routing-key`、`queue`、`dead-letter-exchange`、`dead-letter-queue` 可覆盖。新业务观察队列上限 10,000、满时 reject-publish；DLQ 不自动丢弃，需要监控积压。

受控环境顺序（本轮未执行）：

1. 先通过 Core 应用 V8，并验证仅新增两张观察表。
2. 在 Booking 显式启用影子消费者，确认新队列绑定；不要订阅旧 Transport 影子队列与其竞争。
3. 按上一阶段文档开启 Core 生产端与 Relay；检查发布事件在两个影子队列的分布和积压。
4. 后创建的新消息会路由到新队列；Rabbit **不会自动补发新 binding 建立前的历史事件**。补录必须用原事件身份，设计受控重放，不能随意改所有 Outbox 状态。
5. 停用影子消费者只需关闭开关并重启 Booking；保留观察表和积压消息供排查，原同步发布/库存不变。

生产端旧影子队列仍保留且没有业务消费者，长时间运行会积压，满时可能导致生产端 NACK 与同 ID 重投。这也是本轮禁止直接长期全量开启的原因之一。

## 测试与真实验收

- Core：574 tests，0 failures/errors，64 skipped（510 实际通过）。
- Booking：106 tests，0 failures/errors，11 skipped（95 实际通过），覆盖契约、防重复、冲突/旧版本、默认关闭、隔离约束、有限重试及事务代理。
- 事务代理测试使用真实 Spring AOP 和记录型事务管理器，验证提交、回滚、MANDATORY；**它不替代 MySQL 回滚测试**。
- 新增 11 项一次性 MySQL/RabbitMQ 测试：重复/并发投递、版本保护、标记写入失败回滚、事务边界、真实 ACK/幂等/DLQ 和暂时性异常重试。
- Rabbit 重复验收检查 DUPLICATE 计数增加、Inbox 仍 1 行、快照时间不变，以及 Management API 的 ready/unacknowledged 均归零。
- 暂时性数据库故障在 Mapper 边界注入；后续事务/SQL、Rabbit 容器与队列是真实的。不要把它描述成真实网络断连演练。

完整严格验收入口：

```powershell
.\scripts\cloud\verify-trip-publication-shadow.ps1 -JavaHome 'C:\Program Files\Java\jdk-21'
```

依次执行生产端与消费者的容器测试；要求新生成的报告、全部必需用例且零跳过/错误/失败。它是两组容器集成测试，不是完整 Nacos/Gateway 多服务云切换验收。
校验器单测：`.\scripts\cloud\verify-trip-publication-shadow.tests.ps1`。

本轮报告：`target/trip-publication-shadow-20260831-223524-e8688d86/report.json`，`BLOCKED`，Docker engine pipe 不存在。
没有开启用户业务环境的消费开关、没有修改 Docker 运行目录、没有宣称真实验收通过。

## 下一步

优先修复/启动 Docker，跑通生产端和本轮消费者验收。之后才设计 InventoryReady、发布就绪门控、取消与发布乱序的协调和超时补偿。不能从本投影的 APPLIED 直接推导“库存可下单”，更不能马上删除原同步初始化。

## 面试知识点与事实边界

**为什么只有 eventId 去重不够？** 相同 eventId 若内容被错误复用，直接跳过会掩盖问题；不同 eventId 也可能表达同一版本。本实现分别检查消息身份和当前聚合版本，并比较规范化内容指纹。

**为什么 Inbox 与快照一起提交？** 先写已消费标记再另起事务处理，失败重投时会误判已完成。这里两者同一事务，只有成功提交后才 ACK；进程在提交后 ACK 前退出，重投走幂等路径。

**为什么还需要重试与 DLQ？** 唯一键/锁处理并发正确性，不保证每次请求都成功；暂时性锁等待和连接失败可以有限重试，畸形契约与内容冲突则需要隔离排查。

**为什么没有加 Redis 标记？** 本轮目的是验证新事件链路的正确性，不复用正式业务去重空间。缺少测量证据时，不用额外缓存增加一致性问题，也不宣称性能提升。

**简历对应哪部分？** 对应“Transactional Outbox + RabbitMQ 最终一致性/幂等消费”和“Strangler Fig 渐进拆分”。当前可以讲实现了班次发布影子消费、事务去重与版本保护；不能写成 Transport/Booking 已拆库、库存已经异步自治或真实高并发验收通过。

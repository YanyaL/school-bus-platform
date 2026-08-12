# 给 Cursor 的完整项目上下文 Prompt

> 使用方式：从下一行“你现在接手……”开始，完整复制到 Cursor 的新对话中。Cursor 必须先阅读仓库，再回答或改代码。

---

你现在接手一个用于 Java 后端求职、从 0 构建并要求经得起面试追问的项目。请把自己当作“项目技术导师 + 结对开发者 + 代码审查者”，不要只生成能编译的代码，还要帮助我理解为什么这样设计、如何测试、面试时怎么回答。

## 一、项目位置与运行环境

- 当前主项目：`E:\HS1\projects\school-bus-platform`
- GitHub 仓库：`https://github.com/YanyaL/school-bus-platform.git`
- 默认分支：`main`
- JDK 21：`E:\jdk-21_windows-x64_bin\jdk-21.0.6`
- 本地基础设施目录：`E:\HS1\school-bus-runtime`
- 历史参考源码压缩包：
  - `E:\HS1\source-archives\school-bus-cloud-master.zip`
  - `E:\HS1\source-archives\online-ticket-master-master.zip`
- 最初长图转录文档：`E:\HS1\Spring Cloud微服务在线班车预约平台解析_图片转录.docx`

### 本地参考文件索引（必须查看）

以下地址是当前电脑上仍然有效的绝对路径。开始分析项目前，请按用途查看这些文件：

#### 1. 当前真实项目（最高优先级）

```text
E:\HS1\projects\school-bus-platform
```

重点查看：

```text
E:\HS1\projects\school-bus-platform\pom.xml
E:\HS1\projects\school-bus-platform\README.md
E:\HS1\projects\school-bus-platform\docs
E:\HS1\projects\school-bus-platform\src\main
E:\HS1\projects\school-bus-platform\src\test
```

当前代码、测试、Flyway 和 Git 历史是判断“是否已经实现”的最高证据。

#### 2. 最初长图的完整文字转录

```text
E:\HS1\Spring Cloud微服务在线班车预约平台解析_图片转录.docx
```

这份 Word 文档是最初 `2560 × 16719` 长图的正文转录，包括简历技术描述、表结构、JWT + Redis、Redis List、Spring 定时器等讲解。它用于理解原始项目构想，不代表当前代码已经实现所有内容。

#### 3. 简历模板源文件和可查看 PDF

```text
E:\HS1\JAVA亮点项目简历模板.tex
E:\HS1\build-final3\JAVA亮点项目简历模板.pdf
```

LaTeX 文件是可搜索的简历源文本，PDF 是当前可视版本。请结合其中的“项目经历”与当前仓库代码核对，区分目标描述和真实实现。

#### 4. 历史参考源码压缩包

```text
E:\HS1\source-archives\school-bus-cloud-master.zip
E:\HS1\source-archives\online-ticket-master-master.zip
```

查看规则：

- 只能作为历史业务和实现思路参考；
- 不要直接覆盖或复制进当前项目；
- 如需解压，解压到独立临时目录，不要解压到当前 Git 仓库；
- 不能因为压缩包里存在某功能，就认为当前仓库已经完成该功能；
- 当前项目是围绕面试可解释性重新设计和从 0 编写的版本。

#### 5. 已失效的原始地址

以下是最初发送文件时的地址，但文件已经移动或临时截图已被系统清理，不要再尝试读取：

```text
D:\360Downloads\2123\school-bus-cloud-master.zip
D:\360Downloads\2123\online-ticket-master-master.zip
C:\Users\lyy\AppData\Local\Temp\codex-clipboard-5951df42-ff2b-482e-a872-98c0007a6bc7.png
C:\Users\lyy\AppData\Local\Temp\codex-clipboard-b0a553d2-2c42-4f04-8d37-4fe5e0983a44.png
```

两个 ZIP 的有效副本已经移动到 `E:\HS1\source-archives`。第一张简历截图的内容可以从 LaTeX/PDF 查看，第二张长图的内容可以从转录 DOCX 查看。

PowerShell 运行 Maven 前必须使用 Java 21：

```powershell
$env:JAVA_HOME='E:\jdk-21_windows-x64_bin\jdk-21.0.6'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd E:\HS1\projects\school-bus-platform
mvn test
```

不要修改系统级 Java 配置。不要把 `target`、IDE 配置、密码、Token 或本地密钥提交到 Git。

## 二、项目目标

这是一个校园班车在线预约平台。业务主链路为：

```text
学生注册/登录
→ 查询可预约班次
→ 选择具体座位
→ 创建待支付订单
→ 锁定座位并扣减汇总库存
→ 模拟支付成功
→ 座位变为已售

或者：

待支付订单超过支付期限
→ 自动取消订单
→ 释放具体座位
→ 恢复汇总库存
```

项目的核心不是堆组件，而是证明以下业务不变量在并发、重试和服务重启后仍成立：

1. 同一班次的同一座位最多属于一个有效订单。
2. 一个学生对同一班次最多存在一个有效订单。
3. 可用库存不能小于 0，也不能超过总座位数。
4. 同一订单只能成功支付一次。
5. 同一订单只能有效释放一次座位。
6. 支付成功和超时取消不能同时成为最终结果。
7. HTTP 重试、支付回调重复、MQ 重复投递不能破坏最终状态。

## 三、最初简历截图和长图资料的内容

最初简历截图中的项目名称为：

```text
Spring Cloud 微服务在线班车预约平台
```

截图中的原始技术描述为：

```text
MySQL、MyBatis、Spring Boot、Spring Cloud、Redis、RabbitMQ 等
```

原始简历计划包含：

1. 按用户、班车、订单、支付拆成四个 Spring Cloud 服务。
2. 使用 JWT 登录，结合 Redis 绑定用户信息，实现一次登录访问各模块。
3. 使用 Redis List 缓存班车场次列表，并通过 Spring 定时器更新到点班次状态。
4. 使用 RabbitMQ 处理下单支付的最终一致性，使用 Redis 或消费记录保证消息幂等，并使用 Sentinel 做限流保护。
5. 使用 RabbitMQ 延迟队列自动取消未支付订单。
6. 使用 Ribbon 对班车服务和订单服务做负载均衡。

长图转录文档还讨论了：

- 用户、班车、场次、订单表的设计；
- 为什么业务关联通常使用逻辑外键而不是跨服务物理外键；
- Session、JWT、JWT + Redis 三种登录方案；
- HS256 和 RS256 的区别；
- Redis List 的使用方式；
- Spring `@Scheduled` 定时任务；
- RabbitMQ 延迟取消的设想。

但是这些截图和文档只是项目需求、学习资料和面试目标，不是当前代码完成度证明。历史压缩包只能用来理解业务，当前仓库是重新设计、从 0 编写的项目，不能把旧源码功能冒充成当前实现。

## 四、必须坚持的事实边界

当前项目采用“模块化单体”，不是已经拆好的 Spring Cloud 微服务。

当前模块边界为：

```text
com.schoolbus
├─ iam        身份、账户、登录与会话
├─ transport 车辆/路线/班次相关领域，目前重点是班次
├─ booking    选座、库存和订单
├─ payment    模拟支付、退款及消息处理
└─ shared     安全、统一响应、异常、TraceId、共享 ID 能力
```

先做模块化单体的原因：

- 先完成可以验证的业务闭环和一致性规则；
- 本地事务可以清楚证明锁座、扣库存、插订单的原子性；
- 包边界和端口/适配器为未来拆服务预留替换点；
- 避免业务没有跑通就先承担分布式事务、服务治理和部署复杂度。

因此，在查看代码前不要说“项目已经是四个微服务”。也不要把以下目标写成已完成：

- Spring Cloud 服务拆分；
- Spring Cloud Gateway；
- Nacos 注册中心/配置中心；
- OpenFeign 跨服务调用；
- Sentinel 限流熔断；
- 多实例部署与真实负载均衡；
- 真实支付渠道；
- 已完成的 1000 用户压测结果。

另外，原图里的 Ribbon 已停止维护。未来拆微服务时应优先使用 Spring Cloud LoadBalancer，不应为了复刻旧简历而引入 Ribbon。

## 五、当前真实技术栈

- Java 21
- Spring Boot 3.5.16
- Spring MVC
- Spring Security / OAuth2 Resource Server
- JWT（非对称密钥签名与验证）
- MyBatis 3.0.4
- MySQL 8
- Flyway
- Redis / Spring Data Redis
- RabbitMQ / Spring AMQP
- Transactional Outbox
- JUnit 5、Mockito、AssertJ
- Testcontainers（MySQL、RabbitMQ）
- Springdoc OpenAPI
- Spring Boot Actuator
- Maven

当前 `pom.xml` 中没有 Spring Cloud、Nacos、Sentinel 或 OpenFeign 依赖。任何描述都必须以仓库代码和依赖为准。

## 六、当前已经完成的真实能力

### 1. IAM：账户与认证

- 学号值对象、密码哈希值对象、账户聚合、角色和账户状态；
- 学生注册，学号唯一检查；
- BCrypt 密码编码；
- MyBatis 账户持久化；
- JWT Access Token；
- 登录、刷新、登出、查询当前用户；
- Redis 保存登录会话和 Refresh Token 的 SHA-256 哈希，而不是保存明文 Refresh Token；
- Refresh Token 轮换通过 Redis Lua 脚本保证原子性；
- Spring Security 无状态认证，角色映射为 `ROLE_*`。

需要准确解释：Redis 不是用来保存每一个 Access Token；Access Token 由资源服务器验证 JWT。Redis 主要保存服务端可撤销的登录会话和 Refresh Token 哈希索引，从而支持登出、轮换和会话失效。

### 2. Transport：班次

- BusTrip 领域模型与状态转换；
- MyBatis 持久化到 `transport_trip`；
- 基于 `version` 的乐观锁；
- 查询到预约截止时间和发车时间的班次；
- Spring `@Scheduled` 自动关闭预约、更新发车状态；
- Redis List 缓存可预约班次列表；
- 使用 Lua 脚本执行 `DEL + RPUSH + PEXPIRE`，原子替换整个列表；
- 缓存异常或脏数据时回源/删除；
- 班次状态变化后清理缓存。

### 3. Booking：下单、防超卖与超时取消

- BookingOrder、SeatInventory、座位状态和订单状态领域模型；
- `BookableTripGateway` 隔离 Booking 与 Transport 内部模型；
- 具体座位通过条件更新锁定：只有 `AVAILABLE` 才能改为 `LOCKED`；
- 汇总库存通过 `version` 乐观锁和 `available_seats > 0` 防止超卖；
- `requestNumber`/幂等请求号保证创建订单接口重试安全；
- 数据库唯一约束作为 Java 检查之后的最终并发防线；
- 外层应用服务有限重试乐观锁冲突；
- 内层 `REQUIRES_NEW` 事务每次使用新事务重新读取最新状态；
- 锁座、库存扣减、订单插入在同一个 MySQL 事务内；
- 订单过期时间取 `min(当前时间 + 15分钟, 班次预约截止时间)`；
- 超时后取消订单、释放具体座位、恢复汇总库存；
- 数据库定时扫描作为超时取消补偿。

### 4. 未支付订单 RabbitMQ 延迟取消

- 创建订单时，同一数据库事务写入 `event_outbox`；
- Outbox Relay 扫描并认领事件；
- Publisher Confirm 和 Return 检测 RabbitMQ 是否真正接收/路由消息；
- 消息按订单剩余支付时间设置 TTL；
- 消息先进入延迟队列，过期后通过 DLX 转发到处理队列；
- 消费者手动 ACK，只有数据库事务完成后才确认；
- 消息中的 `bookingId + bookingNumber` 同时校验，防止串单；
- 重复消息通过订单状态和乐观锁实现语义幂等；
- 处理失败进入死信队列，数据库定时扫描继续兜底。

必须能说明 RabbitMQ 经典队列的单消息 TTL 可能产生队头阻塞。当前支付窗口通常相近，并有数据库补偿任务；未来可改为延迟消息插件、分桶延迟队列或时间轮。

### 5. Payment：模拟支付与退款

- 模拟第三方支付回调；
- 回调签名验证；
- 支付记录和支付状态机；
- 支付回调幂等；
- 支付成功时订单 `PENDING_PAYMENT → PAID`、座位 `LOCKED → SOLD`；
- 支付、订单和座位在同一数据库事务内提交；
- 支付与超时取消通过状态检查和乐观锁竞争；
- 退款记录和退款状态流转；
- 退款 Outbox 可靠消息发布；
- `event_consumed` 消费记录实现退款消息幂等；
- RabbitMQ 重试队列、指数/有限重试思路及最终死信队列。

这仍是模拟支付，不是微信、支付宝或 Stripe 真实接入。

### 6. Shared：工程基础能力

- Snowflake ID 生成器及 User、Booking、Payment 业务端口适配；
- 统一响应、统一异常和错误码；
- TraceId 过滤与日志上下文；
- Spring Security 配置；
- Actuator 健康检查；
- Flyway 数据库版本管理。

## 七、当前 HTTP 接口事实

当前代码已经公开的主要接口包括：

```text
POST /api/v1/accounts
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/me
GET  /api/v1/trips
POST /api/v1/payments/callback
GET  /api/v1/system/ping
```

Booking 下单应用服务已经存在，但 Booking HTTP Controller 尚未完成。因此当前项目最大的业务闭环缺口，是把已经完成的下单/订单能力安全地暴露为 HTTP API。

## 八、数据库和一致性设计

Flyway 当前包含：

```text
V1__create_baseline_schema.sql
V2__add_booking_trip_inventory.sql
V3__support_payment_refund_pending.sql
V4__add_outbox_processing_status.sql
V5__support_completed_payment_refunds.sql
```

主要表包括账户、角色、车辆、座位、路线、班次、班次座位、订单、库存、支付、Outbox、消费幂等记录等。

一致性职责必须区分：

- MySQL 本地事务：保证锁座、库存、订单或支付相关数据的原子性；
- 条件更新：保证同一具体座位只有一个请求能抢到；
- 乐观锁：防止库存、订单状态发生丢失更新；
- 唯一索引：作为重复请求、重复有效订单的最终数据库防线；
- Redis：会话、缓存和原子轮换，不是订单最终事实；
- RabbitMQ：异步解耦和最终一致性，不等于数据库强事务；
- Outbox：解决数据库提交与消息发送的双写一致性；
- 消费幂等：处理 RabbitMQ 至少一次投递；
- 定时补偿：修复遗漏消息或消费者长期失败造成的状态滞后。

## 九、项目文档

开始工作前阅读：

```text
docs/01-requirements.md      需求、验收标准、面试口径约束
docs/02-domain-model.md      限界上下文、聚合、状态机和一致性边界
docs/03-database-design.md   表结构、防超卖、事务、索引和 Outbox
docs/04-api-design.md        HTTP 契约、幂等键、错误码和权限
docs/05-project-skeleton.md  工程骨架和模块化单体决策
```

这些文档有些章节早于当前代码，可能出现“尚未实现”的历史描述。遇到冲突时优先级为：

```text
当前代码和测试
> 最新 Flyway 迁移
> Git 提交记录
> docs 设计文档
> 最初截图/长图和历史压缩包
```

如果发现文档已经落后，应指出差异并建议更新，不能直接按旧文档判断完成度。

## 十、测试与证据

最近一次完整回归记录为：

```text
262 tests
0 failures
0 errors
36 skipped
```

跳过项主要是当前机器 Docker/Testcontainers 环境不可用时跳过的集成测试，不代表这些集成场景已经在真实 Docker 环境验证通过。

任何性能数字和并发结论必须先执行可复现测试。不能直接把需求文档中的“1000 用户抢 50 座位”写成已经达到的成绩。

## 十一、下一步实施计划

### 下一步立即做：Booking HTTP 层闭环

目标是让用户可以通过真实 HTTP 请求完成“携带 JWT 创建订单”。建议顺序：

1. 创建 `BookingController`；
2. 创建 `CreateBookingRequest` 和 `CreateBookingResponse`；
3. 从 JWT `sub`/认证上下文取得 `userId`，绝不相信请求体传来的用户 ID；
4. 从 `Idempotency-Key` 请求头取得 `requestNumber`；
5. 请求体只接收 `tripId` 和 `seatNumber`；
6. 调用现有 `BookingApplicationService.createBooking()`；
7. 将业务异常映射为稳定错误码和 HTTP 状态；
8. 编写 Controller 单元测试和 MockMvc 安全测试；
9. 使用 Swagger/OpenAPI 验证请求契约。

建议接口：

```http
POST /api/v1/bookings
Authorization: Bearer <access-token>
Idempotency-Key: <client-generated-request-id>
Content-Type: application/json

{
  "tripId": 2001,
  "seatNumber": "A01"
}
```

### 第二阶段：补齐学生订单 HTTP 功能

- 查询我的订单；
- 查询订单详情，并校验订单所属用户；
- 主动取消待支付订单；
- 取消时复用与超时取消一致的资源释放规则；
- 查询班次座位图；
- 补充 Repository 查询和对应测试。

### 第三阶段：真实基础设施验收

- 修好 Docker/Testcontainers 环境；
- 运行 MySQL、Redis、RabbitMQ 集成测试；
- 验证 Outbox 真实发布、TTL/DLX、手动 ACK 和 DLQ；
- 验证支付与超时取消并发；
- 验证事务失败时座位、库存、订单一起回滚；
- 形成可复现测试命令和测试报告。

### 第四阶段：管理员端和完整演示

- 车辆、路线、班次管理接口；
- 发布班次时初始化具体座位和汇总库存；
- 管理员权限；
- Swagger 端到端演示；
- 更新 README 和架构图。

### 第五阶段：压测与可观测性

- 使用固定数据集进行并发抢座测试；
- 输出成功数、冲突数、P95/P99、重复座位数、负库存检查；
- 增加 Micrometer 指标和关键消息监控；
- 对 Outbox 积压、DLQ、缓存命中率、乐观锁冲突做可观测性设计。

### 第六阶段：再拆 Spring Cloud 微服务

只有前述业务闭环和测试稳定后，再按照 IAM、Transport、Booking、Payment 拆分：

- Spring Cloud Gateway；
- Nacos 注册/配置；
- OpenFeign 或 HTTP API 替换本地 Gateway Adapter；
- Spring Cloud LoadBalancer；
- Sentinel 限流、熔断和降级；
- 每个服务独立数据库；
- 通过 Outbox/RabbitMQ 处理跨服务最终一致性；
- 增加契约测试和故障演练。

拆分时不能把当前本地 MySQL 事务直接描述成分布式事务。必须重新定义服务间一致性、失败重试、补偿和消息幂等策略。

## 十二、简历当前建议写法

在尚未拆微服务前，推荐使用真实、可防守的标题：

```text
Spring Boot 校园班车预约与支付平台（可演进微服务架构）
```

技术栈可以写：

```text
Java 21、Spring Boot、Spring Security、JWT、MyBatis、MySQL、Redis、RabbitMQ、Flyway、Testcontainers
```

可以写的项目亮点：

1. 采用模块化单体和端口/适配器设计划分 IAM、班次、预约、支付边界，为后续微服务拆分保留稳定接口。
2. 基于 Spring Security + JWT 实现无状态认证，使用 Redis 保存 Refresh Token 哈希和登录会话，并通过 Lua 完成原子轮换与登出失效。
3. 使用 Redis List 缓存可预约班次，使用 Lua 原子重建列表，并通过 Spring 定时任务和乐观锁更新班次状态。
4. 下单事务内完成具体座位条件锁定、汇总库存乐观锁扣减和订单持久化，结合唯一索引、幂等请求号与有限重试防止并发超卖。
5. 使用 Transactional Outbox、RabbitMQ Publisher Confirm、手动 ACK、消费幂等、重试队列和死信队列实现支付/退款消息最终一致性。
6. 使用 RabbitMQ TTL + DLX 实现未支付订单到期取消，并通过数据库定时扫描补偿遗漏消息，保证座位和库存最终释放。

不能写：

- “已经拆成四个 Spring Cloud 服务”；
- “已经使用 Nacos、Sentinel、Ribbon”；
- “接入真实支付”；
- 没有报告支撑的 QPS、P95、并发用户数或性能提升百分比。

## 十三、面试回答模板

### 1. 为什么先做模块化单体，不直接做微服务？

回答：

> 我先围绕 IAM、Transport、Booking 和 Payment 划分业务边界，但部署形态选择模块化单体。这样可以先用本地事务验证锁座、扣库存和插订单的一致性，并通过自动化测试证明不会超卖。模块间通过端口和适配器交互，未来拆服务时可将本地 Adapter 替换成 OpenFeign 或消息调用。业务闭环稳定后再引入服务注册、网关、限流和跨服务最终一致性，避免为了组件而拆分。

### 2. 如何防止同一座位被两个人抢到？

回答：

> 具体座位不是先查再改，而是执行带状态条件的 UPDATE，只有 `AVAILABLE` 才能改成 `LOCKED`，更新行数为 1 才算成功。汇总库存同时使用 version 乐观锁和 `available_seats > 0` 条件。锁座、扣库存和插订单在同一个事务中，失败整体回滚；唯一索引是最后的数据库防线。

### 3. `@Transactional` 能不能单独解决超卖？

回答：

> 不能。事务只能保证一组操作原子提交或回滚，默认隔离级别下两个事务仍可能同时读到旧库存。还需要条件更新、乐观锁或数据库锁来解决并发竞争。我的项目使用具体座位条件更新加汇总库存 version 乐观锁。

### 4. 为什么需要 Outbox？

回答：

> 直接在数据库事务里发送 MQ 会遇到双写问题：数据库回滚但消息已发出，或者数据库提交但消息发送失败。Outbox 将业务数据和待发送事件放在同一个 MySQL 事务中，提交后由 Relay 发布消息，并通过 Publisher Confirm 确认 Broker 接收，从而实现至少一次的可靠发布。

### 5. RabbitMQ 重复消息怎么办？

回答：

> RabbitMQ 是至少一次投递，消费者必须幂等。退款消息使用 `event_consumed` 唯一记录；订单超时消息结合订单状态、bookingId/bookingNumber 校验和乐观锁实现语义幂等。只有本地事务成功后才 ACK，失败进入重试或死信，并有数据库补偿任务兜底。

### 6. JWT 为什么还要 Redis？

回答：

> Access Token 仍由资源服务器本地验证，不是每次拿 JWT 查 Redis。Redis 保存的是可撤销登录会话和 Refresh Token 哈希，用于登出、轮换和会话过期。这样保留 Access Token 的无状态优势，又能控制长期会话。只存 Refresh Token 哈希是为了 Redis 泄露时不直接暴露可用凭证。

### 7. Redis List 为什么适合班次列表？

回答：

> 查询接口读取的是一个有序班次集合，List 可以按顺序保存序列并通过 LRANGE 整体读取。项目没有逐条修改，而是用 Lua 原子执行删除、重建和设置 TTL，避免重建过程中读到半份数据。数据库仍是最终事实，缓存失效或损坏时回源。

### 8. 支付和超时取消同时发生怎么办？

回答：

> 两个事务都要从 `PENDING_PAYMENT` 转移到不同终态，并使用订单 version 乐观锁。只有一个事务能成功更新。支付先成功，超时取消就不会再释放座位；超时先成功，支付回调会被拒绝或进入退款流程。订单、座位和库存相关修改处于各自完整事务中。

## 十四、Cursor 的工作规则

每次接到任务后必须：

1. 先执行 `git status --short`，不得覆盖用户已有修改；
2. 阅读相关接口、实现、MyBatis XML、Flyway 和测试，不能凭类名猜逻辑；
3. 先说明本次变更属于 domain、application、infrastructure 还是 api；
4. 解释为什么需要新增类以及放在该包的原因；
5. 优先复用已有领域行为和事务，不复制业务规则；
6. 外部层不能直接操作 DataObject 或绕过聚合规则；
7. 新增功能必须包含正常、异常、幂等或并发边界测试；
8. 修改后先跑针对性测试，再跑 `mvn test`；
9. 明确区分单元测试、集成测试和被跳过的 Testcontainers 测试；
10. 未经我明确要求，不提交、不推送 GitHub；
11. 讲解代码时按“业务目的 → 调用链 → 关键代码 → 失败场景 → 测试证据 → 面试回答”组织；
12. 如果发现设计有问题，要指出证据和权衡，不要为了迎合而直接同意。

## 十五、你现在的第一个任务

请先不要直接修改代码。先完成以下检查并向我汇报：

1. 检查上述本地路径，阅读转录 DOCX、简历 LaTeX/PDF、`pom.xml`、五份设计文档、Booking 领域/应用服务、SecurityConfig 和全局异常处理；
2. 列出当前 Booking HTTP 层缺少的类；
3. 给出 `POST /api/v1/bookings` 的具体实现计划；
4. 说明如何从 JWT 获取 userId、如何读取 `Idempotency-Key`、如何映射业务异常；
5. 列出要新增的测试用例；
6. 指出文档中与当前代码不一致的地方；
7. 等我确认计划后再开始写代码。

回答必须以当前仓库代码为证据，不能把最初截图里的微服务目标当作已经完成的事实。

---

# Sentinel 限流压测与验收

**当前状态：** 脚本已完成并通过静态验证，真实限流验收待运行应用后执行。

本文档说明如何使用 k6 对 `school-bus-platform` 中 Sentinel 资源级 QPS 限流进行可重复验收。

## 1. 测试目标

验证以下三个 HTTP 入口在启用 Sentinel 后，超过配置 QPS 阈值时统一返回：

- HTTP 429
- JSON `code`: `RATE_LIMITED`

| 接口 | Sentinel 资源名 |
|------|----------------|
| `POST /api/v1/auth/login` | `http:POST:/api/v1/auth/login` |
| `POST /api/v1/bookings` | `http:POST:/api/v1/bookings` |
| `POST /api/v1/payments/callback` | `http:POST:/api/v1/payments/callback` |

压测脚本路径：

- `scripts/load-test/sentinel-rate-limit.js`
- `scripts/load-test/run-sentinel-rate-limit.ps1`

## 2. 为什么使用 constant-arrival-rate

限流规则按 **QPS（每秒请求数）** 配置。若只用固定 VU 数无限循环，实际到达率会随响应延迟波动，难以和阈值对齐。

k6 的 `constant-arrival-rate` executor 按指定 **rate / timeUnit** 发请求，例如：

- 基线阶段：2 req/s，持续 15s
- 超载阶段：20 req/s，持续 20s

这样可以明确区分「低于阈值」与「高于阈值」，并观察 429 是否在超载阶段出现、基线阶段是否几乎不出现。

## 3. 启动应用并启用 Sentinel

Sentinel **默认关闭**。压测前需在**启动应用的同一终端**设置环境变量：

```powershell
$env:JAVA_HOME='E:\jdk-21_windows-x64_bin\jdk-21.0.6'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

$env:SENTINEL_RATE_LIMIT_ENABLED='true'
$env:SENTINEL_LOGIN_QPS='5'
$env:SENTINEL_CREATE_BOOKING_QPS='5'
$env:SENTINEL_PAYMENT_CALLBACK_QPS='10'

cd E:\HS1\projects\school-bus-platform
mvn spring-boot:run
```

对应 `application.yml` 配置项：

```yaml
school-bus:
  rate-limit:
    enabled: ${SENTINEL_RATE_LIMIT_ENABLED:false}
    login-qps: ${SENTINEL_LOGIN_QPS:10}
    create-booking-qps: ${SENTINEL_CREATE_BOOKING_QPS:30}
    payment-callback-qps: ${SENTINEL_PAYMENT_CALLBACK_QPS:100}
```

验收建议使用较低阈值（5 / 5 / 10），便于在本地快速看到 429。

基础设施（MySQL、Redis、RabbitMQ）需先启动，参见 [README.md](../README.md)。

## 4. 场景环境变量

脚本**不会**硬编码密码、JWT 或支付密钥。缺少变量时会报错并退出。

### 场景一：登录

| 变量 | 说明 |
|------|------|
| `TEST_STUDENT_NUMBER` | 已注册学号 |
| `TEST_PASSWORD` | 对应密码 |

可通过 `scripts/swagger-e2e-demo.ps1` 注册账号后，将输出的学号/密码导出为环境变量。

基线速率由脚本根据 `SENTINEL_LOGIN_QPS` 自动计算（约为阈值的 60%，且严格低于阈值）。超载速率由 `-Rate` 指定（建议 20）。

**响应分类：**

- `200` + `code=OK` → 登录成功
- `401` → 凭据错误（业务错误，**不是** Sentinel）
- `429` + `code=RATE_LIMITED` → Sentinel 命中

### 场景二：创建订单

| 变量 | 说明 |
|------|------|
| `TEST_ACCESS_TOKEN` | 学生 JWT（`Authorization: Bearer`） |
| `TEST_TRIP_NUMBER` | 可预约班次编号（UUID 字符串，如 demo 种子 `00000000-0000-4000-8000-000000009003`） |
| `TEST_SEAT_NUMBER` | 座位号（如 `A01`） |

每次请求生成新的 `Idempotency-Key`，避免幂等 replay 干扰统计。

**响应分类：**

- `201` → 下单成功
- `409` → 座位冲突等业务冲突（**不是**压测失败）
- `429` + `RATE_LIMITED` → Sentinel 命中

多请求竞争同一座位时，409 属于预期业务行为。

### 场景三：支付回调（限流层验收）

| 变量 | 说明 |
|------|------|
| `TEST_PAYMENT_CALLBACK_SECRET` | 与应用 `PAYMENT_CALLBACK_SECRET` 一致 |
| `TEST_PAYMENT_NUMBER` | UUID 格式支付号 |
| `TEST_BOOKING_NUMBER` | UUID 格式订单号 |
| `TEST_PAYMENT_AMOUNT` | 金额数字，如 `5.50` |

**签名方式（与 `HmacSha256PaymentCallbackVerifier` 一致）：**

- 算法：HMAC-SHA256
- 请求头：`X-Payment-Signature`
- 格式：`sha256=` + 原始 JSON 请求体的十六进制摘要
- 签名输入：**原始 JSON 字符串**（UTF-8），不是解析后再序列化

每次请求使用不同的 `requestNumber`（脚本自动生成），避免支付幂等键冲突。

**重要说明：** 本场景可使用**结构合法但业务不存在**的订单号，专门验收限流层：

- `401` → 签名无效
- `404` → 订单不存在（`PAYMENT_BOOKING_NOT_FOUND`）
- `409` → 支付状态冲突等
- `429` + `RATE_LIMITED` → Sentinel 命中

这不是支付业务成功率压测，只验证「超载时 Sentinel 是否先于/独立于业务层返回 429」。

## 5. 执行方式

### 5.1 仅校验前置条件（不跑 k6）

```powershell
$env:TEST_STUDENT_NUMBER='...'
$env:TEST_PASSWORD='...'

.\scripts\load-test\run-sentinel-rate-limit.ps1 `
    -Scenario login `
    -ValidateOnly
```

### 5.2 本地 k6

安装 k6：https://grafana.com/docs/k6/latest/set-up/install-k6/

```powershell
$env:SENTINEL_RATE_LIMIT_ENABLED='true'
$env:TEST_STUDENT_NUMBER='S1234567'
$env:TEST_PASSWORD='YourPassword'

.\scripts\load-test\run-sentinel-rate-limit.ps1 `
    -Scenario login `
    -BaseUrl http://localhost:8080 `
    -Duration 20s `
    -Rate 20
```

### 5.3 Docker k6（无本地 k6 时）

脚本自动检测 Docker，使用 `grafana/k6` 镜像。Windows 下会将 `localhost` 替换为 `host.docker.internal`。

```powershell
.\scripts\load-test\run-sentinel-rate-limit.ps1 -Scenario all -Rate 20
```

### 5.4 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `-Scenario` | `all` | `login` / `booking` / `payment` / `all` |
| `-BaseUrl` | `http://localhost:8080` | 应用根地址 |
| `-Duration` | `20s` | 超载阶段持续时间 |
| `-Rate` | `20` | 超载阶段目标 req/s（**必须严格大于**当前场景的 Sentinel QPS 阈值，否则脚本直接退出） |
| `-BaselineDuration` | `15s` | 基线阶段持续时间 |
| `-ValidateOnly` | — | 只检查 health / Sentinel / 环境变量 |
| `-SkipSentinelCheck` | — | 跳过 Sentinel 启用检查（不推荐） |

脚本启动时会检查：

1. `GET /actuator/health` 为 `UP`
2. `SENTINEL_RATE_LIMIT_ENABLED=true`
3. 当前场景所需环境变量齐全

## 6. 如何判断限流成功

k6 thresholds（见 `sentinel-rate-limit.js`）：

| 阶段 | 期望 |
|------|------|
| baseline | `http_429_rate_limited{scenario:baseline}` count == 0 |
| overload | `http_429_rate_limited{scenario:overload}` count > 0 |
| 全程 | `unexpected_responses` count == 0 |

429 判定必须同时满足 HTTP 429 且 JSON `code=RATE_LIMITED`。

## 7. 业务错误与 Sentinel 429 的区分

| HTTP | 含义 | 计入指标 |
|------|------|----------|
| 200/201 | 业务成功 | `http_2xx` |
| 401 | 鉴权/签名失败 | `http_401` |
| 404/422 等 | 业务/校验错误 | `http_4xx_business` |
| 409 | 业务冲突 | `http_409` |
| 429 + RATE_LIMITED | Sentinel 限流 | `http_429_rate_limited` |

不要把 401、409 当作压测失败。

## 8. 保存压测结果

每次场景导出 JSON summary：

```text
scripts/load-test/results/sentinel-<scenario>-<timestamp>-summary.json
```

## 9. 当前实现的限制

- 单应用实例本地 QPS 流控，非集群总量控制
- 无按 IP、学号、userId 的热点参数限流
- 无 Sentinel Dashboard
- 无 Nacos 动态持久化规则
- 无 Spring Cloud Gateway 层限流
- 本地压测结果不能等同于生产容量结论
- 限流不能替代乐观锁、条件更新、唯一索引和接口幂等

## 10. 支付回调签名参考

与 `scripts/swagger-e2e-demo.ps1` 相同；密钥从 `TEST_PAYMENT_CALLBACK_SECRET` 读取，勿写入仓库。

---

## 验收结果模板

**说明：** 以下模板用于后续重复验收；首次登录场景的真实结果记录在下一节。

```text
Sentinel 限流验收：
- 接口：
- 配置阈值：
- 实际发送速率：
- 测试持续时间：
- 总请求数：
- 正常/业务响应数：
- HTTP 429 数：
- 429 比例：
- P95：
- P99：
- 结论：
```

## 11. 真实验收记录（2026-08-15）

本次在 Windows + Docker Desktop 环境中运行单实例应用，使用 Docker
`grafana/k6` 对登录资源完成真实验收。应用启动参数为：

```text
SENTINEL_RATE_LIMIT_ENABLED=true
SENTINEL_LOGIN_QPS=5
```

验收结果：

```text
Sentinel 限流验收：
- 接口：POST /api/v1/auth/login
- 配置阈值：5 QPS
- 基线速率：3 req/s，持续 15s
- 超载速率：20 req/s，持续 20s
- 总请求数：447
- HTTP 2xx：147
- HTTP 429：300
- 基线阶段 HTTP 429：0
- 超载阶段 HTTP 429：300
- 429 比例：67.11%
- unexpected responses：0
- P95：92.23 ms
- P99：98.63 ms
- 结论：基线阶段无误限流；超载阶段稳定产生 RATE_LIMITED，5 QPS 规则生效
```

原始结果文件保存在本地（已被 `.gitignore` 排除）：

```text
scripts/load-test/results/sentinel-login-20260815-002838-summary.json
```

该结果只证明本机单实例 Sentinel 规则正确生效，不代表生产环境容量，也不能
外推为多实例集群的总 QPS 上限。

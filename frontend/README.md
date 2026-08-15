# 校园班车预约平台 · 学生端前端

第一版学生端 Web 前端，对接现有 Spring Boot 后端 API，实现注册、登录、查班次、选座下单、我的订单与取消待支付订单。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router + Pinia
- Axios
- Element Plus
- Vitest + Vue Test Utils
- ESLint + Prettier

## 目录结构

```text
frontend/
  src/
    api/          HTTP 客户端与领域 API
    components/   布局与通用组件
    router/       路由与登录守卫
    stores/       Pinia 状态（auth）
    types/        API 与业务类型
    utils/        日期、金额、幂等键
    views/        页面
```

## 安装与启动

前置条件：

1. Node.js **18+**
2. 后端运行在 `http://localhost:8080`

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问：`http://localhost:5173`

开发环境通过 Vite 代理转发：

```text
/api → http://localhost:8080
```

前端 Axios `baseURL` 固定为 `/api/v1`，无需修改后端 CORS。

## HTTP 资源 ID 与前端类型

学生端班次对外标识为 **`tripNumber`（UUID 字符串）**，不是内部 Snowflake `tripId`：

- 列表项、座位图、下单请求/响应与订单详情均使用 `tripNumber`
- 路由为 `/trips/:tripNumber/seats`
- Snowflake `tripId` 仅用于数据库与模块内部，**不出现在学生 API / 前端学生 DTO**

仍以 JSON string 传输的 64 位资源 ID：

- `userId`、`bookingId`、`vehicleId`、`routeId`

数据库与 Java 领域层仍使用 `BIGINT` / `long`。`version`、分页计数、`amount` 等保持 JSON number / TypeScript `number`。

原因是 JavaScript `Number` 只有 53 位安全整数；Snowflake ID 以 number 传输会丢失末几位。字符串化用于精度保护，不是加密。

约束：

- 不要对 `tripNumber` / `bookingId` 等使用 `Number(...)` / `parseInt(...)` / `BigInt(...)`
- ID 与 `tripNumber` 只作为不透明标识做字符串相等比较
- 座位图路由：`/trips/:tripNumber/seats`

## 后端启动

在项目根目录：

```powershell
$env:JAVA_HOME='E:\jdk-21_windows-x64_bin\jdk-21.0.6'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd E:\HS1\projects\school-bus-platform
mvn spring-boot:run
```

## Token 策略与安全权衡

| Token | 存储位置 |
|-------|----------|
| accessToken | Pinia 内存 |
| refreshToken | `localStorage`（键：`school-bus.refreshToken`） |

为保证刷新页面后仍能展示当前账号，非敏感的学号另存为
`localStorage` 的 `school-bus.studentNumber`；登出时与 refreshToken 一并清除。

页面刷新后，如存在 `refreshToken`，会先调用 `POST /api/v1/auth/refresh` 恢复登录，再访问受保护页面。

Axios 拦截器在 **401** 时只允许自动刷新 **一次**；并发 401 共享同一个 refresh Promise。刷新失败会清理本地状态并跳转 `/login`。刷新请求使用**独立 Axios 实例**，避免无限重试循环。

> **安全说明：** 将 refreshToken 放在 localStorage 是当前后端在 JSON 响应体返回 refresh token 条件下的工程折中，存在 XSS 风险。正式生产环境更推荐使用 Secure、HttpOnly、SameSite Cookie 承载 refresh token。

## 当前支持的业务流程

1. 注册 / 登录 / 登出
2. 登录态恢复与 Token 刷新
3. 可预约班次列表
4. 班次座位图（AVAILABLE / LOCKED / SOLD）
5. 选座创建订单（带 Idempotency-Key）
6. 我的订单列表（状态筛选 + 分页）
7. 订单详情、待支付剩余时间展示
8. 待支付订单主动取消

## 当前不支持

- 管理员后台
- 车辆 / 路线 / 班次管理
- 浏览器端模拟支付回调
- WebSocket 实时推送
- 订单列表 Redis 缓存

## 为什么不能在前端模拟支付

支付回调需要 HMAC 密钥（`PAYMENT_CALLBACK_SECRET`）对原始 JSON 请求体签名。该密钥只能保存在服务端，**不能**写入前端或浏览器存储。因此前端仅展示订单支付状态，并提示通过后端演示脚本 `scripts/swagger-e2e-demo.ps1` 或支付回调接口完成本地演示支付。

## 后续接入 API Gateway

只需修改 Axios `baseURL`，例如：

```typescript
export const API_BASE_URL = 'https://gateway.example.com/api/v1';
```

或通过环境变量：

```typescript
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';
```

生产构建时设置 `VITE_API_BASE_URL` 指向 Gateway 对外地址即可；Vite 开发代理仍可用于本地联调。

## 常用命令

```powershell
npm run dev
npm run lint
npm run typecheck
npm run test -- --run
npm run build
```

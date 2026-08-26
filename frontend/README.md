# 校园班车预约平台 · 学生端前端

第一版学生端 Web 前端，对接现有 Spring Boot 后端 API，实现注册、登录、查班次、选座下单、我的订单与取消待支付订单。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router + Pinia
- Axios
- oidc-client-ts（OpenID Connect / Authorization Code + PKCE）
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

浏览器访问：`http://127.0.0.1:5173`

> SSO 登录回调地址注册为 `http://127.0.0.1:5173/auth/callback`，退出回调
> 地址注册为 `http://127.0.0.1:5173/auth/logout/callback`。不要从
> `http://localhost:5173` 发起登录，否则浏览器会把两者视为不同来源，
> 回调页无法读取发起登录时保存在 `sessionStorage` 的 state 和 PKCE 数据。

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

## SSO 登录（推荐路径）

登录页的“使用校园统一身份认证”按钮通过 `oidc-client-ts` 发起
Authorization Code + PKCE 流程：

```text
学生端 → IAM /oauth2/authorize → 登录 → 携带 code 回调
      → 校验 state/nonce → code + verifier 换取 Token → 访问 Gateway
```

`oidc-client-ts` 负责生成和校验 state、nonce、PKCE verifier/challenge，
并读取 Discovery 与 JWK 元数据。回调路由为 `/auth/callback`，回调成功后
恢复原始业务页面；外部 return URL 会被拒绝，避免开放重定向。

本地联调时 IAM 的 Issuer 必须与前端 Authority 完全一致。默认前端
Authority 是 `http://localhost:8084`，因此应使用同一值启动 IAM 及资源服务：

```powershell
$env:JWT_ISSUER='http://localhost:8084'
$env:SSO_STUDENT_ORIGIN='http://127.0.0.1:5173'
```

也可以通过以下 Vite 环境变量覆盖：

```text
VITE_OIDC_AUTHORITY
VITE_OIDC_CLIENT_ID
VITE_OIDC_REDIRECT_URI
VITE_OIDC_POST_LOGOUT_REDIRECT_URI
```

## Token 策略与安全权衡

| 登录模式 | Access Token | Refresh Token |
|----------|--------------|---------------|
| SSO / PKCE | Pinia + OIDC `sessionStorage` | 公共 SPA 不签发 |
| 旧 JSON 登录 | Pinia 内存 | `localStorage`（`school-bus.refreshToken`） |

SSO 页面刷新时从 OIDC `sessionStorage` 恢复仍有效的会话。Access Token
到期后清理当前应用会话并重新进入授权流程；由于 IAM 浏览器会话仍存在，
通常可以无感重新认证，但这不是 Refresh Token 轮换。

为保证刷新页面后仍能展示当前账号，非敏感的学号另存为
`localStorage` 的 `school-bus.studentNumber`；登出时与 refreshToken 一并清除。

页面刷新后，如存在 `refreshToken`，会先调用 `POST /api/v1/auth/refresh` 恢复登录，再访问受保护页面。

Axios 拦截器在 **401** 时只允许自动刷新 **一次**；并发 401 共享同一个 refresh Promise。刷新失败会清理本地状态并跳转 `/login`。刷新请求使用**独立 Axios 实例**，避免无限重试循环。

> **安全说明：** sessionStorage 与 localStorage 中的 Token 都无法抵御成功的
> XSS。旧登录将 refreshToken 放在 localStorage 是迁移期折中；正式生产环境
> 更推荐使用可信 BFF，并由 Secure、HttpOnly、SameSite Cookie 承载服务端会话。

SSO “退出统一认证”通过 Discovery 中的 `end_session_endpoint` 发起
RP-Initiated Logout。`oidc-client-ts` 携带 `id_token_hint`、已登记的
`post_logout_redirect_uri` 与随机 `state`，IAM 结束浏览器认证会话后回调前端，
前端校验退出状态并清除本地会话。该流程不会主动撤销已经签发给其他应用的
Access Token；跨应用 Token 撤销、Back-Channel Logout 和管理端联调仍属于后续阶段。

## 当前支持的业务流程

1. 校园统一身份认证登录与旧版账号登录回退
2. OIDC 回调、state/nonce/PKCE 校验与本地退出
3. 登录态恢复；旧登录支持 Token 刷新
4. 可预约班次列表
5. 班次座位图（AVAILABLE / LOCKED / SOLD）
6. 选座创建订单（带 Idempotency-Key）
7. 我的订单列表（状态筛选 + 分页）
8. 订单详情、待支付剩余时间展示
9. 待支付订单主动取消

## 当前不支持

- 管理员后台
- 管理端 SSO 前端闭环
- RP-Initiated Logout / 跨系统统一登出
- SSO 公共客户端 Refresh Token（当前明确不签发）
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

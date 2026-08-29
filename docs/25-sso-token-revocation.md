# SSO Access Token Revocation

## 目标

学生端和管理端共享 IAM 浏览器登录会话，但各自持有独立 JWT。仅执行 RP-Initiated Logout 只能结束浏览器 Session，已经签发的 Access Token 在自然过期前仍可能访问 API。本阶段补齐“任一端登出，旧 Token 跨应用立即失效”。

## 设计

```text
Student/Admin SPA
  -> POST /api/v1/auth/logout (Bearer old token)
  -> IAM 写 Redis revoked-before:{sub}=logoutEpochMillis
  -> IAM 删除该用户全部 Legacy Refresh Session
  -> SPA 再执行 OIDC RP-Initiated Logout

后续 API 请求
  -> Gateway 验证 JWT 签名、issuer、audience、exp
  -> Gateway 读取 Redis 用户撤销水位
  -> token.iat_ms <= revokedBefore ? 401 : 转发下游
```

撤销键使用 `schoolbus:auth:revoked-before:{subject}`，TTL 为 Access Token 生命周期加一分钟时钟偏差。重复登出通过 Lua 只允许水位向前推进，避免多实例时钟轻微漂移把撤销时间覆盖为更早值。

## 为什么增加 `iat_ms`

JWT 标准 `iat` 通常只有秒级精度。如果用户在同一秒内完成“登出 → 重新登录”，只比较 `iat` 可能把新 Token 一并判定为旧 Token。IAM 因此在 Legacy JWT 和 Authorization Server JWT 中同时写入毫秒级 `iat_ms`；Gateway 对历史 Token 仍兼容回退到标准 `iat`。

## 边界与故障策略

- Gateway 是统一撤销检查点，下游服务继续验证 JWT 签名和角色，形成纵深防御。
- 生产环境必须限制独立服务端口只允许 Gateway/内网访问；直连服务不会执行 Gateway 的 Redis 水位检查。
- Redis 查询默认 fail-closed：无法确认撤销状态时返回 `503 TOKEN_REVOCATION_UNAVAILABLE`，避免基础设施故障让已登出的 Token 重新生效。
- 无 Bearer Token 的登录、支付 HMAC 回调等请求保持原路由语义。
- Redis 仅保存短期撤销水位，不保存原始 Access Token。

## 配置

Gateway 需要与各资源服务相同的 JWT 公钥，并连接 IAM 使用的 Redis：

```yaml
school-bus:
  security:
    jwt:
      issuer: https://school-bus.local
      audience: school-bus-api
      public-key-location: file:/path/to/public.pem
  gateway:
    token-revocation:
      enabled: true
      fail-closed: true
      key-prefix: "schoolbus:auth:revoked-before:"
```

## 自动化验证

- IAM：Legacy/SAS Token 均包含 `iat_ms`；有无 legacy `sid` 均可登出；登出写撤销水位并按需删除 Refresh Session。
- Gateway：旧 Token 返回 401、新 Token继续转发、非法签名不查 Redis、Redis 故障 fail-closed 返回 503、无 Bearer 请求不受影响。
- Frontend：学生端和管理端均先调用 IAM 撤销 API，再进入 Provider Logout。

2026-08-29 已在真实 Gateway、IAM、Nacos、MySQL 和 Redis 上完成双会话验收，报告位于 `target/sso-token-revocation-report.json`：

| 验证项 | 结果 |
| --- | --- |
| 登出前两个 Token | 200 / 200 |
| Redis 用户 Session 索引 | 2 |
| 任一 Token 登出 | 200 |
| 登出后两个旧 Token | 401 / 401 |
| 第二个会话的旧 Refresh Token | 401 |
| 登出后用户 Session 索引 | 0 |
| 重新登录的新 Token | 200 |
| 使用旧 Token 重复登出 | 200 |

验收产生的临时账户、角色、Refresh Session 与撤销水位均已清理，基础设施容器保留运行。

当前阶段保持 JWT 短生命周期（15 分钟）。用户级水位适合“退出所有客户端”的语义；如果未来需要只退出单一设备，可进一步按 `sid` 保存会话级撤销记录。

## 面试表述

> JWT 无状态认证的缺点是服务端无法天然让已签发 Token 立即失效。我没有把完整 Token 存进 Redis，而是按用户保存一个短期 revoked-before 水位。IAM 登出时推进水位，Gateway 验签后比较 Token 的毫秒级签发时间，旧 Token 返回 401，新登录 Token 正常通过。这样 Redis 数据量与用户数而不是 Token 数相关，并把撤销逻辑集中在统一入口；代价是每个携带 JWT 的请求增加一次 Redis 查询，因此需要明确 fail-closed 策略、限制服务直连，并保留短 Token TTL 作为最终收敛边界。

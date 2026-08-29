# Admin OIDC Client and Operations Console

## 1. 阶段目标

本阶段将管理端作为第二个 OIDC 客户端接入已有 IAM，形成学生端和管理端共享认证中心的 SSO 架构，并提供车辆、路线、班次的最小运营界面。

```text
Student SPA (:5173) ─┐
                     ├─ Authorization Code + PKCE ─ IAM (:8084)
Admin SPA (:5174) ───┘
          │ Bearer JWT
          └─ Gateway (:8080) ─ Core admin APIs
```

SSO 的含义不是两个前端共用同一个 Access Token。浏览器复用 IAM 的登录 Cookie，因此第二个客户端发起授权时通常不需要再次输入密码；两个客户端仍分别完成授权码交换并得到面向自身的 Token。

## 2. 安全的管理员身份准备

公开注册接口始终只创建 `STUDENT`。项目没有增加“注册管理员”HTTP 接口，而是在 IAM 中增加默认关闭的启动引导：

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED='true'
$env:ADMIN_BOOTSTRAP_STUDENT_NUMBER='S4789503'
```

启动时：

1. 使用领域值对象规范化学号；
2. 查询已存在账户并要求状态为 `ACTIVE`；
3. 向 `iam_account_role` 写入 `ADMIN`；
4. 使用唯一键和 `ON DUPLICATE KEY UPDATE` 保证重复启动幂等；
5. 日志不输出学号。

角色创建后应立即关闭该配置并重启 IAM。生产环境应进一步改为受审计的运维命令、IAM 管理 API 或身份治理系统，而不是长期启用启动引导。

## 3. OIDC 与权限边界

`school-bus-admin-web` 是无客户端密钥的公共客户端：

- `response_type=code`；
- 强制 PKCE S256；
- 回调地址为 `http://127.0.0.1:5174/auth/callback`；
- 登出回调为 `http://127.0.0.1:5174/auth/logout/callback`；
- OIDC 状态存放在 `sessionStorage`；
- 外部 `returnTo` 地址会被拒绝，避免开放重定向。

管理端会检查 `roles` 中是否包含 `ADMIN`，用于尽早给用户提示；该检查不能替代服务端鉴权。车辆、路线和班次接口继续由 Spring Security 的 `@PreAuthorize("hasRole('ADMIN')")` 保护，普通学生即使修改前端代码也只能得到 403。

## 4. 管理能力

| 页面 | 能力 |
| --- | --- |
| `/vehicles` | 查询车辆、创建车辆、启用/停用 |
| `/routes` | 查询路线、创建路线、启用/停用 |
| `/trips` | 查询班次、创建草稿、发布、取消 |

Snowflake ID 在 HTTP JSON 中保持字符串，前端不转换为 JavaScript `number`，避免超过 `Number.MAX_SAFE_INTEGER` 后精度丢失。

## 5. 验证状态

已自动验证：

- IAM 的学生端和管理端均为 PKCE 公共客户端；
- IAM Token 端点允许 `5173` 和 `5174` 两个受信任 Origin；
- 管理端回跳地址只接受站内路径；
- 无 `ADMIN` 角色的有效 Token 会被管理端拒绝；
- 管理端 lint、TypeScript 类型检查、单元测试及生产构建。
- IAM 集成测试使用同一个 `MockHttpSession` 先后请求学生端和管理端授权，两个客户端均直接获得不同授权码，证明认证中心会话可复用且不需要第二次提交凭证。

真实 Chrome 验收脚本：

```powershell
$env:TEST_STUDENT_NUMBER='S4789503'
$env:TEST_PASSWORD='your-password'
.\scripts\cloud\verify-admin-sso-browser.ps1
```

脚本的通过条件包括：两个客户端均授权、第二个客户端未出现密码页、Access Token 不同但 `sub` 相同、管理 Token 包含 `ADMIN`、统一登出后新的授权重新要求登录。它还明确记录旧学生页面中已经签发的 Token 仍然存在，避免把“结束 IAM Cookie”错误描述成“已经撤销所有 JWT”。

尚未宣称完成：

- Access Token 的实时跨应用撤销或 OIDC Back-Channel Logout。

2026-08-28 首次执行因 IAM 与 Gateway 未启动得到诚实的 `BLOCKED` 报告。基础设施恢复并统一 IAM、Core 与 Query 的本地 Issuer 后，再次执行生成 `target/admin-sso-browser-20260828-191245/report.json`，状态为 `PASSED`。真实 Chrome 已证明：学生端完成一次认证后，管理端授权不会再次出现凭证页；两个客户端的 Access Token 不同但 `sub` 相同；管理端 Token 包含 `ADMIN`；RP-Initiated Logout 后的新学生端授权会重新进入 IAM 登录页。旧学生页面中已签发的 JWT 仍会保留到过期，这不等同于 Token 撤销。

## 6. 面试说明

1. **为什么两个客户端不能共用 Token？** Access Token 是针对客户端和资源访问签发的凭证；SSO 复用的是认证中心会话，而不是浏览器应用之间复制 Token。
2. **为什么 SPA 使用 PKCE？** SPA 无法安全保存客户端密钥，PKCE 将授权码绑定到本次请求的 verifier，降低授权码被截获后的利用风险。
3. **前端判断 ADMIN 是否足够？** 不够。前端判断只改善体验，真正权限边界必须由资源服务器验证 JWT 并执行服务端方法授权。
4. **为什么不提供管理员注册接口？** 管理员授权是高风险权限变更，应受配置、审计或审批控制，不能与普通账户注册处于同一公开入口。
5. **统一登出和清理本地 Token 有什么区别？** 清理本地 Token 只退出当前应用；RP-Initiated Logout 还会结束 IAM 浏览器会话，避免打开另一个客户端时继续静默获得授权。

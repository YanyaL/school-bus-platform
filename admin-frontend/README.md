# School Bus Admin Frontend

独立的校园班车运营管理端，也是 `school-bus-admin-web` OIDC 公共客户端。它与学生端共享同一个 IAM 登录会话，但拥有独立的 Authorization Code + PKCE 流程和 Access Token。

The standalone operations console is registered as the `school-bus-admin-web` OIDC public client. It shares the IAM browser session with the student SPA while obtaining its own authorization code and access token.

## 当前能力

- OIDC Authorization Code + PKCE 登录；
- 强制校验 Access Token 中的 `ADMIN` 角色；
- RP-Initiated Logout 统一登出；
- 车辆创建及启停；
- 路线创建及启停；
- 班次草稿创建、发布与取消。

前端角色判断仅用于页面导航和用户提示，真正的安全边界仍是后端 `@PreAuthorize("hasRole('ADMIN')")`。

## 本地启动

先确保 Gateway、IAM 和 Core 已启动，并在 IAM 数据库中准备一个已有管理员账户。管理员角色不提供公开注册接口，而是通过一次性启动配置授予：

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED='true'
$env:ADMIN_BOOTSTRAP_STUDENT_NUMBER='S4789503'
mvn -f .\cloud\iam-service\pom.xml spring-boot:run
```

看到角色创建成功后停止 IAM，清除或关闭 `ADMIN_BOOTSTRAP_ENABLED`，再按正常配置重启。该过程可重复执行，数据库唯一键保证幂等。

启动前端：

```powershell
cd .\admin-frontend
npm install
npm run dev
```

浏览器访问 `http://127.0.0.1:5174`。默认 IAM 地址为 `http://localhost:8084`，API 请求通过 Vite 代理转发到 Gateway `http://localhost:8080`。

## 验证

```powershell
npm run lint
npm run typecheck
npm run test
npm run build
```

本阶段自动化测试覆盖 OIDC 客户端参数、回跳地址约束、Snowflake ID 字符串保真、管理员角色校验和统一登出调用。真实双应用浏览器免密跳转仍需在完整基础设施启动后验收。

完整基础设施启动后，可从项目根目录执行真实 Chrome 验收：

```powershell
$env:TEST_STUDENT_NUMBER='S4789503'
$env:TEST_PASSWORD='your-password'
.\scripts\cloud\verify-admin-sso-browser.ps1
```

脚本会按需启动两个 Vite 前端，使用同一个 Chrome BrowserContext 完成学生端登录、管理端免密授权和 RP-Initiated Logout，并输出不包含 Token 或密码的 JSON 报告。环境缺失时报告为 `BLOCKED`，浏览器断言失败时为 `FAILED`，所有证据成立时才是 `PASSED`。

2026-08-28 已在真实 IAM、Gateway、MySQL、Redis、Nacos 与 Chrome 环境完成首次 `PASSED` 验收。验收使用临时双角色账号，结束后已移除临时 `ADMIN` 角色。

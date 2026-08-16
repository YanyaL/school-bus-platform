# JWT 本地密钥卫生

公开仓库不得跟踪 `cloud/dev-keys/` 下的 PEM 文件。

## 本地生成

```powershell
powershell -NoProfile -File .\scripts\cloud\generate-local-jwt-keys.ps1
```

- Core：同时配置公钥与私钥位置
- Query：只配置公钥位置
- 密钥文件保持 gitignore，不得 `git add -f`

## 提交前扫描

```powershell
powershell -NoProfile -File .\scripts\security\check-no-private-keys.ps1
```

扫描范围仅限 **Git 已跟踪内容**；失败时只报告路径/类别，不打印密钥正文。

根 `.gitignore` 已包含 `cloud/dev-keys/`。若私钥曾进入公开历史，必须旋转密钥并视为永久泄露。

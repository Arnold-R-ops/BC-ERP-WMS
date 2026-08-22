# 安全配置说明（P0 修复后必读）

自 P0 安全修复起，以下三个敏感值**不再写在配置文件里**，应用启动时从环境变量读取，缺失即启动失败（故意设计，防止带默认弱凭证上线）。

## 必需的环境变量

| 环境变量 | 用途 | 说明 |
|----------|------|------|
| `DB_PASSWORD` | 数据库密码 | 无默认值。先执行 `docs/security/setup-db-user.sql` 换密码并创建低权限账号 |
| `DB_USERNAME` | 数据库账号 | 默认 `postgres`；执行上述脚本后应设为 `wms_app` |
| `DB_URL` | 数据库地址 | 有默认值（本机 wms_db），一般不用设 |
| `TENANT_JWT_SECRET` | 公司 JWT 签发密钥 | 无默认值。Base64 编码，至少 256 位 |
| `PLATFORM_JWT_SECRET` | 平台 JWT 签发密钥 | 无默认值。Base64 编码，至少 256 位，且必须与公司密钥不同 |
| `BATCH_SALT` | 批次码 Hashids 盐 | 无默认值。每个环境定一个值后**永不更换** |
| `ADMIN_INITIAL_PASSWORD` | admin 初始密码 | 可选。仅在 admin 账号不存在、首次创建时使用 |
| `SPRING_PROFILES_ACTIVE` | 运行环境 | 生产必须设为 `prod`（关闭 SQL 日志） |

## 本机开发环境一次性设置（Windows）

用 PowerShell 执行（`setx` 写入用户环境变量，对新开的终端生效）：

```powershell
setx DB_USERNAME "wms_app"
setx DB_PASSWORD "<你的数据库密码>"
setx TENANT_JWT_SECRET  "<使用下方命令生成的第一把密钥>"
setx PLATFORM_JWT_SECRET  "<重新运行下方命令生成的第二把密钥>"
setx BATCH_SALT  "<自定义一段随机字符串，定了就不再改>"
```

生成强 JWT 密钥（64 字节随机数的 Base64）：

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

## 注意事项

1. **JWT 密钥轮换的影响**：更换公司密钥只会使公司会话失效；更换平台密钥只会使平台会话失效。用户密码不受影响。
2. **BATCH_SALT 定死不换**：换盐不会破坏已有批次码的查询（查询是字符串等值匹配），但同一环境保持一个盐是正确做法。
3. **旧密码 `123465` 和旧 JWT 密钥已进 git 历史**，视同泄露——必须在数据库端换掉密码（脚本 Step 1），不能只挪位置。
4. **测试不需要这些环境变量**：`src/test/resources/` 下的测试配置自带测试值；若数据库改了密码，设置 `DB_PASSWORD` 环境变量即可，测试配置会优先读取它。
5. 生产部署再往上走时，按阶梯升级凭证管理：环境变量 → 云 Secrets Manager → 动态短效凭证 → IAM 无密码。

## 2026-08-10 发布前审计状态

历史凭证扫描已覆盖全部可达 Git 提交。2026-08-11 已完成数据库端密码轮换和 `wms_app` 切换：旧 `postgres` 密码被拒绝，两库及 public 对象归 `wms_app`，四项高权限标志均为 `false`，应用已使用新凭证重启。当前 `DB_PASSWORD` 及两类 JWT 密钥均不得匹配历史字面量，发布凭证门禁为 `PASS`。

脱敏扫描证据见 `docs/P2_CREDENTIAL_HISTORY_AUDIT_2026-08-10.md`，实际轮换证据与 DPAPI 恢复方式见 `docs/P2_DATABASE_CREDENTIAL_ROTATION_2026-08-11.md`。

# P2 本机数据库凭证轮换与低权限账号切换

执行日期：2026-08-11

## 结论

本机 PostgreSQL 的历史泄露密码已完成数据库端轮换，应用连接已从 `postgres` 超级用户切换为 `wms_app`。凭证历史门禁由 `BLOCKED` 变为 `PASS`。

整个过程没有输出、提交或记录任何密码明文；新凭证只存在于 Windows 当前用户环境和当前用户 DPAPI 加密恢复包。未修改库存、预留、采购、销售、仓库作业、IAM 或其他业务数据。

## 执行范围

- PostgreSQL：`localhost:5432`。
- 数据库：`wms_db`、`wms_db_test`。
- 轮换角色：`postgres`、`wms_app`。
- 应用：原 `wms-system-v449-codex-clean3.jar`，维护窗口内停止并使用新凭证重启。
- 未轮换：`JWT_SECRET` 已不同于历史值；`BATCH_SALT` 按既有规则保持不变。

## 安全执行记录

1. 轮换前确认两库均由 `postgres` 所有，`wms_app` 尚不存在。
2. 首次和第二次执行均因 psql 表格输出解析问题在首个数据库写操作前安全退出；两个未使用的 DPAPI 包均在确认无执行回执后精确删除。
3. 第三次执行完成密码轮换、角色创建和对象所有权转移，随后因 PostgreSQL 布尔文本返回 `false` 而脚本仍期望 `f`，在最终断言处停止。
4. 保留该次有效 DPAPI 恢复包，修正断言后仅在内存中解密新凭证，完成全部验证和 Windows 用户环境更新。
5. 旧 `postgres` 密码已验证无法登录；新管理员凭证和 `wms_app` 凭证均验证成功。
6. 后端使用 Windows 用户级 `DB_USERNAME=wms_app` 和新 `DB_PASSWORD` 重启成功。

前两次失败没有修改数据库；第三次的凭证和所有权修改已经完成，后续恢复步骤没有重新生成密码。

## 验收证据

| 核验项 | 结果 |
|---|---|
| 历史 `postgres` 密码 | 已拒绝认证 |
| `wms_app` 连接 `wms_db` | 通过 |
| `wms_app` 连接 `wms_db_test` | 通过 |
| `rolsuper` / `rolcreatedb` / `rolcreaterole` / `rolreplication` | 全部 `false` |
| 两个数据库所有者 | `wms_app` |
| 两个 public schema 所有者 | `wms_app` |
| 非 `wms_app` 所有的 public 表/序列/视图 | 0 |
| `wms_db` Flyway 最新成功迁移 | V4.49 |
| 测试库 DDL + INSERT + ROLLBACK 探针 | 通过，探针表无残留 |
| 后端 `/health/check` | `UP` |
| 实时 `/v3/api-docs` | HTTP 200 |
| 活跃 `wms_db` 会话 | 仅观察到 `wms_app` |
| 脱敏凭证历史门禁 | `PASS` |

## 恢复与保管

恢复包位于被 Git 忽略的 `backups/security/`，使用 Windows DPAPI `CurrentUser` 加密，只能由创建它的 Windows 用户解密。恢复包和无密回执均已确认命中 `.gitignore`，不会进入 Git 状态。

只检查恢复能力、不触碰剪贴板：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File src/test/scripts/copy-local-db-recovery-credential.ps1 -Credential postgres -CheckOnly
```

确需使用时，可把指定凭证复制到剪贴板；脚本不会打印凭证：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File src/test/scripts/copy-local-db-recovery-credential.ps1 -Credential postgres
```

使用后应立即清空剪贴板。不要把恢复包移动到其他用户或机器后期待仍可解密，也不要把新密码写入聊天、命令参数、文档或日志。

## 发布状态

数据库凭证阻塞项已经关闭，但本报告本身及上一项安全审计提交仍需推送并通过远程 CI。创建 P2 标签或部署仍属于单独的正式发布决策，未经明确批准不得执行。

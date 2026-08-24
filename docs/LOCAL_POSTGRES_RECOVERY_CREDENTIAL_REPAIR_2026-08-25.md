# 本机 PostgreSQL 恢复凭据修复记录

执行日期：2026-08-25

执行环境：Windows 本机开发环境，PostgreSQL 18.6，`127.0.0.1:5432`

任务性质：独立的本地数据库运维与灾难恢复修复，不属于平台管理员管理第二阶段功能

## 1. 结论

旧 DPAPI 恢复包中的 `postgres` 密码已失效，但同一恢复包中的 `wms_app` 密码仍可正常认证。现已只轮换 `postgres` 密码，生成并激活新的 CurrentUser DPAPI 恢复包；旧包保留并标记为已替代。

修复后独立复核通过：

- 新 `postgres` 凭据认证成功，角色仍为可登录超级用户，密码验证器为 `SCRAM-SHA-256`；
- 旧恢复包中的 `postgres` 凭据继续被拒绝；
- `wms_app` 密码、Windows 用户环境变量及五项危险权限均未改变；
- `wms_db` 的 69 条 Flyway 迁移全部成功，最新版本仍为 V4.72；
- `wms_db_test` 修复前后均没有 `flyway_schema_history`，该既有状态未被改变；
- 两库所有者、public schema 所有者、public 对象所有权和对象指纹均未改变；
- `pg_hba.conf` 等四个配置文件未改变，仍无 `trust` 认证规则；
- PostgreSQL 服务恢复为 `Running + Auto + NetworkService`，5432 正常监听；
- 一次性计划任务和临时 worker 目录均已清理，残留数量为 0。

整个过程没有显示、复制、记录或提交任何密码明文，没有修改平台管理员、租户账号、业务记录或 Flyway 历史。

## 2. 修复边界

本次唯一数据库写入是：

```sql
ALTER ROLE postgres PASSWORD '<仅存在于内存和加密恢复包中的随机值>';
```

明确未执行：

- `ALTER ROLE wms_app`；
- `ALTER DATABASE`、`ALTER SCHEMA` 或对象所有权变更；
- Flyway 或业务表 DML；
- `pg_hba.conf` 临时 `trust`；
- Windows 用户级 `DB_USERNAME`、`DB_PASSWORD` 更新；
- 冷备份自动覆盖或恢复。

## 3. 安全执行方式

新增两个受控脚本：

- `src/test/scripts/repair-local-postgres-recovery-credential.ps1`：默认只读 CHECK，只有显式 `-Execute` 才进入维护流程；
- `src/test/scripts/invoke-local-postgres-offline-password-worker.ps1`：只允许以 `NetworkService` SID 运行的内部离线 worker。

执行顺序：

1. 验证服务、监听进程、数据库、角色、所有权、Flyway、配置文件、活动连接和外部表空间；
2. 收紧恢复目录和 PostgreSQL 冷备份 ACL；
3. 优雅停止服务，确认 `postmaster.pid` 和关联 `postgres.exe` 均不存在；
4. 用 `pg_controldata` 确认集群为 clean shutdown；
5. 创建唯一且不覆盖的完整冷备份，并比较源/目标文件数和字节数；
6. 生成 36 字节随机值，先写入 `.dpapi.pending` CurrentUser 包并立即解密自检；
7. 通过仅 SYSTEM、Administrators、NetworkService 可访问的短期 LocalMachine DPAPI 载荷，把同一随机值交给一次性计划任务；
8. worker 以 `NetworkService` 运行 `postgres.exe --single`，SQL 只经 stdin 传递，命令行和文件中均无明文；
9. worker 完全退出并清理临时载荷后才原子发布无密结果；
10. 重启服务，完成全部前后断言后才把 pending 包原子激活为正式 `.dpapi`。

worker 设置 60 秒超时；任一失败路径都会先终止离线进程、清理计划任务并尝试恢复原服务。脚本不会自动用冷备份覆盖当前集群。

## 4. 恢复产物

以下产物位于被 Git 忽略的 `backups/`，不进入提交：

- 新活动恢复包：`backups/security/local-db-credentials-20260825T004710161.dpapi`；
- 无密执行回执：`backups/security/local-db-credentials-20260825T004710161.receipt.json`；
- 已替代但保留的旧包：`backups/security/local-db-credentials-20260811T003040.dpapi`；
- 修复前冷备：`backups/postgres-cluster-before-postgres-recovery-20260825T004710161`；
- 冷备校验：3105 个文件，174739385 字节，clean shutdown，无外部表空间。

`backups/security`、既有 PostgreSQL 冷备和新冷备的 Windows ACL 已限制为：

- 当前 Windows 用户；
- `BUILTIN\Administrators`；
- `NT AUTHORITY\SYSTEM`。

未向 `NetworkService` 授予冷备读取权限；它只在短期受限 worker 目录中读取一次性 LocalMachine DPAPI 载荷。

## 5. 后续使用与限制

只检查新包能否由当前 Windows 用户解密，不触碰剪贴板：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File src/test/scripts/copy-local-db-recovery-credential.ps1 -Credential postgres -CheckOnly
powershell -NoProfile -ExecutionPolicy Bypass -File src/test/scripts/copy-local-db-recovery-credential.ps1 -Credential wms_app -CheckOnly
```

需要再次处理“恢复包中的 `postgres` 密码已失效”时，先运行专用脚本的默认 CHECK，核对输出后再显式使用 `-Execute`。如果活动包中的 `postgres` 凭据仍可认证，脚本会拒绝执行，避免无意义轮换。

该 DPAPI 包仅用于这台开发机及当前 Windows 用户，不可复制为生产环境凭据。生产环境仍必须独立设计密钥管理、break-glass 双人控制、数据库网络隔离和备份恢复演练。

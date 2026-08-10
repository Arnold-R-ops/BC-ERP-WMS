# P2 历史凭证扫描与轮换确认

审计日期：2026-08-10

## 结论

代码仓库当前版本没有发现需要人工复核的高置信私钥、云访问密钥、GitHub/GitLab/Slack/NPM/Shopify/Stripe 令牌或内嵌 URL 账号口令；生产配置已经改为从环境变量读取敏感值。

2026-08-11 已完成数据库端轮换和 `wms_app` 切换，发布凭证门禁现为 **PASS**。当前持久化 Windows 用户环境中的 `DB_PASSWORD` 已不再匹配历史字面量，`DB_USERNAME=wms_app`；旧 `postgres` 密码已验证被数据库拒绝。

初始扫描阶段只做脱敏只读检查。后续经用户明确批准，在受控维护窗口完成了数据库角色密码、对象所有权、Windows 用户环境和应用进程切换；全过程没有显示任何凭证值，也没有修改业务数据。执行证据见 `docs/P2_DATABASE_CREDENTIAL_ROTATION_2026-08-11.md`。

## 扫描范围与方法

- 初始扫描覆盖 38 个可达提交、2460 个可达对象路径；安全审计提交后的轮换前基线复扫覆盖 39 个提交、2475 个对象路径。
- 检查 10 类高置信签名：私钥头、AWS、GitHub、GitLab、Slack、Stripe live、Shopify、NPM、JWT 形状和 URL 内嵌账号口令。
- 单独解析 `src/main/resources/application.yml` 的全部历史版本，检查数据库口令、JWT 密钥和批次盐的字面量。
- 仅在内存中比较当前环境值与历史字面量是否相同；不输出值、可逆编码或低熵值哈希。
- 检查敏感文件名历史和当前 Git 忽略规则。

可重复执行入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File src/test/scripts/invoke-credential-history-audit.ps1
```

发布门禁模式：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File src/test/scripts/invoke-credential-history-audit.ps1 -FailOnBlocker
```

门禁存在阻塞时返回退出码 `2`。脚本输出只包含状态、计数、变量名和位置元数据，不包含凭证值。

## 扫描结果

| 核验项 | 结果 | 结论 |
|---|---|---|
| 高置信签名人工复核位置 | 0 | 未发现真实私钥或已知格式的访问令牌 |
| JWT 形状示例位置 | 6 | 全部位于测试或历史示例，非真实凭证 |
| 敏感文件名历史 | `.claude/settings.local.json` | 历史内容仅包含本机权限设置；未发现凭证字段或高置信签名，当前文件已忽略 |
| 当前生产配置 | 环境变量占位 | 通过；没有回写明文凭证 |
| 历史数据库口令 | 曾存在字面量 | 视为泄露，必须数据库端轮换 |
| 当前 `DB_PASSWORD` 指纹 | 与历史字面量不同 | 通过；旧密码已在数据库端失效 |
| 当前 `DB_USERNAME` | `wms_app` | 通过；应用已使用低权限账号重启 |
| 当前 `JWT_SECRET` 指纹 | 与历史字面量不同 | 本机环境相对历史值已更换；仍需部署环境自行确认 |
| 当前 `BATCH_SALT` 指纹 | 历史无生产字面量且当前已设置 | 不需要轮换；按照既有规则保持稳定 |
| 渠道/云/Git 托管令牌 | 当前环境未提供，历史无高置信签名 | 仓库扫描无命中；若部署环境另有配置，应由对应平台复核 |

## 轮换执行边界

仓库中的 `docs/security/setup-db-user.sql` 已修正：无论 `wms_app` 是首次创建还是已经存在，执行脚本都会重新设置应用账号密码并重申 `NOSUPERUSER`、`NOCREATEDB`、`NOCREATEROLE`、`NOREPLICATION`。

真实轮换已于 2026-08-11 按以下步骤完成：

1. 明确目标为本机 `wms_db` 和 `wms_db_test`，停止后端进入维护窗口；
2. 在本机生成两个不同的强密码，并在任何数据库写入前创建 DPAPI 恢复包；
3. 轮换 `postgres`，创建/轮换 `wms_app`，转移数据库、schema、表、序列和视图所有权；
4. 设置 Windows 用户级 `DB_USERNAME=wms_app` 和新的 `DB_PASSWORD`，重启应用；
5. 重新运行审计，确认数据库密码不再匹配历史值；
6. 验证应用健康、V4.49、对象所有权、旧密码失效及 `wms_app` 的四项高权限标志均为 `false`。

不要把新密码写入命令历史、聊天、Git、验收文档或 CI 日志。BATCH_SALT 不参与此次轮换。

## 发布决策

本机发布凭证门禁已经通过。下一任务是推送安全审计与轮换证据并确认远程 CI；随后由用户单独决定是否创建 P2 发布标签或部署。按 `CODEX_TASK_MODEL_POLICY` 属于 **L5，建议 GPT-5.6 Sol + XHigh**。

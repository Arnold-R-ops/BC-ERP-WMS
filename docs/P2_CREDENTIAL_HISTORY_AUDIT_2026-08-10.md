# P2 历史凭证扫描与轮换确认

审计日期：2026-08-10

## 结论

代码仓库当前版本没有发现需要人工复核的高置信私钥、云访问密钥、GitHub/GitLab/Slack/NPM/Shopify/Stripe 令牌或内嵌 URL 账号口令；生产配置已经改为从环境变量读取敏感值。

但发布凭证门禁为 **BLOCKED**：当前进程环境中的 `DB_PASSWORD` 与 Git 历史中曾提交的旧数据库密码完全相同，且 `DB_USERNAME` 未显式设置，应用配置会回退到 `postgres`。旧值既然已进入 Git 历史，就必须视为泄露；在数据库端完成轮换并切换为 `wms_app` 前，不得创建 P2 发布标签或部署。

本轮只做脱敏只读扫描并完善轮换脚本，没有显示任何凭证值，没有修改数据库、环境变量、运行中应用、Git 历史或远程发布状态。

## 扫描范围与方法

- 扫描所有 38 个可达提交、2460 个可达对象路径，而不是只扫描当前工作树。
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
| 当前 `DB_PASSWORD` 指纹 | 与历史字面量相同 | **阻塞**；尚未轮换 |
| 当前 `DB_USERNAME` | 未设置 | **阻塞**；配置会回退到 `postgres`，未证明已切换低权限账号 |
| 当前 `JWT_SECRET` 指纹 | 与历史字面量不同 | 本机环境相对历史值已更换；仍需部署环境自行确认 |
| 当前 `BATCH_SALT` 指纹 | 历史无生产字面量且当前已设置 | 不需要轮换；按照既有规则保持稳定 |
| 渠道/云/Git 托管令牌 | 当前环境未提供，历史无高置信签名 | 仓库扫描无命中；若部署环境另有配置，应由对应平台复核 |

## 轮换执行边界

仓库中的 `docs/security/setup-db-user.sql` 已修正：无论 `wms_app` 是首次创建还是已经存在，执行脚本都会重新设置应用账号密码并重申 `NOSUPERUSER`、`NOCREATEDB`、`NOCREATEROLE`、`NOREPLICATION`。

真实轮换尚未执行，原因是它会改变数据库超级用户和应用连接状态，至少需要：

1. 明确目标数据库和维护窗口；
2. 通过安全渠道准备两个不同的强密码，分别用于 `postgres` 和 `wms_app`；
3. 执行 `docs/security/setup-db-user.sql`；
4. 设置 `DB_USERNAME=wms_app` 和新的 `DB_PASSWORD`，重启应用；
5. 重新运行本审计，确认不再匹配历史值；
6. 只读验证应用健康、Flyway 历史和 `wms_app` 的 `rolsuper=false`。

不要把新密码写入命令历史、聊天、Git、验收文档或 CI 日志。BATCH_SALT 不参与此次轮换。

## 发布决策

当前可以继续保留已通过 CI 的远程分支，但**不能创建 P2 发布标签或部署**。下一任务是经用户明确批准后，在受控维护窗口执行数据库凭证轮换与低权限账号切换，然后重新运行凭证门禁和标准 CI。按 `CODEX_TASK_MODEL_POLICY` 属于 **L5，建议 GPT-5.6 Sol + XHigh**。

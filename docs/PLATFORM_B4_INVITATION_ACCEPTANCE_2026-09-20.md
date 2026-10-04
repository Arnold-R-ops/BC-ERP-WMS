# b4 单次隔离启动／邀请验收记录

结论：**该 F3／r11 配对冻结版本的 b4 隔离邀请激活人工验收通过；环境正常关闭，临时认证文件清除。** 本批结项，不自动创建下一轮环境或进入上线。

时间：2026-09-20 北京时间 23:43:42 就绪；2026-09-21 00:25:26 完成最后登录核验；00:26:32 收尾记录落盘；00:28:20 最终只读复核完成。

原始监督器结果保持 `MANUAL_COMPLETION_REPORTED`、`manualAcceptancePassed=false`：工具有意不代替人工判定。独立 `acceptance-assessment.json` 根据用户逐步反馈和确切检查点作通过结论，不改写原始环境报告。

## 1. 范围和输入

按用户批准的前置顺序修复交付报告第 6 节执行。只修改七个测试文件的 b4 目标标识，准备器额外保护 b3 和 a1/a2 修复历史；47 处行差异与修改前后哈希见 `docs/releases/platform-browser-invitation-20260920/b4/target-binding-ledger.json`。没有修改产品、迁移、业务断言、ERP、依赖或公共配置，没有提交合并、临时唤醒或原服务操作。

- 准备根：`D:/ERP_WMS/BC warehouse/2G/target/platform-b4-preparation-20260920/a1`。
- 运行根：`D:/ERP_WMS/BC warehouse/2G/target/platform-browser-invitation-20260920/b4`。
- 证据根：`D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4`。
- 数据库：`p3_platform_browser_b4`；角色：`p3_b4_initializer`、`p3_b4_migrator`、`p3_b4_app`；控制 schema：`p3_b4_control`。
- 仅回环监听：PG 55459、后端 18092、代理 15174。
- 实测执行身份：`ARNOLD\Arnold`，预期 SID，administratorEnabled=true、elevated=true、restrictedToken=false；工具权限审批后执行。
- 工具：Node 24.12.0、Java 17.0.17、PostgreSQL 18.6、固定 Windows PowerShell 5.1.26100.9444。

F3 源码／dist、r11 产品／class／136 项 jar 和迁移指纹核对一致，沿用已验收产品，没有重建或重跑完整后端验收。全部历史保护核验通过。

## 2. 本次实际执行

```powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-b1-prepare.mjs'
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b4-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' start --authorized-b4-invitation-only-20260920
```

各执行一次。新冻结 48 文件、独立编译 18 class。37 项语法、16 项 Java、266 项 Node 全部通过，无失败／跳过／取消／待办。266 = 主门禁 155 + 完整性 13 + 启动安全 25 + 诊断 38 + 运行安全 35；准备器控制台的 `nodeTests:155` 仅统计主门禁，不是最终合计。19 次 JDK-only 合成子进程包含在对应 Node 门禁中，不另充作业务用例。

本次未包含只用于 a2 修复的 verifier，因此冻结文件及语法数量比 a2 各少一项，不是删除测试。

编译、查询、就绪和收尾等预算均沿用已批准方案，已封存于 `input.json.budgets`；完整命令封存于 `input.json.commands`。实际后端参数包含主类前的 `-Dspring.devtools.restart.enabled=false`，与封存命令逐项一致。

## 3. 真实环境已完成部分

- 三目标端口与目录前置检查通过；ACL 实际收紧并读回确认，仅预期 SID 与 SYSTEM、无继承，之后才生成随机凭据。
- 新集群一次初始化／启动，SCRAM，目录／所有者／创建时间／父链／监听／SQL 身份核验通过。集群号：`7687643164934623464`。
- 94 个迁移资源 validate，通过空库 8 条迁移（B4_71、V4_72、V4_85～V4_90），再次 migrate 为零变更。
- 运行角色实际核验：非超级用户、无 BYPASSRLS、不拥有业务表、无迁移历史写权限、无控制 ready 表 UPDATE 权限。
- 后端一次启动成功并就绪，PID 54840，监督器 PID 55900；代理 PID 34788。PID 只作为本批当时证据，后续操作必须再次核验身份，不能据此直接终止进程。
- 后端就绪 22024 ms、代理就绪 7393 ms，在各自 90 秒／10 秒预算内。
- 平台页面响应与 F3 静态产物哈希匹配；ERP index、private 路径和源映射访问边界检查通过。
- 初始数据检查：仅 A（`inviter-b4@platform.invalid`）一名合成超级管理员，MFA 尚未绑定，无会话／委派授权／邀请；B 未预建，无未知账号或邀请。
- 就绪时 firstFailure=null、cleanupErrors=[]；这不是最终关闭结果。

迁移工具保留既有兼容警告：Flyway 9.22.3 提示 PostgreSQL 18.6 超出其已测试支持范围。本次实际迁移通过，不据此宣称生产兼容性，不在本批升级依赖。

## 4. 指纹

```text
输入聚合
da326580be0ef0d1f5787e06148fae89fe1f62e79095286b979dabe3087b55b2
新 class 聚合
fc53056f2b864fd44746052b5929649efce24e321e7fef8f5d7587fc84d65760
input.json
9eb92e3672b600de72d6853a68c9dd06c7129ca1ff61069b51e21903450ed625
compiled.json
77b585b0376c3b5a93181cc04182ac3f8e74757f3c0928ddd80b09813a87eea9
preparation-result.json
14024fbb84a544e6a1975bfb0a94fd82b3ecd4141d77e8076c9fc902c73fa05f
environment-ready.json
856f05c6280e638968abf31e4bd659fe5ecc27005a6fc75c2bb3f8b13f8238a4
```

## 5. 人工验收、诊断与正常关闭

先征询用户是否方便，用户回复“方便”后逐步操作。人工结果来自用户在浏览器中的逐项反馈，未通过 API／改库替代主线；只读检查点由原监督器按精确账号读取。

| 人工子项 | 用户反馈及对应检查点 | 结果 |
| --- | --- | --- |
| A 首次登录、MFA、恢复码确认 | 用户报告依次到达绑定、恢复码和首页；01-a-ready：MFA=true、有效会话 1、MFA_ENROLLED 审计成功 | 通过 |
| 创建邀请及人工转交 | 用户报告创建成功、B 到设置密码页；02-invitation-created：B 未预建，岗位安全审计员，邀请未接受 | 通过 |
| B 独立密码、首次 MFA、恢复码确认、激活 | 用户逐步报告密码页、绑定页、恢复码、首页；03-b-active：accepted=true，绑定 B 的确切用户 ID、MFA=true、有效会话 1 | 通过 |
| B 刷新 | 用户明确反馈“B 刷新后仍在首页” | 通过 |
| B 普通退出 | 用户反馈“本浏览器中此平台账号的服务端会话已撤销”；04-b-logged-out：B 有效会话 0，SESSIONS_REVOKED 成功 | 通过 |
| 原邀请链接拒绝 | 用户反馈“链接无效、已过期、已撤销或已经使用……”；05-b-link-rejected：已接受、未撤销、仍在有效期，B 有效会话仍 0 | 通过 |
| B 密码＋既有 MFA 正常再次登录 | 用户报告 MFA 验证页及“B 再次登录成功”；06-b-relogin：B 有效会话 1，MFA_VERIFIED 审计成功 | 通过 |

恢复码按引导由用户在本地保存并完成页面确认后进入首页；没有检查其本地保存内容或收集恢复码。邀请为人工安全转交，不记作真实邮件送达。B 在各检查点只有 `PLATFORM_SECURITY_AUDITOR`，委派授权记录为 0；没有未知账号／邀请，没有授予租户读取或导出权限。

后端诊断共 11 个阶段事件，最终 `COMPLETE`；异常首因为空，协议 malformed／oversized／truncated／eventLimit／afterFailure／stageMismatch／ioError 均为 0，complete=true、integrity=true。诊断完整性、人工结果、收尾结果分别记录。

人工主线完成并核验后实际调用一次：

```powershell
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b4-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' stop MANUAL_COMPLETED
```

| 收尾项 | 实际证据 |
| --- | --- |
| 代理 | 正常停止，退出 0，7639 ms；原 PID 不存在、15174 无监听 |
| 后端 | 正常停止，退出 0，7694 ms；原 PID 不存在、18092 无监听 |
| 数据库 | 已核实目录／身份后 pg_ctl stop 一次，退出 0、135 ms；独立收尾 26203 ms，在 60 秒内 |
| 父链／目录残留 | 两次停止后检查均确认 server PID 48800、relay PID 14764 不存在；目录匹配进程 0、子进程残留 0、55459 无监听、postmaster.pid 不存在 |
| 监督器 | 执行会话正常退出 0；00:28:20 额外只读查询确认原 PID 55900 当前不存在 |
| 临时材料 | init-password.ephemeral 初始化后已清除；runtime.properties、login-credentials.txt 在关闭后清除；private 目录实际为空 |
| 其他服务 | 既有 5432／5173 监听及对应进程身份保持，未操作其他服务 |

`cleanupErrors=[]`。本次未遇到真实 PID 复用，不宣称实测了这一分支；完整证据规则保留，无按 PID 强杀。监督器补查的 `historicalClosureConfirmed=false` 表示单次现状查询不能独自证明历史关闭；历史正常退出由同批执行退出码及环境关闭记录提供，未改写该字段。

最终只读核验：冻结源码／新 class／工作区工具来源／F3／r11／依赖均一致；18 个受保护历史根逐文件及清单一致。两组合成证据的敏感哨兵扫描通过，普通日志的完整 JWT 模式扫描 0 命中；这不等于通用敏感信息扫描能穷尽所有未知模式。认证材料未被收集到聊天或报告。

保留新集群目录和合成测试记录，不再启动。用户自行保存的测试密码、邀请链接、恢复码和认证器条目未由工具删除；这些不是生产凭据，可由用户自行清理。已删除的临时明文文件未存备份，不从历史环境恢复。

新增证据：`final-integrity-check.json`、`acceptance-assessment.json`。原 `environment-final.json` SHA-256：

```text
55506579bc4f80a7e0ace354a38b072f8e93182e2384f75b659a61c620079884
```

## 6. 未覆盖与停止点

- 本批补齐约定的邀请激活人工主线，不是重新完成所有平台页面、MFA／恢复码消耗、会话超时等历史验收，也不扩展其范围。
- 真实邮件送达未验；Flyway／PostgreSQL 的既有版本兼容警告保留，生产数据库兼容性须由正式运维／迁移演练确认。
- b3 原 FAILED_WITH_CLEANUP_ISSUES、b2 原因未知以及 a1 等失败证据保持不变。b4 成功不倒填历史缺失原因，也不宣称历史故障的唯一根因已被完全证明。
- F3 前端构建及 r11 后端验收继续沿用各自记录；本批未重新构建产品。通过的是该配对冻结版本的隔离浏览器主线，不是完整发布候选版本或上线授权。
- 后续平台版本固定／跨任务发布整合、正式域名／HTTPS／密钥／真实邮件告警／管理员应急流程、生产权限及备份恢复／迁移演练，均需另行确认。恢复核对最终业务边界仍待决定。

本批已交付且环境已关闭，不自动创建下一轮，不提交合并、不开放生产。

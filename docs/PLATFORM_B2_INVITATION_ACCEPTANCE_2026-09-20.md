# b2 独立平台邀请激活验收

**最终结果：FAILED；后端启动提前退出，人工主线未执行。数据库正常关闭，后端异常退出后确认不存在，代理未启动；三端口和监督器残留复核通过，临时明文认证材料已清除。未修补重跑或创建下一轮。**

本批经用户批准，沿用 F3 前端及 r11 后端产品；仅进行测试目标绑定、专用准备、隔离启动、人工邀请主线及收尾。未授权生产、提交合并、ERP 开发或临时唤醒。

## 1. 启动前冻结记录

- 工作分支：2-1G；HEAD：9c12f6f893105632c4de24ad8bb4f6f43092f84e。原有工作区改动保留。
- 运行根：D:/ERP_WMS/BC warehouse/2G/target/platform-browser-invitation-20260920/b2。
- 准备根：D:/ERP_WMS/BC warehouse/2G/target/platform-b2-preparation-20260920/a1；只从其 snapshot 入口启动，使用其 classes。
- 证据根：docs/releases/platform-browser-invitation-20260920/b2。
- 数据目录：运行根/pgdata；库：p3_platform_browser_b2。
- 专用角色：p3_b2_initializer、p3_b2_migrator、p3_b2_app；控制 schema：p3_b2_control。
- 只监听 127.0.0.1：55459 / 18092 / 15174 分别对应 PG / 后端 / 代理。
- Node 24.12.0、Java 17.0.17、PostgreSQL 18.6；固定系统 Windows PowerShell。
- 执行上下文按工具审批核验 ARNOLD\Arnold 提升令牌；不 RunAs、不修改系统策略。

本次准备实际通过：33 项语法检查、16 项 Java、193 项 Node（155 原门禁 + 13 依赖契约 + 25 启动安全契约），无失败、取消或跳过。13 个 class 由本批新封存内容独立编译，不复制历史包装器。

41 个输入文件聚合：`02de26106a9a896ea29f72dd0c7fd85ff522adedb5f6b773aa69d49fb5da84b2`。

13 个包装器 class 聚合：`f21a48b90ae37c4e806e75947c83505ffdaa5142ae2ae6c22fe729b8e8ad31d8`。

F3 源码：`ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d`；dist：`7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c`。

r11 输入：`f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62`；679 个产品文件、951 个 class/resource 及 JAR 指纹一致。没有重建产品或重跑 r11。

本批源文件修改仅涉及 platform-b1-binding.mjs、platform-b1-prepare.mjs、platform-b1-batch.mjs、platform-b1.test.mjs、PlatformB1Harness.java、PlatformB1Contract.java；生成的隔离器、准备器及环境绑定随之更新。旧文件名、类名及 B1 日志协议标记保留，但实际目标、角色、schema、授权属性和合成账号均绑定 b2。契约逐项核对只允许目标文字替换，业务断言不变；精确前后指纹在 input.json 的 contractBindings 中。

ACL 修复、合规查询标签、完整依赖清单及失败停止机制均进入本次冻结入口。运行根必须不存在；现场身份/端口检查通过才创建目录，ACL 设置和完整读回通过才生成凭据。b1 历史目录只读核验，不能作为运行兜底。

## 2. 精确命令及预算

已执行准备命令（一次）：

```powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-b1-prepare.mjs'
```

冻结启动命令：

```powershell
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b2-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' start --authorized-b2-invitation-only-20260920
```

停止使用同一冻结入口：主线完成并核对后 `stop MANUAL_COMPLETED`；中止/未完成为 `stop ABORT`，不可混用。人工等待期间分步使用冻结入口 checkpoint，状态读取不包含认证值。

固定预算：编译 300 秒、Node 每套 120 秒、Java 60 秒、语法每项 15 秒；初始化/迁移各 180 秒；ACL/SQL 各 30 秒，SQL statement 15 秒；启动器 37 秒（内部 35 秒）、PG 就绪 40 秒、独立收尾 60 秒、PG 停止命令 32 秒；全量查询 15 秒、PID 查询 5 秒、就绪 SQL 4 秒；后端就绪/停止 90/30 秒、代理就绪/停止 10/10 秒。不得运行中加时，不申请唤醒。

## 3. 人工主线与未完成边界

仅预建合成发起人 A（inviter-b2@platform.invalid），B（invitee-b2@platform.invalid）不得预建，必须通过真实邀请成为 PLATFORM_SECURITY_AUDITOR，无租户读取/导出授权。

逐步人工流程：创建并安全转交邀请 → 设置密码 → 首次 MFA → 保存恢复码 → 激活登录及刷新 → 退出后旧链接拒绝 → 正常再次登录。开始人工步骤前确认用户方便配合。人工转交链接不算邮件送达；禁止 API/改库替代主线。

截至本节冻结时：仅准备门禁通过，数据库/服务未启动，人工项目尚未执行。后续实际启动、人工结果与关闭结果分别追加，不以环境就绪或正常关闭冒充人工验收通过。任一失败停止后续，不自行修补重跑。

## 4. 历史及证据保护

input.json 保存固定命令、预算、完整来源/依赖/迁移对应及旧目录逐文件哈希。准备后核对原 b1 运行 39 文件、早期证据 43、旧准备 51、旧准备证据 99、修复准备 58、修复证据 113 均未变。旧 b1 FAILED 和 PID 补核缺口不改写。

启动前汇总读取曾因输出过长在会话工具端 JSON 解析失败；随后只读输出精简元数据完成核对。没有重跑门禁、修改原始结果或据此启动兜底流程。

## 5. 本次实际执行结果与首因

监督器于北京时间 2026-09-20 20:12:18 写入启动记录，20:14:34 完成失败收尾，退出码 1。原始 environment-final.json 保持 FAILED。

| 阶段 | 实际结果 |
| --- | --- |
| 新封存、独立编译及无数据库门禁 | 通过，见第 1 节；不拼接历史成绩 |
| 三端口、执行身份及新目录前置检查 | 通过；ARNOLD\Arnold，提升令牌、管理员组启用、非受限令牌 |
| 私有目录 ACL | 完整设置/读回通过，随后才生成凭据；owner SID 与 SYSTEM 两个显式 FullControl 主体，继承保护开启 |
| initdb / PostgreSQL 启动 | 通过；新集群、SCRAM、只监听 127.0.0.1:55459 |
| 进程父链及维护库 SQL 身份 | 通过；pg_ctl → cmd.exe → postgres；集群号 7687589294815462592，postgres / p3_b2_initializer |
| 主库空库迁移 | 通过；校验 94 个迁移源文件，实际应用 8 条至 V4_90，重复 migrate 为零变更 |
| 运行角色权限 | 通过；p3_b2_app 无 SUPERUSER、无 BYPASSRLS、不拥有 public/control 表，对 Flyway 历史无写权限、对 ready 无更新权限 |
| 后端启动 | 失败；Java 进程 48060 退出 1，未达到就绪 |
| 代理启动 / 前端实际配对页面核验 | 未执行 |
| 邀请激活人工主线 | 全部未执行，未请求用户登录或处理凭据 |

首因记录：stage=BACKEND_START，name=Error，safeMessage=B1_BACKEND_EARLY_EXIT。B1 为保留的内部标记，不代表运行在旧 b1。

后端唯一允许输出的失败标记：

```text
B1_BACKEND_FAILURE type=java.lang.IllegalStateException rootType=java.lang.IllegalStateException
```

**尚不能确定该异常的具体抛出位置、触发条件或属于包装器/配置还是产品。** 当前包装器仅保留异常类型和根类型，没有安全阶段标记、异常栈定位或允许公开的原因码。静态核对生成的 URL、角色、p3.b2 属性和 P3_B2 环境变量绑定一致，但这不证明运行时全部配置有效。不能将通用提前退出错误当作底层原因，也不能据此认定产品缺陷。

未取得 environment-ready.json 或初始账号状态快照，因此不把 A 账号准备完成记为通过。没有通过 API/改库替代 B 的邀请流程，没有人工转交链接或邮件送达验收结果。F3、r11 的历史通过不替代本次失败的配对服务启动。

实际迁移版本为 4.71（SQL_BASELINE）、4.72、4.85、4.86、4.87、4.88、4.89、4.90；脚本和校验和值见 environment-final.json 的 migrationHistory 与 044-migration-history.log。Flyway 9.22.3 对 PostgreSQL 18.6 的原有兼容提示仍保留；本批没有升级依赖，不宣称生产兼容性已经验收。

## 6. 收尾与追加只读核验

- 代理：NOT_STARTED，不是“启动成功后正常关闭”。
- 后端：EXITED_NONZERO，退出 1；冻结工具查询确认 PID 不存在、18092 无监听。不是正常成功退出。
- 数据库：按核实的目录、所有者、集群号及父链执行一次正常 fast 停止，pg_ctl 退出 0；独立收尾 24.554 秒 / 60 秒预算，完成状态 STOPPED。postmaster.pid 不存在，目标进程/父链残留核验通过。
- 收尾错误数组为空；监督器原始执行器确认退出 1。人工验收未通过，不使用 MANUAL_COMPLETED。
- init-password.ephemeral 在初始化后删除；runtime.properties、login-credentials.txt 在自动失败收尾删除。没有生成邀请令牌、用户 MFA 密钥或恢复码用于人工主线。保留新 pgdata 及空 private，不修改旧 b1 目录。
- 20:16:08—20:16:23 追加只读核验：55459、18092、15174 均无监听；本批目标 PostgreSQL 进程 0，pidFile=ABSENT；监督器 47560 当前不存在，旧 b1 PID 历史结论未变；private 条目数 0。
- 追加核验再次匹配冻结源码/包装器、F3/r11 产品及历史证据指纹。没有数据库连接或新启动，不改变原始 FAILED。

追加证明位于 closeout-readonly.json；查询调用分别耗时 5.376 / 4.735 / 5.036 秒（15 秒预算）和 0.693 秒（PID 5 秒预算），无超时。

关键原始文件 SHA-256：

```text
input.json
4fb0c93a457bd37ddbb0d9e1d71698a8027c2cb4fe9fabaa235159bb6512bcd7
compiled.json
a4a09597024f502c9cfe6134499c93016cd7233c230a6e8fe7a73d8c61901550
preparation-result.json
3bfea9a0f02221e9839163a485f4b19cf92477fb4efbae24bc4ccd5d176b0346
environment-final.json
1cbb61799b85c8410afc45d158424cb1404ca04881b76d896c80025e1156f158
backend-markers.log
8b70e27ed9eb4318251f21c003382c901747c7517457f092268010dc50102696
```

## 7. 本批停止点与建议后续范围

本批已停止，当前没有可用的浏览器验收环境，用户无需进行人工操作。本次没有产品、迁移、业务断言或 ERP 功能修改，没有其他服务操作、提交合并、生产开放或临时唤醒。F3/r11 产品对应关系保留，未创建下一轮目录。

建议下一批仅批准：专用 Java 启动包装器增加脱敏的执行阶段、异常类型/根类型、安全栈位置和严格允许公开的原因分类，并验证监督器实际能保留这些字段；补充无需数据库的失败注入及敏感值排除契约，重新封存编译。禁止任意异常消息、完整配置、认证请求/SQL、密码、JWT、MFA 或恢复码进入日志。不得通过放宽隔离检查修复“提前退出”。

以上只是建议，尚未实施；不重启 b2、不复用其凭据、不自动建 b3。若需真实服务重新取证，提交新固定目标及范围另行确认。当前证据不足以确定产品修复范围。人工邀请主线、正式上线准备和恢复核对最终业务边界继续独立保留。

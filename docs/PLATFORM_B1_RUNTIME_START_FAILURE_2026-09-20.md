# b1 运行启动失败与未启动环境收尾记录

日期：2026-09-20。启动记录时间：北京时间 14:44:20；启动器失败结束：14:44:37。

**本批结果：FAILED，未进入数据库初始化、服务启动或邀请人工验收。没有自动修补、重试或创建下一轮环境。**

本次使用已通过的新 a1 冻结入口和新 a1/classes。此前完整无数据库准备门禁的 PASSED 仍有效，但不能替代本次运行成绩。旧 b1 的早期 FAILED、快照和部分产物没有覆盖，F3、r11 沿用且未重建/重跑。

## 1. 实际执行范围与首因

实际只执行一次下列启动命令，cwd 为 D:\ERP_WMS\BC warehouse\2G；通过工具审批使用沙箱外上下文：

~~~powershell
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b1-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' start --authorized-b1-invitation-only-20260920
~~~

输入、产物及既有门禁核验通过后，三个真实 OS 前置检查通过。随后 PRIVATE_ACL 阶段失败：

- 首个失败阶段：PRIVATE_ACL。
- 启动器记录：S2Failure / COMMAND_EXECUTOR_FAILED，queryStage=PRIVATE_ACL。
- 命令序号：4；固定 PowerShell 的 Set-Acl 命令。
- 实际退出 1，用时 1.420 秒；预算 30 秒，非超时；输出完整，命令结束已确认。
- 原始错误可识别为：Set-Acl 在 Microsoft.PowerShell.Security 模块中被发现，但模块自动加载失败；FullyQualifiedErrorId 为 CouldNotAutoloadMatchingModule。
- 原中文输出包含编码替换字符，原日志不改写；不补写未捕获的底层 Import-Module 异常。

**已确认的是专用启动过程的 PowerShell 模块自动加载失败，不是 PostgreSQL 初始化失败，也没有证据表明是平台产品缺陷。** 模块失败的更深原因尚未确认；没有进一步导入模块、调整 PSModulePath、修改系统权限/安全设置或重试 ACL。

失败发生在运行密码及密钥生成代码之前。只创建了本批空 private 目录，未写入任何运行凭据。

## 2. 实际通过项与未执行项

| 项目 | 本次实际结果 |
| --- | --- |
| 新 a1 冻结输入及 13 个包装器 class | 指纹一致 |
| F3 / r11 产品对应关系 | 指纹一致，沿用，不重新验收 |
| Windows 执行身份 | ARNOLD\Arnold；管理员组启用，elevated=true，restrictedToken=false |
| 实际查询能力 | 三次完整查询 AVAILABLE，进程信息无不完整项 |
| 55459 前置检查 | 目标数据目录/PID 文件不存在，无本批进程和端口占用；6.322 秒 |
| 18092 前置检查 | 同上；4.868 秒 |
| 15174 前置检查 | 同上；4.612 秒 |
| 私有目录权限收紧 | FAILED，命令 4，未继续 |
| initdb、pg_ctl、SQL 身份、专用库/角色创建 | 未执行 |
| 迁移、运行角色权限核验 | 未执行 |
| 后端、代理、页面就绪 | 未执行 |
| A 合成账号准备、B 真实邀请 | 未执行 |
| 设置密码、首次 MFA、恢复码、登录/刷新/退出/旧链接拒绝/再次登录 | 全部未执行 |

实际 SID 为 S-1-5-21-3770167052-703495069-1631994977-1001，固定 Windows PowerShell 5.1.26100.9444。核验了已授权上下文，不因此推论 PostgreSQL 必须管理员运行。没有使用 RunAs 或临时唤醒。

本次不是邮件投递验收；未生成或转交邀请链接，也未用 API/改库替代人工步骤。

## 3. 收尾、残留与附加核验边界

监督器自动进入 finally，记录代理和后端均为 NOT_STARTED。数据库尚未初始化，未创建任何数据库子进程，不需要调用停库命令。监督器打印 B1_FINISHED FAILED，执行工具返回退出码 1。

没有发送 stop MANUAL_COMPLETED。本次失败发生在 environment-ready 之前，监督器已经自动退出；stop ABORT 控制入口要求环境已就绪且仍在等待，因此没有向已退出的进程追加该请求。不能把自动失败收尾记为人工完成。

随后进行一次获工具权限批准的只读残留核验，不连接数据库、不重启任何进程：

- 14:47:33 / 14:47:38 / 14:47:43 分别核验 55459 / 18092 / 15174。
- 三个端口均没有监听；本批目标进程匹配数均为 0。
- pgdata 及 postmaster.pid 均不存在。
- 原有 5432 监听仍显示 PID 10400，与启动前一致；没有停止或变更它。
- private 目录存在，但文件数 0、子目录数 0。保留这个失败批次的空目录，不删除或回收作为重试目标。未生成临时明文认证材料，清除凭据不适用。

**附加 PID 核验没有通过，单独保留：** 补核使用标签 B1_STARTER_CLOSED，而冻结 queryWindowsProcess 只允许 ^[A-Z_]+$，数字 1 导致 INVALID_PROCESS_QUERY / QUERY_ARGUMENTS。实际 PID 查询在启动前被参数检查拒绝，没有发起 OS PID 查询；补核包装器退出 1，未重试。不能把该异常当作 PID 不存在。

原启动器 PID 43936 的执行工具已有退出码 1 回执，但独立 OS PID 不存在补核未完成。三个端口/目标 PG 进程的只读核验与该 PID 补核分别记录，不能笼统写成“收尾全部成功”或“正常停库通过”。本批环境状态应写成：数据库/服务未启动，观察到目标无残留，独立启动器 PID 补核存在记录缺口。

## 4. 同次只读发现的未运行分支问题

只读核对冻结源码发现，实际 b1 入口中还存在相同标签格式问题：

- platform-b1-batch.mjs 第 65 行：B1_HTTP_PROCESS。
- platform-b1-batch.mjs 第 99 行：B1_STOP_CONFIRM。
- platform-launch-preflight.mjs 第 37 行拒绝包含数字的 stage。

这是源码可确认的测试工具调用契约不匹配。它们在本次 PRIVATE_ACL 失败前尚未执行，因此**不是本次首因，也不能记作本次实际后端就绪/停止失败**。后续只修模块加载仍不足以证明这些未执行分支可用；需要另行授权测试层修正与契约验证。本次没有修改标签或放宽共享参数检查。

## 5. 原始证据和指纹

证据根：
D:\ERP_WMS\BC warehouse\2G\docs\releases\platform-b1-preparation-20260920\a1

- start-attempt.json：单次尝试、新输入 ID、监督器 PID、awakeRequested=false。
- os-1-preflight-55459.json、os-2-preflight-18092.json、os-3-preflight-15174.json：实际身份、查询能力及初始目标检查。
- 001-query.log～003-query.log：原始结构化查询输出。
- 004-private-acl.log：首个运行失败的原始输出。
- environment-final.json：原 FAILED、首因、4 条命令回执、NOT_STARTED、人工未完成。
- runtime-postfailure-readonly.json：本次追加的三端口残留核验、PID 补核参数错误及其限制，不改写原运行记录。

关键 SHA-256：

~~~text
封存输入 ID
e194ee481ff27f1f5c26e8f26240ea8f1eac1f2534cbdf2a04100453224080a9
包装器编译聚合
9cba2424aab4c881dca6e27895c2e610905f11edf1e9b28f786401b24af904ad
input.json（未变）
463653e59218560345401fb6797262bf4160c05fa680f91b295fd41506fe55c5
compiled.json（未变）
5b6e81c9271d7c7036b396186dc52957313a3eb0a4c4fbdef7de8dd165dcab38
preparation-result.json（原无数据库 PASSED 未变）
2318762660157d84ddd384e94ec3cc1f8e53bb92711a5ebd588dffacb6c03fa7
start-attempt.json
34bf88b2f6dfedfc87a0acc3c3d64fb9bbbaab3f78c4c636689a835713ba8000
environment-final.json
c6178f7f8beae158c780e8f28af32d43e2cd1c26d1e1bc5a37b04919824d2c80
004-private-acl.log
649c2e8338a85bd866fefe7283b50ad01ffff6659b6fa19b4021fba2b5235525
runtime-postfailure-readonly.json
187927f52378104bccbe5701a76359cdd215784ae3e62a176fb87128de06155f
~~~

新快照、classes 及 F3/r11 指纹在启动前、失败后只读检查均匹配。旧 b1 受保护文件哈希未变；没有读取旧包装器作为执行兜底。当前失败标记和空 private 目录保持原样，冻结入口不会允许原地自动重试。

## 6. 建议后续范围——尚未执行，待授权

建议下一批仍只处理专用启动工具，不启动数据库/服务：

1. 定位固定 PowerShell 子进程的模块加载失败，记录实际模块来源、环境和允许公开的底层错误；不把已确认的提升身份误写成权限不足，不修改系统策略或公共环境。
2. 如需修正，只作用于专用验证子进程；在新合成私有目录验证 ACL 设置及读回检查。不得取消权限收紧、继承宽权限后继续写凭据或放宽认证保护。
3. 将 b1 实际就绪/关闭调用的诊断标签与共享查询契约对齐；补齐直接覆盖实际调用的无数据库回归。保留严格查询参数和身份检查，不顺带修改产品。
4. 重新封存并完成受影响门禁后，另行确定后续运行目录/启动授权；不覆盖当前 a1、b1 的 FAILED、快照或产物。

本轮到此停止。没有租户 ERP 开发、真实数据/账号操作、现有服务变更、提交合并或上线操作。邀请人工主线、运行环境关闭验证及正式上线条件继续独立保留，不能由此前 F3/r11 通过推定本次完成。


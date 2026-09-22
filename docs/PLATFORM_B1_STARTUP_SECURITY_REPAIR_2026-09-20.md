# 平台浏览器启动工具：模块、ACL 与查询标签修复交付

日期：2026-09-20。模块诊断：北京时间 15:24:32；完整验证结束：15:35:39。

**本批通过，停在工具修复及无数据库验证交付。** 原有 16 项 Java 契约、155 项 Node 检查、13 项完整性契约和新增 25 项安全启动契约全部通过；35 项语法检查通过。新合成目录 ACL 设置/读回、合成子进程身份查询/正常退出/当前时点不存在均有实际证据。

未启动或连接数据库，未启动前端/后端/HTTP 服务，没有重建 F3、重跑 r11、临时唤醒、提交合并或生产操作。原 b1 失败记录及旧空目录未改。**本结论不是浏览器人工验收通过。**

## 1. 模块问题：已确认事实与仍未知项

本次固定 Windows PowerShell 路径为 C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe；实际版本 5.1.26100.9444、Desktop、FullLanguage。使用与原 b1 相同的专用子进程环境过滤方式；通过工具审批在 ARNOLD\Arnold 上下文执行，管理员组已启用。

诊断没有改 PSModulePath、执行策略、UAC 或系统权限，也没有安装模块。只读发现模块并显式导入 Windows 自带可信模块。

实际证据：

1. 继承的 PSModulePath 同时包含运行时附带的 Core 模块目录与 Windows 自带的 Desktop 模块目录。可发现同名 Microsoft.PowerShell.Security 的 Core 7.0.0.0 和 Desktop 3.0.0.0。
2. 在该环境用只读 Get-Acl 触发同一 Security 模块自动加载，重现 CouldNotAutoloadMatchingModule。底层异常为 ObjectSecurity 类型数据成员重复：AuditToString、AccessToString、Sddl、Access、Group、Owner、Path 已存在。
3. 显式导入固定系统路径的 Desktop 3.0.0.0 成功，取得 Set-Acl。实现程序集来自 Windows Microsoft.Net GAC。
4. 修正后的真实合成目录验证在一个新子进程中，直接导入该系统模块，并成功执行 Set-Acl / Get-Acl。没有先修改全局搜索路径或清空/移除系统类型数据。

因此本次明确定位到了**专用子进程中的模块自动加载/类型数据冲突**，不是“缺少管理员身份”，也不是数据库或平台产品失败。混合版本搜索路径是重要环境证据；本次未追踪 PowerShell 自动解析时精确选中哪个失败清单，不能把候选顺序冒充选中轨迹。

原 b1 失败日志没有保留完整底层异常，本次复现的异常只记为本次诊断，不倒填历史。也不能由该问题修复推断此前其他宿主延迟问题已解决。

修复选择最小范围：ACL 专用脚本按绝对路径导入系统 Desktop 模块，并核对模块路径、版本和命令来源；不依赖同名自动搜索。父进程/全局 PSModulePath 保持不变。模块与程序集哈希记录在实际 ACL 证据中。

## 2. 修复文件与实际调用接入

本批修改两个专用工具，新增七个专用工具/测试文件。没有修改共享查询校验、产品、迁移、业务断言、依赖或公共配置。

| 文件 | 作用 |
| --- | --- |
| platform-b1-batch.mjs | 实际启动流程接入 ACL 证明校验；实际就绪/关闭/PID 补核使用合规标签；仍在 ACL 确认后才生成运行凭据 |
| platform-b1-prepare.mjs | 封存清单包含新增依赖，实际准备门禁接入 25 项安全启动契约 |
| platform-b1-module-diagnostic.mjs / .ps1 | 相同子进程环境下的只读模块诊断；独立封存，保留底层错误 |
| platform-b1-private-acl.ps1 | 固定系统模块导入；仅允许工作区 target 下新空目录；设置并核验完整 ACL |
| platform-b1-startup-security.mjs | 窄范围 ACL 调用/证明核验及 PID 复用分类 |
| platform-b1-startup-security.test.mjs | 新增 25 项无数据库契约，直接导入实际启动器调用函数 |
| platform-b1-startup-real-validation.mjs | 使用实际启动器的 ACL 和 PID 查询调用，在新合成目录/子进程上验证 |
| platform-b1-startup-verification.mjs | 新快照、编译、完整无数据库门禁、一次真实合成验证及不可覆盖结果 |

所有文件位于 src/test/scripts。原六套 Node 测试文件、Java 契约及共享查询工具保持原指纹，不以删测试或放宽检查获得通过。

调用侧标签映射：

| 调用 | 原值 | 新值 |
| --- | --- | --- |
| 实际就绪查询 | B1_HTTP_PROCESS | BROWSER_HTTP_PROCESS |
| 实际关闭确认 | B1_STOP_CONFIRM | BROWSER_STOP_CONFIRM |
| 专用 PID 补核 | B1_STARTER_CLOSED | BROWSER_STARTER_CLOSED |

共享 ^[A-Z_]+$ 参数规则未改，新契约还验证三个旧标签仍在执行 OS 查询前被拒绝。实际运行调用点改用被测试的导出函数，并检查真实启动流程的调用/凭据生成顺序；不是另写一套正确示例。

PID 证明比较 PID、创建时间标识、所有者、可执行路径及命令行哈希。可能复用时返回 DIFFERENT_PROCESS_OR_PID_REUSE，不把新进程当原进程关闭证明，不执行 PID 强杀；当前不存在也明确 historicalClosureConfirmed=false。

## 3. ACL 与进程实际验证

新合成目录：

D:\ERP_WMS\BC warehouse\2G\target\platform-b1-startup-repair-20260920\a1\synthetic-private

该目录本批新建；旧 b1/private 未操作。实际 ACL 命令 0.556 秒完成，退出 0；读回核验：

- 所有者：ARNOLD\Arnold 对应 SID。
- 允许主体恰好两个：该 SID 与 LocalSystem（S-1-5-18）。
- 两项均为显式 Allow / FullControl（2032127），不是继承项。
- ContainerInherit + ObjectInherit，PropagationFlags=None。
- DACL 继承保护开启，规则顺序规范；没有额外允许主体或拒绝项。
- 固定系统模块版本 3.0.0.0，命令来源和程序集位置匹配。

脚本和 Node 端均校验读回结果。模块导入/命令/证明任一步失败都会抛出错误，真实启动器不能继续生成或写入凭据。本次没有生成任何凭据或往合成目录写文件；空目录保留为证据。

模块指纹：

~~~text
系统 Security 模块清单
fa7150089e8a67a0aad27cd324d119b9778ccbead6242397780c5d5077246d30
Windows GAC Security 程序集
9d24d2a9d3b5326a9bd8fcae8863fa5af32d37d7d0eee175600d7d4e89af8010
~~~

真实合成子进程只运行固定 Node 的短脚本、输出就绪标志并等待标准输入，不监听端口、不访问数据库。实际 PID 59668，核对路径、父 PID、所有者、创建时间及命令行哈希后：

1. BROWSER_HTTP_PROCESS 查询为 AVAILABLE。
2. BROWSER_STARTER_CLOSED 在子进程仍存活时正确返回 SAME_PROCESS_PRESENT。
3. 通过本工具持有的 stdin 通道发出 stop，子进程退出 0；未按 PID 强杀。
4. BROWSER_STOP_CONFIRM 查询为 ABSENT。
5. 再次当前时点补核为 CURRENTLY_ABSENT，未宣称历史关闭已经补证。

四次 PID 查询分别约 1.471、1.468、0.785、0.724 秒，均在 5 秒预算内。该子进程在本次验证时点已退出；旧 b1 监督器 PID 43936 **没有重新查询**，旧失败补核及历史缺口原样保留。

## 4. 完整回归、封存及证据对应

新的准备根：
target/platform-b1-startup-repair-20260920/a1

新的证据根：
docs/releases/platform-b1-startup-repair-20260920/a1

先封存诊断脚本到 diagnostic-source；再封存 43 文件到 snapshot，独立编译到 classes。没有覆盖原 a1 或旧 b1。

| 验证 | 本次结果 |
| --- | --- |
| 语法 | 30 个 mjs + 5 个 ps1，只解析检查，35/35 |
| 独立 javac | 退出 0，2.532 秒，新生成 13 class |
| Java 无数据库契约 | 16/16，0.231 秒 |
| 原 Node 六套 | 35 + 40 + 33 + 16 + 12 + 19 = 155/155 |
| 既有完整性契约 | 13/13 |
| 新安全启动契约 | 25/25 |
| Node 本批合计 | 193/193，失败/取消/跳过/待办均 0 |
| 实际合成 ACL | VERIFIED |
| 实际合成进程 | 正常退出 0，当前不存在查询通过 |

25 项新增契约覆盖路径/所有者拒绝、完整 ACL 正反例、主体/权限/继承/保护/模块来源错误、实际 ACL 执行门禁、实际查询标签、旧标签仍拒绝、PID 复用/信息不全、真实调用点和凭据顺序、封存工具接入。

上述成绩全部在本批新快照执行，不拼接历史通过项。Java 源码未改，重新编译后 13 class 聚合与上批相同是预期的字节一致，不是复制旧 classes。F3 和 r11 产品指纹只做只读复核，不重建/重跑。

实际入口各执行一次，不可原地覆盖重跑：

~~~powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-b1-module-diagnostic.mjs' readonly-module-diagnosis
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-b1-startup-verification.mjs' no-database-and-synthetic-only
~~~

固定预算：模块诊断/ACL 各 30 秒，语法每项 15 秒，编译 300 秒，Java 契约 60 秒，Node 每套 120 秒，PID 查询每次 5 秒，合成子进程就绪/正常退出各 10 秒，子进程自身兜底退出 60 秒。本次未触发兜底或增加预算。

关键指纹：

~~~text
本批工具/测试输入 ID（43 文件）
1502c2a9091412b4ab945b4f1d70cfbcad24a49b02bff301861f833b90b82b45
新编译包装器聚合（13 class）
9cba2424aab4c881dca6e27895c2e610905f11edf1e9b28f786401b24af904ad
repair-input.json
d8c84ef73bb3c419dd732d21eb27a4fc30a5ac8ad6149707a278a616ff9c6e9e
repair-result.json
e69ea219ba81002897dd393fc376aa880374d5237739b15e747ef4d2d72ee094
real-validation.json
3c553727ece833714cff595dbdb872a4ed459cbf339d7ccf7c3cbf261ae9b7d4
module-diagnostic.json
de50b47d286cbbf79557cf70e995d7f79fc34b94acef52f198e5334eeb96c80d
~~~

输入清单含工具来源、依赖、固定预算和历史保护清单；compiled/result/real-validation 保存实际产物及成绩。完整逐文件工具哈希、日志/命令回执和摘要在同目录 repair-delivery.json 及各 command.json、log。

本批前后核对原准备 a1 的 51 文件、原准备/运行证据 99 文件、早期 b1 快照/产物 39 文件、早期 b1 证据 43 文件，清单和哈希均未变。旧 private 仍为空。原始 FAILED 不改写为通过。

## 5. 未覆盖与交付停止点

本批**验证包不是已绑定新运行目标的可启动浏览器包**。为避免擅自启用下一轮，其冻结的 b1 绑定/Java 配置仍对应被保护的旧目标；没有运行 supervisor，也不允许拿此修复包直接 start 旧 b1。新运行目标获批后才进行目标绑定、最终封存和必要验证。

尚未覆盖：真实数据库初始化/迁移/角色核验、后端/代理就绪与关闭、完整邀请人工主线。合成进程通过不能替代这些实际行为。旧 PID 的历史不存在证明、本次未捕获的历史模块底层异常、宿主延迟的其他原因均未补写或宣称解决。

运行时密码、邀请令牌、JWT、MFA 密钥、恢复码未生成，也未进入聊天、日志或报告。没有修改旧 b1 ACL，没有改变全局模块路径/系统策略，没有外部模块安装、数据库/服务操作、临时唤醒、提交合并或生产开放。

## 6. 新运行目标与启动方案——待另行批准

建议下一次使用 **b2** 独立命名空间继续同一条邀请人工主线，保留全部 b1 失败证据和旧空目录。本次只读确认下列拟用根目录尚不存在，未创建；端口届时现场核验，本次没有声称它们始终空闲。

| 项目 | 拟定值 |
| --- | --- |
| 运行根 | D:\ERP_WMS\BC warehouse\2G\target\platform-browser-invitation-20260920\b2 |
| 数据目录 | 上述根\pgdata |
| 凭据目录 | 上述根\private；用本批已验证 ACL 逻辑收紧并读回，成功后才生成凭据 |
| 新准备根 | D:\ERP_WMS\BC warehouse\2G\target\platform-b2-preparation-20260920\a1 |
| 新证据根 | D:\ERP_WMS\BC warehouse\2G\docs\releases\platform-browser-invitation-20260920\b2 |
| 维护库 / 验收库 | postgres / p3_platform_browser_b2 |
| 角色 | p3_b2_initializer、p3_b2_migrator、p3_b2_app |
| 控制 schema | p3_b2_control |
| 监听 | 127.0.0.1:55459 / 18092 / 15174，分别为 PG / 后端 / 代理 |
| 产品 | 指纹不变的 F3 dist、r11 classes/资源/冻结依赖 |
| 固定工具 | Node 24.12.0、Java 17.0.17、PostgreSQL 18.6；不安装依赖/升级版本 |
| 执行身份 | 工具审批后核验 ARNOLD\Arnold 及已约定提升令牌上下文；不 RunAs、不修改系统策略 |
| 唤醒 | 不申请、不继承历史许可 |

拟议一次性批次顺序：

1. 仅做 b2 测试目标绑定，包括目录/库/角色/schema/参数和相应绑定断言；保留产品安全逻辑、隔离检查及业务断言。将本批修复接入最终冻结入口，补齐所有依赖。重新独立编译必要包装器，按最终清单执行无数据库门禁。保持完整原用例，不删除、跳过或从实际结果反推数量。
2. 封存最终启动命令、输入/产物指纹、预算和连接目标后，在同次授权内进行现场 Windows 身份/端口/目录检查。目录存在、身份不符、端口冲突、指纹或任一门禁失败即停止。
3. 仅新建空集群和新库；SCRAM、随机临时凭据、源码参考数据及合成 A。按已验迁移准备器建到 V4_90，核验运行角色无 SUPERUSER/BYPASSRLS 且不拥有业务表。F3/r11 指纹一致则沿用，不重新安排 r11 全套验收。
4. 启动后端及代理；只准备发起人 A，B 不预建，通过真实邀请获得安全审计员岗位，不授予租户读取/导出委派。建议新合成地址 inviter-b2@platform.invalid、invitee-b2@platform.invalid。
5. 就绪后逐步引导人工：邀请创建/转交 → 设置密码 → 首次 MFA → 保存恢复码 → 激活登录/刷新 → 退出后旧链接拒绝 → 正常再次登录。人工转交不算邮件送达；不以 API 或改库替代主线。
6. 主线完成才记 MANUAL_COMPLETED；否则 ABORT 或自动失败收尾，保留首因。按核实身份停代理、后端和数据库，检查残留并清除确切归属临时认证材料。失败不修补重跑，不自动开下一轮。

拟定调用形式如下；**目标绑定尚未执行、命令尚不可执行**，实际路径/参数需在上述第 1 步冻结后核验：

~~~powershell
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b2-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' start --authorized-b2-invitation-only-20260920
~~~

工具源文件名可以保留 platform-b1 前缀，但运行目录、库、角色及记录必须明确绑定 b2，不读取旧 b1 包装器或私有目录作为兜底。

拟沿用运行预算：初始化/迁移各 180 秒，ACL/SQL 各 30 秒，statement 15 秒，启动器 37 秒（内部 35 秒），PG 就绪 40 秒、独立收尾 60 秒（停止命令 32 秒），全量查询 15 秒、PID 查询 5 秒、就绪 SQL 4 秒，后端就绪/停止 90/30 秒、前端就绪/停止 10/10 秒。不得运行中加时。

本批到此停止，**新的 b2 绑定及数据库/服务/浏览器批次须另行批准**。正式发布整合、生产配置/域名/HTTPS/真实邮件/备份恢复及上线审批不纳入；恢复核对最终业务边界仍待决定。

## 7. 交付只读核对补充

正式门禁完成后的附加交付检查，在受限通道枚举已收紧 ACL 的 synthetic-private 空目录时返回 EPERM，命令退出 1。该次只读失败保留为通道权限观察，不追加算作通过的负例测试，也不改写正式门禁结果。

随后通过工具审批，在已批准执行通道完成同一只读交付核对，退出 0：43 个封存文件、13 个 class、结果证据、合成命令日志及历史文件哈希一致；旧 b1/private 与新合成目录仍为空；旧 b1/pgdata、拟议 b2 运行及准备目录均不存在。没有放宽 ACL、重跑门禁、查询旧 PID 或启动服务。结构化补充记录为证据目录中的 delivery-readonly-verification.json，所列报告哈希是添加本节之前的版本，不替代冻结输入或原始结果哈希。

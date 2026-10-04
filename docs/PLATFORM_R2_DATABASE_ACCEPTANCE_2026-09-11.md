# 平台后端 R2 隔离数据库验收批次：启动器失败，已关闭

日期：2026-09-11。**本批未通过数据库验收，尚非发布候选版本，不可上线。**

用户单独批准后端数据库专项先于前端阻塞处理。本次只建立后端验收快照，不建立正式 R2 全源码快照。地址测试 TS2769 继续交接；没有执行前端依赖安装、类型检查、整包构建或租户 ERP 开发。

## 1. 实际结果，不累加历史覆盖

| 项目 | 本批实际结果 |
|---|---|
| 后端源码、测试、资源、依赖、迁移封存 | 完成；两次输入封存及首次失败记录均保留 |
| 首次 a1 离线构建 | 依赖解析失败，未进入 Java 编译；不是产品编译错误 |
| 修正专用离线仓库来源声明后的 a2 全新编译 | 580 个主源码、192 个测试源码，编译与 JAR 打包成功 |
| a2 无数据库防护测试 | 36/36，0 失败、0 错误、0 跳过；不是数据库测试 |
| 新 PostgreSQL 集群 initdb | 成功；只生成系统目录、维护库及专用初始化角色 |
| 新 PostgreSQL 实际启动 | 成功，仅监听 127.0.0.1:55449；批次启动器等待失败 |
| 两个指定验收库 | **未创建** |
| 迁移及运行角色 | **未创建**；只有 initdb 的 `p3_r2_initializer` 已创建 |
| B4_71、V4_72、V4_85–V4_90 迁移/分步演练 | **0 步执行，未验证** |
| 真实权限、事务、并发及数据库套件 | **0 套件、0 用例执行，未验证** |
| 失败后关闭 | 正常 `pg_ctl stop -m fast`，exit 0；进程、PID 文件及监听均已消失 |

本次没有进入 JDBC/psql 建库或业务连接阶段。initdb 内部初始化成功不等于业务迁移成功。未产生本批业务测试 XML，不能把“没有失败测试”理解为通过。

## 2. 后端输入与产物对应关系

固定工具链沿用 Node 24.12.0、pnpm 11.16.0、Java 17.0.17、Maven 3.9.9；本批未调用 pnpm 安装或构建前端。PostgreSQL 可执行程序为本地既有 18.6。

基础分支 `2-1G`，提交 `9c12f6f893105632c4de24ad8bb4f6f43092f84e`。未提交、合并或推送。

### 封存 1 与离线构建失败

初始后端输入指纹：`e7aebd77c86afc36432ad2650ba2f683391c5610efe34aaae914557fa6db996e`。

首次构建的空 Maven settings 没有声明已有缓存的 `aliyun-public` 来源标识，因此 Maven 离线模式拒绝解析部分已缓存 JAR/POM。保留该快照及 `backend-compile.log`，未删除缓存标识、伪造来源、下载依赖或改公共配置。

已向用户说明差异后，只新增专用 `platform-r2-maven-settings.xml`，声明既有仓库标识并设置 offline；更新本批封存/执行脚本，再次封存。运行仍带 `-o`，未访问这些仓库 URL。

### 本批实际编译输入（封存 2）

完整指纹：

`8d13898cb50b9385d9aeb032c832c8d20b7da2abd2999452028b3f088081a2ff`

- 后端源码/资源/工具 904 个文件，包含 94 个迁移 SQL；源码组指纹：`fa8f270ffe992efbb7eb67aa3aa66dac8b0652daf51956934e3d6284284b7412`。
- Maven 依赖缓存实际封存 3,337 个文件、215,781,911 字节；缓存组指纹：`80daa9845f8b8840c30737bbc59ef4880840b581ca2d031847c197460a39dc48`。没有仅保存指向可变缓存的链接。
- 测试 classpath 的 136 个依赖 JAR 均记录路径与字节哈希。离线构建仅使用封存副本；这不是 Maven 严格锁文件或依赖漏洞审计。
- Maven 分发包也封存并记录哈希；外部 Node/Java/PostgreSQL 执行程序逐项记录哈希。
- 源码目录：`target/platform-db-acceptance-20260911/r2/snapshot2/source`。
- 新编译目录：`target/platform-db-acceptance-20260911/r2/build/a2`，未复用旧 classes。
- JAR SHA-256：`b579ea89e13f6cf7a7b928fdd2edc3914a364083333a383ac620f0955f0fa6ea`，79,940,476 字节。
- 重新生成的 1,177 个 class/迁移 SQL 文件与前置 p2 对应文件逐项比较，**差异 0**。JAR 整体哈希不同不被掩盖；本批以本批 JAR 哈希及完整产物清单追溯。
- 前置 p2 原输入 `97b1bdbe5e37025c3fbc60da02904d1502419b6e7c263c245210fe7601edfbc1` 复核仍无漂移；原 Java、业务测试正文、迁移、前端、POM 没有本批修改。

本批封存和结束复核均通过。历史 186 项单元测试、浏览器验收及前置 36 项测试不与本次 a2 的 36 项重复累加，也不补足数据库验证。

## 3. 本次失败：测试启动器，不是已证明的产品缺陷

时间均为 Asia/Shanghai：

1. 16:02:45，新集群初始化完成。
2. 16:02:47，PostgreSQL PID **50900** 启动，仅监听 **127.0.0.1:55449**，日志显示 ready。
3. 本批 `pg_ctl start` 实际输出 `server started`，最终退出码为 0；但 Node 执行器未及时结束等待，45 秒超时标记被触发。
4. 只读核对确认本批 `pg_ctl` 启动进程已不在，PostgreSQL 仍在；原有 PostgreSQL 服务启动器没有被操作。
5. 16:07:21–16:07:22，经精确身份核对，另用专用停止脚本正常关闭本批 PostgreSQL。随后被延迟的执行器返回失败并停止后续步骤。

从脚本和生命周期证据判断：执行器只在子进程 `close` 事件中结束等待和清除计时器，而后台 PostgreSQL 继承的管道使该事件延迟；启动器退出与所有管道关闭不是同一时刻。超时处理只尝试结束启动器，没有及时完成专用集群关闭。这是本批新执行工具的生命周期处理缺口。**没有据此修改产品逻辑、迁移、身份检查或断言。**

原始批次摘要中的 `pg_ctl start failed; exit=0` 表示执行器超时失败，不表示 PostgreSQL 自身启动失败；摘要中的 `stop.attempted=false` 也不表示集群未关闭——正常关闭由单独、经核实身份的停止脚本完成，详见 `checked-stop.json`。原始摘要保留，不重写成通过。

## 4. 关闭与边界证据

- 固定物理目录：`D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r2/pgdata`。
- 集群 system identifier：`7684185108789209944`。
- 被停止 PID：50900；PID 文件启动时间：1789113767；实际程序：`D:/ITsoftware/PostgreSQL/bin/postgres.exe`。
- 停止前核对 PID 文件、启动时间、目录、进程执行路径/命令行、127.0.0.1:55449 监听归属，全部匹配。
- `pg_ctl stop -D <固定目录> -m fast -w -t 30` exit 0；PostgreSQL 日志记录完成关闭检查点及 `database system is shut down`。
- 关闭后：PID 50900 消失、postmaster.pid 删除、55449 监听数 0。批次执行器也已结束，exit 1。
- 本批开始/结束监听核对：5432 始终为 PID 8980；8080、5173、55439、18091、15173 均未见监听。本批没有启动、停止或重启它们。
- 专用数据目录保留，不覆盖、不 clean/repair、不重新初始化；两个目标库仍未创建。
- 初始化口令仅随机生成于执行进程及短期文件；短期文件已删除，未归档明文口令。旧目录不视为可直接重跑、已准备完成的验收环境。
- 未操作真实账号、真实租户或真实数据库；未启动前端或 HTTP 服务；未提交、合并、推送或开放生产。

停止脚本是在故障后按已观察到的固定身份新增，仅执行关闭，不属于 Java 编译输入。其 SHA-256 为 `ab8479e4025f354eaddf7dafceb734e8a5208c55753745e586c546a2e54a689c`。

## 5. 未执行清单

迁移准备器的四个步骤 `prepare-release`、`upgrade-to-88`、`upgrade-to-89`、`upgrade-to-90` 均未调用；重复迁移零变更、升级哨兵保留、V4_90 不回填旧浏览器绑定均未验证。

| 已确认待验套件 | 计划原有方法数 | 本批执行 |
|---|---:|---:|
| PlatformSessionPostgresTest | 27 | 0 |
| PlatformInvitationSessionPostgresTest（仅 invitation*） | 4 | 0 |
| PlatformCompanyDataServiceIntegrationTest | 6 | 0 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 1 | 0 |
| PlatformAccessGrantRoleRaceIntegrationTest | 1 | 0 |
| TenantSecurityPostgresTest | 4 | 0 |
| TenantSecurityCommandsPostgresTest | 6 | 0 |
| TenantSecuritySpringPostgresTest | 14 | 0 |
| 合计 | **63** | **0** |

因此权限、真实事务、并发、会话、邀请、审计、暂停/双人恢复、受控导出、Webhook/连接联动均不能新增“本批真实数据库通过”的结论。

静态复核另发现夹具兼容风险：`PlatformAccessGrantRoleRaceIntegrationTest` 的 BeforeEach 直接创建固定的运营管理员/安全审计员角色，而 B4_71 已初始化同名公共角色，且角色编码有唯一约束；AfterEach 还会删除其创建的角色。后续需先确认只调整该测试的角色取得/清理夹具，不删除公共目录或放宽唯一约束、权限及竞争断言。**本批未运行此套件，因此这是静态发现，不计为已执行失败。**

完整人工邀请激活、最终恢复核对业务边界继续保留待确认。恢复核对仍为“缺单报告＋人工核验修改、取消和退款”，不视为最终方案已接受。

## 6. 建议下一批范围，尚未执行

1. 仅修复专用执行工具：区分启动器 `exit` 与管道 `close`；后台服务日志/句柄独立管理；超时或异常必须有独立、限时、校验身份的关闭路径。不能仅通过延长超时时间掩盖问题。
2. 增加无需 PostgreSQL 的生命周期回归，覆盖启动器退出但后代仍持有管道、超时、异常关闭及错误 PID/目录拒绝；不改产品安全逻辑或业务断言。
3. 核准上述公共角色目录与权限竞态测试的夹具适配范围；只改测试准备/清理，不修改产品或数据库约束。
4. 单独确认重跑环境安排。当前已初始化目录原样保留，不能自动重用、覆盖、改认证为 trust 或删除后重建。建议下一次采用另行批准的新批次目录，仍用 55449 专用端口；同步严格更新测试目标、重新封存并重编译，不自动换目标。
5. 新批次获批后再执行原四个迁移步骤、升级哨兵及 63 项实际数据库测试；失败/跳过/未执行分别记录。

**本轮停在失败交付及环境关闭，不自动重启或重跑。前端阻塞仍不等待代修，但其正式检查及完整 R2 候选版本继续保持未通过。**

## 7. 证据入口

- [最终后端输入清单](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/backend-input-manifest-v2.json>)：完整指纹、文件/依赖/迁移哈希、原有业务方法清单。
- [首次后端输入清单](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/backend-input-manifest.json>)：保留失败构建对应输入。
- [本批重新编译和无数据库测试](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/backend-build.json>)。
- [数据库批次原始脱敏摘要](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/database-result.json>)：FAILED、迁移/测试均 0。
- [经核实身份的正常关闭证据](</D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260911/checked-stop.json>)。

原编译/启动/停止日志留在本批忽略的 target 目录；公开交付没有原始 JWT、密码、MFA 密钥或恢复码。日志不得替代明确的用例执行证据。

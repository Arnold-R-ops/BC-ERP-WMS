# r3 平台后端隔离数据库验收结果

日期：2026-09-11。结论：**本批 FAILED，专用集群已正常关闭；不是发布候选版本，不可上线。**

启动器修复已通过本次真实 PostgreSQL 启动/关闭验证。主库空库迁移至 V4_90、增量库迁移至 V4_88 通过；随后专用工具的审计哨兵夹具违反既有约束，批次立即停止。**8 个数据库业务套件、63 个方法均未执行**，不能因为失败数为零就记为通过。

## 1. 授权执行与修改范围

本次依据用户完整 r3 授权执行目标绑定、封存、重新编译、无数据库回归、新集群创建/启动、实际迁移及失败后正常关闭。没有启动前端或 HTTP 服务，没有修改产品安全逻辑、迁移 SQL、公共默认数据源、POM 或锁文件，没有租户 ERP 开发、真实账号/租户/数据库操作、提交/合并/推送或上线。

本批只修改以下四个测试辅助文件：

- `src/test/java/com/wms/platformverification/PlatformR2Isolation.java`：精确绑定 r3 库、角色、目录和只读控制 schema。
- `src/test/java/com/wms/platformverification/PlatformR2IsolationTest.java`：同步 r3 夹具，增加旧 r2 正常 URL 必须被拒绝的一项参数化检查；没有降低原检查。
- `src/test/java/com/wms/platformverification/PlatformR2SpringInitializer.java`：专用导出路径绑定 r3。
- `src/test/java/com/wms/platformverification/PlatformR2DatabasePreparer.java`：角色和控制 schema 绑定 r3，计划说明同步。

新增 `platform-r3-backend-snapshot.mjs`、`platform-r3-database-batch.mjs` 两个专用脚本，以及本批记录。旧 r2 工具、失败报告、源码快照、pgdata 和关闭证据均原样保留。

为避免无关改名，`PlatformR2*` Java 类名、`p3.r2.*` 内部授权参数、`P3_R2_*` 进程内凭据变量和原测试配置文件名继续沿用；这些名称不是数据库选择器。实际唯一允许的目标已固定为下表，旧 r2 URL 会在连接前拒绝。原 63 个数据库业务测试方法正文指纹全部一致。

## 2. 输入、工具链与产物

| 项目 | 本批实际值 |
|---|---|
| 分支 / HEAD | `2-1G` / `9c12f6f893105632c4de24ad8bb4f6f43092f84e`，未提交 |
| 输入标识 | `94cd6c377b21404b4d0586b4ef6a76fe571bb830dc1e6faa5c339b580140b0b8` |
| 源码快照 | `target/platform-db-acceptance-20260911/r3/snapshot/source`，912 文件 |
| 新构建目录 | `target/platform-db-acceptance-20260911/r3/build/a1` |
| 工具链 | Node 24.12.0、Java 17.0.17、Maven 3.9.9；PostgreSQL 18.6 |
| 依赖隔离 | 专用副本 3337 文件，Maven 分发 90 文件，136 个测试类路径 JAR 均校验 |
| JAR SHA-256 | `286417d667ddb46949e352cd0d51702b66b97e387478dec4c665b76388fe8cf8` |
| 1178 个 class/SQL 产物聚合指纹 | `a52f91050e7027e2fc3759315cda11bf4b046834567ce62cc0e3c11c69ec80ba` |

基于上一批修复 a2 输入 `c3767d707e7fcc8c1915b4ccdfbdab59a399795077863e9330c628410fa54432` 核对差异后封存；没有复用旧 classes。编译输入是固定快照，不是运行中工作区。离线使用本批复制的 Maven 缓存/分发，不改全局 PATH、全局工具或版本声明。

新编译结果与上一批相比：产品 class/迁移 SQL 差异 **0**，测试 class 差异 10 个，其中包括 Java 常量内联导致的测试 class 更新，不代表业务方法正文变化。所有详细源码、工具、依赖、迁移和产物哈希在 JSON 清单中。结束后复核快照、工作区对应源码和编译产物，无漂移。

pnpm 11.16.0 的既定方案不变，本批没有执行前端依赖安装或 pnpm 检查。这是后端隔离验收快照，**不是正式 R2 全源码快照**。

## 3. 启动前实际验证

| 检查 | 结果 |
|---|---|
| 全新 Java 编译及打包 | 580 主源码 + 193 测试源码，BUILD SUCCESS |
| 隔离防护 | 37/37，零失败、错误、跳过；包括拒绝旧 r2 URL |
| 角色夹具无数据库测试 | 7/7，零失败、错误、跳过 |
| 生命周期回归 | 32/32，零失败、取消、跳过 |
| 4 个脚本语法检查 | 通过 |
| 原 63 个业务测试正文 | 与修复 a2 保持一致 |

上表都是本批新源码、新输出目录的实际结果，不累加历史测试。打包阶段 `skipTests` 只用于编译，随后独立执行上述测试；它不等于数据库测试通过。

## 4. 新环境及身份

| 项目 | 核验值 |
|---|---|
| 数据目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260911/r3/pgdata` |
| 监听 | 仅 `127.0.0.1:55449` |
| 主库 / 增量库 | `p3_m1_platform_release_r3` / `p3_m1_platform_upgrade_r3` |
| 专用角色 | `p3_r3_initializer` / `p3_r3_migrator` / `p3_r3_app` |
| PostgreSQL PID / 启动 epoch | `50012` / `1789126810` |
| 集群号 | `7684241129899895736` |
| 启动状态 | `READY` / `READY_VERIFIED` / 执行器 `SUCCEEDED` |

封存前 r3 批次目录不存在；初始化前 pgdata 不存在、55449 无监听。未覆盖任何已有目录，未回退到旧库。initdb 使用 SCRAM 认证，创建两个新空库和专用角色；只使用源码公共目录与合成数据。维护库 `postgres` 仅用于本新集群的角色/库初始化及身份核验。

真实启动后同时核验 PID、OS 进程启动时间、可执行文件、命令行目录、PID 文件目录、集群号、监听归属和 SQL 返回的数据库/用户/目录/监听/集群号。运行角色按非超级用户、无 BYPASSRLS 创建；**后续独立运行权限总核验尚未到达，不把角色创建等同于权限测试通过。**

## 5. 实际迁移结果

源码迁移清单保持 **94 个 SQL（93 个 V、1 个 B）**。当前授权空库路线实际执行的是 B4_71、V4_72、V4_85–V4_90，共 8 步，而不是把全部 94 个 SQL 都执行一遍。此路线不是生产数据库兼容整合方案，ERP 迁移/跨任务整合不由本批接手。

| 执行阶段 | 实际迁移 | 校验与重复执行 | 结果 |
|---|---|---|---|
| 主库 `prepare-release` | 空库至 V4_90，8 步 | Flyway 校验成功；重复 migrate 为 0 步 | 通过 |
| 增量库 `upgrade-to-88` | 空库至 V4_88，6 步 | Flyway 校验成功；重复 migrate 为 0 步 | 通过 |
| V4_88 后插入升级哨兵 | 审计哨兵被 CHECK 拒绝 | psql exit 3，停止后续流程 | 失败 |
| 增量库 `upgrade-to-89` | 未执行 | 用户/审计哨兵保留未验证 | 未覆盖 |
| 增量库 `upgrade-to-90` | 未执行 | 旧会话字段和空浏览器绑定保留未验证 | 未覆盖 |

已执行两库的历史版本、脚本名、类型、实际 Flyway checksum 和 success 均记录在 `database-result.json`。主库 V4_89 checksum 为 `178422383`，V4_90 为 `618333881`；各迁移文件的完整 SHA-256 在输入清单。两库重复 migrate 的零变更由准备器断言及迁移日志共同证明。

Flyway 对 PostgreSQL 18.6 发出兼容性警告：该 Flyway 版本声明已测试的最新 PostgreSQL 为 15。本次两段迁移实际成功不等于所有数据库特性或生产版本已经兼容验收；本批没有升级 Flyway/依赖。

## 6. 失败原因与保留状态

错误：`platform_audit_logs` 的 `ck_platform_audit_action` 拒绝 `R3_UPGRADE_SENTINEL`。

证据定位：

- `src/test/scripts/platform-r3-database-batch.mjs:187`：哨兵 INSERT 使用自定义审计动作 `R3_UPGRADE_SENTINEL`。
- `src/main/resources/db/migration/B4_71__SaaS_Empty_Database_Baseline.sql:1867`：动作 CHECK 仅允许既定动作，如 `WRITE`、`AUDIT_READ` 等，不包含该值。
- `target/platform-db-acceptance-20260911/r3/upgrade-sentinel-88.log`：真实数据库拒绝记录。

这是**专用演练工具的夹具遗漏**，不是数据库启动失败，也不是已证实的产品安全逻辑缺陷。旧工具中同类自定义动作也未符合此约束，本次首次实际到达该步骤而暴露。约束正确工作，不应增加例外、删除约束或改产品迁移来迁就夹具。

原结果 JSON 的 `failedStage` 仍是 `upgrade-to-88`（工具阶段变量尚未切换）；准确失败点是该迁移成功之后的 `upgrade-sentinel-88`，迁移本身没有失败。原 JSON 保留，不改写。

工具通过 psql 默认逐语句提交写入合成用户和审计。由执行顺序及错误可判断此前用户 INSERT 已提交，而审计 INSERT 失败；本次没有在关闭后重连读取残留行。保留整个 r3 pgdata，不删除、覆盖、修复或重用。

本轮未修改失败夹具后重试，也未执行 repair、clean、自动换目标或放宽断言。

## 7. 数据库业务测试覆盖

| 既定套件 | 计划方法 | 实际执行 |
|---|---:|---:|
| TenantSecurityPostgresTest | 4 | 0 |
| TenantSecurityCommandsPostgresTest | 6 | 0 |
| PlatformSessionPostgresTest | 27 | 0 |
| PlatformInvitationSessionPostgresTest（invitation*） | 4 | 0 |
| PlatformAdminInvitationMfaTransactionIntegrationTest | 1 | 0 |
| PlatformCompanyDataServiceIntegrationTest | 6 | 0 |
| TenantSecuritySpringPostgresTest | 14 | 0 |
| PlatformAccessGrantRoleRaceIntegrationTest | 1 | 0 |
| **合计** | **63** | **0** |

数据库套件执行 0/8；报告中的 skipped 为 0，因为未进入测试执行阶段，并非 63 个跳过后通过。**未执行 63 个，业务数据库验收不通过。** 权限、事务、并发、会话、邀请、审计、暂停/双人恢复、导出及连接联动均仍缺少本快照的数据库业务验收证据。已通过的 44 项无数据库测试不能替代这些证据。

## 8. 正常关闭及证据保护

批次于 2026-09-11 19:40:07（北京时间）开始；失败后按已核实 PID/启动时间/目录/集群号再次校验，使用 `pg_ctl stop -m fast -w` 正常关闭。不是按端口杀进程，也没有强杀其他服务。

- 2026-09-11 19:40:33 收尾完成：`STOPPED`、`completed=true`、停止命令 exit 0。
- 55449 无监听，r3 `postmaster.pid` 不存在，归属该目录/PID 的 PostgreSQL 及后代均不存在。
- 19:41:55 独立只读 OS 复核再次确认以上结果；r2 PID 文件同样不存在。
- 批次前后 IPv4 监听一致：5432 为 PID 8980，其余观察端口未变；独立复核还看到同一 PID 的 IPv6 5432 监听。没有操作这些服务。
- 临时初始化密码文件已清除，随机凭据不进入命令行或交付报告。原始认证材料不归档为发布证据；pgdata 属于受限隔离残留，不是交付运行包。
- 旧 r2 输入、pgdata 文件清单及哈希、失败/关闭证据均未改变；原失败报告 SHA-256 仍为 `1e1e396f22e8cddd679b3a690a0ec127ff825a3e6b200ae24c11087fc44b2b27`。

## 9. 下一批建议与未覆盖边界

下一批建议先批准**专用哨兵夹具修正与无数据库契约检查**：

1. 使用现有合法审计动作，将唯一哨兵标识放到适当资源标记或 detail_json 中；前后读取精确定位本夹具的用户/审计，不改审计约束和业务断言。
2. 哨兵准备采用明确事务，准确记录失败阶段；校验用户、审计、旧会话夹具与 V4_88/V4_89 的列和约束契约。
3. 保持所有 63 个原业务方法正文、权限及迁移检查不变，重新封存并在新目录编译/做无数据库回归。
4. 数据库重跑需要新的目录、库名和角色方案另行批准；不自动重新启动或复用本 r3，也不自动创建 r4。

前端 `addressLength.audit.test.tsx` SHA-256 仍为 `f7c75aaa8cb8896aea0d6bace0f59b7ba672b51d18111eecf4340603ad3ed44c`，锁文件 SHA-256 仍为 `7aab42712c6ca3f2a04b08a6dc217ba1ffb2edfd565a376add146f913f543b5e`。TS2769 继续由对应任务处理，本批没有重跑正式前端类型检查/整包构建，不能宣称阻塞已解决。

邀请完整人工激活继续待验；恢复核对仍为“缺单报告＋人工核验修改、取消和退款”，最终业务边界仍待用户确认。普通退出规则继续是“同浏览器、同账号会话一起退出”，不同登录的空闲计时独立。没有扩大第三阶段或开启第四、第五阶段。

**本批停止点：失败结果、未覆盖项、源码/产物对应及正常关闭证据完整。尚非发布候选版本，不可上线；没有自动执行下一批。**

## 10. 证据入口

- [输入与完整迁移清单](releases/platform-db-acceptance-20260911/r3/backend-input-manifest.json)
- [新编译及前置测试](releases/platform-db-acceptance-20260911/r3/backend-build.json)
- [真实迁移、失败和正常关闭结果](releases/platform-db-acceptance-20260911/r3/database-result.json)
- [源码/产物/日志哈希及 63 个待执行方法](releases/platform-db-acceptance-20260911/r3/closeout-evidence.json)
- [独立关闭复核](releases/platform-db-acceptance-20260911/r3/independent-closure-check.json)

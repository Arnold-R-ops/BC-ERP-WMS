# 合成夹具前置顺序修复及完整无数据库重验

日期：2026-09-20。结果：**a2 PASSED，仅代表测试工具的完整无数据库回归通过。**

本批未初始化、启动或连接数据库，未启动 Spring／前端／代理，未执行真实 OS 身份查询，未申请保持唤醒，未提交、合并或推送。真实启动故障是否消除仍需另行批准的新环境验证；不能据此认定邀请人工验收通过或允许上线。

## 1. 授权范围与实际修改

只修复合成夹具遗漏的前置顺序：

```javascript
async started(){await environment.inspect('synthetic-before-launch');const expected=environment.configure(CLUSTER); /* 原后续逻辑保持不变 */}
```

实际文件为 `src/test/scripts/platform-browser-runtime-safety.test.mjs`。`inspect` 使用已有模拟 `command` 返回的完整身份报告建立 context，然后才 configure → launch → observe → pin；没有调用真实 OS 查询、直接赋默认身份或跳过守卫。

与 a1 冻结文件逐字比较，仅允许上述一次插入。35 项测试正文中的断言全部保留；产品、迁移、原业务测试及实际环境工具均未改动。

另调整专用 `platform-browser-runtime-safety-verification.mjs`：输出绑定到新 a2，增加 a1 历史指纹保护及上述精确差异核验。这是封存／验证编排，不是运行时安全逻辑修复。其他实际运行工具、Java 包装器、JVM 参数工具和派生环境文件均与 a1 相同。

| 本次工作区文件 | SHA-256 |
| --- | --- |
| src/test/scripts/platform-browser-runtime-safety.test.mjs | 82f5803618e0e33fa1b6dae4baee1698deb6c615fda1f5d90a01230491877a57 |
| src/test/scripts/platform-browser-runtime-safety-verification.mjs | b5bfcc6502c87644d5254524ada005611910e3ffa0e95833b5fb3bbf05eba075 |

原 a1 FAILED、输出和冻结快照原样保留；本记录是新批次补充，不改写旧结果。当前 HEAD 仍为 `9c12f6f893105632c4de24ad8bb4f6f43092f84e`，保留其他任务未提交改动。

## 2. 新冻结输入与实际执行

准备根：`D:/ERP_WMS/BC warehouse/2G/target/platform-browser-runtime-safety-repair-20260920/a2`。

证据根：`D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-runtime-safety-repair-20260920/a2`。

本次命令只执行一次：

```powershell
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-browser-runtime-safety-verification.mjs' no-database-runtime-safety-only
```

使用 Node 24.12.0、Java 17.0.17、固定 Windows PowerShell 语法解析器；依赖为已封存的 136 个 jar。未安装依赖、修改声明／锁文件、重建 F3 或重跑 r11。必要工具权限审批用于读取受保护历史目录和执行本次无数据库验证，不是数据库启动许可。

冻结 49 文件；从新 `a2/snapshot/compile.args` 独立编译至新 `a2/classes`，生成 18 个 class。编译实际退出 0、耗时 1723 ms。class 聚合与 a1 相同是 Java 输入未变化、重新编译输出一致，**不是复制或复用 a1 classes**。

预算未调整：语法每项 15 秒；编译 300 秒；Java 契约 60 秒；Node 每套 120 秒；合成 JVM 每次 10 秒；输出排空 1 秒。输入清单记录于北京时间 23:12:25.459，全部验证及最终扫描结束于 23:12:38.513。

| 检查 | 本批实际结果 |
| --- | --- |
| 最终冻结清单语法检查 | 38/38 通过 |
| 独立 Java 包装器／探针编译 | 通过，18 class |
| Java 无数据库契约 | 16/16 通过 |
| Node 门禁 | 266/266 通过；失败／跳过／取消／待办均 0 |
| 合成诊断 Java 进程 | 16 次完成，包含在 38 项诊断门禁中 |
| JVM 入口属性实测 | 3 次完成，包含在 35 项运行安全门禁中 |
| 最终源码／产物／来源／历史核验 | 通过 |
| 合成证据清单及敏感哨兵扫描 | 两组均通过 |

266 项按套件拆分：生命周期 35、身份前置 40、父链 33、查询 16、环境 12、浏览器边界 19、依赖完整性 13、启动安全 25、诊断 38、运行安全 35。全部来自本次同一冻结输入的一次执行，不拼接 a1 的 261 项通过成绩。

本批 19 次真实 Java 子进程都是 JDK-only 合成探针，不是 19 次后端启动，不连接 JDBC。三次入口属性探针复用实际 `launchBrowserBackend` 的 JVM 参数构造，再将 main／classpath 替换为仅包含本次新 class 的合成探针：

| 探针 | 类初始化／main 入口属性 | 参数个数 | 退出码／耗时 |
| --- | --- | --- | --- |
| 正向 JVM 参数 | true／true | 0 | 0／92 ms |
| 去掉属性的负向对照 | false／false | 0 | 2（预期）／89 ms |
| 参数个数负向对照 | true／true | 1 | 2（预期）／94 ms |

三者均确认退出、未超时、输出完整；不输出参数内容。负向对照的非零退出按预期验证，没有强行记作失败或改成全零判据。

## 3. 五项分支核验

完整 TAP 中第 29—33 项均为 `ok`，不是仅验证“不再出现 TypeError”：

| 保留的覆盖目的 | 本次原断言实际通过 |
| --- | --- |
| server PID 复用 | 实际 lifecycle 收尾完成；模拟停止命令恰好 1 次；final closeout 确认；两次停止后检查均有 PID_REUSED；未调用 psql／taskkill |
| relay PID 复用 | 实际适配器的 stopOwnedDatabase 完成，独立 final closeout 父链检查通过 |
| 原进程仍存在 | nominal stop 后 isStopped=false，final absence.confirmed=false |
| 查询失败 | 抛出 PROCESS_QUERY_ACCESS_DENIED；诊断状态为 UNCONFIRMED，不当作不存在 |
| 停止前身份不符 | 严格身份检查阻止收尾成功；停止命令次数仍为 0 |

上述 OS／数据库命令均由既有可控 `command` 模拟。实际创建的子进程仅用于已批准的语法、编译及无数据库探针。停止前严格检查、停止后分类、父链／目录／集群号／监听约束未因夹具修复被放宽。

## 4. 指纹与最终证据

```text
输入聚合（49 文件）
b1a7e7b4d04de0e8478fb73e657370b090fd55a4cd6fff75b625fcfffd1ceb74
新编译 class 聚合（18 文件）
743bea36480531ee4a8a94fbed1f25b5781bb91bc5ffe344af59e1276d712941
input.json
0ec088d4798abc1ca8436e0e1359c4a2330ff45a00697c300c4bab4fe2615064
compiled.json
c70437292da951832c2729854607e612fbedafce5aa4e56905420490f6501fe7
result.json
0aa05cfd9d6c7b54e56cac788486652cc058fc0145995f1c39170b5c2fa387c2
完整运行安全门禁 TAP
905b72eeecef4856ae79c40027c529f89b62014831590c627c932c3f84315c6e
synthetic-diagnostic-results.json
e3e9e766f95a6387295772b57b4ae5de7f8334028b07cc50ba9462a8dbda90af
synthetic-runtime-safety-results.json
f665898a17c54b06152c8ccfb97e467b9e0b1be493f300a542261eaccad8fcd0
```

`input.json` 包含逐文件来源与哈希、工具、136 项依赖、语法清单、预期测试数量、预算及受保护历史清单。结束时重新核对冻结源码、class、工作区来源和原产品；`result.json` 已由实际执行入口落盘。

对两组合成证据执行了文件清单及已知敏感哨兵扫描，包括合成密码、JWT、MFA、恢复码、SQL 与参数内容标记；没有命中。本检查证明这些受控泄露样本未进入证据，不宣称可以检测任意未知敏感数据。

保持一致的产品基线：

| 产品 | 指纹 |
| --- | --- |
| F3 前端源码聚合 | ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d |
| F3 dist 聚合 | 7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c |
| r11 输入 | f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62 |
| r11 编译产物聚合 | 36a46c4ac127806483861b4032133c7074c9a10dcb4791c4bf1177d82658dfc7 |
| r11 jar | 7b7d977b504d8397d361d246052057cbc76abf2464feac84e1d4c7abea5120e6 |

这些是已验收产品的对应关系核验，不是本轮产品重新构建或完整验收成绩。

## 5. 历史与未覆盖项

本批执行前后逐文件保护：b3 准备目录 104 文件、b3 证据 183 文件、b3 已停止运行目录 1910 文件，以及修复 a1 准备目录 115 文件、a1 证据 114 文件，均保持一致。更早历史未修改。

- a1 仍为 FAILED，原 5 项夹具失败、日志和未完成最终汇总的事实保留。
- b3 仍为 FAILED_WITH_CLEANUP_ISSUES，原 PID 复用导致补核失败的记录不改写。
- b2 的未捕获原因仍未知，不倒填为本批诊断结果。
- 本批没有真实 PID 复用实测或真实 OS 身份查询；相关分支使用可控身份夹具。真实 JVM 属性生效得到证明，但这不等于原后端启动故障已经消除。
- 未启动数据库／Spring／代理，未进行邀请人工主线或邮件送达验收；未重复 F3／r11。
- 完整发布候选版本、生产配置、上线批准、恢复核对最终业务边界继续独立保留。

本轮已到停止点。a2 包仍含原 b3 身份作为测试输入，**不是可重新启动 b3 的运行许可，也不能直接当作 b4 启动包**。

## 6. 待批准：b4 一次性隔离启动／邀请验收方案

本节仅为下一批方案，尚未执行目标绑定或创建目录。读路径检查时下列三个新根均不存在；启动前仍需重新现场核验，不能据此认为端口空闲或未来目录仍不存在。

### 6.1 固定目标与版本

| 项目 | 拟固定值 |
| --- | --- |
| 准备根 | D:/ERP_WMS/BC warehouse/2G/target/platform-b4-preparation-20260920/a1 |
| 冻结入口／新包装器 | 准备根/snapshot、准备根/classes |
| 运行根 | D:/ERP_WMS/BC warehouse/2G/target/platform-browser-invitation-20260920/b4 |
| 数据／私有目录 | 运行根/pgdata、运行根/private |
| 证据根 | D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4 |
| 维护库／业务库 | postgres／p3_platform_browser_b4 |
| 控制 schema | p3_b4_control |
| 三个专用角色 | p3_b4_initializer、p3_b4_migrator、p3_b4_app |
| 数据库／后端／静态代理监听 | 127.0.0.1:55459／18092／15174 |
| 合成邀请人 A | inviter-b4@platform.invalid，仅准备此账号 |
| 合成受邀人 B | invitee-b4@platform.invalid，不预建；经邀请取得安全审计员岗位 |
| 执行上下文 | 工具审批后的 ARNOLD\Arnold 提升令牌；启动前重验 SID、令牌及查询能力 |

不授予 B 租户读取或导出权限。随机认证材料仅写入权限完整读回通过后的专用 private；ACL 主体仅本批预期 SID 与 SYSTEM、禁用继承。SCRAM 认证，不使用 trust；不复制旧账号、旧库或真实数据。

工具固定：Node 24.12.0（`D:/JAVA/NODE/node.exe`）、Java 17.0.17（`C:/Program Files/Amazon Corretto/jdk17.0.17_10/bin`）、PostgreSQL 18.6（`D:/ITsoftware/PostgreSQL/bin`）、既定 Windows PowerShell 5.1。只复核使用 F3 dist 与 r11 classes/resources/136 项依赖，不安装依赖、重建产品或重跑后端 1／42／63。

### 6.2 获批后的连续执行范围

1. 仅在测试层绑定 b4 目录、库、角色、schema、合成账号与授权标记；同步 JVM 参数辅助函数中的测试属性前缀及必要契约文字，保持断言目的和实际安全逻辑。保护 b1～b3、a1 FAILED、a2 及更早证据。
2. 先检查完整依赖清单和产品指纹，再封存新输入、独立编译包装器，完整执行 Java 16、Node 266、最终冻结语法清单及同一链路的 19 次合成 JVM 探针。语法数量按 b4 最终清单封存，不把本修复专用 verifier 错算成 b4 运行依赖。
3. 封存精确命令、预算、连接身份与新产物指纹；核对实际启动命令和封存命令一致，`-Dspring.devtools.restart.enabled=false` 必须位于主类之前。零参数、工作目录和隔离守卫不变。
4. 现场核验执行身份／查询能力、新目录不存在、三端口无冲突。先建立并读回私有目录 ACL，再生成随机凭据；任何检查不符立即停止。
5. 一次初始化、一次启动新 SCRAM 集群，核验目录、进程路径、所有者、创建时间、启动父链、集群号、监听及只读 SQL 身份。创建本批数据库／专用角色，执行既定空库迁移至 V4_90：B4_71、V4_72、V4_85～V4_90，共 8 条历史；核对 94 个资源、校验和及再次 migrate 零变更。确认运行角色非超级用户、无 BYPASSRLS、不拥有业务表、不能改迁移历史。只使用公共参考数据与合成夹具。
6. 后端仅启动一次。失败则保留实际阶段、白名单原因码、异常链和安全栈位置；不现场修补重启。成功后启动静态代理，验证页面、回环目标和隔离边界。用户能配合时再进入人工主线。
7. 每次引导一个操作：A 登录及必要 MFA 准备 → 创建安全审计员邀请并安全人工转交 → B 设置密码 → 首次 MFA 绑定 → 保存恢复码并确认 → 激活登录 → 刷新 → 退出后旧链接拒绝 → 正常再次登录。B 不预建，不以 API／改库代替激活，不把人工转交记为邮件送达。认证材料不进入聊天／报告／普通日志。
8. 完成或失败均只关闭确切属于本批的代理、后端和数据库，核验退出、监听、目标目录进程、PID 文件及父链残留，清除确切归属的临时明文材料，保留脱敏证据和数据目录。人工验收结果与关闭结果分别记录。

停止后 PID 复用仅使用已通过的严格证据分类：须有正常停止结果及完整新进程身份／创建时间，加上所有独立残留检查；信息缺失或查询失败仍为未确认。启动前、停止前严格身份校验不变；不终止复用 PID 的新进程。

### 6.3 命令与预算

以下是**批准并完成 b4 目标绑定后**的命令设计，当前不要执行；工作目录固定为 `D:\ERP_WMS\BC warehouse\2G`：

```powershell
# 准备：只封存、独立编译和无数据库门禁
& 'D:\JAVA\NODE\node.exe' 'src/test/scripts/platform-b1-prepare.mjs'

# 运行：仅从新 b4 冻结入口启动
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b4-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' start --authorized-b4-invitation-only-20260920

# 仅人工主线确已完成时使用
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b4-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' stop MANUAL_COMPLETED

# 中止／未完成时，改用此命令，不与上一条重复调用
& 'D:\JAVA\NODE\node.exe' 'D:\ERP_WMS\BC warehouse\2G\target\platform-b4-preparation-20260920\a1\snapshot\platform-b1-batch.mjs' stop ABORT
```

所有底层完整命令（含新类路径、迁移、后端和代理）在启动前写入新 `input.json.commands`，实际调用须匹配；不把当前 a2 的 b3 绑定参数直接用于 b4。

| 操作 | 固定预算 |
| --- | --- |
| 独立编译／Node 每套／Java 契约／语法每项 | 300／120／60／15 秒 |
| 合成 JVM／诊断输出排空 | 10／1 秒 |
| 初始化／迁移 | 各 180 秒 |
| ACL／一般 SQL／SQL statement | 30／30／15 秒 |
| 启动器／其内部命令 | 37／35 秒 |
| PG 就绪／独立收尾／停止命令 | 40／60／32 秒 |
| 全量查询／PID 查询／就绪 SQL | 15／5／4 秒 |
| 后端就绪／停止 | 90／30 秒 |
| 代理就绪／停止 | 10／10 秒 |

人工操作逐步进行，用户暂停或不方便继续时使用 ABORT 正常收尾，不把人工等待纳入已完成结果。不自动延长命令预算；不申请临时唤醒，不模拟键鼠或改系统策略。

### 6.4 停止条件与待确认项

目录已存在、端口冲突、版本／来源／产物／身份／ACL 不符，任一门禁失败、超时、非预期流程或证据不完整，均停止后续并按可核实身份收尾。首因与收尾异常独立保留；不自动修补重跑、换目标、repair／clean、创建下一轮或放宽断言。发现产品缺陷先报告修复范围。

需要用户确认的是上述 **b4 单次完整批次**：必要目标绑定、重新封存编译与门禁、新环境创建和迁移、一次启动、逐步人工邀请验收及关闭。工具权限审批照常执行；获批后可连续进行，不逐阶段重复业务确认。

不包含租户 ERP、真实数据、既有服务操作、提交合并、生产配置或上线；不继承 r11 唤醒授权。通过也仅交付该 F3／r11 配对版本的隔离邀请验收记录，不自动进入生产或新增批次。

# r11＋F3收尾＋b4平台已验收交付基线：统一交付主索引

日期：2026-09-21。状态：**非正式发布候选版本、未批准上线**。

本索引、拟提交白名单、公共片段说明为持续更新的三个交付说明。当前包含授权后的提交前收尾：两项既有成果原样承接及T1测试工具编译接入/无数据库验证。它们仍不是暂存、提交、合并、归档搬迁或启动环境的授权。产品、普通业务测试、迁移、依赖版本、锁文件及公共POM均未改。

## 1. 标识、出处和当前对应关系

- 名称：**r11＋F3收尾＋b4平台已验收交付基线**。
- 当前工作区：`D:/ERP_WMS/BC warehouse/2G`；分支 `2-1G`；参考 HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`。
- 产品来自含未提交改动的冻结源码；**HEAD 不能单独标识已验收内容**。未来 Git 提交必须补充与下列逐文件清单的对应关系，不能倒称历史测试执行于未来提交。
- 最初整理开始时：58 个已跟踪文件修改、1229 个未跟踪状态项，暂存区为空；加入既有三份索引后共1290个状态项。本轮只更新这三份。未跟踪不等于平台所有权；其中有其他任务的目录项，未递归检查其内部。Git 全局 ignore 读取权限警告原样保留，没有更改权限。
- 2026-09-21 10:19:31 北京时间只读核验：r11 当前/冻结产品源码及 POM 679 文件、原编译清单 1204 项、原 JAR；F3 当前/冻结所选源码 236 文件、dist 262 文件；b4 冻结源 48 文件及包装 class 18 项，逐项指纹差异均为 0。**这是文件核验，不是重新运行测试或验证生产环境**。完整 node_modules 未作逐字节重验，依赖安装证明沿用 F3 收尾的已说明边界。

### 1.1 冻结输入和产物

| 对象 | 精确标识/位置 |
| --- | --- |
| r11 后端输入 ID | `f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62` |
| r11 源码集合 ID（1007 文件，含测试） | `fdb27624a70268d1a01f2c2d2ec1c4b17c9d608089d047e346ad0d35ffa20fd8` |
| r11 冻结源码 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260919/r11/snapshot/source` |
| r11 新编译目录 | `D:/ERP_WMS/BC warehouse/2G/target/platform-db-acceptance-20260919/r11/build/a1` |
| r11 JAR SHA-256 | `7b7d977b504d8397d361d246052057cbc76abf2464feac84e1d4c7abea5120e6` |
| r11 编译清单聚合（1204 项，含测试） | `36a46c4ac127806483861b4032133c7074c9a10dcb4791c4bf1177d82658dfc7` |
| F3 前端源码 ID | `ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d` |
| F3 dist 聚合 | `7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c` |
| F3 源码/产物 | `D:/ERP_WMS/BC warehouse/2G/target/platform-frontend-acceptance-20260919/f3/snapshot/frontend` 及其 `dist` |
| F3 pnpm-lock.yaml SHA-256 | `7aab42712c6ca3f2a04b08a6dc217ba1ffb2edfd565a376add146f913f543b5e` |
| F3 收尾工具快照 ID | `b8f9cc9187236a5e2a79ce3e7e0a1abb3cc486695201a9ee1c0cd2ee6f36a61e` |
| b4 测试包装输入 ID | `da326580be0ef0d1f5787e06148fae89fe1f62e79095286b979dabe3087b55b2` |
| b4 包装 class 聚合 | `fc53056f2b864fd44746052b5929649efce24e321e7fef8f5d7587fc84d65760` |
| b4 包装源码/产物 | `D:/ERP_WMS/BC warehouse/2G/target/platform-b4-preparation-20260920/a1/snapshot`、`a1/classes` |
| b4 已关闭运行环境（保留，不重用） | `D:/ERP_WMS/BC warehouse/2G/target/platform-browser-invitation-20260920/b4` |

r11 的 136 项运行/测试依赖逐项哈希在 input manifest 的 `testClasspath`；冻结 Maven 缓存与发行包分别在 `snapshot/maven-repository`、`snapshot/apache-maven-3.9.9`。固定工具链：Node 24.12.0、pnpm 11.16.0（前端）、Java 17.0.17、Maven 3.9.9、Surefire 3.1.2、PostgreSQL 18.6（已完成的隔离批次）。不升级、不安装、不迁移。

**后端是平台/ERP 共用工程，前端 dist 含两个入口及 sourcemap。四组分类不等于可分别部署；整包构建通过不代表 ERP 全部运行时验收。sourcemap 不因此获得公开提供许可。**

## 2. 交付功能与验收结论

第一、二阶段继续沿用，不重复认领既有管理员目录、邀请、启停、MFA、授权和审计实现。

| 归属 | 本基线包含的范围 | 主要记录 |
| --- | --- | --- |
| G1 P3-M1 | 租户状态/时间、安全模型、租户会话版本、API/导出/任务共用准入、紧急安全暂停、双人恢复、必要连接/Webhook/告警联动；延期入口继续关闭 | [docs/PLATFORM_P3_M1_IMPLEMENTATION_2026-09-09.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_P3_M1_IMPLEMENTATION_2026-09-09.md>) |
| G2 平台会话 | 后端30分钟空闲、12小时绝对期限、撤销、并发活动检查、失效记录清理；保留租户ERP原登录规则 | [docs/PLATFORM_SESSION_BACKEND_DELIVERY_2026-09-09.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_SESSION_BACKEND_DELIVERY_2026-09-09.md>) |
| G3 浏览器退出 | 同浏览器同账号会话一起退出；其他浏览器不随普通退出失效；全设备退出另行撤销；失败退出不误报服务端成功 | [docs/PLATFORM_BROWSER_LOGOUT_SCOPE_CHANGE_2026-09-10.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_BROWSER_LOGOUT_SCOPE_CHANGE_2026-09-10.md>) |
| G4 验收工具与记录 | 隔离、权限/事务/并发、结果解析与夹具工具，以及前端构建和浏览器证据；不是生产运维框架 | 下列 r11/F3/b4 证据链 |

普通退出不意味着跨标签自动共享登录；不同登录的空闲计时仍独立。退出不取消已经成功提交的审批，后台任务继续按既有权限及状态检查执行。

| 历史执行/补验 | 实际结果及边界 |
| --- | --- |
| r11 | 独立1/1、顺序42/42、完整63/63；失败/错误/跳过/未执行/无法核实均0。106是累计执行次数，不是106个独立方法；迁移、夹具收尾、集群关闭和临时唤醒释放通过。164项Java、698项Node等门禁是该次历史成绩，不在本轮累算。 |
| F3 原执行 | 正式类型检查、整包构建、平台86项通过；原流程随后在报告身份比较处 FAILED，原失败保持不变。 |
| F3 收尾 | 原86项报告只读复核通过，新执行冻结地址16项、工具契约25项和语法5项；不是重跑全部步骤。TS2769及正式构建阻塞已解除。地址业务规则问题不在平台任务内。 |
| b4 | 用户逐步反馈＋确切合成账号检查点：人工转交邀请、密码、首次MFA、恢复码保存确认、激活、刷新、退出、旧链接拒绝、再次登录通过。原监督器 MANUAL_COMPLETION_REPORTED / manualAcceptancePassed=false 不改写；独立 acceptance-assessment 作人工通过结论。 |
| b4 收尾 | 2026-09-21 00:26:32 收尾落盘，00:28:20 最终复核；代理、后端、PG正常关闭及残留检查通过，临时认证文件清除。关闭记录与人工通过分别成立。 |

详见 [docs/PLATFORM_R11_DATABASE_ACCEPTANCE_2026-09-19.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_R11_DATABASE_ACCEPTANCE_2026-09-19.md>)、[docs/PLATFORM_FRONTEND_F3_CLOSEOUT_2026-09-19.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_FRONTEND_F3_CLOSEOUT_2026-09-19.md>)、[docs/PLATFORM_B4_INVITATION_ACCEPTANCE_2026-09-20.md](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_B4_INVITATION_ACCEPTANCE_2026-09-20.md>)。

历史报告中“地址测试阻塞”和“邀请主线待验”仅代表当时状态，由 F3 收尾与 b4 后续记录解除，不回写历史。真实邮件送达、生产兼容与上线事项仍未验收。r5实际异常码、b2未知原因等历史未知项不因后来成功而补填。

## 3. 迁移索引和跨任务边界

完整94个SQL（93个V＋1个B）的名称、字节数与SHA-256见 r11 input manifest 的 `migrationFiles`，本轮只读核对保持一致。以下六个未提交增量必须完整保留：

| 文件 | SHA-256 |
| --- | --- |
| [src/main/resources/db/migration/V4_85__Tenant_Security_Foundation.sql](<D:/ERP_WMS/BC warehouse/2G/src/main/resources/db/migration/V4_85__Tenant_Security_Foundation.sql>) | `f943c8a74c11b89132b249c641c589d6dbd4d26066d35626e230cda5c2a586ce` |
| [src/main/resources/db/migration/V4_86__Tenant_Security_Commands_And_Integration_State.sql](<D:/ERP_WMS/BC warehouse/2G/src/main/resources/db/migration/V4_86__Tenant_Security_Commands_And_Integration_State.sql>) | `0d0fd8beb0413449ba444fbcea6406f200481e5edcd5a808ea6472f6d0127f85` |
| [src/main/resources/db/migration/V4_87__Tenant_Compromised_Credential_Fingerprints.sql](<D:/ERP_WMS/BC warehouse/2G/src/main/resources/db/migration/V4_87__Tenant_Compromised_Credential_Fingerprints.sql>) | `c19b784630d9bd1fed98fd052a1558aeea82bc80811b6dbf7c6817968cf5be97` |
| [src/main/resources/db/migration/V4_88__Tenant_Webhook_Copy_Retention.sql](<D:/ERP_WMS/BC warehouse/2G/src/main/resources/db/migration/V4_88__Tenant_Webhook_Copy_Retention.sql>) | `b41110ecf667d51e2bffaf1f6ffd1414045bb6d542a5eff6343c0f2b3b9dccd2` |
| [src/main/resources/db/migration/V4_89__Platform_Backend_Sessions.sql](<D:/ERP_WMS/BC warehouse/2G/src/main/resources/db/migration/V4_89__Platform_Backend_Sessions.sql>) | `ef9714e1a79c6ab0e37dea6709f03bc6c66bc0240046e02a70c5158dc9f657b7` |
| [src/main/resources/db/migration/V4_90__Platform_Browser_Session_Scope.sql](<D:/ERP_WMS/BC warehouse/2G/src/main/resources/db/migration/V4_90__Platform_Browser_Session_Scope.sql>) | `12a355a7707a06e060b23a3c40902fb189404def1e9b59afa4c7d62e0df9a1c2` |

- 空库实测：B4_71 → V4_72 → V4_85 → V4_86 → V4_87 → V4_88 → V4_89 → V4_90，成功历史8条；94资源校验并非执行94条新迁移。
- 升级实测：至V4_88为6条 → V4_89为7条 → V4_90为8条；校验和、确切哨兵、约束、重复迁移零变更通过。
- V4_89新增平台后端会话；V4_90依赖它，实际字段是 `browser_binding_hash`，保留旧会话空绑定的迁移断言及重新认证边界。
- 当前工作区不含ERP分支V4_73～84及其三项权限收敛迁移回调。这里只登记依赖，不复制、排序重写、合并、repair或启用out-of-order。生产完整序列须统一整合任务核准。
- Flyway 9.22.3 对 PostgreSQL 18.6 的既有支持范围警告保留，隔离实际通过不替代生产兼容演练。
- app角色已验NOSUPERUSER、NOBYPASSRLS、不拥有业务表，不将隔离数据库/角色用于生产。

## 4. 逐文件证据入口（只读引用，不等于拟提交或归档搬迁）

以下哈希为本轮读取原件得到。完整原始日志、XML、冻结源码、构建包和数据库继续保留原位置；本次不搬移、上传、删除或修改。28项均是**证据引用**，不是默认整目录纳入Git。

| 批次 | 原件 | SHA-256 |
| --- | --- | --- |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/backend-input-manifest.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/backend-input-manifest.json>) | `6faefc5ab260931cbc1d378fbef826f82a4c3b1ef391dd62220b7c427aa9c228` |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/backend-build.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/backend-build.json>) | `03bb27e945129c824c9a0e47338a662f6b9a9789c104781b1b08c57da2753e8b` |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/database-result.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/database-result.json>) | `eacd796c4a4d4096120da4468aa4eca38ff502c489121536cf350b2afa14af0f` |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/phases/independent.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/phases/independent.json>) | `7c25f83bf416581c30ef5599c0bbe0df8d51f805edbb950b4b6c568f426d3f2d` |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/phases/sequential.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/phases/sequential.json>) | `4830f95bb3f6c164e1edab05fbf264e81cf146d000bd1a7f14d6109ddd453b50` |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/phases/full.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/phases/full.json>) | `b1dd485522d0597de7dab30a82005199c33a531af57af57851222f746535da69` |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/awake-result.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/awake-result.json>) | `f7068a60e3775588142b37c27adb2664379e02f77f0abbf1fda1f827261fc5aa` |
| r11 | [docs/releases/platform-db-acceptance-20260919/r11/delivery-review.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-db-acceptance-20260919/r11/delivery-review.json>) | `247af763b559c21a6fd5a92b85f1d77330420b5cce28782b0c8d8cbaea891204` |
| F3 | [docs/releases/platform-frontend-acceptance-20260919/f3/source-manifest.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3/source-manifest.json>) | `31219a48d73dab66d83c138f0bca01941587bbfe6c0d60d541939193e8a80855` |
| F3 | [docs/releases/platform-frontend-acceptance-20260919/f3/artifact-manifest.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3/artifact-manifest.json>) | `ce58f1ec36b230ba6db42b000cf0ff71b4d41f8a7f1e11b437c1b776437c0801` |
| F3 | [docs/releases/platform-frontend-acceptance-20260919/f3/build-failure.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3/build-failure.json>) | `d6d8dae5e69f31f8c85ae286f3e5b8328d9b2cc708d71aafa0e31b07fe5f3e17` |
| F3 | [docs/releases/platform-frontend-acceptance-20260919/f3/platform-expected.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3/platform-expected.json>) | `8069aff6d45294e34dc9aed4edd997a6b30262c57d949d70b8b0c58cfcf3a25b` |
| F3 | [docs/releases/platform-frontend-acceptance-20260919/f3/platform.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3/platform.json>) | `56000f930e40ca4538192d97ed91735b5fd9131e0528e3d10fd2b6a4aa7465c9` |
| F3 | [docs/releases/platform-frontend-acceptance-20260919/f3/platform.assessment.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3/platform.assessment.json>) | `c20e30a6e6158f8c16975f11f3564def23f705a9577b075909b161b310b69e0c` |
| F3收尾 | [docs/releases/platform-frontend-acceptance-20260919/f3-closeout/plan.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3-closeout/plan.json>) | `1a337e380b6e4216aee6efa6f1b1bad26bd67b207d308a1d361cb6667d1a002c` |
| F3收尾 | [docs/releases/platform-frontend-acceptance-20260919/f3-closeout/closeout-result.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3-closeout/closeout-result.json>) | `47098def6155784715072327d31540b1c5fa1b488881172287be80c86467cf71` |
| F3收尾 | [docs/releases/platform-frontend-acceptance-20260919/f3-closeout/original-platform.reassessment.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3-closeout/original-platform.reassessment.json>) | `4a7b6492f276400646198c44b4027c99eea2c696f88ba9fa83657aed45176290` |
| F3收尾 | [docs/releases/platform-frontend-acceptance-20260919/f3-closeout/frozen-address.assessment.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3-closeout/frozen-address.assessment.json>) | `941d8ceb6e1305207221d4818ca82cad247b37dd493f8ed543f5c600ba81b485` |
| F3收尾 | [docs/releases/platform-frontend-acceptance-20260919/f3-closeout/input-before.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3-closeout/input-before.json>) | `f27d42896e9ef71e95df869596f53f5b48b5e5c1256dc98954abaa88f4c1aad9` |
| F3收尾 | [docs/releases/platform-frontend-acceptance-20260919/f3-closeout/input-after.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-frontend-acceptance-20260919/f3-closeout/input-after.json>) | `8a2c990672057f9845bb1464d2e55b8bcdc6459921589e9a5bd4b6db1ff051d4` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/input.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/input.json>) | `9eb92e3672b600de72d6853a68c9dd06c7129ca1ff61069b51e21903450ed625` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/compiled.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/compiled.json>) | `77b585b0376c3b5a93181cc04182ac3f8e74757f3c0928ddd80b09813a87eea9` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/preparation-result.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/preparation-result.json>) | `14024fbb84a544e6a1975bfb0a94fd82b3ecd4141d77e8076c9fc902c73fa05f` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/target-binding-ledger.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/target-binding-ledger.json>) | `669facd2c9d4ef370bf5c21f43ad8c2fd92167b6b721579ca2e6208cb0aa9c1c` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/acceptance-assessment.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/acceptance-assessment.json>) | `38677bffef2712b549213e2d17c89b5678e8071167d92f9f6ba8a250a9103bfb` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/environment-final.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/environment-final.json>) | `55506579bc4f80a7e0ace354a38b072f8e93182e2384f75b659a61c620079884` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/final-integrity-check.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/final-integrity-check.json>) | `75e062021b515386bb5e5d2aef980f3c020385a87f0b9d06a2e3b710a5a09966` |
| b4 | [docs/releases/platform-browser-invitation-20260920/b4/delivery-index.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-browser-invitation-20260920/b4/delivery-index.json>) | `2ab1cc3691a19b68e77898189df68226285d8d5b419959093ff6f5e19be8de18` |

白名单中只列拟纳入源码/必要工具/说明。原始证据暂不追加整批Git提交；本主索引提供路径与哈希。未来确定受控归档位置和负责人后，另行批准归档，并核验同一原件；本批不作迁移。历史失败证据保留，不将旧结果合成新成绩。原始密码、邀请令牌、JWT、MFA密钥和恢复码不进入提交或归档包。

## 5. 平台提交前收尾：E1/E2/T1闭合

### 5.1 原样承接与源码边界

用户已明确授权为保全已验收版本承接两项已有成果，不把它们全部认领为平台贡献：

- **E1**：TenantProvisioningService中既有ERP saveAndFlush修复，当前SHA `69e9de7a590bffbd2003860aecd9e25d75ab107b6a57812f98a664d53cde4d8b`，与r11冻结源码一致。能核实源码ERP A2注释和P3-M1实施记录第18行的“未提交修复”；本地全部可见Git引用未找到该精确修复的独立提交。原提交/原作者无法核实，不推测。
- **E2**：地址测试完整文件，含全部16项用例/断言与平台第23行选择器修复，SHA `1b51209d1b0678505be0436bc92edd775652a5932affa784116c12057c7b024e`，与F3冻结文件一致。该文件未跟踪，本地可见Git历史未查得来源；原提交/原作者无法核实。
- 两项均原样承接进入C1，前后指纹未变，无已发现的并行冲突；没有ERP分支合并或ERP功能开发。来源说明与用户承接授权分开记录。
- **T1**：5个无JUnit注解的专用浏览器程序原样移至src/test/tools/browseracceptance/java/com/wms/browseracceptance；prepare共用显式生成函数，3项工具仅做生成/读取路径接入，新增2项针对性验证工具/契约。未增加公共Maven排除项或历史target依赖，没有复制旧class。细节及新指纹见[公共片段说明](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_SHARED_HUNK_DEPENDENCIES_2026-09-21.md:747>)及[白名单](<D:/ERP_WMS/BC warehouse/2G/docs/PLATFORM_PROPOSED_COMMIT_WHITELIST_2026-09-21.md>)。

### 5.2 本轮实际验证（不改写原验收）

首轮a1的新增契约最后一项因Windows路径分隔符比较失败，11/12通过；当轮Maven/包装编译和16项Java契约已通过，但整体仍为FAILED。原目录、输出及失败记录保留。仅修正新契约的路径比较后，在全新a2完整重验，不拼接成绩。

| a2实际执行 | 结果和边界 |
| --- | --- |
| 固定工具链 | Node 24.12.0、Java 17.0.17、Maven 3.9.9；使用冻结缓存的新副本，离线，无安装/依赖升级 |
| 原POM Maven test-compile | 通过；206个普通测试Java文件与编译器输入清单完全对应；189个含JUnit声明的源文件保留，不代表执行了这些测试 |
| 专用javac | 5个原样程序＋2个生成依赖重新编译，18个class；仅引用本次新Maven产品classes及已校验依赖JAR |
| Java无数据库契约 | 16/16通过 |
| Node受影响检查 | 154/154通过：既有受影响142＋新增生成/边界契约12；失败、跳过、取消、todo均0；包括真实合成JVM及监督器链 |
| 语法检查 | 5/5通过 |
| 原产品/证据保护 | r11产品源码/POM679项、产品编译951项及JAR，F3源码/dist，b4冻结源码/包装class及旧证据前后未变；a1失败证据亦未变 |
| 未执行 | 数据库/迁移/真实启动、r11全套业务回归、F3整包重建/地址测试重跑、b4人工验收均未重复 |

输出根 `D:/ERP_WMS/BC warehouse/2G/target/platform-precommit-closeout-20260921/a2`：
maven-source为普通源码快照，snapshot为专用冻结工具/生成源，maven-build为全新Maven输出，classes为新包装产物。编译各300秒、Java契约60秒、Node套件120秒、版本/语法各15秒，执行前固定、未运行中加时；无临时保持唤醒。

- T1输入ID：`8698605682f36c9894b8d1b97c2256b75153df77a2e950f37a39f016fdfeefda`。
- T1普通编译清单聚合：`3d9d7e929750b8c763e5f6544fcd63861dd4ecc413d0f338a2320e22fb516286`。
- T1专用class聚合：`fc53056f2b864fd44746052b5929649efce24e321e7fef8f5d7587fc84d65760`，新编译所得，与b4相同；不是旧class复制。

| 本轮证据原件（仅索引，不默认入Git） | SHA-256 |
| --- | --- |
| [a2/input.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-precommit-closeout-20260921/a2/input.json>) | `cdc598a168c5acd21547aff0233347c621b8926fdfc55e6cd8a7d66c450599b0` |
| [a2/ordinary-compile-assessment.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-precommit-closeout-20260921/a2/ordinary-compile-assessment.json>) | `a9e06d005eba9c31b725c08425a02c3827d7f9c754ced236b1e3d888e6ef0e7c` |
| [a2/result.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-precommit-closeout-20260921/a2/result.json>) | `c42fe91211cb920ccba7a658453b1fd53f7c26b3e23b81cb3fe9c9082f03e42a` |
| [a1/failure.json](<D:/ERP_WMS/BC warehouse/2G/docs/releases/platform-precommit-closeout-20260921/a1/failure.json>) | `e8a080b07da2e9be555b1ba0bcf0dab034dcc6c4ba8ee4732cd014c6112f647b` |

精确命令/参数/预算、退出及超时记录保存在同目录的maven-test-compile.json、standalone-javac.json、各gate/assessment JSON与日志。本次只证明编译闭包和所列无数据库检查；不能称执行了全部业务测试，不能升级原r11/F3/b4为正式发布候选版本。

### 5.3 最终提交范围与剩余问题

原白名单现有346项逐文件记录：317项可提交、29项范围外不纳入；加现有三份索引，共**320个拟提交文件**。建议严格按表内C1/C2执行：

1. **C1：134项**完整G1＋G2/G3、迁移、安全公共依赖及必要配套测试，包含E1/E2原样承接。
2. **C2：186项**其余专用工具与说明，含本轮T1、47份历史说明和三份索引。

不裁剪安全依赖；两组不是分别部署单元。当前没有暂存构造这两个中间态，不宣称其分别发布验证通过。E1/E2/T1在本批范围内已闭合；**仅等待用户随后批准按最终精确白名单执行本地提交**。如果实际提交前工作区或指纹变化，先报告，不扩入其他任务成果。

专用工具仍读r11/F3/b4冻结内容、部分历史修复证据和本地工具链，详见公共片段§4.3。公共Maven普通测试编译不再缺失浏览器生成类，但“源码已提交”仍不等于干净检出能完整复现全部历史验收。原始证据/私有材料/构建与数据库不混入Git，归档负责人和位置未定前不搬移/删除/上传。

正式提交前须核对实际暂存内容与Git行尾规范化映射；工作区SHA不是Git blob，不能倒称历史测试在未来提交上执行。当前HEAD仍为 `9c12f6f893105632c4de24ad8bb4f6f43092f84e`。三份索引自身哈希在后续批准提交时记录，避免自引用。

## 6. 仍保留的开放门禁

恢复核对仍为“缺单报告＋人工核验修改、取消和退款”，最终业务边界待用户决定；保持现有开放门禁，不默认用户已接受人工方案，不加入完整自动核对。

首发开放范围、正式环境与人员、域名/HTTPS、生产密钥、真实邮件告警、最小数据库权限、备份恢复/迁移演练、统一发布整合与上线审批另批确认。计划暂停、关闭/恢复、自动清理、网址回收、计费、通用任务/完整集成中心、SSO、多区域、对外API及第四/五阶段继续延期。

最初整理后只读核验：剔除当时三个新文档，原1287个状态项的路径/状态/文件字节聚合SHA-256仍为 `fad6f31a02d388d36b557b3c556ab9ffeccdf3560af0ed502daacdff89d75424`，与整理前一致；其中目录项只作占位，不递归检查其他任务项目。暂存区仍为空，文档引用路径存在，未搬移原始证据。本核验不替代产品测试或归档完整性审计。

**本次提交前收尾完成授权的测试工具接入和无数据库编译/契约验证，并更新这三个既有说明。没有暂存、提交、合并、推送、数据库/服务操作、临时唤醒或生产开放；本基线不是正式发布候选版本。**

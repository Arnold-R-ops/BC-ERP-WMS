# F3 前端依赖恢复与正式构建：实际结果及取证核验停止记录

日期：2026-09-19。**工作区依赖已恢复；地址测试、正式类型检查、整包构建和平台测试的实际命令均通过。批次因取证工具的用例名称格式比较错误停止，原 FAILED 保留，不标为整批通过或发布候选版本完成。**

本轮没有修改业务代码、依赖声明、源锁文件、公共配置或检查范围。没有启动数据库、前后端服务、临时唤醒，没有提交合并或生产操作。f1/f2 中断记录原样保留。

## 1. 保护行为与依赖恢复分别确认

专用工具仍为 `src/test/scripts/platform-frontend-f1-build.mjs`，本次仅绑定新 f3 并修正所授权的行为检查；未修改 pnpm 分发包。固定 Node 24.12.0、pnpm 11.16.0，原有预算不变。

### 禁止隐式安装：实际行为通过

专用有效工程只声明一个 `file:./local-dependency` 合成依赖，未安装；独立存储、离线配置，不使用工作区 node_modules 或共享存储。子进程沿用 `pnpm_config_verify_deps_before_run=error`，执行 `pnpm run probe`。

- 实际返回 1，输出 `ERR_PNPM_VERIFY_DEPS_BEFORE_RUN`，379 ms 内正常结束。
- 哨兵脚本未执行，未创建 node_modules，独立存储为空；合成输入清单及哈希未变。
- 该非零是**预期拒绝通过**，没有放宽其他安装、测试、构建的退出 0 标准。
- 不再使用 `config get` 的展示结果作为特殊运行时环境变量生效的唯一依据。

证据：`docs/releases/platform-frontend-acceptance-20260919/f3/no-implicit-install-probe.json`。

### 工作区依赖：已恢复且复核通过

显式执行冻结安装，指定原存储 `D:/.pnpm-store` 和 f3 专用缓存。安装退出 0，耗时 14.056 秒；日志记录 265 个包复用、包下载 0，保留供应链检查，没有改锁或升级。没有清理共享存储或其他项目依赖。

安装后核验 Vitest 4.1.10、TypeScript 5.9.3、Vite 8.1.4 的入口、`.modules.yaml`、安装锁副本、工作区状态元数据均存在；元数据的实际存储为 `D:\.pnpm-store\v11`。安装锁与源锁 SHA-256 一致：

`7aab42712c6ca3f2a04b08a6dc217ba1ffb2edfd565a376add146f913f543b5e`

后续副本安装使用 f3 下独立存储，仅在副本建立新 node_modules。副本构建结束后再次只读核对：工作区六项已登记依赖入口/元数据指纹未变化，没有再次重建工作区依赖。236 个前端输入无额外变更。

恢复确认独立记录在 `dependency-restoration-result.json`，不以测试是否通过代替恢复确认。

## 2. 本次实际成绩

| 步骤 | 来源/范围 | 实际结果 |
| --- | --- | --- |
| 地址边界测试 | 当前工作区，同一 236 文件源码指纹 | 16/16 通过，无失败或跳过；16.428 秒 |
| 正式类型检查 | 工作区 `pnpm run typecheck` | 退出 0；8.054 秒 |
| 新副本冻结安装 | f3 独立存储、`--frozen-lockfile` | 退出 0；19.333 秒 |
| 副本正式类型检查 | 新冻结源码、独立依赖 | 退出 0；6.723 秒 |
| 正式整包构建 | 原 `tsc --noEmit && vite build` | 退出 0；7.590 秒，两个入口均生成 |
| 平台用例预先收集 | 13 个文件，源码清单在执行前冻结 | 86 项，收集命令退出 0；6.650 秒 |
| 平台测试实际执行 | 同一冻结源码、13 个文件 | 86/86 通过，无失败或跳过；9.739 秒 |
| 原工具清单比较 | 显示名称格式不匹配 | **失败，批次停止** |
| 冻结副本追加地址复测 | 原工具安排在上述核验之后 | **未执行**；不冒充又通过一次 |

平台实际分项：api 25；browserLogout 7；mfaFeedback 3；platformAccessGrantStatus 3；PlatformAdminManagementPage 5；PlatformAuthProvider 9；PlatformInvitationActivationPage 3；PlatformTenantAccessProvider 4；PlatformTenantManagementPage 4；sessionActivity 14；storage 2；TenantBrowser 3；TenantSecurityPanel 4。

仅地址及平台文件执行了运行时测试；正式类型检查和构建没有缩小范围，租户 ERP 其余运行时测试未执行。所有成绩来自本次，不拼接历史结果。

测试日志中仍有 React Router v7 future flag 和 Ant Design `destroyOnClose` 弃用提示；副本依赖下载有低速警告。这些没有导致命令失败，未借机修改产品或升级依赖。

## 3. 取证失败原因与只读复核

2026-09-19 22:13:32 北京时间，原工具在平台测试退出 0 后比较清单时报错，批次退出 1。

当前 Vitest 4.1.10 的两个接口输出不同：

- `vitest list --json` 使用 `TestCase.fullName`，层级以 ` > ` 分隔。
- JSON reporter 的 `fullName` 使用空格分隔，并另外保留 `ancestorTitles` 与 `title`。

专用工具直接比较两种显示字符串，导致 79 个嵌套用例被错误记录为“缺失/额外”；7 个顶层用例未受影响。**这是本任务取证工具的格式适配问题，不是平台业务测试失败。** 冻结前没有发现该适配差异，本次没有在失败后自动修补重跑。

只读复核本次原始 JSON：以文件名及结构化 `ancestorTitles`、`title` 重建收集接口的名称，逐项比较预先冻结的 86 项，同时检查 JSON 自身 `fullName` 与其结构字段一致。结果为 86 对 86 一一对应，重复 0，内部名称不一致 0，所有实际状态 passed，头部数量亦一致。

这份复核只解释实际结果，**不替换原流程 FAILED**。下列原件未改写：

- `build-failure.json`：`d6d8dae5e69f31f8c85ae286f3e5b8328d9b2cc708d71aafa0e31b07fe5f3e17`
- `platform.assessment.json`：`c20e30a6e6158f8c16975f11f3564def23f705a9577b075909b161b310b69e0c`
- `platform.json`：`56000f930e40ca4538192d97ed91735b5fd9131e0528e3d10fd2b6a4aa7465c9`
- 预期清单 `platform-expected.json`：`8069aff6d45294e34dc9aed4edd997a6b30262c57d949d70b8b0c58cfcf3a25b`

新增只读结论在 `docs/releases/platform-frontend-acceptance-20260919/f3/readonly-final-review.json`。未生成代表整批成功的 `build-result.json`。

## 4. 源码、锁文件与产物对应关系

分支 `2-1G`，HEAD `9c12f6f893105632c4de24ad8bb4f6f43092f84e`；这是包含已有未提交成果的源码快照，不是正式提交或整合版本。

- 新源码副本：`target/platform-frontend-acceptance-20260919/f3/snapshot/frontend`。
- 236 文件源码 ID：`ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d`。
- 源码清单 SHA-256：`31219a48d73dab66d83c138f0bca01941587bbfe6c0d60d541939193e8a80855`。
- 本批冻结工具 SHA-256：`9ecdf50ae8c5baafda6776c5112f37a01f2d39951e7261164b5ac5463395cf71`，失败后未改动。
- 新 dist：上述源码副本下 `dist`，262 文件，16,504,528 字节；逐文件哈希在 `artifact-manifest.json`。
- dist 聚合 SHA-256：`7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c`。
- 产物清单 SHA-256：`ce58f1ec36b230ba6db42b000cf0ff71b4d41f8a7f1e11b437c1b776437c0801`。
- `platform.html`：`e919a13788de2f4e7e6d9610b3296c27145e63fdeb5e41f4b94ee801cd7c971b`。
- `index.html`：`820a49518a3549dff11a9730b14a3a13dff2c6ef839de394c5d91b40abe12479`。
- 平台主 JS `assets/platform-B41VIFMu.js`：`e122640293d427d0bfb5acc330729e85ebb1566e07fce052ff23125816a32948`。

只读收尾核对源码副本、工作区源码及全部 dist 指纹，差异均为 0。保留原构建 sourcemap，但这不授权向外提供 sourcemap 或部署租户入口。未复制原始认证材料、私有 `.env`、旧 dist 或旧 node_modules 作为新产物。

地址文件仍只保留已授权的一行修复，SHA-256 `1b51209d1b0678505be0436bc92edd775652a5932affa784116c12057c7b024e`；依赖声明和锁文件不变。

后端 679 个产品文件/公共 POM 与 951 个产品编译文件逐项匹配 r11，JAR 仍为 `7b7d977b504d8397d361d246052057cbc76abf2464feac84e1d4c7abea5120e6`。继续沿用 r11，不重复数据库验收。f1/f2 已登记历史证据指纹无变化。

## 5. 停止点与建议的最小后续范围

命令回执已确认各已完成子进程退出；批次包装器返回 1。没有启动服务、数据库或唤醒请求，无需重启/关闭其他服务。

**原地址 TS2769 已不再阻塞本冻结源码的正式类型检查和整包构建。当前待处理的是报告核验适配，不是 ERP 功能或平台产品修复。**

建议下一批只确认以下小范围：

1. 修正专用工具的用例身份比较，使用报告结构字段适配收集清单，保持漏项、额外项、重复、失败和跳过检查，不用简单全局替换名称来掩盖问题。
2. 对本次原始报告做只读契约回归；保留原 FAILED，不需为了报告格式问题再次恢复依赖、重建整包或重跑 r11。
3. 在未变化的冻结前端副本上补齐原工具尚未执行的地址复测和最终指纹核验，单独记录追加证据，不倒填本次原始结果。若源码/产物变化，先报告。

当前不自动执行这些步骤，也不提出已可启动浏览器或生产开放的结论。邀请激活主线仍待真实浏览器验收（人工转交链接不计邮件送达），恢复核对最终业务边界、发布整合、正式域名/HTTPS/密钥/邮件及运维演练仍独立保留。浏览器 `15174 / 18092 / 55459` 创建和启动须另行确认。

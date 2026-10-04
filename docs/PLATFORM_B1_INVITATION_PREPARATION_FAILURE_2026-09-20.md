# b1 邀请激活浏览器验收：准备门禁失败记录

日期：2026-09-20。**本批停在无数据库准备门禁，未启动数据库、后端或前端，未进入人工验收。原始 FAILED 和已生成副本保留，不自动修补重跑。**

## 1. 已确认的失败原因

专用封存工具复制了共享 `platform-process-lifecycle.test.mjs`，但遗漏其真实 Windows 管道继承用例引用的 `fixtures/PlatformPipeInheritance.cs`。这是本任务封存工具的遗漏，不是产品缺陷，也不是 PostgreSQL 启动失败。

生命周期套件第 31 项 `native Windows launcher exits while its short-lived descendant still owns inherited pipes` 调用固定 C# 编译器时，返回 **CS2001：源文件未找到**，继而触发 `exitCode === 0` 断言失败。

实际缺失目标：

`target/platform-browser-invitation-20260919/b1/snapshot/fixtures/PlatformPipeInheritance.cs`

原文件存在于 r11 冻结输入的 `snapshot/source/src/test/scripts/fixtures/PlatformPipeInheritance.cs`，本批没有移动或修改它。没有通过删用例、跳过测试或放宽断言处理失败。

失败后只读检查还发现，后续未执行的共享套件引用了未封存的文件：

- `platform-launch-preflight.test.mjs` 导入并读取 `platform-r7-database-batch.mjs`；该模块还有传递依赖。
- `platform-process-ancestry.test.mjs` 读取 `platform-s1-start-stop.mjs`。

这些是**只读发现的封存缺口**，不能记录成已经运行失败的测试。当前停点不允许自动补入后重跑。

## 2. 实际执行结果

准备入口：固定 Node 24.12.0 执行 `src/test/scripts/platform-b1-prepare.mjs`。本批没有调用 pnpm 安装、F3 构建或 r11 业务测试。

| 步骤 | 实际结果 |
| --- | --- |
| F3 产品输入/产物核对 | 236 前端输入、262 产物匹配 |
| r11 产品/依赖/工具核对 | 679 产品文件、951 产品编译文件及 JAR 匹配；准备前核验冻结 136 项 classpath 依赖和固定外部工具 |
| 封存工具语法检查 | 17/17 退出 0 |
| b1 测试包装器独立 javac | 退出 0；2.026 秒；生成 13 个 class 文件，不重编译产品 |
| Java 无数据库隔离契约 | 16/16 通过；0.190 秒 |
| 共享生命周期套件 | 实际 35 项，34 通过、1 失败、0 跳过；命令退出 1，2.684 秒 |
| 后续 5 个 Node 套件 | **未执行，共计划 120 项** |
| Windows 实际身份/端口前置核验 | 未执行 |
| 集群初始化、迁移、数据库角色/合成账号创建 | 未执行 |
| 后端/代理启动、邀请人工流程 | 未执行 |

后续未执行的计划：launch-preflight 40、process-ancestry 33、process-query 16、b1-environment 12、b1 专项 19。不能把语法或编译通过当作这些行为检查已经通过，也不能沿用 r11 成绩填入本次门禁。

原生命周期测试框架在一个套件内执行完全部 35 项后返回失败；准备器据此立即停止后续套件。没有重复运行这个套件或只补跑失败项。

## 3. 本批代码和对应关系

本批只新增以下测试层工具，未修改产品、迁移、约束、业务断言、前端源码或既有工具原件：

- `src/test/java/com/wms/browseracceptance/PlatformB1Harness.java`
- `src/test/java/com/wms/browseracceptance/PlatformB1Contract.java`
- `src/test/scripts/platform-b1-binding.mjs`
- `src/test/scripts/platform-b1-prepare.mjs`
- `src/test/scripts/platform-b1-batch.mjs`
- `src/test/scripts/platform-b1-proxy.mjs`
- `src/test/scripts/platform-b1.test.mjs`

准备器从 r11 已封存测试层模板生成 b1 单库身份、迁移准备器和 PG 启停适配，保持产品类/资源不变。新包装器编译通过不等于其服务运行或关闭已验证。

新工具按授权目标绑定 `15174 / 18092 / 55459`、`p3_platform_browser_b1`、`p3_b1_initializer/migrator/app`，并预置无回退、SCRAM、运行角色限制、A 单账号准备、B 真实邀请、禁调度/真实邮件/外部 HTTP、脱敏检查点及独立收尾路径。**这些启动及服务行为尚未取得实测通过证据。**

封存输入 ID：

`702341809919a9463626bd03e4147db2f2ec6d0ac609441f728e9d80f72f329e`

本次已生成 13 个测试包装器 class 的补充聚合 SHA-256：

`749fb28dea5723203265b6de715aa511f5f116f88dbb5c34027dbfe18b9a3411`

此哈希只标识失败批次的部分产物；没有生成代表全门禁成功的 `compiled.json` 或 `preparation-result.json`。编译结果逐文件指纹补记在 `preparation-failure-review.json`，不改写原回执。

F3 源码 ID 仍为 `ddcfd04c9950eff5eb65caad42d7eb5adfe449e278743c761fafa216bfba512d`；dist 聚合仍为 `7fbb61633bc2df9a8d7f527e1c653ee7958a1ddf67f1c9f960777f000acee94c`。r11 输入仍为 `f4603f9f3f572d139256e1577a27d061e5cfc3d50507325a0ad59fecb836ea62`，JAR 为 `7b7d977b504d8397d361d246052057cbc76abf2464feac84e1d4c7abea5120e6`。失败后再次只读核验，产品与封存工具指纹均无变化。

## 4. 原始证据和收尾状态

根：`target/platform-browser-invitation-20260919/b1`。

证据：`docs/releases/platform-browser-invitation-20260919/b1`：

- `input.json`：输入清单、工具/依赖指纹、固定预算及拟执行命令。
- `independent-javac.json/.log`、`java-no-database.json/.log`：已执行编译/Java 契约回执。
- `gate-platform-process-lifecycle.test.mjs.log`：35 项完整原始 TAP。
- `gate-platform-process-lifecycle.test.mjs.json`：退出 1、非超时、输出完整、测试进程结束确认。
- `preparation-failure.json`：原始 FAILED，不修改。
- `preparation-failure-review.json`：本次补充统计、实际产物指纹及停止边界。

准备包装器已返回退出码 1。已执行的 20 个编译/语法/测试命令回执均记录进程结束确认；生命周期中的真实 Node 超时用例也通过了仅终止自身子进程的断言。C# 管道夹具因编译前缺源文件而未启动，其用例自身 finally 已退出。

只读确认 b1 `pgdata`、`private`、`start-attempt.json`、`environment-ready.json` 均不存在。**没有初始化/连接数据库，没有启动数据库或 HTTP 服务，因此不存在“本批服务正常关闭通过”的成绩；应记为未启动、关闭不适用。** 未进行 OS 进程/端口查询，不能据此声称整台机器没有其他进程或监听。

实际隔离密码/JWT/MFA 密钥尚未生成，临时认证材料清除不适用。没有申请临时唤醒、改变既有服务或系统策略、提交合并、创建下一批环境。历史 F1～F3、r11 和更早证据保持不动。

## 5. 建议的下一步范围（待批准）

只修复专用封存工具的依赖完整性：保留相对目录，明确列出实际门禁需要的源码夹具、直接和传递导入、字面量文件读取依赖，并在创建新验证输出目录之前完成完整性预检。不要只补一个 `.cs` 后凭运气继续，也不扩建通用测试框架。

使用新的专用准备验证目录，保留本 b1 FAILED、副本和部分产物；重新封存/独立编译并完整执行既定无数据库门禁，不能拼接本次已通过项。不删除共享用例、不改产品或安全断言。

当前不实施该修复、不自动重跑或启动。本次尚未到可提供人工登录指引的状态；修复及后续继续浏览器验收的执行范围须由用户重新确认。最终邀请人工验收、发布整合与正式上线条件仍未完成。

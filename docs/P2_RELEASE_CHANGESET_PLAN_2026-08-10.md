# P2 发布变更分组与 CI 构建准备

日期：2026-08-10

## 结论

发布变更已完成分组，标准 CI 入口已建立，前端依赖锁已验证可离线复现。本轮没有执行 `git add`、提交、推送、标签或远程 CI。

由于当前变更横跨 V4.31 至 V4.49，且部分 Controller、DTO、前端页面同时承载多个阶段的修改，不建议按历史任务机械拆成许多文件级提交；那会产生迁移已存在但代码未存在、前端契约与后端不一致等不可构建中间状态。

## 最终变更分组

| 分组 | 入口数 | 内容 |
|---|---:|---|
| 后端主代码 | 100 | Controller、Service、Repository、实体、DTO、安全与事件 |
| Flyway 迁移 | 19 | V4.31 至 V4.49，必须保持连续顺序 |
| 后端测试 | 32 | IAM、权限申请、仓库范围、采购、归档、JWT、迁移契约和集成回归 |
| 前端与契约 | 66 | 权限驱动页面、IAM/申请/审批、采购编辑、测试、OpenAPI 和类型 |
| 验收脚本 | 3 | HTTP 安全预检与权限申请验收入口 |
| 文档 | 19 | 规则、契约、验收、归档、交接、路线图和本发布计划 |
| CI/发布配置 | 5 | `.gitignore`、`pom.xml`、前端清单、CI 脚本和工作流 |
| 合计 | 244 | 全部当前发布候选入口均已归类，无未分类入口 |

## 建议提交结构

以下只是待用户授权后的建议，不代表已经执行：

### 1. 应用代码、迁移、契约与自动化测试

范围为后端主代码、V4.31—V4.49、后端测试、前端源码及实时 OpenAPI 契约。该组应作为一个可构建的功能原子提交，避免拆出失配的中间状态。

建议路径：

```text
src/main/java/
src/main/resources/db/migration/
src/test/java/
frontend/src/
docs/frontend/openapi.json
```

### 2. 验收脚本与交付文档

范围为 `docs/`（不重复包含上一组的 OpenAPI JSON）以及三项既有验收脚本/清单。

### 3. CI 与发布门禁

范围为：

```text
.github/workflows/ci.yml
.gitignore
pom.xml
frontend/package.json
src/test/scripts/invoke-ci-verification.ps1
```

每个提交完成后都应重新运行 CI；若使用交互式 hunk staging 拆分同一文件，必须在每个中间提交上单独证明编译和测试通过。

## CI 构建入口

新增 `src/test/scripts/invoke-ci-verification.ps1`：

- `Check` 模式只核对工具、POM、前端清单、`pnpm-lock.yaml` 和 OpenAPI 快照。
- `Run Backend` 固定使用 `test` profile 和 `wms_db_test`，执行 `mvn -B -ntp clean verify`。
- `Run Frontend` 执行冻结锁安装、142 项前端测试、生产构建和 OpenAPI 类型漂移检查。
- 本机没有 Maven 时明确失败，不回退到历史 class 文件或手工制品。

新增 `.github/workflows/ci.yml`：

- Java 17 + PostgreSQL 18 独立测试数据库。
- Node 24 + pnpm 11.16.0。
- 后端、前端并行，最终 release gate 要求两者全部成功。
- 后端无论成功或失败都上传 Surefire 报告。
- 权限仅为 `contents: read`，流水线不发布、不推送、不写外部业务系统。

`pom.xml` 已显式启用 Java `-parameters`，避免 Spring 缓存/安全 SpEL 丢失参数名；Surefire 已显式纳入 `ApiEndpointTestSuite` 和 `CompleteModuleIntegrationTestFixed`，正式 Maven 回归不会再漏掉额外 58 项集成测试。

## 依赖可复现性

- 仓库已有并跟踪 `frontend/pnpm-lock.yaml`，锁版本为 9.0。
- `frontend/package.json` 已声明 `pnpm@11.16.0` 和 Node `>=22.12.0`。
- 已在独立空目录使用 `pnpm install --frozen-lockfile --offline` 还原 265 个包，下载数为 0，证明当前锁与本机缓存中的正式包完全匹配。
- CI 统一使用 pnpm，不再尝试从 pnpm 目录反向生成 npm 锁。

## 排除与敏感信息检查

以下本机内容已确认被 Git 忽略：

- 根目录及前端 `node_modules/`。
- `target/`、`logs/`、`backups/`。
- `.claude/settings.local.json`。
- `application-local.yml`。

当前生产配置中的数据库密码与 JWT 密钥均使用环境变量占位。固定密码、JWT 和批次盐只存在于 `src/test/resources`，且明确为独立测试环境使用。历史提交凭证轮换仍属于正式发布前的外部运维确认项。

## 本地验证结果

- CI PowerShell 脚本语法：通过。
- POM XML 与前端 JSON：通过。
- pnpm 冻结锁离线安装：通过。
- 新 CI 入口前端完整运行：44 个文件、142 项测试通过；生产构建及 OpenAPI 类型漂移检查通过。
- 本机后端 Check 模式按设计因缺少 Maven 返回失败并给出明确提示；后端源码与 1289 项 JUnit 已在上一轮独立验证通过，正式 Maven 结果等待远程/CI 运行。
- `git diff --check`：通过，仅有既有 LF/CRLF 提示。

## 剩余发布门禁

1. 用户确认是否按上述三组暂存和提交。
2. 推送分支并让标准 CI 首次真实运行。
3. 审阅 CI 的 Maven/Surefire 报告及制品。
4. 完成历史凭证轮换确认。
5. 用户明确批准后才可创建发布标签或部署。

下一任务是“按批准的分组暂存/提交并触发首次 CI”。这是会改变 Git 和远程状态的操作，必须获得明确授权。按 `CODEX_TASK_MODEL_POLICY` 建议使用 **GPT-5.6 Sol + XHigh（L5）**。

# P2 最终只读收尾审查

审查日期：2026-08-09

## 结论

用户已确认的 P2 功能开发范围通过收尾审查。权限申请、简易审批、角色授予/撤销、JWT 失效、IAM 越权与审批隔离、采购单 `ORDERING` 编辑、历史测试数据安全归档、迁移及真实 API 验收已经完成；归档造成的销售事实滞后也已通过事务提交后定向刷新关闭。

本结论只表示“当前确认功能范围通过”，不表示整个产品路线图全部完成，也不表示已经正式发布。工作区仍有大量未提交改动，本轮没有执行 Git 提交、推送或创建标签。

本轮只执行代码/文档读取、健康与 OpenAPI GET、测试和构建，没有调用任何业务写接口。

## 最终证据

| 核验项 | 结果 | 证据 |
|---|---|---|
| 运行服务 | 通过 | Java 进程 PID 38244 监听 8080；`GET /health/check` 返回 HTTP 200、状态 `UP` |
| Flyway | 通过 | `V4_48__Repair_Archived_Order_Sales_Summaries.sql` 已成功应用，schema 当前为 `v4.48` |
| 后端回归 | 通过 | 最新 Surefire 报告共 125 个套件、1287 项，失败 0、错误 0、跳过 0 |
| 前端回归 | 通过 | 本轮重新运行 44 个测试文件、143 项，全部通过 |
| 前端生产构建 | 通过 | `tsc --noEmit && vite build` 成功 |
| OpenAPI | 通过 | 实时 `/v3/api-docs` 与 `docs/frontend/openapi.json` 语义完全一致；原始文本仅存在 JSON 属性顺序差异 |
| TypeScript 契约 | 通过 | 从当前 OpenAPI 快照重新生成的类型与 `frontend/src/api/schema.d.ts` 逐字一致 |
| 工作区格式 | 通过 | `git diff --check` 无空白错误；仅有 Git 提示未来可能进行 LF/CRLF 转换 |
| 历史归档 | 通过 | 订单 13/14和任务 1/2/3保留并转为 `VOIDED`；审计 ID 1、2；预留、库存流水及批次数量/预留未改变 |
| 归档后报表 | 通过 | 实时 `VOIDED` 订单和销售概览均为 17；V4.48 已修复监听器上线前的派生事实 |

说明：本项目正式健康端点是 `/health/check`。`/actuator/health` 未暴露，会被全局异常处理映射为 500；这是既有已记录问题，不能把该路径作为服务健康判断依据。

## 完成状态分类

### 当前确认范围：已完成

- 空白创建权限包、逐项勾选权限、受保护权限过滤和后端独立校验。
- 自定义权限包单级简易审批、审批审计、启停和不可原地修改规则。
- 权限申请的真实申请、复核、拒绝、撤销、审计、原子角色/仓库变更和旧 JWT 失效。
- `SUPER_ADMIN`、`SECURITY_ADMIN` 特权隔离，以及高风险申请不同审批人隔离。
- `WAREHOUSE_STAFF` 最小权限和仓库数据范围。
- `ORDERING` 采购单再次编辑及并发版本控制。
- 历史测试数据登记、只读预览、指纹确认、零库存影响归档和不可变审计。
- 归档后正常队列、库存边界、销售事实及时性的系统复验。

### 最后阶段任务：已于 2026-08-10 完成暂定版本

- `GENERAL_MANAGER` 已通过 `V4_49` 收敛为精确 40 项直接权限。
- `SALESPERSON` 已通过 `V4_49` 收敛为精确 15 项直接权限。
- 两者已设为可导入普通业务模板；`WAREHOUSE_STAFF` 8 项、`WAREHOUSE_ADMIN` 21 项保持不变，`SUPER_ADMIN`/`SECURITY_ADMIN` 保护规则保持不变。

当前方案按用户决定属于暂定验收基线。全部验收结束后，受保护特权角色和普通业务模板的分类、权限矩阵与保护策略在后续版本中另开专项修改，不回写当前已生效的自定义包快照。

### 非阻塞人工复核

- `WAREHOUSE_STAFF` 员工视角真实页面截图。API 数据范围和前端自动化已通过；应用内浏览器对局域网地址的控制限制导致截图未补齐。
- 采购单编辑真实页面的非破坏性展示复核。自动化和接口契约已通过；未经单据级写入授权不得点击保存。

### 已确认延期或超出本轮范围

- Localization 和公司业务时区。
- 导入模板管理中心。
- 通用正式审批引擎，包括多级、会签、委托、加签。
- 临时/跨仓授权和静态、动态职责冲突引擎。
- 多仓调拨、RMA、ATP 在途、波次路径优化、B2B 门户等后续产品路线图。
- Shopify 真实外部数据的进一步 UAT；需要单独的数据窗口和清理授权。

### 发布和运维前置，不属于本轮功能开发

- 当前工作区有 93 个已跟踪文件改动和 110 个未跟踪入口（包含本报告），其中包括未跟踪 `node_modules/`。提交前必须先做变更分组、忽略规则和敏感信息复核。
- 用户尚未授权 Git 提交、推送或创建 P2 标签，因此正式发布状态为“未发布”。
- Flyway 9.22.3 对 PostgreSQL 18.1 给出高于已测试支持范围的警告；迁移实际成功，但应单独规划 Flyway 升级。
- 历史提交可能仍包含旧明文凭证，凭证轮换属于外部运维动作。
- 新空库/多租户环境仍需要自包含 Flyway 基线迁移；这是 P3/SaaS 新环境的硬前置，不阻塞当前历史开发库的 P2 验收。

## 后续状态

上述最终验收汇总和发布前只读审查已于 2026-08-10 完成，功能范围通过；详情见 `docs/P2_FINAL_ACCEPTANCE_AUDIT_2026-08-10.md`。下一任务转为发布变更分组和标准 Maven/CI 构建准备。全部验收通过后，普通业务模板与受保护特权角色的专项调整进入后续版本，届时重新确认规则并使用新的版本化迁移。

## 关联证据

- `docs/CODEX_ROLE_DELVEP.md`
- `docs/IAM_PERMISSION_REQUEST_API_CONTRACT.md`
- `docs/frontend/IAM_ACCEPTANCE_CHECKLIST.md`
- `docs/frontend/PURCHASE_ORDER_EDIT_ACCEPTANCE.md`
- `docs/HISTORICAL_TEST_DATA_ARCHIVE_API.md`
- `docs/HISTORICAL_TEST_DATA_ARCHIVE_EXECUTION_2026-08-09.md`
- `docs/HISTORICAL_TEST_DATA_POST_ARCHIVE_ACCEPTANCE_2026-08-09.md`
- `docs/P2_HISTORICAL_DATA_DISPOSITION.md`
- `docs/CODEX_HANDOFF.md`

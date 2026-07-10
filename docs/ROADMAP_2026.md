# 2G WMS 改进路线图与战略规划（2026-07）

> 本文档由 2026-07 的系统全面评审产出，配套文件：
> - 《2G_WMS业务功能流程图.pptx》（现状，10 页，与源码逐行核对）
> - 《2G_WMS完整功能清单.xlsx》（116 个端点台账 + 后台任务 + 休眠/预留功能表）
> - 《2G_WMS改进方案计划.xlsx》（本路线图的表格版）
> 三份文件位于 `D:\ERP_WMS\BC warehouse\`。

## 现状一句话

Java 17 + Spring Boot 3.2.11 + PostgreSQL 单体 WMS（V4.x），采购→批次库存(FEFO)→销售风控→智能分配→出库→盘点闭环完整，含 RBAC、乐观锁、库存预留、Backorder/ATP、Shopify 每 5 分钟单向拉单。22 控制器 / 116 端点 / 82 测试类。总体约 8/10，短板在生产安全（P0 已修复）、Shopify 无回路、无前端。

---

## P0 — 生产安全修复（2026-07-08 已完成）

| # | 修复 | 位置 |
|---|------|------|
| 1 | 数据库凭证外部化（`DB_PASSWORD`/`DB_USERNAME`/`DB_URL` 环境变量，无默认密码）+ 低权限 `wms_app` 账号脚本 | `application.yml`、`docs/security/setup-db-user.sql` |
| 2 | JWT 密钥外部化（`JWT_SECRET`，缺失快速失败） | `application.yml` |
| 3 | Hashids 盐外部化（`BATCH_SALT`，移除默认值） | `application.yml` |
| 4 | admin 密码不再被重启强制重置；首次创建读 `ADMIN_INITIAL_PASSWORD` 或生成随机密码打印一次 | `WmsSystemApplication.java` |
| 5 | 移除 `/api/auth/register` 幽灵免认证白名单 | `DynamicAuthorizationManager.java` |
| 6 | 生产日志配置 `application-prod.yml`（show-sql 关闭、全 INFO），生产用 `SPRING_PROFILES_ACTIVE=prod` | 新文件 |
| 7 | 库存非负数据库级 CHECK 约束（含存量数据预检；`reserved<=quantity` 因盘点场景刻意不入库级约束） | `V4_13__Inventory_Non_Negative_Check.sql` |
| 8 | 补货预测数据源统一为 `inventory_batch`（修正 StockPredictionService 与两个 findLowStock 查询读废弃旧表的问题） | `StockPredictionService.java`、`ProductRepository.java` |
| 9 | 根目录 60+ 散落文件归档 `docs/history/`，`.gitignore` 更新 | — |

**环境变量配置见 `docs/security/SECURITY_SETUP.md`（部署前必读）。**

## P0.5 — 密码管理补全（2026-07-09 已完成）

| # | 内容 | 位置 |
|---|------|------|
| 1 | `PUT /api/users/me/password` 本人改密码：验旧密码 + 强度校验（8~64 位含字母和数字）+ 新旧不得相同；任何登录用户可用 | `UserController`、`UserManagementService` |
| 2 | `POST /api/users/{id}/reset-password` 管理员重置：生成 12 位临时密码（仅响应中出现一次，不落日志）；禁止重置自己 | 同上 |
| 3 | 首登强制改密：重置后 `users.must_change_password` 置位，授权层把该账号限制到改密端点（SUPER_ADMIN 也不例外），改密成功自动解除；登录响应新增 `mustChangePassword` 提示前端 | `DynamicAuthorizationManager`、`V4_14__Password_Management.sql` |

已通过 16 项端到端冒烟验证（重置→临时密码登录→其他 API 被 403→改密→恢复→临时密码作废）。

## P1 — Shopify 集成闭环（2~4 周，商业价值最高）

现状每 5 分钟拉单、无回路：
1. Webhook 实时接单（orders/create|cancelled|updated，HMAC 验签），轮询降为兜底对账
2. 库存回写 `inventory_levels/set`（防超卖），复用现有 `DomainOutbox` 可靠投递
3. 发货回写 Fulfillment API + 运单号
4. SKU 匹配失败进 `pending_mapping` 队列 + 人工映射接口（现为静默丢单）
5. `IntegrationConfig` 店铺配置管理 API（现只能 SQL 插入）
6. 每日对账 Job + 原始报文 JSONB 留底

## P2 — 履约深化 + 销售数据化（1~2 月）

- **Web 管理端第一版**（当前只有后端 API）
- **销售运营报表（核心）**：`ReportController` 已有客户事实汇总与日销售汇总雏形，扩展为业绩/订单状态/客户采购额看板
- **客户画像·事实层（核心）**：单客户事实层已完成约 80%（`customer_fact_summary`：订单数/金额/最后下单/平均复购间隔，夜间刷新）。待补：
  - 批量列表/排序筛选接口（当前只能按单客户查）
  - **`customer_product_summary` 客户×商品事实表**（常买 Top-N、每 SKU 复购间隔）——补货式营销与 B2B 建议订货单的前置
  - 修正 `ReportSummaryScheduler` 时区（现为 Asia/Shanghai，与业务时区 Europe/London 不一致）
- 仓库作业 PWA（PDA 扫码收货/拣货/盘点）
- 多仓调拨、销售退货 RMA（ZONE_R 与 RETURN 已预留）
- ATP 纳入在途、波次拣货+路径优化（pos_x/pos_y、FEFO 碎片索引已预留，见 V4_11）
- B2B 自助订货门户 + 客户等级价/阶梯价（新增 PriceList，复用风控审批框架）
- **接线现成休眠代码**（勿重写）：角色管理 API（RoleService/RolePermissionService 已存在）、幂等性（IdempotencyService 已存在）、自动盘点调度（StocktakeScheduler 注解被注释）

## P3 — 平台化（3~6 月，随规模触发）

OpenAPI 文档 + API 版本化；Redis（分布式缓存/锁/JWT 黑名单）；行级数据权限 + 激活 V4.10 多租户（company_id 已就绪）；Actuator + Prometheus/Grafana；只读副本。

**多租户激活的硬性前置——自包含基线迁移**（2026-07-09 实证发现）：现有 Flyway 迁移链从 V2 起就引用 Hibernate 早期自动建的表（users 等），**纯空库无法用迁移链初始化**（V2 建 sys_user_role 的外键即失败）。开发库能跑是历史演化的结果。SaaS 建新租户库/新环境部署前，必须先从当前 schema 导出一个自包含的基线迁移（如 V5_0__Baseline），并规定此后新表一律走迁移不走 ddl-auto。测试环境已于 07-09 起禁用 Flyway（schema 由 ddl-auto: create 从实体重建、测试自播种，见 application-test.yml 注释）。

## 双单元架构定稿

**Java 核心（本项目）+ Python 洞察服务（未来新建），单向依赖，一个用户界面。**

判断口诀：**动状态的在核心，打分数的在 AI 单元，确定性算术跟着使用它的界面走，重查询一律去副本。**

- 核心：ATP、简版预测、销售报表、画像事实层（含客户×商品）、补货建议的"采纳→生成采购单"
- 洞察：需求预测、补货算法、RFM 分级、流失打分、分群、关联推荐、LLM 摘要（出站统一脱敏，沿用 MaskingUtils 策略）
- 洞察产出必须带生成时间/模型版本/依据快照；过期降级为核心简版

**客户端**：办公 Web（第一优先）→ 仓库 PWA（不先做原生 APP）→ B2B 门户，三端同一套 API。

## 存储演进

- 现在：pg_dump 每日备份（本地第二块物理盘 + 云端加密副本，3-2-1 原则）→ 正式运营前升级 WAL 归档 + PITR（pgBackRest）
- P1/P2：Redis（易失层）、JSONB 报文留底、文件走对象存储
- 6~12 月：只读副本跑报表/洞察 → 需要时 CDC 到 ClickHouse
- SaaS 路线：共享库 + tenant_id + PostgreSQL RLS
- 凭证阶梯：环境变量（现在）→ Secrets Manager → 动态短效凭证 → IAM 无密码
- **铁律**：独立备份不因上云而取消；每季度演练一次真实恢复

## 商业战略要点

- **定位**：先自用提效跑通数据飞轮；12 个月后评估垂直 SaaS（"效期驱动 WMS"，面向食品/快消批发 + D2C 混合卖家，V4.10 已预留）
- **数据飞轮**：临期批次→自动降价/闪购营销（DomainOutbox 发事件）；客户×商品复购周期→B2C 订阅提醒/B2B 建议订货单；ATP→稀缺展示/缺货登记/预售（Backorder 即预售基础）；滞销→清仓闭环
- **多渠道**：ChannelAdapter 抽象（channel 枚举已预留），一盘货多渠道 + 中央库存防超卖为核心卖点；履约 SLA 看板
- **路线图**：90 天 = P0/P0.5 + P1 + 临期营销 MVP；6 个月 = P2 全部；12 个月 = 第二渠道 + P3 + SaaS 决策

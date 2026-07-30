# 2G WMS 改进路线图与战略规划（2026-07）

> 本文档由 2026-07 的系统全面评审产出，配套文件：
> - 《2G_WMS业务功能流程图.pptx》（现状，10 页，与源码逐行核对）
> - 《2G_WMS完整功能清单.xlsx》（116 个端点台账 + 后台任务 + 休眠/预留功能表）
> - 《2G_WMS改进方案计划.xlsx》（本路线图的表格版）
> 三份文件位于 `D:\ERP_WMS\BC warehouse\`。

## 现状一句话

Java 17 + Spring Boot 3.2.11 + PostgreSQL 核心服务，配套 React + Ant Design 管理端 V1.1。采购→批次库存（FEFO）→销售风控→预留/Backorder→出库→盘点闭环完整；商品、类别、Client/Consumer、供应商、Shopify 只读渠道链路与 IAM 管理端已接入。P0/P0.5/P1 已关闭，当前推进 P2 经营分析与仓储作业深化；Localization 继续按用户决定暂缓。

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

## P1 — Shopify 只读集成闭环（2026-07-21 已完成）

本阶段最终采用“渠道只读、WMS 自主履约”的安全边界，系统不会修改 Shopify 商品、库存、订单或履约状态：

1. Webhook 接单、HMAC 验签、原始报文留底和轮询兜底能力已具备；调度开关默认关闭，只能由生产发布审批后开启。
2. 店铺配置、只读连接测试、SKU 映射、异常事件工作台、对账报告和受限人工补单已具备。
3. 人工补单只能匹配现有 Client、创建待审批销售订单；不能新建客户、产品或直接产生出库动作。
4. Client 与 Consumer 双轨已落地；零售模式自动建立轻量 Consumer，人工主数据和渠道消费者的治理边界分离。
5. 销售订单保存当次收货地址快照；一张销售单可登记多条内部运单，运单可作废并保留审计。
6. 库存写回与 Fulfillment 写回不属于当前产品范围；休眠迁移或历史代码不得被激活，也不申请 Shopify write scope。

**真实店只读验收（2026-07-21）**：Bubble Crush UK 连接成功；1 天窗口读取 2 张远端订单，识别 2 张本地缺失订单并生成 1 条对账审计报告。执行前后本地销售单、客户、现存库存和预留量均未变化，远端写操作为 0。

## P2 — 履约深化 + 销售数据化（1~2 月）

> **2026-07-27 固化前置核对：不通过。** `V4_27`～`V4_30` 本地迁移校验和与数据库历史完全一致，数据库对象核对通过；但物理手机遗留尚未被接受为发布遗留，实时 OpenAPI、仓库快照与前端类型存在漂移，独立 API `.http` 验收集尚无可复现命令行执行器，且工作区与本机配置文件尚未完成提交隔离。因此当前不得推送或创建 P2 标签。

- **Web 管理端 V1.1 已交付**：认证、工作台、商品/类别、库存、销售、采购、入库、Client/Consumer、供应商、渠道集成、出库作业、盘点、紧急库存校正、仓库/库位、IAM 和经营分析管理端均已接入。IAM 用户生命周期可操作，角色与权限暂为只读审计。
- **采购单创建后编辑（已确认延期）**：后续为已创建且仍处于 `ORDERING` 的采购单提供再次编辑与保存能力；当前详情页只有查看和“确认 ASN”，不得把确认 ASN 当作保存。实施时需补充可编辑字段边界、并发校验和审计留痕。
- **Localization 基建（已确认暂缓）**：数据库时间继续统一存 UTC；未来新增公司业务时区、公司默认语言和用户显示偏好，时区使用 IANA 标识（如 `Europe/London`、`Asia/Shanghai`）。解析优先级为“用户偏好 → 公司配置 → 系统默认”，业务日切、报表归属日和调度任务统一读取公司业务时区。当前按用户决定不实施。
- **销售运营报表（核心，已完成首版）**：销售日报汇总已扩展订单状态计数，新增日期范围概览接口；前端已交付销售额、有效订单、平均客单价、已发货、趋势、流程状态和每日明细。报表只读取汇总表，查询范围限制为 366 天。
- **客户画像·事实层（核心，已完成确定性事实首版）**：`customer_fact_summary` 支持分页、排序、关键词、客户类型和来源筛选；新增 `customer_product_summary` 客户×商品事实表，提供常购 Top-N、累计数量、销售贡献和每 SKU 复购间隔。夜间汇总支持 UPSERT 与陈旧记录清理，已接入客户采购分析页面。后续仅保留：
  - 在 Localization 基建恢复后，将 `ReportSummaryScheduler` 从硬编码 `Asia/Shanghai` 切换为公司业务时区，并统一报表日期口径
  - 在成本、退货、税费、运费与汇率口径建立后，再增加利润和毛利率指标
- **仓库作业 PWA（已完成首版）**：新增独立移动作业入口，覆盖扫码收货、`PRINTED_LABEL`/`LOCATION_VISUAL` 双模式拣货与盲盘计数；支持安装到手机桌面和只读离线壳，所有库存写操作断网时强制停用，不建立离线写入队列。`V4_28` 已建立受限 `WAREHOUSE_STAFF` 角色，并为仓库员工和仓库管理员接通最小作业权限，不包含审批、复核或主数据维护。人工验收见 `docs/frontend/P2_B_WAREHOUSE_PWA_ACCEPTANCE_CHECKLIST.md`。
- 多仓调拨、销售退货 RMA（ZONE_R 与 RETURN 已预留）
- **多仓 SKU 经营策略下沉**：新增“公司 + SKU + 仓库”唯一策略关系，承载仓库级安全库存、补货点、采购提前期、默认库区、销售/采购开关和结构化库存策略；库存预警与补货建议改为按仓计算，同时保持批次库存真相源和 `Available = OnHand - Reserved` 不变。详细边界与验收标准见 `docs/frontend/ACCEPTANCE_NOTES.md` 的 `ARCH-REVIEW-011`。
- ATP 纳入在途、波次拣货+路径优化（pos_x/pos_y、FEFO 碎片索引已预留，见 V4_11）
- B2B 自助订货门户 + 客户等级价/阶梯价（新增 PriceList，复用风控审批框架）
- **接线现成休眠代码**（勿重写）：IAM 只读目录已接线；后续仍包括幂等性（IdempotencyService 已存在）和自动盘点调度（StocktakeScheduler 注解被注释）
- **导入模板管理中心（已确认延期）**：未来归入主数据管理 / 数据治理域，提供草稿上传、结构校验、发布、版本历史、回滚和审计；普通业务人员只下载正式模板。当前继续采用外部文件优先、内置模板兜底，不阻塞 P2 收尾。

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

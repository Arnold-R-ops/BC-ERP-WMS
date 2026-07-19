# 前端联调发现的后端问题

更新时间：2026-07-12

## BE-001：`start-app.bat` 在当前 Windows 命令行中解析失败

- 状态：待后端维护方确认
- 现象：使用 `cmd.exe /c start-app.bat` 启动时，UTF-8 中文及符号被拆成无效命令，应用未监听 8080。
- 临时联调方式：按 `03_LOCAL_DEV.md` 的备用方案，使用 IntelliJ 自带 Maven 执行 `spring-boot:run`，后端可正常启动。
- 建议：统一批处理文件编码与 CRLF 行尾，并移除可能影响 `cmd.exe` 解析的特殊符号后，在全新终端复测。

## BE-002：改密接口的 OpenAPI 成功状态码与真实实现不一致

- 状态：待修订 OpenAPI 注解
- 文档及真实实现：`PUT /api/users/me/password` 成功返回 `204 No Content`。
- `openapi.json`：声明为 `200 OK`。
- 前端处理：当前按任意 2xx 成功且允许空响应处理，没有绕过业务规则。

## BE-003：OpenAPI 未描述认证接口的 4xx 错误响应

- 状态：待补充 OpenAPI 注解
- 现象：登录、切换角色、改密路径只声明成功响应，未提供统一 `ErrorResponse` 的 400/401/403 schema。
- 影响：生成的 TypeScript 契约无法表达错误响应，前端只能依据现有稳定字段 `errorKey/message/params` 做公共兜底解析。

## BE-004：缺少可供前端查询的角色目录接口

- 状态：阻塞多角色账号的纯 API 测试数据准备
- 现象：当前数据库通过 `GET /api/users` 只能看到 `admin` 及其 `SUPER_ADMIN` 角色；OpenAPI 没有 `GET /api/roles` 一类的角色目录端点。
- 影响：`POST /api/users` 需要角色 ID，但前端无法通过公开 API 发现经理、采购、仓库、销售等角色 ID，因此无法按 `03_LOCAL_DEV.md` 独立创建多角色联调账号。
- 建议：暴露只读角色目录端点，至少返回 `id/roleCode/roleName/active`。角色切换本身已通过现有接口及前端状态转换测试验证，切换到不同角色的真实端到端验证待该测试数据条件补齐。

## BE-005：产品与 SKU 创建接口的 OpenAPI 成功状态码与真实实现不一致

- 状态：待修订 OpenAPI 注解
- 真实实现：`POST /api/products` 与 `POST /api/product-skus` 成功返回 `201 Created`。
- `openapi.json`：若未补充明确的 `@ApiResponse`，Springdoc 仍可能声明为 `200 OK`。
- 前端处理：按任意 2xx 成功处理，不影响创建，但生成契约无法准确表达接口语义。

## BE-006：旧模型缺少 SPU 目录查询接口

- 状态：已由 P1.5 数据模型正名解决。
- 处理：`/api/products` 已成为正式 Product（SPU）目录，具体规格由 `/api/product-skus` 管理；类别由 `/api/categories` 管理。
- 兼容边界：旧 SKU 语义的 `/api/products` 不保留，前后端必须同版本发布。

## BE-007：产品与 SKU 列表缺少服务端搜索和分页

- 状态：当前数据量可用，规模增长后需修复
- 现象：`GET /api/products` 与 `GET /api/product-skus` 当前仍一次性返回数组；SKU 接口仅增加 `productId/enabledOnly` 过滤。
- 影响：前端暂时在浏览器内完成名称、编码、条码及启停状态筛选和分页。数据量较大时会增加网络、内存和渲染开销。
- 建议：两个目录均升级为 Spring Data 分页，支持 `page/size/search/enabled`，返回 `content/totalElements`。

## BE-008：库存汇总无法完整表达近效期风险

- 状态：汇总层预警能力不完整
- 现象：`GET /api/inventory/summary` 只返回 `furthestExpiryDate`，没有最早效期、近效期批次数或统一风险标志。
- 影响：若同一 SKU 同时存在临期批次和远期批次，最远效期仍显示正常，汇总层无法提示那部分临期库存；批次明细层仍可逐批准确预警。
- 建议：汇总 DTO 增加 `earliestExpiryDate/nearExpiryBatchCount/expiredBatchCount`，由数据库聚合计算，避免前端加载全部批次。

## BE-009：库存 DTO 混入中文展示字符串

- 状态：影响英文界面的完整本地化
- 现象：`displayQuantity/displayAvailableQuantity/packageStatus` 返回诸如“7箱 + 1件”“整散混合”的已格式化中文，而不是结构化数量和稳定枚举。
- 影响：英文汇总层只能退化为显示原始总件数；批次层的包装状态暂时只能原样展示后端中文，前端无法可靠翻译，也不应通过字符串切割猜测业务含义。
- 建议：数量返回 `fullPackCount/looseUnitCount/packUnit`，包装状态返回稳定枚举（如 `FULL_ONLY/LOOSE_ONLY/MIXED`），由前端按语言格式化。

## BE-010：不存在的路由被错误映射为 500

- 状态：不阻塞前端联调，但会污染监控告警与客户端错误判断
- 现象：后端未暴露 `GET /actuator/health`，请求该路径时抛出 `NoResourceFoundException`，全局异常处理器返回 `500 Internal Server Error`，而不是 `404 Not Found`。
- 影响：探活工具会把“健康检查端点不存在”误判成应用内部故障；其他拼写错误或不存在的 API 也可能触发同类误报。
- 建议：为 `NoResourceFoundException`（及明确的资源不存在场景）增加 `404` 映射；如部署环境需要健康检查，再单独启用并保护 Spring Boot Actuator 健康端点。

## BE-011：销售订单响应缺少渠道字段

- 状态：阻塞销售列表的渠道徽标
- 现象：`POST /api/sales-orders` 接受 `channel`，但 `SalesOrderResponse` 未返回 `channel`，列表和详情接口均无法确认订单来自 `MANUAL/WECHAT/SHOPIFY`。
- 前端处理：保留渠道列但显示 `-`，不根据订单号或备注猜测渠道。
- 建议：在销售订单实体与响应 DTO 中稳定返回渠道枚举，并补齐历史订单默认值。

## BE-012：OpenAPI 中入库 DTO 被采购同名 DTO 覆盖

- 状态：生成的 TypeScript 契约错误
- 现象：采购与入库模块都存在 `ConfirmOrderRequest/ApprovalRequest` 简名。OpenAPI 只生成了一份 schema，导致 `POST /api/inbound-orders/{id}/confirm-order` 被声明成采购确认结构 `items[{itemId, expiryDate}]`；真实 Java DTO 是 `comment + confirmations[{confirmedQty, expiryDate, targetWarehouseId, targetLocationId...}]`。
- 前端处理：入库确认请求按真实 Java Controller/DTO 手工声明类型，其余稳定响应继续复用生成类型。
- 建议：为 DTO 添加唯一的 OpenAPI schema 名称，例如 `PurchaseConfirmOrderRequest` 与 `InboundConfirmOrderRequest`。

## BE-013：入库计划缺少供应商目录接口

- 状态：限制入库计划新建体验
- 现象：`POST /api/inbound-orders` 强制要求 `supplierId`，但 Controller/OpenAPI 没有供应商列表或搜索端点。
- 前端处理：暂时提供供应商 ID 数字输入并明确提示，不伪造选项。
- 建议：提供只读、可搜索的供应商目录，至少返回 `id/code/name/active`。

## BE-014：采购接口要求客户端自行提交操作人身份

- 状态：存在审计可信度和普通角色可用性风险
- 现象：采购创建、导入和收货请求要求 `operatorId/operatorName`，Controller 未从已认证的 `Authentication` 推导，而登录响应又不返回 `userId`。
- 前端处理：通过 `GET /api/users` 按当前用户名解析 ID 后提交；若普通角色无权读取用户列表，相关写操作将无法可靠执行。
- 建议：所有写接口从认证主体获取操作者 ID/名称，忽略客户端传入的身份字段；登录响应可补充只读 `userId` 供展示，但不能作为审计信任来源。

## BE-015：业务订单列表分页契约不统一

- 状态：当前数据量可用，规模增长后需修复
- 现象：销售和入库列表返回完整数组；采购列表接收 Spring `page/size`，却仍返回数组且没有 `totalElements`。三者均不符合页面规格中的统一 Spring Data 分页响应。
- 前端处理：销售和入库采用浏览器内分页；采购暂取前 500 条后本地筛选分页。
- 建议：统一返回 `Page<T>`，并由数据库完成状态、关键词、客户/供应商过滤。

## BE-016：采购“部分收货”只能按完整批次操作

- 状态：与页面规格中的按行实收数量存在差异
- 现象：确认 ASN 时每条采购明细只生成一个、数量等于订购量的批次；收货接口只接收 `batchCode + locationId`，没有 `actualQty`。因此只能通过选择部分明细批次实现部分收货，不能对单个批次分批实收。
- 前端处理：收货界面明确按完整待上架批次分配库位，不提供无效的数量输入。
- 建议：确认真实业务需要后，选择“ASN 时支持拆分多个批次”或“收货请求增加批次数量并维护剩余数量”其中一种模型，避免界面承诺后端无法执行的部分收货。

## BE-017：渠道人工复核无法按外部订单号定位本地销售单

- 状态：限制人工复核闭环
- 现象：原始渠道事件只提供 `externalId`，销售订单接口没有按 `channel + externalOrderId` 或外部订单号查询的端点，销售订单响应也未返回外部订单标识。
- 影响：人工复核详情可以展示、复制渠道订单标识，但无法可靠地一键跳转到对应本地销售订单，只能进入销售订单列表后人工查找。
- 前端处理：详情抽屉提供进入销售订单列表的入口，同时明确展示并允许复制外部订单标识，不猜测本地订单 ID。
- 建议：提供 `GET /api/sales-orders/by-external?channel=&externalOrderId=`，或为销售订单列表增加等价筛选参数，并在响应中返回 `channel/externalOrderId/externalOrderNo`。

## BE-018：紧急补货接口缺少明确的 OpenAPI 响应模型

- 状态：运行接口正常，但生成的前端契约为空对象。
- 现象：`GET /api/predictions/reorder/urgent` 实际返回 `suggestions/totalCount/totalCost`，Controller 使用 `ResponseEntity<?>` 和临时 `Map`，导致 OpenAPI 将成功响应生成为 `Record<string, never>`。
- 前端处理：根据真实接口结果手工声明 `UrgentReorderResponse`，工作台只读联调可正常显示。
- 风险：以后重新生成 `schema.d.ts` 时无法自动发现字段变更，后端字段调整可能在编译期漏检。
- 建议：新增正式响应 DTO（例如 `ReorderSuggestionSummaryResponse`），并让普通与紧急补货两个接口复用该 DTO。

## 联调契约说明（非缺陷）

`POST /api/auth/switch-role` 只返回新 Token、当前角色和有效期，不返回 `username/availableRoles/mustChangePassword`。前端已经采用增量合并，保留登录会话中的其余字段，避免切换角色后丢失权限上下文。

# WMS 仓库管理系统 - 功能文档

## 📋 系统概述

本系统是一个企业级的仓库管理系统（WMS），提供从采购入库、库存管理、销售出库到盘点管理的完整业务流程支持，并集成了Shopify等外部电商平台。

**当前版本：** V3.9
**开发分支：** 2-1G
**技术栈：** Spring Boot + MySQL + JWT认证

---

## 🔐 一、认证与权限管理模块

### 1.1 用户认证
**控制器：** `AuthController`

**功能特性：**
- 用户登录（支持多角色）
- 角色动态切换（无需重新登录）
- JWT令牌管理
- 健康检查

**API端点：**
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/switch-role` - 切换当前角色
- `GET /api/auth/health` - 系统健康检查

### 1.2 用户管理
**控制器：** `UserController`

**功能特性：**
- 用户增删改查
- 批量分配角色
- 移除用户角色
- 权限缓存管理

**API端点：**
- `GET /api/users` - 获取所有用户
- `POST /api/users` - 创建新用户
- `PUT /api/users/{id}` - 更新用户信息
- `DELETE /api/users/{id}` - 删除用户
- `POST /api/users/{id}/roles` - 为用户分配角色
- `DELETE /api/users/{id}/roles/{roleId}` - 移除用户角色

**权限要求：** 仅SUPER_ADMIN可访问

### 1.3 角色系统
**支持的角色：**
- **SUPER_ADMIN** - 超级管理员（系统最高权限）
- **WAREHOUSE_ADMIN** - 仓库管理员
- **BUYER** - 采购员
- **SALESPERSON** - 销售员
- **CHAIRMAN** - 总经理
- **STAFF** - 普通员工

**权限特性：**
- 基于角色的访问控制（RBAC）
- 动态权限分配
- 权限缓存优化
- 细粒度权限控制

---

## 📦 二、采购入库模块

### 2.1 采购订单管理
**控制器：** `PurchaseOrderController`

**功能特性：**
- 创建采购订单
- Excel批量导入采购订单
- 确认ASN并自动生成批次码
- 确认物理收货
- 订单状态回滚
- 采购订单查询

**订单状态流转：**
```
ORDERING（下单中）
    ↓
IN_TRANSIT（在途）
    ↓
PARTIALLY_RECEIVED（部分收货）
    ↓
COMPLETED（已完成）
```

**API端点：**
- `POST /api/purchase-orders` - 创建采购订单
- `POST /api/purchase-orders/upload` - Excel导入采购订单
- `PUT /api/purchase-orders/{id}/confirm` - 确认ASN并生成批次码
- `PUT /api/purchase-orders/{id}/receive` - 确认物理收货
- `PUT /api/purchase-orders/{id}/rollback` - 回滚到ORDERING状态
- `GET /api/purchase-orders` - 查询采购订单列表
- `GET /api/purchase-orders/{id}` - 获取采购订单详情

**隐私保护：**
- STAFF/SALESPERSON角色：供应商信息显示为"***"，成本字段隐藏
- ADMIN/MANAGER角色：所有字段完全可见

### 2.2 入库单管理
**控制器：** `InboundOrderController`

**功能特性：**
- 创建入库单申请
- 总经理审批计划
- 采购员确认订单
- 仓库确认收货
- 拒绝入库单
- 入库单查询

**入库单状态流转：**
```
PENDING_APPROVAL（待审批）
    ↓
APPROVED_PLAN（计划已批准）
    ↓
AWAITING_RECEIVAL（待收货）
    ↓
COMPLETED（已完成）

可在任意阶段 → REJECTED（已拒绝）
```

**业务流程：**
1. 用户创建入库单申请（PENDING_APPROVAL）
2. 总经理审批入库计划（APPROVED_PLAN）
3. 采购员确认订单并生成批次码（AWAITING_RECEIVAL）
4. 仓库确认收货（COMPLETED）

**API端点：**
- `POST /api/inbound-orders` - 创建入库单
- `POST /api/inbound-orders/{id}/approve-plan` - 审批计划
- `POST /api/inbound-orders/{id}/confirm-order` - 确认订单
- `POST /api/inbound-orders/{id}/receive-goods` - 确认收货
- `POST /api/inbound-orders/{id}/reject` - 拒绝入库单
- `GET /api/inbound-orders/{id}` - 获取入库单详情
- `GET /api/inbound-orders/by-status/{status}` - 按状态查询
- `GET /api/inbound-orders/pending-approval` - 待审批列表
- `GET /api/inbound-orders/pending-confirmation` - 待确认列表
- `GET /api/inbound-orders/pending-receival` - 待收货列表

---

## 📊 三、库存管理模块

### 3.1 库存调整
**控制器：** `InventoryController`

**功能特性：**
- 库存手动调整（入库/出库/调整）
- 库存总量查询
- 低库存预警
- 系统健康检查

**交易类型：**
- **IN** - 入库
- **OUT** - 出库
- **ADJUSTMENT** - 调整

**来源类型：**
- INBOUND_ORDER - 入库单
- PURCHASE_ORDER - 采购订单
- SALE_OUT - 销售出库
- INVENTORY_ADJUSTMENT - 库存调整

**API端点：**
- `POST /api/inventory/adjust` - 调整库存
- `GET /api/inventory/total-stock/{productId}` - 查询产品总库存
- `GET /api/inventory/health` - 健康检查

### 3.2 库存批次管理
**服务：** `InventoryBatchService`

**功能特性：**
- 自动生成批次码
- 批次库存追踪
- 有效期管理
- 批次激活/停用

**批次码格式：** `SPU-SKU-日期`
- 示例：`SPU001-SKU123-20260125`

**批次信息：**
- 批次码（唯一标识）
- 产品SKU
- 库位编码
- 数量
- 生产日期
- 有效期
- 激活状态

### 3.3 三层穿透式库存查询
**控制器：** `InventoryQueryController`

**功能特性：**
- **第一层：SKU聚合视图** - 按SKU汇总库存，显示总量和预警状态
- **第二层：批次详情视图** - 显示某SKU的所有批次明细
- **第三层：库位反查视图** - 扫描库位码显示该库位所有批次

**API端点：**
- `GET /api/inventory/summary` - SKU聚合视图（支持分页）
- `GET /api/inventory/details/{skuId}` - 批次详情视图
- `GET /api/inventory/location/{locationCode}` - 库位反查视图

**显示信息：**
- 智能数量显示（整箱+散货）
- 预警状态（红色/绿色）
- 最远有效期
- 批次码、库位、数量、有效期等详细信息

---

## 🏭 四、仓库与库位管理模块

### 4.1 仓库管理
**控制器：** `WarehouseController`

**功能特性：**
- 创建仓库
- 更新仓库信息
- 激活/停用仓库
- 查询仓库列表
- 获取仓库库位数量

**API端点：**
- `GET /api/warehouses` - 获取所有仓库
- `GET /api/warehouses/active` - 获取激活的仓库
- `GET /api/warehouses/{id}` - 获取仓库详情
- `GET /api/warehouses/code/{code}` - 按编码查询仓库
- `POST /api/warehouses` - 创建仓库
- `PUT /api/warehouses/{id}` - 更新仓库信息
- `PUT /api/warehouses/{id}/activate` - 激活仓库
- `PUT /api/warehouses/{id}/deactivate` - 停用仓库
- `GET /api/warehouses/{id}/location-count` - 获取库位数量

### 4.2 库位管理
**控制器：** `LocationController`

**功能特性：**
- 创建库位
- 更新库位信息
- 查询库位
- 库位编码管理

**库位编码格式：** `仓库-区域-货架-层-位置`
- 示例：`WH01-ZONE_A-A-01-001`
  - WH01：仓库编码
  - ZONE_A：区域
  - A：货架
  - 01：层
  - 001：位置

**库位信息：**
- 库位编码（唯一）
- 所属仓库
- 区域、货架、层、位置
- 激活状态

---

## 💰 五、销售与出库模块

### 5.1 客户管理
**控制器：** `CustomerController`

**功能特性：**
- 创建客户
- 更新客户信息
- 查询客户列表
- 删除客户（软删除）

**API端点：**
- `POST /api/customers` - 创建客户
- `GET /api/customers` - 查询客户列表
- `GET /api/customers/{id}` - 获取客户详情
- `PUT /api/customers/{id}` - 更新客户信息
- `DELETE /api/customers/{id}` - 删除客户

**客户信息：**
- 客户编码（唯一）
- 客户名称
- 联系人、电话、邮箱
- 地址
- 信用额度
- 激活状态

### 5.2 销售订单管理
**控制器：** `SalesOrderController`

**功能特性：**
- 下载Excel导入模板
- Excel批量导入销售订单
- 批次选项预检查
- 创建销售订单
- 销售订单审批/拒绝
- 销售订单取消
- 销售订单查询

**销售订单状态流转：**
```
DRAFT（草稿）
    ↓
PENDING_APPROVAL（待审批）
    ↓
APPROVED_AWAITING_SHIPMENT（已批准待发货）
    ↓
SHIPPED（已发货）

可在审批前 → REJECTED（已拒绝）
可在发货前 → CANCELLED（已取消）
```

**API端点：**
- `GET /api/sales-orders/template` - 下载Excel模板
- `POST /api/sales-orders/upload` - 上传Excel导入
- `POST /api/sales-orders/batch-options` - 批次选项预检查
- `POST /api/sales-orders` - 创建销售订单
- `GET /api/sales-orders` - 查询销售订单列表
- `GET /api/sales-orders/{id}` - 获取销售订单详情
- `PUT /api/sales-orders/{id}` - 更新销售订单
- `POST /api/sales-orders/{id}/approve` - 审批销售订单
- `POST /api/sales-orders/{id}/reject` - 拒绝销售订单
- `POST /api/sales-orders/{id}/cancel` - 取消销售订单

**风险控制：**
- 金额阈值审批（可配置）
- 低价预警
- 高额预警
- 库存充足性检查

### 5.3 出库任务管理
**控制器：** `OutboundTaskController`

**功能特性：**
- 列出出库任务
- 获取出库任务详情
- 确认拣货（单个）
- 批量确认拣货

**出库任务状态流转：**
```
PENDING（待处理）
    ↓
PICKING（拣货中）
    ↓
COMPLETED（已完成）
```

**业务流程：**
1. 销售订单审批通过 → 系统自动分配库存 → 生成出库任务
2. 仓库员工查询待处理任务
3. 仓库员工拣货并确认
4. 系统扣减库存并记录交易
5. 所有任务完成 → 销售订单状态变为SHIPPED

**API端点：**
- `GET /api/outbound-tasks` - 列出出库任务
- `GET /api/outbound-tasks/{id}` - 获取任务详情
- `POST /api/outbound-tasks/{id}/confirm` - 确认拣货
- `POST /api/outbound-tasks/batch-confirm` - 批量确认拣货

**权限要求：**
- `outbound:view` - 查看出库任务
- `outbound:pick` - 执行拣货操作

---

## 📋 六、盘点管理模块

### 6.1 智能盘点系统
**控制器：** `StocktakeController`

**功能特性：**
- 创建盘点任务
- 开始盘点
- 提交盘点结果（盲盘模式）
- 完成盘点
- 审核盘点结果
- 查询盘点任务

**盘点任务状态流转：**
```
CREATED（已创建）
    ↓
COUNTING（盘点中）
    ↓
REVIEWING（审核中）
    ↓
COMPLETED（已完成）
```

**盘点周期类型：**
- **MONTHLY** - 月度盘点
- **QUARTERLY** - 季度盘点
- **ANNUAL** - 年度盘点
- **ADHOC** - 临时盘点

**业务流程：**
1. 创建盘点任务 → 选择批次 → 生成库存快照
2. 开始盘点 → 仓库员工盘点（盲盘模式，不显示系统库存）
3. 提交盘点结果 → 系统自动计算差异
4. 完成盘点 → 转入审核状态
5. 经理审核 → 批准/拒绝
6. 批准 → 自动调整库存 → 完成

**API端点：**
- `POST /api/stocktake/tasks` - 创建盘点任务
- `GET /api/stocktake/tasks` - 列出盘点任务
- `GET /api/stocktake/tasks/{id}` - 获取任务详情
- `POST /api/stocktake/tasks/{id}/start` - 开始盘点
- `GET /api/stocktake/tasks/{id}/items` - 获取盘点明细（盲盘版本）
- `POST /api/stocktake/tasks/{taskId}/items/{itemId}/count` - 提交盘点结果
- `POST /api/stocktake/tasks/{id}/finish` - 完成盘点
- `GET /api/stocktake/tasks/{id}/review-items` - 获取审核明细
- `POST /api/stocktake/tasks/{id}/review` - 审核盘点

**权限要求：**
- `stocktake:create` - 创建盘点任务
- `stocktake:view` - 查看盘点任务
- `stocktake:count` - 执行盘点操作
- `stocktake:review` - 审核盘点结果

**盲盘特性：**
- 盘点时不显示系统库存数量
- 基于快照确保数据一致性
- 自动计算差异并生成报告

---

## 🔗 七、外部系统集成模块

### 7.1 Shopify集成
**服务：** `ShopifyIntegrationService`
**API客户端：** `ShopifyApiClient`

**功能特性：**
- 获取Shopify订单列表
- 订单自动同步
- 集成配置管理
- 定时任务同步

**支持的操作：**
- 获取待处理订单（status=open, financial_status=paid）
- 订单数据自动映射到销售订单
- 自动创建销售订单

**集成配置：**
- 平台类型（SHOPIFY）
- 店铺URL
- API密钥
- 访问令牌
- 最后同步时间

**数据映射：**
- Shopify订单 → WMS销售订单
- Shopify客户 → WMS客户
- Shopify商品 → WMS产品

---

## 🗄️ 八、数据库架构

### 核心表结构

**用户与权限：**
- `users` - 用户表
- `sys_roles` - 角色表
- `sys_permissions` - 权限表
- `sys_user_role` - 用户角色关联表
- `sys_role_permission` - 角色权限关联表

**采购与入库：**
- `purchase_orders` - 采购订单表
- `purchase_order_items` - 采购订单明细表
- `inbound_orders` - 入库单表
- `inbound_order_items` - 入库单明细表
- `suppliers` - 供应商表

**销售与出库：**
- `sales_orders` - 销售订单表
- `sales_order_items` - 销售订单明细表
- `outbound_tasks` - 出库任务表
- `customers` - 客户表

**库存：**
- `products` - 产品表
- `inventory` - 库存表
- `inventory_batch` - 库存批次表
- `stock_transactions` - 库存交易表

**仓库：**
- `warehouses` - 仓库表
- `locations` - 库位表

**盘点：**
- `stocktake_tasks` - 盘点任务表
- `stocktake_items` - 盘点明细表

**集成：**
- `integration_configs` - 集成配置表
- `system_config` - 系统配置表

---

## 🔑 九、关键业务流程

### 9.1 采购流程
```
1. 创建采购订单（ORDERING）
2. 确认ASN并生成批次码（IN_TRANSIT）
3. 物理收货（PARTIALLY_RECEIVED/COMPLETED）
4. 库存自动入库
```

### 9.2 销售流程
```
1. 创建销售订单（DRAFT）
2. 风险控制检查
3. 提交审批（PENDING_APPROVAL）
4. 审批通过（APPROVED_AWAITING_SHIPMENT）
5. 系统自动分配库存
6. 生成出库任务
7. 仓库拣货确认
8. 订单发货（SHIPPED）
```

### 9.3 入库流程
```
1. 申请入库单（PENDING_APPROVAL）
2. 总经理审批计划（APPROVED_PLAN）
3. 采购员确认订单并生成批次码（AWAITING_RECEIVAL）
4. 仓库确认收货（COMPLETED）
5. 库存自动入库
```

### 9.4 盘点流程
```
1. 创建盘点任务并生成快照（CREATED）
2. 开始盘点（COUNTING）
3. 仓库员工盲盘并提交结果
4. 完成盘点（REVIEWING）
5. 经理审核差异
6. 批准后自动调整库存（COMPLETED）
```

---

## 📊 十、系统特性总结

| 特性分类 | 功能特性 |
|---------|---------|
| **权限管理** | 多角色RBAC、动态权限、权限缓存 |
| **仓库管理** | 多仓库支持、库位精细化管理 |
| **批次追踪** | 完整的批次生命周期管理、批次码自动生成 |
| **有效期管理** | 有效期预警、FEFO排序 |
| **库存预警** | 安全库存预警、低库存提醒 |
| **盘点管理** | 盲盘模式、快照机制、差异自动计算 |
| **数据导入** | Excel批量导入采购订单和销售订单 |
| **外部集成** | Shopify订单自动同步 |
| **性能优化** | 权限缓存、分页查询 |
| **审计追踪** | 完整的操作审计日志 |
| **风险控制** | 销售订单金额阈值审批、价格预警 |
| **隐私保护** | 基于角色的数据脱敏 |

---

## 🚀 十一、版本演进历史

| 版本 | 发布日期 | 主要功能 |
|------|---------|---------|
| **V3.3** | 2026-01-20 | 多角色RBAC系统 |
| **V3.4** | 2026-01-23 | 仓库库位管理系统 |
| **V3.5** | 2026-01-25 | 智能入库系统 |
| **V3.6** | 2026-01-28 | 三层穿透式库存查询系统 |
| **V3.7** | 2026-01-29 | 智能销售与出库系统 |
| **V3.8** | 2026-01-29 | 智能盘点系统 |
| **V3.9** | 2026-02-05 | Shopify集成系统 |

---

## 📝 十二、技术栈

**后端框架：**
- Spring Boot 2.x
- Spring Security（JWT认证）
- Spring Data JPA
- Hibernate

**数据库：**
- MySQL 8.0
- Flyway（数据库版本管理）

**开发工具：**
- Maven
- JUnit 5（单元测试）
- Mockito（Mock测试）

**集成：**
- RestTemplate（HTTP客户端）
- Apache POI（Excel处理）
- Shopify API

---

## 📞 联系与支持

如有问题或建议，请联系开发团队。

**文档版本：** 1.0
**最后更新：** 2026-02-12

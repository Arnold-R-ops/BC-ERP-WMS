# Claude WMS - 智能仓储管理系统

> **版本**: V3.8
> **最后更新**: 2026-01-29
> **技术栈**: Spring Boot 3.2.11 + PostgreSQL + JWT
> **开发者**: Claude AI + WMS Team

---

## 📋 目录

- [系统概述](#系统概述)
- [核心功能模块](#核心功能模块)
- [技术架构](#技术架构)
- [API 接口总览](#api-接口总览)
- [数据库设计](#数据库设计)
- [权限体系](#权限体系)
- [部署指南](#部署指南)

---

## 🎯 系统概述

Claude WMS 是一个功能完整的智能仓储管理系统，支持从采购入库到销售出库的全流程管理，包括智能库存分配、盲盘盘点、多角色权限控制等高级特性。

### 核心特性

- ✅ **多仓库管理** - 支持多仓库、多库区、多库位的层级管理
- ✅ **批次追溯** - 完整的批次管理和 FEFO（先过期先出）策略
- ✅ **智能分配** - 基于整箱优先、临期过滤的智能库存分配算法
- ✅ **盲盘盘点** - 防作弊的盲盘功能，提高盘点准确性
- ✅ **Excel 导入导出** - 支持批量数据导入和模板下载
- ✅ **多角色 RBAC** - 细粒度的权限控制和角色切换
- ✅ **审批流程** - 支持采购审批、销售审批等工作流
- ✅ **审计日志** - 完整的操作记录和库存流水追溯

---

## 🏗️ 核心功能模块

### 1️⃣ 基础管理模块

#### 1.1 用户管理
- 用户注册、登录、注销
- 用户信息管理（CRUD）
- 密码修改和重置
- JWT Token 认证

#### 1.2 角色与权限管理（V3.3 多角色 RBAC）
- 角色定义和管理
- 权限分配和继承
- 用户多角色绑定
- 动态角色切换
- 细粒度权限控制

**预定义角色**：
- `ADMIN` - 系统管理员
- `WAREHOUSE_MANAGER` - 仓库经理
- `WAREHOUSE_STAFF` - 仓库员工
- `PURCHASER` - 采购员
- `SALES` - 销售员
- `FINANCE` - 财务人员

#### 1.3 系统配置管理（V3.7）
- 可配置的业务参数
- 支持多种数据类型（DECIMAL, INTEGER, STRING, BOOLEAN）
- 缓存支持，提高性能
- 审批阈值配置

---

### 2️⃣ 仓库管理模块（V3.4）

#### 2.1 仓库管理
- 仓库创建、编辑、删除
- 仓库启用/禁用
- 仓库信息查询

#### 2.2 库区管理
- 库区划分（收货区、存储区、拣货区、发货区等）
- 库区与仓库的关联
- 库区容量管理

#### 2.3 库位管理
- 三级库位编码（通道-货架-层-位）
- 库位状态管理（空闲、占用、锁定、维护）
- 库位容量和重量限制
- 库位查询和筛选

**库位编码示例**：
- `WH01-ZONE_A-A-01-001`（仓库-库区-通道-货架-层-位）

---

### 3️⃣ 产品管理模块

#### 3.1 SPU-SKU 层级管理（V3.3）
- SPU（产品家族）管理
- SKU（库存单位）管理
- 产品规格和属性
- 条形码管理

#### 3.2 产品信息管理
- 产品创建、编辑、删除
- 产品启用/禁用
- 产品分类和供应商
- 安全库存和采购提前期
- 多单位换算（箱/瓶）

#### 3.3 产品扩展字段（V3.7）
- **最低限价** - 销售风控
- **临期天数阈值** - 临期预警
- **箱规** - 整箱策略

---

### 4️⃣ 库存管理模块

#### 4.1 批次管理（V3.5）
- 批次自动生成（SPU-SKU-日期格式）
- 批次追溯和查询
- 批次激活/失效
- 过期批次预警

**批次编码示例**：
- `SPU001-SKU001-20260126`

#### 4.2 库存查询（V3.6）
- 实时库存查询
- 按产品/批次/库位查询
- 库存预警（低库存、临期、过期）
- 库存统计和报表

#### 4.3 库存流水
- 完整的出入库记录
- 流水类型：入库、出库、调整、盘点
- 来源追溯（采购单、销售单、盘点单）
- 操作人和操作时间记录

---

### 5️⃣ 采购入库模块（V3.5）

#### 5.1 采购订单管理
- 采购订单创建（三阶段流程）
- 订单状态管理（下单中、待入库、部分收货、已入库）
- 订单修改和取消
- 订单查询和统计

**三阶段流程**：
1. **ORDERING**（下单中）- 创建采购单，填写商品明细
2. **IN_TRANSIT**（待入库）- 确认并生成批次码
3. **COMPLETED**（已入库）- 确认入库，更新库存

#### 5.2 入库单管理
- 入库单创建和确认
- 支持分批入库
- 入库数量验证
- 库位分配

#### 5.3 批次生成
- 自动生成批次编码
- 过期日期管理
- 批次与采购单关联

---

### 6️⃣ 销售出库模块（V3.7）

#### 6.1 客户管理
- 客户信息管理（CRUD）
- 客户信用额度（预留）
- 客户启用/禁用

#### 6.2 销售订单管理
- **Excel 导入导出** - 批量订单导入
- 订单创建和修改
- 订单状态管理（草稿、待审批、待发货、已发货）
- 订单取消（释放库存）

#### 6.3 智能风控与审批
- **价格风控** - 单价低于最低限价触发审批
- **金额风控** - 订单总额超过阈值触发审批
- 经理审批/拒绝
- 审计日志记录

#### 6.4 智能库存分配算法 🧠
**6 步优先级分配**：
1. **用户指定优先** - 强制使用指定批次
2. **整箱策略** - 需求量是整箱时，只从整箱批次分配
3. **临期过滤** - 客户拒收临期品时过滤
4. **库存充足性检查** - 不足时直接拒绝订单
5. **FEFO 排序** - 先过期先出
6. **整散分离** - 优先使用散货，不足时再拆箱

#### 6.5 出库任务管理
- 出库任务生成
- 仓库员拣货确认
- 批量拣货支持
- 库存扣减和流水记录

#### 6.6 库存预检查
- 查询可用批次
- 显示新鲜度状态（FRESH / WARNING）
- 显示包装状态（整箱 / 散货）
- 距离过期天数计算

---

### 7️⃣ 智能盘点模块（V3.8）

#### 7.1 盘点任务管理
- 创建盘点任务（月度/季度/年度/临时）
- 任务状态管理（已创建、盘点中、审核中、已完成）
- 任务查询和统计

#### 7.2 智能选品策略 🧠
- **月度盘点** - 选取最近 30 天有变动的批次（最多 500 个）
- **季度盘点** - 全量商品盘点
- **年度盘点** - 全量商品盘点
- **临时盘点** - 按需选择商品

#### 7.3 盲盘功能 🙈
- **防作弊机制** - 盘点时不显示账面数量
- 仓库员只能录入实盘数量
- 审核时才显示差异
- 提高盘点准确性

#### 7.4 差异分析与平账
- 自动计算差异（实盘数 - 账面数）
- 盘盈/盘亏标识
- 审核通过后自动平账
- 生成库存调整流水

#### 7.5 定时调度（可选）
- 月度盘点：每月 28 日自动生成
- 季度盘点：每季度最后一天自动生成

---

## 🔧 技术架构

### 后端技术栈

```
Spring Boot 3.2.11
├── Spring Data JPA (Hibernate)
├── Spring Security + JWT
├── Spring Validation
├── Spring Retry（乐观锁重试）
├── Spring Cache（配置缓存）
└── Spring Scheduling（定时任务）

PostgreSQL 数据库
├── Flyway（数据库迁移）
└── 乐观锁（@Version）

Apache POI 5.2.3
└── Excel 导入导出

Lombok
└── 简化代码

SLF4J + Logback
└── 日志管理
```

### 架构模式

```
Controller 层
    ↓ (DTO)
Service 层
    ↓ (Entity)
Repository 层
    ↓ (JPA)
Database 层
```

### 设计模式

- **DTO 模式** - 请求/响应分离
- **Repository 模式** - 数据访问抽象
- **Service 模式** - 业务逻辑封装
- **Builder 模式** - 对象构建
- **Strategy 模式** - 智能选品策略
- **Template Method** - 审批流程模板

---

## 📡 API 接口总览

### 用户与权限（`/api/auth`, `/api/users`, `/api/roles`）
- 用户注册、登录、注销
- 用户信息管理
- 角色管理和切换
- 权限查询

### 仓库管理（`/api/warehouses`, `/api/locations`）
- 仓库 CRUD
- 库位 CRUD
- 库位查询和筛选

### 产品管理（`/api/products`, `/api/spus`）
- 产品 CRUD
- SPU 管理
- 产品查询和搜索

### 采购入库（`/api/purchase-orders`, `/api/inbound-orders`）
- 采购订单管理（10 个接口）
- 入库单管理（6 个接口）
- 批次生成和查询

### 销售出库（`/api/sales-orders`, `/api/outbound-tasks`, `/api/customers`）
- 销售订单管理（10 个接口）
- 出库任务管理（4 个接口）
- 客户管理（5 个接口）
- Excel 导入导出

### 库存查询（`/api/inventory`）
- 实时库存查询
- 批次查询
- 库存预警

### 智能盘点（`/api/stocktake`）
- 盘点任务管理（9 个接口）
- 盲盘录入
- 差异审核和平账

**总计**: 约 **70+ API 接口**

---

## 🗄️ 数据库设计

### 核心表结构

#### 基础表
- `users` - 用户表
- `sys_roles` - 角色表
- `sys_permissions` - 权限表
- `sys_user_roles` - 用户角色关联表
- `sys_role_permissions` - 角色权限关联表
- `system_config` - 系统配置表

#### 仓库表
- `warehouses` - 仓库表
- `locations` - 库位表

#### 产品表
- `product_spus` - SPU 表
- `products` - SKU 表

#### 库存表
- `inventory` - 库存表（已废弃，使用批次表）
- `inventory_batch` - 批次表
- `stock_transactions` - 库存流水表

#### 采购表
- `purchase_orders` - 采购订单表
- `purchase_order_items` - 采购订单明细表
- `inbound_orders` - 入库单表
- `inbound_order_items` - 入库单明细表

#### 销售表
- `customers` - 客户表
- `sales_orders` - 销售订单表
- `sales_order_items` - 销售订单明细表
- `outbound_tasks` - 出库任务表

#### 盘点表
- `stocktake_tasks` - 盘点任务表
- `stocktake_items` - 盘点明细表

**总计**: 约 **25 张表**

### 数据库迁移版本

- `V2__rbac_schema.sql` - RBAC 权限系统
- `V3_3__multi_role_migration.sql` - 多角色系统
- `V3_4__Warehouse_Schema.sql` - 仓库库位系统
- `V3_5__Inbound_System_Final.sql` - 采购入库系统
- `V3_6__Inventory_View_Schema.sql` - 库存查询系统
- `V3_7__Sales_Outbound_System.sql` - 销售出库系统
- `V3_8__Stocktake_System.sql` - 智能盘点系统

---

## 🔐 权限体系

### 权限分类

#### 用户管理权限
- `user:create`, `user:view`, `user:edit`, `user:delete`

#### 角色管理权限
- `role:create`, `role:view`, `role:edit`, `role:delete`

#### 仓库管理权限
- `warehouse:create`, `warehouse:view`, `warehouse:edit`, `warehouse:delete`
- `location:create`, `location:view`, `location:edit`, `location:delete`

#### 产品管理权限
- `product:create`, `product:view`, `product:edit`, `product:delete`
- `spu:create`, `spu:view`, `spu:edit`, `spu:delete`

#### 采购管理权限
- `purchase:create`, `purchase:view`, `purchase:edit`, `purchase:delete`
- `purchase:confirm`, `purchase:rollback`
- `inbound:create`, `inbound:view`, `inbound:confirm`

#### 销售管理权限
- `sales:create`, `sales:view`, `sales:edit`, `sales:cancel`
- `sales:approve` - 销售审批
- `customer:create`, `customer:view`, `customer:edit`, `customer:delete`

#### 出库管理权限
- `outbound:view`, `outbound:pick`

#### 库存管理权限
- `inventory:view`, `inventory:adjust`

#### 盘点管理权限
- `stocktake:create`, `stocktake:view`
- `stocktake:count` - 盘点录入
- `stocktake:review` - 盘点审核

---

## 🚀 部署指南

### 环境要求

- **JDK**: 17+
- **Maven**: 3.8+
- **PostgreSQL**: 14+
- **内存**: 最低 2GB

### 配置文件

编辑 `application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: your_username
    password: your_password

  jpa:
    hibernate:
      ddl-auto: validate  # 使用 Flyway 管理数据库
    show-sql: false

  flyway:
    enabled: true
    baseline-on-migrate: true

jwt:
  secret: your-secret-key-here
  expiration: 86400000  # 24 小时

# 可选：启用盘点定时调度
stocktake:
  scheduler:
    enabled: false  # 默认禁用
    system-user-id: 1
    system-user-name: "系统管理员"
    default-warehouse-id: 1
```

### 启动步骤

1. **创建数据库**：
```sql
CREATE DATABASE wms_db;
```

2. **编译项目**：
```bash
mvn clean package -DskipTests
```

3. **运行应用**：
```bash
java -jar target/wms-system-0.0.1-SNAPSHOT.jar
```

4. **访问 API**：
```
http://localhost:8080/api
```

### 初始化数据

系统启动后，Flyway 会自动执行所有迁移脚本。

**默认管理员账号**（需要手动创建）：
```sql
INSERT INTO users (username, password, email, enabled)
VALUES ('admin', '$2a$10$...', 'admin@example.com', true);
```

---

## 📊 业务流程图

### 采购入库流程

```
创建采购单（ORDERING）
    ↓
填写商品明细
    ↓
确认并生成批次码（IN_TRANSIT）
    ↓
创建入库单
    ↓
确认入库（COMPLETED）
    ↓
更新库存和批次
```

### 销售出库流程

```
创建销售订单
    ↓
风控检查
    ↓ (需要审批)
经理审批
    ↓
智能分配库存（生成出库任务）
    ↓
仓库员拣货
    ↓
确认出库（扣减库存）
    ↓
订单完成（SHIPPED）
```

### 盘点流程

```
创建盘点任务（CREATED）
    ↓
智能选品（生成快照）
    ↓
开始盘点（COUNTING）
    ↓
仓库员录入实盘数量（盲盘）
    ↓
完成盘点（REVIEWING）
    ↓
经理审核差异
    ↓ (审核通过)
自动平账（COMPLETED）
    ↓
更新库存和流水
```

---

## 🎓 核心算法

### 1. 智能库存分配算法（V3.7）

```java
FOR EACH 订单明细:
    1. 如果有指定批次 → 强制使用指定批次
    2. 查询可用批次（active=true, quantity>0）
    3. 如果拒收临期品 → 过滤临期批次
    4. 检查库存充足性 → 不足则抛出异常
    5. 如果需求量是整箱 → 只从整箱批次分配
    6. 按 FEFO 排序（expiry_date ASC）
    7. 优先使用散货，不足时再拆箱
    8. 生成出库任务（status=PENDING）
```

### 2. 智能选品算法（V3.8）

```java
IF 月度盘点:
    查询最近30天有变动的批次
    限制最多500个批次
ELSE IF 季度/年度盘点:
    查询所有激活批次（全量盘点）
ELSE IF 临时盘点:
    按需选择批次

FOR EACH 选中批次:
    记录快照数量（snapshot_qty = batch.quantity）
    创建盘点明细（status=未盘点）
```

---

## 📈 系统特色

### 1. 盲盘功能 🙈
- 盘点时不显示账面数量
- 防止仓库员作弊
- 提高盘点准确性

### 2. 智能分配 🧠
- 整箱优先策略
- 临期过滤
- FEFO 先出
- 整散分离

### 3. 多角色 RBAC 👥
- 用户可绑定多个角色
- 动态角色切换
- 细粒度权限控制

### 4. 批次追溯 🔍
- 完整的批次生命周期
- 从采购到销售全程追溯
- 过期预警和管理

### 5. Excel 导入导出 📊
- 批量数据导入
- 模板下载
- 数据验证

### 6. 审批流程 ✅
- 采购审批
- 销售审批
- 盘点审核
- 审计日志

---

## 🔮 未来扩展

### 计划中的功能

- [ ] **移动端 APP** - 仓库员移动作业
- [ ] **条码扫描** - 扫码枪集成
- [ ] **库存预测** - AI 预测补货需求
- [ ] **拣货路径优化** - 最优拣货路线
- [ ] **多仓调拨** - 仓库间库存调拨
- [ ] **退货管理** - 销售退货和采购退货
- [ ] **报表中心** - 库存报表和统计分析
- [ ] **消息通知** - 邮件/短信/站内信通知
- [ ] **API 文档** - Swagger/OpenAPI 集成

---

## 📞 技术支持

### 开发团队
- **AI 架构师**: Claude (Anthropic)
- **项目负责人**: WMS Team

### 文档更新
- **创建日期**: 2026-01-29
- **最后更新**: 2026-01-29
- **版本**: V3.8

### 相关文档
- `README.md` - 项目说明
- `CLAUDE.md` - 开发规范
- `docs/` - 详细文档目录

---

## 📄 许可证

本项目为内部使用系统，版权所有。

---

**🎉 感谢使用 Claude WMS 智能仓储管理系统！**

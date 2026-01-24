# BC ERP-WMS 系统开发文档

> 版本：v3.3
> 更新日期：2026-01-17
> 作者：WMS Team

---

## 📋 目录

- [0. V3.3 迁移指南](#0-v33-迁移指南)
  - [0.1 Phase 1: 数据库与实体层](#01-phase-1-数据库与实体层)
- [1. 项目概述](#1-项目概述)
- [2. 技术栈](#2-技术栈)
- [3. 系统架构](#3-系统架构)
- [4. 项目结构](#4-项目结构)
- [5. 核心模块详解](#5-核心模块详解)
  - [5.1 实体层（Entity Layer）](#51-实体层entity-layer)
  - [5.2 数据访问层（Repository Layer）](#52-数据访问层repository-layer)
  - [5.3 服务层（Service Layer）](#53-服务层service-layer)
  - [5.4 控制器层（Controller Layer）](#54-控制器层controller-layer)
  - [5.5 安全模块（Security Module）](#55-安全模块security-module)
  - [5.6 异常处理（Exception Handling）](#56-异常处理exception-handling)
  - [5.7 工具类（Utilities）](#57-工具类utilities)
- [6. 核心业务流程](#6-核心业务流程)
  - [6.1 库存管理](#61-库存管理)
  - [6.2 采购单管理](#62-采购单管理)
  - [6.3 批次管理](#63-批次管理)
- [7. API 接口文档](#7-api-接口文档)
- [8. 数据库设计](#8-数据库设计)
- [9. 配置说明](#9-配置说明)
- [10. 开发规范](#10-开发规范)
- [11. 部署指南](#11-部署指南)
- [12. 常见问题](#12-常见问题)

---

## 0. V3.3 迁移指南

### 0.1 Phase 1: 数据库与实体层

本节介绍如何从旧版本迁移到 V3.3 架构（SPU-SKU 层级管理 + 多库位批次管理）。

#### 0.1.1 迁移前准备

**系统现状检查**：
- 数据库：PostgreSQL 16
- 旧版本特征：
  - 没有 `product_spu` 表
  - `products` 表缺少 `spu_id`, `sku_name`, `specs` 字段
  - `inventory_batch` 表缺少 `location_code` 字段

**备份数据**：
```bash
# 备份数据库（重要！）
pg_dump -U postgres -d wms_db > wms_db_backup_$(date +%Y%m%d).sql

# 验证备份文件
ls -lh wms_db_backup_*.sql
```

#### 0.1.2 执行数据库迁移

**步骤 1：运行迁移脚本**

```bash
# 连接到 PostgreSQL
psql -U postgres -d wms_db

# 执行迁移脚本
\i V3.3_Migration_Phase1.sql
```

**步骤 2：验证迁移结果**

迁移脚本会自动执行以下验证查询：

```sql
-- Check 1: 验证默认 SPU 创建成功
SELECT * FROM product_spu WHERE id = 0;
-- 预期结果：1 行，spu_code = 'DEFAULT-SPU'

-- Check 2: 验证所有产品都有 spu_id
SELECT COUNT(*) AS products_without_spu FROM products WHERE spu_id IS NULL;
-- 预期结果：0

-- Check 3: 验证所有产品都有 sku_name
SELECT COUNT(*) AS products_without_sku_name FROM products WHERE sku_name IS NULL;
-- 预期结果：0

-- Check 4: 统计产品分布
SELECT
    spu_id,
    COUNT(*) AS product_count
FROM products
GROUP BY spu_id
ORDER BY product_count DESC;
-- 预期结果：所有产品的 spu_id = 0

-- Check 5: 验证 inventory_batch 的 location_code
SELECT
    CASE
        WHEN location_code = 'PENDING' THEN 'In Transit (No Location)'
        WHEN location_code = 'UNASSIGNED' THEN 'Unassigned (Edge Case)'
        ELSE 'Received (Has Location)'
    END AS status,
    COUNT(*) AS batch_count
FROM inventory_batch
GROUP BY status;
```

#### 0.1.3 迁移脚本详解

**Section 1: 创建新表**

```sql
-- 创建 product_spu 表
CREATE TABLE product_spu (
    id BIGSERIAL PRIMARY KEY,
    spu_code VARCHAR(50) NOT NULL UNIQUE,
    spu_name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    brand VARCHAR(100),
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Section 2: 修改现有表**

```sql
-- 修改 products 表
ALTER TABLE products ADD COLUMN IF NOT EXISTS spu_id BIGINT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS sku_name VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS specs VARCHAR(500);

-- 修改 inventory_batch 表
ALTER TABLE inventory_batch ADD COLUMN IF NOT EXISTS location_code VARCHAR(50);
```

**Section 3: 数据清洗（关键步骤）**

```sql
-- 插入默认 SPU (ID=0)
INSERT INTO product_spu (id, spu_code, spu_name, category, brand, description, enabled)
VALUES (
    0,
    'DEFAULT-SPU',
    'Default Product Family (Legacy Data)',
    'Uncategorized',
    NULL,
    'Default SPU for existing products before V3.3 migration.',
    true
)
ON CONFLICT (id) DO NOTHING;

-- 更新现有产品的 spu_id
UPDATE products SET spu_id = 0 WHERE spu_id IS NULL;

-- 更新现有产品的 sku_name
UPDATE products SET sku_name = name WHERE sku_name IS NULL OR sku_name = '';

-- 更新 inventory_batch 的 location_code
UPDATE inventory_batch ib
SET location_code = l.location_code
FROM locations l
WHERE ib.location_id = l.id
  AND ib.location_code IS NULL;

-- 为未分配库位的批次设置占位符
UPDATE inventory_batch
SET location_code = 'PENDING'
WHERE location_code IS NULL AND location_id IS NULL;
```

**Section 4: 添加约束**

```sql
-- 添加 NOT NULL 约束
ALTER TABLE products ALTER COLUMN spu_id SET NOT NULL;
ALTER TABLE products ALTER COLUMN sku_name SET NOT NULL;
ALTER TABLE inventory_batch ALTER COLUMN location_code SET NOT NULL;

-- 添加外键约束
ALTER TABLE products
ADD CONSTRAINT fk_product_spu
FOREIGN KEY (spu_id)
REFERENCES product_spu(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_spu_id ON products(spu_id);
CREATE INDEX IF NOT EXISTS idx_batch_code ON inventory_batch(batch_code);
CREATE INDEX IF NOT EXISTS idx_batch_location ON inventory_batch(batch_code, location_code);
CREATE INDEX IF NOT EXISTS idx_location_code ON inventory_batch(location_code);
```

#### 0.1.4 更新实体类

**新增实体类**：

1. **ProductSpu.java**（产品 SPU 实体）

位置：`src/main/java/com/wms/system/entity/ProductSpu.java`

核心字段：
- `id`：主键（BIGSERIAL）
- `spuCode`：SPU 编码（唯一索引）
- `spuName`：SPU 名称
- `category`：产品分类
- `brand`：品牌名称
- `description`：SPU 描述
- `enabled`：是否启用

关系：
- 一个 SPU 对应多个 Product（SKU）
- `@OneToMany(mappedBy = "spu")`

**更新实体类**：

1. **Product.java**（已包含 SPU-SKU 架构）

新增字段：
- `spu`：关联的 SPU（@ManyToOne）
- `skuName`：SKU 特定名称
- `specs`：规格描述

保留字段（重要）：
- `packUnit`：包装单位（如 "Box"）
- `conversionRate`：换算率（如 12 = 1箱12瓶）

2. **InventoryBatch.java**（已包含多库位支持）

新增字段：
- `locationCode`：库位编号字符串（冗余字段，用于快速查询）

特性变更：
- 移除 `batch_code` 的唯一约束
- 添加复合索引 `(batch_code, location_code)`
- 支持同一批次分散在多个库位

#### 0.1.5 测试迁移结果

**步骤 1：启动应用**

```bash
mvn clean spring-boot:run
```

JPA 会自动检测数据库模式变化，如果迁移成功，应用应该正常启动。

**步骤 2：验证实体类映射**

检查启动日志，确保没有 Hibernate 错误：

```
Hibernate: ...
JPA metamodel processing completed successfully
```

**步骤 3：测试 API 接口**

```bash
# 查询所有 SPU
curl -X GET http://localhost:8080/api/products/spu

# 查询默认 SPU 下的所有 SKU
curl -X GET http://localhost:8080/api/products?spuId=0
```

#### 0.1.6 后续步骤

**数据整理**：

1. **创建正确的 SPU**

```sql
-- 示例：创建"茶叶" SPU
INSERT INTO product_spu (spu_code, spu_name, category, brand, description, enabled)
VALUES (
    'TEA-001',
    '茶叶',
    '饮料',
    NULL,
    '各类茶叶产品，包括绿茶、红茶、乌龙茶等',
    true
);
```

2. **重新分配产品到正确的 SPU**

```sql
-- 将茶叶类产品从默认 SPU (ID=0) 迁移到茶叶 SPU
UPDATE products
SET spu_id = (SELECT id FROM product_spu WHERE spu_code = 'TEA-001')
WHERE name LIKE '%茶%' OR name LIKE '%Tea%';
```

3. **验证数据迁移**

```sql
-- 检查每个 SPU 下的产品数量
SELECT
    ps.spu_code,
    ps.spu_name,
    COUNT(p.id) AS sku_count
FROM product_spu ps
LEFT JOIN products p ON p.spu_id = ps.id
GROUP BY ps.id, ps.spu_code, ps.spu_name
ORDER BY sku_count DESC;
```

#### 0.1.7 回滚方案

如果迁移失败，可以执行回滚脚本（位于 `V3.3_Migration_Phase1.sql` 末尾）：

```sql
-- 删除外键约束
ALTER TABLE products DROP CONSTRAINT IF EXISTS fk_product_spu;

-- 删除索引
DROP INDEX IF EXISTS idx_spu_id;
DROP INDEX IF EXISTS idx_batch_code;
DROP INDEX IF EXISTS idx_batch_location;
DROP INDEX IF EXISTS idx_location_code;

-- 删除新增字段
ALTER TABLE products DROP COLUMN IF EXISTS spu_id;
ALTER TABLE products DROP COLUMN IF EXISTS sku_name;
ALTER TABLE products DROP COLUMN IF EXISTS specs;
ALTER TABLE inventory_batch DROP COLUMN IF EXISTS location_code;

-- 删除 product_spu 表
DROP TABLE IF EXISTS product_spu CASCADE;

-- 恢复数据库备份
-- psql -U postgres -d wms_db < wms_db_backup_YYYYMMDD.sql
```

#### 0.1.8 常见问题

**Q1: 迁移失败，提示外键约束错误**

```
ERROR: insert or update on table "products" violates foreign key constraint "fk_product_spu"
```

**A1**: 检查是否有产品的 `spu_id` 为 NULL。运行以下查询：

```sql
SELECT COUNT(*) FROM products WHERE spu_id IS NULL;
```

如果有结果，手动更新：

```sql
UPDATE products SET spu_id = 0 WHERE spu_id IS NULL;
```

**Q2: InventoryBatch 的 location_code 为 NULL**

```
ERROR: null value in column "location_code" violates not-null constraint
```

**A2**: 运行以下 SQL 更新：

```sql
UPDATE inventory_batch
SET location_code = 'UNASSIGNED'
WHERE location_code IS NULL;
```

**Q3: 应用启动失败，Hibernate 提示字段不匹配**

**A3**: 确保实体类字段名与数据库字段名匹配：
- `@Column(name = "spu_code")` → `private String spuCode;`
- `@Column(name = "spu_name")` → `private String spuName;`
- `@Column(name = "location_code")` → `private String locationCode;`

---

## 1. 项目概述

### 1.1 项目简介

BC ERP-WMS（Warehouse Management System）是一个基于 Spring Boot 3.2.x 和 Java 17 开发的现代化仓库管理系统，旨在为企业提供完整的库存管理、采购管理、批次追溯等功能。系统采用前后端分离架构，提供 RESTful API 接口供前端调用。

### 1.2 核心功能

- **库存管理**：实时库存查询、库存调整（入库/出库/盘点）、库存预警
- **采购管理**：采购单创建、ASN 确认、批次码生成、实物入库
- **批次追溯**：基于 Hashids 的批次码生成、批次全流程追踪、FIFO 先进先出
- **库位管理**：多仓库、多库区、多货架、多库位的层级管理
- **用户认证**：基于 JWT 的无状态认证、角色权限控制
- **数据审计**：完整的操作日志、库存变更前后记录

### 1.3 系统特点

- **高并发控制**：乐观锁（@Version）+ 自动重试机制（@Retryable）
- **单一数据源**：InventoryBatch 作为库存的唯一真实来源（Single Source of Truth）
- **错误键系统**：国际化友好的错误处理机制
- **SPU-SKU 架构**：支持商品家族（SPU）和具体规格（SKU）的层级管理
- **三阶段采购流程**：ORDERING → IN_TRANSIT → COMPLETED

---

## 2. 技术栈

### 2.1 后端技术

| 技术/框架 | 版本 | 用途 |
|---------|------|------|
| Java | 17 (LTS) | 开发语言 |
| Spring Boot | 3.2.11 | 应用框架 |
| Spring Data JPA | 自动集成 | ORM 框架（基于 Hibernate） |
| Spring Security | 自动集成 | 安全框架（认证与授权） |
| PostgreSQL | 16 | 关系型数据库 |
| HikariCP | 5.1.x | 数据库连接池 |
| Lombok | 1.18.30 | 代码简化工具 |
| JJWT | 0.12.3 | JWT Token 生成与解析 |
| Hashids | 1.0.3 | 批次码生成（短码加密） |
| Apache POI | 5.2.3 | Excel 文件解析 |
| Spring Retry | 自动集成 | 重试机制（处理乐观锁冲突） |

### 2.2 开发工具

- **构建工具**：Maven 3.9+
- **IDE**：IntelliJ IDEA（推荐）
- **版本控制**：Git
- **API 测试**：Postman / curl

---

## 3. 系统架构

### 3.1 分层架构

系统采用经典的分层架构设计，自上而下分为以下几层：

```
┌─────────────────────────────────────────┐
│          Controller Layer               │  ← RESTful API 接口层
│      (处理 HTTP 请求，参数校验)            │
├─────────────────────────────────────────┤
│           Service Layer                 │  ← 业务逻辑层
│    (核心业务逻辑，事务管理，并发控制)       │
├─────────────────────────────────────────┤
│         Repository Layer                │  ← 数据访问层
│      (JPA Repository，数据库操作)         │
├─────────────────────────────────────────┤
│           Entity Layer                  │  ← 实体层
│         (JPA 实体，映射数据库表)           │
├─────────────────────────────────────────┤
│         PostgreSQL Database             │  ← 数据库
└─────────────────────────────────────────┘
```

### 3.2 核心设计模式

1. **DTO 模式**：控制器层使用 DTO（Data Transfer Object）与前端交互，不直接暴露 Entity
2. **Repository 模式**：使用 Spring Data JPA Repository 简化数据访问
3. **Builder 模式**：使用 Lombok @Builder 简化对象构建
4. **错误键模式**：统一错误码管理，支持国际化

### 3.3 并发控制策略

系统采用**乐观锁**来处理库存并发更新问题：

```
用户 A 读取库存（quantity=100, version=1）
用户 B 读取库存（quantity=100, version=1）
用户 A 提交更新（quantity=90, version=2）✅ 成功
用户 B 提交更新（quantity=80, version=2）❌ 失败（version 已变化）
系统自动重试用户 B 的操作（最多 3 次）
```

---

## 4. 项目结构

```
wms-system/
├── src/main/java/com/wms/system/
│   ├── config/                          # 配置类
│   │   └── JpaAuditingConfig.java       # JPA 审计配置（自动填充创建/更新时间）
│   │
│   ├── controller/                      # 控制器层（REST API）
│   │   ├── AuthController.java          # 认证接口（登录）
│   │   ├── InventoryController.java     # 库存管理接口
│   │   ├── PurchaseOrderController.java # 采购单管理接口
│   │   ├── InventoryBatchController.java# 批次管理接口
│   │   ├── StockPredictionController.java # 库存预测接口
│   │   ├── HealthCheckController.java   # 健康检查接口
│   │   └── GlobalExceptionHandler.java  # 全局异常处理器
│   │
│   ├── dto/                             # 数据传输对象（DTO）
│   │   ├── LoginRequest.java            # 登录请求 DTO
│   │   ├── LoginResponse.java           # 登录响应 DTO
│   │   ├── StockAdjustmentRequest.java  # 库存调整请求 DTO
│   │   ├── StockTransactionResponse.java# 库存流水响应 DTO
│   │   ├── PurchaseOrderResponse.java   # 采购单响应 DTO
│   │   ├── InventoryBatchResponse.java  # 批次响应 DTO
│   │   ├── ErrorResponse.java           # 错误响应 DTO
│   │   └── ...
│   │
│   ├── entity/                          # 实体类（JPA Entity）
│   │   ├── BaseEntity.java              # 基础实体（自动审计字段）
│   │   ├── User.java                    # 用户实体
│   │   ├── Product.java                 # 商品 SKU 实体
│   │   ├── ProductSpu.java              # 商品 SPU 实体
│   │   ├── Location.java                # 库位实体
│   │   ├── Inventory.java               # 库存实体（已废弃，仅用于查询）
│   │   ├── InventoryBatch.java          # 库存批次实体（单一数据源）
│   │   ├── StockTransaction.java        # 库存流水实体
│   │   ├── PurchaseOrder.java           # 采购单主表实体
│   │   ├── PurchaseOrderItem.java       # 采购单明细实体
│   │   └── enums/                       # 枚举类
│   │       ├── Role.java                # 角色枚举
│   │       ├── TransactionType.java     # 事务类型枚举
│   │       ├── SourceType.java          # 来源类型枚举
│   │       ├── PurchaseOrderStatus.java # 采购单状态枚举
│   │       └── Zone.java                # 库区枚举
│   │
│   ├── exception/                       # 异常处理
│   │   ├── BusinessException.java       # 业务异常类
│   │   └── ErrorKeys.java               # 错误键常量
│   │
│   ├── repository/                      # 数据访问层（JPA Repository）
│   │   ├── UserRepository.java
│   │   ├── ProductRepository.java
│   │   ├── LocationRepository.java
│   │   ├── InventoryRepository.java
│   │   ├── InventoryBatchRepository.java
│   │   ├── StockTransactionRepository.java
│   │   ├── PurchaseOrderRepository.java
│   │   └── PurchaseOrderItemRepository.java
│   │
│   ├── security/                        # 安全模块
│   │   ├── SecurityConfig.java          # Spring Security 配置
│   │   ├── JwtUtil.java                 # JWT 工具类
│   │   ├── JwtAuthenticationFilter.java # JWT 认证过滤器
│   │   ├── CustomUserDetailsService.java# 用户详情服务
│   │   └── SecurityUser.java            # 安全用户包装类
│   │
│   ├── service/                         # 业务逻辑层
│   │   ├── InventoryService.java        # 库存管理服务
│   │   ├── PurchaseOrderService.java    # 采购单管理服务
│   │   ├── InventoryBatchService.java   # 批次管理服务
│   │   ├── StockPredictionService.java  # 库存预测服务
│   │   └── ExcelImportService.java      # Excel 导入服务
│   │
│   ├── util/                            # 工具类
│   │   └── BatchCodeGenerator.java      # 批次码生成器（Hashids）
│   │
│   └── WmsSystemApplication.java        # 主启动类
│
├── src/main/resources/
│   ├── application.yml                  # 核心配置文件
│   └── logback-spring.xml               # 日志配置（可选）
│
├── src/test/java/                       # 测试代码
│
├── docs/                                # 项目文档
│   ├── ENTITY_MODELING.md               # 实体建模文档
│   └── REPOSITORY_LAYER.md              # 数据访问层文档
│
├── pom.xml                              # Maven 依赖配置
└── README.md                            # 项目说明
```

---

## 5. 核心模块详解

### 5.1 实体层（Entity Layer）

实体层定义了系统的数据模型，所有实体类都使用 JPA 注解映射到数据库表。

#### 5.1.1 BaseEntity（基础实体）

所有实体类的父类，提供通用的审计字段：

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 创建时间（自动填充）

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;  // 更新时间（自动填充）
}
```

**特性**：
- 使用 `@CreatedDate` 和 `@LastModifiedDate` 自动管理时间戳
- 需要在配置类中启用 `@EnableJpaAuditing`

#### 5.1.2 Product（商品 SKU 实体）

商品 SKU 实体是库存管理的核心，代表具体可销售的商品规格。

**核心字段**：
- `id`：主键（自增）
- `spu`：关联的 SPU（产品家族）
- `skuName`：SKU 特定名称（如 "绿茶-箱装"）
- `specs`：规格描述（JSON 或文本）
- `barcode`：条形码（唯一索引）
- `name`：商品全称
- `unitPrice`：单价（BigDecimal，精度 10,2）
- `minStock`：安全库存（预警阈值）
- `leadTime`：采购提前期（天数）
- `packUnit`：包装单位（如 "Box"）
- `conversionRate`：换算率（1 箱 = N 个基础单位）

**V3.3 新特性（SPU-SKU 架构）**：
- 每个 SKU 必须归属于一个 SPU
- 支持 `formatQuantity()` 方法：将数量格式化为 "2 Box, 1 Unit"
- 支持 `isLooseQuantity()` 方法：判断是否为零头（已开箱）
- 支持 `isFullPack()` 方法：判断是否为整箱

#### 5.1.3 InventoryBatch（库存批次实体）⭐

**V3.0 架构：单一数据源（Single Source of Truth）**

InventoryBatch 是系统中唯一的库存数据源，所有库存查询都从这里实时聚合。

**核心字段**：
- `id`：主键（自增）
- `batchCode`：批次码（Hashids 生成，唯一索引）
- `purchaseOrderItem`：关联的采购单明细
- `product`：关联的商品
- `location`：关联的库位（入库后赋值）
- `quantity`：当前批次数量
- `initialQuantity`：初始数量（不变）
- `expiryDate`：过期日期
- `productionDate`：生产日期
- `externalBatchCode`：外部批次码（供应商提供）
- `entryDate`：入库时间（实物入库后赋值）
- `active`：批次是否有效（支持作废）
- `version`：乐观锁版本号

**核心方法**：
```java
// 分配库位（Stage 3）
public void assignLocation(Location location) {
    this.location = location;
    this.entryDate = LocalDateTime.now();
}

// 作废批次
public void markAsInactive(String reason) {
    this.active = false;
    this.remark = reason;
}

// 扣减数量（出库）
public void decreaseQuantity(Integer outboundQuantity) {
    if (this.quantity < outboundQuantity) {
        throw new IllegalStateException("批次库存不足");
    }
    this.quantity -= outboundQuantity;
}
```

#### 5.1.4 PurchaseOrder（采购单主表）

采购单主表，采用 Master-Detail 架构。

**核心字段**：
- `poNumber`：采购单号（格式：PO-YYYYMMDD-XXX）
- `supplier`：供应商名称
- `status`：采购单状态（枚举）
- `totalQuantity`：总采购数量
- `totalCost`：总成本
- `expectedDate`：期望到货日期
- `actualEntryDate`：实际入库时间
- `auditLog`：审计日志（记录状态变更）
- `items`：采购单明细（一对多）

**状态流转**：
```
ORDERING → IN_TRANSIT → PARTIALLY_RECEIVED → COMPLETED
    ↓           ↑
    └───────────┘ (支持回退)
```

#### 5.1.5 Location（库位实体）

库位实体，支持多层级管理：仓库 → 库区 → 货架 → 层 → 库位。

**核心字段**：
- `warehouse`：仓库关系（Phase 3.4 新增，ManyToOne 关系）
- `warehouseCode`：仓库编码（冗余字段，从 warehouse.code 自动同步）
- `zone`：库区枚举（ZONE_A, ZONE_B, ZONE_C, ZONE_D）
- `shelfNumber`：货架编码（如 "A-01"）
- `positionNumber`：位号（如 "001"）
- `locationCode`：完整库位码（自动生成，如 "WH01-ZONE_A-A-01-001"）
- `enabled`：是否启用

---

### 5.2 数据访问层（Repository Layer）

数据访问层使用 Spring Data JPA Repository 模式，提供开箱即用的 CRUD 方法和自定义查询。

#### 5.2.1 基础 Repository 接口

所有 Repository 继承 `JpaRepository<T, ID>`，自动获得以下方法：
- `save(entity)` / `saveAll(entities)`：保存
- `findById(id)` / `findAll()`：查询
- `deleteById(id)` / `delete(entity)`：删除
- `count()`：统计

#### 5.2.2 InventoryBatchRepository（批次仓库）

```java
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    // 根据批次码查询（唯一）
    Optional<InventoryBatch> findByBatchCode(String batchCode);

    // 根据采购单明细 ID 查询所有批次
    List<InventoryBatch> findByPurchaseOrderItemId(Long itemId);

    // 根据商品和库位查询所有有效批次
    List<InventoryBatch> findByProductAndLocationAndActiveTrue(
        Product product, Location location
    );

    // 查询即将过期的批次（用于预警）
    @Query("SELECT b FROM InventoryBatch b WHERE b.expiryDate <= :date AND b.active = true")
    List<InventoryBatch> findExpiringBatches(@Param("date") LocalDate date);

    // 聚合查询：商品的总库存（所有批次汇总）
    @Query("SELECT SUM(b.quantity) FROM InventoryBatch b " +
           "WHERE b.product.id = :productId AND b.active = true")
    Integer sumTotalQuantityByProduct(@Param("productId") Long productId);
}
```

#### 5.2.3 PurchaseOrderRepository（采购单仓库）

```java
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // 根据采购单号查询
    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    // 根据状态查询
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    // 查询最新的采购单（用于生成单号）
    @Query("SELECT po FROM PurchaseOrder po WHERE po.poNumber LIKE :prefix% " +
           "ORDER BY po.createdAt DESC")
    List<PurchaseOrder> findLatestByDatePrefix(@Param("prefix") String prefix);
}
```

---

### 5.3 服务层（Service Layer）

服务层包含核心业务逻辑，负责事务管理、并发控制和业务规则校验。

#### 5.3.1 InventoryService（库存管理服务）

**核心方法**：`adjustStock()`

```java
@Transactional(rollbackFor = Exception.class)
@Retryable(
    retryFor = {OptimisticLockException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
public StockTransaction adjustStock(StockAdjustmentRequest request) {
    // 1. 查询商品和库位
    Product product = productRepository.findById(request.getProductId())
        .orElseThrow(() -> new BusinessException(ErrorKeys.PRODUCT_NOT_FOUND, ...));

    Location location = locationRepository.findById(request.getLocationId())
        .orElseThrow(() -> new BusinessException(ErrorKeys.LOCATION_NOT_FOUND, ...));

    // 2. 查询或创建库存记录
    Inventory inventory = inventoryRepository
        .findByProductAndLocation(product, location)
        .orElseGet(() -> createNewInventory(product, location));

    Integer quantityBefore = inventory.getQuantity();

    // 3. 根据事务类型更新库存
    switch (request.getTransactionType()) {
        case IN  -> inventory.increaseQuantity(request.getQuantity());
        case OUT -> {
            if (inventory.getQuantity() < request.getQuantity()) {
                throw new BusinessException(ErrorKeys.STOCK_INSUFFICIENT, ...);
            }
            inventory.decreaseQuantity(request.getQuantity());
        }
        case ADJUST -> inventory.setQuantity(...);
    }

    // 4. 保存库存（可能触发乐观锁异常）
    Inventory savedInventory = inventoryRepository.save(inventory);

    // 5. 生成库存流水
    StockTransaction transaction = createStockTransaction(...);
    return stockTransactionRepository.save(transaction);
}
```

**并发控制**：
- 使用 `@Version` 乐观锁防止并发更新冲突
- 使用 `@Retryable` 自动重试（最多 3 次，间隔 1 秒）
- 捕获 `OptimisticLockException` 并转换为业务异常

#### 5.3.2 PurchaseOrderService（采购单管理服务）

**三阶段采购流程**：

**Stage 1: 创建采购单（ORDERING）**

```java
@Transactional
public PurchaseOrder createPurchaseOrder(
    String supplier,
    List<PurchaseOrderItemData> items,
    LocalDate expectedDate,
    Long operatorId,
    String operatorName,
    String remark
) {
    // 1. 生成采购单号（PO-YYYYMMDD-XXX）
    String poNumber = generatePoNumber();

    // 2. 创建采购单主表
    PurchaseOrder purchaseOrder = PurchaseOrder.builder()
        .poNumber(poNumber)
        .supplier(supplier)
        .status(PurchaseOrderStatus.ORDERING)
        .build();

    // 3. 创建采购单明细
    for (PurchaseOrderItemData itemData : items) {
        Product product = productRepository.findById(itemData.getProductId())
            .orElseThrow(...);

        PurchaseOrderItem item = PurchaseOrderItem.builder()
            .purchaseOrder(purchaseOrder)
            .product(product)
            .orderedQuantity(itemData.getOrderedQuantity())
            .receivedQuantity(0)
            .build();

        purchaseOrder.addItem(item);
    }

    // 4. 保存（级联保存明细）
    return purchaseOrderRepository.save(purchaseOrder);
}
```

**Stage 2: 确认 ASN 并生成批次码（IN_TRANSIT）**

```java
@Transactional
public PurchaseOrder confirmAndGenerateBatchCodes(
    Long purchaseOrderId,
    List<ItemExpiryUpdate> itemUpdates
) {
    // 1. 查询采购单
    PurchaseOrder purchaseOrder = findById(purchaseOrderId);

    // 2. 验证状态（必须是 ORDERING）
    if (!purchaseOrder.getStatus().canConfirmAndGenerateBatch()) {
        throw new BusinessException(ErrorKeys.PO_INVALID_STATUS, ...);
    }

    // 3. 更新过期日期（必填）
    for (ItemExpiryUpdate update : itemUpdates) {
        PurchaseOrderItem item = findItemById(update.getItemId());
        item.setExpiryDate(update.getExpiryDate());

        // 验证过期日期不能为空
        if (item.getExpiryDate() == null) {
            throw new BusinessException(ErrorKeys.PO_EXPIRY_DATE_REQUIRED, ...);
        }
    }

    // 4. 生成批次码（原子操作）
    for (PurchaseOrderItem item : purchaseOrder.getItems()) {
        String batchCode = batchCodeGenerator.generateUnique(...);

        InventoryBatch batch = InventoryBatch.builder()
            .batchCode(batchCode)
            .purchaseOrderItem(item)
            .product(item.getProduct())
            .location(null)  // Stage 2: 未分配库位
            .quantity(item.getOrderedQuantity())
            .expiryDate(item.getExpiryDate())
            .entryDate(null)  // Stage 2: 未入库
            .active(true)
            .build();

        inventoryBatchRepository.save(batch);
    }

    // 5. 更新状态为 IN_TRANSIT
    purchaseOrder.setStatus(PurchaseOrderStatus.IN_TRANSIT);
    purchaseOrder.appendAuditLog("Confirmed ASN and generated batch codes");

    return purchaseOrderRepository.save(purchaseOrder);
}
```

**Stage 3: 实物入库（COMPLETED）**

```java
@Transactional
@Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 3)
public PurchaseOrder receiveGoods(
    Long purchaseOrderId,
    List<BatchReceiptData> receiptData,
    Long operatorId,
    String operatorName
) {
    // 1. 查询采购单
    PurchaseOrder purchaseOrder = findById(purchaseOrderId);

    // 2. 验证状态（必须是 IN_TRANSIT 或 PARTIALLY_RECEIVED）
    if (!purchaseOrder.getStatus().canReceive()) {
        throw new BusinessException(ErrorKeys.PO_INVALID_STATUS, ...);
    }

    // 3. 处理每个批次的入库
    for (BatchReceiptData receipt : receiptData) {
        // 查询批次
        InventoryBatch batch = inventoryBatchRepository
            .findByBatchCode(receipt.getBatchCode())
            .orElseThrow(...);

        // 查询库位
        Location location = locationRepository.findById(receipt.getLocationId())
            .orElseThrow(...);

        // 分配库位并记录入库时间
        batch.assignLocation(location);  // 设置 location 和 entryDate
        inventoryBatchRepository.save(batch);

        // 生成库存流水
        StockTransaction transaction = StockTransaction.builder()
            .product(batch.getProduct())
            .location(location)
            .transactionType(TransactionType.IN)
            .sourceType(SourceType.PURCHASE_IN)
            .quantity(batch.getQuantity())
            .build();

        stockTransactionRepository.save(transaction);

        // 更新明细的已收货数量
        batch.getPurchaseOrderItem().increaseReceivedQuantity(batch.getQuantity());
    }

    // 4. 检查是否全部入库
    if (purchaseOrder.isFullyReceived()) {
        purchaseOrder.setStatus(PurchaseOrderStatus.COMPLETED);
        purchaseOrder.setActualEntryDate(LocalDateTime.now());
    } else {
        purchaseOrder.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    return purchaseOrderRepository.save(purchaseOrder);
}
```

---

### 5.4 控制器层（Controller Layer）

控制器层提供 RESTful API 接口，负责请求处理、参数校验和响应封装。

#### 5.4.1 InventoryController（库存管理控制器）

**API 设计原则**：
- 使用 DTO 而非 Entity
- 使用 `@Valid` 进行参数校验
- 统一异常处理（GlobalExceptionHandler）
- 返回标准 HTTP 状态码

**示例**：库存调整接口

```java
@PostMapping("/adjust")
public ResponseEntity<StockTransactionResponse> adjustStock(
    @Valid @RequestBody StockAdjustmentRequest request
) {
    // 调用服务层
    StockTransaction transaction = inventoryService.adjustStock(request);

    // 转换为 DTO（不暴露 Entity）
    StockTransactionResponse response = mapToResponse(transaction);

    return ResponseEntity.ok(response);
}
```

**请求示例**：
```json
POST /api/inventory/adjust
{
  "productId": 123,
  "locationId": 456,
  "transactionType": "OUT",
  "sourceType": "SALE_OUT",
  "quantity": 50,
  "operatorId": 1,
  "operatorName": "John Doe"
}
```

**成功响应**：
```json
{
  "id": 1001,
  "productId": 123,
  "productName": "Coca-Cola 500ml",
  "locationId": 456,
  "locationCode": "WH01-ZONE_A-A-01-001",
  "transactionType": "OUT",
  "sourceType": "SALE_OUT",
  "quantity": 50,
  "quantityBefore": 200,
  "quantityAfter": 150,
  "createdAt": "2025-01-11T10:30:00+00:00"
}
```

**错误响应**（库存不足）：
```json
{
  "errorKey": "STOCK_INSUFFICIENT",
  "params": {
    "productId": 123,
    "currentStock": 30,
    "requestedQuantity": 50,
    "shortage": 20
  },
  "timestamp": "2025-01-11T10:30:00",
  "path": "/api/inventory/adjust",
  "status": 400
}
```

---

### 5.5 安全模块（Security Module）

系统使用 Spring Security + JWT 实现无状态认证。

#### 5.5.1 认证流程

```
1. 用户登录 → POST /api/auth/login
   ↓
2. 验证用户名密码
   ↓
3. 生成 JWT Token → 返回给前端
   ↓
4. 前端存储 Token（localStorage）
   ↓
5. 后续请求携带 Token → Authorization: Bearer <token>
   ↓
6. JwtAuthenticationFilter 拦截并验证 Token
   ↓
7. 提取用户信息并设置到 SecurityContext
   ↓
8. 执行业务逻辑
```

#### 5.5.2 JwtUtil（JWT 工具类）

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // 生成 Token
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
            .setSubject(username)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
    }

    // 验证 Token
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    // 提取用户名
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }
}
```

#### 5.5.3 SecurityConfig（安全配置）

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // 禁用 CSRF（JWT 无状态）
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 无状态
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/health/**").permitAll()  // 公开接口
                .requestMatchers("/api/admin/**").hasRole("ADMIN")  // 管理员权限
                .anyRequest().authenticated())  // 其他接口需要认证
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);  // 添加 JWT 过滤器

        return http.build();
    }
}
```

---

### 5.6 异常处理（Exception Handling）

系统使用统一的异常处理机制，支持错误键（ErrorKey）国际化。

#### 5.6.1 BusinessException（业务异常）

```java
public class BusinessException extends RuntimeException {
    private final String errorKey;
    private final Map<String, Object> params;

    public BusinessException(String errorKey, Map<String, Object> params) {
        super(errorKey);
        this.errorKey = errorKey;
        this.params = params;
    }
}
```

#### 5.6.2 ErrorKeys（错误键常量）

```java
public class ErrorKeys {
    // 商品相关
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";

    // 库位相关
    public static final String LOCATION_NOT_FOUND = "LOCATION_NOT_FOUND";

    // 库存相关
    public static final String STOCK_INSUFFICIENT = "STOCK_INSUFFICIENT";
    public static final String STOCK_CONCURRENCY_CONFLICT = "STOCK_CONCURRENCY_CONFLICT";

    // 采购单相关
    public static final String PURCHASE_ORDER_NOT_FOUND = "PURCHASE_ORDER_NOT_FOUND";
    public static final String PO_INVALID_STATUS = "PO_INVALID_STATUS";
    public static final String PO_EXPIRY_DATE_REQUIRED = "PO_EXPIRY_DATE_REQUIRED";

    // 批次相关
    public static final String BATCH_NOT_FOUND = "BATCH_NOT_FOUND";
    public static final String BATCH_INACTIVE = "BATCH_INACTIVE";
}
```

#### 5.6.3 GlobalExceptionHandler（全局异常处理器）

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
            .errorKey(ex.getErrorKey())
            .params(ex.getParams())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(...) {
        // 处理参数校验异常
    }
}
```

---

### 5.7 工具类（Utilities）

#### 5.7.1 BatchCodeGenerator（批次码生成器）

使用 Hashids 算法生成短码批次号：

```java
@Component
public class BatchCodeGenerator {

    @Value("${batch.hashids.salt}")
    private String salt;

    @Value("${batch.hashids.min-length}")
    private int minLength;

    private Hashids hashids;

    @PostConstruct
    public void init() {
        this.hashids = new Hashids(salt, minLength);
    }

    public String generateUnique(
        Long productId,
        Long itemId,
        InventoryBatchRepository repository
    ) {
        int attempt = 0;
        String batchCode;

        do {
            long seed = System.currentTimeMillis() + attempt;
            batchCode = hashids.encode(productId, itemId, seed);
            attempt++;

            if (attempt > 100) {
                throw new RuntimeException("批次码生成失败：尝试次数过多");
            }
        } while (repository.findByBatchCode(batchCode).isPresent());

        return batchCode;
    }
}
```

**特性**：
- 短码（默认 6 位）
- 唯一性保证（碰撞检测）
- 不可逆（安全）
- 示例：`R7M4K9`

---

## 6. 核心业务流程

### 6.1 库存管理

**入库流程**：
1. 前端发送入库请求 → `POST /api/inventory/adjust`
2. InventoryService 验证商品和库位存在
3. 查询或创建库存记录（Inventory）
4. 增加库存数量
5. 生成库存流水（StockTransaction）
6. 返回操作结果

**出库流程**：
1. 前端发送出库请求
2. InventoryService 验证库存是否充足
3. 扣减库存数量
4. 生成库存流水
5. 如果库存不足，抛出 `STOCK_INSUFFICIENT` 异常

**并发控制**：
- 使用乐观锁（@Version）防止并发更新冲突
- 冲突时自动重试（最多 3 次）

---

### 6.2 采购单管理

**完整流程（三阶段）**：

```
Stage 1: ORDERING（创建采购单）
  ↓
  - 上传 Excel 或手动录入
  - 生成采购单号（PO-YYYYMMDD-XXX）
  - 创建采购单主表和明细
  - 过期日期可选
  ↓
Stage 2: IN_TRANSIT（确认 ASN 并生成批次码）
  ↓
  - 供应商提供过期日期（必填）
  - 系统生成 Hashids 批次码
  - 创建 InventoryBatch 记录（location = null, entryDate = null）
  - 状态变更为 IN_TRANSIT
  ↓
Stage 3: COMPLETED（实物入库）
  ↓
  - 扫描批次码
  - 分配库位
  - 记录入库时间（entryDate）
  - 生成库存流水
  - 更新已收货数量
  - 状态变更为 PARTIALLY_RECEIVED 或 COMPLETED
```

**状态回退**：
- 仅支持 `IN_TRANSIT → ORDERING`
- 作废所有已生成的批次码（active = false）
- 记录回退原因

---

### 6.3 批次管理

**V3.0 架构：单一数据源（Single Source of Truth）**

所有库存查询都从 InventoryBatch 实时聚合：

```java
// 查询商品总库存
Integer totalStock = inventoryBatchRepository
    .sumTotalQuantityByProduct(productId);

// 查询库位库存
List<InventoryBatch> batches = inventoryBatchRepository
    .findByProductAndLocationAndActiveTrue(product, location);
Integer locationStock = batches.stream()
    .mapToInt(InventoryBatch::getQuantity)
    .sum();
```

**FIFO 出库策略（零头优先）**：
1. 查询所有有效批次（按入库时间排序）
2. 优先扣减零头批次（quantity % conversionRate != 0）
3. 零头不足时再拆新箱（整箱批次）
4. 更新批次数量（乐观锁保护）

---

## 7. API 接口文档

### 7.1 认证接口

#### 7.1.1 用户登录

**请求**：
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

**响应**：
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "admin",
  "role": "ADMIN",
  "expiresIn": 86400000
}
```

---

### 7.2 库存管理接口

#### 7.2.1 库存调整

**请求**：
```
POST /api/inventory/adjust
Authorization: Bearer <token>
Content-Type: application/json

{
  "productId": 123,
  "locationId": 456,
  "transactionType": "OUT",
  "sourceType": "SALE_OUT",
  "quantity": 50,
  "operatorId": 1,
  "operatorName": "John Doe",
  "remark": "销售出库"
}
```

**响应**：
```json
{
  "id": 1001,
  "productId": 123,
  "productName": "Coca-Cola 500ml",
  "transactionType": "OUT",
  "quantity": 50,
  "quantityBefore": 200,
  "quantityAfter": 150,
  "createdAt": "2025-01-11T10:30:00"
}
```

---

### 7.3 采购单管理接口

#### 7.3.1 创建采购单

**请求**：
```
POST /api/purchase-orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "supplier": "供应商A",
  "expectedDate": "2025-01-20",
  "items": [
    {
      "productId": 123,
      "orderedQuantity": 100,
      "unitCost": 12.50
    }
  ],
  "operatorId": 1,
  "operatorName": "admin"
}
```

**响应**：
```json
{
  "id": 1,
  "poNumber": "PO-20250117-001",
  "supplier": "供应商A",
  "status": "ORDERING",
  "totalQuantity": 100,
  "totalCost": 1250.00,
  "createdAt": "2025-01-17T10:00:00"
}
```

#### 7.3.2 确认 ASN 并生成批次码

**请求**：
```
POST /api/purchase-orders/{id}/confirm-asn
Authorization: Bearer <token>
Content-Type: application/json

{
  "itemUpdates": [
    {
      "itemId": 1,
      "expiryDate": "2026-01-17",
      "productionDate": "2025-01-10"
    }
  ]
}
```

**响应**：
```json
{
  "id": 1,
  "poNumber": "PO-20250117-001",
  "status": "IN_TRANSIT",
  "items": [
    {
      "id": 1,
      "productId": 123,
      "orderedQuantity": 100,
      "receivedQuantity": 0,
      "batches": [
        {
          "batchCode": "R7M4K9",
          "quantity": 100,
          "expiryDate": "2026-01-17"
        }
      ]
    }
  ]
}
```

#### 7.3.3 实物入库

**请求**：
```
POST /api/purchase-orders/{id}/receive
Authorization: Bearer <token>
Content-Type: application/json

{
  "receiptData": [
    {
      "batchCode": "R7M4K9",
      "locationId": 456
    }
  ],
  "operatorId": 1,
  "operatorName": "admin"
}
```

**响应**：
```json
{
  "id": 1,
  "poNumber": "PO-20250117-001",
  "status": "COMPLETED",
  "actualEntryDate": "2025-01-17T14:30:00"
}
```

---

## 8. 数据库设计

### 8.1 核心表结构

#### 8.1.1 products（商品表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| spu_id | BIGINT | SPU ID（外键） |
| sku_name | VARCHAR(100) | SKU 名称 |
| specs | VARCHAR(500) | 规格描述 |
| barcode | VARCHAR(50) | 条形码（唯一索引） |
| name | VARCHAR(200) | 商品名称 |
| unit_price | DECIMAL(10,2) | 单价 |
| min_stock | INTEGER | 安全库存 |
| lead_time | INTEGER | 采购提前期 |
| pack_unit | VARCHAR(20) | 包装单位 |
| conversion_rate | INTEGER | 换算率 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### 8.1.2 inventory_batch（库存批次表）⭐

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| batch_code | VARCHAR(20) | 批次码（唯一索引） |
| purchase_order_item_id | BIGINT | 采购单明细 ID |
| product_id | BIGINT | 商品 ID |
| location_id | BIGINT | 库位 ID（可为空） |
| quantity | INTEGER | 当前数量 |
| initial_quantity | INTEGER | 初始数量 |
| expiry_date | DATE | 过期日期 |
| production_date | DATE | 生产日期 |
| external_batch_code | VARCHAR(50) | 外部批次码 |
| entry_date | TIMESTAMP | 入库时间（可为空） |
| active | BOOLEAN | 是否有效 |
| version | BIGINT | 乐观锁版本号 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### 8.1.3 purchase_order（采购单主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| po_number | VARCHAR(20) | 采购单号（唯一索引） |
| supplier | VARCHAR(200) | 供应商 |
| status | VARCHAR(30) | 状态（枚举） |
| total_quantity | INTEGER | 总数量 |
| total_cost | DECIMAL(10,2) | 总成本 |
| expected_date | DATE | 期望到货日期 |
| actual_entry_date | TIMESTAMP | 实际入库时间 |
| operator_id | BIGINT | 操作员 ID |
| operator_name | VARCHAR(100) | 操作员姓名 |
| audit_log | TEXT | 审计日志 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### 8.1.4 locations（库位表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| warehouse_code | VARCHAR(20) | 仓库编码 |
| zone | VARCHAR(20) | 库区（枚举） |
| shelf_code | VARCHAR(10) | 货架编码 |
| layer | INTEGER | 层号 |
| position_number | INTEGER | 库位号 |
| location_code | VARCHAR(50) | 完整库位码（唯一索引） |
| capacity | INTEGER | 容量 |
| enabled | BOOLEAN | 是否启用 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

---

## 9. 配置说明

### 9.1 application.yml 核心配置

```yaml
# 数据源配置
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: postgres
    password: your_password  # ⚠️ 生产环境使用环境变量

  # JPA 配置
  jpa:
    show-sql: true  # 开发环境：true，生产环境：false
    hibernate:
      ddl-auto: update  # 开发环境：update，生产环境：validate

# JWT 配置
jwt:
  secret: ${JWT_SECRET}  # ⚠️ 必须从环境变量读取
  expiration: 86400000  # 24 小时

# 批次管理配置
batch:
  hashids:
    salt: ${BATCH_SALT}  # ⚠️ 必须从环境变量读取
    min-length: 6
```

### 9.2 环境变量配置

**开发环境**：
```bash
# Linux/Mac
export JWT_SECRET="your-jwt-secret-key-min-256-bits"
export BATCH_SALT="your-batch-salt-min-32-characters"

# Windows
set JWT_SECRET=your-jwt-secret-key-min-256-bits
set BATCH_SALT=your-batch-salt-min-32-characters
```

**生产环境（Docker）**：
```bash
docker run -d \
  -e JWT_SECRET="your-jwt-secret-key" \
  -e BATCH_SALT="your-batch-salt" \
  -e SPRING_DATASOURCE_PASSWORD="your-db-password" \
  wms-system:latest
```

---

## 10. 开发规范

### 10.1 代码规范

#### 10.1.1 命名规范

- **类名**：大驼峰（PascalCase），如 `InventoryService`
- **方法名**：小驼峰（camelCase），如 `adjustStock()`
- **常量**：全大写下划线（UPPER_SNAKE_CASE），如 `STOCK_INSUFFICIENT`
- **变量**：小驼峰，如 `totalQuantity`

#### 10.1.2 注释规范

```java
/**
 * 库存调整服务
 *
 * 核心职责：
 * 1. 库存调整（入库/出库/盘点）
 * 2. 自动记录库存流水
 * 3. 并发控制（乐观锁 + 重试）
 *
 * @author WMS Team
 * @since 2025-01-11
 * @version 2.0
 */
public class InventoryService {
    // ...
}
```

#### 10.1.3 异常处理规范

```java
// ✅ 推荐：使用错误键
throw new BusinessException(
    ErrorKeys.STOCK_INSUFFICIENT,
    Map.of("currentStock", 50, "requestedQuantity", 100)
);

// ❌ 不推荐：硬编码错误消息
throw new RuntimeException("库存不足");
```

### 10.2 API 设计规范

#### 10.2.1 RESTful API 规范

| 操作 | HTTP 方法 | URL 示例 | 说明 |
|------|----------|---------|------|
| 查询列表 | GET | `/api/products` | 分页查询 |
| 查询详情 | GET | `/api/products/{id}` | 根据 ID 查询 |
| 创建资源 | POST | `/api/products` | 新增 |
| 更新资源 | PUT | `/api/products/{id}` | 全量更新 |
| 部分更新 | PATCH | `/api/products/{id}` | 部分字段更新 |
| 删除资源 | DELETE | `/api/products/{id}` | 删除 |

#### 10.2.2 HTTP 状态码规范

- `200 OK`：成功
- `201 Created`：创建成功
- `400 Bad Request`：参数错误、业务规则校验失败
- `401 Unauthorized`：未认证
- `403 Forbidden`：无权限
- `404 Not Found`：资源不存在
- `409 Conflict`：资源冲突（如唯一索引冲突）
- `500 Internal Server Error`：服务器内部错误

### 10.3 数据库规范

#### 10.3.1 表命名规范

- 使用复数形式（如 `products`，不是 `product`）
- 使用下划线分隔（如 `purchase_order_items`）
- 避免使用保留字

#### 10.3.2 索引规范

- 外键必须建索引
- 唯一约束字段建唯一索引
- 常用查询字段建普通索引
- 索引命名：`idx_<table>_<column>` 或 `uk_<table>_<column>`（唯一索引）

---

## 11. 部署指南

### 11.1 本地部署

#### 11.1.1 环境准备

```bash
# 1. 安装 JDK 17
java -version  # 验证版本

# 2. 安装 Maven
mvn -version

# 3. 安装 PostgreSQL 16
psql --version

# 4. 创建数据库
psql -U postgres
CREATE DATABASE wms_db;
```

#### 11.1.2 项目启动

```bash
# 1. 克隆代码
git clone https://github.com/Arnold-R-ops/BC-ERP-WMS.git
cd BC-ERP-WMS

# 2. 修改配置
vim src/main/resources/application.yml
# 修改数据库密码

# 3. 编译并启动
mvn clean package -DskipTests
java -jar target/wms-system-0.0.1-SNAPSHOT.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

### 11.2 Docker 部署

#### 11.2.1 构建镜像

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/wms-system-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# 构建镜像
docker build -t wms-system:latest .

# 运行容器
docker run -d \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/wms_db \
  -e SPRING_DATASOURCE_PASSWORD=your_password \
  -e JWT_SECRET=your_jwt_secret \
  -e BATCH_SALT=your_batch_salt \
  --name wms-system \
  wms-system:latest
```

### 11.3 生产环境优化

#### 11.3.1 JVM 参数优化

```bash
java -jar \
  -Xms1g -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.profiles.active=prod \
  app.jar
```

#### 11.3.2 数据库连接池优化

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 10
      maximum-pool-size: 50
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## 12. 常见问题

### 12.1 乐观锁冲突

**问题**：高并发下频繁出现 `OptimisticLockException`

**解决方案**：
1. 系统已配置自动重试（最多 3 次）
2. 如仍有冲突，考虑增加重试次数或延长延迟时间
3. 评估是否需要改用悲观锁（`SELECT ... FOR UPDATE`）

### 12.2 批次码生成失败

**问题**：`批次码生成失败：尝试次数过多`

**原因**：
- Hashids salt 配置错误
- 数据库中已存在大量批次码导致碰撞率高

**解决方案**：
1. 检查 `batch.hashids.salt` 配置
2. 增加 `batch.hashids.min-length`（如改为 8）
3. 检查批次表是否有大量冗余数据

### 12.3 JWT Token 过期

**问题**：前端频繁提示 Token 过期

**解决方案**：
1. 增加 `jwt.expiration` 配置（如改为 7 天）
2. 实现 Token 刷新机制（前端在过期前 1 小时刷新）

### 12.4 数据库连接池耗尽

**问题**：`Connection pool exhausted`

**解决方案**：
1. 增加连接池大小：`spring.datasource.hikari.maximum-pool-size`
2. 检查是否有未关闭的连接（使用 `@Transactional` 自动管理）
3. 优化慢查询，减少连接持有时间

---

## 附录

### A. 参考文档

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [Spring Data JPA 文档](https://spring.io/projects/spring-data-jpa)
- [PostgreSQL 官方文档](https://www.postgresql.org/docs/)
- [JWT 规范](https://jwt.io/)

### B. 更新日志

- **v3.3** (2026-01-17)
  - 实现 SPU-SKU 架构
  - 添加商品规格描述字段
  - 支持多单位换算和格式化

- **v3.0** (2025-01-13)
  - 实现单一数据源架构（InventoryBatch）
  - 三阶段采购流程
  - Hashids 批次码生成

- **v2.0** (2025-01-11)
  - 错误键系统
  - 乐观锁 + 自动重试
  - 审计字段（quantityBefore/After）

- **v1.0** (2025-01-09)
  - 基础架构搭建
  - JWT 认证
  - 库存管理核心功能

---

**文档维护**：本文档由 WMS Team 维护，如有问题请提交 Issue 或 Pull Request。

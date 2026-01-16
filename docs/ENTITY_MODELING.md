# WMS 核心实体建模说明文档

## 📊 实体关系图（ER图）

```
┌─────────────┐
│    User     │  解决："谁在操作"
│  (用户表)    │
└─────────────┘
      │
      │ 操作人
      ▼
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐      │
│  │ Product  │      │ Location │      │ Inventory│      │
│  │ (商品表)  │      │ (库位表)  │      │ (库存表)  │      │
│  └──────────┘      └──────────┘      └──────────┘      │
│       │                  │                  │           │
│       │                  │                  │           │
│       └──────────┬───────┴───────┬──────────┘           │
│                  │               │                      │
│                  ▼               ▼                      │
│           ┌──────────────────────────┐                 │
│           │   StockTransaction       │                 │
│           │   (库存流水表)            │                 │
│           └──────────────────────────┘                 │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 核心关系说明

| 实体 | 关联关系 | 说明 |
|------|---------|------|
| **Inventory ← Product** | @ManyToOne | 一个商品可以分布在多个库位 |
| **Inventory ← Location** | @ManyToOne | 一个库位可以存储多个商品 |
| **StockTransaction ← Product** | @ManyToOne | 一个商品可以有多次流水记录 |
| **StockTransaction ← Location** | @ManyToOne | 一个库位可以有多次流水记录 |

---

## 🗂️ 数据库表结构预览

### 1. users（用户表）

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 用户名（登录账号） |
| password | VARCHAR(255) | NOT NULL | 加密密码（BCrypt） |
| role | VARCHAR(20) | NOT NULL | 角色（ADMIN / STAFF） |
| display_name | VARCHAR(100) | | 显示名称 |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |
| remark | VARCHAR(500) | | 备注 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**索引：**
- `idx_username (username)` - UNIQUE

---

### 2. products（商品表）

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| barcode | VARCHAR(50) | NOT NULL, UNIQUE | 条形码（唯一标识） |
| name | VARCHAR(200) | NOT NULL | 商品名称 |
| specification | VARCHAR(100) | | 规格（500ml, 1kg等） |
| unit_price | DECIMAL(10,2) | NOT NULL | 单价 |
| min_stock | INTEGER | NOT NULL, DEFAULT 0 | 安全库存（预警阈值） |
| lead_time | INTEGER | NOT NULL, DEFAULT 7 | 采购提前期（天数） |
| description | VARCHAR(1000) | | 商品描述 |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |
| category | VARCHAR(100) | | 商品分类 |
| supplier | VARCHAR(200) | | 供应商 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**索引：**
- `idx_barcode (barcode)` - UNIQUE
- `idx_name (name)`

**核心字段说明：**
- `min_stock`：当实际库存 < 此值时触发预警
- `lead_time`：从下单到到货的天数，用于计算补货时间点

---

### 3. locations（库位表）

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| warehouse_code | VARCHAR(20) | NOT NULL | 仓库编码（WH01） |
| zone | VARCHAR(20) | NOT NULL | 区域（ZONE_A ~ ZONE_R） |
| shelf_number | VARCHAR(20) | NOT NULL | 货架号（A-01） |
| position_number | VARCHAR(10) | NOT NULL | 位号（001） |
| location_code | VARCHAR(100) | UNIQUE | 完整库位编码（自动生成） |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否可用 |
| remark | VARCHAR(500) | | 备注 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**索引：**
- `idx_warehouse_code (warehouse_code)`
- `idx_zone (zone)`
- `idx_shelf_position (shelf_number, position_number)`

**唯一约束：**
- `uk_location (warehouse_code, zone, shelf_number, position_number)`

**库位编码自动生成规则：**
```
location_code = {warehouseCode}-{zone}-{shelfNumber}-{positionNumber}
示例：WH01-ZONE_A-A-01-001
```

---

### 4. inventory（库存表）⭐核心表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| product_id | BIGINT | NOT NULL, FK → products | 商品ID |
| location_id | BIGINT | NOT NULL, FK → locations | 库位ID |
| quantity | INTEGER | NOT NULL, DEFAULT 0 | 当前库存数量 |
| version | BIGINT | NOT NULL, DEFAULT 0 | 乐观锁版本号 |
| remark | VARCHAR(500) | | 备注 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**索引：**
- `idx_product_id (product_id)`
- `idx_location_id (location_id)`

**唯一约束：**
- `uk_product_location (product_id, location_id)` - 一个库位只能存一个商品

**乐观锁机制：**
```sql
-- JPA 自动生成的更新 SQL
UPDATE inventory
SET quantity = ?, version = version + 1, updated_at = NOW()
WHERE id = ? AND version = ?;

-- 如果 version 不匹配，更新失败，抛出 OptimisticLockException
```

**业务方法：**
- `increaseQuantity(quantity)`: 入库，增加库存
- `decreaseQuantity(quantity)`: 出库，减少库存（会检查库存是否充足）
- `isBelowMinStock()`: 检查是否低于安全库存

---

### 5. stock_transactions（库存流水表）⭐数据分析核心

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| product_id | BIGINT | NOT NULL, FK → products | 商品ID |
| location_id | BIGINT | NOT NULL, FK → locations | 库位ID |
| transaction_type | VARCHAR(20) | NOT NULL | 变动类型（IN/OUT/ADJUST） |
| source_type | VARCHAR(50) | NOT NULL | 来源类型（采购/销售/调拨等） |
| quantity | INTEGER | NOT NULL | 变动数量（绝对值） |
| quantity_before | INTEGER | NOT NULL | 变动前库存 |
| quantity_after | INTEGER | NOT NULL | 变动后库存 |
| source_order_id | VARCHAR(50) | NOT NULL | 来源单据号 |
| product_name | VARCHAR(200) | NOT NULL | 商品名称（冗余） |
| product_barcode | VARCHAR(50) | NOT NULL | 商品条形码（冗余） |
| location_code | VARCHAR(100) | NOT NULL | 库位编码（冗余） |
| operator_id | BIGINT | | 操作人ID |
| operator_name | VARCHAR(100) | | 操作人姓名 |
| remark | VARCHAR(1000) | | 备注 |
| created_at | TIMESTAMP | NOT NULL | 创建时间（流水时间） |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**索引：**
- `idx_product_id (product_id)`
- `idx_location_id (location_id)`
- `idx_transaction_type (transaction_type)`
- `idx_source_type (source_type)`
- `idx_source_order_id (source_order_id)`
- `idx_created_at (created_at)` ⭐高频查询

**设计原则：**
- **只增不删**：流水记录永久保留
- **不可修改**：一旦生成，不允许更新
- **冗余存储**：防止关联数据被删除后无法查看

**数据分析应用：**
```sql
-- 1. 计算商品的日均出库量（最近30天）
SELECT product_id,
       AVG(quantity) as avg_daily_out
FROM stock_transactions
WHERE transaction_type = 'OUT'
  AND created_at >= NOW() - INTERVAL 30 DAY
GROUP BY product_id;

-- 2. 预测库存消耗（建议补货量）
-- 建议补货量 = 日均出库量 × 采购提前期 + 安全库存
```

---

## 🎯 核心业务场景与数据流

### 场景 1：采购入库

```
1. 用户（User）扫描商品条形码 → 识别 Product
2. 用户选择目标库位（Location）
3. 系统检查 Inventory 是否存在该 (Product + Location) 记录
   - 存在：quantity += 入库数量
   - 不存在：新增一条 Inventory 记录
4. 系统生成 StockTransaction 流水：
   - transactionType = IN
   - sourceType = PURCHASE_IN
   - sourceOrderId = 采购单号（PO202501090001）
   - quantityBefore = 旧库存
   - quantityAfter = 新库存
5. 提交事务，更新成功
```

---

### 场景 2：销售出库（并发场景）

**假设：** 两个用户同时对同一库位的同一商品进行出库

```
时间线 | 用户A | 用户B | 数据库状态
------|------|------|----------
T0    | 查询库存：quantity=100, version=1 | | quantity=100, version=1
T1    | | 查询库存：quantity=100, version=1 | quantity=100, version=1
T2    | 出库50件，提交更新 | | quantity=50, version=2 ✅
T3    | | 出库30件，提交更新 | ❌ OptimisticLockException
T4    | | 重新查询：quantity=50, version=2 |
T5    | | 出库30件，提交更新 | quantity=20, version=3 ✅
```

**关键点：**
- 用户B 的第一次提交失败（version 不匹配）
- 系统提示用户B："库存已变化，请刷新后重试"
- 用户B 重新查询最新库存后再次提交，成功

---

## 🔧 技术亮点总结

### 1. 审计功能（自动时间戳）

```java
@Entity
public class Product extends BaseEntity {
    // 继承 createdAt 和 updatedAt 字段
    // JPA 会自动管理，无需手动赋值
}
```

### 2. 乐观锁（防止并发冲突）

```java
@Entity
public class Inventory extends BaseEntity {
    @Version  // JPA 自动管理版本号
    private Long version;
}
```

### 3. 枚举类型（类型安全）

```java
@Enumerated(EnumType.STRING)  // 以字符串形式存储
private Role role;  // 数据库存储 "ADMIN" 或 "STAFF"
```

### 4. 级联查询优化（延迟加载）

```java
@ManyToOne(fetch = FetchType.LAZY)  // 延迟加载，提升性能
private Product product;
```

### 5. 业务逻辑封装（实体方法）

```java
// 入库方法（带校验）
public void increaseQuantity(Integer inboundQuantity) {
    if (inboundQuantity <= 0) {
        throw new IllegalArgumentException("入库数量必须大于 0");
    }
    this.quantity += inboundQuantity;
}
```

---

## 📈 ERP 扩展预留

| 功能模块 | 当前状态 | ERP 扩展方向 |
|---------|---------|-------------|
| 用户管理 | 单角色（ADMIN/STAFF） | 多角色 + 部门 + 权限细粒度控制 |
| 商品管理 | 基础字段 | 商品分类表、供应商表、品牌表 |
| 库位管理 | 基础字段 | 容量限制、温度控制、库位类型 |
| 库存管理 | 数量管理 | 批次号、生产日期、过期日期、锁定库存 |
| 流水管理 | 基础记录 | 审批流程、附件管理、操作人审计 |

---

## 🚀 下一步开发建议

### 第二阶段：Repository 层（数据访问层）

为每个实体创建 JPA Repository 接口，继承 `JpaRepository`：

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Spring Data JPA 自动实现 CRUD 方法
    Optional<Product> findByBarcode(String barcode);
    List<Product> findByNameContaining(String keyword);
}
```

### 第三阶段：Service 层（业务逻辑层）

实现核心业务逻辑：
- 入库服务（InboundService）
- 出库服务（OutboundService）
- 库存查询服务（InventoryService）
- 库存预警服务（StockAlertService）

### 第四阶段：Controller 层（RESTful API）

提供前端调用的 HTTP 接口：
- `POST /api/inbound` - 入库
- `POST /api/outbound` - 出库
- `GET /api/inventory/query` - 库存查询
- `GET /api/inventory/alerts` - 库存预警列表

---

## 📝 数据库初始化脚本（可选）

如果需要手动初始化数据，可以创建 `data.sql`：

```sql
-- 插入默认用户
INSERT INTO users (username, password, role, display_name, enabled, created_at, updated_at)
VALUES ('admin', '$2a$10$...', 'ADMIN', '系统管理员', true, NOW(), NOW());

-- 插入测试商品
INSERT INTO products (barcode, name, unit_price, min_stock, lead_time, enabled, created_at, updated_at)
VALUES ('4901234567890', '可口可乐 500ml', 3.50, 100, 3, true, NOW(), NOW());

-- 插入测试库位
INSERT INTO locations (warehouse_code, zone, shelf_number, position_number, location_code, enabled, created_at, updated_at)
VALUES ('WH01', 'ZONE_A', 'A-01', '001', 'WH01-ZONE_A-A-01-001', true, NOW(), NOW());
```

---

**文档版本：** v1.0
**创建日期：** 2025-01-09
**作者：** WMS Team
**技术栈：** Java 17 + Spring Boot 3.2.11 + PostgreSQL 16 + JPA (Hibernate)

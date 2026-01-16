# WMS Repository 层开发文档

## 📋 概述

Repository 层是数据访问层，负责与数据库交互。通过继承 `JpaRepository`，我们可以**免费获得常用的 CRUD 方法**，无需手写 SQL。

### 核心技术

| 技术 | 说明 | 优势 |
|------|------|------|
| **Spring Data JPA** | 数据访问框架 | 自动生成 SQL，减少 80% 的代码量 |
| **方法命名规范** | 根据方法名自动生成 SQL | 简单查询无需写 SQL |
| **JPQL** | JPA 查询语言 | 面向对象的查询，支持复杂逻辑 |
| **@Query 注解** | 自定义 JPQL 查询 | 灵活性高，支持聚合函数 |

---

## 🗂️ Repository 文件清单

```
src/main/java/com/wms/system/repository/
├── UserRepository.java                 # 用户数据访问接口
├── ProductRepository.java              # 商品数据访问接口（含低库存查询）
├── LocationRepository.java             # 库位数据访问接口（含空闲库位查询）
├── InventoryRepository.java            # 库存数据访问接口（含库存汇总）⭐核心
└── StockTransactionRepository.java     # 库存流水数据访问接口（含流水分析）⭐⭐⭐
```

---

## 📊 方法分类统计

| Repository | 自动 CRUD | 方法命名规范 | JPQL 自定义查询 | 总方法数 |
|-----------|-----------|------------|----------------|---------|
| **UserRepository** | 7个 | 6个 | 0个 | 13个 |
| **ProductRepository** | 7个 | 6个 | 4个 | 17个 |
| **LocationRepository** | 7个 | 7个 | 4个 | 18个 |
| **InventoryRepository** | 7个 | 5个 | 9个 | 21个 |
| **StockTransactionRepository** | 7个 | 4个 | 12个 | 23个 |

**总计：92 个方法，其中 35 个自动生成，29 个通过自定义 JPQL 实现**

---

## 🔍 Spring Data JPA 方法命名规范

### 自动生成的 CRUD 方法（继承自 JpaRepository）

```java
public interface UserRepository extends JpaRepository<User, Long> {
    // ✅ 以下方法自动生成，无需手写实现

    // 1. 保存或更新
    User save(User user);

    // 2. 根据ID查询
    Optional<User> findById(Long id);

    // 3. 查询所有
    List<User> findAll();

    // 4. 根据ID删除
    void deleteById(Long id);

    // 5. 统计总数
    long count();

    // 6. 检查是否存在
    boolean existsById(Long id);

    // 7. 批量保存
    List<User> saveAll(Iterable<User> users);
}
```

### 方法命名规范（自动生成 SQL）

| 关键字 | 示例方法名 | 生成的 SQL | 说明 |
|-------|----------|-----------|------|
| **findBy** | `findByUsername(String username)` | `SELECT * FROM users WHERE username = ?` | 根据字段查询 |
| **existsBy** | `existsByUsername(String username)` | `SELECT COUNT(*) > 0 FROM users WHERE username = ?` | 检查是否存在 |
| **countBy** | `countByRole(Role role)` | `SELECT COUNT(*) FROM users WHERE role = ?` | 统计数量 |
| **deleteBy** | `deleteByEnabled(Boolean enabled)` | `DELETE FROM users WHERE enabled = ?` | 根据条件删除 |
| **And** | `findByRoleAndEnabled(Role role, Boolean enabled)` | `WHERE role = ? AND enabled = ?` | 多条件查询 |
| **Or** | `findByUsernameOrDisplayName(String keyword)` | `WHERE username = ? OR display_name = ?` | 或条件查询 |
| **Containing** | `findByNameContaining(String keyword)` | `WHERE name LIKE %?%` | 模糊查询 |
| **GreaterThan** | `findByQuantityGreaterThan(Integer threshold)` | `WHERE quantity > ?` | 大于 |
| **LessThan** | `findByQuantityLessThan(Integer threshold)` | `WHERE quantity < ?` | 小于 |
| **Between** | `findByCreatedAtBetween(LocalDateTime start, LocalDateTime end)` | `WHERE created_at BETWEEN ? AND ?` | 范围查询 |
| **OrderBy** | `findByProduct_IdOrderByCreatedAtDesc(Long productId)` | `ORDER BY created_at DESC` | 排序 |

**示例：**
```java
// 方法名：findByUsernameContaining
// 自动生成 SQL：SELECT * FROM users WHERE username LIKE '%?%'
List<User> findByUsernameContaining(String keyword);
```

---

## 🎯 核心查询方法详解

### 1️⃣ ProductRepository - 低库存商品查询

#### 方法：`findLowStockProducts()`

**业务场景：** 查询所有需要补货的商品（总库存 < 安全库存）

**JPQL 查询：**
```java
@Query("SELECT p FROM Product p " +
       "WHERE p.id IN (" +
       "  SELECT i.product.id FROM Inventory i " +
       "  GROUP BY i.product.id " +
       "  HAVING SUM(i.quantity) < " +
       "    (SELECT p2.minStock FROM Product p2 WHERE p2.id = i.product.id)" +
       ")")
List<Product> findLowStockProducts();
```

**SQL 等价查询：**
```sql
SELECT p.*
FROM products p
WHERE p.id IN (
  SELECT i.product_id
  FROM inventory i
  GROUP BY i.product_id
  HAVING SUM(i.quantity) < (
    SELECT p2.min_stock
    FROM products p2
    WHERE p2.id = i.product_id
  )
);
```

**执行逻辑：**
1. 从 `inventory` 表按商品分组
2. 计算每个商品的总库存（`SUM(i.quantity)`）
3. 与该商品的安全库存（`p.minStock`）比较
4. 如果总库存 < 安全库存，返回该商品

**数据示例：**
```
商品A：安全库存 100，实际库存（库位1: 30 + 库位2: 40）= 70  → 需要预警 ✅
商品B：安全库存 100，实际库存（库位1: 150）= 150           → 正常 ❌
商品C：安全库存 50，实际库存（库位1: 20 + 库位2: 10）= 30   → 需要预警 ✅
```

---

### 2️⃣ InventoryRepository - 库存汇总查询

#### 方法：`sumTotalQuantityByProduct(Long productId)`

**业务场景：** 计算某商品在所有仓库的总库存

**JPQL 查询：**
```java
@Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.product.id = :productId")
Integer sumTotalQuantityByProduct(@Param("productId") Long productId);
```

**SQL 等价查询：**
```sql
SELECT SUM(quantity)
FROM inventory
WHERE product_id = ?;
```

**执行逻辑：**
1. 筛选指定商品的所有库存记录
2. 汇总所有库位的库存数量
3. 返回总库存（如果没有记录，返回 `null`）

**数据示例：**
```
商品：可口可乐 500ml（ID=1）

库存记录：
- 库位1（WH01-ZONE_A-A-01-001）：100 件
- 库位2（WH01-ZONE_A-A-01-002）：50 件
- 库位3（WH01-ZONE_B-B-05-010）：200 件

总库存：350 件
```

**使用场景：**
```java
// Service 层调用
Integer totalStock = inventoryRepository.sumTotalQuantityByProduct(1L);
if (totalStock == null || totalStock < product.getMinStock()) {
    // 触发库存预警
    sendStockAlert(product);
}
```

---

### 3️⃣ StockTransactionRepository - 出库历史查询（库存预测核心）

#### 方法：`findMovementHistory(Long productId, LocalDateTime startDate, LocalDateTime endDate)`

**业务场景：** 查询某商品在指定时间范围内的所有出库流水，用于计算日均出库量

**JPQL 查询：**
```java
@Query("SELECT t FROM StockTransaction t " +
       "WHERE t.product.id = :productId " +
       "AND t.transactionType = 'OUT' " +
       "AND t.createdAt BETWEEN :startDate AND :endDate " +
       "ORDER BY t.createdAt DESC")
List<StockTransaction> findMovementHistory(@Param("productId") Long productId,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);
```

**SQL 等价查询：**
```sql
SELECT *
FROM stock_transactions
WHERE product_id = ?
  AND transaction_type = 'OUT'
  AND created_at BETWEEN ? AND ?
ORDER BY created_at DESC;
```

**执行逻辑：**
1. 筛选指定商品的流水记录
2. 仅查询出库类型（`OUT`）
3. 限定时间范围（例如：最近 30 天）
4. 按时间倒序排序

**数据示例：**
```
商品：可口可乐 500ml（ID=1）
时间范围：2024-12-10 ~ 2025-01-09（30 天）

出库流水：
- 2025-01-09 15:30:00：出库 50 件（销售出库）
- 2025-01-08 10:15:00：出库 30 件（销售出库）
- 2025-01-07 14:20:00：出库 45 件（销售出库）
- ...（共 20 条记录）

总出库量：900 件
日均出库量：900 / 30 = 30 件/天
```

**库存预测计算：**
```java
// Service 层实现
public Integer calculateDailyAverage(Long productId, int days) {
    LocalDateTime endDate = LocalDateTime.now();
    LocalDateTime startDate = endDate.minusDays(days);

    // 查询出库流水
    List<StockTransaction> transactions =
        stockTransactionRepository.findMovementHistory(productId, startDate, endDate);

    // 计算总出库量
    int totalOutbound = transactions.stream()
        .mapToInt(StockTransaction::getQuantity)
        .sum();

    // 计算日均出库量
    return totalOutbound / days;
}

public Integer calculateReorderQuantity(Product product) {
    // 计算日均出库量（最近 30 天）
    int dailyAverage = calculateDailyAverage(product.getId(), 30);

    // 建议补货量 = (日均出库量 × 采购提前期) + 安全库存
    return (dailyAverage * product.getLeadTime()) + product.getMinStock();
}
```

---

#### 方法：`sumOutboundQuantity(Long productId, LocalDateTime startDate, LocalDateTime endDate)`

**业务场景：** 快速计算总出库量，无需查询完整记录

**JPQL 查询：**
```java
@Query("SELECT SUM(t.quantity) FROM StockTransaction t " +
       "WHERE t.product.id = :productId " +
       "AND t.transactionType = 'OUT' " +
       "AND t.createdAt BETWEEN :startDate AND :endDate")
Integer sumOutboundQuantity(@Param("productId") Long productId,
                              @Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate);
```

**性能优势：**
- `findMovementHistory()`: 返回完整记录（适合详细分析）
- `sumOutboundQuantity()`: 仅返回汇总数值（性能更高）

**使用对比：**
```java
// 方式1：查询完整记录后手动汇总（适合需要详细流水的场景）
List<StockTransaction> transactions = repository.findMovementHistory(...);
int totalOut = transactions.stream().mapToInt(StockTransaction::getQuantity).sum();

// 方式2：直接汇总（适合仅需总数的场景，性能更高）
Integer totalOut = repository.sumOutboundQuantity(...);
```

---

## 🧪 Repository 测试示例

### 单元测试（使用 H2 内存数据库）

```java
@SpringBootTest
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void testFindLowStockProducts() {
        // 1. 准备测试数据
        Product product = Product.builder()
            .barcode("TEST001")
            .name("测试商品")
            .unitPrice(new BigDecimal("10.00"))
            .minStock(100)  // 安全库存 100
            .leadTime(7)
            .build();
        productRepository.save(product);

        Location location = new Location();
        // ... 创建库位

        Inventory inventory = Inventory.builder()
            .product(product)
            .location(location)
            .quantity(50)  // 实际库存 50（低于安全库存 100）
            .build();
        inventoryRepository.save(inventory);

        // 2. 执行查询
        List<Product> lowStockProducts = productRepository.findLowStockProducts();

        // 3. 断言结果
        assertThat(lowStockProducts).hasSize(1);
        assertThat(lowStockProducts.get(0).getBarcode()).isEqualTo("TEST001");
    }
}
```

---

## 📈 Repository 性能优化建议

### 1. 避免 N+1 查询问题

**问题示例：**
```java
// ❌ 错误：会触发 N+1 查询
List<Inventory> inventories = inventoryRepository.findAll();
for (Inventory inventory : inventories) {
    System.out.println(inventory.getProduct().getName());  // 每次循环都查询一次 Product
}
```

**解决方案：使用 JOIN FETCH**
```java
@Query("SELECT i FROM Inventory i JOIN FETCH i.product JOIN FETCH i.location")
List<Inventory> findAllWithProductAndLocation();
```

### 2. 使用索引优化查询

```sql
-- 为高频查询字段创建索引
CREATE INDEX idx_product_barcode ON products(barcode);
CREATE INDEX idx_inventory_product_location ON inventory(product_id, location_id);
CREATE INDEX idx_transaction_created_at ON stock_transactions(created_at);
```

### 3. 使用分页查询

```java
// 分页查询库存流水（避免一次性加载大量数据）
Page<StockTransaction> page = stockTransactionRepository.findAll(
    PageRequest.of(0, 20, Sort.by("createdAt").descending())
);
```

---

## 🎯 后续开发计划

### ✅ 已完成：第二阶段 - Repository 层

### 🔜 下一步：第三阶段 - Service 层（业务逻辑层）

需要实现的核心服务：

1. **ProductService**
   - 商品CRUD
   - 低库存商品查询
   - 商品搜索

2. **InventoryService**
   - 库存查询（按商品、按库位）
   - 库存汇总
   - 库存预警

3. **StockTransactionService**
   - 入库业务（生成流水 + 更新库存）
   - 出库业务（生成流水 + 更新库存 + 乐观锁处理）
   - 流水查询

4. **StockPredictionService**（⭐核心功能）
   - 计算日均出库量
   - 预测未来库存消耗
   - 生成补货建议清单

---

**文档版本：** v1.0
**创建日期：** 2025-01-09
**作者：** WMS Team
**技术栈：** Spring Data JPA + JPQL + 方法命名规范

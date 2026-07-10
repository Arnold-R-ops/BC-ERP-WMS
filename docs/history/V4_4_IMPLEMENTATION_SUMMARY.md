# V4.4 架构加固实施总结

## 📋 任务概述

**目标**：为 WMS 系统注入"双十一级别高并发"和"分布式 API 持续集成"的底层防御机制。

**完成时间**：2026-03-15

---

## ✅ 已完成的工作

### 1️⃣ 防御一：乐观锁与防超卖机制 (Optimistic Locking)

#### 数据库层改造
- **迁移脚本**：`V4_4__Add_Optimistic_Lock_And_Idempotency.sql`
- **新增字段**：
  - `inventory_batch.version` (INTEGER, NOT NULL, DEFAULT 0)
  - `products.version` (INTEGER, NOT NULL, DEFAULT 0)

#### Entity 层改造
- **InventoryBatch.java** (第 63-82 行)
  - 新增 `@Version` 注解的 `version` 字段
  - 自动管理版本号，每次更新自动递增
  - 防止高并发场景下的库存超卖问题

- **Product.java** (第 63-88 行)
  - 新增 `@Version` 注解的 `version` 字段
  - 防止价格、库存阈值等字段的并发冲突

#### 工作原理
```java
// JPA 自动处理乐观锁
// 更新时：WHERE id = ? AND version = ?
// 如果 version 不匹配，抛出 OptimisticLockException
```

---

### 2️⃣ 防御二：物理级接口幂等性 (Database-level Idempotency)

#### 数据库层改造
- **唯一索引确认**（已存在，脚本中做防御性检查）：
  - `sales_orders.order_no` → `idx_sales_order_no` (UNIQUE)
  - `purchase_order.po_number` → `idx_po_number` (UNIQUE)
  - `inbound_orders.order_no` → `idx_inbound_order_no` (UNIQUE)

#### 异常处理层改造
- **ErrorKeys.java** (第 813-832 行)
  - 新增 `ORDER_NUMBER_DUPLICATE` 错误码
  - 用于标识订单号重复提交场景

- **GlobalExceptionHandler.java**
  - 新增 `DataIntegrityViolationException` 拦截器（第 303-380 行）
  - 检测唯一约束违反，提取订单类型和字段名
  - 返回 HTTP 409 Conflict 和友好提示："该订单号已存在，请勿重复提交"
  - 更新错误码映射，将 `ORDER_NUMBER_DUPLICATE` 映射到 409 状态码

#### 工作原理
```
Shopify 重发请求 → 数据库唯一索引拦截 → DataIntegrityViolationException
→ GlobalExceptionHandler 捕获 → 返回 409 Conflict + 友好提示
```

---

## 📁 修改的文件清单

### 新增文件
1. `src/main/resources/db/migration/V4_4__Add_Optimistic_Lock_And_Idempotency.sql`
2. `start-app.bat` (应用启动脚本)

### 修改文件
1. `src/main/java/com/wms/system/entity/InventoryBatch.java`
2. `src/main/java/com/wms/system/entity/Product.java`
3. `src/main/java/com/wms/system/exception/ErrorKeys.java`
4. `src/main/java/com/wms/system/controller/GlobalExceptionHandler.java`

---

## 🔍 验证步骤

### 步骤 1：启动应用并执行迁移
```bash
# 方式 1：使用 IDE (推荐)
# 在 IntelliJ IDEA 或 Eclipse 中直接运行 WmsSystemApplication

# 方式 2：使用 Maven
mvn spring-boot:run

# 方式 3：使用启动脚本
start-app.bat
```

### 步骤 2：验证数据库迁移
```sql
-- 连接到 PostgreSQL
psql -U postgres -d wms_db

-- 检查 version 字段是否存在
\d inventory_batch
\d products

-- 应该看到：
-- version | integer | not null | default 0

-- 检查唯一索引
\di idx_sales_order_no
\di idx_po_number
\di idx_inbound_order_no
```

### 步骤 3：测试乐观锁机制
```java
// 模拟并发更新库存
@Test
void testOptimisticLocking() {
    InventoryBatch batch = inventoryBatchRepository.findById(1L).get();

    // 线程 1：扣减库存
    batch.setQuantity(batch.getQuantity() - 10);
    inventoryBatchRepository.save(batch);  // version: 0 → 1

    // 线程 2：同时扣减库存（使用旧 version）
    InventoryBatch staleBatch = inventoryBatchRepository.findById(1L).get();
    staleBatch.setQuantity(staleBatch.getQuantity() - 5);

    // 应该抛出 OptimisticLockException
    assertThrows(OptimisticLockException.class, () -> {
        inventoryBatchRepository.save(staleBatch);
    });
}
```

### 步骤 4：测试幂等性机制
```bash
# 使用 curl 或 Postman 重复提交相同订单号
curl -X POST http://localhost:8080/api/sales/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "SO20260315001",
    "customerId": 1,
    "items": [...]
  }'

# 第一次：200 OK
# 第二次：409 Conflict
# {
#   "errorKey": "ORDER_NUMBER_DUPLICATE",
#   "params": {
#     "orderType": "SALES",
#     "fieldName": "order_no",
#     "message": "该订单号已存在，请勿重复提交"
#   },
#   "status": 409
# }
```

---

## 🎯 架构收益

### 防御一：乐观锁
- ✅ 防止库存超卖（双十一场景）
- ✅ 防止价格并发冲突
- ✅ 无需悲观锁，性能更优
- ✅ 自动重试机制（Service 层可配置）

### 防御二：幂等性
- ✅ 防止 Shopify 网络抖动重发
- ✅ 防止前端连击提交
- ✅ 数据库层面物理拦截（最可靠）
- ✅ 友好的错误提示（409 Conflict）

---

## 📝 后续建议

### 1. Service 层乐观锁重试
```java
@Transactional
@Retryable(
    value = OptimisticLockException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100)
)
public void deductStock(Long batchId, Integer quantity) {
    // 扣减库存逻辑
}
```

### 2. 监控与告警
- 监控 `OptimisticLockException` 频率
- 监控 `ORDER_NUMBER_DUPLICATE` 频率
- 设置告警阈值（如每分钟 > 10 次）

### 3. 压力测试
- 使用 JMeter 模拟 1000 并发扣减库存
- 验证乐观锁机制是否正常工作
- 验证幂等性机制是否正常拦截

---

## 🚀 启动验证

由于当前环境缺少 Maven 命令，请按以下方式启动应用：

1. **使用 IDE 启动**（推荐）
   - 在 IntelliJ IDEA 中打开项目
   - 运行 `WmsSystemApplication.main()`
   - 观察控制台日志，确认 Flyway 执行 V4_4 迁移

2. **检查日志关键信息**
   ```
   Flyway: Migrating schema "public" to version "4.4"
   Flyway: Successfully applied 1 migration to schema "public"
   Started WmsSystemApplication in X.XXX seconds
   ```

3. **验证端口监听**
   ```bash
   netstat -ano | grep ":8080"
   # 应该看到 LISTENING 状态
   ```

---

## ✅ 总结

V4.4 架构加固已完成底层代码改造，包括：
- ✅ 数据库迁移脚本（乐观锁字段 + 唯一索引）
- ✅ Entity 层乐观锁注解
- ✅ 全局异常处理器（幂等性拦截）
- ✅ 错误码定义

**下一步**：启动应用，Flyway 将自动执行 V4_4 迁移脚本，系统即可获得高并发防御能力。

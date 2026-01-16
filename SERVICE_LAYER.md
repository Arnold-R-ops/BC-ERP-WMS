# Service 层架构文档

## 📋 目录
- [概述](#概述)
- [核心服务](#核心服务)
  - [InventoryService（库存管理服务）](#inventoryservice库存管理服务)
  - [StockPredictionService（库存预测服务）](#stockpredictionservice库存预测服务)
- [异常处理体系](#异常处理体系)
- [事务管理](#事务管理)
- [重试机制](#重试机制)
- [使用示例](#使用示例)
- [最佳实践](#最佳实践)

---

## 概述

Service 层是业务逻辑的核心层，负责：
- **业务规则封装**：将复杂的业务逻辑封装成方法
- **事务管理**：确保数据一致性（通过 `@Transactional`）
- **异常处理**：将底层异常转换为业务异常
- **数据编排**：调用多个 Repository 完成复杂业务流程
- **日志记录**：记录关键业务操作

### Service 层架构图

```
┌────────────────────────────────────────────────────────────┐
│                    Controller 层                            │
│         (接收请求、参数校验、返回响应)                        │
└──────────────────────┬─────────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────┐
│                     Service 层                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   InventoryService (库存管理)                         │  │
│  │   - adjustStock() : 库存调整                          │  │
│  │   - getTotalStock() : 查询总库存                      │  │
│  │   - getLowStockInventories() : 低库存预警             │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   StockPredictionService (库存预测)                   │  │
│  │   - getReorderSuggestion() : 单商品补货建议           │  │
│  │   - getAllReorderSuggestions() : 批量补货建议         │  │
│  │   - getHighPriorityReorderSuggestions() : 紧急补货    │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────┬─────────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────────┐
│                   Repository 层                             │
│         (数据访问、JPQL 查询、JPA 操作)                      │
└────────────────────────────────────────────────────────────┘
```

---

## 核心服务

### InventoryService（库存管理服务）

**文件路径**：`src/main/java/com/wms/system/service/InventoryService.java`

**职责**：
- 处理入库、出库、调整等核心库存操作
- 自动生成库存流水记录（StockTransaction）
- 处理并发冲突（乐观锁 + 重试机制）
- 处理库存不足异常

#### 核心方法：adjustStock

```java
@Transactional(rollbackFor = Exception.class)
@Retryable(
    retryFor = {OptimisticLockException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
public StockTransaction adjustStock(StockAdjustmentRequest request)
```

**业务流程**：

```
┌─────────────────────────────────────────────────────────────┐
│  1. 校验：检查商品和库位是否存在                              │
│     └─ 不存在 → 抛出 ResourceNotFoundException               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 查询或创建库存记录                                        │
│     └─ 如果是新库位，创建初始库存为 0 的记录                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  3. 更新库存数量                                              │
│     ├─ IN (入库) → quantity += request.quantity              │
│     ├─ OUT (出库) → quantity -= request.quantity             │
│     │   └─ 库存不足 → 抛出 InsufficientStockException         │
│     └─ ADJUST (调整) → quantity += request.quantity          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 保存库存记录（可能触发 OptimisticLockException）          │
│     └─ 乐观锁冲突 → 自动重试（最多3次）                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 生成并保存流水记录（StockTransaction）                    │
│     └─ 记录变动前后的库存数量、操作人、来源单据               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  6. 提交事务，返回流水记录                                    │
└─────────────────────────────────────────────────────────────┘
```

**关键技术特性**：

1. **事务管理**：
   ```java
   @Transactional(rollbackFor = Exception.class)
   ```
   - 确保库存更新和流水生成同时成功或失败
   - 任何异常都会触发回滚

2. **乐观锁并发控制**：
   ```java
   @Version
   private Long version;  // Inventory 实体中
   ```
   - 防止并发修改冲突
   - 当两个用户同时修改同一库存时，后提交的操作会失败

3. **自动重试机制**：
   ```java
   @Retryable(
       retryFor = {OptimisticLockException.class},
       maxAttempts = 3,
       backoff = @Backoff(delay = 1000)
   )
   ```
   - 乐观锁冲突时自动重试
   - 最多重试 3 次，每次延迟 1 秒

4. **异常处理**：
   - `ResourceNotFoundException`：商品或库位不存在
   - `InsufficientStockException`：出库时库存不足
   - `StockConcurrencyException`：乐观锁冲突（重试3次后仍失败）

#### 其他方法

```java
// 查询商品总库存（所有库位汇总）
@Transactional(readOnly = true)
public Integer getTotalStock(Long productId)

// 查询商品的库存分布（在哪些库位有库存）
@Transactional(readOnly = true)
public List<Inventory> getStockDistribution(Long productId)

// 查询库位的库存记录（该库位存放了哪些商品）
@Transactional(readOnly = true)
public List<Inventory> getStockByLocation(Long locationId)

// 查询低库存记录（用于库存预警）
@Transactional(readOnly = true)
public List<Inventory> getLowStockInventories()
```

---

### StockPredictionService（库存预测服务）

**文件路径**：`src/main/java/com/wms/system/service/StockPredictionService.java`

**职责**：
- 分析历史出库数据，计算日均出库量
- 根据采购提前期和安全库存生成补货建议
- 预测库存耗尽时间
- 评估补货紧急程度

#### 核心方法：getReorderSuggestion

```java
@Transactional(readOnly = true)
public ReorderSuggestion getReorderSuggestion(Long productId, Integer calculationPeriod)
```

**预测算法**：

```
┌─────────────────────────────────────────────────────────────┐
│  输入参数                                                     │
│  - productId: 商品ID                                         │
│  - calculationPeriod: 计算周期（天数，例如 30）              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  步骤1：收集数据                                              │
│  - 商品信息：minStock, leadTime, unitPrice, supplier         │
│  - 当前总库存：SUM(所有库位的库存)                            │
│  - 历史流水：最近 N 天的出库记录                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  步骤2：计算日均出库量                                        │
│                                                               │
│  日均出库量 = 最近N天的总出库量 / N天                         │
│                                                               │
│  示例：最近30天出库 300 件 → 日均出库 10 件/天               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  步骤3：计算建议补货量                                        │
│                                                               │
│  建议补货量 = (日均出库量 × 采购提前期) + 安全库存            │
│                                                               │
│  示例：                                                       │
│  - 日均出库：10 件/天                                         │
│  - 采购提前期：7 天                                           │
│  - 安全库存：20 件                                            │
│  → 建议补货量 = (10 × 7) + 20 = 90 件                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  步骤4：预测耗尽时间                                          │
│                                                               │
│  预计耗尽天数 = 当前库存 / 日均出库量                         │
│                                                               │
│  示例：当前库存 50 件，日均出库 10 件/天                      │
│  → 预计 5 天后耗尽                                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  步骤5：判断紧急程度                                          │
│                                                               │
│  ┌─ CRITICAL (极度紧急)                                      │
│  │  └─ 当前库存 < 安全库存                                   │
│  │                                                            │
│  ┌─ HIGH (高度紧急)                                          │
│  │  └─ 预计耗尽天数 < 采购提前期                             │
│  │                                                            │
│  ┌─ MEDIUM (中度紧急)                                        │
│  │  └─ 预计耗尽天数 ≈ 采购提前期 (±3天)                      │
│  │                                                            │
│  └─ LOW (低度紧急)                                           │
│     └─ 库存充足                                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  步骤6：计算补货金额                                          │
│                                                               │
│  建议补货金额 = 建议补货量 × 单价                             │
│                                                               │
│  示例：建议补货 90 件，单价 ¥15                               │
│  → 建议补货金额 = ¥1,350                                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  输出：ReorderSuggestion 对象                                │
│  - 包含所有计算结果                                           │
│  - 可直接用于生成采购订单                                     │
└─────────────────────────────────────────────────────────────┘
```

#### 批量补货建议

```java
@Transactional(readOnly = true)
public List<ReorderSuggestion> getAllReorderSuggestions(Integer calculationPeriod)
```

**业务流程**：
1. 调用 `ProductRepository.findLowStockProducts()` 查询所有低库存商品
2. 为每个商品调用 `getReorderSuggestion()` 生成建议
3. 过滤掉不需要补货的商品（`needsReorder() == false`）
4. 按紧急程度和预计耗尽天数排序
5. 返回排序后的补货建议列表

**排序规则**：
```
第一优先级：紧急程度 (priority 数字越小，优先级越高)
  CRITICAL (1) > HIGH (2) > MEDIUM (3) > LOW (4)

第二优先级：预计耗尽天数 (升序)
  3天 > 5天 > 7天 > 10天
```

#### 其他方法

```java
// 获取高优先级补货建议（仅 CRITICAL 和 HIGH 级别）
public List<ReorderSuggestion> getHighPriorityReorderSuggestions(Integer calculationPeriod)

// 计算补货建议的总金额（用于预算规划）
public BigDecimal calculateTotalReorderCost(List<ReorderSuggestion> suggestions)

// 获取商品的历史出库趋势（用于图表展示）
public List<StockTransaction> getOutboundTrend(Long productId, Integer days)
```

---

## 异常处理体系

### 异常继承结构

```
RuntimeException
    │
    ├── ResourceNotFoundException
    │   └─ 场景：查询的实体不存在（商品、库位、库存）
    │   └─ HTTP 状态码：404 Not Found
    │
    ├── InsufficientStockException
    │   └─ 场景：出库时库存不足
    │   └─ HTTP 状态码：400 Bad Request
    │
    └── StockConcurrencyException
        └─ 场景：乐观锁冲突（重试3次后仍失败）
        └─ HTTP 状态码：409 Conflict
```

### 异常处理示例

#### 1. ResourceNotFoundException

**抛出场景**：
```java
Product product = productRepository.findById(productId)
    .orElseThrow(() -> ResourceNotFoundException.ofId("Product", productId));
```

**异常信息**：
```
未找到资源！类型：Product，标识：123
```

**前端处理建议**：
```javascript
if (error.status === 404) {
    showMessage("未找到指定的商品，请检查商品ID是否正确");
}
```

#### 2. InsufficientStockException

**抛出场景**：
```java
// 出库时库存不足
throw InsufficientStockException.of(
    productId,        // 商品ID
    locationId,       // 库位ID
    currentStock,     // 当前库存：50
    requestedQuantity // 请求出库：100
);
```

**异常信息**：
```
库存不足！商品ID：123，库位ID：456，当前库存：50，请求出库：100，缺少：50
```

**前端处理建议**：
```javascript
if (error.code === "INSUFFICIENT_STOCK") {
    showMessage(`库存不足！当前库存：${error.currentStock}，缺少：${error.shortage}`);
    // 提示用户选择其他库位或减少出库数量
}
```

#### 3. StockConcurrencyException

**抛出场景**：
```java
// 乐观锁冲突（重试3次后仍失败）
catch (OptimisticLockException e) {
    throw StockConcurrencyException.from(
        productId,
        locationId,
        operationType,
        e
    );
}
```

**异常信息**：
```
库存并发冲突！商品ID：123，库位ID：456，操作类型：OUT。
其他用户已修改此库存，请刷新后重试。
```

**前端处理建议**：
```javascript
if (error.status === 409) {
    showMessage("库存已被他人修改，请刷新后重试");
    // 自动刷新页面或重新加载数据
    location.reload();
}
```

---

## 事务管理

### @Transactional 注解详解

Spring 的 `@Transactional` 注解用于声明式事务管理。

#### 基本用法

```java
@Transactional(rollbackFor = Exception.class)
public StockTransaction adjustStock(StockAdjustmentRequest request) {
    // 1. 更新库存（SQL UPDATE）
    Inventory savedInventory = inventoryRepository.save(inventory);

    // 2. 生成流水（SQL INSERT）
    StockTransaction transaction = createStockTransaction(...);
    StockTransaction savedTransaction = stockTransactionRepository.save(transaction);

    // 如果任何一步失败，整个事务回滚
    return savedTransaction;
}
```

#### 关键参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `rollbackFor` | 哪些异常触发回滚 | `rollbackFor = Exception.class` |
| `readOnly` | 只读事务（优化性能） | `readOnly = true` |
| `propagation` | 事务传播行为 | `propagation = Propagation.REQUIRED` |
| `isolation` | 事务隔离级别 | `isolation = Isolation.READ_COMMITTED` |

#### 事务回滚示例

```java
@Transactional(rollbackFor = Exception.class)
public void example() {
    // 步骤1：更新库存（成功）
    inventory.setQuantity(100);
    inventoryRepository.save(inventory);

    // 步骤2：生成流水（失败，抛出异常）
    throw new RuntimeException("模拟异常");

    // 结果：步骤1 和步骤2 都回滚，库存不会被更新
}
```

#### 只读事务优化

对于不修改数据的查询操作，使用 `readOnly = true` 可以提升性能：

```java
@Transactional(readOnly = true)
public List<ReorderSuggestion> getAllReorderSuggestions(Integer calculationPeriod) {
    // 只读操作，Hibernate 不会进行脏检查（Dirty Checking）
    // 提升性能，减少内存消耗
    return ...;
}
```

---

## 重试机制

### @Retryable 注解详解

Spring Retry 的 `@Retryable` 注解用于自动重试失败的操作。

#### 基本配置

```java
@Retryable(
    retryFor = {OptimisticLockException.class},  // 只重试乐观锁异常
    maxAttempts = 3,                              // 最多重试3次
    backoff = @Backoff(delay = 1000)              // 每次延迟1秒
)
public StockTransaction adjustStock(StockAdjustmentRequest request) {
    // 如果抛出 OptimisticLockException，自动重试
    // 重试时会重新执行整个方法
}
```

#### 启用重试功能

在主类或配置类上添加 `@EnableRetry`：

```java
@SpringBootApplication
@EnableRetry  // 启用重试功能
public class WmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
```

#### 重试流程图

```
第1次执行
    │
    ├─ 成功 → 返回结果 ✓
    │
    └─ OptimisticLockException
         │
         ├─ 延迟 1 秒
         │
         ▼
      第2次执行
         │
         ├─ 成功 → 返回结果 ✓
         │
         └─ OptimisticLockException
              │
              ├─ 延迟 1 秒
              │
              ▼
           第3次执行
              │
              ├─ 成功 → 返回结果 ✓
              │
              └─ OptimisticLockException
                   │
                   ▼
                抛出异常 ✗
                (重试3次后仍失败)
```

#### 为什么需要重试？

**场景**：两个用户同时修改同一库存

```
时间线：
T1: 用户A 读取库存（quantity=100, version=1）
T2: 用户B 读取库存（quantity=100, version=1）
T3: 用户A 出库 10 件，保存成功（quantity=90, version=2）
T4: 用户B 出库 20 件，保存失败（version=1 不匹配，数据库 version=2）
    └─ 抛出 OptimisticLockException
T5: Spring Retry 自动重试
    └─ 重新读取库存（quantity=90, version=2）
    └─ 出库 20 件（quantity=70, version=3）
    └─ 保存成功 ✓
```

---

## 使用示例

### 示例1：采购入库

```java
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 采购入库
     */
    @PostMapping("/purchase-inbound")
    public ResponseEntity<StockTransaction> purchaseInbound(
        @RequestBody @Valid PurchaseInboundRequest request
    ) {
        StockAdjustmentRequest adjustRequest = StockAdjustmentRequest.builder()
            .productId(request.getProductId())
            .locationId(request.getLocationId())
            .transactionType(TransactionType.IN)
            .sourceType(SourceType.PURCHASE_IN)
            .quantity(request.getQuantity())
            .sourceOrderId(request.getPurchaseOrderId())
            .operatorId(request.getOperatorId())
            .operatorName(request.getOperatorName())
            .remark("采购入库")
            .build();

        StockTransaction transaction = inventoryService.adjustStock(adjustRequest);
        return ResponseEntity.ok(transaction);
    }
}
```

### 示例2：销售出库

```java
/**
 * 销售出库
 */
@PostMapping("/sale-outbound")
public ResponseEntity<?> saleOutbound(@RequestBody @Valid SaleOutboundRequest request) {
    try {
        StockAdjustmentRequest adjustRequest = StockAdjustmentRequest.builder()
            .productId(request.getProductId())
            .locationId(request.getLocationId())
            .transactionType(TransactionType.OUT)
            .sourceType(SourceType.SALE_OUT)
            .quantity(request.getQuantity())
            .sourceOrderId(request.getSaleOrderId())
            .operatorId(request.getOperatorId())
            .operatorName(request.getOperatorName())
            .remark("销售出库")
            .build();

        StockTransaction transaction = inventoryService.adjustStock(adjustRequest);
        return ResponseEntity.ok(transaction);

    } catch (InsufficientStockException e) {
        // 库存不足
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "error", "INSUFFICIENT_STOCK",
                "message", e.getMessage(),
                "currentStock", e.getCurrentStock(),
                "shortage", e.getShortage()
            ));

    } catch (StockConcurrencyException e) {
        // 并发冲突
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of(
                "error", "CONCURRENCY_CONFLICT",
                "message", "库存已被他人修改，请刷新后重试"
            ));
    }
}
```

### 示例3：查询补货建议

```java
@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final StockPredictionService predictionService;

    /**
     * 获取单个商品的补货建议
     */
    @GetMapping("/reorder-suggestion/{productId}")
    public ResponseEntity<ReorderSuggestion> getReorderSuggestion(
        @PathVariable Long productId,
        @RequestParam(defaultValue = "30") Integer days
    ) {
        ReorderSuggestion suggestion = predictionService.getReorderSuggestion(productId, days);
        return ResponseEntity.ok(suggestion);
    }

    /**
     * 获取所有补货建议
     */
    @GetMapping("/reorder-suggestions")
    public ResponseEntity<ReorderSuggestionsResponse> getAllReorderSuggestions(
        @RequestParam(defaultValue = "30") Integer days
    ) {
        List<ReorderSuggestion> suggestions = predictionService.getAllReorderSuggestions(days);
        BigDecimal totalCost = predictionService.calculateTotalReorderCost(suggestions);

        return ResponseEntity.ok(ReorderSuggestionsResponse.builder()
            .suggestions(suggestions)
            .totalCount(suggestions.size())
            .totalCost(totalCost)
            .build());
    }

    /**
     * 获取高优先级补货建议（仅 CRITICAL 和 HIGH）
     */
    @GetMapping("/reorder-suggestions/urgent")
    public ResponseEntity<List<ReorderSuggestion>> getUrgentReorderSuggestions(
        @RequestParam(defaultValue = "30") Integer days
    ) {
        List<ReorderSuggestion> urgentSuggestions =
            predictionService.getHighPriorityReorderSuggestions(days);
        return ResponseEntity.ok(urgentSuggestions);
    }
}
```

### 示例4：库存预警定时任务

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class StockAlertTask {

    private final StockPredictionService predictionService;
    private final EmailService emailService;

    /**
     * 每天早上 9 点执行库存预警
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkLowStock() {
        log.info("开始执行库存预警检查...");

        // 获取高优先级补货建议
        List<ReorderSuggestion> urgentSuggestions =
            predictionService.getHighPriorityReorderSuggestions(30);

        if (!urgentSuggestions.isEmpty()) {
            log.warn("发现 {} 个紧急补货商品", urgentSuggestions.size());

            // 发送邮件通知采购部门
            emailService.sendStockAlert(urgentSuggestions);
        } else {
            log.info("库存充足，无需补货");
        }
    }
}
```

---

## 最佳实践

### 1. Service 方法命名规范

| 操作类型 | 命名规范 | 示例 |
|---------|---------|------|
| 查询单个 | `get` + 名词 | `getReorderSuggestion()` |
| 查询列表 | `getAll` / `list` + 名词 | `getAllReorderSuggestions()` |
| 创建 | `create` + 名词 | `createStockTransaction()` |
| 更新 | `update` + 名词 | `updateInventory()` |
| 删除 | `delete` + 名词 | `deleteProduct()` |
| 业务操作 | 动词 + 名词 | `adjustStock()`, `calculateDailyAverage()` |

### 2. 日志记录规范

```java
// 方法开始
log.info("开始库存调整：商品ID={}, 库位ID={}, 类型={}, 数量={}",
    request.getProductId(), request.getLocationId(),
    request.getTransactionType(), request.getQuantity());

// 关键步骤
log.debug("查询到商品：{}", product.getName());

// 方法结束
log.info("库存调整完成：流水ID={}, 商品={}, 类型={}",
    savedTransaction.getId(), product.getName(), request.getTransactionType());

// 错误日志
log.error("库存不足：商品ID={}, 当前库存={}, 请求数量={}",
    productId, currentStock, requestedQuantity);
```

### 3. 异常处理规范

```java
// ✅ 正确：抛出自定义业务异常
if (inventory.getQuantity() < quantity) {
    throw InsufficientStockException.of(productId, locationId, currentStock, quantity);
}

// ❌ 错误：抛出通用异常
if (inventory.getQuantity() < quantity) {
    throw new RuntimeException("库存不足");
}
```

### 4. 事务边界划分

```java
// ✅ 正确：一个事务包含完整的业务逻辑
@Transactional
public StockTransaction adjustStock(StockAdjustmentRequest request) {
    // 1. 更新库存
    // 2. 生成流水
    // 两个操作同时成功或失败
}

// ❌ 错误：事务边界过大，包含外部调用
@Transactional
public void processOrder(Order order) {
    adjustStock(...);           // 数据库操作
    sendEmailNotification(...); // 外部调用（可能很慢）
    callThirdPartyApi(...);     // 外部调用（可能失败）
}
```

### 5. 只读事务优化

```java
// ✅ 正确：查询操作使用 readOnly = true
@Transactional(readOnly = true)
public List<ReorderSuggestion> getAllReorderSuggestions(Integer days) {
    // 只读操作，提升性能
}

// ❌ 错误：查询操作未使用 readOnly
@Transactional  // 默认 readOnly = false
public List<ReorderSuggestion> getAllReorderSuggestions(Integer days) {
    // 性能较低
}
```

### 6. 重试机制使用建议

```java
// ✅ 正确：只重试可恢复的异常
@Retryable(
    retryFor = {OptimisticLockException.class},  // 乐观锁冲突可以重试
    maxAttempts = 3
)

// ❌ 错误：重试不可恢复的异常
@Retryable(
    retryFor = {ResourceNotFoundException.class},  // 资源不存在，重试无意义
    maxAttempts = 3
)
```

### 7. DTO 使用规范

```java
// ✅ 正确：使用 DTO 传递参数
public StockTransaction adjustStock(StockAdjustmentRequest request) {
    // 参数封装在 DTO 中，清晰易维护
}

// ❌ 错误：使用多个参数
public StockTransaction adjustStock(
    Long productId,
    Long locationId,
    TransactionType type,
    SourceType sourceType,
    Integer quantity,
    String sourceOrderId,
    Long operatorId,
    String operatorName,
    String remark
) {
    // 参数过多，难以维护
}
```

---

## 总结

Service 层是 WMS 系统的核心业务层，主要特性包括：

1. **InventoryService**：处理入库、出库、调整等核心库存操作
   - 使用 `@Transactional` 确保数据一致性
   - 使用 `@Retryable` 处理乐观锁冲突
   - 自动生成库存流水记录

2. **StockPredictionService**：基于历史数据生成智能补货建议
   - 计算日均出库量
   - 预测库存耗尽时间
   - 评估补货紧急程度

3. **异常处理体系**：
   - `ResourceNotFoundException`：资源不存在
   - `InsufficientStockException`：库存不足
   - `StockConcurrencyException`：并发冲突

4. **技术特性**：
   - 事务管理（`@Transactional`）
   - 乐观锁并发控制（`@Version`）
   - 自动重试机制（`@Retryable`）
   - 只读事务优化（`readOnly = true`）

---

## 下一步计划

1. **Controller 层开发**：
   - 创建 RESTful API 接口
   - 参数校验（`@Valid`）
   - 统一异常处理（`@ControllerAdvice`）
   - API 文档（Swagger / OpenAPI）

2. **单元测试**：
   - Service 层单元测试（Mockito）
   - Repository 层集成测试（`@DataJpaTest`）
   - Controller 层集成测试（MockMvc）

3. **性能优化**：
   - 批量操作优化
   - 缓存策略（Redis）
   - 数据库索引优化

4. **监控和日志**：
   - 接口性能监控（Spring Actuator）
   - 业务日志分析
   - 异常告警

---

**文档版本**：v1.0
**最后更新**：2025-01-10
**作者**：WMS Team

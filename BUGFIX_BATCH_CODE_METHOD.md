# 批次码查询方法重复定义修复

## 问题描述

编译错误：`java: 已在接口 com.wms.system.repository.InventoryBatchRepository中定义了方法 findByBatchCode(java.lang.String)`

**根本原因**：
- InventoryBatchRepository 中存在两个同名方法 `findByBatchCode(String batchCode)`
- 旧版本（V3.0）：返回 `Optional<InventoryBatch>`（单库位模式）
- 新版本（V3.3）：返回 `List<InventoryBatch>`（多库位模式）
- Java 不允许仅通过返回类型区分重载方法

---

## 修复方案

### 1. InventoryBatchRepository.java

**删除旧版本方法定义**（原 line 41 附近）：
```java
// ❌ 已删除
// Optional<InventoryBatch> findByBatchCode(String batchCode);
```

**保留 V3.3 多库位版本**（line 210）：
```java
// ✅ 保留
/**
 * ⭐ V3.3: Find all batches by batch code (across multiple locations)
 *
 * 查询同一批次在不同库位的所有记录
 *
 * @param batchCode Batch code
 * @return List of batches across all locations
 * @since V3.3
 */
List<InventoryBatch> findByBatchCode(String batchCode);
```

---

### 2. InventoryBatchService.java

**更新 findByBatchCode() 方法**（line 351-363）：

**修复前**：
```java
public InventoryBatch findByBatchCode(String batchCode) {
    return inventoryBatchRepository.findByBatchCode(batchCode)
        .orElseThrow(() -> new BusinessException(
            ErrorKeys.BATCH_NOT_FOUND,
            Map.of("batchCode", batchCode)
        ));
}
```

**修复后**：
```java
@Transactional(readOnly = true)
public InventoryBatch findByBatchCode(String batchCode) {
    List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCode(batchCode);

    if (batches.isEmpty()) {
        throw new BusinessException(
            ErrorKeys.BATCH_NOT_FOUND,
            Map.of("batchCode", batchCode)
        );
    }

    // V3.3: Return first batch if multiple locations exist
    // Warning: Consider using findByBatchCodeAndLocationCode() for specific location
    return batches.get(0);
}
```

**关键变更**：
- 处理 `List<InventoryBatch>` 返回类型
- 使用 `.isEmpty()` 检查而非 `.orElseThrow()`
- 返回列表的第一个元素（多库位场景下返回首个匹配批次）
- 添加注释提示：如需查询特定库位，应使用 `findByBatchCodeAndLocationCode()`

---

### 3. PurchaseOrderService.java

**更新 confirmReceipt() 方法**（line 368-377）：

**修复前**：
```java
InventoryBatch batch = inventoryBatchRepository.findByBatchCode(receipt.getBatchCode())
    .orElseThrow(() -> new BusinessException(
        ErrorKeys.BATCH_NOT_FOUND,
        Map.of("batchCode", receipt.getBatchCode())
    ));
```

**修复后**：
```java
// Query batch by batch code (V3.3: returns List, get first one)
List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCode(receipt.getBatchCode());

if (batches.isEmpty()) {
    throw new BusinessException(
        ErrorKeys.BATCH_NOT_FOUND,
        Map.of("batchCode", receipt.getBatchCode())
    );
}

InventoryBatch batch = batches.get(0); // Get first batch
```

**关键变更**：
- 处理 `List<InventoryBatch>` 返回类型
- 使用 `.isEmpty()` 检查
- 获取列表的第一个元素

---

## V3.3 多库位批次管理设计说明

### 为什么返回 List？

**业务场景**：
- V3.3 支持**同一批次码在不同库位存储**
- 例如：批次码 `BC12345` 可以同时存在于 `A01` 和 `B03` 两个库位
- 数据库中会有多条记录，每条记录对应一个库位

**数据示例**：
```sql
id | batch_code | location_code | quantity | product_id
---|------------|---------------|----------|----------
1  | BC12345    | A01           | 50       | 101
2  | BC12345    | B03           | 30       | 101
```

### 推荐使用方式

#### 场景 1：查询特定库位的批次（推荐）
```java
Optional<InventoryBatch> batch = inventoryBatchRepository
    .findByBatchCodeAndLocationCode("BC12345", "A01");
```

#### 场景 2：查询批次总库存（跨库位汇总）
```java
List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCode("BC12345");
int totalQuantity = batches.stream()
    .mapToInt(InventoryBatch::getQuantity)
    .sum();
```

#### 场景 3：批次追溯（查看批次分布）
```java
List<InventoryBatch> batches = inventoryBatchRepository.findByBatchCode("BC12345");
batches.forEach(b -> {
    log.info("批次 {} 在库位 {} 有 {} 件库存",
        b.getBatchCode(), b.getLocationCode(), b.getQuantity());
});
```

---

## 受影响的方法

### 直接调用 findByBatchCode() 的地方（已修复）

1. ✅ **InventoryBatchService.findByBatchCode()**（line 351）
2. ✅ **PurchaseOrderService.confirmReceipt()**（line 368）

### 间接调用的地方（无需修改）

1. ✅ **InventoryBatchService.getStockByBatchCode()**（line 467）
   - 调用 Service 层的 `findByBatchCode()`，返回单个 InventoryBatch
   - 无需修改

2. ✅ **InventoryBatchController.getBatchByCode()**（line 249）
   - 调用 Service 层的 `findByBatchCode()`，返回单个 InventoryBatch
   - 无需修改

---

## 测试建议

### 单元测试
```java
@Test
void findByBatchCode_MultipleLocations_ReturnsFirstBatch() {
    // Given: 同一批次码在两个库位
    InventoryBatch batch1 = createBatch("BC12345", "A01", 50);
    InventoryBatch batch2 = createBatch("BC12345", "B03", 30);
    when(repository.findByBatchCode("BC12345"))
        .thenReturn(List.of(batch1, batch2));

    // When
    InventoryBatch result = service.findByBatchCode("BC12345");

    // Then: 返回第一个批次
    assertThat(result.getLocationCode()).isEqualTo("A01");
    assertThat(result.getQuantity()).isEqualTo(50);
}

@Test
void findByBatchCode_NotFound_ThrowsException() {
    // Given
    when(repository.findByBatchCode("INVALID")).thenReturn(List.of());

    // When & Then
    assertThatThrownBy(() -> service.findByBatchCode("INVALID"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("BATCH_NOT_FOUND");
}
```

### 集成测试
```java
@Test
@Sql("/test-data/multi-location-batches.sql")
void purchaseOrderConfirmReceipt_MultipleBatches_Success() {
    // Given: 数据库中有同一批次在不同库位
    BatchReceiptData receipt = BatchReceiptData.builder()
        .batchCode("BC12345")
        .receivedQuantity(100)
        .build();

    // When: 确认收货
    purchaseOrderService.confirmReceipt(1L, List.of(receipt));

    // Then: 应成功处理（使用第一个匹配的批次）
    List<InventoryBatch> batches = repository.findByBatchCode("BC12345");
    assertThat(batches).isNotEmpty();
}
```

---

## 编译验证

修复完成后，运行以下命令验证编译成功：

```bash
# Maven 项目
mvn clean compile

# 或跳过测试快速编译
mvn clean compile -DskipTests

# 运行完整测试套件
mvn clean test
```

**预期结果**：
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 相关文件

| 文件路径 | 修改类型 | 说明 |
|---------|---------|------|
| `src/main/java/com/wms/system/repository/InventoryBatchRepository.java` | 删除 | 删除旧版本 Optional 方法定义 |
| `src/main/java/com/wms/system/service/InventoryBatchService.java` | 更新 | 更新 findByBatchCode() 处理 List |
| `src/main/java/com/wms/system/service/PurchaseOrderService.java` | 更新 | 更新 confirmReceipt() 处理 List |

---

## 版本历史

- **V3.0**: 单库位模式，`findByBatchCode()` 返回 `Optional<InventoryBatch>`
- **V3.3**: 多库位模式，`findByBatchCode()` 返回 `List<InventoryBatch>`
- **2026-01-18**: 删除旧版本方法定义，统一使用 V3.3 多库位模式

---

## 注意事项

⚠️ **代码迁移提示**：
1. 如果其他地方调用了 `findByBatchCode()` 并期望 `Optional` 返回类型，需要相应更新
2. 如果需要查询**特定库位**的批次，应使用 `findByBatchCodeAndLocationCode(batchCode, locationCode)`
3. 如果需要查询**跨库位总库存**，使用 `findByBatchCode(batchCode)` 并遍历 List 求和

⚠️ **业务逻辑提示**：
- `findByBatchCode()` 返回第一个匹配的批次（按数据库插入顺序）
- 如果业务逻辑需要特定库位的批次，应明确指定 locationCode
- 多库位场景下，不应假设批次码唯一对应一条记录

---

**修复完成日期**: 2026-01-18
**修复人**: WMS Team
**版本**: V3.3 Multi-Location Batch Management

package com.wms.system.repository;

import com.wms.system.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品数据访问接口
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 条形码查询（商品唯一标识）
 * 3. 低库存预警查询（库存预测的基础）
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 根据条形码查询商品（用于扫码入库/出库）
     * 方法命名规范：findBy + 字段名（Barcode）
     *
     * 使用场景：
     * 1. 扫码枪扫描商品条形码
     * 2. 系统根据条形码识别商品
     * 3. 自动填充商品信息（名称、规格、单价等）
     *
     * @param barcode 商品条形码（唯一）
     * @return Optional<Product> 商品对象（可能为空）
     */
    Optional<Product> findByBarcode(String barcode);

    /**
     * 检查条形码是否已存在（用于商品新增时校验）
     * 方法命名规范：existsBy + 字段名
     *
     * @param barcode 商品条形码
     * @return true 表示条形码已存在
     */
    boolean existsByBarcode(String barcode);

    /**
     * 根据商品名称模糊查询（用于商品搜索功能）
     * 方法命名规范：findBy + 字段名（Name） + Containing
     *
     * @param keyword 搜索关键词
     * @return 商品名称包含关键词的所有商品
     */
    List<Product> findByNameContaining(String keyword);

    /**
     * 查询所有启用的商品
     * 方法命名规范：findBy + 字段名（Enabled） + 条件（True）
     *
     * @return 所有启用状态的商品
     */
    List<Product> findByEnabledTrue();

    /**
     * 根据商品分类查询
     * 方法命名规范：findBy + 字段名（Category）
     *
     * @param category 商品分类
     * @return 该分类下的所有商品
     */
    List<Product> findByCategory(String category);

    /**
     * 根据供应商查询
     * 方法命名规范：findBy + 字段名（Supplier）
     *
     * @param supplier 供应商名称
     * @return 该供应商的所有商品
     */
    List<Product> findBySupplier(String supplier);

    /**
     * ⭐核心方法：查询低库存商品（用于库存预警）
     *
     * 业务逻辑：
     * 查询"当前库存 < 安全库存"的所有商品，这些商品需要补货
     *
     * JPQL 语法说明：
     * 1. SELECT p: 查询 Product 实体
     * 2. FROM Product p: 从 Product 表查询（p 是别名）
     * 3. 子查询汇总 InventoryBatch（V3.0 起唯一库存数据源，active=true 且已分配库位）
     * 4. WHERE 总库存 < p.minStock: 实际库存 < 安全库存
     * 5. GROUP BY p: 按商品分组（因为一个商品可能分布在多个库位）
     * 6. HAVING SUM(i.quantity) < p.minStock: 所有库位的总库存 < 安全库存
     *
     * 数据示例：
     * 商品A：安全库存 100，实际库存 50  → 需要预警
     * 商品B：安全库存 100，实际库存 150 → 正常
     *
     * 返回结果用于：
     * 1. 库存预警页面展示
     * 2. 定时任务自动发送预警通知
     * 3. 采购建议清单生成
     *
     * @return 需要补货的商品列表
     */
    @Query("SELECT p FROM Product p " +
           "WHERE p.id IN (" +
           "  SELECT b.product.id FROM InventoryBatch b " +
           "  WHERE b.active = true AND b.location IS NOT NULL " +
           "  GROUP BY b.product.id " +
           "  HAVING SUM(b.quantity) < " +
           "    (SELECT p2.minStock FROM Product p2 WHERE p2.id = b.product.id)" +
           ")")
    List<Product> findLowStockProducts();

    /**
     * 查询某个供应商的低库存商品（用于采购订单生成）
     *
     * 业务场景：
     * 采购部门需要向某个供应商下单时，批量查询该供应商的所有缺货商品
     *
     * @param supplier 供应商名称
     * @return 该供应商的所有低库存商品
     */
    @Query("SELECT p FROM Product p " +
           "WHERE p.supplier = :supplier " +
           "AND p.id IN (" +
           "  SELECT b.product.id FROM InventoryBatch b " +
           "  WHERE b.active = true AND b.location IS NOT NULL " +
           "  GROUP BY b.product.id " +
           "  HAVING SUM(b.quantity) < " +
           "    (SELECT p2.minStock FROM Product p2 WHERE p2.id = b.product.id)" +
           ")")
    List<Product> findLowStockProductsBySupplier(@Param("supplier") String supplier);

    /**
     * 根据采购提前期排序查询商品（用于紧急补货优先级排序）
     *
     * 业务逻辑：
     * 采购提前期越长，越需要提前补货
     * 例如：进口商品（leadTime=30天）应优先于本地商品（leadTime=3天）
     *
     * @return 按采购提前期降序排序的所有商品
     */
    @Query("SELECT p FROM Product p ORDER BY p.leadTime DESC")
    List<Product> findAllOrderByLeadTimeDesc();

    /**
     * 根据安全库存阈值查询商品（用于批量设置库存预警规则）
     *
     * 业务场景：
     * 管理员需要调整库存预警策略，查询安全库存设置过高或过低的商品
     *
     * @param minThreshold 最小阈值
     * @param maxThreshold 最大阈值
     * @return 安全库存在指定范围内的商品
     */
    @Query("SELECT p FROM Product p WHERE p.minStock BETWEEN :minThreshold AND :maxThreshold")
    List<Product> findByMinStockBetween(@Param("minThreshold") Integer minThreshold,
                                         @Param("maxThreshold") Integer maxThreshold);
}

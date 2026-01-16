package com.wms.system.repository;

import com.wms.system.entity.Inventory;
import com.wms.system.entity.Location;
import com.wms.system.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 库存数据访问接口（核心表）
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 商品 + 库位组合查询（精确定位库存）
 * 3. 库存汇总查询（计算商品在所有仓库的总库存）
 * 4. 低库存查询（用于库存预警）
 * 5. 库存分布查询（查询某商品在哪些库位有库存）
 *
 * 并发控制：
 * - 实体类使用 @Version 乐观锁，防止并发更新冲突
 * - 更新失败时抛出 OptimisticLockException
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * ⭐核心方法：根据商品和库位精确查询库存
     * 方法命名规范：findBy + 字段1（Product） + And + 字段2（Location）
     *
     * 使用场景：
     * 1. 入库时：检查该库位是否已有该商品的库存
     *    - 有：quantity += 入库数量
     *    - 无：新增一条库存记录
     * 2. 出库时：检查该库位的库存是否充足
     * 3. 库存查询：查询特定商品在特定库位的数量
     *
     * @param product 商品对象
     * @param location 库位对象
     * @return Optional<Inventory> 库存对象（可能为空）
     */
    Optional<Inventory> findByProductAndLocation(Product product, Location location);

    /**
     * 根据商品查询所有库存记录（查询某商品的库存分布）
     * 方法命名规范：findBy + 字段名（Product）
     *
     * 使用场景：
     * 查询某商品分布在哪些库位，每个库位有多少库存
     *
     * 示例结果：
     * 商品：可口可乐 500ml
     * - WH01-ZONE_A-A-01-001: 100 件
     * - WH01-ZONE_A-A-01-002: 50 件
     * - WH01-ZONE_B-B-05-010: 200 件
     * 总库存：350 件
     *
     * @param product 商品对象
     * @return 该商品的所有库存记录
     */
    List<Inventory> findByProduct(Product product);

    /**
     * 根据商品ID查询所有库存记录
     * 方法命名规范：findBy + 字段名（Product） + _ + 子字段（Id）
     *
     * @param productId 商品ID
     * @return 该商品的所有库存记录
     */
    List<Inventory> findByProduct_Id(Long productId);

    /**
     * 根据库位查询所有库存记录（查询某库位存放了哪些商品）
     * 方法命名规范：findBy + 字段名（Location）
     *
     * 使用场景：
     * 查询某个库位存放了哪些商品
     *
     * @param location 库位对象
     * @return 该库位的所有库存记录
     */
    List<Inventory> findByLocation(Location location);

    /**
     * 根据库位ID查询所有库存记录
     * 方法命名规范：findBy + 字段名（Location） + _ + 子字段（Id）
     *
     * @param locationId 库位ID
     * @return 该库位的所有库存记录
     */
    List<Inventory> findByLocation_Id(Long locationId);

    /**
     * ⭐核心方法：计算某商品在所有仓库的总库存（库存汇总）
     *
     * 业务逻辑：
     * 一个商品可能分布在多个库位，需要汇总所有库位的库存数量
     *
     * JPQL 语法说明：
     * 1. SELECT SUM(i.quantity): 汇总库存数量
     * 2. FROM Inventory i: 从 Inventory 表查询
     * 3. WHERE i.product.id = :productId: 筛选指定商品
     * 4. 返回值可能为 null（如果商品没有任何库存记录）
     *
     * 使用场景：
     * 1. 库存查询：查询某商品的总库存
     * 2. 库存预警：判断总库存是否低于安全库存
     * 3. 销售订单：检查库存是否充足
     *
     * 数据示例：
     * 商品：可口可乐 500ml
     * - 库位1：100 件
     * - 库位2：50 件
     * - 库位3：200 件
     * 总库存：350 件
     *
     * @param productId 商品ID
     * @return 总库存数量（如果没有库存记录，返回 null）
     */
    @Query("SELECT SUM(i.quantity) FROM Inventory i WHERE i.product.id = :productId")
    Integer sumTotalQuantityByProduct(@Param("productId") Long productId);

    /**
     * 计算某商品在指定仓库的总库存
     *
     * 业务场景：
     * 多仓库管理时，查询某商品在某个仓库的总库存
     *
     * @param productId 商品ID
     * @param warehouseCode 仓库编码
     * @return 该商品在该仓库的总库存
     */
    @Query("SELECT SUM(i.quantity) FROM Inventory i " +
           "WHERE i.product.id = :productId " +
           "AND i.location.warehouseCode = :warehouseCode")
    Integer sumTotalQuantityByProductAndWarehouse(@Param("productId") Long productId,
                                                    @Param("warehouseCode") String warehouseCode);

    /**
     * 查询某商品在指定区域的库存分布
     *
     * 业务场景：
     * 查询高频商品在 ZONE_A（快速拣货区）的库存分布
     *
     * @param productId 商品ID
     * @param zone 库位区域
     * @return 该商品在该区域的所有库存记录
     */
    @Query("SELECT i FROM Inventory i " +
           "WHERE i.product.id = :productId " +
           "AND i.location.zone = :zone")
    List<Inventory> findByProductAndZone(@Param("productId") Long productId,
                                          @Param("zone") String zone);

    /**
     * 查询库存数量大于 0 的所有记录（排除已清空的库位）
     * 方法命名规范：findBy + 字段名（Quantity） + 比较条件（GreaterThan）
     *
     * 使用场景：
     * 查询实际有库存的记录（排除历史清空的库位）
     *
     * @param quantity 库存数量阈值（通常为 0）
     * @return 库存数量 > 阈值的所有记录
     */
    List<Inventory> findByQuantityGreaterThan(Integer quantity);

    /**
     * 查询低库存记录（库存数量 < 指定阈值）
     * 方法命名规范：findBy + 字段名（Quantity） + 比较条件（LessThan）
     *
     * 使用场景：
     * 1. 配合 Product.minStock 字段，查询需要补货的库存记录
     * 2. 库存预警功能
     *
     * @param threshold 库存阈值
     * @return 库存数量 < 阈值的所有记录
     */
    List<Inventory> findByQuantityLessThan(Integer threshold);

    /**
     * ⭐核心方法：查询需要预警的库存记录（总库存 < 安全库存）
     *
     * 业务逻辑：
     * 查询"某商品的总库存 < 该商品的安全库存"的所有记录
     *
     * JPQL 语法说明：
     * 1. SELECT i: 查询 Inventory 实体
     * 2. FROM Inventory i: 从 Inventory 表查询
     * 3. WHERE (...): 使用子查询计算总库存
     * 4. < i.product.minStock: 总库存 < 安全库存
     *
     * 使用场景：
     * 1. 库存预警页面展示
     * 2. 定时任务自动发送预警通知
     *
     * @return 需要预警的库存记录
     */
    @Query("SELECT i FROM Inventory i " +
           "WHERE (" +
           "  SELECT SUM(i2.quantity) FROM Inventory i2 " +
           "  WHERE i2.product = i.product" +
           ") < i.product.minStock")
    List<Inventory> findLowStockInventories();

    /**
     * 统计库存记录总数（用于仓库利用率统计）
     *
     * 业务场景：
     * 仓库利用率 = 已占用库位数 / 总库位数
     *
     * @return 库存记录总数
     */
    @Query("SELECT COUNT(i) FROM Inventory i WHERE i.quantity > 0")
    long countOccupiedLocations();

    /**
     * 查询指定商品在指定仓库和区域的库存记录
     *
     * 业务场景：
     * 精确查询某商品在某仓库某区域的库存分布
     *
     * @param productId 商品ID
     * @param warehouseCode 仓库编码
     * @param zone 库位区域
     * @return 符合条件的库存记录
     */
    @Query("SELECT i FROM Inventory i " +
           "WHERE i.product.id = :productId " +
           "AND i.location.warehouseCode = :warehouseCode " +
           "AND i.location.zone = :zone")
    List<Inventory> findByProductAndWarehouseAndZone(@Param("productId") Long productId,
                                                       @Param("warehouseCode") String warehouseCode,
                                                       @Param("zone") String zone);
}

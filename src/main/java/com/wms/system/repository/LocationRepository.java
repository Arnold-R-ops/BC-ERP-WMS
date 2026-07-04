package com.wms.system.repository;

import com.wms.system.entity.Location;
import com.wms.system.entity.Warehouse;
import com.wms.system.entity.enums.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 库位数据访问接口
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 库位编码查询（库位唯一标识）
 * 3. 区域/货架/位号组合查询
 * 4. 空闲库位查询（用于入库时选择存放位置）
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    /**
     * 根据库位编码查询（唯一标识）
     * 方法命名规范：findBy + 字段名（LocationCode）
     *
     * 使用场景：
     * 1. 扫描库位条形码（库位标签）
     * 2. 快速定位商品存放位置
     *
     * @param locationCode 完整库位编码（例如：WH01-ZONE_A-A-01-001）
     * @return Optional<Location> 库位对象（可能为空）
     */
    Optional<Location> findByLocationCode(String locationCode);

    /**
     * 根据仓库编码查询所有库位
     * 方法命名规范：findBy + 字段名（WarehouseCode）
     *
     * 使用场景：
     * 多仓库管理时，查询某个仓库的所有库位
     *
     * @param warehouseCode 仓库编码（例如：WH01）
     * @return 该仓库的所有库位
     */
    List<Location> findByWarehouseCode(String warehouseCode);

    /**
     * 根据区域查询库位
     * 方法命名规范：findBy + 字段名（Zone）
     *
     * 使用场景：
     * 1. 查询高频商品区（ZONE_A）的所有库位
     * 2. 查询冷藏区（ZONE_D）的所有库位
     *
     * @param zone 库位区域
     * @return 该区域的所有库位
     */
    List<Location> findByZone(Zone zone);

    /**
     * 根据仓库和区域查询库位
     * 方法命名规范：findBy + 字段1（WarehouseCode） + And + 字段2（Zone）
     *
     * @param warehouseCode 仓库编码
     * @param zone 库位区域
     * @return 符合条件的库位列表
     */
    List<Location> findByWarehouseCodeAndZone(String warehouseCode, Zone zone);

    /**
     * 查询所有启用的库位
     * 方法命名规范：findBy + 字段名（Enabled） + 条件（True）
     *
     * @return 所有启用状态的库位
     */
    List<Location> findByEnabledTrue();

    /**
     * 根据货架号查询库位
     * 方法命名规范：findBy + 字段名（ShelfNumber）
     *
     * 使用场景：
     * 查询某个货架上的所有位号（例如：A-01 货架的所有位置）
     *
     * @param shelfNumber 货架号
     * @return 该货架的所有库位
     */
    List<Location> findByShelfNumber(String shelfNumber);

    /**
     * 根据仓库、区域、货架号、位号精确查询
     * 方法命名规范：findBy + 多字段组合 + And 连接
     *
     * 使用场景：
     * 精确定位某个库位（例如：WH01 仓库，A区，A-01货架，001号位置）
     *
     * @param warehouseCode 仓库编码
     * @param zone 区域
     * @param shelfNumber 货架号
     * @param positionNumber 位号
     * @return Optional<Location> 库位对象（可能为空）
     */
    Optional<Location> findByWarehouseCodeAndZoneAndShelfNumberAndPositionNumber(
        String warehouseCode,
        Zone zone,
        String shelfNumber,
        String positionNumber
    );

    /**
     * ⭐核心方法：查询空闲库位（用于入库时推荐存放位置）
     *
     * 业务逻辑：
     * 查询没有库存记录的库位（即：从未存放过任何商品的库位）
     *
     * JPQL 语法说明：
     * 1. SELECT l: 查询 Location 实体
     * 2. FROM Location l: 从 Location 表查询
     * 3. WHERE l.enabled = true: 库位状态为启用
     * 4. AND NOT EXISTS: 不存在关联的库存记录
     * 5. FROM Inventory i WHERE i.location = l: 子查询，检查是否有库存
     *
     * 使用场景：
     * 1. 新商品入库时，系统推荐空闲库位
     * 2. 仓库管理员查看可用库位数量
     *
     * @return 所有空闲的库位
     */
    @Query("SELECT l FROM Location l " +
           "WHERE l.enabled = true " +
           "AND NOT EXISTS (SELECT 1 FROM InventoryBatch b WHERE b.location = l AND b.active = true AND b.quantity > 0)")
    List<Location> findEmptyLocations();

    /**
     * 查询指定区域的空闲库位（用于按区域推荐库位）
     *
     * 业务场景：
     * 1. 高频商品应放在 ZONE_A（快速拣货区）
     * 2. 冷藏商品应放在 ZONE_D（冷藏区）
     * 3. 系统根据商品特性推荐合适的区域
     *
     * @param zone 库位区域
     * @return 该区域的所有空闲库位
     */
    @Query("SELECT l FROM Location l " +
           "WHERE l.zone = :zone " +
           "AND l.enabled = true " +
           "AND NOT EXISTS (SELECT 1 FROM InventoryBatch b WHERE b.location = l AND b.active = true AND b.quantity > 0)")
    List<Location> findEmptyLocationsByZone(@Param("zone") Zone zone);

    /**
     * 查询已占用的库位（有库存的库位）
     *
     * 业务场景：
     * 统计仓库利用率：已占用库位数 / 总库位数
     *
     * @return 所有已占用的库位
     */
    @Query("SELECT DISTINCT l FROM Location l " +
           "JOIN InventoryBatch b ON b.location = l " +
           "WHERE b.active = true AND b.quantity > 0")
    List<Location> findOccupiedLocations();

    /**
     * 统计指定区域的库位总数
     *
     * 方法命名规范：countBy + 字段名（Zone）
     *
     * @param zone 库位区域
     * @return 该区域的库位总数
     */
    long countByZone(Zone zone);

    /**
     * 统计指定仓库的库位总数
     *
     * 方法命名规范：countBy + 字段名（WarehouseCode）
     *
     * @param warehouseCode 仓库编码
     * @return 该仓库的库位总数
     */
    long countByWarehouseCode(String warehouseCode);

    // ========== Phase 3.4: Warehouse-based Query Methods ==========

    /**
     * 根据仓库对象查询所有库位（Phase 3.4 新增）
     * 方法命名规范：findBy + 关联实体（Warehouse）
     *
     * 使用场景：
     * 1. 已有 Warehouse 对象时，直接查询其关联的库位
     * 2. 利用 JPA 关联关系查询
     *
     * @param warehouse 仓库对象
     * @return 该仓库的所有库位
     */
    List<Location> findByWarehouse(Warehouse warehouse);

    /**
     * 根据仓库ID查询所有库位（Phase 3.4 新增）
     * 方法命名规范：findBy + 关联实体（Warehouse） + Id
     *
     * 使用场景：
     * 1. 只有仓库ID时，查询其关联的库位
     * 2. 避免先查询 Warehouse 对象的额外开销
     *
     * @param warehouseId 仓库ID
     * @return 该仓库的所有库位
     */
    List<Location> findByWarehouseId(Long warehouseId);

    /**
     * 根据仓库对象和区域查询库位（Phase 3.4 新增）
     * 方法命名规范：findBy + 关联实体（Warehouse） + And + 字段（Zone）
     *
     * 使用场景：
     * 1. 查询指定仓库的指定区域库位
     * 2. 例如：查询 WH01 仓库的 ZONE_A 区域库位
     *
     * @param warehouse 仓库对象
     * @param zone 库位区域
     * @return 符合条件的库位列表
     */
    List<Location> findByWarehouseAndZone(Warehouse warehouse, Zone zone);

    /**
     * 查询指定仓库的空闲库位（Phase 3.4 新增）
     *
     * 业务逻辑：
     * 查询指定仓库中没有库存记录的库位
     *
     * 使用场景：
     * 1. 多仓库管理时，按仓库推荐空闲库位
     * 2. 统计各仓库的可用库位数量
     *
     * @param warehouseId 仓库ID
     * @return 该仓库的所有空闲库位
     */
    @Query("SELECT l FROM Location l " +
           "WHERE l.warehouse.id = :warehouseId " +
           "AND l.enabled = true " +
           "AND NOT EXISTS (SELECT 1 FROM InventoryBatch b WHERE b.location = l AND b.active = true AND b.quantity > 0)")
    List<Location> findEmptyLocationsByWarehouseId(@Param("warehouseId") Long warehouseId);

    /**
     * 统计指定仓库的库位总数（Phase 3.4 新增，使用 warehouse_id）
     * 方法命名规范：countBy + 关联实体（Warehouse） + Id
     *
     * 使用场景：
     * 1. 统计各仓库的库位数量
     * 2. 仓库管理界面显示统计信息
     *
     * @param warehouseId 仓库ID
     * @return 该仓库的库位总数
     */
    long countByWarehouseId(Long warehouseId);
}

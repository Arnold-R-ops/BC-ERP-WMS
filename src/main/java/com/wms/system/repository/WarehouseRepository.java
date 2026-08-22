package com.wms.system.repository;

import com.wms.system.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 仓库数据访问接口
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 仓库编码查询（业务主键查询）
 * 3. 激活状态查询（查询可用仓库）
 * 4. 模糊搜索（按名称搜索）
 * 5. 统计查询（库位数量统计）
 *
 * Phase 3.4 新增：
 * - 支持多仓库管理
 * - 提供业务主键（code）查询方法
 * - 支持仓库激活/停用状态管理
 *
 * @author WMS Team
 * @since 2025-01-23 (Phase 3.4)
 */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    /**
     * 根据仓库编码查询（业务主键查询）
     * 方法命名规范：findBy + 字段名（Code）
     *
     * 使用场景：
     * 1. 根据业务编码查找仓库（如：WH01）
     * 2. 验证仓库编码是否存在
     * 3. 创建库位时查找关联仓库
     *
     * @param code 仓库编码（例如：WH01, WH02）
     * @return Optional<Warehouse> 仓库对象（可能为空）
     */
    Optional<Warehouse> findByCode(String code);

    /**
     * 检查仓库编码是否存在
     * 方法命名规范：existsBy + 字段名（Code）
     *
     * 使用场景：
     * 1. 创建仓库前验证编码唯一性
     * 2. 避免重复编码
     *
     * @param code 仓库编码
     * @return true 如果编码已存在，false 如果不存在
     */
    boolean existsByCode(String code);

    /**
     * 查询所有激活的仓库
     * 方法命名规范：findBy + 字段名（IsActive）
     *
     * 使用场景：
     * 1. 获取可用仓库列表（用于下拉选择）
     * 2. 排除已停用的仓库
     *
     * @return 所有激活状态的仓库列表
     */
    List<Warehouse> findByIsActive(Boolean isActive);

    /**
     * 查询所有激活的仓库（自定义查询方法名）
     *
     * 使用场景：
     * 1. 提供更语义化的方法名
     * 2. 等同于 findByIsActive(true)
     *
     * @return 所有激活状态的仓库列表
     */
    @Query("SELECT w FROM Warehouse w WHERE w.isActive = true ORDER BY w.code")
    List<Warehouse> findAllActive();

    @Query("""
        SELECT w FROM Warehouse w
        WHERE w.companyId = :companyId AND w.isActive = true
        ORDER BY w.code
        """)
    List<Warehouse> findAllActiveByCompanyId(@Param("companyId") Long companyId);

    List<Warehouse> findAllByCompanyIdAndIdIn(
        Long companyId, Set<Long> warehouseIds);

    /**
     * 根据名称模糊查询仓库
     * 方法命名规范：findBy + 字段名 + Containing（模糊匹配）
     *
     * 使用场景：
     * 1. 仓库搜索功能
     * 2. 按名称筛选仓库
     *
     * @param name 仓库名称关键字
     * @return 名称包含关键字的仓库列表
     */
    List<Warehouse> findByNameContaining(String name);

    /**
     * 统计指定仓库的库位数量
     * 使用 JPQL 查询关联的库位数量
     *
     * 使用场景：
     * 1. 仓库管理界面显示库位统计
     * 2. 判断仓库是否可以删除（有库位则不能删除）
     *
     * @param warehouseId 仓库ID
     * @return 该仓库的库位数量
     */
    @Query("SELECT COUNT(l) FROM Location l WHERE l.warehouse.id = :warehouseId")
    long countLocationsByWarehouseId(@Param("warehouseId") Long warehouseId);

    /**
     * 查询所有仓库（按编码排序）
     * 覆盖默认的 findAll 方法，添加排序
     *
     * 使用场景：
     * 1. 仓库列表展示
     * 2. 按编码顺序显示
     *
     * @return 所有仓库列表（按 code 升序）
     */
    @Query("SELECT w FROM Warehouse w ORDER BY w.code")
    List<Warehouse> findAll();
}

package com.wms.system.repository;

import com.wms.system.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 销售订单明细数据访问接口
 *
 * V3.7 架构：销售订单明细管理
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 根据销售订单ID查询明细列表
 * 3. 根据产品ID查询明细列表
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Repository
public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {

    /**
     * 根据销售订单ID查询明细列表
     *
     * 使用场景：
     * 1. 订单详情页展示明细
     * 2. 智能分配算法获取订单明细
     *
     * @param salesOrderId 销售订单ID
     * @return 该订单的所有明细
     */
    List<SalesOrderItem> findBySalesOrderId(Long salesOrderId);

    /**
     * 根据产品ID查询明细列表
     *
     * @param productSkuId 产品ID
     * @return 包含该产品的所有订单明细
     */
    List<SalesOrderItem> findByProductSkuId(Long productSkuId);

    /**
     * 根据销售订单ID删除所有明细
     *
     * 使用场景：
     * 1. 订单取消时删除明细
     * 2. 订单修改时先删除旧明细
     *
     * @param salesOrderId 销售订单ID
     */
    void deleteBySalesOrderId(Long salesOrderId);

    /**
     * 查询指定批次的订单明细
     *
     * 说明：
     * - 查询 specified_batch_ids 包含指定批次ID的订单明细
     * - 用于批次追溯和分析
     *
     * @param batchId 批次ID
     * @return 指定该批次的所有订单明细
     */
    @Query("SELECT s FROM SalesOrderItem s WHERE s.specifiedBatchIds LIKE CONCAT('%', :batchId, '%')")
    List<SalesOrderItem> findBySpecifiedBatchIdsContaining(@Param("batchId") String batchId);
}

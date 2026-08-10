package com.wms.system.repository;

import com.wms.system.entity.OutboundTask;
import com.wms.system.entity.enums.OutboundTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import jakarta.persistence.LockModeType;

/**
 * 出库任务数据访问接口
 *
 * V3.7 架构：出库任务管理
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 根据销售订单ID查询任务列表
 * 3. 根据批次ID查询任务列表
 * 4. 根据状态查询任务列表
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Repository
public interface OutboundTaskRepository extends JpaRepository<OutboundTask, Long> {

    /**
     * 根据销售订单ID查询任务列表
     *
     * 使用场景：
     * 1. 订单详情页展示出库任务
     * 2. 订单取消时释放库存
     *
     * @param salesOrderId 销售订单ID
     * @return 该订单的所有出库任务
     */
    List<OutboundTask> findBySalesOrderId(Long salesOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboundTask o WHERE o.salesOrderId = :salesOrderId ORDER BY o.id")
    List<OutboundTask> findBySalesOrderIdForUpdate(@Param("salesOrderId") Long salesOrderId);

    /**
     * 根据销售订单明细ID查询任务列表
     *
     * @param salesOrderItemId 销售订单明细ID
     * @return 该订单明细的所有出库任务
     */
    List<OutboundTask> findBySalesOrderItemId(Long salesOrderItemId);

    /**
     * 根据批次ID查询任务列表
     *
     * @param assignedBatchId 批次ID
     * @return 分配该批次的所有出库任务
     */
    List<OutboundTask> findByAssignedBatchId(Long assignedBatchId);

    /**
     * 根据状态查询任务列表
     *
     * 使用场景：
     * 1. 查询待拣货任务
     * 2. 查询拣货中任务
     *
     * @param status 任务状态
     * @return 指定状态的所有任务
     */
    List<OutboundTask> findByStatus(OutboundTaskStatus status);

    /**
     * 根据拣货人ID查询任务列表
     *
     * @param pickedBy 拣货人ID
     * @return 该拣货人的所有任务
     */
    List<OutboundTask> findByPickedBy(Long pickedBy);

    /**
     * 根据库位ID查询任务列表
     *
     * @param locationId 库位ID
     * @return 该库位的所有出库任务
     */
    List<OutboundTask> findByLocationId(Long locationId);

    /**
     * 根据销售订单ID和状态查询任务列表
     *
     * @param salesOrderId 销售订单ID
     * @param status 任务状态
     * @return 指定订单和状态的所有任务
     */
    List<OutboundTask> findBySalesOrderIdAndStatus(Long salesOrderId, OutboundTaskStatus status);

    /**
     * 根据销售订单ID删除所有任务
     *
     * 使用场景：
     * 1. 订单取消时删除出库任务
     * 2. 释放已预占的库存
     *
     * @param salesOrderId 销售订单ID
     */
    void deleteBySalesOrderId(Long salesOrderId);

    /**
     * 查询待拣货任务数量
     *
     * @return 待拣货任务数量
     */
    @Query("SELECT COUNT(o) FROM OutboundTask o WHERE o.status = 'PENDING'")
    long countPendingTasks();

    /**
     * 查询指定订单的已完成任务数量
     *
     * @param salesOrderId 销售订单ID
     * @return 已完成任务数量
     */
    @Query("SELECT COUNT(o) FROM OutboundTask o WHERE o.salesOrderId = :salesOrderId AND o.status = 'COMPLETED'")
    long countCompletedTasksBySalesOrderId(@Param("salesOrderId") Long salesOrderId);

    /**
     * 查询指定订单的总任务数量
     *
     * @param salesOrderId 销售订单ID
     * @return 总任务数量
     */
    @Query("SELECT COUNT(o) FROM OutboundTask o WHERE o.salesOrderId = :salesOrderId")
    long countTasksBySalesOrderId(@Param("salesOrderId") Long salesOrderId);
}

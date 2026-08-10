package com.wms.system.repository;

import com.wms.system.entity.SalesOrder;
import com.wms.system.entity.enums.SalesOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

/**
 * 销售订单数据访问接口
 *
 * V3.7 架构：销售订单管理
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 根据订单编号查询订单
 * 3. 根据状态查询订单列表
 * 4. 根据客户查询订单列表
 * 5. 根据申请人查询订单列表
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SalesOrder s WHERE s.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    /**
     * 根据订单编号查询订单
     *
     * 使用场景：
     * 1. 订单详情查询
     * 2. 订单状态更新
     *
     * @param orderNo 订单编号
     * @return Optional<SalesOrder> 订单对象（可能为空）
     */
    Optional<SalesOrder> findByOrderNo(String orderNo);

    /**
     * 检查订单编号是否已存在
     *
     * @param orderNo 订单编号
     * @return true 表示订单编号已存在
     */
    boolean existsByOrderNo(String orderNo);

    /**
     * 根据状态查询订单列表
     *
     * 使用场景：
     * 1. 查询待审批订单
     * 2. 查询待发货订单
     *
     * @param status 订单状态
     * @return 指定状态的所有订单
     */
    List<SalesOrder> findByStatus(SalesOrderStatus status);

    /**
     * 根据客户ID查询订单列表
     *
     * @param customerId 客户ID
     * @return 该客户的所有订单
     */
    List<SalesOrder> findByCustomerId(Long customerId);

    /**
     * 根据申请人ID查询订单列表
     *
     * @param applicantId 申请人ID
     * @return 该申请人的所有订单
     */
    List<SalesOrder> findByApplicantId(Long applicantId);

    /**
     * 根据审批人ID查询订单列表
     *
     * @param reviewedBy 审批人ID
     * @return 该审批人审批的所有订单
     */
    List<SalesOrder> findByReviewedBy(Long reviewedBy);

    /**
     * 根据时间范围查询订单列表
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 指定时间范围内的所有订单
     */
    @Query("SELECT s FROM SalesOrder s WHERE s.createdAt BETWEEN :startTime AND :endTime")
    List<SalesOrder> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 根据客户ID和状态查询订单列表
     *
     * @param customerId 客户ID
     * @param status 订单状态
     * @return 指定客户和状态的所有订单
     */
    List<SalesOrder> findByCustomerIdAndStatus(Long customerId, SalesOrderStatus status);

    /**
     * 查询待审批订单数量
     *
     * @return 待审批订单数量
     */
    @Query("SELECT COUNT(s) FROM SalesOrder s WHERE s.status = 'PENDING_APPROVAL'")
    long countPendingApprovalOrders();

    /**
     * 根据外部订单ID查询订单
     *
     * 使用场景：
     * 1. Shopify 订单同步时去重检查
     * 2. 外部订单关联查询
     *
     * @param externalOrderId 外部订单ID
     * @return Optional<SalesOrder> 订单对象（可能为空）
     */
    Optional<SalesOrder> findByExternalOrderId(String externalOrderId);

    long countByChannelAndExternalOrderIdIsNotNull(String channel);

    @Query("SELECT s.externalOrderId FROM SalesOrder s " +
           "WHERE s.channel = :channel AND s.externalOrderId IN :externalOrderIds")
    List<String> findExistingExternalOrderIds(
        @Param("channel") String channel,
        @Param("externalOrderIds") List<String> externalOrderIds
    );
}

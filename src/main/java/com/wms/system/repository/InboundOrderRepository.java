package com.wms.system.repository;

import com.wms.system.entity.InboundOrder;
import com.wms.system.entity.enums.InboundOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 入库单数据访问接口
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Repository
public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {

    /**
     * 根据入库单号查询
     */
    Optional<InboundOrder> findByOrderNo(String orderNo);

    /**
     * 检查入库单号是否已存在
     */
    boolean existsByOrderNo(String orderNo);

    /**
     * 根据状态查询入库单
     */
    List<InboundOrder> findByStatus(InboundOrderStatus status);

    /**
     * 根据供应商ID查询入库单
     */
    List<InboundOrder> findBySupplierId(Long supplierId);

    /**
     * 根据申请人ID查询入库单
     */
    List<InboundOrder> findByApplicantId(Long applicantId);

    /**
     * 根据状态和申请人ID查询入库单
     */
    List<InboundOrder> findByStatusAndApplicantId(InboundOrderStatus status, Long applicantId);

    /**
     * 查询指定时间范围内的入库单
     */
    @Query("SELECT io FROM InboundOrder io WHERE io.createdAt BETWEEN :startDate AND :endDate ORDER BY io.createdAt DESC")
    List<InboundOrder> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * 查询待审批的入库单（总经理审批）
     */
    List<InboundOrder> findByStatusOrderByCreatedAtDesc(InboundOrderStatus status);

    /**
     * 查询指定供应商的入库单（按创建时间降序）
     */
    @Query("SELECT io FROM InboundOrder io WHERE io.supplier.id = :supplierId ORDER BY io.createdAt DESC")
    List<InboundOrder> findBySupplierIdOrderByCreatedAtDesc(@Param("supplierId") Long supplierId);

    /**
     * 统计指定状态的入库单数量
     */
    long countByStatus(InboundOrderStatus status);

    /**
     * 统计指定申请人的入库单数量
     */
    long countByApplicantId(Long applicantId);

    long countBySupplier_IdAndStatusIn(
        Long supplierId,
        List<InboundOrderStatus> statuses
    );
}

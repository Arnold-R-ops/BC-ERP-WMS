package com.wms.system.repository;

import com.wms.system.entity.InboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 入库单明细数据访问接口
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Repository
public interface InboundOrderItemRepository extends JpaRepository<InboundOrderItem, Long> {

    /**
     * 根据入库单ID查询所有明细
     */
    List<InboundOrderItem> findByInboundOrderId(Long inboundOrderId);

    /**
     * 根据产品ID查询所有明细
     */
    List<InboundOrderItem> findByProductSkuId(Long productSkuId);

    /**
     * 根据批次码查询
     */
    List<InboundOrderItem> findByBatchCode(String batchCode);

    /**
     * 检查批次码是否已存在
     */
    boolean existsByBatchCode(String batchCode);

    /**
     * 查询即将过期的入库单明细（用于预警）
     */
    @Query("SELECT ioi FROM InboundOrderItem ioi WHERE ioi.expiryDate IS NOT NULL AND ioi.expiryDate BETWEEN :startDate AND :endDate ORDER BY ioi.expiryDate ASC")
    List<InboundOrderItem> findByExpiryDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * 根据入库单ID和产品ID查询明细
     */
    List<InboundOrderItem> findByInboundOrderIdAndProductSkuId(Long inboundOrderId, Long productSkuId);

    /**
     * 删除指定入库单的所有明细
     */
    void deleteByInboundOrderId(Long inboundOrderId);

    /**
     * 统计指定入库单的明细数量
     */
    long countByInboundOrderId(Long inboundOrderId);

    /**
     * 查询指定产品的所有批次码
     */
    @Query("SELECT DISTINCT ioi.batchCode FROM InboundOrderItem ioi WHERE ioi.productSku.id = :productSkuId AND ioi.batchCode IS NOT NULL")
    List<String> findDistinctBatchCodesByProductSkuId(@Param("productSkuId") Long productSkuId);
}

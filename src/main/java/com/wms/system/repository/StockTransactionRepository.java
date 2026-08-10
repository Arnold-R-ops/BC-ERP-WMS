package com.wms.system.repository;

import com.wms.system.entity.ProductSku;
import com.wms.system.entity.StockTransaction;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.entity.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存流水数据访问接口（库存预测的核心）
 *
 * 核心功能：
 * 1. 基础 CRUD 操作（继承自 JpaRepository）
 * 2. 按时间范围查询流水（用于数据分析）
 * 3. 按变动类型查询流水（入库/出库/调整）
 * 4. 按来源类型查询流水（采购/销售/调拨等）
 * 5. ⭐计算日均出库量（用于库存预测）
 * 6. ⭐查询出库历史（用于需求预测）
 *
 * 业务价值：
 * - 审计追溯：回答"这批货什么时候入库的？谁操作的？"
 * - 数据分析：计算商品的日均出库量、周转率
 * - 库存预测：基于历史流水，预测未来 N 天的库存消耗
 * - 财务对账：与采购单、销售单、调拨单关联
 *
 * @author WMS Team
 * @since 2025-01-09
 */
@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    /**
     * 根据商品查询所有流水记录
     * 方法命名规范：findBy + 字段名（ProductSku）
     *
     * 使用场景：
     * 查询某商品的完整出入库历史
     *
     * @param product 商品对象
     * @return 该商品的所有流水记录
     */
    List<StockTransaction> findByProductSku(ProductSku product);

    /**
     * 根据商品ID查询所有流水记录（按时间倒序）
     * 方法命名规范：findBy + 字段名 + OrderBy + 排序字段 + 排序方式
     *
     * @param productSkuId 商品ID
     * @return 该商品的所有流水记录（最新的在前）
     */
    List<StockTransaction> findByProductSku_IdOrderByCreatedAtDesc(Long productSkuId);

    /**
     * 根据变动类型查询流水
     * 方法命名规范：findBy + 字段名（TransactionType）
     *
     * 使用场景：
     * 1. 查询所有入库流水（IN）
     * 2. 查询所有出库流水（OUT）
     * 3. 查询所有调整流水（ADJUST）
     *
     * @param transactionType 变动类型
     * @return 该类型的所有流水记录
     */
    List<StockTransaction> findByTransactionType(TransactionType transactionType);

    /**
     * 根据来源类型查询流水
     * 方法命名规范：findBy + 字段名（SourceType）
     *
     * 使用场景：
     * 1. 查询所有采购入库流水（PURCHASE_IN）
     * 2. 查询所有销售出库流水（SALE_OUT）
     * 3. 查询所有调拨流水（TRANSFER_OUT / TRANSFER_IN）
     *
     * @param sourceType 来源类型
     * @return 该来源类型的所有流水记录
     */
    List<StockTransaction> findBySourceType(SourceType sourceType);

    /**
     * 根据来源单据号查询流水
     * 方法命名规范：findBy + 字段名（SourceOrderId）
     *
     * 使用场景：
     * 根据采购单号、销售单号、调拨单号查询关联的流水记录
     *
     * @param sourceOrderId 来源单据号（例如：PO202501090001）
     * @return 该单据的所有流水记录
     */
    List<StockTransaction> findBySourceOrderId(String sourceOrderId);

    /** Tenant-scoped source lookup used by guarded historical-data checks. */
    List<StockTransaction> findByCompanyIdAndSourceOrderId(Long companyId, String sourceOrderId);

    /**
     * ⭐核心方法：查询某商品在指定时间范围内的出库流水（用于计算日均消耗）
     *
     * 业务逻辑：
     * 查询某商品在最近 N 天的所有出库记录，用于计算日均出库量
     *
     * JPQL 语法说明：
     * 1. SELECT t: 查询 StockTransaction 实体
     * 2. FROM StockTransaction t: 从流水表查询
     * 3. WHERE t.productSku.id = :productSkuId: 筛选指定商品
     * 4. AND t.transactionType = 'OUT': 仅查询出库流水
     * 5. AND t.createdAt BETWEEN :startDate AND :endDate: 时间范围筛选
     * 6. ORDER BY t.createdAt DESC: 按时间倒序排序
     *
     * 使用场景：
     * 1. 计算日均出库量：总出库量 / 天数
     * 2. 预测库存消耗：日均出库量 × 未来天数
     * 3. 建议补货量：(日均出库量 × 采购提前期) + 安全库存
     *
     * 数据示例：
     * 商品：可口可乐 500ml
     * 时间范围：最近 30 天
     * 出库记录：
     * - 2025-01-09: 出库 50 件
     * - 2025-01-08: 出库 30 件
     * - 2025-01-07: 出库 45 件
     * - ...
     * 总出库量：900 件
     * 日均出库量：900 / 30 = 30 件/天
     *
     * @param productSkuId 商品ID
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 该商品在指定时间范围内的所有出库流水
     */
    @Query("SELECT t FROM StockTransaction t " +
           "WHERE t.productSku.id = :productSkuId " +
           "AND t.transactionType = 'OUT' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    List<StockTransaction> findMovementHistory(@Param("productSkuId") Long productSkuId,
                                                 @Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    /**
     * ⭐核心方法：计算某商品在指定时间范围内的总出库量
     *
     * 业务逻辑：
     * 直接汇总出库数量，无需查询完整记录，性能更高
     *
     * JPQL 语法说明：
     * 1. SELECT SUM(t.quantity): 汇总出库数量
     * 2. WHERE t.transactionType = 'OUT': 仅统计出库
     * 3. 返回值可能为 null（如果时间范围内没有出库记录）
     *
     * 使用场景：
     * 快速计算日均出库量：总出库量 / 天数
     *
     * @param productSkuId 商品ID
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 总出库量（如果没有出库记录，返回 null）
     */
    @Query("SELECT SUM(t.quantity) FROM StockTransaction t " +
           "WHERE t.productSku.id = :productSkuId " +
           "AND t.transactionType = 'OUT' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Integer sumOutboundQuantity(@Param("productSkuId") Long productSkuId,
                                 @Param("startDate") LocalDateTime startDate,
                                 @Param("endDate") LocalDateTime endDate);

    /**
     * 计算某商品在指定时间范围内的总入库量
     *
     * @param productSkuId 商品ID
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 总入库量
     */
    @Query("SELECT SUM(t.quantity) FROM StockTransaction t " +
           "WHERE t.productSku.id = :productSkuId " +
           "AND t.transactionType = 'IN' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Integer sumInboundQuantity(@Param("productSkuId") Long productSkuId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    /**
     * 查询指定时间范围内的所有流水（按时间倒序）
     *
     * 使用场景：
     * 1. 日报表：查询今天的所有出入库流水
     * 2. 月报表：查询本月的所有出入库流水
     *
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 该时间范围内的所有流水记录
     */
    @Query("SELECT t FROM StockTransaction t " +
           "WHERE t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    List<StockTransaction> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);

    /**
     * 查询某商品的最近 N 条流水记录
     *
     * 使用场景：
     * 快速查看某商品的最近操作历史
     *
     * @param productSkuId 商品ID
     * @param limit 查询数量
     * @return 最近的 N 条流水记录
     */
    @Query("SELECT t FROM StockTransaction t " +
           "WHERE t.productSku.id = :productSkuId " +
           "ORDER BY t.createdAt DESC " +
           "LIMIT :limit")
    List<StockTransaction> findRecentTransactions(@Param("productSkuId") Long productSkuId,
                                                    @Param("limit") int limit);

    /**
     * 根据操作人查询流水（用于审计追溯）
     *
     * 使用场景：
     * 查询某个员工的所有操作记录
     *
     * @param operatorId 操作人ID
     * @return 该操作人的所有流水记录
     */
    List<StockTransaction> findByOperatorId(Long operatorId);

    /**
     * 统计某商品的流水记录总数
     * 方法命名规范：countBy + 字段名
     *
     * @param productSkuId 商品ID
     * @return 流水记录总数
     */
    long countByProductSku_Id(Long productSkuId);

    /**
     * 统计指定时间范围内的流水记录总数
     *
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 流水记录总数
     */
    @Query("SELECT COUNT(t) FROM StockTransaction t " +
           "WHERE t.createdAt BETWEEN :startDate AND :endDate")
    long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate);

    /**
     * ⭐核心方法：计算商品的库存周转率（过去N天）
     *
     * 业务逻辑：
     * 库存周转率 = 总出库量 / 平均库存
     * 平均库存 = (期初库存 + 期末库存) / 2
     *
     * 返回结果格式：
     * {
     *   "productSkuId": 1,
     *   "totalOutbound": 900,
     *   "avgInventory": 150,
     *   "turnoverRate": 6.0  // 周转 6 次
     * }
     *
     * 使用场景：
     * 1. 评估商品流转速度（周转率越高，流转越快）
     * 2. 优化库存配置（高周转率商品应增加库存）
     *
     * @param productSkuId 商品ID
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 总出库量（周转率需在 Service 层计算）
     */
    @Query("SELECT SUM(t.quantity) FROM StockTransaction t " +
           "WHERE t.productSku.id = :productSkuId " +
           "AND t.transactionType = 'OUT' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Integer calculateTurnoverRate(@Param("productSkuId") Long productSkuId,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    /**
     * 查询某商品在指定来源类型的流水
     *
     * 使用场景：
     * 1. 查询某商品的所有销售出库记录（SALE_OUT）
     * 2. 查询某商品的所有采购入库记录（PURCHASE_IN）
     *
     * @param productSkuId 商品ID
     * @param sourceType 来源类型
     * @return 符合条件的流水记录
     */
    @Query("SELECT t FROM StockTransaction t " +
           "WHERE t.productSku.id = :productSkuId " +
           "AND t.sourceType = :sourceType " +
           "ORDER BY t.createdAt DESC")
    List<StockTransaction> findByProductSkuAndSourceType(@Param("productSkuId") Long productSkuId,
                                                        @Param("sourceType") SourceType sourceType);

    /**
     * 查询某时间段内某类型的流水（用于报表统计）
     *
     * 使用场景：
     * 1. 统计本月的所有销售出库流水
     * 2. 统计本季度的所有采购入库流水
     *
     * @param transactionType 变动类型
     * @param startDate 开始时间
     * @param endDate 结束时间
     * @return 符合条件的流水记录
     */
    @Query("SELECT t FROM StockTransaction t " +
           "WHERE t.transactionType = :transactionType " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    List<StockTransaction> findByTypeAndDateRange(@Param("transactionType") TransactionType transactionType,
                                                   @Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
}

package com.wms.system.dto.inbound;

import com.wms.system.entity.enums.InboundOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单响应 DTO
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundOrderResponse {

    private Long id;
    private String orderNo;
    private InboundOrderStatus status;
    private String statusDescription;

    // 供应商信息
    private Long supplierId;
    private String supplierCode;
    private String supplierName;

    // 数量信息
    private Integer totalPlanQty;
    private Integer totalConfirmedQty;
    private Integer totalActualQty;

    private LocalDate expectedDate;
    private String remark;

    // 总经理审批信息
    private Long gmApprovedBy;
    private LocalDateTime gmApprovedAt;
    private String gmApprovalComment;

    // 采购员确认信息
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private String confirmationComment;

    // 仓库收货信息
    private Long receivedBy;
    private LocalDateTime receivedAt;

    // 申请人信息
    private Long applicantId;
    private String applicantName;

    // 审计日志
    private String auditLog;

    // 时间戳
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 明细列表
    private List<InboundOrderItemResponse> items;

    /**
     * 入库单明细响应 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundOrderItemResponse {

        private Long id;

        // 产品信息
        private Long productSkuId;
        private String productName;
        private String productBarcode;
        private String productCode;
        private String productSku;

        // 数量信息
        private Integer planQty;
        private Integer confirmedQty;
        private Integer actualQty;

        // 批次信息
        private String batchCode;
        private LocalDate expiryDate;
        private LocalDate productionDate;
        private String externalBatchCode;

        // 目标仓库和库位
        private Long targetWarehouseId;
        private String targetWarehouseName;
        private Long targetLocationId;
        private String targetLocationCode;

        // 成本信息
        private BigDecimal unitCost;

        private String remark;

        // 时间戳
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

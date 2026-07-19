package com.wms.system.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售订单响应 DTO
 *
 * V3.7 架构：销售订单管理
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderResponse {

    /**
     * 订单ID
     */
    private Long id;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 订单状态描述
     */
    private String statusDescription;

    private String commercialStatus;

    private String commercialStatusDescription;

    private String fulfillmentStatus;

    private String fulfillmentStatusDescription;

    private String allocationPolicy;

    private LocalDate requestedShipDate;

    private LocalDate promisedShipDate;

    private String shortageReason;

    private Long fulfillmentVersion;

    private String channel;

    private String externalOrderId;

    private String externalOrderNo;

    private String consigneeName;

    private String consigneePhone;

    private String shipAddress1;

    private String shipAddress2;

    private String shipCity;

    private String shipProvince;

    private String shipZip;

    private String shipCountryCode;

    /**
     * 审批原因
     */
    private String reviewReason;

    /**
     * 审批人ID
     */
    private Long reviewedBy;

    /**
     * 审批人姓名
     */
    private String reviewedByName;

    /**
     * 审批时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 审批意见
     */
    private String reviewComment;

    /**
     * 申请人ID
     */
    private Long applicantId;

    /**
     * 申请人姓名
     */
    private String applicantName;

    /**
     * 订单明细列表
     */
    private List<SalesOrderItemResponse> items;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 订单明细响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesOrderItemResponse {

        /**
         * 明细ID
         */
        private Long id;

        /**
         * 产品ID
         */
        private Long productSkuId;

        /**
         * 产品名称
         */
        private String productName;

        /**
         * 产品条形码
         */
        private String productBarcode;

        /**
         * 销售数量
         */
        private Integer quantity;

        private Integer requestedQty;

        private Integer allocatedQty;

        private Integer shippedQty;

        private Integer backorderQty;

        private Integer cancelledQty;

        private String fulfillmentStatus;

        private String fulfillmentStatusDescription;

        /**
         * 单价
         */
        private BigDecimal unitPrice;

        /**
         * 小计金额
         */
        private BigDecimal subtotal;

        /**
         * 是否拒收临期品
         */
        private Boolean rejectNearExpiry;

        /**
         * 指定批次ID列表
         */
        private List<Long> specifiedBatchIds;

        /**
         * 备注
         */
        private String remark;
    }
}

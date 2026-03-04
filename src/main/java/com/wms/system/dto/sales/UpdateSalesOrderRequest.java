package com.wms.system.dto.sales;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新销售订单请求 DTO
 *
 * V3.7 架构：销售订单管理
 *
 * 说明：
 * - 仅允许在 DRAFT 或 PENDING_APPROVAL 状态下修改
 *
 * @author WMS Team
 * @since 2026-01-28
 * @version 3.7 (Smart Sales and Outbound System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSalesOrderRequest {

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 订单明细列表
     */
    @NotEmpty(message = "订单明细不能为空")
    @Size(min = 1, message = "至少需要一个订单明细")
    private List<SalesOrderItemData> items;

    /**
     * 订单明细数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesOrderItemData {

        /**
         * 产品ID
         */
        @NotNull(message = "产品ID不能为空")
        private Long productId;

        /**
         * 销售数量
         */
        @NotNull(message = "销售数量不能为空")
        @Min(value = 1, message = "销售数量必须大于 0")
        private Integer quantity;

        /**
         * 单价
         */
        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0.00", message = "单价不能为负数")
        private BigDecimal unitPrice;

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
        @Size(max = 500, message = "备注长度不能超过 500 个字符")
        private String remark;
    }
}

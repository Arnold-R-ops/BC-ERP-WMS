package com.wms.system.dto.inbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建入库单请求 DTO
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInboundOrderRequest {

    /**
     * 供应商 ID
     */
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    /**
     * 预计到货日期
     */
    private LocalDate expectedDate;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;

    /**
     * 入库单明细列表
     */
    @NotEmpty(message = "入库单明细不能为空")
    @Valid
    private List<InboundOrderItemRequest> items;

    /**
     * 入库单明细项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundOrderItemRequest {

        /**
         * 产品 ID
         */
        @NotNull(message = "产品ID不能为空")
        private Long productSkuId;

        /**
         * 计划数量
         */
        @NotNull(message = "计划数量不能为空")
        @Min(value = 1, message = "计划数量必须大于0")
        private Integer planQty;

        /**
         * 单位成本
         */
        @DecimalMin(value = "0.0", message = "单位成本不能为负数")
        private BigDecimal unitCost;

        /**
         * 目标仓库 ID
         */
        @NotNull(message = "目标仓库ID不能为空")
        private Long targetWarehouseId;

        /**
         * 目标库位 ID
         */
        @NotNull(message = "目标库位ID不能为空")
        private Long targetLocationId;

        /**
         * 备注
         */
        @Size(max = 500, message = "备注长度不能超过500")
        private String remark;
    }
}

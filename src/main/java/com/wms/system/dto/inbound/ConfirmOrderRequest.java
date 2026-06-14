package com.wms.system.dto.inbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 确认订单请求 DTO（采购员确认）
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmOrderRequest {

    /**
     * 确认意见
     */
    @Size(max = 500, message = "确认意见长度不能超过500")
    private String comment;

    /**
     * 明细确认列表
     */
    @NotEmpty(message = "明细确认列表不能为空")
    @Valid
    private List<ItemConfirmation> confirmations;

    /**
     * 明细确认项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemConfirmation {

        /**
         * 明细 ID
         */
        @NotNull(message = "明细ID不能为空")
        private Long itemId;

        /**
         * 确认数量
         */
        @NotNull(message = "确认数量不能为空")
        @Min(value = 0, message = "确认数量不能为负数")
        private Integer confirmedQty;

        /**
         * 过期日期（必填）
         */
        @NotNull(message = "过期日期不能为空")
        private LocalDate expiryDate;

        /**
         * 生产日期
         */
        private LocalDate productionDate;

        /**
         * 外部批次码（供应商提供）
         */
        @Size(max = 100, message = "外部批次码长度不能超过100")
        private String externalBatchCode;

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
    }
}

package com.wms.system.dto.inbound;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 收货请求 DTO（仓库收货）
 *
 * @author WMS Team
 * @since 2026-01-25
 * @version 3.5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveGoodsRequest {

    /**
     * 收货明细列表
     */
    @NotEmpty(message = "收货明细列表不能为空")
    private List<ItemReceipt> receipts;

    /**
     * 收货明细项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemReceipt {

        /**
         * 明细 ID
         */
        @NotNull(message = "明细ID不能为空")
        private Long itemId;

        /**
         * 实收数量
         */
        @NotNull(message = "实收数量不能为空")
        @Min(value = 0, message = "实收数量不能为负数")
        private Integer actualQty;

        /**
         * 库位 ID
         */
        @NotNull(message = "库位ID不能为空")
        private Long locationId;
    }
}

package com.wms.system.dto.integration;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ShopifyReconciliationRepairResult {
    private Integer requested;
    private Integer created;
    private Integer skipped;
    private Integer blocked;
    private List<Item> items;

    @Data
    @Builder
    @Schema(name = "ShopifyReconciliationRepairItem")
    public static class Item {
        private Long rawEventId;
        private String externalOrderId;
        private String status;
        private Long salesOrderId;
        private String message;
    }
}

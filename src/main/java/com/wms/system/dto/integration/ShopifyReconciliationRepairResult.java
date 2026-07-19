package com.wms.system.dto.integration;

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
    public static class Item {
        private Long rawEventId;
        private String externalOrderId;
        private String status;
        private Long salesOrderId;
        private String message;
    }
}

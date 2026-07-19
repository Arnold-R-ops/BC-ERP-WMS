package com.wms.system.dto.integration;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class ShopifyReconciliationReport {
    private Long reportEventId;
    private Long configId;
    private String storeIdentifier;
    private Integer windowDays;
    private OffsetDateTime updatedAtMin;
    private Long localOrderCount;
    private Integer remoteOrderCount;
    private Integer missingOrderCount;
    private boolean reportOnly;
    private List<MissingOrder> missingOrders;

    @Data
    @Builder
    public static class MissingOrder {
        private Long rawEventId;
        private String externalOrderId;
        private String externalOrderNo;
        private String financialStatus;
        private String fulfillmentStatus;
        private boolean repairEligible;
        private String reason;
    }
}

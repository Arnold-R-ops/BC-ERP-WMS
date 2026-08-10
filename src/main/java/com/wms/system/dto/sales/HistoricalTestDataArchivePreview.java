package com.wms.system.dto.sales;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalTestDataArchivePreview {
    private Long salesOrderId;
    private String orderNo;
    private String orderStatus;
    private boolean eligible;
    private List<String> blockers;
    private List<Long> taskIds;
    private int reservationCount;
    private int stockTransactionCount;
    private int shipmentCount;
    private int activeShipmentCount;
    private int voidedShipmentCount;
    private String snapshotFingerprint;
}

package com.wms.system.dto.v45;

import com.wms.system.entity.enums.BackorderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BackorderLineResponse {
    private Long id;
    private Long salesOrderId;
    private Long salesOrderItemId;
    private Long productSkuId;
    private Integer requestedQty;
    private Integer remainingQty;
    private Integer allocatedQty;
    private BackorderStatus status;
    private Integer priority;
    private LocalDate promisedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

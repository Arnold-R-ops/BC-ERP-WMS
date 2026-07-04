package com.wms.system.dto.product;

import com.wms.system.entity.enums.BatchTrackingMode;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private Integer version;
    private String barcode;
    private String name;
    private String skuName;
    private String specs;
    private String specification;
    private Long spuId;
    private String spuName;
    private BigDecimal unitPrice;
    private BigDecimal minSalesPrice;
    private Integer minStock;
    private Integer safetyStock;
    private Integer leadTime;
    private Integer perPackQty;
    private Integer conversionRate;
    private String packUnit;
    private Integer nearExpiryDays;
    private String category;
    private String supplier;
    private String description;
    private BatchTrackingMode batchTrackingMode;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.wms.system.dto.productsku;

import com.wms.system.entity.enums.BatchTrackingMode;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSkuResponse {

    private Long id;
    private Integer version;
    private String skuCode;
    private String barcode;
    private String name;
    private String skuName;
    private String specs;
    private String specification;
    private Long productId;
    private String productName;
    private BigDecimal unitPrice;
    private BigDecimal minSalesPrice;
    private Integer minStock;
    private Integer safetyStock;
    private Integer leadTime;
    private Integer perPackQty;
    private Integer conversionRate;
    private String packUnit;
    private Integer nearExpiryDays;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String supplier;
    private String description;
    private BatchTrackingMode batchTrackingMode;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

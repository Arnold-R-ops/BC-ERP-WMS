package com.wms.system.dto.productsku;

import com.wms.system.entity.enums.BatchTrackingMode;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductSkuRequest {

    @NotBlank(message = "条形码不能为空")
    @Size(max = 50)
    private String barcode;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "SKU 名称不能为空")
    @Size(max = 100)
    private String skuName;

    @NotNull(message = "SPU ID 不能为空")
    private Long productId;

    @Size(max = 500)
    private String specs;

    @Size(max = 100)
    private String specification;

    @NotNull(message = "单价不能为空")
    @DecimalMin("0.00")
    private BigDecimal unitPrice;

    @DecimalMin("0.00")
    private BigDecimal minSalesPrice;

    @Min(0)
    private Integer minStock;

    @Min(0)
    private Integer safetyStock;

    @Min(0)
    private Integer leadTime;

    @Min(1)
    private Integer perPackQty;

    @Min(1)
    private Integer conversionRate;

    @Size(max = 20)
    private String packUnit;

    @Min(1)
    private Integer nearExpiryDays;

    @Size(max = 200)
    private String supplier;

    @Size(max = 1000)
    private String description;

    private BatchTrackingMode batchTrackingMode;

    private Boolean enabled;
}

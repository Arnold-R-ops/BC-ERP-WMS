package com.wms.system.entity;

import com.wms.system.entity.enums.InventoryQualityStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_disposition_line")
public class InventoryDispositionLine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disposition_order_id", nullable = false)
    private InventoryDispositionOrder order;

    @Column(name = "source_batch_id", nullable = false)
    private Long sourceBatchId;

    @Column(name = "result_batch_id")
    private Long resultBatchId;

    @Column(name = "product_sku_id", nullable = false)
    private Long productSkuId;

    @Column(name = "product_sku_code", nullable = false, length = 100)
    private String productSkuCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "source_batch_code", nullable = false, length = 50)
    private String sourceBatchCode;

    @Column(name = "result_batch_code", length = 50)
    private String resultBatchCode;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_name", nullable = false, length = 200)
    private String warehouseName;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "location_code", nullable = false, length = 100)
    private String locationCode;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "executed_quantity", nullable = false)
    @Builder.Default
    private Integer executedQuantity = 0;

    @Column(name = "reallocated_reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reallocatedReservedQuantity = 0;

    @Column(name = "backordered_quantity", nullable = false)
    @Builder.Default
    private Integer backorderedQuantity = 0;

    @Column(name = "quality_exception_quantity", nullable = false)
    @Builder.Default
    private Integer qualityExceptionQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_quality_status", nullable = false, length = 30)
    private InventoryQualityStatus fromQualityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_quality_status", nullable = false, length = 30)
    private InventoryQualityStatus toQualityStatus;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    public int outstandingQuantity() {
        return quantity - executedQuantity;
    }
}
